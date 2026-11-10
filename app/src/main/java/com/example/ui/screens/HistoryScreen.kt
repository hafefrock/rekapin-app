package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.AnimeAnalysisEntity
import com.example.ui.components.ResultView

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Surface
import com.example.ui.components.MergeExportDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    analyses: List<AnimeAnalysisEntity>,
    onBack: () -> Unit,
    onSelectAnalysis: (AnimeAnalysisEntity) -> Unit,
    onDeleteAnalysis: (Long) -> Unit,
    onResumeAnalysis: (Long) -> Unit,
    onFinishWithoutCurrentSegment: ((Long) -> Unit)? = null,
    onMergeNarrationsWithAi: (suspend (parts: List<Pair<String, String>>, onStatus: (String) -> Unit) -> String)? = null,
    getMaxDurationForAnalysis: ((Long) -> Int)? = null,
    failedSeriesBibleBuildIds: Set<Long> = emptySet(),
    seriesBibleBuildInProgressId: Long? = null,
    onBuildSeriesBibleNow: ((Long) -> Unit)? = null,
    bottomNavigationContent: @Composable () -> Unit = {}
) {
    // Mode pilih banyak
    var isSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showMergeDialog by remember { mutableStateOf(false) }

    BackHandler {
        if (isSelectMode) {
            isSelectMode = false
            selectedIds = emptySet()
        } else {
            onBack()
        }
    }
    var itemToDelete by remember { mutableStateOf<AnimeAnalysisEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = if (isSelectMode) "${selectedIds.size} Dipilih" else "Riwayat Analisis",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isSelectMode) "PILIH HASIL UNTUK DIGABUNGKAN" else "ARSIP REKAP ANDA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            letterSpacing = 1.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSelectMode) {
                                isSelectMode = false
                                selectedIds = emptySet()
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("btn_back_history")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (isSelectMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedIds.size} dipilih",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    isSelectMode = false
                                    selectedIds = emptySet()
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Batal", fontSize = 12.sp)
                            }
                            Button(
                                onClick = { showMergeDialog = true },
                                enabled = selectedIds.size >= 2,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("btn_merge_export"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Gabung & Ekspor", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                bottomNavigationContent()
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (analyses.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Belum Ada Riwayat Analisis",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Hasil analisis anime akan otomatis tersimpan di sini setiap kali selesai maupun bertahap per segmen.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(analyses, key = { it.id }) { entity ->
                        val isSelected = selectedIds.contains(entity.id)
                        HistoryItemCard(
                            entity = entity,
                            isSelectMode = isSelectMode,
                            isSelected = isSelected,
                            onLongClick = {
                                if (entity.isCompleted) {
                                    isSelectMode = true
                                    selectedIds = selectedIds + entity.id
                                }
                            },
                            onClick = {
                                if (isSelectMode) {
                                    if (entity.isCompleted) {
                                        selectedIds = if (isSelected) {
                                            selectedIds - entity.id
                                        } else {
                                            selectedIds + entity.id
                                        }
                                    }
                                } else {
                                    onSelectAnalysis(entity)
                                }
                            },
                            onDelete = { itemToDelete = entity },
                            onResume = { onResumeAnalysis(entity.id) },
                            maxSceneDuration = getMaxDurationForAnalysis?.invoke(entity.id) ?: 5,
                            seriesBibleBuildFailed = failedSeriesBibleBuildIds.contains(entity.id),
                            isBuildingSeriesBible = seriesBibleBuildInProgressId == entity.id,
                            onBuildSeriesBibleNow = { onBuildSeriesBibleNow?.invoke(entity.id) }
                        )
                    }
                }
            }
        }

        // Dialog Gabung & Ekspor
        if (showMergeDialog) {
            val selectedEntities = analyses.filter { selectedIds.contains(it.id) }
            MergeExportDialog(
                selectedAnalyses = selectedEntities,
                onDismiss = { showMergeDialog = false },
                onMergeNarrationsWithAi = { parts, onStatus ->
                    onMergeNarrationsWithAi?.invoke(parts, onStatus)
                        ?: throw IllegalStateException("Penyatuan narasi AI tidak tersedia")
                }
            )
        }

        // Delete Confirmation Dialog
        itemToDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        text = "Hapus Riwayat Analisis",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Text(
                        text = "Apakah Anda yakin ingin menghapus hasil analisis video \"${target.videoName}\"? Data yang dihapus tidak dapat dipulihkan.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteAnalysis(target.id)
                            itemToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Hapus", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { itemToDelete = null },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Batal")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryItemCard(
    entity: AnimeAnalysisEntity,
    isSelectMode: Boolean,
    isSelected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onResume: () -> Unit,
    maxSceneDuration: Int = 5,
    seriesBibleBuildFailed: Boolean = false,
    isBuildingSeriesBible: Boolean = false,
    onBuildSeriesBibleNow: () -> Unit = {}
) {
    val isEnabled = !isSelectMode || entity.isCompleted
    val alpha = if (isSelectMode && !entity.isCompleted) 0.38f else 1f

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                enabled = isEnabled,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else if (entity.isCompleted) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag(if (isSelectMode) "history_select_${entity.id}" else "history_card_${entity.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else if (entity.isCompleted) MaterialTheme.colorScheme.surfaceContainerHighest
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.Check
                        else if (entity.isCompleted) Icons.Default.CheckCircle
                        else Icons.Default.Movie,
                        contentDescription = if (isSelected) "Dipilih" else null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entity.videoName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = entity.formattedDate,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("delete_history_${entity.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Hapus",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata is stacked to remain readable on narrow screens and large font scales.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (entity.isCompleted) {
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Selesai (${entity.totalSegments} Segmen)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Belum Selesai (Segmen ${entity.completedSegmentsCount}/${entity.totalSegments})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    val intervalText = if (entity.intervalSeconds == 0) {
                        val maxDurText = if (maxSceneDuration == 0) "Tanpa batas" else "${maxSceneDuration}s"
                        "Per Adegan ($maxDurText)"
                    } else "Interval ${entity.intervalSeconds}s"
                    Text(
                        text = intervalText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!entity.isCompleted) {
                    Button(
                        onClick = onResume,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .align(Alignment.End)
                            .heightIn(min = 48.dp)
                            .testTag("btn_resume_${entity.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Lanjutkan dari segmen ${entity.completedSegmentsCount + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            if (entity.isCompleted && seriesBibleBuildFailed && !isSelectMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Memori seri belum berhasil diperbarui.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onBuildSeriesBibleNow,
                    enabled = !isBuildingSeriesBible,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_build_series_bible_${entity.id}")
                ) {
                    Text(
                        text = if (isBuildingSeriesBible) "Sedang membangun..." else "Bangun memori seri sekarang",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    entity: AnimeAnalysisEntity,
    styleName: String = "Anime 2D standar",
    narrationModeName: String = "",
    languageName: String = "",
    maxSceneDuration: Int = 5,
    seriesBible: com.example.data.model.SeriesBible? = null,
    continuityWarnings: List<String> = emptyList(),
    repeatedSceneNotes: List<String> = emptyList(),
    onBack: () -> Unit,
    onResume: () -> Unit,
    onFinishWithoutCurrentSegment: (() -> Unit)? = null
) {
    BackHandler { onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = entity.videoName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (entity.isCompleted) "Analisis Selesai (${entity.totalSegments} Segmen)"
                            else "Sebagian: Segmen ${entity.completedSegmentsCount} dari ${entity.totalSegments}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                actions = {
                    if (!entity.isCompleted) {
                        if (entity.completedSegmentsCount > 0 && onFinishWithoutCurrentSegment != null) {
                            OutlinedButton(
                                onClick = onFinishWithoutCurrentSegment,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text(
                                    text = "Selesaikan",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Button(
                            onClick = onResume,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Lanjutkan",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val baseResult = entity.toAnimeAnalysisResult(maxSceneDuration = maxSceneDuration, seriesBible = seriesBible)
            val continuityNote = continuityWarnings.takeIf { it.isNotEmpty() }?.joinToString(
                separator = "\n",
                prefix = "Pemeriksa kontinuitas karakter menemukan perbedaan yang perlu ditinjau:\n- ",
                transform = { it }
            )?.replace("\n", "\n- ") ?: ""
            val repeatedSceneNote = repeatedSceneNotes.takeIf { it.isNotEmpty() }?.joinToString(
                separator = "\n",
                prefix = "Kandidat adegan berulang berdasarkan kemiripan visual (tinjau manual):\n- "
            )?.replace("\n", "\n- ") ?: ""
            val combinedNote = listOf(baseResult.note, continuityNote, repeatedSceneNote)
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
            val result = baseResult.copy(note = combinedNote)
            ResultView(
                result = result,
                styleName = styleName,
                narrationModeName = narrationModeName,
                languageName = languageName,
                seriesBible = seriesBible,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
