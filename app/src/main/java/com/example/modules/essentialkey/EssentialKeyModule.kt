package com.example.modules.essentialkey

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SmartButton
import com.example.core.module.GoodLockModule
import com.example.core.module.ModuleCategory
import com.example.core.module.ModuleColorRole
import com.example.core.module.ModuleDescriptor
import com.example.modules.onehand.AppLauncher
import com.example.modules.onehand.GestureAction
import com.example.modules.onehand.OneHandModule

enum class KeyPress(val label: String) {
    SHORT("Appui court"),
    LONG("Appui long"),
    DOUBLE("Double appui")
}

/**
 * Actions de la touche Essential. Chaque action est une clé : "" (aucune), le nom d’un outil
 * ([GestureAction]) ou « app:paquet » pour ouvrir une application.
 */
data class EssentialKeyConfig(
    val enabled: Boolean = false,
    val shortPress: String = GestureAction.FLASHLIGHT.name,
    val longPress: String = "",
    val doublePress: String = ""
) {
    fun actionFor(press: KeyPress): String = when (press) {
        KeyPress.SHORT -> shortPress
        KeyPress.LONG -> longPress
        KeyPress.DOUBLE -> doublePress
    }

    fun with(press: KeyPress, action: String): EssentialKeyConfig = when (press) {
        KeyPress.SHORT -> copy(shortPress = action)
        KeyPress.LONG -> copy(longPress = action)
        KeyPress.DOUBLE -> copy(doublePress = action)
    }
}

/**
 * Touche Essential des Nothing Phone (3a) et suivants : code matériel 250, sans touche Android
 * associée. Le système la confie à Essential Space ; Andromate la reçoit juste après par son
 * service d’accessibilité. Pour qu’Andromate soit seul à y répondre, Essential Space doit être
 * désactivé (commande adb, Andromate ne peut pas le faire lui-même).
 */
object EssentialKeyModule : GoodLockModule {
    const val MODULE_ID = "essential_key"
    const val ROUTE = "module/essential_key"

    /** Code matériel de la touche (gpio-keys). */
    const val SCAN_CODE = 250
    const val RECORDER_PACKAGE = "com.nothing.ntessentialrecorder"
    const val DISABLE_COMMAND = "adb shell pm disable-user --user 0 $RECORDER_PACKAGE"
    const val ENABLE_COMMAND = "adb shell pm enable --user 0 $RECORDER_PACKAGE"

    /** Délai d’un appui long, et attente d’un second appui pour le double appui. */
    const val LONG_PRESS_MS = 500L
    const val DOUBLE_PRESS_MS = 350L

    override val descriptor: ModuleDescriptor = ModuleDescriptor(
        id = MODULE_ID,
        title = "Touche Essential",
        subtitle = "Reprogrammer la touche",
        description = "Choisit l’action de l’appui court, long et double sur la touche Essential des Nothing.",
        version = "v1.0",
        category = ModuleCategory.SYSTEM,
        icon = Icons.Rounded.SmartButton,
        colorRole = ModuleColorRole.PRIMARY,
        route = ROUTE,
        capabilities = listOf("KeyEvents", "Nothing")
    )

    override fun isSystemReady(context: Context): Boolean = OneHandModule.isAccessibilityServiceEnabled(context)

    /** Dernier réglage connu, lu par le service d’accessibilité. */
    @Volatile
    var current: EssentialKeyConfig = EssentialKeyConfig()

    /** Vrai sur un téléphone avec Essential Space (donc la touche), même désactivé. */
    fun hasKey(context: Context): Boolean = runCatching {
        context.packageManager.getApplicationInfo(RECORDER_PACKAGE, PackageManager.MATCH_DISABLED_COMPONENTS)
        true
    }.getOrDefault(false)

    /** Vrai si Essential Space répond encore à la touche. */
    fun isEssentialSpaceActive(context: Context): Boolean {
        if (!hasKey(context)) return false
        val pm = context.packageManager
        return when (runCatching { pm.getApplicationEnabledSetting(RECORDER_PACKAGE) }.getOrNull()) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            else -> runCatching {
                pm.getApplicationInfo(RECORDER_PACKAGE, PackageManager.MATCH_DISABLED_COMPONENTS).enabled
            }.getOrDefault(true)
        }
    }

    /** Outils proposés pour la touche. */
    val actionChoices: List<GestureAction> = GestureAction.entries.filter { it != GestureAction.NONE }

    fun appPackage(action: String): String? = action.takeIf { it.startsWith("app:") }?.removePrefix("app:")

    fun toolFor(action: String): GestureAction? =
        if (action.isBlank() || action.startsWith("app:")) null
        else runCatching { GestureAction.valueOf(action) }.getOrNull()

    fun label(context: Context, action: String): String {
        appPackage(action)?.let { return "Ouvrir ${AppLauncher.label(context, it)}" }
        return toolFor(action)?.label ?: "Aucune action"
    }
}
