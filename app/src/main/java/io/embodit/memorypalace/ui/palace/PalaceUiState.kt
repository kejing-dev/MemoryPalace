package io.embodit.memorypalace.ui.palace

import io.embodit.memorypalace.data.LanguagePref
import io.embodit.memorypalace.domain.BlockReason
import io.embodit.memorypalace.domain.ImportError
import io.embodit.memorypalace.domain.SurfaceKind
import io.embodit.memorypalace.domain.Vec3

enum class Phase { STARTING, NO_PERMISSION, SCANNING, READY }

enum class Mode { HOME, PLACING, REVIEW, REVIEW_DONE, RECALL, RECALL_DONE }

enum class Popup { LIBRARY, IMPORT, SETTINGS }

enum class HomeKind { EMPTY, DUE, DONE, LOST }

enum class ConfirmKind { TEAR_DOWN, DELETE_LOCUS, END_PLACING }

enum class NudgeKind { TOO_FAR, GO_FIRST, FLIP_FIRST, PLACE_FAILED }

/** One row of the palace library (design S03). */
data class PalaceRow(val id: String, val name: String, val color: Int, val loci: Int, val placed: Int, val cards: Int, val depth: Int, val dueToday: Int, val current: Boolean)

/** A pinned level-1 locus of the current palace, as the scene draws it. */
data class LocusUi(
    val nodeId: String,
    /** Route number, 1-based (= placing order). */
    val number: Int,
    val title: String,
    val position: Vec3,
    val normal: Vec3,
    val surface: SurfaceKind,
    val found: Boolean,
    /** The locus is itself a card (flat decks). */
    val single: Boolean,
    val cards: Int,
    /** Cards waiting in it: due today (home), still to rate (review). */
    val due: Int,
    val tone: Tone,
) {
    enum class Tone { DUE, NEXT, DONE, DIM }
}

/** One cell of an opened locus (design `nesting.layout`): a card or a sub-locus. */
data class CellUi(
    val nodeId: String,
    /** ①②③ position in the grid, 1-based. */
    val index: Int,
    val group: Boolean,
    val text: String,
    val back: String = "",
    /** Due and not yet rated (review), or the next one (recall). */
    val lit: Boolean = false,
    /** A sub-locus: cards due under it. */
    val badge: Int = 0,
    /** Group: how many children. */
    val count: Int = 0,
    /** Recall: still face down, only ① ② ③ show. */
    val faceDown: Boolean = false,
    /** Showing its back (review: rate it; recall: judge it). */
    val flipped: Boolean = false,
    /** Rated in this round / judged right: a jade tick. */
    val done: Boolean = false,
    val wrong: Boolean = false,
)

/** The open locus: a title above and the grid below; for a single-card locus the plaque is the card. */
data class OpenUi(
    val locusId: String,
    val number: Int,
    /** "中国朝代 › 先秦 › 周". */
    val crumbs: List<String>,
    /** Title text: "② 秦汉" or "① 先秦 › 周" (empty while the recall hides it). */
    val title: String,
    /** Level below the locus (entered a sub-locus). */
    val inside: Boolean,
    val cells: List<CellUi>,
    /** The locus itself is a card. */
    val single: CellUi? = null,
    /** Recall: the name is still hidden ("这里是哪里？"). */
    val hidden: Boolean = false,
    val cards: Int = 0,
)

data class PlacingUi(
    /** Route number the next pin gets. */
    val number: Int,
    val placed: Int,
    val total: Int,
    val nextTitle: String,
    /** Group: the labels of what is inside. Single card: front and back. */
    val nextLines: List<String>,
    val nextSingle: Boolean,
    val block: BlockReason?,
    /** The number just pinned (S08), shown for a moment. */
    val flash: Int?,
    /** S26: the locus picked for deleting. */
    val edit: String?,
    val canUndo: Boolean,
    val busy: Boolean,
)

data class ReviewUi(val done: Int, val total: Int, val nextNumber: Int?, val nextTitle: String?, val hereDue: Int, val undo: Boolean, val single: Boolean)

data class SummaryUi(val remembered: Int, val blurry: Int, val forgot: Int, val tomorrow: Int)

data class RecallUi(val number: Int, val lociTotal: Int, val nextTitle: String?, val revealed: Boolean, val inLocusDone: Int, val inLocusTotal: Int)

data class RecallResultUi(val right: Int, val total: Int, val outOfOrder: List<Int>, val wrong: Int)

data class Nudge(val kind: NudgeKind, val text: String, val targetId: String?)

data class ImportFileUi(val name: String, val lines: Int)

data class ImportResultUi(val palaces: Int, val loci: Int, val cards: Int, val depth: Int)

data class PalaceUiState(
    val phase: Phase = Phase.STARTING,
    val scanSlow: Boolean = false,
    val surfaces: Int = 0,
    val mode: Mode = Mode.HOME,
    val home: HomeKind = HomeKind.EMPTY,
    val palaceId: String = "",
    val palaceName: String = "",
    val palaceColor: Int = 0,
    val palaces: List<PalaceRow> = emptyList(),
    val lociTotal: Int = 0,
    val placedCount: Int = 0,
    val cardTotal: Int = 0,
    val depth: Int = 0,
    val dueToday: Int = 0,
    val dueLoci: Int = 0,
    val tomorrow: Int = 0,
    val unplaced: Int = 0,
    val lostLoci: Int = 0,
    val lostCards: Int = 0,
    val justPlaced: Boolean = false,
    val loci: List<LocusUi> = emptyList(),
    /** The locus the line leads to and the pocket names (review: next with due cards; recall: next in order). */
    val targetLocus: String? = null,
    /** Loci that may open when walked up to. */
    val wakeCandidates: Set<String> = emptySet(),
    val open: OpenUi? = null,
    val placing: PlacingUi? = null,
    val review: ReviewUi? = null,
    val summary: SummaryUi? = null,
    val recall: RecallUi? = null,
    val recallResult: RecallResultUi? = null,
    val nudge: Nudge? = null,
    val popup: Popup? = null,
    val importFiles: List<ImportFileUi> = emptyList(),
    val importResult: ImportResultUi? = null,
    val importError: ImportError? = null,
    val confirm: ConfirmKind? = null,
    val confirmName: String = "",
    val language: LanguagePref = LanguagePref.SYSTEM,
    val zh: Boolean = true,
    val versionName: String = "",
    val designState: String = "S01",
) {
    /** The reticle and plane taps are live. */
    val aiming: Boolean
        get() = mode == Mode.PLACING && confirm == null && popup == null && placing?.edit == null
}
