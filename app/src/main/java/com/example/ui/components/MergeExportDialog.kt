package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.db.AnimeAnalysisEntity
import com.example.ui.theme.RekapinRadii
import com.example.ui.theme.RekapinSpacing
import com.example.ui.theme.RekapinTypography
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MergeExportDialog(
    selectedAnalyses: List<AnimeAnalysisEntity>,
    onDismiss: () -> Unit,
    onMergeNarrationsWithAi: suspend (parts: List<Pair<String, String>>, onStatus: (String) -> Unit) -> String
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // Urutan awal berdasarkan createdAt dari terlama
    var orderedEntities by remember {
        mutableStateOf(selectedAnalyses.sortedBy { it.createdAt })
    }

    // Opsi centang bagian yang ikut digabung (default aktif)
    var includeNarration by remember { mutableStateOf(true) }
    var includeCharacters by remember { mutableStateOf(true) }
    var includeTimeline by remember { mutableStateOf(true) }
    var includeVisualPromptsOnly by remember { mutableStateOf(false) }
    var includeSegmentSummaries by remember { mutableStateOf(true) }

    // Status AI Narasi
    var isMergingAi by remember { mutableStateOf(false) }
    var aiStatusMessage by remember { mutableStateOf("") }
    var aiErrorMessage by remember { mutableStateOf<String?>(null) }
    var aiMergedNarration by remember { mutableStateOf<String?>(null) }
    var useAiNarration by remember { mutableStateOf(false) }

    val defaultFileName = remember {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        "rekapin_gabungan_${dateFormat.format(Date())}.txt"
    }
    val defaultSrtFileName = remember {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        "rekapin_gabungan_${dateFormat.format(Date())}.srt"
    }
    val defaultTtsFileName = remember {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        "rekapin_gabungan_tts_${dateFormat.format(Date())}.txt"
    }

    var showSrtDialog by remember { mutableStateOf(false) }
    var selectedSrtMode by remember { mutableStateOf(SubtitleSourceMode.DEFAULT) }

    var showTtsDialog by remember { mutableStateOf(false) }
    var selectedTtsSource by remember { mutableStateOf(TtsSourceType.DEFAULT) }
    var selectedTtsPause by remember { mutableStateOf(TtsPauseStyle.DEFAULT) }
    var selectedTtsPace by remember { mutableStateOf(TtsReadingPace.DEFAULT) }

    var showFindReplaceDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val exportText = ExportFormatters.formatMergedExport(
                    items = orderedEntities,
                    includeNarration = includeNarration,
                    includeCharacters = includeCharacters,
                    includeTimeline = includeTimeline,
                    includeVisualPromptsOnly = includeVisualPromptsOnly,
                    includeSegmentSummaries = includeSegmentSummaries,
                    customMergedNarration = if (useAiNarration) aiMergedNarration else null
                )
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(exportText.toByteArray(Charsets.UTF_8))
                    stream.flush()
                }
                Toast.makeText(context, "Berkas gabungan berhasil disimpan!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal menyimpan berkas: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportSrtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val srtText = SubtitleExporter.formatMergedSrt(
                    items = orderedEntities,
                    mode = selectedSrtMode
                )
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(srtText.toByteArray(Charsets.UTF_8))
                    stream.flush()
                }
                showSrtDialog = false
                Toast.makeText(context, "Subtitle .srt gabungan berhasil disimpan!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal menyimpan subtitle: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportTtsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val ttsText = TtsScriptExporter.formatMergedTtsScript(
                    items = orderedEntities,
                    customMergedNarration = if (useAiNarration) aiMergedNarration else null,
                    sourceType = selectedTtsSource,
                    pauseStyle = selectedTtsPause
                )
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(ttsText.toByteArray(Charsets.UTF_8))
                    stream.flush()
                }
                showTtsDialog = false
                Toast.makeText(context, "Naskah TTS gabungan berhasil disimpan!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal menyimpan naskah TTS: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Teks pratinjau yang reaktif
    val previewText = remember(
        orderedEntities,
        includeNarration,
        includeCharacters,
        includeTimeline,
        includeVisualPromptsOnly,
        includeSegmentSummaries,
        aiMergedNarration,
        useAiNarration
    ) {
        ExportFormatters.formatMergedExport(
            items = orderedEntities,
            includeNarration = includeNarration,
            includeCharacters = includeCharacters,
            includeTimeline = includeTimeline,
            includeVisualPromptsOnly = includeVisualPromptsOnly,
            includeSegmentSummaries = includeSegmentSummaries,
            customMergedNarration = if (useAiNarration) aiMergedNarration else null
        )
    }

    if (showSrtDialog) {
        val mergedSrt = remember(orderedEntities, selectedSrtMode) {
            SubtitleExporter.formatMergedSrt(orderedEntities, selectedSrtMode)
        }
        SubtitleExportDialog(
            selectedMode = selectedSrtMode,
            onSelectMode = { selectedSrtMode = it },
            srtContent = mergedSrt,
            onCopy = {
                clipboard.setText(AnnotatedString(mergedSrt))
                Toast.makeText(context, "Subtitle .srt gabungan tersalin ke clipboard!", Toast.LENGTH_SHORT).show()
            },
            onSave = {
                try {
                    exportSrtLauncher.launch(defaultSrtFileName)
                } catch (e: Exception) {
                    Toast.makeText(context, "Gagal membuka penyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showSrtDialog = false }
        )
    }

    if (showTtsDialog) {
        val mergedTts = remember(orderedEntities, aiMergedNarration, useAiNarration, selectedTtsSource, selectedTtsPause) {
            TtsScriptExporter.formatMergedTtsScript(
                items = orderedEntities,
                customMergedNarration = if (useAiNarration) aiMergedNarration else null,
                sourceType = selectedTtsSource,
                pauseStyle = selectedTtsPause
            )
        }
        val totalMergedSec = remember(orderedEntities) {
            orderedEntities.sumOf { (it.durationMs / 1_000L).coerceAtLeast(0L) }
        }
        TtsExportDialog(
            selectedSource = selectedTtsSource,
            onSelectSource = { selectedTtsSource = it },
            selectedPauseStyle = selectedTtsPause,
            onSelectPauseStyle = { selectedTtsPause = it },
            selectedPace = selectedTtsPace,
            onSelectPace = { selectedTtsPace = it },
            ttsScript = mergedTts,
            onCopy = {
                clipboard.setText(AnnotatedString(mergedTts))
                Toast.makeText(context, "Naskah TTS gabungan tersalin ke clipboard!", Toast.LENGTH_SHORT).show()
            },
            onSave = {
                try {
                    exportTtsLauncher.launch(defaultTtsFileName)
                } catch (e: Exception) {
                    Toast.makeText(context, "Gagal membuka penyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showTtsDialog = false },
            sourceVideoSeconds = totalMergedSec.takeIf { it > 0L }
        )
    }

    if (showFindReplaceDialog) {
        MergedFindReplaceDialog(
            matchCountProvider = { find, ignoreCase, wholeWord ->
                val baseCount = ResultEditHelpers.previewReplaceSummaryInEntities(
                    entities = orderedEntities,
                    findText = find,
                    ignoreCase = ignoreCase,
                    wholeWord = wholeWord
                )
                val aiCount = aiMergedNarration?.let {
                    ResultEditHelpers.countOccurrences(it, find, ignoreCase, wholeWord)
                } ?: 0
                baseCount + aiCount
            },
            onApply = { find, replace, ignoreCase, wholeWord ->
                orderedEntities = ResultEditHelpers.replaceTermInEntities(
                    entities = orderedEntities,
                    findText = find,
                    replaceText = replace,
                    ignoreCase = ignoreCase,
                    wholeWord = wholeWord
                )
                aiMergedNarration = aiMergedNarration?.let { currentAi ->
                    val flags = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
                    val escaped = Regex.escape(find)
                    val pat = if (wholeWord) """(?<![\p{L}\p{N}_])$escaped(?![\p{L}\p{N}_])""" else escaped
                    Regex(pat, flags).replace(currentAi, Regex.escapeReplacement(replace))
                }
                showFindReplaceDialog = false
                Toast.makeText(context, "Teks gabungan diperbarui sebelum ekspor!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showFindReplaceDialog = false }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(RekapinRadii.lg)),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(RekapinRadii.lg)
        ) {
            Scaffold(
                topBar = {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = RekapinSpacing.xs, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Tutup",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Gabung & Ekspor",
                                        style = RekapinTypography.bodyBold.copy(
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${orderedEntities.size} Video Dipilih",
                                        style = RekapinTypography.caption.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            )
                        }
                    }
                },
                bottomBar = {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = RekapinSpacing.md, vertical = RekapinSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showFindReplaceDialog = true },
                                    contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .testTag("btn_edit_merged_words"),
                                    shape = RoundedCornerShape(RekapinRadii.sm)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                                    Text(
                                        text = "Edit Kata",
                                        style = RekapinTypography.captionBold.copy(fontSize = 12.sp),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                OutlinedButton(
                                    onClick = { showSrtDialog = true },
                                    contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .testTag("btn_export_merged_srt"),
                                    shape = RoundedCornerShape(RekapinRadii.sm)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Subtitles,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                                    Text(
                                        text = "Ekspor .srt",
                                        style = RekapinTypography.captionBold.copy(fontSize = 12.sp),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                OutlinedButton(
                                    onClick = { showTtsDialog = true },
                                    contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .testTag("btn_export_merged_tts"),
                                    shape = RoundedCornerShape(RekapinRadii.sm)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RecordVoiceOver,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                                    Text(
                                        text = "Naskah TTS",
                                        style = RekapinTypography.captionBold.copy(fontSize = 12.sp),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(previewText))
                                        Toast.makeText(context, "Teks gabungan tersalin ke clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("btn_copy_merged"),
                                    shape = RoundedCornerShape(RekapinRadii.sm)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                                    Text(
                                        text = "Salin",
                                        style = RekapinTypography.bodyBold.copy(fontSize = 12.sp),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Button(
                                    onClick = {
                                        try {
                                            exportLauncher.launch(defaultFileName)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Gagal membuka penyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("btn_export_merged_txt"),
                                    shape = RoundedCornerShape(RekapinRadii.sm),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                                    Text(
                                        text = "Ekspor .txt",
                                        style = RekapinTypography.bodyBold.copy(fontSize = 12.sp),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = RekapinSpacing.md, vertical = RekapinSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
                ) {
                    // SEKSI 1: URUTAN VIDEO
                    item {
                        Text(
                            text = "1. Urutan Penggabungan Video",
                            style = RekapinTypography.bodyBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    itemsIndexed(orderedEntities) { index, entity ->
                        RekapinCard(
                            shape = RoundedCornerShape(RekapinRadii.md),
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(RekapinRadii.md)
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = RekapinSpacing.md, vertical = RekapinSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(RekapinRadii.sm))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = RekapinTypography.captionBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = entity.videoName,
                                        style = RekapinTypography.bodyBold.copy(fontSize = 13.sp),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val durSec = (entity.durationMs / 1000).coerceAtLeast(0L)
                                    val mm = durSec / 60
                                    val ss = durSec % 60
                                    Text(
                                        text = String.format(Locale.US, "Durasi %02d:%02d • %d segmen", mm, ss, entity.totalSegments),
                                        style = RekapinTypography.caption.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val list = orderedEntities.toMutableList()
                                                val item = list.removeAt(index)
                                                list.add(index - 1, item)
                                                orderedEntities = list
                                            }
                                        },
                                        enabled = (index > 0),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = "Naik",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (index < orderedEntities.size - 1) {
                                                val list = orderedEntities.toMutableList()
                                                val item = list.removeAt(index)
                                                list.add(index + 1, item)
                                                orderedEntities = list
                                            }
                                        },
                                        enabled = (index < orderedEntities.size - 1),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = "Turun",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // SEKSI 2: BAGIAN YANG IKUT DIGABUNG
                    item {
                        Spacer(modifier = Modifier.height(RekapinSpacing.xs))
                        Text(
                            text = "2. Bagian yang Ikut Digabung",
                            style = RekapinTypography.bodyBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(RekapinSpacing.xs))

                        RekapinCard(
                            shape = RoundedCornerShape(RekapinRadii.md),
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = RekapinSpacing.md, vertical = RekapinSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                CheckboxOption(
                                    label = "Narasi Alur Cerita",
                                    checked = includeNarration,
                                    onCheckedChange = { includeNarration = it }
                                )
                                CheckboxOption(
                                    label = "Lembar Karakter (Character Sheet)",
                                    checked = includeCharacters,
                                    onCheckedChange = { includeCharacters = it }
                                )
                                CheckboxOption(
                                    label = "Timeline Lengkap (Scene + Visual Prompt)",
                                    checked = includeTimeline,
                                    onCheckedChange = {
                                        includeTimeline = it
                                        if (it) includeVisualPromptsOnly = false
                                    }
                                )
                                CheckboxOption(
                                    label = "Visual Prompt Saja (Tanpa deskripsi scene)",
                                    checked = includeVisualPromptsOnly,
                                    onCheckedChange = {
                                        includeVisualPromptsOnly = it
                                        if (it) includeTimeline = false
                                    }
                                )
                                CheckboxOption(
                                    label = "Ringkasan Tiap Segmen",
                                    checked = includeSegmentSummaries,
                                    onCheckedChange = { includeSegmentSummaries = it }
                                )
                            }
                        }
                    }

                    // SEKSI 3: SATUKAN NARASI DENGAN AI (OPSIONAL)
                    item {
                        Spacer(modifier = Modifier.height(RekapinSpacing.xs))
                        Text(
                            text = "3. Satukan Narasi dengan AI (Opsional)",
                            style = RekapinTypography.bodyBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(RekapinSpacing.xs))

                        RekapinCard(
                            shape = RoundedCornerShape(RekapinRadii.md),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(RekapinSpacing.md),
                                verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
                            ) {
                                Text(
                                    text = "Satukan narasi jadi satu cerita mengalir (1 permintaan AI)",
                                    style = RekapinTypography.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = {
                                        scope.launch {
                                            isMergingAi = true
                                            aiErrorMessage = null
                                            aiStatusMessage = "Menghubungkan ke Gemini..."
                                            try {
                                                val parts = orderedEntities.map { entity ->
                                                    Pair(entity.videoName, entity.narration.ifBlank { "(Tidak ada narasi)" })
                                                }
                                                val resultText = onMergeNarrationsWithAi(parts) { status ->
                                                    aiStatusMessage = status
                                                }
                                                aiMergedNarration = resultText
                                                useAiNarration = true
                                                Toast.makeText(context, "Narasi berhasil disatukan oleh AI!", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                aiErrorMessage = e.message ?: "Gagal menyatukan narasi."
                                                useAiNarration = false
                                            } finally {
                                                isMergingAi = false
                                            }
                                        }
                                    },
                                    enabled = !isMergingAi,
                                    shape = RoundedCornerShape(RekapinRadii.md),
                                    contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .testTag("btn_merge_narrations_ai")
                                ) {
                                    if (isMergingAi) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                                        Text(
                                            text = aiStatusMessage.ifBlank { "Sedang menyatukan..." },
                                            style = RekapinTypography.caption.copy(fontSize = 11.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                                        Text(
                                            text = "Satukan narasi dengan AI",
                                            style = RekapinTypography.bodyBold.copy(fontSize = 13.sp)
                                        )
                                    }
                                }

                                if (aiErrorMessage != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(RekapinRadii.sm))
                                            .background(MaterialTheme.colorScheme.errorContainer)
                                            .padding(RekapinSpacing.sm)
                                    ) {
                                        Text(
                                            text = "Gagal memproses dengan AI: $aiErrorMessage. Narasi tetap menggunakan format gabungan sederhana.",
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = RekapinTypography.caption.copy(fontSize = 11.sp)
                                        )
                                    }
                                }

                                if (aiMergedNarration != null) {
                                    Text(
                                        text = "Pilih versi narasi untuk ekspor:",
                                        style = RekapinTypography.captionBold.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
                                            RadioButton(
                                                selected = !useAiNarration,
                                                onClick = { useAiNarration = false }
                                            )
                                        }
                                        Text(
                                            text = "Narasi per bagian",
                                            style = RekapinTypography.caption,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .clickable { useAiNarration = false }
                                                .padding(start = RekapinSpacing.xs)
                                        )
                                        Spacer(modifier = Modifier.width(RekapinSpacing.md))
                                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
                                            RadioButton(
                                                selected = useAiNarration,
                                                onClick = { useAiNarration = true }
                                            )
                                        }
                                        Text(
                                            text = "Narasi gabungan (AI)",
                                            style = RekapinTypography.captionBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .clickable { useAiNarration = true }
                                                .padding(start = RekapinSpacing.xs)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // SEKSI 4: PRATINJAU TEKS GABUNGAN
                    item {
                        Spacer(modifier = Modifier.height(RekapinSpacing.xs))
                        Text(
                            text = "4. Pratinjau Teks Gabungan",
                            style = RekapinTypography.bodyBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(RekapinSpacing.xs))

                        RekapinCard(
                            shape = RoundedCornerShape(RekapinRadii.md),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 96.dp, max = 220.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(RekapinRadii.md)
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(RekapinSpacing.md)
                            ) {
                                Text(
                                    text = previewText,
                                    style = RekapinTypography.caption.copy(
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 15.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(RekapinSpacing.sm))
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckboxOption(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(RekapinRadii.sm))
            .clickable { onCheckedChange(!checked) }
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
        Spacer(modifier = Modifier.width(RekapinSpacing.xs))
        Text(
            text = label,
            style = RekapinTypography.caption,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
