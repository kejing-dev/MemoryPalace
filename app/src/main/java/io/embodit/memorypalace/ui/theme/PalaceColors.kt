package io.embodit.memorypalace.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.PicoTheme

/**
 * The design's exact colours (design-package `palette`): lantern amber, jade, coral and mist blue over
 * passthrough; porcelain plaques with ink text and a brass pin. This is the only file with colour
 * literals; the four semantic roles go into `PicoTheme` ([MemoryPalaceTheme]), brand tokens are used directly.
 */
object PalaceColors {
    // ---- ColorScheme roles with a fixed design value (the other twelve inherit SpatialUI Vibrant)
    val Interaction = Color(0xFFFFB648) // design-style: fixed-figma-color interaction: lanternAmber, due / next locus
    val Passable = Color(0xFF5FD3A4) // design-style: fixed-figma-color passable: jade, remembered / right
    val Alert = Color(0xFFFF9A3D) // design-style: fixed-figma-color alert
    val Error = Color(0xFFFF6B5E) // design-style: fixed-figma-color error: coral, forgot / wrong / cannot pin

    // ---- brand tokens outside ColorScheme
    val Amber = Color(0xFFFFB648) // design-style: fixed-figma-color lanternAmber
    val Jade = Color(0xFF5FD3A4) // design-style: fixed-figma-color jade
    val Coral = Color(0xFFFF6B5E) // design-style: fixed-figma-color coral
    val Mist = Color(0xFF8FB8FF) // design-style: fixed-figma-color mistBlue: blurry
    val Porcelain = Color(0xFFF4F0E8) // design-style: fixed-figma-color porcelain: plaque and card face
    val Ink = Color(0xFF1F2430) // design-style: fixed-figma-color plaqueInk: text on porcelain and on semantic fills
    val InkSecondary = Color(0xFF6A7080) // design-style: fixed-figma-color plaqueInk secondary (sample `#6a7080`)
    val PorcelainDim = Color(0xFFCFCBC3) // design-style: fixed-figma-color porcelain, not due (dimmed card)
    val Brass = Color(0xFFC9A35A) // design-style: fixed-figma-color brassPin
    val Dim = Color(0xFF7B7B7B) // design-style: fixed-figma-color dim dot (sample `C.dim`)
    val White = Color(0xFFFFFFFF) // design-style: fixed-figma-color labelPrimaryLight

    /** `brandTokens.deckColors`: one per palace. */
    val Deck = listOf(Color(0xFFFFB648), Color(0xFF8FB8FF), Color(0xFF5FD3A4), Color(0xFFE58FD0), Color(0xFFF2D16B), Color(0xFF7FD6E8)) // design-style: fixed-figma-color deckColors

    fun deck(i: Int): Color = Deck[((i % Deck.size) + Deck.size) % Deck.size]
}

/** Type roles of the design (`typography.roles`); Chinese ≤ 17 sp is Medium at least. */
object PalaceType {
    val due: TextStyle
        @Composable get() = PicoTheme.typography.displayLarge.copy(fontSize = 40.sp, lineHeight = 54.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum, lnum")

    val card: TextStyle
        @Composable get() = PicoTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)

    val title: TextStyle
        @Composable get() = PicoTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)

    val body: TextStyle
        @Composable get() = PicoTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)

    val label: TextStyle
        @Composable get() = PicoTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum, lnum")

    val crumb: TextStyle
        @Composable get() = PicoTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
}
