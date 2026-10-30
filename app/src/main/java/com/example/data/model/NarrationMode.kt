package com.example.data.model

enum class NarrationMode(
    val code: String,
    val displayName: String,
    val description: String
) {
    REKAP_VOICEOVER(
        code = "rekap_voiceover",
        displayName = "Rekap Voiceover",
        description = "Narasi rekap baru bergaya voiceover, aman untuk YouTube"
    ),
    DIALOG_KONTEN(
        code = "dialog_konten",
        displayName = "Dialog Konten",
        description = "Transkrip percakapan karakter per timestamp"
    ),
    NARASI_ASLI(
        code = "narasi_asli",
        displayName = "Narasi Asli Konten",
        description = "Transkrip suara narator yang ada di video"
    );

    companion object {
        val DEFAULT = REKAP_VOICEOVER

        fun fromDisplayName(name: String?): NarrationMode {
            if (name.isNullOrBlank()) return DEFAULT
            val trimmed = name.trim()
            return entries.firstOrNull { it.displayName.equals(trimmed, ignoreCase = true) }
                ?: entries.firstOrNull { it.code.equals(trimmed, ignoreCase = true) }
                ?: DEFAULT
        }
    }
}
