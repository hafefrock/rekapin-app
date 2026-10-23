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
import androidx.compose.ui.unit.sp
import com.example.data.model.AnimeAnalysisResult
import com.example.data.model.CharacterInfo
import com.example.data.model.TimelineItem

object ExportFormatters {
    fun formatFullExport(result: AnimeAnalysisResult): String {
        return buildString {
            appendLine("==================================================")
            appendLine("       HASIL REKAPIN & PROMPT GENERATOR ANIME     ")
            appendLine("==================================================")
            if (result.videoName.isNotBlank()) {
                appendLine("Video              : ${result.videoName}")
            }
            appendLine("Durasi Video       : ${result.durationFormatted}")
            appendLine("Total Segmen       : ${result.totalSegments}")
            appendLine("Interval Prompt    : ${result.intervalSeconds} detik")
            appendLine("Total Karakter     : ${result.characterSheet.size}")
            appendLine("Total Prompt Adegan: ${result.timeline.size}")
            appendLine("==================================================")
            appendLine()
            appendLine("A. NARASI ALUR CERITA (RECAP NARRATIVE)")
            appendLine("--------------------------------------------------")
            appendLine(result.narration.ifBlank { "Tidak ada narasi yang dihasilkan." })
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
            appendLine("C. TIMELINE VISUAL PROMPT (INTERVAL ${result.intervalSeconds}s)")
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
            appendLine("==================================================")
            appendLine("Dihasilkan oleh Rekapin Anime AI")
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

    fun formatTimeline(timeline: List<TimelineItem>, intervalSeconds: Int): String {
        if (timeline.isEmpty()) return "Timeline kosong."
        return buildString {
            appendLine("=== TIMELINE VISUAL PROMPT (Interval $intervalSeconds Detik) ===")
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
}

@Composable
fun ResultView(
    result: AnimeAnalysisResult,
    modifier: Modifier = Modifier,
    styleName: String = "Anime 2D standar"
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

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
                val exportText = ExportFormatters.formatFullExport(result)
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
    val tabs = remember(result.characterSheet.size, result.timeline.size, result.segmentSummaries.size) {
        buildList {
            add("Semua Format")
            add("a. Narasi")
            add("b. Karakter (${result.characterSheet.size})")
            add("c. Timeline (${result.timeline.size})")
            if (result.segmentSummaries.isNotEmpty()) {
                add("d. Segmen (${result.segmentSummaries.size})")
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Quick Action Bar: "Salin Semua" & "Ekspor .txt"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val fullText = ExportFormatters.formatFullExport(result)
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
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
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
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("tab_result_$index")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            0 -> AllResultsSection(
                result = result,
                styleName = styleName,
                onCopyNarration = {
                    clipboard.setText(AnnotatedString(result.narration))
                    notify("Tersalin")
                },
                onCopyCharacters = {
                    clipboard.setText(AnnotatedString(ExportFormatters.formatCharacterSheet(result.characterSheet)))
                    notify("Tersalin")
                },
                onCopyTimeline = {
                    clipboard.setText(AnnotatedString(ExportFormatters.formatTimeline(result.timeline, result.intervalSeconds)))
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
                }
            )
            1 -> NarrationSection(
                narration = result.narration,
                onCopyNarration = {
                    clipboard.setText(AnnotatedString(result.narration))
                    notify("Tersalin")
                }
            )
            2 -> CharacterSheetSection(
                characters = result.characterSheet,
                onCopyCharacters = {
                    clipboard.setText(AnnotatedString(ExportFormatters.formatCharacterSheet(result.characterSheet)))
                    notify("Tersalin")
                }
            )
            3 -> TimelineSection(
                timeline = result.timeline,
                intervalSeconds = result.intervalSeconds,
                styleName = styleName,
                onCopyTimeline = {
                    clipboard.setText(AnnotatedString(ExportFormatters.formatTimeline(result.timeline, result.intervalSeconds)))
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
            4 -> SegmentSummariesSection(
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
        }
    }
}

@Composable
private fun AllResultsSection(
    result: AnimeAnalysisResult,
    styleName: String = "Anime 2D standar",
    onCopyNarration: () -> Unit,
    onCopyCharacters: () -> Unit,
    onCopyTimeline: () -> Unit,
    onCopyAllVisualPrompts: () -> Unit,
    onCopyScenePrompt: (String) -> Unit,
    onCopySegmentSummaries: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Section a: Narasi
        item {
            SectionHeader(
                stepLabel = "a. Narasi Alur Cerita",
                subtitle = "Rekap cerita mengalir bergaya narator anime (Natural American English)",
                onCopy = onCopyNarration,
                copyLabel = "Salin"
            )
            Spacer(modifier = Modifier.height(8.dp))
            NarrationCard(
                narration = result.narration,
                onCopy = onCopyNarration
            )
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
            SectionHeader(
                stepLabel = "c. Timeline Visual Prompt",
                subtitle = "Interval ${result.intervalSeconds} detik dengan $styleName prompt",
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
    }
}

@Composable
private fun NarrationSection(
    narration: String,
    onCopyNarration: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            SectionHeader(
                stepLabel = "a. Narasi Video",
                subtitle = "Rekap alur cerita bergaya narator, mengalir dan emosional",
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
            SectionHeader(
                stepLabel = "c. Timeline Visual Prompt (${timeline.size} Adegan)",
                subtitle = "Interval $intervalSeconds detik, prompt $styleName",
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
