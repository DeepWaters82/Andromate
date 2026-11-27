package com.example.modules.automate

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Battery5Bar
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DoNotDisturbOff
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StayCurrentLandscape
import androidx.compose.material.icons.rounded.StayCurrentPortrait
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class TriggerCategory {
    WIFI,
    BLUETOOTH,
    USB_CHARGING,
    CHARGING,
    BATTERY,
    APP,
    MANUAL
}

enum class TriggerType(
    val label: String,
    val shortLabel: String,
    val icon: ImageVector,
    val category: TriggerCategory
) {
    WIFI_CONNECTED("Wi-Fi connecté", "Wi-Fi connecté", Icons.Rounded.Wifi, TriggerCategory.WIFI),
    WIFI_DISCONNECTED("Wi-Fi déconnecté", "Wi-Fi coupé", Icons.Rounded.WifiOff, TriggerCategory.WIFI),
    BLUETOOTH_CONNECTED("Bluetooth connecté", "Bluetooth connecté", Icons.Rounded.BluetoothConnected, TriggerCategory.BLUETOOTH),
    BLUETOOTH_DISCONNECTED("Bluetooth déconnecté", "Bluetooth coupé", Icons.Rounded.BluetoothDisabled, TriggerCategory.BLUETOOTH),
    USB_CHARGING_CONNECTED("Câble USB branché", "Charge USB ON", Icons.Rounded.Usb, TriggerCategory.USB_CHARGING),
    USB_CHARGING_DISCONNECTED("Câble USB débranché", "Charge USB OFF", Icons.Rounded.PowerOff, TriggerCategory.USB_CHARGING),
    CHARGING_STARTED("Charge démarrée", "En charge", Icons.Rounded.BatteryChargingFull, TriggerCategory.CHARGING),
    CHARGING_STOPPED("Charge arrêtée", "Sur batterie", Icons.Rounded.BatteryStd, TriggerCategory.CHARGING),
    BATTERY_BELOW("Batterie faible", "Batterie basse", Icons.Rounded.BatteryAlert, TriggerCategory.BATTERY),
    BATTERY_ABOVE("Batterie élevée", "Batterie haute", Icons.Rounded.BatteryFull, TriggerCategory.BATTERY),
    /** Pendant la charge, quand la batterie atteint le seuil (une fois par charge). */
    CHARGE_REACHED("Charge atteinte", "Charge atteinte", Icons.Rounded.BatteryChargingFull, TriggerCategory.BATTERY),
    APP_OPENED("Appli ouverte", "Appli ouverte", Icons.Rounded.Apps, TriggerCategory.APP),
    /** Modes uniquement : activé à la main depuis l’écran Modes. */
    MANUAL("Manuel", "Manuel", Icons.Rounded.TouchApp, TriggerCategory.MANUAL);

    val isBluetooth: Boolean get() = category == TriggerCategory.BLUETOOTH
    val isWifi: Boolean get() = category == TriggerCategory.WIFI
    val isUsbCharging: Boolean get() = category == TriggerCategory.USB_CHARGING
    val isBattery: Boolean get() = category == TriggerCategory.BATTERY
    val isApp: Boolean get() = category == TriggerCategory.APP

    /**
     * Évènement inverse qui termine la routine (et rétablit l’état précédent si demandé).
     * Batterie et application sont gérées à part : elles se terminent quand la condition cesse.
     */
    val endTrigger: TriggerType?
        get() = when (this) {
            WIFI_CONNECTED -> WIFI_DISCONNECTED
            WIFI_DISCONNECTED -> WIFI_CONNECTED
            BLUETOOTH_CONNECTED -> BLUETOOTH_DISCONNECTED
            BLUETOOTH_DISCONNECTED -> BLUETOOTH_CONNECTED
            USB_CHARGING_CONNECTED -> USB_CHARGING_DISCONNECTED
            USB_CHARGING_DISCONNECTED -> USB_CHARGING_CONNECTED
            CHARGING_STARTED -> CHARGING_STOPPED
            CHARGING_STOPPED -> CHARGING_STARTED
            BATTERY_BELOW, BATTERY_ABOVE, CHARGE_REACHED, APP_OPENED, MANUAL -> null
        }

    /** Libellé de la fin de routine, affiché dans l’éditeur. */
    val endDescription: String
        get() = when (this) {
            WIFI_CONNECTED -> "à la déconnexion du Wi-Fi"
            WIFI_DISCONNECTED -> "à la reconnexion du Wi-Fi"
            BLUETOOTH_CONNECTED -> "à la déconnexion de l’appareil"
            BLUETOOTH_DISCONNECTED -> "à la reconnexion de l’appareil"
            USB_CHARGING_CONNECTED -> "au débranchement du câble"
            USB_CHARGING_DISCONNECTED -> "au rebranchement du câble"
            CHARGING_STARTED -> "à la fin de la charge"
            CHARGING_STOPPED -> "à la reprise de la charge"
            BATTERY_BELOW -> "quand la batterie remonte au-dessus du seuil"
            BATTERY_ABOVE -> "quand la batterie repasse sous le seuil"
            CHARGE_REACHED -> "au débranchement du chargeur"
            APP_OPENED -> "quand vous quittez l’application"
            MANUAL -> "à l’arrêt du mode"
        }
}

enum class RoutineActionType(
    val label: String,
    val shortLabel: String,
    val icon: ImageVector,
    /** Les actions d’un même groupe s’excluent (ex. Torche ON / Torche OFF). */
    val group: String,
    /** Exige l’autorisation « Modifier les paramètres système ». */
    val needsWriteSettings: Boolean = false,
    /** false : action conservée pour la compatibilité mais plus proposée dans l’éditeur. */
    val selectable: Boolean = true
) {
    AOD_ON("Always-On activé", "AOD ON", Icons.Rounded.WbTwilight, "aod"),
    AOD_OFF("Always-On désactivé", "AOD OFF", Icons.Rounded.BrightnessLow, "aod"),
    DND_ON("Ne pas déranger activé", "NPD ON", Icons.Rounded.DoNotDisturbOn, "dnd"),
    DND_OFF("Ne pas déranger désactivé", "NPD OFF", Icons.Rounded.DoNotDisturbOff, "dnd"),
    RINGER_SILENT("Silencieux", "Silencieux", Icons.AutoMirrored.Rounded.VolumeOff, "ringer"),
    RINGER_VIBRATE("Vibreur", "Vibreur", Icons.Rounded.Vibration, "ringer"),
    RINGER_NORMAL("Sonnerie", "Sonnerie", Icons.Rounded.NotificationsActive, "ringer"),
    MEDIA_VOLUME("Volume média", "Volume", Icons.AutoMirrored.Rounded.VolumeUp, "volume"),
    ROTATION_AUTO("Rotation auto", "Rotation auto", Icons.Rounded.ScreenRotation, "rotation", needsWriteSettings = true),
    ROTATION_PORTRAIT("Portrait fixe", "Portrait", Icons.Rounded.StayCurrentPortrait, "rotation", needsWriteSettings = true),
    ROTATION_LANDSCAPE("Paysage fixe", "Paysage", Icons.Rounded.StayCurrentLandscape, "rotation", needsWriteSettings = true),
    SCREEN_TIMEOUT("Mise en veille", "Veille", Icons.Rounded.Timer, "timeout", needsWriteSettings = true),
    BRIGHTNESS_LEVEL("Luminosité", "Luminosité", Icons.Rounded.BrightnessMedium, "brightness", needsWriteSettings = true),
    BRIGHTNESS_AUTO("Luminosité adaptative", "Lum. auto", Icons.Rounded.BrightnessAuto, "brightness", needsWriteSettings = true),
    CAFFEINE_ON("Caféine activée", "Caféine ON", Icons.Rounded.Coffee, "caffeine"),
    CAFFEINE_OFF("Caféine désactivée", "Caféine OFF", Icons.Rounded.Bedtime, "caffeine"),
    ONE_HAND_ON("Une Main + activé", "Une Main + ON", Icons.Rounded.SwipeRight, "onehand"),
    ONE_HAND_OFF("Une Main + désactivé", "Une Main + OFF", Icons.Rounded.NightsStay, "onehand"),
    TORCH_ON("Torche allumée", "Torche ON", Icons.Rounded.FlashlightOn, "torch"),
    TORCH_OFF("Torche éteinte", "Torche OFF", Icons.Rounded.FlashlightOff, "torch"),
    BT_BATTERY("Batterie de l’appareil Bluetooth", "Batterie BT", Icons.Rounded.Battery5Bar, "btbattery"),
    PLAY_SOUND("Jouer un son", "Son", Icons.Rounded.MusicNote, "sound"),
    SEND_NOTIFICATION("Afficher une notification", "Notification", Icons.Rounded.Notifications, "notif", selectable = false);

    /** Action qui n’a de sens qu’avec le déclencheur « Bluetooth connecté ». */
    val requiresBluetoothTrigger: Boolean get() = this == BT_BATTERY
}

data class RoutineAction(
    val type: RoutineActionType,
    val intParam: Int = 75,
    val textParam: String = ""
) {
    fun displayBadge(): String {
        return when (type) {
            RoutineActionType.MEDIA_VOLUME -> "Volume $intParam %"
            RoutineActionType.BRIGHTNESS_LEVEL -> "Luminosité $intParam %"
            RoutineActionType.SCREEN_TIMEOUT -> "Veille ${formatTimeoutSeconds(intParam)}"
            RoutineActionType.BT_BATTERY -> "Batterie BT"
            RoutineActionType.PLAY_SOUND -> "Son"
            RoutineActionType.SEND_NOTIFICATION -> {
                if (textParam.isBlank()) "Notification" else "Notif : $textParam"
            }
            else -> type.shortLabel
        }
    }

    companion object {
        val SCREEN_TIMEOUT_PRESETS = listOf(15, 30, 60, 120, 300, 600, 1800)

        fun formatTimeoutSeconds(seconds: Int): String = when {
            seconds < 60 -> "$seconds s"
            seconds < 3600 -> "${seconds / 60} min"
            else -> "${seconds / 3600} h"
        }

        fun defaultParamFor(type: RoutineActionType): Int = when (type) {
            RoutineActionType.SCREEN_TIMEOUT -> 30
            RoutineActionType.BRIGHTNESS_LEVEL -> 50
            RoutineActionType.PLAY_SOUND -> 0
            else -> 75
        }
    }
}

data class RoutineIconOption(val key: String, val icon: ImageVector, val label: String)
data class RoutineColorOption(val key: String, val color: Color, val label: String)

/** Icônes et couleurs personnalisables des routines (comme les Modes de One UI). */
object RoutineStyle {
    val icons: List<RoutineIconOption> = listOf(
        RoutineIconOption("home", Icons.Rounded.Home, "Maison"),
        RoutineIconOption("work", Icons.Rounded.Work, "Travail"),
        RoutineIconOption("car", Icons.Rounded.DirectionsCar, "Voiture"),
        RoutineIconOption("sleep", Icons.Rounded.Bedtime, "Sommeil"),
        RoutineIconOption("night", Icons.Rounded.NightsStay, "Soirée"),
        RoutineIconOption("sun", Icons.Rounded.WbSunny, "Journée"),
        RoutineIconOption("school", Icons.Rounded.School, "Études"),
        RoutineIconOption("sport", Icons.Rounded.FitnessCenter, "Sport"),
        RoutineIconOption("generic", Icons.Rounded.Category, "Générique"),
        RoutineIconOption("puzzle", Icons.Rounded.Extension, "Puzzle"),
        RoutineIconOption("settings", Icons.Rounded.Settings, "Réglages"),
        RoutineIconOption("tune", Icons.Rounded.Tune, "Curseurs"),
        RoutineIconOption("music", Icons.Rounded.Headphones, "Casque"),
        RoutineIconOption("earbuds", Icons.Rounded.Headset, "Écouteurs"),
        RoutineIconOption("note", Icons.Rounded.MusicNote, "Musique"),
        RoutineIconOption("relax", Icons.Rounded.Spa, "Détente"),
        RoutineIconOption("idea", Icons.Rounded.Lightbulb, "Idée"),
        RoutineIconOption("magic", Icons.Rounded.AutoAwesome, "Magie"),
        RoutineIconOption("widgets", Icons.Rounded.Widgets, "Widgets"),
        RoutineIconOption("game", Icons.Rounded.SportsEsports, "Jeu"),
        RoutineIconOption("movie", Icons.Rounded.Movie, "Film"),
        RoutineIconOption("food", Icons.Rounded.Restaurant, "Repas"),
        RoutineIconOption("coffee", Icons.Rounded.LocalCafe, "Pause"),
        RoutineIconOption("travel", Icons.Rounded.Flight, "Voyage"),
        RoutineIconOption("holiday", Icons.Rounded.BeachAccess, "Vacances"),
        RoutineIconOption("silent", Icons.Rounded.NotificationsOff, "Silence"),
        RoutineIconOption("phone", Icons.Rounded.PhoneAndroid, "Téléphone"),
        RoutineIconOption("battery", Icons.Rounded.BatteryChargingFull, "Batterie"),
        RoutineIconOption("bolt", Icons.Rounded.Bolt, "Énergie"),
        RoutineIconOption("wifi", Icons.Rounded.Wifi, "Wi-Fi"),
        RoutineIconOption("bluetooth", Icons.Rounded.Bluetooth, "Bluetooth"),
        RoutineIconOption("heart", Icons.Rounded.Favorite, "Favori"),
        RoutineIconOption("star", Icons.Rounded.Star, "Étoile"),
        RoutineIconOption("pet", Icons.Rounded.Pets, "Animal")
    )

    val colors: List<RoutineColorOption> = listOf(
        RoutineColorOption("blue", Color(0xFF4F7CFF), "Bleu"),
        RoutineColorOption("mint", Color(0xFF2FB58C), "Menthe"),
        RoutineColorOption("teal", Color(0xFF1E9FB5), "Turquoise"),
        RoutineColorOption("green", Color(0xFF4CAF50), "Vert"),
        RoutineColorOption("amber", Color(0xFFD9A400), "Ambre"),
        RoutineColorOption("orange", Color(0xFFF07B2E), "Orange"),
        RoutineColorOption("red", Color(0xFFE5484D), "Rouge"),
        RoutineColorOption("pink", Color(0xFFE0569B), "Rose"),
        RoutineColorOption("violet", Color(0xFF8B5CF6), "Violet"),
        RoutineColorOption("slate", Color(0xFF64748B), "Ardoise")
    )

    fun iconFor(routine: AutomationRoutine): ImageVector =
        icons.firstOrNull { it.key == routine.iconKey }?.icon
            ?: if (routine.isMode) Icons.Rounded.Category else routine.triggerType.icon

    fun colorFor(colorKey: String): Color? = colors.firstOrNull { it.key == colorKey }?.color
}

data class AutomationRoutine(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val triggerType: TriggerType,
    /** SSID, nom Bluetooth, filtre USB ou nom de paquet de l’application selon le déclencheur. */
    val filterName: String = "",
    val actions: List<RoutineAction>,
    val lastTriggeredMs: Long? = null,
    /** Nom lisible associé au filtre (ex. nom de l’application). */
    val filterLabel: String = "",
    /** Seuil en % pour les déclencheurs batterie. */
    val triggerValue: Int = 20,
    val iconKey: String = "",
    val colorKey: String = "",
    /** Rétablir les réglages d’avant quand la condition se termine. */
    val restoreOnEnd: Boolean = false,
    /** Notification personnalisée affichée après l’exécution. */
    val notifyOnRun: Boolean = false,
    val notifyTitle: String = "",
    val notifyText: String = "",
    /**
     * Mode (comme sur One UI) : un seul actif à la fois, activé à la main ou par un déclencheur,
     * réglages rétablis à l’arrêt et icône dans la barre d’état.
     */
    val isMode: Boolean = false
) {
    /** Déclencheur automatique réel (un mode « Manuel » n’en a pas). */
    val hasAutoTrigger: Boolean get() = triggerType != TriggerType.MANUAL

    fun triggerSummary(): String = when {
        triggerType == TriggerType.MANUAL -> "Manuel"
        triggerType.isApp -> "Ouverture de ${filterLabel.ifBlank { filterName.ifBlank { "une application" } }}"
        triggerType == TriggerType.BATTERY_BELOW -> "Batterie ≤ $triggerValue %"
        triggerType == TriggerType.BATTERY_ABOVE -> "Batterie ≥ $triggerValue %"
        triggerType == TriggerType.CHARGE_REACHED -> "Charge à $triggerValue %"
        triggerType.category == TriggerCategory.CHARGING -> triggerType.label
        // Filtre « tous » : le libellé seul suffit.
        filterName.isBlank() -> triggerType.label
        else -> "${triggerType.label} : ${AutomateConfig.formatFilterDisplay(triggerType, filterName)}"
    }
}

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
    val logs: List<AutomateLogEntry> = emptyList(),
    /** Réglages sauvegardés avant l’exécution, par identifiant de routine en cours. */
    val snapshots: Map<String, Map<String, String>> = emptyMap()
) {
    val modes: List<AutomationRoutine> get() = routines.filter { it.isMode }
    val plainRoutines: List<AutomationRoutine> get() = routines.filterNot { it.isMode }

    /** Mode en cours (au plus un). */
    val activeMode: AutomationRoutine?
        get() = routines.firstOrNull { it.isMode && snapshots.containsKey(it.id) }

    /** Routines et modes à surveiller (déclencheur automatique activé). */
    val watchedCount: Int
        get() = routines.count { it.enabled && it.hasAutoTrigger }

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
                else -> filterNames(filterName).joinToString(", ")
            }
        }

        /** Plusieurs appareils ou réseaux peuvent être choisis : un nom par ligne. */
        fun filterNames(raw: String): List<String> =
            raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        fun joinFilterNames(names: Collection<String>): String =
            names.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")

        /** Vrai si aucun nom n’est choisi, ou si l’un d’eux correspond au nom observé. */
        fun matchesNamedFilter(raw: String, observed: String): Boolean {
            val names = filterNames(raw)
            return names.isEmpty() || names.any { observed.contains(it, ignoreCase = true) }
        }

        fun defaultRoutines(): List<AutomationRoutine> = listOf(
            AutomationRoutine(
                id = "preset_car_bt",
                name = "Mode Trajet / Audio",
                enabled = false,
                triggerType = TriggerType.BLUETOOTH_CONNECTED,
                actions = listOf(
                    RoutineAction(RoutineActionType.MEDIA_VOLUME, intParam = 80),
                    RoutineAction(RoutineActionType.CAFFEINE_ON)
                ),
                iconKey = "car",
                colorKey = "blue",
                restoreOnEnd = true
            ),
            AutomationRoutine(
                id = "preset_bt_battery",
                name = "Batterie des écouteurs",
                enabled = false,
                triggerType = TriggerType.BLUETOOTH_CONNECTED,
                actions = listOf(RoutineAction(RoutineActionType.BT_BATTERY)),
                iconKey = "music",
                colorKey = "teal"
            ),
            AutomationRoutine(
                id = "preset_charge_reached",
                name = "Charge à 80 %",
                enabled = false,
                triggerType = TriggerType.CHARGE_REACHED,
                triggerValue = 80,
                actions = listOf(RoutineAction(RoutineActionType.PLAY_SOUND, intParam = 0)),
                iconKey = "battery",
                colorKey = "green",
                notifyOnRun = true,
                notifyTitle = "Charge atteinte"
            ),
            AutomationRoutine(
                id = "preset_usb_desk",
                name = "Bureau en charge USB",
                enabled = false,
                triggerType = TriggerType.USB_CHARGING_CONNECTED,
                filterName = USB_FILTER_ANY,
                actions = listOf(
                    RoutineAction(RoutineActionType.CAFFEINE_ON)
                ),
                iconKey = "work",
                colorKey = "mint",
                restoreOnEnd = true
            ),
            AutomationRoutine(
                id = "preset_home_wifi",
                name = "Mode Maison",
                enabled = false,
                triggerType = TriggerType.WIFI_CONNECTED,
                actions = listOf(
                    RoutineAction(RoutineActionType.DND_OFF),
                    RoutineAction(RoutineActionType.RINGER_NORMAL),
                    RoutineAction(RoutineActionType.ONE_HAND_ON)
                ),
                iconKey = "home",
                colorKey = "teal"
            ),
            AutomationRoutine(
                id = "preset_away_wifi",
                name = "Départ silencieux",
                enabled = false,
                triggerType = TriggerType.WIFI_DISCONNECTED,
                actions = listOf(
                    RoutineAction(RoutineActionType.RINGER_SILENT),
                    RoutineAction(RoutineActionType.CAFFEINE_OFF)
                ),
                iconKey = "silent",
                colorKey = "violet"
            )
        ) + defaultModes()

        /** Modes proposés au départ, comme ceux de One UI. */
        fun defaultModes(): List<AutomationRoutine> = listOf(
            mode(
                "mode_sleep", "Sommeil", "sleep", "violet",
                RoutineAction(RoutineActionType.DND_ON),
                RoutineAction(RoutineActionType.BRIGHTNESS_LEVEL, intParam = 10)
            ),
            mode(
                "mode_work", "Travail", "work", "blue",
                RoutineAction(RoutineActionType.RINGER_VIBRATE)
            ),
            mode(
                "mode_drive", "Conduite", "car", "orange",
                RoutineAction(RoutineActionType.MEDIA_VOLUME, intParam = 80),
                RoutineAction(RoutineActionType.CAFFEINE_ON)
            ),
            mode(
                "mode_sport", "Sport", "sport", "green",
                RoutineAction(RoutineActionType.MEDIA_VOLUME, intParam = 70)
            ),
            mode(
                "mode_relax", "Détente", "relax", "teal",
                RoutineAction(RoutineActionType.DND_ON),
                RoutineAction(RoutineActionType.MEDIA_VOLUME, intParam = 40)
            ),
            mode(
                "mode_movie", "Cinéma", "movie", "red",
                RoutineAction(RoutineActionType.RINGER_SILENT),
                RoutineAction(RoutineActionType.BRIGHTNESS_LEVEL, intParam = 30)
            )
        )

        private fun mode(id: String, name: String, icon: String, color: String, vararg actions: RoutineAction) =
            AutomationRoutine(
                id = id,
                name = name,
                enabled = true,
                triggerType = TriggerType.MANUAL,
                actions = actions.toList(),
                iconKey = icon,
                colorKey = color,
                restoreOnEnd = true,
                isMode = true
            )
    }
}
