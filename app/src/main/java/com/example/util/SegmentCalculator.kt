package com.example.util

import com.example.data.api.GeminiConstants

data class SegmentSpan(
    val segmentIndex: Int, // 1-based index (1, 2, 3...)
    val startSec: Long,
    val endSec: Long
) {
    val durationSec: Long get() = endSec - startSec
}

object SegmentCalculator {
    const val DEFAULT_SEGMENT_DURATION_SECONDS = GeminiConstants.SEGMENT_DURATION_SECONDS // 180L
    const val MIN_LAST_SEGMENT_SECONDS = 90L

    /**
     * Menghitung pembagian segmen video berdasarkan durasi asli video.
     * Aturan:
     * 1. End offset segmen terakhir tidak boleh melebihi durasi sebenarnya (dibatasi ke totalSeconds).
     * 2. Jika segmen terakhir lebih pendek dari 90 detik, gabungkan ke segmen sebelumnya.
     */
    fun calculateSegments(
        totalSeconds: Long,
        segmentDuration: Long = DEFAULT_SEGMENT_DURATION_SECONDS,
        minLastSegmentDuration: Long = MIN_LAST_SEGMENT_SECONDS
    ): List<SegmentSpan> {
        val safeTotal = totalSeconds.coerceAtLeast(1L)
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
            // gabungkan seluruh sisa tersebut ke segmen ini (segmen sebelumnya).
            if (remainingAfter in 1 until minLastSegmentDuration) {
                spans.add(
                    SegmentSpan(
                        segmentIndex = index,
                        startSec = currentStart,
                        endSec = safeTotal // Dibatasi ketat ke durasi video sebenarnya
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
}
