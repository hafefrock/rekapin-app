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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.model.RequestPacingMode
import com.example.data.model.ThinkingLevelMode
import com.example.data.prefs.ModelUsageStorage
import com.example.ui.theme.RekapinRadii
import com.example.ui.theme.RekapinSpacing
import com.example.ui.theme.RekapinTypography

@Composable
internal fun SettingsModelPage(
    models: List<String>,
    selectedModel: String,
    isAutoSwitchModel: Boolean,
    onToggleAutoSwitch: (Boolean) -> Unit,
    selectedPacingMode: RequestPacingMode,
    onSelectPacingMode: ((RequestPacingMode) -> Unit)?,
    selectedThinkingLevel: ThinkingLevelMode,
    onSelectThinkingLevel: ((ThinkingLevelMode) -> Unit)?,
    onAddModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onMoveModel: (fromIndex: Int, toIndex: Int) -> Unit,
    onSelectModel: (String) -> Unit,
    modelUsageStorage: ModelUsageStorage?,
    onSetModelLimit: ((model: String, limit: Int) -> Unit)?,
    onSetModelUsage: ((model: String, usage: Int) -> Unit)?
) {
    var showAddModelDialog by remember { mutableStateOf(false) }
    var modelToDelete by remember { mutableStateOf<String?>(null) }
    var modelToEditQuota by remember { mutableStateOf<String?>(null) }
    var quotaRefreshTick by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = RekapinSpacing.md, vertical = RekapinSpacing.md)
    ) {
        RekapinCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingRow(
                    title = "Pindah otomatis saat kuota habis",
                    subtitle = "Ganti model otomatis saat kuota (429) habis",
                    modifier = Modifier.padding(
                        horizontal = RekapinSpacing.md,
                        vertical = 6.dp
                    ),
                    control = {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
                            Switch(
                                checked = isAutoSwitchModel,
                                onCheckedChange = onToggleAutoSwitch,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("switch_auto_switch_model")
                            )
                        }
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = RekapinSpacing.md)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RekapinSpacing.md, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SettingRow(
                        title = "Jeda antar-permintaan",
                        subtitle = selectedPacingMode.description,
                        modifier = Modifier.fillMaxWidth(),
                        control = {}
                    )
                    ChoiceChips(
                        items = RequestPacingMode.entries,
                        selectedItem = selectedPacingMode,
                        onItemSelected = { onSelectPacingMode?.invoke(it) },
                        labelProvider = { it.label },
                        testTagProvider = { "chip_pacing_${it.code}" },
                        compact = true
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = RekapinSpacing.md)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RekapinSpacing.md, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SettingRow(
                        title = "Tingkat berpikir (Gemini 3.8 Flash)",
                        subtitle = "Rendah lebih cepat dan hemat token. Hanya berlaku untuk gemini-3.8-flash.",
                        modifier = Modifier.fillMaxWidth(),
                        control = {}
                    )
                    ChoiceChips(
                        items = ThinkingLevelMode.entries,
                        selectedItem = selectedThinkingLevel,
                        onItemSelected = { onSelectThinkingLevel?.invoke(it) },
                        labelProvider = { it.label },
                        testTagProvider = { "chip_thinking_${it.code}" },
                        compact = true
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(RekapinSpacing.md))

        SectionHeader(
            title = "Daftar Model (${models.size})",
            titleStyle = RekapinTypography.bodyBold,
            action = {
                FilledTonalButton(
                    onClick = { showAddModelDialog = true },
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
                        .testTag("btn_add_model")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                    Text("Tambah Model", style = RekapinTypography.captionBold)
                }
            }
        )

        Spacer(modifier = Modifier.height(RekapinSpacing.sm))

        if (models.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = RekapinSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(RekapinSpacing.sm))
                Text(
                    text = "Belum ada model tersedia.",
                    style = RekapinTypography.body.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
            ) {
                itemsIndexed(models, key = { index, model -> "$model-$index" }) { index, model ->
                    val isSelected = (model == selectedModel)
                    RekapinCard(
                        isSelected = isSelected,
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        onClick = { onSelectModel(model) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("model_item_$index")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = RekapinSpacing.sm,
                                    vertical = 6.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = { onMoveModel(index, index - 1) },
                                    enabled = index > 0,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("btn_move_up_$index")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Pindah ke atas",
                                        tint = if (index > 0) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onMoveModel(index, index + 1) },
                                    enabled = index < models.size - 1,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("btn_move_down_$index")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = "Pindah ke bawah",
                                        tint = if (index < models.size - 1) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = RekapinSpacing.xs),
                                verticalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)
                                ) {
                                    Text(
                                        text = model,
                                        style = RekapinTypography.bodyBold.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(RekapinRadii.sm))
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                                .padding(
                                                    horizontal = 6.dp,
                                                    vertical = 2.dp
                                                )
                                        ) {
                                            Text(
                                                text = "Terpilih",
                                                style = RekapinTypography.captionBold.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }

                                if (modelUsageStorage != null) {
                                    val usageInfo = remember(model, quotaRefreshTick) {
                                        modelUsageStorage.getUsageInfo(model)
                                    }
                                    val progress = if (usageInfo.limit > 0) {
                                        (usageInfo.used.toFloat() / usageInfo.limit.toFloat()).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                    Text(
                                        text = "Urutan ke-${index + 1} • Kuota: ${usageInfo.used}/${usageInfo.limit} (sisa ${usageInfo.remaining})",
                                        style = RekapinTypography.caption.copy(fontSize = 11.sp),
                                        color = if (usageInfo.isQuotaExceeded) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        color = if (usageInfo.isQuotaExceeded) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(RekapinRadii.sm))
                                    )
                                } else {
                                    Text(
                                        text = "Urutan ke-${index + 1}",
                                        style = RekapinTypography.caption.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (modelUsageStorage != null) {
                                    IconButton(
                                        onClick = { modelToEditQuota = model },
                                        modifier = Modifier
                                            .size(34.dp)
                                            .testTag("btn_edit_quota_$index")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = "Atur batas dan hitungan kuota model",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { modelToDelete = model },
                                    enabled = models.size > 1,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .testTag("btn_delete_model_$index")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus model",
                                        tint = if (models.size > 1) {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddModelDialog) {
        AddCustomModelDialog(
            onDismiss = { showAddModelDialog = false },
            onConfirm = { newModelName ->
                onAddModel(newModelName)
                showAddModelDialog = false
            }
        )
    }

    modelToDelete?.let { modelName ->
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            shape = RoundedCornerShape(RekapinRadii.lg),
            title = {
                Text(
                    text = "Hapus Model?",
                    style = RekapinTypography.bodyBold.copy(fontSize = 15.sp)
                )
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus model \"$modelName\" dari daftar?",
                    style = RekapinTypography.body.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteModel(modelName)
                        modelToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(RekapinRadii.sm),
                    contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("btn_confirm_delete_model")
                ) {
                    Text("Hapus", style = RekapinTypography.captionBold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { modelToDelete = null },
                    shape = RoundedCornerShape(RekapinRadii.sm),
                    contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Batal", style = RekapinTypography.captionBold)
                }
            }
        )
    }

    modelToEditQuota?.let { modelName ->
        val usageInfo = modelUsageStorage?.getUsageInfo(modelName)
        if (usageInfo != null) {
            ManualQuotaDialog(
                modelName = modelName,
                currentUsage = usageInfo.used,
                currentLimit = usageInfo.limit,
                onDismiss = { modelToEditQuota = null },
                onConfirm = { newUsage, newLimit ->
                    onSetModelUsage?.invoke(modelName, newUsage)
                    onSetModelLimit?.invoke(modelName, newLimit)
                    quotaRefreshTick++
                    modelToEditQuota = null
                }
            )
        }
    }
}

@Composable
private fun AddCustomModelDialog(
    onDismiss: () -> Unit,
    onConfirm: (modelName: String) -> Unit
) {
    var modelInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(RekapinRadii.lg),
        title = {
            Text(
                text = "Tambah Model Gemini",
                style = RekapinTypography.bodyBold.copy(fontSize = 15.sp)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
            ) {
                Text(
                    text = "Contoh: gemini-2.5-flash",
                    style = RekapinTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                CompactTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    label = "Nama Model",
                    placeholder = "gemini-2.5-flash",
                    singleLine = true,
                    fieldModifier = Modifier.testTag("input_custom_model_name"),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = modelInput.trim()
                    if (clean.isNotBlank()) {
                        onConfirm(clean)
                    }
                },
                enabled = modelInput.trim().isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(RekapinRadii.sm),
                contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("btn_confirm_add_model")
            ) {
                Text("Tambah", style = RekapinTypography.captionBold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(RekapinRadii.sm),
                contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text("Batal", style = RekapinTypography.captionBold)
            }
        }
    )
}
