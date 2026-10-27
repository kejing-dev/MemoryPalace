package io.embodit.memorypalace.domain

/** Why the reticle cannot pin here (design S07). */
enum class BlockReason { SURFACE, TOO_LOW, TOO_HIGH, TOO_FAR_FROM_ROUTE, TOO_CLOSE, TABLE_EDGE, LIMIT, NO_SURFACE }

/** Where a locus would go: the plaque centre, its surface normal and the surface kind. */
data class Spot(val position: Vec3, val normal: Vec3, val surface: SurfaceKind)

/**
 * Placing rules (design `spatialLayout`): walls, unknown vertical surfaces and table tops near the
 * edge; plaque centre 0.80 - 1.80 m above the floor; ≥ 0.80 m from every locus of the palace; no two
 * loci of a palace more than 5.0 m apart (horizontally).
 */
object PlaceRules {
    const val MIN_H = 0.80f
    const val MAX_H = 1.80f
    const val MIN_GAP = 0.80f
    const val MAX_SPAN = 5.0f
    const val TABLE_EDGE = 0.30f
    const val WALL_OFFSET = 0.015f
    const val TABLE_LIFT = 0.09f

    private val BLOCKED = setOf("FLOOR", "CEILING", "SOFA", "CHAIR", "DOOR", "WINDOW", "BED", "HUMAN", "STAIRWAY", "OPENING")

    fun surfaceOf(plane: PlaneSurface): SurfaceKind? =
        when {
            plane.semantic in BLOCKED -> null
            plane.semantic == "TABLE" && plane.isHorizontal -> SurfaceKind.TABLE
            plane.isVertical -> if (plane.semantic == "WALL") SurfaceKind.WALL else SurfaceKind.OTHER
            else -> null
        }

    /** A surface that can take a locus at all (S01 counts these). */
    fun usable(plane: PlaneSurface): Boolean = surfaceOf(plane) != null

    /** The spot a hit on [plane] at [point] would give, before the distance rules. */
    fun spot(point: Vec3, normal: Vec3, plane: PlaneSurface): Spot? {
        val kind = surfaceOf(plane) ?: return null
        return if (kind == SurfaceKind.TABLE) Spot(point + Vec3.UP * TABLE_LIFT, Vec3.UP, kind) else Spot(point + normal * WALL_OFFSET, normal, kind)
    }

    /** v0.2.2: how far ahead along the gaze a locus is pinned when it is placed freely (no surface). */
    const val FREE_DISTANCE = 1.2f

    /** Below / above the eyes a free locus may sit (a gaze at the floor or the ceiling is clamped). */
    const val FREE_BELOW_EYE = 0.8f
    const val FREE_ABOVE_EYE = 0.3f

    /**
     * v0.2.2 (device feedback: "use spatial anchors, no plane detection"): a locus is an anchor in the air,
     * [FREE_DISTANCE] along [dir] from the eyes, its height kept between [FREE_BELOW_EYE] below and
     * [FREE_ABOVE_EYE] above the eyes; its plaque stands upright facing back towards where it was placed from.
     */
    fun free(head: Vec3, dir: Vec3): Spot {
        val d = dir.normalized().let { if (it.length < 0.5f) Vec3.AHEAD else it }
        val flat = Vec3(d.x, 0f, d.z).let { if (it.length < 1e-3f) Vec3.AHEAD else it.normalized() }
        val p = head + d * FREE_DISTANCE
        val y = p.y.coerceIn(head.y - FREE_BELOW_EYE, head.y + FREE_ABOVE_EYE)
        return Spot(Vec3(p.x, y, p.z), -flat, SurfaceKind.WALL)
    }

    /** The rules a free locus still follows: the room's anchor budget, 0.8 m from the others, a 5 m route. */
    fun checkFree(spot: Spot, others: List<Vec3>, appTotal: Int): BlockReason? {
        if (appTotal >= Limits.ANCHORED_LOCI_PER_APP) return BlockReason.LIMIT
        if (others.any { it.distanceTo(spot.position) < MIN_GAP }) return BlockReason.TOO_CLOSE
        if (others.any { flatDistance(it, spot.position) > MAX_SPAN }) return BlockReason.TOO_FAR_FROM_ROUTE
        return null
    }

    /** Null when [spot] may take a locus; [others] are the placed loci of the same palace. */
    fun check(spot: Spot?, plane: PlaneSurface?, floorY: Float, others: List<Vec3>, appTotal: Int): BlockReason? {
        if (plane == null) return BlockReason.NO_SURFACE
        if (spot == null) return BlockReason.SURFACE
        if (appTotal >= Limits.ANCHORED_LOCI_PER_APP) return BlockReason.LIMIT
        if (spot.surface == SurfaceKind.TABLE && plane.edgeDistance(spot.position - Vec3.UP * TABLE_LIFT) > TABLE_EDGE) return BlockReason.TABLE_EDGE
        val h = spot.position.y - floorY
        if (h < MIN_H) return BlockReason.TOO_LOW
        if (h > MAX_H) return BlockReason.TOO_HIGH
        if (others.any { it.distanceTo(spot.position) < MIN_GAP }) return BlockReason.TOO_CLOSE
        if (others.any { flatDistance(it, spot.position) > MAX_SPAN }) return BlockReason.TOO_FAR_FROM_ROUTE
        return null
    }
}

/**
 * Wake rules (design `spatialLayout.wakeDistance`): a locus opens within 1.00 m (horizontal), closes
 * beyond 1.20 m; one at a time; wall loci also need the head in front of the wall, table loci the
 * head above the table.
 */
object Wake {
    const val OPEN_M = 1.0f
    const val CLOSE_M = 1.2f
    const val LABEL_M = 3.0f
    const val GAZE_COS = 0.94f // ~20 degrees

    fun facing(locus: Locus, head: Vec3): Boolean =
        when (locus.surface) {
            SurfaceKind.TABLE -> head.y > locus.position.y
            else -> locus.normal.dot(head - locus.position) > 0f
        }

    /**
     * The locus that should be open now: [current] stays while within [CLOSE_M] (a card turned over
     * there keeps it too: nothing else wakes while it is near); otherwise the one looked at among
     * [candidates] within [OPEN_M], else the nearest.
     */
    fun pick(loci: List<Locus>, candidates: Set<String>, current: String?, head: HeadPose): String? {
        val cur = current?.let { id -> loci.firstOrNull { it.nodeId == id } }
        if (cur != null && cur.nodeId in candidates) {
            val d = flatDistance(cur.position, head.position)
            if (d <= CLOSE_M && facing(cur, head.position)) return cur.nodeId
        }
        val near = loci.filter { it.nodeId in candidates && flatDistance(it.position, head.position) <= OPEN_M && facing(it, head.position) }
        if (near.isEmpty()) return null
        val f = head.forward
        val gazed =
            near.map { it to (it.position - head.position).normalized().dot(f) }.filter { it.second >= GAZE_COS }.maxByOrNull { it.second }?.first
        return (gazed ?: near.minByOrNull { flatDistance(it.position, head.position) })?.nodeId
    }

    /** "Come 0.6 m closer": how much farther than [OPEN_M] a target is, rounded up to 0.1 m. */
    fun closer(distance: Float): Float = (kotlin.math.ceil(((distance - OPEN_M).coerceAtLeast(0.1f)) * 10f) / 10f)
}
