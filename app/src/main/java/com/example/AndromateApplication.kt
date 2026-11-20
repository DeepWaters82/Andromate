package com.example

import android.app.Application
import com.example.core.preferences.ModuYouPreferences
import com.example.modules.automate.AutomateController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AndromateApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val prefs = ModuYouPreferences.getInstance(this@AndromateApplication)
            val automateConfig = prefs.automateConfigFlow.first()
            AutomateController.syncMonitoringService(this@AndromateApplication, automateConfig)
            AutomateController.refreshLiveConnectivity(this@AndromateApplication)
        }
    }
}
