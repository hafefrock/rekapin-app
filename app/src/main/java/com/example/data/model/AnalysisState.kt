package com.example.data.model

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
        val errorMessage: String,
        val canRetry: Boolean = true
    ) : AnalysisState
}
