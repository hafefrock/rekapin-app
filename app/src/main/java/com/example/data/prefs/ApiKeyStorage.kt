package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

class ApiKeyStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, apiKey.trim()).apply()
    }

    fun getCustomApiKey(): String {
        return prefs.getString(KEY_GEMINI_API_KEY, "")?.trim() ?: ""
    }

    fun getEffectiveApiKey(): String {
        val userKey = getCustomApiKey()
        if (userKey.isNotBlank()) {
            return userKey
        }
        val buildKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }
        if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") {
            return buildKey.trim()
        }
        return ""
    }

    fun hasApiKey(): Boolean {
        return getEffectiveApiKey().isNotBlank()
    }

    companion object {
        private const val PREFS_NAME = "rekapin_secure_prefs"
        private const val KEY_GEMINI_API_KEY = "custom_gemini_api_key"
    }
}
