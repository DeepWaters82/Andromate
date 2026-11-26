package com.example.modules.onehand

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import com.example.modules.caffeine.CaffeineController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwipeRight
import com.example.core.module.GoodLockModule
import com.example.core.module.ModuleCategory
import com.example.core.module.ModuleColorRole
import com.example.core.module.ModuleDescriptor
import com.example.modules.automate.TorchMonitor

object OneHandModule : GoodLockModule {
    const val MODULE_ID = "one_hand_plus"
    const val ROUTE = "module/one_hand_plus"

    override val descriptor: ModuleDescriptor = ModuleDescriptor(
        id = MODULE_ID,
        title = "Une Main +",
        subtitle = "Gestes sur les bords",
        description = "Poignées de gestes sur les bords de l’écran.",
        version = "v1.1",
        category = ModuleCategory.GESTURES,
        icon = Icons.Rounded.SwipeRight,
        colorRole = ModuleColorRole.PRIMARY,
        route = ROUTE,
        capabilities = listOf(
            "AccessibilityService",
            "TypeAccessibilityOverlay",
            "HapticFeedback"
        )
    )

    override fun isSystemReady(context: Context): Boolean {
        return isAccessibilityServiceEnabled(context)
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val expectedComponent = ComponentName(context, OneHandAccessibilityService::class.java)

        val enabledList = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        if (enabledList != null) {
            val found = enabledList.any { info ->
                val serviceInfo = info.resolveInfo?.serviceInfo
                serviceInfo != null &&
                    serviceInfo.packageName == expectedComponent.packageName &&
                    serviceInfo.name == expectedComponent.className
            }
            if (found) return true
        }

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val flattened = expectedComponent.flattenToString()
        val shortFlattened = expectedComponent.flattenToShortString()
        return enabledServices.split(':').any { entry ->
            entry.equals(flattened, ignoreCase = true) ||
                entry.equals(shortFlattened, ignoreCase = true)
        }
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Navigation par gestes (Android 10+, One UI « Gestes de balayage »). */
    fun isGestureNavigation(context: Context): Boolean {
        val cr = context.contentResolver
        val navMode = runCatching { Settings.Secure.getInt(cr, "navigation_mode", 0) }.getOrDefault(0)
        val samsungGestures = runCatching {
            Settings.Global.getInt(cr, "navigation_bar_gesture_while_hidden", 0)
        }.getOrDefault(0)
        return navMode == 2 || samsungGestures == 1
    }

    /** Hauteur maximale qu’Android laisse prioritaire sur le geste Retour, par bord. */
    const val GESTURE_EXCLUSION_LIMIT_DP = 200f

    /**
     * Geste Retour par balayage latéral actif : il passe avant les poignées hors des 200 dp
     * prioritaires. Sur One UI, « Balayer depuis le bas » (type 0) n’a pas de Retour latéral.
     */
    fun hasSideBackGesture(context: Context): Boolean {
        if (!isGestureNavigation(context)) return false
        val samsungType = runCatching {
            Settings.Global.getInt(context.contentResolver, "navigation_bar_gesture_detail_type", -1)
        }.getOrDefault(-1)
        return samsungType != 0
    }

    val isSamsung: Boolean get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    /** Ouvre le choix du type de gestes (One UI) ou, à défaut, les réglages de navigation. */
    fun openNavigationGestureSettings(context: Context) {
        val candidates = listOf(
            Intent("com.samsung.settings.NAVIGATION_BAR_DETAIL_SETTING"),
            Intent().setClassName("com.android.settings", "com.android.settings.Settings\$NavigationBarSettingsActivity"),
            Intent("android.settings.GESTURE_NAVIGATION_SETTINGS"),
            Intent(Settings.ACTION_DISPLAY_SETTINGS)
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
    }

    fun hasFlashlightHardware(context: Context): Boolean {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
        return runCatching {
            cm.cameraIdList.any { id ->
                cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrDefault(false)
    }
}

object OneHandActionExecutor {

    fun executeSystemAction(
        service: AccessibilityService,
        action: GestureAction,
        onShowClipboardOverlay: () -> Unit = {},
        onShowToolsMenu: () -> Unit = {}
    ): Boolean {
        return when (action) {
            GestureAction.BACK -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            GestureAction.HOME -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            GestureAction.RECENTS -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            GestureAction.NOTIFICATIONS -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            GestureAction.QUICK_SETTINGS -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            GestureAction.CLIPBOARD -> {
                onShowClipboardOverlay()
                true
            }
            GestureAction.POWER_MENU -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            GestureAction.SCREENSHOT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                } else {
                    false
                }
            }
            GestureAction.LOCK_SCREEN -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                } else {
                    false
                }
            }
            GestureAction.FLASHLIGHT -> toggleFlashlight(service)
            GestureAction.TOOLS_MENU -> {
                onShowToolsMenu()
                true
            }
            GestureAction.PREVIOUS_APP -> {
                // Double appui sur Récents : bascule vers la dernière application.
                val ok = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                Handler(Looper.getMainLooper()).postDelayed({
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                }, 300L)
                ok
            }
            GestureAction.SPLIT_SCREEN ->
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            GestureAction.PLAY_PAUSE -> MediaTracker.playPause(service)
            GestureAction.NEXT_TRACK -> MediaTracker.next(service)
            GestureAction.PREVIOUS_TRACK -> MediaTracker.previous(service)
            GestureAction.VOLUME_PANEL -> {
                val am = service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                am != null
            }
            GestureAction.CAFFEINE -> {
                CaffeineController.toggle(service)
                true
            }
            GestureAction.ROTATION -> toggleAutoRotation(service)
            // Basculés par le service via les tuiles du volet (voir QuickToggles.toggleTile).
            GestureAction.WIFI, GestureAction.BLUETOOTH, GestureAction.MOBILE_DATA,
            GestureAction.HOTSPOT, GestureAction.AIRPLANE -> false
            GestureAction.DND, GestureAction.SILENT, GestureAction.VIBRATE ->
                QuickToggles.toggleDirect(service, action) != null
            GestureAction.NONE -> false
        }
    }


    private fun toggleAutoRotation(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
            Toast.makeText(context, "Autorisez « Modifier les paramètres système »", Toast.LENGTH_SHORT).show()
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                        .setData(android.net.Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return false
        }
        return runCatching {
            val cr = context.contentResolver
            val auto = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
            // La confirmation est affichée par la bulle du service Une Main +.
            Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, if (auto) 0 else 1)
            true
        }.getOrDefault(false)
    }

    fun toggleFlashlight(context: Context): Boolean {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
        return runCatching {
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false
            TorchMonitor.ensureRegistered(context)
            val target = !TorchMonitor.isOn
            cm.setTorchMode(cameraId, target)
            TorchMonitor.markSet(target)
            true
        }.getOrDefault(false)
    }
}
