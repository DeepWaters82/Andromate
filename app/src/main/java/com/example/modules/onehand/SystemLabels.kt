package com.example.modules.onehand

import android.content.Context
import android.content.Intent
import java.util.Locale

/**
 * Libellés lus dans les ressources du volet système (SystemUI) et du lanceur du téléphone :
 * Andromate reconnaît ainsi les tuiles et les options de Récents dans la langue et la
 * variante du fabricant, sans dictionnaire écrit en dur. Les noms connus (FR/EN, Samsung,
 * Nothing) restent en secours.
 */
object SystemLabels {

    private const val SYSTEM_UI = "com.android.systemui"
    private val cache = HashMap<String, List<String>>()

    private fun normalize(text: String): String =
        text.lowercase(Locale.ROOT).replace('\n', ' ').replace(',', ' ').replace(Regex("""\s+"""), " ").trim()

    /** Textes des ressources [names] de [packageName], normalisés ; vide si introuvables. */
    private fun read(context: Context, packageName: String, names: List<String>): List<String> {
        val key = "$packageName|${Locale.getDefault()}|${names.joinToString()}"
        cache[key]?.let { return it }
        val result = runCatching {
            val res = context.packageManager.getResourcesForApplication(packageName)
            names.mapNotNull { name ->
                val id = res.getIdentifier(name, "string", packageName)
                if (id == 0) null else runCatching { normalize(res.getString(id)) }.getOrNull()
            }.filter { it.isNotEmpty() && !it.contains('%') }.distinct()
        }.getOrDefault(emptyList())
        cache[key] = result
        return result
    }

    fun launcherPackage(context: Context): String? = runCatching {
        context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        )?.activityInfo?.packageName
    }.getOrNull()

    /** Libellés de tuile possibles pour un interrupteur (ressources du volet + secours). */
    fun tileLabels(context: Context, tool: GestureAction): List<String> {
        val names = when (tool) {
            GestureAction.WIFI -> listOf("quick_settings_wifi_label", "wifi")
            GestureAction.BLUETOOTH -> listOf("quick_settings_bluetooth_label", "bluetooth")
            GestureAction.MOBILE_DATA -> listOf("quick_settings_cellular_detail_title", "mobile_data", "quick_settings_mobile_data_label")
            GestureAction.HOTSPOT -> listOf("quick_settings_hotspot_label", "hotspot")
            GestureAction.AIRPLANE -> listOf("airplane_mode", "quick_settings_airplane_mode_label", "airplane_mode_title")
            else -> emptyList()
        }
        return read(context, SYSTEM_UI, names)
    }

    /**
     * Tuile « Internet » d’Android 12+ (Pixel, Motorola…) : Wi-Fi et données y sont réunis ;
     * un appui ouvre une fenêtre avec les interrupteurs Wi-Fi et données.
     */
    fun internetTileLabels(context: Context): List<String> =
        read(context, SYSTEM_UI, listOf("quick_settings_internet_label")).ifEmpty { listOf("internet") }

    /** Options « fenêtre flottante » du menu de Récents, d’après les ressources du lanceur. */
    fun recentsPopupOptions(context: Context): List<String> {
        val launcher = launcherPackage(context) ?: return emptyList()
        return read(
            context, launcher,
            listOf(
                "recent_task_option_freeform", "recent_task_option_desktop", "recent_task_option_popup",
                "recent_task_option_pop_up", "recent_task_option_floating", "popup_view", "pop_up_view",
                "open_in_pop_up_view", "freeform", "floating_window"
            )
        )
    }
}
