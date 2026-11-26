package com.example.modules.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import com.example.core.module.GoodLockModule
import com.example.core.module.ModuleCategory
import com.example.core.module.ModuleColorRole
import com.example.core.module.ModuleDescriptor
import com.example.core.preferences.ModuYouPreferences
import java.util.UUID

object ClipboardModule : GoodLockModule {
    const val MODULE_ID = "clipboard"
    const val ROUTE = "module/clipboard"

    override val descriptor: ModuleDescriptor = ModuleDescriptor(
        id = MODULE_ID,
        title = "Presse-papier",
        subtitle = "Historique des copies",
        description = "Historique des textes copiés.",
        version = "v1.0",
        category = ModuleCategory.SYSTEM,
        icon = Icons.Rounded.ContentPaste,
        colorRole = ModuleColorRole.PRIMARY,
        route = ROUTE,
        capabilities = listOf(
            "ClipboardManager",
            "QuickCopy",
            "OneHandAction"
        )
    )

    override fun isSystemReady(context: Context): Boolean = true
}

object ClipboardController {
    /** ClipDescription.EXTRA_IS_SENSITIVE (API 33), aussi utilisé par les gestionnaires de mots de passe avant. */
    private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    fun readCurrentSystemClip(context: Context): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        if (!cm.hasPrimaryClip()) return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        // Mots de passe et codes copiés depuis un gestionnaire : jamais mémorisés.
        if (clip.description?.extras?.getBoolean(EXTRA_IS_SENSITIVE, false) == true) return null
        return runCatching {
            clip.getItemAt(0).coerceToText(context)?.toString()?.trim()
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    suspend fun captureCurrentClipIfPresent(context: Context): String? {
        val text = readCurrentSystemClip(context) ?: return null
        val prefs = ModuYouPreferences.getInstance(context)
        prefs.addClipboardEntry(
            ClipboardEntry(
                id = "clip_${UUID.randomUUID()}",
                text = text,
                timestampMs = System.currentTimeMillis(),
                pinned = false
            )
        )
        return text
    }

    fun copyToSystemClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("Andromate", text))
    }
}
