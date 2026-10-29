package io.embodit.memorypalace.ui.palace.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.SpatialHoverEffectGroup
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.hover.spatialHoverEffectGroup
import com.pico.spatial.ui.foundation.hover.tween
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import io.embodit.memorypalace.R
import io.embodit.memorypalace.domain.Sm2
import io.embodit.memorypalace.ui.palace.CellUi
import io.embodit.memorypalace.ui.palace.Mode
import io.embodit.memorypalace.ui.palace.Nudge
import io.embodit.memorypalace.ui.palace.NudgeKind
import io.embodit.memorypalace.ui.palace.OpenUi
import io.embodit.memorypalace.ui.palace.PalaceEvent
import io.embodit.memorypalace.ui.palace.PalaceViewModel.Companion.circled
import io.embodit.memorypalace.ui.palace.debug.probe
import io.embodit.memorypalace.ui.palace.scene.LabelUi
import io.embodit.memorypalace.ui.theme.PalaceColors
import io.embodit.memorypalace.ui.theme.PalaceType

/** Decoded image cards (each file is <= 1024 px; a palace holds <= 120), shared by cells and peeks. */
private val bitmaps = LruCache<String, ImageBitmap>(24)

@Composable
private fun imageOf(path: String): ImageBitmap? =
    remember(path) {
        bitmaps.get(path) ?: runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()?.also { bitmaps.put(path, it) }
    }

/*
 * The porcelain faces of the room (design `panels.plaque`, `palette.plaque`): the plaque / title, the
 * child cards, the distance labels, the nudge sign and the edit bar. Cards are things, not glass: a
 * porcelain face, ink text and a 6 mm deck-colour band on the left (7.5 dp at 0.8 mm per dp).
 */

private val Band = 7.5.dp

/** A pinch on a porcelain face without a ripple (it is a thing, not a button). */
@Composable
private fun Modifier.pinch(name: String, onClick: (() -> Unit)?): Modifier {
    if (onClick == null) return this
    val source = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = source, indication = null, onClick = onClick).probe(name)
}

@Composable
private fun Porcelain(width: Float, height: Float, deck: Color, lit: Boolean, dim: Boolean, name: String, onClick: (() -> Unit)?, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier =
            Modifier
                .size(width.dp, height.dp)
                .clip(shape)
                .background(if (dim) PalaceColors.PorcelainDim else PalaceColors.Porcelain)
                .then(if (lit) Modifier.border(3.dp, PalaceColors.Amber, shape) else Modifier)
                .pinch(name, onClick),
    ) {
        Box(modifier = Modifier.width(Band).fillMaxHeight().background(deck))
        Box(modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 7.dp)) { content() }
    }
}

@Composable
private fun InkText(text: String, style: TextStyle, color: Color = PalaceColors.Ink, maxLines: Int = 2, modifier: Modifier = Modifier) {
    // QA q8-1: shrink to fit before any ellipsis
    FitText(text, style, color, modifier, maxLines = maxLines, minScale = 0.7f)
}

/**
 * Card text (design `typography.roles.titleLarge`, at least 20 sp = 0.9° at 1 m): short fronts go larger
 * so a card reads from the edge of its 1 m; long ones down to 16 sp.
 */
@Composable
private fun cardStyle(text: String, small: Boolean = false, big: Boolean = false): TextStyle =
    when {
        // a Wikipedia sentence: smaller and on more lines (lines() below)
        text.length > LONG_TEXT -> PalaceType.body.copy(fontSize = 13.sp, lineHeight = 16.sp)
        small || text.length > 10 -> PalaceType.title
        text.length <= 7 -> PalaceType.card.copy(fontSize = if (big) 28.sp else 24.sp, lineHeight = if (big) 34.sp else 30.sp)
        else -> PalaceType.card
    }

private const val LONG_TEXT = 22

/** Lines a card text may take: long sentences wrap further at their smaller size. */
private fun lines(text: String, short: Int): Int = if (text.length > LONG_TEXT) short + 2 else short

@Composable
private fun SmallButton(name: String, text: String, icon: Int?, container: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.probe(name),
        size = ButtonDefaults.Regular.copy(height = 34.dp, minWidth = 0.dp),
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 1.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = PalaceColors.Ink),
    ) {
        if (icon != null) Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(14.dp), tint = PalaceColors.Ink)
        Text(text = text, color = PalaceColors.Ink, style = PalaceType.label.copy(fontSize = 12.sp, letterSpacing = 0.sp), maxLines = 1, softWrap = false)
    }
}

/** The back of a card: small front, the answer, then ✕ / ～ / ✓ (review) or ✓ / ✕ (recall). */
@Composable
private fun Back(cell: CellUi, recall: Boolean, width: Float, onEvent: (PalaceEvent) -> Unit, browse: Boolean = false) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        InkText(cell.text, PalaceType.label, PalaceColors.InkSecondary, maxLines = 1)
        InkText(cell.back, cardStyle(cell.back, small = width < 240f), maxLines = if (cell.back.length > LONG_TEXT) 3 else 2)
        // the credit line (design panels.cardSource): 《春望》· 杜甫, 维基百科《长城》· CC BY-SA 4.0
        if (cell.source.isNotEmpty()) InkText(cell.source, PalaceType.label.copy(fontSize = 9.sp, lineHeight = 11.sp), PalaceColors.InkSecondary, maxLines = 1)
        if (browse) return@Column
        Row(modifier = Modifier.fillMaxWidth().padding(end = 0.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            val w = Modifier.weight(1f)
            if (recall) {
                SmallButton("judge_right", "✓ " + rs(R.string.judge_right), null, PalaceColors.Jade, w) { onEvent(PalaceEvent.Judge(true)) }
                SmallButton("judge_wrong", "✕ " + rs(R.string.judge_wrong), null, PalaceColors.Coral, w) { onEvent(PalaceEvent.Judge(false)) }
            } else {
                SmallButton("rate_forgot", rs(R.string.rate_forgot), null, PalaceColors.Coral, w) { onEvent(PalaceEvent.Rate(Sm2.FORGOT)) }
                SmallButton("rate_blurry", rs(R.string.rate_blurry), null, PalaceColors.Mist, w) { onEvent(PalaceEvent.Rate(Sm2.BLURRY)) }
                SmallButton("rate_remembered", rs(R.string.rate_remembered), null, PalaceColors.Jade, w) { onEvent(PalaceEvent.Rate(Sm2.REMEMBERED)) }
            }
        }
    }
}

@Composable
private fun Mark(cell: CellUi, modifier: Modifier) {
    if (cell.done || cell.wrong) {
        Box(modifier = modifier.size(20.dp).clip(CircleShape).background(if (cell.wrong) PalaceColors.Coral else PalaceColors.Jade), contentAlignment = Alignment.Center) {
            Icon(painter = painterResource(if (cell.wrong) R.drawable.ic_close else R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(14.dp), tint = PalaceColors.Ink)
        }
    }
}

/**
 * The plaque above an open locus (S12 - S18): "② 秦汉" over its grid (a pinch goes up a level), the
 * hidden "② Where is this?" of the recall, or, for a single-card locus, the card itself.
 */
@Composable
internal fun TitlePanel(open: OpenUi?, mode: Mode?, deck: Int, onTitle: () -> Unit, onEvent: (PalaceEvent) -> Unit) {
    Box(modifier = Modifier.size(PanelSizes.TITLE_W.dp, PanelSizes.TITLE_H.dp)) {
        if (open == null) return@Box
        val recall = mode == Mode.RECALL
        val single = open.single
        val color = PalaceColors.deck(deck)
        when {
            open.hidden ->
                Porcelain(PanelSizes.TITLE_W, PanelSizes.TITLE_H, color, lit = true, dim = false, name = "title", onClick = onTitle) {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                        InkText(open.title, PalaceType.due.copy(fontSize = 34.sp, lineHeight = 40.sp), maxLines = 1)
                        InkText(rs(R.string.card_where), PalaceType.card, maxLines = 1)
                        InkText(rs(R.string.card_pinch), PalaceType.label, PalaceColors.InkSecondary, maxLines = 1)
                    }
                }
            single != null ->
                Porcelain(PanelSizes.TITLE_W, PanelSizes.TITLE_H, color, lit = single.lit && !single.flipped, dim = !single.lit && !single.done, name = "title", onClick = if (single.flipped && !browse(mode)) null else onTitle) {
                    if (single.flipped) Back(single, recall, PanelSizes.TITLE_W, onEvent, browse = browse(mode))
                    else if (single.image != null) Picture(single.image, single.text)
                    else
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            InkText(open.title, PalaceType.title, maxLines = 1)
                            InkText(single.text, cardStyle(single.text, big = true), maxLines = lines(single.text, 3))
                        }
                    Mark(single, Modifier.align(Alignment.TopEnd))
                }
            else ->
                Porcelain(PanelSizes.TITLE_W, PanelSizes.TITLE_H, color, lit = false, dim = false, name = "title", onClick = onTitle) {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        InkText(open.title, if (open.title.length > 10) PalaceType.card else PalaceType.card.copy(fontSize = 24.sp, lineHeight = 30.sp), maxLines = 2)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (open.inside) Icon(painter = painterResource(R.drawable.ic_up), contentDescription = rs(R.string.cd_up), modifier = Modifier.size(18.dp), tint = PalaceColors.InkSecondary)
                            InkText(rs(R.string.card_cards, open.cards), PalaceType.label, PalaceColors.InkSecondary, maxLines = 1)
                        }
                    }
                }
        }
    }
}

/** One cell of the grid (design S13 / S14 / S15 / S18): a card, a sub-locus "周 ▸ 2", or a face-down ①. */
@Composable
internal fun CellPanel(cell: CellUi?, mode: Mode?, deck: Int, onTap: (String) -> Unit, onEvent: (PalaceEvent) -> Unit) {
    Box(modifier = Modifier.size(PanelSizes.CELL_W.dp, PanelSizes.CELL_H.dp)) {
        if (cell == null) return@Box
        val recall = mode == Mode.RECALL
        val color = PalaceColors.deck(deck)
        val browse = browse(mode)
        val tap = if (cell.flipped && !browse) null else ({ onTap(cell.nodeId) })
        if (cell.add) return@Box AddCell(cell, tap)
        val dim = !cell.lit && !cell.done && !cell.wrong && !cell.flipped
        Porcelain(PanelSizes.CELL_W, PanelSizes.CELL_H, color, lit = cell.lit && !cell.flipped && !browse, dim = dim, name = "cell_${cell.index}", onClick = tap) {
            when {
                cell.flipped -> Back(cell, recall, PanelSizes.CELL_W, onEvent, browse = browse)
                cell.image != null && !cell.faceDown -> Picture(cell.image, cell.text)
                cell.faceDown && !cell.group ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { InkText(circled(cell.index), PalaceType.due.copy(fontSize = 32.sp, lineHeight = 38.sp), maxLines = 1) }
                cell.group ->
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        InkText(circled(cell.index), PalaceType.label, PalaceColors.InkSecondary, maxLines = 1)
                        InkText(if (cell.faceDown) "▸ ${cell.count}" else "${cell.text} ▸ ${cell.count}", cardStyle(cell.text, small = true), maxLines = 2)
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                else ->
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        InkText(circled(cell.index), PalaceType.label, PalaceColors.InkSecondary, maxLines = 1)
                        InkText(cell.text, cardStyle(cell.text), maxLines = lines(cell.text, 2))
                        if (recall && (cell.done || cell.wrong)) InkText(cell.back, PalaceType.label, PalaceColors.InkSecondary, maxLines = 1)
                    }
            }
            if (cell.model && !cell.flipped) Icon(painter = painterResource(R.drawable.ic_cube), contentDescription = null, modifier = Modifier.align(Alignment.BottomEnd).size(16.dp), tint = PalaceColors.InkSecondary)
            if (cell.group && cell.badge > 0) {
                Box(modifier = Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(PalaceColors.Amber), contentAlignment = Alignment.Center) {
                    Text(text = cell.badge.toString(), color = PalaceColors.Ink, style = PalaceType.label, maxLines = 1)
                }
            }
            Mark(cell, Modifier.align(Alignment.TopEnd))
        }
    }
}

/**
 * A locus name plate (v0.3): "② 秦汉" + the due badge, never a distance; a pinch on it opens the locus.
 * Under the name row hangs the peek card (design `interactionModel.hover.peek`): transparent until the eyes
 * or the ray rest on the plate, then the system fades it in and grows it (CustomHover, `alpha` / `scale`);
 * the app never learns where the user looks and asks for no eye-tracking permission.
 */
@Composable
internal fun LabelPanel(label: LabelUi?, onTap: (String) -> Unit) {
    Column(modifier = Modifier.size(PanelSizes.LABEL_W.dp, PanelSizes.LABEL_H.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (label == null) return@Column
        val group = remember { SpatialHoverEffectGroup.obtain() }
        val open = Modifier.pinch("label_${label.number}") { onTap(label.nodeId) }
        Row(
            modifier =
                Modifier
                    .height(PanelSizes.LABEL_ROW_H.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(PalaceColors.Porcelain)
                    .spatialHoverEffectGroup(group.behavior(SpatialHoverEffectGroup.Behavior.TriggerAndResponder))
                    .spatialHoverEffect()
                    .then(open)
                    .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val tail = if (label.placing) " · " + rs(R.string.card_cards, label.cards) else ""
            InkText("${circled(label.number)} ${label.title}$tail", PalaceType.crumb, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            if (label.due > 0 && !label.placing) {
                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(PalaceColors.Amber), contentAlignment = Alignment.Center) {
                    Text(text = label.due.toString(), color = PalaceColors.Ink, style = PalaceType.label, maxLines = 1)
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        if (label.peek.isEmpty() && label.peekImage == null) return@Column
        val shape = RoundedCornerShape(14.dp)
        Box(
            modifier =
                Modifier
                    .size(PanelSizes.CELL_W.dp, (PanelSizes.LABEL_H - PanelSizes.LABEL_ROW_H - 12f).dp)
                    .spatialHoverEffectGroup(group.behavior(SpatialHoverEffectGroup.Behavior.TriggerAndResponder))
                    .spatialHoverEffect {
                        animation(tween(durationMillis = 180)) {
                            alpha(if (it.isActive) 1f else 0f)
                            scale(if (it.isActive) 1.05f else 1f)
                        }
                    }
                    .clip(shape)
                    .background(PalaceColors.Porcelain)
                    .border(2.dp, PalaceColors.Amber, shape)
                    .then(open)
                    .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (label.peekImage != null) Picture(label.peekImage, label.peek) else InkText(label.peek, cardStyle(label.peek), maxLines = lines(label.peek, 3))
        }
    }
}

/** An image card's face: the whole picture, kept in proportion, its name small underneath. */
@Composable
private fun Picture(path: String, name: String) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        val bmp = imageOf(path)
        if (bmp != null) Image(bitmap = bmp, contentDescription = name, contentScale = ContentScale.Fit, modifier = Modifier.weight(1f).fillMaxWidth())
        else Box(modifier = Modifier.weight(1f))
        if (name.isNotEmpty()) InkText(name, PalaceType.label.copy(fontSize = 10.sp, lineHeight = 12.sp), PalaceColors.InkSecondary, maxLines = 1)
    }
}

/** 「＋ 加记忆点」 (S34): the last cell of an open locus while placing. */
@Composable
private fun AddCell(cell: CellUi, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier =
            Modifier
                .size(PanelSizes.CELL_W.dp, PanelSizes.CELL_H.dp)
                .clip(shape)
                .background(PalaceColors.PorcelainDim)
                .border(3.dp, if (cell.lit) PalaceColors.Amber else PalaceColors.InkSecondary, shape)
                .spatialHoverEffect()
                .pinch("cell_add", onClick),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InkText("＋", PalaceType.due.copy(fontSize = 34.sp, lineHeight = 38.sp), maxLines = 1)
        InkText(rs(R.string.add_item), PalaceType.title, maxLines = 1)
    }
}

/** Home and placing open a locus to look through it: cards turn over and back, nothing is rated. */
private fun browse(mode: Mode?): Boolean = mode == Mode.HOME || mode == Mode.PLACING

/** The nudge sign (S11 / S19): "0.6 m closer", "Go to ③ first →", "Turn ② first". */
@Composable
internal fun NudgePanel(nudge: Nudge?) {
    Box(modifier = Modifier.size(PanelSizes.NUDGE_W.dp, PanelSizes.NUDGE_H.dp), contentAlignment = Alignment.Center) {
        if (nudge == null || nudge.kind == NudgeKind.PLACE_FAILED) return@Box
        val (text, color) =
            when (nudge.kind) {
                NudgeKind.NOTHING_DUE -> rs(R.string.nudge_nothing_due) to PalaceColors.Mist
                NudgeKind.GO_FIRST -> rs(R.string.nudge_go_first, nudge.text) to PalaceColors.Amber
                else -> rs(R.string.nudge_flip_first, nudge.text) to PalaceColors.Amber
            }
        Box(modifier = Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(color).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(text = text, color = PalaceColors.Ink, style = PalaceType.title, maxLines = 1)
        }
    }
}

/** The edit bar under a pinned plaque while placing (S26, v0.3 `editBarV03`): rename / a 3D memory object / delete. */
@Composable
internal fun EditPanel(visible: Boolean, onEvent: (PalaceEvent) -> Unit) {
    Box(modifier = Modifier.size(PanelSizes.EDIT_W.dp, PanelSizes.EDIT_H.dp), contentAlignment = Alignment.Center) {
        if (!visible) return@Box
        Row(
            modifier = Modifier.clip(RoundedCornerShape(28.dp)).backgroundMaterial(enable = true, style = Material.Thick).then(glassRefresh()).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Secondary("edit_rename", rs(R.string.edit_rename), { onEvent(PalaceEvent.RenameLocus) }, icon = R.drawable.ic_edit, height = 48.dp)
                Secondary("edit_object", rs(R.string.edit_object), { onEvent(PalaceEvent.PlaceObject) }, icon = R.drawable.ic_cube, height = 48.dp)
                Secondary("edit_delete", rs(R.string.edit_delete), { onEvent(PalaceEvent.DeleteLocus) }, icon = R.drawable.ic_trash, height = 48.dp)
            }
        }
    }
}
