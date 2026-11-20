package com.example.modules.onehand

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwipeRight
import com.example.core.module.GoodLockModule
import com.example.core.module.ModuleCategory
import com.example.core.module.ModuleColorRole
import com.example.core.module.ModuleDescriptor

object OneHandModule : GoodLockModule {
    const val MODULE_ID = "one_hand_plus"
    const val ROUTE = "module/one_hand_plus"

    override val descriptor: ModuleDescriptor = ModuleDescriptor(
        id = MODULE_ID,
        title = "Une Main +",
        subtitle = "Gestes latéraux & raccourcis rapides",
        description = "Ajoute des poignées de navigation personnalisables sur les bords gauche et droit de l'écran (jusqu'à 100 % de la hauteur) pour contrôler votre appareil d'une seule main.",
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
    @Volatile
    private var isTorchOn: Boolean = false

    fun executeSystemAction(
        service: AccessibilityService,
        action: GestureAction,
        onShowClipboardOverlay: () -> Unit = {}
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
            GestureAction.NONE -> false
        }
    }

    fun toggleFlashlight(context: Context): Boolean {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
        return runCatching {
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false
            isTorchOn = !isTorchOn
            cm.setTorchMode(cameraId, isTorchOn)
            true
        }.getOrDefault(false)
    }
}
