package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.BibleEpisode
import com.example.data.model.BibleGlossaryItem
import com.example.data.model.BibleLocation
import com.example.data.model.BibleRelationship
import com.example.data.model.SeriesBible
import org.json.JSONArray
import org.json.JSONObject

/**
 * Penyimpanan SharedPreferences untuk Memori Seri (Series Bible).
 * Kunci = id analisis (Long.toString()), nilai = JSON string.
 */
class SeriesBibleStorage(context: Context) {
    companion object {
        private const val PREFS_NAME = "rekapin_series_bible_prefs"
        private const val KEY_AUTO_BUILD = "pref_auto_build_series_bible"
        private const val KEY_PREPEND_RECAP = "pref_prepend_recap_narration"

        /**
         * Mengubah objek SeriesBible menjadi string JSON yang rapi dan mematuhi batas ukuran.
         */
        fun toJson(bible: SeriesBible): String {
            val trimmed = bible.trimToLimits()
            val obj = JSONObject()

            val locArray = JSONArray()
            trimmed.locations.forEach { loc ->
                locArray.put(JSONObject().apply {
                    put("name", loc.name)
                    put("description", loc.description)
                })
            }
            obj.put("locations", locArray)

            val gloArray = JSONArray()
            trimmed.glossary.forEach { g ->
                gloArray.put(JSONObject().apply {
                    put("term", g.term)
                    put("definition", g.definition)
                })
            }
            obj.put("glossary", gloArray)

            val relArray = JSONArray()
            trimmed.relationships.forEach { r ->
                relArray.put(JSONObject().apply {
                    put("characterA", r.characterA)
                    put("characterB", r.characterB)
                    put("relationship", r.relationship)
                })
            }
            obj.put("relationships", relArray)

            obj.put("plotSoFar", trimmed.plotSoFar)

            val epArray = JSONArray()
            trimmed.episodes.forEach { ep ->
                epArray.put(JSONObject().apply {
                    put("videoName", ep.videoName)
                    put("recap", ep.recap)
                })
            }
            obj.put("episodes", epArray)

            return obj.toString()
        }

        /**
         * Mengurai string JSON menjadi SeriesBible dengan toleransi terhadap kode markdown (```json / ```).
         * Jika string kosong atau parsing gagal, mengembalikan null tanpa menimbulkan exception/crash.
         */
        fun fromJson(rawJson: String?): SeriesBible? {
            if (rawJson.isNullOrBlank()) return null
            return try {
                var cleaned = rawJson.trim()
                if (cleaned.startsWith("```json", ignoreCase = true)) {
                    cleaned = cleaned.substring(7)
                } else if (cleaned.startsWith("```")) {
                    cleaned = cleaned.substring(3)
                }
                if (cleaned.endsWith("```")) {
                    cleaned = cleaned.substring(0, cleaned.length - 3)
                }
                cleaned = cleaned.trim()

                val obj = JSONObject(cleaned)

                val locations = mutableListOf<BibleLocation>()
                val locArr = obj.optJSONArray("locations")
                if (locArr != null) {
                    for (i in 0 until locArr.length()) {
                        val item = locArr.optJSONObject(i) ?: continue
                        val name = item.optString("name", "").trim()
                        val desc = item.optString("description", "").trim()
                        if (name.isNotBlank()) {
                            locations.add(BibleLocation(name, desc))
                        }
                    }
                }

                val glossary = mutableListOf<BibleGlossaryItem>()
                val gloArr = obj.optJSONArray("glossary")
                if (gloArr != null) {
                    for (i in 0 until gloArr.length()) {
                        val item = gloArr.optJSONObject(i) ?: continue
                        val term = item.optString("term", "").trim()
                        val def = item.optString("definition", "").trim()
                        if (term.isNotBlank()) {
                            glossary.add(BibleGlossaryItem(term, def))
                        }
                    }
                }

                val relationships = mutableListOf<BibleRelationship>()
                val relArr = obj.optJSONArray("relationships")
                if (relArr != null) {
                    for (i in 0 until relArr.length()) {
                        val item = relArr.optJSONObject(i) ?: continue
                        val a = item.optString("characterA", item.optString("a", "")).trim()
                        val b = item.optString("characterB", item.optString("b", "")).trim()
                        val rel = item.optString("relationship", item.optString("relasi", "")).trim()
                        if (a.isNotBlank() && b.isNotBlank()) {
                            relationships.add(BibleRelationship(a, b, rel))
                        }
                    }
                }

                val plotSoFar = obj.optString("plotSoFar", "").trim()

                val episodes = mutableListOf<BibleEpisode>()
                val epArr = obj.optJSONArray("episodes")
                if (epArr != null) {
                    for (i in 0 until epArr.length()) {
                        val item = epArr.optJSONObject(i) ?: continue
                        val vName = item.optString("videoName", "").trim()
                        val recap = item.optString("recap", "").trim()
                        if (vName.isNotBlank() || recap.isNotBlank()) {
                            episodes.add(BibleEpisode(vName, recap))
                        }
                    }
                }

                SeriesBible(
                    locations = locations,
                    glossary = glossary,
                    relationships = relationships,
                    plotSoFar = plotSoFar,
                    episodes = episodes
                ).trimToLimits()
            } catch (e: Exception) {
                null
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Menyimpan memori seri untuk suatu analisis.
     */
    fun saveBible(analysisId: Long, bible: SeriesBible) {
        val json = toJson(bible)
        prefs.edit().putString(analysisId.toString(), json).apply()
    }

    /**
     * Mengambil memori seri untuk suatu analisis. Mengembalikan null jika belum ada atau gagal dibaca.
     */
    fun getBible(analysisId: Long): SeriesBible? {
        val key = analysisId.toString()
        val json = prefs.getString(key, null)
        return fromJson(json)
    }

    /**
     * Menghapus memori seri ketika analisis dihapus dari riwayat.
     */
    fun removeBible(analysisId: Long) {
        prefs.edit().remove(analysisId.toString()).apply()
    }

    /**
     * Saklar: Bangun memori seri otomatis setelah analisis selesai (default: true).
     */
    fun isAutoBuildEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_BUILD, true)
    }

    fun setAutoBuildEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BUILD, enabled).apply()
    }

    /**
     * Saklar: Tambahkan recap 'Sebelumnya...' di awal narasi (default: false).
     */
    fun isPrependRecapEnabled(): Boolean {
        return prefs.getBoolean(KEY_PREPEND_RECAP, false)
    }

    fun setPrependRecapEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PREPEND_RECAP, enabled).apply()
    }
}
