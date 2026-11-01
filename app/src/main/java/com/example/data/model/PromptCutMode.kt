package com.example.data.model

enum class PromptCutMode(
    val code: String,
    val displayName: String,
    val description: String
) {
    FIXED_INTERVAL(
        code = "fixed_interval",
        displayName = "Interval Tetap",
        description = "Potong adegan secara berkala tiap N detik"
    ),
    PER_SCENE(
        code = "per_scene",
        displayName = "Per Adegan",
        description = "Adegan dideteksi otomatis dari pergantian gambar visual video"
    );

    companion object {
        val DEFAULT = FIXED_INTERVAL

        fun fromCode(code: String?): PromptCutMode {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: DEFAULT
        }

        fun fromDisplayName(name: String?): PromptCutMode {
            return entries.find { it.displayName.equals(name, ignoreCase = true) } ?: DEFAULT
        }
    }
}
