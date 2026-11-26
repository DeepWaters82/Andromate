package com.example.modules.shortcuts

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.modules.automate.ModeIconRenderer
import com.example.ui.theme.OverlayTheme
import com.example.ui.theme.ThemeSettings

/** Page des paramètres ouvrable par un raccourci ; [targets] essayés dans l’ordre. */
data class SettingsPage(val id: String, val label: String, val icon: ImageVector, val targets: List<String>)

/**
 * Raccourcis d’écran d’accueil vers une page précise des paramètres (Batterie, Écran…).
 * Le raccourci passe par [ShortcutTrampolineActivity], qui ouvre la page et se ferme.
 * Une cible « cmp:paquet/classe » désigne un écran précis, sinon c’est une action d’Intent.
 */
object SettingsShortcuts {

    val pages: List<SettingsPage> = listOf(
        SettingsPage("battery", "Batterie", Icons.Rounded.BatteryFull,
            listOf(Intent.ACTION_POWER_USAGE_SUMMARY, Settings.ACTION_BATTERY_SAVER_SETTINGS)),
        SettingsPage("battery_saver", "Économie d’énergie", Icons.Rounded.BatterySaver,
            listOf(Settings.ACTION_BATTERY_SAVER_SETTINGS)),
        SettingsPage("display", "Écran", Icons.Rounded.BrightnessMedium, listOf(Settings.ACTION_DISPLAY_SETTINGS)),
        SettingsPage("wallpaper", "Fond d’écran", Icons.Rounded.Wallpaper, listOf(Intent.ACTION_SET_WALLPAPER)),
        SettingsPage("wifi", "Wi-Fi", Icons.Rounded.Wifi, listOf(Settings.ACTION_WIFI_SETTINGS)),
        SettingsPage("bluetooth", "Bluetooth", Icons.Rounded.Bluetooth, listOf(Settings.ACTION_BLUETOOTH_SETTINGS)),
        SettingsPage("network", "Réseaux", Icons.Rounded.Public,
            listOf(Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS)),
        SettingsPage("data", "Consommation des données", Icons.Rounded.DataUsage,
            listOf("android.settings.DATA_USAGE_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS)),
        SettingsPage("hotspot", "Point d’accès", Icons.Rounded.WifiTethering,
            listOf("cmp:com.android.settings/com.android.settings.TetherSettings",
                "android.settings.TETHER_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS)),
        SettingsPage("nfc", "NFC", Icons.Rounded.Nfc, listOf(Settings.ACTION_NFC_SETTINGS)),
        SettingsPage("sound", "Son", Icons.AutoMirrored.Rounded.VolumeUp, listOf(Settings.ACTION_SOUND_SETTINGS)),
        SettingsPage("notifications", "Notifications", Icons.Rounded.Notifications,
            listOf("android.settings.NOTIFICATION_SETTINGS", Settings.ACTION_SETTINGS)),
        SettingsPage("dnd", "Ne pas déranger", Icons.Rounded.DoNotDisturbOn,
            listOf("android.settings.ZEN_MODE_SETTINGS", Settings.ACTION_SOUND_SETTINGS)),
        SettingsPage("apps", "Applications", Icons.Rounded.Apps, listOf(Settings.ACTION_APPLICATION_SETTINGS)),
        SettingsPage("storage", "Stockage", Icons.Rounded.Storage, listOf(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)),
        SettingsPage("location", "Localisation", Icons.Rounded.LocationOn, listOf(Settings.ACTION_LOCATION_SOURCE_SETTINGS)),
        SettingsPage("security", "Sécurité", Icons.Rounded.Security, listOf(Settings.ACTION_SECURITY_SETTINGS)),
        SettingsPage("privacy", "Confidentialité", Icons.Rounded.PrivacyTip,
            listOf(Settings.ACTION_PRIVACY_SETTINGS, Settings.ACTION_SECURITY_SETTINGS)),
        SettingsPage("accessibility", "Accessibilité", Icons.Rounded.AccessibilityNew, listOf(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
        SettingsPage("date", "Date et heure", Icons.Rounded.Schedule, listOf(Settings.ACTION_DATE_SETTINGS)),
        SettingsPage("language", "Langues", Icons.Rounded.Translate, listOf(Settings.ACTION_LOCALE_SETTINGS)),
        SettingsPage("developer", "Options pour les développeurs", Icons.Rounded.DeveloperMode,
            listOf(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)),
        SettingsPage("about", "À propos du téléphone", Icons.Rounded.Info, listOf(Settings.ACTION_DEVICE_INFO_SETTINGS)),
        SettingsPage("settings", "Paramètres", Icons.Rounded.Settings, listOf(Settings.ACTION_SETTINGS))
    )

    const val EXTRA_PAGE = "settings_page"

    fun find(id: String?): SettingsPage? = pages.firstOrNull { it.id == id }

    /** Ouvre la page : première cible qui existe sur ce téléphone. */
    fun open(context: Context, page: SettingsPage): Boolean {
        for (target in page.targets) {
            val intent = if (target.startsWith("cmp:")) {
                val (pkg, cls) = target.removePrefix("cmp:").split('/', limit = 2)
                Intent().setComponent(ComponentName(pkg, cls))
            } else {
                Intent(target)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        return false
    }

    fun canPin(context: Context): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    // ---- Icône d’appli : entrée du tiroir d’applis (sans petit logo, packs d’icônes) ----

    private const val ALIAS_PREFIX = "com.example.sc_"

    private fun aliasComponent(context: Context, page: SettingsPage) =
        ComponentName(context.packageName, ALIAS_PREFIX + page.id)

    fun pageFromComponent(className: String?): SettingsPage? =
        className?.takeIf { it.startsWith(ALIAS_PREFIX) }?.let { find(it.removePrefix(ALIAS_PREFIX)) }

    fun isAppIconEnabled(context: Context, page: SettingsPage): Boolean =
        context.packageManager.getComponentEnabledSetting(aliasComponent(context, page)) ==
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    /** Affiche ou retire la page dans le tiroir d’applis, comme une application. */
    /** @return false si Android a refusé le changement. */
    fun setAppIconEnabled(context: Context, page: SettingsPage, enabled: Boolean): Boolean =
        runCatching {
            context.packageManager.setComponentEnabledSetting(
                aliasComponent(context, page),
                if (enabled) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        }.onFailure { android.util.Log.w("Andromate", "Icône d’appli ${page.id}", it) }.isSuccess

    private fun iconResource(context: Context, page: SettingsPage): Int =
        context.resources.getIdentifier("ic_sc_${page.id}", "drawable", context.packageName)

    /** Demande au lanceur d’épingler le raccourci (icône aux couleurs du thème choisi). */
    fun pin(context: Context, page: SettingsPage, theme: ThemeSettings): Boolean {
        if (!canPin(context)) return false
        val intent = Intent(context, ShortcutTrampolineActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_PAGE, page.id)
        val info = ShortcutInfoCompat.Builder(context, "settings_${page.id}")
            .setShortLabel(page.label)
            .setLongLabel(page.label)
            .setIcon(
                iconResource(context, page).takeIf { it != 0 }?.let { IconCompat.createWithResource(context, it) }
                    ?: IconCompat.createWithAdaptiveBitmap(iconBitmap(context, page.icon, theme))
            )
            .setIntent(intent)
            .build()
        return runCatching { ShortcutManagerCompat.requestPinShortcut(context, info, null) }.getOrDefault(false)
    }

    /** Icône adaptative : fond « primaryContainer », pictogramme centré dans la zone sûre. */
    private fun iconBitmap(context: Context, icon: ImageVector, theme: ThemeSettings): Bitmap {
        val colors = OverlayTheme.colors(context, theme)
        val size = (108 * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(colors.primaryContainer)
        val glyphSize = (size * 0.40f).toInt()
        val glyph = ModeIconRenderer.renderTinted(icon, glyphSize, colors.onPrimaryContainer)
        val offset = (size - glyphSize) / 2f
        canvas.drawBitmap(glyph, offset, offset, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return bitmap
    }
}

/** Ouvre la page des paramètres demandée par le raccourci, puis se ferme aussitôt. */
class ShortcutTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = SettingsShortcuts.find(intent?.getStringExtra(SettingsShortcuts.EXTRA_PAGE))
            ?: SettingsShortcuts.pageFromComponent(intent?.component?.className)
        page?.let { SettingsShortcuts.open(this, it) }
        finish()
    }
}
