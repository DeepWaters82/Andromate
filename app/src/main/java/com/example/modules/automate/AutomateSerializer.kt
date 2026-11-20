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
                val obj = array.getJSONObject(i)
                val triggerType = runCatching {
                    TriggerType.valueOf(obj.getString("triggerType"))
                }.getOrDefault(TriggerType.WIFI_CONNECTED)

                val actionsArr = obj.optJSONArray("actions") ?: JSONArray()
                val actions = mutableListOf<RoutineAction>()
                for (j in 0 until actionsArr.length()) {
                    val actObj = actionsArr.getJSONObject(j)
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

                val lastTriggered = if (obj.isNull("lastTriggeredMs")) {
                    null
                } else {
                    obj.optLong("lastTriggeredMs")
                }

                result.add(
                    AutomationRoutine(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        enabled = obj.optBoolean("enabled", true),
                        triggerType = triggerType,
                        filterName = obj.optString("filterName", ""),
                        actions = actions,
                        lastTriggeredMs = lastTriggered
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
                val obj = array.getJSONObject(i)
                list.add(
                    AutomateLogEntry(
                        timestampMs = obj.getLong("timestampMs"),
                        routineName = obj.getString("routineName"),
                        triggerDetail = obj.getString("triggerDetail"),
                        summary = obj.getString("summary")
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }
}
