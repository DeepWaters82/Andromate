package com.example.modules.deepbluetooth.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.core.preferences.ModuYouPreferences
import com.example.modules.automate.AutomateConfig
import com.example.modules.automate.AutomateModule
import com.example.modules.deepbluetooth.DeepBluetoothConfig
import com.example.modules.deepbluetooth.PopupPosition
import com.example.modules.onehand.OneHandAccessibilityService
import com.example.modules.onehand.OneHandModule
import com.example.ui.MainUiState
import kotlinx.coroutines.launch

/** Réglages de la popup Bluetooth : quand, quoi, où, combien de temps, pour quels appareils. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeepBluetoothScreen(
    state: MainUiState,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ModuYouPreferences.getInstance(context) }
    val config = state.deepBluetoothConfig
    val update: ((DeepBluetoothConfig) -> DeepBluetoothConfig) -> Unit = { transform ->
        scope.launch { prefs.updateDeepBluetoothConfig(transform) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("DeepBluetooth", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (config.enabled) "Popup active" else "Popup désactivée",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour au hub")
                    }
                },
                actions = {
                    Switch(
                        checked = config.enabled,
                        onCheckedChange = { checked -> update { it.copy(enabled = checked) } },
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!state.isAccessibilityEnabled) {
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "La popup est affichée par le service d’accessibilité d’Andromate.",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            FilledTonalButton(onClick = { OneHandModule.openAccessibilitySettings(context) }) {
                                Text("Activer l’accessibilité")
                            }
                        }
                    }
                }
            }

            item {
                SettingsCard("Quand") {
                    ToggleRow("À la connexion", config.onConnect) { v -> update { it.copy(onConnect = v) } }
                    ToggleRow("À la déconnexion", config.onDisconnect) { v -> update { it.copy(onDisconnect = v) } }
                }
            }

            item {
                SettingsCard("Dans la popup") {
                    ToggleRow("Icône de l’appareil", config.showDeviceIcon) { v -> update { it.copy(showDeviceIcon = v) } }
                    ToggleRow("État (connecté / déconnecté)", config.showStatus) { v -> update { it.copy(showStatus = v) } }
                    ToggleRow("Pourcentage", config.showPercent) { v -> update { it.copy(showPercent = v) } }
                    ToggleRow("Barre de batterie", config.showBatteryBar) { v -> update { it.copy(showBatteryBar = v) } }
                }
            }

            item {
                SettingsCard("Affichage") {
                    Text("Position", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PopupPosition.entries.forEach { pos ->
                            FilterChip(
                                selected = config.position == pos,
                                onClick = { update { it.copy(position = pos) } },
                                label = { Text(pos.label) }
                            )
                        }
                    }
                    Text("Durée", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, 8, 12).forEach { sec ->
                            FilterChip(
                                selected = config.durationSec == sec,
                                onClick = { update { it.copy(durationSec = sec) } },
                                label = { Text("$sec s") }
                            )
                        }
                    }
                    ToggleRow("Aussi dans les notifications", config.alsoNotification) { v ->
                        update { it.copy(alsoNotification = v) }
                    }
                    FilledTonalButton(
                        onClick = { OneHandAccessibilityService.previewBluetoothPopup() },
                        enabled = state.isAccessibilityEnabled
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Aperçu")
                    }
                }
            }

            item {
                val paired = remember { AutomateModule.queryAvailableConnections(context).pairedBluetoothDevices }
                val selected = AutomateConfig.filterNames(config.devices)
                SettingsCard("Appareils") {
                    Text(
                        text = if (selected.isEmpty()) "Tous les appareils (cocher pour limiter)" else "Uniquement : ${selected.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    (selected + paired).distinctBy { it.lowercase() }.forEach { name ->
                        val checked = selected.any { it.equals(name, ignoreCase = true) }
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                val next = if (checked) selected.filterNot { it.equals(name, true) } else selected + name
                                update { it.copy(devices = AutomateConfig.joinFilterNames(next)) }
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (paired.isEmpty()) {
                        Text(
                            "Aucun appareil appairé visible (autorisation « Appareils à proximité »).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
