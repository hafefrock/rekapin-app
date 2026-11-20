package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.AnimeAnalysisEntity
import com.example.data.model.ModelUsageInfo
import com.example.data.model.NarrationMode
import com.example.data.model.PromptCutMode
import com.example.data.model.SelectedVideo
import com.example.data.model.SeriesBible
import com.example.data.model.VisualStyle
import com.example.ui.components.CollapsibleSection
import com.example.ui.components.PrimaryActionBar
import com.example.ui.components.RekapinCard
import com.example.ui.theme.RekapinBorders
import com.example.ui.theme.RekapinRadii
import com.example.ui.theme.RekapinSpacing
import com.example.ui.theme.RekapinTypography
import com.example.util.SegmentCalculator
import com.example.util.SegmentDurationOption
import com.example.util.SegmentPlanSummary
import com.example.util.VideoThumbnailHelper
import kotlinx.coroutines.delay

@Composable
internal fun IdleSetupContent(
    selectedVideo: SelectedVideo?,
    promptInterval: Int,
    promptCutMode: PromptCutMode,
    maxSceneDurationSeconds: Int,
    detectedSceneCount: Int?,
    onPromptCutModeChanged: (PromptCutMode) -> Unit,
    onMaxSceneDurationChanged: (Int) -> Unit,
    segmentDurationOption: SegmentDurationOption,
    segmentPlanSummary: SegmentPlanSummary?,
    narrationMode: NarrationMode,
    isAutoBuildSeriesBible: Boolean,
    historyList: List<AnimeAnalysisEntity>,
    models: List<String>,
    selectedModel: String,
    selectedModelUsage: ModelUsageInfo,
    visualStyles: List<VisualStyle>,
    selectedStyle: VisualStyle,
    onSelectStyle: (VisualStyle) -> Unit,
    analysisRangeStartSec: Long,
    analysisRangeEndSec: Long,
    onRangeChanged: (Long, Long) -> Unit,
    onOpenManualQuota: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectVideoClicked: () -> Unit,
    onClearVideo: () -> Unit,
    onIntervalChanged: (Int) -> Unit,
    onSegmentDurationChanged: (SegmentDurationOption) -> Unit,
    onStartAnalysis: () -> Unit,
    onResumeAnalysis: (Long) -> Unit,
    onFinishWithoutCurrentSegment: (Long) -> Unit,
    selectedSeriesSourceId: Long?,
    selectedSeriesSourceName: String,
    selectedSeriesSourceBible: SeriesBible? = null,
    onSelectSeriesSource: (Long?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    hasApiKey: Boolean
) {
    val scrollState = rememberScrollState()
    val pendingAnalysis = remember(historyList) {
        historyList.firstOrNull { !it.isCompleted && it.completedSegmentsCount > 0 }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = RekapinSpacing.md, vertical = RekapinSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
        ) {
            // Banner Lanjutkan Analisis Tertunda (jika ada yang belum selesai)
            if (pendingAnalysis != null) {
                PendingResumeBanner(
                    pendingAnalysis = pendingAnalysis,
                    onResumeAnalysis = onResumeAnalysis,
                    onFinishWithoutCurrentSegment = onFinishWithoutCurrentSegment
                )
            }

            // Banner Warning jika API key belum diatur
            if (!hasApiKey) {
                ApiKeyWarningBanner(onOpenSettings = onOpenSettings)
            }

            // 1. Kartu "Video"
            VideoSetupCard(
                selectedVideo = selectedVideo,
                analysisRangeStartSec = analysisRangeStartSec,
                analysisRangeEndSec = analysisRangeEndSec,
                onSelectVideoClicked = onSelectVideoClicked,
                onClearVideo = onClearVideo,
                onRangeChanged = onRangeChanged
            )

            // 2. Kartu "Pengaturan Utama"
            MainSettingsCard(
                visualStyles = visualStyles,
                selectedStyle = selectedStyle,
                onSelectStyle = onSelectStyle,
                promptCutMode = promptCutMode,
                onPromptCutModeChanged = onPromptCutModeChanged,
                promptInterval = promptInterval,
                onIntervalChanged = onIntervalChanged,
                maxSceneDurationSeconds = maxSceneDurationSeconds,
                onMaxSceneDurationChanged = onMaxSceneDurationChanged
            )

            // 3. Kartu "Lanjutan" (tertutup secara default)
            AdvancedSettingsCard(
                segmentDurationOption = segmentDurationOption,
                onSegmentDurationChanged = onSegmentDurationChanged,
                historyList = historyList,
                selectedSeriesSourceId = selectedSeriesSourceId,
                selectedSeriesSourceName = selectedSeriesSourceName,
                selectedSeriesSourceBible = selectedSeriesSourceBible,
                onSelectSeriesSource = onSelectSeriesSource,
                models = models,
                selectedModel = selectedModel,
                selectedModelUsage = selectedModelUsage,
                onSelectModel = onSelectModel,
                onOpenManualQuota = onOpenManualQuota
            )

            // 4. Kartu "Ringkasan Rencana"
            PlanSummaryCard(
                selectedVideo = selectedVideo,
                segmentPlanSummary = segmentPlanSummary,
                promptCutMode = promptCutMode,
                detectedSceneCount = detectedSceneCount,
                selectedModelUsage = selectedModelUsage
            )
        }

        // 5. PrimaryActionBar menempel di bawah dengan tombol "Mulai Analisis"
        val canStart = (selectedVideo != null)
        val summaryText = if (!canStart) {
            "Pilih video dulu"
        } else {
            val segCount = segmentPlanSummary?.segmentCount ?: 1
            val modelName = selectedModel.ifBlank { "Gemini" }
            "Siap dianalisis · $segCount segmen ($modelName)"
        }

        PrimaryActionBar(
            buttonText = "Mulai Analisis",
            enabled = canStart,
            summaryText = summaryText,
            icon = Icons.Default.Animation,
            onClick = onStartAnalysis,
            buttonModifier = Modifier.testTag("btn_start_analysis")
        )
    }
}

@Composable
private fun PendingResumeBanner(
    pendingAnalysis: AnimeAnalysisEntity,
    onResumeAnalysis: (Long) -> Unit,
    onFinishWithoutCurrentSegment: (Long) -> Unit
) {
    RekapinCard(
        isSelected = true,
        shape = RoundedCornerShape(RekapinRadii.md),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("banner_resume_pending")
    ) {
        Column(
            modifier = Modifier.padding(RekapinSpacing.md),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(RekapinRadii.sm))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lanjutkan Analisis Tertunda",
                        style = RekapinTypography.bodyBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${pendingAnalysis.videoName} · Segmen ${pendingAnalysis.completedSegmentsCount}/${pendingAnalysis.totalSegments}",
                        style = RekapinTypography.caption.copy(fontSize = 11.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onResumeAnalysis(pendingAnalysis.id) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(RekapinRadii.sm),
                    contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("btn_resume_pending_home")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                    Text(
                        text = "Lanjutkan",
                        style = RekapinTypography.captionBold.copy(fontSize = 12.sp)
                    )
                }

                TextButton(
                    onClick = { onFinishWithoutCurrentSegment(pendingAnalysis.id) },
                    contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("btn_finish_without_segment_home")
                ) {
                    Text(
                        text = "Selesaikan tanpa segmen ini",
                        style = RekapinTypography.captionMedium.copy(fontSize = 12.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ApiKeyWarningBanner(onOpenSettings: () -> Unit) {
    RekapinCard(
        isSelected = true,
        onClick = onOpenSettings,
        shape = RoundedCornerShape(RekapinRadii.md),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("api_key_warning_banner")
    ) {
        Row(
            modifier = Modifier.padding(RekapinSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(RekapinSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "API Key Gemini Belum Diatur",
                    style = RekapinTypography.bodyBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Ketuk untuk mengatur",
                    style = RekapinTypography.caption.copy(fontSize = 11.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VideoSetupCard(
    selectedVideo: SelectedVideo?,
    analysisRangeStartSec: Long,
    analysisRangeEndSec: Long,
    onSelectVideoClicked: () -> Unit,
    onClearVideo: () -> Unit,
    onRangeChanged: (Long, Long) -> Unit
) {
    val context = LocalContext.current

    if (selectedVideo == null) {
        RekapinCard(
            onClick = onSelectVideoClicked,
            shape = RoundedCornerShape(RekapinRadii.md),
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pick_video_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(RekapinSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Pilih Berkas Video Anime",
                    style = RekapinTypography.bodyBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = onSelectVideoClicked,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(RekapinRadii.sm),
                    contentPadding = PaddingValues(horizontal = RekapinSpacing.lg, vertical = 0.dp),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("btn_select_video")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                    Text("Pilih Video dari Galeri", style = RekapinTypography.bodyBold)
                }
            }
        }
    } else {
        RekapinCard(
            shape = RoundedCornerShape(RekapinRadii.md),
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("video_info_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(RekapinSpacing.md)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(RekapinRadii.sm))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedVideo.displayName,
                            style = RekapinTypography.bodyBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${selectedVideo.durationFormatted} · ${selectedVideo.sizeFormatted}",
                            style = RekapinTypography.caption.copy(fontSize = 11.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onClearVideo,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("clear_video_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Hapus video",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = onSelectVideoClicked,
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("btn_change_video")
                    ) {
                        Text("Ganti", style = RekapinTypography.captionBold)
                    }
                }

                if (selectedVideo.durationMs > 0) {
                    val totalVideoSeconds = (selectedVideo.durationMs / 1000).coerceAtLeast(1L)
                    val currentRangeStart = analysisRangeStartSec.coerceIn(0L, totalVideoSeconds)
                    val currentRangeEnd = if (analysisRangeEndSec <= 0L || analysisRangeEndSec > totalVideoSeconds) totalVideoSeconds else analysisRangeEndSec

                    var isRangeExpanded by remember { mutableStateOf(false) }

                    Spacer(modifier = Modifier.height(RekapinSpacing.sm))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("analysis_range_card")
                    ) {
                        Column(modifier = Modifier.padding(RekapinSpacing.md)) {
                            val rangeSummary =
                                "${SegmentCalculator.formatTimeSeconds(currentRangeStart)} – ${SegmentCalculator.formatTimeSeconds(currentRangeEnd)} · total ${SegmentCalculator.formatTimeSeconds(totalVideoSeconds)}"
                            CollapsibleSection(
                                title = "Rentang Analisis",
                                summary = rangeSummary,
                                titleStyle = RekapinTypography.bodyBold,
                                headerVerticalPadding = RekapinSpacing.xs,
                                showSummaryWhenExpanded = true,
                                isExpanded = isRangeExpanded,
                                onToggle = { isRangeExpanded = !isRangeExpanded }
                            ) {
                                AnalysisRangeEditor(
                                    context = context,
                                    videoUri = selectedVideo.uri,
                                    totalVideoSeconds = totalVideoSeconds,
                                    currentRangeStart = currentRangeStart,
                                    currentRangeEnd = currentRangeEnd,
                                    onRangeChanged = onRangeChanged
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
private fun AnalysisRangeEditor(
    context: Context,
    videoUri: Uri,
    totalVideoSeconds: Long,
    currentRangeStart: Long,
    currentRangeEnd: Long,
    onRangeChanged: (Long, Long) -> Unit
) {
    var activeThumbTimeSec by remember { mutableStateOf<Long?>(currentRangeStart) }
    var activeThumbLabel by remember { mutableStateOf("Awal") }
    var thumbBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isThumbLoading by remember { mutableStateOf(false) }

    LaunchedEffect(activeThumbTimeSec) {
        val sec = activeThumbTimeSec ?: return@LaunchedEffect
        delay(200)
        isThumbLoading = true
        val bmp = VideoThumbnailHelper.getFrameThumbnail(context, videoUri, sec)
        thumbBitmap = bmp
        isThumbLoading = false
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
    ) {
        // Thumbnail Frame Pratinjau selebar penuh kartu
        if (thumbBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .clip(RoundedCornerShape(RekapinRadii.sm))
                    .border(
                        width = RekapinBorders.selectedWidth,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(RekapinRadii.sm)
                    )
            ) {
                Image(
                    bitmap = thumbBitmap!!.asImageBitmap(),
                    contentDescription = "Thumbnail frame $activeThumbLabel",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = RekapinSpacing.xs)
                ) {
                    Text(
                        text = "$activeThumbLabel: ${SegmentCalculator.formatTimeSeconds(activeThumbTimeSec ?: 0L)}",
                        style = RekapinTypography.caption.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color.White
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .clip(RoundedCornerShape(RekapinRadii.sm))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (isThumbLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Geser untuk pratinjau",
                            style = RekapinTypography.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // RangeSlider kompak (tinggi maksimal 32 dp)
        RangeSlider(
            value = currentRangeStart.toFloat()..currentRangeEnd.toFloat(),
            onValueChange = { range ->
                val startMoved = kotlin.math.abs(range.start - currentRangeStart.toFloat()) > 0.5f
                var newStart = range.start.toLong().coerceIn(0L, totalVideoSeconds - SegmentCalculator.MIN_RANGE_SECONDS)
                var newEnd = range.endInclusive.toLong().coerceIn(SegmentCalculator.MIN_RANGE_SECONDS, totalVideoSeconds)

                if (newEnd - newStart < SegmentCalculator.MIN_RANGE_SECONDS) {
                    if (startMoved) {
                        newStart = (newEnd - SegmentCalculator.MIN_RANGE_SECONDS).coerceAtLeast(0L)
                    } else {
                        newEnd = (newStart + SegmentCalculator.MIN_RANGE_SECONDS).coerceAtMost(totalVideoSeconds)
                    }
                }

                activeThumbTimeSec = if (startMoved) newStart else newEnd
                activeThumbLabel = if (startMoved) "Awal" else "Akhir"
                if (newStart != currentRangeStart || newEnd != currentRangeEnd) {
                    onRangeChanged(newStart, newEnd)
                }
            },
            valueRange = 0f..totalVideoSeconds.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 32.dp)
                .testTag("range_slider_analysis")
        )

        var startInputText by remember(currentRangeStart) {
            mutableStateOf(SegmentCalculator.formatTimeSeconds(currentRangeStart))
        }
        var endInputText by remember(currentRangeEnd) {
            mutableStateOf(SegmentCalculator.formatTimeSeconds(currentRangeEnd))
        }
        var validationErrorMsg by remember { mutableStateOf<String?>(null) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.sm),
            verticalAlignment = Alignment.Bottom
        ) {
            CompactTimeInput(
                label = "Awal",
                value = startInputText,
                onValueChange = { input ->
                    startInputText = input
                    val parsedStart = SegmentCalculator.parseTimeSeconds(input)
                    val parsedEnd = SegmentCalculator.parseTimeSeconds(endInputText) ?: currentRangeEnd
                    if (parsedStart == null) {
                        validationErrorMsg = "Format waktu awal tidak valid (mm:ss)"
                    } else if (parsedStart < 0L) {
                        validationErrorMsg = "Waktu awal minimal 00:00"
                    } else if (parsedEnd - parsedStart < SegmentCalculator.MIN_RANGE_SECONDS) {
                        validationErrorMsg = "Selisih waktu minimal 10 detik"
                    } else if (parsedEnd > totalVideoSeconds) {
                        validationErrorMsg = "Waktu akhir melebihi durasi video"
                    } else {
                        validationErrorMsg = null
                        activeThumbTimeSec = parsedStart
                        activeThumbLabel = "Awal"
                        onRangeChanged(parsedStart, parsedEnd)
                    }
                },
                isError = validationErrorMsg != null,
                fieldModifier = Modifier.testTag("input_range_start"),
                modifier = Modifier.weight(1f)
            )

            CompactTimeInput(
                label = "Akhir",
                value = endInputText,
                onValueChange = { input ->
                    endInputText = input
                    val parsedEnd = SegmentCalculator.parseTimeSeconds(input)
                    val parsedStart = SegmentCalculator.parseTimeSeconds(startInputText) ?: currentRangeStart
                    if (parsedEnd == null) {
                        validationErrorMsg = "Format waktu akhir tidak valid (mm:ss)"
                    } else if (parsedEnd > totalVideoSeconds) {
                        validationErrorMsg = "Waktu akhir melebihi durasi video"
                    } else if (parsedEnd - parsedStart < SegmentCalculator.MIN_RANGE_SECONDS) {
                        validationErrorMsg = "Selisih waktu minimal 10 detik"
                    } else {
                        validationErrorMsg = null
                        activeThumbTimeSec = parsedEnd
                        activeThumbLabel = "Akhir"
                        onRangeChanged(parsedStart, parsedEnd)
                    }
                },
                isError = validationErrorMsg != null,
                fieldModifier = Modifier.testTag("input_range_end"),
                modifier = Modifier.weight(1f)
            )

            TextButton(
                onClick = {
                    validationErrorMsg = null
                    startInputText = SegmentCalculator.formatTimeSeconds(0L)
                    endInputText = SegmentCalculator.formatTimeSeconds(totalVideoSeconds)
                    activeThumbTimeSec = 0L
                    activeThumbLabel = "Awal"
                    onRangeChanged(0L, totalVideoSeconds)
                },
                contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .height(32.dp)
                    .testTag("btn_reset_full_video")
            ) {
                Text(
                    text = "Seluruh Video",
                    style = RekapinTypography.captionBold,
                    maxLines = 1
                )
            }
        }

        if (validationErrorMsg != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                Text(
                    text = validationErrorMsg ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = RekapinTypography.caption
                )
            }
        }
    }
}

@Composable
private fun CompactTimeInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    fieldModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = RekapinTypography.caption.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = RekapinTypography.body.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = fieldModifier
                .fillMaxWidth()
                .height(40.dp)
                .onFocusChanged { isFocused = it.isFocused },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(RekapinRadii.sm))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(RekapinRadii.sm)
                        )
                        .padding(horizontal = RekapinSpacing.sm),
                    contentAlignment = Alignment.CenterStart
                ) {
                    innerTextField()
                }
            }
        )
    }
}
