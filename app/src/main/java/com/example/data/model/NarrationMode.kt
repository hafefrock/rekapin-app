package com.example.data.model

enum class NarrationMode(
    val code: String,
    val displayName: String,
    val description: String
) {
    REKAP_CERITA(
        code = "rekap_cerita",
        displayName = "Rekap Cerita",
        description = "Gaya bercerita mengalir dengan alur cerita sinematik"
    ),
    DESKRIPSI(
        code = "deskripsi",
        displayName = "Deskripsi Apa Adanya",
        description = "Hanya mendeskripsikan apa yang benar-benar terlihat di video"
    ),
    PAS_DURASI(
        code = "pas_durasi",
        displayName = "Pas Durasi",
        description = "Jumlah kata disesuaikan dengan total durasi video"
    ),
    TANPA_NARASI(
        code = "tanpa_narasi",
        displayName = "Tanpa Narasi",
        description = "Hanya menyusun prompt visual timeline tanpa naskah narasi"
    );

    companion object {
        val DEFAULT = REKAP_CERITA

        fun fromDisplayName(name: String?): NarrationMode {
            if (name.isNullOrBlank()) return DEFAULT
            val trimmed = name.trim()
            return entries.firstOrNull { it.displayName.equals(trimmed, ignoreCase = true) }
                ?: entries.firstOrNull { it.code.equals(trimmed, ignoreCase = true) }
                ?: DEFAULT
        }
    }
}
