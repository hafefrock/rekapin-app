package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/** Keeps local repeated-scene review notes outside the Room schema. */
class RepeatedSceneStorage(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(analysisId: Long): List<String> {
        val raw = prefs.getString(analysisId.toString(), null) ?: return emptyList()
        return try {
            val json = JSONArray(raw)
            buildList {
                for (i in 0 until json.length()) {
                    val value = json.optString(i)
                    if (value.isNotBlank()) add(value)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(analysisId: Long, notes: List<String>) {
        val json = JSONArray()
        notes.distinct().forEach { json.put(it) }
        prefs.edit().putString(analysisId.toString(), json.toString()).apply()
    }

    fun remove(analysisId: Long) {
        prefs.edit().remove(analysisId.toString()).apply()
    }

    fun isDetectRepeatedScenes(): Boolean = prefs.getBoolean(KEY_DETECT_REPEATED_SCENES, true)

    fun setDetectRepeatedScenes(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DETECT_REPEATED_SCENES, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "rekapin_repeated_scene_prefs"
        private const val KEY_DETECT_REPEATED_SCENES = "detect_repeated_scenes"
    }
}
