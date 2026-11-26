package com.example.modules.deepbluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.modules.automate.ModeIconRenderer
import com.example.ui.theme.OverlayTheme

/**
 * Popup DeepBluetooth : carte aux couleurs du thème qui glisse du haut (ou du bas) à la
 * connexion d’un appareil, avec son icône, son nom, son état, le pourcentage et une barre
 * de batterie qui se remplit. Le niveau arrive souvent quelques secondes après la connexion :
 * la carte se met alors à jour.
 */
class BluetoothPopup(
    private val context: Context,
    private val windowManager: WindowManager,
    private val colors: () -> OverlayTheme.Colors
) {
    private val handler = Handler(Looper.getMainLooper())
    private var window: View? = null
    private var card: View? = null
    private var shownAddress: String? = null
    private var percentView: TextView? = null
    private var barFill: View? = null
    private var barTrack: View? = null
    private var position = PopupPosition.TOP
    private var config = DeepBluetoothConfig()
    private var connected = true
    private val hideRunnable = Runnable { dismiss() }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics)

    @SuppressLint("MissingPermission")
    fun iconFor(device: BluetoothDevice?): ImageVector {
        val cls = runCatching { device?.bluetoothClass }.getOrNull() ?: return Icons.Rounded.Bluetooth
        return when {
            cls.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> Icons.Rounded.DirectionsCar
            cls.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO -> Icons.Rounded.Headphones
            cls.majorDeviceClass == BluetoothClass.Device.Major.WEARABLE -> Icons.Rounded.Watch
            cls.majorDeviceClass == BluetoothClass.Device.Major.PERIPHERAL -> Icons.Rounded.Keyboard
            else -> Icons.Rounded.Bluetooth
        }
    }

    /** Affiche la carte. [level] < 0 : niveau pas encore connu. */
    fun show(
        address: String,
        name: String,
        icon: ImageVector,
        connected: Boolean,
        level: Int,
        config: DeepBluetoothConfig
    ) {
        dismiss(animated = false)
        this.config = config
        this.connected = connected
        position = config.position
        val c = colors()
        val pad = dp(14f).toInt()

        val cardView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(12f).toInt(), dp(18f).toInt(), dp(14f).toInt())
            background = GradientDrawable().apply {
                setColor(c.surfaceContainerHigh)
                cornerRadius = dp(28f)
            }
            elevation = dp(10f)
            setOnClickListener { dismiss() }
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (config.showDeviceIcon) {
            row.addView(ImageView(context).apply {
                setImageBitmap(ModeIconRenderer.renderTinted(icon, dp(22f).toInt(), c.onPrimaryContainer))
                scaleType = ImageView.ScaleType.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c.primaryContainer)
                }
                layoutParams = LinearLayout.LayoutParams(dp(44f).toInt(), dp(44f).toInt()).apply {
                    marginEnd = dp(12f).toInt()
                }
            })
        }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(TextView(context).apply {
            text = name.ifBlank { "Appareil Bluetooth" }
            setTextColor(c.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        if (config.showStatus) {
            texts.addView(TextView(context).apply {
                text = if (connected) "Connecté" else "Déconnecté"
                setTextColor(c.onSurfaceVariant)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            })
        }
        row.addView(texts)
        val percent = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            visibility = if (config.showPercent) View.VISIBLE else View.GONE
        }
        row.addView(percent)
        cardView.addView(row)

        val track = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(c.surfaceContainerHighest)
                cornerRadius = dp(4f)
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8f).toInt()).apply {
                topMargin = dp(12f).toInt()
            }
            visibility = if (config.showBatteryBar) View.VISIBLE else View.GONE
        }
        val fill = View(context).apply {
            pivotX = 0f
            scaleX = 0f
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        track.addView(fill)
        cardView.addView(track)

        val top = position == PopupPosition.TOP
        val statusBar = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it != 0 }?.let { context.resources.getDimensionPixelSize(it) } ?: dp(24f).toInt()
        val root = FrameLayout(context).apply {
            setPadding(dp(12f).toInt(), if (top) statusBar + dp(8f).toInt() else 0, dp(12f).toInt(), if (top) 0 else dp(40f).toInt())
            clipToPadding = false
            addView(cardView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = if (top) Gravity.TOP else Gravity.BOTTOM }

        runCatching { windowManager.addView(root, params) }.onFailure { return }
        window = root
        card = cardView
        shownAddress = address
        percentView = percent
        barFill = fill
        barTrack = track
        applyLevel(level, animate = true)

        cardView.alpha = 0f
        cardView.translationY = if (top) -dp(110f) else dp(110f)
        cardView.scaleX = 0.92f
        cardView.scaleY = 0.92f
        cardView.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(420L)
            .setInterpolator(OvershootInterpolator(1.1f)).start()
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, config.durationSec * 1000L)
    }

    /** Niveau reçu après coup : mise à jour de la carte et un peu plus de temps à l’écran. */
    fun updateLevel(address: String, level: Int) {
        if (address != shownAddress || window == null) return
        applyLevel(level, animate = true)
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, (config.durationSec * 1000L).coerceAtLeast(3000L))
    }

    private fun applyLevel(level: Int, animate: Boolean) {
        val c = colors()
        val known = level in 0..100
        // Déconnecté : barre et pourcentage grisés (dernier niveau connu, toujours affiché).
        val color = when {
            !connected -> c.outline
            known && level <= 20 -> c.error
            else -> c.primary
        }
        percentView?.apply {
            text = if (known) "$level %" else "…"
            setTextColor(if (known) color else c.onSurfaceVariant)
        }
        barFill?.background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(4f)
        }
        val target = if (known) level / 100f else 0f
        barFill?.let { fill ->
            if (animate) {
                fill.animate().scaleX(target).setStartDelay(220L).setDuration(700L)
                    .setInterpolator(DecelerateInterpolator(1.6f)).start()
            } else {
                fill.scaleX = target
            }
        }
    }

    fun dismiss(animated: Boolean = true) {
        handler.removeCallbacks(hideRunnable)
        val root = window ?: return
        val cardView = card
        window = null
        card = null
        shownAddress = null
        percentView = null
        barFill = null
        barTrack = null
        val remove = { runCatching { windowManager.removeView(root) } }
        if (!animated || cardView == null) {
            remove()
            return
        }
        val top = position == PopupPosition.TOP
        cardView.animate().alpha(0f).translationY(if (top) -dp(80f) else dp(80f)).scaleX(0.95f).scaleY(0.95f)
            .setDuration(220L).setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction { remove() }.start()
    }
}
