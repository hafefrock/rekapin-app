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
    val spans: List<SegmentSpan>
)

object SegmentCalculator {
    const val DEFAULT_SEGMENT_DURATION_SECONDS = GeminiConstants.SEGMENT_DURATION_SECONDS // 180L
    const val MIN_LAST_SEGMENT_SECONDS = 90L

    /**
     * Menentukan panjang segmen dalam detik berdasarkan opsi dan durasi video.
     * Aturan Otomatis:
     * - Durasi sampai 4,5 menit (<= 270 detik) = 1 segmen (seluruh durasi)
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
     * Menghitung pembagian segmen video berdasarkan durasi asli video dan opsi panjang segmen.
     * Aturan:
     * 1. End offset segmen terakhir tidak boleh melebihi durasi sebenarnya (dibatasi ke totalSeconds).
     * 2. Jika segmen terakhir lebih pendek dari 90 detik, gabungkan ke segmen sebelumnya.
     */
    fun calculateSegments(
        totalSeconds: Long,
        option: SegmentDurationOption = SegmentDurationOption.AUTO,
        minLastSegmentDuration: Long = MIN_LAST_SEGMENT_SECONDS
    ): List<SegmentSpan> {
        val safeTotal = totalSeconds.coerceAtLeast(1L)

        // Aturan Otomatis: durasi sampai 4,5 menit = 1 segmen
        if (option == SegmentDurationOption.AUTO && safeTotal <= 270L) {
            return listOf(
                SegmentSpan(
                    segmentIndex = 1,
                    startSec = 0L,
                    endSec = safeTotal
                )
            )
        }

        val segmentDuration = determineSegmentDuration(safeTotal, option)
        if (safeTotal <= segmentDuration) {
            return listOf(
                SegmentSpan(
                    segmentIndex = 1,
                    startSec = 0L,
                    endSec = safeTotal
                )
            )
        }

        val spans = mutableListOf<SegmentSpan>()
        var currentStart = 0L
        var index = 1

        while (currentStart < safeTotal) {
            val normalEnd = minOf(currentStart + segmentDuration, safeTotal)
            val remainingAfter = safeTotal - normalEnd

            // Jika sisa durasi setelah segmen ini ada (>0) tapi kurang dari 90 detik,
            // gabungkan seluruh sisa tersebut ke segmen ini.
            if (remainingAfter in 1 until minLastSegmentDuration) {
                spans.add(
                    SegmentSpan(
                        segmentIndex = index,
                        startSec = currentStart,
                        endSec = safeTotal // Dibatasi ketat ke durasi sebenarnya
                    )
                )
                break
            } else {
                spans.add(
                    SegmentSpan(
                        segmentIndex = index,
                        startSec = currentStart,
                        endSec = normalEnd
                    )
                )
                currentStart = normalEnd
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
        intervalSeconds: Int = 5
    ): SegmentPlanSummary {
        val safeTotal = totalSeconds.coerceAtLeast(1L)
        val spans = calculateSegments(safeTotal, option)
        val segmentCount = spans.size
        val safeInterval = intervalSeconds.coerceAtLeast(1)
        val estimatedScenes = (safeTotal / safeInterval).toInt().coerceAtLeast(1)
        val totalRequests = segmentCount + 1 // X segmen + 1 narasi

        val minutes = safeTotal / 60
        val seconds = safeTotal % 60
        val durationFormatted = String.format(Locale.US, "%02d:%02d (%d detik)", minutes, seconds, safeTotal)

        return SegmentPlanSummary(
            totalSeconds = safeTotal,
            totalDurationFormatted = durationFormatted,
            segmentDurationLabel = getSegmentDurationLabel(safeTotal, option),
            segmentCount = segmentCount,
            estimatedScenesCount = estimatedScenes,
            totalRequestsCount = totalRequests,
            spans = spans
        )
    }
}
