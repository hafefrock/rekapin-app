package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.RequestPacingMode

class RequestPacingStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getMode(): RequestPacingMode {
        val raw = prefs.getString(KEY_PACING_MODE, null)
        return RequestPacingMode.fromCode(raw)
    }

    @Synchronized
    fun setMode(mode: RequestPacingMode) {
        prefs.edit().putString(KEY_PACING_MODE, mode.code).apply()
    }

    companion object {
        private const val PREFS_NAME = "rekapin_pacing_prefs"
        private const val KEY_PACING_MODE = "request_pacing_mode"
    }
}
