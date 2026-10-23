package io.embodit.memorypalace.ui.palace.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import io.embodit.memorypalace.platform.PalaceLog
import io.embodit.memorypalace.ui.palace.PalaceEvent
import io.embodit.memorypalace.ui.palace.PalaceUiState
import io.embodit.memorypalace.ui.palace.debug.LocalProbePanel
import io.embodit.memorypalace.ui.palace.debug.Probe
import io.embodit.memorypalace.ui.palace.scene.PalaceScene
import io.embodit.memorypalace.ui.palace.scene.PanelIds

/**
 * The Stage content: one full-size SpatialView with the loci, the planes' pinch targets and the panel
 * pool. Every attachment panel is declared once with a fixed size, shown or not ([PalaceScene] parents
 * and places them). Window model: single panel with popups (design `container.windowModel`).
 */
@Composable
internal fun PalaceStage(state: PalaceUiState, scene: PalaceScene, onEvent: (PalaceEvent) -> Unit) {
    val context = LocalContext.current
    val bindTick = scene.bindTick.intValue
    SpatialView(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectSpatialTapGesture(context = context, targetedToEntity = TargetEntity.any()) { value -> scene.onSpatialTap(value) }
                },
        attachments = {
            AttachmentPanel(PanelIds.POCKET) {
                PoolPanel(PanelIds.POCKET, scene, pressed = { scene.onPocketPointer() }) { inView -> PocketPanel(state = if (inView) state else null, targetDm = scene.targetDecimetres.intValue, onEvent = onEvent) }
            }
            AttachmentPanel(PanelIds.LIBRARY) {
                PoolPanel(PanelIds.LIBRARY, scene, pressed = { scene.onPocketPointer() }) { inView -> LibraryPanel(state = if (inView) state else null, onEvent = onEvent) }
            }
            AttachmentPanel(PanelIds.SETTINGS) {
                PoolPanel(PanelIds.SETTINGS, scene, pressed = { scene.onPocketPointer() }) { inView -> SettingsPanel(state = if (inView) state else null, onEvent = onEvent) }
            }
            AttachmentPanel(PanelIds.TITLE) {
                PoolPanel(PanelIds.TITLE, scene) { inView -> TitlePanel(open = if (inView) state.open else null, mode = state.mode, deck = state.palaceColor, onTitle = { scene.titleTapped() }, onEvent = onEvent) }
            }
            for (i in 0 until PanelIds.CELLS) {
                AttachmentPanel(PanelIds.cell(i)) {
                    PoolPanel(PanelIds.cell(i), scene) { inView ->
                        CellPanel(cell = if (inView) state.open?.cells?.getOrNull(i) else null, mode = state.mode, deck = state.palaceColor, onTap = { scene.cellTapped(it) }, onEvent = onEvent)
                    }
                }
            }
            for (i in 0 until PanelIds.LABELS) {
                AttachmentPanel(PanelIds.label(i)) {
                    PoolPanel(PanelIds.label(i), scene) { inView -> LabelPanel(label = if (inView) scene.labels[i] else null) }
                }
            }
            AttachmentPanel(PanelIds.NUDGE) {
                PoolPanel(PanelIds.NUDGE, scene) { inView -> NudgePanel(nudge = if (inView) state.nudge else null) }
            }
            AttachmentPanel(PanelIds.EDIT) {
                PoolPanel(PanelIds.EDIT, scene) { inView -> EditPanel(visible = inView && state.placing?.edit != null, onEvent = onEvent) }
            }
        },
        update = { _, attachments ->
            // reading the tick makes this block run again while panel entities are still missing
            @Suppress("UNUSED_EXPRESSION") bindTick
            scene.bindPanels(attachments)
        },
        initial = { content, attachments -> scene.attach(content, attachments) },
    )
}

/**
 * Root of a pooled panel: [content] gets `inView` (the panel has been moved into view; a pooled panel
 * draws only then, Spatial Ruler finding 3). Debug builds log every press as `PTR panel=<id> ev=`.
 */
@Composable
private fun PoolPanel(id: String, scene: PalaceScene, pressed: (() -> Unit)? = null, content: @Composable (inView: Boolean) -> Unit) {
    val inView = scene.panelShown[id] == true
    val logged = Probe.enabled
    val watcher =
        if (logged || pressed != null) {
            Modifier.pointerInput(id, logged) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Move) continue
                        if (event.type == PointerEventType.Press) pressed?.invoke()
                        if (logged) {
                            val p = event.changes.firstOrNull()?.position
                            PalaceLog.i(String.format(java.util.Locale.US, "PTR panel=%s ev=%s x=%.1f y=%.1f", id, event.type, p?.x ?: 0f, p?.y ?: 0f))
                        }
                    }
                }
            }
        } else Modifier
    CompositionLocalProvider(LocalProbePanel provides id) {
        Box(modifier = watcher) { content(inView) }
    }
}
