package com.example.modules.caffeine

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Coffee
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.module.GoodLockModule
import com.example.core.module.ModuleCategory
import com.example.core.module.ModuleColorRole
import com.example.core.module.ModuleDescriptor
import com.example.core.preferences.ModuYouPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object CaffeineModule : GoodLockModule {
    const val MODULE_ID = "caffeine"
    const val ROUTE = "module/caffeine"

    override val descriptor: ModuleDescriptor = ModuleDescriptor(
        id = MODULE_ID,
        title = "Caféine",
        subtitle = "Écran toujours allumé",
        description = "Empêche la mise en veille de l'écran via un raccourci rapide dans le volet système, jusqu'à désactivation manuelle ou appui sur le bouton Power.",
        version = "v1.0",
        category = ModuleCategory.SYSTEM,
        icon = Icons.Rounded.Coffee,
        colorRole = ModuleColorRole.TERTIARY,
        route = ROUTE,
        capabilities = listOf(
            "QuickSettingsTile",
            "WakeLock",
            "ScreenOffTrigger"
        )
    )

    override fun isSystemReady(context: Context): Boolean = true
}

object CaffeineController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun activate(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val prefs = ModuYouPreferences.getInstance(appContext)
            val notifPrefs = prefs.notificationPreferencesFlow.first()
            val configBefore = prefs.caffeineConfigFlow.first()
            prefs.updateCaffeineConfig {
                it.copy(
                    isActive = true,
                    activeSinceEpochMs = System.currentTimeMillis()
                )
            }
            val shouldShowIcon = notifPrefs.allExecutionNotificationsEnabled && configBefore.showStatusBarIcon
            val startIntent = Intent(appContext, CaffeineKeepAwakeService::class.java).apply {
                action = CaffeineKeepAwakeService.ACTION_START
                putExtra(CaffeineKeepAwakeService.EXTRA_SHOW_STATUS_ICON, shouldShowIcon)
                putExtra(CaffeineKeepAwakeService.EXTRA_TIMEOUT_LABEL, configBefore.timeoutMode.shortLabel)
            }
            runCatching { ContextCompat.startForegroundService(appContext, startIntent) }
            requestTileSync(appContext)
        }
    }

    fun deactivate(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            ModuYouPreferences.getInstance(appContext).updateCaffeineConfig {
                it.copy(
                    isActive = false,
                    activeSinceEpochMs = null
                )
            }
            val stopIntent = Intent(appContext, CaffeineKeepAwakeService::class.java).apply {
                action = CaffeineKeepAwakeService.ACTION_STOP
            }
            runCatching { appContext.stopService(stopIntent) }
            requestTileSync(appContext)
        }
    }

    fun toggle(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val current = ModuYouPreferences.getInstance(appContext).caffeineConfigFlow.first()
            if (current.isActive) {
                deactivate(appContext)
            } else {
                activate(appContext)
            }
        }
    }

    fun requestTileSync(context: Context) {
        runCatching {
            TileService.requestListeningState(
                context.applicationContext,
                ComponentName(context.applicationContext, CaffeineTileService::class.java)
            )
        }
    }

    fun requestAddQuickSettingsTile(
        context: Context,
        onResultMessage: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResultMessage("Déroulez les paramètres rapides, appuyez sur Modifier et glissez « Caféine » dans vos tuiles actives.")
            return
        }

        val statusBarManager = context.getSystemService(StatusBarManager::class.java)
        if (statusBarManager == null) {
            onResultMessage("Impossible d'accéder au panneau de raccourcis sur cet appareil.")
            return
        }

        val componentName = ComponentName(context, CaffeineTileService::class.java)
        val icon = Icon.createWithResource(context, R.drawable.ic_tile_caffeine)
        statusBarManager.requestAddTileService(
            componentName,
            context.getString(R.string.caffeine_tile_label),
            icon,
            ContextCompat.getMainExecutor(context)
        ) { result ->
            val message = when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                    "Tuile Caféine ajoutée aux raccourcis rapides !"
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                    "La tuile Caféine est déjà présente dans vos raccourcis rapides."
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED ->
                    "Ajout annulé. Vous pouvez l'ajouter à tout moment depuis l'édition des raccourcis."
                else ->
                    "Vérifiez vos raccourcis rapides pour placer la tuile Caféine."
            }
            onResultMessage(message)
        }
    }
}
