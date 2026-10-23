package com.example.util

import com.example.data.api.GeminiConstants
import java.util.Locale

enum class SegmentDurationOption(val label: String, val seconds: Long?) {
    AUTO("Otomatis", null),
    THREE_MIN("3 Menit", 180L),
    FIVE_MIN("5 Menit", 300L)
}

data class SegmentSpan(
    val segmentIndex: Int, // 1-based index (1, 2, 3...)
    val startSec: Long,
    val endSec: Long
) {
    val durationSec: Long get() = endSec - startSec
}

data class SegmentPlanSummary(
    val totalSeconds: Long,
    val totalDurationFormatted: String,
    val segmentDurationLabel: String,
    val segmentCount: Int,
    val estimatedScenesCount: Int,
    val totalRequestsCount: Int, // segmen + 1 narasi
    val spans: List<SegmentSpan>,
    val rangeStartSec: Long = 0L,
    val rangeEndSec: Long = totalSeconds,
    val rangeDurationSec: Long = (rangeEndSec - rangeStartSec).coerceAtLeast(1L),
    val rangeFormatted: String = ""
)

object SegmentCalculator {
    const val DEFAULT_SEGMENT_DURATION_SECONDS = GeminiConstants.SEGMENT_DURATION_SECONDS // 180L
    const val MIN_LAST_SEGMENT_SECONDS = 90L
    const val MIN_RANGE_SECONDS = 10L

    /**
     * Format detik menjadi "mm:ss" atau "h:mm:ss" jika durasi >= 1 jam.
     */
    fun formatTimeSeconds(totalSec: Long): String {
        val safeSec = totalSec.coerceAtLeast(0L)
        val h = safeSec / 3600
        val m = (safeSec % 3600) / 60
        val s = safeSec % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    /**
     * Mem-parsing string waktu pengguna ("mm:ss", "h:mm:ss", atau detik angka) menjadi nilai detik (Long).
     * Mengembalikan null jika format tidak valid.
     */
    fun parseTimeSeconds(timeStr: String): Long? {
        val clean = timeStr.trim()
        if (clean.isBlank()) return null
        val parts = clean.split(":")
        return try {
            when (parts.size) {
                1 -> {
                    val s = parts[0].toLong()
                    if (s >= 0) s else null
                }
                2 -> {
                    val m = parts[0].toLong()
                    val s = parts[1].toLong()
                    if (m < 0 || s < 0 || s >= 60) null else m * 60 + s
                }
                3 -> {
                    val h = parts[0].toLong()
                    val m = parts[1].toLong()
                    val s = parts[2].toLong()
                    if (h < 0 || m < 0 || m >= 60 || s < 0 || s >= 60) null else h * 3600 + m * 60 + s
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Menentukan panjang segmen dalam detik berdasarkan opsi dan durasi (atau panjang rentang).
     * Aturan Otomatis:
     * - Durasi sampai 4,5 menit (<= 270 detik) = 1 segmen (seluruh rentang)
     * - Durasi sampai 12 menit (<= 720 detik) = segmen 3 menit (180 detik)
     * - Durasi di atas 12 menit (> 720 detik) = segmen 5 menit (300 detik)
     */
    fun determineSegmentDuration(totalSeconds: Long, option: SegmentDurationOption): Long {
        return when (option) {
            SegmentDurationOption.AUTO -> {
                when {
                    totalSeconds <= 270L -> totalSeconds
                    totalSeconds <= 720L -> 180L
                    else -> 300L
                }
            }
            SegmentDurationOption.THREE_MIN -> 180L
            SegmentDurationOption.FIVE_MIN -> 300L
        }
    }

    /**
     * Menghitung label keterangan panjang segmen terpilih untuk ringkasan.
     */
    fun getSegmentDurationLabel(totalSeconds: Long, option: SegmentDurationOption): String {
        return when (option) {
            SegmentDurationOption.AUTO -> {
                when {
                    totalSeconds <= 270L -> "Otomatis (1 Segmen Penuh)"
                    totalSeconds <= 720L -> "Otomatis (3 Menit)"
                    else -> "Otomatis (5 Menit)"
                }
            }
            SegmentDurationOption.THREE_MIN -> "3 Menit"
            SegmentDurationOption.FIVE_MIN -> "5 Menit"
        }
    }

    /**
     * Menghitung pembagian segmen video berdasarkan panjang rentang yang dipilih.
     * Aturan:
     * 1. startSec dan endSec tiap segmen memakai WAKTU ABSOLUT dari awal video (ditambah offset awal rentang).
     * 2. Panjang rentang = rangeEndSec - rangeStartSec (minimal 1 detik).
     * 3. Aturan segmen otomatis, penggabungan segmen terakhir di bawah 90 detik, dan pembatasan akhir ke batas rentang tetap berlaku.
     */
    fun calculateSegments(
        totalSeconds: Long,
        option: SegmentDurationOption = SegmentDurationOption.AUTO,
        minLastSegmentDuration: Long = MIN_LAST_SEGMENT_SECONDS,
        rangeStartSec: Long = 0L,
        rangeEndSec: Long = totalSeconds
    ): List<SegmentSpan> {
        val safeTotal = totalSeconds.coerceAtLeast(1L)
        val safeStart = rangeStartSec.coerceIn(0L, safeTotal)
        val safeEnd = rangeEndSec.coerceIn(safeStart + 1L, safeTotal)
        val rangeDuration = (safeEnd - safeStart).coerceAtLeast(1L)

        // Aturan Otomatis: durasi rentang sampai 4,5 menit = 1 segmen penuh rentang
        if (option == SegmentDurationOption.AUTO && rangeDuration <= 270L) {
            return listOf(
                SegmentSpan(
                    segmentIndex = 1,
                    startSec = safeStart,
                    endSec = safeEnd
                )
            )
        }

        val segmentDuration = determineSegmentDuration(rangeDuration, option)
        if (rangeDuration <= segmentDuration) {
            return listOf(
                SegmentSpan(
                    segmentIndex = 1,
                    startSec = safeStart,
                    endSec = safeEnd
                )
            )
        }

        val spans = mutableListOf<SegmentSpan>()
        var relativeStart = 0L
        var index = 1

        while (relativeStart < rangeDuration) {
            val relativeNormalEnd = minOf(relativeStart + segmentDuration, rangeDuration)
            val remainingAfter = rangeDuration - relativeNormalEnd

            // Jika sisa durasi setelah segmen ini ada (>0) tapi kurang dari 90 detik,
            // gabungkan seluruh sisa tersebut ke segmen ini.
            if (remainingAfter in 1 until minLastSegmentDuration) {
                spans.add(
                    SegmentSpan(
                        segmentIndex = index,
                        startSec = safeStart + relativeStart,
                        endSec = safeEnd // Dibatasi ketat ke waktu absolut akhir rentang
                    )
                )
                break
            } else {
                spans.add(
                    SegmentSpan(
                        segmentIndex = index,
                        startSec = safeStart + relativeStart,
                        endSec = safeStart + relativeNormalEnd
                    )
                )
                relativeStart = relativeNormalEnd
                index++
            }
        }

        return spans
    }

    /**
     * Menghasilkan ringkasan rencana analisis sebelum tombol Mulai Analisis ditekan.
     */
    fun calculatePlanSummary(
        totalSeconds: Long,
        option: SegmentDurationOption = SegmentDurationOption.AUTO,
        intervalSeconds: Int = 5,
        rangeStartSec: Long = 0L,
        rangeEndSec: Long = totalSeconds
    ): SegmentPlanSummary {
        val safeTotal = totalSeconds.coerceAtLeast(1L)
        val safeStart = rangeStartSec.coerceIn(0L, safeTotal)
        val safeEnd = rangeEndSec.coerceIn(safeStart + 1L, safeTotal)
        val rangeDuration = (safeEnd - safeStart).coerceAtLeast(1L)

        val spans = calculateSegments(
            totalSeconds = safeTotal,
            option = option,
            minLastSegmentDuration = MIN_LAST_SEGMENT_SECONDS,
            rangeStartSec = safeStart,
            rangeEndSec = safeEnd
        )
        val segmentCount = spans.size
        val safeInterval = intervalSeconds.coerceAtLeast(1)
        val estimatedScenes = (rangeDuration / safeInterval).toInt().coerceAtLeast(1)
        val totalRequests = segmentCount + 1 // segmen + 1 narasi

        val fullDurationStr = formatTimeSeconds(safeTotal)
        val startStr = formatTimeSeconds(safeStart)
        val endStr = formatTimeSeconds(safeEnd)

        val rangeFormatted = if (safeStart == 0L && safeEnd >= safeTotal) {
            "$startStr-$endStr (Seluruh video, $fullDurationStr)"
        } else {
            "$startStr-$endStr dari $fullDurationStr"
        }

        val totalDurationFormatted = String.format(Locale.US, "%s (%d detik)", fullDurationStr, safeTotal)

        return SegmentPlanSummary(
            totalSeconds = safeTotal,
            totalDurationFormatted = totalDurationFormatted,
            segmentDurationLabel = getSegmentDurationLabel(rangeDuration, option),
            segmentCount = segmentCount,
            estimatedScenesCount = estimatedScenes,
            totalRequestsCount = totalRequests,
            spans = spans,
            rangeStartSec = safeStart,
            rangeEndSec = safeEnd,
            rangeDurationSec = rangeDuration,
            rangeFormatted = rangeFormatted
        )
    }
}
