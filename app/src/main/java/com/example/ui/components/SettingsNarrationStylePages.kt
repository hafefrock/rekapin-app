package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NarrationLanguage
import com.example.data.model.NarrationMode
import com.example.data.model.VisualStyle
import com.example.ui.theme.RekapinRadii
import com.example.ui.theme.RekapinSpacing
import com.example.ui.theme.RekapinTypography

@Composable
internal fun SettingsLanguageAndModePage(
    selectedLanguage: NarrationLanguage,
    selectedNarrationMode: NarrationMode,
    onSelectLanguage: (NarrationLanguage) -> Unit,
    onSelectNarrationMode: ((NarrationMode) -> Unit)?
) {
    val isVoiceoverMode = (selectedNarrationMode == NarrationMode.REKAP_VOICEOVER)
    val isEffectiveEnglish =
        (selectedLanguage == NarrationLanguage.ENGLISH_USA) ||
            (isVoiceoverMode && selectedLanguage == NarrationLanguage.ORIGINAL)
    val isIndonesian = (selectedLanguage == NarrationLanguage.INDONESIAN)
    val isOriginalEnabled = !isVoiceoverMode
    val isOriginalSelected =
        (!isVoiceoverMode && selectedLanguage == NarrationLanguage.ORIGINAL)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = RekapinSpacing.md, vertical = RekapinSpacing.md)
            .testTag("scroll_container_language_settings"),
        verticalArrangement = Arrangement.spacedBy(RekapinSpacing.md)
    ) {
        Text(
            text = "Bagian lain (ringkasan, character sheet, deskripsi adegan, visual prompt) tetap berbahasa Inggris.",
            style = RekapinTypography.caption.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader(
                title = "Bahasa Narasi",
                titleStyle = RekapinTypography.bodyBold
            )
            RekapinCard(modifier = Modifier.fillMaxWidth()) {
                RekapinRadioRow(
                    title = "English (USA)",
                    subtitle = "Narasi rekap dalam American English",
                    badgeText = if (isVoiceoverMode && selectedLanguage == NarrationLanguage.ORIGINAL) {
                        "(berlaku)"
                    } else {
                        "(default)"
                    },
                    selected = isEffectiveEnglish,
                    onClick = { onSelectLanguage(NarrationLanguage.ENGLISH_USA) },
                    modifier = Modifier.testTag("card_lang_english"),
                    radioModifier = Modifier.testTag("radio_lang_english")
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    modifier = Modifier.padding(horizontal = RekapinSpacing.md)
                )
                RekapinRadioRow(
                    title = "Bahasa Indonesia",
                    subtitle = "Narasi rekap dalam Bahasa Indonesia natural",
                    selected = isIndonesian,
                    onClick = { onSelectLanguage(NarrationLanguage.INDONESIAN) },
                    modifier = Modifier.testTag("card_lang_indonesian"),
                    radioModifier = Modifier.testTag("radio_lang_indonesian")
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    modifier = Modifier.padding(horizontal = RekapinSpacing.md)
                )
                RekapinRadioRow(
                    title = "Bahasa Asli",
                    subtitle = "Transkrip tetap dalam bahasa suara di video, tanpa terjemahan",
                    warningText = if (!isOriginalEnabled) {
                        "Hanya untuk mode Dialog Konten / Narasi Asli"
                    } else {
                        null
                    },
                    enabled = isOriginalEnabled,
                    selected = isOriginalSelected,
                    onClick = { onSelectLanguage(NarrationLanguage.ORIGINAL) },
                    modifier = Modifier.testTag("card_language_original"),
                    radioModifier = Modifier.testTag("radio_lang_original")
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader(
                title = "Mode Narasi",
                titleStyle = RekapinTypography.bodyBold
            )
            RekapinCard(modifier = Modifier.fillMaxWidth()) {
                NarrationMode.entries.forEachIndexed { index, mode ->
                    val isSelected = (selectedNarrationMode == mode)
                    val isDefault = (mode == NarrationMode.DEFAULT)
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                            modifier = Modifier.padding(horizontal = RekapinSpacing.md)
                        )
                    }
                    RekapinRadioRow(
                        title = mode.displayName,
                        subtitle = mode.description,
                        badgeText = if (isDefault) "(default)" else null,
                        selected = isSelected,
                        onClick = { onSelectNarrationMode?.invoke(mode) },
                        modifier = Modifier.testTag("card_mode_${mode.code}"),
                        radioModifier = Modifier.testTag("radio_mode_${mode.code}")
                    )
                }
            }
        }

        if (selectedNarrationMode != NarrationMode.REKAP_VOICEOVER) {
            RekapinCard(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("warning_copyright_transcript")
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.sm),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(16.dp)
                    )
                    Text(
                        text = "Perhatian hak cipta: mode ini menyalin dialog atau narasi asli dari video. Isinya bisa dilindungi hak cipta, jadi jangan diunggah mentah-mentah ke YouTube. Pakai sebagai bahan referensi, lalu tulis ulang dengan kata-katamu sendiri.",
                        style = RekapinTypography.caption,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsVisualStylesPage(
    visualStyles: List<VisualStyle>,
    selectedStyle: VisualStyle?,
    onSelectStyle: ((VisualStyle) -> Unit)?,
    onAddCustomStyle: ((name: String, promptDescription: String) -> Unit)?,
    onUpdateCustomStyle: ((id: String, name: String, promptDescription: String) -> Unit)?,
    onDeleteCustomStyle: ((id: String) -> Unit)?
) {
    var showStyleDialog by remember { mutableStateOf(false) }
    var isEditingStyle by remember { mutableStateOf(false) }
    var styleToEditId by remember { mutableStateOf<String?>(null) }
    var styleInputName by remember { mutableStateOf("") }
    var styleInputPrompt by remember { mutableStateOf("") }
    var styleToDelete by remember { mutableStateOf<VisualStyle?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = RekapinSpacing.md, vertical = RekapinSpacing.md)
    ) {
        SectionHeader(
            title = "Daftar Gaya (${visualStyles.size})",
            titleStyle = RekapinTypography.bodyBold,
            action = {
                FilledTonalButton(
                    onClick = {
                        isEditingStyle = false
                        styleToEditId = null
                        styleInputName = ""
                        styleInputPrompt = ""
                        showStyleDialog = true
                    },
                    shape = RoundedCornerShape(RekapinRadii.sm),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    contentPadding = PaddingValues(
                        horizontal = RekapinSpacing.md,
                        vertical = 0.dp
                    ),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("btn_add_custom_style")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                    Text("Gaya Baru", style = RekapinTypography.captionBold)
                }
            }
        )

        Spacer(modifier = Modifier.height(RekapinSpacing.sm))

        if (visualStyles.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(RekapinSpacing.sm))
                Text(
                    text = "Belum ada gaya visual.",
                    style = RekapinTypography.body.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
            ) {
                items(visualStyles, key = { it.id }) { style ->
                    val isSelected = (selectedStyle?.id == style.id)
                    RekapinCard(
                        isSelected = isSelected,
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        onClick = { onSelectStyle?.invoke(style) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("style_item_${style.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(RekapinSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 40.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onSelectStyle?.invoke(style) },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                                Text(
                                    text = style.name,
                                    style = RekapinTypography.bodyBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(RekapinRadii.sm))
                                        .background(
                                            if (style.isBuiltIn) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHighest
                                            }
                                        )
                                        .padding(
                                            horizontal = RekapinSpacing.sm,
                                            vertical = 2.dp
                                        )
                                ) {
                                    Text(
                                        text = if (style.isBuiltIn) "Bawaan" else "Gaya Sendiri",
                                        style = RekapinTypography.captionMedium.copy(fontSize = 11.sp),
                                        color = if (style.isBuiltIn) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }

                            Text(
                                text = style.promptDescription,
                                style = RekapinTypography.caption.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = RekapinSpacing.xs)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (style.isBuiltIn) {
                                    TextButton(
                                        onClick = {
                                            isEditingStyle = false
                                            styleToEditId = null
                                            styleInputName = "${style.name} (Kustom)"
                                            styleInputPrompt = style.promptDescription
                                            showStyleDialog = true
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        contentPadding = PaddingValues(
                                            horizontal = RekapinSpacing.sm,
                                            vertical = 0.dp
                                        ),
                                        modifier = Modifier
                                            .height(32.dp)
                                            .testTag("btn_copy_style_${style.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                                        Text(
                                            text = "Salin ke gaya sendiri",
                                            style = RekapinTypography.captionBold.copy(fontSize = 11.sp)
                                        )
                                    }
                                } else {
                                    TextButton(
                                        onClick = {
                                            isEditingStyle = true
                                            styleToEditId = style.id
                                            styleInputName = style.name
                                            styleInputPrompt = style.promptDescription
                                            showStyleDialog = true
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        contentPadding = PaddingValues(
                                            horizontal = RekapinSpacing.sm,
                                            vertical = 0.dp
                                        ),
                                        modifier = Modifier
                                            .height(32.dp)
                                            .testTag("btn_edit_style_${style.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                                        Text(
                                            text = "Ubah",
                                            style = RekapinTypography.captionBold.copy(fontSize = 11.sp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(RekapinSpacing.xs))

                                    TextButton(
                                        onClick = { styleToDelete = style },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                        ),
                                        contentPadding = PaddingValues(
                                            horizontal = RekapinSpacing.sm,
                                            vertical = 0.dp
                                        ),
                                        modifier = Modifier
                                            .height(32.dp)
                                            .testTag("btn_delete_style_${style.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                                        Text(
                                            text = "Hapus",
                                            style = RekapinTypography.captionBold.copy(fontSize = 11.sp)
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

    if (showStyleDialog) {
        AlertDialog(
            onDismissRequest = { showStyleDialog = false },
            shape = RoundedCornerShape(RekapinRadii.lg),
            title = {
                Text(
                    text = if (isEditingStyle) "Ubah Gaya Visual" else "Tambah Gaya Visual Sendiri",
                    style = RekapinTypography.bodyBold.copy(fontSize = 15.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
                ) {
                    CompactTextField(
                        value = styleInputName,
                        onValueChange = { styleInputName = it },
                        label = "Nama Gaya",
                        placeholder = "mis. Mecha Cyberpunk",
                        singleLine = true,
                        fieldModifier = Modifier.testTag("input_style_name"),
                        modifier = Modifier.fillMaxWidth()
                    )

                    CompactTextField(
                        value = styleInputPrompt,
                        onValueChange = { styleInputPrompt = it },
                        label = "Deskripsi Gaya Visual",
                        placeholder = "2D Japanese mecha anime style, detailed mechanical lineart...",
                        singleLine = false,
                        minLines = 3,
                        maxLines = 5,
                        supportingText = "Teks bebas, disarankan dalam bahasa Inggris, tanpa tanda titik di akhir.",
                        fieldModifier = Modifier.testTag("input_style_prompt"),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = styleInputName.trim()
                        val cleanPrompt = styleInputPrompt.trim().removeSuffix(".")
                        if (cleanName.isNotBlank() && cleanPrompt.isNotBlank()) {
                            if (isEditingStyle) {
                                styleToEditId?.let { id ->
                                    onUpdateCustomStyle?.invoke(id, cleanName, cleanPrompt)
                                }
                            } else {
                                onAddCustomStyle?.invoke(cleanName, cleanPrompt)
                            }
                            showStyleDialog = false
                        }
                    },
                    enabled = styleInputName.trim().isNotBlank() && styleInputPrompt.trim().isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(RekapinRadii.sm),
                    contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("btn_save_style")
                ) {
                    Text("Simpan", style = RekapinTypography.captionBold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showStyleDialog = false },
                    shape = RoundedCornerShape(RekapinRadii.sm),
                    contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Batal", style = RekapinTypography.captionBold)
                }
            }
        )
    }

    styleToDelete?.let { targetStyle ->
        AlertDialog(
            onDismissRequest = { styleToDelete = null },
            shape = RoundedCornerShape(RekapinRadii.lg),
            title = {
                Text(
                    text = "Hapus Gaya Visual",
                    style = RekapinTypography.bodyBold.copy(fontSize = 15.sp)
                )
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus gaya buatan sendiri \"${targetStyle.name}\"?",
                    style = RekapinTypography.body.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCustomStyle?.invoke(targetStyle.id)
                        styleToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(RekapinRadii.sm),
                    contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("btn_confirm_delete_style")
                ) {
                    Text("Hapus", style = RekapinTypography.captionBold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { styleToDelete = null },
                    shape = RoundedCornerShape(RekapinRadii.sm),
                    contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Batal", style = RekapinTypography.captionBold)
                }
            }
        )
    }
}
