package com.example.data.api

class GeminiApiException(
    val stage: String,
    val httpCode: Int? = null,
    val rawMessage: String,
    val userFriendlyMessage: String,
    cause: Throwable? = null
) : Exception(userFriendlyMessage, cause) {

    fun toErrorReport(): String {
        return buildString {
            appendLine("=== Rekapin Error Report ===")
            appendLine("Tahap: $stage")
            if (httpCode != null) {
                appendLine("Kode HTTP: $httpCode")
            }
            appendLine("Model: ${GeminiConstants.GEMINI_MODEL}")
            appendLine("Pesan: $userFriendlyMessage")
            appendLine("\nDetail Pesan API:")
            appendLine(rawMessage)
        }
    }
}
