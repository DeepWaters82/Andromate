package com.example.modules.shortcuts.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.modules.shortcuts.SettingsShortcuts
import com.example.ui.MainUiState

/** Liste des pages des paramètres : « Ajouter » épingle un raccourci sur l’écran d’accueil. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutsScreen(state: MainUiState, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val canPin = remember { SettingsShortcuts.canPin(context) }
    val appIcons = remember {
        androidx.compose.runtime.mutableStateMapOf<String, Boolean>().apply {
            SettingsShortcuts.pages.forEach { put(it.id, SettingsShortcuts.isAppIconEnabled(context, it)) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Raccourcis", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Vers une page des paramètres",
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
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Interrupteur : la page apparaît dans le tiroir d’applis comme une application. " +
                                "Fais-la glisser sur l’écran d’accueil : sans petit logo, compatible icônes thématiques et packs d’icônes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "Bouton écran d’accueil : raccourci épinglé directement (le lanceur y ajoute le petit logo d’Andromate).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            if (!canPin) {
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            "Le lanceur de ce téléphone n’accepte pas les raccourcis épinglés.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            items(SettingsShortcuts.pages, key = { it.id }) { page ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text(page.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { SettingsShortcuts.open(context, page) }) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Ouvrir")
                        }
                        IconButton(
                            enabled = canPin,
                            onClick = {
                                if (!SettingsShortcuts.pin(context, page, state.themeSettings)) {
                                    Toast.makeText(context, "Raccourci refusé par le lanceur", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.AddToHomeScreen, contentDescription = "Épingler sur l’écran d’accueil")
                        }
                        androidx.compose.material3.Switch(
                            checked = appIcons[page.id] == true,
                            onCheckedChange = { on ->
                                SettingsShortcuts.setAppIconEnabled(context, page, on)
                                appIcons[page.id] = on
                                if (on) Toast.makeText(context, "« ${page.label} » ajouté au tiroir d’applis", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}
