package io.embodit.memorypalace.ui.palace.components

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import io.embodit.memorypalace.R
import io.embodit.memorypalace.data.LanguagePref
import io.embodit.memorypalace.domain.BlockReason
import io.embodit.memorypalace.domain.ImportError

/**
 * Copy for the language chosen in the app (follow system / 中文 / English), switched at run time
 * without restarting the Stage: the screen provides a Resources object for that locale and every panel
 * reads its strings from it (Spatial Ruler). `LocalContext` is not overridden.
 */
val LocalPalaceResources = staticCompositionLocalOf<Resources> { error("LocalPalaceResources not provided") }

fun localizedResources(context: Context, language: LanguagePref): Resources {
    val tag =
        when (language) {
            LanguagePref.SYSTEM -> return context.resources
            LanguagePref.ZH -> "zh-CN"
            LanguagePref.EN -> "en"
        }
    val configuration = Configuration(context.resources.configuration)
    configuration.setLocale(java.util.Locale.forLanguageTag(tag))
    return context.createConfigurationContext(configuration).resources
}

@Composable
@ReadOnlyComposable
fun rs(@StringRes id: Int): String = LocalPalaceResources.current.getString(id)

@Composable
@ReadOnlyComposable
fun rs(@StringRes id: Int, vararg args: Any): String = LocalPalaceResources.current.getString(id, *args)

@Composable
@ReadOnlyComposable
fun blockText(b: BlockReason): String =
    rs(
        when (b) {
            BlockReason.SURFACE -> R.string.block_surface
            BlockReason.TOO_LOW -> R.string.block_too_low
            BlockReason.TOO_HIGH -> R.string.block_too_high
            BlockReason.TOO_FAR_FROM_ROUTE -> R.string.block_too_far
            BlockReason.TOO_CLOSE -> R.string.block_too_close
            BlockReason.TABLE_EDGE -> R.string.block_table_edge
            BlockReason.LIMIT -> R.string.block_limit
            BlockReason.NO_SURFACE -> R.string.block_no_surface
        }
    )

@Composable
@ReadOnlyComposable
fun importErrorText(e: ImportError): String =
    rs(
        when (e) {
            ImportError.EMPTY -> R.string.import_error_empty
            ImportError.NO_TWO_COLUMNS -> R.string.import_error_columns
            ImportError.TOO_DEEP -> R.string.import_error_deep
            ImportError.ENCODING -> R.string.import_error_encoding
        }
    )

/** "2.3 m" from decimetres. */
fun metres(dm: Int): String = "${dm / 10}.${dm % 10} m"

/** Panel sizes (design `panels[].sizeDp`), in dp; physical size = dp × 0.0008 m × the panel's scale. */
object PanelSizes {
    const val POCKET_W = 400f
    const val POCKET_H = 248f
    const val LIBRARY_W = 480f
    const val LIBRARY_H = 520f
    const val SETTINGS_W = 420f
    const val SETTINGS_H = 340f

    /** Plaque / title 0.22 × 0.14 m at scale 1. */
    const val TITLE_W = 275f
    const val TITLE_H = 175f

    /** Child card 0.16 × 0.10 m at scale 1. */
    const val CELL_W = 200f
    const val CELL_H = 125f
    const val LABEL_W = 260f
    const val LABEL_H = 48f
    const val NUDGE_W = 240f
    const val NUDGE_H = 48f
    const val EDIT_W = 240f
    const val EDIT_H = 56f
}
