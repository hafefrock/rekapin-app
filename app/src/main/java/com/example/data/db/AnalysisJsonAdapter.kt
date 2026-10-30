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
                put("dialogue", item.dialogue)
                put("originalNarration", item.originalNarration)
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
                val dialogue = obj.optString("dialogue", "")
                val originalNarration = obj.optString("originalNarration", "")
                if (timestamp.isNotBlank() || sceneDescription.isNotBlank()) {
                    result.add(
                        TimelineItem(
                            timestamp = timestamp,
                            sceneDescription = sceneDescription,
                            visualPrompt = visualPrompt,
                            dialogue = dialogue,
                            originalNarration = originalNarration
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

    fun segmentPlanToJson(spans: List<com.example.util.SegmentSpan>): String {
        val array = JSONArray()
        spans.forEach { span ->
            val obj = JSONObject().apply {
                put("segmentIndex", span.segmentIndex)
                put("startSec", span.startSec)
                put("endSec", span.endSec)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun jsonToSegmentPlan(jsonStr: String): List<com.example.util.SegmentSpan> {
        if (jsonStr.isBlank()) return emptyList()
        val result = mutableListOf<com.example.util.SegmentSpan>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val idx = obj.optInt("segmentIndex", i + 1)
                val start = obj.optLong("startSec", 0L)
                val end = obj.optLong("endSec", 0L)
                if (end > start) {
                    result.add(com.example.util.SegmentSpan(segmentIndex = idx, startSec = start, endSec = end))
                }
            }
        } catch (_: Exception) {}
        return result
    }
}
