package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/** Stores local character-continuity warnings without changing the Room schema. */
class ContinuityWarningStorage(context: Context) {
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

    fun save(analysisId: Long, warnings: List<String>) {
        val json = JSONArray()
        warnings.distinct().forEach { json.put(it) }
        prefs.edit().putString(analysisId.toString(), json.toString()).apply()
    }

    fun remove(analysisId: Long) {
        prefs.edit().remove(analysisId.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "rekapin_continuity_warnings_prefs"
    }
}
