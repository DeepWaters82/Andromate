package com.example.modules.caffeine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.core.preferences.ModuYouPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CaffeineKeepAwakeService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var timeoutJob: Job? = null
    private var observerJob: Job? = null
    private var screenOffReceiverRegistered = false
    private var disableOnScreenOff = true
    private var hasStarted = false
    private var currentShowStatusIcon: Boolean? = null

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF && disableOnScreenOff) {
                stopAndDeactivate()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        registerScreenOffReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAndDeactivate()
            return START_NOT_STICKY
        }

        hasStarted = true
        val initialShowIcon = intent?.getBooleanExtra(EXTRA_SHOW_STATUS_ICON, true) ?: true
        val initialTimeoutLabel = intent?.getStringExtra(EXTRA_TIMEOUT_LABEL)
        promoteForeground(initialTimeoutLabel, initialShowIcon)

        serviceScope.launch {
            val prefs = ModuYouPreferences.getInstance(this@CaffeineKeepAwakeService)
            val config = prefs.caffeineConfigFlow.first()
            val notifPrefs = prefs.notificationPreferencesFlow.first()
            disableOnScreenOff = config.disableOnScreenOff

            if (!config.isActive) {
                prefs.updateCaffeineConfig {
                    it.copy(isActive = true, activeSinceEpochMs = System.currentTimeMillis())
                }
            }

            acquireScreenWakeLock(keepBright = config.keepBright)
            val shouldShowStatusIcon = notifPrefs.allExecutionNotificationsEnabled && config.showStatusBarIcon
            updateNotification(config.timeoutMode.shortLabel, shouldShowStatusIcon)
            scheduleTimeoutIfNeeded(config.timeoutMode.durationMs)
            CaffeineController.requestTileSync(this@CaffeineKeepAwakeService)
            startPreferenceObserver(prefs)
        }

        return START_STICKY
    }

    private fun startPreferenceObserver(prefs: ModuYouPreferences) {
        if (observerJob != null) return
        observerJob = serviceScope.launch {
            combine(
                prefs.caffeineConfigFlow,
                prefs.notificationPreferencesFlow
            ) { config, notifPrefs ->
                config to notifPrefs
            }.collect { (config, notifPrefs) ->
                if (!hasStarted || !config.isActive) return@collect
                disableOnScreenOff = config.disableOnScreenOff
                val shouldShowStatusIcon = notifPrefs.allExecutionNotificationsEnabled && config.showStatusBarIcon
                updateNotification(config.timeoutMode.shortLabel, shouldShowStatusIcon)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireScreenWakeLock(keepBright: Boolean) {
        releaseWakeLock()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val level = if (keepBright) {
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK
        } else {
            PowerManager.SCREEN_DIM_WAKE_LOCK
        }
        val lock = pm.newWakeLock(
            level or PowerManager.ON_AFTER_RELEASE,
            "Andromate:CaffeineWakeLock"
        )
        lock.setReferenceCounted(false)
        lock.acquire()
        wakeLock = lock
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
    }

    private fun scheduleTimeoutIfNeeded(durationMs: Long?) {
        timeoutJob?.cancel()
        if (durationMs == null) return
        timeoutJob = serviceScope.launch {
            delay(durationMs)
            stopAndDeactivate()
        }
    }

    private fun stopAndDeactivate() {
        hasStarted = false
        observerJob?.cancel()
        observerJob = null
        timeoutJob?.cancel()
        releaseWakeLock()
        serviceScope.launch {
            ModuYouPreferences.getInstance(this@CaffeineKeepAwakeService).updateCaffeineConfig {
                it.copy(isActive = false, activeSinceEpochMs = null)
            }
            CaffeineController.requestTileSync(this@CaffeineKeepAwakeService)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun registerScreenOffReceiver() {
        if (screenOffReceiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenOffReceiverRegistered = true
    }

    private fun unregisterScreenOffReceiver() {
        if (!screenOffReceiverRegistered) return
        runCatching { unregisterReceiver(screenOffReceiver) }
        screenOffReceiverRegistered = false
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val statusBarChannel = NotificationChannel(
                CHANNEL_ID_STATUS_BAR,
                "Caféine — Icône barre d'état",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Affiche l'icône tasse Caféine dans la barre d'état tant que l'écran reste allumé."
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }
            val hiddenChannel = NotificationChannel(
                CHANNEL_ID_HIDDEN,
                "Caféine — Discret (masqué)",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Maintient Caféine actif sans icône dans la barre d'état."
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }
            nm.createNotificationChannel(statusBarChannel)
            nm.createNotificationChannel(hiddenChannel)
        }
    }

    private fun promoteForeground(timeoutLabel: String?, showStatusBarIcon: Boolean) {
        val notification = buildNotification(timeoutLabel, showStatusBarIcon)
        currentShowStatusIcon = showStatusBarIcon
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(timeoutLabel: String?, showStatusBarIcon: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        if (currentShowStatusIcon != null && currentShowStatusIcon != showStatusBarIcon) {
            nm.cancel(NOTIFICATION_ID)
            promoteForeground(timeoutLabel, showStatusBarIcon)
        } else {
            currentShowStatusIcon = showStatusBarIcon
            nm.notify(NOTIFICATION_ID, buildNotification(timeoutLabel, showStatusBarIcon))
        }
    }

    private fun buildNotification(timeoutLabel: String?, showStatusBarIcon: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, CaffeineKeepAwakeService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = buildString {
            append("L'écran reste allumé")
            if (!timeoutLabel.isNullOrBlank()) {
                append(" ($timeoutLabel)")
            }
            append(" • Power ou tuile pour arrêter")
        }

        val channelId = if (showStatusBarIcon) CHANNEL_ID_STATUS_BAR else CHANNEL_ID_HIDDEN
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_tile_caffeine)
            .setContentTitle("Caféine active")
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(if (showStatusBarIcon) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(
                if (showStatusBarIcon) NotificationCompat.VISIBILITY_PUBLIC
                else NotificationCompat.VISIBILITY_SECRET
            )
            .setContentIntent(openAppIntent)
            .addAction(
                R.drawable.ic_tile_caffeine,
                "Désactiver",
                stopIntent
            )
            .build()
    }

    override fun onDestroy() {
        hasStarted = false
        observerJob?.cancel()
        timeoutJob?.cancel()
        releaseWakeLock()
        unregisterScreenOffReceiver()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.modules.caffeine.ACTION_START"
        const val ACTION_STOP = "com.example.modules.caffeine.ACTION_STOP"
        const val EXTRA_SHOW_STATUS_ICON = "extra_show_status_icon"
        const val EXTRA_TIMEOUT_LABEL = "extra_timeout_label"
        private const val CHANNEL_ID_STATUS_BAR = "caffeine_status_bar_channel_v3"
        private const val CHANNEL_ID_HIDDEN = "caffeine_hidden_channel"
        private const val NOTIFICATION_ID = 4102
    }
}
