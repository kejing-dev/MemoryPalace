package io.embodit.memorypalace.ui.palace.components

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
    Text(text = text, color = color, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/**
 * Card text (design `typography.roles.titleLarge`, at least 20 sp = 0.9° at 1 m): short fronts go larger
 * so a card reads from the edge of its 1 m; long ones down to 16 sp.
 */
@Composable
private fun cardStyle(text: String, small: Boolean = false, big: Boolean = false): TextStyle =
    when {
        small || text.length > 10 -> PalaceType.title
        text.length <= 7 -> PalaceType.card.copy(fontSize = if (big) 28.sp else 24.sp, lineHeight = if (big) 34.sp else 30.sp)
        else -> PalaceType.card
    }

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
private fun Back(cell: CellUi, recall: Boolean, width: Float, onEvent: (PalaceEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        InkText(cell.text, PalaceType.label, PalaceColors.InkSecondary, maxLines = 1)
        InkText(cell.back, cardStyle(cell.back, small = width < 240f), maxLines = 2)
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
                Porcelain(PanelSizes.TITLE_W, PanelSizes.TITLE_H, color, lit = single.lit && !single.flipped, dim = !single.lit && !single.done, name = "title", onClick = if (single.flipped) null else onTitle) {
                    if (single.flipped) Back(single, recall, PanelSizes.TITLE_W, onEvent)
                    else
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            InkText(open.title, PalaceType.title, maxLines = 1)
                            InkText(single.text, cardStyle(single.text, big = true), maxLines = 3)
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
        val tap = if (cell.flipped) null else ({ onTap(cell.nodeId) })
        val dim = !cell.lit && !cell.done && !cell.wrong && !cell.flipped
        Porcelain(PanelSizes.CELL_W, PanelSizes.CELL_H, color, lit = cell.lit && !cell.flipped, dim = dim, name = "cell_${cell.index}", onClick = tap) {
            when {
                cell.flipped -> Back(cell, recall, PanelSizes.CELL_W, onEvent)
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
                        InkText(cell.text, cardStyle(cell.text), maxLines = 2)
                        if (recall && (cell.done || cell.wrong)) InkText(cell.back, PalaceType.label, PalaceColors.InkSecondary, maxLines = 1)
                    }
            }
            if (cell.group && cell.badge > 0) {
                Box(modifier = Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(PalaceColors.Amber), contentAlignment = Alignment.Center) {
                    Text(text = cell.badge.toString(), color = PalaceColors.Ink, style = PalaceType.label, maxLines = 1)
                }
            }
            Mark(cell, Modifier.align(Alignment.TopEnd))
        }
    }
}

/** A distance label (1 - 3 m): "② 秦汉 · 2.3 m" with the due count; while placing "② 秦汉 · 3 张". */
@Composable
internal fun LabelPanel(label: LabelUi?) {
    Box(modifier = Modifier.size(PanelSizes.LABEL_W.dp, PanelSizes.LABEL_H.dp), contentAlignment = Alignment.Center) {
        if (label == null) return@Box
        Row(
            modifier = Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(PalaceColors.Porcelain).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val tail = if (label.placing) rs(R.string.card_cards, label.cards) else metres(label.decimetres)
            InkText("${circled(label.number)} ${label.title} · $tail", PalaceType.crumb, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            if (label.due > 0 && !label.placing) {
                Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(PalaceColors.Amber), contentAlignment = Alignment.Center) {
                    Text(text = label.due.toString(), color = PalaceColors.Ink, style = PalaceType.label, maxLines = 1)
                }
            }
        }
    }
}

/** The nudge sign (S11 / S19): "0.6 m closer", "Go to ③ first →", "Turn ② first". */
@Composable
internal fun NudgePanel(nudge: Nudge?) {
    Box(modifier = Modifier.size(PanelSizes.NUDGE_W.dp, PanelSizes.NUDGE_H.dp), contentAlignment = Alignment.Center) {
        if (nudge == null || nudge.kind == NudgeKind.PLACE_FAILED) return@Box
        val (text, color) =
            when (nudge.kind) {
                NudgeKind.TOO_FAR -> rs(R.string.nudge_closer, nudge.text) to PalaceColors.Coral
                NudgeKind.GO_FIRST -> rs(R.string.nudge_go_first, nudge.text) to PalaceColors.Amber
                else -> rs(R.string.nudge_flip_first, nudge.text) to PalaceColors.Amber
            }
        Box(modifier = Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(color).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(text = text, color = PalaceColors.Ink, style = PalaceType.title, maxLines = 1)
        }
    }
}

/** The edit bar under a pinned plaque while placing (S26): 🗑 delete this locus. */
@Composable
internal fun EditPanel(visible: Boolean, onEvent: (PalaceEvent) -> Unit) {
    Box(modifier = Modifier.size(PanelSizes.EDIT_W.dp, PanelSizes.EDIT_H.dp), contentAlignment = Alignment.Center) {
        if (!visible) return@Box
        Row(
            modifier = Modifier.clip(RoundedCornerShape(28.dp)).backgroundMaterial(enable = true, style = Material.Thick).then(glassRefresh()).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Secondary("edit_delete", rs(R.string.edit_delete), { onEvent(PalaceEvent.DeleteLocus) }, icon = R.drawable.ic_trash)
        }
    }
}
