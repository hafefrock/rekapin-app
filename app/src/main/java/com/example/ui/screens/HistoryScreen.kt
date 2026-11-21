package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.AnimeAnalysisEntity
import com.example.data.model.AnimeAnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.ui.components.ChoiceChips
import com.example.ui.components.MergeExportDialog
import com.example.ui.components.PrimaryActionBar
import com.example.ui.components.RekapinCard
import com.example.ui.components.ResultView
import com.example.ui.theme.RekapinRadii
import com.example.ui.theme.RekapinSpacing
import com.example.ui.theme.RekapinTypography

private enum class HistoryFilter(val label: String) {
    ALL("Semua"),
    COMPLETED("Selesai"),
    INCOMPLETE("Belum selesai")
}

private val HistorySuccessContainer = Color(0xFF1D3326)
private val HistorySuccessContent = Color(0xFF7AD69A)

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
    var showMergeDialogStandalone by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HistoryTopBar(
                isSelectMode = isSelectMode,
                selectedCount = selectedIds.size,
                totalCount = analyses.size,
                onBackClick = {
                    if (isSelectMode) {
                        isSelectMode = false
                        selectedIds = emptySet()
                    } else {
                        onBack()
                    }
                }
            )
        },
        bottomBar = {
            if (isSelectMode) {
                HistorySelectBottomBar(
                    selectedCount = selectedIds.size,
                    onMergeExport = { showMergeDialogStandalone = true },
                    onCancel = {
                        isSelectMode = false
                        selectedIds = emptySet()
                    }
                )
            } else {
                bottomNavigationContent()
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        HistoryBody(
            analyses = analyses,
            isSelectMode = isSelectMode,
            selectedIds = selectedIds,
            onEnterSelectMode = { initialId ->
                isSelectMode = true
                selectedIds = selectedIds + initialId
            },
            onToggleSelectId = { id ->
                selectedIds = if (selectedIds.contains(id)) selectedIds - id else selectedIds + id
            },
            onExitSelectMode = {
                isSelectMode = false
                selectedIds = emptySet()
            },
            showMergeDialog = showMergeDialogStandalone,
            onDismissMergeDialog = { showMergeDialogStandalone = false },
            onBack = onBack,
            onSelectAnalysis = onSelectAnalysis,
            onDeleteAnalysis = onDeleteAnalysis,
            onResumeAnalysis = onResumeAnalysis,
            onMergeNarrationsWithAi = onMergeNarrationsWithAi,
            getMaxDurationForAnalysis = getMaxDurationForAnalysis,
            failedSeriesBibleBuildIds = failedSeriesBibleBuildIds,
            seriesBibleBuildInProgressId = seriesBibleBuildInProgressId,
            onBuildSeriesBibleNow = onBuildSeriesBibleNow,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@Composable
internal fun HistoryTopBar(
    isSelectMode: Boolean,
    selectedCount: Int,
    totalCount: Int,
    onBackClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = RekapinSpacing.xs, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("btn_back_history")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(RekapinSpacing.xs))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isSelectMode) "Pilih Riwayat" else "Riwayat",
                    style = RekapinTypography.sectionTitle.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isSelectMode) "$selectedCount dipilih" else "$totalCount analisis",
                    style = RekapinTypography.caption.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun HistoryBody(
    analyses: List<AnimeAnalysisEntity>,
    isSelectMode: Boolean,
    selectedIds: Set<Long>,
    onEnterSelectMode: (Long) -> Unit,
    onToggleSelectId: (Long) -> Unit,
    onExitSelectMode: () -> Unit,
    showMergeDialog: Boolean,
    onDismissMergeDialog: () -> Unit,
    onBack: () -> Unit,
    onSelectAnalysis: (AnimeAnalysisEntity) -> Unit,
    onDeleteAnalysis: (Long) -> Unit,
    onResumeAnalysis: (Long) -> Unit,
    onMergeNarrationsWithAi: (suspend (parts: List<Pair<String, String>>, onStatus: (String) -> Unit) -> String)? = null,
    getMaxDurationForAnalysis: ((Long) -> Int)? = null,
    failedSeriesBibleBuildIds: Set<Long> = emptySet(),
    seriesBibleBuildInProgressId: Long? = null,
    onBuildSeriesBibleNow: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scriptContext = androidx.compose.ui.platform.LocalContext.current
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    var itemToDelete by remember { mutableStateOf<AnimeAnalysisEntity?>(null) }
    val listState = rememberLazyListState()

    val filteredAnalyses = remember(analyses, selectedFilter) {
        when (selectedFilter) {
            HistoryFilter.ALL -> analyses
            HistoryFilter.COMPLETED -> analyses.filter { it.isCompleted }
            HistoryFilter.INCOMPLETE -> analyses.filter { !it.isCompleted }
        }
    }

    BackHandler {
        if (isSelectMode) {
            onExitSelectMode()
        } else {
            onBack()
        }
    }

    Column(modifier = modifier) {
        if (analyses.isEmpty()) {
            HistoryEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(RekapinSpacing.lg)
            )
        } else {
            ChoiceChips(
                items = HistoryFilter.entries,
                selectedItem = selectedFilter,
                onItemSelected = { selectedFilter = it },
                labelProvider = { it.label },
                compact = true,
                modifier = Modifier.padding(
                    horizontal = RekapinSpacing.md,
                    vertical = 6.dp
                )
            )

            if (filteredAnalyses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(RekapinSpacing.lg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tidak ada analisis di filter ini",
                        style = RekapinTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = RekapinSpacing.md,
                        end = RekapinSpacing.md,
                        top = RekapinSpacing.xs,
                        bottom = RekapinSpacing.xl
                    ),
                    verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
                ) {
                    items(
                        items = filteredAnalyses,
                        key = { it.id },
                        contentType = { "history_item" }
                    ) { entity ->
                        val isSelected = selectedIds.contains(entity.id)
                        val cachedMaxDuration = remember(entity.id, entity.updatedAt) {
                            getMaxDurationForAnalysis?.invoke(entity.id) ?: 5
                        }
                        HistoryItemCard(
                            entity = entity,
                            isSelectMode = isSelectMode,
                            isSelected = isSelected,
                            onLongClick = {
                                if (entity.isCompleted) {
                                    onEnterSelectMode(entity.id)
                                }
                            },
                            onClick = {
                                if (isSelectMode) {
                                    if (entity.isCompleted) {
                                        onToggleSelectId(entity.id)
                                    }
                                } else if (entity.isScript) {
                                    android.widget.Toast.makeText(
                                        scriptContext,
                                        "Analisis naskah belum tersedia, segera hadir",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    onSelectAnalysis(entity)
                                }
                            },
                            onDelete = { itemToDelete = entity },
                            onResume = { onResumeAnalysis(entity.id) },
                            maxSceneDuration = cachedMaxDuration,
                            seriesBibleBuildFailed = failedSeriesBibleBuildIds.contains(entity.id),
                            isBuildingSeriesBible = seriesBibleBuildInProgressId == entity.id,
                            onBuildSeriesBibleNow = { onBuildSeriesBibleNow?.invoke(entity.id) }
                        )
                    }
                }
            }
        }
    }

    // Dialog Gabung & Ekspor
    if (showMergeDialog) {
        val selectedEntities = remember(analyses, selectedIds) {
            analyses.filter { selectedIds.contains(it.id) }
        }
        MergeExportDialog(
            selectedAnalyses = selectedEntities,
            onDismiss = onDismissMergeDialog,
            onMergeNarrationsWithAi = { parts, onStatus ->
                onMergeNarrationsWithAi?.invoke(parts, onStatus)
                    ?: throw IllegalStateException("Penyatuan narasi AI tidak tersedia")
            }
        )
    }

    // Dialog Konfirmasi Hapus
    itemToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            shape = RoundedCornerShape(RekapinRadii.lg),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Hapus Riwayat Analisis",
                    style = RekapinTypography.sectionTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus hasil analisis video \"${target.videoName}\"? Data yang dihapus tidak dapat dipulihkan.",
                    style = RekapinTypography.body,
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
                    shape = RoundedCornerShape(RekapinRadii.sm)
                ) {
                    Text("Hapus", style = RekapinTypography.bodyBold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { itemToDelete = null },
                    shape = RoundedCornerShape(RekapinRadii.sm)
                ) {
                    Text("Batal", style = RekapinTypography.bodyMedium)
                }
            }
        )
    }
}

@Composable
internal fun HistorySelectBottomBar(
    selectedCount: Int,
    onMergeExport: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 6.dp)
        ) {
            PrimaryActionBar(
                buttonText = "Gabung & Ekspor",
                onClick = onMergeExport,
                enabled = selectedCount >= 2,
                summaryText = "$selectedCount dipilih",
                icon = Icons.Default.Layers,
                buttonModifier = Modifier.testTag("btn_merge_export")
            )
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(RekapinRadii.md),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = RekapinSpacing.md)
                    .height(36.dp)
            ) {
                Text(
                    text = "Batal",
                    style = RekapinTypography.captionBold.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun HistoryEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(RekapinSpacing.md))
        Text(
            text = "Belum Ada Riwayat Analisis",
            style = RekapinTypography.sectionTitle.copy(fontSize = 15.sp),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(RekapinSpacing.xs))
        Text(
            text = "Hasil analisis tersimpan otomatis di sini.",
            style = RekapinTypography.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
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
    val cardShape = RoundedCornerShape(RekapinRadii.md)

    val intervalText = if (entity.isScript) {
        "Naskah · ${entity.videoSizeBytes / 1024} KB"
    } else if (entity.intervalSeconds == 0) {
        val maxDurText = if (maxSceneDuration == 0) "Tanpa batas" else "${maxSceneDuration}s"
        "Per Adegan ($maxDurText)"
    } else {
        "Interval ${entity.intervalSeconds}s"
    }

    RekapinCard(
        isSelected = isSelected,
        shape = cardShape,
        containerColor = if (isSelected) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clip(cardShape)
            .combinedClickable(
                enabled = isEnabled,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag(if (isSelectMode) "history_select_${entity.id}" else "history_card_${entity.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(RekapinSpacing.md),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Baris atas: ikon centang (jika terpilih), nama video 14sp bold + tanggal 11sp, ikon hapus 36dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Dipilih",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = entity.videoName,
                        style = RekapinTypography.bodyBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = entity.formattedDate,
                        style = RekapinTypography.caption.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
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

            // Baris status (chip 24 dp teks 11sp + keterangan interval)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (entity.isCompleted) {
                    HistoryStatusChip(
                        text = "Selesai · ${entity.totalSegments} segmen",
                        containerColor = HistorySuccessContainer,
                        contentColor = HistorySuccessContent
                    )
                } else if (entity.isScript) {
                    HistoryStatusChip(
                        text = "Draf naskah · belum dianalisis",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                } else {
                    HistoryStatusChip(
                        text = "Belum selesai · ${entity.completedSegmentsCount}/${entity.totalSegments} segmen",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }

                HistoryStatusChip(
                    text = intervalText,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Progres 3 dp & tombol aksi Lanjut 36 dp untuk yang belum selesai
            if (!entity.isCompleted && !entity.isScript) {
                val progress = if (entity.totalSegments > 0) {
                    (entity.completedSegmentsCount.toFloat() / entity.totalSegments.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

                LinearProgressIndicator(
                    progress = { progress },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(RekapinRadii.sm))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = onResume,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        contentPadding = PaddingValues(
                            horizontal = RekapinSpacing.md,
                            vertical = 0.dp
                        ),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("btn_resume_${entity.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                        Text(
                            text = "Lanjut (${entity.completedSegmentsCount + 1})",
                            style = RekapinTypography.captionBold
                        )
                    }
                }
            }

            // Baris info kecil jika Memori Seri belum berhasil diperbarui
            if (entity.isCompleted && seriesBibleBuildFailed && !isSelectMode) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Memori seri belum berhasil diperbarui.",
                        style = RekapinTypography.caption,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                    TextButton(
                        onClick = onBuildSeriesBibleNow,
                        enabled = !isBuildingSeriesBible,
                        contentPadding = PaddingValues(
                            horizontal = RekapinSpacing.xs,
                            vertical = 0.dp
                        ),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("btn_build_series_bible_${entity.id}")
                    ) {
                        Text(
                            text = if (isBuildingSeriesBible) "Sedang membangun..." else "Bangun memori seri sekarang",
                            style = RekapinTypography.captionBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryStatusChip(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(RekapinRadii.sm),
        color = containerColor,
        modifier = Modifier.height(24.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = RekapinSpacing.sm)
        ) {
            Text(
                text = text,
                style = RekapinTypography.captionBold.copy(fontSize = 11.sp),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

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
    opEdSkipNote: String? = null,
    onBack: () -> Unit,
    onResume: () -> Unit,
    onFinishWithoutCurrentSegment: (() -> Unit)? = null,
    stageTimings: Map<String, Long>? = null,
    onResultEdited: ((com.example.data.model.AnimeAnalysisResult) -> Unit)? = null
) {
    BackHandler { onBack() }
    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = RekapinSpacing.xs, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entity.videoName,
                            style = RekapinTypography.bodyBold.copy(fontSize = 15.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (entity.isCompleted) {
                                "Analisis Selesai (${entity.totalSegments} Segmen)"
                            } else {
                                "Sebagian: Segmen ${entity.completedSegmentsCount} dari ${entity.totalSegments}"
                            },
                            style = RekapinTypography.caption.copy(fontSize = 11.sp),
                            color = if (entity.isCompleted) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!entity.isCompleted) {
                        Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                        if (entity.completedSegmentsCount > 0 && onFinishWithoutCurrentSegment != null) {
                            OutlinedButton(
                                onClick = onFinishWithoutCurrentSegment,
                                shape = RoundedCornerShape(RekapinRadii.sm),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 0.dp),
                                modifier = Modifier
                                    .padding(end = RekapinSpacing.xs)
                                    .height(36.dp)
                            ) {
                                Text(
                                    text = "Selesaikan",
                                    style = RekapinTypography.captionBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Button(
                            onClick = onResume,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(RekapinRadii.sm),
                            contentPadding = PaddingValues(horizontal = RekapinSpacing.md, vertical = 0.dp),
                            modifier = Modifier
                                .padding(end = RekapinSpacing.xs)
                                .height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                            Text(
                                text = "Lanjutkan",
                                style = RekapinTypography.captionBold
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = RekapinSpacing.md, vertical = RekapinSpacing.sm)
        ) {
            val parsedResult by produceState<AnimeAnalysisResult?>(
                initialValue = null,
                entity.id,
                entity.updatedAt,
                maxSceneDuration,
                seriesBible,
                continuityWarnings,
                repeatedSceneNotes,
                opEdSkipNote
            ) {
                value = withContext(Dispatchers.Default) {
                    val baseResult = entity.toAnimeAnalysisResult(
                        maxSceneDuration = maxSceneDuration,
                        seriesBible = seriesBible
                    )
                    val continuityNote = continuityWarnings.takeIf { it.isNotEmpty() }?.joinToString(
                        separator = "\n",
                        prefix = "Pemeriksa kontinuitas karakter menemukan perbedaan yang perlu ditinjau:\n- ",
                        transform = { it }
                    )?.replace("\n", "\n- ") ?: ""
                    val repeatedSceneNote = repeatedSceneNotes.takeIf { it.isNotEmpty() }?.joinToString(
                        separator = "\n",
                        prefix = "Kandidat adegan berulang berdasarkan kemiripan visual (tinjau manual):\n- "
                    )?.replace("\n", "\n- ") ?: ""
                    val opEdNote = opEdSkipNote?.takeIf { it.isNotBlank() } ?: ""
                    val combinedNote = listOf(baseResult.note, continuityNote, repeatedSceneNote, opEdNote)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString("\n\n")
                    baseResult.copy(note = combinedNote)
                }
            }

            val currentResult = parsedResult
            if (currentResult == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                ResultView(
                    result = currentResult,
                    styleName = styleName,
                    narrationModeName = narrationModeName,
                    languageName = languageName,
                    seriesBible = seriesBible,
                    stageTimings = stageTimings,
                    onResultEdited = onResultEdited,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
