package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Penyimpanan SharedPreferences untuk menghubungkan analisis baru dengan analisis sumber seri.
 * Kunci = id analisis baru (Long.toString()).
 * Nilai = id analisis sumber (Long).
 */
class SeriesStorage(context: Context) {
    companion object {
        private const val PREFS_NAME = "rekapin_series_prefs"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Menyimpan ID analisis sumber untuk analisis baru.
     * Kunci adalah id analisis baru sesuai instruksi.
     */
    fun saveSourceAnalysisId(newAnalysisId: Long, sourceAnalysisId: Long) {
        prefs.edit().putLong(newAnalysisId.toString(), sourceAnalysisId).apply()
    }

    /**
     * Mengambil ID analisis sumber berdasarkan ID analisis baru.
     * Mengembalikan null jika tidak ada relasi seri tersimpan.
     */
    fun getSourceAnalysisId(newAnalysisId: Long): Long? {
        val key = newAnalysisId.toString()
        return if (prefs.contains(key)) {
            val id = prefs.getLong(key, -1L)
            if (id > 0L) id else null
        } else {
            null
        }
    }

    /**
     * Menghapus relasi seri untuk analisis tertentu jika diperlukan.
     */
    fun removeSourceAnalysisId(newAnalysisId: Long) {
        prefs.edit().remove(newAnalysisId.toString()).apply()
    }
}
