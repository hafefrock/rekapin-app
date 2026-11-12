package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.util.FrameFingerprint
import com.example.util.OpeningEndingFingerprint
import org.json.JSONArray
import org.json.JSONObject

/** Local-only cross-episode OP/ED visual fingerprints, keyed by the chosen series source ID. */
class OpeningEndingStorage(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(analysisId: Long): OpeningEndingFingerprint? {
        val raw = prefs.getString(analysisId.toString(), null) ?: return null
        return try {
            val root = JSONObject(raw)
            OpeningEndingFingerprint(
                openingFrames = readFrames(root.optJSONArray("opening")),
                endingFrames = readFrames(root.optJSONArray("ending"))
            ).takeIf { it.openingFrames.isNotEmpty() || it.endingFrames.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    fun save(analysisId: Long, fingerprint: OpeningEndingFingerprint) {
        val root = JSONObject().apply {
            put("opening", writeFrames(fingerprint.openingFrames))
            put("ending", writeFrames(fingerprint.endingFrames))
        }
        prefs.edit().putString(analysisId.toString(), root.toString()).apply()
    }

    fun isAutoSkipOpEd(): Boolean = prefs.getBoolean(KEY_AUTO_SKIP_OP_ED, true)

    fun setAutoSkipOpEd(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SKIP_OP_ED, enabled).apply()
    }

    fun getSkipNote(analysisId: Long): String? =
        prefs.getString(KEY_SKIP_NOTE_PREFIX + analysisId, null)

    fun saveSkipNote(analysisId: Long, note: String) {
        prefs.edit().putString(KEY_SKIP_NOTE_PREFIX + analysisId, note).apply()
    }

    fun removeSkipNote(analysisId: Long) {
        prefs.edit().remove(KEY_SKIP_NOTE_PREFIX + analysisId).apply()
    }

    fun remove(analysisId: Long) {
        prefs.edit()
            .remove(analysisId.toString())
            .remove(KEY_SKIP_NOTE_PREFIX + analysisId)
            .apply()
    }

    private fun writeFrames(frames: List<FrameFingerprint>): JSONArray = JSONArray().apply {
        frames.forEach { frame ->
            put(JSONObject().apply {
                put("timeSec", frame.timeSec)
                put("hash", frame.hash.toString())
            })
        }
    }

    private fun readFrames(array: JSONArray?): List<FrameFingerprint> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val time = item.optLong("timeSec", -1L)
                val hash = item.optString("hash", "").toLongOrNull()
                if (time >= 0L && hash != null) add(FrameFingerprint(time, hash))
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "rekapin_opening_ending_prefs"
        private const val KEY_AUTO_SKIP_OP_ED = "auto_skip_op_ed"
        private const val KEY_SKIP_NOTE_PREFIX = "skip_note_"
    }
}
