package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.db.AnimeAnalysisEntity
import com.example.data.model.AnimeAnalysisResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeExportDialog(
    selectedAnalyses: List<AnimeAnalysisEntity>,
    onDismiss: () -> Unit,
    onMergeNarrationsWithAi: suspend (parts: List<Pair<String, String>>, onStatus: (String) -> Unit) -> String
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // Urutan awal berdasarkan createdAt dari terlama
    var orderedEntities by remember {
        mutableStateOf(selectedAnalyses.sortedBy { it.createdAt })
    }

    // Opsi centang bagian yang ikut digabung (default aktif)
    var includeNarration by remember { mutableStateOf(true) }
    var includeCharacters by remember { mutableStateOf(true) }
    var includeTimeline by remember { mutableStateOf(true) }
    var includeVisualPromptsOnly by remember { mutableStateOf(false) }
    var includeSegmentSummaries by remember { mutableStateOf(true) }

    // Status AI Narasi
    var isMergingAi by remember { mutableStateOf(false) }
    var aiStatusMessage by remember { mutableStateOf("") }
    var aiErrorMessage by remember { mutableStateOf<String?>(null) }
    var aiMergedNarration by remember { mutableStateOf<String?>(null) }
    var useAiNarration by remember { mutableStateOf(false) }

    val defaultFileName = remember {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        "rekapin_gabungan_${dateFormat.format(Date())}.txt"
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val exportText = ExportFormatters.formatMergedExport(
                    items = orderedEntities,
                    includeNarration = includeNarration,
                    includeCharacters = includeCharacters,
                    includeTimeline = includeTimeline,
                    includeVisualPromptsOnly = includeVisualPromptsOnly,
                    includeSegmentSummaries = includeSegmentSummaries,
                    customMergedNarration = if (useAiNarration) aiMergedNarration else null
                )
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(exportText.toByteArray(Charsets.UTF_8))
                    stream.flush()
                }
                Toast.makeText(context, "Berkas gabungan berhasil disimpan!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal menyimpan berkas: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Teks pratinjau yang reaktif
    val previewText = remember(
        orderedEntities,
        includeNarration,
        includeCharacters,
        includeTimeline,
        includeVisualPromptsOnly,
        includeSegmentSummaries,
        aiMergedNarration,
        useAiNarration
    ) {
        ExportFormatters.formatMergedExport(
            items = orderedEntities,
            includeNarration = includeNarration,
            includeCharacters = includeCharacters,
            includeTimeline = includeTimeline,
            includeVisualPromptsOnly = includeVisualPromptsOnly,
            includeSegmentSummaries = includeSegmentSummaries,
            customMergedNarration = if (useAiNarration) aiMergedNarration else null
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(20.dp)
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Gabung & Ekspor",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${orderedEntities.size} Video Dipilih",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Tutup",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                bottomBar = {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(previewText))
                                    Toast.makeText(context, "Teks gabungan tersalin ke clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("btn_copy_merged"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Salin Semua", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    try {
                                        exportLauncher.launch(defaultFileName)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Gagal membuka penyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("btn_export_merged_txt"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ekspor .txt", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // SEKSI 1: URUTAN VIDEO
                    item {
                        Text(
                            text = "1. Urutan Penggabungan Video",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    itemsIndexed(orderedEntities) { index, entity ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entity.videoName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val durSec = (entity.durationMs / 1000).coerceAtLeast(0L)
                                    val mm = durSec / 60
                                    val ss = durSec % 60
                                    Text(
                                        text = String.format(Locale.US, "Durasi %02d:%02d • %d segmen", mm, ss, entity.totalSegments),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val list = orderedEntities.toMutableList()
                                                val item = list.removeAt(index)
                                                list.add(index - 1, item)
                                                orderedEntities = list
                                            }
                                        },
                                        enabled = (index > 0),
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = "Naik",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (index < orderedEntities.size - 1) {
                                                val list = orderedEntities.toMutableList()
                                                val item = list.removeAt(index)
                                                list.add(index + 1, item)
                                                orderedEntities = list
                                            }
                                        },
                                        enabled = (index < orderedEntities.size - 1),
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = "Turun",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // SEKSI 2: BAGIAN YANG IKUT DIGABUNG
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "2. Bagian yang Ikut Digabung",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                CheckboxOption(
                                    label = "Narasi Alur Cerita",
                                    checked = includeNarration,
                                    onCheckedChange = { includeNarration = it }
                                )
                                CheckboxOption(
                                    label = "Lembar Karakter (Character Sheet)",
                                    checked = includeCharacters,
                                    onCheckedChange = { includeCharacters = it }
                                )
                                CheckboxOption(
                                    label = "Timeline Lengkap (Scene + Visual Prompt)",
                                    checked = includeTimeline,
                                    onCheckedChange = {
                                        includeTimeline = it
                                        if (it) includeVisualPromptsOnly = false
                                    }
                                )
                                CheckboxOption(
                                    label = "Visual Prompt Saja (Tanpa deskripsi scene)",
                                    checked = includeVisualPromptsOnly,
                                    onCheckedChange = {
                                        includeVisualPromptsOnly = it
                                        if (it) includeTimeline = false
                                    }
                                )
                                CheckboxOption(
                                    label = "Ringkasan Tiap Segmen",
                                    checked = includeSegmentSummaries,
                                    onCheckedChange = { includeSegmentSummaries = it }
                                )
                            }
                        }
                    }

                    // SEKSI 3: SATUKAN NARASI DENGAN AI (OPSIONAL)
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "3. Satukan Narasi dengan AI (Opsional)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Menggabungkan narasi tiap video menjadi satu cerita mengalir dengan transisi halus tanpa menambah kejadian baru.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        scope.launch {
                                            isMergingAi = true
                                            aiErrorMessage = null
                                            aiStatusMessage = "Menghubungkan ke Gemini..."
                                            try {
                                                val parts = orderedEntities.map { entity ->
                                                    Pair(entity.videoName, entity.narration.ifBlank { "(Tidak ada narasi)" })
                                                }
                                                val resultText = onMergeNarrationsWithAi(parts) { status ->
                                                    aiStatusMessage = status
                                                }
                                                aiMergedNarration = resultText
                                                useAiNarration = true
                                                Toast.makeText(context, "Narasi berhasil disatukan oleh AI!", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                aiErrorMessage = e.message ?: "Gagal menyatukan narasi."
                                                useAiNarration = false
                                            } finally {
                                                isMergingAi = false
                                            }
                                        }
                                    },
                                    enabled = !isMergingAi,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_merge_narrations_ai")
                                ) {
                                    if (isMergingAi) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(aiStatusMessage.ifBlank { "Sedang menyatukan..." }, fontSize = 11.sp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Satukan narasi dengan AI", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                if (aiErrorMessage != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Gagal memproses dengan AI: $aiErrorMessage. Narasi tetap menggunakan format gabungan sederhana.",
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }

                                if (aiMergedNarration != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Pilih versi narasi untuk ekspor:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        RadioButton(
                                            selected = !useAiNarration,
                                            onClick = { useAiNarration = false }
                                        )
                                        Text(
                                            text = "Narasi per bagian",
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        RadioButton(
                                            selected = useAiNarration,
                                            onClick = { useAiNarration = true }
                                        )
                                        Text(
                                            text = "Narasi gabungan (AI)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // SEKSI 4: PRATINJAU TEKS GABUNGAN
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "4. Pratinjau Teks Gabungan",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 260.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = previewText,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckboxOption(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
