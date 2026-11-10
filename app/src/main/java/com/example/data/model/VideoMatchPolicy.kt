package com.example.data.model

/**
 * Conservative identity check used before resuming a saved analysis.
 *
 * Unknown metadata must never be treated as a match: a false positive can append
 * results from a different video to an existing analysis.
 */
object VideoMatchPolicy {
    private const val MAX_DURATION_DIFFERENCE_MS = 2_000L

    fun matches(
        savedDurationMs: Long,
        savedSizeBytes: Long,
        selectedDurationMs: Long,
        selectedSizeBytes: Long
    ): Boolean {
        if (savedDurationMs <= 0L || selectedDurationMs <= 0L) return false
        if (savedSizeBytes <= 0L || selectedSizeBytes <= 0L) return false
        if (savedSizeBytes != selectedSizeBytes) return false

        val durationDifference = if (savedDurationMs >= selectedDurationMs) {
            savedDurationMs - selectedDurationMs
        } else {
            selectedDurationMs - savedDurationMs
        }
        return durationDifference <= MAX_DURATION_DIFFERENCE_MS
    }
}
