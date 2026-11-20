package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.prefs.StageTimingStorage
import com.example.ui.theme.RekapinRadii
import com.example.ui.theme.RekapinSpacing
import com.example.ui.theme.RekapinTypography

private val PacingBalancedContainer = Color(0xFF1D3326)
private val PacingBalancedContent = Color(0xFF7AD69A)
private val PacingWarningContainer = Color(0xFF3A2D18)
private val PacingWarningContent = Color(0xFFFFC166)

@Composable
internal fun PacingStatusBadge(
    status: PacingStatus,
    modifier: Modifier = Modifier
) {
    val (container, content) = when (status) {
        PacingStatus.BALANCED -> PacingBalancedContainer to PacingBalancedContent
        PacingStatus.SLIGHTLY_DENSE, PacingStatus.TOO_SHORT -> PacingWarningContainer to PacingWarningContent
        PacingStatus.TOO_DENSE -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        PacingStatus.EMPTY -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(RekapinRadii.sm),
        color = container,
        modifier = modifier.height(22.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = status.label,
                style = RekapinTypography.captionBold.copy(fontSize = 11.sp),
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun PacingCompactBanner(
    report: OverallPacingReport,
    targetPreset: PacingTargetPreset,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val speechText = StageTimingStorage.formatDuration(report.estimatedSpeechSeconds * 1_000L)
    val targetText = StageTimingStorage.formatDuration(report.targetVideoSeconds * 1_000L)
    val deltaText = RecapPacingAnalyzer.formatSignedDelta(report.deltaSeconds)

    RekapinCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_pacing_compact_banner"),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(RekapinRadii.md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RekapinSpacing.md, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Pacing (${targetPreset.label})",
                        style = RekapinTypography.captionBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    PacingStatusBadge(status = report.status)
                }
                Text(
                    text = "Baca ~$speechText vs Target $targetText ($deltaText)",
                    style = RekapinTypography.caption.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = onOpenDetail,
                contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("btn_open_pacing_from_banner")
            ) {
                Text(
                    text = "Detail",
                    style = RekapinTypography.captionBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
internal fun PacingAnalysisDialog(
    report: OverallPacingReport,
    selectedSource: TtsSourceType,
    onSelectSource: (TtsSourceType) -> Unit,
    selectedTargetPreset: PacingTargetPreset,
    onSelectTargetPreset: (PacingTargetPreset) -> Unit,
    selectedPace: TtsReadingPace,
    onSelectPace: (TtsReadingPace) -> Unit,
    onOpenNarrationEdit: (() -> Unit)? = null,
    onOpenTtsExport: (() -> Unit)? = null,
    onEditDenseCue: ((Int) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val speechText = StageTimingStorage.formatDuration(report.estimatedSpeechSeconds * 1_000L)
    val targetText = StageTimingStorage.formatDuration(report.targetVideoSeconds * 1_000L)
    val sourceText = StageTimingStorage.formatDuration(report.sourceVideoSeconds * 1_000L)
    val deltaText = RecapPacingAnalyzer.formatSignedDelta(report.deltaSeconds)
    val progressColor = when (report.status) {
        PacingStatus.BALANCED -> PacingBalancedContent
        PacingStatus.SLIGHTLY_DENSE, PacingStatus.TOO_SHORT -> PacingWarningContent
        PacingStatus.TOO_DENSE -> MaterialTheme.colorScheme.error
        PacingStatus.EMPTY -> MaterialTheme.colorScheme.outline
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(RekapinRadii.lg),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                Text(
                    text = "Analisis Pacing & Beat Sheet",
                    style = RekapinTypography.sectionTitle
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
            ) {
                Text(
                    text = "Target rasio durasi klip",
                    style = RekapinTypography.captionBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ChoiceChips(
                    items = PacingTargetPreset.entries,
                    selectedItem = selectedTargetPreset,
                    onItemSelected = onSelectTargetPreset,
                    labelProvider = { it.label },
                    testTagProvider = { "chip_pacing_target_${it.code}" },
                    compact = true
                )
                Text(
                    text = "${selectedTargetPreset.description} (Video asli: $sourceText → Target: $targetText)",
                    style = RekapinTypography.caption.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Kecepatan baca",
                    style = RekapinTypography.captionBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ChoiceChips(
                    items = TtsReadingPace.entries,
                    selectedItem = selectedPace,
                    onItemSelected = onSelectPace,
                    labelProvider = { it.label },
                    testTagProvider = { "chip_pacing_pace_${it.wpm}" },
                    compact = true
                )

                Text(
                    text = "Sumber naskah",
                    style = RekapinTypography.captionBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ChoiceChips(
                    items = TtsSourceType.entries,
                    selectedItem = selectedSource,
                    onItemSelected = onSelectSource,
                    labelProvider = { it.label },
                    testTagProvider = { "chip_pacing_source_${it.code}" },
                    compact = true
                )

                // Kartu Ringkasan Status Utama
                RekapinCard(
                    shape = RoundedCornerShape(RekapinRadii.md),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_pacing_summary")
                ) {
                    Column(
                        modifier = Modifier.padding(RekapinSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PacingStatusBadge(
                                status = report.status,
                                modifier = Modifier.testTag("badge_pacing_status")
                            )
                            Text(
                                text = "Selisih: $deltaText",
                                style = RekapinTypography.captionBold,
                                color = progressColor
                            )
                        }

                        LinearProgressIndicator(
                            progress = { (report.fillRatio / 1.5f).coerceIn(0f, 1f) },
                            color = progressColor,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Baca: ~$speechText (${report.scriptWordCount} kata)",
                                style = RekapinTypography.captionMedium.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Laju efektif: ${report.effectiveWpm} kpm",
                                style = RekapinTypography.caption.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "Ideal: ${report.recommendedMinWords}–${report.recommendedMaxWords} kata (~${report.idealWordCount} kata)",
                            style = RekapinTypography.caption.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = report.actionableAdvice,
                            style = RekapinTypography.caption.copy(lineHeight = 16.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Beat Sheet per Paragraf
                if (report.beats.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text(
                        text = "Beat Sheet per Paragraf (${report.beats.size} bagian)",
                        style = RekapinTypography.captionBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    report.beats.forEach { beat ->
                        val windowLabel = "${RecapPacingAnalyzer.formatTimeMMSS(beat.targetWindowStartSec)} – ${RecapPacingAnalyzer.formatTimeMMSS(beat.targetWindowEndSec)}"
                        val estText = StageTimingStorage.formatDuration(beat.estimatedSpeechSeconds * 1_000L)
                        val allocText = StageTimingStorage.formatDuration(beat.targetDurationSeconds * 1_000L)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(RekapinRadii.sm))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(RekapinSpacing.sm)
                                .testTag("pacing_beat_item_${beat.index}"),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${beat.index} • $windowLabel",
                                    style = RekapinTypography.captionBold.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                PacingStatusBadge(status = beat.status)
                            }
                            Text(
                                text = beat.previewText,
                                style = RekapinTypography.caption.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${beat.wordCount} kata • Baca ~$estText / jatah $allocText (${RecapPacingAnalyzer.formatSignedDelta(beat.deltaSeconds)})",
                                style = RekapinTypography.captionMedium.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Peringatan Cue Timeline yang Melebihi Durasi Klip
                if (report.denseCues.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = PacingWarningContent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Cue Timeline Terlalu Padat (${report.denseCues.size})",
                            style = RekapinTypography.captionBold,
                            color = PacingWarningContent
                        )
                    }

                    report.denseCues.forEach { cue ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(RekapinRadii.sm))
                                .background(PacingWarningContainer.copy(alpha = 0.65f))
                                .padding(horizontal = RekapinSpacing.sm, vertical = 6.dp)
                                .testTag("pacing_dense_cue_${cue.timelineIndex + 1}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "${cue.timestamp} (klip ${cue.clipDurationSeconds} dtk • baca ~${cue.estimatedSpeechSeconds} dtk)",
                                    style = RekapinTypography.captionBold.copy(fontSize = 11.sp),
                                    color = PacingWarningContent
                                )
                                Text(
                                    text = cue.textPreview,
                                    style = RekapinTypography.caption.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (onEditDenseCue != null) {
                                IconButton(
                                    onClick = { onEditDenseCue(cue.timelineIndex) },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("btn_edit_dense_cue_${cue.timelineIndex + 1}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit cue #${cue.timelineIndex + 1}",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)) {
                if (onOpenNarrationEdit != null) {
                    OutlinedButton(
                        onClick = onOpenNarrationEdit,
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("btn_pacing_open_edit")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Edit Narasi",
                            style = RekapinTypography.captionBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (onOpenTtsExport != null) {
                    Button(
                        onClick = onOpenTtsExport,
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("btn_pacing_open_tts")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Naskah TTS",
                            style = RekapinTypography.captionBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_close_pacing_dialog")
            ) {
                Text("Tutup")
            }
        }
    )
}
