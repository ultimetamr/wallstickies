package com.spatialapps.wallstickies.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.spatialapps.wallstickies.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
