package com.example.modules.caffeine.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.modules.automate.AutomateModule
import com.example.modules.caffeine.CaffeineConfig
import com.example.modules.caffeine.CaffeineController
import com.example.modules.caffeine.CaffeineTimeoutMode
import com.example.ui.MainUiState
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CaffeineScreen(
    state: MainUiState,
    onNavigateBack: () -> Unit,
    onToggleCaffeine: (Boolean) -> Unit,
    onUpdateCaffeineConfig: ((CaffeineConfig) -> CaffeineConfig) -> Unit
) {
    val context = LocalContext.current
    val config = state.caffeineConfig
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var hasNotifPermission by remember {
        mutableStateOf(AutomateModule.hasNotificationPermission(context))
    }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(config.isActive, config.activeSinceEpochMs) {
        while (config.isActive) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val heroContainer by animateColorAsState(
        targetValue = if (config.isActive) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "caffeineHeroBg"
    )
    val heroContentColor = if (config.isActive) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Caféine",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (config.isActive) "Écran maintenu allumé" else "Veille normale du système",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("caffeine_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Retour au hub"
                        )
                    }
                },
                actions = {
                    Switch(
                        checked = config.isActive,
                        onCheckedChange = { checked ->
                            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            onToggleCaffeine(checked)
                        },
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("caffeine_top_switch")
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = heroContainer,
                        tonalElevation = if (config.isActive) 4.dp else 1.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (config.isActive) {
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (config.isActive) {
                                    SteamingCupIcon(
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(42.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Bedtime,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }

                            Text(
                                text = if (config.isActive) "Caféine est active" else "Écran au repos",
                                style = MaterialTheme.typography.headlineSmall,
                                color = heroContentColor
                            )

                            val elapsedText = if (config.isActive && config.activeSinceEpochMs != null) {
                                val elapsedSec = ((nowMs - config.activeSinceEpochMs).coerceAtLeast(0L) / 1000L)
                                val mins = elapsedSec / 60
                                val secs = elapsedSec % 60
                                "Allumé depuis %02d:%02d • ${config.timeoutMode.shortLabel}".format(mins, secs)
                            } else {
                                "Veille normale."
                            }

                            Text(
                                text = elapsedText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = heroContentColor.copy(alpha = 0.85f)
                            )

                            Button(
                                onClick = {
                                    val next = !config.isActive
                                    if (next && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    onToggleCaffeine(next)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("caffeine_main_toggle_button"),
                                contentPadding = PaddingValues(vertical = 14.dp),
                                colors = if (config.isActive) {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                        contentColor = MaterialTheme.colorScheme.onTertiary
                                    )
                                } else {
                                    ButtonDefaults.buttonColors()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Coffee,
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (config.isActive) "Désactiver Caféine" else "Maintenir l'écran allumé",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Icône et contrôle",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "Requis pour l’icône de la barre d’état.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
                                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                    modifier = Modifier.testTag("caffeine_request_notif_button")
                                ) {
                                    Text("Autoriser")
                                }
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
                                    imageVector = Icons.Rounded.AddToHomeScreen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Tuile des réglages rapides",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            Text(
                                text = "Ajoutez la tuile « Caféine » au panneau de paramètres rapides pour l'activer ou la désactiver d'un seul geste depuis n'importe quelle application.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            FilledTonalButton(
                                onClick = {
                                    CaffeineController.requestAddQuickSettingsTile(context) { msg ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(msg)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("caffeine_add_qs_tile_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AddToHomeScreen,
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ajouter la tuile")
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
                            Text(
                                text = "Comportement",
                                style = MaterialTheme.typography.titleMedium
                            )

                            CaffeineToggleRow(
                                icon = Icons.Rounded.PowerSettingsNew,
                                title = "Arrêter avec le bouton Power",
                                subtitle = "Arrêt au verrouillage",
                                checked = config.disableOnScreenOff,
                                onCheckedChange = { checked ->
                                    onUpdateCaffeineConfig { it.copy(disableOnScreenOff = checked) }
                                },
                                testTag = "caffeine_power_off_switch"
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            CaffeineToggleRow(
                                icon = Icons.Rounded.WbSunny,
                                title = "Luminosité active",
                                subtitle = "Sinon l’écran est tamisé",
                                checked = config.keepBright,
                                onCheckedChange = { checked ->
                                    onUpdateCaffeineConfig { it.copy(keepBright = checked) }
                                },
                                testTag = "caffeine_bright_switch"
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            CaffeineToggleRow(
                                icon = Icons.Rounded.NotificationsActive,
                                title = "Icône dans la barre d'état",
                                subtitle = if (!state.notificationPreferences.allExecutionNotificationsEnabled) {
                                    "Notifications coupées dans Studio"
                                } else {
                                    "Tasse visible pendant le maintien"
                                },
                                checked = config.showStatusBarIcon && state.notificationPreferences.allExecutionNotificationsEnabled,
                                onCheckedChange = { checked ->
                                    onUpdateCaffeineConfig { it.copy(showStatusBarIcon = checked) }
                                },
                                testTag = "caffeine_status_icon_switch"
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Durée maximale",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CaffeineTimeoutMode.entries.forEach { mode ->
                                        FilterChip(
                                            selected = config.timeoutMode == mode,
                                            onClick = {
                                                onUpdateCaffeineConfig { it.copy(timeoutMode = mode) }
                                            },
                                            label = { Text(mode.shortLabel) },
                                            modifier = Modifier.testTag("caffeine_timeout_${mode.name.lowercase()}")
                                        )
                                    }
                                }
                                Text(
                                    text = config.timeoutMode.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CaffeineToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

/** Tasse dont la vapeur monte, comme l’icône de la barre d’état quand Caféine est active. */
@Composable
fun SteamingCupIcon(
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val frames = remember {
        listOf(
            com.example.R.drawable.ic_caffeine_frame1,
            com.example.R.drawable.ic_caffeine_frame2,
            com.example.R.drawable.ic_caffeine_frame3
        )
    }
    var frame by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // Quelques secondes de vapeur à l’activation, puis la tasse se fige (comme la barre d’état).
    LaunchedEffect(Unit) {
        repeat(9) {
            delay(450L)
            frame = (frame + 1) % frames.size
        }
        frame = 0
    }
    Icon(
        painter = androidx.compose.ui.res.painterResource(frames[frame]),
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}
