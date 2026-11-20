package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

class StageTimingStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun addStageDuration(analysisId: Long, stage: String, durationMs: Long) {
        if (durationMs < 0L) return
        val current = getTimings(analysisId).toMutableMap()
        val prev = current[stage] ?: 0L
        current[stage] = prev + durationMs
        saveMap(analysisId, current)
    }

    @Synchronized
    fun addSegmentTiming(analysisId: Long, durationMs: Long, segmentCount: Int = 1) {
        if (durationMs < 0L && segmentCount <= 0) return
        val current = getTimings(analysisId).toMutableMap()
        if (durationMs >= 0L) {
            current[STAGE_SEGMENT_ANALYSIS] = (current[STAGE_SEGMENT_ANALYSIS] ?: 0L) + durationMs
        }
        if (segmentCount > 0) {
            current[KEY_SEGMENT_COUNT] = (current[KEY_SEGMENT_COUNT] ?: 0L) + segmentCount.toLong()
        }
        saveMap(analysisId, current)
    }

    @Synchronized
    fun mergeTimings(analysisId: Long, deltaTimings: Map<String, Long>) {
        if (deltaTimings.isEmpty()) return
        val current = getTimings(analysisId).toMutableMap()
        deltaTimings.forEach { (key, value) ->
            if (value >= 0L) {
                current[key] = (current[key] ?: 0L) + value
            }
        }
        saveMap(analysisId, current)
    }

    @Synchronized
    fun getTimings(analysisId: Long): Map<String, Long> {
        val raw = prefs.getString(KEY_PREFIX + analysisId, null) ?: return emptyMap()
        return parseJsonToOrderedMap(raw)
    }

    @Synchronized
    fun remove(analysisId: Long) {
        prefs.edit().remove(KEY_PREFIX + analysisId).apply()
    }

    private fun saveMap(analysisId: Long, map: Map<String, Long>) {
        val json = JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(KEY_PREFIX + analysisId, json.toString()).apply()
    }

    private fun parseJsonToOrderedMap(raw: String): Map<String, Long> {
        return try {
            val json = JSONObject(raw)
            val result = linkedMapOf<String, Long>()
            ORDERED_STAGES.forEach { stage ->
                if (json.has(stage)) {
                    result[stage] = json.optLong(stage, 0L).coerceAtLeast(0L)
                }
            }
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (!result.containsKey(key)) {
                    result[key] = json.optLong(key, 0L).coerceAtLeast(0L)
                }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    companion object {
        private const val PREFS_NAME = "rekapin_stage_timing_prefs"
        private const val KEY_PREFIX = "timings_"

        const val STAGE_OP_ED = "Deteksi OP/ED"
        const val STAGE_SCENE_DETECTION = "Deteksi adegan"
        const val STAGE_REPEATED_SCENE = "Cari adegan berulang"
        const val STAGE_UPLOAD = "Unggah video"
        const val STAGE_VERIFY_FILE = "Verifikasi file"
        const val STAGE_AUDIO_DETECTION = "Deteksi suara"
        const val STAGE_SEGMENT_ANALYSIS = "Analisis segmen"
        const val STAGE_PACING_WAIT = "Jeda antar-permintaan"
        const val STAGE_FINAL_NARRATION = "Narasi akhir"
        const val STAGE_SERIES_BIBLE = "Memori seri"
        const val STAGE_TOTAL = "Total"
        const val KEY_SEGMENT_COUNT = "_segment_count"

        val ORDERED_STAGES = listOf(
            STAGE_OP_ED,
            STAGE_SCENE_DETECTION,
            STAGE_REPEATED_SCENE,
            STAGE_UPLOAD,
            STAGE_VERIFY_FILE,
            STAGE_AUDIO_DETECTION,
            STAGE_SEGMENT_ANALYSIS,
            STAGE_PACING_WAIT,
            STAGE_FINAL_NARRATION,
            STAGE_SERIES_BIBLE,
            STAGE_TOTAL
        )

        fun formatDuration(durationMs: Long): String {
            val safeMs = durationMs.coerceAtLeast(0L)
            val totalSeconds = safeMs / 1000L
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L

            return when {
                hours > 0L -> if (minutes > 0L) "$hours j $minutes mnt" else "$hours j"
                minutes > 0L -> if (seconds > 0L) "$minutes mnt $seconds dtk" else "$minutes mnt"
                else -> "$seconds dtk"
            }
        }
    }
}
