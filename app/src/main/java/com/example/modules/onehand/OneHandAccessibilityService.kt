package com.example.modules.onehand

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
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
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
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
import kotlinx.coroutines.flow.first
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
    private var ambientOverlayView: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        preferences = ModuYouPreferences.getInstance(this)
        isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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
            preferences.automateConfigFlow.collect { automateConfig ->
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
        // Aucun contenu d'écran n'est lu : service uniquement dédié aux gestes latéraux.
    }

    override fun onInterrupt() {
        removeAllOverlays()
    }

    override fun onDestroy() {
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
        val isFullSide = clampedPct >= 0.99f
        val heightPx = if (isFullSide) {
            screenHeight
        } else {
            (screenHeight * clampedPct).toInt().coerceAtLeast(60)
        }
        val yOffset = if (isFullSide) {
            0
        } else {
            ((screenHeight - heightPx) * handleConfig.verticalPositionPercent)
                .toInt()
                .coerceIn(0, (screenHeight - heightPx).coerceAtLeast(0))
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
                        accentColor = accentColor,
                        showLabel = currentConfig.showGestureLabel
                    )
                },
                onSwipeEnd = { gesture ->
                    feedbackOverlayView?.clearGesture()
                    if (gesture != null && gesture.isReady && gesture.action != GestureAction.NONE) {
                        performHaptic(isLong = gesture.length == SwipeLength.LONG)
                        OneHandActionExecutor.executeSystemAction(
                            service = this,
                            action = gesture.action,
                            onShowClipboardOverlay = { showClipboardQuickPanel() }
                        )
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

    private fun showClipboardQuickPanel() {
        dismissClipboardQuickPanel()
        serviceScope.launch {
            val clipConfig = preferences.clipboardConfigFlow.first()
            val entries = clipConfig.entries.sortedWith(
                compareByDescending<ClipboardEntry> { it.pinned }.thenByDescending { it.timestampMs }
            ).take(8)

            val scheme = paletteColorScheme(currentTheme.fallbackPalette, darkTheme = true)
            val cardBg = scheme.surfaceContainerHigh.toArgb()
            val itemBg = scheme.surfaceContainerHighest.toArgb()
            val primaryColor = scheme.primary.toArgb()
            val mintColor = scheme.secondary.toArgb()
            val onSurface = scheme.onSurface.toArgb()
            val onSurfaceVar = scheme.onSurfaceVariant.toArgb()

            val root = LinearLayout(this@OneHandAccessibilityService).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.argb(145, 7, 11, 20))
                gravity = Gravity.BOTTOM
                setPadding(dpToPx(16f).toInt(), dpToPx(48f).toInt(), dpToPx(16f).toInt(), dpToPx(32f).toInt())
                setOnClickListener { dismissClipboardQuickPanel() }
            }

            val sheet = LinearLayout(this@OneHandAccessibilityService).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(18f).toInt(), dpToPx(18f).toInt(), dpToPx(18f).toInt(), dpToPx(18f).toInt())
                background = GradientDrawable().apply {
                    setColor(cardBg)
                    cornerRadius = dpToPx(24f)
                }
                setOnClickListener { /* Consume clicks inside card */ }
            }

            val headerRow = LinearLayout(this@OneHandAccessibilityService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val titleView = TextView(this@OneHandAccessibilityService).apply {
                text = "Presse-papier récent"
                setTextColor(onSurface)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                paint.isFakeBoldText = true
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val openAppBtn = TextView(this@OneHandAccessibilityService).apply {
                text = "Ouvrir"
                setTextColor(mintColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dpToPx(12f).toInt(), dpToPx(6f).toInt(), dpToPx(12f).toInt(), dpToPx(6f).toInt())
                setOnClickListener {
                    dismissClipboardQuickPanel()
                    startActivity(
                        Intent(this@OneHandAccessibilityService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                    )
                }
            }
            val closeBtn = TextView(this@OneHandAccessibilityService).apply {
                text = "Fermer"
                setTextColor(primaryColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dpToPx(10f).toInt(), dpToPx(6f).toInt(), dpToPx(4f).toInt(), dpToPx(6f).toInt())
                setOnClickListener { dismissClipboardQuickPanel() }
            }
            headerRow.addView(titleView)
            headerRow.addView(openAppBtn)
            headerRow.addView(closeBtn)
            sheet.addView(headerRow)

            val scroll = ScrollView(this@OneHandAccessibilityService).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(280f).toInt()
                ).apply {
                    topMargin = dpToPx(12f).toInt()
                }
            }
            val listContainer = LinearLayout(this@OneHandAccessibilityService).apply {
                orientation = LinearLayout.VERTICAL
            }

            if (entries.isEmpty()) {
                val emptyText = TextView(this@OneHandAccessibilityService).apply {
                    text = "Aucun élément mémorisé pour le moment. Ouvrez Andromate → Presse-papier pour capturer vos copies."
                    setTextColor(onSurfaceVar)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setPadding(dpToPx(8f).toInt(), dpToPx(16f).toInt(), dpToPx(8f).toInt(), dpToPx(16f).toInt())
                }
                listContainer.addView(emptyText)
            } else {
                entries.forEach { entry ->
                    val itemCard = TextView(this@OneHandAccessibilityService).apply {
                        text = if (entry.pinned) "★ ${entry.text}" else entry.text
                        setTextColor(onSurface)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        maxLines = 3
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(dpToPx(14f).toInt(), dpToPx(12f).toInt(), dpToPx(14f).toInt(), dpToPx(12f).toInt())
                        background = GradientDrawable().apply {
                            setColor(itemBg)
                            cornerRadius = dpToPx(14f)
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            bottomMargin = dpToPx(8f).toInt()
                        }
                        setOnClickListener {
                            ClipboardController.copyToSystemClipboard(this@OneHandAccessibilityService, entry.text)
                            Toast.makeText(this@OneHandAccessibilityService, "Élément copié", Toast.LENGTH_SHORT).show()
                            dismissClipboardQuickPanel()
                        }
                    }
                    listContainer.addView(itemCard)
                }
            }

            scroll.addView(listContainer)
            sheet.addView(scroll)
            root.addView(sheet)

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
            }
        }
    }

    private fun dismissClipboardQuickPanel() {
        clipboardOverlayView?.let { removeViewSafely(it) }
        clipboardOverlayView = null
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
            setTextColor(Color.rgb(214, 226, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 68f)
            gravity = Gravity.CENTER
        }
        val dateText = TextClock(this).apply {
            format12Hour = "EEEE d MMMM"
            format24Hour = "EEEE d MMMM"
            setTextColor(Color.rgb(118, 224, 184))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(8f).toInt(), 0, dpToPx(20f).toInt())
        }
        val routineLabel = TextView(this).apply {
            text = "Always-On • $routineName"
            setTextColor(Color.argb(180, 180, 195, 220))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
        }
        val hint = TextView(this).apply {
            text = "Touchez l'écran pour quitter"
            setTextColor(Color.argb(120, 180, 195, 220))
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
            PixelFormat.OPAQUE
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
        dismissClipboardQuickPanel()
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

        val scheme = paletteColorScheme(currentTheme.fallbackPalette, darkTheme = false)
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

                    onSwipeProgress(startRawY, evaluated)
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

    private class GestureFeedbackOverlayView(context: Context) : View(context) {

        private var activeSide: HandleSide? = null
        private var anchorY: Float = 0f
        private var currentGesture: EvaluatedGesture? = null
        private var accentColor: Int = Color.BLUE
        private var showLabel: Boolean = false

        private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        private val path = Path()
        private val arrowPath = Path()

        init {
            visibility = GONE
        }

        fun updateGesture(
            side: HandleSide,
            anchorYScreen: Float,
            gesture: EvaluatedGesture,
            accentColor: Int,
            showLabel: Boolean
        ) {
            this.activeSide = side
            this.anchorY = anchorYScreen
            this.currentGesture = gesture
            this.accentColor = accentColor
            this.showLabel = showLabel
            visibility = if (gesture.progress > 0.08f) VISIBLE else GONE
            invalidate()
        }

        fun clearGesture() {
            activeSide = null
            currentGesture = null
            visibility = GONE
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val side = activeSide ?: return
            val gesture = currentGesture ?: return
            val density = resources.displayMetrics.density

            val pullFactor = gesture.progress.coerceIn(0.15f, 1.22f)
            val maxBulge = 42f * density * pullFactor
            val halfSpan = 46f * density

            val directionOffsetY = when (gesture.direction) {
                SwipeDirection.HORIZONTAL -> 0f
                SwipeDirection.DIAGONAL_UP -> -16f * density
                SwipeDirection.DIAGONAL_DOWN -> 16f * density
            }
            val centerY = (anchorY + directionOffsetY).coerceIn(
                halfSpan + 12f * density,
                (height - halfSpan - 12f * density).coerceAtLeast(halfSpan + 12f * density)
            )

            val isLong = gesture.length == SwipeLength.LONG
            val isReady = gesture.isReady && gesture.action != GestureAction.NONE

            bubblePaint.color = when {
                isReady && isLong -> Color.argb(230, 16, 28, 54)
                isReady -> Color.argb(
                    214,
                    Color.red(accentColor),
                    Color.green(accentColor),
                    Color.blue(accentColor)
                )
                else -> Color.argb(150, 20, 26, 40)
            }

            borderPaint.strokeWidth = (if (isLong) 1.8f else 1.1f) * density
            borderPaint.color = when {
                isReady && isLong -> Color.argb(220, 118, 224, 184)
                isReady -> Color.argb(110, 255, 255, 255)
                else -> Color.argb(130, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            }

            path.reset()
            if (side == HandleSide.LEFT) {
                path.moveTo(0f, centerY - halfSpan)
                path.cubicTo(
                    0f, centerY - halfSpan * 0.48f,
                    maxBulge, centerY - halfSpan * 0.38f,
                    maxBulge, centerY
                )
                path.cubicTo(
                    maxBulge, centerY + halfSpan * 0.38f,
                    0f, centerY + halfSpan * 0.48f,
                    0f, centerY + halfSpan
                )
                path.close()
            } else {
                val right = width.toFloat()
                path.moveTo(right, centerY - halfSpan)
                path.cubicTo(
                    right, centerY - halfSpan * 0.48f,
                    right - maxBulge, centerY - halfSpan * 0.38f,
                    right - maxBulge, centerY
                )
                path.cubicTo(
                    right - maxBulge, centerY + halfSpan * 0.38f,
                    right, centerY + halfSpan * 0.48f,
                    right, centerY + halfSpan
                )
                path.close()
            }
            canvas.drawPath(path, bubblePaint)
            canvas.drawPath(path, borderPaint)

            // Crisp rotated directional arrow inside the native-style pill
            if (maxBulge >= 14f * density) {
                val arrowCenterX = if (side == HandleSide.LEFT) {
                    (maxBulge * 0.54f).coerceAtLeast(11f * density)
                } else {
                    (width - maxBulge * 0.54f).coerceAtMost(width - 11f * density)
                }
                val arrowScale = (0.72f + 0.28f * gesture.progress.coerceIn(0f, 1f)) *
                    (if (isLong) 1.12f else 1.0f)
                val halfLen = 6.2f * density * arrowScale
                val wing = 4.6f * density * arrowScale

                val angleDeg = when (side) {
                    HandleSide.LEFT -> when (gesture.direction) {
                        SwipeDirection.HORIZONTAL -> 0f
                        SwipeDirection.DIAGONAL_UP -> -45f
                        SwipeDirection.DIAGONAL_DOWN -> 45f
                    }
                    HandleSide.RIGHT -> when (gesture.direction) {
                        SwipeDirection.HORIZONTAL -> 180f
                        SwipeDirection.DIAGONAL_UP -> -135f
                        SwipeDirection.DIAGONAL_DOWN -> 135f
                    }
                }

                arrowPaint.strokeWidth = (if (isReady) 2.5f else 2.0f) * density
                arrowPaint.color = if (isReady) {
                    Color.WHITE
                } else {
                    Color.argb(200, 235, 240, 250)
                }

                arrowPath.reset()
                arrowPath.moveTo(-halfLen, 0f)
                arrowPath.lineTo(halfLen, 0f)
                arrowPath.moveTo(halfLen - wing, -wing)
                arrowPath.lineTo(halfLen, 0f)
                arrowPath.lineTo(halfLen - wing, wing)

                canvas.save()
                canvas.translate(arrowCenterX, centerY)
                canvas.rotate(angleDeg)
                canvas.drawPath(arrowPath, arrowPaint)
                canvas.restore()
            }

            if (showLabel && isReady) {
                val label = if (isLong) "• ${gesture.action.shortLabel}" else gesture.action.shortLabel
                textPaint.textSize = 12f * density
                val measuredWidth = textPaint.measureText(label)
                val pillWidth = (measuredWidth + 24f * density).coerceAtLeast(78f * density)
                val pillHeight = 32f * density
                val pillX = if (side == HandleSide.LEFT) {
                    (maxBulge + 12f * density).coerceAtMost(width - pillWidth - 16f * density)
                } else {
                    (width - maxBulge - pillWidth - 12f * density).coerceAtLeast(16f * density)
                }
                val pillRect = RectF(
                    pillX,
                    centerY - pillHeight / 2f,
                    pillX + pillWidth,
                    centerY + pillHeight / 2f
                )
                badgePaint.color = if (isLong) {
                    Color.argb(235, 16, 28, 54)
                } else {
                    Color.argb(228, 28, 36, 54)
                }
                canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, badgePaint)
                val textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(label, pillRect.centerX(), textY, textPaint)
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

        fun dismissAmbientOverlay(): Boolean {
            val instance = activeInstance ?: return false
            instance.serviceScope.launch {
                instance.dismissAmbientOverlayInternal()
            }
            return true
        }
    }
}
