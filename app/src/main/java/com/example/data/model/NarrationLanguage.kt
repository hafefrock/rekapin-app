package com.example.data.model

enum class NarrationLanguage(
    val code: String,
    val displayName: String
) {
    ENGLISH_USA("en_US", "English (USA)"),
    INDONESIAN("id", "Bahasa Indonesia");

    companion object {
        val DEFAULT = ENGLISH_USA

        fun fromDisplayName(name: String?): NarrationLanguage {
            if (name.isNullOrBlank()) return DEFAULT
            val trimmed = name.trim()
            return entries.firstOrNull { it.displayName.equals(trimmed, ignoreCase = true) }
                ?: entries.firstOrNull { it.code.equals(trimmed, ignoreCase = true) }
                ?: DEFAULT
        }
    }
}
