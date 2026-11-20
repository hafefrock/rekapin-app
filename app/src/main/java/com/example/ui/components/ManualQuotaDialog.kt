package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.RekapinRadii
import com.example.ui.theme.RekapinSpacing
import com.example.ui.theme.RekapinTypography

@Composable
fun ManualQuotaDialog(
    modelName: String,
    currentUsage: Int,
    currentLimit: Int,
    onDismiss: () -> Unit,
    onConfirm: (newUsage: Int, newLimit: Int) -> Unit
) {
    var usageText by remember { mutableStateOf(currentUsage.toString()) }
    var limitText by remember { mutableStateOf(currentLimit.toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val defaultLimit = if (modelName.contains("flash-lite", ignoreCase = true)) 500 else 20

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(RekapinRadii.lg),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("dialog_manual_quota"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Atur Kuota Model",
                        style = RekapinTypography.bodyBold.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = modelName,
                        style = RekapinTypography.caption.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
            ) {
                Text(
                    text = "Hitungan hanya dari perangkat ini.",
                    style = RekapinTypography.caption.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Input Terpakai Hari Ini
                Column(verticalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)) {
                    CompactTextField(
                        value = usageText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                usageText = input
                                errorMessage = null
                            }
                        },
                        label = "Permintaan Terpakai Hari Ini",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = errorMessage != null && (usageText.toIntOrNull()?.let { it < 0 } ?: true),
                        fieldModifier = Modifier.testTag("input_manual_usage"),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
                    ) {
                        TextButton(
                            onClick = { usageText = "0" },
                            contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("btn_reset_usage_zero")
                        ) {
                            Text(
                                text = "Reset ke 0",
                                style = RekapinTypography.captionBold.copy(fontSize = 11.sp)
                            )
                        }
                        TextButton(
                            onClick = {
                                val limitVal = limitText.toIntOrNull() ?: currentLimit
                                usageText = limitVal.toString()
                            },
                            contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("btn_set_usage_full")
                        ) {
                            Text(
                                text = "Set Penuh",
                                style = RekapinTypography.captionBold.copy(fontSize = 11.sp)
                            )
                        }
                    }
                }

                // Input Batas Harian
                CompactTextField(
                    value = limitText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) {
                            limitText = input
                            errorMessage = null
                        }
                    },
                    label = "Batas Harian (Permintaan / Hari Pasifik)",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = "Bawaan: $defaultLimit permintaan/hari",
                    isError = errorMessage != null && (limitText.toIntOrNull()?.let { it <= 0 } ?: true),
                    fieldModifier = Modifier.testTag("input_manual_limit"),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = RekapinTypography.caption.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newUsage = usageText.toIntOrNull()
                    val newLimit = limitText.toIntOrNull()
                    if (newUsage == null || newUsage < 0) {
                        errorMessage = "Jumlah terpakai harus berupa angka >= 0"
                        return@Button
                    }
                    if (newLimit == null || newLimit <= 0) {
                        errorMessage = "Batas harian harus berupa angka > 0"
                        return@Button
                    }
                    onConfirm(newUsage, newLimit)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(RekapinRadii.sm),
                contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("btn_save_manual_quota")
            ) {
                Text("Simpan", style = RekapinTypography.captionBold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(RekapinRadii.sm),
                contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("btn_cancel_manual_quota")
            ) {
                Text("Batal", style = RekapinTypography.captionBold)
            }
        }
    )
}
