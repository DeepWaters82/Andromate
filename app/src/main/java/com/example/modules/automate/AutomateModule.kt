package com.example.modules.automate

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoMode
import androidx.core.content.ContextCompat
import com.example.core.module.GoodLockModule
import com.example.core.module.ModuleCategory
import com.example.core.module.ModuleColorRole
import com.example.core.module.ModuleDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AvailableConnections(
    val currentWifiSsid: String? = null,
    val pairedBluetoothDevices: List<String> = emptyList(),
    val hasBtPermission: Boolean = false,
    val hasLocationPermission: Boolean = false,
    /** Réseaux déjà vus par Andromate (Android ne donne plus la liste des réseaux enregistrés). */
    val knownWifiNetworks: List<String> = emptyList(),
    /** Réseaux à portée (dernier balayage du téléphone). */
    val nearbyWifiNetworks: List<String> = emptyList(),
    val locationEnabled: Boolean = true,
    val hasBackgroundLocation: Boolean = true
)

object AutomateModule : GoodLockModule {
    const val MODULE_ID = "automate"
    const val ROUTE = "module/automate"

    override val descriptor: ModuleDescriptor = ModuleDescriptor(
        id = MODULE_ID,
        title = "Modes et routines",
        subtitle = "Modes, automatismes",
        description = "Modes à activer d’un geste, routines automatiques.",
        version = "v1.3",
        category = ModuleCategory.SYSTEM,
        icon = Icons.Rounded.AutoMode,
        colorRole = ModuleColorRole.SECONDARY,
        route = ROUTE,
        capabilities = listOf(
            "WiFiCallback",
            "BluetoothEvents",
            "USBCharging",
            "DND & AOD"
        )
    )

    override fun isSystemReady(context: Context): Boolean = true

    fun hasBluetoothConnectPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    /** Le nom du Wi-Fi (SSID) exige la localisation PRÉCISE : l’approximative ne suffit pas. */
    fun hasPreciseLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Sans localisation « tout le temps », Android masque le SSID quand l’app est en arrière-plan. */
    fun hasBackgroundLocationPermission(context: Context): Boolean {
        if (!hasPreciseLocationPermission(context)) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /** Android 12+ ignore une demande de localisation précise faite sans l’approximative. */
    val LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Une app exemptée d’optimisation de batterie peut démarrer ses services de premier plan
     * depuis l’arrière-plan (Caféine déclenchée par une routine, redémarrage de la surveillance)
     * et n’est pas mise en veille par One UI.
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(direct) }
            .recoverCatching { context.startActivity(fallback) }
    }

    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasDndPolicyAccess(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return nm?.isNotificationPolicyAccessGranted == true
    }

    fun openDndPolicySettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openAppNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun hasWriteSecureSettings(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun queryAvailableConnections(context: Context): AvailableConnections {
        val appContext = context.applicationContext
        val hasBt = hasBluetoothConnectPermission(appContext)
        val hasLoc = hasPreciseLocationPermission(appContext)

        val liveWifi = AutomateController.connectivityState.value.wifiLabel
            .takeIf { it.isNotBlank() && it != "Déconnecté" && it != "Wi-Fi connecté" }
        val ssid = resolveCurrentWifiSsid(appContext).takeIf { it.isNotBlank() } ?: liveWifi

        val btDevices = if (hasBt) {
            val bm = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            runCatching {
                bm?.adapter?.bondedDevices
                    ?.mapNotNull { it.name?.trim()?.takeIf(String::isNotBlank) }
                    ?.distinct()
                    ?.sorted()
                    .orEmpty()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val nearby = if (hasLoc) {
            @Suppress("DEPRECATION")
            runCatching {
                (appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.scanResults
                    ?.sortedByDescending { it.level }
                    ?.mapNotNull { cleanWifiSsid(it.SSID).takeIf(String::isNotBlank) }
                    ?.distinct()
                    .orEmpty()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        return AvailableConnections(
            currentWifiSsid = ssid,
            pairedBluetoothDevices = btDevices,
            hasBtPermission = hasBt,
            hasLocationPermission = hasLoc,
            knownWifiNetworks = knownWifiNetworks(appContext),
            nearbyWifiNetworks = nearby,
            locationEnabled = isLocationEnabled(appContext),
            hasBackgroundLocation = hasBackgroundLocation(appContext)
        )
    }

    private const val WIFI_HISTORY_PREFS = "wifi_history"
    private const val WIFI_HISTORY_KEY = "ssids"

    /** Mémorise un réseau vu, pour le proposer plus tard dans le choix des routines. */
    internal fun rememberWifiNetwork(context: Context, ssid: String) {
        if (ssid.isBlank()) return
        val prefs = context.applicationContext.getSharedPreferences(WIFI_HISTORY_PREFS, Context.MODE_PRIVATE)
        val known = prefs.getStringSet(WIFI_HISTORY_KEY, emptySet()).orEmpty()
        if (ssid in known) return
        prefs.edit().putStringSet(WIFI_HISTORY_KEY, known + ssid).apply()
    }

    fun knownWifiNetworks(context: Context): List<String> =
        context.applicationContext.getSharedPreferences(WIFI_HISTORY_PREFS, Context.MODE_PRIVATE)
            .getStringSet(WIFI_HISTORY_KEY, emptySet()).orEmpty().sortedBy { it.lowercase() }

    fun isLocationEnabled(context: Context): Boolean = runCatching {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        androidx.core.location.LocationManagerCompat.isLocationEnabled(lm)
    }.getOrDefault(true)

    fun hasBackgroundLocation(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun openLocationSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Page d’autorisation de l’appli, où choisir « Toujours autoriser » pour la position. */
    fun openAppPermissionSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    internal fun isWifiTransportActive(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeCaps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        if (activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) return true
        @Suppress("DEPRECATION")
        return cm.allNetworks.any { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    internal fun resolveCurrentWifiSsid(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val networksToCheck = buildList {
                cm.activeNetwork?.let { add(it) }
                @Suppress("DEPRECATION")
                cm.allNetworks.forEach { if (it !in this) add(it) }
            }
            for (network in networksToCheck) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
                val info = caps.transportInfo as? WifiInfo
                val cleaned = cleanWifiSsid(info?.ssid)
                if (cleaned.isNotBlank()) return cleaned.also { rememberWifiNetwork(context, it) }
            }
        }

        @Suppress("DEPRECATION")
        val wifiMgr = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val legacyRaw = runCatching { wifiMgr?.connectionInfo?.ssid }.getOrNull()
        return cleanWifiSsid(legacyRaw).also { if (it.isNotBlank()) rememberWifiNetwork(context, it) }
    }

    internal fun cleanWifiSsid(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim().removePrefix("\"").removeSuffix("\"")
        return if (trimmed.equals("<unknown ssid>", ignoreCase = true) || trimmed == "0x") "" else trimmed
    }

    @SuppressLint("MissingPermission")
    internal fun isAnyBluetoothDeviceConnected(context: Context): Boolean {
        if (!hasBluetoothConnectPermission(context)) return false
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
        val adapter = bm.adapter ?: return false
        if (!adapter.isEnabled) return false
        return runCatching {
            val a2dp = adapter.getProfileConnectionState(BluetoothProfile.A2DP)
            val headset = adapter.getProfileConnectionState(BluetoothProfile.HEADSET)
            val leAudio = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                adapter.getProfileConnectionState(BluetoothProfile.LE_AUDIO)
            } else {
                BluetoothProfile.STATE_DISCONNECTED
            }
            a2dp == BluetoothAdapter.STATE_CONNECTED ||
                headset == BluetoothAdapter.STATE_CONNECTED ||
                leAudio == BluetoothAdapter.STATE_CONNECTED
        }.getOrDefault(false)
    }

    internal fun readBatteryPluggedFlags(context: Context): Int {
        val batteryStatus = ContextCompat.registerReceiver(
            context.applicationContext,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        return batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    }
}

data class LaunchableApp(val packageName: String, val label: String)

object AutomateController {
    private val _connectivityState = MutableStateFlow(LiveConnectivityState())
    val connectivityState: StateFlow<LiveConnectivityState> = _connectivityState.asStateFlow()

    private val _foregroundApp = MutableStateFlow<String?>(null)
    /** Paquet de l’application au premier plan (fourni par le service d’accessibilité). */
    val foregroundApp: StateFlow<String?> = _foregroundApp.asStateFlow()

    private val _connectedBluetooth = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Appareils Bluetooth connectés vus par la surveillance (adresse -> nom). */
    val connectedBluetooth: StateFlow<Map<String, String>> = _connectedBluetooth.asStateFlow()

    internal fun setConnectedBluetooth(devices: Map<String, String>) {
        _connectedBluetooth.value = devices
    }

    fun reportForegroundApp(packageName: String) {
        _foregroundApp.value = packageName
    }

    /** Applications lançables depuis l’écran d’accueil, triées par nom (pour le déclencheur « appli »). */
    fun queryLaunchableApps(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            pm.queryIntentActivities(intent, 0)
                .map { info ->
                    LaunchableApp(
                        packageName = info.activityInfo.packageName,
                        label = info.loadLabel(pm).toString()
                    )
                }
                .filter { it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())
    }

    internal fun updateConnectivity(transform: (LiveConnectivityState) -> LiveConnectivityState) {
        _connectivityState.value = transform(_connectivityState.value)
    }

    fun refreshLiveConnectivity(context: Context) {
        val appContext = context.applicationContext
        val isWifi = AutomateModule.isWifiTransportActive(appContext)
        val wifiName = if (isWifi) AutomateModule.resolveCurrentWifiSsid(appContext) else ""
        val isBtConnected = AutomateModule.isAnyBluetoothDeviceConnected(appContext)
        val plugged = AutomateModule.readBatteryPluggedFlags(appContext)
        val isCharging = plugged != 0
        val isUsbPort = (plugged and BatteryManager.BATTERY_PLUGGED_USB) != 0
        val isAc = (plugged and BatteryManager.BATTERY_PLUGGED_AC) != 0
        val isWireless = (plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0
        val chargingLabel = when {
            isUsbPort -> "Charge USB (PC)"
            isAc -> "Charge secteur USB-C"
            isWireless -> "Charge sans fil"
            isCharging -> "En charge"
            else -> "Sur batterie"
        }

        _connectivityState.value = _connectivityState.value.let { existing ->
            LiveConnectivityState(
                isWifiConnected = isWifi,
                wifiLabel = when {
                    !isWifi -> "Déconnecté"
                    wifiName.isNotBlank() -> wifiName
                    existing.isWifiConnected && existing.wifiLabel != "Déconnecté" -> existing.wifiLabel
                    else -> "Wi-Fi connecté"
                },
                isBluetoothConnected = isBtConnected,
                bluetoothLabel = when {
                    !isBtConnected -> "Aucun appareil"
                    existing.isBluetoothConnected && existing.bluetoothLabel != "Aucun appareil" -> existing.bluetoothLabel
                    else -> "Appareil connecté"
                },
                isUsbCharging = isCharging,
                chargingLabel = chargingLabel
            )
        }
    }

    /** Surveillance utile : un déclencheur à guetter, ou un mode actif à montrer dans la barre d’état. */
    fun shouldMonitor(config: AutomateConfig): Boolean =
        (config.masterEnabled && config.watchedCount > 0) || config.activeMode != null

    /** @return false si Android a refusé de démarrer le service (démarrage depuis l’arrière-plan). */
    fun syncMonitoringService(context: Context, config: AutomateConfig): Boolean {
        val appContext = context.applicationContext
        val intent = Intent(appContext, AutomateMonitorService::class.java)
        return if (shouldMonitor(config)) {
            runCatching { ContextCompat.startForegroundService(appContext, intent) }
                .onFailure { Log.w(TAG, "Impossible de démarrer la surveillance des routines", it) }
                .isSuccess
        } else {
            runCatching { appContext.stopService(intent) }
            true
        }
    }

    private const val TAG = "AutomateController"
}
