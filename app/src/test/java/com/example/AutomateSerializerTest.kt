package com.example

import com.example.modules.automate.AutomateConfig
import com.example.modules.automate.AutomateLogEntry
import com.example.modules.automate.AutomateSerializer
import com.example.modules.automate.AutomationRoutine
import com.example.modules.automate.RoutineAction
import com.example.modules.automate.RoutineActionType
import com.example.modules.automate.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutomateSerializerTest {

    @Test
    fun roundTripRoutinesAndLogs() {
        val routines = listOf(
            AutomationRoutine(
                id = "r1",
                name = "Mode Voiture",
                enabled = true,
                triggerType = TriggerType.BLUETOOTH_CONNECTED,
                filterName = "CarAudio",
                actions = listOf(
                    RoutineAction(RoutineActionType.MEDIA_VOLUME, intParam = 85),
                    RoutineAction(RoutineActionType.CAFFEINE_ON),
                    RoutineAction(RoutineActionType.SEND_NOTIFICATION, textParam = "Bonne route")
                ),
                lastTriggeredMs = 123456789L
            ),
            AutomationRoutine(
                id = "r2",
                name = "Bureau USB",
                enabled = true,
                triggerType = TriggerType.USB_CHARGING_CONNECTED,
                filterName = AutomateConfig.USB_FILTER_USB_ONLY,
                actions = listOf(
                    RoutineAction(RoutineActionType.AOD_ON),
                    RoutineAction(RoutineActionType.DND_ON),
                    RoutineAction(RoutineActionType.RINGER_SILENT)
                )
            )
        )

        val encoded = AutomateSerializer.encodeRoutines(routines)
        val decoded = AutomateSerializer.decodeRoutines(encoded)

        assertNotNull(decoded)
        assertEquals(2, decoded!!.size)
        assertEquals("Mode Voiture", decoded[0].name)
        assertEquals(TriggerType.BLUETOOTH_CONNECTED, decoded[0].triggerType)
        assertEquals("CarAudio", decoded[0].filterName)
        // L’ancienne action « notification » devient la notification de fin de routine.
        assertEquals(2, decoded[0].actions.size)
        assertEquals(85, decoded[0].actions[0].intParam)
        assertEquals(true, decoded[0].notifyOnRun)
        assertEquals("Bonne route", decoded[0].notifyText)
        assertEquals(123456789L, decoded[0].lastTriggeredMs)

        assertEquals(TriggerType.USB_CHARGING_CONNECTED, decoded[1].triggerType)
        assertEquals(AutomateConfig.USB_FILTER_USB_ONLY, decoded[1].filterName)
        assertEquals("Port USB PC", AutomateConfig.formatFilterDisplay(decoded[1].triggerType, decoded[1].filterName))
        assertEquals("Tout câble USB", AutomateConfig.formatFilterDisplay(TriggerType.USB_CHARGING_DISCONNECTED, ""))

        val styled = decoded[1].copy(
            iconKey = "work",
            colorKey = "mint",
            restoreOnEnd = true,
            triggerType = TriggerType.BATTERY_BELOW,
            triggerValue = 15
        )
        val styledBack = AutomateSerializer.decodeRoutines(AutomateSerializer.encodeRoutines(listOf(styled)))!!.single()
        assertEquals(styled, styledBack)

        val snapshots = mapOf("r1" to mapOf("ringer" to "2", "volume" to "7"))
        assertEquals(snapshots, AutomateSerializer.decodeSnapshots(AutomateSerializer.encodeSnapshots(snapshots)))

        val logs = listOf(
            AutomateLogEntry(
                timestampMs = 987654321L,
                routineName = "Mode Voiture",
                triggerDetail = "Bluetooth connecté",
                summary = "Volume 85% • Caféine ON"
            )
        )
        val encodedLogs = AutomateSerializer.encodeLogs(logs)
        val decodedLogs = AutomateSerializer.decodeLogs(encodedLogs)
        assertEquals(1, decodedLogs.size)
        assertEquals("Mode Voiture", decodedLogs[0].routineName)
    }
}
