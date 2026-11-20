package com.example.modules.automate

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DoNotDisturbOff
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.ui.graphics.vector.ImageVector

enum class TriggerCategory {
    WIFI,
    BLUETOOTH,
    USB_CHARGING
}

enum class TriggerType(
    val label: String,
    val shortLabel: String,
    val icon: ImageVector,
    val category: TriggerCategory
) {
    WIFI_CONNECTED(
        label = "Connexion Wi-Fi",
        shortLabel = "Wi-Fi connecté",
        icon = Icons.Rounded.Wifi,
        category = TriggerCategory.WIFI
    ),
    WIFI_DISCONNECTED(
        label = "Déconnexion Wi-Fi",
        shortLabel = "Wi-Fi coupé",
        icon = Icons.Rounded.WifiOff,
        category = TriggerCategory.WIFI
    ),
    BLUETOOTH_CONNECTED(
        label = "Connexion Bluetooth",
        shortLabel = "Bluetooth connecté",
        icon = Icons.Rounded.BluetoothConnected,
        category = TriggerCategory.BLUETOOTH
    ),
    BLUETOOTH_DISCONNECTED(
        label = "Déconnexion Bluetooth",
        shortLabel = "Bluetooth coupé",
        icon = Icons.Rounded.BluetoothDisabled,
        category = TriggerCategory.BLUETOOTH
    ),
    USB_CHARGING_CONNECTED(
        label = "Chargement USB branché",
        shortLabel = "Charge USB ON",
        icon = Icons.Rounded.Usb,
        category = TriggerCategory.USB_CHARGING
    ),
    USB_CHARGING_DISCONNECTED(
        label = "Chargement USB débranché",
        shortLabel = "Charge USB OFF",
        icon = Icons.Rounded.PowerOff,
        category = TriggerCategory.USB_CHARGING
    );

    val isBluetooth: Boolean get() = category == TriggerCategory.BLUETOOTH
    val isWifi: Boolean get() = category == TriggerCategory.WIFI
    val isUsbCharging: Boolean get() = category == TriggerCategory.USB_CHARGING
}

enum class RoutineActionType(
    val label: String,
    val shortLabel: String,
    val icon: ImageVector
) {
    AOD_ON(
        label = "Activer Always-On Display",
        shortLabel = "AOD ON",
        icon = Icons.Rounded.WbTwilight
    ),
    AOD_OFF(
        label = "Désactiver Always-On Display",
        shortLabel = "AOD OFF",
        icon = Icons.Rounded.BrightnessLow
    ),
    DND_ON(
        label = "Activer Ne pas déranger",
        shortLabel = "NPD ON",
        icon = Icons.Rounded.DoNotDisturbOn
    ),
    DND_OFF(
        label = "Désactiver Ne pas déranger",
        shortLabel = "NPD OFF",
        icon = Icons.Rounded.DoNotDisturbOff
    ),
    RINGER_SILENT(
        label = "Mode silencieux",
        shortLabel = "Silencieux",
        icon = Icons.AutoMirrored.Rounded.VolumeOff
    ),
    RINGER_VIBRATE(
        label = "Mode vibreur",
        shortLabel = "Vibreur",
        icon = Icons.Rounded.Vibration
    ),
    RINGER_NORMAL(
        label = "Sonnerie normale",
        shortLabel = "Sonnerie",
        icon = Icons.Rounded.NotificationsActive
    ),
    CAFFEINE_ON(
        label = "Activer Caféine (écran allumé)",
        shortLabel = "Caféine ON",
        icon = Icons.Rounded.Coffee
    ),
    CAFFEINE_OFF(
        label = "Désactiver Caféine",
        shortLabel = "Caféine OFF",
        icon = Icons.Rounded.Bedtime
    ),
    ONE_HAND_ON(
        label = "Activer Une Main +",
        shortLabel = "Une Main + ON",
        icon = Icons.Rounded.SwipeRight
    ),
    ONE_HAND_OFF(
        label = "Désactiver Une Main +",
        shortLabel = "Une Main + OFF",
        icon = Icons.Rounded.NightsStay
    ),
    MEDIA_VOLUME(
        label = "Régler le volume média",
        shortLabel = "Volume",
        icon = Icons.AutoMirrored.Rounded.VolumeUp
    ),
    TORCH_ON(
        label = "Allumer la lampe torche",
        shortLabel = "Torche ON",
        icon = Icons.Rounded.FlashlightOn
    ),
    TORCH_OFF(
        label = "Éteindre la lampe torche",
        shortLabel = "Torche OFF",
        icon = Icons.Rounded.FlashlightOff
    ),
    SEND_NOTIFICATION(
        label = "Afficher une notification",
        shortLabel = "Notification",
        icon = Icons.Rounded.Notifications
    )
}

data class RoutineAction(
    val type: RoutineActionType,
    val intParam: Int = 75,
    val textParam: String = ""
) {
    fun displayBadge(): String {
        return when (type) {
            RoutineActionType.MEDIA_VOLUME -> "Volume $intParam%"
            RoutineActionType.SEND_NOTIFICATION -> {
                if (textParam.isBlank()) "Notification" else "Notif : $textParam"
            }
            else -> type.shortLabel
        }
    }
}

data class AutomationRoutine(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val triggerType: TriggerType,
    val filterName: String = "",
    val actions: List<RoutineAction>,
    val lastTriggeredMs: Long? = null
)

data class AutomateLogEntry(
    val timestampMs: Long,
    val routineName: String,
    val triggerDetail: String,
    val summary: String
)

data class LiveConnectivityState(
    val isWifiConnected: Boolean = false,
    val wifiLabel: String = "Déconnecté",
    val isBluetoothConnected: Boolean = false,
    val bluetoothLabel: String = "Aucun appareil",
    val isUsbCharging: Boolean = false,
    val chargingLabel: String = "Sur batterie"
)

data class AutomateConfig(
    val masterEnabled: Boolean = true,
    val executionNotificationsEnabled: Boolean = true,
    val routines: List<AutomationRoutine> = defaultRoutines(),
    val logs: List<AutomateLogEntry> = emptyList()
) {
    companion object {
        const val USB_FILTER_ANY = ""
        const val USB_FILTER_USB_ONLY = "USB_ONLY"
        const val USB_FILTER_AC_ONLY = "AC_ONLY"

        fun formatFilterDisplay(triggerType: TriggerType, filterName: String): String {
            return when {
                triggerType.isUsbCharging -> when (filterName) {
                    USB_FILTER_USB_ONLY -> "Port USB PC"
                    USB_FILTER_AC_ONLY -> "Secteur USB-C"
                    else -> "Tout câble USB"
                }
                filterName.isBlank() -> "Tous"
                else -> filterName
            }
        }

        fun defaultRoutines(): List<AutomationRoutine> = listOf(
            AutomationRoutine(
                id = "preset_car_bt",
                name = "Mode Trajet / Audio",
                enabled = false,
                triggerType = TriggerType.BLUETOOTH_CONNECTED,
                filterName = "",
                actions = listOf(
                    RoutineAction(RoutineActionType.MEDIA_VOLUME, intParam = 80),
                    RoutineAction(RoutineActionType.CAFFEINE_ON)
                )
            ),
            AutomationRoutine(
                id = "preset_usb_desk",
                name = "Bureau en charge USB",
                enabled = false,
                triggerType = TriggerType.USB_CHARGING_CONNECTED,
                filterName = USB_FILTER_ANY,
                actions = listOf(
                    RoutineAction(RoutineActionType.CAFFEINE_ON)
                )
            ),
            AutomationRoutine(
                id = "preset_home_wifi",
                name = "Mode Maison",
                enabled = false,
                triggerType = TriggerType.WIFI_CONNECTED,
                filterName = "",
                actions = listOf(
                    RoutineAction(RoutineActionType.DND_OFF),
                    RoutineAction(RoutineActionType.RINGER_NORMAL),
                    RoutineAction(RoutineActionType.ONE_HAND_ON)
                )
            ),
            AutomationRoutine(
                id = "preset_away_wifi",
                name = "Départ silencieux",
                enabled = false,
                triggerType = TriggerType.WIFI_DISCONNECTED,
                filterName = "",
                actions = listOf(
                    RoutineAction(RoutineActionType.RINGER_SILENT),
                    RoutineAction(RoutineActionType.CAFFEINE_OFF)
                )
            )
        )
    }
}
