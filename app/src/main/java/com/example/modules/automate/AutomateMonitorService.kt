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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class AutomateMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var preferences: ModuYouPreferences
    private lateinit var connectivityManager: ConnectivityManager

    private var currentConfig = AutomateConfig()
    private var showMonitorNotification = true
    // Icône du mode dans la barre d’état (réglage Notifications d’Andromate).
    private var showModeIcon = true
    private var lastAppliedChannel: String? = null
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
    private var lastKnownAnyCharging: Boolean? = null

    // Déclencheurs « à condition » : ids des routines dont la condition est vraie en ce moment.
    private var lastBatteryLevel: Int = -1
    private val batteryConditionMet = mutableSetOf<String>()
    private val activeAppRoutines = mutableSetOf<String>()
    private var knownEnabledRoutineIds: Set<String>? = null
    private val restoringRoutineIds = mutableSetOf<String>()

    private val recentRoutineDispatchMs = mutableMapOf<String, Long>()

    // Appareils Bluetooth connectés (adresse -> nom) : un appareil ne déclenche qu’une fois,
    // même si Android envoie ACL + A2DP + HEADSET pour la même connexion.
    private val connectedBtDevices = linkedMapOf<String, String>()
    private var btConnectedAtStartup = false

    // Le NetworkCallback est appelé sur un thread système : tout est ramené sur le thread
    // principal pour éviter les accès concurrents aux champs ci-dessus.
    private val mainHandler = Handler(Looper.getMainLooper())

    private val wifiCallback: ConnectivityManager.NetworkCallback by lazy { createWifiCallback() }

    private fun createWifiCallback(): ConnectivityManager.NetworkCallback {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onAvailable(network: Network) {
                    mainHandler.post { handleWifiAvailable(network) }
                }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    mainHandler.post { handleWifiCapabilitiesChanged(network, caps) }
                }
                override fun onLost(network: Network) {
                    mainHandler.post { handleWifiLost(network) }
                }
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    mainHandler.post { handleWifiAvailable(network) }
                }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    mainHandler.post { handleWifiCapabilitiesChanged(network, caps) }
                }
                override fun onLost(network: Network) {
                    mainHandler.post { handleWifiLost(network) }
                }
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

        if (switchedNetwork) {
            // Changement de réseau : fin des routines liées à l’ancien réseau.
            endRoutines("Wi-Fi changé ($wifiName)") { routine ->
                routine.triggerType == TriggerType.WIFI_CONNECTED &&
                    routine.filterName.isNotBlank() &&
                    !AutomateConfig.matchesNamedFilter(routine.filterName, wifiName)
            }
        }

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
                    connectedBtDevices.keys.forEach { BluetoothBatteryNotifier.onDisconnected(this@AutomateMonitorService, it) }
                    val names = connectedBtDevices.values.toList()
                    val wasConnected = names.isNotEmpty() || btConnectedAtStartup
                    connectedBtDevices.clear()
                    btConnectedAtStartup = false
                    publishBluetoothState()
                    if (wasConnected) {
                        if (names.isEmpty()) {
                            dispatchTrigger(TriggerType.BLUETOOTH_DISCONNECTED, "")
                        } else {
                            names.forEach { dispatchTrigger(TriggerType.BLUETOOTH_DISCONNECTED, it) }
                        }
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
            val address = device?.address ?: return
            val deviceName = safeBluetoothDeviceName(device)

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    onBluetoothDeviceConnected(address, deviceName)
                }
                BluetoothBatteryNotifier.ACTION_BATTERY_LEVEL_CHANGED -> {
                    val level = intent.getIntExtra(BluetoothBatteryNotifier.EXTRA_BATTERY_LEVEL, -1)
                    BluetoothBatteryNotifier.onBatteryLevelChanged(this@AutomateMonitorService, address, deviceName, level)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    BluetoothBatteryNotifier.onDisconnected(this@AutomateMonitorService, address)
                    // Seule la coupure du lien ACL compte : un profil audio qui se ferme
                    // ne veut pas dire que l’appareil est déconnecté.
                    onBluetoothDeviceDisconnected(address, deviceName)
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        onBluetoothDeviceConnected(address, deviceName)
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
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    if (level >= 0) evaluateBatteryLevel(level * 100 / scale)
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

    private fun onBluetoothDeviceConnected(address: String, deviceName: String) {
        val known = connectedBtDevices[address]
        if (known == null || (known.isBlank() && deviceName.isNotBlank())) {
            connectedBtDevices[address] = deviceName
        }
        publishBluetoothState()
        if (known == null) {
            dispatchTrigger(TriggerType.BLUETOOTH_CONNECTED, deviceName)
        }
    }

    private fun onBluetoothDeviceDisconnected(address: String, deviceName: String) {
        val known = connectedBtDevices.remove(address)
        // Appareil déjà connecté au démarrage du service : on ne connaissait pas son adresse.
        val shouldFire = known != null || btConnectedAtStartup
        if (connectedBtDevices.isEmpty()) btConnectedAtStartup = false
        publishBluetoothState()
        if (shouldFire) {
            dispatchTrigger(TriggerType.BLUETOOTH_DISCONNECTED, known?.ifBlank { deviceName } ?: deviceName)
        }
    }

    private fun publishBluetoothState() {
        val anyConnected = connectedBtDevices.isNotEmpty() || btConnectedAtStartup
        val label = connectedBtDevices.values.lastOrNull { it.isNotBlank() }
            ?: if (anyConnected) "Appareil connecté" else "Aucun appareil"
        AutomateController.updateConnectivity {
            it.copy(isBluetoothConnected = anyConnected, bluetoothLabel = label)
        }
        AutomateController.setConnectedBluetooth(connectedBtDevices.toMap())
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

        // Les déclencheurs « Chargement USB » ne concernent que la charge par câble :
        // la charge sans fil ne doit pas les activer.
        val isWired = isWiredCharging(plugged)
        val wasWired = lastKnownCharging
        val prevTag = lastChargingTag
        lastKnownCharging = isWired
        if (isWired) {
            lastChargingTag = currentTag
        }

        if (wasWired == false && isWired) {
            dispatchTrigger(TriggerType.USB_CHARGING_CONNECTED, currentTag)
        } else if (wasWired == true && !isWired) {
            dispatchTrigger(TriggerType.USB_CHARGING_DISCONNECTED, prevTag)
        }

        // Charge quelle que soit la source (câble, sans fil, station).
        val wasAny = lastKnownAnyCharging
        lastKnownAnyCharging = isCharging
        if (wasAny == false && isCharging) {
            dispatchTrigger(TriggerType.CHARGING_STARTED, label)
        } else if (wasAny == true && !isCharging) {
            dispatchTrigger(TriggerType.CHARGING_STOPPED, "")
        }
    }

    /**
     * Seuils de batterie : la routine se lance quand la condition devient vraie
     * et se termine (retour à l’état précédent) quand elle cesse de l’être.
     */
    private fun evaluateBatteryLevel(level: Int, silent: Boolean = false) {
        lastBatteryLevel = level
        val toRun = mutableListOf<AutomationRoutine>()
        currentConfig.routines.filter { it.triggerType.isBattery }.forEach { routine ->
            val conditionMet = isBatteryConditionMet(routine, level)
            val wasMet = routine.id in batteryConditionMet
            if (conditionMet && !wasMet) {
                batteryConditionMet += routine.id
                if (!silent && routine.enabled) toRun += routine
            } else if (!conditionMet && wasMet) {
                batteryConditionMet -= routine.id
            }
        }
        endRoutines("Batterie à $level %") { routine ->
            routine.triggerType.isBattery && !isBatteryConditionMet(routine, level)
        }
        if (currentConfig.masterEnabled) runRoutines(toRun, "Batterie à $level %")
    }

    private fun isBatteryConditionMet(routine: AutomationRoutine, level: Int): Boolean =
        if (routine.triggerType == TriggerType.BATTERY_BELOW) level <= routine.triggerValue
        else level >= routine.triggerValue

    private fun handleForegroundApp(packageName: String) {
        endRoutines("Application quittée") { routine ->
            routine.triggerType.isApp && routine.filterName != packageName
        }
        activeAppRoutines.retainAll(
            currentConfig.routines.filter { it.triggerType.isApp && it.filterName == packageName }.map { it.id }.toSet()
        )
        if (!currentConfig.masterEnabled) return
        val toRun = currentConfig.routines.filter {
            it.enabled && it.triggerType.isApp && it.filterName == packageName && it.id !in activeAppRoutines
        }
        activeAppRoutines += toRun.map { it.id }
        toRun.groupBy { it.filterLabel.ifBlank { packageName } }.forEach { (label, routines) ->
            runRoutines(routines, "Appli ouverte ($label)")
        }
    }

    /**
     * Termine les routines « en cours » (instantané enregistré) qui vérifient [predicate]
     * en rétablissant les réglages d’avant.
     */
    private fun endRoutines(reason: String, predicate: (AutomationRoutine) -> Boolean) {
        val targets = currentConfig.routines.filter { routine ->
            routine.restoreOnEnd &&
                currentConfig.snapshots.containsKey(routine.id) &&
                routine.id !in restoringRoutineIds &&
                predicate(routine)
        }
        if (targets.isEmpty()) return
        restoringRoutineIds += targets.map { it.id }
        serviceScope.launch {
            targets.forEach { routine ->
                runCatching { AutomateActionExecutor.restoreRoutine(this@AutomateMonitorService, routine, reason) }
                restoringRoutineIds -= routine.id
            }
        }
    }

    /** Termine au démarrage les routines restées « en cours » alors que leur condition a disparu. */
    private fun reconcileRunningRoutines() {
        val wifi = lastKnownWifiConnected == true
        val bt = btConnectedAtStartup
        val wired = lastKnownCharging == true
        val charging = lastKnownAnyCharging == true
        endRoutines("Condition terminée") { routine ->
            when (routine.triggerType) {
                TriggerType.WIFI_CONNECTED -> !wifi
                TriggerType.WIFI_DISCONNECTED -> wifi
                TriggerType.BLUETOOTH_CONNECTED -> !bt
                TriggerType.BLUETOOTH_DISCONNECTED -> bt
                TriggerType.USB_CHARGING_CONNECTED -> !wired
                TriggerType.USB_CHARGING_DISCONNECTED -> wired
                TriggerType.CHARGING_STARTED -> !charging
                TriggerType.CHARGING_STOPPED -> charging
                TriggerType.BATTERY_BELOW, TriggerType.BATTERY_ABOVE ->
                    lastBatteryLevel >= 0 && !isBatteryConditionMet(routine, lastBatteryLevel)
                TriggerType.APP_OPENED, TriggerType.MANUAL -> false
            }
        }
    }

    private fun isWiredCharging(plugged: Int): Boolean {
        val wiredMask = BatteryManager.BATTERY_PLUGGED_USB or
            BatteryManager.BATTERY_PLUGGED_AC or
            BATTERY_PLUGGED_DOCK
        return (plugged and wiredMask) != 0
    }

    /** Liaison du service d’accessibilité (voir [com.example.core.ServiceKeeper]). */
    override fun onBind(intent: Intent?): IBinder? = android.os.Binder()

    /** Notification retirée : service tenu par l’accessibilité, notification masquée par l’utilisateur. */
    private var hiddenByKeeper = false

    override fun onCreate() {
        super.onCreate()
        preferences = ModuYouPreferences.getInstance(this)
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        createNotificationChannels()

        serviceScope.launch {
            combine(
                preferences.automateConfigFlow,
                preferences.notificationPreferencesFlow,
                com.example.core.ServiceKeeper.held
            ) { config, notifPrefs, _ ->
                config to notifPrefs
            }.collect { (config, notifPrefs) ->
                currentConfig = config
                showMonitorNotification = notifPrefs.allExecutionNotificationsEnabled &&
                    notifPrefs.routinesNotificationsEnabled &&
                    config.executionNotificationsEnabled
                showModeIcon = notifPrefs.allExecutionNotificationsEnabled && notifPrefs.modeStatusIconEnabled

                val enabledIds = config.routines.filter { it.enabled }.map { it.id }.toSet()
                if (!monitorsRegistered) {
                    monitorsRegistered = true
                    snapshotInitialConnectivity()
                    reconcileRunningRoutines()
                    registerWifiMonitor()
                    registerBluetoothMonitor()
                    registerChargingMonitor()
                } else {
                    // Routine batterie tout juste activée ou modifiée : on l’évalue tout de suite
                    // (sinon « Batterie ≤ 50 % » à 45 % attendrait la prochaine baisse).
                    val newlyEnabled = enabledIds - (knownEnabledRoutineIds ?: emptySet())
                    batteryConditionMet.removeAll(newlyEnabled)
                    if (lastBatteryLevel >= 0) evaluateBatteryLevel(lastBatteryLevel)
                }
                knownEnabledRoutineIds = enabledIds

                if (!AutomateController.shouldMonitor(config)) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    applyMonitorNotification()
                }
            }
        }

        // Application au premier plan, signalée par le service d’accessibilité Une Main +.
        serviceScope.launch {
            AutomateController.foregroundApp
                .filterNotNull()
                .distinctUntilChanged()
                .collect { pkg ->
                    if (monitorsRegistered) handleForegroundApp(pkg)
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Démarrage au premier plan exigé par Android, puis notification retirée si masquée.
        hiddenByKeeper = false
        startMonitorForeground()
        applyMonitorNotification()
        if (intent?.action == ACTION_STOP_MODE) {
            serviceScope.launch {
                runCatching { ModeController.deactivateActive(this@AutomateMonitorService, "Arrêté (notification)") }
            }
        }
        return START_STICKY
    }

    private fun currentChannel(): String = when {
        currentConfig.activeMode != null && showModeIcon -> CHANNEL_ID_MODE
        currentConfig.activeMode != null -> CHANNEL_ID_HIDDEN
        showMonitorNotification -> CHANNEL_ID_VISIBLE
        else -> CHANNEL_ID_HIDDEN
    }

    private fun startMonitorForeground() {
        lastAppliedChannel = currentChannel()
        val notification = buildMonitorNotification()
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

    private fun applyMonitorNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        if (currentChannel() == CHANNEL_ID_HIDDEN && com.example.core.ServiceKeeper.isHeld(AutomateMonitorService::class.java)) {
            if (!hiddenByKeeper) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                hiddenByKeeper = true
                lastAppliedChannel = null
            }
            return
        }
        if (hiddenByKeeper) {
            hiddenByKeeper = false
            runCatching { startMonitorForeground() }
            return
        }
        if (lastAppliedChannel != currentChannel()) {
            nm?.cancel(NOTIFICATION_ID)
            startMonitorForeground()
        } else {
            nm?.notify(NOTIFICATION_ID, buildMonitorNotification())
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
        lastKnownCharging = isWiredCharging(plugged)
        lastKnownAnyCharging = isCharging
        val batteryManager = getSystemService(BATTERY_SERVICE) as? BatteryManager
        lastBatteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        batteryConditionMet.clear()
        if (lastBatteryLevel in 0..100) evaluateBatteryLevel(lastBatteryLevel, silent = true)
        btConnectedAtStartup = isBtConnected
        connectedBtDevices.clear()
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
            addAction(BluetoothBatteryNotifier.ACTION_BATTERY_LEVEL_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(
            this,
            bluetoothReceiver,
            filter,
            // Doit rester EXPORTED : les annonces Bluetooth viennent de l’appli système Bluetooth
            // (uid 1002) et One UI les refuse aux récepteurs non exportés.
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
            // EXPORTED aussi : sur One UI, BATTERY_CHANGED est refusé aux récepteurs non exportés.
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

        // L’évènement inverse termine les routines en cours (ex. Bluetooth coupé pour une
        // routine « Bluetooth connecté ») et rétablit l’état précédent.
        endRoutines(triggerType.shortLabel) { routine ->
            routine.triggerType.endTrigger == triggerType && endMatches(routine, eventTargetName)
        }

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

        runRoutines(matchingRoutines, detail)
    }

    private fun runRoutines(routines: List<AutomationRoutine>, detail: String) {
        if (routines.isEmpty()) return
        serviceScope.launch {
            routines.forEach { routine ->
                runCatching {
                    if (routine.isMode) {
                        ModeController.activate(this@AutomateMonitorService, routine, detail)
                    } else {
                        AutomateActionExecutor.executeRoutine(
                            context = this@AutomateMonitorService,
                            routine = routine,
                            triggerDetail = detail
                        )
                    }
                }
            }
        }
    }

    /** Filtre plus souple pour la fin : un nom pas encore connu (SSID masqué) compte comme correspondant. */
    private fun endMatches(routine: AutomationRoutine, observedName: String): Boolean {
        val cleanFilter = routine.filterName.trim()
        if (cleanFilter.isEmpty() || observedName.isBlank()) return true
        return matchesFilter(routine, observedName)
    }

    private fun matchesFilter(routine: AutomationRoutine, observedName: String): Boolean {
        val cleanFilter = routine.filterName.trim()
        if (cleanFilter.isEmpty()) return true
        if (routine.triggerType.isUsbCharging) {
            return observedName.equals(cleanFilter, ignoreCase = true)
        }
        return AutomateConfig.matchesNamedFilter(cleanFilter, observedName)
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
            // Importance « aucune » : la notification exigée par Android n’est pas affichée du tout.
            // (L’importance d’un canal est figée à sa création : nouvel identifiant, l’ancien est supprimé.)
            nm.deleteNotificationChannel("automate_monitor_channel_hidden")
            val hiddenChannel = NotificationChannel(
                CHANNEL_ID_HIDDEN,
                "Routines — Masquée",
                NotificationManager.IMPORTANCE_NONE
            ).apply {
                description = "Surveille les routines sans afficher de notification visible."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            val modeChannel = NotificationChannel(
                CHANNEL_ID_MODE,
                "Mode actif",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Icône du mode en cours dans la barre d’état."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            nm.createNotificationChannel(visibleChannel)
            nm.createNotificationChannel(hiddenChannel)
            nm.createNotificationChannel(modeChannel)
        }
    }

    private fun buildMonitorNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            20,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        currentConfig.activeMode?.let { mode -> return buildModeNotification(mode, openAppIntent) }
        val activeCount = currentConfig.watchedCount
        val channelId = if (showMonitorNotification) CHANNEL_ID_VISIBLE else CHANNEL_ID_HIDDEN
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_automate_notif)
            .setContentTitle("Routines actives")
            .setContentText("$activeCount en attente")
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

    /** Mode en cours : son icône s’affiche dans la barre d’état, avec un bouton pour l’arrêter. */
    private fun buildModeNotification(mode: AutomationRoutine, openAppIntent: PendingIntent): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            21,
            Intent(this, AutomateMonitorService::class.java).setAction(ACTION_STOP_MODE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID_MODE)
            .setContentTitle(mode.name)
            .setContentText(mode.actions.joinToString(" • ") { it.displayBadge() }.ifBlank { "Mode actif" })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent)
            .addAction(0, "Arrêter", stopIntent)
        val icon = ModeIconRenderer.iconFor(this, mode)
        if (icon != null) builder.setSmallIcon(icon) else builder.setSmallIcon(R.drawable.ic_automate_notif)
        RoutineStyle.colorFor(mode.colorKey)?.let { color ->
            builder.setColor(
                android.graphics.Color.rgb(
                    (color.red * 255).toInt(),
                    (color.green * 255).toInt(),
                    (color.blue * 255).toInt()
                )
            )
        }
        return builder.build()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
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
        private const val CHANNEL_ID_HIDDEN = "automate_monitor_silent_v2"
        private const val CHANNEL_ID_MODE = "automate_mode_active"
        const val ACTION_STOP_MODE = "com.example.automate.STOP_MODE"
        private const val NOTIFICATION_ID = 4103
        private const val BATTERY_PLUGGED_DOCK = 8 // BatteryManager.BATTERY_PLUGGED_DOCK (API 33)
        private const val ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED =
            "android.bluetooth.action.LE_AUDIO_CONNECTION_STATE_CHANGED"
    }
}
