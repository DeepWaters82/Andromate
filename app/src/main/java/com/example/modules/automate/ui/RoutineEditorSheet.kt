package com.example.modules.automate.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MyLocation
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.modules.automate.AutomateConfig
import com.example.modules.automate.AutomateModule
import com.example.modules.automate.AutomationRoutine
import com.example.modules.automate.RoutineAction
import com.example.modules.automate.RoutineActionType
import com.example.modules.automate.TriggerCategory
import com.example.modules.automate.TriggerType
import java.util.UUID
import kotlin.math.roundToInt

private const val MANUAL_ENTRY_SENTINEL = "__ANDROMATE_MANUAL__"

private data class ConnectionDropdownItem(
    val filterValue: String,
    val label: String,
    val isManualTrigger: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoutineEditorSheet(
    initialRoutine: AutomationRoutine?,
    onDismiss: () -> Unit,
    onSave: (AutomationRoutine) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var availableConnections by remember {
        mutableStateOf(AutomateModule.queryAvailableConnections(context))
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        availableConnections = AutomateModule.queryAvailableConnections(context)
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        availableConnections = AutomateModule.queryAvailableConnections(context)
    }

    var name by remember(initialRoutine) {
        mutableStateOf(initialRoutine?.name.orEmpty())
    }
    var triggerType by remember(initialRoutine) {
        mutableStateOf(initialRoutine?.triggerType ?: TriggerType.WIFI_CONNECTED)
    }
    var filterName by remember(initialRoutine) {
        mutableStateOf(initialRoutine?.filterName.orEmpty())
    }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var showManualInput by remember(initialRoutine) { mutableStateOf(false) }

    var selectedTypes by remember(initialRoutine) {
        mutableStateOf(
            initialRoutine?.actions?.map { it.type }?.toSet()
                ?: setOf(RoutineActionType.MEDIA_VOLUME)
        )
    }
    var volumePercent by remember(initialRoutine) {
        mutableIntStateOf(
            initialRoutine?.actions
                ?.firstOrNull { it.type == RoutineActionType.MEDIA_VOLUME }
                ?.intParam ?: 75
        )
    }
    var notificationMessage by remember(initialRoutine) {
        mutableStateOf(
            initialRoutine?.actions
                ?.firstOrNull { it.type == RoutineActionType.SEND_NOTIFICATION }
                ?.textParam ?: ""
        )
    }

    val dropdownOptions: List<ConnectionDropdownItem> = remember(
        triggerType,
        availableConnections,
        filterName
    ) {
        when (triggerType.category) {
            TriggerCategory.WIFI -> buildList {
                add(ConnectionDropdownItem("", "N'importe quel réseau Wi-Fi"))
                val ssid = availableConnections.currentWifiSsid
                if (!ssid.isNullOrBlank()) {
                    add(ConnectionDropdownItem(ssid, "$ssid (connecté)"))
                }
                if (filterName.isNotBlank() && filterName != ssid) {
                    add(ConnectionDropdownItem(filterName, filterName))
                }
                add(ConnectionDropdownItem(MANUAL_ENTRY_SENTINEL, "Saisir un nom Wi-Fi…", isManualTrigger = true))
            }
            TriggerCategory.BLUETOOTH -> buildList {
                add(ConnectionDropdownItem("", "N'importe quel appareil Bluetooth"))
                availableConnections.pairedBluetoothDevices.forEach { deviceName ->
                    add(ConnectionDropdownItem(deviceName, deviceName))
                }
                if (filterName.isNotBlank() && filterName !in availableConnections.pairedBluetoothDevices) {
                    add(ConnectionDropdownItem(filterName, filterName))
                }
                add(ConnectionDropdownItem(MANUAL_ENTRY_SENTINEL, "Saisir un appareil…", isManualTrigger = true))
            }
            TriggerCategory.USB_CHARGING -> listOf(
                ConnectionDropdownItem(
                    AutomateConfig.USB_FILTER_ANY,
                    "Tout câble / charge USB (USB ou secteur)"
                ),
                ConnectionDropdownItem(
                    AutomateConfig.USB_FILTER_USB_ONLY,
                    "Port USB PC uniquement"
                ),
                ConnectionDropdownItem(
                    AutomateConfig.USB_FILTER_AC_ONLY,
                    "Chargeur secteur USB-C uniquement"
                )
            )
        }
    }

    val selectedDisplayText = remember(triggerType, filterName, dropdownOptions, showManualInput) {
        if (showManualInput && !triggerType.isUsbCharging) {
            filterName.ifBlank { "Saisie manuelle" }
        } else {
            dropdownOptions.firstOrNull { !it.isManualTrigger && it.filterValue == filterName }?.label
                ?: AutomateConfig.formatFilterDisplay(triggerType, filterName)
        }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (initialRoutine == null) "Nouvelle routine" else "Modifier la routine",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nom du mode ou de la routine") },
                placeholder = { Text("Ex : Mode Voiture, Bureau USB…") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("routine_name_input")
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SI (Déclencheur Wi-Fi / Bluetooth / USB)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TriggerType.entries.forEach { trigger ->
                        val selected = triggerType == trigger
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (triggerType.category != trigger.category) {
                                    filterName = ""
                                    showManualInput = false
                                }
                                triggerType = trigger
                                availableConnections = AutomateModule.queryAvailableConnections(context)
                            },
                            label = { Text(trigger.label) },
                            leadingIcon = {
                                Icon(
                                    imageVector = trigger.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.testTag("editor_trigger_${trigger.name.lowercase()}")
                        )
                    }
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
                        value = selectedDisplayText,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = {
                            Text(
                                when (triggerType.category) {
                                    TriggerCategory.WIFI -> "Réseau Wi-Fi cible"
                                    TriggerCategory.BLUETOOTH -> "Appareil Bluetooth cible"
                                    TriggerCategory.USB_CHARGING -> "Type de branchement USB"
                                }
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                        },
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
                        dropdownOptions.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.label) },
                                leadingIcon = if (item.isManualTrigger) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    dropdownExpanded = false
                                    if (item.isManualTrigger) {
                                        showManualInput = true
                                    } else {
                                        showManualInput = false
                                        filterName = item.filterValue
                                    }
                                }
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = showManualInput && !triggerType.isUsbCharging) {
                    OutlinedTextField(
                        value = filterName,
                        onValueChange = { filterName = it },
                        label = {
                            Text(
                                if (triggerType.isBluetooth) {
                                    "Nom exact ou partiel de l'appareil"
                                } else {
                                    "Nom exact ou partiel du réseau Wi-Fi (SSID)"
                                }
                            )
                        },
                        placeholder = { Text("Laisser vide pour tous") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("routine_filter_input")
                    )
                }

                if (triggerType.isWifi && availableConnections.currentWifiSsid == null && !availableConnections.hasLocationPermission) {
                    AssistChip(
                        onClick = {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        },
                        label = { Text("Détecter le nom Wi-Fi actuel (Localisation)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("editor_grant_location_chip")
                    )
                }

                if (triggerType.isBluetooth && !availableConnections.hasBtPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    AssistChip(
                        onClick = {
                            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        },
                        label = { Text("Charger les appareils Bluetooth appairés") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Bluetooth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("editor_grant_bluetooth_chip")
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ALORS (Actions à exécuter)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                RoutineActionType.entries.forEach { actionType ->
                    val checked = actionType in selectedTypes
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                selectedTypes = if (checked) {
                                    selectedTypes - actionType
                                } else {
                                    selectedTypes + actionType
                                }
                            }
                            .testTag("editor_action_${actionType.name.lowercase()}"),
                        shape = RoundedCornerShape(16.dp),
                        color = if (checked) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
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
                                Text(
                                    text = actionType.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = null
                                )
                            }

                            AnimatedVisibility(visible = checked && actionType == RoutineActionType.MEDIA_VOLUME) {
                                Column(modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp)) {
                                    Text(
                                        text = "Niveau cible : $volumePercent%",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Slider(
                                        value = volumePercent.toFloat(),
                                        onValueChange = { volumePercent = it.roundToInt() },
                                        valueRange = 0f..100f
                                    )
                                }
                            }

                            AnimatedVisibility(visible = checked && actionType == RoutineActionType.SEND_NOTIFICATION) {
                                OutlinedTextField(
                                    value = notificationMessage,
                                    onValueChange = { notificationMessage = it },
                                    label = { Text("Texte de la notification") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Annuler")
                }

                Button(
                    onClick = {
                        val finalActions = selectedTypes.map { type ->
                            when (type) {
                                RoutineActionType.MEDIA_VOLUME -> RoutineAction(type, intParam = volumePercent)
                                RoutineActionType.SEND_NOTIFICATION -> RoutineAction(type, textParam = notificationMessage.trim())
                                else -> RoutineAction(type)
                            }
                        }
                        val resolvedName = name.trim().ifBlank { triggerType.label }
                        val saved = AutomationRoutine(
                            id = initialRoutine?.id ?: "routine_${UUID.randomUUID()}",
                            name = resolvedName,
                            enabled = initialRoutine?.enabled ?: true,
                            triggerType = triggerType,
                            filterName = filterName.trim(),
                            actions = finalActions,
                            lastTriggeredMs = initialRoutine?.lastTriggeredMs
                        )
                        onSave(saved)
                    },
                    enabled = selectedTypes.isNotEmpty(),
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("save_routine_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Enregistrer")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
