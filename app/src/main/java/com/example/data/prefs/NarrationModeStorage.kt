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

    /**
     * Menyimpan mode narasi yang terkunci untuk satu analisis tertentu.
     */
    @Synchronized
    fun saveModeForAnalysis(analysisId: Long, mode: NarrationMode) {
        prefs.edit().putString(KEY_ANALYSIS_MODE_PREFIX + analysisId, mode.code).apply()
    }

    /**
     * Mengambil mode narasi yang tersimpan untuk id analisis.
     * Mengembalikan null jika belum pernah disimpan.
     */
    @Synchronized
    fun getModeForAnalysis(analysisId: Long): NarrationMode? {
        val raw = prefs.getString(KEY_ANALYSIS_MODE_PREFIX + analysisId, null) ?: return null
        return NarrationMode.fromDisplayName(raw)
    }

    @Synchronized
    fun removeModeForAnalysis(analysisId: Long) {
        prefs.edit().remove(KEY_ANALYSIS_MODE_PREFIX + analysisId).apply()
    }

    companion object {
        private const val PREFS_NAME = "rekapin_narration_mode_prefs"
        private const val KEY_MODE = "narration_mode"
        private const val KEY_ANALYSIS_MODE_PREFIX = "analysis_mode_"
    }
}
