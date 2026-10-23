package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.data.api.GeminiConstants
import org.json.JSONArray

class ModelStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getModels(): List<String> {
        val jsonStr = prefs.getString(KEY_MODELS_JSON, null)
        if (jsonStr.isNullOrBlank()) {
            return DEFAULT_MODELS
        }
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val item = array.optString(i, "").trim()
                if (item.isNotBlank() && !list.contains(item)) {
                    list.add(item)
                }
            }
        } catch (_: Exception) {
            // fallback gracefully
        }
        return if (list.isEmpty()) listOf(DEFAULT_MODEL) else list
    }

    @Synchronized
    fun addModel(modelName: String): Boolean {
        val clean = modelName.trim()
        if (clean.isBlank()) return false
        val current = getModels().toMutableList()
        if (!current.contains(clean)) {
            current.add(clean)
            saveModelsInternal(current)
            return true
        }
        return false
    }

    @Synchronized
    fun deleteModel(modelName: String): Boolean {
        val clean = modelName.trim()
        val current = getModels().toMutableList()
        val removed = current.remove(clean)
        if (removed) {
            if (current.isEmpty()) {
                current.add(DEFAULT_MODEL)
            }
            saveModelsInternal(current)
            // Jika model terpilih yang dihapus, alihkan ke model pertama
            val selected = getSelectedModel()
            if (selected == clean) {
                setSelectedModel(current.first())
            }
            return true
        }
        return false
    }

    @Synchronized
    fun moveModel(fromIndex: Int, toIndex: Int): Boolean {
        val current = getModels().toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            saveModelsInternal(current)
            return true
        }
        return false
    }

    @Synchronized
    fun getSelectedModel(): String {
        val stored = prefs.getString(KEY_SELECTED_MODEL, "")?.trim().orEmpty()
        val models = getModels()
        return if (stored.isNotBlank() && models.contains(stored)) {
            stored
        } else {
            models.firstOrNull() ?: DEFAULT_MODEL
        }
    }

    @Synchronized
    fun setSelectedModel(modelName: String) {
        val clean = modelName.trim()
        if (clean.isNotBlank()) {
            prefs.edit().putString(KEY_SELECTED_MODEL, clean).apply()
        }
    }

    @Synchronized
    fun isAutoSwitchOnQuota(): Boolean {
        return prefs.getBoolean(KEY_AUTO_SWITCH_QUOTA, false)
    }

    @Synchronized
    fun setAutoSwitchOnQuota(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SWITCH_QUOTA, enabled).apply()
    }

    private fun saveModelsInternal(models: List<String>) {
        val array = JSONArray()
        models.forEach { array.put(it) }
        prefs.edit().putString(KEY_MODELS_JSON, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "rekapin_model_prefs"
        private const val KEY_MODELS_JSON = "gemini_models_list_json"
        private const val KEY_SELECTED_MODEL = "selected_gemini_model"
        private const val KEY_AUTO_SWITCH_QUOTA = "auto_switch_model_on_quota"

        val DEFAULT_MODEL = GeminiConstants.GEMINI_MODEL
        val DEFAULT_MODELS = listOf(
            "gemini-3.8-flash",
            "gemini-3.6-flash",
            "gemini-3.7-flash",
            "gemini-3.5-flash-lite"
        )
    }
}
