package com.example.modules.compat.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.modules.compat.CheckStatus
import com.example.modules.compat.CompatCheck
import com.example.modules.compat.Compatibility
import com.example.modules.onehand.OneHandAccessibilityService
import com.example.ui.MainUiState

/**
 * Page « Compatibilité » : ce qui fonctionne sur ce téléphone, avec le réglage à ouvrir pour
 * corriger, des tests en direct (volet, Récents) et un rapport à copier ou partager.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompatibilityScreen(state: MainUiState, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val live by Compatibility.live.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val staticChecks = remember(refresh, state.isAccessibilityEnabled) { Compatibility.staticChecks(context) }
    val liveChecks = Compatibility.liveChecks(live)
    val all = staticChecks + liveChecks

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Compatibilité", style = MaterialTheme.typography.titleLarge)
                        val ok = all.count { it.status == CheckStatus.OK }
                        Text(
                            "$ok / ${all.size} fonctions prêtes",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            Compatibility.deviceSummary(context),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "Les tests en direct ouvrent un instant le volet de réglages puis l’écran Récents.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                enabled = state.isAccessibilityEnabled && !live.running,
                                onClick = { OneHandAccessibilityService.runCompatibilityProbe() }
                            ) {
                                if (live.running) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.size(8.dp))
                                Text(if (live.done) "Relancer les tests" else "Tests en direct")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { copyReport(context, Compatibility.report(context, all)) }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Copier")
                            }
                            OutlinedButton(onClick = { shareReport(context, Compatibility.report(context, all)) }) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Partager")
                            }
                        }
                    }
                }
            }
            items(all, key = { it.title }) { check -> CheckRow(check) }
        }
    }
}

@Composable
private fun CheckRow(check: CompatCheck) {
    val context = LocalContext.current
    val (icon, tint) = when (check.status) {
        CheckStatus.OK -> Icons.Rounded.CheckCircle to MaterialTheme.colorScheme.primary
        CheckStatus.WARN -> Icons.Rounded.Warning to MaterialTheme.colorScheme.tertiary
        CheckStatus.FAIL -> Icons.Rounded.Error to MaterialTheme.colorScheme.error
        CheckStatus.UNKNOWN -> Icons.Rounded.HelpOutline to MaterialTheme.colorScheme.outline
    }
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint)
            Column(Modifier.weight(1f)) {
                Text(check.title, style = MaterialTheme.typography.bodyLarge)
                Text(check.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (check.status != CheckStatus.OK && check.fix != null && check.fixLabel != null) {
                TextButton(onClick = { check.fix.invoke(context) }) { Text(check.fixLabel) }
            }
        }
    }
}

private fun copyReport(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Compatibilité Andromate", text))
    Toast.makeText(context, "Rapport copié", Toast.LENGTH_SHORT).show()
}

private fun shareReport(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Partager le rapport").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
