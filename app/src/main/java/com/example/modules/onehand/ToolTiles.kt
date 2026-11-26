package com.example.modules.onehand

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.core.graphics.ColorUtils
import com.example.modules.automate.ModeIconRenderer
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Bouton du menu d’outils, dessiné à la main pour des animations soignées :
 * remplissage qui part du doigt, icône qui rebondit, onde, coche de confirmation,
 * état « Activé / Désactivé » des interrupteurs, et mode édition (poignée, contour).
 */
@SuppressLint("ViewConstructor")
class ToolTileView(
    context: Context,
    val item: ToolItem,
    private val look: Look,
    private val host: Host,
    private val label: String,
    private val shortLabel: String,
    private val iconIdle: android.graphics.Bitmap,
    private val iconOn: android.graphics.Bitmap
) : View(context) {

    /** Outil représenté, ou null pour une application. */
    val tool: GestureAction? get() = (item as? ToolItem.Action)?.action

    data class Look(
        val shape: ToolShape,
        val bubbleWidth: Int,
        val bubbleHeight: Int,
        val iconPx: Int,
        val accent: Int,
        val onAccent: Int,
        val idle: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
        val showLabels: Boolean,
        val density: Float,
        val scaledDensity: Float
    )

    interface Host {
        val editing: Boolean
        fun onTileTap(tile: ToolTileView, x: Float, y: Float)
        fun onTileLongPress(tile: ToolTileView)
        fun onDragStart(tile: ToolTileView, rawX: Float, rawY: Float)
        fun onDragMove(tile: ToolTileView, rawX: Float, rawY: Float)
        fun onDragEnd(tile: ToolTileView)
    }

    var span: ToolSpan = ToolSpan.SINGLE
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }
    var active: Boolean? = null
        private set
    var busy = false
        private set

    /** Réseau Wi-Fi ou appareil Bluetooth connecté, affiché à la place du libellé ou de l’état. */
    var detail: String? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
    var editMode = false
        set(value) {
            field = value
            invalidate()
        }

    private fun dp(v: Float) = v * look.density
    private fun sp(v: Float) = v * look.scaledDensity

    private val handleIcon = ModeIconRenderer.renderTinted(Icons.Rounded.DragIndicator, dp(14f).toInt(), look.onAccent)
    private val sizeIcon = ModeIconRenderer.renderTinted(Icons.Rounded.OpenInFull, dp(11f).toInt(), look.accent)

    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = look.accent }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = look.accent
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = look.accent
        pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f)
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(12f) }
    private val pillLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(13f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val statePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(11f) }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = look.accent
    }

    private val shapeRect = RectF()
    private val shapePath = Path()
    private var cornerRadius = 0f
    private val checkPath = Path()
    private val checkSegment = Path()

    private var fill = 0f
    private var revealX = -1f
    private var revealY = -1f
    private var ring = 0f
    private var ringAlpha = 0f
    private var check = 0f
    private var iconScale = 1f
    private var iconRotation = 0f
    private var busyAnimator: ValueAnimator? = null
    private val running = ArrayList<ValueAnimator>()

    init {
        contentDescription = label
        isClickable = true
    }

    /** Ce bouton a un libellé sous lui (bouton d’une case, libellés activés). */
    val showsLabel: Boolean get() = look.showLabels

    private fun computeShape() {
        val w = width.toFloat()
        val h = height.toFloat()
        val top = dp(4f)
        val bh = look.bubbleHeight.toFloat()
        val bw = look.bubbleWidth.toFloat()
        // Les pilules s’alignent sur les bords des boutons des autres rangées.
        val cellW = w / span.columns
        val inset = ((cellW - bw) / 2f).coerceAtLeast(dp(2f))
        when (span) {
            ToolSpan.SINGLE -> {
                val l = (w - bw) / 2f
                shapeRect.set(l, top, l + bw, top + bh)
            }
            ToolSpan.WIDE -> shapeRect.set(inset, top, w - inset, top + bh)
            ToolSpan.TALL -> {
                val tw = max(bw, min(w - dp(4f), dp(60f)))
                val l = (w - tw) / 2f
                shapeRect.set(l, top, l + tw, h - dp(6f))
            }
        }
        val minSide = min(shapeRect.width(), shapeRect.height())
        cornerRadius = if (look.shape == ToolShape.SQUIRCLE) minSide * 0.32f else minSide / 2f
        shapePath.reset()
        shapePath.addRoundRect(shapeRect, cornerRadius, cornerRadius, Path.Direction.CW)
    }

    private fun stateText(): String? = when {
        busy -> "…"
        active == true && !detail.isNullOrBlank() -> detail
        active == true -> "Activé"
        active == false -> "Désactivé"
        else -> null
    }

    override fun onDraw(canvas: Canvas) {
        computeShape()
        val on = fill.coerceIn(0f, 1f)
        shapePaint.color = look.idle
        canvas.drawPath(shapePath, shapePaint)
        if (on > 0f) {
            val rx = if (revealX < 0f) shapeRect.centerX() else revealX
            val ry = if (revealY < 0f) shapeRect.centerY() else revealY
            val maxR = maxOf(
                hypot(rx - shapeRect.left, ry - shapeRect.top),
                hypot(rx - shapeRect.right, ry - shapeRect.top),
                hypot(rx - shapeRect.left, ry - shapeRect.bottom),
                hypot(rx - shapeRect.right, ry - shapeRect.bottom)
            )
            canvas.save()
            canvas.clipPath(shapePath)
            canvas.drawCircle(rx, ry, maxR * on, fillPaint)
            canvas.restore()
        }
        if (editMode) canvas.drawPath(shapePath, outlinePaint)
        if (ringAlpha > 0f) {
            ringPaint.alpha = (255 * ringAlpha).toInt().coerceIn(0, 255)
            val grow = dp(9f) * ring
            canvas.drawRoundRect(
                shapeRect.left - grow, shapeRect.top - grow, shapeRect.right + grow, shapeRect.bottom + grow,
                cornerRadius + grow, cornerRadius + grow, ringPaint
            )
        }

        val state = stateText()
        val showText = span != ToolSpan.SINGLE
        val cx: Float
        val cy: Float
        when (span) {
            ToolSpan.SINGLE -> { cx = shapeRect.centerX(); cy = shapeRect.centerY() }
            ToolSpan.WIDE -> { cx = shapeRect.left + look.bubbleHeight / 2f + dp(2f); cy = shapeRect.centerY() }
            ToolSpan.TALL -> { cx = shapeRect.centerX(); cy = shapeRect.top + shapeRect.height() * 0.36f }
        }
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(iconRotation)
        canvas.scale(iconScale, iconScale)
        bitmapPaint.alpha = (255 * (1f - on)).toInt()
        canvas.drawBitmap(iconIdle, -iconIdle.width / 2f, -iconIdle.height / 2f, bitmapPaint)
        if (on > 0f) {
            bitmapPaint.alpha = (255 * on).toInt()
            canvas.drawBitmap(iconOn, -iconOn.width / 2f, -iconOn.height / 2f, bitmapPaint)
        }
        canvas.restore()

        val textColor = ColorUtils.blendARGB(look.onSurface, look.onAccent, on)
        val subColor = ColorUtils.blendARGB(look.onSurfaceVariant, look.onAccent, on)
        if (!showText) {
            if (look.showLabels) {
                labelPaint.color = look.onSurface
                labelPaint.textAlign = Paint.Align.CENTER
                val name = detail?.takeIf { active == true && it.isNotBlank() } ?: shortLabel
                val text = TextUtils.ellipsize(name, labelPaint, width - dp(4f), TextUtils.TruncateAt.END)
                canvas.drawText(text, 0, text.length, width / 2f, shapeRect.bottom + dp(4f) - labelPaint.ascent(), labelPaint)
            }
        } else if (span == ToolSpan.WIDE) {
            val left = shapeRect.left + look.bubbleHeight + dp(2f)
            val avail = shapeRect.right - left - dp(12f)
            pillLabelPaint.color = textColor
            pillLabelPaint.textAlign = Paint.Align.LEFT
            statePaint.color = subColor
            statePaint.textAlign = Paint.Align.LEFT
            val label = TextUtils.ellipsize(this.label, pillLabelPaint, avail, TextUtils.TruncateAt.END)
            if (state != null) {
                canvas.drawText(label, 0, label.length, left, shapeRect.centerY() - dp(2f), pillLabelPaint)
                val s = TextUtils.ellipsize(state, statePaint, avail, TextUtils.TruncateAt.END)
                canvas.drawText(s, 0, s.length, left, shapeRect.centerY() + dp(2f) - statePaint.ascent(), statePaint)
            } else {
                canvas.drawText(label, 0, label.length, left, shapeRect.centerY() - (pillLabelPaint.ascent() + pillLabelPaint.descent()) / 2f, pillLabelPaint)
            }
        } else {
            val avail = shapeRect.width() - dp(8f)
            pillLabelPaint.color = textColor
            pillLabelPaint.textAlign = Paint.Align.CENTER
            statePaint.color = subColor
            statePaint.textAlign = Paint.Align.CENTER
            val label = TextUtils.ellipsize(shortLabel, pillLabelPaint, avail, TextUtils.TruncateAt.END)
            val base = shapeRect.top + shapeRect.height() * 0.68f
            canvas.drawText(label, 0, label.length, shapeRect.centerX(), base, pillLabelPaint)
            if (state != null) {
                val s = TextUtils.ellipsize(state, statePaint, avail, TextUtils.TruncateAt.END)
                canvas.drawText(s, 0, s.length, shapeRect.centerX(), base + dp(4f) - statePaint.ascent(), statePaint)
            }
        }

        val inset = min(shapeRect.width(), shapeRect.height()) * 0.12f
        if (check > 0f) drawCheck(canvas, shapeRect.right - inset, shapeRect.top + inset)
        if (editMode) {
            badgePaint.color = look.accent
            val hx = shapeRect.left + inset
            val hy = shapeRect.top + inset
            canvas.drawCircle(hx, hy, dp(10f), badgePaint)
            canvas.drawBitmap(handleIcon, hx - handleIcon.width / 2f, hy - handleIcon.height / 2f, null)
            val sx = shapeRect.right - inset
            val sy = shapeRect.bottom - inset
            badgePaint.color = look.onAccent
            canvas.drawCircle(sx, sy, dp(9f), badgePaint)
            canvas.drawBitmap(sizeIcon, sx - sizeIcon.width / 2f, sy - sizeIcon.height / 2f, null)
        }
    }

    private fun drawCheck(canvas: Canvas, x: Float, y: Float) {
        val pop = OvershootInterpolator(2.5f).getInterpolation(min(1f, check * 1.6f))
        badgePaint.color = look.onAccent
        canvas.drawCircle(x, y, dp(10f) * pop, badgePaint)
        badgePaint.color = look.accent
        canvas.drawCircle(x, y, dp(8f) * pop, badgePaint)
        checkPath.reset()
        checkPath.moveTo(x - dp(4f), y + dp(0.2f))
        checkPath.lineTo(x - dp(1.2f), y + dp(3f))
        checkPath.lineTo(x + dp(4.2f), y - dp(2.8f))
        val measure = PathMeasure(checkPath, false)
        checkSegment.reset()
        val progress = ((check - 0.35f) / 0.65f).coerceIn(0f, 1f)
        measure.getSegment(0f, measure.length * progress, checkSegment, true)
        checkPaint.color = look.onAccent
        canvas.drawPath(checkSegment, checkPaint)
    }

    private fun tween(from: Float, to: Float, duration: Long, delay: Long = 0L, interpolator: android.animation.TimeInterpolator = DecelerateInterpolator(), update: (Float) -> Unit): ValueAnimator =
        ValueAnimator.ofFloat(from, to).apply {
            this.duration = duration
            startDelay = delay
            this.interpolator = interpolator
            addUpdateListener {
                update(it.animatedValue as Float)
                invalidate()
            }
            running += this
            start()
        }

    /** Icône qui bondit en tournant légèrement, plus une onde autour du bouton. */
    private fun pop() {
        tween(1f, 1.32f, 130L) { iconScale = it }
        tween(1.32f, 1f, 360L, delay = 130L, interpolator = OvershootInterpolator(3f)) { iconScale = it }
        tween(-16f, 0f, 520L, interpolator = OvershootInterpolator(4f)) { iconRotation = it }
        tween(0f, 1f, 560L) {
            ring = it
            ringAlpha = 1f - it
        }
    }

    /** Nouvel état d’un interrupteur ; le remplissage part du point touché. */
    fun setActive(value: Boolean?, animated: Boolean, fromX: Float = -1f, fromY: Float = -1f) {
        active = value
        val target = if (value == true) 1f else 0f
        if (!animated) {
            fill = target
            invalidate()
            return
        }
        if (fromX >= 0f) {
            revealX = fromX
            revealY = fromY
        }
        tween(fill, target, 440L) { fill = it }
        pop()
    }

    /** Action ponctuelle réussie : le bouton se remplit, l’icône bondit, une coche se dessine. */
    fun confirm(x: Float, y: Float) {
        revealX = x
        revealY = y
        tween(fill, 1f, 260L) { fill = it }
        pop()
        tween(0f, 1f, 420L, delay = 80L) { check = it }
    }

    /** Attente (tuile du volet en cours) : l’onde tourne en boucle. */
    fun setBusy(value: Boolean) {
        busy = value
        busyAnimator?.cancel()
        busyAnimator = null
        if (value) {
            busyAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 900L
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    ring = it.animatedValue as Float
                    ringAlpha = 1f - ring
                    invalidate()
                }
                start()
            }
        } else {
            ring = 0f
            ringAlpha = 0f
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        busyAnimator?.cancel()
        running.forEach { it.cancel() }
        running.clear()
        removeCallbacks(longPress)
        super.onDetachedFromWindow()
    }

    // ---- Toucher : appui, appui long (mode édition) et glisser ----

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var tapX = 0f
    private var tapY = 0f
    private var dragging = false
    private var moved = false
    private var longPressed = false

    private val longPress = Runnable {
        longPressed = true
        if (!host.editing) host.onTileLongPress(this)
        dragging = true
        host.onDragStart(this, lastRawX, lastRawY)
    }

    private fun pressScale(down: Boolean) {
        if (dragging) return
        animate().scaleX(if (down) 0.93f else 1f).scaleY(if (down) 0.93f else 1f)
            .setDuration(if (down) 90L else 160L).start()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                tapX = event.x
                tapY = event.y
                dragging = false
                moved = false
                longPressed = false
                parent?.requestDisallowInterceptTouchEvent(true)
                postDelayed(longPress, if (host.editing) 220L else ViewConfiguration.getLongPressTimeout().toLong())
                pressScale(true)
            }
            MotionEvent.ACTION_MOVE -> {
                lastRawX = event.rawX
                lastRawY = event.rawY
                if (!dragging && !moved && hypot(event.rawX - downRawX, event.rawY - downRawY) > slop) {
                    moved = true
                    removeCallbacks(longPress)
                    if (host.editing) {
                        dragging = true
                        host.onDragStart(this, downRawX, downRawY)
                    } else {
                        pressScale(false)
                    }
                }
                if (dragging) host.onDragMove(this, event.rawX, event.rawY)
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                if (dragging) {
                    dragging = false
                    host.onDragEnd(this)
                } else {
                    pressScale(false)
                    if (!moved && !longPressed) {
                        tapX = event.x
                        tapY = event.y
                        performClick()
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                if (dragging) {
                    dragging = false
                    host.onDragEnd(this)
                } else {
                    pressScale(false)
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        host.onTileTap(this, tapX.takeIf { it > 0f } ?: (width / 2f), tapY.takeIf { it > 0f } ?: (height / 2f))
        tapX = 0f
        tapY = 0f
        return true
    }
}

/**
 * Grille du menu d’outils : place les boutons dans l’ordre en sautant les cases prises
 * (pilules horizontales sur 2 colonnes, verticales sur 2 rangées), et anime les boutons
 * qui changent de place quand on en déplace ou redimensionne un.
 */
class ToolGridLayout(context: Context) : ViewGroup(context) {

    var columns = 4
    /** Rangée avec libellés (au moins un bouton d’une case dessous). */
    var rowHeight = 0
    /** Rangée sans libellé (pilules seules) : plus basse. */
    var compactRowHeight = 0
    val order = ArrayList<ToolTileView>()
    var animateMoves = false
    var draggedTile: ToolTileView? = null
    private val slots = HashMap<ToolTileView, Rect>()

    init {
        clipChildren = false
        clipToPadding = false
    }

    fun addTile(tile: ToolTileView) {
        order += tile
        addView(tile)
    }

    private fun place(width: Int): Int {
        slots.clear()
        val cellWidth = width / columns
        val taken = ArrayList<BooleanArray>()
        val cells = HashMap<ToolTileView, IntArray>()
        fun free(row: Int, col: Int) = row >= taken.size || !taken[row][col]
        fun take(row: Int, col: Int) {
            while (taken.size <= row) taken += BooleanArray(columns)
            taken[row][col] = true
        }
        for (tile in order) {
            val spanCols = tile.span.columns.coerceAtMost(columns)
            val spanRows = tile.span.rows
            var row = 0
            var placed = false
            while (!placed) {
                for (col in 0..columns - spanCols) {
                    val fits = (row until row + spanRows).all { r -> (col until col + spanCols).all { c -> free(r, c) } }
                    if (fits) {
                        for (r in row until row + spanRows) for (c in col until col + spanCols) take(r, c)
                        cells[tile] = intArrayOf(row, col, spanRows, spanCols)
                        placed = true
                        break
                    }
                }
                row++
            }
        }
        // Hauteur de chaque rangée : avec libellés seulement si un bouton d’une case y est.
        val rows = taken.size
        val labelled = BooleanArray(rows)
        cells.forEach { (tile, c) -> if (tile.span == ToolSpan.SINGLE && tile.showsLabel) labelled[c[0]] = true }
        val tops = IntArray(rows + 1)
        for (r in 0 until rows) tops[r + 1] = tops[r] + if (labelled[r] || compactRowHeight <= 0) rowHeight else compactRowHeight
        cells.forEach { (tile, c) ->
            slots[tile] = Rect(c[1] * cellWidth, tops[c[0]], (c[1] + c[3]) * cellWidth, tops[c[0] + c[2]])
        }
        totalHeight = tops[rows]
        return rows
    }

    private var totalHeight = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rows = place(width)
        for (tile in order) {
            val slot = slots[tile] ?: continue
            tile.measure(
                MeasureSpec.makeMeasureSpec(slot.width(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(slot.height(), MeasureSpec.EXACTLY)
            )
        }
        setMeasuredDimension(width, if (rows == 0) 0 else totalHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (tile in order) {
            val slot = slots[tile] ?: continue
            val oldLeft = tile.left
            val oldTop = tile.top
            val wasLaidOut = tile.width > 0
            tile.layout(slot.left, slot.top, slot.right, slot.bottom)
            if (!wasLaidOut || (oldLeft == slot.left && oldTop == slot.top)) continue
            if (tile === draggedTile) {
                // Le bouton tenu reste sous le doigt.
                tile.translationX += (oldLeft - slot.left).toFloat()
                tile.translationY += (oldTop - slot.top).toFloat()
            } else if (animateMoves) {
                tile.translationX += (oldLeft - slot.left).toFloat()
                tile.translationY += (oldTop - slot.top).toFloat()
                tile.animate().translationX(0f).translationY(0f).setDuration(240L)
                    .setInterpolator(DecelerateInterpolator(1.6f)).start()
            }
        }
    }

    /** Bouton (autre que celui tenu) sous le point [x], [y] de la grille. */
    fun tileAt(x: Float, y: Float): ToolTileView? =
        order.firstOrNull { it !== draggedTile && slots[it]?.contains(x.toInt(), y.toInt()) == true }

    fun moveTile(tile: ToolTileView, before: ToolTileView) {
        val target = order.indexOf(before)
        if (target < 0) return
        order.remove(tile)
        order.add(target.coerceAtMost(order.size), tile)
        animateMoves = true
        requestLayout()
    }
}
