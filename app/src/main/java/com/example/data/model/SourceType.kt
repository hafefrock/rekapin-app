package com.example.data.model

/** Jenis sumber sebuah analisis di riwayat. */
object SourceType {
    const val VIDEO = "VIDEO"
    const val SCRIPT = "SCRIPT"

    /** Status naskah yang sudah disimpan tapi belum dianalisis. */
    const val STATUS_DRAFT = "DRAFT"

    /** Batas karakter naskah (menjaga ukuran file dan memori tetap wajar). */
    const val MAX_SCRIPT_CHARS = 1_000_000
}
