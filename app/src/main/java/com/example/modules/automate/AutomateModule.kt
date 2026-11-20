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
import android.provider.Settings
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
    val hasLocationPermission: Boolean = false
)

object AutomateModule : GoodLockModule {
    const val MODULE_ID = "automate"
    const val ROUTE = "module/automate"

    override val descriptor: ModuleDescriptor = ModuleDescriptor(
        id = MODULE_ID,
        title = "Routines",
        subtitle = "Modes & routines Wi-Fi / BT / USB",
        description = "Créez des routines automatiques : Always-On Display, Ne pas déranger, Silencieux, Caféine ou volume dès qu'un Wi-Fi, appareil Bluetooth ou câble USB change d'état.",
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
        val hasLoc = hasLocationPermission(appContext)

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

        return AvailableConnections(
            currentWifiSsid = ssid,
            pairedBluetoothDevices = btDevices,
            hasBtPermission = hasBt,
            hasLocationPermission = hasLoc
        )
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
                if (cleaned.isNotBlank()) return cleaned
            }
        }

        @Suppress("DEPRECATION")
        val wifiMgr = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val legacyRaw = runCatching { wifiMgr?.connectionInfo?.ssid }.getOrNull()
        return cleanWifiSsid(legacyRaw)
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

object AutomateController {
    private val _connectivityState = MutableStateFlow(LiveConnectivityState())
    val connectivityState: StateFlow<LiveConnectivityState> = _connectivityState.asStateFlow()

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

    fun syncMonitoringService(context: Context, config: AutomateConfig) {
        val appContext = context.applicationContext
        val shouldMonitor = config.masterEnabled && config.routines.any { it.enabled }
        val intent = Intent(appContext, AutomateMonitorService::class.java)
        if (shouldMonitor) {
            runCatching { ContextCompat.startForegroundService(appContext, intent) }
        } else {
            runCatching { appContext.stopService(intent) }
        }
    }
}
