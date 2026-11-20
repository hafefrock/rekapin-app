package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.ThinkingLevelMode

class ThinkingLevelStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getMode(): ThinkingLevelMode {
        val raw = prefs.getString(KEY_THINKING_LEVEL, null)
        return ThinkingLevelMode.fromCode(raw)
    }

    @Synchronized
    fun setMode(mode: ThinkingLevelMode) {
        prefs.edit().putString(KEY_THINKING_LEVEL, mode.code).apply()
    }

    companion object {
        private const val PREFS_NAME = "rekapin_thinking_prefs"
        private const val KEY_THINKING_LEVEL = "thinking_level_mode"
    }
}
