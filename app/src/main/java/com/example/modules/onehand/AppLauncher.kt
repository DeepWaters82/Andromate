package com.example.modules.onehand

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.provider.Settings

/** Application ajoutée au menu d’outils ; [popup] : ouverture par défaut en fenêtre réduite. */
data class AppShortcut(val packageName: String, val popup: Boolean = false)

/**
 * Lancement des applications depuis le menu d’outils : plein écran, ou fenêtre réduite
 * (fenêtre flottante redimensionnable) quand le téléphone le permet.
 */
object AppLauncher {

    private const val WINDOWING_MODE_FREEFORM = 5

    fun encode(apps: List<AppShortcut>): String =
        apps.joinToString(";") { "${it.packageName}|${if (it.popup) 1 else 0}" }

    fun decode(raw: String?): List<AppShortcut> =
        raw.orEmpty().split(';').mapNotNull { item ->
            val parts = item.split('|')
            val pkg = parts.getOrNull(0)?.trim().orEmpty()
            if (pkg.isEmpty()) null else AppShortcut(pkg, parts.getOrNull(1) == "1")
        }.distinctBy { it.packageName }

    /** Fenêtres libres d’Android utilisables directement (bureau, option développeur…). */
    fun hasFreeformWindows(context: Context): Boolean {
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)) return true
        return runCatching {
            Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) == 1
        }.getOrDefault(false)
    }

    /**
     * Fenêtre réduite possible : fenêtres libres, ou fenêtre contextuelle du système ouverte
     * depuis l’écran Récents par le service d’accessibilité (Nothing, Samsung…).
     */
    fun supportsPopup(context: Context): Boolean =
        hasFreeformWindows(context) || OneHandModule.isAccessibilityServiceEnabled(context)

    fun label(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    /**
     * Lance l’application. En fenêtre réduite : fenêtre centrée sur 86 % de la largeur et
     * 55 % de la hauteur. @return false si l’appli est introuvable ; null si la fenêtre réduite
     * n’est pas possible (l’appli s’ouvre alors en plein écran).
     */
    fun launch(context: Context, packageName: String, popup: Boolean): Boolean? {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            ?: return false
        if (!popup) {
            return runCatching { context.startActivity(intent); true }.getOrDefault(false)
        }
        if (!hasFreeformWindows(context)) {
            runCatching { context.startActivity(intent) }
            return null
        }
        val metrics = context.resources.displayMetrics
        val w = (metrics.widthPixels * 0.86f).toInt()
        val h = (metrics.heightPixels * 0.55f).toInt()
        val left = (metrics.widthPixels - w) / 2
        val top = (metrics.heightPixels * 0.16f).toInt()
        val options = ActivityOptions.makeBasic().setLaunchBounds(Rect(left, top, left + w, top + h))
        runCatching {
            ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(options, WINDOWING_MODE_FREEFORM)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        return runCatching { context.startActivity(intent, options.toBundle()); true }.getOrElse {
            runCatching { context.startActivity(intent.removeFlagsCompat()) }
            null
        }
    }

    private fun Intent.removeFlagsCompat(): Intent = apply { flags = flags and Intent.FLAG_ACTIVITY_MULTIPLE_TASK.inv() }
}
