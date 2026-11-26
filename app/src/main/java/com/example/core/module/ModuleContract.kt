package com.example.core.module

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector

enum class ModuleCategory(
    val label: String,
    val icon: ImageVector
) {
    ALL("Tous", Icons.Rounded.AutoAwesome),
    GESTURES("Gestes", Icons.Rounded.SwipeRight),
    APPEARANCE("Apparence", Icons.Rounded.Palette),
    SYSTEM("Système", Icons.Rounded.Tune)
}

enum class ModuleStatus(val label: String) {
    ACTIVE("Actif"),
    NEEDS_SETUP("Configuration requise"),
    DISABLED("Désactivé")
}

enum class ModuleColorRole {
    PRIMARY,
    SECONDARY,
    TERTIARY
}

data class ModuleDescriptor(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val version: String,
    val category: ModuleCategory,
    val icon: ImageVector,
    val colorRole: ModuleColorRole,
    val route: String,
    val capabilities: List<String>
)

/**
 * Contrat de base pour tout module de la suite ModuYou (architecture façon Good Lock).
 * Pour ajouter un nouveau module, implémentez cette interface et enregistrez-le dans [ModuleRegistry].
 */
interface GoodLockModule {
    val descriptor: ModuleDescriptor
    fun isSystemReady(context: Context): Boolean
    fun resolveStatus(context: Context, isEnabledInSuite: Boolean): ModuleStatus {
        return when {
            !isEnabledInSuite -> ModuleStatus.DISABLED
            !isSystemReady(context) -> ModuleStatus.NEEDS_SETUP
            else -> ModuleStatus.ACTIVE
        }
    }
}
