package com.example.data.model

enum class ThinkingLevelMode(
    val code: String,
    val label: String,
    val apiValue: String?
) {
    AUTO(
        code = "AUTO",
        label = "Otomatis",
        apiValue = null
    ),
    LOW(
        code = "LOW",
        label = "Rendah",
        apiValue = "low"
    ),
    MEDIUM(
        code = "MEDIUM",
        label = "Sedang",
        apiValue = "medium"
    ),
    HIGH(
        code = "HIGH",
        label = "Tinggi",
        apiValue = "high"
    );

    companion object {
        val DEFAULT = AUTO

        fun fromCode(code: String?): ThinkingLevelMode {
            return entries.firstOrNull { it.code == code } ?: DEFAULT
        }
    }
}
