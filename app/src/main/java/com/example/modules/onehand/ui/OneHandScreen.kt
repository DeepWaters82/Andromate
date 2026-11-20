package com.example.modules.onehand.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
                        preventGestureDuplication = config.preventGestureDuplication,
                        onTogglePreventDuplication = { prevent ->
                            onUpdateConfig { current ->
                                if (prevent) {
                                    current.copy(preventGestureDuplication = true)
                                } else {
                                    current.copy(preventGestureDuplication = false)
                                        .withDuplicatedGesturesFrom(selectedSide)
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
                if (!config.preventGestureDuplication) {
                    onUpdateConfig { current ->
                        current.copy(
                            leftHandle = current.leftHandle.withAction(target.direction, target.length, chosenAction),
                            rightHandle = current.rightHandle.withAction(target.direction, target.length, chosenAction)
                        )
                    }
                } else {
                    onUpdateHandle(target.side) { handle ->
                        handle.withAction(target.direction, target.length, chosenAction)
                    }
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
                    Text(
                        text = "Affiche une zone sensible le long du bord ${selectedSide.shortLabel.lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
    preventGestureDuplication: Boolean,
    onTogglePreventDuplication: (Boolean) -> Unit,
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
                if (!preventGestureDuplication) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "Synchronisé G ↔ D",
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
                    .clickable { onTogglePreventDuplication(!preventGestureDuplication) }
                    .testTag("prevent_gesture_duplication_row"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Checkbox(
                        checked = preventGestureDuplication,
                        onCheckedChange = onTogglePreventDuplication,
                        modifier = Modifier.testTag("prevent_gesture_duplication_checkbox")
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Empêcher la duplication des gestes",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (preventGestureDuplication) {
                                "Cochée : gauche et droite sont configurées indépendamment"
                            } else {
                                "Décochée : tout geste modifié à droite s'applique à gauche et vice versa"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                text = "Apparence & zone tactile",
                style = MaterialTheme.typography.titleMedium
            )

            val isFullHeight = handleConfig.heightPercent >= 0.99f
            LabeledSlider(
                label = if (isFullHeight) "Zone prioritaire anti-retour" else "Position verticale",
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

            if (handleConfig.heightPercent > 0.32f) {
                Text(
                    text = "Android limite l'exclusion du geste Retour système à 200 dp par bord : la zone prioritaire reste centrée sur le réglage vertical ci-dessus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                text = "Sensibilité & comportement",
                style = MaterialTheme.typography.titleMedium
            )

            SwitchSettingRow(
                title = "Glissement long (2e action)",
                subtitle = "Maintenez et tirez plus loin vers le centre pour une action secondaire",
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
                title = "Afficher le nom de l'action",
                subtitle = "Affiche une petite bulle texte à côté de la flèche lors du glissement",
                checked = config.showGestureLabel,
                onCheckedChange = { checked ->
                    onUpdateConfig { it.copy(showGestureLabel = checked) }
                }
            )

            SwitchSettingRow(
                title = "Retour haptique",
                subtitle = "Vibration légère au franchissement du seuil de déclenchement",
                checked = config.hapticFeedbackEnabled,
                onCheckedChange = { checked ->
                    onUpdateConfig { it.copy(hapticFeedbackEnabled = checked) }
                }
            )

            SwitchSettingRow(
                title = "Désactiver en mode paysage",
                subtitle = "Masque temporairement les poignées pendant les vidéos ou jeux",
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

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.height(380.dp)
            ) {
                items(GestureAction.entries) { action ->
                    val supported = action.isSupportedOnDevice()
                    val selected = action == target.currentAction
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = supported) { onSelectAction(action) }
                            .testTag("pick_action_${action.name.lowercase()}"),
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = null,
                                tint = if (supported) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = action.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (supported) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    }
                                )
                                Text(
                                    text = action.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Sélectionné",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
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
