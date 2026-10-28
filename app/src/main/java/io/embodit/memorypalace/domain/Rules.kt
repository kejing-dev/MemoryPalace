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

    /** v0.3: the controller stick pushes the spot out / pulls it in (design `spatialLayout.placement.distance`). */
    const val MIN_DISTANCE = 0.6f
    const val MAX_DISTANCE = 2.0f

    /** Below / above the eyes a free locus may sit (a gaze at the floor or the ceiling is clamped). */
    const val FREE_BELOW_EYE = 0.8f
    const val FREE_ABOVE_EYE = 0.3f

    /**
     * v0.2.2 (device feedback: "use spatial anchors, no plane detection"): a locus is an anchor in the air,
     * [FREE_DISTANCE] along [dir] from the eyes, its height kept between [FREE_BELOW_EYE] below and
     * [FREE_ABOVE_EYE] above the eyes; its plaque stands upright facing back towards where it was placed from.
     */
    fun free(head: Vec3, dir: Vec3, distance: Float = FREE_DISTANCE): Spot {
        val d = dir.normalized().let { if (it.length < 0.5f) Vec3.AHEAD else it }
        val flat = Vec3(d.x, 0f, d.z).let { if (it.length < 1e-3f) Vec3.AHEAD else it.normalized() }
        val p = head + d * distance.coerceIn(MIN_DISTANCE, MAX_DISTANCE)
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
 * v0.3 (design `interactionModel.click.readability`): a locus opened from afar turns to the viewer and
 * grows with the distance so its cards read as they would at 1 m; capped at 5 times (palaces span <= 5 m).
 */
object Readability {
    const val NEAR_M = 1.0f
    const val MAX_SCALE = 5f

    fun scale(distance: Float): Float = (distance / NEAR_M).coerceIn(1f, MAX_SCALE)
}
