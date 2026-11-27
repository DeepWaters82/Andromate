package com.example.core.module

import com.example.modules.automate.AutomateModule
import com.example.modules.caffeine.CaffeineModule
import com.example.modules.clipboard.ClipboardModule
import com.example.modules.onehand.OneHandModule

/**
 * Registre central des modules embarqués dans Andromate.
 * Chaque module enregistré ici apparaît automatiquement dans le Hub principal,
 * les filtres par catégorie et l'inspecteur d'architecture modulaire.
 */
object ModuleRegistry {
    val modules: List<GoodLockModule> = listOf(
        OneHandModule,
        CaffeineModule,
        AutomateModule,
        ClipboardModule,
        com.example.modules.deepbluetooth.DeepBluetoothModule,
        com.example.modules.shortcuts.ShortcutsModule,
        com.example.modules.essentialkey.EssentialKeyModule,
        com.example.modules.compat.CompatibilityModule
    )

    fun findById(id: String): GoodLockModule? = modules.find { it.descriptor.id == id }

    fun filterByCategory(category: ModuleCategory): List<GoodLockModule> {
        return if (category == ModuleCategory.ALL) {
            modules
        } else {
            modules.filter { it.descriptor.category == category }
        }
    }
}
