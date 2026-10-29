package io.embodit.memorypalace.ui.palace.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.ButtonChip
import com.pico.spatial.ui.design.ChipsDefaults
import com.pico.spatial.ui.design.CircularProgressDefaults
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.ListItem
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.ScrollIndicator
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentControlDefaults
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.TextField
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import io.embodit.memorypalace.R
import io.embodit.memorypalace.domain.LibraryPacks
import io.embodit.memorypalace.domain.Limits
import io.embodit.memorypalace.domain.MediaError
import io.embodit.memorypalace.ui.palace.AddStep
import io.embodit.memorypalace.ui.palace.AddUi
import io.embodit.memorypalace.ui.palace.PalaceEvent
import io.embodit.memorypalace.ui.palace.PalaceUiState
import io.embodit.memorypalace.ui.palace.debug.probe
import io.embodit.memorypalace.ui.theme.PalaceColors
import io.embodit.memorypalace.ui.theme.PalaceType

/*
 * v0.3 pages of the library popup: S32 the content library, S33 a blank palace, S34 - S38 adding an item
 * (text, image, 3D model, sub-locus) to a locus, and renaming a locus. The text fields read the
 * ViewModel's input states directly (QA q2-1: a value taken from a frame-old UI state undoes keys).
 */

/** The ViewModel's `nameInput`, `frontInput`, `backInput`, provided by the screen. */
val LocalNameInput = staticCompositionLocalOf<MutableState<String>> { mutableStateOf("") }
val LocalFrontInput = staticCompositionLocalOf<MutableState<String>> { mutableStateOf("") }
val LocalBackInput = staticCompositionLocalOf<MutableState<String>> { mutableStateOf("") }

private val ink: Color
    @Composable get() = PicoTheme.colorScheme.labelPrimary

private val ink2: Color
    @Composable get() = PicoTheme.colorScheme.labelSecondary

@Composable
private fun categoryName(c: String): String =
    rs(
        when (c) {
            "poetry" -> R.string.cat_poetry
            "language" -> R.string.cat_language
            "geo" -> R.string.cat_geo
            "history" -> R.string.cat_history
            "nature" -> R.string.cat_nature
            "common" -> R.string.cat_common
            else -> R.string.cat_numbers
        }
    )

/**
 * v0.3.1: seven categories as two rows of chips (a single segment row would not fit 440 dp with readable
 * labels); the selected one is filled. 48 dp high (taste B19).
 */
@Composable
private fun Categories(selected: String, onEvent: (PalaceEvent) -> Unit) {
    for (row in LibraryPacks.categories.chunked(4)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (cat in row) {
                val on = cat == selected
                ButtonChip(
                    label = {
                        // design-style: inherited-content-color ButtonChip
                        Text(text = categoryName(cat), style = PalaceType.crumb, maxLines = 1, softWrap = false)
                    },
                    onClick = { onEvent(PalaceEvent.LibraryCategory(cat)) },
                    modifier = Modifier.height(44.dp).probe("lib_cat_$cat"),
                    colors = ChipsDefaults.chipColors(contentColor = ink, backgroundColor = if (on) PicoTheme.colorScheme.fillSecondary else PicoTheme.colorScheme.fillTertiary),
                    leadingIcon = if (on) ({ Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(16.dp), tint = ink) }) else null,
                )
            }
        }
    }
}

/** S32: five segments, a row per collection; a row opens into its volumes, each 「加入宫殿库」. */
@Composable
internal fun ColumnScope.ContentView(state: PalaceUiState, onEvent: (PalaceEvent) -> Unit) {
    val c = state.content ?: return
    Header(rs(R.string.source_library), true, onEvent) {}
    Categories(c.category, onEvent)
    val list = rememberLazyListState()
    Box(modifier = Modifier.weight(1f)) {
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // the v0.2 poetry packs stay in the poetry segment (design S28 merged into S32)
            items(c.packs, key = { "pack_" + it.id }) { p ->
                Row(modifier = Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = p.name, style = PalaceType.title, color = ink, maxLines = 1)
                        Text(text = rs(R.string.pack_count, p.poets.size, p.poems, p.cards), style = PalaceType.body, color = ink2, maxLines = 1)
                    }
                    if (p.built) Secondary("pack_open_${p.id}", rs(R.string.pack_in_library), { onEvent(PalaceEvent.BuildPack(p.id)) }, icon = R.drawable.ic_check, height = 48.dp)
                    else Primary("pack_build_${p.id}", rs(R.string.lib_add), { onEvent(PalaceEvent.BuildPack(p.id)) }, height = 48.dp)
                }
            }
            for (col in c.collections) {
                item(key = col.id) {
                    ListItem(
                        headlineContent = { Text(text = col.name, style = PalaceType.title, color = ink) },
                        // QA q7-4: the row grows with its structure line (no fixed height, no ellipsis)
                        modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp).clip(RoundedCornerShape(16.dp)).clickable { onEvent(PalaceEvent.LibraryExpand(col.id)) }.probe("lib_${col.id}"),
                        supportingContent = {
                            val count = if (col.generated) rs(R.string.lib_generated, col.cards) else rs(R.string.lib_count, col.cards, col.volumes.size)
                            Text(text = "$count · ${col.structure}", style = PalaceType.body, color = ink2)
                        },
                        trailingContent = { Icon(painter = painterResource(R.drawable.ic_up), contentDescription = null, modifier = Modifier.size(20.dp), tint = ink2) },
                    )
                }
                if (col.expanded) {
                    // v0.3.1: where the cards come from (index.json source + license)
                    if (col.source.isNotBlank()) {
                        item(key = col.id + "_src") {
                            Text(text = rs(R.string.lib_source, col.source, col.license), style = PalaceType.label, color = ink2, modifier = Modifier.padding(start = 24.dp, end = 8.dp).probe("lib_src_${col.id}"))
                        }
                    }
                    if (col.generated) {
                        item(key = col.id + "_gen") {
                            Row(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), horizontalArrangement = Arrangement.End) {
                                Primary("lib_gen_${col.id}", rs(R.string.lib_new_shuffled), { onEvent(PalaceEvent.AddGenerated(col.id)) }, height = 48.dp)
                            }
                        }
                    }
                    items(col.volumes, key = { it.id }) { v ->
                        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = v.name, style = PalaceType.title, color = ink)
                                Text(text = rs(R.string.lib_volume, v.loci, v.cards), style = PalaceType.label, color = ink2, maxLines = 1)
                            }
                            if (v.inLibrary) Secondary("lib_vol_${v.id}", rs(R.string.pack_in_library), { onEvent(PalaceEvent.AddVolume(v.id)) }, icon = R.drawable.ic_check, height = 48.dp)
                            else Primary("lib_vol_${v.id}", rs(R.string.lib_add), { onEvent(PalaceEvent.AddVolume(v.id)) }, height = 48.dp)
                        }
                    }
                }
            }
        }
        ScrollIndicator(state = list)
    }
    Text(text = rs(R.string.lib_license), style = PalaceType.label, color = PicoTheme.colorScheme.labelTertiary)
}

@Composable
private fun Field(input: MutableState<String>, placeholder: String, name: String, max: Int, singleLine: Boolean = true) {
    TextField(
        value = input.value,
        onValueChange = { typed -> input.value = typed.take(max) },
        modifier = Modifier.fillMaxWidth().probe(name),
        placeholder = { Text(text = placeholder, style = PalaceType.body, color = PicoTheme.colorScheme.labelTertiary) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    )
}

/** S33: name a blank palace, then straight to placing. */
@Composable
internal fun ColumnScope.BlankView(onEvent: (PalaceEvent) -> Unit) {
    Header(rs(R.string.source_blank), true, onEvent) {}
    val name = LocalNameInput.current
    Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Field(name, rs(R.string.blank_name), "blank_name", Limits.FRONT_MAX)
        Text(text = rs(R.string.blank_hint), style = PalaceType.body, color = ink2, maxLines = 3)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Primary("blank_create", rs(R.string.blank_start), { onEvent(PalaceEvent.BlankCreate) }, icon = R.drawable.ic_pin, enabled = name.value.isNotBlank())
    }
}

/** A big choice of the S34 menu: icon + word (an icon alone is not clear enough, taste B19). */
@Composable
private fun Choice(name: String, icon: Int, text: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier =
            modifier
                .height(112.dp)
                .clip(RoundedCornerShape(20.dp))
                .then(if (enabled) Modifier.spatialHoverEffect().clickable(onClick = onClick) else Modifier)
                .probe(name)
                .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val tint = if (enabled) ink else PicoTheme.colorScheme.labelQuaternary
        Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(36.dp), tint = tint)
        Text(text = text, style = PalaceType.title, color = tint, maxLines = 1)
    }
}

/** S34 - S38 and the rename / sub-locus names. */
@Composable
internal fun ColumnScope.AddItemView(state: PalaceUiState, onEvent: (PalaceEvent) -> Unit) {
    val a = state.add ?: return
    val title =
        when (a.step) {
            AddStep.TEXT -> rs(R.string.add_text)
            AddStep.IMAGE -> rs(R.string.add_image)
            AddStep.MODEL -> rs(R.string.add_model)
            AddStep.GROUP -> rs(R.string.add_group)
            AddStep.RENAME -> rs(R.string.edit_rename)
            else -> rs(R.string.add_item)
        }
    Header(title, a.step != AddStep.MENU, onEvent) {}
    Text(text = rs(R.string.add_target, a.targetTitle), style = PalaceType.body, color = ink2)
    Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (a.step) {
            AddStep.MENU -> Menu(a, onEvent)
            AddStep.TEXT -> {
                Field(LocalFrontInput.current, rs(R.string.add_front), "add_front", Limits.FRONT_MAX)
                Field(LocalBackInput.current, rs(R.string.add_back), "add_back", Limits.BACK_MAX)
            }
            AddStep.IMAGE -> ImageStep(a)
            AddStep.MODEL -> ModelStep(a, onEvent)
            AddStep.GROUP, AddStep.RENAME -> Field(LocalNameInput.current, rs(R.string.add_name), "add_name", Limits.FRONT_MAX)
            AddStep.ERROR -> ErrorStep(a)
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        when (a.step) {
            AddStep.MENU -> Text(text = rs(R.string.add_files_hint), style = PalaceType.label, color = PicoTheme.colorScheme.labelTertiary, maxLines = 2, modifier = Modifier.weight(1f))
            AddStep.ERROR -> Primary("add_error_ok", rs(R.string.import_got_it), { onEvent(PalaceEvent.PopupBack) })
            else -> {
                val ready =
                    when (a.step) {
                        AddStep.TEXT -> LocalFrontInput.current.value.isNotBlank()
                        AddStep.IMAGE -> a.image != null && !a.busy
                        AddStep.MODEL -> a.modelReady == true
                        else -> LocalNameInput.current.value.isNotBlank()
                    }
                Primary("add_done", rs(R.string.place_done), { onEvent(PalaceEvent.AddDone) }, icon = R.drawable.ic_check, enabled = ready)
            }
        }
    }
}

@Composable
private fun Menu(a: AddUi, onEvent: (PalaceEvent) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Choice("add_text", R.drawable.ic_text, rs(R.string.add_text), true, Modifier.weight(1f)) { onEvent(PalaceEvent.AddText) }
        Choice("add_image", R.drawable.ic_image, rs(R.string.add_image), true, Modifier.weight(1f)) { onEvent(PalaceEvent.AddImage) }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Choice("add_model", R.drawable.ic_cube, rs(R.string.add_model), true, Modifier.weight(1f)) { onEvent(PalaceEvent.AddModel) }
        Choice("add_group", R.drawable.ic_folder, rs(R.string.add_group), a.canGroup, Modifier.weight(1f)) { onEvent(PalaceEvent.AddGroup) }
    }
}

@Composable
private fun Waiting(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = ink, progressSize = CircularProgressDefaults.circleProgressSize(28.dp))
        Text(text = text, style = PalaceType.body, color = ink2, maxLines = 2)
    }
}

@Composable
private fun ColumnScope.ImageStep(a: AddUi) {
    val path = a.image
    if (path == null) return Waiting(rs(R.string.add_picking))
    val bmp = remember(path) { runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
    if (bmp != null) Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)))
    if (a.imageScaled) Text(text = rs(R.string.add_image_scaled), style = PalaceType.label, color = ink2, maxLines = 1)
    Field(LocalBackInput.current, rs(R.string.add_back), "add_back", Limits.BACK_MAX)
}

@Composable
private fun ModelStep(a: AddUi, onEvent: (PalaceEvent) -> Unit) {
    when {
        a.busy && a.modelReady == null -> Waiting(rs(R.string.add_model_loading))
        a.modelReady == true -> {
            Text(text = rs(R.string.add_model_ready), style = PalaceType.title, color = PalaceColors.Jade, maxLines = 1)
            if (a.modelHeavy) Text(text = rs(R.string.add_model_heavy), style = PalaceType.body, color = PalaceColors.Amber, maxLines = 1)
        }
        else -> Waiting(rs(R.string.add_picking))
    }
    Field(LocalNameInput.current, rs(R.string.add_model_name), "add_name", Limits.FRONT_MAX)
    Field(LocalBackInput.current, rs(R.string.add_back), "add_back", Limits.BACK_MAX)
    SegmentControl(modifier = Modifier.fillMaxWidth().height(44.dp), backgroundColor = PicoTheme.colorScheme.fillTertiary) {
        for ((beside, label) in listOf(true to rs(R.string.add_beside), false to rs(R.string.add_on_card))) {
            SegmentItem(
                selected = a.beside == beside,
                onClick = { onEvent(PalaceEvent.AddBeside(beside)) },
                modifier = Modifier.width(219.dp).fillMaxHeight().probe(if (beside) "add_beside" else "add_on_card"),
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
    Text(text = rs(R.string.edit_stick_turn), style = PalaceType.label, color = ink2, maxLines = 1)
}

@Composable
private fun ErrorStep(a: AddUi) {
    Spacer(modifier = Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painter = painterResource(R.drawable.ic_close), contentDescription = null, modifier = Modifier.size(40.dp), tint = PalaceColors.Coral)
        Text(text = mediaErrorText(a.error), style = PalaceType.card, color = ink, maxLines = 3)
    }
}

@Composable
private fun mediaErrorText(e: MediaError?): String =
    rs(
        when (e) {
            MediaError.FORMAT_MODEL -> R.string.media_format_model
            MediaError.FORMAT_IMAGE -> R.string.media_format_image
            MediaError.TOO_BIG_IMAGE -> R.string.media_big_image
            MediaError.TOO_BIG_MODEL -> R.string.media_big_model
            MediaError.LOAD_FAILED -> R.string.media_load_failed
            MediaError.TOO_MANY_MODELS -> R.string.media_too_many_models
            MediaError.NO_PICKER -> R.string.media_no_picker
            else -> R.string.media_full
        }
    )
