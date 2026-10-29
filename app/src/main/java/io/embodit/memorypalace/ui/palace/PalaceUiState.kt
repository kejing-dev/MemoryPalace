package io.embodit.memorypalace.ui.palace

import io.embodit.memorypalace.data.LanguagePref
import io.embodit.memorypalace.domain.BlockReason
import io.embodit.memorypalace.domain.ImportError
import io.embodit.memorypalace.domain.MediaError
import io.embodit.memorypalace.domain.SurfaceKind
import io.embodit.memorypalace.domain.Vec3

enum class Phase { STARTING, NO_PERMISSION, SCANNING, READY }

enum class Mode { HOME, PLACING, REVIEW, REVIEW_DONE, RECALL, RECALL_DONE }

/**
 * Pages of the library popup (v0.2: new palace -> poetry / Wikipedia / file; v0.3: content library S32,
 * blank palace S33, add an item S34 - S38) and settings.
 */
enum class Popup { LIBRARY, NEW_PALACE, POETRY, WIKI, IMPORT, SETTINGS, CONTENT, BLANK, ADD_ITEM }

/** v0.3 add-an-item pages (S34 menu, S35 text, S36 image, S37 3D model, S38 error) and the locus names. */
enum class AddStep { MENU, TEXT, IMAGE, MODEL, GROUP, RENAME, ERROR }

enum class HomeKind { EMPTY, DUE, DONE, LOST }

enum class ConfirmKind { TEAR_DOWN, DELETE_LOCUS, END_PLACING }

/** v0.3: NOTHING_DUE replaces the old "x m closer" (S11): no step ever asks for walking. */
enum class NudgeKind { NOTHING_DUE, GO_FIRST, FLIP_FIRST, PLACE_FAILED }

/** One row of the palace library (design S03). */
data class PalaceRow(
    val id: String,
    val name: String,
    val color: Int,
    val loci: Int,
    val placed: Int,
    val cards: Int,
    val depth: Int,
    val dueToday: Int,
    val current: Boolean,
    /** In the 「诗词」 group (a poetry pack) rather than 「我的」. */
    val poetry: Boolean = false,
    /** Built from Wikipedia (its subtitle says so). */
    val wiki: Boolean = false,
)

/** One poetry pack on the S28 page. */
data class PackUi(val id: String, val name: String, val poets: List<String>, val poems: Int, val cards: Int, val built: Boolean)

/** The Wikipedia pages (S29 search, S30 preview, S31 unreachable). */
data class WikiUi(
    val lang: String,
    val query: String,
    val searching: Boolean,
    val hits: List<WikiHitUi>,
    /** Fetching / building the picked article. */
    val loading: String?,
    val preview: WikiPreviewUi?,
    val failed: Boolean,
)

data class WikiHitUi(val title: String, val description: String)

data class WikiPreviewUi(val title: String, val description: String, val loci: List<WikiLocusUi>, val keptLoci: Int, val keptCards: Int, val tooShort: Boolean)

data class WikiLocusUi(val index: Int, val title: String, val cards: Int, val firstFront: String, val kept: Boolean)

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
    /** v0.3 hover peek (design `interactionModel.hover.peek`): the card a glance shows under the plaque. */
    val peek: String = "",
    /** The peek is an image card: its file. */
    val peekImage: String? = null,
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
    /** The small credit line on the back: 《春望》· 杜甫, or 维基百科《长城》· CC BY-SA 4.0. */
    val source: String = "",
    /** v0.3: an image card (absolute file path). */
    val image: String? = null,
    /** v0.3: a card with a 3D memory object. */
    val model: Boolean = false,
    /** v0.3: the last cell while placing, 「＋ 加记忆点」 (S34). */
    val add: Boolean = false,
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
    /** A blank palace: every pinch adds a new locus 「位置 n」. */
    val free: Boolean = false,
)

/** One collection row of the content library (S32); [expanded] lists its volumes. */
data class CollectionUi(val id: String, val name: String, val structure: String, val cards: Int, val generated: Boolean, val volumes: List<VolumeUi>, val expanded: Boolean, val source: String = "", val license: String = "")

data class VolumeUi(val id: String, val name: String, val loci: Int, val cards: Int, val inLibrary: Boolean)

data class ContentUi(val category: String, val collections: List<CollectionUi>, val packs: List<PackUi>)

/** The add-an-item popup (S34 - S38) for [targetTitle] (a locus or a sub-locus). */
data class AddUi(
    val step: AddStep,
    val targetTitle: String,
    /** S36: the scaled copy of the picked image. */
    val image: String? = null,
    val imageScaled: Boolean = false,
    /** S37: the model is loaded and scaled to 0.30 m (null: still loading). */
    val modelReady: Boolean? = null,
    val modelHeavy: Boolean = false,
    val beside: Boolean = true,
    val error: MediaError? = null,
    /** A sub-locus can only go on the locus level (depth <= 3). */
    val canGroup: Boolean = true,
    val busy: Boolean = false,
)

/** A 3D memory object in the room (design `userContent.itemTypes.model.display`). */
data class ModelUi(
    val nodeId: String,
    val locusId: String,
    val file: String,
    /** Stands next to the plaque, always seen; else it floats over its open card. */
    val beside: Boolean,
    val yawDeg: Float,
    /** On-card models only show while their locus is open at their level. */
    val shown: Boolean,
    /** The cell index (0-based) it floats over when on a card. */
    val cell: Int = -1,
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
    val open: OpenUi? = null,
    val placing: PlacingUi? = null,
    /** v0.3: S32 / S34 - S38. */
    val content: ContentUi? = null,
    val add: AddUi? = null,
    val models: List<ModelUi> = emptyList(),
    /** The model the stick turns (placing). */
    val selectedModel: String? = null,
    /** S37: a freshly picked model to load and measure (absolute path). */
    val pendingModel: String? = null,
    val review: ReviewUi? = null,
    val summary: SummaryUi? = null,
    val recall: RecallUi? = null,
    val recallResult: RecallResultUi? = null,
    val nudge: Nudge? = null,
    val popup: Popup? = null,
    val importFiles: List<ImportFileUi> = emptyList(),
    val packs: List<PackUi> = emptyList(),
    val wiki: WikiUi? = null,
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
        get() = mode == Mode.PLACING && confirm == null && popup == null && placing?.edit == null && open == null && placing?.let { it.number <= it.total || it.free } == true
}
