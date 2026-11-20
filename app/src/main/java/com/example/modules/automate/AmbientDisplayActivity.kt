package com.example.modules.automate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AmbientDisplayActivity : ComponentActivity() {

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CLOSE) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val lp = window.attributes
        lp.screenBrightness = 0.08f
        window.attributes = lp

        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        ContextCompat.registerReceiver(
            this,
            closeReceiver,
            IntentFilter(ACTION_CLOSE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val routineLabel = intent.getStringExtra(EXTRA_ROUTINE_NAME).orEmpty().ifBlank { "Always-On Display" }

        setContent {
            var now by remember { mutableStateOf(Date()) }
            var batteryPct by remember { mutableIntStateOf(readBatteryPercent()) }

            LaunchedEffect(Unit) {
                while (true) {
                    now = Date()
                    batteryPct = readBatteryPercent()
                    delay(15_000L)
                }
            }

            val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
            val dateFmt = remember { SimpleDateFormat("EEEE d MMMM", Locale.FRENCH) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { finish() }
                        )
                    }
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = timeFmt.format(now),
                        fontFamily = OutfitFontFamily,
                        fontSize = 72.sp,
                        color = Color(0xFFA8C7FF)
                    )
                    Text(
                        text = dateFmt.format(now).replaceFirstChar { it.uppercase() },
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 18.sp,
                        color = Color(0xFF6EE7B7)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BatteryStd,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "$batteryPct%",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF111829)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF6EE7B7),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = routineLabel,
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 12.sp,
                                color = Color(0xFFE0E6F5)
                            )
                        }
                    }

                    Text(
                        text = "Double-tap pour quitter",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 11.sp,
                        color = Color(0xFF475569),
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
            }
        }
    }

    private fun readBatteryPercent(): Int {
        val bm = getSystemService(BATTERY_SERVICE) as? BatteryManager ?: return 100
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(closeReceiver) }
        super.onDestroy()
    }

    companion object {
        const val ACTION_CLOSE = "com.example.modules.automate.CLOSE_AMBIENT_DISPLAY"
        const val EXTRA_ROUTINE_NAME = "routine_name"
    }
}
