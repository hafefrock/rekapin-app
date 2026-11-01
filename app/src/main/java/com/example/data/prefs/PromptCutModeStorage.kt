package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.PromptCutMode

class PromptCutModeStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getMode(): PromptCutMode {
        val saved = prefs.getString(KEY_MODE, null)
        return PromptCutMode.fromCode(saved)
    }

    @Synchronized
    fun setMode(mode: PromptCutMode) {
        prefs.edit().putString(KEY_MODE, mode.code).apply()
    }

    @Synchronized
    fun getMaxSceneDuration(): Int {
        return prefs.getInt(KEY_MAX_SCENE_DURATION, 5)
    }

    @Synchronized
    fun setMaxSceneDuration(seconds: Int) {
        prefs.edit().putInt(KEY_MAX_SCENE_DURATION, seconds).apply()
    }

    /**
     * Menyimpan mode potong prompt dan batas maksimum durasi adegan per analisis
     * agar terkunci konsisten untuk sesi analisis tersebut.
     */
    @Synchronized
    fun saveModeForAnalysis(analysisId: Long, mode: PromptCutMode, maxDurationSec: Int = 5) {
        prefs.edit()
            .putString(KEY_ANALYSIS_MODE_PREFIX + analysisId, mode.code)
            .putInt(KEY_ANALYSIS_MAX_DUR_PREFIX + analysisId, maxDurationSec)
            .apply()
    }

    @Synchronized
    fun getModeForAnalysis(analysisId: Long): PromptCutMode? {
        val raw = prefs.getString(KEY_ANALYSIS_MODE_PREFIX + analysisId, null) ?: return null
        return PromptCutMode.fromCode(raw)
    }

    @Synchronized
    fun getMaxDurationForAnalysis(analysisId: Long): Int {
        return prefs.getInt(KEY_ANALYSIS_MAX_DUR_PREFIX + analysisId, 5)
    }

    companion object {
        private const val PREFS_NAME = "rekapin_cut_mode_prefs"
        private const val KEY_MODE = "prompt_cut_mode"
        private const val KEY_MAX_SCENE_DURATION = "max_scene_duration"
        private const val KEY_ANALYSIS_MODE_PREFIX = "analysis_cut_mode_"
        private const val KEY_ANALYSIS_MAX_DUR_PREFIX = "analysis_cut_max_dur_"
    }
}
