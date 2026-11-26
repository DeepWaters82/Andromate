package com.example.modules.caffeine

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService
import android.util.Log
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
        description = "Garde l’écran allumé.",
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
        scope.launch { activateNow(appContext) }
    }

    /**
     * Démarre le service Caféine puis seulement ensuite marque Caféine comme active :
     * si Android refuse le démarrage (app en arrière-plan), l’état reste cohérent.
     * @return false si le service n’a pas pu démarrer.
     */
    suspend fun activateNow(context: Context): Boolean {
        val appContext = context.applicationContext
        val prefs = ModuYouPreferences.getInstance(appContext)
        val notifPrefs = prefs.notificationPreferencesFlow.first()
        val configBefore = prefs.caffeineConfigFlow.first()
        val shouldShowIcon = notifPrefs.allExecutionNotificationsEnabled && configBefore.showStatusBarIcon
        val startIntent = Intent(appContext, CaffeineKeepAwakeService::class.java).apply {
            action = CaffeineKeepAwakeService.ACTION_START
            putExtra(CaffeineKeepAwakeService.EXTRA_SHOW_STATUS_ICON, shouldShowIcon)
            putExtra(CaffeineKeepAwakeService.EXTRA_TIMEOUT_LABEL, configBefore.timeoutMode.shortLabel)
        }
        val started = runCatching { ContextCompat.startForegroundService(appContext, startIntent) }
            .onFailure { Log.w(TAG, "Démarrage de Caféine refusé par Android", it) }
            .isSuccess
        if (started) {
            prefs.updateCaffeineConfig {
                it.copy(
                    isActive = true,
                    activeSinceEpochMs = if (it.isActive) {
                        it.activeSinceEpochMs ?: System.currentTimeMillis()
                    } else {
                        System.currentTimeMillis()
                    }
                )
            }
        }
        requestTileSync(appContext)
        return started
    }

    private const val TAG = "CaffeineController"

    /** État réel : actif dans les préférences ET service en cours d’exécution. */
    suspend fun isActuallyActive(context: Context): Boolean =
        CaffeineKeepAwakeService.isRunning &&
            ModuYouPreferences.getInstance(context.applicationContext).caffeineConfigFlow.first().isActive

    /**
     * Au démarrage du processus, aucun service ne tourne encore : un « actif » enregistré est
     * un reste d’une mise à jour ou d’un arrêt forcé. On le corrige pour l’interface et la tuile.
     */
    suspend fun clearStaleActiveState(context: Context) {
        val appContext = context.applicationContext
        val prefs = ModuYouPreferences.getInstance(appContext)
        if (prefs.caffeineConfigFlow.first().isActive && !CaffeineKeepAwakeService.isRunning) {
            prefs.updateCaffeineConfig {
                if (CaffeineKeepAwakeService.isRunning) it else it.copy(isActive = false, activeSinceEpochMs = null)
            }
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
            onResultMessage("Ajoutez « Caféine » via Modifier dans les réglages rapides.")
            return
        }

        val statusBarManager = context.getSystemService(StatusBarManager::class.java)
        if (statusBarManager == null) {
            onResultMessage("Réglages rapides inaccessibles.")
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
                    "Tuile ajoutée."
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                    "Tuile déjà présente."
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED ->
                    "Ajout annulé."
                else ->
                    "Vérifiez vos réglages rapides."
            }
            onResultMessage(message)
        }
    }
}
