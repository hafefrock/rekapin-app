package com.example.data.api

class GeminiApiException(
    val stage: String,
    val httpCode: Int? = null,
    val rawMessage: String,
    val userFriendlyMessage: String,
    val isDailyQuota: Boolean = false,
    val retryDelaySeconds: Long? = null,
    val isFileProcessingTimeout: Boolean = false,
    val isFileProcessingFailed: Boolean = false,
    cause: Throwable? = null,
    val modelName: String = ""
) : Exception(userFriendlyMessage, cause) {

    fun toErrorReport(): String {
        return buildString {
            appendLine("=== Rekapin Error Report ===")
            appendLine("Tahap: $stage")
            if (httpCode != null) {
                appendLine("Kode HTTP: $httpCode")
            }
            appendLine("Model: ${modelName.ifBlank { GeminiConstants.GEMINI_MODEL }}")
            appendLine("Pesan: $userFriendlyMessage")
            appendLine("\nDetail Pesan API:")
            appendLine(rawMessage)
        }
    }
}
