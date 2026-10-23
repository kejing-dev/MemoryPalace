package io.embodit.memorypalace.ui.palace

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.embodit.memorypalace.data.AnchorGateway
import io.embodit.memorypalace.data.AnchorOutcome
import io.embodit.memorypalace.data.LanguagePref
import io.embodit.memorypalace.data.Library
import io.embodit.memorypalace.data.PalaceStore
import io.embodit.memorypalace.domain.BlockReason
import io.embodit.memorypalace.domain.Days
import io.embodit.memorypalace.domain.ImportOutcome
import io.embodit.memorypalace.domain.Importer
import io.embodit.memorypalace.domain.Locus
import io.embodit.memorypalace.domain.Node
import io.embodit.memorypalace.domain.PalaceTree
import io.embodit.memorypalace.domain.Quat4
import io.embodit.memorypalace.domain.Recall
import io.embodit.memorypalace.domain.RecallStep
import io.embodit.memorypalace.domain.Review
import io.embodit.memorypalace.domain.Sm2
import io.embodit.memorypalace.domain.Spot
import io.embodit.memorypalace.domain.Srs
import io.embodit.memorypalace.domain.Vec3
import io.embodit.memorypalace.domain.Wake
import io.embodit.memorypalace.platform.PalaceGraph
import io.embodit.memorypalace.platform.PalaceLog
import io.embodit.memorypalace.platform.PickerActivity
import java.io.File
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** One-shot effects the screen carries out (they need the platform). */
sealed interface PalaceEffect {
    data object RestartSensing : PalaceEffect

    data object OpenPicker : PalaceEffect
}

/**
 * All behaviour of the app. Panels and the scene send [PalaceEvent]s; the state is rebuilt from the
 * model in [publish]. The scene's per-frame rules (wake distance, reticle) only report changes here,
 * so nothing in this class runs per frame. Main thread only.
 */
class PalaceViewModel(
    private val store: PalaceStore,
    private val anchors: AnchorGateway,
    private val importDir: File?,
    private val versionName: String,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(PalaceUiState())
    val state: StateFlow<PalaceUiState> = _state

    private val effectChannel = Channel<PalaceEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    // ---- model
    private var lib: Library = store.load()
    private var language: LanguagePref = store.language()
    private var phase = Phase.STARTING
    private var scanSlow = false
    private var surfaces = 0
    private var sceneReady = false

    /** Anchor id -> pose as loaded (null until the first load answered). */
    private var anchorPoses: Map<String, Pair<Vec3, Quat4>>? = null
    private var lost: Set<String> = emptySet()
    private var lostDismissed = false

    private var mode = Mode.HOME
    private var justPlaced = false
    private var popup: Popup? = null
    private var importError: io.embodit.memorypalace.domain.ImportError? = null
    private var importResult: ImportResultUi? = null
    private var importFiles: List<ImportFileUi> = emptyList()
    private var confirm: ConfirmKind? = null
    private var block: BlockReason? = null
    private var flash: Int? = null
    private var edit: String? = null
    private var busy = false
    private var review: Review? = null
    private var undo: Triple<String, Srs, Review>? = null
    private var recall: Recall? = null
    private var recallResult: RecallResultUi? = null
    private var summary: SummaryUi? = null
    private var openLocus: String? = null
    private var path: List<String> = emptyList()
    private var flipped: String? = null
    private var nudge: Nudge? = null

    private var nudgeJob: Job? = null
    private var undoJob: Job? = null
    private var laterJob: Job? = null
    private var scanJob: Job? = null
    private var lastDesignState = ""

    /** Debug: transient states (flash, nudges, import result) stay until the next state jump. */
    var holdTransients = false

    /** Debug: the reticle's reason is pinned (S07 without turning the emulator camera to the floor). */
    private var debugBlock: BlockReason? = null

    init {
        publish()
    }

    private val today: Int
        get() = Days.of(clock(), TimeZone.getDefault().getOffset(clock()).toLong())

    private val zh: Boolean
        get() = when (language) {
            LanguagePref.ZH -> true
            LanguagePref.EN -> false
            LanguagePref.SYSTEM -> Locale.getDefault().language == "zh"
        }

    private fun tree(id: String = lib.current) = PalaceTree(id, lib.nodes)

    private fun lociOf(palaceId: String = lib.current): List<Locus> = lib.loci.filter { it.palaceId == palaceId }.sortedBy { it.route }

    private fun found(l: Locus): Boolean = anchorPoses != null && l.nodeId !in lost

    private fun foundLoci(palaceId: String = lib.current) = lociOf(palaceId).filter { found(it) }

    private fun dueCards(palaceId: String = lib.current): List<Node> {
        val t = tree(palaceId)
        val d = today
        return foundLoci(palaceId).flatMap { t.cardsUnder(it.nodeId) }.filter { it.srs?.isDue(d) == true }
    }

    private fun pendingUnder(t: PalaceTree, id: String): Int {
        val r = review ?: return 0
        val p = r.pending
        return t.cardsUnder(id).count { it.id in p }
    }

    // =========================================================================================
    // events
    // =========================================================================================

    fun onEvent(e: PalaceEvent) {
        if (e !is PalaceEvent.AimChanged) PalaceLog.i("UI ${e.javaClass.simpleName}${detail(e)}")
        when (e) {
            PalaceEvent.SceneReady -> sceneReady()
            is PalaceEvent.SurfacesChanged -> surfacesChanged(e.usable, e.error)
            is PalaceEvent.AimChanged -> if (debugBlock == null && block != e.block) block = e.block else return
            is PalaceEvent.PlaceTapped -> placeTapped(e.spot, e.block)
            is PalaceEvent.LocusTapped -> locusTapped(e.nodeId, e.distance)
            is PalaceEvent.LocusWoke -> woke(e.nodeId)
            is PalaceEvent.NearOther -> nearOther(e.nodeId)
            is PalaceEvent.CellTapped -> cellTapped(e.nodeId, e.distance)
            is PalaceEvent.TitleTapped -> titleTapped(e.distance)
            PalaceEvent.ChipClicked -> popup = if (popup == Popup.LIBRARY) null else Popup.LIBRARY
            PalaceEvent.SettingsClicked -> popup = if (popup == Popup.SETTINGS) null else Popup.SETTINGS
            PalaceEvent.StartPlacing -> startPlacing()
            PalaceEvent.StartReview -> startReview()
            PalaceEvent.StartRecall -> startRecall()
            PalaceEvent.EndSession -> endSession()
            PalaceEvent.UndoPlace -> undoPlace()
            PalaceEvent.DonePlacing -> donePlacing()
            PalaceEvent.UpLevel -> upLevel()
            is PalaceEvent.Rate -> rate(e.q)
            PalaceEvent.UndoRate -> undoRate()
            is PalaceEvent.Judge -> judge(e.right)
            PalaceEvent.SummaryOk -> toHome()
            PalaceEvent.EnterAnyway -> if (phase == Phase.SCANNING) phase = Phase.READY
            PalaceEvent.GrantPermission -> {
                phase = Phase.SCANNING
                effectChannel.trySend(PalaceEffect.RestartSensing)
                loadAnchors(retry = true)
            }
            PalaceEvent.RetryLost -> loadAnchors(retry = false)
            PalaceEvent.ReplaceLost -> replaceLost()
            PalaceEvent.DeleteLocus -> if (edit != null) confirm = ConfirmKind.DELETE_LOCUS
            PalaceEvent.ConfirmYes -> confirmed()
            PalaceEvent.ConfirmNo -> {
                confirm = null
                edit = null
            }
            PalaceEvent.PopupClosed -> {
                popup = null
                importError = null
                importResult = null
            }
            is PalaceEvent.PalacePicked -> pickPalace(e.id)
            PalaceEvent.ImportClicked -> {
                refreshImportFiles()
                popup = Popup.IMPORT
                importError = null
                importResult = null
            }
            PalaceEvent.ImportBack -> {
                popup = Popup.LIBRARY
                importError = null
                importResult = null
            }
            is PalaceEvent.ImportFile -> importFile(e.name)
            PalaceEvent.PickFromFiles -> effectChannel.trySend(PalaceEffect.OpenPicker)
            PalaceEvent.ImportErrorOk -> importError = null
            is PalaceEvent.LanguagePicked -> {
                language = e.pref
                store.saveLanguage(e.pref)
            }
            PalaceEvent.Relocate -> {
                popup = null
                loadAnchors(retry = true)
            }
            PalaceEvent.TearDown -> if (lociOf().isNotEmpty()) confirm = ConfirmKind.TEAR_DOWN
        }
        publish()
    }

    private fun detail(e: PalaceEvent): String =
        when (e) {
            is PalaceEvent.LocusTapped -> " id=${e.nodeId} d=${"%.2f".format(Locale.US, e.distance)}"
            is PalaceEvent.CellTapped -> " id=${e.nodeId} d=${"%.2f".format(Locale.US, e.distance)}"
            is PalaceEvent.LocusWoke -> " id=${e.nodeId}"
            is PalaceEvent.NearOther -> " id=${e.nodeId}"
            is PalaceEvent.PlaceTapped -> " block=${e.block} at=${PalaceLog.pos(e.spot?.position)}"
            is PalaceEvent.Rate -> " q=${e.q}"
            is PalaceEvent.Judge -> " right=${e.right}"
            is PalaceEvent.SurfacesChanged -> " usable=${e.usable}"
            else -> ""
        }

    // ---- start-up -----------------------------------------------------------------------

    private fun sceneReady() {
        sceneReady = true
        if (phase == Phase.STARTING) phase = if (PalaceGraph.planes.startError != null) Phase.NO_PERMISSION else Phase.SCANNING
        scanJob?.cancel()
        scanJob =
            viewModelScope.launch {
                delay(SCAN_SLOW_MS)
                if (phase == Phase.SCANNING) {
                    scanSlow = true
                    publish()
                }
            }
        if (phase == Phase.SCANNING && surfaces > 0) phase = Phase.READY
        loadAnchors(retry = true)
    }

    private fun surfacesChanged(usable: Int, error: String?) {
        surfaces = usable
        if (error != null && phase != Phase.READY) phase = Phase.NO_PERMISSION
        else if (phase == Phase.SCANNING && usable > 0) phase = Phase.READY
    }

    /** Full load of the anchor store (design `anchorReload`): a missing anchor is retried once after 2 s. */
    fun loadAnchors(retry: Boolean) {
        viewModelScope.launch {
            when (val r = anchors.loadAll()) {
                is AnchorOutcome.Ok -> {
                    anchorPoses = r.value.associate { it.id to (it.position to it.rotation) }
                    val missing = lib.loci.filter { it.anchorId !in anchorPoses!! }.map { it.nodeId }.toSet()
                    PalaceLog.i("ANCHOR load n=${r.value.size} loci=${lib.loci.size} missing=${missing.size} retry=$retry")
                    if (missing.isNotEmpty() && retry) {
                        delay(RELOAD_RETRY_MS)
                        loadAnchors(retry = false)
                        return@launch
                    }
                    lost = missing
                    lostDismissed = false
                    // anchors no locus points to (a crash between "create" and "save", an old install) are this
                    // app's own: let them go, they count towards the room's limit
                    val used = lib.loci.map { it.anchorId }.toSet()
                    if (!busy) for (a in r.value.filter { it.id !in used }) {
                        PalaceLog.i("ANCHOR orphan removed id=${a.id.take(8)} ok=${anchors.remove(a.id) is AnchorOutcome.Ok}")
                    }
                }
                is AnchorOutcome.Failed -> {
                    PalaceLog.w("ANCHOR load failed ${r.code} ${r.message}")
                    if (r.code == ANCHOR_NO_PERMISSION) {
                        // S23: the system refuses spatial data to this app; [Allow] restarts sensing and loads again
                        phase = Phase.NO_PERMISSION
                    } else {
                        if (anchorPoses == null) anchorPoses = emptyMap()
                        lost = lib.loci.map { it.nodeId }.toSet()
                    }
                }
            }
            publish()
        }
    }

    // ---- placing ------------------------------------------------------------------------

    private fun nextUnplaced(): Node? {
        val placed = lociOf().map { it.nodeId }.toSet()
        return tree().loci.firstOrNull { it.id !in placed }
    }

    private fun startPlacing() {
        if (nextUnplaced() == null) return
        popup = null
        mode = Mode.PLACING
        block = null
        edit = null
        flash = null
        openLocus = null
    }

    private fun placeTapped(spot: Spot?, b: BlockReason?) {
        if (mode != Mode.PLACING || confirm != null || popup != null) return
        if (edit != null) {
            edit = null
            return
        }
        val next = nextUnplaced() ?: return
        if (busy) return
        if (b != null || spot == null) {
            block = b ?: BlockReason.NO_SURFACE
            return
        }
        busy = true
        viewModelScope.launch {
            val r = anchors.create(spot.position, "locus:${next.id}")
            busy = false
            when (r) {
                is AnchorOutcome.Ok -> {
                    val route = (lociOf().maxOfOrNull { it.route } ?: -1) + 1
                    // the normal is kept in the anchor's own frame: it turns with the anchor when it is found again
                    val local = r.value.rotation.conjugate().rotate(spot.normal)
                    lib = lib.copy(loci = lib.loci + Locus(next.id, lib.current, r.value.id, route, spot.position, local, spot.surface))
                    anchorPoses = (anchorPoses ?: emptyMap()) + (r.value.id to (r.value.position to r.value.rotation))
                    save()
                    PalaceLog.i("LOCUS placed id=${next.id} n=${route + 1} at=${PalaceLog.pos(spot.position)} surface=${spot.surface}")
                    flash = lociOf().size
                    laterJob?.cancel()
                    laterJob =
                        viewModelScope.launch {
                            delay(FLASH_MS)
                            if (holdTransients) return@launch
                            flash = null
                            if (mode == Mode.PLACING && nextUnplaced() == null) {
                                justPlaced = true
                                mode = Mode.HOME
                            }
                            publish()
                        }
                }
                is AnchorOutcome.Failed -> {
                    PalaceLog.w("ANCHOR create failed ${r.code} ${r.message}")
                    showNudge(Nudge(NudgeKind.PLACE_FAILED, "", null), NUDGE_LONG_MS)
                }
            }
            publish()
        }
    }

    private fun undoPlace() {
        val last = lociOf().maxByOrNull { it.route } ?: return
        removeLoci(listOf(last))
    }

    private fun removeLoci(list: List<Locus>) {
        val ids = list.map { it.nodeId }.toSet()
        lib = lib.copy(loci = lib.loci.filter { it.nodeId !in ids })
        // the route closes up: numbers after a deleted locus move forward (design `deckRule`)
        val renumbered = lociOf().mapIndexed { i, l -> l.copy(route = i) }.associateBy { it.nodeId }
        lib = lib.copy(loci = lib.loci.map { renumbered[it.nodeId] ?: it })
        lost = lost - ids
        save()
        viewModelScope.launch {
            for (l in list) {
                val r = anchors.remove(l.anchorId)
                PalaceLog.i("ANCHOR removed id=${l.nodeId} ok=${r is AnchorOutcome.Ok}")
            }
        }
    }

    private fun donePlacing() {
        mode = Mode.HOME
        edit = null
        justPlaced = lociOf().isNotEmpty()
    }

    private fun replaceLost() {
        val gone = lociOf().filter { it.nodeId in lost }
        removeLoci(gone)
        startPlacing()
    }

    private fun confirmed() {
        when (confirm) {
            ConfirmKind.TEAR_DOWN -> {
                removeLoci(lociOf())
                mode = Mode.HOME
                popup = null
                justPlaced = false
            }
            ConfirmKind.DELETE_LOCUS -> edit?.let { id -> lociOf().firstOrNull { it.nodeId == id }?.let { removeLoci(listOf(it)) } }
            ConfirmKind.END_PLACING -> donePlacing()
            null -> Unit
        }
        confirm = null
        edit = null
    }

    // ---- review ---------------------------------------------------------------------------

    private fun startReview() {
        val due = dueCards()
        if (due.isEmpty()) return
        popup = null
        justPlaced = false
        review = Review(due.map { it.id })
        mode = Mode.REVIEW
        openLocus = null
        path = emptyList()
        flipped = null
        undo = null
        PalaceLog.i("REVIEW start cards=${due.size}")
    }

    private fun startRecall() {
        val loci = foundLoci()
        if (loci.isEmpty()) return
        popup = null
        justPlaced = false
        recall = Recall(loci.map { it.nodeId })
        mode = Mode.RECALL
        openLocus = null
        path = emptyList()
        PalaceLog.i("RECALL start loci=${loci.size}")
    }

    private fun endSession() {
        if (mode == Mode.PLACING) {
            confirm = ConfirmKind.END_PLACING
            return
        }
        toHome()
    }

    private fun toHome() {
        mode = Mode.HOME
        review = null
        recall = null
        summary = null
        recallResult = null
        openLocus = null
        path = emptyList()
        flipped = null
        undo = null
        nudge = null
        laterJob?.cancel()
    }

    private fun woke(id: String?) {
        if (mode != Mode.REVIEW && mode != Mode.RECALL) return
        if (id == openLocus) return
        openLocus = id
        path = emptyList()
        flipped = null
        if (id != null) PalaceLog.i("WAKE open id=$id") else PalaceLog.i("WAKE close")
    }

    private fun nearOther(id: String?) {
        if (mode != Mode.RECALL) return
        val rc = recall ?: return
        if (id == null) {
            if (nudge?.kind == NudgeKind.GO_FIRST && !holdTransients) nudge = null
            return
        }
        val number = numberOf(id) ?: return
        recall = rc.approachedEarly(number)
        val next = rc.nextLocus?.let { numberOf(it) } ?: return
        showNudge(Nudge(NudgeKind.GO_FIRST, circled(next), id), NUDGE_LONG_MS)
    }

    private fun numberOf(locusId: String): Int? = lociOf().indexOfFirst { it.nodeId == locusId }.takeIf { it >= 0 }?.plus(1)

    private fun locusTapped(id: String, distance: Float) {
        when (mode) {
            Mode.PLACING -> if (lociOf().any { it.nodeId == id }) edit = id
            Mode.REVIEW -> {
                if (distance > Wake.OPEN_M) return tooFar(id, distance)
                val t = tree()
                if (pendingUnder(t, id) == 0) return
                if (openLocus != id) {
                    openLocus = id
                    path = emptyList()
                    flipped = null
                } else if (t[id]?.isCard == true && id in review!!.pending) flipped = id
            }
            Mode.RECALL -> {
                val rc = recall ?: return
                if (id != rc.nextLocus) {
                    numberOf(id)?.let { recall = rc.approachedEarly(it) }
                    rc.nextLocus?.let { numberOf(it) }?.let { showNudge(Nudge(NudgeKind.GO_FIRST, circled(it), id), NUDGE_LONG_MS) }
                    return
                }
                if (distance > Wake.OPEN_M) return tooFar(id, distance)
                openLocus = id
                revealOrPinch(id)
            }
            else -> Unit
        }
    }

    private fun revealOrPinch(locusId: String) {
        val rc = recall ?: return
        val t = tree()
        if (!rc.revealed) {
            recall = rc.reveal(t)
            PalaceLog.i("RECALL reveal id=$locusId")
            // a single-card locus opens at once: front and back together
            if (t[locusId]?.isCard == true) recall = recall!!.pinch(locusId).first
        } else if (t[locusId]?.isCard == true && rc.open == null) recall = rc.pinch(locusId).first
    }

    private fun tooFar(id: String, distance: Float) {
        showNudge(Nudge(NudgeKind.TOO_FAR, String.format(Locale.US, "%.1f", Wake.closer(distance)), id), NUDGE_SHORT_MS)
    }

    private fun cellTapped(id: String, distance: Float) {
        if (distance > Wake.OPEN_M) return tooFar(openLocus ?: id, distance)
        val t = tree()
        val n = t[id] ?: return
        when (mode) {
            Mode.REVIEW -> {
                val r = review ?: return
                if (n.isCard) {
                    if (id in r.pending) flipped = id
                } else if (pendingUnder(t, id) > 0 || t.children(id).isNotEmpty()) {
                    path = path + id
                    flipped = null
                    PalaceLog.i("LEVEL enter id=$id")
                }
            }
            Mode.RECALL -> {
                val rc = recall ?: return
                if (!rc.revealed) return
                val (next, ok) = rc.pinch(id)
                if (!ok) {
                    val cur = rc.current()?.nodeId
                    val idx = cur?.let { c -> t.children(t[c]?.parentId).indexOfFirst { it.id == c } + 1 } ?: 0
                    showNudge(Nudge(NudgeKind.FLIP_FIRST, circled(idx), id), NUDGE_LONG_MS)
                    return
                }
                recall = next
                PalaceLog.i("RECALL pinch id=$id open=${next.open}")
            }
            else -> Unit
        }
    }

    private fun titleTapped(distance: Float) {
        val id = openLocus ?: return
        when (mode) {
            Mode.REVIEW -> if (path.isNotEmpty()) upLevel() else locusTapped(id, distance)
            Mode.RECALL -> if (distance <= Wake.OPEN_M) revealOrPinch(id) else tooFar(id, distance)
            else -> Unit
        }
    }

    private fun upLevel() {
        if (path.isEmpty()) return
        path = path.dropLast(1)
        flipped = null
        PalaceLog.i("LEVEL up depth=${path.size}")
    }

    private fun rate(q: Int) {
        val id = flipped ?: return
        val r = review ?: return
        val node = tree()[id] ?: return
        val before = node.srs ?: Srs()
        val after = Sm2.rate(before, q, today)
        setSrs(id, after)
        undo = Triple(id, before, r)
        review = r.rate(id, q)
        flipped = null
        PalaceLog.i("RATE id=$id q=$q due=${after.dueDay} ef=${"%.2f".format(Locale.US, after.ef)} done=${review!!.done}/${review!!.total}")
        undoJob?.cancel()
        undoJob =
            viewModelScope.launch {
                delay(UNDO_MS)
                undo = null
                publish()
            }
        afterRating()
    }

    /** A level whose due cards are all rated closes after 1 s; the last card of the round leads to S16 after 3 s. */
    private fun afterRating() {
        val r = review ?: return
        laterJob?.cancel()
        if (r.finished) {
            laterJob =
                viewModelScope.launch {
                    delay(SUMMARY_DELAY_MS)
                    if (review?.finished != true) return@launch
                    summary = summaryOf(review!!)
                    mode = Mode.REVIEW_DONE
                    openLocus = null
                    PalaceLog.i("REVIEW done ${summary}")
                    publish()
                }
            return
        }
        if (path.isNotEmpty() && pendingUnder(tree(), path.last()) == 0) {
            laterJob =
                viewModelScope.launch {
                    delay(LEVEL_BACK_MS)
                    if (path.isNotEmpty() && pendingUnder(tree(), path.last()) == 0) upLevel()
                    publish()
                }
        }
    }

    private fun summaryOf(r: Review): SummaryUi {
        val t = today
        val tomorrow = foundLoci().flatMap { tree().cardsUnder(it.nodeId) }.count { it.srs?.dueDay == t + 1 }
        return SummaryUi(r.count(Sm2.REMEMBERED), r.count(Sm2.BLURRY), r.count(Sm2.FORGOT), tomorrow)
    }

    private fun undoRate() {
        val (id, before, r) = undo ?: return
        setSrs(id, before)
        review = r
        undo = null
        flipped = id
        laterJob?.cancel()
        if (mode == Mode.REVIEW_DONE) mode = Mode.REVIEW
        PalaceLog.i("RATE undo id=$id")
    }

    private fun setSrs(id: String, srs: Srs) {
        lib = lib.copy(nodes = lib.nodes.map { if (it.id == id) it.copy(srs = srs) else it })
        save()
    }

    private fun judge(right: Boolean) {
        val rc = recall ?: return
        val id = rc.open ?: return
        val node = tree()[id] ?: return
        val s = node.srs ?: Srs()
        val d = today
        // design `recallScoring`: wrong = forgot (and back into today's review); right counts once when due today
        if (!right) setSrs(id, Sm2.rate(s, Sm2.FORGOT, d).copy(dueDay = d))
        else if (s.isDue(d)) setSrs(id, Sm2.rate(s, Sm2.REMEMBERED, d))
        val next = rc.judge(right)
        recall = next
        PalaceLog.i("RECALL judge id=$id right=$right locus=${next.index}/${next.loci.size} score=${next.right}/${next.total}")
        if (next.index != rc.index) {
            openLocus = null
            path = emptyList()
        }
        if (next.finished) {
            recallResult = RecallResultUi(next.right, next.total, next.outOfOrder.sorted(), next.wrong.size)
            mode = Mode.RECALL_DONE
            openLocus = null
            PalaceLog.i("RECALL done ${recallResult}")
        }
    }

    // ---- library / import -----------------------------------------------------------------------

    private fun pickPalace(id: String) {
        if (lib.palaces.none { it.id == id }) return
        lib = lib.copy(current = id)
        save()
        popup = null
        toHome()
        justPlaced = false
        lostDismissed = false
    }

    fun refreshImportFiles() {
        val dir = importDir ?: return
        importFiles =
            (dir.listFiles()?.filter { it.isFile && it.length() > 0 } ?: emptyList()).sortedByDescending { it.lastModified() }.map { f ->
                ImportFileUi(f.name, runCatching { f.readLines().size }.getOrDefault(0))
            }
        if (PickerActivity.result != null) PickerActivity.result = null
    }

    private fun importFile(name: String) {
        val f = importDir?.let { File(it, name) } ?: return
        val bytes = runCatching { f.readBytes() }.getOrNull() ?: return
        val seed = "imp" + clock().toString(36)
        when (val r = Importer.parse(name, bytes, seed, lib.palaces.size % 6)) {
            is ImportOutcome.Ok -> {
                lib = lib.copy(palaces = lib.palaces + r.palaces, nodes = lib.nodes + r.nodes, current = r.palaces.first().id)
                save()
                importResult = ImportResultUi(r.palaces.size, r.loci, r.cards, r.depth)
                importError = null
                toHome()
                PalaceLog.i("IMPORT ok file=$name palaces=${r.palaces.size} loci=${r.loci} cards=${r.cards} depth=${r.depth}")
                laterJob =
                    viewModelScope.launch {
                        delay(IMPORT_RESULT_MS)
                        if (holdTransients) return@launch
                        if (popup == Popup.IMPORT && importResult != null) {
                            popup = null
                            importResult = null
                            publish()
                        }
                    }
            }
            is ImportOutcome.Failed -> {
                importError = r.error
                importResult = null
                PalaceLog.i("IMPORT failed file=$name error=${r.error}")
            }
        }
    }

    // =========================================================================================
    // state
    // =========================================================================================

    private fun showNudge(n: Nudge, ms: Long) {
        nudge = n
        PalaceLog.i("NUDGE ${n.kind} ${n.text} at=${n.targetId}")
        nudgeJob?.cancel()
        nudgeJob =
            viewModelScope.launch {
                delay(ms)
                if (holdTransients) return@launch
                if (nudge === n) {
                    nudge = null
                    publish()
                }
            }
    }

    private fun save() {
        runCatching { store.save(lib) }.onFailure { PalaceLog.e("SAVE failed: $it", it) }
    }

    fun publish() {
        val t = tree()
        val zh = zh
        val palace = lib.currentPalace
        val placedLoci = lociOf()
        val d = today
        val pending = review?.pending ?: emptySet()

        // the locus the line leads to
        val target =
            when (mode) {
                Mode.REVIEW -> placedLoci.firstOrNull { found(it) && t.cardsUnder(it.nodeId).any { c -> c.id in pending } }?.nodeId
                Mode.RECALL -> recall?.nextLocus
                else -> null
            }
        val candidates =
            when (mode) {
                Mode.REVIEW -> placedLoci.filter { found(it) && t.cardsUnder(it.nodeId).any { c -> c.id in pending } }.map { it.nodeId }.toSet()
                Mode.RECALL -> setOfNotNull(recall?.nextLocus)
                else -> emptySet()
            }
        if (openLocus != null && mode == Mode.REVIEW && openLocus !in candidates && flipped == null && undo == null) {
            openLocus = null
            path = emptyList()
        }

        val loci =
            placedLoci.mapIndexed { i, l ->
                val pose = anchorPoses?.get(l.anchorId)
                val node = t[l.nodeId]
                val cards = t.cardsUnder(l.nodeId)
                val due = if (mode == Mode.REVIEW) cards.count { it.id in pending } else cards.count { it.srs?.isDue(d) == true }
                val tone =
                    when (mode) {
                        Mode.REVIEW, Mode.REVIEW_DONE -> if (l.nodeId == target) LocusUi.Tone.NEXT else if (due > 0) LocusUi.Tone.DUE else if (cards.any { it.id in (review?.first?.keys ?: emptySet()) }) LocusUi.Tone.DONE else LocusUi.Tone.DIM
                        Mode.RECALL, Mode.RECALL_DONE -> {
                            val idx = recall?.loci?.indexOf(l.nodeId) ?: -1
                            val at = recall?.index ?: Int.MAX_VALUE
                            if (mode == Mode.RECALL_DONE || (idx in 0 until at)) LocusUi.Tone.DONE else if (idx == at) LocusUi.Tone.NEXT else LocusUi.Tone.DIM
                        }
                        else -> if (due > 0) LocusUi.Tone.DUE else LocusUi.Tone.DIM
                    }
                LocusUi(
                    nodeId = l.nodeId,
                    number = i + 1,
                    title = node?.label ?: "?",
                    position = pose?.first ?: l.position,
                    normal = (pose?.second?.rotate(l.normal) ?: l.normal).normalized(),
                    surface = l.surface,
                    found = found(l),
                    single = node?.isCard == true,
                    cards = cards.size,
                    due = due,
                    tone = tone,
                )
            }

        val dueNow = dueCards()
        val lostHere = placedLoci.filter { it.nodeId in lost }
        val home =
            when {
                placedLoci.isEmpty() -> HomeKind.EMPTY
                lostHere.isNotEmpty() && !lostDismissed -> HomeKind.LOST
                dueNow.isNotEmpty() -> HomeKind.DUE
                else -> HomeKind.DONE
            }

        val open = openUi(t, loci, palace.name(zh))
        val placingUi =
            if (mode == Mode.PLACING) {
                val next = nextUnplaced()
                PlacingUi(
                    number = placedLoci.size + 1,
                    placed = placedLoci.size,
                    total = t.loci.size,
                    nextTitle = next?.label ?: "",
                    nextLines = when {
                        next == null -> emptyList()
                        next.isCard -> listOf(next.front, next.back)
                        else -> t.children(next.id).map { it.label }
                    },
                    nextSingle = next?.isCard == true,
                    block = block,
                    flash = flash,
                    edit = edit,
                    canUndo = placedLoci.isNotEmpty(),
                    busy = busy,
                )
            } else null

        val reviewUi =
            review?.takeIf { mode == Mode.REVIEW }?.let { r ->
                val tl = loci.firstOrNull { it.nodeId == target }
                val level = path.lastOrNull() ?: openLocus
                ReviewUi(r.done, r.total, tl?.number, tl?.title, level?.let { pendingUnder(t, it) } ?: 0, undo != null, open?.single != null)
            }
        val recallUi =
            recall?.takeIf { mode == Mode.RECALL }?.let { rc ->
                val next = rc.nextLocus
                val cardsHere = next?.let { t.cardsUnder(it) } ?: emptyList()
                val judged = rc.steps.take(rc.step).count { it is RecallStep.Card }
                RecallUi(rc.index + 1, rc.loci.size, next?.let { t[it]?.label }, rc.revealed, judged, cardsHere.size)
            }

        val rows =
            lib.palaces.map { p ->
                val pt = tree(p.id)
                val pl = lociOf(p.id)
                PalaceRow(p.id, p.name(zh), p.color, pt.loci.size, pl.size, pt.cards.size, pt.depth, dueCards(p.id).size, p.id == lib.current)
            }

        var s =
            PalaceUiState(
                phase = phase,
                scanSlow = scanSlow,
                surfaces = surfaces,
                mode = mode,
                home = home,
                palaceId = palace.id,
                palaceName = palace.name(zh),
                palaceColor = palace.color,
                palaces = rows,
                lociTotal = t.loci.size,
                placedCount = placedLoci.size,
                cardTotal = t.cards.size,
                depth = t.depth,
                dueToday = dueNow.size,
                dueLoci = loci.count { it.found && t.cardsUnder(it.nodeId).any { c -> c.srs?.isDue(d) == true } },
                tomorrow = foundLoci().flatMap { t.cardsUnder(it.nodeId) }.count { it.srs?.dueDay == d + 1 },
                unplaced = t.loci.size - placedLoci.size,
                lostLoci = lostHere.size,
                lostCards = lostHere.sumOf { t.cardsUnder(it.nodeId).size },
                justPlaced = justPlaced,
                loci = loci,
                targetLocus = target,
                wakeCandidates = candidates,
                open = open,
                placing = placingUi,
                review = reviewUi,
                summary = summary.takeIf { mode == Mode.REVIEW_DONE },
                recall = recallUi,
                recallResult = recallResult.takeIf { mode == Mode.RECALL_DONE },
                nudge = nudge,
                popup = popup,
                importFiles = importFiles,
                importResult = importResult,
                importError = importError,
                confirm = confirm,
                confirmName = when (confirm) {
                    ConfirmKind.TEAR_DOWN -> palace.name(zh)
                    ConfirmKind.DELETE_LOCUS -> loci.firstOrNull { it.nodeId == edit }?.let { "${circled(it.number)} ${it.title}" } ?: ""
                    else -> ""
                },
                language = language,
                zh = zh,
                versionName = versionName,
            )
        s = s.copy(designState = designState(s))
        if (s.designState != lastDesignState) {
            lastDesignState = s.designState
            PalaceLog.i("STATE ${s.designState} mode=${s.mode} home=${s.home} open=${s.open?.locusId} inside=${s.open?.inside}")
        }
        _state.value = s
    }

    private fun openUi(t: PalaceTree, loci: List<LocusUi>, palaceName: String): OpenUi? {
        val id = openLocus ?: return null
        if (mode != Mode.REVIEW && mode != Mode.RECALL) return null
        val l = loci.firstOrNull { it.nodeId == id } ?: return null
        val node = t[id] ?: return null
        val badge = circled(l.number)
        val cards = t.cardsUnder(id).size
        if (mode == Mode.REVIEW) {
            val pending = review?.pending ?: emptySet()
            val rated = review?.first?.keys ?: emptySet()
            if (node.isCard) {
                return OpenUi(id, l.number, listOf(palaceName), badge, false, emptyList(), cell(node, 1, t, pending, rated), cards = 1)
            }
            val level = path.lastOrNull() ?: id
            val names = listOf(node.label) + path.mapNotNull { t[it]?.label }
            val cells = t.children(level).take(8).mapIndexed { i, c -> cell(c, i + 1, t, pending, rated) }
            return OpenUi(id, l.number, listOf(palaceName) + names, "$badge ${names.joinToString(" › ")}", path.isNotEmpty(), cells, cards = cards)
        }
        // recall
        val rc = recall ?: return null
        if (id != rc.nextLocus) return null
        if (!rc.revealed) return OpenUi(id, l.number, listOf(palaceName), badge, false, emptyList(), hidden = true, cards = cards)
        val passed = rc.steps.take(rc.step).map { it.nodeId }.toSet()
        val current = rc.current()?.nodeId
        if (node.isCard) {
            val c = CellUi(id, 1, false, node.front, node.back, lit = true, flipped = rc.open == id, done = id in passed && id !in rc.wrong, wrong = id in rc.wrong)
            return OpenUi(id, l.number, listOf(palaceName), badge, false, emptyList(), c, cards = 1)
        }
        val level = rc.level(t) ?: id
        val names = t.pathTo(level).map { it.label }
        val cells =
            t.children(level).take(8).mapIndexed { i, c ->
                val exitPassed = rc.steps.take(rc.step).any { it is RecallStep.Exit && it.nodeId == c.id }
                val done = if (c.isCard) c.id in passed && rc.open != c.id else exitPassed
                CellUi(
                    nodeId = c.id,
                    index = i + 1,
                    group = !c.isCard,
                    text = c.label,
                    back = c.back,
                    lit = c.id == current,
                    count = t.children(c.id).size,
                    faceDown = !done && rc.open != c.id,
                    flipped = rc.open == c.id,
                    done = done && c.id !in rc.wrong,
                    wrong = c.id in rc.wrong,
                )
            }
        return OpenUi(id, l.number, listOf(palaceName) + names, "$badge ${names.joinToString(" › ")}", level != id, cells, cards = cards)
    }

    private fun cell(n: Node, index: Int, t: PalaceTree, pending: Set<String>, rated: Set<String>): CellUi =
        if (n.isCard) CellUi(n.id, index, false, n.front, n.back, lit = n.id in pending, flipped = flipped == n.id, done = n.id in rated && n.id !in pending)
        else CellUi(n.id, index, true, n.title, badge = t.cardsUnder(n.id).count { it.id in pending }, count = t.children(n.id).size, lit = t.cardsUnder(n.id).any { it.id in pending })

    private fun designState(s: PalaceUiState): String =
        when {
            s.phase == Phase.NO_PERMISSION -> "S23"
            s.phase != Phase.READY -> "S01"
            s.confirm != null -> "S25"
            s.popup == Popup.SETTINGS -> "S24"
            s.popup == Popup.LIBRARY -> "S03"
            s.popup == Popup.IMPORT -> if (s.importError != null) "S05" else "S04"
            s.mode == Mode.PLACING ->
                when {
                    s.placing?.edit != null -> "S26"
                    s.placing?.flash != null -> "S08"
                    s.placing?.block != null && s.placing.block != BlockReason.NO_SURFACE -> "S07"
                    else -> "S06"
                }
            s.mode == Mode.REVIEW ->
                when {
                    s.nudge?.kind == NudgeKind.TOO_FAR -> "S11"
                    s.open?.single?.flipped == true || s.open?.cells?.any { it.flipped } == true -> "S15"
                    s.open?.single != null -> "S12"
                    s.open?.inside == true -> "S14"
                    s.open != null -> "S13"
                    else -> "S10"
                }
            s.mode == Mode.REVIEW_DONE -> "S16"
            s.mode == Mode.RECALL ->
                when {
                    s.nudge?.kind == NudgeKind.GO_FIRST || s.nudge?.kind == NudgeKind.FLIP_FIRST -> "S19"
                    s.open != null && !s.open.hidden -> "S18"
                    else -> "S17"
                }
            s.mode == Mode.RECALL_DONE -> "S20"
            s.home == HomeKind.LOST -> "S22"
            s.home == HomeKind.EMPTY -> "S02"
            s.home == HomeKind.DUE -> "S09"
            else -> "S21"
        }

    // =========================================================================================
    // debug seams (driven only by src/debug)
    // =========================================================================================

    internal val library: Library
        get() = lib

    internal val todayIndex: Int
        get() = today

    /** Pins every locus of the current palace at [spots] (tree order), replacing what was pinned; then runs [then]. */
    internal fun debugSeed(spots: List<Spot>, then: () -> Unit) {
        val old = lociOf()
        lib = lib.copy(loci = lib.loci.filter { it.palaceId != lib.current })
        lost = lost - old.map { it.nodeId }.toSet()
        val nodes = tree().loci
        viewModelScope.launch {
            for (l in old) anchors.remove(l.anchorId)
            val made = ArrayList<Locus>()
            for ((i, n) in nodes.withIndex()) {
                val spot = spots.getOrNull(i) ?: break
                when (val r = anchors.create(spot.position, "locus:${n.id}")) {
                    is AnchorOutcome.Ok -> {
                        made += Locus(n.id, lib.current, r.value.id, i, spot.position, r.value.rotation.conjugate().rotate(spot.normal), spot.surface)
                        anchorPoses = (anchorPoses ?: emptyMap()) + (r.value.id to (r.value.position to r.value.rotation))
                    }
                    is AnchorOutcome.Failed -> PalaceLog.w("CMD seed anchor failed ${r.code} ${r.message}")
                }
            }
            lib = lib.copy(loci = lib.loci + made)
            if (anchorPoses == null) anchorPoses = emptyMap()
            save()
            PalaceLog.i("CMD seeded ${made.size}/${nodes.size}")
            then()
            publish()
        }
    }

    /** Every card of the current palace due today ([due] true) or tomorrow (false). */
    internal fun debugDue(due: Boolean) {
        val ids = tree().cards.map { it.id }.toSet()
        val d = today
        lib = lib.copy(nodes = lib.nodes.map { if (it.id in ids) it.copy(srs = Srs(dueDay = if (due) d else d + 1, interval = if (due) 0 else 1, reps = if (due) 0 else 1)) else it })
        save()
        publish()
    }

    /** Only [ids] are due today; every other card of the palace tomorrow. */
    internal fun debugDueOnly(ids: Set<String>) {
        val d = today
        lib = lib.copy(nodes = lib.nodes.map { if (it.id in ids) it.copy(srs = Srs(dueDay = d)) else it })
        save()
        publish()
    }

    internal fun debugReset() {
        nudgeJob?.cancel()
        laterJob?.cancel()
        undoJob?.cancel()
        toHome()
        popup = null
        confirm = null
        edit = null
        flash = null
        block = null
        importError = null
        importResult = null
        justPlaced = false
        lostDismissed = false
        debugBlock = null
        if (phase != Phase.READY) phase = Phase.READY
        publish()
    }

    internal fun debugPhase(p: Phase) {
        phase = p
        publish()
    }

    internal fun debugPalace(id: String) {
        if (lib.palaces.any { it.id == id }) lib = lib.copy(current = id)
        publish()
    }

    /** Adds (or replaces) a palace from text, as an import of [name] would. */
    internal fun debugImportText(name: String, text: String): String? {
        val r = Importer.parse(name, text.toByteArray(), "dbg-" + name.substringBeforeLast('.'), 3)
        if (r !is ImportOutcome.Ok) return null
        val ids = r.palaces.map { it.id }.toSet()
        lib = lib.copy(palaces = lib.palaces.filter { it.id !in ids } + r.palaces, nodes = lib.nodes.filter { it.palaceId !in ids } + r.nodes, loci = lib.loci.filter { it.palaceId !in ids }, current = r.palaces.first().id)
        save()
        return r.palaces.first().id
    }

    internal fun debugMarkLost(n: Int) {
        lost = lost + lociOf().takeLast(n).map { it.nodeId }
        publish()
    }

    /** Removes the anchors of the last [n] loci but keeps their records: after a restart they are lost (S22). */
    internal fun debugDropAnchors(n: Int) {
        val list = lociOf().takeLast(n)
        viewModelScope.launch {
            for (l in list) PalaceLog.i("CMD dropAnchor ${l.nodeId} ok=${anchors.remove(l.anchorId) is AnchorOutcome.Ok}")
        }
    }

    internal fun debugBlock(b: BlockReason?) {
        debugBlock = b
        block = b
        publish()
    }

    internal fun debugPlace(spot: Spot) = onEvent(PalaceEvent.PlaceTapped(spot, null))

    internal fun debugEdit(id: String?) {
        edit = id
        publish()
    }

    internal fun debugOpen(locusId: String?, sub: String? = null) {
        openLocus = locusId
        path = if (sub != null) listOf(sub) else emptyList()
        flipped = null
        publish()
    }

    internal fun debugNudge(n: Nudge?) {
        nudge = n
        publish()
    }

    internal fun debugRecall(index: Int) {
        val rc = recall ?: return
        recall = rc.copy(index = index.coerceIn(0, rc.loci.size))
        publish()
    }

    internal fun debugImportFiles() {
        refreshImportFiles()
        publish()
    }

    internal fun debugConfirm(k: ConfirmKind?) {
        confirm = k
        publish()
    }

    /** Rates every due card of the running review (remembered, one blurry, one forgot), as a walk through the round would. */
    internal fun debugRateAll() {
        val r = review ?: return
        var i = 0
        while (true) {
            val id = review?.pending?.firstOrNull() ?: break
            flipped = id
            rate(if (i == 1) Sm2.BLURRY else if (i == 2) Sm2.FORGOT else Sm2.REMEMBERED)
            i++
            if (i > r.queue.size * 2 + 2) break
        }
        publish()
    }

    internal fun debugRecallResult(right: Int, total: Int, outOfOrder: List<Int>, wrong: Int) {
        recallResult = RecallResultUi(right, total, outOfOrder, wrong)
        mode = Mode.RECALL_DONE
        openLocus = null
        publish()
    }

    internal fun debugPopup(p: Popup?) {
        popup = p
        publish()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = context.applicationContext
            val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: ""
            return PalaceViewModel(PalaceGraph.store(app), PalaceGraph.anchors, PickerActivity.importDir(app), version) as T
        }
    }

    companion object {
        const val SCAN_SLOW_MS = 15_000L
        const val RELOAD_RETRY_MS = 2_000L
        const val FLASH_MS = 1_500L
        const val NUDGE_SHORT_MS = 1_200L
        const val NUDGE_LONG_MS = 2_500L
        const val UNDO_MS = 3_000L
        const val SUMMARY_DELAY_MS = 3_000L
        const val LEVEL_BACK_MS = 1_000L
        const val IMPORT_RESULT_MS = 4_000L
        const val ANCHOR_NO_PERMISSION = -5

        private val CIRCLED = "①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳"

        fun circled(n: Int): String = if (n in 1..20) CIRCLED[n - 1].toString() else n.toString()
    }
}
