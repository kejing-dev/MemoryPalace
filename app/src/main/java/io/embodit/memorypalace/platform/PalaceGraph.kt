package io.embodit.memorypalace.platform

import android.content.Context
import io.embodit.memorypalace.data.FilePalaceStore
import io.embodit.memorypalace.data.PalaceStore
import io.embodit.memorypalace.platform.anchor.WorldAnchorGateway
import io.embodit.memorypalace.platform.sense.PlaneTracker
import io.embodit.memorypalace.platform.tracking.HeadTracker

/**
 * Process-wide platform services. The scene starts and stops the sensing services (every start has a
 * stop: `PalaceScene.attach` / `detach` / `pause`); the ViewModel only sees interfaces and lambdas.
 */
object PalaceGraph {
    val anchors = WorldAnchorGateway()
    val planes = PlaneTracker()
    val head = HeadTracker()

    @Volatile private var store: PalaceStore? = null

    @Volatile private var wikiClient: WikiClient? = null

    fun wiki(context: Context): WikiClient = wikiClient ?: synchronized(this) { wikiClient ?: WikiClient(context.applicationContext).also { wikiClient = it } }

    fun store(context: Context): PalaceStore = store ?: synchronized(this) { store ?: FilePalaceStore(context.applicationContext).also { store = it } }
}
