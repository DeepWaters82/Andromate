package com.example.modules.onehand

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import org.xmlpull.v1.XmlPullParser

/**
 * Icône de l’appli audio dans la zone média : icône d’origine, version Material You
 * (calque monochrome d’Android 13 teinté aux couleurs du thème) ou icône d’un pack installé
 * (format appfilter.xml commun aux lanceurs Nova, ADW, Lawnchair…).
 */
object SourceIcons {

    data class IconPack(val packageName: String, val label: String)

    private val PACK_ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.gau.go.launcherex.theme",
        "com.anddoes.launcher.THEME"
    )

    private val appFilterCache = HashMap<String, Map<String, String>>()

    fun themedSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun installedPacks(context: Context): List<IconPack> {
        val pm = context.packageManager
        return PACK_ACTIONS.flatMap { action ->
            runCatching { pm.queryIntentActivities(Intent(action), 0) }.getOrDefault(emptyList())
        }.map { it.activityInfo.packageName }.distinct().mapNotNull { pkg ->
            runCatching { IconPack(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()) }.getOrNull()
        }.sortedBy { it.label.lowercase() }
    }

    /** Icône de [packageName] selon le style choisi ; repli sur l’icône d’origine. */
    fun iconFor(context: Context, packageName: String, style: String, accent: Int, container: Int, sizePx: Int): Drawable? {
        val pm = context.packageManager
        val original = runCatching { pm.getApplicationIcon(packageName) }.getOrNull()
        return when (style) {
            SourceIconStyle.APP -> original
            SourceIconStyle.THEMED -> themed(context, original, accent, container, sizePx) ?: original
            else -> fromPack(context, style, packageName) ?: original
        }
    }

    private fun themed(context: Context, icon: Drawable?, accent: Int, container: Int, sizePx: Int): Drawable? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val mono = (icon as? AdaptiveIconDrawable)?.monochrome ?: return null
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = container })
        // Le calque fait 108 dp dont 72 visibles : on l’agrandit d’un quart de chaque côté.
        val inset = sizePx / 4
        mono.mutate().apply {
            colorFilter = PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN)
            setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
            draw(canvas)
        }
        return BitmapDrawable(context.resources, bitmap)
    }

    /**
     * Pictogramme Material You de l’appli (calque monochrome d’Android 13+) teinté de [color],
     * sur fond transparent, ou null si l’appli n’en fournit pas.
     */
    fun themedGlyph(context: Context, packageName: String, color: Int, sizePx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || sizePx <= 0) return null
        val icon = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
        val mono = (icon as? AdaptiveIconDrawable)?.monochrome ?: return null
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val inset = sizePx / 4
        mono.mutate().apply {
            colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
            draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun fromPack(context: Context, pack: String, packageName: String): Drawable? = runCatching {
        val pm = context.packageManager
        val filter = appFilterCache.getOrPut(pack) { readAppFilter(context, pack) }
        val launch = pm.getLaunchIntentForPackage(packageName)?.component
        val exact = launch?.let { filter["ComponentInfo{${it.packageName}/${it.className}}"] }
        val name = exact ?: filter.entries.firstOrNull { it.key.startsWith("ComponentInfo{$packageName/") }?.value
            ?: return@runCatching null
        val res = pm.getResourcesForApplication(pack)
        val id = res.getIdentifier(name, "drawable", pack).takeIf { it != 0 } ?: return@runCatching null
        @Suppress("DEPRECATION")
        res.getDrawable(id)
    }.getOrNull()

    private fun readAppFilter(context: Context, pack: String): Map<String, String> {
        val result = HashMap<String, String>()
        runCatching {
            val res = context.packageManager.getResourcesForApplication(pack)
            val xmlId = res.getIdentifier("appfilter", "xml", pack)
            val parser: XmlPullParser = if (xmlId != 0) {
                res.getXml(xmlId)
            } else {
                android.util.Xml.newPullParser().apply { setInput(res.assets.open("appfilter.xml"), "UTF-8") }
            }
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (component != null && drawable != null) result.putIfAbsent(component, drawable)
                }
                event = parser.next()
            }
        }
        return result
    }

    /** Ouvre l’appli source : son écran de lecture si la session le fournit, sinon l’appli. */
    fun openSource(context: Context): Boolean {
        val session = MediaTracker.activeController()?.sessionActivity
        if (session != null) {
            val sent = runCatching {
                if (Build.VERSION.SDK_INT >= 34) {
                    val options = android.app.ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(
                            android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        )
                    session.send(context, 0, null, null, null, null, options.toBundle())
                } else {
                    session.send()
                }
                true
            }.getOrDefault(false)
            if (sent) return true
        }
        val pkg = MediaTracker.lastPackage(context) ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return false
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }
}
