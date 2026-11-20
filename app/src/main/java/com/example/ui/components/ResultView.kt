package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AnimeAnalysisResult
import com.example.data.model.NarrationMode
import com.example.data.model.SeriesBible
import com.example.ui.theme.RekapinRadii
import com.example.ui.theme.RekapinSpacing
import com.example.ui.theme.RekapinTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultView(
    result: AnimeAnalysisResult,
    modifier: Modifier = Modifier,
    styleName: String = "Anime 2D standar",
    narrationModeName: String = "",
    languageName: String = "",
    seriesBible: SeriesBible? = result.seriesBible,
    stageTimings: Map<String, Long>? = null,
    onResultEdited: ((AnimeAnalysisResult) -> Unit)? = null
) {
    var workingResult by remember(result) { mutableStateOf(result) }
    val isEdited = workingResult != result

    val effectiveBible = seriesBible ?: workingResult.seriesBible
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val effectiveModeName = narrationModeName.ifBlank {
        workingResult.narrationMode.ifBlank {
            NarrationMode.DEFAULT.displayName
        }
    }

    val notify: (String) -> Unit = remember(context) {
        { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val applyEditedResult: (AnimeAnalysisResult) -> Unit = { updated ->
        workingResult = updated
        onResultEdited?.invoke(updated)
    }

    val defaultFileName = remember(workingResult.videoName) {
        val rawName = workingResult.videoName.trim()
        if (rawName.isNotBlank()) {
            val base = if (rawName.contains('.')) rawName.substringBeforeLast('.') else rawName
            "$base.txt"
        } else {
            "analisis_anime.txt"
        }
    }
    val defaultSrtFileName = remember(workingResult.videoName) {
        SubtitleExporter.defaultSrtFileName(workingResult.videoName)
    }
    val defaultTtsFileName = remember(workingResult.videoName) {
        TtsScriptExporter.defaultTtsFileName(workingResult.videoName)
    }

    var showSrtDialog by remember { mutableStateOf(false) }
    var selectedSrtMode by remember { mutableStateOf(SubtitleSourceMode.DEFAULT) }

    var showTtsDialog by remember { mutableStateOf(false) }
    var selectedTtsSource by remember(workingResult.narration.isNotBlank()) {
        mutableStateOf(
            if (workingResult.narration.isNotBlank()) TtsSourceType.NARRATION else TtsSourceType.TIMELINE_SPEECH
        )
    }
    var selectedTtsPause by remember { mutableStateOf(TtsPauseStyle.DEFAULT) }
    var selectedTtsPace by remember { mutableStateOf(TtsReadingPace.DEFAULT) }

    var showQuickEditDialog by remember { mutableStateOf(false) }
    var quickEditInitialTab by remember { mutableIntStateOf(0) }
    var editingCharacterIndex by remember { mutableStateOf<Int?>(null) }
    var editingTimelineIndex by remember { mutableStateOf<Int?>(null) }

    var showPacingDialog by remember { mutableStateOf(false) }
    var selectedPacingTarget by remember { mutableStateOf(PacingTargetPreset.DEFAULT) }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val exportText = ExportFormatters.formatFullExport(workingResult, effectiveModeName, languageName, effectiveBible)
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

    val exportSrtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val srtText = SubtitleExporter.formatSrt(
                    timeline = workingResult.timeline,
                    mode = selectedSrtMode,
                    intervalSeconds = workingResult.intervalSeconds,
                    maxSceneDuration = workingResult.maxSceneDuration
                )
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(srtText.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                }
                showSrtDialog = false
                notify("Subtitle .srt tersimpan")
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
                val ttsText = TtsScriptExporter.formatTtsScript(
                    result = workingResult,
                    sourceType = selectedTtsSource,
                    pauseStyle = selectedTtsPause
                )
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(ttsText.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                }
                showTtsDialog = false
                notify("Naskah TTS tersimpan")
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal menyimpan naskah TTS: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val hasNarration = workingResult.narration.isNotBlank()
    val hasTtsContent = hasNarration || workingResult.timeline.isNotEmpty()
    val sourceVideoSeconds = remember(workingResult.durationFormatted, workingResult.timeline, workingResult.totalSegments) {
        RecapPacingAnalyzer.parseVideoDurationSeconds(workingResult)
    }
    val pacingReport by produceState<OverallPacingReport?>(
        initialValue = null,
        workingResult,
        selectedTtsSource,
        selectedPacingTarget,
        selectedTtsPace
    ) {
        value = withContext(Dispatchers.Default) {
            RecapPacingAnalyzer.analyzeResult(
                result = workingResult,
                sourceType = selectedTtsSource,
                targetPreset = selectedPacingTarget,
                pace = selectedTtsPace
            )
        }
    }
    val narasiTabLabel = remember(effectiveModeName) {
        when {
            effectiveModeName.contains("Dialog", ignoreCase = true) -> "Dialog"
            effectiveModeName.contains("Asli", ignoreCase = true) -> "Narasi Asli"
            else -> "Narasi"
        }
    }
    val tabs = remember(
        workingResult.characterSheet.size,
        workingResult.timeline.size,
        workingResult.segmentSummaries.size,
        hasNarration,
        narasiTabLabel,
        effectiveBible
    ) {
        buildList {
            add("all" to "Semua")
            if (hasNarration) {
                add("narration" to narasiTabLabel)
            }
            add("characters" to "Karakter (${workingResult.characterSheet.size})")
            add("timeline" to "Timeline (${workingResult.timeline.size})")
            if (workingResult.segmentSummaries.isNotEmpty()) {
                add("segments" to "Segmen (${workingResult.segmentSummaries.size})")
            }
            if (effectiveBible != null && !effectiveBible.isEmpty()) {
                add("series_bible" to "Memori Seri")
            }
        }
    }

    val onCopyAll = remember(workingResult, effectiveModeName, languageName, effectiveBible) {
        {
            val fullText = ExportFormatters.formatFullExport(workingResult, effectiveModeName, languageName, effectiveBible)
            clipboard.setText(AnnotatedString(fullText))
            notify("Tersalin")
        }
    }
    val onExportTxt = remember(defaultFileName) {
        {
            try {
                exportFileLauncher.launch(defaultFileName)
            } catch (e: Exception) {
                Toast.makeText(context, "Tidak dapat membuka file picker: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val onCopyNarration = remember(workingResult.narration) {
        {
            clipboard.setText(AnnotatedString(workingResult.narration))
            notify("Tersalin")
        }
    }
    val onCopyCharacters = remember(workingResult.characterSheet) {
        {
            clipboard.setText(AnnotatedString(ExportFormatters.formatCharacterSheet(workingResult.characterSheet)))
            notify("Tersalin")
        }
    }
    val onCopyTimeline = remember(workingResult.timeline, workingResult.intervalSeconds, workingResult.maxSceneDuration) {
        {
            clipboard.setText(AnnotatedString(ExportFormatters.formatTimeline(workingResult.timeline, workingResult.intervalSeconds, workingResult.maxSceneDuration)))
            notify("Tersalin")
        }
    }
    val onCopyAllVisualPrompts = remember(workingResult.timeline) {
        {
            clipboard.setText(AnnotatedString(ExportFormatters.formatAllVisualPromptsOnly(workingResult.timeline)))
            notify("Tersalin")
        }
    }
    val onCopyScenePrompt: (String) -> Unit = remember {
        { prompt ->
            clipboard.setText(AnnotatedString(prompt))
            notify("Tersalin")
        }
    }
    val onCopySegmentSummaries = remember(workingResult.segmentSummaries, workingResult.segmentModels) {
        {
            clipboard.setText(AnnotatedString(ExportFormatters.formatSegmentSummaries(workingResult.segmentSummaries, workingResult.segmentModels)))
            notify("Tersalin")
        }
    }
    val onCopySeriesBible = remember(effectiveBible) {
        {
            effectiveBible?.let {
                clipboard.setText(AnnotatedString(ExportFormatters.formatSeriesBible(it)))
                notify("Tersalin")
            }
            Unit
        }
    }
    val onEditCharacter: (Int) -> Unit = remember { { idx -> editingCharacterIndex = idx } }
    val onEditTimelineItem: (Int) -> Unit = remember { { idx -> editingTimelineIndex = idx } }

    if (showSrtDialog) {
        val srtContent = remember(workingResult.timeline, workingResult.intervalSeconds, workingResult.maxSceneDuration, selectedSrtMode) {
            SubtitleExporter.formatSrt(
                timeline = workingResult.timeline,
                mode = selectedSrtMode,
                intervalSeconds = workingResult.intervalSeconds,
                maxSceneDuration = workingResult.maxSceneDuration
            )
        }
        SubtitleExportDialog(
            selectedMode = selectedSrtMode,
            onSelectMode = { selectedSrtMode = it },
            srtContent = srtContent,
            onCopy = {
                clipboard.setText(AnnotatedString(srtContent))
                notify("Subtitle .srt tersalin")
            },
            onSave = {
                try {
                    exportSrtLauncher.launch(defaultSrtFileName)
                } catch (e: Exception) {
                    Toast.makeText(context, "Tidak dapat membuka file picker: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showSrtDialog = false }
        )
    }

    if (showTtsDialog) {
        val ttsScript = remember(workingResult, selectedTtsSource, selectedTtsPause) {
            TtsScriptExporter.formatTtsScript(
                result = workingResult,
                sourceType = selectedTtsSource,
                pauseStyle = selectedTtsPause
            )
        }
        TtsExportDialog(
            selectedSource = selectedTtsSource,
            onSelectSource = { selectedTtsSource = it },
            selectedPauseStyle = selectedTtsPause,
            onSelectPauseStyle = { selectedTtsPause = it },
            selectedPace = selectedTtsPace,
            onSelectPace = { selectedTtsPace = it },
            ttsScript = ttsScript,
            onCopy = {
                clipboard.setText(AnnotatedString(ttsScript))
                notify("Naskah TTS tersalin")
            },
            onSave = {
                try {
                    exportTtsLauncher.launch(defaultTtsFileName)
                } catch (e: Exception) {
                    Toast.makeText(context, "Tidak dapat membuka file picker: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showTtsDialog = false },
            sourceVideoSeconds = sourceVideoSeconds,
            selectedTargetPreset = selectedPacingTarget,
            onOpenPacingDetail = {
                showTtsDialog = false
                showPacingDialog = true
            }
        )
    }

    if (showPacingDialog) {
        val activePacingReport = pacingReport ?: remember(workingResult, selectedTtsSource, selectedPacingTarget, selectedTtsPace) {
            RecapPacingAnalyzer.analyzeResult(
                result = workingResult,
                sourceType = selectedTtsSource,
                targetPreset = selectedPacingTarget,
                pace = selectedTtsPace
            )
        }
        PacingAnalysisDialog(
            report = activePacingReport,
            selectedSource = selectedTtsSource,
            onSelectSource = { selectedTtsSource = it },
            selectedTargetPreset = selectedPacingTarget,
            onSelectTargetPreset = { selectedPacingTarget = it },
            selectedPace = selectedTtsPace,
            onSelectPace = { selectedTtsPace = it },
            onOpenNarrationEdit = {
                quickEditInitialTab = 1
                showPacingDialog = false
                showQuickEditDialog = true
            },
            onOpenTtsExport = {
                showPacingDialog = false
                showTtsDialog = true
            },
            onEditDenseCue = { idx ->
                showPacingDialog = false
                editingTimelineIndex = idx
            },
            onDismiss = { showPacingDialog = false }
        )
    }

    if (showQuickEditDialog) {
        ResultQuickEditDialog(
            initialResult = workingResult,
            canResetToOriginal = isEdited,
            onResetToOriginal = {
                applyEditedResult(result)
                showQuickEditDialog = false
                notify("Dikembalikan ke hasil asli")
            },
            onApplyAndClose = { updated ->
                applyEditedResult(updated)
                showQuickEditDialog = false
                notify("Perubahan disimpan")
            },
            onDismiss = { showQuickEditDialog = false },
            initialTab = quickEditInitialTab
        )
    }

    editingCharacterIndex?.let { charIdx ->
        val charInfo = workingResult.characterSheet.getOrNull(charIdx)
        if (charInfo != null) {
            CharacterEditDialog(
                character = charInfo,
                onSave = { newName, newDesc, propagateGlobally ->
                    val updated = ResultEditHelpers.updateCharacter(
                        result = workingResult,
                        index = charIdx,
                        newName = newName,
                        newDescription = newDesc,
                        propagateRenameGlobally = propagateGlobally
                    )
                    applyEditedResult(updated)
                    editingCharacterIndex = null
                    notify("Karakter diperbarui")
                },
                onDismiss = { editingCharacterIndex = null }
            )
        }
    }

    editingTimelineIndex?.let { timelineIdx ->
        val item = workingResult.timeline.getOrNull(timelineIdx)
        if (item != null) {
            TimelineItemEditDialog(
                index = timelineIdx + 1,
                item = item,
                onSave = { updatedItem ->
                    val updated = ResultEditHelpers.updateTimelineItem(
                        result = workingResult,
                        index = timelineIdx,
                        newItem = updatedItem
                    )
                    applyEditedResult(updated)
                    editingTimelineIndex = null
                    notify("Adegan diperbarui")
                },
                onDismiss = { editingTimelineIndex = null }
            )
        }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        ResultSummaryHeaderCard(
            result = workingResult,
            effectiveModeName = effectiveModeName,
            languageName = languageName
        )

        Spacer(modifier = Modifier.height(RekapinSpacing.sm))

        ResultActionRow(
            onCopyAll = onCopyAll,
            onExportTxt = onExportTxt,
            onExportSrt = { showSrtDialog = true },
            hasTimelineForSrt = workingResult.timeline.isNotEmpty(),
            onEditResult = {
                quickEditInitialTab = 0
                showQuickEditDialog = true
            },
            isEdited = isEdited,
            onExportTts = { showTtsDialog = true },
            hasTtsContent = hasTtsContent,
            onCheckPacing = { showPacingDialog = true }
        )

        if (workingResult.note.isNotBlank()) {
            Spacer(modifier = Modifier.height(RekapinSpacing.sm))
            ResultNoteCard(note = workingResult.note)
        }

        Spacer(modifier = Modifier.height(RekapinSpacing.sm))

        ResultTabsContainer(
            tabs = tabs,
            workingResult = workingResult,
            styleName = styleName,
            effectiveModeName = effectiveModeName,
            effectiveBible = effectiveBible,
            stageTimings = stageTimings,
            pacingReport = pacingReport,
            selectedPacingTarget = selectedPacingTarget,
            onOpenPacingDetail = { showPacingDialog = true },
            onCopyNarration = onCopyNarration,
            onCopyCharacters = onCopyCharacters,
            onCopyTimeline = onCopyTimeline,
            onCopyAllVisualPrompts = onCopyAllVisualPrompts,
            onCopyScenePrompt = onCopyScenePrompt,
            onCopySegmentSummaries = onCopySegmentSummaries,
            onCopySeriesBible = onCopySeriesBible,
            onEditCharacter = onEditCharacter,
            onEditTimelineItem = onEditTimelineItem
        )
    }
}

@Composable
private fun ResultTabsContainer(
    tabs: List<Pair<String, String>>,
    workingResult: AnimeAnalysisResult,
    styleName: String,
    effectiveModeName: String,
    effectiveBible: SeriesBible?,
    stageTimings: Map<String, Long>?,
    pacingReport: OverallPacingReport?,
    selectedPacingTarget: PacingTargetPreset,
    onOpenPacingDetail: () -> Unit,
    onCopyNarration: () -> Unit,
    onCopyCharacters: () -> Unit,
    onCopyTimeline: () -> Unit,
    onCopyAllVisualPrompts: () -> Unit,
    onCopyScenePrompt: (String) -> Unit,
    onCopySegmentSummaries: () -> Unit,
    onCopySeriesBible: () -> Unit,
    onEditCharacter: (Int) -> Unit,
    onEditTimelineItem: (Int) -> Unit
) {
    var selectedTab by rememberSaveable(
        workingResult.videoName,
        workingResult.durationFormatted,
        workingResult.totalSegments
    ) {
        mutableIntStateOf(0)
    }
    val safeSelectedTab = selectedTab.coerceIn(tabs.indices)
    val tabStateHolder = rememberSaveableStateHolder()

    ScrollableTabRow(
        selectedTabIndex = safeSelectedTab,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        edgePadding = RekapinSpacing.md,
        divider = {},
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[safeSelectedTab]),
                color = MaterialTheme.colorScheme.primary,
                height = 2.dp
            )
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        tabs.forEachIndexed { index, (_, title) ->
            val isTabSelected = safeSelectedTab == index
            Tab(
                selected = isTabSelected,
                onClick = { selectedTab = index },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = {
                    Text(
                        text = title,
                        style = (if (isTabSelected) RekapinTypography.bodyBold else RekapinTypography.body)
                            .copy(fontSize = 13.sp),
                        maxLines = 1
                    )
                },
                modifier = Modifier
                    .height(40.dp)
                    .testTag("tab_result_$index")
            )
        }
    }

    Spacer(modifier = Modifier.height(RekapinSpacing.sm))

    val currentTabKey = tabs[safeSelectedTab].first

    tabStateHolder.SaveableStateProvider(key = currentTabKey) {
        val listState = rememberLazyListState()
        when (currentTabKey) {
            "narration" -> NarrationSection(
                narration = workingResult.narration,
                modeName = effectiveModeName,
                onCopyNarration = onCopyNarration,
                pacingReport = pacingReport,
                pacingTargetPreset = selectedPacingTarget,
                onOpenPacingDetail = onOpenPacingDetail,
                listState = listState
            )
            "characters" -> CharacterSheetSection(
                characters = workingResult.characterSheet,
                onCopyCharacters = onCopyCharacters,
                onEditCharacter = onEditCharacter,
                listState = listState
            )
            "timeline" -> TimelineSection(
                timeline = workingResult.timeline,
                intervalSeconds = workingResult.intervalSeconds,
                maxSceneDuration = workingResult.maxSceneDuration,
                styleName = styleName,
                onCopyTimeline = onCopyTimeline,
                onCopyAllVisualPrompts = onCopyAllVisualPrompts,
                onCopyScenePrompt = onCopyScenePrompt,
                onEditTimelineItem = onEditTimelineItem,
                listState = listState
            )
            "segments" -> SegmentSummariesSection(
                summaries = workingResult.segmentSummaries,
                models = workingResult.segmentModels,
                onCopyAll = onCopySegmentSummaries,
                onCopySegment = onCopyScenePrompt,
                listState = listState
            )
            "series_bible" -> effectiveBible?.let { bible ->
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm),
                    contentPadding = PaddingValues(bottom = RekapinSpacing.lg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item(key = "series_bible_tab", contentType = "series_bible") {
                        SeriesBibleSection(
                            bible = bible,
                            onCopy = onCopySeriesBible,
                            defaultExpanded = true
                        )
                    }
                }
            } ?: Text(
                text = "Tidak ada memori seri.",
                style = RekapinTypography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(RekapinSpacing.sm)
            )
            else -> AllResultsSection(
                result = workingResult,
                styleName = styleName,
                narrationModeName = effectiveModeName,
                seriesBible = effectiveBible,
                stageTimings = stageTimings,
                onCopyNarration = onCopyNarration,
                onCopyCharacters = onCopyCharacters,
                onCopyTimeline = onCopyTimeline,
                onCopyAllVisualPrompts = onCopyAllVisualPrompts,
                onCopyScenePrompt = onCopyScenePrompt,
                onCopySegmentSummaries = onCopySegmentSummaries,
                onCopySeriesBible = onCopySeriesBible,
                onEditCharacter = onEditCharacter,
                onEditTimelineItem = onEditTimelineItem,
                listState = listState
            )
        }
    }
}

@Composable
internal fun SubtitleExportDialog(
    selectedMode: SubtitleSourceMode,
    onSelectMode: (SubtitleSourceMode) -> Unit,
    srtContent: String,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val cueCount = remember(srtContent) { SubtitleExporter.countSrtCues(srtContent) }
    val hasContent = srtContent.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(RekapinRadii.lg),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Subtitles,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                Text(
                    text = "Ekspor Subtitle (.srt)",
                    style = RekapinTypography.sectionTitle
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
            ) {
                Text(
                    text = "Sumber teks subtitle",
                    style = RekapinTypography.bodyBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ChoiceChips(
                    items = SubtitleSourceMode.entries,
                    selectedItem = selectedMode,
                    onItemSelected = onSelectMode,
                    labelProvider = { it.label },
                    testTagProvider = { "chip_srt_mode_${it.code}" },
                    compact = true
                )
                Text(
                    text = "${selectedMode.description} • $cueCount baris subtitle",
                    style = RekapinTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                RekapinCard(
                    shape = RoundedCornerShape(RekapinRadii.sm),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 84.dp, max = 180.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(RekapinRadii.sm)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(RekapinSpacing.sm)
                    ) {
                        Text(
                            text = if (hasContent) srtContent else "Tidak ada baris subtitle untuk sumber yang dipilih.",
                            style = RekapinTypography.caption.copy(
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 15.sp
                            ),
                            color = if (hasContent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
                ) {
                    OutlinedButton(
                        onClick = onCopy,
                        enabled = hasContent,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("btn_copy_srt")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                        Text("Salin .srt", style = RekapinTypography.captionBold, maxLines = 1)
                    }
                    Button(
                        onClick = onSave,
                        enabled = hasContent,
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("btn_save_srt")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                        Text("Simpan .srt", style = RekapinTypography.captionBold, maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}
