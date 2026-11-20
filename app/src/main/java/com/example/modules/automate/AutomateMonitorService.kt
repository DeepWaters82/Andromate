package com.example.modules.automate

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.core.preferences.ModuYouPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AutomateMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var preferences: ModuYouPreferences
    private lateinit var connectivityManager: ConnectivityManager

    private var currentConfig = AutomateConfig()
    private var showMonitorNotification = true
    private var lastAppliedNotificationVisibility: Boolean? = null
    private var monitorsRegistered = false
    private var wifiCallbackRegistered = false
    private var bluetoothReceiverRegistered = false
    private var chargingReceiverRegistered = false

    private val activeWifiNetworks = mutableSetOf<Network>()
    private var lastKnownWifiConnected: Boolean? = null
    private var lastKnownWifiName: String = ""
    private var pendingWifiSsidTriggerWindowUntilMs: Long = 0L

    private var lastKnownCharging: Boolean? = null
    private var lastChargingTag: String = ""

    private val recentRoutineDispatchMs = mutableMapOf<String, Long>()

    private val wifiCallback: ConnectivityManager.NetworkCallback by lazy { createWifiCallback() }

    private fun createWifiCallback(): ConnectivityManager.NetworkCallback {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onAvailable(network: Network) = handleWifiAvailable(network)
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                    handleWifiCapabilitiesChanged(network, caps)
                override fun onLost(network: Network) = handleWifiLost(network)
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = handleWifiAvailable(network)
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                    handleWifiCapabilitiesChanged(network, caps)
                override fun onLost(network: Network) = handleWifiLost(network)
            }
        }
    }

    private fun handleWifiAvailable(network: Network) {
        activeWifiNetworks.add(network)
        val wifiName = resolveWifiLabel(network)
        val wasConnected = lastKnownWifiConnected
        val prevName = lastKnownWifiName
        lastKnownWifiConnected = true
        if (wifiName.isNotBlank()) {
            lastKnownWifiName = wifiName
        }

        AutomateController.updateConnectivity {
            it.copy(
                isWifiConnected = true,
                wifiLabel = lastKnownWifiName.ifBlank { "Wi-Fi connecté" }
            )
        }

        val switchedNetwork = wasConnected == true &&
            wifiName.isNotBlank() &&
            prevName.isNotBlank() &&
            !wifiName.equals(prevName, ignoreCase = true)

        if (wasConnected == false || switchedNetwork) {
            if (wifiName.isBlank()) {
                pendingWifiSsidTriggerWindowUntilMs = System.currentTimeMillis() + 5_000L
            } else {
                pendingWifiSsidTriggerWindowUntilMs = 0L
            }
            dispatchTrigger(TriggerType.WIFI_CONNECTED, wifiName)
        }
    }

    private fun handleWifiCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities
    ) {
        activeWifiNetworks.add(network)
        val wifiName = extractSsid(networkCapabilities).ifBlank {
            AutomateModule.resolveCurrentWifiSsid(this@AutomateMonitorService)
        }
        if (wifiName.isNotBlank()) {
            val prevName = lastKnownWifiName
            lastKnownWifiName = wifiName
            AutomateController.updateConnectivity {
                it.copy(isWifiConnected = true, wifiLabel = wifiName)
            }
            if (prevName.isBlank() && System.currentTimeMillis() <= pendingWifiSsidTriggerWindowUntilMs) {
                pendingWifiSsidTriggerWindowUntilMs = 0L
                dispatchTrigger(TriggerType.WIFI_CONNECTED, wifiName, onlyNamedFilter = true)
            }
        }
    }

    private fun handleWifiLost(network: Network) {
        activeWifiNetworks.remove(network)
        if (activeWifiNetworks.isNotEmpty() || AutomateModule.isWifiTransportActive(this)) {
            return
        }
        val prevName = lastKnownWifiName
        val wasConnected = lastKnownWifiConnected
        lastKnownWifiConnected = false
        lastKnownWifiName = ""
        pendingWifiSsidTriggerWindowUntilMs = 0L
        AutomateController.updateConnectivity {
            it.copy(isWifiConnected = false, wifiLabel = "Déconnecté")
        }
        if (wasConnected == true) {
            dispatchTrigger(TriggerType.WIFI_DISCONNECTED, prevName)
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_OFF) {
                    val wasConnected = AutomateController.connectivityState.value.isBluetoothConnected
                    val prevLabel = AutomateController.connectivityState.value.bluetoothLabel
                    AutomateController.updateConnectivity {
                        it.copy(isBluetoothConnected = false, bluetoothLabel = "Aucun appareil")
                    }
                    if (wasConnected) {
                        dispatchTrigger(
                            TriggerType.BLUETOOTH_DISCONNECTED,
                            if (prevLabel == "Aucun appareil" || prevLabel == "Appareil connecté") "" else prevLabel
                        )
                    }
                }
                return
            }

            val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            val deviceName = safeBluetoothDeviceName(device)

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    onBluetoothConnectedEvent(deviceName)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    onBluetoothDisconnectedEvent(deviceName)
                }
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_CONNECTION_STATE,
                        BluetoothAdapter.ERROR
                    )
                    when (state) {
                        BluetoothAdapter.STATE_CONNECTED -> onBluetoothConnectedEvent(deviceName)
                        BluetoothAdapter.STATE_DISCONNECTED -> onBluetoothDisconnectedEvent(deviceName)
                    }
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                    when (state) {
                        BluetoothProfile.STATE_CONNECTED -> onBluetoothConnectedEvent(deviceName)
                        BluetoothProfile.STATE_DISCONNECTED -> onBluetoothDisconnectedEvent(deviceName)
                    }
                }
            }
        }
    }

    private val chargingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                    evaluateChargingState(plugged)
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    // Attend ACTION_BATTERY_CHANGED si plugged vaut encore 0 pour ne pas falsifier USB vs AC
                    val plugged = AutomateModule.readBatteryPluggedFlags(this@AutomateMonitorService)
                    if (plugged != 0) {
                        evaluateChargingState(plugged)
                    }
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    evaluateChargingState(0)
                }
            }
        }
    }

    private fun onBluetoothConnectedEvent(deviceName: String) {
        AutomateController.updateConnectivity {
            it.copy(
                isBluetoothConnected = true,
                bluetoothLabel = deviceName.ifBlank { "Appareil connecté" }
            )
        }
        dispatchTrigger(TriggerType.BLUETOOTH_CONNECTED, deviceName)
    }

    private fun onBluetoothDisconnectedEvent(deviceName: String) {
        AutomateController.updateConnectivity {
            it.copy(
                isBluetoothConnected = false,
                bluetoothLabel = "Aucun appareil"
            )
        }
        dispatchTrigger(TriggerType.BLUETOOTH_DISCONNECTED, deviceName)
    }

    private fun evaluateChargingState(plugged: Int) {
        val isUsbPort = (plugged and BatteryManager.BATTERY_PLUGGED_USB) != 0
        val isAc = (plugged and BatteryManager.BATTERY_PLUGGED_AC) != 0
        val isWireless = (plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0
        val isCharging = plugged != 0

        val currentTag = when {
            isUsbPort -> AutomateConfig.USB_FILTER_USB_ONLY
            isAc -> AutomateConfig.USB_FILTER_AC_ONLY
            isCharging -> "CHARGE"
            else -> ""
        }
        val label = when {
            isUsbPort -> "Charge USB (PC)"
            isAc -> "Charge secteur USB-C"
            isWireless -> "Charge sans fil"
            isCharging -> "En charge"
            else -> "Sur batterie"
        }

        AutomateController.updateConnectivity {
            it.copy(
                isUsbCharging = isCharging,
                chargingLabel = label
            )
        }

        val wasCharging = lastKnownCharging
        val prevTag = lastChargingTag
        lastKnownCharging = isCharging
        if (isCharging) {
            lastChargingTag = currentTag
        }

        if (wasCharging == false && isCharging) {
            dispatchTrigger(TriggerType.USB_CHARGING_CONNECTED, currentTag)
        } else if (wasCharging == true && !isCharging) {
            dispatchTrigger(TriggerType.USB_CHARGING_DISCONNECTED, prevTag)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        preferences = ModuYouPreferences.getInstance(this)
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        createNotificationChannels()

        serviceScope.launch {
            combine(
                preferences.automateConfigFlow,
                preferences.notificationPreferencesFlow
            ) { config, notifPrefs ->
                config to notifPrefs
            }.collect { (config, notifPrefs) ->
                currentConfig = config
                showMonitorNotification = notifPrefs.allExecutionNotificationsEnabled &&
                    notifPrefs.routinesNotificationsEnabled &&
                    config.executionNotificationsEnabled

                if (!monitorsRegistered) {
                    monitorsRegistered = true
                    snapshotInitialConnectivity()
                    registerWifiMonitor()
                    registerBluetoothMonitor()
                    registerChargingMonitor()
                }

                val activeCount = config.routines.count { it.enabled }
                if (!config.masterEnabled || activeCount == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    applyMonitorNotification(activeCount)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val activeCount = currentConfig.routines.count { it.enabled }.coerceAtLeast(1)
        startMonitorForeground(activeCount)
        return START_STICKY
    }

    private fun startMonitorForeground(activeCount: Int) {
        lastAppliedNotificationVisibility = showMonitorNotification
        val notification = buildMonitorNotification(activeCount)
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

    private fun applyMonitorNotification(activeCount: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        if (lastAppliedNotificationVisibility != showMonitorNotification) {
            nm?.cancel(NOTIFICATION_ID)
            startMonitorForeground(activeCount)
        } else {
            nm?.notify(NOTIFICATION_ID, buildMonitorNotification(activeCount))
        }
    }

    private fun snapshotInitialConnectivity() {
        val isWifi = AutomateModule.isWifiTransportActive(this)
        lastKnownWifiConnected = isWifi
        val wifiName = if (isWifi) AutomateModule.resolveCurrentWifiSsid(this) else ""
        lastKnownWifiName = wifiName

        val isBtConnected = AutomateModule.isAnyBluetoothDeviceConnected(this)
        val plugged = AutomateModule.readBatteryPluggedFlags(this)
        val isCharging = plugged != 0
        lastKnownCharging = isCharging
        val isUsbPort = (plugged and BatteryManager.BATTERY_PLUGGED_USB) != 0
        val isAc = (plugged and BatteryManager.BATTERY_PLUGGED_AC) != 0
        val isWireless = (plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0
        lastChargingTag = when {
            isUsbPort -> AutomateConfig.USB_FILTER_USB_ONLY
            isAc -> AutomateConfig.USB_FILTER_AC_ONLY
            isCharging -> "CHARGE"
            else -> ""
        }
        val chargingLabel = when {
            isUsbPort -> "Charge USB (PC)"
            isAc -> "Charge secteur USB-C"
            isWireless -> "Charge sans fil"
            isCharging -> "En charge"
            else -> "Sur batterie"
        }

        AutomateController.updateConnectivity {
            LiveConnectivityState(
                isWifiConnected = isWifi,
                wifiLabel = if (isWifi) wifiName.ifBlank { "Wi-Fi connecté" } else "Déconnecté",
                isBluetoothConnected = isBtConnected,
                bluetoothLabel = if (isBtConnected) "Appareil connecté" else "Aucun appareil",
                isUsbCharging = isCharging,
                chargingLabel = chargingLabel
            )
        }
    }

    private fun registerWifiMonitor() {
        if (wifiCallbackRegistered) return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        runCatching {
            connectivityManager.registerNetworkCallback(request, wifiCallback)
            wifiCallbackRegistered = true
        }
    }

    private fun registerBluetoothMonitor() {
        if (bluetoothReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(
            this,
            bluetoothReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        bluetoothReceiverRegistered = true
    }

    private fun registerChargingMonitor() {
        if (chargingReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        ContextCompat.registerReceiver(
            this,
            chargingReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        chargingReceiverRegistered = true
    }

    private fun dispatchTrigger(
        triggerType: TriggerType,
        eventTargetName: String,
        onlyNamedFilter: Boolean = false
    ) {
        if (!currentConfig.masterEnabled) return
        val nowMs = System.currentTimeMillis()

        val matchingRoutines = currentConfig.routines.filter { routine ->
            if (!routine.enabled || routine.triggerType != triggerType) return@filter false
            if (onlyNamedFilter && routine.filterName.isBlank()) return@filter false
            if (!matchesFilter(routine, eventTargetName)) return@filter false

            val dedupKey = "${routine.id}|${triggerType.name}"
            val lastMs = recentRoutineDispatchMs[dedupKey] ?: 0L
            if (nowMs - lastMs < 1_800L) return@filter false
            recentRoutineDispatchMs[dedupKey] = nowMs
            true
        }
        if (matchingRoutines.isEmpty()) return

        val displayTarget = if (triggerType.isUsbCharging) {
            AutomateConfig.formatFilterDisplay(triggerType, eventTargetName)
        } else {
            eventTargetName
        }

        val detail = if (displayTarget.isBlank()) {
            triggerType.shortLabel
        } else {
            "${triggerType.shortLabel} ($displayTarget)"
        }

        serviceScope.launch {
            matchingRoutines.forEach { routine ->
                AutomateActionExecutor.executeRoutine(
                    context = this@AutomateMonitorService,
                    routine = routine,
                    triggerDetail = detail
                )
            }
        }
    }

    private fun matchesFilter(routine: AutomationRoutine, observedName: String): Boolean {
        val cleanFilter = routine.filterName.trim()
        if (cleanFilter.isEmpty()) return true
        if (routine.triggerType.isUsbCharging) {
            return observedName.equals(cleanFilter, ignoreCase = true)
        }
        return observedName.contains(cleanFilter, ignoreCase = true)
    }

    private fun resolveWifiLabel(network: Network): String {
        val caps = connectivityManager.getNetworkCapabilities(network)
        val fromCaps = if (caps != null) extractSsid(caps) else ""
        if (fromCaps.isNotBlank()) return fromCaps
        return AutomateModule.resolveCurrentWifiSsid(this)
    }

    private fun extractSsid(caps: NetworkCapabilities): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val info = caps.transportInfo as? WifiInfo
            val ssid = AutomateModule.cleanWifiSsid(info?.ssid)
            if (ssid.isNotBlank()) return ssid
        }
        return ""
    }

    @SuppressLint("MissingPermission")
    private fun safeBluetoothDeviceName(device: BluetoothDevice?): String {
        if (device == null) return ""
        if (!AutomateModule.hasBluetoothConnectPermission(this)) return ""
        return runCatching { device.name.orEmpty() }.getOrDefault("")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val visibleChannel = NotificationChannel(
                CHANNEL_ID_VISIBLE,
                "Routines — Surveillance active",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Indique que les routines Wi-Fi, Bluetooth et USB sont actives en arrière-plan."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            val hiddenChannel = NotificationChannel(
                CHANNEL_ID_HIDDEN,
                "Routines — Discret (masqué)",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Surveille les routines sans afficher de notification visible."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            nm.createNotificationChannel(visibleChannel)
            nm.createNotificationChannel(hiddenChannel)
        }
    }

    private fun buildMonitorNotification(activeCount: Int): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            20,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val channelId = if (showMonitorNotification) CHANNEL_ID_VISIBLE else CHANNEL_ID_HIDDEN
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_automate_notif)
            .setContentTitle("Routines actives")
            .setContentText("$activeCount routine${if (activeCount > 1) "s" else ""} en attente (Wi-Fi / BT / USB)")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(!showMonitorNotification)
            .setPriority(if (showMonitorNotification) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN)
            .setVisibility(
                if (showMonitorNotification) NotificationCompat.VISIBILITY_PUBLIC
                else NotificationCompat.VISIBILITY_SECRET
            )
            .setForegroundServiceBehavior(
                if (showMonitorNotification) NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
                else NotificationCompat.FOREGROUND_SERVICE_DEFERRED
            )
            .setContentIntent(openAppIntent)
            .build()
    }

    override fun onDestroy() {
        if (wifiCallbackRegistered) {
            runCatching { connectivityManager.unregisterNetworkCallback(wifiCallback) }
            wifiCallbackRegistered = false
        }
        if (bluetoothReceiverRegistered) {
            runCatching { unregisterReceiver(bluetoothReceiver) }
            bluetoothReceiverRegistered = false
        }
        if (chargingReceiverRegistered) {
            runCatching { unregisterReceiver(chargingReceiver) }
            chargingReceiverRegistered = false
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID_VISIBLE = "automate_monitor_channel_v2"
        private const val CHANNEL_ID_HIDDEN = "automate_monitor_channel_hidden"
        private const val NOTIFICATION_ID = 4103
        private const val ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED =
            "android.bluetooth.action.LE_AUDIO_CONNECTION_STATE_CHANGED"
    }
}
