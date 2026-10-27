package io.embodit.memorypalace.ui.palace.scene

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.System
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.pico.spatial.ui.foundation.gesture.SpatialTapValue
import io.embodit.memorypalace.domain.BlockReason
import io.embodit.memorypalace.domain.HeadPose
import io.embodit.memorypalace.domain.Locus
import io.embodit.memorypalace.domain.P2
import io.embodit.memorypalace.domain.PanelFacing
import io.embodit.memorypalace.domain.PlaceRules
import io.embodit.memorypalace.domain.PlaneSurface
import io.embodit.memorypalace.domain.Quat4
import io.embodit.memorypalace.domain.Spot
import io.embodit.memorypalace.domain.SurfaceKind
import io.embodit.memorypalace.domain.Vec3
import io.embodit.memorypalace.domain.Wake
import io.embodit.memorypalace.domain.flatDistance
import io.embodit.memorypalace.domain.raycast
import io.embodit.memorypalace.platform.PalaceGraph
import io.embodit.memorypalace.platform.PalaceLog
import io.embodit.memorypalace.ui.palace.LocusUi
import io.embodit.memorypalace.ui.palace.Mode
import io.embodit.memorypalace.ui.palace.NudgeKind
import io.embodit.memorypalace.ui.palace.PalaceEvent
import io.embodit.memorypalace.ui.palace.PalaceUiState
import io.embodit.memorypalace.ui.palace.Popup
import io.embodit.memorypalace.ui.palace.components.PanelSizes
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

internal object ActiveScene {
    @Volatile var scene: PalaceScene? = null
}

/** The one per-frame ECS system of the app (`registerSystem` in PalaceScreen). */
class PalaceFrameSystem : System() {
    override fun update(context: SceneUpdateContext) {
        ActiveScene.scene?.onFrame(context)
    }
}

/** Panel ids of the pool (design `budget.panels`). */
object PanelIds {
    const val POCKET = "pocket"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val TITLE = "title"
    const val NUDGE = "nudge"
    const val EDIT = "edit"
    const val CELLS = 8
    const val LABELS = 6

    fun cell(i: Int) = "cell$i"

    fun label(i: Int) = "label$i"

    val pool: List<String> = listOf(POCKET, LIBRARY, SETTINGS, TITLE, NUDGE, EDIT) + (0 until CELLS).map { cell(it) } + (0 until LABELS).map { label(it) }
}

/** What a distance label says (design `entities3d.locusDot.lod`, 1 - 3 m). */
data class LabelUi(val number: Int, val title: String, val decimetres: Int, val due: Int, val placing: Boolean, val cards: Int)

/**
 * The 3D side of the app (design `entities3d`): lantern dots, plaques, the thread of memory, the open
 * locus (title + grid of cards), the reticle and the pre-built panel pool. It starts and stops the
 * sensing (planes, head, anchors) and turns system pinches into events. Per-frame work (wake distance,
 * reticle, panel placement, slide / flip / breathing animations) happens in [onFrame], never through
 * Compose: the Compose state written here is which panel is in view, the six distance labels and the
 * distance to the next locus, each on change only. Main thread only.
 */
class PalaceScene(private val sink: (PalaceEvent) -> Unit) {

    /** Panel id -> in view. A pooled panel composes its content only while this is true (Spatial Ruler finding 3). */
    val panelShown = mutableStateMapOf<String, Boolean>()

    /** Label slot -> what it says (absent: hidden). */
    val labels = mutableStateMapOf<Int, LabelUi>()

    /** Head to the target locus, horizontal, in decimetres (-1: none). */
    val targetDecimetres = mutableIntStateOf(-1)

    /** Bumped to make `SpatialView.update` run again while panel entities are still missing. */
    val bindTick = mutableIntStateOf(0)

    private val main = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private val head = PalaceGraph.head
    private val planes = PalaceGraph.planes
    private var content: SpatialViewContent? = null
    private var contentProbe: Entity? = null
    private var stageRoot: Entity? = null
    private var rig: PanelRig? = null
    private var shared: SharedResources? = null
    private var materials: PalaceMaterials? = null
    private var meshes: PalaceMeshes? = null
    private var colliders: PlaneColliders? = null
    private val views = LinkedHashMap<String, LocusView>()
    private val threads = ArrayList<Box>()
    private val gridThreads = ArrayList<Box>()
    private var titleBoard: Box? = null
    private var titlePin: Box? = null
    private val cellBoards = ArrayList<Box>()
    private var reticle: Reticle? = null
    private var built = false
    private var attached = false
    private var paused = false
    private var buildJob: Job? = null

    private var pose = HeadPose(Vec3.ZERO, Quat4.IDENTITY)
    private var hasHead = false
    private var ui = PalaceUiState()
    private var frames = 0L
    private var lastLogMs = 0L

    private var floorY: Float? = null
    private var usable = -1
    private var lastWake: String? = null
    private var lastNear: String? = null
    private var lastAim: BlockReason? = BlockReason.NO_SURFACE
    private var aimSent = false
    private var openKey = ""
    private var openStartMs = 0L
    private val flipStart = HashMap<String, Long>()
    private var flippedIds: Set<String> = emptySet()
    private var shakeStartMs = -1L
    private var shakeTarget: String? = null

    private val pocketFollow = LazyFollow("pocket", ::pocketRest, windowDeg = POCKET_WINDOW_DEG, nearM = 0.35f, farM = 1.2f)

    // =========================================================================================
    // lifecycle
    // =========================================================================================

    fun attach(content: SpatialViewContent, attachments: SpatialViewAttachments) {
        if (attached || stageRoot != null) detach()
        val t0 = SystemClock.elapsedRealtime()
        this.content = content
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val root = Entity()
        runCatching { root.setName("stageRoot") }
        content.addEntity(root)
        val probe = Entity()
        runCatching { probe.setName("contentProbe") }
        content.addEntity(probe)
        contentProbe = probe
        // An entity added to the SpatialView is not at the Stage origin (Spatial Ruler): move the root
        // once so that its local space equals Stage space.
        val offset = root.convertPositionFrom(Vector3.ZERO, null)
        val rotation = root.convertRotationFrom(Quat.identity(), null)
        root.components[TransformComponent::class.java]?.apply {
            setPosition(offset)
            setQuaternion(rotation)
        }
        stageRoot = root
        PalaceLog.i("STAGE root offset=${PalaceLog.pos(offset.domain())}")

        val rig = PanelRig(root) { id, shown -> panelShown[id] = shown }
        this.rig = rig
        rig.world(PanelIds.POCKET, PanelSizes.POCKET_W, PanelSizes.POCKET_H, POCKET_SCALE)
        rig.world(PanelIds.LIBRARY, PanelSizes.LIBRARY_W, PanelSizes.LIBRARY_H, POCKET_SCALE)
        rig.world(PanelIds.SETTINGS, PanelSizes.SETTINGS_W, PanelSizes.SETTINGS_H, POCKET_SCALE)
        rig.world(PanelIds.TITLE, PanelSizes.TITLE_W, PanelSizes.TITLE_H, 1f)
        rig.world(PanelIds.NUDGE, PanelSizes.NUDGE_W, PanelSizes.NUDGE_H, 1f)
        rig.world(PanelIds.EDIT, PanelSizes.EDIT_W, PanelSizes.EDIT_H, 1f)
        for (i in 0 until PanelIds.CELLS) rig.world(PanelIds.cell(i), PanelSizes.CELL_W, PanelSizes.CELL_H, 1f)
        for (i in 0 until PanelIds.LABELS) rig.world(PanelIds.label(i), PanelSizes.LABEL_W, PanelSizes.LABEL_H, 1f)
        head.start()
        ActiveScene.scene = this
        attached = true
        bindPanels(attachments)
        startSensing()
        buildJob =
            scope?.launch {
                try {
                    build(root, t0)
                } catch (t: Throwable) {
                    if (t !is CancellationException) PalaceLog.e("SCENE build failed: $t", t)
                    throw t
                }
            }
        PalaceLog.i("SCENE attached")
    }

    /** Materials and meshes, then the pools; the build yields between steps (a mesh costs about a frame). */
    private suspend fun build(root: Entity, t0: Long) {
        delay(300)
        val shared = SharedResources()
        this.shared = shared
        val m = PalaceMaterials(shared)
        materials = m
        yield()
        val meshes = PalaceMeshes(shared)
        this.meshes = meshes
        yield()
        val colliders = PlaneColliders(root)
        colliders.buildBackstop()
        this.colliders = colliders
        yield()
        repeat(MAX_THREADS) { i -> threads += Box(root, meshes.cube, m.threadFuture, "thread$i").also { it.show(false) } }
        repeat(PanelIds.CELLS - 1) { i -> gridThreads += Box(root, meshes.cube, m.amber, "gridThread$i").also { it.show(false) } }
        titleBoard = Box(root, meshes.cube, m.porcelain, "titleBoard").also { it.show(false) }
        titlePin = Box(root, meshes.sphere, m.brass, "titlePin").also { it.show(false) }
        repeat(PanelIds.CELLS) { i -> cellBoards += Box(root, meshes.cube, m.porcelain, "cellBoard$i").also { it.show(false) } }
        reticle = Reticle(root, meshes, m)
        yield()
        built = true
        PalaceLog.i("SCENE built ms=${SystemClock.elapsedRealtime() - t0}")
        render(ui)
        sink(PalaceEvent.SceneReady)
    }

    /**
     * v0.2.2 (device feedback): no plane detection any more, a locus is a free spatial anchor. Sensing is the
     * anchor subscription only; an anchor that world tracking moves after a recalibration is passed on.
     */
    private fun startSensing() {
        PalaceGraph.anchors.onUpdated = { r -> main.post { if (attached) sink(PalaceEvent.AnchorMoved(r.id, r.position, r.rotation)) } }
        PalaceGraph.anchors.start()
    }

    /** Sensing restarts (S23 "allow"). */
    fun restartSensing() {
        PalaceGraph.anchors.stop()
        startSensing()
    }

    /** The Stage went to the background: stop every tracker (design `container.lifecycle`). */
    fun pause() {
        if (paused || !attached) return
        paused = true
        head.stop()
        PalaceLog.i("SCENE paused")
    }

    fun resume() {
        if (!paused || !attached) return
        paused = false
        head.start()
        startSensing()
        PalaceLog.i("SCENE resumed")
    }

    fun bindPanels(attachments: SpatialViewAttachments) {
        val rig = rig ?: return
        if (!rig.bind(attachments, PanelIds.pool, SystemClock.elapsedRealtime())) {
            main.removeCallbacks(rebind)
            main.postDelayed(rebind, 250L)
        }
    }

    private val rebind = Runnable { if (stageRoot != null) bindTick.intValue++ }

    fun detach() {
        if (!attached && stageRoot == null) return
        attached = false
        if (ActiveScene.scene === this) ActiveScene.scene = null
        main.removeCallbacksAndMessages(null)
        buildJob?.cancel()
        scope?.cancel()
        scope = null
        head.stop()
        PalaceGraph.anchors.onUpdated = null
        PalaceGraph.anchors.stop()
        panelShown.clear()
        labels.clear()
        views.values.forEach { it.destroy() }
        views.clear()
        threads.clear()
        gridThreads.clear()
        cellBoards.clear()
        titleBoard = null
        titlePin = null
        reticle = null
        runCatching { colliders?.clear() }
        colliders = null
        runCatching { rig?.release() }
        runCatching { stageRoot?.destroy(true) }
        runCatching { contentProbe?.destroy(true) }
        shared?.close()
        shared = null
        materials = null
        meshes = null
        stageRoot = null
        contentProbe = null
        rig = null
        content = null
        built = false
        hasHead = false
        usable = -1
        pocketFollow.reset()
        PalaceLog.i("SCENE detached")
    }

    // =========================================================================================
    // state -> entities
    // =========================================================================================

    fun render(state: PalaceUiState) {
        val before = ui
        ui = state
        val rig = rig ?: return
        rig[PanelIds.POCKET]?.show()
        if (state.popup != null && state.popup != Popup.SETTINGS) rig[PanelIds.LIBRARY]?.show() else rig[PanelIds.LIBRARY]?.hide()
        if (state.popup == Popup.SETTINGS) rig[PanelIds.SETTINGS]?.show() else rig[PanelIds.SETTINGS]?.hide()
        if (!built) return
        val root = stageRoot ?: return
        val m = materials ?: return
        val meshes = meshes ?: return
        // locus views: create / destroy
        val ids = state.loci.filter { it.found }.map { it.nodeId }.toSet()
        for (id in views.keys.filter { it !in ids }) views.remove(id)?.destroy()
        for (l in state.loci) if (l.found && l.nodeId !in views) views[l.nodeId] = LocusView(root, meshes, m, l.nodeId)
        // open locus: (re)start the slide when the locus or its level changes
        val key = state.open?.let { o -> o.locusId + "|" + o.cells.joinToString(",") { it.nodeId } + "|" + o.hidden } ?: ""
        if (key != openKey) {
            openKey = key
            openStartMs = SystemClock.elapsedRealtime()
        }
        val nowFlipped = (state.open?.cells.orEmpty() + listOfNotNull(state.open?.single)).filter { it.flipped }.map { it.nodeId }.toSet()
        for (id in nowFlipped - flippedIds) flipStart[id] = SystemClock.elapsedRealtime()
        flippedIds = nowFlipped
        if (state.nudge?.kind == NudgeKind.TOO_FAR && before.nudge != state.nudge) {
            shakeStartMs = SystemClock.elapsedRealtime()
            shakeTarget = state.nudge.targetId
        }
        if (state.open?.locusId != before.open?.locusId) lastWake = state.open?.locusId
        if (state.mode != Mode.RECALL) lastNear = null
        if (!state.aiming) {
            reticle?.hide()
            aimSent = false
        }
    }

    // =========================================================================================
    // per frame
    // =========================================================================================

    fun onFrame(context: SceneUpdateContext) {
        val root = stageRoot ?: return
        if (root.scene == null || context.scene != root.scene) return
        val now = SystemClock.elapsedRealtime()
        frames++
        head.pose()?.let {
            pose = it
            hasHead = it.position.length > 1e-3f
        }
        val rig = rig ?: return
        rig.onFrame(now)
        rig.stowBehind(pose)
        if (!attached || paused) return
        if (built && hasHead) {
            wake()
            drawLoci(now)
            drawThreads()
            drawOpen(now)
            drawLabels()
            aim()
        }
        if (hasHead) placePanels(now)
        rig.faceViewer(pose.position)
        if (now - lastLogMs > LOG_MS) {
            lastLogMs = now
            PalaceLog.i("FRAME n=$frames head=${PalaceLog.pos(pose.position)} fwd=${PalaceLog.pos(pose.forward)} loci=${views.size} planes=${planes.all.size} usable=$usable floor=${floorY}")
        }
    }

    private fun locus(l: LocusUi) = Locus(l.nodeId, ui.palaceId, "", l.number, l.position, l.normal, l.surface)

    private fun foundLoci(): List<LocusUi> = ui.loci.filter { it.found }

    /** Wake rule (design `wakeDistance`, T3): only in review / recall; reported on change. */
    private fun wake() {
        val session = ui.mode == Mode.REVIEW || ui.mode == Mode.RECALL
        val loci = foundLoci()
        if (session && ui.popup == null && ui.confirm == null) {
            val pick = Wake.pick(loci.map { locus(it) }, ui.wakeCandidates, ui.open?.locusId, pose)
            if (pick != lastWake) {
                lastWake = pick
                sink(PalaceEvent.LocusWoke(pick))
            }
        }
        if (ui.mode == Mode.RECALL) {
            val other =
                loci.filter { it.nodeId !in ui.wakeCandidates && it.tone != LocusUi.Tone.DONE }
                    .filter { flatDistance(it.position, pose.position) <= Wake.OPEN_M && Wake.facing(locus(it), pose.position) }
                    .minByOrNull { flatDistance(it.position, pose.position) }?.nodeId
            if (other != lastNear) {
                lastNear = other
                sink(PalaceEvent.NearOther(other))
            }
        }
        val target = ui.targetLocus?.let { id -> loci.firstOrNull { it.nodeId == id } }
        val dm = target?.let { (flatDistance(it.position, pose.position) * 10f).roundToInt() } ?: -1
        if (dm != targetDecimetres.intValue) targetDecimetres.intValue = dm
    }

    /** The plaque frame of a locus: wall loci face out of the wall; table loci lean back 30 degrees and turn to the viewer. */
    private fun frameOf(l: LocusUi): PanelFacing =
        if (l.surface == SurfaceKind.TABLE) {
            val to = pose.position - l.position
            PanelFacing(atan2(to.x, to.z), TABLE_TILT_RAD)
        } else PanelFacing(atan2(l.normal.x, l.normal.z), 0f)

    private fun drawLoci(now: Long) {
        val m = materials ?: return
        val placing = ui.mode == Mode.PLACING
        for (l in foundLoci()) {
            val v = views[l.nodeId] ?: continue
            val f = frameOf(l)
            val isOpen = ui.open?.locusId == l.nodeId
            var at = l.position
            if (shakeTarget == l.nodeId && shakeStartMs > 0 && now - shakeStartMs < SHAKE_MS) at = at + f.right * (0.012f * sin(((now - shakeStartMs) / 1000f) * 2f * PI.toFloat() * 6f))
            val breathe = if (l.tone == LocusUi.Tone.NEXT || l.tone == LocusUi.Tone.DUE) 1f + 0.18f * sin(now / 1000f * 2f * PI.toFloat() * 0.5f) else 1f
            val dotMat =
                when (l.tone) {
                    LocusUi.Tone.NEXT, LocusUi.Tone.DUE -> m.amber
                    LocusUi.Tone.DONE -> m.jade
                    LocusUi.Tone.DIM -> m.dim
                }
            v.draw(at, f, ui.palaceColor, dotMat, breathe, showDot = !isOpen, showPlaque = placing || (ui.mode == Mode.HOME && flatDistance(l.position, pose.position) <= Wake.LABEL_M), halo = l.tone == LocusUi.Tone.NEXT)
        }
    }

    private fun drawThreads() {
        val m = materials ?: return
        val loci = foundLoci().sortedBy { it.number }
        val targetIdx = loci.indexOfFirst { it.nodeId == ui.targetLocus }
        for (i in threads.indices) {
            val a = loci.getOrNull(i)
            val b = loci.getOrNull(i + 1)
            val box = threads[i]
            if (a == null || b == null || ui.mode == Mode.HOME && ui.placedCount < 2) {
                box.show(false)
                continue
            }
            val mat =
                when {
                    ui.mode == Mode.RECALL || ui.mode == Mode.RECALL_DONE ->
                        when {
                            b.tone == LocusUi.Tone.DONE -> m.jade
                            b.tone == LocusUi.Tone.NEXT -> m.amber
                            else -> m.threadFuture
                        }
                    ui.mode == Mode.REVIEW -> if (i + 1 == targetIdx) m.amber else if (targetIdx >= 0 && i + 1 < targetIdx) m.jade else m.threadFuture
                    ui.mode == Mode.REVIEW_DONE -> m.jade
                    else -> m.threadFuture
                }
            box.material(mat)
            box.rod(threadEnd(a), threadEnd(b), THREAD_M)
        }
    }

    /** Where the route thread meets a locus: its dot, or the title of the open one (not through its grid). */
    private fun threadEnd(l: LocusUi): Vec3 {
        val open = ui.open
        if (open == null || open.locusId != l.nodeId || open.cells.isEmpty()) return l.position
        return l.position + frameOf(l).up * (TITLE_RISE_M + PanelSizes.TITLE_H * DP_M / 2f)
    }

    /** Title + grid of the open locus (design `nesting.layout`), with the slide-out and the flip. */
    private fun drawOpen(now: Long) {
        val rig = rig ?: return
        val open = ui.open
        val l = open?.let { o -> foundLoci().firstOrNull { it.nodeId == o.locusId } }
        if (open == null || l == null) {
            rig[PanelIds.TITLE]?.hide()
            for (i in 0 until PanelIds.CELLS) {
                rig[PanelIds.cell(i)]?.hide()
                cellBoards.getOrNull(i)?.show(false)
            }
            gridThreads.forEach { it.show(false) }
            titleBoard?.show(false)
            titlePin?.show(false)
            return
        }
        val f = frameOf(l)
        val rot = f.rotation
        val n = f.normal
        val up = f.up
        val right = f.right
        val grid = open.cells.isNotEmpty()
        val titleAt = l.position + (if (grid) up * TITLE_RISE_M else Vec3.ZERO)
        val slide = ((now - openStartMs) / SLIDE_MS).coerceIn(0f, 1f).let { 1f - (1f - it) * (1f - it) }
        val title = rig[PanelIds.TITLE]
        title?.orient(rot)
        val titleCell = open.single
        val kx = titleCell?.let { flipScale(it.nodeId, now) } ?: 1f
        title?.show(l.position.lerp(titleAt, slide) + n * PANEL_OUT_M, kx, 1f)
        titleBoard?.place(l.position.lerp(titleAt, slide) - n * 0.002f, Vec3(PanelSizes.TITLE_W * DP_M * kx, PanelSizes.TITLE_H * DP_M, BOARD_M), rot)
        titlePin?.place(l.position.lerp(titleAt, slide) + up * (PanelSizes.TITLE_H * DP_M / 2f - 0.012f) + n * 0.012f, Vec3(PIN_M, PIN_M, PIN_M))
        val count = open.cells.size
        val centers = ArrayList<Vec3>()
        for (i in 0 until PanelIds.CELLS) {
            val slot = rig[PanelIds.cell(i)]
            val c = open.cells.getOrNull(i)
            if (c == null) {
                slot?.hide()
                cellBoards.getOrNull(i)?.show(false)
                continue
            }
            val row = i / 4
            val inRow = if (row == 0) minOf(4, count) else count - 4
            val col = i % 4
            val x = (col - (inRow - 1) / 2f) * CELL_PITCH_X
            val y = -ROW0_M - row * CELL_PITCH_Y
            val target = l.position + right * x + up * y
            val at = l.position.lerp(target, slide)
            centers += at
            val k = flipScale(c.nodeId, now)
            slot?.orient(rot)
            slot?.show(at + n * PANEL_OUT_M, k, 1f)
            cellBoards.getOrNull(i)?.place(at - n * 0.002f, Vec3(PanelSizes.CELL_W * DP_M * k, PanelSizes.CELL_H * DP_M, BOARD_M * 0.75f), rot)
        }
        // the 2 mm thread through ① ② ③ … (only once the grid has slid out)
        for (i in gridThreads.indices) {
            val a = centers.getOrNull(i)
            val b = centers.getOrNull(i + 1)
            if (a == null || b == null || slide < 1f) gridThreads[i].show(false) else gridThreads[i].rod(a + n * 0.001f, b + n * 0.001f, GRID_THREAD_M)
        }
    }

    /** Horizontal scale of a card turning over (about its vertical axis, 0.35 s): edge-on half way. */
    private fun flipScale(id: String, now: Long): Float {
        val t0 = flipStart[id] ?: return 1f
        val t = (now - t0) / FLIP_MS
        if (t >= 1f) {
            flipStart.remove(id)
            return 1f
        }
        return abs(cos(PI.toFloat() * t)).coerceAtLeast(0.02f)
    }

    /** Distance labels on the nearest six loci between 1 and 3 m (and every placed locus nearby while placing). */
    private fun drawLabels() {
        val rig = rig ?: return
        val placing = ui.mode == Mode.PLACING
        val shown =
            foundLoci().asSequence()
                .filter { it.nodeId != ui.open?.locusId }
                .map { it to flatDistance(it.position, pose.position) }
                .filter { (_, d) -> d <= Wake.LABEL_M && (placing || d > Wake.OPEN_M || ui.mode == Mode.HOME) }
                .sortedBy { it.second }
                .take(PanelIds.LABELS)
                .toList()
        for (i in 0 until PanelIds.LABELS) {
            val slot = rig[PanelIds.label(i)]
            val e = shown.getOrNull(i)
            if (e == null || ui.popup != null) {
                slot?.hide()
                if (labels.containsKey(i)) labels.remove(i)
                continue
            }
            val (l, d) = e
            val label = LabelUi(l.number, l.title, (d * 10f).roundToInt(), l.due, placing, l.cards)
            if (labels[i] != label) labels[i] = label
            val k = (LABEL_K * pose.position.distanceTo(l.position)).coerceIn(0.6f, 1.6f)
            val f = frameOf(l)
            val lift = if (placing) PanelSizes.TITLE_H * DP_M / 2f + 0.03f else 0.05f
            slot?.show(l.position + f.up * (lift + PanelSizes.LABEL_H * DP_M * k / 2f) + f.normal * 0.02f, k, k)
        }
    }

    /**
     * The reticle while placing (design `entities3d.reticle`), v0.2.2: floating 1.2 m along the gaze, where a
     * pinch would pin the locus; amber, or coral ✕ when it is too close to / too far from the other loci.
     */
    private fun aim() {
        if (!ui.aiming) return
        val r = reticle ?: return
        val (spot, block) = evaluate(pose.forward)
        r.draw(spot.position, spot.normal, block == null, PlaceRules.FREE_DISTANCE * RETICLE_K)
        if (!aimSent || block != lastAim) {
            aimSent = true
            lastAim = block
            sink(PalaceEvent.AimChanged(block))
        }
    }

    /** The free spot along [dir] from the eyes and why it cannot take a locus (null: it can). */
    private fun evaluate(dir: Vec3): Pair<Spot, BlockReason?> {
        val spot = PlaceRules.free(pose.position, dir)
        return spot to PlaceRules.checkFree(spot, foundLoci().map { it.position }, ui.loci.size)
    }

    // =========================================================================================
    // panels
    // =========================================================================================

    /** Pocket panel rest pose (design `spatialLayout.pocketPanel`): 25° left, 20° down, 0.75 m. */
    private fun pocketRest(h: HeadPose): Vec3 {
        val f = h.level()
        val l = Math.toRadians(POCKET_LEFT_DEG.toDouble()).toFloat()
        val u = Math.toRadians(-POCKET_DOWN_DEG.toDouble()).toFloat()
        val left = P2(f.z, -f.x)
        val d = (f * cos(l) + left * sin(l)).normalized()
        return h.position + d.at(0f) * (POCKET_M * cos(u)) + Vec3(0f, POCKET_M * sin(u), 0f)
    }

    private var lastPocketPressMs = -1L

    /** A control of the pocket panel was pressed: it holds still for a moment. */
    fun onPocketPointer() {
        lastPocketPressMs = SystemClock.elapsedRealtime()
    }

    private fun placePanels(now: Long) {
        val rig = rig ?: return
        val pocket = rig[PanelIds.POCKET] ?: return
        val hold = ui.popup != null || ui.confirm != null || now - lastPocketPressMs < KEY_HOLD_MS
        pocketFollow.step(pose, now, hold)?.let { pocket.moveTo(it) }
        val facing = io.embodit.memorypalace.domain.FacePanel.facing(pocket.position, pose.position, null)
        val pocketH = PanelSizes.POCKET_H * DP_M * POCKET_SCALE
        if (ui.popup != null && ui.popup != Popup.SETTINGS) {
            val h = PanelSizes.LIBRARY_H * DP_M * POCKET_SCALE
            rig[PanelIds.LIBRARY]?.moveTo(pocket.position + facing.up * (pocketH / 2f + h / 2f + 0.012f) + facing.normal * 0.02f)
        }
        if (ui.popup == Popup.SETTINGS) {
            val h = PanelSizes.SETTINGS_H * DP_M * POCKET_SCALE
            rig[PanelIds.SETTINGS]?.moveTo(pocket.position + facing.up * (pocketH / 2f + h / 2f + 0.012f) + facing.normal * 0.02f)
        }
        placeNudge()
        placeEdit()
    }

    private fun placeNudge() {
        val rig = rig ?: return
        val slot = rig[PanelIds.NUDGE] ?: return
        val n = ui.nudge
        if (n == null || n.kind == NudgeKind.PLACE_FAILED) {
            slot.hide()
            return
        }
        val l = foundLoci().firstOrNull { it.nodeId == n.targetId }
        val at: Vec3? =
            if (l != null) {
                val f = frameOf(l)
                val lift = if (ui.open?.locusId == l.nodeId && ui.open?.cells?.isNotEmpty() == true) TITLE_RISE_M + PanelSizes.TITLE_H * DP_M / 2f + 0.04f else 0.12f
                l.position + f.up * lift + f.normal * 0.03f
            } else {
                val idx = ui.open?.cells?.indexOfFirst { it.nodeId == n.targetId } ?: -1
                val ol = ui.open?.let { o -> foundLoci().firstOrNull { it.nodeId == o.locusId } }
                if (idx >= 0 && ol != null) {
                    val f = frameOf(ol)
                    val count = ui.open!!.cells.size
                    val row = idx / 4
                    val inRow = if (row == 0) minOf(4, count) else count - 4
                    ol.position + f.right * ((idx % 4 - (inRow - 1) / 2f) * CELL_PITCH_X) + f.up * (-ROW0_M - row * CELL_PITCH_Y + 0.085f) + f.normal * 0.03f
                } else null
            }
        if (at == null) slot.hide() else slot.show(at)
    }

    private fun placeEdit() {
        val rig = rig ?: return
        val slot = rig[PanelIds.EDIT] ?: return
        val id = ui.placing?.edit
        val l = id?.let { e -> foundLoci().firstOrNull { it.nodeId == e } }
        if (l == null || ui.confirm != null) {
            slot.hide()
            return
        }
        val f = frameOf(l)
        slot.show(l.position - f.up * (PanelSizes.TITLE_H * DP_M / 2f + 0.04f) + f.normal * 0.02f)
    }

    // =========================================================================================
    // input: system pinches
    // =========================================================================================

    /** A pinch on a card or the title (Compose click inside a pooled panel): the distance check is the scene's. */
    fun cellTapped(id: String) {
        sink(PalaceEvent.CellTapped(id, openDistance()))
    }

    fun titleTapped() {
        sink(PalaceEvent.TitleTapped(openDistance()))
    }

    private fun openDistance(): Float {
        val l = ui.open?.let { o -> foundLoci().firstOrNull { it.nodeId == o.locusId } } ?: return Float.MAX_VALUE
        return flatDistance(l.position, pose.position)
    }

    fun onSpatialTap(value: SpatialTapValue) {
        val target = value.targetEntity
        val name = runCatching { target?.getName() }.getOrNull() ?: "none"
        val stage = toStage(value.position.x, value.position.y, value.position.z)
        val locusId = target?.let { t -> views.entries.firstOrNull { it.value.owns(t) }?.key }
        when {
            locusId != null -> {
                val l = foundLoci().firstOrNull { it.nodeId == locusId } ?: return
                val d = flatDistance(l.position, pose.position)
                PalaceLog.i("TAP locus id=$locusId d=${String.format(Locale.US, "%.2f", d)}")
                sink(PalaceEvent.LocusTapped(locusId, d))
            }
            target != null && colliders?.isRoom(target) == true -> {
                PalaceLog.i("TAP room target=$name stage=${PalaceLog.pos(stage)}")
                if (ui.mode != Mode.PLACING) return
                // the pinch hit the far shell: the locus floats 1.2 m along that direction (the gaze, or the ray)
                val dir = stage?.let { it - pose.position }?.takeIf { it.length > 0.1f } ?: pose.forward
                val (spot, block) = evaluate(dir)
                sink(PalaceEvent.PlaceTapped(spot, block))
            }
            else -> PalaceLog.i("TAP other target=$name stage=${PalaceLog.pos(stage)}")
        }
    }

    /** View-local pixels of a spatial gesture -> Stage metres (Spatial Ruler). */
    private fun toStage(x: Float, y: Float, z: Float): Vec3? {
        val c = content ?: return null
        val probe = contentProbe ?: return null
        return runCatching {
                val local = c.convertPosition(Vector3(x, y, z), ViewCoordinateSpace.Local, c.localSpatialCoordinateSpace)
                probe.convertPositionTo(local, null).domain()
            }
            .getOrNull()
    }

    // =========================================================================================
    // debug seams
    // =========================================================================================

    fun headPose(): HeadPose? = if (hasHead) pose else null

    fun floor(): Float = floorY ?: (pose.position.y - DEFAULT_EYE_M)

    /** Debug: what a pinch on the head ray would pin (S07 / S08 hooks). */
    fun debugAim(): Pair<Spot?, BlockReason?> = evaluate(pose.forward)

    fun poseLine(): String =
        String.format(Locale.US, "POSE hmd pos=%s %s fwd=%s valid=%s support=%s frames=%d floor=%s", PalaceLog.pos(pose.position), PalaceLog.quat(pose.rotation), PalaceLog.pos(pose.forward), hasHead, head.supportState(), frames, floorY)

    fun panelLines(): List<String> = rig?.dump() ?: emptyList()

    fun planeLines(): List<String> =
        planes.all.map { s -> String.format(Locale.US, "PLANE %s usable=%s center=%s normal=%s", s.label, PlaceRules.usable(s), PalaceLog.pos(s.centroid), PalaceLog.pos(s.normal)) }

    fun parkPanels() = pocketFollow.reset()

    companion object {
        const val POCKET_SCALE = 0.75f
        const val POCKET_LEFT_DEG = 25f
        const val POCKET_DOWN_DEG = 20f
        const val POCKET_M = 0.75f
        const val POCKET_WINDOW_DEG = 35f
        const val KEY_HOLD_MS = 600L
        const val TITLE_RISE_M = 0.12f
        const val CELL_PITCH_X = 0.19f
        const val CELL_PITCH_Y = 0.13f
        const val ROW0_M = 0.04f
        const val PANEL_OUT_M = 0.008f
        const val BOARD_M = 0.008f
        const val PIN_M = 0.012f
        const val THREAD_M = 0.004f
        const val GRID_THREAD_M = 0.002f
        const val SLIDE_MS = 250f
        const val FLIP_MS = 350f
        const val SHAKE_MS = 350L
        const val LABEL_K = 0.45f
        const val RETICLE_K = 0.5f
        const val TABLE_TILT_RAD = 0.5235988f
        const val DEFAULT_EYE_M = 1.6f
        const val MAX_THREADS = 19
        const val LOG_MS = 5_000L
    }
}

/** One pinned locus: lantern dot (+ halo), porcelain plaque with deck-colour band and brass pin, a pinch target. */
internal class LocusView(parent: Entity, meshes: PalaceMeshes, private val m: PalaceMaterials, val id: String) {
    private val group = Entity()
    private val dot: Box
    private val halo: Box
    private val plaque: Box
    private val band: Box
    private val pin: Box
    private val collider = Entity()
    private var deck = -1

    init {
        runCatching { group.setName("locus:$id") }
        parent.addChild(group)
        dot = Box(group, meshes.sphere, m.amber, "dot:$id")
        halo = Box(group, meshes.ring, m.halo, "halo:$id")
        plaque = Box(group, meshes.cube, m.porcelain, "plaque:$id")
        band = Box(group, meshes.cube, m.deck(0), "band:$id")
        pin = Box(group, meshes.sphere, m.brass, "pin:$id")
        runCatching { collider.setName("locusHit:$id") }
        collider.components.set(CollisionComponent(collisionShape = listOf(ShapeResource.createBox(Vector3(0.24f, 0.16f, 0.05f))), physicsMaterial = PhysicsMaterialResource()))
        collider.components.set(InteractableComponent())
        group.addChild(collider)
    }

    fun owns(entity: Entity): Boolean = entity.id == collider.id || entity.id == dot.entity.id || entity.id == plaque.entity.id

    fun draw(at: Vec3, f: PanelFacing, deckColor: Int, dotMaterial: com.pico.spatial.core.ecs.resource.Material, breathe: Float, showDot: Boolean, showPlaque: Boolean, halo: Boolean) {
        val rot = f.rotation
        group.setLocal(position = at)
        collider.setLocal(rotation = rot)
        if (deckColor != deck) {
            deck = deckColor
            band.material(m.deck(deckColor))
        }
        if (showPlaque) {
            val w = 0.22f
            val h = 0.14f
            plaque.place(Vec3.ZERO, Vec3(w, h, 0.008f), rot)
            band.place(f.right * (-w / 2f + 0.003f) + f.normal * 0.0045f, Vec3(0.006f, h, 0.001f), rot)
            pin.place(f.up * (h / 2f - 0.012f) + f.normal * 0.01f, Vec3(0.012f, 0.012f, 0.012f))
        } else {
            plaque.show(false)
            band.show(false)
            pin.show(false)
        }
        val dotAt = if (showPlaque) f.normal * 0.03f else f.normal * 0.025f
        if (showDot) {
            val k = 0.04f * breathe
            dot.place(dotAt, Vec3(k, k, k))
            dot.material(dotMaterial)
            if (halo) this.halo.place(dotAt, Vec3(0.075f * breathe, 0.075f * breathe, 1f), rot) else this.halo.show(false)
        } else {
            dot.show(false)
            this.halo.show(false)
        }
    }

    fun destroy() {
        runCatching { group.destroy(true) }
    }
}

/** The placing reticle: a 6 cm ring, amber where a locus can go, coral with a ✕ where it cannot. */
internal class Reticle(parent: Entity, meshes: PalaceMeshes, private val m: PalaceMaterials) {
    private val ring = Box(parent, meshes.ring, m.amber, "reticle").also { it.show(false) }
    private val cross = List(2) { Box(parent, meshes.cube, m.coral, "reticleX$it").also { b -> b.show(false) } }

    fun draw(at: Vec3, normal: Vec3, ok: Boolean, k: Float) {
        val rot = Quat4.fromTo(Vec3(0f, 0f, 1f), normal)
        ring.material(if (ok) m.amber else m.coral)
        ring.place(at, Vec3(0.06f * k, 0.06f * k, 1f), rot)
        if (ok) cross.forEach { it.show(false) }
        else {
            val u = rot.rotate(Vec3.RIGHT)
            val v = rot.rotate(Vec3.UP)
            val s = 0.02f * k
            cross[0].rod(at - (u + v) * s, at + (u + v) * s, 0.004f * k)
            cross[1].rod(at - (u - v) * s, at + (u - v) * s, 0.004f * k)
        }
    }

    fun hide() {
        ring.show(false)
        cross.forEach { it.show(false) }
    }
}

/**
 * Lazy follow (Spatial Ruler T11): the panel rests where it is; once its centre has been more than
 * [windowDeg] off the view direction, or nearer than [nearM] or farther than [farM], for 0.5 s, it
 * slides (0.4 s, ease-in-out cubic) to the rest pose of that moment (design `pocketPanel`).
 */
internal class LazyFollow(private val name: String, private val rest: (HeadPose) -> Vec3, private val windowDeg: Float, private val nearM: Float, private val farM: Float) {
    private var at: Vec3? = null
    private var from = Vec3.ZERO
    private var to = Vec3.ZERO
    private var slideStartMs = NEVER
    private var outSinceMs = NEVER
    private var lastWritten: Vec3? = null

    fun reset() {
        at = null
        slideStartMs = NEVER
        outSinceMs = NEVER
        lastWritten = null
    }

    fun step(head: HeadPose, nowMs: Long, hold: Boolean): Vec3? {
        val current = at
        if (current == null) {
            val p = rest(head)
            at = p
            PalaceLog.i("FOLLOW $name park pos=${PalaceLog.pos(p)}")
            return write(p)
        }
        if (slideStartMs != NEVER) {
            val q = ((nowMs - slideStartMs) / SLIDE_MS).coerceIn(0f, 1f)
            val e = if (q < 0.5f) 4f * q * q * q else 1f - (-2f * q + 2f).let { it * it * it } / 2f
            val p = from.lerp(to, e)
            if (q >= 1f) {
                slideStartMs = NEVER
                at = to
            }
            return write(p)
        }
        val toPanel = current - head.position
        val dist = toPanel.length
        val away = if (dist < 1e-3f) 0f else Math.toDegrees(kotlin.math.acos((toPanel / dist).dot(head.forward).coerceIn(-1f, 1f)).toDouble()).toFloat()
        // the rest pose itself sits ~32° off the view direction: the window is measured from there
        val restAway = Math.toDegrees(kotlin.math.acos(((rest(head) - head.position).normalized()).dot(head.forward).coerceIn(-1f, 1f)).toDouble()).toFloat()
        val out = away - restAway > windowDeg || dist < nearM || dist > farM
        if (!out || hold) {
            outSinceMs = NEVER
        } else if (outSinceMs == NEVER) {
            outSinceMs = nowMs
        } else if (nowMs - outSinceMs >= OUT_MS) {
            from = current
            to = rest(head)
            slideStartMs = nowMs
            outSinceMs = NEVER
            PalaceLog.i(String.format(Locale.US, "FOLLOW %s slide angle=%.1f dist=%.2f to=%s", name, away, dist, PalaceLog.pos(to)))
        }
        return write(current)
    }

    private fun write(p: Vec3): Vec3? {
        if (p == lastWritten) return null
        lastWritten = p
        return p
    }

    companion object {
        private const val NEVER = -1L
        private const val OUT_MS = 500L
        private const val SLIDE_MS = 400f
    }
}
