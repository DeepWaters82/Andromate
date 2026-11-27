package com.example.modules.essentialkey.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.preferences.ModuYouPreferences
import com.example.modules.automate.AutomateController
import com.example.modules.essentialkey.EssentialKeyConfig
import com.example.modules.essentialkey.EssentialKeyModule
import com.example.modules.essentialkey.KeyPress
import com.example.modules.onehand.OneHandModule
import com.example.ui.MainUiState
import kotlinx.coroutines.launch

/** Reprogrammation de la touche Essential : action par type d’appui, état d’Essential Space. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EssentialKeyScreen(state: MainUiState, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ModuYouPreferences.getInstance(context) }
    val config = state.essentialKeyConfig
    val update: ((EssentialKeyConfig) -> EssentialKeyConfig) -> Unit = { transform ->
        scope.launch { prefs.updateEssentialKeyConfig(transform) }
    }

    // Essential Space peut être désactivé depuis le PC pendant que la page est ouverte.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hasKey = remember(refresh) { EssentialKeyModule.hasKey(context) }
    val essentialSpaceActive = remember(refresh) { EssentialKeyModule.isEssentialSpaceActive(context) }
    var picking by remember { mutableStateOf<KeyPress?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Touche Essential", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (config.enabled) "Reprogrammée" else "Désactivée",
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!hasKey) {
                item {
                    InfoCard(
                        icon = Icons.Rounded.Warning,
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                        text = "Ce téléphone n’a pas de touche Essential (Nothing Phone (3a) et suivants)."
                    )
                }
            }
            if (!state.isAccessibilityEnabled) {
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "La touche est lue par le service d’accessibilité d’Andromate.",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            FilledTonalButton(onClick = { OneHandModule.openAccessibilitySettings(context) }) {
                                Text("Activer l’accessibilité")
                            }
                        }
                    }
                }
            }

            if (hasKey) item { EssentialSpaceCard(active = essentialSpaceActive) }

            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(
                            "Actions",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                        KeyPress.entries.forEach { press ->
                            PressRow(
                                press = press,
                                action = config.actionFor(press),
                                enabled = config.enabled,
                                onClick = { picking = press }
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "La touche répond écran allumé. Un double appui retarde l’appui court de 0,35 s ; " +
                        "sans action de double appui, l’appui court est immédiat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }

    picking?.let { press ->
        ActionPickerDialog(
            title = press.label,
            current = config.actionFor(press),
            onDismiss = { picking = null },
            onPick = { action ->
                update { it.with(press, action) }
                picking = null
            }
        )
    }
}

@Composable
private fun InfoCard(icon: ImageVector, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color, text: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = container) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = content)
            Text(text, color = content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** État d’Essential Space, avec la commande adb pour libérer ou rendre la touche. */
@Composable
private fun EssentialSpaceCard(active: Boolean) {
    val context = LocalContext.current
    val container = if (active) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (active) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val command = if (active) EssentialKeyModule.DISABLE_COMMAND else EssentialKeyModule.ENABLE_COMMAND
    Surface(shape = RoundedCornerShape(20.dp), color = container) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (active) Icons.Rounded.Warning else Icons.Rounded.CheckCircle, contentDescription = null, tint = content)
                Text(
                    if (active) "Essential Space répond aussi à la touche" else "Touche libre : seul Andromate y répond",
                    style = MaterialTheme.typography.titleSmall,
                    color = content
                )
            }
            Text(
                if (active) {
                    "Android ne laisse pas une appli désactiver Essential Space. Lancez une fois cette commande " +
                        "depuis un PC (débogage USB activé) pour qu’Andromate soit seul sur la touche :"
                } else {
                    "Essential Space est désactivé. Pour le retrouver, lancez depuis un PC :"
                },
                style = MaterialTheme.typography.bodySmall,
                color = content
            )
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        command,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f).padding(vertical = 10.dp)
                    )
                    IconButton(onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("Commande adb", command))
                        Toast.makeText(context, "Commande copiée", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copier la commande")
                    }
                }
            }
        }
    }
}

@Composable
private fun PressRow(press: KeyPress, action: String, enabled: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val tool = EssentialKeyModule.toolFor(action)
    val pkg = EssentialKeyModule.appPackage(action)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when {
            pkg != null -> AppIcon(pkg, 28)
            else -> Icon(
                tool?.icon ?: Icons.Rounded.Block,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(28.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(press.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                EssentialKeyModule.label(context, action),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppIcon(packageName: String, sizeDp: Int) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(sizeDp.dp))
    else Spacer(Modifier.size(sizeDp.dp))
}

/** Choix d’un outil, d’une application, ou d’aucune action. */
@Composable
private fun ActionPickerDialog(title: String, current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    var showApps by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val apps = remember(showApps) { if (showApps) AutomateController.queryLaunchableApps(context) else emptyList() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (showApps) "Ouvrir une application" else title) },
        text = {
            if (showApps) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Rechercher") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val filtered = apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                    LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                        items(filtered.size) { index ->
                            val app = filtered[index]
                            ChoiceRow(
                                selected = current == "app:${app.packageName}",
                                label = app.label,
                                leading = { AppIcon(app.packageName, 32) },
                                onClick = { onPick("app:${app.packageName}") }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                    item {
                        ChoiceRow(current.isBlank(), "Aucune action", { ChoiceIcon(Icons.Rounded.Block) }) { onPick("") }
                    }
                    item {
                        ChoiceRow(
                            selected = current.startsWith("app:"),
                            label = if (current.startsWith("app:")) EssentialKeyModule.label(context, current) else "Ouvrir une application…",
                            leading = { ChoiceIcon(Icons.Rounded.Apps) },
                            onClick = { showApps = true }
                        )
                    }
                    items(EssentialKeyModule.actionChoices.size) { index ->
                        val tool = EssentialKeyModule.actionChoices[index]
                        ChoiceRow(current == tool.name, tool.label, { ChoiceIcon(tool.icon) }) { onPick(tool.name) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { if (showApps) showApps = false else onDismiss() }) {
                Text(if (showApps) "Retour" else "Annuler")
            }
        }
    )
}

@Composable
private fun ChoiceIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
}

@Composable
private fun ChoiceRow(selected: Boolean, label: String, leading: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        leading()
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}
