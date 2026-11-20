package com.example.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.module.ModuleRegistry
import com.example.modules.automate.AutomateModule
import com.example.modules.onehand.OneHandModule
import com.example.ui.MainUiState
import com.example.ui.theme.AccentPalette
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemeSettings

@Composable
fun SettingsScreen(
    state: MainUiState,
    onUpdateTheme: ((ThemeSettings) -> ThemeSettings) -> Unit,
    onToggleAllNotifications: (Boolean) -> Unit,
    onToggleCaffeineStatusIcon: (Boolean) -> Unit,
    onToggleRoutinesNotifications: (Boolean) -> Unit,
    onResetOneHand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val theme = state.themeSettings
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Studio Andromate",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Personnalisez les tons bleu cosmique & vert menthe de Material You et vérifiez vos services système.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ColorLens,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Thème & Couleurs",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = "Couleur dynamique du système",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = if (supportsDynamicColor) {
                                    "Adapte automatiquement Andromate au fond d'écran (désactivé par défaut pour garder le thème cosmique)"
                                } else {
                                    "Disponible à partir d'Android 12 ; palette sélectionnée utilisée"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = theme.dynamicColor && supportsDynamicColor,
                            enabled = supportsDynamicColor,
                            onCheckedChange = { checked ->
                                onUpdateTheme { it.copy(dynamicColor = checked) }
                            },
                            modifier = Modifier.testTag("settings_dynamic_color_switch")
                        )
                    }

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = theme.themeMode == mode,
                                onClick = { onUpdateTheme { it.copy(themeMode = mode) } },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ThemeMode.entries.size
                                ),
                                modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                            ) {
                                Text(mode.label)
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (theme.dynamicColor && supportsDynamicColor) {
                                "Palette de secours (quand dynamique est désactivé)"
                            } else {
                                "Palette active"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AccentPalette.entries.forEach { palette ->
                                val selected = theme.fallbackPalette == palette
                                PaletteSwatchCard(
                                    palette = palette,
                                    selected = selected,
                                    onClick = {
                                        onUpdateTheme {
                                            it.copy(
                                                fallbackPalette = palette,
                                                dynamicColor = if (supportsDynamicColor) false else it.dynamicColor
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            val notifPrefs = state.notificationPreferences
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Notifications d'exécution",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = "Toutes les notifications d'exécution",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Désactive l'ensemble des notifications d'état (Andromate, Caféine et Routines)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = notifPrefs.allExecutionNotificationsEnabled,
                            onCheckedChange = onToggleAllNotifications,
                            modifier = Modifier.testTag("settings_all_notifications_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = "Icône Caféine (barre d'état)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Affiche l'icône tasse tant que Caféine maintient l'écran",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = notifPrefs.allExecutionNotificationsEnabled && notifPrefs.caffeineStatusBarIconEnabled,
                            enabled = notifPrefs.allExecutionNotificationsEnabled,
                            onCheckedChange = onToggleCaffeineStatusIcon,
                            modifier = Modifier.testTag("settings_caffeine_notif_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = "Notifications des Routines",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Surveillance en arrière-plan et alertes déclenchées par les routines",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = notifPrefs.allExecutionNotificationsEnabled && notifPrefs.routinesNotificationsEnabled,
                            enabled = notifPrefs.allExecutionNotificationsEnabled,
                            onCheckedChange = onToggleRoutinesNotifications,
                            modifier = Modifier.testTag("settings_routines_notif_switch")
                        )
                    }

                    FilledTonalButton(
                        onClick = { AutomateModule.openAppNotificationSettings(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_open_app_notifications")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsOff,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Paramètres système des notifications")
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccessibilityNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Diagnostics système",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    DiagnosticRow(
                        title = "Service d'accessibilité Une Main +",
                        status = if (state.isAccessibilityEnabled) "Connecté" else "Inactif",
                        isPositive = state.isAccessibilityEnabled
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    DiagnosticRow(
                        title = "Accès Ne pas déranger (Routines)",
                        status = if (state.hasDndPolicyAccess) "Autorisé" else "Requis",
                        isPositive = state.hasDndPolicyAccess
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    DiagnosticRow(
                        title = "Matériel lampe torche",
                        status = if (state.hasFlashlight) "Détecté" else "Absent",
                        isPositive = state.hasFlashlight,
                        icon = Icons.Rounded.FlashlightOn
                    )

                    FilledTonalButton(
                        onClick = { OneHandModule.openAccessibilitySettings(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_open_accessibility")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccessibilityNew,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Ouvrir les paramètres d'accessibilité")
                    }
                }
            }
        }

        item {
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Registre & maintenance",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Text(
                        text = "${ModuleRegistry.modules.size} module enregistré dans ModuleRegistry : ${
                            ModuleRegistry.modules.joinToString { it.descriptor.title }
                        }.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FilledTonalButton(
                        onClick = onResetOneHand,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_reset_one_hand")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Réinitialiser les réglages d'Une Main +")
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PaletteSwatchCard(
    palette: AccentPalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("palette_${palette.name.lowercase()}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(palette.previewColor),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Palette sélectionnée",
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = palette.label.substringBefore(' '),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DiagnosticRow(
    title: String,
    status: String,
    isPositive: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Surface(
            shape = CircleShape,
            color = if (isPositive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            }
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = if (isPositive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
