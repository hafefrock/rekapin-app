package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AnimeAnalysisResult
import com.example.data.model.CharacterInfo
import com.example.data.model.SeriesBible
import com.example.data.model.TimelineItem
import com.example.data.prefs.StageTimingStorage
import com.example.ui.theme.RekapinRadii
import com.example.ui.theme.RekapinSpacing
import com.example.ui.theme.RekapinTypography

@Composable
internal fun AllResultsSection(
    result: AnimeAnalysisResult,
    styleName: String = "Anime 2D standar",
    narrationModeName: String = "",
    seriesBible: SeriesBible? = null,
    stageTimings: Map<String, Long>? = null,
    onCopyNarration: () -> Unit,
    onCopyCharacters: () -> Unit,
    onCopyTimeline: () -> Unit,
    onCopyAllVisualPrompts: () -> Unit,
    onCopyScenePrompt: (String) -> Unit,
    onCopySegmentSummaries: () -> Unit,
    onCopySeriesBible: () -> Unit = {},
    onEditCharacter: ((Int) -> Unit)? = null,
    onEditTimelineItem: ((Int) -> Unit)? = null
) {
    val narrationTitle = narrationHeaderTitle(narrationModeName)

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm),
        contentPadding = PaddingValues(bottom = RekapinSpacing.lg),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (result.narration.isNotBlank()) {
            item(key = "section_narration", contentType = "narration_card") {
                SectionHeader(
                    title = narrationTitle,
                    titleStyle = RekapinTypography.bodyBold,
                    action = {
                        SectionCopyButton(
                            onClick = onCopyNarration,
                            testTag = "btn_copy_section_a"
                        )
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))
                NarrationCard(
                    narration = result.narration,
                    onCopy = onCopyNarration,
                    showCopyButton = false
                )
            }
        }

        item(key = "header_characters", contentType = "section_header") {
            if (result.narration.isNotBlank()) {
                Spacer(modifier = Modifier.height(RekapinSpacing.sm))
            }
            SectionHeader(
                title = "Karakter (${result.characterSheet.size})",
                titleStyle = RekapinTypography.bodyBold,
                action = {
                    SectionCopyButton(
                        onClick = onCopyCharacters,
                        testTag = "btn_copy_section_b"
                    )
                }
            )
        }

        if (result.characterSheet.isEmpty()) {
            item(key = "empty_characters", contentType = "empty_hint") {
                Text(
                    text = "Belum ada karakter spesifik yang terdaftar pada segmen ini.",
                    style = RekapinTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(RekapinSpacing.sm)
                )
            }
        } else {
            itemsIndexed(
                items = result.characterSheet,
                key = { index, charInfo -> "char_${index}_${charInfo.name}" },
                contentType = { _, _ -> "character_card" }
            ) { index, charInfo ->
                CharacterCard(
                    charInfo = charInfo,
                    index = index,
                    onEdit = onEditCharacter?.let { cb -> { cb(index) } }
                )
            }
        }

        item(key = "header_timeline", contentType = "section_header") {
            Spacer(modifier = Modifier.height(RekapinSpacing.sm))
            val timelineSubtitle = if (result.intervalSeconds == 0) {
                val maxDurLabel = if (result.maxSceneDuration == 0) "Tanpa batas" else "${result.maxSceneDuration}s"
                "Mode Per Adegan (Batas: $maxDurLabel) • $styleName"
            } else {
                "Interval ${result.intervalSeconds} detik • $styleName"
            }
            SectionHeader(
                title = "Timeline Visual Prompt (${result.timeline.size})",
                subtitle = timelineSubtitle,
                titleStyle = RekapinTypography.bodyBold,
                action = {
                    SectionCopyButton(
                        onClick = onCopyTimeline,
                        testTag = "btn_copy_section_c"
                    )
                }
            )
            Spacer(modifier = Modifier.height(6.dp))
            TimelineActionRow(
                onCopyTimeline = onCopyTimeline,
                onCopyAllVisualPrompts = onCopyAllVisualPrompts
            )
        }

        itemsIndexed(
            items = result.timeline,
            key = { index, item -> "tl_${index}_${item.timestamp}" },
            contentType = { _, _ -> "timeline_card" }
        ) { index, timelineItem ->
            TimelineCard(
                index = index + 1,
                item = timelineItem,
                onCopyPrompt = { onCopyScenePrompt(timelineItem.visualPrompt) },
                styleName = styleName,
                onEdit = onEditTimelineItem?.let { cb -> { cb(index) } }
            )
        }

        if (result.segmentSummaries.isNotEmpty()) {
            item(key = "header_segments", contentType = "section_header") {
                Spacer(modifier = Modifier.height(RekapinSpacing.sm))
                SectionHeader(
                    title = "Ringkasan Segmen (${result.segmentSummaries.size})",
                    titleStyle = RekapinTypography.bodyBold,
                    action = {
                        SectionCopyButton(
                            onClick = onCopySegmentSummaries,
                            testTag = "btn_copy_section_d"
                        )
                    }
                )
            }

            itemsIndexed(
                items = result.segmentSummaries,
                key = { index, _ -> "seg_$index" },
                contentType = { _, _ -> "segment_card" }
            ) { index, summary ->
                val modelName = result.segmentModels.getOrNull(index).orEmpty()
                SegmentSummaryCard(
                    index = index + 1,
                    summary = summary,
                    modelName = modelName,
                    onCopy = { onCopyScenePrompt(summary) }
                )
            }
        }

        if (seriesBible != null && !seriesBible.isEmpty()) {
            item(key = "section_series_bible", contentType = "series_bible") {
                Spacer(modifier = Modifier.height(RekapinSpacing.sm))
                SectionHeader(
                    title = "Memori Seri (Series Bible)",
                    titleStyle = RekapinTypography.bodyBold,
                    action = {
                        SectionCopyButton(
                            onClick = onCopySeriesBible,
                            testTag = "btn_copy_section_e"
                        )
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))
                SeriesBibleSection(
                    bible = seriesBible,
                    onCopy = onCopySeriesBible,
                    defaultExpanded = false
                )
            }
        }

        val hasStageTimings = !stageTimings.isNullOrEmpty() &&
            stageTimings.any { (k, _) -> k != StageTimingStorage.KEY_SEGMENT_COUNT }
        if (hasStageTimings) {
            item(key = "section_stage_timings", contentType = "stage_timings") {
                Spacer(modifier = Modifier.height(RekapinSpacing.sm))
                StageTimingsSection(
                    stageTimings = stageTimings!!,
                    fallbackSegmentCount = result.segmentSummaries.size.takeIf { it > 0 } ?: result.totalSegments
                )
            }
        }
    }
}

@Composable
internal fun StageTimingsSection(
    stageTimings: Map<String, Long>,
    fallbackSegmentCount: Int,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val visibleEntries = remember(stageTimings) {
        stageTimings.entries.filter { (key, _) -> key != StageTimingStorage.KEY_SEGMENT_COUNT }
    }
    val totalMs = stageTimings[StageTimingStorage.STAGE_TOTAL]
        ?: visibleEntries.sumOf { it.value }
    val summaryText = "Total ${StageTimingStorage.formatDuration(totalMs)}"
    val analyzedSegments = (stageTimings[StageTimingStorage.KEY_SEGMENT_COUNT] ?: 0L)
        .toInt()
        .takeIf { it > 0 }
        ?: fallbackSegmentCount

    RekapinCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_stage_timings_section")
    ) {
        Column(modifier = Modifier.padding(RekapinSpacing.md)) {
            CollapsibleSection(
                title = "Waktu Proses",
                isExpanded = isExpanded,
                onToggle = { isExpanded = !isExpanded },
                summary = summaryText,
                titleStyle = RekapinTypography.bodyBold,
                headerVerticalPadding = 2.dp
            ) {
                visibleEntries.forEach { (stageName, durationMs) ->
                    val durationText = if (stageName == StageTimingStorage.STAGE_SEGMENT_ANALYSIS && analyzedSegments > 0) {
                        val avgMs = durationMs / analyzedSegments
                        "${StageTimingStorage.formatDuration(durationMs)} (rata-rata ${StageTimingStorage.formatDuration(avgMs)}/segmen)"
                    } else {
                        StageTimingStorage.formatDuration(durationMs)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stageName,
                            style = RekapinTypography.caption.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                        Text(
                            text = durationText,
                            style = RekapinTypography.captionMedium.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
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

    RekapinCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_series_bible_section")
    ) {
        Column(modifier = Modifier.padding(RekapinSpacing.md)) {
            CollapsibleSection(
                title = "Memori Seri (Series Bible)",
                summary = "${bible.locations.size} lokasi • ${bible.glossary.size} istilah • ${bible.relationships.size} relasi",
                titleStyle = RekapinTypography.bodyBold,
                headerVerticalPadding = RekapinSpacing.xs,
                showSummaryWhenExpanded = true,
                isExpanded = isExpanded,
                onToggle = { isExpanded = !isExpanded },
                headerAction = {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("btn_copy_series_bible")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Salin Memori Seri",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    if (bible.plotSoFar.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)) {
                            Text(
                                text = "Ringkasan Cerita Sejauh Ini",
                                style = RekapinTypography.captionBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = bible.plotSoFar,
                                style = RekapinTypography.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(RekapinRadii.sm))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(RekapinSpacing.sm)
                            )
                        }
                    }

                    if (bible.locations.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)) {
                            Text(
                                text = "Lokasi Penting (${bible.locations.size})",
                                style = RekapinTypography.captionBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            bible.locations.forEach { loc ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(RekapinRadii.sm))
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .padding(RekapinSpacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = loc.name,
                                        style = RekapinTypography.bodyBold.copy(fontSize = 13.sp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (loc.description.isNotBlank()) {
                                        Text(
                                            text = loc.description,
                                            style = RekapinTypography.caption,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (bible.glossary.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)) {
                            Text(
                                text = "Daftar Istilah (${bible.glossary.size})",
                                style = RekapinTypography.captionBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            bible.glossary.forEach { g ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(RekapinRadii.sm))
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .padding(RekapinSpacing.sm)
                                ) {
                                    Text(
                                        text = g.term,
                                        style = RekapinTypography.captionBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.widthIn(min = 80.dp, max = 130.dp)
                                    )
                                    Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                                    Text(
                                        text = g.definition,
                                        style = RekapinTypography.caption,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    if (bible.relationships.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)) {
                            Text(
                                text = "Hubungan Antar Karakter (${bible.relationships.size})",
                                style = RekapinTypography.captionBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            bible.relationships.forEach { rel ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(RekapinRadii.sm))
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .padding(RekapinSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${rel.characterA} ↔ ${rel.characterB}",
                                        style = RekapinTypography.captionBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.widthIn(min = 90.dp, max = 150.dp)
                                    )
                                    Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                                    Text(
                                        text = rel.relationship,
                                        style = RekapinTypography.caption,
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
}

@Composable
internal fun NarrationSection(
    narration: String,
    modeName: String = "",
    onCopyNarration: () -> Unit,
    pacingReport: OverallPacingReport? = null,
    pacingTargetPreset: PacingTargetPreset = PacingTargetPreset.DEFAULT,
    onOpenPacingDetail: (() -> Unit)? = null
) {
    val title = narrationHeaderTitle(modeName)

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm),
        contentPadding = PaddingValues(bottom = RekapinSpacing.lg),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            SectionHeader(
                title = title,
                titleStyle = RekapinTypography.bodyBold,
                action = {
                    SectionCopyButton(
                        onClick = onCopyNarration,
                        testTag = "btn_copy_section_a"
                    )
                }
            )
            Spacer(modifier = Modifier.height(6.dp))
            NarrationCard(
                narration = narration,
                onCopy = onCopyNarration
            )
        }

        if (pacingReport != null && onOpenPacingDetail != null) {
            item {
                PacingCompactBanner(
                    report = pacingReport,
                    targetPreset = pacingTargetPreset,
                    onOpenDetail = onOpenPacingDetail
                )
            }
        }
    }
}

@Composable
internal fun CharacterSheetSection(
    characters: List<CharacterInfo>,
    onCopyCharacters: () -> Unit,
    onEditCharacter: ((Int) -> Unit)? = null
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm),
        contentPadding = PaddingValues(bottom = RekapinSpacing.lg),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            SectionHeader(
                title = "Karakter (${characters.size})",
                titleStyle = RekapinTypography.bodyBold,
                action = {
                    SectionCopyButton(
                        onClick = onCopyCharacters,
                        testTag = "btn_copy_section_b"
                    )
                }
            )
        }

        if (characters.isEmpty()) {
            item {
                Text(
                    text = "Belum ada karakter yang terdaftar pada segmen ini.",
                    style = RekapinTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(RekapinSpacing.sm)
                )
            }
        } else {
            itemsIndexed(
                items = characters,
                key = { index, char -> "char_tab_${index}_${char.name}" },
                contentType = { _, _ -> "character_card" }
            ) { index, char ->
                CharacterCard(
                    charInfo = char,
                    index = index,
                    onEdit = onEditCharacter?.let { cb -> { cb(index) } }
                )
            }
        }
    }
}

@Composable
internal fun TimelineSection(
    timeline: List<TimelineItem>,
    intervalSeconds: Int,
    maxSceneDuration: Int = 5,
    styleName: String = "Anime 2D standar",
    onCopyTimeline: () -> Unit,
    onCopyAllVisualPrompts: () -> Unit,
    onCopyScenePrompt: (String) -> Unit,
    onEditTimelineItem: ((Int) -> Unit)? = null
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm),
        contentPadding = PaddingValues(bottom = RekapinSpacing.lg),
        modifier = Modifier.fillMaxWidth()
    ) {
        item(key = "header_timeline_tab", contentType = "section_header") {
            val timelineSubtitle = if (intervalSeconds == 0) {
                val maxDurLabel = if (maxSceneDuration == 0) "Tanpa batas" else "${maxSceneDuration}s"
                "Mode Per Adegan (Batas: $maxDurLabel) • $styleName"
            } else {
                "Interval ${intervalSeconds} detik • $styleName"
            }
            SectionHeader(
                title = "Timeline Visual Prompt (${timeline.size})",
                subtitle = timelineSubtitle,
                titleStyle = RekapinTypography.bodyBold,
                action = {
                    SectionCopyButton(
                        onClick = onCopyTimeline,
                        testTag = "btn_copy_section_c"
                    )
                }
            )
            Spacer(modifier = Modifier.height(6.dp))
            TimelineActionRow(
                onCopyTimeline = onCopyTimeline,
                onCopyAllVisualPrompts = onCopyAllVisualPrompts
            )
        }

        itemsIndexed(
            items = timeline,
            key = { index, item -> "tl_tab_${index}_${item.timestamp}" },
            contentType = { _, _ -> "timeline_card" }
        ) { index, item ->
            TimelineCard(
                index = index + 1,
                item = item,
                onCopyPrompt = { onCopyScenePrompt(item.visualPrompt) },
                styleName = styleName,
                onEdit = onEditTimelineItem?.let { cb -> { cb(index) } }
            )
        }
    }
}

@Composable
internal fun SegmentSummariesSection(
    summaries: List<String>,
    models: List<String>,
    onCopyAll: () -> Unit,
    onCopySegment: (String) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm),
        contentPadding = PaddingValues(bottom = RekapinSpacing.lg),
        modifier = Modifier.fillMaxWidth()
    ) {
        item(key = "header_segments_tab", contentType = "section_header") {
            SectionHeader(
                title = "Ringkasan Segmen (${summaries.size})",
                titleStyle = RekapinTypography.bodyBold,
                action = {
                    SectionCopyButton(
                        onClick = onCopyAll,
                        testTag = "btn_copy_section_d",
                        label = "Salin Semua"
                    )
                }
            )
        }

        if (summaries.isEmpty()) {
            item(key = "empty_segments_tab", contentType = "empty_hint") {
                Text(
                    text = "Belum ada ringkasan segmen yang tercatat.",
                    style = RekapinTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(RekapinSpacing.sm)
                )
            }
        } else {
            itemsIndexed(
                items = summaries,
                key = { index, _ -> "seg_tab_$index" },
                contentType = { _, _ -> "segment_card" }
            ) { index, summary ->
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
