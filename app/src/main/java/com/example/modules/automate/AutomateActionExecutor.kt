package com.example.modules.automate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
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

    suspend fun executeRoutine(
        context: Context,
        routine: AutomationRoutine,
        triggerDetail: String
    ): AutomateLogEntry {
        val appContext = context.applicationContext
        val prefs = ModuYouPreferences.getInstance(appContext)
        val executedLabels = mutableListOf<String>()

        routine.actions.forEach { action ->
            when (action.type) {
                RoutineActionType.AOD_ON -> {
                    val nativeOk = setSystemAlwaysOnDisplay(appContext, enabled = true)
                    if (nativeOk) {
                        executedLabels.add("AOD système ON")
                    } else if (OneHandAccessibilityService.showAmbientOverlay(routine.name)) {
                        executedLabels.add("Écran Always-On affiché")
                    } else {
                        launchAmbientDisplay(appContext, routine.name)
                        executedLabels.add("Écran Always-On lancé")
                    }
                }
                RoutineActionType.AOD_OFF -> {
                    val nativeOk = setSystemAlwaysOnDisplay(appContext, enabled = false)
                    val overlayClosed = OneHandAccessibilityService.dismissAmbientOverlay()
                    closeAmbientDisplay(appContext)
                    executedLabels.add(
                        when {
                            nativeOk -> "AOD système OFF"
                            overlayClosed -> "Écran Always-On fermé"
                            else -> "Always-On arrêté"
                        }
                    )
                }
                RoutineActionType.DND_ON -> {
                    if (setDoNotDisturb(appContext, enabled = true)) {
                        executedLabels.add("Ne pas déranger ON")
                    } else {
                        executedLabels.add("NPD requis (autorisation)")
                    }
                }
                RoutineActionType.DND_OFF -> {
                    if (setDoNotDisturb(appContext, enabled = false)) {
                        executedLabels.add("Ne pas déranger OFF")
                    } else {
                        executedLabels.add("NPD requis (autorisation)")
                    }
                }
                RoutineActionType.RINGER_SILENT -> {
                    if (setRingerMode(appContext, AudioManager.RINGER_MODE_SILENT, requiresPolicy = true)) {
                        executedLabels.add("Silencieux")
                    } else {
                        executedLabels.add("Silencieux (accès NPD requis)")
                    }
                }
                RoutineActionType.RINGER_VIBRATE -> {
                    if (setRingerMode(appContext, AudioManager.RINGER_MODE_VIBRATE, requiresPolicy = false)) {
                        executedLabels.add("Mode vibreur")
                    }
                }
                RoutineActionType.RINGER_NORMAL -> {
                    if (setRingerMode(appContext, AudioManager.RINGER_MODE_NORMAL, requiresPolicy = false)) {
                        executedLabels.add("Sonnerie normale")
                    }
                }
                RoutineActionType.CAFFEINE_ON -> {
                    CaffeineController.activate(appContext)
                    executedLabels.add("Caféine ON")
                }
                RoutineActionType.CAFFEINE_OFF -> {
                    CaffeineController.deactivate(appContext)
                    executedLabels.add("Caféine OFF")
                }
                RoutineActionType.ONE_HAND_ON -> {
                    prefs.setModuleEnabled(OneHandModule.MODULE_ID, true)
                    executedLabels.add("Une Main + ON")
                }
                RoutineActionType.ONE_HAND_OFF -> {
                    prefs.setModuleEnabled(OneHandModule.MODULE_ID, false)
                    executedLabels.add("Une Main + OFF")
                }
                RoutineActionType.TORCH_ON -> {
                    if (setTorchState(appContext, true)) {
                        executedLabels.add("Torche ON")
                    }
                }
                RoutineActionType.TORCH_OFF -> {
                    if (setTorchState(appContext, false)) {
                        executedLabels.add("Torche OFF")
                    }
                }
                RoutineActionType.MEDIA_VOLUME -> {
                    setMediaVolumePercent(appContext, action.intParam)
                    executedLabels.add("Volume ${action.intParam}%")
                }
                RoutineActionType.SEND_NOTIFICATION -> {
                    val notifPrefs = prefs.notificationPreferencesFlow.first()
                    if (notifPrefs.allExecutionNotificationsEnabled && notifPrefs.routinesNotificationsEnabled) {
                        val message = action.textParam.ifBlank { "Routine « ${routine.name} » déclenchée" }
                        postRoutineNotification(appContext, routine.name, message)
                        executedLabels.add("Notification")
                    } else {
                        executedLabels.add("Notif masquée")
                    }
                }
            }
        }

        val summary = if (executedLabels.isEmpty()) {
            "Aucune action exécutée"
        } else {
            executedLabels.joinToString(" • ")
        }

        val entry = AutomateLogEntry(
            timestampMs = System.currentTimeMillis(),
            routineName = routine.name,
            triggerDetail = triggerDetail,
            summary = summary
        )
        prefs.appendAutomateLog(routine.id, entry)
        return entry
    }

    private fun setSystemAlwaysOnDisplay(context: Context, enabled: Boolean): Boolean {
        if (!AutomateModule.hasWriteSecureSettings(context)) return false
        return runCatching {
            Settings.Secure.putInt(context.contentResolver, "doze_always_on", if (enabled) 1 else 0)
        }.getOrDefault(false)
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
        runCatching {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
    }

    private fun setRingerMode(context: Context, mode: Int, requiresPolicy: Boolean): Boolean {
        if (requiresPolicy && !AutomateModule.hasDndPolicyAccess(context)) {
            return false
        }
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
                cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false
            cm.setTorchMode(cameraId, enabled)
            true
        }.getOrDefault(false)
    }

    private fun postRoutineNotification(context: Context, title: String, message: String) {
        if (!AutomateModule.hasNotificationPermission(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ROUTINE_NOTIF_CHANNEL_ID,
                "Alertes des routines",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications envoyées par vos routines."
            }
            nm.createNotificationChannel(channel)
        }

        val tapIntent = PendingIntent.getActivity(
            context,
            title.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(context, ROUTINE_NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_automate_notif)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .build()

        runCatching {
            nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notif)
        }
    }
}
