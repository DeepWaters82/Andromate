package com.example.modules.onehand

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

/**
 * Noms affichés dans les pilules Wi-Fi et Bluetooth du menu d’outils.
 * - Bluetooth : appareils connectés d’après les profils audio d’Android (autorisation
 *   « Appareils à proximité »).
 * - Wi-Fi : Android ne donne le nom du réseau qu’avec la localisation. Sinon, on reprend le
 *   nom lu sur la tuile Wi-Fi du volet de réglages, mémorisé pour le réseau en cours.
 */
object ConnectionNames {

    // ---- Bluetooth ----

    private val proxies = ConcurrentHashMap<Int, BluetoothProfile>()
    private const val PROFILE_LE_AUDIO = 22
    private const val PROFILE_HEARING_AID = 21

    fun hasBluetoothPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Ouvre (une fois) les profils audio ; leurs appareils connectés se lisent ensuite sans attente. */
    fun ensureBluetoothProfiles(context: Context) {
        if (!hasBluetoothPermission(context)) return
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        val wanted = buildList {
            add(BluetoothProfile.A2DP)
            add(BluetoothProfile.HEADSET)
            add(PROFILE_HEARING_AID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(PROFILE_LE_AUDIO)
        }
        wanted.filter { !proxies.containsKey(it) }.forEach { profile ->
            runCatching {
                adapter.getProfileProxy(context.applicationContext, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                        proxies[p] = proxy
                    }

                    override fun onServiceDisconnected(p: Int) {
                        proxies.remove(p)
                    }
                }, profile)
            }
        }
    }

    @Suppress("MissingPermission")
    fun connectedBluetoothNames(context: Context): List<String> {
        if (!hasBluetoothPermission(context)) return emptyList()
        ensureBluetoothProfiles(context)
        val fromProfiles = proxies.values.flatMap { proxy ->
            runCatching { proxy.connectedDevices }.getOrDefault(emptyList())
        }.mapNotNull { device -> runCatching { device.name }.getOrNull()?.trim()?.takeIf(String::isNotBlank) }
        val fromBonded = runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
                .filter { device ->
                    runCatching { device.javaClass.getMethod("isConnected").invoke(device) as Boolean }.getOrDefault(false)
                }
                .mapNotNull { it.name?.trim()?.takeIf(String::isNotBlank) }
        }.getOrDefault(emptyList())
        val fromMonitor = com.example.modules.automate.AutomateController.connectedBluetooth.value.values.filter { it.isNotBlank() }
        return (fromProfiles + fromBonded + fromMonitor).distinct()
    }

    // ---- Wi-Fi ----

    private const val PREFS = "connection_names"
    private const val KEY_NETWORK = "wifi_network"
    private const val KEY_SSID = "wifi_ssid"

    /** Identifiant du réseau Wi-Fi en cours (change à chaque connexion), sans localisation. */
    fun currentWifiNetworkKey(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        val wifi = (listOfNotNull(cm.activeNetwork) + cm.allNetworks).firstOrNull { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        return wifi?.toString()
    }

    fun rememberWifiName(context: Context, ssid: String) {
        val key = currentWifiNetworkKey(context) ?: return
        if (ssid.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_NETWORK, key).putString(KEY_SSID, ssid).apply()
        com.example.modules.automate.AutomateModule.rememberWifiNetwork(context, ssid)
    }

    /** Nom du réseau en cours : par Android si possible, sinon le nom mémorisé pour ce réseau. */
    fun currentWifiName(context: Context): String? {
        val direct = com.example.modules.automate.AutomateModule.resolveCurrentWifiSsid(context)
        if (direct.isNotBlank()) return direct
        val key = currentWifiNetworkKey(context) ?: return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SSID, null)?.takeIf { prefs.getString(KEY_NETWORK, null) == key }
    }

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
