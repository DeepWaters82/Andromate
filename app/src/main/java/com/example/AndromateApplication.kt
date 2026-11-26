package com.example

import android.app.Application
import com.example.core.preferences.ModuYouPreferences
import com.example.modules.automate.AutomateController
import com.example.modules.automate.TorchMonitor
import com.example.modules.caffeine.CaffeineController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AndromateApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        TorchMonitor.ensureRegistered(this)
        appScope.launch {
            CaffeineController.clearStaleActiveState(this@AndromateApplication)
            val prefs = ModuYouPreferences.getInstance(this@AndromateApplication)
            val automateConfig = prefs.automateConfigFlow.first()
            AutomateController.syncMonitoringService(this@AndromateApplication, automateConfig)
            AutomateController.refreshLiveConnectivity(this@AndromateApplication)
        }
        appScope.launch {
            ModuYouPreferences.getInstance(this@AndromateApplication).notificationPreferencesFlow.collect {
                com.example.core.preferences.NotificationSettingsCache.current = it
            }
        }
    }
}
