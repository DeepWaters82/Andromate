package com.example.modules.automate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Surface
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.core.preferences.ModuYouPreferences
import com.example.modules.caffeine.CaffeineController
import com.example.modules.onehand.OneHandAccessibilityService
import com.example.modules.onehand.OneHandModule
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

object AutomateActionExecutor {

    private const val ROUTINE_NOTIF_CHANNEL_ID = "automate_routine_alerts"

    // Clés de l’instantané pris avant une routine (pour le retour à l’état précédent).
    private const val K_ACTIVE_SINCE = "_since"
    private const val K_AOD = "aod"
    private const val K_AOD_OVERLAY = "aod_overlay"
    private const val K_DND = "dnd"
    private const val K_RINGER = "ringer"
    private const val K_VOLUME = "volume"
    private const val K_CAFFEINE = "caffeine"
    private const val K_ONE_HAND = "onehand"
    private const val K_TORCH = "torch"
    private const val K_ROTATION_AUTO = "rotation_auto"
    private const val K_ROTATION_USER = "rotation_user"
    private const val K_TIMEOUT = "timeout"
    private const val K_BRIGHTNESS = "brightness"
    private const val K_BRIGHTNESS_MODE = "brightness_mode"

    private const val AOD_SETTING = "doze_always_on"

    fun canWriteSystemSettings(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)

    fun openWriteSettingsPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    suspend fun executeRoutine(
        context: Context,
        routine: AutomationRoutine,
        triggerDetail: String
    ): AutomateLogEntry {
        val appContext = context.applicationContext
        val prefs = ModuYouPreferences.getInstance(appContext)
        TorchMonitor.ensureRegistered(appContext)

        // L’état d’avant est relevé AVANT d’appliquer quoi que ce soit.
        val snapshot = if (routine.restoreOnEnd) captureSnapshot(appContext, prefs, routine) else emptyMap()

        val executedLabels = routine.actions.mapNotNull { action ->
            applyAction(appContext, prefs, routine, action)
        }
        val summary = if (executedLabels.isEmpty()) "Aucune action exécutée" else executedLabels.joinToString(" • ")

        if (routine.notifyOnRun) {
            val text = routine.notifyText
                .replace("{actions}", summary)
                .replace("{routine}", routine.name)
                .ifBlank { summary }
            postRoutineNotification(appContext, routine, routine.notifyTitle.ifBlank { routine.name }, text)
        }

        val entry = AutomateLogEntry(
            timestampMs = System.currentTimeMillis(),
            routineName = routine.name,
            triggerDetail = triggerDetail,
            summary = summary
        )
        prefs.updateAutomateConfig { config ->
            val snapshots = if (routine.restoreOnEnd) {
                // Routine relancée sans être terminée : on garde l’état d’origine le plus ancien.
                val merged = snapshot + (config.snapshots[routine.id] ?: emptyMap())
                config.snapshots + (routine.id to merged)
            } else {
                config.snapshots
            }
            config.copy(
                routines = config.routines.map {
                    if (it.id == routine.id) it.copy(lastTriggeredMs = entry.timestampMs) else it
                },
                logs = (listOf(entry) + config.logs).take(30),
                snapshots = snapshots
            )
        }
        return entry
    }

    /**
     * Rétablit les réglages relevés avant la routine, puis oublie l’instantané.
     * @return l’entrée de journal, ou null si la routine n’était pas en cours.
     */
    suspend fun restoreRoutine(
        context: Context,
        routine: AutomationRoutine,
        reason: String
    ): AutomateLogEntry? {
        val appContext = context.applicationContext
        val prefs = ModuYouPreferences.getInstance(appContext)
        val snapshot = prefs.automateConfigFlow.first().snapshots[routine.id] ?: return null
        val restored = mutableListOf<String>()

        snapshot[K_AOD]?.toIntOrNull()?.let { value ->
            if (putSecureInt(appContext, AOD_SETTING, value)) restored += "Always-On"
        }
        if (snapshot[K_AOD_OVERLAY] == "1") {
            OneHandAccessibilityService.dismissAmbientOverlay()
            closeAmbientDisplay(appContext)
            restored += "Always-On"
        }
        snapshot[K_DND]?.toIntOrNull()?.let { filter ->
            val nm = appContext.getSystemService(NotificationManager::class.java)
            if (nm != null && nm.isNotificationPolicyAccessGranted &&
                runCatching { nm.setInterruptionFilter(filter) }.isSuccess
            ) {
                restored += "Ne pas déranger"
            }
        }
        snapshot[K_RINGER]?.toIntOrNull()?.let { mode ->
            if (setRingerMode(appContext, mode, requiresPolicy = mode == AudioManager.RINGER_MODE_SILENT)) {
                restored += "Sonnerie"
            }
        }
        snapshot[K_VOLUME]?.toIntOrNull()?.let { index ->
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null && runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0) }.isSuccess) {
                restored += "Volume"
            }
        }
        snapshot[K_CAFFEINE]?.let { wasActive ->
            if (wasActive == "true") CaffeineController.activateNow(appContext) else CaffeineController.deactivate(appContext)
            restored += "Caféine"
        }
        snapshot[K_ONE_HAND]?.let { wasEnabled ->
            prefs.setModuleEnabled(OneHandModule.MODULE_ID, wasEnabled == "true")
            restored += "Une Main +"
        }
        snapshot[K_TORCH]?.let { wasOn ->
            if (setTorchState(appContext, wasOn == "true")) restored += "Torche"
        }
        val userRotation = snapshot[K_ROTATION_USER]?.toIntOrNull()
        val autoRotation = snapshot[K_ROTATION_AUTO]?.toIntOrNull()
        if (userRotation != null || autoRotation != null) {
            var ok = true
            userRotation?.let { ok = putSystemInt(appContext, Settings.System.USER_ROTATION, it) && ok }
            autoRotation?.let { ok = putSystemInt(appContext, Settings.System.ACCELEROMETER_ROTATION, it) && ok }
            if (ok) restored += "Rotation"
        }
        snapshot[K_TIMEOUT]?.toIntOrNull()?.let { ms ->
            if (putSystemInt(appContext, Settings.System.SCREEN_OFF_TIMEOUT, ms)) restored += "Mise en veille"
        }
        val brightness = snapshot[K_BRIGHTNESS]?.toIntOrNull()
        val brightnessMode = snapshot[K_BRIGHTNESS_MODE]?.toIntOrNull()
        if (brightness != null || brightnessMode != null) {
            var ok = true
            brightness?.let { ok = putSystemInt(appContext, Settings.System.SCREEN_BRIGHTNESS, it) && ok }
            brightnessMode?.let { ok = putSystemInt(appContext, Settings.System.SCREEN_BRIGHTNESS_MODE, it) && ok }
            if (ok) restored += "Luminosité"
        }

        if (restored.isEmpty()) {
            // Rien à rétablir (ex. routine qui n’affiche qu’une info) : on termine sans bruit.
            prefs.updateAutomateConfig { it.copy(snapshots = it.snapshots - routine.id) }
            return null
        }
        val summary = "Rétabli : " + restored.distinct().joinToString(" • ")
        if (routine.notifyOnRun) {
            postRoutineNotification(appContext, routine, "Fin de « ${routine.name} »", summary)
        }
        val entry = AutomateLogEntry(
            timestampMs = System.currentTimeMillis(),
            routineName = routine.name,
            triggerDetail = reason,
            summary = summary
        )
        prefs.updateAutomateConfig { config ->
            config.copy(
                logs = (listOf(entry) + config.logs).take(30),
                snapshots = config.snapshots - routine.id
            )
        }
        return entry
    }

    private suspend fun captureSnapshot(
        context: Context,
        prefs: ModuYouPreferences,
        routine: AutomationRoutine
    ): Map<String, String> {
        val snap = linkedMapOf(K_ACTIVE_SINCE to System.currentTimeMillis().toString())
        val cr = context.contentResolver
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val groups = routine.actions.map { it.type.group }.toSet()

        fun putIfOk(key: String, read: () -> Any?) {
            runCatching { read() }.getOrNull()?.let { snap[key] = it.toString() }
        }

        if ("aod" in groups) {
            if (AutomateModule.hasWriteSecureSettings(context)) {
                putIfOk(K_AOD) { Settings.Secure.getInt(cr, AOD_SETTING, 0) }
            } else if (routine.actions.any { it.type == RoutineActionType.AOD_ON }) {
                snap[K_AOD_OVERLAY] = "1"
            }
        }
        if ("dnd" in groups) {
            putIfOk(K_DND) { context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter }
        }
        if ("ringer" in groups) putIfOk(K_RINGER) { am?.ringerMode }
        if ("volume" in groups) putIfOk(K_VOLUME) { am?.getStreamVolume(AudioManager.STREAM_MUSIC) }
        if ("caffeine" in groups) snap[K_CAFFEINE] = CaffeineController.isActuallyActive(context).toString()
        if ("onehand" in groups) snap[K_ONE_HAND] = prefs.oneHandConfigFlow.first().masterEnabled.toString()
        if ("torch" in groups) snap[K_TORCH] = TorchMonitor.isOn.toString()
        if ("rotation" in groups) {
            putIfOk(K_ROTATION_AUTO) { Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1) }
            putIfOk(K_ROTATION_USER) { Settings.System.getInt(cr, Settings.System.USER_ROTATION, Surface.ROTATION_0) }
        }
        if ("timeout" in groups) putIfOk(K_TIMEOUT) { Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT) }
        if ("brightness" in groups) {
            putIfOk(K_BRIGHTNESS) { Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS) }
            putIfOk(K_BRIGHTNESS_MODE) { Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE) }
        }
        return snap
    }

    /** Applique une action et renvoie le libellé à inscrire au journal. */
    private suspend fun applyAction(
        context: Context,
        prefs: ModuYouPreferences,
        routine: AutomationRoutine,
        action: RoutineAction
    ): String? {
        if (action.type.needsWriteSettings && !canWriteSystemSettings(context)) {
            return "${action.type.shortLabel} (autorisation système requise)"
        }
        return when (action.type) {
            RoutineActionType.AOD_ON -> when {
                putSecureInt(context, AOD_SETTING, 1) -> "AOD système ON"
                OneHandAccessibilityService.showAmbientOverlay(routine.name) -> "Écran Always-On affiché"
                else -> {
                    launchAmbientDisplay(context, routine.name)
                    "Écran Always-On demandé"
                }
            }
            RoutineActionType.AOD_OFF -> {
                val nativeOk = putSecureInt(context, AOD_SETTING, 0)
                val overlayClosed = OneHandAccessibilityService.dismissAmbientOverlay()
                closeAmbientDisplay(context)
                when {
                    nativeOk -> "AOD système OFF"
                    overlayClosed -> "Écran Always-On fermé"
                    else -> "Always-On arrêté"
                }
            }
            RoutineActionType.DND_ON ->
                if (setDoNotDisturb(context, true)) "Ne pas déranger ON" else "NPD (autorisation requise)"
            RoutineActionType.DND_OFF ->
                if (setDoNotDisturb(context, false)) "Ne pas déranger OFF" else "NPD (autorisation requise)"
            RoutineActionType.RINGER_SILENT ->
                if (setRingerMode(context, AudioManager.RINGER_MODE_SILENT, requiresPolicy = true)) "Silencieux"
                else "Silencieux (accès NPD requis)"
            RoutineActionType.RINGER_VIBRATE ->
                if (setRingerMode(context, AudioManager.RINGER_MODE_VIBRATE, requiresPolicy = false)) "Mode vibreur"
                else "Vibreur (accès NPD requis)"
            RoutineActionType.RINGER_NORMAL ->
                if (setRingerMode(context, AudioManager.RINGER_MODE_NORMAL, requiresPolicy = false)) "Sonnerie normale"
                else "Sonnerie (accès NPD requis)"
            RoutineActionType.MEDIA_VOLUME -> {
                setMediaVolumePercent(context, action.intParam)
                "Volume ${action.intParam} %"
            }
            RoutineActionType.ROTATION_AUTO ->
                if (putSystemInt(context, Settings.System.ACCELEROMETER_ROTATION, 1)) "Rotation auto" else "Rotation (échec)"
            RoutineActionType.ROTATION_PORTRAIT -> lockRotation(context, Surface.ROTATION_0, "Portrait")
            RoutineActionType.ROTATION_LANDSCAPE -> lockRotation(context, Surface.ROTATION_90, "Paysage")
            RoutineActionType.SCREEN_TIMEOUT -> {
                val seconds = action.intParam.coerceIn(5, 30 * 60)
                if (putSystemInt(context, Settings.System.SCREEN_OFF_TIMEOUT, seconds * 1000)) {
                    "Veille ${RoutineAction.formatTimeoutSeconds(seconds)}"
                } else {
                    "Veille (échec)"
                }
            }
            RoutineActionType.BRIGHTNESS_LEVEL -> {
                val value = (action.intParam.coerceIn(1, 100) * 255 / 100f).roundToInt().coerceIn(1, 255)
                val ok = putSystemInt(
                    context,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                ) && putSystemInt(context, Settings.System.SCREEN_BRIGHTNESS, value)
                if (ok) "Luminosité ${action.intParam} %" else "Luminosité (échec)"
            }
            RoutineActionType.BRIGHTNESS_AUTO ->
                if (putSystemInt(
                        context,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                    )
                ) "Luminosité adaptative" else "Luminosité (échec)"
            RoutineActionType.CAFFEINE_ON ->
                if (CaffeineController.activateNow(context)) "Caféine ON" else "Caféine bloquée (optimisation batterie)"
            RoutineActionType.CAFFEINE_OFF -> {
                CaffeineController.deactivate(context)
                "Caféine OFF"
            }
            RoutineActionType.ONE_HAND_ON -> {
                prefs.setModuleEnabled(OneHandModule.MODULE_ID, true)
                "Une Main + ON"
            }
            RoutineActionType.ONE_HAND_OFF -> {
                prefs.setModuleEnabled(OneHandModule.MODULE_ID, false)
                "Une Main + OFF"
            }
            RoutineActionType.TORCH_ON -> if (setTorchState(context, true)) "Torche ON" else "Torche indisponible"
            RoutineActionType.TORCH_OFF -> if (setTorchState(context, false)) "Torche OFF" else "Torche indisponible"
            RoutineActionType.BT_BATTERY -> BluetoothBatteryNotifier.showFor(context, routine)
            // Ancienne action convertie en notification de fin de routine au chargement.
            RoutineActionType.SEND_NOTIFICATION -> null
        }
    }

    private fun lockRotation(context: Context, rotation: Int, label: String): String {
        val ok = putSystemInt(context, Settings.System.ACCELEROMETER_ROTATION, 0) &&
            putSystemInt(context, Settings.System.USER_ROTATION, rotation)
        return if (ok) label else "$label (échec)"
    }

    private fun putSystemInt(context: Context, key: String, value: Int): Boolean {
        if (!canWriteSystemSettings(context)) return false
        return runCatching { Settings.System.putInt(context.contentResolver, key, value) }.getOrDefault(false)
    }

    private fun putSecureInt(context: Context, key: String, value: Int): Boolean {
        if (!AutomateModule.hasWriteSecureSettings(context)) return false
        return runCatching { Settings.Secure.putInt(context.contentResolver, key, value) }.getOrDefault(false)
    }

    private fun launchAmbientDisplay(context: Context, routineName: String) {
        val intent = Intent(context, AmbientDisplayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(AmbientDisplayActivity.EXTRA_ROUTINE_NAME, routineName)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun closeAmbientDisplay(context: Context) {
        context.sendBroadcast(
            Intent(AmbientDisplayActivity.ACTION_CLOSE).setPackage(context.packageName)
        )
    }

    private fun setDoNotDisturb(context: Context, enabled: Boolean): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        if (!nm.isNotificationPolicyAccessGranted) return false
        return runCatching {
            nm.setInterruptionFilter(
                if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            true
        }.getOrDefault(false)
    }

    private fun setMediaVolumePercent(context: Context, percent: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = ((percent.coerceIn(0, 100) / 100f) * maxVol).roundToInt().coerceIn(0, maxVol)
        runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
    }

    private fun setRingerMode(context: Context, mode: Int, requiresPolicy: Boolean): Boolean {
        if (requiresPolicy && !AutomateModule.hasDndPolicyAccess(context)) return false
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return runCatching {
            if (mode == AudioManager.RINGER_MODE_NORMAL) {
                am.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
            }
            am.ringerMode = mode
            if (mode == AudioManager.RINGER_MODE_SILENT && am.ringerMode != AudioManager.RINGER_MODE_SILENT) {
                am.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
            }
            true
        }.getOrDefault(false)
    }

    private fun setTorchState(context: Context, enabled: Boolean): Boolean {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
        return runCatching {
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false
            cm.setTorchMode(cameraId, enabled)
            TorchMonitor.markSet(enabled)
            true
        }.getOrDefault(false)
    }

    private fun postRoutineNotification(
        context: Context,
        routine: AutomationRoutine,
        title: String,
        message: String
    ) {
        val notif = com.example.core.preferences.NotificationSettingsCache.current
        if (!notif.allExecutionNotificationsEnabled || !notif.routineAlertsEnabled) return
        if (!AutomateModule.hasNotificationPermission(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ROUTINE_NOTIF_CHANNEL_ID,
                "Alertes des routines",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications personnalisées affichées après l’exécution d’une routine."
            }
            nm.createNotificationChannel(channel)
        }

        val tapIntent = PendingIntent.getActivity(
            context,
            routine.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, ROUTINE_NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_automate_notif)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
        RoutineStyle.colorFor(routine.colorKey)?.let { color ->
            builder.setColor(android.graphics.Color.argb(
                255,
                (color.red * 255).roundToInt(),
                (color.green * 255).roundToInt(),
                (color.blue * 255).roundToInt()
            ))
        }

        // Identifiant stable par routine : une nouvelle exécution remplace l’ancienne notification.
        runCatching { nm.notify(5000 + (routine.id.hashCode() and 0xFFFF), builder.build()) }
    }
}
