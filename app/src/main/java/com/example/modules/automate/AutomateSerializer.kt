package com.example.modules.automate

import org.json.JSONArray
import org.json.JSONObject

object AutomateSerializer {

    fun encodeRoutines(routines: List<AutomationRoutine>): String {
        val array = JSONArray()
        routines.forEach { routine ->
            val obj = JSONObject().apply {
                put("id", routine.id)
                put("name", routine.name)
                put("enabled", routine.enabled)
                put("triggerType", routine.triggerType.name)
                put("filterName", routine.filterName)
                put("filterLabel", routine.filterLabel)
                put("triggerValue", routine.triggerValue)
                put("iconKey", routine.iconKey)
                put("colorKey", routine.colorKey)
                put("restoreOnEnd", routine.restoreOnEnd)
                put("notifyOnRun", routine.notifyOnRun)
                put("notifyTitle", routine.notifyTitle)
                put("notifyText", routine.notifyText)
                put("isMode", routine.isMode)
                put("lastTriggeredMs", routine.lastTriggeredMs ?: JSONObject.NULL)
                val actionsArr = JSONArray()
                routine.actions.forEach { action ->
                    actionsArr.put(
                        JSONObject().apply {
                            put("type", action.type.name)
                            put("intParam", action.intParam)
                            put("textParam", action.textParam)
                        }
                    )
                }
                put("actions", actionsArr)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun decodeRoutines(raw: String?): List<AutomationRoutine>? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val array = JSONArray(raw)
            val result = mutableListOf<AutomationRoutine>()
            for (i in 0 until array.length()) {
                // Une entrée abîmée est ignorée au lieu de faire perdre toutes les routines.
                val obj = array.optJSONObject(i) ?: continue
                if (obj.optString("id").isBlank()) continue
                val triggerType = runCatching {
                    TriggerType.valueOf(obj.getString("triggerType"))
                }.getOrDefault(TriggerType.WIFI_CONNECTED)

                val actionsArr = obj.optJSONArray("actions") ?: JSONArray()
                val actions = mutableListOf<RoutineAction>()
                for (j in 0 until actionsArr.length()) {
                    val actObj = actionsArr.optJSONObject(j) ?: continue
                    val type = runCatching {
                        RoutineActionType.valueOf(actObj.getString("type"))
                    }.getOrNull() ?: continue
                    actions.add(
                        RoutineAction(
                            type = type,
                            intParam = actObj.optInt("intParam", 75),
                            textParam = actObj.optString("textParam", "")
                        )
                    )
                }

                // Anciennes versions : l’action « Afficher une notification » devient
                // la notification de fin d’exécution de la routine.
                var notifyOnRun = obj.optBoolean("notifyOnRun", false)
                var notifyText = obj.optString("notifyText", "")
                val legacy = actions.firstOrNull { it.type == RoutineActionType.SEND_NOTIFICATION }
                if (legacy != null && !notifyOnRun) {
                    notifyOnRun = true
                    notifyText = legacy.textParam
                }
                actions.removeAll { it.type == RoutineActionType.SEND_NOTIFICATION }

                val lastTriggered = if (obj.isNull("lastTriggeredMs")) {
                    null
                } else {
                    obj.optLong("lastTriggeredMs")
                }

                result.add(
                    AutomationRoutine(
                        id = obj.getString("id"),
                        name = obj.optString("name", "Routine"),
                        enabled = obj.optBoolean("enabled", true),
                        triggerType = triggerType,
                        filterName = obj.optString("filterName", ""),
                        actions = actions,
                        lastTriggeredMs = lastTriggered,
                        filterLabel = obj.optString("filterLabel", ""),
                        triggerValue = obj.optInt("triggerValue", 20).coerceIn(1, 100),
                        iconKey = obj.optString("iconKey", ""),
                        colorKey = obj.optString("colorKey", ""),
                        restoreOnEnd = obj.optBoolean("restoreOnEnd", false),
                        notifyOnRun = notifyOnRun,
                        notifyTitle = obj.optString("notifyTitle", ""),
                        notifyText = notifyText,
                        isMode = obj.optBoolean("isMode", false)
                    )
                )
            }
            result
        }.getOrNull()
    }

    fun encodeLogs(logs: List<AutomateLogEntry>): String {
        val array = JSONArray()
        logs.take(30).forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("timestampMs", entry.timestampMs)
                    put("routineName", entry.routineName)
                    put("triggerDetail", entry.triggerDetail)
                    put("summary", entry.summary)
                }
            )
        }
        return array.toString()
    }

    fun decodeLogs(raw: String?): List<AutomateLogEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val list = mutableListOf<AutomateLogEntry>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                list.add(
                    AutomateLogEntry(
                        timestampMs = obj.optLong("timestampMs"),
                        routineName = obj.optString("routineName"),
                        triggerDetail = obj.optString("triggerDetail"),
                        summary = obj.optString("summary")
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    fun encodeSnapshots(snapshots: Map<String, Map<String, String>>): String {
        val root = JSONObject()
        snapshots.forEach { (routineId, values) ->
            root.put(routineId, JSONObject(values as Map<*, *>))
        }
        return root.toString()
    }

    fun decodeSnapshots(raw: String?): Map<String, Map<String, String>> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                root.keys().forEach { routineId ->
                    val values = root.optJSONObject(routineId) ?: return@forEach
                    put(routineId, buildMap { values.keys().forEach { put(it, values.optString(it)) } })
                }
            }
        }.getOrDefault(emptyMap())
    }
}
