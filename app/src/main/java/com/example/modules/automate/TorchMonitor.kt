package com.example.modules.automate

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

/**
 * Suit l’état réel de la lampe torche (allumée depuis le volet, une autre appli, un geste…),
 * pour que la bascule et le retour à l’état précédent partent de la bonne valeur.
 */
object TorchMonitor {
    @Volatile
    var isOn: Boolean = false
        private set

    @Volatile
    private var registered = false

    fun ensureRegistered(context: Context) {
        if (registered) return
        val cm = context.applicationContext.getSystemService(CameraManager::class.java) ?: return
        runCatching {
            cm.registerTorchCallback(
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        isOn = enabled
                    }
                },
                Handler(Looper.getMainLooper())
            )
            registered = true
        }
    }

    /** Appelé juste après setTorchMode : le rappel système arrive un peu plus tard. */
    fun markSet(enabled: Boolean) {
        isOn = enabled
    }
}
