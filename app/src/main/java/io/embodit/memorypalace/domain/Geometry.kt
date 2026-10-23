package io.embodit.memorypalace.domain

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Pose of the head in Stage metres. */
data class HeadPose(val position: Vec3, val rotation: Quat4) {
    val forward: Vec3
        get() = rotation.rotate(Vec3.AHEAD)

    val up: Vec3
        get() = rotation.rotate(Vec3.UP)

    val right: Vec3
        get() = rotation.rotate(Vec3.RIGHT)

    /** Unit vector of the view direction projected on the floor; [fallback] when looking straight down. */
    fun level(fallback: P2 = P2(0f, -1f)): P2 {
        val f = forward
        val l = hypot(f.x, f.z)
        return if (l < 0.05f) fallback else P2(f.x / l, f.z / l)
    }
}

/** How a room panel is turned: yaw about the vertical and pitch towards the viewer, radians; no roll. */
data class PanelFacing(val yaw: Float, val pitch: Float) {
    val rotation: Quat4
        get() = Quat4.axisAngle(Vec3.UP, yaw) * Quat4.axisAngle(Vec3.RIGHT, -pitch)

    /** Unit vector out of the panel's face, towards the viewer. */
    val normal: Vec3
        get() = Vec3(sin(yaw) * cos(pitch), sin(pitch), cos(yaw) * cos(pitch))

    val right: Vec3
        get() = Vec3(cos(yaw), 0f, -sin(yaw))

    val up: Vec3
        get() = normal.cross(right)
}

/**
 * Facing rule for panels that stand in the room (Spatial Ruler): turn about the vertical towards the
 * head, then pitch towards it (clamped to 60 degrees), never roll. Straight above or below, the last
 * yaw is kept.
 */
object FacePanel {
    private const val MAX_PITCH_RAD = 1.0471976f
    private const val KEEP_YAW_WITHIN_M = 0.1f

    fun facing(panel: Vec3, head: Vec3, lastYaw: Float?): PanelFacing {
        val to = head - panel
        val flat = hypot(to.x, to.z)
        val yaw = if (flat < KEEP_YAW_WITHIN_M && lastYaw != null) lastYaw else if (flat < 1e-4f) 0f else atan2(to.x, to.z)
        val pitch = atan2(to.y, flat).coerceIn(-MAX_PITCH_RAD, MAX_PITCH_RAD)
        return PanelFacing(yaw, pitch)
    }
}

/** A point or direction on the floor plane (Stage x, z). */
data class P2(val x: Float, val z: Float) {
    operator fun plus(o: P2) = P2(x + o.x, z + o.z)

    operator fun minus(o: P2) = P2(x - o.x, z - o.z)

    operator fun times(s: Float) = P2(x * s, z * s)

    fun dot(o: P2): Float = x * o.x + z * o.z

    val length: Float
        get() = hypot(x, z)

    fun normalized(): P2 {
        val l = length
        return if (l < 1e-9f) this else P2(x / l, z / l)
    }

    fun at(y: Float): Vec3 = Vec3(x, y, z)

    companion object {
        fun of(v: Vec3) = P2(v.x, v.z)
    }
}

/** Horizontal (floor-projected) distance between two Stage points: the design's wake distance. */
fun flatDistance(a: Vec3, b: Vec3): Float = hypot(a.x - b.x, a.z - b.z)

/**
 * A detected plane: a polygon with its vertices in Stage metres. Winding is not trusted (wall quads
 * wind outwards on the emulator, Spatial Ruler), so hits are two-sided and [normal]'s sign is arbitrary.
 */
data class PlaneSurface(val id: String, val semantic: String, val vertices: List<Vec3>) {
    val normal: Vec3 by lazy {
        var best = Vec3.UP
        var bestLen = 0f
        for (i in 1 until vertices.size - 1) {
            val c = (vertices[i] - vertices[0]).cross(vertices[i + 1] - vertices[0])
            if (c.length > bestLen) {
                bestLen = c.length
                best = c
            }
        }
        if (bestLen < 1e-9f) Vec3.UP else best.normalized()
    }

    val centroid: Vec3 by lazy { if (vertices.isEmpty()) Vec3.ZERO else vertices.fold(Vec3.ZERO) { a, v -> a + v } / vertices.size.toFloat() }

    val isVertical: Boolean
        get() = abs(normal.y) < 0.2f

    val isHorizontal: Boolean
        get() = abs(normal.y) > 0.9f

    /** `SEMANTIC:uuid8`, used in entity names and logs. */
    val label: String
        get() = "$semantic:${id.take(8)}"

    /** Distance from [p] (on the plane) to the polygon's nearest edge. */
    fun edgeDistance(p: Vec3): Float {
        var best = Float.MAX_VALUE
        for (i in vertices.indices) {
            val a = vertices[i]
            val b = vertices[(i + 1) % vertices.size]
            val ab = b - a
            val t = if (ab.dot(ab) < 1e-9f) 0f else ((p - a).dot(ab) / ab.dot(ab)).coerceIn(0f, 1f)
            best = minOf(best, (a + ab * t).distanceTo(p))
        }
        return best
    }

    /** Is [p] (on the plane) inside the polygon? Convex or not: crossing count in the plane's 2D frame. */
    fun contains(p: Vec3): Boolean {
        if (vertices.size < 3) return false
        val n = normal
        val u = (vertices[1] - vertices[0]).rejectFrom(n).normalized()
        val w = n.cross(u)
        val px = (p - vertices[0]).dot(u)
        val py = (p - vertices[0]).dot(w)
        var inside = false
        var j = vertices.size - 1
        for (i in vertices.indices) {
            val xi = (vertices[i] - vertices[0]).dot(u)
            val yi = (vertices[i] - vertices[0]).dot(w)
            val xj = (vertices[j] - vertices[0]).dot(u)
            val yj = (vertices[j] - vertices[0]).dot(w)
            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    /** Two-sided ray hit: distance along the ray, or null. */
    fun hit(origin: Vec3, dir: Vec3): Float? {
        val n = normal
        val denom = dir.dot(n)
        if (abs(denom) < 1e-5f) return null
        val t = (vertices[0] - origin).dot(n) / denom
        if (t <= 0.05f) return null
        return if (contains(origin + dir * t)) t else null
    }
}

/** The nearest plane hit along a ray; [normal] faces the ray origin. */
data class SurfaceHit(val point: Vec3, val normal: Vec3, val distance: Float, val plane: PlaneSurface)

fun raycast(planes: Collection<PlaneSurface>, origin: Vec3, dir: Vec3, maxM: Float = 8f): SurfaceHit? {
    var best: SurfaceHit? = null
    for (p in planes) {
        val t = p.hit(origin, dir) ?: continue
        if (t > maxM || (best != null && t >= best.distance)) continue
        val n = if (p.normal.dot(dir) > 0f) -p.normal else p.normal
        best = SurfaceHit(origin + dir * t, n, t, p)
    }
    return best
}
