package com.example.data.model

import com.example.data.api.GeminiConstants

sealed interface AnalysisState {
    object Idle : AnalysisState

    data class Preparing(
        val message: String = "Mempersiapkan berkas video..."
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
        val message: String = "Menganalisis segmen..."
    ) : AnalysisState

    data class GeneratingNarration(
        val message: String = "Menyusun narasi episode lengkap berdasarkan ringkasan segmen..."
    ) : AnalysisState

    data class Success(
        val result: AnimeAnalysisResult
    ) : AnalysisState

    data class Error(
        val stage: String,
        val httpCode: Int? = null,
        val rawApiMessage: String,
        val errorMessage: String,
        val canRetry: Boolean = true
    ) : AnalysisState {
        fun getFullReport(): String {
            return buildString {
                appendLine("=== Rekapin Error Report ===")
                appendLine("Tahap: $stage")
                if (httpCode != null) {
                    appendLine("Kode HTTP: $httpCode")
                }
                appendLine("Model: ${GeminiConstants.GEMINI_MODEL}")
                appendLine("Penjelasan: $errorMessage")
                appendLine("\nDetail Pesan Asli API / Server:")
                appendLine(rawApiMessage)
            }
        }
    }
}
