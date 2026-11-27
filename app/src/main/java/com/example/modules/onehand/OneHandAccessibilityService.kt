package com.example.modules.onehand

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.PlayArrow
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.example.MainActivity
import com.example.core.preferences.ModuYouPreferences
import com.example.modules.automate.AutomateController
import com.example.modules.clipboard.ClipboardController
import com.example.modules.clipboard.ClipboardEntry
import com.example.ui.theme.ThemeSettings
import com.example.ui.theme.paletteColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class OneHandAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private lateinit var preferences: ModuYouPreferences

    private var currentConfig = OneHandConfig()
    private var currentTheme = ThemeSettings()
    private var isLandscape = false

    private var leftHandleView: EdgeHandleOverlayView? = null
    private var rightHandleView: EdgeHandleOverlayView? = null
    private var feedbackOverlayView: GestureFeedbackOverlayView? = null
    private var clipboardOverlayView: View? = null
    private var toolsOverlayView: View? = null
    private var toolsCardView: View? = null
    private var feedbackPillView: View? = null
    private var mediaCallback: android.media.session.MediaController.Callback? = null
    private var mediaCallbackController: android.media.session.MediaController? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var ambientOverlayView: View? = null

    // ---- Touche Essential (Nothing) ----

    private var essentialLongFired = false
    private var essentialPendingShort: Runnable? = null
    private var essentialSecondPress = false
    private val essentialLongRunnable = Runnable {
        essentialLongFired = true
        runEssentialAction(com.example.modules.essentialkey.KeyPress.LONG)
    }

    private fun setKeyFiltering(on: Boolean) {
        val info = serviceInfo ?: return
        val flag = android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        val wanted = if (on) info.flags or flag else info.flags and flag.inv()
        if (wanted != info.flags) {
            info.flags = wanted
            serviceInfo = info
        }
    }

    /**
     * Touche Essential : code matériel 250, sans touche Android. Appui long au bout de
     * [com.example.modules.essentialkey.EssentialKeyModule.LONG_PRESS_MS] ; si un double appui
     * est choisi, l’appui court attend un éventuel second appui. Les autres touches passent.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val module = com.example.modules.essentialkey.EssentialKeyModule
        val config = module.current
        if (!config.enabled || event.scanCode != module.SCAN_CODE || event.keyCode != KeyEvent.KEYCODE_UNKNOWN) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                essentialLongFired = false
                mainHandler.removeCallbacks(essentialLongRunnable)
                // Second appui avant la fin de l’attente : c’est un double appui (compté à l’appui,
                // comme le double tap d’Android, pas au relâchement).
                val pending = essentialPendingShort
                if (pending != null) {
                    mainHandler.removeCallbacks(pending)
                    essentialPendingShort = null
                    essentialSecondPress = true
                } else {
                    essentialSecondPress = false
                    if (config.longPress.isNotBlank()) mainHandler.postDelayed(essentialLongRunnable, module.LONG_PRESS_MS)
                }
            }
            KeyEvent.ACTION_UP -> {
                mainHandler.removeCallbacks(essentialLongRunnable)
                if (essentialLongFired) return true
                when {
                    essentialSecondPress -> {
                        essentialSecondPress = false
                        runEssentialAction(com.example.modules.essentialkey.KeyPress.DOUBLE)
                    }
                    config.doublePress.isBlank() -> runEssentialAction(com.example.modules.essentialkey.KeyPress.SHORT)
                    else -> {
                        val single = Runnable {
                            essentialPendingShort = null
                            runEssentialAction(com.example.modules.essentialkey.KeyPress.SHORT)
                        }
                        essentialPendingShort = single
                        mainHandler.postDelayed(single, module.DOUBLE_PRESS_MS)
                    }
                }
            }
        }
        return true
    }

    private fun runEssentialAction(press: com.example.modules.essentialkey.KeyPress) {
        val module = com.example.modules.essentialkey.EssentialKeyModule
        val action = module.current.actionFor(press)
        if (action.isBlank()) return
        performHaptic(isLong = press == com.example.modules.essentialkey.KeyPress.LONG)
        module.appPackage(action)?.let { pkg ->
            dismissToolsPanel()
            launchApp(pkg, popup = false)
            return
        }
        module.toolFor(action)?.let { runToolWithFeedback(it) }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        preferences = ModuYouPreferences.getInstance(this)
        isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        runCatching { registerReceiver(screenOffReceiver, android.content.IntentFilter(Intent.ACTION_SCREEN_OFF)) }
        // Changement de mode de navigation : la zone des poignées est recalculée aussitôt.
        runCatching {
            contentResolver.registerContentObserver(android.provider.Settings.Secure.getUriFor("navigation_mode"), false, navigationObserver)
            contentResolver.registerContentObserver(
                android.provider.Settings.Global.getUriFor("navigation_bar_gesture_detail_type"), false, navigationObserver
            )
        }
        ConnectionNames.ensureBluetoothProfiles(this)
        // Caféine et la surveillance des routines sont « tenues » tant qu’elles tournent :
        // elles peuvent alors se passer de notification si l’utilisateur l’a masquée.
        serviceScope.launch {
            preferences.automateConfigFlow.map { AutomateController.shouldMonitor(it) }.distinctUntilChanged().collect {
                keepService(com.example.modules.automate.AutomateMonitorService::class.java, it)
            }
        }
        serviceScope.launch {
            preferences.caffeineConfigFlow.map { it.isActive }.distinctUntilChanged().collect {
                keepService(com.example.modules.caffeine.CaffeineKeepAwakeService::class.java, it)
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                bluetoothReceiver,
                android.content.IntentFilter().apply {
                    addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
                    addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
                    addAction(com.example.modules.automate.BluetoothBatteryNotifier.ACTION_BATTERY_LEVEL_CHANGED)
                },
                ContextCompat.RECEIVER_EXPORTED
            )
        }
        serviceScope.launch {
            preferences.deepBluetoothConfigFlow.collect {
                com.example.modules.deepbluetooth.DeepBluetoothModule.current = it
            }
        }
        serviceScope.launch {
            preferences.essentialKeyConfigFlow.collect {
                com.example.modules.essentialkey.EssentialKeyModule.current = it
                // Les touches ne sont filtrées que si la touche Essential est reprogrammée.
                setKeyFiltering(it.enabled)
            }
        }

        serviceScope.launch {
            combine(
                preferences.oneHandConfigFlow,
                preferences.themeSettingsFlow
            ) { config, theme ->
                config to theme
            }.collect { (config, theme) ->
                currentConfig = config
                currentTheme = theme
                syncOverlays()
            }
        }

        serviceScope.launch {
            preferences.automateConfigFlow
                .map { AutomateController.shouldMonitor(it) to it }
                .distinctUntilChanged { old, new -> old.first == new.first }
                .collect { (_, automateConfig) ->
                    AutomateController.syncMonitoringService(this@OneHandAccessibilityService, automateConfig)
                }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        syncOverlays()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Notification de lecture d’une appli audio : seule sa session média est retenue
        // (zone média du menu d’outils). Le contenu des autres notifications est ignoré.
        if (event?.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val notification = event.parcelableData as? android.app.Notification ?: return
            val pkg = event.packageName?.toString() ?: return
            MediaTracker.onNotification(this, pkg, notification)
            return
        }
        // Seul le nom de l’application au premier plan est lu (déclencheur « Ouverture d’une
        // application » des routines). Aucun contenu d’écran n’est lu.
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        if (isActivityWindow(pkg, cls)) {
            AutomateController.reportForegroundApp(pkg)
        }
    }

    private val activityWindowCache = HashMap<String, Boolean>()

    /** Ignore clavier, volet de notifications, boîtes de dialogue et nos propres superpositions. */
    private fun isActivityWindow(pkg: String, cls: String): Boolean {
        val key = "$pkg/$cls"
        return activityWindowCache.getOrPut(key) {
            runCatching {
                packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
                true
            }.getOrDefault(false)
        }
    }

    override fun onInterrupt() {
        // Rien à interrompre (aucun retour vocal) : retirer les poignées ici les faisait
        // disparaître jusqu’au prochain changement de réglage.
    }

    /** Écran éteint : le menu d’outils se ferme (il ne doit pas rester sur l’écran verrouillé). */
    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                dismissToolsPanel(animated = false)
                dismissClipboardQuickPanel(animated = false)
            }
        }
    }

    // ---- DeepBluetooth : popup à la connexion / déconnexion d’un appareil ----

    private val btPopup by lazy {
        com.example.modules.deepbluetooth.BluetoothPopup(this, windowManager) { themeColors() }
    }
    private val btLevels = HashMap<String, Int>()
    private val btLastPopup = HashMap<String, Long>()

    private val bluetoothReceiver = object : android.content.BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val device: android.bluetooth.BluetoothDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE, android.bluetooth.BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            val address = device.address ?: return
            val name = runCatching { device.name }.getOrNull()?.trim().orEmpty()
            val config = com.example.modules.deepbluetooth.DeepBluetoothModule.current
            when (action) {
                com.example.modules.automate.BluetoothBatteryNotifier.ACTION_BATTERY_LEVEL_CHANGED -> {
                    val level = intent.getIntExtra(com.example.modules.automate.BluetoothBatteryNotifier.EXTRA_BATTERY_LEVEL, -1)
                    if (level !in 0..100) return
                    btLevels[address] = level
                    btPopup.updateLevel(address, level)
                    if (config.enabled && config.alsoNotification) {
                        com.example.modules.automate.BluetoothBatteryNotifier.postLevel(this@OneHandAccessibilityService, address, name, level)
                    }
                }
                android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    if (!config.enabled || !config.onConnect) return
                    if (!com.example.modules.automate.AutomateConfig.matchesNamedFilter(config.devices, name)) return
                    val now = System.currentTimeMillis()
                    if (now - (btLastPopup[address] ?: 0L) < 10_000L) return
                    btLastPopup[address] = now
                    val level = readBluetoothLevel(device).takeIf { it in 0..100 } ?: -1
                    if (level >= 0) btLevels[address] = level
                    btPopup.show(address, name, btPopup.iconFor(device), connected = true, level = level, config = config)
                }
                android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (config.enabled && config.alsoNotification) {
                        com.example.modules.automate.BluetoothBatteryNotifier.onDisconnected(this@OneHandAccessibilityService, address)
                    }
                    if (!config.enabled || !config.onDisconnect) return
                    if (!com.example.modules.automate.AutomateConfig.matchesNamedFilter(config.devices, name)) return
                    btPopup.show(address, name, btPopup.iconFor(device), connected = false, level = btLevels[address] ?: -1, config = config)
                }
            }
        }
    }

    private suspend fun compatibilityProbe() {
        val state = com.example.modules.compat.Compatibility.live
        if (state.value.running) return
        state.value = com.example.modules.compat.LiveProbe(running = true)
        val capabilities = serviceInfo?.capabilities ?: 0
        val screenshot = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && (capabilities and 0x80) != 0
        val gestures = (capabilities and 0x20) != 0
        val tiles = runCatching { QuickToggles.probeTiles(this) }.getOrDefault(emptyMap())
        kotlinx.coroutines.delay(700L)
        val recents = runCatching { RecentsPopup.probe(this, AppLauncher.label(this, packageName)) }.getOrNull()
        state.value = com.example.modules.compat.LiveProbe(
            done = true, tiles = tiles, recents = recents, screenshot = screenshot, gestures = gestures
        )
    }

    private fun readBluetoothLevel(device: android.bluetooth.BluetoothDevice): Int = runCatching {
        android.bluetooth.BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(device) as Int
    }.getOrDefault(-1)

    /** Aperçu depuis les réglages DeepBluetooth : premier appareil connecté, sinon un exemple. */
    private fun showBluetoothPopupPreview() {
        val config = com.example.modules.deepbluetooth.DeepBluetoothModule.current
        val name = ConnectionNames.connectedBluetoothNames(this).firstOrNull() ?: "Écouteurs"
        val level = btLevels.values.firstOrNull() ?: 80
        btPopup.show("preview", name, androidx.compose.material.icons.Icons.Rounded.Headphones, true, level, config)
    }

    private val navigationObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            syncOverlays()
        }
    }

    private val keepers = HashMap<Class<*>, android.content.ServiceConnection>()

    private fun keepService(serviceClass: Class<*>, keep: Boolean) {
        val existing = keepers[serviceClass]
        if (keep && existing == null) {
            val connection = object : android.content.ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: android.os.IBinder?) {
                    com.example.core.ServiceKeeper.setHeld(serviceClass, true)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    com.example.core.ServiceKeeper.setHeld(serviceClass, false)
                }
            }
            val bound = runCatching { bindService(Intent(this, serviceClass), connection, BIND_AUTO_CREATE) }.getOrDefault(false)
            if (bound) keepers[serviceClass] = connection
        } else if (!keep && existing != null) {
            keepers.remove(serviceClass)
            com.example.core.ServiceKeeper.setHeld(serviceClass, false)
            runCatching { unbindService(existing) }
        }
    }

    override fun onDestroy() {
        keepers.keys.toList().forEach { keepService(it, false) }
        runCatching { contentResolver.unregisterContentObserver(navigationObserver) }
        runCatching { unregisterReceiver(screenOffReceiver) }
        runCatching { unregisterReceiver(bluetoothReceiver) }
        btPopup.dismiss(animated = false)
        mainHandler.removeCallbacksAndMessages(null)
        if (activeInstance === this) {
            activeInstance = null
        }
        dismissAmbientOverlay()
        removeAllOverlays()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun syncOverlays() {
        if (!::windowManager.isInitialized) return

        val shouldShowAny = currentConfig.masterEnabled && !(currentConfig.disableInLandscape && isLandscape)
        if (!shouldShowAny) {
            removeAllOverlays()
            return
        }

        ensureFeedbackOverlay()
        feedbackOverlayView?.colors = themeColors()
        syncSideHandle(HandleSide.LEFT, currentConfig.leftHandle)
        syncSideHandle(HandleSide.RIGHT, currentConfig.rightHandle)
    }

    private fun syncSideHandle(side: HandleSide, handleConfig: HandleConfig) {
        val existingView = if (side == HandleSide.LEFT) leftHandleView else rightHandleView
        if (!handleConfig.enabled) {
            if (existingView != null) {
                removeViewSafely(existingView)
                if (side == HandleSide.LEFT) leftHandleView = null else rightHandleView = null
            }
            return
        }

        val accentColor = resolveAccentColor(handleConfig.colorStyle)
        val screenHeight = currentScreenHeightPx()
        val widthPx = dpToPx(handleConfig.widthDp).toInt().coerceAtLeast(12)
        val clampedPct = handleConfig.heightPercent.coerceIn(0.15f, 1.0f)
        val requestedHeightPx = if (clampedPct >= 0.99f) {
            screenHeight
        } else {
            (screenHeight * clampedPct).toInt().coerceAtLeast(60)
        }
        // La poignée garde la hauteur choisie. En navigation par gestes, Android ne la laisse
        // passer avant le geste Retour que sur 200 dp par bord : cette zone prioritaire est
        // placée par le réglage « Zone prioritaire » (voir applySystemGestureExclusion).
        var isFullSide = clampedPct >= 0.99f
        var heightPx = requestedHeightPx
        var yOffset = if (isFullSide) {
            0
        } else {
            ((screenHeight - heightPx) * handleConfig.verticalPositionPercent)
                .toInt()
                .coerceIn(0, (screenHeight - heightPx).coerceAtLeast(0))
        }
        // Retour latéral d’Android actif : il ne laisse la priorité aux poignées que sur
        // 200 dp. Au-delà, la poignée captait le toucher en même temps que le Retour système
        // (geste « doublé », zone qui réagit mal). La poignée se limite donc à la zone où elle
        // fonctionne vraiment, centrée sur la position choisie ; le reste du bord reste au système.
        val limitPx = dpToPx(OneHandModule.GESTURE_EXCLUSION_LIMIT_DP).toInt()
        if (OneHandModule.hasSideBackGesture(this) && heightPx > limitPx) {
            val center = if (isFullSide) {
                (screenHeight * handleConfig.verticalPositionPercent).toInt()
            } else {
                yOffset + heightPx / 2
            }
            heightPx = limitPx
            yOffset = (center - limitPx / 2).coerceIn(0, (screenHeight - limitPx).coerceAtLeast(0))
            isFullSide = false
        }

        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (side == HandleSide.LEFT) Gravity.START else Gravity.END) or Gravity.TOP
            x = 0
            y = yOffset
        }

        if (existingView == null) {
            val view = EdgeHandleOverlayView(
                context = this,
                side = side,
                onSwipeProgress = { touchYInScreen, gesture ->
                    feedbackOverlayView?.updateGesture(
                        side = side,
                        anchorYScreen = touchYInScreen,
                        gesture = gesture,
                        showLabel = currentConfig.showGestureLabel,
                        showIcon = currentConfig.showGestureIcon
                    )
                },
                onSwipeEnd = { gesture ->
                    feedbackOverlayView?.clearGesture()
                    if (gesture != null && gesture.isReady && gesture.action != GestureAction.NONE) {
                        performHaptic(isLong = gesture.length == SwipeLength.LONG)
                        runToolWithFeedback(gesture.action)
                    }
                },
                onThresholdCrossed = { isLong ->
                    performHaptic(isLong = isLong)
                },
                configProvider = { currentConfig }
            )
            view.updateAppearance(
                color = accentColor,
                opacity = handleConfig.opacity,
                fullSide = isFullSide,
                verticalPositionPercent = handleConfig.verticalPositionPercent
            )
            runCatching { windowManager.addView(view, params) }
            if (side == HandleSide.LEFT) leftHandleView = view else rightHandleView = view
        } else {
            existingView.updateAppearance(
                color = accentColor,
                opacity = handleConfig.opacity,
                fullSide = isFullSide,
                verticalPositionPercent = handleConfig.verticalPositionPercent
            )
            runCatching { windowManager.updateViewLayout(existingView, params) }
        }
    }

    private fun themeColors() = com.example.ui.theme.OverlayTheme.colors(this, currentTheme)

    private fun iconBitmap(vector: androidx.compose.ui.graphics.vector.ImageVector, sizeDp: Float, color: Int) =
        com.example.modules.automate.ModeIconRenderer.renderTinted(vector, dpToPx(sizeDp).toInt(), color)

    /** Fond de carte commun aux panneaux (menu d’outils, presse-papier). */
    private fun panelCard(colors: com.example.ui.theme.OverlayTheme.Colors) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(colors.surfaceContainer)
            cornerRadius = dpToPx(28f)
        }
        elevation = dpToPx(8f)
        clipChildren = false
        isClickable = true
    }

    private fun animatePanelIn(root: View, card: View) {
        root.alpha = 0f
        card.translationY = dpToPx(90f)
        card.scaleX = 0.94f
        card.scaleY = 0.94f
        root.animate().alpha(1f).setDuration(180L).start()
        card.animate().translationY(0f).scaleX(1f).scaleY(1f).setDuration(320L)
            .setInterpolator(android.view.animation.OvershootInterpolator(0.8f)).start()
    }

    private fun animatePanelOut(root: View, card: View?, onEnd: () -> Unit) {
        root.isClickable = false
        root.animate().alpha(0f).setDuration(180L).start()
        if (card == null) {
            onEnd()
            return
        }
        card.animate().translationY(dpToPx(70f)).scaleX(0.95f).scaleY(0.95f).setDuration(180L)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction(onEnd)
            .start()
    }

    private var clipboardCardView: View? = null

    /**
     * Presse-papier récent : même carte, mêmes couleurs (thème choisi) et mêmes animations
     * que le menu d’outils. Un appui copie l’élément et le confirme par une bulle.
     */
    private fun showClipboardQuickPanel() {
        dismissClipboardQuickPanel(animated = false)
        serviceScope.launch {
            val clipConfig = preferences.clipboardConfigFlow.first()
            val entries = clipConfig.entries.sortedWith(
                compareByDescending<ClipboardEntry> { it.pinned }.thenByDescending { it.timestampMs }
            ).take(8)
            val colors = themeColors()
            val pad = dpToPx(16f).toInt()

            val root = LinearLayout(this@OneHandAccessibilityService).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM
                setPadding(pad, dpToPx(48f).toInt(), pad, dpToPx(32f).toInt())
                setOnClickListener { dismissClipboardQuickPanel() }
            }
            val card = panelCard(colors).apply { setPadding(pad, pad, pad, dpToPx(12f).toInt()) }

            val header = LinearLayout(this@OneHandAccessibilityService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dpToPx(12f).toInt())
            }
            header.addView(ImageView(this@OneHandAccessibilityService).apply {
                setImageBitmap(iconBitmap(GestureAction.CLIPBOARD.icon, 20f, colors.onPrimaryContainer))
                scaleType = ImageView.ScaleType.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colors.primaryContainer)
                }
                layoutParams = LinearLayout.LayoutParams(dpToPx(40f).toInt(), dpToPx(40f).toInt()).apply {
                    marginEnd = dpToPx(12f).toInt()
                }
            })
            header.addView(TextView(this@OneHandAccessibilityService).apply {
                text = "Presse-papier"
                setTextColor(colors.onSurface)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            header.addView(TextView(this@OneHandAccessibilityService).apply {
                text = "Ouvrir"
                setTextColor(colors.primary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setPadding(dpToPx(14f).toInt(), dpToPx(8f).toInt(), dpToPx(14f).toInt(), dpToPx(8f).toInt())
                background = GradientDrawable().apply {
                    setStroke(dpToPx(1f).toInt(), colors.outline)
                    cornerRadius = dpToPx(20f)
                }
                setOnClickListener {
                    performHaptic(isLong = false)
                    dismissClipboardQuickPanel()
                    startActivity(
                        Intent(this@OneHandAccessibilityService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                    )
                }
            })
            card.addView(header)

            val list = LinearLayout(this@OneHandAccessibilityService).apply { orientation = LinearLayout.VERTICAL }
            if (entries.isEmpty()) {
                list.addView(TextView(this@OneHandAccessibilityService).apply {
                    text = "Aucune copie mémorisée."
                    setTextColor(colors.onSurfaceVariant)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setPadding(dpToPx(8f).toInt(), dpToPx(12f).toInt(), dpToPx(8f).toInt(), dpToPx(16f).toInt())
                })
            }
            entries.forEach { entry ->
                val itemBackground = GradientDrawable().apply {
                    setColor(colors.surfaceContainerHighest)
                    cornerRadius = dpToPx(18f)
                }
                val row = LinearLayout(this@OneHandAccessibilityService).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dpToPx(14f).toInt(), dpToPx(12f).toInt(), dpToPx(14f).toInt(), dpToPx(12f).toInt())
                    background = itemBackground
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dpToPx(6f).toInt() }
                }
                if (entry.pinned) {
                    row.addView(ImageView(this@OneHandAccessibilityService).apply {
                        setImageBitmap(iconBitmap(androidx.compose.material.icons.Icons.Rounded.PushPin, 16f, colors.primary))
                        setPadding(0, 0, dpToPx(10f).toInt(), 0)
                    })
                }
                row.addView(TextView(this@OneHandAccessibilityService).apply {
                    text = entry.text
                    setTextColor(colors.onSurface)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    maxLines = 3
                    ellipsize = TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                row.setOnClickListener {
                    performHaptic(isLong = false)
                    itemBackground.setColor(colors.primaryContainer)
                    row.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80L).withEndAction {
                        row.animate().scaleX(1f).scaleY(1f).setDuration(140L).start()
                    }.start()
                    ClipboardController.copyToSystemClipboard(this@OneHandAccessibilityService, entry.text)
                    mainHandler.postDelayed({ dismissClipboardQuickPanel() }, 180L)
                    mainHandler.postDelayed({ showFeedbackPill("Copié", GestureAction.CLIPBOARD.icon) }, 260L)
                }
                list.addView(row)
            }
            val scroll = ScrollView(this@OneHandAccessibilityService).apply {
                isVerticalScrollBarEnabled = false
                addView(list)
            }
            card.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            // Hauteur limitée : la liste défile au-delà.
            scroll.layoutParams.height = if (entries.size > 4) dpToPx(300f).toInt() else LinearLayout.LayoutParams.WRAP_CONTENT
            root.addView(card)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            runCatching {
                windowManager.addView(root, params)
                clipboardOverlayView = root
                clipboardCardView = card
                animatePanelIn(root, card)
            }
        }
    }

    private var toolsGrid: ToolGridLayout? = null
    private var toolsEditBar: View? = null
    private var toolsEditing = false
    private var quickTileBusy = false

    /**
     * Menu d’outils façon One Hand Operation + : zone audio puis grille de boutons (ordre,
     * forme, taille, pilules, libellés). Un appui long sur un bouton passe en mode édition :
     * on déplace les boutons par leur poignée et un appui change leur taille.
     */
    private fun showToolsPanel() {
        dismissToolsPanel(animated = false)
        MediaTracker.refresh(this)
        toolsEditing = false
        val toolsConfig = currentConfig.tools
        val colors = themeColors()
        val accent = colors.primary
        val onAccent = colors.onPrimary
        val onSurface = colors.onSurface
        val onSurfaceVariant = colors.onSurfaceVariant
        val pad = dpToPx(16f).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(pad, dpToPx(48f).toInt(), pad, dpToPx(32f).toInt())
            alpha = 0f
            setOnClickListener { if (toolsEditing) setToolsEditing(false) else dismissToolsPanel() }
        }
        val card = panelCard(colors).apply { setPadding(pad, pad, pad, dpToPx(10f).toInt()) }
        val editBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dpToPx(4f).toInt(), 0, 0, dpToPx(12f).toInt())
            addView(TextView(this@OneHandAccessibilityService).apply {
                text = "Glisser : déplacer · Toucher : taille"
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(onSurfaceVariant)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@OneHandAccessibilityService).apply {
                text = "OK"
                setTextColor(onAccent)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setPadding(dpToPx(20f).toInt(), dpToPx(8f).toInt(), dpToPx(20f).toInt(), dpToPx(8f).toInt())
                background = GradientDrawable().apply {
                    setColor(accent)
                    cornerRadius = dpToPx(20f)
                }
                setOnClickListener { setToolsEditing(false) }
            })
        }
        card.addView(editBar)
        if (toolsConfig.showMediaZone) {
            card.addView(buildMediaZone(accent, onSurface, onSurfaceVariant, colors.surfaceContainerHigh))
        }

        val columns = toolsConfig.columns.coerceIn(3, 5)
        val tools = toolsConfig.gridItems().filter {
            !(it is ToolItem.Action && it.action == GestureAction.FLASHLIGHT && !OneHandModule.hasFlashlightHardware(this))
        }
        val screenWidth = resources.displayMetrics.widthPixels
        val cellWidth = (screenWidth - 4 * pad) / columns
        val maxBubble = cellWidth - dpToPx(8f).toInt()
        val bubbleHeight = dpToPx(toolsConfig.iconSize.bubbleDp).toInt().coerceAtMost(maxBubble)
        val bubbleWidth = if (toolsConfig.shape == ToolShape.PILL) {
            (bubbleHeight * 1.45f).toInt().coerceAtMost(maxBubble)
        } else {
            bubbleHeight
        }
        val spPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, resources.displayMetrics)
        val labelSpace = if (toolsConfig.showLabels) (dpToPx(4f) + spPx * 12f * 1.3f).toInt() else 0
        val look = ToolTileView.Look(
            shape = toolsConfig.shape,
            bubbleWidth = bubbleWidth,
            bubbleHeight = bubbleHeight,
            iconPx = dpToPx(toolsConfig.iconSize.iconDp).toInt(),
            accent = accent,
            onAccent = onAccent,
            idle = colors.surfaceContainerHighest,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant,
            showLabels = toolsConfig.showLabels,
            density = resources.displayMetrics.density,
            scaledDensity = spPx
        )
        val grid = ToolGridLayout(this).apply {
            this.columns = columns
            // Rangées serrées : la place du libellé n’existe que là où il y a des libellés.
            compactRowHeight = dpToPx(4f).toInt() + bubbleHeight + dpToPx(6f).toInt()
            rowHeight = compactRowHeight + labelSpace
        }
        val host = object : ToolTileView.Host {
            private var gridX = 0
            private var gridY = 0
            private var startRawX = 0f
            private var startRawY = 0f
            private var startLeft = 0f
            private var startTop = 0f
            private var lastTarget: ToolTileView? = null

            override val editing: Boolean get() = toolsEditing

            override fun onTileTap(tile: ToolTileView, x: Float, y: Float) {
                if (!toolsEditing) {
                    onToolTapped(tile, x, y)
                    return
                }
                // Mode édition : 1 case → pilule horizontale → pilule verticale.
                performHaptic(isLong = false)
                grid.animateMoves = true
                tile.span = tile.span.next()
                tile.scaleX = 0.88f
                tile.scaleY = 0.88f
                tile.animate().scaleX(1f).scaleY(1f).setDuration(280L)
                    .setInterpolator(android.view.animation.OvershootInterpolator(2.2f)).start()
                saveToolsLayout(grid)
            }

            override fun onTileLongPress(tile: ToolTileView) {
                performHaptic(isLong = true)
                setToolsEditing(true)
            }

            override fun onDragStart(tile: ToolTileView, rawX: Float, rawY: Float) {
                val location = IntArray(2)
                grid.getLocationOnScreen(location)
                gridX = location[0]
                gridY = location[1]
                startRawX = rawX
                startRawY = rawY
                tile.animate().cancel()
                startLeft = tile.left + tile.translationX
                startTop = tile.top + tile.translationY
                lastTarget = null
                grid.draggedTile = tile
                tile.animate().scaleX(1.1f).scaleY(1.1f).translationZ(dpToPx(12f)).setDuration(140L).start()
            }

            override fun onDragMove(tile: ToolTileView, rawX: Float, rawY: Float) {
                tile.translationX = startLeft + (rawX - startRawX) - tile.left
                tile.translationY = startTop + (rawY - startRawY) - tile.top
                val target = grid.tileAt(rawX - gridX, rawY - gridY)
                if (target != null && target !== lastTarget) {
                    grid.moveTile(tile, target)
                    performHaptic(isLong = false)
                }
                lastTarget = target
            }

            override fun onDragEnd(tile: ToolTileView) {
                grid.draggedTile = null
                tile.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).translationZ(0f)
                    .setDuration(240L).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                saveToolsLayout(grid)
            }
        }
        tools.forEach { item ->
            val tile = buildToolTile(item, look, host, toolsConfig, colors, bubbleHeight)
            tile.span = toolsConfig.spanOf(item.key)
            val tool = tile.tool
            if (tool != null && tool.isToggle) {
                tile.setActive(QuickToggles.state(this, tool), animated = false)
                tile.detail = QuickToggles.detail(this, tool)
            }
            grid.addTile(tile)
        }
        if (tools.isNotEmpty()) {
            card.addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        root.addView(card)

        // Plein écran, barre d’état comprise : la capture figée doit tout recouvrir.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        runCatching {
            windowManager.addView(root, params)
            toolsOverlayView = root
            toolsCardView = card
            toolsGrid = grid
            toolsEditBar = editBar
            card.translationY = dpToPx(90f)
            card.scaleX = 0.94f
            card.scaleY = 0.94f
            root.animate().alpha(1f).setDuration(180L).start()
            card.animate().translationY(0f).scaleX(1f).scaleY(1f).setDuration(320L)
                .setInterpolator(android.view.animation.OvershootInterpolator(0.8f)).start()
            // Les boutons arrivent en cascade.
            grid.order.forEachIndexed { index, tile ->
                tile.alpha = 0f
                tile.translationY = dpToPx(14f)
                tile.animate().alpha(1f).translationY(0f).setStartDelay(60L + index * 18L).setDuration(220L)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .withEndAction { tile.animate().setStartDelay(0L) }.start()
            }
            // Nom du Wi-Fi inconnu (pas de localisation) : lu sur la tuile une fois le menu posé.
            grid.order.firstOrNull { it.tool == GestureAction.WIFI }?.let { wifiTile ->
                if (QuickToggles.wifiNameMissing(this)) mainHandler.postDelayed({ peekWifiName(wifiTile) }, 850L)
            }
        }.onFailure { android.util.Log.w("Andromate", "Menu d’outils", it) }
    }

    private fun peekWifiName(tile: ToolTileView) {
        if (toolsGrid?.order?.contains(tile) != true || quickTileBusy || toolsEditing) return
        quickTileBusy = true
        tile.setBusy(true)
        freezeScreen {
            serviceScope.launch {
                QuickToggles.peekWifiName(this@OneHandAccessibilityService)
                kotlinx.coroutines.delay(560L)
                unfreezeScreen()
                quickTileBusy = false
                tile.setBusy(false)
                tile.detail = QuickToggles.detail(this@OneHandAccessibilityService, GestureAction.WIFI)
            }
        }
    }

    /**
     * Bouton de la grille pour un outil ou une application. Une appli prend son icône
     * Material You (calque monochrome teinté comme les autres outils) quand elle en a une,
     * sinon son icône d’origine (ou celle du pack d’icônes choisi).
     */
    private fun buildToolTile(
        item: ToolItem,
        look: ToolTileView.Look,
        host: ToolTileView.Host,
        toolsConfig: ToolsMenuConfig,
        colors: com.example.ui.theme.OverlayTheme.Colors,
        bubbleHeight: Int
    ): ToolTileView = when (item) {
        is ToolItem.Action -> ToolTileView(
            this, item, look, host, item.action.label, item.action.shortLabel,
            com.example.modules.automate.ModeIconRenderer.renderTinted(item.action.icon, look.iconPx, look.accent),
            com.example.modules.automate.ModeIconRenderer.renderTinted(item.action.icon, look.iconPx, look.onAccent)
        )
        is ToolItem.App -> {
            val pkg = item.app.packageName
            val label = AppLauncher.label(this, pkg)
            val usePack = toolsConfig.sourceIcon != SourceIconStyle.APP && toolsConfig.sourceIcon != SourceIconStyle.THEMED
            val glyphSize = (bubbleHeight * 0.72f).toInt()
            val idle = if (usePack) null else SourceIcons.themedGlyph(this, pkg, look.accent, glyphSize)
            val on = if (idle == null) null else SourceIcons.themedGlyph(this, pkg, look.onAccent, glyphSize)
            if (idle != null && on != null) {
                ToolTileView(this, item, look, host, label, label, idle, on)
            } else {
                val size = (bubbleHeight * 0.82f).toInt().coerceAtLeast(1)
                val bitmap = SourceIcons.iconFor(this, pkg, toolsConfig.sourceIcon, colors.primary, colors.primaryContainer, size)
                    ?.let { drawable ->
                        android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888).also { bmp ->
                            drawable.setBounds(0, 0, size, size)
                            drawable.draw(Canvas(bmp))
                        }
                    }
                    ?: com.example.modules.automate.ModeIconRenderer.renderTinted(GestureAction.TOOLS_MENU.icon, look.iconPx, look.accent)
                ToolTileView(this, item, look, host, label, label, bitmap, bitmap)
            }
        }
    }

    /**
     * Ouvre une application du menu. Fenêtre réduite : directement si le téléphone gère les
     * fenêtres libres, sinon par l’écran Récents (« Vue fenêtre contextuelle » de Nothing,
     * « vue contextuelle » de Samsung), écran figé pendant l’opération.
     */
    private fun launchApp(packageName: String, popup: Boolean) {
        if (!popup || AppLauncher.hasFreeformWindows(this)) {
            if (AppLauncher.launch(this, packageName, popup) == null) {
                showFeedbackPill("Fenêtre réduite indisponible", GestureAction.TOOLS_MENU.icon, on = false)
            }
            return
        }
        freezeScreen {
            serviceScope.launch {
                val ok = RecentsPopup.open(this@OneHandAccessibilityService, packageName)
                kotlinx.coroutines.delay(350L)
                unfreezeScreen()
                if (!ok) showFeedbackPill("Fenêtre réduite indisponible", GestureAction.TOOLS_MENU.icon, on = false)
            }
        }
    }

    private fun setToolsEditing(on: Boolean) {
        if (toolsEditing == on) return
        toolsEditing = on
        val grid = toolsGrid ?: return
        grid.order.forEach { it.editMode = on }
        toolsEditBar?.let { bar ->
            if (on) {
                bar.visibility = View.VISIBLE
                bar.alpha = 0f
                bar.animate().alpha(1f).setDuration(180L).start()
            } else {
                bar.visibility = View.GONE
            }
        }
        if (!on) saveToolsLayout(grid)
    }

    /** Enregistre l’ordre et la taille des boutons choisis dans le menu. */
    private fun saveToolsLayout(grid: ToolGridLayout) {
        val visible = grid.order.map { it.item.key }
        val spans = grid.order.associate { it.item.key to it.span }
        serviceScope.launch {
            preferences.updateOneHandConfig { config ->
                val tools = config.tools.withVisibleOrder(visible)
                config.copy(tools = tools.copy(spans = tools.spans + spans))
            }
        }
    }

    /** Appui sur un bouton hors édition : interrupteur animé sur place, ou action confirmée. */
    private fun onToolTapped(tile: ToolTileView, x: Float, y: Float) {
        val appItem = tile.item as? ToolItem.App
        if (appItem != null) {
            // Application : confirmation, menu refermé, puis ouverture (plein écran ou fenêtre).
            performHaptic(isLong = false)
            tile.confirm(x, y)
            mainHandler.postDelayed({ dismissToolsPanel() }, 380L)
            mainHandler.postDelayed({ launchApp(appItem.app.packageName, appItem.app.popup) }, 560L)
            return
        }
        val tool = tile.tool ?: return
        performHaptic(isLong = false)
        when {
            tool.usesQuickTile -> {
                if (quickTileBusy || tile.busy) return
                quickTileBusy = true
                tile.setBusy(true)
                freezeScreen { serviceScope.launch {
                    val result = QuickToggles.toggleTile(this@OneHandAccessibilityService, tool)
                    kotlinx.coroutines.delay(560L)
                    unfreezeScreen()
                    quickTileBusy = false
                    tile.setBusy(false)
                    if (result == null) {
                        dismissToolsPanel()
                        QuickToggles.openSettingsFor(this@OneHandAccessibilityService, tool)
                    } else {
                        tile.setActive(result, animated = true, fromX = x, fromY = y)
                        performHaptic(isLong = false)
                        // Le mode avion ou le point d’accès changent souvent d’autres réglages,
                        // et la connexion (réseau, appareil) arrive quelques secondes après.
                        mainHandler.postDelayed({ refreshToggleStates(except = tile) }, 700L)
                        mainHandler.postDelayed({ refreshToggleStates(except = null) }, 3500L)
                    }
                } }
            }
            tool == GestureAction.DND || tool == GestureAction.SILENT || tool == GestureAction.VIBRATE -> {
                val result = QuickToggles.toggleDirect(this, tool)
                if (result == null) {
                    dismissToolsPanel()
                    return
                }
                tile.setActive(result, animated = true, fromX = x, fromY = y)
                refreshToggleStates(except = tile)
            }
            tool.isToggle -> {
                val before = QuickToggles.state(this, tool)
                val ok = OneHandActionExecutor.executeSystemAction(service = this, action = tool)
                if (!ok) {
                    // Autorisation manquante : l’écran de réglage vient de s’ouvrir.
                    dismissToolsPanel()
                    return
                }
                val after = if (tool == GestureAction.CAFFEINE) before != true else QuickToggles.state(this, tool) ?: (before != true)
                tile.setActive(after, animated = true, fromX = x, fromY = y)
            }
            else -> {
                tile.confirm(x, y)
                mainHandler.postDelayed({ dismissToolsPanel() }, 430L)
                // L’action part une fois le panneau refermé (sinon il serait sur la capture).
                mainHandler.postDelayed({ runToolWithFeedback(tool) }, 650L)
            }
        }
    }

    private var freezeWindow: View? = null
    private var freezeHost: View? = null
    private var freezeBitmap: android.graphics.Bitmap? = null

    /**
     * Fige l’écran le temps d’actionner une tuile du volet de réglages : une capture est
     * affichée (fond du menu d’outils, ou fenêtre dédiée après un geste) pendant que le volet
     * s’ouvre et se referme derrière, sans que rien ne bouge à l’écran. Android 11 et plus.
     */
    private fun freezeScreen(onReady: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onReady()
            return
        }
        val started = runCatching {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val bitmap = runCatching {
                            val hardware = android.graphics.Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                            val copy = hardware?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                            result.hardwareBuffer.close()
                            copy
                        }.getOrNull()
                        if (bitmap != null) showFreeze(bitmap)
                        onReady()
                    }

                    override fun onFailure(errorCode: Int) = onReady()
                }
            )
        }.isSuccess
        if (!started) onReady()
    }

    private fun showFreeze(bitmap: android.graphics.Bitmap) {
        unfreezeScreen(animated = false)
        freezeBitmap = bitmap
        val panel = toolsOverlayView
        if (panel != null) {
            panel.background = ScreenCopyDrawable(bitmap, panel)
            freezeHost = panel
            return
        }
        val view = View(this)
        view.background = ScreenCopyDrawable(bitmap, view)
        // Ni opaque ni touchable : sinon Android cache aux services d’accessibilité les fenêtres
        // dessous (Récents, volet) et Andromate ne pourrait plus les actionner.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT // (opaque : Android masquerait aux services ce qui est dessous)
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        runCatching {
            windowManager.addView(view, params)
            freezeWindow = view
            freezeHost = view
        }
    }

    /** Retire la capture figée en fondu, une fois le volet refermé. */
    private fun unfreezeScreen(animated: Boolean = true) {
        val host = freezeHost ?: return
        val window = freezeWindow
        val bitmap = freezeBitmap
        freezeHost = null
        freezeWindow = null
        freezeBitmap = null
        val drawable = host.background as? ScreenCopyDrawable
        val finish: () -> Unit = {
            if (host.background === drawable) host.background = null
            window?.let { removeViewSafely(it) }
            bitmap?.recycle()
        }
        if (!animated || drawable == null) {
            finish()
            return
        }
        android.animation.ValueAnimator.ofInt(255, 0).apply {
            duration = 160L
            addUpdateListener { drawable.alpha = it.animatedValue as Int }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    finish()
                }
            })
            start()
        }
    }

    /** Dessine la capture d’écran à sa place exacte, quelle que soit la position de la vue. */
    private class ScreenCopyDrawable(
        private val bitmap: android.graphics.Bitmap,
        private val host: View
    ) : android.graphics.drawable.Drawable() {
        private val location = IntArray(2)
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        override fun draw(canvas: Canvas) {
            if (bitmap.isRecycled) return
            host.getLocationOnScreen(location)
            canvas.drawBitmap(bitmap, -location[0].toFloat(), -location[1].toFloat(), paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            invalidateSelf()
        }

        override fun getAlpha(): Int = paint.alpha
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun refreshToggleStates(except: ToolTileView?) {
        toolsGrid?.order?.forEach { tile ->
            val tool = tile.tool ?: return@forEach
            if (!tool.isToggle || tile.busy) return@forEach
            tile.detail = QuickToggles.detail(this, tool)
            if (tile === except) return@forEach
            val state = QuickToggles.state(this, tool)
            if (state != null && state != tile.active) tile.setActive(state, animated = true)
        }
    }

    /** Exécute un outil (geste ou menu) puis confirme son effet par une bulle. */
    private fun runToolWithFeedback(tool: GestureAction) {
        if (tool.usesQuickTile) {
            if (quickTileBusy) return
            quickTileBusy = true
            freezeScreen { serviceScope.launch {
                val result = QuickToggles.toggleTile(this@OneHandAccessibilityService, tool)
                kotlinx.coroutines.delay(560L)
                unfreezeScreen()
                quickTileBusy = false
                if (result == null) {
                    QuickToggles.openSettingsFor(this@OneHandAccessibilityService, tool)
                } else {
                    showFeedbackPill(stateLabel(tool, result), tool.icon, result)
                }
            } }
            return
        }
        if (tool == GestureAction.DND || tool == GestureAction.SILENT || tool == GestureAction.VIBRATE) {
            val result = QuickToggles.toggleDirect(this, tool) ?: return
            showFeedbackPill(stateLabel(tool, result), tool.icon, result)
            return
        }
        val before = QuickToggles.state(this, tool)
        OneHandActionExecutor.executeSystemAction(
            service = this,
            action = tool,
            onShowClipboardOverlay = { showClipboardQuickPanel() },
            onShowToolsMenu = { showToolsPanel() }
        )
        mainHandler.postDelayed({
            feedbackText(tool, before)?.let { (text, on) -> showFeedbackPill(text, tool.icon, on) }
        }, 120L)
    }

    private fun stateLabel(tool: GestureAction, on: Boolean): String = when (tool) {
        GestureAction.WIFI -> if (on) "Wi-Fi activé" else "Wi-Fi désactivé"
        GestureAction.BLUETOOTH -> if (on) "Bluetooth activé" else "Bluetooth désactivé"
        GestureAction.MOBILE_DATA -> if (on) "Données mobiles activées" else "Données mobiles coupées"
        GestureAction.HOTSPOT -> if (on) "Point d’accès activé" else "Point d’accès désactivé"
        GestureAction.AIRPLANE -> if (on) "Mode avion activé" else "Mode avion désactivé"
        GestureAction.DND -> if (on) "Ne pas déranger activé" else "Ne pas déranger désactivé"
        GestureAction.SILENT -> if (on) "Mode silencieux" else "Sonnerie rétablie"
        GestureAction.VIBRATE -> if (on) "Mode vibreur" else "Sonnerie rétablie"
        else -> tool.label
    }

    private fun autoRotationOn(): Boolean? = runCatching {
        android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.ACCELEROMETER_ROTATION, 1) == 1
    }.getOrNull()

    private fun feedbackText(tool: GestureAction, before: Boolean?): Pair<String, Boolean>? = when (tool) {
        GestureAction.FLASHLIGHT -> com.example.modules.automate.TorchMonitor.isOn.let {
            (if (it) "Torche allumée" else "Torche éteinte") to it
        }
        GestureAction.CAFFEINE -> if (before == true) "Caféine désactivée" to false else "Caféine activée" to true
        GestureAction.ROTATION -> autoRotationOn()?.takeIf { it != before }?.let { auto ->
            (if (auto) "Rotation auto" else "Rotation verrouillée") to auto
        }
        GestureAction.PREVIOUS_APP -> "Appli précédente" to true
        GestureAction.SPLIT_SCREEN -> "Écran partagé" to true
        else -> null
    }

    /**
     * Bulle de confirmation en bas de l’écran : la pastille s’ouvre, l’icône arrive en tournant
     * dans un rond plein (allumé) ou vide (éteint), le texte glisse, puis tout s’efface.
     */
    private fun showFeedbackPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean = true) {
        feedbackPillView?.let { removeViewSafely(it) }
        val colors = themeColors()
        val accent = colors.primary
        val onAccent = colors.onPrimary
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8f).toInt(), dpToPx(8f).toInt(), dpToPx(20f).toInt(), dpToPx(8f).toInt())
            background = GradientDrawable().apply {
                setColor(colors.surfaceContainerHigh)
                cornerRadius = dpToPx(28f)
            }
            elevation = dpToPx(6f)
        }
        val badgeSize = dpToPx(36f).toInt()
        val badge = ImageView(this).apply {
            setImageBitmap(
                com.example.modules.automate.ModeIconRenderer.renderTinted(icon, dpToPx(20f).toInt(), if (on) onAccent else accent)
            )
            scaleType = ImageView.ScaleType.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (on) setColor(accent) else {
                    setColor(colors.surfaceContainerHighest)
                    setStroke(dpToPx(1.5f).toInt(), accent)
                }
            }
            layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize).apply { marginEnd = dpToPx(12f).toInt() }
        }
        val label = TextView(this).apply {
            this.text = text
            setTextColor(colors.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        pill.addView(badge)
        pill.addView(label)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dpToPx(120f).toInt()
        }
        runCatching {
            windowManager.addView(pill, params)
            feedbackPillView = pill
            pill.alpha = 0f
            pill.scaleX = 0.7f
            pill.scaleY = 0.7f
            pill.translationY = dpToPx(20f)
            pill.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(260L)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.4f)).start()
            badge.scaleX = 0f
            badge.scaleY = 0f
            badge.rotation = -90f
            badge.animate().scaleX(1f).scaleY(1f).rotation(0f).setStartDelay(90L).setDuration(380L)
                .setInterpolator(android.view.animation.OvershootInterpolator(2.6f)).start()
            label.alpha = 0f
            label.translationX = -dpToPx(10f)
            label.animate().alpha(1f).translationX(0f).setStartDelay(150L).setDuration(240L).start()
            mainHandler.postDelayed({
                pill.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).translationY(dpToPx(10f)).setDuration(220L).withEndAction {
                    removeViewSafely(pill)
                    if (feedbackPillView === pill) feedbackPillView = null
                }.start()
            }, 1600L)
        }
    }

    /** Referme le menu d’outils en le faisant redescendre (sans animation à l’arrêt du service). */
    private fun dismissToolsPanel(animated: Boolean = true) {
        val root = toolsOverlayView ?: return
        val card = toolsCardView
        toolsOverlayView = null
        toolsCardView = null
        if (toolsEditing) toolsGrid?.let { saveToolsLayout(it) }
        toolsGrid = null
        toolsEditBar = null
        toolsEditing = false
        detachMediaCallback()
        if (!animated || card == null) {
            removeViewSafely(root)
            return
        }
        root.isClickable = false
        root.animate().alpha(0f).setDuration(180L).start()
        card.animate().translationY(dpToPx(70f)).scaleX(0.95f).scaleY(0.95f).setDuration(180L)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction { removeViewSafely(root) }
            .start()
    }

    private fun detachMediaCallback() {
        val callback = mediaCallback
        val controller = mediaCallbackController
        if (callback != null && controller != null) runCatching { controller.unregisterCallback(callback) }
        mediaCallback = null
        mediaCallbackController = null
    }

    /**
     * Zone audio : icône de l’appli source (touchée, elle ouvre l’appli ; style au choix :
     * d’origine, Material You ou pack d’icônes), titre qui défile s’il est trop long, artiste,
     * puis Précédent / Lecture-pause / Suivant, mis à jour en direct.
     * Sans accès aux notifications, la source reste inconnue : un appui sur le texte l’accorde.
     */
    private fun buildMediaZone(accent: Int, onSurface: Int, onSurfaceVariant: Int, background: Int): View {
        val hasAccess = MediaTracker.hasListenerAccess(this)
        val zone = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(10f).toInt(), dpToPx(10f).toInt(), dpToPx(8f).toInt(), dpToPx(10f).toInt())
            this.background = GradientDrawable().apply {
                setColor(background)
                cornerRadius = dpToPx(22f)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(12f).toInt() }
        }
        val appIconPx = dpToPx(44f).toInt()
        val fallbackIcon = com.example.modules.automate.ModeIconRenderer.renderTinted(
            androidx.compose.material.icons.Icons.Rounded.MusicNote, dpToPx(24f).toInt(), accent
        )
        val iconContainer = themeColors().primaryContainer
        val openSource = {
            performHaptic(isLong = false)
            dismissToolsPanel()
            mainHandler.postDelayed({ SourceIcons.openSource(this) }, 120L)
        }
        val appIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(appIconPx, appIconPx)
            contentDescription = "Ouvrir la source"
            setOnClickListener {
                animate().scaleX(0.82f).scaleY(0.82f).setDuration(80L).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(140L).start()
                }.start()
                openSource()
            }
        }
        val title = TextView(this).apply {
            setTextColor(onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            setHorizontallyScrolling(true)
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(dpToPx(18f).toInt())
            isSelected = true
        }
        val subtitle = TextView(this).apply {
            setTextColor(if (hasAccess) onSurfaceVariant else accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            setHorizontallyScrolling(true)
            isSelected = true
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12f).toInt(), 0, dpToPx(4f).toInt(), 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(title)
            addView(subtitle)
            setOnClickListener {
                if (hasAccess) {
                    openSource()
                } else {
                    dismissToolsPanel()
                    MediaTracker.openListenerSettings(this@OneHandAccessibilityService)
                }
            }
        }
        zone.addView(appIcon)
        zone.addView(texts)

        val buttonPx = dpToPx(46f).toInt()
        val iconPx = dpToPx(26f).toInt()
        fun icon(vector: androidx.compose.ui.graphics.vector.ImageVector) =
            com.example.modules.automate.ModeIconRenderer.renderTinted(vector, iconPx, accent)
        val playIcon = icon(androidx.compose.material.icons.Icons.Rounded.PlayArrow)
        val pauseIcon = icon(androidx.compose.material.icons.Icons.Rounded.Pause)
        lateinit var playPause: ImageView
        var shownPackage: String? = "?"

        fun refreshViews() {
            val pkg = MediaTracker.lastPackage(this)
            if (pkg != shownPackage) {
                shownPackage = pkg
                val drawable = pkg?.let {
                    SourceIcons.iconFor(this, it, currentConfig.tools.sourceIcon, accent, iconContainer, appIconPx)
                }
                if (drawable != null) {
                    appIcon.scaleType = ImageView.ScaleType.FIT_CENTER
                    appIcon.setImageDrawable(drawable)
                } else {
                    appIcon.scaleType = ImageView.ScaleType.CENTER
                    appIcon.setImageBitmap(fallbackIcon)
                }
            }
            val newTitle = MediaTracker.currentTitle() ?: "Audio"
            if (title.text?.toString() != newTitle) title.text = newTitle
            val newSubtitle = if (!hasAccess) {
                "Voir la source"
            } else {
                MediaTracker.currentArtist() ?: if (MediaTracker.currentTitle() == null) "Rien en lecture" else ""
            }
            if (subtitle.text?.toString() != newSubtitle) subtitle.text = newSubtitle
            subtitle.visibility = if (newSubtitle.isEmpty()) View.GONE else View.VISIBLE
            playPause.setImageBitmap(if (MediaTracker.isPlaying()) pauseIcon else playIcon)
        }

        fun mediaButton(bitmap: android.graphics.Bitmap, description: String, onTap: () -> Unit) =
            ImageView(this).apply {
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.CENTER
                contentDescription = description
                layoutParams = LinearLayout.LayoutParams(buttonPx, buttonPx)
                this.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(themeColors().surfaceContainerHighest)
                }
                setOnClickListener {
                    performHaptic(isLong = false)
                    animate().scaleX(0.8f).scaleY(0.8f).setDuration(80L).withEndAction {
                        animate().scaleX(1f).scaleY(1f).setDuration(150L)
                            .setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
                    }.start()
                    onTap()
                    // Si la session ne prévient pas, on relit l’état un instant après.
                    postDelayed({ MediaTracker.refresh(this@OneHandAccessibilityService); refreshViews() }, 450L)
                }
            }
        playPause = mediaButton(playIcon, "Lecture / pause") { MediaTracker.playPause(this) }
        zone.addView(mediaButton(icon(GestureAction.PREVIOUS_TRACK.icon), "Précédent") { MediaTracker.previous(this) })
        zone.addView(playPause)
        zone.addView(mediaButton(icon(GestureAction.NEXT_TRACK.icon), "Suivant") { MediaTracker.next(this) })
        refreshViews()

        // Mise à jour en direct du titre et de l’état tant que le panneau est ouvert.
        MediaTracker.activeController()?.let { controller ->
            val callback = object : android.media.session.MediaController.Callback() {
                override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = refreshViews()
                override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) = refreshViews()
            }
            runCatching { controller.registerCallback(callback, mainHandler) }
            mediaCallback = callback
            mediaCallbackController = controller
        }
        return zone
    }

    private fun dismissClipboardQuickPanel(animated: Boolean = true) {
        val root = clipboardOverlayView ?: return
        val card = clipboardCardView
        clipboardOverlayView = null
        clipboardCardView = null
        if (!animated) {
            removeViewSafely(root)
            return
        }
        animatePanelOut(root, card) { removeViewSafely(root) }
    }

    private fun ensureFeedbackOverlay() {
        if (feedbackOverlayView != null) return
        val view = GestureFeedbackOverlayView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        runCatching {
            windowManager.addView(view, params)
            feedbackOverlayView = view
        }
    }

    private fun showAmbientOverlayInternal(routineName: String) {
        dismissAmbientOverlayInternal()
        // Écran noir (économie d’énergie), textes aux couleurs du thème choisi.
        val ambientColors = com.example.ui.theme.OverlayTheme.colors(
            this, currentTheme.copy(themeMode = com.example.ui.theme.ThemeMode.DARK)
        )
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(dpToPx(24f).toInt(), dpToPx(48f).toInt(), dpToPx(24f).toInt(), dpToPx(48f).toInt())
            setOnClickListener { dismissAmbientOverlayInternal() }
        }

        val clock = TextClock(this).apply {
            format12Hour = "HH:mm"
            format24Hour = "HH:mm"
            setTextColor(ambientColors.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 68f)
            gravity = Gravity.CENTER
        }
        val dateText = TextClock(this).apply {
            format12Hour = "EEEE d MMMM"
            format24Hour = "EEEE d MMMM"
            setTextColor(ambientColors.primary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(8f).toInt(), 0, dpToPx(20f).toInt())
        }
        val routineLabel = TextView(this).apply {
            text = "Always-On • $routineName"
            setTextColor(ambientColors.onSurfaceVariant)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
        }
        val hint = TextView(this).apply {
            text = "Touchez l'écran pour quitter"
            setTextColor(ambientColors.outline)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(28f).toInt(), 0, 0)
        }

        container.addView(clock)
        container.addView(dateText)
        container.addView(routineLabel)
        container.addView(hint)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT // (opaque : Android masquerait aux services ce qui est dessous)
        )
        runCatching {
            windowManager.addView(container, params)
            ambientOverlayView = container
        }
    }

    private fun dismissAmbientOverlayInternal() {
        ambientOverlayView?.let { removeViewSafely(it) }
        ambientOverlayView = null
    }

    private fun removeAllOverlays() {
        leftHandleView?.let { removeViewSafely(it) }
        rightHandleView?.let { removeViewSafely(it) }
        feedbackOverlayView?.let { removeViewSafely(it) }
        dismissClipboardQuickPanel(animated = false)
        dismissToolsPanel(animated = false)
        feedbackPillView?.let { removeViewSafely(it) }
        feedbackPillView = null
        dismissAmbientOverlayInternal()
        leftHandleView = null
        rightHandleView = null
        feedbackOverlayView = null
    }

    private fun removeViewSafely(view: View) {
        runCatching { windowManager.removeViewImmediate(view) }
    }

    private fun resolveAccentColor(style: HandleColorStyle): Int {
        if (style == HandleColorStyle.MINIMAL_NEUTRAL) {
            return Color.argb(255, 160, 174, 192)
        }
        if (currentTheme.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val resId = when (style) {
                HandleColorStyle.DYNAMIC_PRIMARY -> android.R.color.system_accent1_500
                HandleColorStyle.DYNAMIC_SECONDARY -> android.R.color.system_accent2_500
                HandleColorStyle.DYNAMIC_TERTIARY -> android.R.color.system_accent3_500
                HandleColorStyle.MINIMAL_NEUTRAL -> android.R.color.system_neutral1_400
            }
            return ContextCompat.getColor(this, resId)
        }

        val scheme = com.example.ui.theme.OverlayTheme.scheme(this, currentTheme)
        return when (style) {
            HandleColorStyle.DYNAMIC_PRIMARY -> scheme.primary.toArgb()
            HandleColorStyle.DYNAMIC_SECONDARY -> scheme.secondary.toArgb()
            HandleColorStyle.DYNAMIC_TERTIARY -> scheme.tertiary.toArgb()
            HandleColorStyle.MINIMAL_NEUTRAL -> scheme.outline.toArgb()
        }
    }

    private fun performHaptic(isLong: Boolean) {
        if (!currentConfig.hapticFeedbackEnabled) return
        val durationMs = if (isLong) 34L else 18L
        val amplitude = if (isLong) 210 else 130

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun currentScreenHeightPx(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.height()
        } else {
            resources.displayMetrics.heightPixels
        }
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        )
    }

    @SuppressLint("ViewConstructor")
    private class EdgeHandleOverlayView(
        context: Context,
        private val side: HandleSide,
        private val onSwipeProgress: (Float, EvaluatedGesture) -> Unit,
        private val onSwipeEnd: (EvaluatedGesture?) -> Unit,
        private val onThresholdCrossed: (Boolean) -> Unit,
        private val configProvider: () -> OneHandConfig
    ) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val rect = RectF()
        private val exclusionRect = Rect()

        private var isFullSide = false
        private var verticalPosPct = 0.62f
        private var startRawX = 0f
        private var startRawY = 0f
        private var lastEvaluated: EvaluatedGesture? = null
        private var wasReady = false
        private var wasLong = false

        fun updateAppearance(
            color: Int,
            opacity: Float,
            fullSide: Boolean,
            verticalPositionPercent: Float
        ) {
            isFullSide = fullSide
            verticalPosPct = verticalPositionPercent
            val alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
            paint.color = Color.argb(
                alpha,
                Color.red(color),
                Color.green(color),
                Color.blue(color)
            )
            invalidate()
            post { applySystemGestureExclusion() }
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            applySystemGestureExclusion()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            applySystemGestureExclusion()
        }

        private fun applySystemGestureExclusion() {
            if (width <= 0 || height <= 0) return
            val maxBudgetPx = (200f * resources.displayMetrics.density).toInt().coerceAtLeast(1)
            if (height <= maxBudgetPx) {
                exclusionRect.set(0, 0, width, height)
            } else {
                val centerY = if (isFullSide) {
                    (height * verticalPosPct).toInt()
                } else {
                    height / 2
                }
                val top = (centerY - maxBudgetPx / 2).coerceIn(0, (height - maxBudgetPx).coerceAtLeast(0))
                exclusionRect.set(0, top, width, (top + maxBudgetPx).coerceAtMost(height))
            }
            ViewCompat.setSystemGestureExclusionRects(this, listOf(exclusionRect))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            applySystemGestureExclusion()
            val density = resources.displayMetrics.density
            val pillWidth = (5.5f * density).coerceIn(3f, width * 0.30f)
            val verticalInset = if (isFullSide) 0f else height * 0.06f
            val cornerRadius = if (isFullSide) 0f else pillWidth / 2f

            if (side == HandleSide.LEFT) {
                rect.set(0f, verticalInset, pillWidth, height - verticalInset)
            } else {
                rect.set(width - pillWidth, verticalInset, width.toFloat(), height - verticalInset)
            }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val config = configProvider()
            val handleConfig = config.handleFor(side)
            val density = resources.displayMetrics.density

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    startRawX = event.rawX
                    startRawY = event.rawY
                    wasReady = false
                    wasLong = false
                    lastEvaluated = null
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startRawX
                    val dy = event.rawY - startRawY
                    val evaluated = GestureDetectorMath.evaluate(
                        side = side,
                        dxPx = dx,
                        dyPx = dy,
                        triggerDistancePx = config.triggerDistanceDp * density,
                        longTriggerDistancePx = config.longTriggerDistanceDp * density,
                        longSwipeEnabled = config.longSwipeEnabled,
                        handleConfig = handleConfig
                    )
                    lastEvaluated = evaluated

                    if (evaluated.isReady && !wasReady && evaluated.action != GestureAction.NONE) {
                        wasReady = true
                        onThresholdCrossed(false)
                    }
                    if (evaluated.length == SwipeLength.LONG && !wasLong && evaluated.action != GestureAction.NONE) {
                        wasLong = true
                        onThresholdCrossed(true)
                    } else if (evaluated.length == SwipeLength.SHORT && wasLong) {
                        wasLong = false
                    }

                    // Comme la flèche Retour d’Android, le retour visuel suit le doigt.
                    onSwipeProgress(event.rawY, evaluated)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val result = if (event.actionMasked == MotionEvent.ACTION_UP) lastEvaluated else null
                    onSwipeEnd(result)
                    lastEvaluated = null
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    /**
     * Retour visuel des gestes, au style du geste Retour d’Android : une capsule aux couleurs
     * du thème sort du bord à hauteur du doigt et s’allonge avec le glissement. Elle porte un
     * chevron orienté, puis l’icône de l’action quand le geste est validé (petit rebond) ;
     * en glissement long, elle se remplit de la couleur principale.
     */
    private class GestureFeedbackOverlayView(context: Context) : View(context) {

        private var activeSide: HandleSide? = null
        private var anchorY: Float = 0f
        private var currentGesture: EvaluatedGesture? = null
        private var showLabel: Boolean = false
        private var showIcon: Boolean = true
        var colors: com.example.ui.theme.OverlayTheme.Colors? = null

        private val density = resources.displayMetrics.density
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val chevronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val chevron = Path()
        private val iconCache = HashMap<String, android.graphics.Bitmap>()

        private var smoothWidth = 0f
        private var wasReady = false
        private var pop = 0f
        private var popAnimator: android.animation.ValueAnimator? = null

        init {
            visibility = GONE
        }

        fun updateGesture(side: HandleSide, anchorYScreen: Float, gesture: EvaluatedGesture, showLabel: Boolean, showIcon: Boolean) {
            activeSide = side
            anchorY = anchorYScreen
            currentGesture = gesture
            this.showLabel = showLabel
            this.showIcon = showIcon
            val ready = gesture.isReady && gesture.action != GestureAction.NONE
            if (ready && !wasReady) {
                popAnimator?.cancel()
                popAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 320L
                    addUpdateListener {
                        pop = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            }
            wasReady = ready
            visibility = if (gesture.progress > 0.05f) VISIBLE else GONE
            invalidate()
        }

        fun clearGesture() {
            activeSide = null
            currentGesture = null
            wasReady = false
            smoothWidth = 0f
            popAnimator?.cancel()
            visibility = GONE
            invalidate()
        }

        private fun icon(action: GestureAction, sizePx: Int, color: Int): android.graphics.Bitmap =
            iconCache.getOrPut("${action.name}:$sizePx:$color") {
                com.example.modules.automate.ModeIconRenderer.renderTinted(action.icon, sizePx, color)
            }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val side = activeSide ?: return
            val gesture = currentGesture ?: return
            val c = colors ?: return
            val d = density
            val progress = gesture.progress.coerceIn(0f, 1.5f)
            val isLong = gesture.length == SwipeLength.LONG
            val ready = gesture.isReady && gesture.action != GestureAction.NONE

            // Capsule : s’allonge avec le doigt (comme le Retour système), un peu plus en long.
            val height = 44f * d
            val target = (12f * d + 44f * d * progress.coerceAtMost(1f)) + if (isLong) 14f * d else 0f
            smoothWidth = if (smoothWidth == 0f) target else smoothWidth + (target - smoothWidth) * 0.45f
            val popScale = 1f + 0.12f * kotlin.math.sin(Math.PI * pop).toFloat()
            val w = smoothWidth * popScale
            val h = height * popScale
            val margin = 6f * d * progress.coerceAtMost(1f)
            val cy = anchorY.coerceIn(h, (this.height - h).coerceAtLeast(h))
            if (side == HandleSide.LEFT) rect.set(margin, cy - h / 2f, margin + w, cy + h / 2f)
            else rect.set(width - margin - w, cy - h / 2f, width - margin, cy + h / 2f)

            // Couleurs de la flèche Retour d’Android : fond « secondaire », flèche contrastée.
            val filled = ready && isLong
            fillPaint.color = if (filled) c.primary else c.secondaryContainer
            fillPaint.alpha = if (ready) 255 else (170 + 85 * progress.coerceAtMost(1f)).toInt().coerceAtMost(250)
            shadowPaint.color = Color.argb(if (c.dark) 90 else 40, 0, 0, 0)
            canvas.drawRoundRect(rect.left, rect.top + 2f * d, rect.right, rect.bottom + 2f * d, h / 2f, h / 2f, shadowPaint)
            canvas.drawRoundRect(rect, h / 2f, h / 2f, fillPaint)

            val contentColor = if (filled) c.onPrimary else c.onSecondaryContainer
            val cx = rect.centerX()
            if (showIcon && ready && rect.width() >= 30f * d) {
                // Geste validé : icône de l’action.
                val size = (22f * d * popScale).toInt()
                val bmp = icon(gesture.action, (22f * d).toInt(), contentColor)
                bitmapPaint.alpha = 255
                canvas.drawBitmap(bmp, null, RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f), bitmapPaint)
            } else {
                // Chevron « < » tourné vers le bord comme la flèche Retour d’Android,
                // incliné vers le haut ou le bas pour les gestes en diagonale.
                val angle = when (side) {
                    HandleSide.LEFT -> when (gesture.direction) {
                        SwipeDirection.HORIZONTAL -> 180f
                        SwipeDirection.DIAGONAL_UP -> -135f
                        SwipeDirection.DIAGONAL_DOWN -> 135f
                    }
                    HandleSide.RIGHT -> when (gesture.direction) {
                        SwipeDirection.HORIZONTAL -> 0f
                        SwipeDirection.DIAGONAL_UP -> -45f
                        SwipeDirection.DIAGONAL_DOWN -> 45f
                    }
                }
                // Chevron qui s’étire avec le glissement, comme la flèche Retour système.
                val s = (5f + 3.5f * progress.coerceAtMost(1f)) * d
                chevronPaint.strokeWidth = (2.4f + 0.6f * progress.coerceAtMost(1f)) * d
                chevronPaint.color = contentColor
                chevron.reset()
                chevron.moveTo(-s * 0.5f, -s)
                chevron.lineTo(s * 0.5f, 0f)
                chevron.lineTo(-s * 0.5f, s)
                canvas.save()
                canvas.translate(cx, cy)
                canvas.rotate(angle)
                canvas.drawPath(chevron, chevronPaint)
                canvas.restore()
            }

            if (showLabel && ready) {
                val label = gesture.action.shortLabel
                labelPaint.textSize = 13f * d
                labelPaint.color = c.onSurface
                val lw = labelPaint.measureText(label) + 28f * d
                val lh = 34f * d
                val lx = if (side == HandleSide.LEFT) rect.right + 10f * d else rect.left - 10f * d - lw
                labelBgPaint.color = c.surfaceContainerHigh
                canvas.drawRoundRect(lx, cy - lh / 2f, lx + lw, cy + lh / 2f, lh / 2f, lh / 2f, labelBgPaint)
                canvas.drawText(label, lx + lw / 2f, cy - (labelPaint.descent() + labelPaint.ascent()) / 2f, labelPaint)
            }
        }
    }

    companion object {
        @Volatile
        private var activeInstance: OneHandAccessibilityService? = null

        fun showAmbientOverlay(routineName: String): Boolean {
            val instance = activeInstance ?: return false
            instance.serviceScope.launch {
                instance.showAmbientOverlayInternal(routineName)
            }
            return true
        }

        /** Tests en direct de la page Compatibilité (volet, Récents). */
        fun runCompatibilityProbe(): Boolean {
            val service = activeInstance ?: return false
            service.serviceScope.launch { service.compatibilityProbe() }
            return true
        }

        /** Aperçu de la popup DeepBluetooth (service d’accessibilité requis). */
        fun previewBluetoothPopup(): Boolean {
            val service = activeInstance ?: return false
            service.mainHandler.post { service.showBluetoothPopupPreview() }
            return true
        }

        fun dismissAmbientOverlay(): Boolean {
            val instance = activeInstance ?: return false
            instance.serviceScope.launch {
                instance.dismissAmbientOverlayInternal()
            }
            return true
        }
    }
}
