package io.embodit.memorypalace.ui.palace

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pico.spatial.ui.foundation.dsl.registerSystem
import com.pico.spatial.ui.foundation.dsl.unregisterSystem
import io.embodit.memorypalace.platform.PalaceLog
import io.embodit.memorypalace.platform.PickerActivity
import io.embodit.memorypalace.ui.palace.components.LocalPalaceResources
import io.embodit.memorypalace.ui.palace.components.LocalWikiInput
import io.embodit.memorypalace.ui.palace.components.PalaceStage
import io.embodit.memorypalace.ui.palace.components.localizedResources
import io.embodit.memorypalace.ui.palace.debug.Probe
import io.embodit.memorypalace.ui.palace.scene.PalaceFrameSystem
import io.embodit.memorypalace.ui.palace.scene.PalaceScene

/**
 * The one screen of the app, in a Mixed Stage (Full Space). It wires the ViewModel (state), the
 * [PalaceScene] (entities, per-frame rules, pinches) and [PalaceStage] (the panels), and owns nothing.
 */
@Composable
fun PalaceScreen() {
    val context = LocalContext.current
    val viewModel: PalaceViewModel = viewModel(factory = PalaceViewModel.Factory(context))
    val state by viewModel.state.collectAsStateWithLifecycle()
    remember(context) {
        Probe.enabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        true
    }
    val scene = remember(viewModel) { PalaceScene(viewModel::onEvent) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(scene, lifecycle) {
        // the one per-frame system; every start has its stop in onDispose
        registerSystem<PalaceFrameSystem>()
        Probe.host =
            object : Probe.Host {
                override fun viewModel(): PalaceViewModel = viewModel

                override fun scene(): PalaceScene = scene
            }
        // in the background every tracker stops (design `container.lifecycle`)
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> scene.pause()
                    Lifecycle.Event.ON_START -> {
                        scene.resume()
                        viewModel.refreshImportFiles()
                        viewModel.publish()
                    }
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        PalaceLog.i("STAGE enter")
        onDispose {
            lifecycle.removeObserver(observer)
            Probe.host = null
            unregisterSystem<PalaceFrameSystem>()
            scene.detach()
            PalaceLog.i("STAGE disposed")
        }
    }

    LaunchedEffect(scene, state) { scene.render(state) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                PalaceEffect.RestartSensing -> scene.restartSensing()
                PalaceEffect.OpenPicker ->
                    runCatching { context.startActivity(Intent(context, PickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { PalaceLog.e("IMPORT picker failed: $it", it) }
            }
        }
    }

    // the in-app language switches at run time: panels read their copy from this local
    val resources = remember(state.language, context) { localizedResources(context, state.language) }
    CompositionLocalProvider(LocalPalaceResources provides resources, LocalWikiInput provides viewModel.wikiInput) {
        PalaceStage(state = state, scene = scene, onEvent = viewModel::onEvent)
    }
}
