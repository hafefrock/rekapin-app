package com.example.util

import org.json.JSONObject

data class QuotaCheckResult(
    val isDailyQuota: Boolean,
    val retryDelaySeconds: Long?,
    val formattedWaitTime: String,
    val userMessage: String
)

object QuotaHelper {

    /**
     * Memeriksa apakah respons HTTP 429 memuat kuota harian ("PerDay")
     * atau memiliki retryDelay lebih dari 5 menit (300 detik).
     */
    fun checkQuotaError(statusCode: Int, responseBody: String): QuotaCheckResult {
        if (statusCode != 429) {
            return QuotaCheckResult(
                isDailyQuota = false,
                retryDelaySeconds = null,
                formattedWaitTime = "",
                userMessage = ""
            )
        }

        // Cek apakah error 429 memuat kata "PerDay" (case-insensitive)
        val hasPerDay = responseBody.contains("PerDay", ignoreCase = true) ||
                responseBody.contains("per_day", ignoreCase = true) ||
                responseBody.contains("per day", ignoreCase = true)

        val retryDelaySeconds = extractRetryDelaySeconds(responseBody)
        // Batas 5 menit = 300 detik
        val isDelayOver5Min = retryDelaySeconds != null && retryDelaySeconds > 300L

        val isDaily = hasPerDay || isDelayOver5Min
        val formattedTime = formatWaitTimeHoursAndMinutes(retryDelaySeconds)
        val userMsg = if (isDaily) {
            "Kuota harian habis. Coba lagi sekitar $formattedTime"
        } else {
            "Batas kuota per menit Gemini terlampaui (HTTP 429)."
        }

        return QuotaCheckResult(
            isDailyQuota = isDaily,
            retryDelaySeconds = retryDelaySeconds,
            formattedWaitTime = formattedTime,
            userMessage = userMsg
        )
    }

    /**
     * Mengekstrak retryDelay dalam satuan detik dari payload JSON error Gemini atau RPC details.
     */
    fun extractRetryDelaySeconds(responseBody: String): Long? {
        if (responseBody.isBlank()) return null

        // 1. Ekstraksi terstruktur via JSONObject
        try {
            val json = JSONObject(responseBody)
            val errorObj = json.optJSONObject("error")
            val details = errorObj?.optJSONArray("details")
            if (details != null) {
                for (i in 0 until details.length()) {
                    val detail = details.optJSONObject(i) ?: continue
                    val delayVal = detail.opt("retryDelay") ?: detail.opt("retry_delay")
                    if (delayVal != null) {
                        val parsed = parseSecondsString(delayVal.toString())
                        if (parsed != null) return parsed
                    }
                }
            }
            val topDelay = errorObj?.opt("retryDelay") ?: json.opt("retryDelay")
            if (topDelay != null) {
                val parsed = parseSecondsString(topDelay.toString())
                if (parsed != null) return parsed
            }
        } catch (_: Exception) {
            // Lanjut ke fallback regex
        }

        // 2. Fallback regex untuk format seperti "retryDelay": "82451.78s" atau "retryDelay": "3600s" atau "retryDelay": 600
        val regex = """"(?:retryDelay|retry_delay)"\s*:\s*"?([0-9.]+)\s*s?"?""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(responseBody)
        if (match != null) {
            val numStr = match.groupValues[1]
            val sec = numStr.toDoubleOrNull()?.toLong()
            if (sec != null) return sec
        }

        return null
    }

    private fun parseSecondsString(raw: String): Long? {
        val cleaned = raw.trim().removeSuffix("s").trim()
        return cleaned.toDoubleOrNull()?.toLong()
    }

    /**
     * Memformat waktu durasi (detik) ke dalam jam dan menit.
     * Contoh:
     * - 7500 detik -> "2 jam 5 menit"
     * - 3600 detik -> "1 jam"
     * - 540 detik -> "9 menit"
     * - null / 0 -> "24 jam"
     */
    fun formatWaitTimeHoursAndMinutes(seconds: Long?): String {
        if (seconds == null || seconds <= 0L) {
            return "24 jam"
        }

        val hours = seconds / 3600
        val remainderSeconds = seconds % 3600
        val minutes = (remainderSeconds + 59) / 60 // Dibulatkan ke menit terdekat ke atas

        // Tangani jika pembulatan menit mencapai 60
        val finalHours = hours + (minutes / 60)
        val finalMinutes = minutes % 60

        return when {
            finalHours > 0 && finalMinutes > 0 -> "$finalHours jam $finalMinutes menit"
            finalHours > 0 -> "$finalHours jam"
            else -> "$finalMinutes menit"
        }
    }
}
