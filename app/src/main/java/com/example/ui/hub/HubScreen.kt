package com.example.ui.hub

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.module.GoodLockModule
import com.example.core.module.ModuleCategory
import com.example.core.module.ModuleColorRole
import com.example.core.module.ModuleRegistry
import com.example.core.module.ModuleStatus
import com.example.modules.automate.AutomateModule
import com.example.modules.caffeine.CaffeineModule
import com.example.modules.clipboard.ClipboardModule
import com.example.modules.onehand.OneHandModule
import com.example.ui.MainUiState

@Composable
fun HubScreen(
    state: MainUiState,
    onSelectCategory: (ModuleCategory) -> Unit,
    onToggleModule: (String, Boolean) -> Unit,
    onOpenModule: (GoodLockModule) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    val activeCount = ModuleRegistry.modules.count { module ->
        val enabled = when (module.descriptor.id) {
            OneHandModule.MODULE_ID -> state.oneHandConfig.masterEnabled
            CaffeineModule.MODULE_ID -> state.caffeineConfig.isActive
            AutomateModule.MODULE_ID -> state.automateConfig.masterEnabled
            ClipboardModule.MODULE_ID -> state.clipboardConfig.enabled
            else -> false
        }
        module.resolveStatus(context, enabled) == ModuleStatus.ACTIVE
    }

    val handleModuleToggle: (String, Boolean) -> Unit = { moduleId, checked ->
        if (checked &&
            (moduleId == CaffeineModule.MODULE_ID || moduleId == AutomateModule.MODULE_ID) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !AutomateModule.hasNotificationPermission(context)
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        onToggleModule(moduleId, checked)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeroSuiteCard(
                activeCount = activeCount,
                totalCount = ModuleRegistry.modules.size,
                isAccessibilityEnabled = state.isAccessibilityEnabled
            )
        }

        item {
            CategoryFilterStrip(
                selectedCategory = state.selectedCategory,
                onSelectCategory = onSelectCategory
            )
        }

        item {
            SectionHeader(
                title = "Modules installés",
                subtitle = "${state.modules.size} module${if (state.modules.size > 1) "s" else ""} disponible${if (state.modules.size > 1) "s" else ""}"
            )
        }

        item {
            if (state.modules.isEmpty()) {
                EmptyCategoryCard(
                    category = state.selectedCategory,
                    onShowAll = { onSelectCategory(ModuleCategory.ALL) }
                )
            } else {
                AdaptiveModuleGrid(
                    modules = state.modules,
                    state = state,
                    onToggleModule = handleModuleToggle,
                    onOpenModule = onOpenModule
                )
            }
        }

        item {
            ModularArchitectureOverviewCard(
                registeredCount = ModuleRegistry.modules.size
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HeroSuiteCard(
    activeCount: Int,
    totalCount: Int,
    isAccessibilityEnabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(196.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_modular),
                contentDescription = "Bannière ModuYou",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x55121324),
                                Color(0xCC121324)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.18f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Layers,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Suite Material You",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = if (isAccessibilityEnabled) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
                        }
                    ) {
                        Text(
                            text = when {
                                activeCount > 1 -> stringResource(R.string.hub_active_modules_count_plural, activeCount)
                                activeCount == 1 -> stringResource(R.string.hub_active_modules_count, activeCount)
                                else -> "$totalCount modules prêts"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isAccessibilityEnabled) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.hub_hero_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterStrip(
    selectedCategory: ModuleCategory,
    onSelectCategory: (ModuleCategory) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModuleCategory.entries.forEach { category ->
            val selected = category == selectedCategory
            FilterChip(
                selected = selected,
                onClick = { onSelectCategory(category) },
                modifier = Modifier.testTag("category_chip_${category.name.lowercase()}"),
                label = {
                    Text(
                        text = category.label,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AdaptiveModuleGrid(
    modules: List<GoodLockModule>,
    state: MainUiState,
    onToggleModule: (String, Boolean) -> Unit,
    onOpenModule: (GoodLockModule) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 600.dp) 2 else 1
        val rows = modules.chunked(columns)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { module ->
                        val isEnabled = when (module.descriptor.id) {
                            OneHandModule.MODULE_ID -> state.oneHandConfig.masterEnabled
                            CaffeineModule.MODULE_ID -> state.caffeineConfig.isActive
                            AutomateModule.MODULE_ID -> state.automateConfig.masterEnabled
                            ClipboardModule.MODULE_ID -> state.clipboardConfig.enabled
                            else -> false
                        }
                        ModuleCard(
                            module = module,
                            isEnabled = isEnabled,
                            onToggle = { checked -> onToggleModule(module.descriptor.id, checked) },
                            onOpen = { onOpenModule(module) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowItems.size < columns) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModuleCard(
    module: GoodLockModule,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val descriptor = module.descriptor
    val status = module.resolveStatus(context, isEnabled)

    val containerColor = when (descriptor.colorRole) {
        ModuleColorRole.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
        ModuleColorRole.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer
        ModuleColorRole.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val onContainerColor = when (descriptor.colorRole) {
        ModuleColorRole.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
        ModuleColorRole.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
        ModuleColorRole.TERTIARY -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    ElevatedCard(
        modifier = modifier
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .testTag("module_card_${descriptor.id}")
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(containerColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = descriptor.icon,
                            contentDescription = descriptor.title,
                            tint = onContainerColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = descriptor.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = descriptor.version,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = descriptor.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("module_toggle_${descriptor.id}")
                )
            }

            Text(
                text = descriptor.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                descriptor.capabilities.forEach { capability ->
                    AssistChip(
                        onClick = onOpen,
                        label = {
                            Text(
                                text = capability,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        border = null
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModuleStatusPill(status = status)

                FilledTonalButton(
                    onClick = onOpen,
                    modifier = Modifier.testTag("module_open_${descriptor.id}"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Configurer")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleStatusPill(status: ModuleStatus) {
    val (bg, fg, icon) = when (status) {
        ModuleStatus.ACTIVE -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Rounded.CheckCircle
        )
        ModuleStatus.NEEDS_SETUP -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Rounded.WarningAmber
        )
        ModuleStatus.DISABLED -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Rounded.PauseCircle
        )
    }

    Surface(
        shape = CircleShape,
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EmptyCategoryCard(
    category: ModuleCategory,
    onShowAll: () -> Unit
) {
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
                imageVector = category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Aucun module dans « ${category.label} »",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Cet emplacement est prêt à accueillir vos prochains modules enregistrés dans ModuleRegistry.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = onShowAll,
                modifier = Modifier.testTag("empty_show_all_button")
            ) {
                Text("Afficher tous les modules")
            }
        }
    }
}

@Composable
private fun ModularArchitectureOverviewCard(registeredCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Column {
                    Text(
                        text = "Socle modulaire Good Lock",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$registeredCount module${if (registeredCount > 1) "s" else ""} enregistré${if (registeredCount > 1) "s" else ""} via GoodLockModule",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Chaque module implémente le contrat GoodLockModule (métadonnées, catégorie, vérifications système, route dédiée) et s'ajoute dans ModuleRegistry avec persistance DataStore et thème cosmique bleu/menthe partagé.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
