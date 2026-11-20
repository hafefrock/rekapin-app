package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AnimeAnalysisResult
import com.example.data.model.CharacterInfo
import com.example.data.model.TimelineItem
import com.example.ui.theme.RekapinRadii
import com.example.ui.theme.RekapinSpacing
import com.example.ui.theme.RekapinTypography

@Composable
internal fun ResultQuickEditDialog(
    initialResult: AnimeAnalysisResult,
    canResetToOriginal: Boolean,
    onResetToOriginal: () -> Unit,
    onApplyAndClose: (AnimeAnalysisResult) -> Unit,
    onDismiss: () -> Unit,
    initialFindQuery: String = "",
    initialTab: Int = 0
) {
    var draftResult by remember(initialResult) { mutableStateOf(initialResult) }
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }

    var findText by remember { mutableStateOf(initialFindQuery) }
    var replaceText by remember { mutableStateOf("") }
    var ignoreCase by remember { mutableStateOf(true) }
    var wholeWord by remember { mutableStateOf(false) }
    var includeNarration by remember { mutableStateOf(true) }
    var includeCharacters by remember { mutableStateOf(true) }
    var includeTimeline by remember { mutableStateOf(true) }
    var includeSegments by remember { mutableStateOf(true) }
    var replaceFeedback by remember { mutableStateOf<String?>(null) }

    var narrationDraft by remember(draftResult.narration) { mutableStateOf(draftResult.narration) }

    val matchSummary = remember(
        draftResult,
        findText,
        ignoreCase,
        wholeWord,
        includeNarration,
        includeCharacters,
        includeTimeline,
        includeSegments
    ) {
        ResultEditHelpers.previewReplaceSummary(
            result = draftResult,
            findText = findText,
            ignoreCase = ignoreCase,
            wholeWord = wholeWord,
            scope = EditScopeOptions(
                narration = includeNarration,
                characters = includeCharacters,
                timeline = includeTimeline,
                segments = includeSegments
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(RekapinRadii.lg),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                Text(
                    text = "Edit Ringan Sebelum Ekspor",
                    style = RekapinTypography.sectionTitle,
                    modifier = Modifier.weight(1f)
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
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = "Ganti Kata",
                                style = RekapinTypography.captionBold,
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.testTag("tab_edit_find_replace")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            narrationDraft = draftResult.narration
                        },
                        text = {
                            Text(
                                text = "Edit Narasi",
                                style = RekapinTypography.captionBold,
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.testTag("tab_edit_narration")
                    )
                }

                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = findText,
                        onValueChange = {
                            findText = it
                            replaceFeedback = null
                        },
                        label = { Text("Cari nama / kata", style = RekapinTypography.caption) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_edit_find")
                    )

                    OutlinedTextField(
                        value = replaceText,
                        onValueChange = {
                            replaceText = it
                            replaceFeedback = null
                        },
                        label = { Text("Ganti menjadi", style = RekapinTypography.caption) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_edit_replace")
                    )

                    CompactCheckboxRow(
                        label = "Abaikan huruf besar/kecil",
                        checked = ignoreCase,
                        onCheckedChange = { ignoreCase = it }
                    )
                    CompactCheckboxRow(
                        label = "Cocokkan kata utuh saja",
                        checked = wholeWord,
                        onCheckedChange = { wholeWord = it }
                    )

                    Text(
                        text = "Cakupan penggantian:",
                        style = RekapinTypography.captionBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            CompactCheckboxRow("Narasi", includeNarration) { includeNarration = it }
                            CompactCheckboxRow("Karakter", includeCharacters) { includeCharacters = it }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            CompactCheckboxRow("Timeline", includeTimeline) { includeTimeline = it }
                            CompactCheckboxRow("Segmen", includeSegments) { includeSegments = it }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val summaryLine = if (findText.isBlank()) {
                            "Masukkan kata atau nama karakter yang ingin dikoreksi."
                        } else {
                            "Ditemukan ${matchSummary.totalMatches} kecocokan " +
                                "(Narasi ${matchSummary.narrationMatches}, Karakter ${matchSummary.characterMatches}, " +
                                "Timeline ${matchSummary.timelineMatches}, Segmen ${matchSummary.segmentMatches})"
                        }
                        Text(
                            text = summaryLine,
                            style = RekapinTypography.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(RekapinSpacing.sm)
                                .testTag("text_find_replace_summary")
                        )
                    }

                    replaceFeedback?.let { msg ->
                        Text(
                            text = msg,
                            style = RekapinTypography.captionBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val scope = EditScopeOptions(
                                narration = includeNarration,
                                characters = includeCharacters,
                                timeline = includeTimeline,
                                segments = includeSegments
                            )
                            val count = matchSummary.totalMatches
                            if (findText.isNotEmpty() && count > 0) {
                                val updated = ResultEditHelpers.replaceTermInResult(
                                    result = draftResult,
                                    findText = findText,
                                    replaceText = replaceText,
                                    ignoreCase = ignoreCase,
                                    wholeWord = wholeWord,
                                    scope = scope
                                )
                                draftResult = updated
                                narrationDraft = updated.narration
                                replaceFeedback = "$count kecocokan berhasil diganti."
                            }
                        },
                        enabled = findText.isNotEmpty() && matchSummary.totalMatches > 0,
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        border = BorderStroke(
                            1.dp,
                            if (findText.isNotEmpty() && matchSummary.totalMatches > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("btn_apply_find_replace")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FindReplace,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Terapkan Penggantian (${matchSummary.totalMatches})",
                            style = RekapinTypography.captionBold
                        )
                    }
                } else {
                    val wordCount = remember(narrationDraft) {
                        TtsScriptExporter.computeStats(narrationDraft).wordCount
                    }
                    Text(
                        text = "Naskah Narasi ($wordCount kata)",
                        style = RekapinTypography.captionBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = narrationDraft,
                        onValueChange = {
                            narrationDraft = it
                            draftResult = ResultEditHelpers.updateNarration(draftResult, it)
                        },
                        minLines = 6,
                        maxLines = 12,
                        textStyle = RekapinTypography.body.copy(fontSize = 13.sp, lineHeight = 19.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_edit_narration")
                    )
                }

                if (canResetToOriginal) {
                    TextButton(
                        onClick = onResetToOriginal,
                        contentPadding = PaddingValues(horizontal = RekapinSpacing.xs, vertical = 0.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("btn_reset_edits_to_original")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Kembalikan ke hasil asli",
                            style = RekapinTypography.captionBold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApplyAndClose(draftResult) },
                shape = RoundedCornerShape(RekapinRadii.sm),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("btn_save_result_edits")
            ) {
                Text("Simpan", style = RekapinTypography.captionBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
internal fun CharacterEditDialog(
    character: CharacterInfo,
    onSave: (newName: String, newDescription: String, propagateGlobally: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(character) { mutableStateOf(character.name) }
    var description by remember(character) { mutableStateOf(character.description) }
    var propagateRename by remember(character) { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(RekapinRadii.lg),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text("Edit Karakter", style = RekapinTypography.sectionTitle)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama karakter", style = RekapinTypography.caption) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_edit_char_name")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Deskripsi", style = RekapinTypography.caption) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_edit_char_desc")
                )
                if (name.trim() != character.name && name.isNotBlank()) {
                    CompactCheckboxRow(
                        label = "Ganti \"${character.name}\" di Narasi & Timeline",
                        checked = propagateRename,
                        onCheckedChange = { propagateRename = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, description, propagateRename) },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(RekapinRadii.sm),
                modifier = Modifier.testTag("btn_save_char_edit")
            ) {
                Text("Simpan", style = RekapinTypography.captionBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
internal fun TimelineItemEditDialog(
    index: Int,
    item: TimelineItem,
    onSave: (TimelineItem) -> Unit,
    onDismiss: () -> Unit
) {
    var timestamp by remember(item) { mutableStateOf(item.timestamp) }
    var dialogue by remember(item) { mutableStateOf(item.dialogue) }
    var originalNarration by remember(item) { mutableStateOf(item.originalNarration) }
    var sceneDescription by remember(item) { mutableStateOf(item.sceneDescription) }
    var visualPrompt by remember(item) { mutableStateOf(item.visualPrompt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(RekapinRadii.lg),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text("Edit Adegan #$index", style = RekapinTypography.sectionTitle)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
            ) {
                OutlinedTextField(
                    value = timestamp,
                    onValueChange = { timestamp = it },
                    label = { Text("Timestamp", style = RekapinTypography.caption) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_edit_timeline_timestamp")
                )
                OutlinedTextField(
                    value = dialogue,
                    onValueChange = { dialogue = it },
                    label = { Text("Dialog (untuk .srt / TTS)", style = RekapinTypography.caption) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_edit_timeline_dialogue")
                )
                OutlinedTextField(
                    value = originalNarration,
                    onValueChange = { originalNarration = it },
                    label = { Text("Narasi Asli (opsional)", style = RekapinTypography.caption) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_edit_timeline_original_narration")
                )
                OutlinedTextField(
                    value = sceneDescription,
                    onValueChange = { sceneDescription = it },
                    label = { Text("Deskripsi Adegan", style = RekapinTypography.caption) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_edit_timeline_scene")
                )
                OutlinedTextField(
                    value = visualPrompt,
                    onValueChange = { visualPrompt = it },
                    label = { Text("Visual Prompt", style = RekapinTypography.caption) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_edit_timeline_prompt")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        item.copy(
                            timestamp = timestamp.trim().ifBlank { item.timestamp },
                            dialogue = dialogue.trim(),
                            originalNarration = originalNarration.trim(),
                            sceneDescription = sceneDescription.trim(),
                            visualPrompt = visualPrompt.trim()
                        )
                    )
                },
                shape = RoundedCornerShape(RekapinRadii.sm),
                modifier = Modifier.testTag("btn_save_timeline_edit")
            ) {
                Text("Simpan", style = RekapinTypography.captionBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
internal fun MergedFindReplaceDialog(
    matchCountProvider: (findText: String, ignoreCase: Boolean, wholeWord: Boolean) -> Int,
    onApply: (findText: String, replaceText: String, ignoreCase: Boolean, wholeWord: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var ignoreCase by remember { mutableStateOf(true) }
    var wholeWord by remember { mutableStateOf(false) }

    val matches = remember(findText, ignoreCase, wholeWord) {
        matchCountProvider(findText, ignoreCase, wholeWord)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(RekapinRadii.lg),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FindReplace,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                Text("Ganti Kata Gabungan", style = RekapinTypography.sectionTitle)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
            ) {
                Text(
                    text = "Terapkan perbaikan ejaan atau nama karakter ke seluruh video terpilih sebelum ekspor.",
                    style = RekapinTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = findText,
                    onValueChange = { findText = it },
                    label = { Text("Cari kata / nama", style = RekapinTypography.caption) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_merged_find")
                )
                OutlinedTextField(
                    value = replaceText,
                    onValueChange = { replaceText = it },
                    label = { Text("Ganti menjadi", style = RekapinTypography.caption) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_merged_replace")
                )
                CompactCheckboxRow("Abaikan huruf besar/kecil", ignoreCase) { ignoreCase = it }
                CompactCheckboxRow("Cocokkan kata utuh saja", wholeWord) { wholeWord = it }
                Text(
                    text = if (findText.isBlank()) "Masukkan teks yang ingin dicari."
                    else "Ditemukan $matches kecocokan di video terpilih.",
                    style = RekapinTypography.captionBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(findText, replaceText, ignoreCase, wholeWord) },
                enabled = findText.isNotEmpty() && matches > 0,
                shape = RoundedCornerShape(RekapinRadii.sm),
                modifier = Modifier.testTag("btn_apply_merged_replace")
            ) {
                Text("Terapkan ($matches)", style = RekapinTypography.captionBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun CompactCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp)
            .clickable { onCheckedChange(!checked) }
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = RekapinTypography.caption,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
