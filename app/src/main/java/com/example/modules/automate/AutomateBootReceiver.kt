package com.example.modules.automate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.core.preferences.ModuYouPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Relance la surveillance des routines après un redémarrage du téléphone ou une mise à jour
 * de l’application : sans cela, rien ne tourne tant que l’app n’a pas été rouverte.
 * Démarrer un service de premier plan specialUse depuis BOOT_COMPLETED est autorisé.
 */
class AutomateBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val config = ModuYouPreferences.getInstance(appContext).automateConfigFlow.first()
                AutomateController.syncMonitoringService(appContext, config)
            } finally {
                pending.finish()
            }
        }
    }
}
