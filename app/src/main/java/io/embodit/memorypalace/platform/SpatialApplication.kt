package io.embodit.memorypalace.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import io.embodit.memorypalace.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
