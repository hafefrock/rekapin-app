package com.example.data.db

import com.example.data.model.CharacterInfo
import com.example.data.model.TimelineItem
import org.json.JSONArray
import org.json.JSONObject

object AnalysisJsonAdapter {

    fun characterSheetToJson(list: List<CharacterInfo>): String {
        val array = JSONArray()
        list.forEach { c ->
            val obj = JSONObject().apply {
                put("name", c.name)
                put("description", c.description)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun jsonToCharacterSheet(jsonStr: String): List<CharacterInfo> {
        if (jsonStr.isBlank()) return emptyList()
        val result = mutableListOf<CharacterInfo>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.optString("name", "")
                val description = obj.optString("description", "")
                if (name.isNotBlank()) {
                    result.add(CharacterInfo(name = name, description = description))
                }
            }
        } catch (_: Exception) {}
        return result
    }

    fun timelineToJson(list: List<TimelineItem>): String {
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("timestamp", item.timestamp)
                put("sceneDescription", item.sceneDescription)
                put("visualPrompt", item.visualPrompt)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun jsonToTimeline(jsonStr: String): List<TimelineItem> {
        if (jsonStr.isBlank()) return emptyList()
        val result = mutableListOf<TimelineItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val timestamp = obj.optString("timestamp", "")
                val sceneDescription = obj.optString("sceneDescription", "")
                val visualPrompt = obj.optString("visualPrompt", "")
                if (timestamp.isNotBlank() || sceneDescription.isNotBlank()) {
                    result.add(
                        TimelineItem(
                            timestamp = timestamp,
                            sceneDescription = sceneDescription,
                            visualPrompt = visualPrompt
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return result
    }

    fun summariesToJson(list: List<String>): String {
        val array = JSONArray()
        list.forEach { array.put(it) }
        return array.toString()
    }

    fun jsonToSummaries(jsonStr: String): List<String> {
        if (jsonStr.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                result.add(array.optString(i, ""))
            }
        } catch (_: Exception) {}
        return result
    }
}
