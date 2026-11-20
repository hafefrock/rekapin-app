package com.example.data.model

enum class RequestPacingMode(
    val code: String,
    val label: String,
    val description: String
) {
    ADAPTIVE(
        code = "ADAPTIVE",
        label = "Otomatis (12 dtk dari awal permintaan)",
        description = "Menunggu sisa 12 dtk sejak awal permintaan sebelumnya"
    ),
    NONE(
        code = "NONE",
        label = "Tanpa jeda (risiko batas 429)",
        description = "Langsung kirim permintaan berikutnya tanpa menunggu"
    ),
    LEGACY(
        code = "LEGACY",
        label = "Lama (12 dtk setelah selesai)",
        description = "Menunggu 12 dtk penuh setelah setiap respons berhasil"
    );

    companion object {
        val DEFAULT = ADAPTIVE

        fun fromCode(code: String?): RequestPacingMode {
            return entries.firstOrNull { it.code == code } ?: DEFAULT
        }
    }
}
