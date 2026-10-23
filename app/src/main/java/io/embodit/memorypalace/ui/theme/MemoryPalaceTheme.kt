package io.embodit.memorypalace.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.systemColorScheme

/**
 * The app theme: SpatialUI Vibrant with the four semantic roles of the design and the Noto Sans type
 * family. The twelve adaptive grey roles are forwarded from the system scheme on purpose (design
 * `palette.colorScheme`: inherit).
 */
@Composable
fun MemoryPalaceTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val system = systemColorScheme(context)
    val colors =
        remember(system) {
            system.copy(
                error = PalaceColors.Error,
                alert = PalaceColors.Alert,
                passable = PalaceColors.Passable,
                interaction = PalaceColors.Interaction,
            )
        }
    val family = rememberPalaceFontFamily()
    val base = PicoTheme.typography
    val typography = remember(base, family) { base.withFamily(family) }
    PicoTheme(colorScheme = colors, typography = typography, content = content)
}
