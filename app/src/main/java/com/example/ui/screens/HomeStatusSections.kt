package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AnalysisState
import com.example.ui.components.CollapsibleSection
import com.example.ui.components.RekapinCard
import com.example.ui.theme.RekapinBorders
import com.example.ui.theme.RekapinRadii
import com.example.ui.theme.RekapinSpacing
import com.example.ui.theme.RekapinTypography
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgressContent(
    state: AnalysisState,
    activeKeyName: String,
    activeModelName: String,
    onCancel: () -> Unit,
    onSkipDetection: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()

    val currentModelName = when (state) {
        is AnalysisState.AnalyzingSegment -> state.modelName.ifBlank { activeModelName }
        is AnalysisState.GeneratingNarration -> state.modelName.ifBlank { activeModelName }
        else -> activeModelName
    }

    val currentStageIndex = when (state) {
        is AnalysisState.Preparing -> 0
        is AnalysisState.Uploading -> 1
        is AnalysisState.PollingFileStatus -> 2
        is AnalysisState.AnalyzingSegment -> 3
        is AnalysisState.GeneratingNarration -> 4
        else -> 0
    }

    val stageIcon = when (state) {
        is AnalysisState.Preparing -> Icons.Default.Tune
        is AnalysisState.Uploading -> Icons.Default.CloudUpload
        is AnalysisState.PollingFileStatus -> Icons.Default.Refresh
        is AnalysisState.AnalyzingSegment -> Icons.Default.Movie
        is AnalysisState.GeneratingNarration -> Icons.Default.AutoAwesome
        else -> Icons.Default.Animation
    }

    val stageTitle = when (state) {
        is AnalysisState.Preparing -> "Mempersiapkan Analisis"
        is AnalysisState.Uploading -> "Mengunggah Video"
        is AnalysisState.PollingFileStatus -> "Verifikasi Status Video"
        is AnalysisState.AnalyzingSegment -> "Menganalisis Segmen ${state.currentSegment} dari ${state.totalSegments}"
        is AnalysisState.GeneratingNarration -> "Menyusun Narasi Video"
        else -> "Memproses Analisis"
    }

    val stageSubtitle = when (state) {
        is AnalysisState.Uploading -> {
            val sentMb = state.bytesSent.toDouble() / (1024 * 1024)
            val totalMb = state.totalBytes.toDouble() / (1024 * 1024)
            String.format(Locale.US, "%.1f MB / %.1f MB", sentMb, totalMb)
        }
        is AnalysisState.PollingFileStatus -> state.statusText
        else -> ""
    }

    val (progressFraction, progressPercentText) = when (state) {
        is AnalysisState.Preparing -> {
            if (state.progress != null) {
                val p = state.progress.coerceIn(0f, 1f)
                Pair(p, "${(p * 100).toInt()}%")
            } else {
                Pair(null, null)
            }
        }
        is AnalysisState.Uploading -> {
            val p = (state.progressPercent / 100f).coerceIn(0f, 1f)
            Pair(p, "${state.progressPercent}%")
        }
        is AnalysisState.AnalyzingSegment -> {
            val p = (state.currentSegment.toFloat() / state.totalSegments.coerceAtLeast(1)).coerceIn(0f, 1f)
            Pair(p, "${(p * 100).toInt()}%")
        }
        else -> Pair(null, null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(
                start = RekapinSpacing.md,
                end = RekapinSpacing.md,
                top = RekapinSpacing.lg,
                bottom = RekapinSpacing.lg
            )
            .testTag("analysis_progress_card"),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Kepala tahap: ikon 40 dp di kiri, judul 16sp rata kiri di samping ikon
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = stageIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stageTitle,
                    style = RekapinTypography.sectionTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (stageSubtitle.isNotBlank()) {
                    Text(
                        text = stageSubtitle,
                        style = RekapinTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Indikator tahap (stepper horizontal kompak)
        StageStepper(currentStageIndex = currentStageIndex)

        // Bar progres 6 dp dengan persentase 12sp di sampingnya + pesan dinamis di bawahnya
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (progressFraction != null) {
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .then(if (state is AnalysisState.Preparing) Modifier.testTag("preparing_progress_bar") else Modifier)
                        )
                    } else {
                        LinearProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .then(if (state is AnalysisState.Preparing) Modifier.testTag("preparing_progress_bar") else Modifier)
                        )
                    }
                }
                if (progressPercentText != null) {
                    Text(
                        text = progressPercentText,
                        style = RekapinTypography.captionBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (state is AnalysisState.Preparing) {
                Text(
                    text = state.message,
                    style = RekapinTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("preparing_message_text")
                )
                if (state.progress == null) {
                    Box(modifier = Modifier.size(0.dp).testTag("preparing_circular_indicator"))
                }
            } else if (state is AnalysisState.AnalyzingSegment) {
                Text(
                    text = state.message,
                    style = RekapinTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (state is AnalysisState.GeneratingNarration && state.message.isNotBlank()) {
                Text(
                    text = state.message,
                    style = RekapinTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Info detail dalam satu RekapinCard tanpa border
        RekapinCard(
            isSelected = false,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(RekapinRadii.md),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(RekapinSpacing.md),
                verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
            ) {
                if (state is AnalysisState.AnalyzingSegment) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 32.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Bagian",
                            style = RekapinTypography.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = state.timeRange,
                            style = RekapinTypography.captionBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (activeKeyName.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(RekapinRadii.sm),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("badge_active_key_progress")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = RekapinSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                                Text(
                                    text = "Key: $activeKeyName",
                                    style = RekapinTypography.captionMedium.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (currentModelName.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(RekapinRadii.sm),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("badge_active_model_progress")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = RekapinSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                                Text(
                                    text = "Model: $currentModelName",
                                    style = RekapinTypography.captionMedium.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Aksi: tombol lewati deteksi (jika ada) & tombol batalkan
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
        ) {
            if (onSkipDetection != null && state is AnalysisState.Preparing && (state.progress != null || state.message.contains("deteksi", ignoreCase = true))) {
                Button(
                    onClick = onSkipDetection,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(RekapinRadii.sm),
                    contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("btn_skip_scene_detection")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                    Text(
                        text = "Lewati deteksi, pakai interval tetap",
                        style = RekapinTypography.captionBold.copy(fontSize = 13.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(RekapinRadii.sm),
                contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .testTag("btn_cancel_analysis")
            ) {
                Text(
                    text = "Batalkan",
                    style = RekapinTypography.captionBold.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StageStepper(currentStageIndex: Int, modifier: Modifier = Modifier) {
    val stages = listOf(
        "Persiapan" to Icons.Default.Tune,
        "Unggah" to Icons.Default.CloudUpload,
        "Verifikasi" to Icons.Default.Refresh,
        "Analisis" to Icons.Default.Movie,
        "Narasi" to Icons.Default.AutoAwesome
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        stages.forEachIndexed { index, (label, icon) ->
            val isDone = index < currentStageIndex
            val isActive = index == currentStageIndex

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isDone -> MaterialTheme.colorScheme.primary
                                isActive -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        )
                        .then(
                            if (isActive) Modifier.border(
                                width = RekapinBorders.selectedWidth,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ) else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selesai",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = label,
                    style = (if (isActive) RekapinTypography.captionBold else RekapinTypography.caption)
                        .copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = when {
                        isActive -> MaterialTheme.colorScheme.primary
                        isDone -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    maxLines = 1
                )
            }
            if (index < stages.size - 1) {
                Box(
                    modifier = Modifier
                        .weight(0.4f)
                        .padding(bottom = 18.dp)
                        .height(2.dp)
                        .background(
                            if (index < currentStageIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ErrorContent(
    errorState: AnalysisState.Error,
    activeKeyName: String,
    onRetry: () -> Unit,
    onResume: (Long?) -> Unit,
    onFinishWithoutCurrentSegment: (Long?) -> Unit,
    onPickSameVideo: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchKey: (Long?) -> Unit,
    onCheckAgain: (Long?) -> Unit = {},
    onReupload: (Long?) -> Unit = {},
    onCancel: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }
    var isSavedSegmentsExpanded by remember { mutableStateOf(false) }
    var isRawMessageExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val isTimeout = errorState.isFileProcessingTimeout
    val isFailed = errorState.isFileProcessingFailed
    val isAccessLost = errorState.isVideoAccessLost

    val headerIcon = when {
        isTimeout -> Icons.Default.Refresh
        isAccessLost -> Icons.Default.VideoFile
        else -> Icons.Default.ErrorOutline
    }
    val iconTint = when {
        isTimeout -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    val iconBg = when {
        isTimeout -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
    }
    val titleText = when {
        isTimeout -> "Video Masih Diproses di Server Google"
        isFailed -> "Pemrosesan Video Gagal"
        isAccessLost -> "Akses Video Terputus"
        else -> "Gagal Menganalisis Video"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(
                start = RekapinSpacing.md,
                end = RekapinSpacing.md,
                top = RekapinSpacing.lg,
                bottom = RekapinSpacing.lg
            )
            .testTag("error_card"),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Kepala status: ikon 40 dp + judul 16sp rata kiri, pesan 13sp dengan jarak 6 dp
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.md)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = headerIcon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = titleText,
                    style = RekapinTypography.sectionTitle,
                    color = iconTint,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = errorState.errorMessage,
                style = RekapinTypography.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Chip status kompak (tinggi 28 dp, teks 11sp): Tahap, HTTP Code, Key, Model
        val displayKeyName = errorState.activeKeyName.ifBlank { activeKeyName }
        val displayModelName = errorState.modelName
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(RekapinRadii.sm))
                    .background(iconBg)
                    .padding(horizontal = RekapinSpacing.sm)
            ) {
                Text(
                    text = if (isTimeout) "Status: PROCESSING" else "Tahap: ${errorState.stage}",
                    style = RekapinTypography.captionBold.copy(fontSize = 11.sp),
                    color = iconTint,
                    maxLines = 1
                )
            }

            if (errorState.httpCode != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(RekapinRadii.sm))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = RekapinSpacing.sm)
                ) {
                    Text(
                        text = "HTTP ${errorState.httpCode}",
                        style = RekapinTypography.captionBold.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }

            if (displayKeyName.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(RekapinRadii.sm))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = RekapinSpacing.sm)
                        .testTag("badge_active_key_error")
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = displayKeyName,
                        style = RekapinTypography.captionBold.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (displayModelName.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(RekapinRadii.sm))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = RekapinSpacing.sm)
                        .testTag("badge_active_model_error")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = displayModelName,
                        style = RekapinTypography.captionBold.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Hasil Segmen Tersimpan (CollapsibleSection dalam RekapinCard)
        if (errorState.completedSegmentsCount > 0 || errorState.completedSummaries.isNotEmpty()) {
            RekapinCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_completed_segments")
            ) {
                Column(modifier = Modifier.padding(RekapinSpacing.md)) {
                    CollapsibleSection(
                        title = "Hasil Segmen Tersimpan",
                        summary = "${errorState.completedSegmentsCount} dari ${errorState.totalSegments} segmen tersimpan aman",
                        titleStyle = RekapinTypography.bodyBold,
                        headerVerticalPadding = RekapinSpacing.xs,
                        isExpanded = isSavedSegmentsExpanded,
                        onToggle = { isSavedSegmentsExpanded = !isSavedSegmentsExpanded }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)
                        ) {
                            Text(
                                text = "Data analisis hingga segmen ${errorState.completedSegmentsCount} telah tersimpan aman di database.",
                                style = RekapinTypography.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (errorState.completedSummaries.isNotEmpty()) {
                                errorState.completedSummaries.forEachIndexed { idx, summary ->
                                    Text(
                                        text = "• Segmen ${idx + 1}: $summary",
                                        style = RekapinTypography.caption,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Detail Pesan Asli API / Server (CollapsibleSection)
        if (errorState.rawApiMessage.isNotBlank()) {
            RekapinCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(RekapinSpacing.md)) {
                    CollapsibleSection(
                        title = "Detail Pesan Asli API / Server",
                        summary = "Pesan teknis dari Google Gemini",
                        titleStyle = RekapinTypography.bodyBold,
                        headerVerticalPadding = RekapinSpacing.xs,
                        isExpanded = isRawMessageExpanded,
                        onToggle = { isRawMessageExpanded = !isRawMessageExpanded }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
                        ) {
                            Text(
                                text = errorState.rawApiMessage,
                                style = RekapinTypography.caption.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        shape = RoundedCornerShape(RekapinRadii.sm)
                                    )
                                    .padding(RekapinSpacing.sm)
                            )

                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(errorState.getFullReport()))
                                    isCopied = true
                                },
                                shape = RoundedCornerShape(RekapinRadii.sm),
                                contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .testTag("btn_copy_error_detail")
                            ) {
                                Icon(
                                    imageVector = if (isCopied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                                Text(
                                    text = if (isCopied) "Detail Error Berhasil Disalin!" else "Salin Detail Error",
                                    style = RekapinTypography.captionBold.copy(fontSize = 13.sp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Aksi utama (44 dp) & aksi sekunder kompak (36 dp)
        if (errorState.isVideoAccessLost) {
            Button(
                onClick = onPickSameVideo,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(RekapinRadii.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("btn_pick_same_video")
            ) {
                Icon(
                    imageVector = Icons.Default.VideoFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                Text(
                    text = "Pilih Video yang Sama",
                    style = RekapinTypography.bodyBold
                )
            }

            if (errorState.canSkipAndFinish) {
                Button(
                    onClick = { onFinishWithoutCurrentSegment(errorState.analysisId) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    shape = RoundedCornerShape(RekapinRadii.sm),
                    contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("btn_skip_and_finish_error")
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                    Text(
                        text = "Selesaikan tanpa segmen ini",
                        style = RekapinTypography.captionBold.copy(fontSize = 13.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(RekapinRadii.sm),
                contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .testTag("btn_error_back")
            ) {
                Text(
                    text = "Kembali ke Beranda",
                    style = RekapinTypography.captionBold.copy(fontSize = 13.sp)
                )
            }
        } else {
            // Non-AccessLost Error Flow
            val showResumeButton = errorState.isDailyQuota || errorState.canResume
            if (showResumeButton) {
                Button(
                    onClick = { onResume(errorState.analysisId) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(RekapinRadii.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_resume_from_error")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                    Text(
                        text = if (errorState.isDailyQuota) "Lanjutkan" else "Lanjutkan dari Segmen ${errorState.resumeSegment}",
                        style = RekapinTypography.bodyBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (errorState.isFileProcessingTimeout) {
                Button(
                    onClick = { onCheckAgain(errorState.analysisId) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(RekapinRadii.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_check_again")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                    Text(
                        text = "Cek lagi",
                        style = RekapinTypography.bodyBold
                    )
                }
            } else if (errorState.isFileProcessingFailed) {
                Button(
                    onClick = { onReupload(errorState.analysisId) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(RekapinRadii.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_reupload_failed")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                    Text(
                        text = "Unggah Ulang Video",
                        style = RekapinTypography.bodyBold
                    )
                }
            } else if (!errorState.isDailyQuota && !showResumeButton) {
                // Retry is the primary 44dp button
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(RekapinRadii.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_error_retry")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                    Text(
                        text = "Coba Lagi",
                        style = RekapinTypography.bodyBold
                    )
                }
            }

            if (errorState.canSkipAndFinish) {
                Button(
                    onClick = { onFinishWithoutCurrentSegment(errorState.analysisId) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    shape = RoundedCornerShape(RekapinRadii.sm),
                    contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("btn_skip_and_finish_error")
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                    Text(
                        text = "Selesaikan tanpa segmen ini",
                        style = RekapinTypography.captionBold.copy(fontSize = 13.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (errorState.isDailyQuota) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
                ) {
                    Button(
                        onClick = { onSwitchKey(errorState.analysisId) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("btn_switch_key_error")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                        Text(
                            text = "Ganti Key",
                            style = RekapinTypography.captionBold.copy(fontSize = 13.sp),
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("btn_error_settings")
                    ) {
                        Text(
                            text = "Kelola API Key",
                            style = RekapinTypography.captionBold.copy(fontSize = 13.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(RekapinRadii.sm),
                    contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("btn_error_back")
                ) {
                    Text(
                        text = "Kembali ke Beranda",
                        style = RekapinTypography.captionBold.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (errorState.isFileProcessingTimeout || errorState.isFileProcessingFailed) {
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(RekapinRadii.sm),
                    contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("btn_error_back")
                ) {
                    Text(
                        text = "Kembali ke Beranda",
                        style = RekapinTypography.captionBold.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (!showResumeButton) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
                ) {
                    OutlinedButton(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("btn_error_settings")
                    ) {
                        Text(
                            text = "Kelola API Key",
                            style = RekapinTypography.captionBold.copy(fontSize = 13.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("btn_error_back")
                    ) {
                        Text(
                            text = "Kembali",
                            style = RekapinTypography.captionBold.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
                ) {
                    OutlinedButton(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        contentPadding = PaddingValues(horizontal = RekapinSpacing.xs, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("btn_error_settings")
                    ) {
                        Text(
                            text = "API Key",
                            style = RekapinTypography.captionBold.copy(fontSize = 13.sp),
                            maxLines = 1
                        )
                    }
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        contentPadding = PaddingValues(horizontal = RekapinSpacing.xs, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("btn_error_retry")
                    ) {
                        Text(
                            text = "Coba Lagi",
                            style = RekapinTypography.captionBold.copy(fontSize = 13.sp),
                            maxLines = 1
                        )
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        contentPadding = PaddingValues(horizontal = RekapinSpacing.xs, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("btn_error_back")
                    ) {
                        Text(
                            text = "Kembali",
                            style = RekapinTypography.captionBold.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
