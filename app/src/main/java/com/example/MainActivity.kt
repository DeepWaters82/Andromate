package com.example

import android.Manifest
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.modules.automate.AutomateModule
import com.example.modules.clipboard.ClipboardController
import com.example.ui.MainViewModel
import com.example.ui.ModuYouApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var clipboardManager: ClipboardManager? = null

    // Sans cette autorisation (Android 13+), ni l’icône Caféine dans la barre d’état ni les
    // notifications des routines ne s’affichent. La tuile Caféine ne peut pas la demander :
    // on la demande donc dès l’ouverture de l’app.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        lifecycleScope.launch {
            ClipboardController.captureCurrentClipIfPresent(this@MainActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !AutomateModule.hasNotificationPermission(this)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        clipboardManager = (getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager)?.also {
            it.addPrimaryClipChangedListener(clipListener)
        }
        setContent {
            val viewModel: MainViewModel = viewModel()
            ModuYouApp(viewModel = viewModel)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            lifecycleScope.launch {
                ClipboardController.captureCurrentClipIfPresent(this@MainActivity)
            }
        }
    }

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }
}
