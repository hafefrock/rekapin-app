package com.example.data.model

data class ModelUsageInfo(
    val modelName: String,
    val used: Int,
    val limit: Int,
    val remaining: Int,
    val resetTimeFormatted: String,
    val isQuotaExceeded: Boolean
)
