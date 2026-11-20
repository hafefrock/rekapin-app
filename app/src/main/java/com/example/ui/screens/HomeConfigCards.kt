package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.AnimeAnalysisEntity
import com.example.data.model.ModelUsageInfo
import com.example.data.model.PromptCutMode
import com.example.data.model.SelectedVideo
import com.example.data.model.SeriesBible
import com.example.data.model.VisualStyle
import com.example.ui.components.ChoiceChips
import com.example.ui.components.CollapsibleSection
import com.example.ui.components.RekapinCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.RekapinRadii
import com.example.ui.theme.RekapinSpacing
import com.example.ui.theme.RekapinTypography
import com.example.util.SegmentCalculator
import com.example.util.SegmentDurationOption
import com.example.util.SegmentPlanSummary

@Composable
internal fun MainSettingsCard(
    visualStyles: List<VisualStyle>,
    selectedStyle: VisualStyle,
    onSelectStyle: (VisualStyle) -> Unit,
    promptCutMode: PromptCutMode,
    onPromptCutModeChanged: (PromptCutMode) -> Unit,
    promptInterval: Int,
    onIntervalChanged: (Int) -> Unit,
    maxSceneDurationSeconds: Int,
    onMaxSceneDurationChanged: (Int) -> Unit
) {
    RekapinCard(
        shape = RoundedCornerShape(RekapinRadii.md),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(RekapinSpacing.md),
            verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
        ) {
            SectionHeader(
                title = "Pengaturan Utama",
                titleStyle = RekapinTypography.bodyBold
            )

            // 1. Gaya Visual
            Column(verticalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)) {
                Text(
                    text = "Gaya Visual",
                    style = RekapinTypography.captionBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ChoiceChips(
                    items = visualStyles,
                    selectedItem = selectedStyle,
                    onItemSelected = onSelectStyle,
                    labelProvider = { it.name },
                    compact = true,
                    testTagProvider = { style ->
                        val idx = visualStyles.indexOf(style)
                        "menu_item_style_$idx"
                    },
                    modifier = Modifier.testTag("dropdown_style_selector")
                )
                Text(
                    text = selectedStyle.promptDescription,
                    style = RekapinTypography.caption.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 2. Mode Potong Prompt
            Column(verticalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)) {
                Text(
                    text = "Mode Potong Prompt",
                    style = RekapinTypography.captionBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ChoiceChips(
                    items = listOf(PromptCutMode.FIXED_INTERVAL, PromptCutMode.PER_SCENE),
                    selectedItem = promptCutMode,
                    onItemSelected = onPromptCutModeChanged,
                    labelProvider = { mode ->
                        when (mode) {
                            PromptCutMode.FIXED_INTERVAL -> "Interval Tetap"
                            PromptCutMode.PER_SCENE -> "Per Adegan"
                        }
                    },
                    compact = true,
                    testTagProvider = { mode ->
                        when (mode) {
                            PromptCutMode.FIXED_INTERVAL -> "chip_cut_mode_fixed"
                            PromptCutMode.PER_SCENE -> "chip_cut_mode_scene"
                        }
                    }
                )

                // Baris chip kedua di bawahnya untuk interval atau batas durasi
                if (promptCutMode == PromptCutMode.FIXED_INTERVAL) {
                    Text(
                        text = "Interval",
                        style = RekapinTypography.caption.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ChoiceChips(
                        items = listOf(10, 5, 3),
                        selectedItem = promptInterval,
                        onItemSelected = onIntervalChanged,
                        labelProvider = { "$it Detik" },
                        compact = true,
                        testTagProvider = { interval ->
                            when (interval) {
                                10 -> "interval_10s_option"
                                5 -> "interval_5s_option"
                                3 -> "interval_3s_option"
                                else -> "interval_${interval}s_option"
                            }
                        }
                    )
                } else {
                    Text(
                        text = "Batas durasi adegan",
                        style = RekapinTypography.caption.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ChoiceChips(
                        items = listOf(3, 5, 10, 0),
                        selectedItem = maxSceneDurationSeconds,
                        onItemSelected = onMaxSceneDurationChanged,
                        labelProvider = { dur ->
                            if (dur == 0) "Tanpa batas" else "$dur Detik"
                        },
                        compact = true,
                        testTagProvider = { dur ->
                            when (dur) {
                                3 -> "interval_3s_option"
                                5 -> "interval_5s_option"
                                10 -> "radio_max_scene_10"
                                0 -> "radio_max_scene_unlimited"
                                else -> "radio_max_scene_$dur"
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun AdvancedSettingsCard(
    segmentDurationOption: SegmentDurationOption,
    onSegmentDurationChanged: (SegmentDurationOption) -> Unit,
    historyList: List<AnimeAnalysisEntity>,
    selectedSeriesSourceId: Long?,
    selectedSeriesSourceName: String,
    selectedSeriesSourceBible: SeriesBible?,
    onSelectSeriesSource: (Long?) -> Unit,
    models: List<String>,
    selectedModel: String,
    selectedModelUsage: ModelUsageInfo,
    onSelectModel: (String) -> Unit,
    onOpenManualQuota: (String) -> Unit
) {
    val segSummaryText = when (segmentDurationOption) {
        SegmentDurationOption.AUTO -> "Segmen otomatis"
        SegmentDurationOption.THREE_MIN -> "Segmen 3m"
        SegmentDurationOption.FIVE_MIN -> "Segmen 5m"
    }
    val seriesSummaryText = if (selectedSeriesSourceId != null) selectedSeriesSourceName else "Tanpa seri"
    val modelSummaryText = selectedModel.ifBlank { "Model bawaan" }
    val advancedSummary = "$segSummaryText · $seriesSummaryText · $modelSummaryText"

    var isAdvancedExpanded by remember { mutableStateOf(false) }

    RekapinCard(
        shape = RoundedCornerShape(RekapinRadii.md),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(RekapinSpacing.md)) {
            CollapsibleSection(
                title = "Lanjutan",
                summary = advancedSummary,
                titleStyle = RekapinTypography.bodyBold,
                headerVerticalPadding = RekapinSpacing.xs,
                isExpanded = isAdvancedExpanded,
                onToggle = { isAdvancedExpanded = !isAdvancedExpanded }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
                ) {
                    // 1. Panjang Segmen
                    Column(verticalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)) {
                        Text(
                            text = "Panjang Segmen",
                            style = RekapinTypography.captionBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ChoiceChips(
                            items = listOf(
                                SegmentDurationOption.AUTO,
                                SegmentDurationOption.THREE_MIN,
                                SegmentDurationOption.FIVE_MIN
                            ),
                            selectedItem = segmentDurationOption,
                            onItemSelected = onSegmentDurationChanged,
                            labelProvider = { opt ->
                                when (opt) {
                                    SegmentDurationOption.AUTO -> "Otomatis"
                                    SegmentDurationOption.THREE_MIN -> "3 Menit"
                                    SegmentDurationOption.FIVE_MIN -> "5 Menit"
                                }
                            },
                            compact = true,
                            testTagProvider = { opt ->
                                when (opt) {
                                    SegmentDurationOption.AUTO -> "segment_auto_option"
                                    SegmentDurationOption.THREE_MIN -> "segment_3m_option"
                                    SegmentDurationOption.FIVE_MIN -> "segment_5m_option"
                                }
                            }
                        )
                    }

                    // 2. Seri (Opsional)
                    Column(verticalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)) {
                        Text(
                            text = "Seri (Opsional)",
                            style = RekapinTypography.captionBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        SeriesSelectorRow(
                            historyList = historyList,
                            selectedSeriesSourceId = selectedSeriesSourceId,
                            selectedSeriesSourceName = selectedSeriesSourceName,
                            selectedSeriesSourceBible = selectedSeriesSourceBible,
                            onSelectSeriesSource = onSelectSeriesSource
                        )
                    }

                    // 3. Model Gemini
                    Column(verticalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)) {
                        Text(
                            text = "Model Gemini",
                            style = RekapinTypography.captionBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ModelSelectorRow(
                            models = models,
                            selectedModel = selectedModel,
                            selectedModelUsage = selectedModelUsage,
                            onSelectModel = onSelectModel,
                            onOpenManualQuota = onOpenManualQuota
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesSelectorRow(
    historyList: List<AnimeAnalysisEntity>,
    selectedSeriesSourceId: Long?,
    selectedSeriesSourceName: String,
    selectedSeriesSourceBible: SeriesBible?,
    onSelectSeriesSource: (Long?) -> Unit
) {
    var isSeriesDropdownExpanded by remember { mutableStateOf(false) }
    val completedSeriesList = remember(historyList) { historyList.filter { it.isCompleted } }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(RekapinRadii.sm),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RekapinRadii.sm))
                .clickable { isSeriesDropdownExpanded = true }
                .testTag("dropdown_series_selector")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = RekapinSpacing.sm, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selectedSeriesSourceId != null) selectedSeriesSourceName else "Tanpa seri (video mandiri)",
                        style = RekapinTypography.captionBold.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val seriesCaption = if (selectedSeriesSourceId != null) {
                        if (selectedSeriesSourceBible != null && !selectedSeriesSourceBible.isEmpty()) {
                            "Memori seri: ${selectedSeriesSourceBible.locations.size} lokasi, ${selectedSeriesSourceBible.glossary.size} istilah"
                        } else {
                            "Belum ada memori seri (dibuat setelah selesai)"
                        }
                    } else {
                        "Character sheet dimulai dari nol"
                    }
                    Text(
                        text = seriesCaption,
                        style = RekapinTypography.caption.copy(fontSize = 11.sp),
                        color = if (selectedSeriesSourceId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Pilih Seri",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        DropdownMenu(
            expanded = isSeriesDropdownExpanded,
            onDismissRequest = { isSeriesDropdownExpanded = false },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(MaterialTheme.colorScheme.surface)
                .testTag("dropdown_series_menu")
        ) {
            val isNoneSelected = (selectedSeriesSourceId == null)
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tanpa seri (video mandiri)",
                                style = if (isNoneSelected) RekapinTypography.bodyBold else RekapinTypography.body,
                                color = if (isNoneSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Character sheet dimulai dari nol",
                                style = RekapinTypography.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isNoneSelected) {
                            Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Terpilih",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                onClick = {
                    onSelectSeriesSource(null)
                    isSeriesDropdownExpanded = false
                },
                modifier = Modifier.testTag("menu_item_series_none")
            )

            if (completedSeriesList.isNotEmpty()) {
                completedSeriesList.forEach { entity ->
                    val isSelected = (selectedSeriesSourceId == entity.id)
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entity.videoName,
                                        style = if (isSelected) RekapinTypography.bodyBold else RekapinTypography.body,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${entity.totalSegments} Segmen • ${entity.formattedDate}",
                                        style = RekapinTypography.caption,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Terpilih",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelectSeriesSource(entity.id)
                            isSeriesDropdownExpanded = false
                        },
                        modifier = Modifier.testTag("menu_item_series_${entity.id}")
                    )
                }
            } else {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Belum ada analisis selesai di riwayat",
                            style = RekapinTypography.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    enabled = false,
                    onClick = {}
                )
            }
        }
    }
}

@Composable
private fun ModelSelectorRow(
    models: List<String>,
    selectedModel: String,
    selectedModelUsage: ModelUsageInfo,
    onSelectModel: (String) -> Unit,
    onOpenManualQuota: (String) -> Unit
) {
    var isModelDropdownExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Status Quota satu baris ringkas
        Surface(
            shape = RoundedCornerShape(RekapinRadii.sm),
            color = if (selectedModelUsage.isQuotaExceeded) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_quota_today_status")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = RekapinSpacing.sm, end = RekapinSpacing.xs, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Hari ini ${selectedModelUsage.used}/${selectedModelUsage.limit} · Reset ${selectedModelUsage.resetTimeFormatted}",
                    style = RekapinTypography.caption.copy(fontSize = 11.sp),
                    color = if (selectedModelUsage.isQuotaExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { onOpenManualQuota(selectedModel) },
                    contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("btn_manual_adjust_quota")
                ) {
                    Text(
                        text = "Atur manual",
                        style = RekapinTypography.captionBold.copy(fontSize = 11.sp)
                    )
                }
            }
        }

        // Dropdown Button (tinggi maks 36 dp)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { isModelDropdownExpanded = true },
                shape = RoundedCornerShape(RekapinRadii.sm),
                contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .testTag("dropdown_model_button"),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selectedModel.ifBlank { "Pilih Model" },
                        style = RekapinTypography.captionBold.copy(fontSize = 12.sp),
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Pilih Model",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = isModelDropdownExpanded,
                onDismissRequest = { isModelDropdownExpanded = false },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .testTag("dropdown_model_menu")
            ) {
                models.forEachIndexed { index, modelName ->
                    val isSelected = (modelName == selectedModel)
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = modelName,
                                    fontFamily = FontFamily.Monospace,
                                    style = if (isSelected) RekapinTypography.bodyBold else RekapinTypography.body,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Terpilih",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelectModel(modelName)
                            isModelDropdownExpanded = false
                        },
                        modifier = Modifier.testTag("menu_item_model_$index")
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlanSummaryCard(
    selectedVideo: SelectedVideo?,
    segmentPlanSummary: SegmentPlanSummary?,
    promptCutMode: PromptCutMode,
    detectedSceneCount: Int?,
    selectedModelUsage: ModelUsageInfo
) {
    RekapinCard(
        shape = RoundedCornerShape(RekapinRadii.md),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("segment_plan_summary_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(RekapinSpacing.md),
            verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
        ) {
            val sceneHint = if (selectedVideo != null && segmentPlanSummary != null) {
                if (promptCutMode == PromptCutMode.PER_SCENE) {
                    if (detectedSceneCount != null) "$detectedSceneCount adegan" else "Per adegan"
                } else {
                    "±${segmentPlanSummary.estimatedScenesCount} adegan"
                }
            } else {
                null
            }

            SectionHeader(
                title = "Ringkasan Rencana",
                titleStyle = RekapinTypography.bodyBold,
                action = if (sceneHint != null) {
                    {
                        Text(
                            text = sceneHint,
                            style = RekapinTypography.caption.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    null
                }
            )

            if (selectedVideo != null && segmentPlanSummary != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
                ) {
                    PlanMetricItem(
                        value = SegmentCalculator.formatTimeSeconds(segmentPlanSummary.rangeDurationSec),
                        label = "Durasi",
                        modifier = Modifier.weight(1f)
                    )
                    PlanMetricItem(
                        value = "${segmentPlanSummary.segmentCount}",
                        label = "Segmen",
                        modifier = Modifier.weight(1f)
                    )
                    PlanMetricItem(
                        value = "≈ ${segmentPlanSummary.totalRequestsCount}",
                        label = "Permintaan AI",
                        modifier = Modifier.weight(1f)
                    )
                }

                val needed = segmentPlanSummary.totalRequestsCount
                val remaining = selectedModelUsage.remaining
                val isInsufficient = remaining < needed

                if (isInsufficient) {
                    Surface(
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("warning_quota_insufficient")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = RekapinSpacing.sm, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Peringatan Kuota",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                            Text(
                                text = "Sisa kuota model ${selectedModelUsage.modelName} ($remaining) kurang dari estimasi ($needed).",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = RekapinTypography.caption.copy(fontSize = 11.sp)
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Pilih video untuk melihat rencana",
                    style = RekapinTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlanMetricItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(RekapinRadii.sm))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = RekapinSpacing.sm, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = RekapinTypography.bodyBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = RekapinTypography.caption.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
