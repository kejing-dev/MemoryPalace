package io.embodit.memorypalace.ui.palace.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.ListItem
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.ScrollIndicator
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentControlDefaults
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import io.embodit.memorypalace.R
import io.embodit.memorypalace.data.LanguagePref
import io.embodit.memorypalace.ui.palace.PalaceEvent
import io.embodit.memorypalace.ui.palace.PalaceUiState
import io.embodit.memorypalace.ui.palace.Popup
import io.embodit.memorypalace.ui.palace.debug.probe
import io.embodit.memorypalace.ui.theme.PalaceColors
import io.embodit.memorypalace.ui.theme.PalaceType

private val Row72 = 72.dp

/**
 * The palace library (design `panels.library`, S03) and its import view (S04 / S05), a popup above the
 * pocket panel that closes after a choice: title + 📥, rows of colour dot + name + "5 loci · 17 cards ·
 * 4 today", ✓ on the current one.
 */
@Composable
internal fun LibraryPanel(state: PalaceUiState?, onEvent: (PalaceEvent) -> Unit) {
    Column(
        modifier =
            Modifier
                .size(PanelSizes.LIBRARY_W.dp, PanelSizes.LIBRARY_H.dp)
                .clip(RoundedCornerShape(32.dp))
                .backgroundMaterial(enable = true, style = Material.Regular)
                .then(glassRefresh())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state == null) return@Column
        if (state.popup == Popup.IMPORT) ImportView(state, onEvent) else Palaces(state, onEvent)
    }
}

@Composable
private fun Header(title: String, back: Boolean, onEvent: (PalaceEvent) -> Unit, trailing: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (back) Key("import_back", R.drawable.ic_up, rs(R.string.cd_back), { onEvent(PalaceEvent.ImportBack) }, size = 40.dp)
        Text(text = title, color = PicoTheme.colorScheme.labelPrimary, style = PicoTheme.typography.headlineSmall, maxLines = 1, modifier = Modifier.weight(1f))
        trailing()
        Key("popup_close", R.drawable.ic_close, rs(R.string.cd_close_popup), { onEvent(PalaceEvent.PopupClosed) }, size = 40.dp)
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Palaces(state: PalaceUiState, onEvent: (PalaceEvent) -> Unit) {
    Header(rs(R.string.library_title), false, onEvent) { Key("import", R.drawable.ic_import, rs(R.string.import_title), { onEvent(PalaceEvent.ImportClicked) }, size = 40.dp) }
    val list = rememberLazyListState()
    Box(modifier = Modifier.weight(1f)) {
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            items(state.palaces, key = { it.id }) { row ->
                ListItem(
                    headlineContent = { Text(text = row.name, style = PalaceType.title, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth().height(Row72).clip(RoundedCornerShape(16.dp)).clickable { onEvent(PalaceEvent.PalacePicked(row.id)) }.probe("palace_${row.id}"),
                    supportingContent = { Text(text = rs(R.string.library_row, row.loci, row.cards, row.dueToday), style = PalaceType.body, color = PicoTheme.colorScheme.labelSecondary, maxLines = 1) },
                    leadingContent = { Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(PalaceColors.deck(row.color))) },
                    trailingContent = {
                        if (row.current) Icon(painter = painterResource(R.drawable.ic_check), contentDescription = rs(R.string.cd_current), modifier = Modifier.size(24.dp), tint = PicoTheme.colorScheme.interaction)
                    },
                )
            }
        }
        ScrollIndicator(state = list)
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ImportView(state: PalaceUiState, onEvent: (PalaceEvent) -> Unit) {
    Header(rs(R.string.import_title), true, onEvent) {}
    val err = state.importError
    if (err != null) {
        // S05: coral ✕ + one reason + [Got it]
        Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(PalaceColors.Coral), contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(R.drawable.ic_close), contentDescription = null, modifier = Modifier.size(32.dp), tint = PalaceColors.Ink)
            }
            Text(text = importErrorText(err), color = PicoTheme.colorScheme.labelPrimary, style = PalaceType.card, maxLines = 2)
            Text(text = rs(R.string.import_formats), color = PicoTheme.colorScheme.labelSecondary, style = PalaceType.body, maxLines = 3)
            Primary("import_error_ok", rs(R.string.import_got_it), { onEvent(PalaceEvent.ImportErrorOk) })
        }
        return
    }
    Primary("pick_file", rs(R.string.import_pick), { onEvent(PalaceEvent.PickFromFiles) }, icon = R.drawable.ic_import)
    Text(text = rs(R.string.import_folder), color = PicoTheme.colorScheme.labelSecondary, style = PalaceType.label, maxLines = 2)
    val res = state.importResult
    if (res != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(22.dp), tint = PalaceColors.Jade)
            Text(text = rs(R.string.import_ok, res.loci, res.cards, res.depth) + if (res.palaces > 1) " · " + rs(R.string.import_split, res.palaces) else "", color = PalaceColors.Jade, style = PalaceType.title, maxLines = 2)
        }
    }
    val list = rememberLazyListState()
    Box(modifier = Modifier.weight(1f)) {
        if (state.importFiles.isEmpty()) Text(text = rs(R.string.import_empty_list), color = PicoTheme.colorScheme.labelTertiary, style = PalaceType.body, modifier = Modifier.padding(top = 12.dp))
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            items(state.importFiles, key = { it.name }) { f ->
                ListItem(
                    headlineContent = { Text(text = f.name, style = PalaceType.title, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp)).clickable { onEvent(PalaceEvent.ImportFile(f.name)) }.probe("file_${f.name}"),
                    supportingContent = { Text(text = rs(R.string.import_lines, f.lines), style = PalaceType.body, color = PicoTheme.colorScheme.labelSecondary, maxLines = 1) },
                    leadingContent = { Icon(painter = painterResource(R.drawable.ic_file), contentDescription = null, modifier = Modifier.size(24.dp), tint = PicoTheme.colorScheme.labelPrimary) },
                )
            }
        }
        ScrollIndicator(state = list)
    }
}

/** Settings (design `panels.settings`, S24): language segments, find loci again, take the palace down, about. */
@Composable
internal fun SettingsPanel(state: PalaceUiState?, onEvent: (PalaceEvent) -> Unit) {
    Column(
        modifier =
            Modifier
                .size(PanelSizes.SETTINGS_W.dp, PanelSizes.SETTINGS_H.dp)
                .clip(RoundedCornerShape(32.dp))
                .backgroundMaterial(enable = true, style = Material.Regular)
                .then(glassRefresh())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state == null) return@Column
        val ink = PicoTheme.colorScheme.labelPrimary
        Row(modifier = Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = rs(R.string.settings_title), color = ink, style = PicoTheme.typography.headlineSmall, maxLines = 1, modifier = Modifier.weight(1f))
            Key("popup_close", R.drawable.ic_close, rs(R.string.cd_close_popup), { onEvent(PalaceEvent.PopupClosed) }, size = 40.dp)
        }
        Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painter = painterResource(R.drawable.ic_globe), contentDescription = null, modifier = Modifier.size(22.dp), tint = ink)
            SegmentControl(modifier = Modifier.weight(1f).height(44.dp), backgroundColor = PicoTheme.colorScheme.fillTertiary) {
                val w = ((PanelSizes.SETTINGS_W - 40f - 34f - 8f) / 3f).dp
                for (pref in LanguagePref.entries) {
                    SegmentItem(
                        selected = pref == state.language,
                        onClick = { onEvent(PalaceEvent.LanguagePicked(pref)) },
                        modifier = Modifier.width(w).fillMaxHeight().probe("lang_${pref.tag}"),
                        textStyle = PalaceType.crumb,
                        colors =
                            SegmentControlDefaults.colors(
                                itemBackgroundColor = Color.Transparent,
                                itemContentColor = ink,
                                selectedItemBackgroundColor = PicoTheme.colorScheme.fillSecondary,
                                selectedItemContentColor = ink,
                            ),
                        title = {
                            Text(
                                text = rs(when (pref) { LanguagePref.SYSTEM -> R.string.lang_system; LanguagePref.ZH -> R.string.lang_zh; LanguagePref.EN -> R.string.lang_en }),
                                color = ink,
                                style = PalaceType.crumb,
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                    )
                }
            }
        }
        SettingsRow("relocate", R.drawable.ic_retry, rs(R.string.settings_relocate), null, ink) { onEvent(PalaceEvent.Relocate) }
        SettingsRow("tear_down", R.drawable.ic_trash, rs(R.string.settings_tear), rs(R.string.settings_tear_sub), if (state.placedCount > 0) PicoTheme.colorScheme.error else PicoTheme.colorScheme.labelQuaternary) {
            onEvent(PalaceEvent.TearDown)
        }
        SettingsRow("about", R.drawable.ic_info, rs(R.string.settings_about), rs(R.string.settings_about_sub, state.versionName), ink, null)
    }
}

@Composable
private fun SettingsRow(name: String, icon: Int, title: String, sub: String?, color: Color, onClick: (() -> Unit)?) {
    val base = Modifier.fillMaxWidth().height(if (sub == null) 52.dp else 60.dp).clip(RoundedCornerShape(16.dp))
    ListItem(
        headlineContent = { Text(text = title, style = PalaceType.title, color = color, maxLines = 1) },
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base).probe(name),
        supportingContent = sub?.let { s -> { Text(text = s, style = PalaceType.body, color = PicoTheme.colorScheme.labelSecondary, maxLines = 1) } },
        leadingContent = { Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp), tint = color) },
    )
    Spacer(modifier = Modifier.height(0.dp))
}
