package io.embodit.memorypalace

import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import io.embodit.memorypalace.ui.palace.PalaceScreen
import io.embodit.memorypalace.ui.theme.MemoryPalaceTheme

/**
 * App entry. One Mixed Stage (AndroidManifest: `pico.spatial.stage.style=1`, immersion 0/0/0): Full
 * Space with passthrough holds the whole product, because plane detection and world anchors only work
 * there (design `container`). There is no WindowContainer.
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            MemoryPalaceTheme {
                PalaceScreen()
            }
        }
    }
