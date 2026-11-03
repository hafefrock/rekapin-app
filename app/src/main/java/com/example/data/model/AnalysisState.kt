package com.example.data.model

import com.example.data.api.GeminiConstants

sealed interface AnalysisState {
    object Idle : AnalysisState

    data class Preparing(
        val message: String = "Mempersiapkan berkas video...",
        val progress: Float? = null
    ) : AnalysisState

    data class Uploading(
        val progressPercent: Int,
        val bytesSent: Long,
        val totalBytes: Long,
        val message: String = "Mengunggah video ke Gemini File API..."
    ) : AnalysisState

    data class PollingFileStatus(
        val statusText: String = "Memverifikasi pemrosesan video di AI (Status: PROCESSING)..."
    ) : AnalysisState

    data class AnalyzingSegment(
        val currentSegment: Int,
        val totalSegments: Int,
        val timeRange: String,
        val message: String = "Menganalisis segmen...",
        val modelName: String = ""
    ) : AnalysisState

    data class GeneratingNarration(
        val message: String = "Menyusun narasi video lengkap berdasarkan ringkasan segmen...",
        val modelName: String = ""
    ) : AnalysisState

    data class Success(
        val result: AnimeAnalysisResult,
        val styleName: String = "Anime 2D standar"
    ) : AnalysisState

    data class Error(
        val stage: String,
        val httpCode: Int? = null,
        val rawApiMessage: String,
        val errorMessage: String,
        val canRetry: Boolean = true,
        val canResume: Boolean = false,
        val resumeSegment: Int = 1,
        val canSkipAndFinish: Boolean = false,
        val analysisId: Long? = null,
        val isVideoAccessLost: Boolean = false,
        val expectedDurationMs: Long = 0L,
        val expectedSizeBytes: Long = 0L,
        val isDailyQuota: Boolean = false,
        val retryDelaySeconds: Long? = null,
        val completedSegmentsCount: Int = 0,
        val totalSegments: Int = 0,
        val completedSummaries: List<String> = emptyList(),
        val activeKeyName: String = "",
        val modelName: String = "",
        val isFileProcessingTimeout: Boolean = false,
        val isFileProcessingFailed: Boolean = false
    ) : AnalysisState {
        fun getFullReport(): String {
            if (isVideoAccessLost) {
                return buildString {
                    appendLine("=== Rekapin Informasi ===")
                    appendLine("Tahap: $stage")
                    appendLine("Keterangan: $errorMessage")
                }
            }
            return buildString {
                appendLine("=== Rekapin Error Report ===")
                appendLine("Tahap: $stage")
                if (httpCode != null) {
                    appendLine("Kode HTTP: $httpCode")
                }
                appendLine("Model: ${if (modelName.isNotBlank()) modelName else GeminiConstants.GEMINI_MODEL}")
                appendLine("Penjelasan: $errorMessage")
                if (rawApiMessage.isNotBlank()) {
                    appendLine("\nDetail Pesan Asli API / Server:")
                    appendLine(rawApiMessage)
                }
            }
        }
    }
}
