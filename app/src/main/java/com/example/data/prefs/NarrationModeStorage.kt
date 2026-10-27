package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.NarrationMode

class NarrationModeStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getMode(): NarrationMode {
        val saved = prefs.getString(KEY_MODE, null)
        return NarrationMode.fromDisplayName(saved)
    }

    @Synchronized
    fun getModeDisplayName(): String {
        return getMode().displayName
    }

    @Synchronized
    fun setMode(mode: NarrationMode) {
        prefs.edit().putString(KEY_MODE, mode.displayName).apply()
    }

    @Synchronized
    fun setMode(displayName: String) {
        setMode(NarrationMode.fromDisplayName(displayName))
    }

    companion object {
        private const val PREFS_NAME = "rekapin_narration_mode_prefs"
        private const val KEY_MODE = "narration_mode"
    }
}
