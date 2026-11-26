package com.example.modules.automate.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.modules.automate.AutomateActionExecutor
import com.example.modules.automate.RoutineStyle
import com.example.modules.onehand.OneHandModule
import com.example.modules.automate.AutomateController
import com.example.modules.automate.AutomateLogEntry
import com.example.modules.automate.AutomateModule
import com.example.modules.automate.AutomationRoutine
import com.example.modules.automate.LiveConnectivityState
import com.example.modules.automate.TriggerCategory
import com.example.ui.MainUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomateScreen(
    state: MainUiState,
    onNavigateBack: () -> Unit,
    onToggleMaster: (Boolean) -> Unit,
    onToggleRoutine: (String, Boolean) -> Unit,
    onSaveRoutine: (AutomationRoutine) -> Unit,
    onDeleteRoutine: (String) -> Unit,
    onTestRoutine: (AutomationRoutine) -> Unit,
    onRestoreRoutine: (String) -> Unit,
    onToggleMode: (String) -> Unit,
    onClearLogs: () -> Unit
) {
    val context = LocalContext.current
    val config = state.automateConfig
    val connectivity = state.liveConnectivity
    var editingRoutine by remember { mutableStateOf<AutomationRoutine?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    // 0 = Modes, 1 = Routines (comme les deux onglets de One UI).
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val onModesTab = selectedTab == 0

    var hasBtPermission by remember {
        mutableStateOf(AutomateModule.hasBluetoothConnectPermission(context))
    }
    var hasLocationPermission by remember {
        mutableStateOf(AutomateModule.hasPreciseLocationPermission(context))
    }
    var hasBackgroundLocation by remember {
        mutableStateOf(AutomateModule.hasBackgroundLocationPermission(context))
    }
    var isIgnoringBattery by remember {
        mutableStateOf(AutomateModule.isIgnoringBatteryOptimizations(context))
    }
    var hasNotifPermission by remember {
        mutableStateOf(AutomateModule.hasNotificationPermission(context))
    }
    var canWriteSettings by remember {
        mutableStateOf(AutomateActionExecutor.canWriteSystemSettings(context))
    }

    // Les autorisations se donnent souvent dans les Paramètres : on relit tout au retour.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasBtPermission = AutomateModule.hasBluetoothConnectPermission(context)
        hasLocationPermission = AutomateModule.hasPreciseLocationPermission(context)
        hasBackgroundLocation = AutomateModule.hasBackgroundLocationPermission(context)
        isIgnoringBattery = AutomateModule.isIgnoringBatteryOptimizations(context)
        hasNotifPermission = AutomateModule.hasNotificationPermission(context)
        canWriteSettings = AutomateActionExecutor.canWriteSystemSettings(context)
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasBtPermission = granted
        AutomateController.refreshLiveConnectivity(context)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasLocationPermission = AutomateModule.hasPreciseLocationPermission(context)
        hasBackgroundLocation = AutomateModule.hasBackgroundLocationPermission(context)
        AutomateController.refreshLiveConnectivity(context)
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasBackgroundLocation = granted
        if (!granted) AutomateModule.openAppDetailsSettings(context)
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    val activeRoutinesCount = config.plainRoutines.count { it.enabled }
    val activeMode = config.activeMode

    Scaffold(
        topBar = {
          Column {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Modes et routines",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (onModesTab) {
                                activeMode?.let { "Mode ${it.name}" } ?: "Aucun mode actif"
                            } else if (config.masterEnabled && activeRoutinesCount > 0) {
                                "$activeRoutinesCount routine${if (activeRoutinesCount > 1) "s" else ""} active${if (activeRoutinesCount > 1) "s" else ""}"
                            } else {
                                "Surveillance en pause"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("automate_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Retour au hub"
                        )
                    }
                },
                actions = {
                    if (!onModesTab) Switch(
                        checked = config.masterEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            onToggleMaster(enabled)
                        },
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("automate_master_switch")
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = onModesTab,
                    onClick = { selectedTab = 0 },
                    text = { Text("Modes") },
                    modifier = Modifier.testTag("automate_tab_modes")
                )
                Tab(
                    selected = !onModesTab,
                    onClick = { selectedTab = 1 },
                    text = { Text("Routines") },
                    modifier = Modifier.testTag("automate_tab_routines")
                )
            }
          }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingRoutine = null
                    isCreatingNew = true
                },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null
                    )
                },
                text = { Text(if (onModesTab) "Nouveau mode" else "Nouvelle routine") },
                modifier = Modifier.testTag("automate_add_routine_fab")
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (onModesTab) {
                    modesContent(
                        modes = config.modes,
                        activeModeId = activeMode?.id,
                        onToggle = onToggleMode,
                        onEdit = { mode ->
                            editingRoutine = mode
                            isCreatingNew = false
                        },
                        onDelete = { mode -> onDeleteRoutine(mode.id) }
                    )
                } else item {
                    ConnectivityMonitorCard(
                        masterEnabled = config.masterEnabled,
                        activeCount = activeRoutinesCount,
                        connectivity = connectivity
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBtPermission) {
                    item {
                        BluetoothPermissionBanner(
                            onGrantClick = {
                                btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            }
                        )
                    }
                }

                val hasNamedWifiRoutine = config.routines.any {
                    it.triggerType.category == TriggerCategory.WIFI && it.filterName.isNotBlank()
                }
                if (!hasLocationPermission && hasNamedWifiRoutine) {
                    item {
                        LocationPermissionBanner(
                            onGrantClick = {
                                locationPermissionLauncher.launch(AutomateModule.LOCATION_PERMISSIONS)
                            }
                        )
                    }
                }

                if (hasLocationPermission && !hasBackgroundLocation && hasNamedWifiRoutine &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ) {
                    item {
                        SetupBanner(
                            icon = Icons.Rounded.LocationOn,
                            title = "Localisation « Toujours autoriser »",
                            text = "Pour reconnaître le Wi-Fi app fermée.",
                            buttonLabel = "Régler",
                            testTag = "automate_bg_location_button",
                            onClick = {
                                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }
                        )
                    }
                }

                if (!isIgnoringBattery && activeRoutinesCount > 0) {
                    item {
                        SetupBanner(
                            icon = Icons.Rounded.BatterySaver,
                            title = "Optimisation de la batterie",
                            text = "Évite que le téléphone coupe les routines.",
                            buttonLabel = "Autoriser",
                            testTag = "automate_battery_button",
                            onClick = { AutomateModule.requestIgnoreBatteryOptimizations(context) }
                        )
                    }
                }

                val needsWriteSettings = config.routines.any { routine ->
                    routine.actions.any { it.type.needsWriteSettings }
                }
                if (needsWriteSettings && !canWriteSettings) {
                    item {
                        SetupBanner(
                            icon = Icons.Rounded.ScreenRotation,
                            title = "Modifier les paramètres système",
                            text = "Rotation, mise en veille, luminosité.",
                            buttonLabel = "Autoriser",
                            testTag = "automate_write_settings_button",
                            onClick = { AutomateActionExecutor.openWriteSettingsPermission(context) }
                        )
                    }
                }

                if (config.routines.any { it.triggerType.isApp } && !state.isAccessibilityEnabled) {
                    item {
                        SetupBanner(
                            icon = Icons.Rounded.Apps,
                            title = "Service Une Main + requis",
                            text = "Requis pour « Appli ouverte ».",
                            buttonLabel = "Activer",
                            testTag = "automate_accessibility_button",
                            onClick = { OneHandModule.openAccessibilitySettings(context) }
                        )
                    }
                }

                if (!state.hasDndPolicyAccess) {
                    item {
                        DndPolicyPermissionBanner(
                            onGrantClick = { AutomateModule.openDndPolicySettings(context) }
                        )
                    }
                }

                if (onModesTab) {
                    // Les modes sont affichés plus haut.
                } else if (config.plainRoutines.isEmpty()) {
                    item {
                        EmptyRoutinesCard(
                            onCreateRoutine = {
                                editingRoutine = null
                                isCreatingNew = true
                            }
                        )
                    }
                } else {
                    items(config.plainRoutines, key = { it.id }) { routine ->
                        RoutineCard(
                            routine = routine,
                            masterEnabled = config.masterEnabled,
                            isRunning = config.snapshots.containsKey(routine.id),
                            onToggle = { enabled -> onToggleRoutine(routine.id, enabled) },
                            onTest = { onTestRoutine(routine) },
                            onRestore = { onRestoreRoutine(routine.id) },
                            onEdit = {
                                editingRoutine = routine
                                isCreatingNew = false
                            },
                            onDelete = { onDeleteRoutine(routine.id) }
                        )
                    }
                }

                if (!onModesTab) item {
                    ExecutionHistoryCard(
                        logs = config.logs,
                        onClear = onClearLogs
                    )
                }
            }
        }
    }

    if (isCreatingNew || editingRoutine != null) {
        RoutineEditorSheet(
            initialRoutine = editingRoutine,
            isMode = editingRoutine?.isMode ?: onModesTab,
            onDismiss = {
                isCreatingNew = false
                editingRoutine = null
            },
            onSave = { saved ->
                onSaveRoutine(saved)
                isCreatingNew = false
                editingRoutine = null
            }
        )
    }
}

@Composable
private fun ConnectivityMonitorCard(
    masterEnabled: Boolean,
    activeCount: Int,
    connectivity: LiveConnectivityState
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoMode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (masterEnabled && activeCount > 0) {
                            "Routines actives"
                        } else {
                            "En veille"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SensorStatusPill(
                    icon = Icons.Rounded.Wifi,
                    label = "Wi-Fi : ${connectivity.wifiLabel}",
                    active = connectivity.isWifiConnected,
                    modifier = Modifier.weight(1f)
                )
                SensorStatusPill(
                    icon = Icons.Rounded.Bluetooth,
                    label = "BT : ${connectivity.bluetoothLabel}",
                    active = connectivity.isBluetoothConnected,
                    modifier = Modifier.weight(1f)
                )
            }

            SensorStatusPill(
                icon = Icons.Rounded.Usb,
                label = connectivity.chargingLabel,
                active = connectivity.isUsbCharging,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SensorStatusPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (active) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SetupBanner(
    icon: ImageVector,
    title: String,
    text: String,
    buttonLabel: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                )
            }
            FilledTonalButton(
                onClick = onClick,
                modifier = Modifier.testTag(testTag)
            ) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun BluetoothPermissionBanner(onGrantClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Accès Bluetooth",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "Pour reconnaître vos appareils.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                )
            }
            FilledTonalButton(
                onClick = onGrantClick,
                modifier = Modifier.testTag("automate_bt_permission_button")
            ) {
                Text("Autoriser")
            }
        }
    }
}

@Composable
private fun DndPolicyPermissionBanner(onGrantClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.DoNotDisturbOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Accès Ne pas déranger",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Pour Ne pas déranger et Silencieux.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
            FilledTonalButton(
                onClick = onGrantClick,
                modifier = Modifier.testTag("automate_dnd_permission_button")
            ) {
                Text("Autoriser")
            }
        }
    }
}

@Composable
private fun LocationPermissionBanner(onGrantClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MyLocation,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Localisation requise",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Pour reconnaître le nom du Wi-Fi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                )
            }
            FilledTonalButton(
                onClick = onGrantClick,
                modifier = Modifier.testTag("automate_location_permission_button")
            ) {
                Text("Autoriser")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineCard(
    routine: AutomationRoutine,
    masterEnabled: Boolean,
    isRunning: Boolean,
    onToggle: (Boolean) -> Unit,
    onTest: () -> Unit,
    onRestore: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val active = routine.enabled && masterEnabled
    val accent = RoutineStyle.colorFor(routine.colorKey) ?: MaterialTheme.colorScheme.primary
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("routine_card_${routine.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isRunning) {
                accent.copy(alpha = 0.14f).compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (active) accent.copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = RoutineStyle.iconFor(routine),
                            contentDescription = null,
                            tint = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column {
                        Text(
                            text = routine.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Si : ${routine.triggerSummary()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = routine.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("routine_toggle_${routine.id}")
                )
            }

            if (isRunning) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = accent.copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "En cours",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onRestore,
                            modifier = Modifier.testTag("routine_restore_${routine.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rétablir")
                        }
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                routine.actions.forEach { action ->
                    RoutineBadge(icon = action.type.icon, label = action.displayBadge(), onClick = onEdit)
                }
                if (routine.restoreOnEnd) {
                    RoutineBadge(icon = Icons.Rounded.Restore, label = "Retour auto", onClick = onEdit)
                }
                if (routine.notifyOnRun) {
                    RoutineBadge(icon = Icons.Rounded.Notifications, label = "Notification", onClick = onEdit)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onTest,
                    modifier = Modifier.testTag("routine_test_${routine.id}"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tester")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("routine_edit_${routine.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Modifier la routine"
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("routine_delete_${routine.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Supprimer la routine"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineBadge(icon: ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = null
    )
}

@Composable
private fun EmptyRoutinesCard(onCreateRoutine: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoMode,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Aucune routine",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Appuyez sur + pour en créer une.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(onClick = onCreateRoutine) {
                Text("Créer une routine")
            }
        }
    }
}

@Composable
private fun ExecutionHistoryCard(
    logs: List<AutomateLogEntry>,
    onClear: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Journal",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (logs.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text("Effacer")
                    }
                }
            }

            if (logs.isEmpty()) {
                Text(
                    text = "Aucun déclenchement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                logs.take(8).forEachIndexed { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = entry.routineName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${entry.triggerDetail} → ${entry.summary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatter.format(Date(entry.timestampMs)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
