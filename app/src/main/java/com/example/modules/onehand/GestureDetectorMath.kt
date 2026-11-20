package com.example.modules.onehand

import kotlin.math.atan2
import kotlin.math.hypot

data class EvaluatedGesture(
    val direction: SwipeDirection,
    val length: SwipeLength,
    val action: GestureAction,
    val isReady: Boolean,
    val progress: Float,
    val inwardDxPx: Float,
    val dyPx: Float
)

object GestureDetectorMath {
    private const val DIAGONAL_ANGLE_THRESHOLD_DEG = 26.0

    fun evaluate(
        side: HandleSide,
        dxPx: Float,
        dyPx: Float,
        triggerDistancePx: Float,
        longTriggerDistancePx: Float,
        longSwipeEnabled: Boolean,
        handleConfig: HandleConfig
    ): EvaluatedGesture {
        val inwardDx = if (side == HandleSide.LEFT) dxPx else -dxPx
        val distance = hypot(inwardDx.toDouble(), dyPx.toDouble()).toFloat()

        val angleDeg = Math.toDegrees(
            atan2((-dyPx).toDouble(), inwardDx.coerceAtLeast(1f).toDouble())
        )

        val direction = when {
            angleDeg > DIAGONAL_ANGLE_THRESHOLD_DEG -> SwipeDirection.DIAGONAL_UP
            angleDeg < -DIAGONAL_ANGLE_THRESHOLD_DEG -> SwipeDirection.DIAGONAL_DOWN
            else -> SwipeDirection.HORIZONTAL
        }

        val safeTrigger = triggerDistancePx.coerceAtLeast(16f)
        val safeLongTrigger = longTriggerDistancePx.coerceAtLeast(safeTrigger + 24f)

        val isInwardEnough = inwardDx > safeTrigger * 0.35f
        val isReady = isInwardEnough && distance >= safeTrigger
        val length = if (longSwipeEnabled && isReady && distance >= safeLongTrigger) {
            SwipeLength.LONG
        } else {
            SwipeLength.SHORT
        }

        val progress = if (inwardDx <= 0f) 0f else (distance / safeTrigger).coerceIn(0f, 1.5f)
        val action = handleConfig.actionFor(direction, length)

        return EvaluatedGesture(
            direction = direction,
            length = length,
            action = action,
            isReady = isReady,
            progress = progress,
            inwardDxPx = inwardDx,
            dyPx = dyPx
        )
    }
}
