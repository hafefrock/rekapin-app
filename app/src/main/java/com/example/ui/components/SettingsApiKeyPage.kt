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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun SettingsApiKeyPage(
    apiKeys: List<ApiKeyItem>,
    isKeyDecryptFailed: Boolean,
    onAddKey: (name: String, key: String) -> Unit,
    onRenameKey: (id: String, newName: String) -> Unit,
    onDeleteKey: (id: String) -> Unit,
    onSetActiveKey: (id: String) -> Unit,
    onTestKey: (suspend (key: String) -> Pair<Boolean, String>)?,
    onTestKeyWithProgress: (suspend (key: String, onProgress: (String) -> Unit) -> Pair<Boolean, String>)?
) {
    val coroutineScope = rememberCoroutineScope()
    var showAddKeyDialog by remember { mutableStateOf(false) }
    var keyToRename by remember { mutableStateOf<ApiKeyItem?>(null) }
    var keyToDelete by remember { mutableStateOf<ApiKeyItem?>(null) }
    var testingKeyId by remember { mutableStateOf<String?>(null) }
    var testingStatusText by remember { mutableStateOf<String?>(null) }
    var testJob by remember { mutableStateOf<Job?>(null) }
    var testResults by remember { mutableStateOf<Map<String, Pair<Boolean, String>>>(emptyMap()) }

    DisposableEffect(Unit) {
        onDispose {
            testJob?.cancel()
            testJob = null
            testingKeyId = null
            testingStatusText = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = RekapinSpacing.md, vertical = RekapinSpacing.md)
    ) {
        if (isKeyDecryptFailed && apiKeys.isEmpty()) {
            RekapinCard(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = RekapinSpacing.sm)
                    .testTag("card_key_decrypt_failed_notice")
            ) {
                Column(
                    modifier = Modifier.padding(RekapinSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Kunci API Tidak Terbaca",
                            style = RekapinTypography.bodyBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        text = "API key yang tersimpan tidak dapat dibuka di perangkat ini. Masukkan ulang key Anda.",
                        style = RekapinTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(
                        onClick = { showAddKeyDialog = true },
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("btn_reenter_key_in_settings")
                    ) {
                        Text("Masukkan Ulang", style = RekapinTypography.captionBold)
                    }
                }
            }
        }

        SectionHeader(
            title = "Daftar Key (${apiKeys.size})",
            titleStyle = RekapinTypography.bodyBold,
            action = {
                FilledTonalButton(
                    onClick = { showAddKeyDialog = true },
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
                        .testTag("btn_add_api_key")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                    Text("Tambah Key", style = RekapinTypography.captionBold)
                }
            }
        )

        Spacer(modifier = Modifier.height(RekapinSpacing.sm))

        if (apiKeys.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = RekapinSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(RekapinSpacing.sm))
                Text(
                    text = "Belum ada API key tersimpan.",
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
                items(apiKeys, key = { it.id }) { item ->
                    val isActive = item.isActive
                    RekapinCard(
                        isSelected = isActive,
                        containerColor = if (isActive) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        onClick = { onSetActiveKey(item.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("key_item_${item.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                                .padding(
                                    horizontal = RekapinSpacing.sm,
                                    vertical = RekapinSpacing.sm
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
                                RadioButton(
                                    selected = isActive,
                                    onClick = { onSetActiveKey(item.id) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = RekapinSpacing.xs),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)
                                ) {
                                    Text(
                                        text = item.name,
                                        style = RekapinTypography.bodyBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isActive) {
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
                                                text = "Aktif",
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
                                val isTestingThisKey = (testingKeyId == item.id)
                                if (isTestingThisKey && !testingStatusText.isNullOrBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.xs),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(11.dp),
                                            strokeWidth = 1.5.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = testingStatusText.orEmpty(),
                                            style = RekapinTypography.captionMedium.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                } else {
                                    testResults[item.id]?.let { (isOk, msg) ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.xs),
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Close,
                                                contentDescription = null,
                                                tint = if (isOk) Color(0xFF81C784) else MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = msg,
                                                style = RekapinTypography.captionMedium.copy(fontSize = 11.sp),
                                                color = if (isOk) Color(0xFF81C784) else MaterialTheme.colorScheme.error,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (onTestKey != null || onTestKeyWithProgress != null) {
                                    val isTesting = (testingKeyId == item.id)
                                    IconButton(
                                        onClick = {
                                            if (testingKeyId == null && testJob?.isActive != true) {
                                                testingKeyId = item.id
                                                testingStatusText = "Memeriksa key…"
                                                testResults = testResults - item.id
                                                testJob = coroutineScope.launch {
                                                    try {
                                                        val res = if (onTestKeyWithProgress != null) {
                                                            onTestKeyWithProgress.invoke(item.key) { status ->
                                                                coroutineScope.launch {
                                                                    if (testingKeyId == item.id) {
                                                                        testingStatusText = status
                                                                    }
                                                                }
                                                            }
                                                        } else {
                                                            onTestKey!!.invoke(item.key)
                                                        }
                                                        testResults = testResults + (item.id to res)
                                                    } finally {
                                                        if (testingKeyId == item.id) {
                                                            testingKeyId = null
                                                            testingStatusText = null
                                                            testJob = null
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        enabled = (testingKeyId == null),
                                        modifier = Modifier
                                            .size(34.dp)
                                            .testTag("btn_test_key_${item.id}")
                                    ) {
                                        if (isTesting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(15.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Tes API Key",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { keyToRename = item },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .testTag("btn_rename_key_${item.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Ganti nama",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { keyToDelete = item },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .testTag("btn_delete_key_${item.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus key",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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

    if (showAddKeyDialog) {
        AddKeyDialog(
            nextDefaultName = "Key ${apiKeys.size + 1}",
            onDismiss = { showAddKeyDialog = false },
            onConfirm = { name, key ->
                onAddKey(name, key)
                showAddKeyDialog = false
            },
            onTestKey = onTestKey,
            onTestKeyWithProgress = onTestKeyWithProgress
        )
    }

    keyToRename?.let { item ->
        RenameKeyDialog(
            currentName = item.name,
            onDismiss = { keyToRename = null },
            onConfirm = { newName ->
                onRenameKey(item.id, newName)
                keyToRename = null
            }
        )
    }

    keyToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { keyToDelete = null },
            shape = RoundedCornerShape(RekapinRadii.lg),
            title = {
                Text(
                    text = "Hapus API Key?",
                    style = RekapinTypography.bodyBold.copy(fontSize = 15.sp)
                )
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus \"${item.name}\" (${item.maskedKey})?",
                    style = RekapinTypography.body.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteKey(item.id)
                        keyToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(RekapinRadii.sm),
                    contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("btn_confirm_delete_key")
                ) {
                    Text("Hapus", style = RekapinTypography.captionBold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { keyToDelete = null },
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

@Composable
private fun AddKeyDialog(
    nextDefaultName: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, key: String) -> Unit,
    onTestKey: (suspend (key: String) -> Pair<Boolean, String>)? = null,
    onTestKeyWithProgress: (suspend (key: String, onProgress: (String) -> Unit) -> Pair<Boolean, String>)? = null
) {
    var nameInput by remember { mutableStateOf(nextDefaultName) }
    var keyInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var isTesting by remember { mutableStateOf(false) }
    var testingStatusText by remember { mutableStateOf<String?>(null) }
    var testJob by remember { mutableStateOf<Job?>(null) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            testJob?.cancel()
            testJob = null
            isTesting = false
            testingStatusText = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(RekapinRadii.lg),
        title = {
            Text(
                text = "Tambah API Key",
                style = RekapinTypography.bodyBold.copy(fontSize = 15.sp)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
            ) {
                CompactTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = "Nama Key (contoh: Key B)",
                    singleLine = true,
                    fieldModifier = Modifier.testTag("input_key_name"),
                    modifier = Modifier.fillMaxWidth()
                )

                CompactTextField(
                    value = keyInput,
                    onValueChange = {
                        keyInput = it
                        testResult = null
                    },
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
                                contentDescription = if (isPasswordVisible) "Sembunyikan" else "Tampilkan",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    fieldModifier = Modifier.testTag("input_api_key_value"),
                    modifier = Modifier.fillMaxWidth()
                )

                if (onTestKey != null || onTestKeyWithProgress != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clean = keyInput.trim()
                                if (clean.isNotBlank() && !isTesting && testJob?.isActive != true) {
                                    isTesting = true
                                    testingStatusText = "Memeriksa key…"
                                    testResult = null
                                    testJob = coroutineScope.launch {
                                        try {
                                            val res = if (onTestKeyWithProgress != null) {
                                                onTestKeyWithProgress.invoke(clean) { status ->
                                                    coroutineScope.launch {
                                                        if (isTesting) {
                                                            testingStatusText = status
                                                        }
                                                    }
                                                }
                                            } else {
                                                onTestKey!!.invoke(clean)
                                            }
                                            testResult = res
                                        } finally {
                                            isTesting = false
                                            testingStatusText = null
                                            testJob = null
                                        }
                                    }
                                }
                            },
                            enabled = keyInput.trim().isNotBlank() && !isTesting,
                            shape = RoundedCornerShape(RekapinRadii.sm),
                            contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("btn_test_new_key_dialog")
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Menguji...", style = RekapinTypography.captionBold.copy(fontSize = 11.sp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                                Text("Tes Key Ini", style = RekapinTypography.captionBold.copy(fontSize = 11.sp))
                            }
                        }

                        if (isTesting && !testingStatusText.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                            Text(
                                text = testingStatusText.orEmpty(),
                                style = RekapinTypography.captionMedium.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            testResult?.let { (isOk, msg) ->
                                Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                                Text(
                                    text = msg,
                                    style = RekapinTypography.captionMedium.copy(fontSize = 11.sp),
                                    color = if (isOk) Color(0xFF81C784) else Color(0xFFEF9A9A),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanKey = keyInput.trim()
                    if (cleanKey.isNotBlank()) {
                        onConfirm(nameInput.trim(), cleanKey)
                    }
                },
                enabled = keyInput.trim().isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(RekapinRadii.sm),
                contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("btn_save_new_key")
            ) {
                Text("Simpan", style = RekapinTypography.captionBold)
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

@Composable
private fun RenameKeyDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit
) {
    var nameInput by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(RekapinRadii.lg),
        title = {
            Text(
                text = "Ganti Nama API Key",
                style = RekapinTypography.bodyBold.copy(fontSize = 15.sp)
            )
        },
        text = {
            CompactTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = "Nama Key",
                singleLine = true,
                fieldModifier = Modifier.testTag("input_rename_key"),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = nameInput.trim()
                    if (clean.isNotBlank()) {
                        onConfirm(clean)
                    }
                },
                enabled = nameInput.trim().isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(RekapinRadii.sm),
                contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("btn_confirm_rename_key")
            ) {
                Text("Simpan", style = RekapinTypography.captionBold)
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
