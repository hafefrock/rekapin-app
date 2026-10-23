package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.ModelUsageInfo
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ModelUsageStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "rekapin_model_usage_prefs"
        private const val KEY_USAGE_PREFIX = "usage_count_"
        private const val KEY_DATE_PREFIX = "usage_date_"
        private const val KEY_LIMIT_PREFIX = "usage_limit_"
        private const val KEY_RESET_TIME_PREFIX = "usage_reset_time_"

        val PACIFIC_TIME_ZONE: TimeZone = TimeZone.getTimeZone("America/Los_Angeles")
    }

    /**
     * Mengembalikan tanggal hari ini di zona Pasifik (America/Los_Angeles) dalam format "YYYY-MM-DD".
     */
    fun getPacificDateToday(): String {
        val cal = Calendar.getInstance(PACIFIC_TIME_ZONE)
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
    }

    /**
     * Menghitung batas bawaan (default limit):
     * 500 jika nama model mengandung "flash-lite", 20 untuk model lainnya.
     */
    fun getDefaultLimit(model: String): Int {
        val normalized = model.trim().lowercase(Locale.US)
        return if (normalized.contains("flash-lite")) 500 else 20
    }

    /**
     * Mengambil batas harian tersimpan per model, atau default jika belum pernah diatur.
     */
    @Synchronized
    fun getLimit(model: String): Int {
        val key = KEY_LIMIT_PREFIX + model.trim()
        val saved = prefs.getInt(key, -1)
        return if (saved > 0) saved else getDefaultLimit(model)
    }

    /**
     * Menyimpan batas harian untuk model tertentu.
     */
    @Synchronized
    fun setLimit(model: String, limit: Int) {
        val validLimit = maxOf(1, limit)
        val key = KEY_LIMIT_PREFIX + model.trim()
        prefs.edit().putInt(key, validLimit).apply()
    }

    /**
     * Mengambil jumlah permintaan terpakai hari ini untuk model tertentu.
     * Otomatis mereset ke 0 jika:
     * 1. Ada custom reset time (dari 429 retryDelay) dan waktu sekarang sudah melewatinya.
     * 2. Atau tanggal Pasifik saat ini berbeda dengan tanggal tersimpan.
     */
    @Synchronized
    fun getUsage(model: String): Int {
        checkAndPerformResetIfNeeded(model)
        val countKey = KEY_USAGE_PREFIX + model.trim()
        return prefs.getInt(countKey, 0)
    }

    /**
     * Menambah hitungan permintaan terpakai untuk model tertentu sebesar 1.
     */
    @Synchronized
    fun incrementUsage(model: String): Int {
        val current = getUsage(model)
        val next = current + 1
        val countKey = KEY_USAGE_PREFIX + model.trim()
        val dateKey = KEY_DATE_PREFIX + model.trim()
        prefs.edit()
            .putInt(countKey, next)
            .putString(dateKey, getPacificDateToday())
            .apply()
        return next
    }

    /**
     * Mengatur manual hitungan permintaan terpakai untuk suatu model.
     */
    @Synchronized
    fun setUsage(model: String, count: Int) {
        val safeCount = maxOf(0, count)
        val countKey = KEY_USAGE_PREFIX + model.trim()
        val dateKey = KEY_DATE_PREFIX + model.trim()
        prefs.edit()
            .putInt(countKey, safeCount)
            .putString(dateKey, getPacificDateToday())
            .apply()
    }

    /**
     * Menandai model mengalami error 429 kuota harian (PerDay):
     * Set hitungan model menjadi penuh (terpakai = batas),
     * dan jika ada retryDelaySeconds, gunakan itu sebagai waktu reset yang tepat (menggantikan tengah malam Pasifik).
     */
    @Synchronized
    fun markQuotaExceeded(model: String, retryDelaySeconds: Long?) {
        val cleanModel = model.trim()
        val limit = getLimit(cleanModel)
        val countKey = KEY_USAGE_PREFIX + cleanModel
        val dateKey = KEY_DATE_PREFIX + cleanModel
        val resetKey = KEY_RESET_TIME_PREFIX + cleanModel

        val editor = prefs.edit()
        editor.putInt(countKey, limit)
        editor.putString(dateKey, getPacificDateToday())

        if (retryDelaySeconds != null && retryDelaySeconds > 0L) {
            val resetEpoch = System.currentTimeMillis() + (retryDelaySeconds * 1000L)
            editor.putLong(resetKey, resetEpoch)
        } else {
            editor.remove(resetKey)
        }
        editor.apply()
    }

    /**
     * Mengambil epoch millis waktu reset untuk model tertentu:
     * - Jika ada custom reset time dari 429 yang masih di masa depan, kembalikan itu.
     * - Jika tidak, kembalikan tengah malam Pasifik berikutnya (00:00:00 America/Los_Angeles).
     */
    @Synchronized
    fun getResetEpochMillis(model: String): Long {
        val cleanModel = model.trim()
        val resetKey = KEY_RESET_TIME_PREFIX + cleanModel
        val customReset = prefs.getLong(resetKey, 0L)
        val now = System.currentTimeMillis()

        if (customReset > now) {
            return customReset
        }

        return getNextPacificMidnightMillis()
    }

    /**
     * Menghitung epoch millis untuk tengah malam Pasifik berikutnya (00:00:00 America/Los_Angeles).
     */
    fun getNextPacificMidnightMillis(): Long {
        val cal = Calendar.getInstance(PACIFIC_TIME_ZONE).apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * Memformat perkiraan jam reset dalam waktu lokal HP (misal: "~14:00" atau "besok ~14:00").
     */
    fun getFormattedResetTime(model: String): String {
        val resetEpoch = getResetEpochMillis(model)
        val localTz = TimeZone.getDefault()

        val resetCal = Calendar.getInstance(localTz).apply {
            timeInMillis = resetEpoch
        }
        val nowCal = Calendar.getInstance(localTz)

        val isDifferentDay = resetCal.get(Calendar.DAY_OF_YEAR) != nowCal.get(Calendar.DAY_OF_YEAR) ||
                resetCal.get(Calendar.YEAR) != nowCal.get(Calendar.YEAR)

        val timeStr = String.format(
            Locale.getDefault(),
            "%02d:%02d",
            resetCal.get(Calendar.HOUR_OF_DAY),
            resetCal.get(Calendar.MINUTE)
        )

        return if (isDifferentDay) "besok ~$timeStr" else "~$timeStr"
    }

    /**
     * Mengambil status lengkap penggunaan kuota untuk suatu model.
     */
    @Synchronized
    fun getUsageInfo(model: String): ModelUsageInfo {
        val cleanModel = model.trim()
        val used = getUsage(cleanModel)
        val limit = getLimit(cleanModel)
        val remaining = maxOf(0, limit - used)
        val resetTime = getFormattedResetTime(cleanModel)
        return ModelUsageInfo(
            modelName = cleanModel,
            used = used,
            limit = limit,
            remaining = remaining,
            resetTimeFormatted = resetTime,
            isQuotaExceeded = used >= limit
        )
    }

    private fun checkAndPerformResetIfNeeded(model: String) {
        val cleanModel = model.trim()
        val countKey = KEY_USAGE_PREFIX + cleanModel
        val dateKey = KEY_DATE_PREFIX + cleanModel
        val resetKey = KEY_RESET_TIME_PREFIX + cleanModel

        val now = System.currentTimeMillis()
        val customReset = prefs.getLong(resetKey, 0L)
        val savedDate = prefs.getString(dateKey, null)
        val todayPacific = getPacificDateToday()

        var shouldReset = false

        if (customReset > 0L) {
            if (now >= customReset) {
                // Waktu retryDelay telah terlewati
                shouldReset = true
            }
        } else if (savedDate != null && savedDate != todayPacific) {
            // Hari Pasifik telah berganti
            shouldReset = true
        }

        if (shouldReset) {
            prefs.edit()
                .putInt(countKey, 0)
                .putString(dateKey, todayPacific)
                .remove(resetKey)
                .apply()
        }
    }
}
