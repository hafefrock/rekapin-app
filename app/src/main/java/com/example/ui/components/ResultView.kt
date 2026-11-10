package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.sp
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

@Composable
fun ResultView(
    result: AnimeAnalysisResult,
    modifier: Modifier = Modifier,
    styleName: String = "Anime 2D standar",
    narrationModeName: String = "",
    languageName: String = "",
    seriesBible: SeriesBible? = result.seriesBible
) {
    val effectiveBible = seriesBible ?: result.seriesBible
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val effectiveModeName = narrationModeName.ifBlank {
        result.narrationMode.ifBlank {
            NarrationMode.DEFAULT.displayName
        }
    }

    val notify: (String) -> Unit = remember(context) {
        { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val defaultFileName = remember(result.videoName) {
        val rawName = result.videoName.trim()
        if (rawName.isNotBlank()) {
            val base = if (rawName.contains('.')) rawName.substringBeforeLast('.') else rawName
            "$base.txt"
        } else {
            "analisis_anime.txt"
        }
    }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val exportText = ExportFormatters.formatFullExport(result, effectiveModeName, languageName, effectiveBible)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(exportText.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                }
                notify("Tersimpan")
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal menyimpan berkas: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val hasNarration = result.narration.isNotBlank()
    val narasiTabLabel = when {
        effectiveModeName.contains("Dialog", ignoreCase = true) -> "Dialog"
        effectiveModeName.contains("Asli", ignoreCase = true) -> "Narasi Asli"
        else -> "Narasi"
    }
    val tabs = remember(result.characterSheet.size, result.timeline.size, result.segmentSummaries.size, hasNarration, narasiTabLabel, effectiveBible) {
        buildList {
            add("Semua")
            if (hasNarration) {
                add(narasiTabLabel)
            }
            add("Karakter (${result.characterSheet.size})")
            add("Timeline (${result.timeline.size})")
            if (result.segmentSummaries.isNotEmpty()) {
                add("Segmen (${result.segmentSummaries.size})")
            }
            if (effectiveBible != null && !effectiveBible.isEmpty()) {
                add("Memori Seri")
            }
        }
    }
    val safeSelectedTab = selectedTab.coerceIn(tabs.indices)

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Ringkasan hasil: judul kuat dan metadata tetap ringkas.
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                .padding(bottom = 10.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "ANALYSIS REPORT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    letterSpacing = 1.4.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (result.videoName.isNotBlank()) {
                    Text(
                        text = result.videoName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val intervalDisplay = if (result.intervalSeconds == 0) {
                        val maxDurLabel = if (result.maxSceneDuration == 0) "Tanpa batas" else "${result.maxSceneDuration}s"
                        "Per Adegan ($maxDurLabel)"
                    } else "${result.intervalSeconds} detik"
                    Text(
                        text = "Interval Prompt: $intervalDisplay",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Mode Narasi: $effectiveModeName",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (languageName.isNotBlank()) {
                        Text(
                            text = "Bahasa: $languageName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${result.totalSegments} Segmen • ${result.durationFormatted}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        // Quick Action Bar: "Salin Semua" & "Ekspor .txt"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val fullText = ExportFormatters.formatFullExport(result, effectiveModeName, languageName, effectiveBible)
                    clipboard.setText(AnnotatedString(fullText))
                    notify("Tersalin")
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("btn_copy_all_results")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Salin Semua",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = {
                    try {
                        exportFileLauncher.launch(defaultFileName)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Tidak dapat membuka file picker: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("btn_export_txt")
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Ekspor .txt",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (result.note.isNotBlank()) {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Catatan: ${result.note}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        // Tab Navigation
        ScrollableTabRow(
            selectedTabIndex = safeSelectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 12.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = MaterialTheme.colorScheme.primary,
                    height = 3.dp
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = safeSelectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (safeSelectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("tab_result_$index")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val currentTabTitle = tabs[safeSelectedTab]

        when {
            currentTabTitle == narasiTabLabel -> NarrationSection(
                narration = result.narration,
                modeName = effectiveModeName,
                onCopyNarration = {
                    clipboard.setText(AnnotatedString(result.narration))
                    notify("Tersalin")
                }
            )
            currentTabTitle.startsWith("Karakter (") -> CharacterSheetSection(
                characters = result.characterSheet,
                onCopyCharacters = {
                    clipboard.setText(AnnotatedString(ExportFormatters.formatCharacterSheet(result.characterSheet)))
                    notify("Tersalin")
                }
            )
            currentTabTitle.startsWith("Timeline (") -> TimelineSection(
                timeline = result.timeline,
                intervalSeconds = result.intervalSeconds,
                maxSceneDuration = result.maxSceneDuration,
                styleName = styleName,
                onCopyTimeline = {
                    clipboard.setText(AnnotatedString(ExportFormatters.formatTimeline(result.timeline, result.intervalSeconds, result.maxSceneDuration)))
                    notify("Tersalin")
                },
                onCopyAllVisualPrompts = {
                    clipboard.setText(AnnotatedString(ExportFormatters.formatAllVisualPromptsOnly(result.timeline)))
                    notify("Tersalin")
                },
                onCopyScenePrompt = { prompt ->
                    clipboard.setText(AnnotatedString(prompt))
                    notify("Tersalin")
                }
            )
            currentTabTitle.startsWith("Segmen (") -> SegmentSummariesSection(
                summaries = result.segmentSummaries,
                models = result.segmentModels,
                onCopyAll = {
                    clipboard.setText(AnnotatedString(ExportFormatters.formatSegmentSummaries(result.segmentSummaries, result.segmentModels)))
                    notify("Tersalin")
                },
                onCopySegment = { summary ->
                    clipboard.setText(AnnotatedString(summary))
                    notify("Tersalin")
                }
            )
            currentTabTitle == "Memori Seri" -> effectiveBible?.let { bible ->
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        SeriesBibleSection(
                            bible = bible,
                            onCopy = {
                                clipboard.setText(AnnotatedString(ExportFormatters.formatSeriesBible(bible)))
                                notify("Tersalin")
                            },
                            defaultExpanded = true
                        )
                    }
                }
            } ?: Text("Tidak ada memori seri.")
            else -> AllResultsSection(
                result = result,
                styleName = styleName,
                narrationModeName = effectiveModeName,
                seriesBible = effectiveBible,
                onCopyNarration = {
                    clipboard.setText(AnnotatedString(result.narration))
                    notify("Tersalin")
                },
                onCopyCharacters = {
                    clipboard.setText(AnnotatedString(ExportFormatters.formatCharacterSheet(result.characterSheet)))
                    notify("Tersalin")
                },
                onCopyTimeline = {
                    clipboard.setText(AnnotatedString(ExportFormatters.formatTimeline(result.timeline, result.intervalSeconds, result.maxSceneDuration)))
                    notify("Tersalin")
                },
                onCopyAllVisualPrompts = {
                    clipboard.setText(AnnotatedString(ExportFormatters.formatAllVisualPromptsOnly(result.timeline)))
                    notify("Tersalin")
                },
                onCopyScenePrompt = { prompt ->
                    clipboard.setText(AnnotatedString(prompt))
                    notify("Tersalin")
                },
                onCopySegmentSummaries = {
                    clipboard.setText(AnnotatedString(ExportFormatters.formatSegmentSummaries(result.segmentSummaries, result.segmentModels)))
                    notify("Tersalin")
                },
                onCopySeriesBible = {
                    effectiveBible?.let {
                        clipboard.setText(AnnotatedString(ExportFormatters.formatSeriesBible(it)))
                        notify("Tersalin")
                    }
                }
            )
        }
    }
}

@Composable
private fun AllResultsSection(
    result: AnimeAnalysisResult,
    styleName: String = "Anime 2D standar",
    narrationModeName: String = "",
    seriesBible: SeriesBible? = null,
    onCopyNarration: () -> Unit,
    onCopyCharacters: () -> Unit,
    onCopyTimeline: () -> Unit,
    onCopyAllVisualPrompts: () -> Unit,
    onCopyScenePrompt: (String) -> Unit,
    onCopySegmentSummaries: () -> Unit,
    onCopySeriesBible: () -> Unit = {}
) {
    val (stepLabel, subtitle) = when {
        narrationModeName.contains("Dialog", ignoreCase = true) ->
            "a. Dialog Konten" to "Transkrip percakapan karakter per timestamp"
        narrationModeName.contains("Asli", ignoreCase = true) ->
            "a. Narasi Asli Konten" to "Transkrip suara narator asli di dalam video"
        else ->
            "a. Narasi Video" to "Rekap alur cerita bergaya voiceover, mengalir dan emosional"
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Section a: Narasi
        if (result.narration.isNotBlank()) {
            item {
                SectionHeader(
                    stepLabel = stepLabel,
                    subtitle = subtitle,
                    onCopy = onCopyNarration,
                    copyLabel = "Salin"
                )
                Spacer(modifier = Modifier.height(8.dp))
                NarrationCard(
                    narration = result.narration,
                    onCopy = onCopyNarration
                )
            }
        }

        // Section b: Lembar Karakter
        item {
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader(
                stepLabel = "b. Lembar Karakter (Character Sheet)",
                subtitle = "Deskripsi tetap karakter utama (rambut, mata, pakaian, warna ciri khas)",
                onCopy = onCopyCharacters,
                copyLabel = "Salin"
            )
        }

        if (result.characterSheet.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Belum ada karakter spesifik yang terdaftar pada segmen ini.",
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            itemsIndexed(result.characterSheet) { _, charInfo ->
                CharacterCard(charInfo)
            }
        }

        // Section c: Timeline Visual Prompt
        item {
            Spacer(modifier = Modifier.height(8.dp))
            val timelineSubtitle = if (result.intervalSeconds == 0) {
                val maxDurLabel = if (result.maxSceneDuration == 0) "Tanpa batas" else "${result.maxSceneDuration}s"
                "Mode Per Adegan (Batas: $maxDurLabel) dengan $styleName prompt"
            } else {
                "Interval ${result.intervalSeconds} detik dengan $styleName prompt"
            }
            SectionHeader(
                stepLabel = "c. Timeline Visual Prompt",
                subtitle = timelineSubtitle,
                onCopy = onCopyTimeline,
                copyLabel = "Salin"
            )
            Spacer(modifier = Modifier.height(8.dp))
            TimelineActionRow(
                onCopyTimeline = onCopyTimeline,
                onCopyAllVisualPrompts = onCopyAllVisualPrompts
            )
        }

        itemsIndexed(result.timeline) { index, timelineItem ->
            TimelineCard(
                index = index + 1,
                item = timelineItem,
                onCopyPrompt = { onCopyScenePrompt(timelineItem.visualPrompt) },
                styleName = styleName
            )
        }

        // Section d: Hasil per Segmen
        if (result.segmentSummaries.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(
                    stepLabel = "d. Ringkasan Cerita per Segmen",
                    subtitle = "Hasil analisis cerita per segmen beserta model AI yang mengerjakan",
                    onCopy = onCopySegmentSummaries,
                    copyLabel = "Salin"
                )
            }

            itemsIndexed(result.segmentSummaries) { index, summary ->
                val modelName = result.segmentModels.getOrNull(index).orEmpty()
                SegmentSummaryCard(
                    index = index + 1,
                    summary = summary,
                    modelName = modelName,
                    onCopy = { onCopyScenePrompt(summary) }
                )
            }
        }

        // Section e: Memori Seri (jika ada)
        if (seriesBible != null && !seriesBible.isEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(
                    stepLabel = "e. Memori Seri (Series Bible)",
                    subtitle = "Latar dunia, istilah khusus, relasi karakter, dan ringkasan kumulatif cerita",
                    onCopy = onCopySeriesBible,
                    copyLabel = "Salin"
                )
                Spacer(modifier = Modifier.height(8.dp))
                SeriesBibleSection(
                    bible = seriesBible,
                    onCopy = onCopySeriesBible,
                    defaultExpanded = false
                )
            }
        }
    }
}

@Composable
fun SeriesBibleSection(
    bible: SeriesBible,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    defaultExpanded: Boolean = true
) {
    var isExpanded by remember { mutableStateOf(defaultExpanded) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .testTag("card_series_bible_section")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Memori Seri (Series Bible)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${bible.locations.size} lokasi • ${bible.glossary.size} istilah • ${bible.relationships.size} relasi",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.testTag("btn_copy_series_bible")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Salin Memori Seri",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Tutup" else "Buka",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    if (bible.plotSoFar.isNotBlank()) {
                        Text(
                            text = "Ringkasan Cerita Sejauh Ini",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = bible.plotSoFar,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 17.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(10.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (bible.locations.isNotEmpty()) {
                        Text(
                            text = "Lokasi Penting (${bible.locations.size})",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        bible.locations.forEach { loc ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = loc.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (loc.description.isNotBlank()) {
                                    Text(
                                        text = loc.description,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (bible.glossary.isNotEmpty()) {
                        Text(
                            text = "Daftar Istilah (${bible.glossary.size})",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        bible.glossary.forEach { g ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = g.term,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.widthIn(min = 80.dp, max = 130.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = g.definition,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (bible.relationships.isNotEmpty()) {
                        Text(
                            text = "Hubungan Antar Karakter (${bible.relationships.size})",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        bible.relationships.forEach { rel ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${rel.characterA} ↔ ${rel.characterB}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.widthIn(min = 90.dp, max = 150.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = rel.relationship,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NarrationSection(
    narration: String,
    modeName: String = "",
    onCopyNarration: () -> Unit
) {
    val (stepLabel, subtitle) = when {
        modeName.contains("Dialog", ignoreCase = true) ->
            "a. Dialog Konten" to "Transkrip percakapan karakter per timestamp"
        modeName.contains("Asli", ignoreCase = true) ->
            "a. Narasi Asli Konten" to "Transkrip suara narator asli di dalam video"
        else ->
            "a. Narasi Video" to "Rekap alur cerita bergaya voiceover, mengalir dan emosional"
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            SectionHeader(
                stepLabel = stepLabel,
                subtitle = subtitle,
                onCopy = onCopyNarration,
                copyLabel = "Salin"
            )
            Spacer(modifier = Modifier.height(10.dp))
            NarrationCard(
                narration = narration,
                onCopy = onCopyNarration
            )
        }
    }
}

@Composable
private fun CharacterSheetSection(
    characters: List<CharacterInfo>,
    onCopyCharacters: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            SectionHeader(
                stepLabel = "b. Character Sheet (${characters.size} Karakter)",
                subtitle = "Deskripsi visual tetap untuk konsistensi prompt lintas adegan",
                onCopy = onCopyCharacters,
                copyLabel = "Salin"
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (characters.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Belum ada karakter yang terdaftar pada segmen ini.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            itemsIndexed(characters) { _, char ->
                CharacterCard(char)
            }
        }
    }
}

@Composable
private fun TimelineSection(
    timeline: List<TimelineItem>,
    intervalSeconds: Int,
    maxSceneDuration: Int = 5,
    styleName: String = "Anime 2D standar",
    onCopyTimeline: () -> Unit,
    onCopyAllVisualPrompts: () -> Unit,
    onCopyScenePrompt: (String) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            val timelineSubtitle = if (intervalSeconds == 0) {
                val maxDurLabel = if (maxSceneDuration == 0) "Tanpa batas" else "${maxSceneDuration}s"
                "Mode Per Adegan (Batas: $maxDurLabel), prompt $styleName"
            } else {
                "Interval $intervalSeconds detik, prompt $styleName"
            }
            SectionHeader(
                stepLabel = "c. Timeline Visual Prompt (${timeline.size} Adegan)",
                subtitle = timelineSubtitle,
                onCopy = onCopyTimeline,
                copyLabel = "Salin"
            )
            Spacer(modifier = Modifier.height(8.dp))
            TimelineActionRow(
                onCopyTimeline = onCopyTimeline,
                onCopyAllVisualPrompts = onCopyAllVisualPrompts
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        itemsIndexed(timeline) { index, item ->
            TimelineCard(
                index = index + 1,
                item = item,
                onCopyPrompt = { onCopyScenePrompt(item.visualPrompt) },
                styleName = styleName
            )
        }
    }
}

@Composable
private fun TimelineActionRow(
    onCopyTimeline: () -> Unit,
    onCopyAllVisualPrompts: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onCopyTimeline,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .testTag("btn_copy_timeline_section")
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Salin Timeline",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Button(
            onClick = onCopyAllVisualPrompts,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .weight(1.35f)
                .height(40.dp)
                .testTag("btn_copy_all_visual_prompts")
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Salin Semua Visual Prompt",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionHeader(
    stepLabel: String,
    subtitle: String,
    onCopy: (() -> Unit)? = null,
    copyLabel: String = "Salin"
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stepLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (onCopy != null) {
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onCopy,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("btn_copy_section_${stepLabel.take(1)}")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = copyLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun NarrationCard(
    narration: String,
    onCopy: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Video Narrative Recap",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("copy_narration_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Salin narasi",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = narration,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun CharacterCard(charInfo: CharacterInfo) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Text(
                    text = charInfo.name.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = charInfo.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = charInfo.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun TimelineCard(
    index: Int,
    item: TimelineItem,
    onCopyPrompt: () -> Unit,
    styleName: String = "Anime 2D standar"
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header bar: Timestamp badge & Copy action (hanya visual prompt)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = item.timestamp,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "#$index",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onCopyPrompt,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("copy_prompt_button_$index")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Salin Visual Prompt",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scene description
            Text(
                text = item.sceneDescription,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Visual prompt block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(12.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Visual Prompt ($styleName)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.visualPrompt,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 17.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentSummariesSection(
    summaries: List<String>,
    models: List<String>,
    onCopyAll: () -> Unit,
    onCopySegment: (String) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            SectionHeader(
                stepLabel = "d. Ringkasan per Segmen",
                subtitle = "Ringkasan cerita per segmen video beserta label model Gemini yang digunakan",
                onCopy = onCopyAll,
                copyLabel = "Salin Semua"
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (summaries.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Belum ada ringkasan segmen yang tercatat.",
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            itemsIndexed(summaries) { index, summary ->
                val modelName = models.getOrNull(index).orEmpty()
                SegmentSummaryCard(
                    index = index + 1,
                    summary = summary,
                    modelName = modelName,
                    onCopy = { onCopySegment(summary) }
                )
            }
        }
    }
}

@Composable
private fun SegmentSummaryCard(
    index: Int,
    summary: String,
    modelName: String,
    onCopy: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp)
            )
            .testTag("segment_card_$index")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Segmen $index",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (modelName.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                .testTag("chip_model_segment_$index")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = modelName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("btn_copy_segment_$index")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Salin Ringkasan Segmen $index",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = summary,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )
        }
    }
}
