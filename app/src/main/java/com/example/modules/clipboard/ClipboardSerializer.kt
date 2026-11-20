package com.example.modules.clipboard

import org.json.JSONArray
import org.json.JSONObject

object ClipboardSerializer {

    fun encodeEntries(entries: List<ClipboardEntry>): String {
        val array = JSONArray()
        entries.take(25).forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("text", entry.text)
                    put("timestampMs", entry.timestampMs)
                    put("pinned", entry.pinned)
                }
            )
        }
        return array.toString()
    }

    fun decodeEntries(raw: String?): List<ClipboardEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val result = mutableListOf<ClipboardEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val text = obj.optString("text", "")
                if (text.isBlank()) continue
                result.add(
                    ClipboardEntry(
                        id = obj.getString("id"),
                        text = text,
                        timestampMs = obj.getLong("timestampMs"),
                        pinned = obj.optBoolean("pinned", false)
                    )
                )
            }
            result
        }.getOrDefault(emptyList())
    }
}
