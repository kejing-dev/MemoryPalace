package io.embodit.memorypalace.domain

/**
 * The nested palace (design `nesting`): palace (one route through the room) -> locus (level 1, pinned
 * to a real surface, the only level with an anchor) -> sub-locus (level 2, optional) -> card (leaf).
 * A card may sit on level 1, 2 or 3; a sub-locus only on level 2; so the tree is at most 3 deep.
 */
data class Palace(
    val id: String,
    val nameZh: String,
    val nameEn: String,
    /** Index into the design's `deckColors`. */
    val color: Int,
    /** `preset:<id>` or `import:<file name>`. */
    val source: String,
) {
    fun name(zh: Boolean): String = if (zh) nameZh else nameEn
}

enum class NodeKind { GROUP, CARD }

/** One SM-2 record (design `dataModel.srs`); [dueDay] null = new, due on the day its locus is placed. */
data class Srs(val ef: Float = 2.5f, val interval: Int = 0, val reps: Int = 0, val dueDay: Int? = null, val lapses: Int = 0) {
    fun isDue(today: Int): Boolean = dueDay == null || dueDay <= today
}

data class Node(
    val id: String,
    val palaceId: String,
    /** Null = a level-1 locus. */
    val parentId: String?,
    val order: Int,
    val kind: NodeKind,
    /** Group title (a locus or sub-locus that holds children). */
    val title: String = "",
    val front: String = "",
    val back: String = "",
    val srs: Srs? = null,
) {
    val isCard: Boolean
        get() = kind == NodeKind.CARD

    /** What a plaque or grid cell says: a group's title, a card's front. */
    val label: String
        get() = if (isCard) front else title
}

enum class SurfaceKind { WALL, TABLE, OTHER }

/**
 * A level-1 locus pinned in the room. [route] is the placing order (= the route, design
 * `dataModel.deckRule`); [position] is the plaque centre, [normal] points out of the surface.
 */
data class Locus(
    val nodeId: String,
    val palaceId: String,
    val anchorId: String,
    val route: Int,
    val position: Vec3,
    val normal: Vec3,
    val surface: SurfaceKind,
)

/** Read-only view of one palace's nodes. */
class PalaceTree(val palaceId: String, nodes: List<Node>) {
    val nodes: List<Node> = nodes.filter { it.palaceId == palaceId }
    private val byId = this.nodes.associateBy { it.id }
    private val kids: Map<String?, List<Node>> = this.nodes.groupBy { it.parentId }.mapValues { (_, v) -> v.sortedBy { it.order } }

    operator fun get(id: String): Node? = byId[id]

    fun children(id: String?): List<Node> = kids[id] ?: emptyList()

    /** Level-1 loci in tree (import) order. */
    val loci: List<Node>
        get() = children(null)

    /** Every card in the subtree of [id] (the node itself when it is a card), in tree order. */
    fun cardsUnder(id: String): List<Node> {
        val n = byId[id] ?: return emptyList()
        if (n.isCard) return listOf(n)
        return children(id).flatMap { cardsUnder(it.id) }
    }

    val cards: List<Node>
        get() = loci.flatMap { cardsUnder(it.id) }

    /** The level-1 locus that holds [id]. */
    fun locusOf(id: String): Node? {
        var n = byId[id] ?: return null
        while (n.parentId != null) n = byId[n.parentId!!] ?: return null
        return n
    }

    /** Path from the locus down to [id], both included. */
    fun pathTo(id: String): List<Node> {
        val out = ArrayList<Node>()
        var n = byId[id]
        while (n != null) {
            out.add(0, n)
            n = n.parentId?.let { byId[it] }
        }
        return out
    }

    /** Levels below the palace: 1 = flat loci, 3 = locus > sub-locus > card. */
    val depth: Int
        get() = if (nodes.isEmpty()) 0 else nodes.maxOf { pathTo(it.id).size }
}

/** Sizes the design caps (`nesting.limits`). */
object Limits {
    const val LOCI_PER_PALACE = 20
    const val CHILDREN_PER_NODE = 8
    const val MAX_DEPTH = 3
    const val CARDS_PER_PALACE = 120
    const val ANCHORED_LOCI_PER_APP = 300
    const val FRONT_MAX = 40
    const val BACK_MAX = 60
    const val FLAT_GROUP = 6
}

/** The day index with the 4 a.m. boundary (design `dataModel.srs`). */
object Days {
    private const val DAY_MS = 86_400_000L
    private const val SHIFT_MS = 4 * 3_600_000L

    fun of(epochMs: Long, utcOffsetMs: Long): Int = Math.floorDiv(epochMs + utcOffsetMs - SHIFT_MS, DAY_MS).toInt()
}

/**
 * SM-2 on cards only: forgot q=1, blurry q=3, remembered q=5 (design `dataModel.srs`). A lapse starts
 * the card over (due tomorrow); the session shows it once more at the end.
 */
object Sm2 {
    const val FORGOT = 1
    const val BLURRY = 3
    const val REMEMBERED = 5

    fun rate(s: Srs, q: Int, today: Int): Srs {
        val ef = (s.ef + (0.1f - (5 - q) * (0.08f + (5 - q) * 0.02f))).coerceAtLeast(1.3f)
        if (q < 3) return s.copy(ef = ef, interval = 1, reps = 0, dueDay = today + 1, lapses = s.lapses + 1)
        val reps = s.reps + 1
        val interval =
            when (reps) {
                1 -> 1
                2 -> 6
                else -> Math.round(s.interval.coerceAtLeast(1) * ef).coerceAtLeast(1)
            }
        return s.copy(ef = ef, interval = interval, reps = reps, dueDay = today + interval)
    }
}
