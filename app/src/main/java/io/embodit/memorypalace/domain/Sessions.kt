package io.embodit.memorypalace.domain

/**
 * One review round (design S10 - S16): the cards due today in loci that were found, rated in any
 * order; a "forgot" comes back once at the end of the round (`dataModel.srs`).
 */
data class Review(
    /** Due cards at the start, route order. */
    val queue: List<String>,
    val first: Map<String, Int> = emptyMap(),
    val again: List<String> = emptyList(),
    val againDone: Set<String> = emptySet(),
) {
    val pending: Set<String>
        get() = queue.filterTo(LinkedHashSet()) { it !in first } + again.filter { it !in againDone }

    val total: Int
        get() = queue.size + again.size

    val done: Int
        get() = first.size + againDone.size

    val finished: Boolean
        get() = pending.isEmpty()

    fun rate(id: String, q: Int): Review =
        when {
            id !in first && id in queue -> copy(first = first + (id to q), again = if (q == Sm2.FORGOT) again + id else again)
            id in again && id !in againDone -> copy(againDone = againDone + id)
            else -> this
        }

    /** Takes back the last rating of [id] (the 3 s "↶ change rating"). */
    fun unrate(id: String): Review =
        when {
            id in againDone -> copy(againDone = againDone - id)
            id in first -> copy(first = first - id, again = again - id)
            else -> this
        }

    fun count(q: Int): Int = first.values.count { it == q }
}

/** One step of the ordered recall inside a locus (design `nesting.routeOrderRule`). */
sealed interface RecallStep {
    val nodeId: String

    data class Card(override val nodeId: String) : RecallStep

    data class Enter(override val nodeId: String) : RecallStep

    data class Exit(override val nodeId: String) : RecallStep
}

object RecallSteps {
    /** Depth-first: cards in ①②③ order, a sub-locus is entered, recalled in its own order and left. */
    fun of(tree: PalaceTree, locusId: String): List<RecallStep> {
        val locus = tree[locusId] ?: return emptyList()
        if (locus.isCard) return listOf(RecallStep.Card(locusId))
        return walk(tree, locusId)
    }

    private fun walk(tree: PalaceTree, id: String): List<RecallStep> =
        tree.children(id).flatMap { n ->
            if (n.isCard) listOf(RecallStep.Card(n.id)) else listOf(RecallStep.Enter(n.id)) + walk(tree, n.id) + RecallStep.Exit(n.id)
        }
}

/**
 * "Walk it in order" (design S17 - S20): loci in route order, only the next one opens; inside it only
 * the next card can be opened. Results: cards right / total, and which loci were approached out of order.
 */
data class Recall(
    /** Found loci, route order. */
    val loci: List<String>,
    val index: Int = 0,
    val revealed: Boolean = false,
    val steps: List<RecallStep> = emptyList(),
    val step: Int = 0,
    /** The card opened (front and back), waiting for ✓ / ✕. */
    val open: String? = null,
    val right: Int = 0,
    val wrong: List<String> = emptyList(),
    /** Route numbers (1-based) of loci that were walked up to before their turn. */
    val outOfOrder: Set<Int> = emptySet(),
) {
    val nextLocus: String?
        get() = loci.getOrNull(index)

    val finished: Boolean
        get() = index >= loci.size

    val total: Int
        get() = right + wrong.size

    /** Skips the exits: the step the user acts on next. */
    fun current(): RecallStep? = steps.drop(step).firstOrNull { it !is RecallStep.Exit }

    /** The group whose grid is shown now (the locus itself at the top). */
    fun level(tree: PalaceTree): String? {
        val locus = nextLocus ?: return null
        val s = current() ?: return locus
        return tree[s.nodeId]?.parentId ?: locus
    }

    fun reveal(tree: PalaceTree): Recall {
        val l = nextLocus ?: return this
        return copy(revealed = true, steps = RecallSteps.of(tree, l), step = 0, open = null)
    }

    /** The user pinched [id] in the grid (or the single-card plaque): it opens only when it is next. */
    fun pinch(id: String): Pair<Recall, Boolean> {
        val s = current() ?: return this to false
        if (s.nodeId != id) return this to false
        val i = steps.indexOf(s)
        return when (s) {
            is RecallStep.Enter -> copy(step = i + 1) to true
            is RecallStep.Card -> copy(step = i, open = id) to true
            is RecallStep.Exit -> this to false
        }
    }

    /** ✓ / ✕ on the open card; the locus is finished when no card is left. */
    fun judge(ok: Boolean): Recall {
        val id = open ?: return this
        val i = steps.indexOfFirst { it is RecallStep.Card && it.nodeId == id }
        val next = copy(open = null, step = i + 1, right = if (ok) right + 1 else right, wrong = if (ok) wrong else wrong + id)
        return if (next.current() == null) next.copy(index = index + 1, revealed = false, steps = emptyList(), step = 0) else next
    }

    fun approachedEarly(number: Int): Recall = if (number - 1 > index) copy(outOfOrder = outOfOrder + number) else this
}
