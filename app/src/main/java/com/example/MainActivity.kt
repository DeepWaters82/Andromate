package com.example

import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.modules.clipboard.ClipboardController
import com.example.ui.MainViewModel
import com.example.ui.ModuYouApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var clipboardManager: ClipboardManager? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        lifecycleScope.launch {
            ClipboardController.captureCurrentClipIfPresent(this@MainActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
