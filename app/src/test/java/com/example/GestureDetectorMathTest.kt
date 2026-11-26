package com.example

import com.example.modules.onehand.GestureAction
import com.example.modules.onehand.GestureDetectorMath
import com.example.modules.onehand.HandleConfig
import com.example.modules.onehand.HandleSide
import com.example.modules.onehand.OneHandConfig
import com.example.modules.onehand.SwipeDirection
import com.example.modules.onehand.SwipeLength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureDetectorMathTest {

    private val defaultHandle = HandleConfig(
        shortHorizontal = GestureAction.BACK,
        shortDiagonalUp = GestureAction.RECENTS,
        shortDiagonalDown = GestureAction.NOTIFICATIONS,
        longHorizontal = GestureAction.HOME,
        longDiagonalUp = GestureAction.QUICK_SETTINGS,
        longDiagonalDown = GestureAction.SCREENSHOT
    )

    @Test
    fun leftHandleShortHorizontalSwipeTriggersBack() {
        val result = GestureDetectorMath.evaluate(
            side = HandleSide.LEFT,
            dxPx = 90f,
            dyPx = 5f,
            triggerDistancePx = 70f,
            longTriggerDistancePx = 180f,
            longSwipeEnabled = true,
            handleConfig = defaultHandle
        )

        assertTrue(result.isReady)
        assertEquals(SwipeDirection.HORIZONTAL, result.direction)
        assertEquals(SwipeLength.SHORT, result.length)
        assertEquals(GestureAction.BACK, result.action)
    }

    @Test
    fun rightHandleLongDiagonalUpSwipeTriggersQuickSettings() {
        val result = GestureDetectorMath.evaluate(
            side = HandleSide.RIGHT,
            dxPx = -150f,
            dyPx = -140f,
            triggerDistancePx = 70f,
            longTriggerDistancePx = 180f,
            longSwipeEnabled = true,
            handleConfig = defaultHandle
        )

        assertTrue(result.isReady)
        assertEquals(SwipeDirection.DIAGONAL_UP, result.direction)
        assertEquals(SwipeLength.LONG, result.length)
        assertEquals(GestureAction.QUICK_SETTINGS, result.action)
    }

    @Test
    fun rightHandleShortDiagonalDownSwipeTriggersNotifications() {
        val result = GestureDetectorMath.evaluate(
            side = HandleSide.RIGHT,
            dxPx = -80f,
            dyPx = 85f,
            triggerDistancePx = 70f,
            longTriggerDistancePx = 180f,
            longSwipeEnabled = true,
            handleConfig = defaultHandle
        )

        assertTrue(result.isReady)
        assertEquals(SwipeDirection.DIAGONAL_DOWN, result.direction)
        assertEquals(SwipeLength.SHORT, result.length)
        assertEquals(GestureAction.NOTIFICATIONS, result.action)
    }

    @Test
    fun outwardOrShortSwipeDoesNotTrigger() {
        val outward = GestureDetectorMath.evaluate(
            side = HandleSide.RIGHT,
            dxPx = 60f,
            dyPx = 0f,
            triggerDistancePx = 70f,
            longTriggerDistancePx = 180f,
            longSwipeEnabled = true,
            handleConfig = defaultHandle
        )
        assertFalse(outward.isReady)

        val tooShort = GestureDetectorMath.evaluate(
            side = HandleSide.LEFT,
            dxPx = 30f,
            dyPx = 0f,
            triggerDistancePx = 70f,
            longTriggerDistancePx = 180f,
            longSwipeEnabled = true,
            handleConfig = defaultHandle
        )
        assertFalse(tooShort.isReady)
    }

    @Test
    fun handleSyncMirrorsEverythingExceptOnOff() {
        val defaults = OneHandConfig()
        assertTrue(defaults.syncHandles)
        assertFalse(defaults.showGestureLabel)
        assertEquals(defaults.leftHandle.shortHorizontal, defaults.rightHandle.shortHorizontal)
        assertEquals(defaults.leftHandle.longDiagonalDown, defaults.rightHandle.longDiagonalDown)

        val custom = OneHandConfig(
            syncHandles = true,
            leftHandle = HandleConfig(enabled = false, heightPercent = 1.0f, shortHorizontal = GestureAction.FLASHLIGHT),
            rightHandle = HandleConfig(heightPercent = 0.35f, shortHorizontal = GestureAction.SCREENSHOT)
        )
        val synced = custom.updatingHandle(HandleSide.RIGHT) { it.copy(widthDp = 40f) }
        assertEquals(GestureAction.SCREENSHOT, synced.leftHandle.shortHorizontal)
        assertEquals(0.35f, synced.leftHandle.heightPercent, 0.001f)
        assertEquals(40f, synced.leftHandle.widthDp, 0.001f)
        assertFalse(synced.leftHandle.enabled)

        val independent = custom.copy(syncHandles = false).updatingHandle(HandleSide.RIGHT) { it.copy(widthDp = 40f) }
        assertEquals(1.0f, independent.leftHandle.heightPercent, 0.001f)
        assertEquals(30f, independent.leftHandle.widthDp, 0.001f)
    }
}
