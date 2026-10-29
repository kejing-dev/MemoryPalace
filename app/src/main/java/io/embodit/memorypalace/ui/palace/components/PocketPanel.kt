package io.embodit.memorypalace.ui.palace.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonChip
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.ChipsDefaults
import com.pico.spatial.ui.design.CircularProgressDefaults
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.LinearProgressIndicator
import com.pico.spatial.ui.design.NumberBadge
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import io.embodit.memorypalace.R
import io.embodit.memorypalace.domain.BlockReason
import io.embodit.memorypalace.ui.palace.ConfirmKind
import io.embodit.memorypalace.ui.palace.HomeKind
import io.embodit.memorypalace.ui.palace.Mode
import io.embodit.memorypalace.ui.palace.NudgeKind
import io.embodit.memorypalace.ui.palace.PalaceEvent
import io.embodit.memorypalace.ui.palace.PalaceUiState
import io.embodit.memorypalace.ui.palace.PalaceViewModel.Companion.circled
import io.embodit.memorypalace.ui.palace.Phase
import io.embodit.memorypalace.ui.palace.debug.probe
import io.embodit.memorypalace.ui.theme.PalaceColors
import io.embodit.memorypalace.ui.theme.PalaceType

/**
 * The pocket panel (design `panels.pocket`), the one resident panel, lower left in view: home (palace
 * chip, the big number, one primary button, at most two icon keys), the session bar of placing /
 * review / recall (title, breadcrumb, one line), results, and the inline confirm strip. Density cap:
 * one primary button + three icon keys + two lines.
 */
@Composable
internal fun PocketPanel(state: PalaceUiState?, onEvent: (PalaceEvent) -> Unit) {
    Column(
        modifier =
            Modifier
                .size(PanelSizes.POCKET_W.dp, PanelSizes.POCKET_H.dp)
                .clip(RoundedCornerShape(28.dp))
                .backgroundMaterial(enable = true, style = Material.Regular)
                .then(glassRefresh())
                .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state == null) return@Column
        when {
            state.phase == Phase.NO_PERMISSION -> NoPermission(onEvent)
            state.phase != Phase.READY -> Scanning(state, onEvent)
            state.confirm != null -> Confirm(state, onEvent)
            state.mode == Mode.PLACING -> Placing(state, onEvent)
            state.mode == Mode.REVIEW -> Reviewing(state, onEvent)
            state.mode == Mode.REVIEW_DONE -> Summary(state, onEvent)
            state.mode == Mode.RECALL -> Recalling(state, onEvent)
            state.mode == Mode.RECALL_DONE -> RecallResult(state, onEvent)
            else -> Home(state, onEvent)
        }
    }
}

private val ink: Color
    @Composable get() = PicoTheme.colorScheme.labelPrimary

private val ink2: Color
    @Composable get() = PicoTheme.colorScheme.labelSecondary

@Composable
private fun Line(text: String, color: Color = ink2, style: androidx.compose.ui.text.TextStyle = PalaceType.body, maxLines: Int = 1, modifier: Modifier = Modifier) {
    FitText(text, style, color, modifier, maxLines = maxLines, minScale = 0.7f)
}

@Composable
internal fun Primary(name: String, text: String, onClick: () -> Unit, container: Color = PicoTheme.colorScheme.interaction, content: Color = PalaceColors.Ink, icon: Int? = null, enabled: Boolean = true, height: Dp = 48.dp) {
    Button(
        onClick = onClick,
        modifier = Modifier.probe(name),
        size = ButtonDefaults.Regular.copy(height = height, minWidth = 96.dp),
        shape = CircleShape,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        if (icon != null) {
            Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp), tint = content)
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text = text, color = content, style = PalaceType.title, maxLines = 1)
    }
}

@Composable
internal fun Secondary(name: String, text: String, onClick: () -> Unit, icon: Int? = null, height: Dp = 40.dp) =
    Primary(name, text, onClick, container = PicoTheme.colorScheme.fillTertiary, content = PicoTheme.colorScheme.labelPrimary, icon = icon, height = height)

@Composable
internal fun Key(name: String, icon: Int, desc: String, onClick: () -> Unit, enabled: Boolean = true, badge: Int = 0, size: Dp = 48.dp, selected: Boolean = false) {
    Box {
        IconButton(
            onClick = onClick,
            modifier = Modifier.probe(name),
            colors = IconButtonDefaults.iconButtonColors(containerColor = if (selected) PicoTheme.colorScheme.fillSecondary else PicoTheme.colorScheme.fillTertiary, contentColor = ink),
            size = IconButtonDefaults.iconButtonSize(width = size, height = size),
            enabled = enabled,
        ) {
            Icon(painter = painterResource(icon), contentDescription = desc, modifier = Modifier.size(22.dp), tint = if (enabled) ink else PicoTheme.colorScheme.labelQuaternary)
        }
        if (badge > 0) NumberBadge(Modifier.align(Alignment.TopEnd), badge)
    }
}

/** Palace chip (design `spatialUiMap.deckChip`): colour dot + name; opens the library. */
@Composable
private fun PalaceChip(state: PalaceUiState, onEvent: (PalaceEvent) -> Unit, modifier: Modifier = Modifier) {
    ButtonChip(
        label = {
            // design-style: inherited-content-color ButtonChip
            // QA q8-1: long palace names shrink instead of ending in an ellipsis
            FitText(state.palaceName, PalaceType.title, PicoTheme.colorScheme.labelPrimary, maxLines = 2, minScale = 0.7f, oneLineFirst = true)
        },
        onClick = { onEvent(PalaceEvent.ChipClicked) },
        modifier = modifier.probe("chip"),
        leadingIcon = { Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(PalaceColors.deck(state.palaceColor))) },
        colors = ChipsDefaults.chipColors(contentColor = PicoTheme.colorScheme.labelPrimary, backgroundColor = PicoTheme.colorScheme.fillTertiary),
    )
}

/**
 * v0.3.2 (device feedback 「设置图标有缺陷」, profile D23): the settings key is a chip like the palace chip beside it,
 * Material Symbols gear + the word 「设置」, 20 dp like the icons of the labelled buttons.
 */
@Composable
private fun SettingsChip(selected: Boolean, onEvent: (PalaceEvent) -> Unit) {
    ButtonChip(
        label = {
            // design-style: inherited-content-color ButtonChip
            Text(text = rs(R.string.cd_settings), style = PalaceType.title, maxLines = 1, softWrap = false)
        },
        onClick = { onEvent(PalaceEvent.SettingsClicked) },
        modifier = Modifier.probe("settings"),
        leadingIcon = { Icon(painter = painterResource(R.drawable.ic_settings), contentDescription = null, modifier = Modifier.size(20.dp), tint = PicoTheme.colorScheme.labelPrimary) },
        colors = ChipsDefaults.chipColors(contentColor = PicoTheme.colorScheme.labelPrimary, backgroundColor = if (selected) PicoTheme.colorScheme.fillSecondary else PicoTheme.colorScheme.fillTertiary),
    )
}

@Composable
private fun HomeTop(state: PalaceUiState, onEvent: (PalaceEvent) -> Unit) {
    // QA q10-1: the palace chip takes all the width the settings chip leaves (no spacer sharing it)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        PalaceChip(state, onEvent, Modifier.weight(1f, fill = false).padding(end = 8.dp))
        SettingsChip(state.popup == io.embodit.memorypalace.ui.palace.Popup.SETTINGS, onEvent)
    }
}

@Composable
private fun Big(number: String, line1: String, color: Color = ink) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = number, color = color, style = PalaceType.due, maxLines = 1)
        Text(text = line1, color = ink, style = PalaceType.title, maxLines = 2)
    }
}

@Composable
private fun ColumnScope.Home(state: PalaceUiState, onEvent: (PalaceEvent) -> Unit) {
    HomeTop(state, onEvent)
    Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
        when (state.home) {
            HomeKind.EMPTY -> {
                Big("${state.placedCount} / ${state.lociTotal}", rs(R.string.home_placed_of))
                Line(rs(R.string.home_cards, state.cardTotal, state.depth))
            }
            HomeKind.DUE -> {
                Big(state.dueToday.toString(), rs(R.string.home_due_cards))  // ink, not amber: amber on glass is ~1.3:1 (QA v0.3 D13)
                Line(if (state.justPlaced) rs(R.string.home_walk_hint) else rs(R.string.home_due_loci, state.dueLoci), maxLines = 2)
            }
            HomeKind.DONE -> {
                Big("✓", rs(R.string.home_done), PalaceColors.Jade)
                Line(rs(R.string.home_tomorrow, state.tomorrow))
            }
            HomeKind.LOST -> {
                Big(state.lostLoci.toString(), rs(R.string.lost_line, state.lostCards), PalaceColors.Coral)
                Line(rs(R.string.lost_hint))
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state.home) {
            HomeKind.EMPTY -> Primary("start_placing", rs(R.string.home_start_placing), { onEvent(PalaceEvent.StartPlacing) }, icon = R.drawable.ic_pin)
            HomeKind.DUE -> Primary("start_review", rs(R.string.home_start_review), { onEvent(PalaceEvent.StartReview) })
            HomeKind.DONE -> Primary("start_recall", rs(R.string.home_recall), { onEvent(PalaceEvent.StartRecall) }, icon = R.drawable.ic_route)
            HomeKind.LOST -> Primary("lost_retry", rs(R.string.lost_retry), { onEvent(PalaceEvent.RetryLost) }, icon = R.drawable.ic_retry)
        }
        Spacer(modifier = Modifier.weight(1f))
        when (state.home) {
            // the keys that would repeat the primary button are hidden (design R1-12)
            HomeKind.EMPTY -> Key("recall", R.drawable.ic_route, rs(R.string.home_recall), {}, enabled = false)
            HomeKind.DUE -> {
                if (state.unplaced > 0) Key("place", R.drawable.ic_pin, rs(R.string.cd_place), { onEvent(PalaceEvent.StartPlacing) }, badge = state.unplaced)
                Key("recall", R.drawable.ic_route, rs(R.string.home_recall), { onEvent(PalaceEvent.StartRecall) })
            }
            HomeKind.DONE -> if (state.unplaced > 0) Key("place", R.drawable.ic_pin, rs(R.string.cd_place), { onEvent(PalaceEvent.StartPlacing) }, badge = state.unplaced)
            HomeKind.LOST -> Secondary("lost_replace", rs(R.string.lost_replace, state.lostLoci), { onEvent(PalaceEvent.ReplaceLost) })
        }
    }
}

@Composable
private fun SessionTop(title: String, onEvent: (PalaceEvent) -> Unit, progress: Float? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FitText(title, PalaceType.title, ink, Modifier.weight(1f), maxLines = 2, minScale = 0.7f, oneLineFirst = true)
        Key("end", R.drawable.ic_close, rs(R.string.cd_close), { onEvent(PalaceEvent.EndSession) }, size = 40.dp)
    }
    if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Crumbs(crumbs: List<String>, canUp: Boolean, onEvent: (PalaceEvent) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (canUp) Key("up", R.drawable.ic_up, rs(R.string.cd_up), { onEvent(PalaceEvent.UpLevel) }, size = 36.dp)
        FitText(crumbs.joinToString(" › "), PalaceType.crumb, ink2, maxLines = 2, minScale = 0.7f)
    }
}

@Composable
private fun ColumnScope.Placing(state: PalaceUiState, onEvent: (PalaceEvent) -> Unit) {
    val p = state.placing ?: return
    SessionTop(rs(R.string.place_title, p.number) + "   " + "${p.placed} / ${p.total}", onEvent)
    Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
        val open = state.open
        when {
            p.flash != null -> Line(rs(R.string.place_pinned, circled(p.flash)), PalaceColors.Jade, PalaceType.title)
            open != null -> {
                Crumbs(open.crumbs, open.inside, onEvent)
                Line(rs(if (state.selectedModel != null) R.string.edit_stick_turn else R.string.edit_add_hint), ink, PalaceType.body, maxLines = 2)
            }
            p.edit != null -> Line(rs(R.string.edit_hint), ink, PalaceType.body, maxLines = 2)
            else -> {
                Line(p.nextTitle, ink, PalaceType.card)
                Line(if (p.nextSingle) p.nextLines.joinToString(" → ") else rs(R.string.place_inside, p.nextLines.joinToString(" · ")), maxLines = 2)
            }
        }
        val b = p.block
        if (state.nudge?.kind == NudgeKind.PLACE_FAILED) Line(rs(R.string.place_failed), PalaceColors.Coral)
        else if (b != null && p.flash == null && p.edit == null && open == null) Line(blockText(b), if (b == BlockReason.NO_SURFACE) ink2 else PalaceColors.Coral)
        else if (p.flash == null && p.edit == null && open == null) Line(rs(R.string.place_hint), ink2, maxLines = 2)
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Key("undo_place", R.drawable.ic_undo, rs(R.string.cd_undo), { onEvent(PalaceEvent.UndoPlace) }, enabled = p.canUndo && !p.busy)
        if (p.busy) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = ink, progressSize = CircularProgressDefaults.circleProgressSize(24.dp))
        Spacer(modifier = Modifier.weight(1f))
        Primary("done_placing", rs(R.string.place_done), { onEvent(PalaceEvent.DonePlacing) }, icon = R.drawable.ic_check)
    }
}

@Composable
private fun ColumnScope.Reviewing(state: PalaceUiState, onEvent: (PalaceEvent) -> Unit) {
    val r = state.review ?: return
    SessionTop(rs(R.string.review_title, r.done, r.total), onEvent, progress = if (r.total == 0) 0f else r.done.toFloat() / r.total)
    val open = state.open
    if (open != null) Crumbs(open.crumbs, open.inside, onEvent)
    Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
        when {
            open == null && r.nextNumber != null -> {
                Line(rs(R.string.review_next, "${circled(r.nextNumber)} ${r.nextTitle.orEmpty()}"), ink, PalaceType.title)
                Line(rs(R.string.review_open_hint), maxLines = 2)
            }
            open == null -> Line(rs(R.string.review_almost), ink, PalaceType.title)
            open.single == null && open.cells.isEmpty() -> Line(rs(R.string.review_here, r.hereDue), ink, PalaceType.title)
            open.single?.flipped == true || open.cells.any { it.flipped } -> Line(rs(R.string.review_rate), ink, PalaceType.title)
            open.single != null -> Line(rs(R.string.review_flip), ink, PalaceType.title)
            else -> {
                Line(rs(R.string.review_here, r.hereDue), ink, PalaceType.title)
                if (open.inside) Line(rs(R.string.review_back_hint))
            }
        }
    }
    if (r.undo) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Secondary("undo_rate", rs(R.string.review_undo), { onEvent(PalaceEvent.UndoRate) }, icon = R.drawable.ic_undo)
            Line(rs(R.string.review_undo_hint))
        }
    }
}

@Composable
private fun ColumnScope.Summary(state: PalaceUiState, onEvent: (PalaceEvent) -> Unit) {
    val s = state.summary ?: return
    Line(rs(R.string.summary_title), PalaceColors.Jade, PalaceType.title)
    Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
        Line(rs(R.string.summary_line, s.remembered, s.blurry, s.forgot), ink, PalaceType.card)
        Line(rs(R.string.home_tomorrow, s.tomorrow))
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Primary("summary_ok", rs(R.string.ok), { onEvent(PalaceEvent.SummaryOk) }) }
}

@Composable
private fun ColumnScope.Recalling(state: PalaceUiState, onEvent: (PalaceEvent) -> Unit) {
    val r = state.recall ?: return
    SessionTop(rs(R.string.recall_title, r.number), onEvent, progress = (r.number - 1).toFloat() / r.lociTotal.coerceAtLeast(1))
    val open = state.open
    if (open != null && !open.hidden) Crumbs(open.crumbs, false, onEvent)
    Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
        val n = state.nudge
        when {
            n != null && n.kind == NudgeKind.GO_FIRST -> {
                Line(rs(R.string.recall_go, circled(r.number)), ink, PalaceType.title)
                Line(rs(R.string.nudge_go_first, n.text), PalaceColors.Amber)
            }
            n != null && n.kind == NudgeKind.FLIP_FIRST -> {
                Line(rs(R.string.recall_progress, r.nextTitle.orEmpty(), r.inLocusDone, r.inLocusTotal), ink, PalaceType.title)
                Line(rs(R.string.nudge_flip_first, n.text), PalaceColors.Amber)
            }
            open == null -> {
                Line(rs(R.string.recall_go, circled(r.number)), ink, PalaceType.title)
                Line(rs(R.string.recall_open_hint), maxLines = 2)
            }
            open.hidden -> {
                Line(rs(R.string.recall_think, circled(r.number)), ink, PalaceType.title)
                Line(rs(R.string.recall_pinch))
            }
            else -> {
                Line(rs(R.string.recall_progress, r.nextTitle.orEmpty(), r.inLocusDone, r.inLocusTotal), ink, PalaceType.title)
                Line(rs(if (open.single?.flipped == true || open.cells.any { it.flipped }) R.string.recall_judge else R.string.recall_next_hint), maxLines = 2)
            }
        }
    }
}

@Composable
private fun ColumnScope.RecallResult(state: PalaceUiState, onEvent: (PalaceEvent) -> Unit) {
    val r = state.recallResult ?: return
    Line(rs(R.string.result_title), ink, PalaceType.title)
    Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
        Big("${r.right} / ${r.total}", rs(R.string.result_cards), if (r.right == r.total) PalaceColors.Jade else ink)
        Line(
            (if (r.outOfOrder.isEmpty()) rs(R.string.result_order_ok) else rs(R.string.result_order_bad, r.outOfOrder.joinToString(" ") { circled(it) })) +
                (if (r.wrong > 0) " · " + rs(R.string.result_wrong, r.wrong) else ""),
            maxLines = 2,
        )
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Primary("result_ok", rs(R.string.ok), { onEvent(PalaceEvent.SummaryOk) }) }
}

@Composable
private fun ColumnScope.Scanning(state: PalaceUiState, onEvent: (PalaceEvent) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
        Icon(painter = painterResource(R.drawable.ic_look_around), contentDescription = null, modifier = Modifier.size(48.dp), tint = ink)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Line(rs(R.string.scan_title), ink, PalaceType.card)
            Line(rs(R.string.scan_found), maxLines = 2)
            if (state.scanSlow) Line(rs(R.string.scan_hint), PalaceColors.Amber, maxLines = 2)
        }
    }
    if (state.scanSlow) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Secondary("enter_anyway", rs(R.string.scan_enter), { onEvent(PalaceEvent.EnterAnyway) }) }
}

@Composable
private fun ColumnScope.NoPermission(onEvent: (PalaceEvent) -> Unit) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
        Line(rs(R.string.perm_title), ink, PalaceType.card)
        Line(rs(R.string.perm_body), maxLines = 2)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Primary("grant", rs(R.string.perm_grant), { onEvent(PalaceEvent.GrantPermission) }) }
}

/** The inline confirm strip (design `panels.confirm`): one sentence, [Cancel] [OK]; no floating dialog in a Stage. */
@Composable
private fun ColumnScope.Confirm(state: PalaceUiState, onEvent: (PalaceEvent) -> Unit) {
    val (title, body, yes) =
        when (state.confirm) {
            ConfirmKind.TEAR_DOWN -> Triple(rs(R.string.confirm_tear, state.confirmName, state.placedCount), rs(R.string.confirm_tear_body), rs(R.string.confirm_tear_yes))
            ConfirmKind.DELETE_LOCUS -> Triple(rs(R.string.confirm_delete, state.confirmName), rs(R.string.confirm_delete_body), rs(R.string.confirm_delete_yes))
            else -> Triple(rs(R.string.confirm_end), rs(R.string.confirm_end_body), rs(R.string.confirm_end_yes))
        }
    Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
        Line(title, ink, PalaceType.card, maxLines = 2)
        Line(body, maxLines = 2)
    }
    Row(modifier = Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        Secondary("confirm_no", rs(R.string.cancel), { onEvent(PalaceEvent.ConfirmNo) }, height = 48.dp)
        val danger = state.confirm != ConfirmKind.END_PLACING
        Primary("confirm_yes", yes, { onEvent(PalaceEvent.ConfirmYes) }, container = if (danger) PicoTheme.colorScheme.error else PicoTheme.colorScheme.interaction)
    }
}
