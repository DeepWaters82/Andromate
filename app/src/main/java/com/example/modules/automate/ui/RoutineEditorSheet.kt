package com.example.modules.automate.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.modules.automate.AutomateActionExecutor
import com.example.modules.automate.AutomateConfig
import com.example.modules.automate.AutomateController
import com.example.modules.automate.AutomateModule
import com.example.modules.automate.AutomationRoutine
import com.example.modules.automate.LaunchableApp
import com.example.modules.automate.RoutineAction
import com.example.modules.automate.RoutineActionType
import com.example.modules.automate.RoutineStyle
import com.example.modules.automate.TriggerCategory
import com.example.modules.automate.TriggerType
import com.example.modules.onehand.OneHandModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

private const val MANUAL_ENTRY_SENTINEL = "__ANDROMATE_MANUAL__"

private data class ConnectionDropdownItem(
    val filterValue: String,
    val label: String,
    val isManualTrigger: Boolean = false
)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorSheet(
    initialRoutine: AutomationRoutine?,
    onDismiss: () -> Unit,
    onSave: (AutomationRoutine) -> Unit,
    isMode: Boolean = initialRoutine?.isMode ?: false
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember(initialRoutine) { mutableStateOf(initialRoutine?.name.orEmpty()) }
    var iconKey by remember(initialRoutine) { mutableStateOf(initialRoutine?.iconKey.orEmpty()) }
    var colorKey by remember(initialRoutine) { mutableStateOf(initialRoutine?.colorKey ?: "blue") }
    var triggerType by remember(initialRoutine) {
        mutableStateOf(initialRoutine?.triggerType ?: if (isMode) TriggerType.MANUAL else TriggerType.WIFI_CONNECTED)
    }
    var filterName by remember(initialRoutine) { mutableStateOf(initialRoutine?.filterName.orEmpty()) }
    var filterLabel by remember(initialRoutine) { mutableStateOf(initialRoutine?.filterLabel.orEmpty()) }
    var triggerValue by remember(initialRoutine) { mutableIntStateOf(initialRoutine?.triggerValue ?: 20) }
    var actions by remember(initialRoutine) {
        mutableStateOf(
            initialRoutine?.actions?.associateBy { it.type }
                ?: mapOf(RoutineActionType.MEDIA_VOLUME to RoutineAction(RoutineActionType.MEDIA_VOLUME))
        )
    }
    var restoreOnEnd by remember(initialRoutine) { mutableStateOf(initialRoutine?.restoreOnEnd ?: true) }
    var notifyOnRun by remember(initialRoutine) { mutableStateOf(initialRoutine?.notifyOnRun ?: false) }
    var notifyTitle by remember(initialRoutine) { mutableStateOf(initialRoutine?.notifyTitle.orEmpty()) }
    var notifyText by remember(initialRoutine) { mutableStateOf(initialRoutine?.notifyText.orEmpty()) }

    var canWriteSettings by remember { mutableStateOf(AutomateActionExecutor.canWriteSystemSettings(context)) }
    var isAccessibilityOn by remember { mutableStateOf(OneHandModule.isAccessibilityServiceEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canWriteSettings = AutomateActionExecutor.canWriteSystemSettings(context)
        isAccessibilityOn = OneHandModule.isAccessibilityServiceEnabled(context)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = when {
                    isMode && initialRoutine == null -> "Nouveau mode"
                    isMode -> "Modifier le mode"
                    initialRoutine == null -> "Nouvelle routine"
                    else -> "Modifier la routine"
                },
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nom") },
                placeholder = { Text(if (isMode) "Lecture, Jeu…" else "Voiture, Bureau…") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("routine_name_input")
            )

            AppearanceSection(
                triggerType = triggerType,
                iconKey = iconKey,
                colorKey = colorKey,
                onIconChange = { iconKey = it },
                onColorChange = { colorKey = it }
            )

            TriggerSection(
                triggerType = triggerType,
                filterName = filterName,
                filterLabel = filterLabel,
                triggerValue = triggerValue,
                isAccessibilityOn = isAccessibilityOn,
                isMode = isMode,
                onTriggerChange = { newType ->
                    if (newType.category != triggerType.category) {
                        filterName = ""
                        filterLabel = ""
                    }
                    triggerType = newType
                    actions = actions.filterKeys {
                        !it.requiresBluetoothTrigger || newType == TriggerType.BLUETOOTH_CONNECTED
                    }
                },
                onFilterChange = { value, label ->
                    filterName = value
                    filterLabel = label
                },
                onTriggerValueChange = { triggerValue = it }
            )

            ActionsSection(
                title = if (isMode) "PENDANT LE MODE" else "ALORS",
                triggerType = triggerType,
                actions = actions,
                canWriteSettings = canWriteSettings,
                onActionsChange = { actions = it }
            )

            // Un mode rétablit toujours l’état d’avant quand il s’arrête.
            if (!isMode) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("À LA FIN")
                OptionSwitchRow(
                    title = "Rétablir l’état d’avant",
                    subtitle = triggerType.endDescription.replaceFirstChar { it.uppercase() },
                    checked = restoreOnEnd,
                    testTag = "editor_restore_switch",
                    onCheckedChange = { restoreOnEnd = it }
                )
            }

            NotificationSection(
                enabled = notifyOnRun,
                title = notifyTitle,
                text = notifyText,
                onEnabledChange = { notifyOnRun = it },
                onTitleChange = { notifyTitle = it },
                onTextChange = { notifyText = it }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Annuler")
                }
                val appMissing = triggerType.isApp && filterName.isBlank()
                Button(
                    onClick = {
                        val finalActions = RoutineActionType.entries.mapNotNull { actions[it] }
                        val saved = AutomationRoutine(
                            id = initialRoutine?.id ?: "routine_${UUID.randomUUID()}",
                            name = name.trim().ifBlank { triggerType.label.removeSuffix("…") },
                            enabled = initialRoutine?.enabled ?: true,
                            triggerType = triggerType,
                            filterName = filterName.trim(),
                            actions = finalActions,
                            lastTriggeredMs = initialRoutine?.lastTriggeredMs,
                            filterLabel = filterLabel,
                            triggerValue = triggerValue,
                            iconKey = iconKey,
                            colorKey = colorKey,
                            restoreOnEnd = isMode || restoreOnEnd,
                            notifyOnRun = notifyOnRun,
                            notifyTitle = notifyTitle.trim(),
                            notifyText = notifyText.trim(),
                            isMode = isMode
                        )
                        onSave(saved)
                    },
                    enabled = actions.isNotEmpty() && !appMissing,
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("save_routine_button")
                ) {
                    Icon(imageVector = Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Enregistrer")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection(
    triggerType: TriggerType,
    iconKey: String,
    colorKey: String,
    onIconChange: (String) -> Unit,
    onColorChange: (String) -> Unit
) {
    val accent = RoutineStyle.colorFor(colorKey) ?: MaterialTheme.colorScheme.primary
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Replié par défaut : seul l’aperçu est affiché, un appui ouvre les choix.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { expanded = !expanded }
                .testTag("editor_style_toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.24f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = RoutineStyle.icons.firstOrNull { it.key == iconKey }?.icon ?: triggerType.icon,
                    contentDescription = null,
                    tint = accent
                )
            }
            Text(text = "Icône et couleur", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null
            )
        }

        if (!expanded) return@Column

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Première pastille : icône du déclencheur (par défaut).
            IconChoice(
                icon = triggerType.icon,
                selected = iconKey.isBlank(),
                accent = accent,
                description = "Icône du déclencheur",
                onClick = { onIconChange("") }
            )
            RoutineStyle.icons.forEach { option ->
                IconChoice(
                    icon = option.icon,
                    selected = option.key == iconKey,
                    accent = accent,
                    description = option.label,
                    onClick = { onIconChange(option.key) }
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ColorChoice(
                color = MaterialTheme.colorScheme.primary,
                selected = colorKey.isBlank(),
                onClick = { onColorChange("") }
            )
            RoutineStyle.colors.forEach { option ->
                ColorChoice(
                    color = option.color,
                    selected = option.key == colorKey,
                    onClick = { onColorChange(option.key) }
                )
            }
        }
    }
}

@Composable
private fun IconChoice(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) accent.copy(alpha = 0.24f)
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .then(
                if (selected) Modifier.border(2.dp, accent, RoundedCornerShape(14.dp)) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ColorChoice(
    color: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun OptionSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}

@Composable
private fun NotificationSection(
    enabled: Boolean,
    title: String,
    text: String,
    onEnabledChange: (Boolean) -> Unit,
    onTitleChange: (String) -> Unit,
    onTextChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("NOTIFICATION")
        OptionSwitchRow(
            title = "Notification personnalisée",
            subtitle = "Au lancement et à la fin",
            checked = enabled,
            testTag = "editor_notify_switch",
            onCheckedChange = onEnabledChange
        )
        AnimatedVisibility(visible = enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Titre") },
                    placeholder = { Text("Nom de la routine") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("editor_notify_title")
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = { Text("Message") },
                    placeholder = { Text("Actions effectuées") },
                    supportingText = { Text("{actions}  {routine}") },
                    modifier = Modifier.fillMaxWidth().testTag("editor_notify_text")
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TriggerSection(
    triggerType: TriggerType,
    filterName: String,
    filterLabel: String,
    triggerValue: Int,
    isAccessibilityOn: Boolean,
    isMode: Boolean,
    onTriggerChange: (TriggerType) -> Unit,
    onFilterChange: (String, String) -> Unit,
    onTriggerValueChange: (Int) -> Unit
) {
    val context = LocalContext.current
    var showAppPicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(if (isMode) "ACTIVATION" else "SI")

        var triggerMenuOpen by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = triggerMenuOpen,
            onExpandedChange = { triggerMenuOpen = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = triggerType.label,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                leadingIcon = { Icon(imageVector = triggerType.icon, contentDescription = null) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = triggerMenuOpen) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .testTag("editor_trigger_dropdown")
            )
            ExposedDropdownMenu(
                expanded = triggerMenuOpen,
                onDismissRequest = { triggerMenuOpen = false }
            ) {
                TriggerType.entries.filter { isMode || it != TriggerType.MANUAL }.forEach { trigger ->
                    DropdownMenuItem(
                        text = { Text(trigger.label) },
                        leadingIcon = { Icon(imageVector = trigger.icon, contentDescription = null) },
                        onClick = {
                            triggerMenuOpen = false
                            onTriggerChange(trigger)
                        },
                        modifier = Modifier.testTag("editor_trigger_${trigger.name.lowercase()}")
                    )
                }
            }
        }

        when (triggerType.category) {
            TriggerCategory.WIFI, TriggerCategory.BLUETOOTH ->
                ConnectionMultiPicker(
                    triggerType = triggerType,
                    filterName = filterName,
                    onFilterChange = { onFilterChange(it, "") }
                )

            TriggerCategory.USB_CHARGING ->
                ConnectionFilterField(
                    triggerType = triggerType,
                    filterName = filterName,
                    onFilterChange = { onFilterChange(it, "") }
                )

            TriggerCategory.CHARGING -> Text(
                text = "Tout chargeur, même sans fil",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TriggerCategory.BATTERY -> Column {
                Text(
                    text = if (triggerType == TriggerType.BATTERY_BELOW) {
                        "≤ $triggerValue %"
                    } else {
                        "≥ $triggerValue %"
                    },
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = triggerValue.toFloat(),
                    onValueChange = { onTriggerValueChange((it / 5f).roundToInt() * 5) },
                    valueRange = 5f..100f,
                    steps = 18,
                    modifier = Modifier.testTag("editor_battery_slider")
                )
            }

            TriggerCategory.APP -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showAppPicker = true },
                    modifier = Modifier.fillMaxWidth().testTag("editor_pick_app_button")
                ) {
                    Icon(imageVector = Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(if (filterName.isBlank()) "Choisir une application" else filterLabel.ifBlank { filterName })
                }
                if (!isAccessibilityOn) {
                    AssistChip(
                        onClick = { OneHandModule.openAccessibilitySettings(context) },
                        label = { Text("Activer l’accessibilité") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }

            TriggerCategory.MANUAL -> Unit
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onPick = { app ->
                onFilterChange(app.packageName, app.label)
                showAppPicker = false
            }
        )
    }
}

@Composable
private fun AppPickerDialog(
    onDismiss: () -> Unit,
    onPick: (LaunchableApp) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<LaunchableApp>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { AutomateController.queryLaunchableApps(context) }
    }
    val filtered = apps.orEmpty().filter {
        query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choisir une application") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text("Rechercher") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (apps == null) {
                    Text("Chargement des applications…", style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(app) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppIcon(packageName = app.packageName)
                            Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }
    val icon = bitmap
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(36.dp))
    } else {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionFilterField(
    triggerType: TriggerType,
    filterName: String,
    onFilterChange: (String) -> Unit
) {
    val context = LocalContext.current
    var availableConnections by remember { mutableStateOf(AutomateModule.queryAvailableConnections(context)) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var showManualInput by remember(triggerType.category) { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        availableConnections = AutomateModule.queryAvailableConnections(context)
    }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        availableConnections = AutomateModule.queryAvailableConnections(context)
    }

    val options: List<ConnectionDropdownItem> = when (triggerType.category) {
        TriggerCategory.WIFI -> buildList {
            add(ConnectionDropdownItem("", "Tous les réseaux"))
            val ssid = availableConnections.currentWifiSsid
            if (!ssid.isNullOrBlank()) add(ConnectionDropdownItem(ssid, "$ssid (connecté)"))
            if (filterName.isNotBlank() && filterName != ssid) add(ConnectionDropdownItem(filterName, filterName))
            add(ConnectionDropdownItem(MANUAL_ENTRY_SENTINEL, "Autre réseau…", isManualTrigger = true))
        }
        TriggerCategory.BLUETOOTH -> buildList {
            add(ConnectionDropdownItem("", "Tous les appareils"))
            availableConnections.pairedBluetoothDevices.forEach { add(ConnectionDropdownItem(it, it)) }
            if (filterName.isNotBlank() && filterName !in availableConnections.pairedBluetoothDevices) {
                add(ConnectionDropdownItem(filterName, filterName))
            }
            add(ConnectionDropdownItem(MANUAL_ENTRY_SENTINEL, "Autre appareil…", isManualTrigger = true))
        }
        else -> listOf(
            ConnectionDropdownItem(AutomateConfig.USB_FILTER_ANY, "Tout câble (USB ou secteur)"),
            ConnectionDropdownItem(AutomateConfig.USB_FILTER_USB_ONLY, "Port USB PC uniquement"),
            ConnectionDropdownItem(AutomateConfig.USB_FILTER_AC_ONLY, "Chargeur secteur uniquement")
        )
    }
    val selectedText = if (showManualInput && !triggerType.isUsbCharging) {
        filterName.ifBlank { "Saisie manuelle" }
    } else {
        options.firstOrNull { !it.isManualTrigger && it.filterValue == filterName }?.label
            ?: AutomateConfig.formatFilterDisplay(triggerType, filterName)
    }

    ExposedDropdownMenuBox(
        expanded = dropdownExpanded,
        onExpandedChange = {
            availableConnections = AutomateModule.queryAvailableConnections(context)
            dropdownExpanded = !dropdownExpanded
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = {
                Text(
                    when (triggerType.category) {
                        TriggerCategory.WIFI -> "Réseau"
                        TriggerCategory.BLUETOOTH -> "Appareil"
                        else -> "Branchement"
                    }
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag("routine_filter_dropdown")
        )
        ExposedDropdownMenu(
            expanded = dropdownExpanded,
            onDismissRequest = { dropdownExpanded = false }
        ) {
            options.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    leadingIcon = if (item.isManualTrigger) {
                        { Icon(imageVector = Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        dropdownExpanded = false
                        if (item.isManualTrigger) {
                            showManualInput = true
                        } else {
                            showManualInput = false
                            onFilterChange(item.filterValue)
                        }
                    }
                )
            }
        }
    }

    AnimatedVisibility(visible = showManualInput && !triggerType.isUsbCharging) {
        OutlinedTextField(
            value = filterName,
            onValueChange = onFilterChange,
            label = {
                Text(if (triggerType.isBluetooth) "Nom de l’appareil" else "Nom du Wi-Fi")
            },
            placeholder = { Text("Laisser vide pour tous") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("routine_filter_input")
        )
    }

    if (triggerType.isWifi && availableConnections.currentWifiSsid == null && !availableConnections.hasLocationPermission) {
        AssistChip(
            onClick = { locationPermissionLauncher.launch(AutomateModule.LOCATION_PERMISSIONS) },
            label = { Text("Détecter le Wi-Fi actuel") },
            leadingIcon = { Icon(imageVector = Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp)) },
            modifier = Modifier.testTag("editor_grant_location_chip")
        )
    }
    if (triggerType.isBluetooth && !availableConnections.hasBtPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        AssistChip(
            onClick = { bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
            label = { Text("Voir les appareils appairés") },
            leadingIcon = { Icon(imageVector = Icons.Rounded.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp)) },
            modifier = Modifier.testTag("editor_grant_bluetooth_chip")
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionsSection(
    title: String,
    triggerType: TriggerType,
    actions: Map<RoutineActionType, RoutineAction>,
    canWriteSettings: Boolean,
    onActionsChange: (Map<RoutineActionType, RoutineAction>) -> Unit
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)

        if (!canWriteSettings && actions.keys.any { it.needsWriteSettings }) {
            AssistChip(
                onClick = { AutomateActionExecutor.openWriteSettingsPermission(context) },
                label = { Text("Autoriser les paramètres système") },
                leadingIcon = { Icon(imageVector = Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("editor_write_settings_chip")
            )
        }

        RoutineActionType.entries.filter {
            it.selectable && (!it.requiresBluetoothTrigger || triggerType == TriggerType.BLUETOOTH_CONNECTED)
        }.forEach { actionType ->
            val current = actions[actionType]
            val checked = current != null
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onActionsChange(
                            if (checked) {
                                actions - actionType
                            } else {
                                // Les actions d’un même groupe s’excluent (ex. Portrait / Paysage).
                                actions.filterKeys { it.group != actionType.group } +
                                    (actionType to RoutineAction(actionType, RoutineAction.defaultParamFor(actionType)))
                            }
                        )
                    }
                    .testTag("editor_action_${actionType.name.lowercase()}"),
                shape = RoundedCornerShape(16.dp),
                color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = actionType.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(text = actionType.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Checkbox(checked = checked, onCheckedChange = null)
                    }

                    if (current != null) {
                        ActionParameterEditor(
                            action = current,
                            onChange = { updated -> onActionsChange(actions + (actionType to updated)) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionParameterEditor(
    action: RoutineAction,
    onChange: (RoutineAction) -> Unit
) {
    when (action.type) {
        RoutineActionType.MEDIA_VOLUME, RoutineActionType.BRIGHTNESS_LEVEL -> {
            Column(modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp)) {
                Text(
                    text = "Niveau : ${action.intParam} %",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = action.intParam.toFloat(),
                    onValueChange = { onChange(action.copy(intParam = it.roundToInt())) },
                    valueRange = (if (action.type == RoutineActionType.BRIGHTNESS_LEVEL) 1f else 0f)..100f
                )
            }
        }
        RoutineActionType.SCREEN_TIMEOUT -> {
            FlowRow(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RoutineAction.SCREEN_TIMEOUT_PRESETS.forEach { seconds ->
                    FilterChip(
                        selected = action.intParam == seconds,
                        onClick = { onChange(action.copy(intParam = seconds)) },
                        label = { Text(RoutineAction.formatTimeoutSeconds(seconds)) }
                    )
                }
            }
        }
        else -> Unit
    }
}

/**
 * Choix des appareils Bluetooth ou des réseaux Wi-Fi qui déclenchent la routine (plusieurs
 * possibles ; aucun coché = tous). Indique aussi ce qu’il manque pour que ça marche :
 * pour le Wi-Fi, Android masque le nom du réseau sans la localisation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionMultiPicker(
    triggerType: TriggerType,
    filterName: String,
    onFilterChange: (String) -> Unit
) {
    val context = LocalContext.current
    var connections by remember { mutableStateOf(AutomateModule.queryAvailableConnections(context)) }
    val refresh = { connections = AutomateModule.queryAvailableConnections(context) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    val singleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && triggerType.isWifi) AutomateModule.openAppPermissionSettings(context)
        refresh()
    }
    // Retour d’un écran de réglages (localisation, autorisations) : on relit l’état.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val selected = AutomateConfig.filterNames(filterName)
    val isWifi = triggerType.isWifi
    val options: List<Pair<String, String?>> = buildList {
        if (isWifi) {
            connections.currentWifiSsid?.takeIf { it.isNotBlank() }?.let { add(it to "connecté") }
            selected.forEach { add(it to null) }
            connections.knownWifiNetworks.forEach { add(it to null) }
            connections.nearbyWifiNetworks.take(8).forEach { add(it to "à portée") }
        } else {
            selected.forEach { add(it to null) }
            com.example.modules.onehand.QuickToggles.connectedBluetoothNames(context).forEach { add(it to "connecté") }
            connections.pairedBluetoothDevices.forEach { add(it to null) }
        }
    }.distinctBy { it.first.lowercase() }.map { (name, tag) ->
        // Un nom coché garde son repère « connecté » / « à portée ».
        name to (tag ?: when {
            isWifi && name.equals(connections.currentWifiSsid, ignoreCase = true) -> "connecté"
            isWifi && connections.nearbyWifiNetworks.any { it.equals(name, ignoreCase = true) } -> "à portée"
            !isWifi && com.example.modules.onehand.QuickToggles.connectedBluetoothNames(context)
                .any { it.equals(name, ignoreCase = true) } -> "connecté"
            else -> null
        })
    }

    fun toggle(name: String) {
        val next = if (selected.any { it.equals(name, ignoreCase = true) }) {
            selected.filterNot { it.equals(name, ignoreCase = true) }
        } else {
            selected + name
        }
        onFilterChange(AutomateConfig.joinFilterNames(next))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = when {
                selected.isEmpty() && isWifi -> "Tous les réseaux (cocher pour limiter)"
                selected.isEmpty() -> "Tous les appareils (cocher pour limiter)"
                else -> "Uniquement : ${selected.joinToString(", ")}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        options.forEach { (name, tag) ->
            val checked = selected.any { it.equals(name, ignoreCase = true) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { toggle(name) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = checked, onCheckedChange = { toggle(name) })
                Text(text = name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (tag != null) {
                    Text(text = tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.size(8.dp))
                }
            }
        }
        if (options.isEmpty()) {
            Text(
                text = if (isWifi) "Aucun réseau connu pour l’instant." else "Aucun appareil appairé visible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        var manual by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = manual,
                onValueChange = { manual = it },
                singleLine = true,
                label = { Text(if (isWifi) "Autre réseau" else "Autre appareil") },
                modifier = Modifier.weight(1f).testTag("routine_filter_input")
            )
            androidx.compose.material3.IconButton(
                onClick = {
                    if (manual.isNotBlank()) {
                        onFilterChange(AutomateConfig.joinFilterNames(selected + manual.trim()))
                        manual = ""
                    }
                }
            ) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = "Ajouter")
            }
        }

        // Ce qui manque pour que le déclencheur reconnaisse le réseau ou l’appareil.
        if (isWifi) {
            when {
                !connections.hasLocationPermission -> RequirementChip(
                    text = "Autoriser la position (nom du Wi-Fi)",
                    onClick = { locationLauncher.launch(AutomateModule.LOCATION_PERMISSIONS) }
                )
                !connections.locationEnabled -> RequirementChip(
                    text = "Activer la localisation : sans elle, Android masque le nom du Wi-Fi",
                    onClick = { AutomateModule.openLocationSettings(context) }
                )
                selected.isNotEmpty() && !connections.hasBackgroundLocation -> RequirementChip(
                    text = "Position « Toujours autoriser » (Wi-Fi reconnu en arrière-plan)",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            singleLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }
                )
            }
        } else if (!connections.hasBtPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RequirementChip(
                text = "Autoriser les appareils à proximité",
                onClick = { singleLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) }
            )
        }
    }
}

@Composable
private fun RequirementChip(text: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(text) },
        leadingIcon = {
            Icon(imageVector = Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
        },
        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            labelColor = MaterialTheme.colorScheme.onErrorContainer,
            leadingIconContentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    )
}
