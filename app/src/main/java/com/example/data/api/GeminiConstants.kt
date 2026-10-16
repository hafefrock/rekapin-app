package com.example.data.api

object GeminiConstants {
    /**
     * Nama model Gemini Flash free tier terbaru.
     * Ditulis sebagai satu konstanta di satu tempat agar mudah diganti sesuai instruksi:
     * "Pakai model Gemini Flash terbaru yang tersedia di free tier (Gemini 3.8 Flash).
     * Tulis nama model sebagai satu konstanta di satu tempat agar mudah diganti."
     */
    const val GEMINI_MODEL = "gemini-3.8-flash"

    // Rentang analisis per segmen video: 3 menit (180 detik)
    const val SEGMENT_DURATION_SECONDS = 180L

    // Jeda antar request (ms) untuk menghindari rate limit free tier
    const val REQUEST_DELAY_MS = 2500L

    // Maksimal percobaan otomatis dengan exponential backoff saat limit 429 / 503
    const val MAX_RETRIES = 3

    // Gemini API Endpoints
    const val UPLOAD_ENDPOINT = "https://generativelanguage.googleapis.com/upload/v1beta/files"
    const val FILES_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/files"
    const val GENERATE_CONTENT_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
}
