package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ApiKeyItem
import com.example.ui.theme.RekapinRadii
import com.example.ui.theme.RekapinSpacing
import com.example.ui.theme.RekapinTypography

@Composable
fun KeySelectionDialog(
    apiKeys: List<ApiKeyItem>,
    onDismiss: () -> Unit,
    onSelectKeyAndResume: (keyId: String) -> Unit,
    onAddKeyAndResume: (name: String, key: String) -> Unit
) {
    var showAddForm by remember { mutableStateOf(false) }
    var newKeyName by remember { mutableStateOf("Key ${apiKeys.size + 1}") }
    var newKeyValue by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(RekapinRadii.lg),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                Text(
                    text = "Ganti API Key & Lanjutkan",
                    style = RekapinTypography.bodyBold.copy(fontSize = 15.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
            ) {
                Text(
                    text = "Pilih key lain yang masih punya kuota",
                    style = RekapinTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (showAddForm) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(RekapinRadii.md))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(RekapinSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
                    ) {
                        Text(
                            text = "Tambah & Gunakan Key Baru",
                            style = RekapinTypography.bodyBold.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.primary
                        )

                        CompactTextField(
                            value = newKeyName,
                            onValueChange = { newKeyName = it },
                            label = "Nama Key",
                            singleLine = true,
                            fieldModifier = Modifier.testTag("input_switch_key_name"),
                            modifier = Modifier.fillMaxWidth()
                        )

                        CompactTextField(
                            value = newKeyValue,
                            onValueChange = { newKeyValue = it },
                            label = "Gemini API Key",
                            placeholder = "AIzaSy...",
                            singleLine = true,
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { isPasswordVisible = !isPasswordVisible },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            fieldModifier = Modifier.testTag("input_switch_key_val"),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
                        ) {
                            OutlinedButton(
                                onClick = { showAddForm = false },
                                shape = RoundedCornerShape(RekapinRadii.sm),
                                contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            ) {
                                Text("Batal", style = RekapinTypography.captionBold)
                            }

                            Button(
                                onClick = {
                                    val cleanKey = newKeyValue.trim()
                                    if (cleanKey.isNotBlank()) {
                                        onAddKeyAndResume(newKeyName.trim(), cleanKey)
                                    }
                                },
                                enabled = newKeyValue.trim().isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(RekapinRadii.sm),
                                contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .testTag("btn_save_switch_key")
                            ) {
                                Text("Gunakan", style = RekapinTypography.captionBold)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
                    ) {
                        items(apiKeys, key = { it.id }) { item ->
                            RekapinCard(
                                isSelected = item.isActive,
                                shape = RoundedCornerShape(RekapinRadii.md),
                                containerColor = if (item.isActive) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                                },
                                onClick = { onSelectKeyAndResume(item.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("select_key_${item.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 40.dp)
                                        .padding(horizontal = RekapinSpacing.md, vertical = RekapinSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = item.name,
                                                style = RekapinTypography.bodyBold.copy(fontSize = 13.sp),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (item.isActive) {
                                                Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(RekapinRadii.sm))
                                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Aktif Sekarang",
                                                        style = RekapinTypography.captionBold.copy(fontSize = 11.sp),
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = item.maskedKey,
                                            style = RekapinTypography.caption.copy(
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(RekapinSpacing.sm))

                                    Button(
                                        onClick = { onSelectKeyAndResume(item.id) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(RekapinRadii.sm),
                                        contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                                        modifier = Modifier
                                            .height(36.dp)
                                            .testTag("btn_use_key_${item.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                                        Text("Pilih", style = RekapinTypography.captionBold)
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { showAddForm = true },
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("btn_add_and_use_new_key")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                        Text(
                            text = "Tambah Key Baru",
                            style = RekapinTypography.bodyBold.copy(fontSize = 13.sp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(RekapinRadii.sm),
                contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("btn_dismiss_select_key")
            ) {
                Text("Batal", style = RekapinTypography.captionBold)
            }
        }
    )
}
