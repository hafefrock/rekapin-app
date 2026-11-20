package com.example.ui.components

import com.example.data.db.AnimeAnalysisEntity
import com.example.data.model.AnimeAnalysisResult
import com.example.data.model.CharacterInfo
import com.example.data.model.NarrationMode
import com.example.data.model.SeriesBible
import com.example.data.model.TimelineItem

object ExportFormatters {
    fun formatFullExport(
        result: AnimeAnalysisResult,
        narrationModeName: String = "",
        languageName: String = "",
        seriesBible: SeriesBible? = result.seriesBible
    ): String {
        val modeLabel = narrationModeName.ifBlank {
            result.narrationMode.ifBlank {
                NarrationMode.DEFAULT.displayName
            }
        }
        val langLabel = languageName.ifBlank { "" }
        return buildString {
            appendLine("==================================================")
            appendLine("       HASIL REKAPIN & PROMPT GENERATOR ANIME     ")
            appendLine("==================================================")
            if (result.videoName.isNotBlank()) {
                appendLine("Video              : ${result.videoName}")
            }
            appendLine("Durasi Video       : ${result.durationFormatted}")
            appendLine("Total Segmen       : ${result.totalSegments}")
            val intervalLabel = if (result.intervalSeconds == 0) "Per Adegan" else "${result.intervalSeconds} detik"
            appendLine("Interval Prompt    : $intervalLabel")
            if (result.intervalSeconds == 0) {
                val maxDurLabel = if (result.maxSceneDuration == 0) "Tanpa batas" else "${result.maxSceneDuration} detik"
                appendLine("Batas Durasi Adegan: $maxDurLabel")
            }
            appendLine("Mode Narasi        : $modeLabel")
            if (langLabel.isNotBlank()) {
                appendLine("Bahasa             : $langLabel")
            }
            appendLine("Total Karakter     : ${result.characterSheet.size}")
            appendLine("Total Prompt Adegan: ${result.timeline.size}")
            appendLine("==================================================")
            if (result.narration.isNotBlank()) {
                appendLine()
                val narrationHeader = when {
                    modeLabel.contains("Dialog", ignoreCase = true) -> "A. DIALOG KONTEN (CHARACTER DIALOGUE)"
                    modeLabel.contains("Asli", ignoreCase = true) -> "A. NARASI ASLI KONTEN (ORIGINAL NARRATION)"
                    else -> "A. NARASI ALUR CERITA (RECAP NARRATIVE)"
                }
                appendLine(narrationHeader)
                appendLine("--------------------------------------------------")
                appendLine(result.narration)
            }
            appendLine()
            appendLine("B. LEMBAR KARAKTER (CHARACTER SHEET)")
            appendLine("--------------------------------------------------")
            if (result.characterSheet.isEmpty()) {
                appendLine("Tidak ada karakter yang terdaftar.")
            } else {
                result.characterSheet.forEachIndexed { index, char ->
                    appendLine("${index + 1}. ${char.name}")
                    appendLine("   Deskripsi: ${char.description}")
                    appendLine()
                }
            }
            val timelineIntervalLabel = if (result.intervalSeconds == 0) "PER ADEGAN" else "INTERVAL ${result.intervalSeconds}s"
            appendLine("C. TIMELINE VISUAL PROMPT ($timelineIntervalLabel)")
            appendLine("--------------------------------------------------")
            if (result.timeline.isEmpty()) {
                appendLine("Tidak ada timeline visual prompt.")
            } else {
                result.timeline.forEachIndexed { index, item ->
                    appendLine("[${item.timestamp}] Adegan #${index + 1}: ${item.sceneDescription}")
                    appendLine("Visual Prompt:")
                    appendLine(item.visualPrompt)
                    appendLine()
                }
            }
            if (result.segmentSummaries.isNotEmpty()) {
                appendLine("D. HASIL RINGKASAN PER SEGMEN")
                appendLine("--------------------------------------------------")
                result.segmentSummaries.forEachIndexed { index, summary ->
                    val modelName = result.segmentModels.getOrNull(index).orEmpty()
                    val header = if (modelName.isNotBlank()) "Segmen ${index + 1} (Model: $modelName)" else "Segmen ${index + 1}"
                    appendLine("[$header]")
                    appendLine(summary)
                    appendLine()
                }
            }
            val bibleToExport = seriesBible ?: result.seriesBible
            if (bibleToExport != null && !bibleToExport.isEmpty()) {
                appendLine()
                appendLine("E. MEMORI SERI (SERIES BIBLE)")
                appendLine("--------------------------------------------------")
                if (bibleToExport.plotSoFar.isNotBlank()) {
                    appendLine("Ringkasan Cerita Sejauh Ini:")
                    appendLine(bibleToExport.plotSoFar)
                    appendLine()
                }
                if (bibleToExport.locations.isNotEmpty()) {
                    appendLine("Lokasi Penting:")
                    bibleToExport.locations.forEachIndexed { index, loc ->
                        appendLine("${index + 1}. ${loc.name}")
                        appendLine("   Deskripsi: ${loc.description}")
                        appendLine()
                    }
                }
                if (bibleToExport.glossary.isNotEmpty()) {
                    appendLine("Daftar Istilah (Glossary):")
                    bibleToExport.glossary.forEachIndexed { index, g ->
                        appendLine("${index + 1}. ${g.term}")
                        appendLine("   Arti: ${g.definition}")
                        appendLine()
                    }
                }
                if (bibleToExport.relationships.isNotEmpty()) {
                    appendLine("Hubungan Antar Karakter:")
                    bibleToExport.relationships.forEachIndexed { index, rel ->
                        appendLine("${index + 1}. ${rel.characterA} & ${rel.characterB}: ${rel.relationship}")
                    }
                    appendLine()
                }
                if (bibleToExport.episodes.isNotEmpty()) {
                    appendLine("Riwayat Episode:")
                    bibleToExport.episodes.forEachIndexed { index, ep ->
                        appendLine("${index + 1}. ${ep.videoName}: ${ep.recap}")
                    }
                    appendLine()
                }
            }
            appendLine("==================================================")
            appendLine("Dihasilkan oleh Rekapin Anime AI")
        }.trimEnd()
    }

    fun formatSeriesBible(bible: SeriesBible): String {
        return buildString {
            appendLine("=== MEMORI SERI (SERIES BIBLE) ===")
            if (bible.plotSoFar.isNotBlank()) {
                appendLine("Ringkasan Cerita Sejauh Ini:")
                appendLine(bible.plotSoFar)
                appendLine()
            }
            if (bible.locations.isNotEmpty()) {
                appendLine("Lokasi Penting (${bible.locations.size}):")
                bible.locations.forEachIndexed { index, loc ->
                    appendLine("${index + 1}. ${loc.name}")
                    appendLine("   Deskripsi: ${loc.description}")
                    appendLine()
                }
            }
            if (bible.glossary.isNotEmpty()) {
                appendLine("Daftar Istilah (${bible.glossary.size}):")
                bible.glossary.forEachIndexed { index, g ->
                    appendLine("${index + 1}. ${g.term}")
                    appendLine("   Arti: ${g.definition}")
                    appendLine()
                }
            }
            if (bible.relationships.isNotEmpty()) {
                appendLine("Hubungan Antar Karakter (${bible.relationships.size}):")
                bible.relationships.forEachIndexed { index, rel ->
                    appendLine("${index + 1}. ${rel.characterA} & ${rel.characterB}: ${rel.relationship}")
                }
                appendLine()
            }
            if (bible.episodes.isNotEmpty()) {
                appendLine("Riwayat Episode (${bible.episodes.size}):")
                bible.episodes.forEachIndexed { index, ep ->
                    appendLine("${index + 1}. ${ep.videoName}: ${ep.recap}")
                }
                appendLine()
            }
        }.trimEnd()
    }

    fun formatCharacterSheet(characters: List<CharacterInfo>): String {
        if (characters.isEmpty()) return "Tidak ada karakter yang terdaftar."
        return buildString {
            appendLine("=== LEMBAR KARAKTER (CHARACTER SHEET) ===")
            characters.forEachIndexed { index, char ->
                appendLine("${index + 1}. ${char.name}")
                appendLine("   ${char.description}")
                if (index < characters.size - 1) appendLine()
            }
        }.trimEnd()
    }

    fun formatTimeline(timeline: List<TimelineItem>, intervalSeconds: Int, maxSceneDuration: Int = 5): String {
        if (timeline.isEmpty()) return "Timeline kosong."
        return buildString {
            val titleLabel = if (intervalSeconds == 0) "Per Adegan" else "Interval $intervalSeconds Detik"
            appendLine("=== TIMELINE VISUAL PROMPT ($titleLabel) ===")
            if (intervalSeconds == 0) {
                val maxDurLabel = if (maxSceneDuration == 0) "Tanpa batas" else "$maxSceneDuration Detik"
                appendLine("Batas Durasi Adegan: $maxDurLabel")
            }
            timeline.forEachIndexed { index, item ->
                appendLine("[${item.timestamp}] Adegan #${index + 1}: ${item.sceneDescription}")
                appendLine("Visual Prompt: ${item.visualPrompt}")
                if (index < timeline.size - 1) appendLine()
            }
        }.trimEnd()
    }

    fun formatAllVisualPromptsOnly(timeline: List<TimelineItem>): String {
        return timeline.map { it.visualPrompt.trim() }.joinToString(separator = "\n\n")
    }

    fun formatSegmentSummaries(summaries: List<String>, models: List<String>): String {
        if (summaries.isEmpty()) return "Tidak ada ringkasan segmen."
        return buildString {
            appendLine("=== HASIL RINGKASAN PER SEGMEN ===")
            summaries.forEachIndexed { index, summary ->
                val modelName = models.getOrNull(index).orEmpty()
                val header = if (modelName.isNotBlank()) "Segmen ${index + 1} [Model: $modelName]" else "Segmen ${index + 1}"
                appendLine("[$header]")
                appendLine(summary)
                if (index < summaries.size - 1) appendLine()
            }
        }.trimEnd()
    }

    /**
     * Menggeser teks timestamp (e.g., "00:00 - 00:05" atau "01:23") sebesar offset detik.
     */
    fun shiftTimestamp(original: String, offsetSec: Long): String {
        if (offsetSec <= 0L) return original
        fun parseToSeconds(token: String): Long? {
            val parts = token.trim().split(":")
            return when (parts.size) {
                2 -> {
                    val m = parts[0].toLongOrNull() ?: return null
                    val s = parts[1].toLongOrNull() ?: return null
                    m * 60 + s
                }
                3 -> {
                    val h = parts[0].toLongOrNull() ?: return null
                    val m = parts[1].toLongOrNull() ?: return null
                    val s = parts[2].toLongOrNull() ?: return null
                    h * 3600 + m * 60 + s
                }
                else -> null
            }
        }
        fun formatSeconds(sec: Long): String {
            val s = sec.coerceAtLeast(0L)
            val h = s / 3600
            val m = (s % 3600) / 60
            val remSec = s % 60
            return if (h > 0) {
                String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, remSec)
            } else {
                String.format(java.util.Locale.US, "%02d:%02d", m, remSec)
            }
        }

        if (original.contains("-")) {
            val parts = original.split("-")
            if (parts.size == 2) {
                val start = parseToSeconds(parts[0])
                val end = parseToSeconds(parts[1])
                if (start != null && end != null) {
                    return "${formatSeconds(start + offsetSec)} - ${formatSeconds(end + offsetSec)}"
                }
            }
        }
        val single = parseToSeconds(original)
        return if (single != null) formatSeconds(single + offsetSec) else original
    }

    /**
     * Memformat ekspor gabungan beberapa analisis dengan opsi bagian yang dipilih.
     */
    fun formatMergedExport(
        items: List<AnimeAnalysisEntity>,
        includeNarration: Boolean = true,
        includeCharacters: Boolean = true,
        includeTimeline: Boolean = true,
        includeVisualPromptsOnly: Boolean = false,
        includeSegmentSummaries: Boolean = true,
        customMergedNarration: String? = null // Jika diisi hasil AI, gunakan ini
    ): String {
        val mergedPairs = items.map { it to it.toAnimeAnalysisResult() }
        return buildString {
            appendLine("==================================================")
            appendLine("   HASIL GABUNGAN REKAPIN & PROMPT GENERATOR ANIME")
            appendLine("==================================================")
            appendLine("Jumlah Video Gabungan: ${items.size}")
            items.forEachIndexed { idx, itm ->
                val name = itm.videoName.ifBlank { "Video ${idx + 1}" }
                appendLine("  ${idx + 1}. $name (${itm.formattedDuration})")
            }
            val totalSec: Long = items.fold(0L) { acc, entity -> acc + (entity.durationMs / 1000) }
            val totH = totalSec / 3600
            val totM = (totalSec % 3600) / 60
            val totS = totalSec % 60
            val totalFormatted = if (totH > 0L) {
                String.format(java.util.Locale.US, "%02d:%02d:%02d", totH, totM, totS)
            } else {
                String.format(java.util.Locale.US, "%02d:%02d", totM, totS)
            }
            appendLine("Total Durasi Gabungan : $totalFormatted")
            appendLine("==================================================")

            // A. NARASI
            if (includeNarration) {
                appendLine()
                if (customMergedNarration != null && customMergedNarration.isNotBlank()) {
                    appendLine("A. NARASI GABUNGAN ALUR CERITA (DIHARMONISASI AI)")
                    appendLine("--------------------------------------------------")
                    appendLine(customMergedNarration.trim())
                } else {
                    appendLine("A. NARASI ALUR CERITA PER BAGIAN")
                    appendLine("--------------------------------------------------")
                    var hasAnyNarration = false
                    mergedPairs.forEachIndexed { idx, (entity, res) ->
                        val partName = entity.videoName.ifBlank { "Video ${idx + 1}" }
                        appendLine("--- Bagian ${idx + 1}: $partName ---")
                        if (res.narration.isNotBlank()) {
                            hasAnyNarration = true
                            appendLine(res.narration.trim())
                        } else {
                            appendLine("(Tidak ada narasi untuk bagian ini)")
                        }
                        if (idx < items.size - 1) appendLine()
                    }
                    if (!hasAnyNarration && items.isEmpty()) {
                        appendLine("Tidak ada narasi yang tersedia.")
                    }
                }
            }

            // B. LEMBAR KARAKTER (Deduped, nama sama pakai deskripsi analisis paling akhir)
            if (includeCharacters) {
                appendLine()
                appendLine("B. LEMBAR KARAKTER (CHARACTER SHEET - GABUNGAN)")
                appendLine("--------------------------------------------------")
                val charMap = mutableMapOf<String, CharacterInfo>()
                mergedPairs.forEach { (_, res) ->
                    res.characterSheet.forEach { char ->
                        val key = char.name.trim().lowercase()
                        if (key.isNotBlank()) {
                            charMap[key] = char
                        }
                    }
                }
                val mergedCharacters = charMap.values.toList()
                if (mergedCharacters.isEmpty()) {
                    appendLine("Tidak ada karakter yang terdaftar.")
                } else {
                    mergedCharacters.forEachIndexed { index, char ->
                        appendLine("${index + 1}. ${char.name}")
                        appendLine("   Deskripsi: ${char.description}")
                        if (index < mergedCharacters.size - 1) appendLine()
                    }
                }
            }

            // C. TIMELINE VISUAL PROMPT (Berkelanjutan dengan offset durasi)
            if (includeTimeline) {
                appendLine()
                appendLine("C. TIMELINE VISUAL PROMPT LENGKAP (BERKELANJUTAN)")
                appendLine("--------------------------------------------------")
                var currentOffsetSec = 0L
                var globalIndex = 0
                mergedPairs.forEachIndexed { videoIdx, (entity, res) ->
                    val videoTitle = entity.videoName.ifBlank { "Bagian ${videoIdx + 1}" }
                    appendLine("--- [$videoTitle - Dimulai dari offset ${currentOffsetSec}s] ---")
                    if (res.timeline.isEmpty()) {
                        appendLine("(Tidak ada adegan pada bagian ini)")
                    } else {
                        res.timeline.forEach { item ->
                            globalIndex++
                            val shiftedTime = shiftTimestamp(item.timestamp, currentOffsetSec)
                            appendLine("[$shiftedTime] Adegan #$globalIndex: ${item.sceneDescription}")
                            appendLine("Visual Prompt:")
                            appendLine(item.visualPrompt)
                            appendLine()
                        }
                    }
                    val videoDurSec = (entity.durationMs / 1000).coerceAtLeast(0L)
                    currentOffsetSec += videoDurSec
                }
            } else if (includeVisualPromptsOnly) {
                appendLine()
                appendLine("C. VISUAL PROMPT SAJA (SEMUA BAGIAN)")
                appendLine("--------------------------------------------------")
                val allPrompts = mergedPairs.flatMap { (_, res) -> res.timeline }.map { it.visualPrompt.trim() }
                if (allPrompts.isEmpty()) {
                    appendLine("Tidak ada prompt visual.")
                } else {
                    appendLine(allPrompts.joinToString("\n\n"))
                }
            }

            // D. RINGKASAN SEGMEN
            if (includeSegmentSummaries) {
                appendLine()
                appendLine("D. HASIL RINGKASAN PER SEGMEN (SEMUA BAGIAN)")
                appendLine("--------------------------------------------------")
                var hasAnySummary = false
                mergedPairs.forEachIndexed { videoIdx, (entity, res) ->
                    val videoTitle = entity.videoName.ifBlank { "Bagian ${videoIdx + 1}" }
                    appendLine("--- $videoTitle ---")
                    if (res.segmentSummaries.isEmpty()) {
                        appendLine("(Tidak ada ringkasan segmen)")
                    } else {
                        hasAnySummary = true
                        res.segmentSummaries.forEachIndexed { sIdx, sum ->
                            val model = res.segmentModels.getOrNull(sIdx).orEmpty()
                            val header = if (model.isNotBlank()) "Segmen ${sIdx + 1} (Model: $model)" else "Segmen ${sIdx + 1}"
                            appendLine("[$header]")
                            appendLine(sum)
                            appendLine()
                        }
                    }
                }
                if (!hasAnySummary && items.isEmpty()) {
                    appendLine("Tidak ada ringkasan segmen.")
                }
            }

            appendLine("==================================================")
            appendLine("Dihasilkan oleh Rekapin Anime AI (Fitur Gabung & Ekspor)")
        }.trimEnd()
    }
}
