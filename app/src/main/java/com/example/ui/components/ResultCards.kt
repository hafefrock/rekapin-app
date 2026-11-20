package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AnimeAnalysisResult
import com.example.data.model.CharacterInfo
import com.example.data.model.TimelineItem
import com.example.ui.theme.RekapinRadii
import com.example.ui.theme.RekapinSpacing
import com.example.ui.theme.RekapinTypography

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ResultSummaryHeaderCard(
    result: AnimeAnalysisResult,
    effectiveModeName: String,
    languageName: String
) {
    val cutModeChipLabel = if (result.intervalSeconds == 0) {
        val maxDurLabel = if (result.maxSceneDuration == 0) "Tanpa batas" else "${result.maxSceneDuration}s"
        "Per Adegan ($maxDurLabel)"
    } else {
        "Interval ${result.intervalSeconds}s"
    }

    RekapinCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(RekapinSpacing.md),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (result.videoName.isNotBlank()) {
                Text(
                    text = result.videoName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ResultMetadataChip(text = cutModeChipLabel)
                ResultMetadataChip(
                    text = effectiveModeName,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (languageName.isNotBlank()) {
                    ResultMetadataChip(text = languageName)
                }
                ResultMetadataChip(text = "${result.totalSegments} Segmen • ${result.durationFormatted}")
            }
        }
    }
}

@Composable
internal fun ResultMetadataChip(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        shape = RoundedCornerShape(RekapinRadii.sm),
        color = containerColor,
        modifier = Modifier.height(24.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = RekapinSpacing.sm)
        ) {
            Text(
                text = text,
                style = RekapinTypography.captionMedium.copy(fontSize = 11.sp),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun ResultActionRow(
    onCopyAll: () -> Unit,
    onExportTxt: () -> Unit,
    onExportSrt: (() -> Unit)? = null,
    hasTimelineForSrt: Boolean = false,
    onEditResult: (() -> Unit)? = null,
    isEdited: Boolean = false,
    onExportTts: (() -> Unit)? = null,
    hasTtsContent: Boolean = false,
    onCheckPacing: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = onCopyAll,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(RekapinRadii.md),
                contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("btn_copy_all_results")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                Text(
                    text = "Salin Semua",
                    style = RekapinTypography.bodyBold.copy(fontSize = 12.sp),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = onExportTxt,
                shape = RoundedCornerShape(RekapinRadii.md),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("btn_export_txt")
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
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

            if (onExportSrt != null) {
                OutlinedButton(
                    onClick = onExportSrt,
                    enabled = hasTimelineForSrt,
                    shape = RoundedCornerShape(RekapinRadii.md),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (hasTimelineForSrt) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
                    ),
                    contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("btn_export_srt")
                ) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = null,
                        tint = if (hasTimelineForSrt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                    Text(
                        text = "Ekspor .srt",
                        style = RekapinTypography.bodyBold.copy(fontSize = 12.sp),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (onEditResult != null || onExportTts != null || onCheckPacing != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (onEditResult != null) {
                    FilledTonalButton(
                        onClick = onEditResult,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isEdited) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (isEdited) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        ),
                        shape = RoundedCornerShape(RekapinRadii.md),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("btn_edit_result")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isEdited) "Edit • Diedit" else "Edit Ringan",
                            style = RekapinTypography.captionBold.copy(fontSize = 11.5.sp),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (onExportTts != null) {
                    OutlinedButton(
                        onClick = onExportTts,
                        enabled = hasTtsContent,
                        shape = RoundedCornerShape(RekapinRadii.md),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (hasTtsContent) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("btn_export_tts")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = if (hasTtsContent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Naskah TTS",
                            style = RekapinTypography.captionBold.copy(fontSize = 11.5.sp),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (onCheckPacing != null) {
                    OutlinedButton(
                        onClick = onCheckPacing,
                        enabled = hasTtsContent,
                        shape = RoundedCornerShape(RekapinRadii.md),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (hasTtsContent) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(0.85f)
                            .height(36.dp)
                            .testTag("btn_check_pacing")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = if (hasTtsContent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Pacing",
                            style = RekapinTypography.captionBold.copy(fontSize = 11.5.sp),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ResultNoteCard(note: String) {
    RekapinCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(RekapinRadii.sm)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = RekapinSpacing.md, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(14.dp)
            )
            Spacer(modifier = Modifier.width(RekapinSpacing.sm))
            Text(
                text = "Catatan: $note",
                style = RekapinTypography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun SectionCopyButton(
    onClick: () -> Unit,
    testTag: String,
    label: String = "Salin"
) {
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(RekapinRadii.sm),
        contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
        modifier = Modifier
            .height(32.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(RekapinSpacing.xs))
        Text(
            text = label,
            style = RekapinTypography.captionBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

internal fun narrationHeaderTitle(modeName: String): String = when {
    modeName.contains("Dialog", ignoreCase = true) -> "Dialog Konten"
    modeName.contains("Asli", ignoreCase = true) -> "Narasi Asli Konten"
    else -> "Narasi Video"
}

@Composable
internal fun TimelineActionRow(
    onCopyTimeline: () -> Unit,
    onCopyAllVisualPrompts: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
    ) {
        OutlinedButton(
            onClick = onCopyTimeline,
            shape = RoundedCornerShape(RekapinRadii.sm),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .testTag("btn_copy_timeline_section")
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(RekapinSpacing.xs))
            Text(
                text = "Salin Timeline",
                style = RekapinTypography.captionBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        FilledTonalButton(
            onClick = onCopyAllVisualPrompts,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(RekapinRadii.sm),
            contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
            modifier = Modifier
                .weight(1.35f)
                .height(32.dp)
                .testTag("btn_copy_all_visual_prompts")
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(RekapinSpacing.xs))
            Text(
                text = "Salin Semua Visual Prompt",
                style = RekapinTypography.captionBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun NarrationCard(
    narration: String,
    onCopy: () -> Unit,
    showCopyButton: Boolean = true
) {
    RekapinCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(RekapinSpacing.md),
            verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
        ) {
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
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                    Text(
                        text = "Video Narrative Recap",
                        style = RekapinTypography.bodyBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (showCopyButton) {
                    OutlinedButton(
                        onClick = onCopy,
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("copy_narration_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                        Text(
                            text = "Salin",
                            style = RekapinTypography.captionBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text(
                text = narration,
                style = RekapinTypography.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
internal fun CharacterCard(
    charInfo: CharacterInfo,
    index: Int = 0,
    onEdit: (() -> Unit)? = null
) {
    RekapinCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(RekapinSpacing.md)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    text = charInfo.name.take(1).uppercase(),
                    style = RekapinTypography.bodyBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(RekapinSpacing.sm))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = charInfo.name,
                    style = RekapinTypography.bodyBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = charInfo.description,
                    style = RekapinTypography.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onEdit != null) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("btn_edit_character_${index + 1}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Karakter ${charInfo.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun TimelineCard(
    index: Int,
    item: TimelineItem,
    onCopyPrompt: () -> Unit,
    styleName: String = "Anime 2D standar",
    onEdit: (() -> Unit)? = null
) {
    RekapinCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(RekapinSpacing.md),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.sm),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.height(22.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                            Text(
                                text = item.timestamp,
                                style = RekapinTypography.captionBold.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1
                            )
                        }
                    }
                    Text(
                        text = "#$index • $styleName",
                        style = RekapinTypography.caption.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onEdit != null) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("btn_edit_timeline_$index")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Adegan #$index",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onCopyPrompt,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("copy_prompt_button_$index")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Salin Visual Prompt",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Text(
                text = item.visualPrompt,
                style = RekapinTypography.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (item.sceneDescription.isNotBlank() || item.dialogue.isNotBlank() || item.originalNarration.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(RekapinRadii.sm))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (item.sceneDescription.isNotBlank()) {
                        Text(
                            text = item.sceneDescription,
                            style = RekapinTypography.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.dialogue.isNotBlank()) {
                        Text(
                            text = "Dialog: ${item.dialogue}",
                            style = RekapinTypography.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.originalNarration.isNotBlank()) {
                        Text(
                            text = "Narasi: ${item.originalNarration}",
                            style = RekapinTypography.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SegmentSummaryCard(
    index: Int,
    summary: String,
    modelName: String,
    onCopy: () -> Unit
) {
    RekapinCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("segment_card_$index")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(RekapinSpacing.md),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.sm),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Segmen $index",
                        style = RekapinTypography.bodyBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (modelName.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(RekapinRadii.sm),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .height(22.dp)
                                .testTag("chip_model_segment_$index")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                                Text(
                                    text = modelName,
                                    style = RekapinTypography.captionMedium.copy(
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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

            Text(
                text = summary,
                style = RekapinTypography.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
