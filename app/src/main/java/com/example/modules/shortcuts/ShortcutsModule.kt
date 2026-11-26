package com.example.modules.shortcuts

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AppShortcut
import com.example.core.module.GoodLockModule
import com.example.core.module.ModuleCategory
import com.example.core.module.ModuleColorRole
import com.example.core.module.ModuleDescriptor

object ShortcutsModule : GoodLockModule {
    const val MODULE_ID = "settings_shortcuts"
    const val ROUTE = "module/settings_shortcuts"

    override val descriptor: ModuleDescriptor = ModuleDescriptor(
        id = MODULE_ID,
        title = "Raccourcis",
        subtitle = "Pages des paramètres",
        description = "Raccourcis d’écran d’accueil vers une page des paramètres.",
        version = "v1.0",
        category = ModuleCategory.SYSTEM,
        icon = Icons.Rounded.AppShortcut,
        colorRole = ModuleColorRole.PRIMARY,
        route = ROUTE,
        capabilities = listOf("PinnedShortcuts")
    )

    override fun isSystemReady(context: Context): Boolean = true
}
