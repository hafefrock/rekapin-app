package com.example.data.model

/** Pure checkpoint rules shared by orchestration and unit tests. */
object AnalysisResumePolicy {
    fun restoredSegmentIndex(completedSegmentsCount: Int, totalSegments: Int): Int {
        val safeTotal = totalSegments.coerceAtLeast(0)
        return completedSegmentsCount.coerceIn(0, safeTotal)
    }

    fun shouldAnalyzeSegments(completedSegmentsCount: Int, totalSegments: Int): Boolean =
        restoredSegmentIndex(completedSegmentsCount, totalSegments) < totalSegments.coerceAtLeast(0)
}
