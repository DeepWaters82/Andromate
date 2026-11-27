package com.example.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.modules.automate.AutomateConfig
import com.example.modules.automate.AutomateLogEntry
import com.example.modules.automate.AutomateModule
import com.example.modules.automate.AutomateSerializer
import com.example.modules.caffeine.CaffeineConfig
import com.example.modules.caffeine.CaffeineModule
import com.example.modules.caffeine.CaffeineTimeoutMode
import com.example.modules.clipboard.ClipboardConfig
import com.example.modules.clipboard.ClipboardEntry
import com.example.modules.clipboard.ClipboardModule
import com.example.modules.clipboard.ClipboardSerializer
import com.example.modules.onehand.HandleColorStyle
import com.example.modules.onehand.HandleConfig
import com.example.modules.onehand.HandleSide
import com.example.modules.onehand.OneHandConfig
import com.example.modules.onehand.OneHandModule
import com.example.ui.theme.AccentPalette
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "moduyou_prefs")

data class NotificationPreferences(
    val allExecutionNotificationsEnabled: Boolean = true,
    val caffeineStatusBarIconEnabled: Boolean = true,
    /** Surveillance des routines visible (sinon notification minimale, sans icône). */
    val routinesNotificationsEnabled: Boolean = true,
    /** Icône du mode actif dans la barre d’état (sinon notification minimale). */
    val modeStatusIconEnabled: Boolean = true,
    /** Notifications « routine exécutée » demandées par les routines. */
    val routineAlertsEnabled: Boolean = true,
    /** Notification de batterie des appareils Bluetooth. */
    val bluetoothBatteryEnabled: Boolean = true
)

/** Derniers réglages de notifications connus, lus par les services sans attendre DataStore. */
object NotificationSettingsCache {
    @Volatile
    var current: NotificationPreferences = NotificationPreferences()
}

class ModuYouPreferences private constructor(private val context: Context) {

    private object Keys {
        // Theme
        val DYNAMIC_COLOR = booleanPreferencesKey("theme_dynamic_color")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FALLBACK_PALETTE = stringPreferencesKey("theme_fallback_palette")

        // Global Notifications
        val NOTIF_ALL_EXECUTION = booleanPreferencesKey("notif_all_execution")
        val NOTIF_MODE_ICON = booleanPreferencesKey("notif_mode_icon")
        val NOTIF_ROUTINE_ALERTS = booleanPreferencesKey("notif_routine_alerts")
        val NOTIF_BT_BATTERY = booleanPreferencesKey("notif_bt_battery")

        // One Hand + Global
        val OH_MASTER_ENABLED = booleanPreferencesKey("oh_master_enabled")
        val OH_LONG_SWIPE_ENABLED = booleanPreferencesKey("oh_long_swipe_enabled")
        val OH_HAPTIC_ENABLED = booleanPreferencesKey("oh_haptic_enabled")
        val OH_DISABLE_LANDSCAPE = booleanPreferencesKey("oh_disable_landscape")
        val OH_SYNC_HANDLES = booleanPreferencesKey("oh_sync_handles")
        val OH_SHOW_GESTURE_LABEL = booleanPreferencesKey("oh_show_gesture_label")
        val OH_SHOW_GESTURE_ICON = booleanPreferencesKey("oh_show_gesture_icon")
        val OH_TRIGGER_DISTANCE = floatPreferencesKey("oh_trigger_distance")
        val OH_LONG_TRIGGER_DISTANCE = floatPreferencesKey("oh_long_trigger_distance")
        val OH_TOOLS_HIDDEN = stringPreferencesKey("oh_tools_hidden")
        val OH_TOOLS_ICON_SIZE = stringPreferencesKey("oh_tools_icon_size")
        val OH_TOOLS_LABELS = booleanPreferencesKey("oh_tools_labels")
        val OH_TOOLS_MEDIA = booleanPreferencesKey("oh_tools_media")
        val OH_TOOLS_ORDER = stringPreferencesKey("oh_tools_order")
        val OH_TOOLS_SHAPE = stringPreferencesKey("oh_tools_shape")
        val OH_TOOLS_COLUMNS = androidx.datastore.preferences.core.intPreferencesKey("oh_tools_columns")
        val OH_TOOLS_SPANS = stringPreferencesKey("oh_tools_spans")
        val OH_TOOLS_SOURCE_ICON = stringPreferencesKey("oh_tools_source_icon")
        val OH_TOOLS_APPS = stringPreferencesKey("oh_tools_apps")

        // Left handle
        val OH_L_ENABLED = booleanPreferencesKey("oh_l_enabled")
        val OH_L_POS_Y = floatPreferencesKey("oh_l_pos_y")
        val OH_L_HEIGHT_PCT = floatPreferencesKey("oh_l_height_pct")
        val OH_L_WIDTH = floatPreferencesKey("oh_l_width")
        val OH_L_OPACITY = floatPreferencesKey("oh_l_opacity")
        val OH_L_COLOR = stringPreferencesKey("oh_l_color")
        val OH_L_SHORT_H = stringPreferencesKey("oh_l_short_h")
        val OH_L_SHORT_UP = stringPreferencesKey("oh_l_short_up")
        val OH_L_SHORT_DOWN = stringPreferencesKey("oh_l_short_down")
        val OH_L_LONG_H = stringPreferencesKey("oh_l_long_h")
        val OH_L_LONG_UP = stringPreferencesKey("oh_l_long_up")
        val OH_L_LONG_DOWN = stringPreferencesKey("oh_l_long_down")

        // Right handle
        val OH_R_ENABLED = booleanPreferencesKey("oh_r_enabled")
        val OH_R_POS_Y = floatPreferencesKey("oh_r_pos_y")
        val OH_R_HEIGHT_PCT = floatPreferencesKey("oh_r_height_pct")
        val OH_R_WIDTH = floatPreferencesKey("oh_r_width")
        val OH_R_OPACITY = floatPreferencesKey("oh_r_opacity")
        val OH_R_COLOR = stringPreferencesKey("oh_r_color")
        val OH_R_SHORT_H = stringPreferencesKey("oh_r_short_h")
        val OH_R_SHORT_UP = stringPreferencesKey("oh_r_short_up")
        val OH_R_SHORT_DOWN = stringPreferencesKey("oh_r_short_down")
        val OH_R_LONG_H = stringPreferencesKey("oh_r_long_h")
        val OH_R_LONG_UP = stringPreferencesKey("oh_r_long_up")
        val OH_R_LONG_DOWN = stringPreferencesKey("oh_r_long_down")

        // Caffeine
        val CAF_ACTIVE = booleanPreferencesKey("caffeine_active")
        val CAF_KEEP_BRIGHT = booleanPreferencesKey("caffeine_keep_bright")
        val CAF_DISABLE_ON_SCREEN_OFF = booleanPreferencesKey("caffeine_disable_on_screen_off")
        val CAF_SHOW_STATUS_ICON = booleanPreferencesKey("caffeine_show_status_icon")
        val CAF_TIMEOUT_MODE = stringPreferencesKey("caffeine_timeout_mode")
        val CAF_ACTIVE_SINCE = longPreferencesKey("caffeine_active_since")

        // Automate / Routines
        val AUTO_MASTER_ENABLED = booleanPreferencesKey("automate_master_enabled")
        val AUTO_NOTIFICATIONS_ENABLED = booleanPreferencesKey("automate_notifications_enabled")
        val AUTO_ROUTINES_JSON = stringPreferencesKey("automate_routines_json")
        val AUTO_LOGS_JSON = stringPreferencesKey("automate_logs_json")
        val AUTO_SNAPSHOTS_JSON = stringPreferencesKey("automate_snapshots_json")
        val AUTO_MODES_SEEDED = booleanPreferencesKey("automate_modes_seeded")

        // Clipboard
        val CLIP_ENABLED = booleanPreferencesKey("clipboard_enabled")
        val CLIP_ENTRIES_JSON = stringPreferencesKey("clipboard_entries_json")
        val DBT_ENABLED = booleanPreferencesKey("dbt_enabled")
        val DBT_ON_CONNECT = booleanPreferencesKey("dbt_on_connect")
        val DBT_ON_DISCONNECT = booleanPreferencesKey("dbt_on_disconnect")
        val DBT_BAR = booleanPreferencesKey("dbt_bar")
        val DBT_PERCENT = booleanPreferencesKey("dbt_percent")
        val DBT_STATUS = booleanPreferencesKey("dbt_status")
        val DBT_ICON = booleanPreferencesKey("dbt_icon")
        val DBT_POSITION = stringPreferencesKey("dbt_position")
        val DBT_DURATION = androidx.datastore.preferences.core.intPreferencesKey("dbt_duration")
        val DBT_NOTIFICATION = booleanPreferencesKey("dbt_notification")
        val DBT_DEVICES = stringPreferencesKey("dbt_devices")

        // Touche Essential
        val EK_ENABLED = booleanPreferencesKey("ek_enabled")
        val EK_SHORT = stringPreferencesKey("ek_short")
        val EK_LONG = stringPreferencesKey("ek_long")
        val EK_DOUBLE = stringPreferencesKey("ek_double")
    }

    val themeSettingsFlow: Flow<ThemeSettings> = context.dataStore.data.map { prefs ->
        ThemeSettings(
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            themeMode = parseEnum(prefs[Keys.THEME_MODE], ThemeMode.SYSTEM),
            fallbackPalette = parseEnum(prefs[Keys.FALLBACK_PALETTE], AccentPalette.COSMIC_MINT)
        )
    }.distinctUntilChanged()

    val notificationPreferencesFlow: Flow<NotificationPreferences> = context.dataStore.data.map { prefs ->
        NotificationPreferences(
            allExecutionNotificationsEnabled = prefs[Keys.NOTIF_ALL_EXECUTION] ?: true,
            caffeineStatusBarIconEnabled = prefs[Keys.CAF_SHOW_STATUS_ICON] ?: true,
            routinesNotificationsEnabled = prefs[Keys.AUTO_NOTIFICATIONS_ENABLED] ?: true,
            modeStatusIconEnabled = prefs[Keys.NOTIF_MODE_ICON] ?: true,
            routineAlertsEnabled = prefs[Keys.NOTIF_ROUTINE_ALERTS] ?: true,
            bluetoothBatteryEnabled = prefs[Keys.NOTIF_BT_BATTERY] ?: true
        )
    }.distinctUntilChanged()

    suspend fun setNotificationSwitch(key: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            when (key) {
                "mode_icon" -> prefs[Keys.NOTIF_MODE_ICON] = enabled
                "routine_alerts" -> prefs[Keys.NOTIF_ROUTINE_ALERTS] = enabled
                "bt_battery" -> prefs[Keys.NOTIF_BT_BATTERY] = enabled
            }
        }
    }

    val oneHandConfigFlow: Flow<OneHandConfig> = context.dataStore.data
        .map { prefs -> readOneHandConfig(prefs) }
        .distinctUntilChanged()

    private fun readOneHandConfig(prefs: Preferences): OneHandConfig {
        val defaults = OneHandConfig()
        val leftDefault = defaults.leftHandle
        val rightDefault = defaults.rightHandle
        val syncHandles = prefs[Keys.OH_SYNC_HANDLES] ?: defaults.syncHandles

        val left = HandleConfig(
            enabled = prefs[Keys.OH_L_ENABLED] ?: leftDefault.enabled,
            verticalPositionPercent = prefs[Keys.OH_L_POS_Y] ?: leftDefault.verticalPositionPercent,
            heightPercent = prefs[Keys.OH_L_HEIGHT_PCT] ?: leftDefault.heightPercent,
            widthDp = prefs[Keys.OH_L_WIDTH] ?: leftDefault.widthDp,
            opacity = prefs[Keys.OH_L_OPACITY] ?: leftDefault.opacity,
            colorStyle = parseEnum(prefs[Keys.OH_L_COLOR], leftDefault.colorStyle),
            shortHorizontal = parseEnum(prefs[Keys.OH_L_SHORT_H], leftDefault.shortHorizontal),
            shortDiagonalUp = parseEnum(prefs[Keys.OH_L_SHORT_UP], leftDefault.shortDiagonalUp),
            shortDiagonalDown = parseEnum(prefs[Keys.OH_L_SHORT_DOWN], leftDefault.shortDiagonalDown),
            longHorizontal = parseEnum(prefs[Keys.OH_L_LONG_H], leftDefault.longHorizontal),
            longDiagonalUp = parseEnum(prefs[Keys.OH_L_LONG_UP], leftDefault.longDiagonalUp),
            longDiagonalDown = parseEnum(prefs[Keys.OH_L_LONG_DOWN], leftDefault.longDiagonalDown)
        )
        val rawRight = HandleConfig(
            enabled = prefs[Keys.OH_R_ENABLED] ?: rightDefault.enabled,
            verticalPositionPercent = prefs[Keys.OH_R_POS_Y] ?: rightDefault.verticalPositionPercent,
            heightPercent = prefs[Keys.OH_R_HEIGHT_PCT] ?: rightDefault.heightPercent,
            widthDp = prefs[Keys.OH_R_WIDTH] ?: rightDefault.widthDp,
            opacity = prefs[Keys.OH_R_OPACITY] ?: rightDefault.opacity,
            colorStyle = parseEnum(prefs[Keys.OH_R_COLOR], rightDefault.colorStyle),
            shortHorizontal = parseEnum(prefs[Keys.OH_R_SHORT_H], rightDefault.shortHorizontal),
            shortDiagonalUp = parseEnum(prefs[Keys.OH_R_SHORT_UP], rightDefault.shortDiagonalUp),
            shortDiagonalDown = parseEnum(prefs[Keys.OH_R_SHORT_DOWN], rightDefault.shortDiagonalDown),
            longHorizontal = parseEnum(prefs[Keys.OH_R_LONG_H], rightDefault.longHorizontal),
            longDiagonalUp = parseEnum(prefs[Keys.OH_R_LONG_UP], rightDefault.longDiagonalUp),
            longDiagonalDown = parseEnum(prefs[Keys.OH_R_LONG_DOWN], rightDefault.longDiagonalDown)
        )

        val config = OneHandConfig(
            masterEnabled = prefs[Keys.OH_MASTER_ENABLED] ?: defaults.masterEnabled,
            longSwipeEnabled = prefs[Keys.OH_LONG_SWIPE_ENABLED] ?: defaults.longSwipeEnabled,
            hapticFeedbackEnabled = prefs[Keys.OH_HAPTIC_ENABLED] ?: defaults.hapticFeedbackEnabled,
            disableInLandscape = prefs[Keys.OH_DISABLE_LANDSCAPE] ?: defaults.disableInLandscape,
            syncHandles = syncHandles,
            showGestureLabel = prefs[Keys.OH_SHOW_GESTURE_LABEL] ?: defaults.showGestureLabel,
            showGestureIcon = prefs[Keys.OH_SHOW_GESTURE_ICON] ?: defaults.showGestureIcon,
            triggerDistanceDp = prefs[Keys.OH_TRIGGER_DISTANCE] ?: defaults.triggerDistanceDp,
            longTriggerDistanceDp = prefs[Keys.OH_LONG_TRIGGER_DISTANCE] ?: defaults.longTriggerDistanceDp,
            leftHandle = left,
            rightHandle = rawRight,
            tools = com.example.modules.onehand.ToolsMenuConfig(
                hiddenTools = com.example.modules.onehand.ToolsMenuConfig.decodeHidden(prefs[Keys.OH_TOOLS_HIDDEN]),
                iconSize = parseEnum(prefs[Keys.OH_TOOLS_ICON_SIZE], defaults.tools.iconSize),
                showLabels = prefs[Keys.OH_TOOLS_LABELS] ?: defaults.tools.showLabels,
                showMediaZone = prefs[Keys.OH_TOOLS_MEDIA] ?: defaults.tools.showMediaZone,
                // Jamais réarrangé : Wi-Fi et Bluetooth en pilules en tête (disposition de base).
                order = prefs[Keys.OH_TOOLS_ORDER]?.takeIf { it.isNotBlank() }
                    ?.let { com.example.modules.onehand.ToolsMenuConfig.decodeOrder(it) }
                    ?: defaults.tools.order,
                shape = parseEnum(prefs[Keys.OH_TOOLS_SHAPE], defaults.tools.shape),
                columns = (prefs[Keys.OH_TOOLS_COLUMNS] ?: defaults.tools.columns).coerceIn(3, 5),
                spans = com.example.modules.onehand.ToolsMenuConfig.decodeSpans(prefs[Keys.OH_TOOLS_SPANS])
                    .takeIf { it.isNotEmpty() || !prefs[Keys.OH_TOOLS_ORDER].isNullOrBlank() }
                    ?: defaults.tools.spans,
                sourceIcon = prefs[Keys.OH_TOOLS_SOURCE_ICON] ?: defaults.tools.sourceIcon,
                apps = com.example.modules.onehand.AppLauncher.decode(prefs[Keys.OH_TOOLS_APPS])
            )
        )

        return if (syncHandles) config.withSyncedHandles(HandleSide.RIGHT) else config
    }

    val caffeineConfigFlow: Flow<CaffeineConfig> = context.dataStore.data.map { prefs ->
        val isActive = prefs[Keys.CAF_ACTIVE] ?: false
        val since = prefs[Keys.CAF_ACTIVE_SINCE]
        CaffeineConfig(
            isActive = isActive,
            keepBright = prefs[Keys.CAF_KEEP_BRIGHT] ?: true,
            disableOnScreenOff = prefs[Keys.CAF_DISABLE_ON_SCREEN_OFF] ?: true,
            showStatusBarIcon = prefs[Keys.CAF_SHOW_STATUS_ICON] ?: true,
            timeoutMode = parseEnum(prefs[Keys.CAF_TIMEOUT_MODE], CaffeineTimeoutMode.INDEFINITE),
            activeSinceEpochMs = if (isActive && since != null && since > 0L) since else null
        )
    }.distinctUntilChanged()

    val automateConfigFlow: Flow<AutomateConfig> = context.dataStore.data.map { prefs ->
        val routines = readRoutines(prefs)
        val logs = AutomateSerializer.decodeLogs(prefs[Keys.AUTO_LOGS_JSON])
        AutomateConfig(
            snapshots = AutomateSerializer.decodeSnapshots(prefs[Keys.AUTO_SNAPSHOTS_JSON]),
            masterEnabled = prefs[Keys.AUTO_MASTER_ENABLED] ?: true,
            executionNotificationsEnabled = prefs[Keys.AUTO_NOTIFICATIONS_ENABLED] ?: true,
            routines = routines,
            logs = logs
        )
    }.distinctUntilChanged()

    val clipboardConfigFlow: Flow<ClipboardConfig> = context.dataStore.data.map { prefs ->
        ClipboardConfig(
            enabled = prefs[Keys.CLIP_ENABLED] ?: true,
            entries = ClipboardSerializer.decodeEntries(prefs[Keys.CLIP_ENTRIES_JSON])
        )
    }.distinctUntilChanged()

    suspend fun updateThemeSettings(transform: (ThemeSettings) -> ThemeSettings) {
        context.dataStore.edit { prefs ->
            val current = ThemeSettings(
                dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
                themeMode = parseEnum(prefs[Keys.THEME_MODE], ThemeMode.SYSTEM),
                fallbackPalette = parseEnum(prefs[Keys.FALLBACK_PALETTE], AccentPalette.COSMIC_MINT)
            )
            val updated = transform(current)
            prefs[Keys.DYNAMIC_COLOR] = updated.dynamicColor
            prefs[Keys.THEME_MODE] = updated.themeMode.name
            prefs[Keys.FALLBACK_PALETTE] = updated.fallbackPalette.name
        }
    }

    suspend fun setAllExecutionNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIF_ALL_EXECUTION] = enabled
            prefs[Keys.CAF_SHOW_STATUS_ICON] = enabled
            prefs[Keys.AUTO_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun saveOneHandConfig(config: OneHandConfig) {
        context.dataStore.edit { prefs -> writeOneHandConfig(prefs, config) }
    }

    /**
     * Lit la configuration actuelle DANS la transaction : deux réglages modifiés coup sur coup
     * ne s’écrasent plus (l’ancienne version partait d’un état d’interface pas encore à jour).
     */
    suspend fun updateOneHandConfig(transform: (OneHandConfig) -> OneHandConfig) {
        context.dataStore.edit { prefs ->
            writeOneHandConfig(prefs, transform(readOneHandConfig(prefs)))
        }
    }

    private fun writeOneHandConfig(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        config: OneHandConfig
    ) {
            prefs[Keys.OH_MASTER_ENABLED] = config.masterEnabled
            prefs[Keys.OH_LONG_SWIPE_ENABLED] = config.longSwipeEnabled
            prefs[Keys.OH_HAPTIC_ENABLED] = config.hapticFeedbackEnabled
            prefs[Keys.OH_DISABLE_LANDSCAPE] = config.disableInLandscape
            prefs[Keys.OH_SYNC_HANDLES] = config.syncHandles
            prefs[Keys.OH_SHOW_GESTURE_LABEL] = config.showGestureLabel
            prefs[Keys.OH_SHOW_GESTURE_ICON] = config.showGestureIcon
            prefs[Keys.OH_TRIGGER_DISTANCE] = config.triggerDistanceDp
            prefs[Keys.OH_LONG_TRIGGER_DISTANCE] = config.longTriggerDistanceDp
            prefs[Keys.OH_TOOLS_HIDDEN] = config.tools.encodeHidden()
            prefs[Keys.OH_TOOLS_ICON_SIZE] = config.tools.iconSize.name
            prefs[Keys.OH_TOOLS_LABELS] = config.tools.showLabels
            prefs[Keys.OH_TOOLS_MEDIA] = config.tools.showMediaZone
            prefs[Keys.OH_TOOLS_ORDER] = config.tools.encodeOrder()
            prefs[Keys.OH_TOOLS_SHAPE] = config.tools.shape.name
            prefs[Keys.OH_TOOLS_COLUMNS] = config.tools.columns
            prefs[Keys.OH_TOOLS_SPANS] = config.tools.encodeSpans()
            prefs[Keys.OH_TOOLS_SOURCE_ICON] = config.tools.sourceIcon
            prefs[Keys.OH_TOOLS_APPS] = com.example.modules.onehand.AppLauncher.encode(config.tools.apps)

            writeHandle(prefs, HandleSide.LEFT, config.leftHandle)
            writeHandle(prefs, HandleSide.RIGHT, config.rightHandle)
    }

    private fun readDeepBluetooth(prefs: Preferences): com.example.modules.deepbluetooth.DeepBluetoothConfig {
        val d = com.example.modules.deepbluetooth.DeepBluetoothConfig()
        return com.example.modules.deepbluetooth.DeepBluetoothConfig(
            enabled = prefs[Keys.DBT_ENABLED] ?: d.enabled,
            onConnect = prefs[Keys.DBT_ON_CONNECT] ?: d.onConnect,
            onDisconnect = prefs[Keys.DBT_ON_DISCONNECT] ?: d.onDisconnect,
            showBatteryBar = prefs[Keys.DBT_BAR] ?: d.showBatteryBar,
            showPercent = prefs[Keys.DBT_PERCENT] ?: d.showPercent,
            showStatus = prefs[Keys.DBT_STATUS] ?: d.showStatus,
            showDeviceIcon = prefs[Keys.DBT_ICON] ?: d.showDeviceIcon,
            position = parseEnum(prefs[Keys.DBT_POSITION], d.position),
            durationSec = (prefs[Keys.DBT_DURATION] ?: d.durationSec).coerceIn(2, 15),
            alsoNotification = prefs[Keys.DBT_NOTIFICATION] ?: d.alsoNotification,
            devices = prefs[Keys.DBT_DEVICES] ?: d.devices
        )
    }

    private fun readEssentialKey(prefs: Preferences): com.example.modules.essentialkey.EssentialKeyConfig {
        val d = com.example.modules.essentialkey.EssentialKeyConfig()
        return com.example.modules.essentialkey.EssentialKeyConfig(
            enabled = prefs[Keys.EK_ENABLED] ?: d.enabled,
            shortPress = prefs[Keys.EK_SHORT] ?: d.shortPress,
            longPress = prefs[Keys.EK_LONG] ?: d.longPress,
            doublePress = prefs[Keys.EK_DOUBLE] ?: d.doublePress
        )
    }

    val essentialKeyConfigFlow: Flow<com.example.modules.essentialkey.EssentialKeyConfig> =
        context.dataStore.data.map { readEssentialKey(it) }.distinctUntilChanged()

    suspend fun updateEssentialKeyConfig(
        transform: (com.example.modules.essentialkey.EssentialKeyConfig) -> com.example.modules.essentialkey.EssentialKeyConfig
    ) {
        context.dataStore.edit { prefs ->
            val c = transform(readEssentialKey(prefs))
            prefs[Keys.EK_ENABLED] = c.enabled
            prefs[Keys.EK_SHORT] = c.shortPress
            prefs[Keys.EK_LONG] = c.longPress
            prefs[Keys.EK_DOUBLE] = c.doublePress
        }
    }

    val deepBluetoothConfigFlow: Flow<com.example.modules.deepbluetooth.DeepBluetoothConfig> =
        context.dataStore.data.map { readDeepBluetooth(it) }.distinctUntilChanged()

    suspend fun updateDeepBluetoothConfig(
        transform: (com.example.modules.deepbluetooth.DeepBluetoothConfig) -> com.example.modules.deepbluetooth.DeepBluetoothConfig
    ) {
        context.dataStore.edit { prefs ->
            val c = transform(readDeepBluetooth(prefs))
            prefs[Keys.DBT_ENABLED] = c.enabled
            prefs[Keys.DBT_ON_CONNECT] = c.onConnect
            prefs[Keys.DBT_ON_DISCONNECT] = c.onDisconnect
            prefs[Keys.DBT_BAR] = c.showBatteryBar
            prefs[Keys.DBT_PERCENT] = c.showPercent
            prefs[Keys.DBT_STATUS] = c.showStatus
            prefs[Keys.DBT_ICON] = c.showDeviceIcon
            prefs[Keys.DBT_POSITION] = c.position.name
            prefs[Keys.DBT_DURATION] = c.durationSec
            prefs[Keys.DBT_NOTIFICATION] = c.alsoNotification
            prefs[Keys.DBT_DEVICES] = c.devices
        }
    }

    suspend fun updateCaffeineConfig(transform: (CaffeineConfig) -> CaffeineConfig) {
        context.dataStore.edit { prefs ->
            val currentActive = prefs[Keys.CAF_ACTIVE] ?: false
            val currentSince = prefs[Keys.CAF_ACTIVE_SINCE]
            val current = CaffeineConfig(
                isActive = currentActive,
                keepBright = prefs[Keys.CAF_KEEP_BRIGHT] ?: true,
                disableOnScreenOff = prefs[Keys.CAF_DISABLE_ON_SCREEN_OFF] ?: true,
                showStatusBarIcon = prefs[Keys.CAF_SHOW_STATUS_ICON] ?: true,
                timeoutMode = parseEnum(prefs[Keys.CAF_TIMEOUT_MODE], CaffeineTimeoutMode.INDEFINITE),
                activeSinceEpochMs = if (currentActive && currentSince != null && currentSince > 0L) currentSince else null
            )
            val updated = transform(current)
            prefs[Keys.CAF_ACTIVE] = updated.isActive
            prefs[Keys.CAF_KEEP_BRIGHT] = updated.keepBright
            prefs[Keys.CAF_DISABLE_ON_SCREEN_OFF] = updated.disableOnScreenOff
            prefs[Keys.CAF_SHOW_STATUS_ICON] = updated.showStatusBarIcon
            prefs[Keys.CAF_TIMEOUT_MODE] = updated.timeoutMode.name
            prefs[Keys.CAF_ACTIVE_SINCE] = updated.activeSinceEpochMs ?: 0L
        }
    }

    suspend fun updateAutomateConfig(transform: (AutomateConfig) -> AutomateConfig) {
        context.dataStore.edit { prefs ->
            val routines = readRoutines(prefs)
            val logs = AutomateSerializer.decodeLogs(prefs[Keys.AUTO_LOGS_JSON])
            val current = AutomateConfig(
                snapshots = AutomateSerializer.decodeSnapshots(prefs[Keys.AUTO_SNAPSHOTS_JSON]),
                masterEnabled = prefs[Keys.AUTO_MASTER_ENABLED] ?: true,
                executionNotificationsEnabled = prefs[Keys.AUTO_NOTIFICATIONS_ENABLED] ?: true,
                routines = routines,
                logs = logs
            )
            val updated = transform(current)
            prefs[Keys.AUTO_MASTER_ENABLED] = updated.masterEnabled
            prefs[Keys.AUTO_NOTIFICATIONS_ENABLED] = updated.executionNotificationsEnabled
            prefs[Keys.AUTO_ROUTINES_JSON] = AutomateSerializer.encodeRoutines(updated.routines)
            prefs[Keys.AUTO_MODES_SEEDED] = true
            prefs[Keys.AUTO_LOGS_JSON] = AutomateSerializer.encodeLogs(updated.logs)
            prefs[Keys.AUTO_SNAPSHOTS_JSON] = AutomateSerializer.encodeSnapshots(
                updated.snapshots.filterKeys { id -> updated.routines.any { it.id == id } }
            )
        }
    }

    /**
     * Routines enregistrées. Les installations d’avant les Modes reçoivent une fois
     * les modes proposés par défaut (ensuite, un mode supprimé ne revient pas).
     */
    private fun readRoutines(prefs: androidx.datastore.preferences.core.Preferences): List<com.example.modules.automate.AutomationRoutine> {
        val routines = AutomateSerializer.decodeRoutines(prefs[Keys.AUTO_ROUTINES_JSON])
            ?: return AutomateConfig.defaultRoutines()
        val seeded = prefs[Keys.AUTO_MODES_SEEDED] ?: false
        return if (!seeded && routines.none { it.isMode }) routines + AutomateConfig.defaultModes() else routines
    }

    suspend fun appendAutomateLog(
        routineId: String,
        entry: AutomateLogEntry
    ) {
        updateAutomateConfig { config ->
            val updatedRoutines = config.routines.map { routine ->
                if (routine.id == routineId) routine.copy(lastTriggeredMs = entry.timestampMs) else routine
            }
            val updatedLogs = (listOf(entry) + config.logs).take(30)
            config.copy(routines = updatedRoutines, logs = updatedLogs)
        }
    }

    suspend fun addClipboardEntry(entry: ClipboardEntry) {
        context.dataStore.edit { prefs ->
            val enabled = prefs[Keys.CLIP_ENABLED] ?: true
            if (!enabled) return@edit
            val existing = ClipboardSerializer.decodeEntries(prefs[Keys.CLIP_ENTRIES_JSON])
            val existingMatch = existing.firstOrNull { it.text == entry.text }
            val preservedPinned = existingMatch?.pinned ?: entry.pinned
            val filtered = existing.filterNot { it.text == entry.text }
            val updated = (listOf(entry.copy(pinned = preservedPinned)) + filtered).take(25)
            prefs[Keys.CLIP_ENTRIES_JSON] = ClipboardSerializer.encodeEntries(updated)
        }
    }

    suspend fun updateClipboardEntries(transform: (List<ClipboardEntry>) -> List<ClipboardEntry>) {
        context.dataStore.edit { prefs ->
            val existing = ClipboardSerializer.decodeEntries(prefs[Keys.CLIP_ENTRIES_JSON])
            val updated = transform(existing).take(25)
            prefs[Keys.CLIP_ENTRIES_JSON] = ClipboardSerializer.encodeEntries(updated)
        }
    }

    suspend fun setModuleEnabled(moduleId: String, enabled: Boolean) {
        when (moduleId) {
            OneHandModule.MODULE_ID -> {
                context.dataStore.edit { prefs ->
                    prefs[Keys.OH_MASTER_ENABLED] = enabled
                }
            }
            CaffeineModule.MODULE_ID -> {
                updateCaffeineConfig {
                    it.copy(
                        isActive = enabled,
                        activeSinceEpochMs = if (enabled) (it.activeSinceEpochMs ?: System.currentTimeMillis()) else null
                    )
                }
            }
            AutomateModule.MODULE_ID -> {
                updateAutomateConfig { it.copy(masterEnabled = enabled) }
            }
            ClipboardModule.MODULE_ID -> {
                context.dataStore.edit { prefs ->
                    prefs[Keys.CLIP_ENABLED] = enabled
                }
            }
            com.example.modules.deepbluetooth.DeepBluetoothModule.MODULE_ID -> {
                updateDeepBluetoothConfig { it.copy(enabled = enabled) }
            }
            com.example.modules.essentialkey.EssentialKeyModule.MODULE_ID -> {
                updateEssentialKeyConfig { it.copy(enabled = enabled) }
            }
        }
    }

    suspend fun resetOneHandDefaults() {
        saveOneHandConfig(OneHandConfig())
    }

    private fun writeHandle(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        side: HandleSide,
        handle: HandleConfig
    ) {
        if (side == HandleSide.LEFT) {
            prefs[Keys.OH_L_ENABLED] = handle.enabled
            prefs[Keys.OH_L_POS_Y] = handle.verticalPositionPercent
            prefs[Keys.OH_L_HEIGHT_PCT] = handle.heightPercent
            prefs[Keys.OH_L_WIDTH] = handle.widthDp
            prefs[Keys.OH_L_OPACITY] = handle.opacity
            prefs[Keys.OH_L_COLOR] = handle.colorStyle.name
            prefs[Keys.OH_L_SHORT_H] = handle.shortHorizontal.name
            prefs[Keys.OH_L_SHORT_UP] = handle.shortDiagonalUp.name
            prefs[Keys.OH_L_SHORT_DOWN] = handle.shortDiagonalDown.name
            prefs[Keys.OH_L_LONG_H] = handle.longHorizontal.name
            prefs[Keys.OH_L_LONG_UP] = handle.longDiagonalUp.name
            prefs[Keys.OH_L_LONG_DOWN] = handle.longDiagonalDown.name
        } else {
            prefs[Keys.OH_R_ENABLED] = handle.enabled
            prefs[Keys.OH_R_POS_Y] = handle.verticalPositionPercent
            prefs[Keys.OH_R_HEIGHT_PCT] = handle.heightPercent
            prefs[Keys.OH_R_WIDTH] = handle.widthDp
            prefs[Keys.OH_R_OPACITY] = handle.opacity
            prefs[Keys.OH_R_COLOR] = handle.colorStyle.name
            prefs[Keys.OH_R_SHORT_H] = handle.shortHorizontal.name
            prefs[Keys.OH_R_SHORT_UP] = handle.shortDiagonalUp.name
            prefs[Keys.OH_R_SHORT_DOWN] = handle.shortDiagonalDown.name
            prefs[Keys.OH_R_LONG_H] = handle.longHorizontal.name
            prefs[Keys.OH_R_LONG_UP] = handle.longDiagonalUp.name
            prefs[Keys.OH_R_LONG_DOWN] = handle.longDiagonalDown.name
        }
    }

    private inline fun <reified T : Enum<T>> parseEnum(raw: String?, fallback: T): T {
        if (raw.isNullOrBlank()) return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }

    companion object {
        @Volatile
        private var instance: ModuYouPreferences? = null

        fun getInstance(context: Context): ModuYouPreferences {
            return instance ?: synchronized(this) {
                instance ?: ModuYouPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
