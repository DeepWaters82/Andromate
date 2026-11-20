package com.example.modules.onehand.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.modules.onehand.EvaluatedGesture
import com.example.modules.onehand.GestureAction
import com.example.modules.onehand.GestureDetectorMath
import com.example.modules.onehand.HandleColorStyle
import com.example.modules.onehand.HandleSide
import com.example.modules.onehand.OneHandConfig
import com.example.modules.onehand.SwipeDirection
import com.example.modules.onehand.SwipeLength

@Composable
fun GestureSandboxCard(
    config: OneHandConfig,
    selectedSide: HandleSide,
    lastFeedback: String?,
    onSandboxGestureTriggered: (HandleSide, GestureAction, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    var activeSide by remember { mutableStateOf<HandleSide?>(null) }
    var anchorY by remember { mutableStateOf(0f) }
    var activeGesture by remember { mutableStateOf<EvaluatedGesture?>(null) }

    val edgeZonePx = with(density) { 56.dp.toPx() }
    val triggerPx = with(density) { config.triggerDistanceDp.dp.toPx() }
    val longTriggerPx = with(density) { config.longTriggerDistanceDp.dp.toPx() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TouchApp,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.sandbox_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurface
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = if (config.masterEnabled) "Actif" else "En pause",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (config.masterEnabled) colorScheme.primary else colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colorScheme.surfaceContainerLowest)
                    .testTag("gesture_sandbox_area")
                    .pointerInput(config) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startX = down.position.x
                            val startY = down.position.y

                            val side = when {
                                startX <= edgeZonePx && config.leftHandle.enabled -> HandleSide.LEFT
                                startX >= size.width - edgeZonePx && config.rightHandle.enabled -> HandleSide.RIGHT
                                else -> null
                            } ?: return@awaitEachGesture

                            down.consume()
                            activeSide = side
                            anchorY = startY
                            var wasReady = false
                            var wasLong = false
                            var latest: EvaluatedGesture? = null
                            val handleCfg = config.handleFor(side)

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    latest?.let { result ->
                                        if (result.isReady && result.action != GestureAction.NONE) {
                                            if (config.hapticFeedbackEnabled) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            onSandboxGestureTriggered(
                                                side,
                                                result.action,
                                                result.length == SwipeLength.LONG
                                            )
                                        }
                                    }
                                    activeSide = null
                                    activeGesture = null
                                    break
                                }

                                change.consume()
                                val dx = change.position.x - startX
                                val dy = change.position.y - startY
                                val evaluated = GestureDetectorMath.evaluate(
                                    side = side,
                                    dxPx = dx,
                                    dyPx = dy,
                                    triggerDistancePx = triggerPx,
                                    longTriggerDistancePx = longTriggerPx,
                                    longSwipeEnabled = config.longSwipeEnabled,
                                    handleConfig = handleCfg
                                )
                                latest = evaluated
                                activeGesture = evaluated

                                if (evaluated.isReady && !wasReady && evaluated.action != GestureAction.NONE) {
                                    wasReady = true
                                    if (config.hapticFeedbackEnabled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                                if (evaluated.length == SwipeLength.LONG && !wasLong && evaluated.action != GestureAction.NONE) {
                                    wasLong = true
                                    if (config.hapticFeedbackEnabled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Subtle grid dots / phone mockup guide
                    val dotColor = colorScheme.outlineVariant.copy(alpha = 0.35f)
                    val cols = 6
                    val rows = 4
                    for (r in 1..rows) {
                        for (c in 1..cols) {
                            drawCircle(
                                color = dotColor,
                                radius = 2.dp.toPx(),
                                center = Offset(
                                    x = size.width * (c / (cols + 1f)),
                                    y = size.height * (r / (rows + 1f))
                                )
                            )
                        }
                    }

                    // Draw Left & Right handles reflecting live geometry
                    drawPreviewHandle(
                        side = HandleSide.LEFT,
                        config = config.leftHandle,
                        isSelected = selectedSide == HandleSide.LEFT,
                        isMasterEnabled = config.masterEnabled,
                        colorScheme = colorScheme
                    )
                    drawPreviewHandle(
                        side = HandleSide.RIGHT,
                        config = config.rightHandle,
                        isSelected = selectedSide == HandleSide.RIGHT,
                        isMasterEnabled = config.masterEnabled,
                        colorScheme = colorScheme
                    )

                    // Draw sleek native-style swipe pill + rotated directional arrow
                    val dragSide = activeSide
                    val gesture = activeGesture
                    if (dragSide != null && gesture != null && gesture.progress > 0.05f) {
                        val handleColor = resolveHandleColor(
                            config.handleFor(dragSide).colorStyle,
                            colorScheme
                        )
                        val isLong = gesture.length == SwipeLength.LONG
                        val isReady = gesture.isReady && gesture.action != GestureAction.NONE
                        val pull = gesture.progress.coerceIn(0.15f, 1.22f)
                        val maxBulge = 40.dp.toPx() * pull
                        val halfSpan = 42.dp.toPx()
                        val dirOffset = when (gesture.direction) {
                            SwipeDirection.HORIZONTAL -> 0f
                            SwipeDirection.DIAGONAL_UP -> -15.dp.toPx()
                            SwipeDirection.DIAGONAL_DOWN -> 15.dp.toPx()
                        }
                        val cy = (anchorY + dirOffset).coerceIn(halfSpan * 0.65f, size.height - halfSpan * 0.65f)
                        val pillColor = when {
                            isReady && isLong -> colorScheme.primaryContainer.copy(alpha = 0.92f)
                            isReady -> handleColor.copy(alpha = 0.88f)
                            else -> colorScheme.surfaceContainerHighest.copy(alpha = 0.80f)
                        }
                        val strokeColor = when {
                            isReady && isLong -> colorScheme.secondary
                            isReady -> Color.White.copy(alpha = 0.55f)
                            else -> handleColor.copy(alpha = 0.55f)
                        }

                        val path = Path().apply {
                            if (dragSide == HandleSide.LEFT) {
                                moveTo(0f, cy - halfSpan)
                                cubicTo(0f, cy - halfSpan * 0.48f, maxBulge, cy - halfSpan * 0.38f, maxBulge, cy)
                                cubicTo(maxBulge, cy + halfSpan * 0.38f, 0f, cy + halfSpan * 0.48f, 0f, cy + halfSpan)
                                close()
                            } else {
                                val r = size.width
                                moveTo(r, cy - halfSpan)
                                cubicTo(r, cy - halfSpan * 0.48f, r - maxBulge, cy - halfSpan * 0.38f, r - maxBulge, cy)
                                cubicTo(r - maxBulge, cy + halfSpan * 0.38f, r, cy + halfSpan * 0.48f, r, cy + halfSpan)
                                close()
                            }
                        }
                        drawPath(path = path, color = pillColor)
                        drawPath(
                            path = path,
                            color = strokeColor,
                            style = Stroke(width = if (isLong) 2.dp.toPx() else 1.2.dp.toPx())
                        )

                        if (maxBulge >= 14.dp.toPx()) {
                            val arrowCx = if (dragSide == HandleSide.LEFT) {
                                (maxBulge * 0.54f).coerceAtLeast(11.dp.toPx())
                            } else {
                                (size.width - maxBulge * 0.54f).coerceAtMost(size.width - 11.dp.toPx())
                            }
                            val arrowScale = (0.72f + 0.28f * gesture.progress.coerceIn(0f, 1f)) *
                                (if (isLong) 1.12f else 1.0f)
                            val halfLen = 6.dp.toPx() * arrowScale
                            val wing = 4.4.dp.toPx() * arrowScale
                            val angleDeg = when (dragSide) {
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
                            val arrowColor = when {
                                isReady && isLong -> colorScheme.onPrimaryContainer
                                isReady -> Color.White
                                else -> colorScheme.onSurface
                            }
                            val arrowPath = Path().apply {
                                moveTo(arrowCx - halfLen, cy)
                                lineTo(arrowCx + halfLen, cy)
                                moveTo(arrowCx + halfLen - wing, cy - wing)
                                lineTo(arrowCx + halfLen, cy)
                                lineTo(arrowCx + halfLen - wing, cy + wing)
                            }
                            rotate(degrees = angleDeg, pivot = Offset(arrowCx, cy)) {
                                drawPath(
                                    path = arrowPath,
                                    color = arrowColor,
                                    style = Stroke(
                                        width = (if (isReady) 2.4f else 1.9f).dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }
                }

                // Center hint or optional live gesture badge
                val current = activeGesture
                if (config.showGestureLabel && current != null && current.isReady && current.action != GestureAction.NONE) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center),
                        shape = CircleShape,
                        color = if (current.length == SwipeLength.LONG) {
                            colorScheme.tertiaryContainer
                        } else {
                            colorScheme.primaryContainer
                        },
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = current.action.icon,
                                contentDescription = null,
                                tint = if (current.length == SwipeLength.LONG) {
                                    colorScheme.onTertiaryContainer
                                } else {
                                    colorScheme.onPrimaryContainer
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "${current.action.label} (${if (current.length == SwipeLength.LONG) "Long" else "Court"})",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (current.length == SwipeLength.LONG) {
                                    colorScheme.onTertiaryContainer
                                } else {
                                    colorScheme.onPrimaryContainer
                                }
                            )
                        }
                    }
                } else if (activeSide == null) {
                    Text(
                        text = stringResource(R.string.sandbox_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 42.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = lastFeedback != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.secondaryContainer
                ) {
                    Text(
                        text = lastFeedback.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPreviewHandle(
    side: HandleSide,
    config: com.example.modules.onehand.HandleConfig,
    isSelected: Boolean,
    isMasterEnabled: Boolean,
    colorScheme: ColorScheme
) {
    if (!config.enabled) return
    val baseColor = resolveHandleColor(config.colorStyle, colorScheme)
    val effectiveAlpha = if (isMasterEnabled) config.opacity.coerceIn(0.12f, 0.95f) else 0.14f
    val clampedPct = config.heightPercent.coerceIn(0.15f, 1.0f)
    val isFullSide = clampedPct >= 0.99f
    val scaledHeight = if (isFullSide) size.height else (size.height * clampedPct).coerceIn(28.dp.toPx(), size.height)
    val pillWidth = (config.widthDp * 0.36f).dp.toPx().coerceIn(4.dp.toPx(), 16.dp.toPx())
    val topY = if (isFullSide) {
        0f
    } else {
        ((size.height - scaledHeight) * config.verticalPositionPercent)
            .coerceIn(4.dp.toPx(), (size.height - scaledHeight - 4.dp.toPx()).coerceAtLeast(4.dp.toPx()))
    }
    val leftX = if (side == HandleSide.LEFT) 0f else size.width - pillWidth

    if (isSelected) {
        drawRoundRect(
            color = baseColor.copy(alpha = 0.18f),
            topLeft = Offset(
                x = if (side == HandleSide.LEFT) 0f else size.width - pillWidth * 2.1f,
                y = if (isFullSide) 0f else (topY - 4.dp.toPx()).coerceAtLeast(0f)
            ),
            size = Size(
                width = pillWidth * 2.1f,
                height = if (isFullSide) size.height else (scaledHeight + 8.dp.toPx()).coerceAtMost(size.height)
            ),
            cornerRadius = if (isFullSide) CornerRadius.Zero else CornerRadius(pillWidth, pillWidth)
        )
    }

    drawRoundRect(
        color = baseColor.copy(alpha = effectiveAlpha),
        topLeft = Offset(leftX, topY),
        size = Size(pillWidth, scaledHeight),
        cornerRadius = if (isFullSide) CornerRadius.Zero else CornerRadius(pillWidth / 2f, pillWidth / 2f)
    )
}

private fun resolveHandleColor(style: HandleColorStyle, colorScheme: ColorScheme): Color {
    return when (style) {
        HandleColorStyle.DYNAMIC_PRIMARY -> colorScheme.primary
        HandleColorStyle.DYNAMIC_SECONDARY -> colorScheme.secondary
        HandleColorStyle.DYNAMIC_TERTIARY -> colorScheme.tertiary
        HandleColorStyle.MINIMAL_NEUTRAL -> colorScheme.outline
    }
}
