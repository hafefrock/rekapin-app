package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("dialog_manual_quota"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Atur Kuota Model",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = modelName,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Aplikasi hanya menghitung permintaan dari perangkat ini. Sesuaikan hitungan terpakai atau batas harian untuk model ini.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )

                // Input Terpakai Hari Ini
                Column {
                    Text(
                        text = "Permintaan Terpakai Hari Ini",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = usageText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                usageText = input
                                errorMessage = null
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_manual_usage")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { usageText = "0" },
                            modifier = Modifier.testTag("btn_reset_usage_zero")
                        ) {
                            Text("Reset ke 0", fontSize = 11.sp)
                        }
                        TextButton(
                            onClick = {
                                val limitVal = limitText.toIntOrNull() ?: currentLimit
                                usageText = limitVal.toString()
                            },
                            modifier = Modifier.testTag("btn_set_usage_full")
                        ) {
                            Text("Set Penuh", fontSize = 11.sp)
                        }
                    }
                }

                // Input Batas Harian
                Column {
                    Text(
                        text = "Batas Harian (Permintaan / Hari Pasifik)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                limitText = input
                                errorMessage = null
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        supportingText = {
                            Text(
                                text = "Bawaan: $defaultLimit permintaan/hari",
                                fontSize = 10.sp
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_manual_limit")
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp
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
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_save_manual_quota")
            ) {
                Text("Simpan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_cancel_manual_quota")
            ) {
                Text("Batal")
            }
        }
    )
}
