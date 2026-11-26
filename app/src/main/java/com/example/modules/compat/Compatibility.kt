package com.example.modules.compat

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VerifiedUser
import com.example.core.module.GoodLockModule
import com.example.core.module.ModuleCategory
import com.example.core.module.ModuleColorRole
import com.example.core.module.ModuleDescriptor
import com.example.modules.automate.AutomateModule
import com.example.modules.onehand.AppLauncher
import com.example.modules.onehand.ConnectionNames
import com.example.modules.onehand.GestureAction
import com.example.modules.onehand.MediaTracker
import com.example.modules.onehand.OneHandModule
import com.example.modules.onehand.QuickToggles
import com.example.modules.onehand.RecentsPopup
import com.example.modules.onehand.SystemLabels
import com.example.modules.shortcuts.SettingsShortcuts
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

object CompatibilityModule : GoodLockModule {
    const val MODULE_ID = "compatibility"
    const val ROUTE = "module/compatibility"

    override val descriptor: ModuleDescriptor = ModuleDescriptor(
        id = MODULE_ID,
        title = "Compatibilité",
        subtitle = "Diagnostic du téléphone",
        description = "Vérifie ce qui fonctionne sur ce téléphone et produit un rapport.",
        version = "v1.0",
        category = ModuleCategory.SYSTEM,
        icon = Icons.Rounded.VerifiedUser,
        colorRole = ModuleColorRole.TERTIARY,
        route = ROUTE,
        capabilities = listOf("Diagnostic")
    )

    override fun isSystemReady(context: Context): Boolean = true
}

enum class CheckStatus(val mark: String) { OK("✔"), WARN("⚠"), FAIL("✘"), UNKNOWN("·") }

/** Une vérification : état, détail, et réglage à ouvrir pour corriger. */
data class CompatCheck(
    val title: String,
    val status: CheckStatus,
    val detail: String,
    val fixLabel: String? = null,
    val fix: ((Context) -> Unit)? = null
)

/** Résultats des tests en direct (volet, Récents), faits par le service d’accessibilité. */
data class LiveProbe(
    val running: Boolean = false,
    val done: Boolean = false,
    val tiles: Map<GestureAction, String?> = emptyMap(),
    val recents: RecentsPopup.Probe? = null,
    val screenshot: Boolean? = null,
    val gestures: Boolean? = null
)

object Compatibility {

    val live = MutableStateFlow(LiveProbe())

    private fun open(action: String): (Context) -> Unit = { context ->
        runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun openAppDetails(): (Context) -> Unit = { context ->
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun deviceSummary(context: Context): String =
        "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
            "${Locale.getDefault().toLanguageTag()} · lanceur ${SystemLabels.launcherPackage(context) ?: "?"}"

    /** Vérifications immédiates (autorisations, fonctions du système). */
    fun staticChecks(context: Context): List<CompatCheck> {
        val a11y = OneHandModule.isAccessibilityServiceEnabled(context)
        val checks = ArrayList<CompatCheck>()
        checks += CompatCheck(
            "Service d’accessibilité", if (a11y) CheckStatus.OK else CheckStatus.FAIL,
            if (a11y) "Actif : poignées, menu d’outils, popup Bluetooth" else "Inactif : poignées, menu, gestes et popup indisponibles",
            "Accessibilité", open(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        )
        val notifPost = AutomateModule.hasNotificationPermission(context)
        checks += CompatCheck(
            "Notifications", if (notifPost) CheckStatus.OK else CheckStatus.WARN,
            if (notifPost) "Autorisées" else "Refusées : modes, Caféine et alertes sans notification", "Autoriser", openAppDetails()
        )
        val listener = MediaTracker.hasListenerAccess(context)
        checks += CompatCheck(
            "Accès aux notifications", if (listener) CheckStatus.OK else CheckStatus.WARN,
            if (listener) "Zone audio : source et titre" else "Zone audio sans source ni titre",
            "Autoriser", open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        )
        val dnd = QuickToggles.hasPolicyAccess(context)
        checks += CompatCheck(
            "Accès « Ne pas déranger »", if (dnd) CheckStatus.OK else CheckStatus.WARN,
            if (dnd) "NPD, silencieux et vibreur" else "Outils NPD et silencieux indisponibles",
            "Autoriser", open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        )
        val write = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)
        checks += CompatCheck(
            "Modifier les paramètres système", if (write) CheckStatus.OK else CheckStatus.WARN,
            if (write) "Rotation, luminosité, mise en veille" else "Rotation et luminosité indisponibles",
            "Autoriser", open(Settings.ACTION_MANAGE_WRITE_SETTINGS)
        )
        val bt = ConnectionNames.hasBluetoothPermission(context)
        checks += CompatCheck(
            "Appareils à proximité", if (bt) CheckStatus.OK else CheckStatus.WARN,
            if (bt) "Noms des appareils Bluetooth" else "Noms des appareils Bluetooth masqués", "Autoriser", openAppDetails()
        )
        val loc = ConnectionNames.hasLocationPermission(context)
        val locOn = AutomateModule.isLocationEnabled(context)
        checks += CompatCheck(
            "Nom du Wi-Fi", when { loc && locOn -> CheckStatus.OK; else -> CheckStatus.WARN },
            when {
                !loc -> "Position refusée : nom lu sur la tuile du volet"
                !locOn -> "Localisation coupée : nom lu sur la tuile du volet"
                else -> "Donné par Android"
            },
            if (!loc) "Autoriser" else "Localisation",
            if (!loc) openAppDetails() else open(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        )
        val battery = runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)
        checks += CompatCheck(
            "Optimisation de la batterie", if (battery) CheckStatus.OK else CheckStatus.WARN,
            if (battery) "Ignorée : routines fiables en arrière-plan" else "Active : le système peut retarder les routines",
            "Régler", open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )
        val sideBack = OneHandModule.hasSideBackGesture(context)
        checks += CompatCheck(
            "Navigation", if (sideBack) CheckStatus.WARN else CheckStatus.OK,
            if (sideBack) "Retour latéral : poignées limitées à 200 dp par Android" else "Poignées sur toute leur hauteur",
            "Navigation", { OneHandModule.openNavigationGestureSettings(it) }
        )
        val labels = SystemLabels.tileLabels(context, GestureAction.WIFI)
        checks += CompatCheck(
            "Libellés du volet", if (labels.isNotEmpty()) CheckStatus.OK else CheckStatus.WARN,
            if (labels.isNotEmpty()) "Lus dans le système (${labels.first()})" else "Introuvables : noms français/anglais seulement"
        )
        val btLevel = runCatching { android.bluetooth.BluetoothDevice::class.java.getMethod("getBatteryLevel"); true }.getOrDefault(false)
        checks += CompatCheck(
            "Batterie des appareils Bluetooth", if (btLevel) CheckStatus.OK else CheckStatus.WARN,
            if (btLevel) "Lisible (popup DeepBluetooth avec pourcentage)" else "Non lisible : popup sans pourcentage"
        )
        val hotspot = QuickToggles.state(context, GestureAction.HOTSPOT) != null
        checks += CompatCheck(
            "État du point d’accès", if (hotspot) CheckStatus.OK else CheckStatus.WARN,
            if (hotspot) "Lisible" else "Non lisible : bouton Partage sans état"
        )
        val freeform = AppLauncher.hasFreeformWindows(context)
        checks += CompatCheck(
            "Fenêtres libres", if (freeform) CheckStatus.OK else CheckStatus.UNKNOWN,
            if (freeform) "Fenêtre réduite directe" else "Fenêtre réduite par Récents (voir test en direct)"
        )
        checks += CompatCheck(
            "Icônes Material You", if (Build.VERSION.SDK_INT >= 33) CheckStatus.OK else CheckStatus.WARN,
            if (Build.VERSION.SDK_INT >= 33) "Disponibles" else "Android 13 requis"
        )
        checks += CompatCheck(
            "Raccourcis épinglés", if (SettingsShortcuts.canPin(context)) CheckStatus.OK else CheckStatus.WARN,
            if (SettingsShortcuts.canPin(context)) "Acceptés par le lanceur" else "Refusés : utiliser les icônes d’appli"
        )
        return checks
    }

    /** Vérifications issues des tests en direct. */
    fun liveChecks(probe: LiveProbe): List<CompatCheck> {
        if (!probe.done) return emptyList()
        val checks = ArrayList<CompatCheck>()
        probe.screenshot?.let {
            checks += CompatCheck("Écran figé pendant les bascules", if (it) CheckStatus.OK else CheckStatus.WARN,
                if (it) "Disponible" else "Indisponible : le volet sera visible un instant")
        }
        probe.gestures?.let {
            checks += CompatCheck("Toucher simulé", if (it) CheckStatus.OK else CheckStatus.WARN,
                if (it) "Disponible" else "Indisponible : réactiver l’accessibilité")
        }
        probe.tiles.forEach { (tool, label) ->
            checks += CompatCheck(
                "Tuile ${tool.label}", if (label != null) CheckStatus.OK else CheckStatus.FAIL,
                label?.let { "Trouvée : « $it »" } ?: "Introuvable : l’outil ouvrira l’écran de réglage"
            )
        }
        probe.recents?.let { r ->
            checks += CompatCheck(
                "Fenêtre réduite (Récents)",
                when { r.option != null -> CheckStatus.OK; r.iconFound -> CheckStatus.FAIL; else -> CheckStatus.FAIL },
                when {
                    r.option != null -> "Option « ${r.option} »"
                    r.iconFound -> "Menu sans fenêtre flottante : ${r.menu.joinToString(" · ").ifBlank { "vide" }}"
                    else -> "Icône de l’appli introuvable dans Récents"
                }
            )
        }
        return checks
    }

    fun report(context: Context, checks: List<CompatCheck>): String = buildString {
        appendLine("Andromate — compatibilité")
        appendLine(deviceSummary(context))
        checks.forEach { appendLine("${it.status.mark} ${it.title} : ${it.detail}") }
    }
}
