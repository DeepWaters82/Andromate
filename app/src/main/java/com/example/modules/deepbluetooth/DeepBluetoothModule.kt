package com.example.modules.deepbluetooth

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothAudio
import com.example.core.module.GoodLockModule
import com.example.core.module.ModuleCategory
import com.example.core.module.ModuleColorRole
import com.example.core.module.ModuleDescriptor
import com.example.modules.onehand.OneHandModule

enum class PopupPosition(val label: String) {
    TOP("En haut"),
    BOTTOM("En bas")
}

/** Réglages de la popup Bluetooth (connexion / déconnexion d’un appareil). */
data class DeepBluetoothConfig(
    val enabled: Boolean = true,
    val onConnect: Boolean = true,
    val onDisconnect: Boolean = false,
    val showBatteryBar: Boolean = true,
    val showPercent: Boolean = true,
    val showStatus: Boolean = true,
    val showDeviceIcon: Boolean = true,
    val position: PopupPosition = PopupPosition.TOP,
    val durationSec: Int = 5,
    /** Laisse aussi une notification avec le niveau de batterie. */
    val alsoNotification: Boolean = false,
    /** Appareils concernés, un nom par ligne ; vide = tous. */
    val devices: String = ""
)

object DeepBluetoothModule : GoodLockModule {
    const val MODULE_ID = "deep_bluetooth"
    const val ROUTE = "module/deep_bluetooth"

    override val descriptor: ModuleDescriptor = ModuleDescriptor(
        id = MODULE_ID,
        title = "DeepBluetooth",
        subtitle = "Popup des appareils",
        description = "Popup à la connexion d’un appareil Bluetooth, avec sa batterie.",
        version = "v1.0",
        category = ModuleCategory.SYSTEM,
        icon = Icons.Rounded.BluetoothAudio,
        colorRole = ModuleColorRole.SECONDARY,
        route = ROUTE,
        capabilities = listOf("BluetoothBattery", "Overlay")
    )

    /** La popup est dessinée par le service d’accessibilité d’Andromate. */
    override fun isSystemReady(context: Context): Boolean = OneHandModule.isAccessibilityServiceEnabled(context)

    /** Dernier réglage connu (lu par la notification de batterie des routines). */
    @Volatile
    var current: DeepBluetoothConfig = DeepBluetoothConfig()
}
