package io.embodit.memorypalace.ui.palace.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.ButtonChip
import com.pico.spatial.ui.design.Checkbox
import com.pico.spatial.ui.design.CircularProgressDefaults
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.ListItem
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.ScrollIndicator
import com.pico.spatial.ui.design.SearchField
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentControlDefaults
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Text
import io.embodit.memorypalace.R
import io.embodit.memorypalace.ui.palace.PalaceEvent
import io.embodit.memorypalace.ui.palace.PalaceUiState
import io.embodit.memorypalace.ui.palace.WikiUi
import io.embodit.memorypalace.ui.palace.debug.Probe
import io.embodit.memorypalace.ui.palace.debug.probe
import io.embodit.memorypalace.platform.PalaceLog
import io.embodit.memorypalace.ui.theme.PalaceColors
import io.embodit.memorypalace.ui.theme.PalaceType

/*
 * v0.2 content sources (design `contentSources`), pages of the library popup: S27 new palace (poetry /
 * Wikipedia / file), S28 poetry packs, S29 Wikipedia search, S30 preview, S31 unreachable.
 */

/** The search field's text (PalaceViewModel.wikiInput), provided by the screen. */
val LocalWikiInput = staticCompositionLocalOf<MutableState<String>> { mutableStateOf("") }

private val ink: Color
    @Composable get() = PicoTheme.colorScheme.labelPrimary

private val ink2: Color
    @Composable get() = PicoTheme.colorScheme.labelSecondary

@Composable
private fun SourceRow(name: String, icon: Int, title: String, sub: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text = title, style = PalaceType.card, color = ink, maxLines = 1) },
        modifier = Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).probe(name),
        supportingContent = { Text(text = sub, style = PalaceType.body, color = ink2, maxLines = 2) },
        leadingContent = { Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp), tint = ink) },
    )
}

/** S27: three ways to a new palace. */
@Composable
internal fun ColumnScope.NewPalaceView(onEvent: (PalaceEvent) -> Unit) {
    Header(rs(R.string.new_palace_title), true, onEvent) {}
    Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SourceRow("source_poetry", R.drawable.ic_scroll, rs(R.string.source_poetry), rs(R.string.source_poetry_sub)) { onEvent(PalaceEvent.SourcePoetry) }
        SourceRow("source_wiki", R.drawable.ic_globe_wiki, rs(R.string.source_wiki), rs(R.string.source_wiki_sub)) { onEvent(PalaceEvent.SourceWiki) }
        SourceRow("source_file", R.drawable.ic_import, rs(R.string.source_file), rs(R.string.source_file_sub)) { onEvent(PalaceEvent.SourceFile) }
    }
}

/** S28: the bundled poetry packs; one already in the library opens instead of building again. */
@Composable
internal fun ColumnScope.PoetryView(state: PalaceUiState, onEvent: (PalaceEvent) -> Unit) {
    Header(rs(R.string.source_poetry), true, onEvent) {}
    Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (p in state.packs) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = p.name, style = PalaceType.card, color = ink, maxLines = 1)
                Text(text = rs(R.string.pack_count, p.poets.size, p.poems, p.cards), style = PalaceType.body, color = ink2, maxLines = 1)
                Text(text = p.poets.joinToString(" · "), style = PalaceType.body, color = ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (p.built) Secondary("pack_open_${p.id}", rs(R.string.pack_in_library), { onEvent(PalaceEvent.BuildPack(p.id)) }, icon = R.drawable.ic_check)
                    else Primary("pack_build_${p.id}", rs(R.string.pack_build), { onEvent(PalaceEvent.BuildPack(p.id)) }, height = 44.dp)
                }
            }
        }
        Text(text = rs(R.string.pack_license), style = PalaceType.label, color = PicoTheme.colorScheme.labelTertiary, maxLines = 2)
    }
}

/** S29 / S30 / S31. */
@Composable
internal fun ColumnScope.WikiView(state: PalaceUiState, onEvent: (PalaceEvent) -> Unit) {
    val w = state.wiki ?: return
    when {
        w.failed -> WikiFailed(onEvent)
        w.preview != null || w.loading != null -> WikiPreview(w, onEvent)
        else -> WikiSearchPage(w, onEvent)
    }
}

@Composable
private fun ColumnScope.WikiSearchPage(w: WikiUi, onEvent: (PalaceEvent) -> Unit) {
    Header(rs(R.string.source_wiki), true, onEvent) { LangSegments(w.lang, onEvent) }
    // QA q2-1: the field's value is the ViewModel's input state, read here directly. A value taken from the
    // (possibly frame-old) UI state undid every key about 1 s later on the emulator.
    val input = LocalWikiInput.current
    if (Probe.enabled) DisposableEffect(Unit) {
        PalaceLog.i("WIKI field enter text=${input.value}")
        onDispose { PalaceLog.i("WIKI field leave") }
    }
    SearchField(
        input.value,
        { typed ->
            if (typed != input.value) {
                input.value = typed
                onEvent(PalaceEvent.WikiQuery(typed))
            }
        },
        { onEvent(PalaceEvent.WikiSearch(it)) },
        modifier = Modifier.fillMaxWidth().height(48.dp).probe("wiki_search"),
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = rs(R.string.wiki_try), style = PalaceType.label, color = ink2, maxLines = 1)
        val tries = if (w.lang == "zh") listOf("长城", "太阳系", "光合作用") else listOf("Great Wall of China", "Solar System", "Photosynthesis")
        for ((i, t) in tries.withIndex()) {
            ButtonChip(
                label = {
                    // design-style: inherited-content-color ButtonChip
                    Text(text = t, style = PalaceType.label, maxLines = 1)
                },
                onClick = { onEvent(PalaceEvent.WikiSearch(t)) },
                modifier = Modifier.probe("wiki_try_$i"),
            )
        }
    }
    val list = rememberLazyListState()
    Box(modifier = Modifier.weight(1f)) {
        when {
            w.searching -> CircularProgressIndicator(modifier = Modifier.size(32.dp).align(Alignment.Center), color = ink, progressSize = CircularProgressDefaults.circleProgressSize(32.dp))
            w.hits.isEmpty() && w.query.isNotBlank() -> Text(text = rs(R.string.wiki_no_hits), style = PalaceType.body, color = ink2, modifier = Modifier.padding(top = 12.dp))
            w.hits.isEmpty() -> Text(text = rs(R.string.wiki_hint), style = PalaceType.body, color = ink2, maxLines = 3, modifier = Modifier.padding(top = 12.dp))
        }
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            items(w.hits, key = { it.title }) { h ->
                ListItem(
                    headlineContent = { Text(text = h.title, style = PalaceType.title, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(16.dp)).clickable { onEvent(PalaceEvent.WikiPick(h.title, h.description)) }.probe("wiki_hit_${w.hits.indexOf(h)}"),
                    supportingContent = if (h.description.isNotBlank()) ({ Text(text = h.description, style = PalaceType.body, color = ink2, maxLines = 1, overflow = TextOverflow.Ellipsis) }) else null,
                )
            }
        }
        ScrollIndicator(state = list)
    }
    Text(text = rs(R.string.wiki_license), style = PalaceType.label, color = PicoTheme.colorScheme.labelTertiary, maxLines = 1)
}

@Composable
private fun LangSegments(lang: String, onEvent: (PalaceEvent) -> Unit) {
    SegmentControl(modifier = Modifier.width(152.dp).height(40.dp), backgroundColor = PicoTheme.colorScheme.fillTertiary) {
        for ((tag, label) in listOf("zh" to "中文", "en" to "English")) {
            SegmentItem(
                selected = lang == tag,
                onClick = { onEvent(PalaceEvent.WikiLang(tag)) },
                modifier = Modifier.width(72.dp).fillMaxHeight().probe("wiki_lang_$tag"),
                textStyle = PalaceType.crumb,
                colors =
                    SegmentControlDefaults.colors(
                        itemBackgroundColor = Color.Transparent,
                        itemContentColor = ink,
                        selectedItemBackgroundColor = PicoTheme.colorScheme.fillSecondary,
                        selectedItemContentColor = ink,
                    ),
                title = { Text(text = label, color = ink, style = PalaceType.crumb, maxLines = 1, softWrap = false) },
            )
        }
    }
}

@Composable
private fun ColumnScope.WikiPreview(w: WikiUi, onEvent: (PalaceEvent) -> Unit) {
    val p = w.preview
    Header(p?.title ?: w.loading.orEmpty(), true, onEvent) {}
    if (p == null) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp), color = ink, progressSize = CircularProgressDefaults.circleProgressSize(36.dp))
        }
        return
    }
    if (p.description.isNotBlank()) Text(text = p.description, style = PalaceType.body, color = ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
    val list = rememberLazyListState()
    Box(modifier = Modifier.weight(1f)) {
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            items(p.loci, key = { it.index }) { l ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(16.dp)).clickable { onEvent(PalaceEvent.WikiToggle(l.index)) }.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Checkbox(l.kept, { onEvent(PalaceEvent.WikiToggle(l.index)) }, modifier = Modifier.probe("wiki_locus_${l.index}"))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "${l.title} · " + rs(R.string.card_cards, l.cards), style = PalaceType.title, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = l.firstFront, style = PalaceType.label, color = ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        ScrollIndicator(state = list)
    }
    if (p.tooShort) Text(text = rs(R.string.wiki_too_short), style = PalaceType.body, color = PalaceColors.Coral, maxLines = 1)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = rs(R.string.wiki_license), style = PalaceType.label, color = PicoTheme.colorScheme.labelTertiary, maxLines = 2, modifier = Modifier.weight(1f))
        Primary("wiki_build", rs(R.string.wiki_build, p.keptLoci, p.keptCards), { onEvent(PalaceEvent.WikiBuild) }, enabled = !p.tooShort, height = 44.dp)
    }
}

/** S31: this network does not reach Wikipedia (mainland China among others): to the poetry packs. */
@Composable
private fun ColumnScope.WikiFailed(onEvent: (PalaceEvent) -> Unit) {
    Header(rs(R.string.source_wiki), true, onEvent) {}
    Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painter = painterResource(R.drawable.ic_globe_wiki), contentDescription = null, modifier = Modifier.size(48.dp), tint = PalaceColors.Coral)
        Text(text = rs(R.string.wiki_unreachable), style = PalaceType.card, color = ink, maxLines = 2)
        Text(text = rs(R.string.wiki_unreachable_sub), style = PalaceType.body, color = ink2, maxLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Secondary("wiki_retry", rs(R.string.wiki_retry), { onEvent(PalaceEvent.WikiRetry) }, icon = R.drawable.ic_retry, height = 48.dp)
            Primary("go_poetry", rs(R.string.wiki_go_poetry), { onEvent(PalaceEvent.GoPoetry) }, icon = R.drawable.ic_scroll)
        }
    }
}
