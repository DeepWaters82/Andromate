package com.example.modules.automate

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.R

/**
 * Action « Batterie de l’appareil Bluetooth » : affiche (puis met à jour) une notification
 * avec la batterie restante des appareils connectés, et la retire à la déconnexion.
 *
 * Android ne publie pas d’API officielle pour ce niveau : on lit BluetoothDevice.getBatteryLevel()
 * et on écoute BATTERY_LEVEL_CHANGED, comme le fait le volet Bluetooth du système.
 * Le niveau n’arrive souvent que quelques secondes après la connexion : on attend jusqu’à 2 min.
 */
object BluetoothBatteryNotifier {
    const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"

    // Canal « élevé » : la première annonce s’affiche en popup, sans son ni vibration.
    // (L’importance d’un canal ne peut plus changer : nouvel identifiant, l’ancien est supprimé.)
    private const val CHANNEL_ID = "bt_battery_popup"
    private const val OLD_CHANNEL_ID = "bt_battery_levels"
    private const val WAIT_FOR_LEVEL_MS = 120_000L

    /** Appareils suivis (adresse -> fin d’attente du premier niveau, ou Long.MAX_VALUE si affiché). */
    private val watched = mutableMapOf<String, Long>()
    private val names = mutableMapOf<String, String>()

    /** Exécuté par une routine. @return le libellé du journal. */
    @SuppressLint("MissingPermission")
    fun showFor(context: Context, routine: AutomationRoutine): String {
        if (!AutomateModule.hasBluetoothConnectPermission(context)) return "Batterie BT (accès Bluetooth requis)"
        val devices = connectedDevices(context).filter { (_, name) ->
            AutomateConfig.matchesNamedFilter(routine.filterName, name)
        }
        if (devices.isEmpty()) return "Batterie BT : aucun appareil"

        val parts = devices.map { (device, name) ->
            val address = device.address
            synchronized(this) {
                names[address] = name
                if (watched[address] != Long.MAX_VALUE) watched[address] = System.currentTimeMillis() + WAIT_FOR_LEVEL_MS
            }
            val level = readLevel(device)
            if (level in 0..100) {
                post(context, address, name, level)
                "$name $level %"
            } else {
                "$name (niveau en attente)"
            }
        }
        return "Batterie : " + parts.joinToString(", ")
    }

    /** Niveau reçu du système pour un appareil. */
    fun onBatteryLevelChanged(context: Context, address: String, name: String, level: Int) {
        if (level !in 0..100) return
        val shouldShow = synchronized(this) {
            val until = watched[address] ?: return
            until == Long.MAX_VALUE || System.currentTimeMillis() <= until
        }
        if (shouldShow) post(context, address, names[address]?.ifBlank { null } ?: name, level)
    }

    fun onDisconnected(context: Context, address: String) {
        val wasWatched = synchronized(this) {
            names.remove(address)
            watched.remove(address) != null
        }
        if (wasWatched) {
            context.getSystemService(NotificationManager::class.java)?.cancel(notificationId(address))
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectedDevices(context: Context): List<Pair<BluetoothDevice, String>> {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        val knownConnected = AutomateController.connectedBluetooth.value
        return runCatching {
            adapter.bondedDevices.orEmpty()
                .filter { device -> isConnected(device) || device.address in knownConnected }
                .map { device -> device to (device.name?.trim().orEmpty().ifBlank { knownConnected[device.address].orEmpty() }) }
        }.getOrDefault(emptyList())
    }

    private fun isConnected(device: BluetoothDevice): Boolean = runCatching {
        BluetoothDevice::class.java.getMethod("isConnected").invoke(device) as Boolean
    }.getOrDefault(false)

    private fun readLevel(device: BluetoothDevice): Int = runCatching {
        BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(device) as Int
    }.getOrDefault(-1)

    /**
     * Routine : si DeepBluetooth affiche déjà sa popup (service actif), pas de notification
     * en double, sauf si l’utilisateur l’a demandée dans DeepBluetooth.
     */
    private fun post(context: Context, address: String, name: String, level: Int) {
        val deep = com.example.modules.deepbluetooth.DeepBluetoothModule.current
        if (deep.enabled && !deep.alsoNotification &&
            com.example.modules.onehand.OneHandModule.isAccessibilityServiceEnabled(context)
        ) {
            synchronized(this) { watched[address] = Long.MAX_VALUE }
            return
        }
        postLevel(context, address, name, level)
    }

    /** Notification avec le niveau et sa barre (DeepBluetooth « aussi dans les notifications »). */
    fun postLevel(context: Context, address: String, name: String, level: Int) {
        if (level !in 0..100) return
        val notif = com.example.core.preferences.NotificationSettingsCache.current
        if (!notif.allExecutionNotificationsEnabled || !notif.bluetoothBatteryEnabled) return
        if (!AutomateModule.hasNotificationPermission(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.deleteNotificationChannel(OLD_CHANNEL_ID)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Batterie Bluetooth", NotificationManager.IMPORTANCE_HIGH).apply {
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
        synchronized(this) { watched[address] = Long.MAX_VALUE }
        val iconPx = (24 * context.resources.displayMetrics.density).toInt()
        val smallIcon = runCatching {
            IconCompat.createWithBitmap(
                ModeIconRenderer.renderTinted(Icons.Rounded.Headphones, iconPx, android.graphics.Color.WHITE)
            )
        }.getOrNull()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("${name.ifBlank { "Appareil Bluetooth" }} • $level %")
            // Le pourcentage figure aussi dans le texte et sur la jauge : la popup de One UI
            // n’affiche pas toujours le titre en entier.
            .setContentText("Batterie $level %")
            .setProgress(100, level, false)
            // Popup à la première annonce seulement : les mises à jour du niveau restent discrètes.
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        if (smallIcon != null) builder.setSmallIcon(smallIcon) else builder.setSmallIcon(R.drawable.ic_automate_notif)
        val notification = builder.build()
        runCatching { nm.notify(notificationId(address), notification) }
    }

    private fun notificationId(address: String) = 7000 + (address.hashCode() and 0xFFF)
}
