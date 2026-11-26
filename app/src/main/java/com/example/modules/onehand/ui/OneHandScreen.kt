package com.example.modules.onehand.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Crop169
import androidx.compose.material.icons.rounded.CropPortrait
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SouthEast
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.modules.onehand.GestureAction
import com.example.modules.onehand.HandleColorStyle
import com.example.modules.onehand.HandleConfig
import com.example.modules.onehand.HandleSide
import com.example.modules.onehand.OneHandConfig
import com.example.modules.onehand.OneHandModule
import com.example.modules.onehand.SwipeDirection
import com.example.modules.onehand.SwipeLength
import com.example.ui.MainUiState
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material.icons.rounded.DragIndicator

private data class ActionPickerTarget(
    val side: HandleSide,
    val direction: SwipeDirection,
    val length: SwipeLength,
    val currentAction: GestureAction
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneHandScreen(
    state: MainUiState,
    onNavigateBack: () -> Unit,
    onUpdateConfig: ((OneHandConfig) -> OneHandConfig) -> Unit,
    onUpdateHandle: (HandleSide, (HandleConfig) -> HandleConfig) -> Unit,
    onResetDefaults: () -> Unit,
    onSandboxGestureTriggered: (HandleSide, GestureAction, Boolean) -> Unit
) {
    val context = LocalContext.current
    val config = state.oneHandConfig
    var selectedSide by remember { mutableStateOf(HandleSide.RIGHT) }
    var pickerTarget by remember { mutableStateOf<ActionPickerTarget?>(null) }

    val selectedHandle = config.handleFor(selectedSide)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Une Main +",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (config.masterEnabled) "Module activé" else "Module en pause",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("one_hand_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Retour au hub"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onResetDefaults,
                        modifier = Modifier.testTag("one_hand_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = stringResource(R.string.action_reset_defaults)
                        )
                    }
                    Switch(
                        checked = config.masterEnabled,
                        onCheckedChange = { checked ->
                            onUpdateConfig { it.copy(masterEnabled = checked) }
                        },
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("one_hand_master_switch")
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
                    AccessibilityStatusBanner(
                        isEnabled = state.isAccessibilityEnabled,
                        onOpenSettings = { OneHandModule.openAccessibilitySettings(context) }
                    )
                }

                item {
                    GestureSandboxCard(
                        config = config,
                        selectedSide = selectedSide,
                        lastFeedback = state.lastSandboxFeedback,
                        onSandboxGestureTriggered = onSandboxGestureTriggered
                    )
                }

                item {
                    HandleSideSelectorCard(
                        selectedSide = selectedSide,
                        leftEnabled = config.leftHandle.enabled,
                        rightEnabled = config.rightHandle.enabled,
                        onSelectSide = { selectedSide = it },
                        onToggleSelectedSide = { enabled ->
                            onUpdateHandle(selectedSide) { it.copy(enabled = enabled) }
                        }
                    )
                }

                item {
                    GestureMappingsSection(
                        side = selectedSide,
                        handleConfig = selectedHandle,
                        longSwipeEnabled = config.longSwipeEnabled,
                        syncHandles = config.syncHandles,
                        onToggleSync = { sync ->
                            onUpdateConfig { current ->
                                if (sync) {
                                    // La poignée affichée sert de modèle pour l’autre.
                                    current.copy(syncHandles = true).withSyncedHandles(selectedSide)
                                } else {
                                    current.copy(syncHandles = false)
                                }
                            }
                        },
                        onPickAction = { direction, length, current ->
                            pickerTarget = ActionPickerTarget(selectedSide, direction, length, current)
                        }
                    )
                }

                item {
                    HandleGeometryCard(
                        handleConfig = selectedHandle,
                        onUpdateHandle = { transform -> onUpdateHandle(selectedSide, transform) }
                    )
                }

                item {
                    ToolsMenuCard(
                        tools = config.tools,
                        onUpdateTools = { transform ->
                            onUpdateConfig { it.copy(tools = transform(it.tools)) }
                        }
                    )
                }

                item {
                    AdvancedBehaviorCard(
                        config = config,
                        onUpdateConfig = onUpdateConfig
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    pickerTarget?.let { target ->
        ActionPickerBottomSheet(
            target = target,
            onDismiss = { pickerTarget = null },
            onSelectAction = { chosenAction ->
                // La synchronisation gauche / droite est appliquée par le ViewModel.
                onUpdateHandle(target.side) { handle ->
                    handle.withAction(target.direction, target.length, chosenAction)
                }
                pickerTarget = null
            }
        )
    }
}

@Composable
private fun AccessibilityStatusBanner(
    isEnabled: Boolean,
    onOpenSettings: () -> Unit
) {
    val containerColor = if (isEnabled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = if (isEnabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isEnabled) Icons.Rounded.CheckCircle else Icons.Rounded.AccessibilityNew,
                    contentDescription = null,
                    tint = contentColor
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(
                        if (isEnabled) R.string.accessibility_banner_active_title
                        else R.string.accessibility_banner_inactive_title
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
                Text(
                    text = stringResource(
                        if (isEnabled) R.string.accessibility_banner_active_body
                        else R.string.accessibility_banner_inactive_body
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.9f)
                )
            }

            if (!isEnabled) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("enable_accessibility_button")
                ) {
                    Text(stringResource(R.string.accessibility_banner_inactive_cta))
                }
            } else {
                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("open_accessibility_settings_button")
                ) {
                    Text("Réglages", color = contentColor)
                }
            }
        }
    }
}

@Composable
private fun HandleSideSelectorCard(
    selectedSide: HandleSide,
    leftEnabled: Boolean,
    rightEnabled: Boolean,
    onSelectSide: (HandleSide) -> Unit,
    onToggleSelectedSide: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                HandleSide.entries.forEachIndexed { index, side ->
                    SegmentedButton(
                        selected = selectedSide == side,
                        onClick = { onSelectSide(side) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = HandleSide.entries.size
                        ),
                        modifier = Modifier.testTag("segment_side_${side.name.lowercase()}")
                    ) {
                        Text(side.label)
                    }
                }
            }

            val isCurrentSideEnabled = if (selectedSide == HandleSide.LEFT) leftEnabled else rightEnabled
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Activer la ${selectedSide.label.lowercase()}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Switch(
                    checked = isCurrentSideEnabled,
                    onCheckedChange = onToggleSelectedSide,
                    modifier = Modifier.testTag("toggle_selected_side")
                )
            }
        }
    }
}

@Composable
private fun GestureMappingsSection(
    side: HandleSide,
    handleConfig: HandleConfig,
    longSwipeEnabled: Boolean,
    syncHandles: Boolean,
    onToggleSync: (Boolean) -> Unit,
    onPickAction: (SwipeDirection, SwipeLength, GestureAction) -> Unit
) {
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
                Text(
                    text = "Actions des gestes (${side.shortLabel})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (syncHandles) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "G ↔ D",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onToggleSync(!syncHandles) }
                    .testTag("sync_handles_row"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Synchroniser gauche et droite",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (syncHandles) {
                                "Mêmes réglages des deux côtés"
                            } else {
                                "Réglages séparés"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = syncHandles,
                        onCheckedChange = onToggleSync,
                        modifier = Modifier.testTag("sync_handles_switch")
                    )
                }
            }

            SwipeDirection.entries.forEachIndexed { index, direction ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                DirectionMappingRow(
                    side = side,
                    direction = direction,
                    shortAction = handleConfig.actionFor(direction, SwipeLength.SHORT),
                    longAction = handleConfig.actionFor(direction, SwipeLength.LONG),
                    longSwipeEnabled = longSwipeEnabled,
                    onEditShort = {
                        onPickAction(
                            direction,
                            SwipeLength.SHORT,
                            handleConfig.actionFor(direction, SwipeLength.SHORT)
                        )
                    },
                    onEditLong = {
                        onPickAction(
                            direction,
                            SwipeLength.LONG,
                            handleConfig.actionFor(direction, SwipeLength.LONG)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun DirectionMappingRow(
    side: HandleSide,
    direction: SwipeDirection,
    shortAction: GestureAction,
    longAction: GestureAction,
    longSwipeEnabled: Boolean,
    onEditShort: () -> Unit,
    onEditLong: () -> Unit
) {
    val directionIcon = directionIconFor(side, direction)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = directionIcon,
                    contentDescription = direction.label,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = direction.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionSlotButton(
                title = "Court",
                action = shortAction,
                onClick = onEditShort,
                modifier = Modifier
                    .weight(1f)
                    .testTag("action_short_${direction.name.lowercase()}")
            )
            if (longSwipeEnabled) {
                ActionSlotButton(
                    title = "Long",
                    action = longAction,
                    onClick = onEditLong,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("action_long_${direction.name.lowercase()}")
                )
            }
        }
    }
}

@Composable
private fun ActionSlotButton(
    title: String,
    action: GestureAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = action.shortLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HandleGeometryCard(
    handleConfig: HandleConfig,
    onUpdateHandle: ((HandleConfig) -> HandleConfig) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Apparence",
                style = MaterialTheme.typography.titleMedium
            )

            val isFullHeight = handleConfig.heightPercent >= 0.99f
            LabeledSlider(
                label = if (isFullHeight) "Zone prioritaire" else "Position verticale",
                valueText = "${(handleConfig.verticalPositionPercent * 100).roundToInt()}%",
                value = handleConfig.verticalPositionPercent,
                range = 0.15f..0.85f,
                onValueChange = { v -> onUpdateHandle { it.copy(verticalPositionPercent = v) } }
            )

            LabeledSlider(
                label = "Hauteur de la zone",
                valueText = "${(handleConfig.heightPercent * 100).roundToInt()}%",
                value = handleConfig.heightPercent,
                range = 0.15f..1.00f,
                onValueChange = { v -> onUpdateHandle { it.copy(heightPercent = v) } }
            )

            // Retour par balayage latéral : Android ne laisse que 200 dp prioritaires par bord.
            val navContext = androidx.compose.ui.platform.LocalContext.current
            var sideBack by remember { mutableStateOf(OneHandModule.hasSideBackGesture(navContext)) }
            androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                sideBack = OneHandModule.hasSideBackGesture(navContext)
            }
            if (sideBack) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Avec le Retour par balayage latéral, Android ne laisse la priorité aux poignées que sur 200 dp : " +
                                "elles sont réduites à cette hauteur, centrées sur leur position. Le reste du bord garde le Retour système.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        androidx.compose.material3.FilledTonalButton(
                            onClick = { OneHandModule.openNavigationGestureSettings(navContext) }
                        ) {
                            Text(if (OneHandModule.isSamsung) "Choisir « Glissement depuis le bas »" else "Réglages de navigation")
                        }
                    }
                }
            }

            LabeledSlider(
                label = "Épaisseur de détection",
                valueText = "${handleConfig.widthDp.roundToInt()} dp",
                value = handleConfig.widthDp,
                range = 18f..48f,
                onValueChange = { v -> onUpdateHandle { it.copy(widthDp = v) } }
            )

            LabeledSlider(
                label = "Opacité de la poignée",
                valueText = "${(handleConfig.opacity * 100).roundToInt()}%",
                value = handleConfig.opacity,
                range = 0f..0.90f,
                onValueChange = { v -> onUpdateHandle { it.copy(opacity = v) } }
            )

            Text(
                text = "Couleur Material You",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HandleColorStyle.entries.forEach { style ->
                    FilterChip(
                        selected = handleConfig.colorStyle == style,
                        onClick = { onUpdateHandle { it.copy(colorStyle = style) } },
                        label = { Text(style.label) }
                    )
                }
            }
        }
    }
}

/**
 * Réglages du menu d’outils, comme l’édition du panneau de raccourcis : forme, taille,
 * colonnes, libellés, zone audio, puis la liste des outils à afficher et leur ordre
 * (appui long sur une ligne puis glisser).
 */
@Composable
private fun ToolsMenuCard(
    tools: com.example.modules.onehand.ToolsMenuConfig,
    onUpdateTools: ((com.example.modules.onehand.ToolsMenuConfig) -> com.example.modules.onehand.ToolsMenuConfig) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Menu d’outils", style = MaterialTheme.typography.titleMedium)
            ConnectionNamesPermissionCard()

            SwitchSettingRow(
                title = "Zone audio",
                subtitle = "Source, titre, précédent, lecture, suivant",
                checked = tools.showMediaZone,
                onCheckedChange = { checked -> onUpdateTools { it.copy(showMediaZone = checked) } }
            )
            if (tools.showMediaZone) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val iconStyles = remember {
                    buildList {
                        add(com.example.modules.onehand.SourceIconStyle.APP to "Appli")
                        if (com.example.modules.onehand.SourceIcons.themedSupported()) {
                            add(com.example.modules.onehand.SourceIconStyle.THEMED to "Material You")
                        }
                        com.example.modules.onehand.SourceIcons.installedPacks(context).forEach { add(it.packageName to it.label) }
                    }
                }
                ChoiceChipsRow(
                    title = "Icône de la source",
                    options = iconStyles.map { it.first },
                    selected = tools.sourceIcon.takeIf { style -> iconStyles.any { it.first == style } }
                        ?: com.example.modules.onehand.SourceIconStyle.APP,
                    label = { style -> iconStyles.first { it.first == style }.second },
                    onSelect = { style -> onUpdateTools { it.copy(sourceIcon = style) } }
                )
            }
            SwitchSettingRow(
                title = "Libellés",
                subtitle = "Nom sous chaque bouton",
                checked = tools.showLabels,
                onCheckedChange = { checked -> onUpdateTools { it.copy(showLabels = checked) } }
            )

            ChoiceChipsRow(
                title = "Forme",
                options = com.example.modules.onehand.ToolShape.entries,
                selected = tools.shape,
                label = { it.label },
                onSelect = { shape -> onUpdateTools { it.copy(shape = shape) } }
            )
            ChoiceChipsRow(
                title = "Taille",
                options = com.example.modules.onehand.ToolIconSize.entries,
                selected = tools.iconSize,
                label = { it.label },
                onSelect = { size -> onUpdateTools { it.copy(iconSize = size) } }
            )
            ChoiceChipsRow(
                title = "Colonnes",
                options = listOf(3, 4, 5),
                selected = tools.columns,
                label = { it.toString() },
                onSelect = { columns -> onUpdateTools { it.copy(columns = columns) } }
            )

            Text(text = "Boutons", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Appui long puis glisser pour changer l’ordre ; la case règle la taille. " +
                    "Dans le menu aussi : appui long sur un bouton pour le déplacer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ReorderableToolList(tools = tools, onUpdateTools = onUpdateTools)

            ToolAppsSection(tools = tools, onUpdateTools = onUpdateTools)
        }
    }
}

/** Applications du menu d’outils : ajout, mode par défaut (plein écran / fenêtre), retrait. */
@Composable
private fun ToolAppsSection(
    tools: com.example.modules.onehand.ToolsMenuConfig,
    onUpdateTools: ((com.example.modules.onehand.ToolsMenuConfig) -> com.example.modules.onehand.ToolsMenuConfig) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val popupSupported = remember { com.example.modules.onehand.AppLauncher.supportsPopup(context) }
    Text(text = "Applications", style = MaterialTheme.typography.bodyMedium)
    Text(
        text = if (popupSupported) {
            "Des boutons comme les autres : place et taille dans la liste ci-dessus ou dans le menu. " +
                "« Fenêtre » : ouverture en fenêtre réduite."
        } else {
            "Des boutons comme les autres. Fenêtre réduite : activer d’abord l’accessibilité d’Andromate."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    tools.apps.forEach { app ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppIconImage(app.packageName)
            Text(
                text = com.example.modules.onehand.AppLauncher.label(context, app.packageName),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            if (popupSupported) {
                androidx.compose.material3.FilterChip(
                    selected = app.popup,
                    onClick = {
                        onUpdateTools { cfg ->
                            cfg.copy(apps = cfg.apps.map { if (it.packageName == app.packageName) it.copy(popup = !it.popup) else it })
                        }
                    },
                    label = { Text("Fenêtre") }
                )
            }
            androidx.compose.material3.IconButton(
                onClick = { onUpdateTools { cfg -> cfg.copy(apps = cfg.apps.filterNot { it.packageName == app.packageName }) } }
            ) {
                Icon(androidx.compose.material.icons.Icons.Rounded.Close, contentDescription = "Retirer")
            }
        }
    }
    androidx.compose.material3.OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(androidx.compose.material.icons.Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text("Ajouter une application")
    }
    if (picking) {
        ToolAppPickerDialog(
            exclude = tools.apps.map { it.packageName }.toSet(),
            onDismiss = { picking = false },
            onPick = { pkg ->
                picking = false
                onUpdateTools { cfg -> cfg.copy(apps = cfg.apps + com.example.modules.onehand.AppShortcut(pkg)) }
            }
        )
    }
}

@Composable
private fun AppIconImage(packageName: String, sizeDp: Int = 36) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        androidx.compose.foundation.Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(sizeDp.dp))
    } else {
        Spacer(Modifier.size(sizeDp.dp))
    }
}

@Composable
private fun ToolAppPickerDialog(exclude: Set<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var query by remember { mutableStateOf("") }
    val apps = remember { com.example.modules.automate.AutomateController.queryLaunchableApps(context) }
    val filtered = apps.filter { it.packageName !in exclude && (query.isBlank() || it.label.contains(query, ignoreCase = true)) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter une application") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Rechercher") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(filtered.size) { index ->
                        val app = filtered[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(app.packageName) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppIconImage(app.packageName)
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

/** Autorisations pour afficher l’appareil Bluetooth et le réseau Wi-Fi dans les pilules. */
@Composable
private fun ConnectionNamesPermissionCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var btOk by remember { mutableStateOf(com.example.modules.onehand.ConnectionNames.hasBluetoothPermission(context)) }
    var locOk by remember { mutableStateOf(com.example.modules.onehand.ConnectionNames.hasLocationPermission(context)) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) {
        btOk = com.example.modules.onehand.ConnectionNames.hasBluetoothPermission(context)
        locOk = com.example.modules.onehand.ConnectionNames.hasLocationPermission(context)
    }
    if (btOk && locOk) return
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Noms dans les pilules Wi-Fi et Bluetooth",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = when {
                    !btOk -> "« Appareils à proximité » montre l’appareil Bluetooth connecté."
                    else -> "La position donne le nom du Wi-Fi directement (sinon il est lu sur la tuile du volet)."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            androidx.compose.material3.FilledTonalButton(
                onClick = {
                    launcher.launch(
                        buildList {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                add(android.Manifest.permission.BLUETOOTH_CONNECT)
                            }
                            add(android.Manifest.permission.ACCESS_FINE_LOCATION)
                            add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        }.toTypedArray()
                    )
                }
            ) { Text("Autoriser") }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChipsRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                androidx.compose.material3.FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) }
                )
            }
        }
    }
}

/** Liste des outils : interrupteur pour l’afficher, appui long + glisser pour le déplacer. */
@Composable
private fun ReorderableToolList(
    tools: com.example.modules.onehand.ToolsMenuConfig,
    onUpdateTools: ((com.example.modules.onehand.ToolsMenuConfig) -> com.example.modules.onehand.ToolsMenuConfig) -> Unit
) {
    val ordered = tools.orderedItems()
    var list by remember(ordered) { mutableStateOf(ordered) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val rowHeight = 56.dp
    val rowHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { rowHeight.toPx() }

    Column {
        list.forEachIndexed { index, item ->
            val dragging = index == draggingIndex
            val action = (item as? com.example.modules.onehand.ToolItem.Action)?.action
            val shown = action == null || action !in tools.hiddenTools
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (dragging) dragOffset else 0f
                        val scale = if (dragging) 1.03f else 1f
                        scaleX = scale
                        scaleY = scale
                        shadowElevation = if (dragging) 12f else 0f
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (dragging) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                    .pointerInput(item.key) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                draggingIndex = list.indexOfFirst { it.key == item.key }
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val from = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                val target = (from + (dragOffset / rowHeightPx).roundToInt()).coerceIn(0, list.lastIndex)
                                if (target != from) {
                                    list = list.toMutableList().apply { add(target, removeAt(from)) }
                                    dragOffset -= (target - from) * rowHeightPx
                                    draggingIndex = target
                                }
                            },
                            onDragEnd = {
                                draggingIndex = null
                                dragOffset = 0f
                                val newOrder = list.map { it.key }
                                onUpdateTools { it.copy(order = newOrder) }
                            },
                            onDragCancel = {
                                draggingIndex = null
                                dragOffset = 0f
                            }
                        )
                    }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Rounded.DragIndicator,
                    contentDescription = "Déplacer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (action != null) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        tint = if (shown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                } else {
                    Box(modifier = Modifier.size(24.dp)) { AppIconImage((item as com.example.modules.onehand.ToolItem.App).app.packageName, 24) }
                }
                val context = androidx.compose.ui.platform.LocalContext.current
                Text(
                    text = action?.label
                        ?: com.example.modules.onehand.AppLauncher.label(context, (item as com.example.modules.onehand.ToolItem.App).app.packageName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (shown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f)
                )
                val span = tools.spanOf(item.key)
                androidx.compose.material3.IconButton(
                    onClick = {
                        onUpdateTools { it.copy(spans = it.spans + (item.key to it.spanOf(item.key).next())) }
                    }
                ) {
                    Icon(
                        imageVector = when (span) {
                            com.example.modules.onehand.ToolSpan.SINGLE -> androidx.compose.material.icons.Icons.Rounded.CropSquare
                            com.example.modules.onehand.ToolSpan.WIDE -> androidx.compose.material.icons.Icons.Rounded.Crop169
                            com.example.modules.onehand.ToolSpan.TALL -> androidx.compose.material.icons.Icons.Rounded.CropPortrait
                        },
                        contentDescription = span.label,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (action != null) {
                    Switch(
                        checked = shown,
                        onCheckedChange = { show ->
                            onUpdateTools {
                                it.copy(hiddenTools = if (show) it.hiddenTools - action else it.hiddenTools + action)
                            }
                        }
                    )
                } else {
                    val app = (item as com.example.modules.onehand.ToolItem.App).app
                    androidx.compose.material3.IconButton(
                        onClick = { onUpdateTools { cfg -> cfg.copy(apps = cfg.apps.filterNot { it.packageName == app.packageName }) } }
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.Close, contentDescription = "Retirer")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedBehaviorCard(
    config: OneHandConfig,
    onUpdateConfig: ((OneHandConfig) -> OneHandConfig) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Comportement",
                style = MaterialTheme.typography.titleMedium
            )

            SwitchSettingRow(
                title = "Glissement long",
                subtitle = "Tirez plus loin pour une 2e action",
                checked = config.longSwipeEnabled,
                onCheckedChange = { checked ->
                    onUpdateConfig { it.copy(longSwipeEnabled = checked) }
                }
            )

            LabeledSlider(
                label = "Distance du glissement court",
                valueText = "${config.triggerDistanceDp.roundToInt()} dp",
                value = config.triggerDistanceDp,
                range = 34f..90f,
                onValueChange = { v ->
                    onUpdateConfig { current ->
                        val minLong = (v + 36f).coerceAtLeast(current.longTriggerDistanceDp)
                        current.copy(triggerDistanceDp = v, longTriggerDistanceDp = minLong)
                    }
                }
            )

            AnimatedVisibility(visible = config.longSwipeEnabled) {
                LabeledSlider(
                    label = "Distance du glissement long",
                    valueText = "${config.longTriggerDistanceDp.roundToInt()} dp",
                    value = config.longTriggerDistanceDp,
                    range = (config.triggerDistanceDp + 36f)..180f,
                    onValueChange = { v ->
                        onUpdateConfig { it.copy(longTriggerDistanceDp = v) }
                    }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SwitchSettingRow(
                title = "Icône de l’action",
                subtitle = "Dans la flèche une fois le geste validé",
                checked = config.showGestureIcon,
                onCheckedChange = { checked ->
                    onUpdateConfig { it.copy(showGestureIcon = checked) }
                }
            )

            SwitchSettingRow(
                title = "Afficher le nom de l'action",
                subtitle = "Bulle pendant le geste",
                checked = config.showGestureLabel,
                onCheckedChange = { checked ->
                    onUpdateConfig { it.copy(showGestureLabel = checked) }
                }
            )

            SwitchSettingRow(
                title = "Retour haptique",
                subtitle = "Au seuil de déclenchement",
                checked = config.hapticFeedbackEnabled,
                onCheckedChange = { checked ->
                    onUpdateConfig { it.copy(hapticFeedbackEnabled = checked) }
                }
            )

            SwitchSettingRow(
                title = "Désactiver en mode paysage",
                subtitle = "Poignées masquées en paysage",
                checked = config.disableInLandscape,
                onCheckedChange = { checked ->
                    onUpdateConfig { it.copy(disableInLandscape = checked) }
                }
            )
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionPickerBottomSheet(
    target: ActionPickerTarget,
    onDismiss: () -> Unit,
    onSelectAction: (GestureAction) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "${target.direction.label} • ${target.length.label}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Grille d’icônes (comme le menu d’outils) : 21 actions sans texte superflu.
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.height(420.dp)
            ) {
                items(GestureAction.entries.size) { index ->
                    val action = GestureAction.entries[index]
                    val supported = action.isSupportedOnDevice()
                    val selected = action == target.currentAction
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .clickable(enabled = supported) { onSelectAction(action) }
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                            .testTag("pick_action_${action.name.lowercase()}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.label,
                            tint = if (supported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = action.shortLabel,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = if (supported) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

private fun directionIconFor(side: HandleSide, direction: SwipeDirection): ImageVector {
    return when (side) {
        HandleSide.LEFT -> when (direction) {
            SwipeDirection.HORIZONTAL -> Icons.AutoMirrored.Rounded.ArrowForward
            SwipeDirection.DIAGONAL_UP -> Icons.Rounded.NorthEast
            SwipeDirection.DIAGONAL_DOWN -> Icons.Rounded.SouthEast
        }
        HandleSide.RIGHT -> when (direction) {
            SwipeDirection.HORIZONTAL -> Icons.AutoMirrored.Rounded.ArrowBack
            SwipeDirection.DIAGONAL_UP -> Icons.Rounded.NorthWest
            SwipeDirection.DIAGONAL_DOWN -> Icons.Rounded.SouthWest
        }
    }
}
