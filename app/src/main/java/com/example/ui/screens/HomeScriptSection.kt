package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.SourceType
import com.example.ui.components.ChoiceChips
import com.example.ui.components.CompactTextField
import com.example.ui.components.PrimaryActionBar
import com.example.ui.components.RekapinCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.RekapinSpacing
import com.example.ui.theme.RekapinTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pilihan sumber di layar Studio: 0 = Video, 1 = Naskah. */
@Composable
internal fun InputModeToggle(
    selectedMode: Int,
    onSelectMode: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    ChoiceChips(
        items = listOf(0, 1),
        selectedItem = selectedMode,
        onItemSelected = onSelectMode,
        labelProvider = { if (it == 0) "Video" else "Naskah" },
        testTagProvider = { if (it == 0) "mode_video" else "mode_script" },
        compact = true,
        modifier = modifier.padding(
            horizontal = RekapinSpacing.md,
            vertical = 6.dp
        )
    )
}

/**
 * Layar input naskah (cerita, novel, script). Tahap 1: tempel atau buka file teks,
 * lalu simpan ke Riwayat sebagai draf. Analisis naskah dibuat di tahap berikutnya.
 */
@Composable
internal fun ScriptSetupContent(
    onSave: (title: String, text: String, onResult: (Boolean, String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    // Sengaja tidak memakai rememberSaveable: teks panjang bisa melebihi batas Bundle.
    var text by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var isReading by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isReading = true
            val result = withContext(Dispatchers.IO) {
                runCatching { readTextFile(context, uri) }
            }
            isReading = false
            result.onSuccess { (name, content) ->
                if (content == null) {
                    Toast.makeText(
                        context,
                        "File terlalu besar (maksimal ${SourceType.MAX_SCRIPT_CHARS} karakter)",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    text = content
                    if (title.isBlank()) title = name
                }
            }.onFailure {
                Toast.makeText(context, "Gagal membuka file: ${it.message ?: "tidak diketahui"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val wordCount = remember(text) { countWords(text) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RekapinSpacing.md, vertical = RekapinSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
        ) {
            RekapinCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(RekapinSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
                ) {
                    SectionHeader(
                        title = "Naskah",
                        subtitle = "Cerita, novel, atau script. Tempel teks atau buka file teks (.txt, .md)."
                    )
                    CompactTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = "Judul (opsional)",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
                    ) {
                        OutlinedButton(
                            onClick = { filePicker.launch(arrayOf("text/*", "application/octet-stream")) },
                            enabled = !isReading && !isSaving,
                            modifier = Modifier.testTag("btn_open_script_file")
                        ) {
                            Text(if (isReading) "Membaca file..." else "Buka file")
                        }
                        if (text.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { text = "" },
                                enabled = !isReading && !isSaving
                            ) {
                                Text("Kosongkan")
                            }
                        }
                    }
                    CompactTextField(
                        value = text,
                        onValueChange = { new ->
                            text = if (new.length > SourceType.MAX_SCRIPT_CHARS) {
                                new.take(SourceType.MAX_SCRIPT_CHARS)
                            } else {
                                new
                            }
                        },
                        placeholder = "Tempel naskah di sini...",
                        singleLine = false,
                        minLines = 10,
                        maxLines = 18,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("script_text_field")
                    )
                    Text(
                        text = "$wordCount kata · ${text.length} karakter (maks ${SourceType.MAX_SCRIPT_CHARS})",
                        style = RekapinTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        PrimaryActionBar(
            buttonText = "Simpan ke Riwayat",
            enabled = text.isNotBlank() && !isSaving && !isReading,
            summaryText = if (text.isBlank()) "Tempel atau buka naskah dulu" else "Disimpan sebagai draf, analisis menyusul",
            icon = Icons.Default.Save,
            onClick = {
                isSaving = true
                onSave(title, text) { ok, message ->
                    isSaving = false
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    if (ok) {
                        title = ""
                        text = ""
                    }
                }
            },
            buttonModifier = Modifier.testTag("btn_save_script")
        )
    }
}

private fun countWords(text: String): Int {
    var count = 0
    var inWord = false
    for (ch in text) {
        if (ch.isWhitespace()) {
            inWord = false
        } else if (!inWord) {
            inWord = true
            count++
        }
    }
    return count
}

/** Mengembalikan (nama tanpa ekstensi, isi). Isi null jika melebihi batas karakter. */
private fun readTextFile(context: android.content.Context, uri: Uri): Pair<String, String?> {
    val name = context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        ?.substringBeforeLast('.')
        .orEmpty()
    val stream = context.contentResolver.openInputStream(uri)
        ?: throw java.io.IOException("File tidak dapat dibuka")
    val sb = StringBuilder()
    stream.bufferedReader(Charsets.UTF_8).use { reader ->
        val buf = CharArray(8192)
        while (true) {
            val n = reader.read(buf)
            if (n <= 0) break
            sb.append(buf, 0, n)
            if (sb.length > SourceType.MAX_SCRIPT_CHARS) return name to null
        }
    }
    return name to sb.toString()
}
