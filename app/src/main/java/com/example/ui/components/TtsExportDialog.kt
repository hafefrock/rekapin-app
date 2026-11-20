package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.RekapinRadii
import com.example.ui.theme.RekapinSpacing
import com.example.ui.theme.RekapinTypography

@Composable
internal fun TtsExportDialog(
    selectedSource: TtsSourceType,
    onSelectSource: (TtsSourceType) -> Unit,
    selectedPauseStyle: TtsPauseStyle,
    onSelectPauseStyle: (TtsPauseStyle) -> Unit,
    selectedPace: TtsReadingPace,
    onSelectPace: (TtsReadingPace) -> Unit,
    ttsScript: String,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    sourceVideoSeconds: Long? = null,
    selectedTargetPreset: PacingTargetPreset = PacingTargetPreset.DEFAULT,
    onOpenPacingDetail: (() -> Unit)? = null
) {
    val stats = remember(ttsScript, selectedPace) {
        TtsScriptExporter.computeStats(ttsScript, selectedPace)
    }
    val statsLabel = remember(stats) {
        TtsScriptExporter.formatStatsSummary(stats)
    }
    val pacingReport = remember(ttsScript, sourceVideoSeconds, selectedTargetPreset, selectedPace) {
        val sec = sourceVideoSeconds?.takeIf { it > 0L } ?: return@remember null
        RecapPacingAnalyzer.analyzeOverall(
            script = ttsScript,
            sourceVideoSeconds = sec,
            targetPreset = selectedTargetPreset,
            pace = selectedPace
        )
    }
    val hasContent = ttsScript.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(RekapinRadii.lg),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                Text(
                    text = "Ekspor Naskah Khusus TTS",
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
                    text = "Sumber naskah",
                    style = RekapinTypography.bodyBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ChoiceChips(
                    items = TtsSourceType.entries,
                    selectedItem = selectedSource,
                    onItemSelected = onSelectSource,
                    labelProvider = { it.label },
                    testTagProvider = { "chip_tts_source_${it.code}" },
                    compact = true
                )
                Text(
                    text = selectedSource.description,
                    style = RekapinTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Format jeda suara",
                    style = RekapinTypography.bodyBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ChoiceChips(
                    items = TtsPauseStyle.entries,
                    selectedItem = selectedPauseStyle,
                    onItemSelected = onSelectPauseStyle,
                    labelProvider = { it.label },
                    testTagProvider = { "chip_tts_pause_${it.code}" },
                    compact = true
                )
                Text(
                    text = selectedPauseStyle.description,
                    style = RekapinTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Kecepatan baca (estimasi durasi)",
                    style = RekapinTypography.bodyBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ChoiceChips(
                    items = TtsReadingPace.entries,
                    selectedItem = selectedPace,
                    onItemSelected = onSelectPace,
                    labelProvider = { it.label },
                    testTagProvider = { "chip_tts_pace_${it.wpm}" },
                    compact = true
                )

                Surface(
                    shape = RoundedCornerShape(RekapinRadii.sm),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("text_tts_stats")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = RekapinSpacing.sm, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = statsLabel,
                            style = RekapinTypography.captionBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (pacingReport != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Pacing (${selectedTargetPreset.label}): ${pacingReport.status.label} (${RecapPacingAnalyzer.formatSignedDelta(pacingReport.deltaSeconds)})",
                                    style = RekapinTypography.caption.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (onOpenPacingDetail != null) {
                                    TextButton(
                                        onClick = onOpenPacingDetail,
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier
                                            .height(26.dp)
                                            .testTag("btn_tts_open_pacing")
                                    ) {
                                        Text(
                                            text = "Detail",
                                            style = RekapinTypography.captionBold.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                RekapinCard(
                    shape = RoundedCornerShape(RekapinRadii.sm),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp, max = 180.dp)
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
                            text = if (hasContent) ttsScript else "Tidak ada teks narasi atau dialog yang dapat disusun untuk mode ini.",
                            style = RekapinTypography.caption.copy(
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            ),
                            color = if (hasContent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("preview_tts_script")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)) {
                OutlinedButton(
                    onClick = onCopy,
                    enabled = hasContent,
                    shape = RoundedCornerShape(RekapinRadii.sm),
                    border = BorderStroke(
                        1.dp,
                        if (hasContent) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
                    ),
                    contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("btn_copy_tts_script")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Salin",
                        style = RekapinTypography.captionBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
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
                        .height(36.dp)
                        .testTag("btn_save_tts_script")
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Simpan .txt",
                        style = RekapinTypography.captionBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_close_tts_dialog")
            ) {
                Text("Tutup")
            }
        }
    )
}
