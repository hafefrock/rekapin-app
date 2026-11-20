package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.db.AnimeAnalysisEntity
import com.example.data.model.AnalysisState
import com.example.ui.components.KeySelectionDialog
import com.example.ui.components.ManualQuotaDialog
import com.example.ui.components.ResultView
import com.example.ui.components.SettingsDialog
import com.example.ui.theme.RekapinRadii
import com.example.ui.theme.RekapinSpacing
import com.example.ui.theme.RekapinTypography
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel
) {
    val analysisState by viewModel.analysisState.collectAsStateWithLifecycle()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsStateWithLifecycle()
    val activeKeyName by viewModel.activeKeyName.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val pendingMatchDialogState by viewModel.pendingMatchDialogState.collectAsStateWithLifecycle()
    val failedSeriesBibleBuildIds by viewModel.failedSeriesBibleBuildIds.collectAsStateWithLifecycle()
    val seriesBibleBuildFailureNotice by viewModel.seriesBibleBuildFailureNotice.collectAsStateWithLifecycle()
    val seriesBibleBuildInProgressId by viewModel.seriesBibleBuildInProgressId.collectAsStateWithLifecycle()
    val showKeyDecryptFailedDialog by viewModel.showKeyDecryptFailedDialog.collectAsStateWithLifecycle()

    var showKeySelectionDialog by remember { mutableStateOf(false) }
    var pendingResumeAnalysisId by remember { mutableStateOf<Long?>(null) }
    var manualQuotaModelToEdit by remember { mutableStateOf<String?>(null) }
    var selectedDestination by remember { mutableIntStateOf(0) }

    val historyList by viewModel.historyList.collectAsStateWithLifecycle()
    val isHistoryOpen by viewModel.isHistoryOpen.collectAsStateWithLifecycle()
    val viewingHistoryItem by viewModel.viewingHistoryItem.collectAsStateWithLifecycle()
    val tabStateHolder = rememberSaveableStateHolder()

    var isHistorySelectMode by remember { mutableStateOf(false) }
    var selectedHistoryIds by remember { mutableStateOf(setOf<Long>()) }
    var showHistoryMergeDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    LaunchedEffect(isHistoryOpen, isSettingsOpen) {
        selectedDestination = when {
            isSettingsOpen -> 2
            isHistoryOpen -> 1
            else -> 0
        }
        if (!isHistoryOpen) {
            isHistorySelectMode = false
            selectedHistoryIds = emptySet()
            showHistoryMergeDialog = false
        }
    }

    val onSelectPrimaryDestination: (Int) -> Unit = { destination ->
        selectedDestination = destination
        when (destination) {
            0 -> {
                viewModel.closeHistory()
                if (isSettingsOpen) viewModel.closeSettings()
            }
            1 -> {
                if (isSettingsOpen) viewModel.closeSettings()
                viewModel.openHistory()
            }
            2 -> {
                viewModel.openSettings()
            }
        }
    }

    // Activity Result Launcher untuk memilih video menggunakan OpenDocument (Storage Access Framework)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w("HomeScreen", "Gagal takePersistableUriPermission: ${e.message}")
            }
            viewModel.onVideoPicked(it)
        }
    }

    // Activity Result Launcher untuk memilih video yang sama saat pemulihan video (akses hilang pada Lanjutkan)
    val recoveryVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w("HomeScreen", "Gagal takePersistableUriPermission: ${e.message}")
            }
            viewModel.onRecoveryVideoPicked(it)
        }
    }

    var pendingAnalysisAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Jika izin diberikan atau ditolak, analisis tetap berjalan sesuai permintaan
        pendingAnalysisAction?.invoke()
        pendingAnalysisAction = null
    }

    val requestNotificationAndStart: (() -> Unit) -> Unit = { action ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                pendingAnalysisAction = action
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                action()
            }
        } else {
            action()
        }
    }

    // Tampilkan Detail Riwayat jika sedang dipilih
    viewingHistoryItem?.let { historyEntity ->
        HistoryDetailHost(
            historyEntity = historyEntity,
            viewModel = viewModel,
            onStartAnalysis = {
                requestNotificationAndStart {
                    viewModel.startAnalysis(historyEntity.id)
                }
                viewModel.closeHistory()
                selectedDestination = 0
            },
            onFinishWithoutCurrentSegment = {
                viewModel.finishWithoutCurrentSegment(historyEntity.id)
                viewModel.closeHistoryDetail()
                viewModel.closeHistory()
                selectedDestination = 0
            }
        )
        return
    }

    // Tangani tombol Back sistem Android saat di tab Studio (tab Riwayat punya BackHandler sendiri)
    if (!isHistoryOpen) {
        if (analysisState is AnalysisState.Success || analysisState is AnalysisState.Error) {
            BackHandler {
                viewModel.resetAnalysis()
            }
        } else if (analysisState !is AnalysisState.Idle) {
            BackHandler {
                viewModel.cancelAnalysis()
            }
        }
    }

    Scaffold(
        topBar = {
            if (isHistoryOpen) {
                HistoryTopBar(
                    isSelectMode = isHistorySelectMode,
                    selectedCount = selectedHistoryIds.size,
                    totalCount = historyList.size,
                    onBackClick = {
                        if (isHistorySelectMode) {
                            isHistorySelectMode = false
                            selectedHistoryIds = emptySet()
                        } else {
                            viewModel.closeHistory()
                            selectedDestination = 0
                        }
                    }
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = RekapinSpacing.md, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(RekapinRadii.sm))
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Animation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(id = R.string.app_name),
                                style = RekapinTypography.sectionTitle.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 20.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Studio rekap anime",
                                style = RekapinTypography.caption.copy(
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (isHistoryOpen && isHistorySelectMode) {
                HistorySelectBottomBar(
                    selectedCount = selectedHistoryIds.size,
                    onMergeExport = { showHistoryMergeDialog = true },
                    onCancel = {
                        isHistorySelectMode = false
                        selectedHistoryIds = emptySet()
                    }
                )
            } else if (isHistoryOpen ||
                analysisState is AnalysisState.Idle ||
                analysisState is AnalysisState.Success ||
                analysisState is AnalysisState.Error
            ) {
                RekapinBottomNavigation(
                    selectedDestination = selectedDestination,
                    historyCount = historyList.size,
                    onSelect = onSelectPrimaryDestination
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            tabStateHolder.SaveableStateProvider(
                key = if (isHistoryOpen) "history_tab" else "studio_tab"
            ) {
                if (isHistoryOpen) {
                    HistoryBody(
                        analyses = historyList,
                        isSelectMode = isHistorySelectMode,
                        selectedIds = selectedHistoryIds,
                        onEnterSelectMode = { initialId ->
                            isHistorySelectMode = true
                            selectedHistoryIds = selectedHistoryIds + initialId
                        },
                        onToggleSelectId = { id ->
                            selectedHistoryIds = if (selectedHistoryIds.contains(id)) {
                                selectedHistoryIds - id
                            } else {
                                selectedHistoryIds + id
                            }
                        },
                        onExitSelectMode = {
                            isHistorySelectMode = false
                            selectedHistoryIds = emptySet()
                        },
                        showMergeDialog = showHistoryMergeDialog,
                        onDismissMergeDialog = { showHistoryMergeDialog = false },
                        onBack = {
                            viewModel.closeHistory()
                            selectedDestination = 0
                        },
                        onSelectAnalysis = { viewModel.openHistoryDetail(it) },
                        onDeleteAnalysis = { viewModel.deleteHistoryItem(it) },
                        onResumeAnalysis = { resumeId ->
                            requestNotificationAndStart {
                                viewModel.startAnalysis(resumeId)
                            }
                            viewModel.closeHistory()
                            selectedDestination = 0
                        },
                        onMergeNarrationsWithAi = { parts, onStatus ->
                            viewModel.mergeNarrationsWithAi(parts, onStatus)
                        },
                        getMaxDurationForAnalysis = { viewModel.getMaxDurationForAnalysis(it) },
                        failedSeriesBibleBuildIds = failedSeriesBibleBuildIds,
                        seriesBibleBuildInProgressId = seriesBibleBuildInProgressId,
                        onBuildSeriesBibleNow = { viewModel.buildSeriesBibleNow(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    when (val state = analysisState) {
                is AnalysisState.Success -> {
                    HomeSuccessHost(
                        state = state,
                        viewModel = viewModel
                    )
                }

                is AnalysisState.Error -> {
                    ErrorContent(
                        errorState = state,
                        activeKeyName = activeKeyName,
                        onRetry = {
                            requestNotificationAndStart {
                                if (state.canResume && state.analysisId != null) {
                                    viewModel.startAnalysis(state.analysisId)
                                } else {
                                    viewModel.startAnalysis()
                                }
                            }
                        },
                        onResume = { resumeId ->
                            requestNotificationAndStart {
                                viewModel.startAnalysis(resumeId)
                            }
                        },
                        onFinishWithoutCurrentSegment = { analysisId -> viewModel.finishWithoutCurrentSegment(analysisId) },
                        onPickSameVideo = { recoveryVideoPickerLauncher.launch(arrayOf("video/*")) },
                        onOpenSettings = { viewModel.openSettings() },
                        onSwitchKey = { analysisId ->
                            pendingResumeAnalysisId = analysisId
                            showKeySelectionDialog = true
                        },
                        onCheckAgain = { analysisId ->
                            requestNotificationAndStart {
                                viewModel.startAnalysis(resumeId = analysisId, forceReupload = false)
                            }
                        },
                        onReupload = { analysisId ->
                            requestNotificationAndStart {
                                viewModel.startAnalysis(resumeId = analysisId, forceReupload = true)
                            }
                        },
                        onCancel = { viewModel.resetAnalysis() }
                    )
                }

                is AnalysisState.Preparing,
                is AnalysisState.Uploading,
                is AnalysisState.PollingFileStatus,
                is AnalysisState.AnalyzingSegment,
                is AnalysisState.GeneratingNarration -> {
                    ProgressContent(
                        state = state,
                        activeKeyName = activeKeyName,
                        activeModelName = selectedModel,
                        onCancel = { viewModel.cancelAnalysis() },
                        onSkipDetection = { viewModel.skipSceneDetection() }
                    )
                }

                AnalysisState.Idle -> {
                    HomeIdleHost(
                        viewModel = viewModel,
                        historyList = historyList,
                        selectedModel = selectedModel,
                        onSelectVideoClicked = {
                            videoPickerLauncher.launch(arrayOf("video/*"))
                        },
                        onOpenManualQuota = { manualQuotaModelToEdit = it },
                        requestNotificationAndStart = requestNotificationAndStart
                    )
                }
            }
                }
            }
        }

        // Dialog Konfirmasi jika video cocok dengan analisis yang belum selesai
        pendingMatchDialogState?.let { matchState ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissPendingMatchDialog() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Analisis Belum Selesai Ditemukan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Text(
                        text = "Video \"${matchState.newVideo.displayName}\" cocok dengan analisis sebelumnya yang belum selesai (tersimpan segmen ${matchState.entity.completedSegmentsCount} dari ${matchState.entity.totalSegments}).\n\nApakah Anda ingin melanjutkan analisis lama atau memulai analisis baru?",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            requestNotificationAndStart {
                                viewModel.resumeMatchedAnalysis()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("btn_resume_matched_dialog")
                    ) {
                        Text("Lanjutkan Analisis Lama", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            requestNotificationAndStart {
                                viewModel.startFreshAnalysisWithVideo()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("btn_fresh_matched_dialog")
                    ) {
                        Text("Mulai Baru")
                    }
                }
            )
        }

        seriesBibleBuildFailureNotice?.let { analysisId ->
            val failedVideoName = historyList.firstOrNull { it.id == analysisId }?.videoName.orEmpty()
            AlertDialog(
                onDismissRequest = { viewModel.dismissSeriesBibleBuildFailureNotice() },
                title = { Text("Memori Seri belum diperbarui", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Pembaruan Memori Seri${if (failedVideoName.isNotBlank()) " untuk \"$failedVideoName\"" else ""} gagal. Anda dapat mencoba membangunnya sekarang; ini memakai satu permintaan Gemini tambahan.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.dismissSeriesBibleBuildFailureNotice()
                            viewModel.buildSeriesBibleNow(analysisId)
                        },
                        enabled = seriesBibleBuildInProgressId == null
                    ) { Text("Bangun memori seri sekarang") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { viewModel.dismissSeriesBibleBuildFailureNotice() }) {
                        Text("Nanti")
                    }
                }
            )
        }

        if (isSettingsOpen) {
            HomeSettingsHost(
                viewModel = viewModel,
                activeKeyName = activeKeyName,
                selectedModel = selectedModel,
                isKeyDecryptFailed = showKeyDecryptFailedDialog,
                onDismiss = {
                    viewModel.closeSettings()
                    selectedDestination = if (isHistoryOpen) 1 else 0
                }
            )
        }

        if (showKeyDecryptFailedDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissKeyDecryptFailedDialog() },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Kunci API Tidak Terbaca",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Text(
                        text = "API key yang tersimpan tidak dapat dibuka di perangkat ini. Masukkan ulang key Anda.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.dismissKeyDecryptFailedDialog()
                            viewModel.openSettings()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("btn_reenter_api_key_dialog")
                    ) {
                        Text("Masukkan Ulang", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { viewModel.dismissKeyDecryptFailedDialog() },
                        modifier = Modifier.testTag("btn_dismiss_key_decrypt_dialog")
                    ) {
                        Text("Batal")
                    }
                }
            )
        }

        if (showKeySelectionDialog) {
            val apiKeys by viewModel.apiKeys.collectAsStateWithLifecycle()
            KeySelectionDialog(
                apiKeys = apiKeys,
                onDismiss = { showKeySelectionDialog = false },
                onSelectKeyAndResume = { keyId ->
                    showKeySelectionDialog = false
                    viewModel.switchKeyAndResume(keyId, pendingResumeAnalysisId)
                },
                onAddKeyAndResume = { name, key ->
                    showKeySelectionDialog = false
                    viewModel.addKeyAndResume(name, key, pendingResumeAnalysisId)
                }
            )
        }

        // Dialog Atur Manual Kuota Model
        manualQuotaModelToEdit?.let { modelName ->
            val usageInfo = remember(modelName) { viewModel.modelUsageStorage.getUsageInfo(modelName) }
            ManualQuotaDialog(
                modelName = modelName,
                currentUsage = usageInfo.used,
                currentLimit = usageInfo.limit,
                onDismiss = { manualQuotaModelToEdit = null },
                onConfirm = { newUsage, newLimit ->
                    viewModel.setModelUsage(modelName, newUsage)
                    viewModel.setModelLimit(modelName, newLimit)
                    manualQuotaModelToEdit = null
                }
            )
        }
    }
}

@Composable
private fun HistoryDetailHost(
    historyEntity: AnimeAnalysisEntity,
    viewModel: MainViewModel,
    onStartAnalysis: () -> Unit,
    onFinishWithoutCurrentSegment: () -> Unit
) {
    val narrationLanguage by viewModel.narrationLanguage.collectAsStateWithLifecycle()
    val styleName = remember(historyEntity.id, historyEntity.updatedAt) {
        viewModel.getStyleNameForAnalysis(historyEntity.id)
    }
    val narrationModeName = remember(historyEntity.id, historyEntity.updatedAt) {
        viewModel.getModeNameForAnalysis(historyEntity.id)
    }
    val maxSceneDuration = remember(historyEntity.id, historyEntity.updatedAt) {
        viewModel.getMaxDurationForAnalysis(historyEntity.id)
    }
    val seriesBible = remember(historyEntity.id, historyEntity.updatedAt) {
        viewModel.getSeriesBibleForAnalysis(historyEntity.id)
    }
    val continuityWarnings = remember(historyEntity.id, historyEntity.updatedAt) {
        viewModel.getContinuityWarningsForAnalysis(historyEntity.id)
    }
    val repeatedSceneNotes = remember(historyEntity.id, historyEntity.updatedAt) {
        viewModel.getRepeatedSceneNotesForAnalysis(historyEntity.id)
    }
    val opEdSkipNote = remember(historyEntity.id, historyEntity.updatedAt) {
        viewModel.getOpeningEndingSkipNoteForAnalysis(historyEntity.id)
    }
    val stageTimings = remember(historyEntity.id, historyEntity.updatedAt) {
        viewModel.getStageTimingsForAnalysis(historyEntity.id)
    }

    HistoryDetailScreen(
        entity = historyEntity,
        styleName = styleName,
        narrationModeName = narrationModeName,
        languageName = narrationLanguage.displayName,
        maxSceneDuration = maxSceneDuration,
        seriesBible = seriesBible,
        continuityWarnings = continuityWarnings,
        repeatedSceneNotes = repeatedSceneNotes,
        opEdSkipNote = opEdSkipNote,
        onBack = { viewModel.closeHistoryDetail() },
        onResume = onStartAnalysis,
        onFinishWithoutCurrentSegment = onFinishWithoutCurrentSegment,
        stageTimings = stageTimings,
        onResultEdited = { edited -> viewModel.saveEditedResult(historyEntity.id, edited) }
    )
}

@Composable
private fun HomeSuccessHost(
    state: AnalysisState.Success,
    viewModel: MainViewModel
) {
    val narrationMode by viewModel.narrationMode.collectAsStateWithLifecycle()
    val narrationLanguage by viewModel.narrationLanguage.collectAsStateWithLifecycle()
    val latestStageTimings by viewModel.latestStageTimings.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hasil Analisis Selesai",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Tersimpan di Riwayat",
                    style = RekapinTypography.caption.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledTonalButton(
                onClick = { viewModel.resetAnalysis() },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(32.dp)
                    .wrapContentWidth()
                    .testTag("btn_analyze_another")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Ganti Video",
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        ResultView(
            result = state.result,
            styleName = state.styleName,
            narrationModeName = state.result.narrationMode.ifBlank { narrationMode.displayName },
            languageName = narrationLanguage.displayName,
            stageTimings = latestStageTimings,
            onResultEdited = { edited -> viewModel.saveEditedResult(null, edited) },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun HomeIdleHost(
    viewModel: MainViewModel,
    historyList: List<AnimeAnalysisEntity>,
    selectedModel: String,
    onSelectVideoClicked: () -> Unit,
    onOpenManualQuota: (String) -> Unit,
    requestNotificationAndStart: (() -> Unit) -> Unit
) {
    val selectedVideo by viewModel.selectedVideo.collectAsStateWithLifecycle()
    val promptInterval by viewModel.promptIntervalSeconds.collectAsStateWithLifecycle()
    val promptCutMode by viewModel.promptCutMode.collectAsStateWithLifecycle()
    val maxSceneDurationSeconds by viewModel.maxSceneDurationSeconds.collectAsStateWithLifecycle()
    val detectedSceneCount by viewModel.detectedSceneCount.collectAsStateWithLifecycle()
    val segmentDurationOption by viewModel.segmentDurationOption.collectAsStateWithLifecycle()
    val segmentPlanSummary by viewModel.segmentPlanSummary.collectAsStateWithLifecycle()
    val narrationMode by viewModel.narrationMode.collectAsStateWithLifecycle()
    val isAutoBuildSeriesBible by viewModel.isAutoBuildSeriesBible.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val selectedModelUsage by viewModel.selectedModelUsage.collectAsStateWithLifecycle()
    val visualStyles by viewModel.visualStyles.collectAsStateWithLifecycle()
    val selectedStyle by viewModel.selectedStyle.collectAsStateWithLifecycle()
    val analysisRangeStartSec by viewModel.analysisRangeStartSec.collectAsStateWithLifecycle()
    val analysisRangeEndSec by viewModel.analysisRangeEndSec.collectAsStateWithLifecycle()
    val selectedSeriesSourceId by viewModel.selectedSeriesSourceId.collectAsStateWithLifecycle()
    val selectedSeriesSourceName by viewModel.selectedSeriesSourceName.collectAsStateWithLifecycle()

    val apiKeys by viewModel.apiKeys.collectAsStateWithLifecycle()

    val selectedSeriesBible = remember(selectedSeriesSourceId) {
        selectedSeriesSourceId?.let { viewModel.getSeriesBibleForAnalysis(it) }
    }
    val hasApiKey = remember(apiKeys) {
        apiKeys.isNotEmpty() || viewModel.apiKeyStorage.hasApiKey()
    }

    IdleSetupContent(
        selectedVideo = selectedVideo,
        promptInterval = promptInterval,
        promptCutMode = promptCutMode,
        maxSceneDurationSeconds = maxSceneDurationSeconds,
        detectedSceneCount = detectedSceneCount,
        onPromptCutModeChanged = { viewModel.setPromptCutMode(it) },
        onMaxSceneDurationChanged = { viewModel.setMaxSceneDuration(it) },
        segmentDurationOption = segmentDurationOption,
        segmentPlanSummary = segmentPlanSummary,
        narrationMode = narrationMode,
        isAutoBuildSeriesBible = isAutoBuildSeriesBible,
        historyList = historyList,
        models = models,
        selectedModel = selectedModel,
        selectedModelUsage = selectedModelUsage,
        visualStyles = visualStyles,
        selectedStyle = selectedStyle,
        onSelectStyle = { viewModel.selectStyle(it) },
        analysisRangeStartSec = analysisRangeStartSec,
        analysisRangeEndSec = analysisRangeEndSec,
        onRangeChanged = { start, end -> viewModel.setAnalysisRange(start, end) },
        onOpenManualQuota = onOpenManualQuota,
        onSelectModel = { viewModel.setSelectedModel(it) },
        onSelectVideoClicked = onSelectVideoClicked,
        onClearVideo = { viewModel.clearSelectedVideo() },
        onIntervalChanged = { viewModel.setIntervalSeconds(it) },
        onSegmentDurationChanged = { viewModel.setSegmentDurationOption(it) },
        onStartAnalysis = {
            requestNotificationAndStart {
                viewModel.startAnalysis()
            }
        },
        onResumeAnalysis = { resumeId ->
            requestNotificationAndStart {
                viewModel.startAnalysis(resumeId)
            }
        },
        onFinishWithoutCurrentSegment = { analysisId -> viewModel.finishWithoutCurrentSegment(analysisId) },
        selectedSeriesSourceId = selectedSeriesSourceId,
        selectedSeriesSourceName = selectedSeriesSourceName,
        selectedSeriesSourceBible = selectedSeriesBible,
        onSelectSeriesSource = { viewModel.selectSeriesSource(it) },
        onOpenSettings = { viewModel.openSettings() },
        onOpenHistory = { viewModel.openHistory() },
        hasApiKey = hasApiKey
    )
}

@Composable
private fun HomeSettingsHost(
    viewModel: MainViewModel,
    activeKeyName: String,
    selectedModel: String,
    isKeyDecryptFailed: Boolean,
    onDismiss: () -> Unit
) {
    val apiKeys by viewModel.apiKeys.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val isAutoSwitchModel by viewModel.isAutoSwitchModel.collectAsStateWithLifecycle()
    val narrationLanguage by viewModel.narrationLanguage.collectAsStateWithLifecycle()
    val narrationMode by viewModel.narrationMode.collectAsStateWithLifecycle()
    val visualStyles by viewModel.visualStyles.collectAsStateWithLifecycle()
    val selectedStyle by viewModel.selectedStyle.collectAsStateWithLifecycle()
    val isAutoBuildSeriesBible by viewModel.isAutoBuildSeriesBible.collectAsStateWithLifecycle()
    val isPrependRecapNarration by viewModel.isPrependRecapNarration.collectAsStateWithLifecycle()
    val isAutoSkipOpEd by viewModel.isAutoSkipOpEd.collectAsStateWithLifecycle()
    val isDetectRepeatedScenes by viewModel.isDetectRepeatedScenes.collectAsStateWithLifecycle()
    val requestPacingMode by viewModel.requestPacingMode.collectAsStateWithLifecycle()
    val thinkingLevelMode by viewModel.thinkingLevelMode.collectAsStateWithLifecycle()

    SettingsDialog(
        apiKeys = apiKeys,
        activeKeyName = activeKeyName,
        models = models,
        selectedModel = selectedModel,
        isAutoSwitchModel = isAutoSwitchModel,
        selectedLanguage = narrationLanguage,
        selectedNarrationMode = narrationMode,
        onDismiss = onDismiss,
        onAddKey = { name, key -> viewModel.addApiKey(name, key) },
        onRenameKey = { id, newName -> viewModel.renameApiKey(id, newName) },
        onDeleteKey = { id -> viewModel.deleteApiKey(id) },
        onSetActiveKey = { id -> viewModel.setActiveApiKey(id) },
        onAddModel = { model -> viewModel.addModel(model) },
        onDeleteModel = { model -> viewModel.deleteModel(model) },
        onMoveModel = { from, to -> viewModel.moveModel(from, to) },
        onSelectModel = { model -> viewModel.setSelectedModel(model) },
        onToggleAutoSwitch = { enabled -> viewModel.toggleAutoSwitchModel(enabled) },
        onSelectLanguage = { lang -> viewModel.setNarrationLanguage(lang) },
        onSelectNarrationMode = { mode -> viewModel.setNarrationMode(mode) },
        visualStyles = visualStyles,
        selectedStyle = selectedStyle,
        onSelectStyle = { viewModel.selectStyle(it) },
        onAddCustomStyle = { name, desc -> viewModel.addCustomStyle(name, desc) },
        onUpdateCustomStyle = { id, name, desc -> viewModel.updateCustomStyle(id, name, desc) },
        onDeleteCustomStyle = { id -> viewModel.deleteCustomStyle(id) },
        modelUsageStorage = viewModel.modelUsageStorage,
        onSetModelLimit = { model, limit -> viewModel.setModelLimit(model, limit) },
        onSetModelUsage = { model, usage -> viewModel.setModelUsage(model, usage) },
        onTestKey = { key -> viewModel.testApiKey(key) },
        isAutoBuildSeriesBible = isAutoBuildSeriesBible,
        onToggleAutoBuildSeriesBible = { viewModel.toggleAutoBuildSeriesBible(it) },
        isPrependRecapNarration = isPrependRecapNarration,
        onTogglePrependRecapNarration = { viewModel.togglePrependRecapNarration(it) },
        isAutoSkipOpEd = isAutoSkipOpEd,
        onToggleAutoSkipOpEd = { viewModel.toggleAutoSkipOpEd(it) },
        isDetectRepeatedScenes = isDetectRepeatedScenes,
        onToggleDetectRepeatedScenes = { viewModel.toggleDetectRepeatedScenes(it) },
        isKeyDecryptFailed = isKeyDecryptFailed,
        onTestKeyWithProgress = { key, onProgress -> viewModel.testApiKey(key, onProgress) },
        selectedPacingMode = requestPacingMode,
        onSelectPacingMode = { viewModel.setRequestPacingMode(it) },
        selectedThinkingLevel = thinkingLevelMode,
        onSelectThinkingLevel = { viewModel.setThinkingLevelMode(it) }
    )
}

@Composable
private fun RekapinBottomNavigation(
    selectedDestination: Int,
    historyCount: Int,
    onSelect: (Int) -> Unit
) {
    val destinations = listOf(
        Triple(Icons.Default.Animation, "Studio", "nav_studio"),
        Triple(Icons.Default.History, "Riwayat", "btn_open_history"),
        Triple(Icons.Default.Settings, "Pengaturan", "settings_button")
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(64.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEachIndexed { index, (icon, label, testTag) ->
                val isSelected = (selectedDestination == index)
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelect(index) },
                    icon = {
                        if (index == 1 && historyCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) { Text(historyCount.toString(), style = RekapinTypography.captionBold.copy(fontSize = 11.sp)) }
                                }
                            ) {
                                Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
                            }
                        } else {
                            Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
                        }
                    },
                    label = {
                        Text(
                            text = label,
                            style = (if (isSelected) RekapinTypography.captionBold else RekapinTypography.caption)
                                .copy(fontSize = 11.sp, lineHeight = 14.sp),
                            maxLines = 1
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag(testTag)
                )
            }
        }
    }
}
