package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.util.VideoThumbnailHelper
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Palette
import com.example.R
import com.example.data.api.GeminiConstants
import com.example.data.db.AnimeAnalysisEntity
import com.example.data.model.AnalysisState
import com.example.data.model.ModelUsageInfo
import com.example.data.model.PromptCutMode
import com.example.data.model.SelectedVideo
import com.example.data.model.VisualStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.NavigationBarItemDefaults
import com.example.ui.components.ChoiceChips
import com.example.ui.components.CollapsibleSection
import com.example.ui.components.PrimaryActionBar
import com.example.ui.components.RekapinCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.SettingRow
import com.example.ui.theme.RekapinBorders
import com.example.ui.theme.RekapinRadii
import com.example.ui.theme.RekapinSpacing
import com.example.ui.theme.RekapinTypography
import com.example.ui.components.KeySelectionDialog
import com.example.ui.components.ManualQuotaDialog
import com.example.ui.components.ResultView
import com.example.ui.components.SettingsDialog
import com.example.ui.viewmodel.MainViewModel
import com.example.util.SegmentCalculator
import com.example.util.SegmentDurationOption
import com.example.util.SegmentPlanSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel
) {
    val selectedVideo by viewModel.selectedVideo.collectAsState()
    val promptInterval by viewModel.promptIntervalSeconds.collectAsState()
    val segmentDurationOption by viewModel.segmentDurationOption.collectAsState()
    val segmentPlanSummary by viewModel.segmentPlanSummary.collectAsState()
    val pendingMatchDialogState by viewModel.pendingMatchDialogState.collectAsState()
    val analysisState by viewModel.analysisState.collectAsState()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()
    val apiKeys by viewModel.apiKeys.collectAsState()
    val activeKeyName by viewModel.activeKeyName.collectAsState()

    val models by viewModel.models.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val isAutoSwitchModel by viewModel.isAutoSwitchModel.collectAsState()
    val narrationLanguage by viewModel.narrationLanguage.collectAsState()
    val narrationMode by viewModel.narrationMode.collectAsState()
    val selectedModelUsage by viewModel.selectedModelUsage.collectAsState()
    val visualStyles by viewModel.visualStyles.collectAsState()
    val selectedStyle by viewModel.selectedStyle.collectAsState()
    val analysisRangeStartSec by viewModel.analysisRangeStartSec.collectAsState()
    val analysisRangeEndSec by viewModel.analysisRangeEndSec.collectAsState()
    val selectedSeriesSourceId by viewModel.selectedSeriesSourceId.collectAsState()
    val selectedSeriesSourceName by viewModel.selectedSeriesSourceName.collectAsState()
    val promptCutMode by viewModel.promptCutMode.collectAsState()
    val maxSceneDurationSeconds by viewModel.maxSceneDurationSeconds.collectAsState()
    val detectedSceneCount by viewModel.detectedSceneCount.collectAsState()
    val isAutoBuildSeriesBible by viewModel.isAutoBuildSeriesBible.collectAsState()
    val isPrependRecapNarration by viewModel.isPrependRecapNarration.collectAsState()
    val failedSeriesBibleBuildIds by viewModel.failedSeriesBibleBuildIds.collectAsState()
    val seriesBibleBuildFailureNotice by viewModel.seriesBibleBuildFailureNotice.collectAsState()
    val seriesBibleBuildInProgressId by viewModel.seriesBibleBuildInProgressId.collectAsState()
    val isAutoSkipOpEd by viewModel.isAutoSkipOpEd.collectAsState()
    val isDetectRepeatedScenes by viewModel.isDetectRepeatedScenes.collectAsState()
    val showKeyDecryptFailedDialog by viewModel.showKeyDecryptFailedDialog.collectAsState()

    var showKeySelectionDialog by remember { mutableStateOf(false) }
    var pendingResumeAnalysisId by remember { mutableStateOf<Long?>(null) }
    var manualQuotaModelToEdit by remember { mutableStateOf<String?>(null) }
    var selectedDestination by remember { mutableIntStateOf(0) }

    val historyList by viewModel.historyList.collectAsState()
    val isHistoryOpen by viewModel.isHistoryOpen.collectAsState()
    val viewingHistoryItem by viewModel.viewingHistoryItem.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(isHistoryOpen) {
        if (isHistoryOpen) selectedDestination = 1
    }

    val onSelectPrimaryDestination: (Int) -> Unit = { destination ->
        selectedDestination = destination
        when (destination) {
            0 -> {
                viewModel.closeHistory()
                if (isSettingsOpen) viewModel.closeSettings()
            }
            1 -> viewModel.openHistory()
            2 -> {
                viewModel.closeHistory()
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
                android.util.Log.w("HomeScreen", "Gagal takePersistableUriPermission: ${e.message}")
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
                android.util.Log.w("HomeScreen", "Gagal takePersistableUriPermission: ${e.message}")
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
        HistoryDetailScreen(
            entity = historyEntity,
            styleName = viewModel.getStyleNameForAnalysis(historyEntity.id),
            narrationModeName = viewModel.getModeNameForAnalysis(historyEntity.id),
            languageName = narrationLanguage.displayName,
            maxSceneDuration = viewModel.getMaxDurationForAnalysis(historyEntity.id),
            seriesBible = viewModel.getSeriesBibleForAnalysis(historyEntity.id),
            continuityWarnings = viewModel.getContinuityWarningsForAnalysis(historyEntity.id),
            repeatedSceneNotes = viewModel.getRepeatedSceneNotesForAnalysis(historyEntity.id),
            opEdSkipNote = viewModel.getOpeningEndingSkipNoteForAnalysis(historyEntity.id),
            onBack = { viewModel.closeHistoryDetail() },
            onResume = {
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

    // Tampilkan Layar Riwayat jika sedang dibuka
    if (isHistoryOpen) {
        HistoryScreen(
            analyses = historyList,
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
            getMaxDurationForAnalysis = { viewModel.getMaxDurationForAnalysis(it) },
            failedSeriesBibleBuildIds = failedSeriesBibleBuildIds,
            seriesBibleBuildInProgressId = seriesBibleBuildInProgressId,
            onBuildSeriesBibleNow = { viewModel.buildSeriesBibleNow(it) },
            onFinishWithoutCurrentSegment = { resumeId ->
                viewModel.finishWithoutCurrentSegment(resumeId)
                viewModel.closeHistory()
                selectedDestination = 0
            },
            onMergeNarrationsWithAi = { parts, onStatus ->
                viewModel.mergeNarrationsWithAi(parts, onStatus)
            },
            bottomNavigationContent = {
                RekapinBottomNavigation(
                    selectedDestination = selectedDestination,
                    historyCount = historyList.size,
                    onSelect = onSelectPrimaryDestination
                )
            }
        )
        return
    }

    // Tangani tombol Back sistem Android: jika di hasil/error kembalikan ke beranda, jika proses berjalan batalkan
    if (analysisState is AnalysisState.Success || analysisState is AnalysisState.Error) {
        BackHandler {
            viewModel.resetAnalysis()
        }
    } else if (analysisState !is AnalysisState.Idle) {
        BackHandler {
            viewModel.cancelAnalysis()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Animation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(id = R.string.app_name),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Studio rekap anime",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (analysisState is AnalysisState.Idle ||
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
            when (val state = analysisState) {
                is AnalysisState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Header Bar with Quick Action
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Hasil Analisis Selesai",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                val intervalLabel = if (state.result.intervalSeconds == 0) {
                                    val maxDurText = if (state.result.maxSceneDuration == 0) "Tanpa batas" else "${state.result.maxSceneDuration}s"
                                    "Per Adegan ($maxDurText)"
                                } else "Interval ${state.result.intervalSeconds}s"
                                Text(
                                    text = "${state.result.totalSegments} Segmen • $intervalLabel (Tersimpan di Riwayat)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { viewModel.resetAnalysis() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("btn_analyze_another")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ganti Video", fontSize = 12.sp)
                            }
                        }

                        // Hasil Lengkap (Narasi, Lembar Karakter, Timeline Visual Prompt)
                        ResultView(
                            result = state.result,
                            styleName = state.styleName,
                            narrationModeName = state.result.narrationMode.ifBlank { narrationMode.displayName },
                            languageName = narrationLanguage.displayName,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
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
                        onOpenManualQuota = { manualQuotaModelToEdit = it },
                        onSelectModel = { viewModel.setSelectedModel(it) },
                        onSelectVideoClicked = {
                            videoPickerLauncher.launch(arrayOf("video/*"))
                        },
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
                        selectedSeriesSourceBible = selectedSeriesSourceId?.let { viewModel.getSeriesBibleForAnalysis(it) },
                        onSelectSeriesSource = { viewModel.selectSeriesSource(it) },
                        onOpenSettings = { viewModel.openSettings() },
                        onOpenHistory = { viewModel.openHistory() },
                        hasApiKey = viewModel.apiKeyStorage.hasApiKey()
                    )
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
            SettingsDialog(
                apiKeys = apiKeys,
                activeKeyName = activeKeyName,
                models = models,
                selectedModel = selectedModel,
                isAutoSwitchModel = isAutoSwitchModel,
                selectedLanguage = narrationLanguage,
                selectedNarrationMode = narrationMode,
                onDismiss = {
                    viewModel.closeSettings()
                    selectedDestination = 0
                },
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
                isKeyDecryptFailed = showKeyDecryptFailedDialog
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
            val usageInfo = viewModel.modelUsageStorage.getUsageInfo(modelName)
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

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets.navigationBars
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
                                ) { Text(historyCount.toString(), style = RekapinTypography.captionBold) }
                            }
                        ) {
                            Icon(icon, contentDescription = label)
                        }
                    } else {
                        Icon(icon, contentDescription = label)
                    }
                },
                label = {
                    Text(
                        text = label,
                        style = if (isSelected) RekapinTypography.captionBold else RekapinTypography.caption
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


@Composable
private fun IdleSetupContent(
    selectedVideo: SelectedVideo?,
    promptInterval: Int,
    promptCutMode: PromptCutMode,
    maxSceneDurationSeconds: Int,
    detectedSceneCount: Int?,
    onPromptCutModeChanged: (PromptCutMode) -> Unit,
    onMaxSceneDurationChanged: (Int) -> Unit,
    segmentDurationOption: SegmentDurationOption,
    segmentPlanSummary: SegmentPlanSummary?,
    narrationMode: com.example.data.model.NarrationMode,
    isAutoBuildSeriesBible: Boolean,
    historyList: List<AnimeAnalysisEntity>,
    models: List<String>,
    selectedModel: String,
    selectedModelUsage: ModelUsageInfo,
    visualStyles: List<VisualStyle>,
    selectedStyle: VisualStyle,
    onSelectStyle: (VisualStyle) -> Unit,
    analysisRangeStartSec: Long,
    analysisRangeEndSec: Long,
    onRangeChanged: (Long, Long) -> Unit,
    onOpenManualQuota: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectVideoClicked: () -> Unit,
    onClearVideo: () -> Unit,
    onIntervalChanged: (Int) -> Unit,
    onSegmentDurationChanged: (SegmentDurationOption) -> Unit,
    onStartAnalysis: () -> Unit,
    onResumeAnalysis: (Long) -> Unit,
    onFinishWithoutCurrentSegment: (Long) -> Unit,
    selectedSeriesSourceId: Long?,
    selectedSeriesSourceName: String,
    selectedSeriesSourceBible: com.example.data.model.SeriesBible? = null,
    onSelectSeriesSource: (Long?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    hasApiKey: Boolean
) {
    val scrollState = rememberScrollState()
    val pendingAnalysis = historyList.firstOrNull { !it.isCompleted && it.completedSegmentsCount > 0 }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = RekapinSpacing.lg, vertical = RekapinSpacing.md),
            verticalArrangement = Arrangement.spacedBy(RekapinSpacing.md)
        ) {
            // Banner Lanjutkan Analisis Tertunda (jika ada yang belum selesai)
            if (pendingAnalysis != null) {
                PendingResumeBanner(
                    pendingAnalysis = pendingAnalysis,
                    onResumeAnalysis = onResumeAnalysis,
                    onFinishWithoutCurrentSegment = onFinishWithoutCurrentSegment
                )
            }

            // Banner Warning jika API key belum diatur
            if (!hasApiKey) {
                ApiKeyWarningBanner(onOpenSettings = onOpenSettings)
            }

            // 1. Kartu "Video"
            VideoSetupCard(
                selectedVideo = selectedVideo,
                analysisRangeStartSec = analysisRangeStartSec,
                analysisRangeEndSec = analysisRangeEndSec,
                onSelectVideoClicked = onSelectVideoClicked,
                onClearVideo = onClearVideo,
                onRangeChanged = onRangeChanged
            )

            // 2. Kartu "Pengaturan Utama"
            MainSettingsCard(
                visualStyles = visualStyles,
                selectedStyle = selectedStyle,
                onSelectStyle = onSelectStyle,
                promptCutMode = promptCutMode,
                onPromptCutModeChanged = onPromptCutModeChanged,
                promptInterval = promptInterval,
                onIntervalChanged = onIntervalChanged,
                maxSceneDurationSeconds = maxSceneDurationSeconds,
                onMaxSceneDurationChanged = onMaxSceneDurationChanged
            )

            // 3. Kartu "Lanjutan" (tertutup secara default)
            AdvancedSettingsCard(
                segmentDurationOption = segmentDurationOption,
                onSegmentDurationChanged = onSegmentDurationChanged,
                historyList = historyList,
                selectedSeriesSourceId = selectedSeriesSourceId,
                selectedSeriesSourceName = selectedSeriesSourceName,
                selectedSeriesSourceBible = selectedSeriesSourceBible,
                onSelectSeriesSource = onSelectSeriesSource,
                models = models,
                selectedModel = selectedModel,
                selectedModelUsage = selectedModelUsage,
                onSelectModel = onSelectModel,
                onOpenManualQuota = onOpenManualQuota
            )

            // 4. Kartu "Ringkasan Rencana"
            PlanSummaryCard(
                selectedVideo = selectedVideo,
                segmentPlanSummary = segmentPlanSummary,
                promptCutMode = promptCutMode,
                detectedSceneCount = detectedSceneCount,
                selectedModelUsage = selectedModelUsage
            )
        }

        // 5. PrimaryActionBar menempel di bawah dengan tombol "Mulai Analisis"
        val canStart = (selectedVideo != null)
        val summaryText = if (!canStart) {
            "Pilih video dulu"
        } else {
            val segCount = segmentPlanSummary?.segmentCount ?: 1
            val modelName = selectedModel.ifBlank { "Gemini" }
            "Siap dianalisis · $segCount segmen ($modelName)"
        }

        PrimaryActionBar(
            buttonText = "Mulai Analisis",
            enabled = canStart,
            summaryText = summaryText,
            icon = Icons.Default.Animation,
            onClick = onStartAnalysis,
            buttonModifier = Modifier.testTag("btn_start_analysis")
        )
    }
}

@Composable
private fun PendingResumeBanner(
    pendingAnalysis: AnimeAnalysisEntity,
    onResumeAnalysis: (Long) -> Unit,
    onFinishWithoutCurrentSegment: (Long) -> Unit
) {
    RekapinCard(
        isSelected = true,
        shape = RoundedCornerShape(RekapinRadii.md),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("banner_resume_pending")
    ) {
        Column(
            modifier = Modifier.padding(RekapinSpacing.md),
            verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(RekapinRadii.sm))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lanjutkan Analisis Tertunda",
                        style = RekapinTypography.bodyBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${pendingAnalysis.videoName} (Tersimpan Segmen ${pendingAnalysis.completedSegmentsCount} dari ${pendingAnalysis.totalSegments})",
                        style = RekapinTypography.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = { onResumeAnalysis(pendingAnalysis.id) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(RekapinRadii.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("btn_resume_pending_home")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                Text(
                    text = "Lanjutkan dari Segmen ${pendingAnalysis.completedSegmentsCount + 1}",
                    style = RekapinTypography.bodyBold
                )
            }

            OutlinedButton(
                onClick = { onFinishWithoutCurrentSegment(pendingAnalysis.id) },
                shape = RoundedCornerShape(RekapinRadii.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .testTag("btn_finish_without_segment_home")
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                Text(
                    text = "Selesaikan tanpa segmen ini",
                    style = RekapinTypography.captionBold
                )
            }
        }
    }
}

@Composable
private fun ApiKeyWarningBanner(onOpenSettings: () -> Unit) {
    RekapinCard(
        isSelected = true,
        onClick = onOpenSettings,
        shape = RoundedCornerShape(RekapinRadii.md),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("api_key_warning_banner")
    ) {
        Row(
            modifier = Modifier.padding(RekapinSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(RekapinSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "API Key Gemini Belum Diatur",
                    style = RekapinTypography.bodyBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Ketuk untuk memasukkan API Key Gemini gratis Anda agar dapat memulai analisis.",
                    style = RekapinTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VideoSetupCard(
    selectedVideo: SelectedVideo?,
    analysisRangeStartSec: Long,
    analysisRangeEndSec: Long,
    onSelectVideoClicked: () -> Unit,
    onClearVideo: () -> Unit,
    onRangeChanged: (Long, Long) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    if (selectedVideo == null) {
        RekapinCard(
            onClick = onSelectVideoClicked,
            shape = RoundedCornerShape(RekapinRadii.md),
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pick_video_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(RekapinSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(RekapinRadii.sm))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(RekapinSpacing.sm))
                Text(
                    text = "Pilih Berkas Video Anime",
                    style = RekapinTypography.sectionTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(RekapinSpacing.xs))
                Text(
                    text = "Target pemakaian: Video anime (.mp4, .mkv, .webm)",
                    style = RekapinTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(RekapinSpacing.md))
                Button(
                    onClick = onSelectVideoClicked,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(RekapinRadii.sm),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("btn_select_video")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                    Text("Pilih Video dari Galeri", style = RekapinTypography.bodyBold)
                }
            }
        }
    } else {
        RekapinCard(
            shape = RoundedCornerShape(RekapinRadii.md),
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("video_info_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(RekapinSpacing.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(RekapinRadii.sm))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedVideo.displayName,
                            style = RekapinTypography.bodyBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${selectedVideo.sizeFormatted} • Durasi: ${selectedVideo.durationFormatted}",
                            style = RekapinTypography.caption,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onClearVideo,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("clear_video_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Hapus video",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onSelectVideoClicked,
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("btn_change_video")
                    ) {
                        Text("Ganti", style = RekapinTypography.captionBold)
                    }
                }

                if (selectedVideo.durationMs > 0) {
                    val totalVideoSeconds = (selectedVideo.durationMs / 1000).coerceAtLeast(1L)
                    val currentRangeStart = analysisRangeStartSec.coerceIn(0L, totalVideoSeconds)
                    val currentRangeEnd = if (analysisRangeEndSec <= 0L || analysisRangeEndSec > totalVideoSeconds) totalVideoSeconds else analysisRangeEndSec

                    var isRangeExpanded by remember { mutableStateOf(false) }

                    Spacer(modifier = Modifier.height(RekapinSpacing.sm))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("analysis_range_card")
                    ) {
                        Column(modifier = Modifier.padding(RekapinSpacing.sm)) {
                            CollapsibleSection(
                                title = "Rentang Analisis",
                                summary = "${SegmentCalculator.formatTimeSeconds(currentRangeStart)} - ${SegmentCalculator.formatTimeSeconds(currentRangeEnd)} (${SegmentCalculator.formatTimeSeconds(currentRangeEnd - currentRangeStart)} terpilih)",
                                isExpanded = isRangeExpanded,
                                onToggle = { isRangeExpanded = !isRangeExpanded }
                            ) {
                                AnalysisRangeEditor(
                                    context = context,
                                    videoUri = selectedVideo.uri,
                                    totalVideoSeconds = totalVideoSeconds,
                                    currentRangeStart = currentRangeStart,
                                    currentRangeEnd = currentRangeEnd,
                                    onRangeChanged = onRangeChanged
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisRangeEditor(
    context: android.content.Context,
    videoUri: android.net.Uri,
    totalVideoSeconds: Long,
    currentRangeStart: Long,
    currentRangeEnd: Long,
    onRangeChanged: (Long, Long) -> Unit
) {
    var activeThumbTimeSec by remember { mutableStateOf<Long?>(currentRangeStart) }
    var activeThumbLabel by remember { mutableStateOf("Awal") }
    var thumbBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isThumbLoading by remember { mutableStateOf(false) }

    LaunchedEffect(activeThumbTimeSec) {
        val sec = activeThumbTimeSec ?: return@LaunchedEffect
        delay(200)
        isThumbLoading = true
        val bmp = VideoThumbnailHelper.getFrameThumbnail(context, videoUri, sec)
        thumbBitmap = bmp
        isThumbLoading = false
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
    ) {
        Text(
            text = "Batasi analisis hanya pada bagian video tertentu. Geser slider atau masukkan waktu manual (mm:ss).",
            style = RekapinTypography.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Thumbnail Frame Pratinjau
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RekapinRadii.sm))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(RekapinSpacing.xs),
            contentAlignment = Alignment.Center
        ) {
            if (thumbBitmap != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(RekapinRadii.sm))
                        .border(
                            width = RekapinBorders.selectedWidth,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(RekapinRadii.sm)
                        )
                ) {
                    Image(
                        bitmap = thumbBitmap!!.asImageBitmap(),
                        contentDescription = "Thumbnail frame $activeThumbLabel",
                        modifier = Modifier
                            .width(140.dp)
                            .height(80.dp),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "$activeThumbLabel: ${SegmentCalculator.formatTimeSeconds(activeThumbTimeSec ?: 0L)}",
                            style = RekapinTypography.captionBold,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(80.dp)
                        .clip(RoundedCornerShape(RekapinRadii.sm))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    if (isThumbLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Geser untuk pratinjau",
                                style = RekapinTypography.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // RangeSlider
        RangeSlider(
            value = currentRangeStart.toFloat()..currentRangeEnd.toFloat(),
            onValueChange = { range ->
                val startMoved = kotlin.math.abs(range.start - currentRangeStart.toFloat()) > 0.5f
                var newStart = range.start.toLong().coerceIn(0L, totalVideoSeconds - SegmentCalculator.MIN_RANGE_SECONDS)
                var newEnd = range.endInclusive.toLong().coerceIn(SegmentCalculator.MIN_RANGE_SECONDS, totalVideoSeconds)

                if (newEnd - newStart < SegmentCalculator.MIN_RANGE_SECONDS) {
                    if (startMoved) {
                        newStart = (newEnd - SegmentCalculator.MIN_RANGE_SECONDS).coerceAtLeast(0L)
                    } else {
                        newEnd = (newStart + SegmentCalculator.MIN_RANGE_SECONDS).coerceAtMost(totalVideoSeconds)
                    }
                }

                activeThumbTimeSec = if (startMoved) newStart else newEnd
                activeThumbLabel = if (startMoved) "Awal" else "Akhir"
                onRangeChanged(newStart, newEnd)
            },
            valueRange = 0f..totalVideoSeconds.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("range_slider_analysis")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Awal: ${SegmentCalculator.formatTimeSeconds(currentRangeStart)}",
                style = RekapinTypography.captionBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Total: ${SegmentCalculator.formatTimeSeconds(totalVideoSeconds)}",
                style = RekapinTypography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Akhir: ${SegmentCalculator.formatTimeSeconds(currentRangeEnd)}",
                style = RekapinTypography.captionBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        var startInputText by remember(currentRangeStart) {
            mutableStateOf(SegmentCalculator.formatTimeSeconds(currentRangeStart))
        }
        var endInputText by remember(currentRangeEnd) {
            mutableStateOf(SegmentCalculator.formatTimeSeconds(currentRangeEnd))
        }
        var validationErrorMsg by remember { mutableStateOf<String?>(null) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
        ) {
            OutlinedTextField(
                value = startInputText,
                onValueChange = { input ->
                    startInputText = input
                    val parsedStart = SegmentCalculator.parseTimeSeconds(input)
                    val parsedEnd = SegmentCalculator.parseTimeSeconds(endInputText) ?: currentRangeEnd
                    if (parsedStart == null) {
                        validationErrorMsg = "Format waktu awal tidak valid (mm:ss)"
                    } else if (parsedStart < 0L) {
                        validationErrorMsg = "Waktu awal minimal 00:00"
                    } else if (parsedEnd - parsedStart < SegmentCalculator.MIN_RANGE_SECONDS) {
                        validationErrorMsg = "Selisih waktu minimal 10 detik"
                    } else if (parsedEnd > totalVideoSeconds) {
                        validationErrorMsg = "Waktu akhir melebihi durasi video"
                    } else {
                        validationErrorMsg = null
                        activeThumbTimeSec = parsedStart
                        activeThumbLabel = "Awal"
                        onRangeChanged(parsedStart, parsedEnd)
                    }
                },
                label = { Text("Waktu Awal", style = RekapinTypography.caption) },
                singleLine = true,
                isError = validationErrorMsg != null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(RekapinRadii.sm),
                modifier = Modifier
                    .weight(1f)
                    .testTag("input_range_start")
            )

            OutlinedTextField(
                value = endInputText,
                onValueChange = { input ->
                    endInputText = input
                    val parsedEnd = SegmentCalculator.parseTimeSeconds(input)
                    val parsedStart = SegmentCalculator.parseTimeSeconds(startInputText) ?: currentRangeStart
                    if (parsedEnd == null) {
                        validationErrorMsg = "Format waktu akhir tidak valid (mm:ss)"
                    } else if (parsedEnd > totalVideoSeconds) {
                        validationErrorMsg = "Waktu akhir melebihi durasi video"
                    } else if (parsedEnd - parsedStart < SegmentCalculator.MIN_RANGE_SECONDS) {
                        validationErrorMsg = "Selisih waktu minimal 10 detik"
                    } else {
                        validationErrorMsg = null
                        activeThumbTimeSec = parsedEnd
                        activeThumbLabel = "Akhir"
                        onRangeChanged(parsedStart, parsedEnd)
                    }
                },
                label = { Text("Waktu Akhir", style = RekapinTypography.caption) },
                singleLine = true,
                isError = validationErrorMsg != null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(RekapinRadii.sm),
                modifier = Modifier
                    .weight(1f)
                    .testTag("input_range_end")
            )
        }

        if (validationErrorMsg != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                Text(
                    text = validationErrorMsg ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = RekapinTypography.caption
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(
                onClick = {
                    validationErrorMsg = null
                    startInputText = SegmentCalculator.formatTimeSeconds(0L)
                    endInputText = SegmentCalculator.formatTimeSeconds(totalVideoSeconds)
                    activeThumbTimeSec = 0L
                    activeThumbLabel = "Awal"
                    onRangeChanged(0L, totalVideoSeconds)
                },
                shape = RoundedCornerShape(RekapinRadii.sm),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("btn_reset_full_video")
            ) {
                Text("Pilih Seluruh Video", style = RekapinTypography.captionBold)
            }
        }
    }
}

@Composable
private fun MainSettingsCard(
    visualStyles: List<VisualStyle>,
    selectedStyle: VisualStyle,
    onSelectStyle: (VisualStyle) -> Unit,
    promptCutMode: PromptCutMode,
    onPromptCutModeChanged: (PromptCutMode) -> Unit,
    promptInterval: Int,
    onIntervalChanged: (Int) -> Unit,
    maxSceneDurationSeconds: Int,
    onMaxSceneDurationChanged: (Int) -> Unit
) {
    RekapinCard(
        shape = RoundedCornerShape(RekapinRadii.md),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(RekapinSpacing.md),
            verticalArrangement = Arrangement.spacedBy(RekapinSpacing.md)
        ) {
            SectionHeader(
                title = "Pengaturan Utama",
                subtitle = "Gaya visual dan mode pemotongan prompt"
            )

            // 1. Gaya Visual
            Column(verticalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)) {
                Text(
                    text = "Gaya Visual",
                    style = RekapinTypography.bodyBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ChoiceChips(
                    items = visualStyles,
                    selectedItem = selectedStyle,
                    onItemSelected = onSelectStyle,
                    labelProvider = { it.name },
                    subtitleProvider = { if (it.isBuiltIn) "Bawaan" else "Kustom" },
                    testTagProvider = { style ->
                        val idx = visualStyles.indexOf(style)
                        "menu_item_style_$idx"
                    },
                    modifier = Modifier.testTag("dropdown_style_selector")
                )
                Text(
                    text = selectedStyle.promptDescription,
                    style = RekapinTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 2. Mode Potong Prompt
            Column(verticalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)) {
                Text(
                    text = "Mode Potong Prompt",
                    style = RekapinTypography.bodyBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ChoiceChips(
                    items = listOf(PromptCutMode.FIXED_INTERVAL, PromptCutMode.PER_SCENE),
                    selectedItem = promptCutMode,
                    onItemSelected = onPromptCutModeChanged,
                    labelProvider = { mode ->
                        when (mode) {
                            PromptCutMode.FIXED_INTERVAL -> "Interval Tetap"
                            PromptCutMode.PER_SCENE -> "Per Adegan"
                        }
                    },
                    subtitleProvider = { mode ->
                        when (mode) {
                            PromptCutMode.FIXED_INTERVAL -> "Bawaan"
                            PromptCutMode.PER_SCENE -> "Deteksi lokal"
                        }
                    },
                    testTagProvider = { mode ->
                        when (mode) {
                            PromptCutMode.FIXED_INTERVAL -> "chip_cut_mode_fixed"
                            PromptCutMode.PER_SCENE -> "chip_cut_mode_scene"
                        }
                    }
                )

                // Baris chip kedua di bawahnya untuk interval atau batas durasi
                if (promptCutMode == PromptCutMode.FIXED_INTERVAL) {
                    Text(
                        text = "Pilihan interval waktu:",
                        style = RekapinTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ChoiceChips(
                        items = listOf(10, 5, 3),
                        selectedItem = promptInterval,
                        onItemSelected = onIntervalChanged,
                        labelProvider = { "$it Detik" },
                        subtitleProvider = { interval ->
                            when (interval) {
                                10 -> "Stabil"
                                5 -> "Default"
                                3 -> "Rinci"
                                else -> ""
                            }
                        },
                        testTagProvider = { interval ->
                            when (interval) {
                                10 -> "interval_10s_option"
                                5 -> "interval_5s_option"
                                3 -> "interval_3s_option"
                                else -> "interval_${interval}s_option"
                            }
                        }
                    )
                } else {
                    Text(
                        text = "Batas maksimum durasi adegan:",
                        style = RekapinTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ChoiceChips(
                        items = listOf(3, 5, 10, 0),
                        selectedItem = maxSceneDurationSeconds,
                        onItemSelected = onMaxSceneDurationChanged,
                        labelProvider = { dur ->
                            if (dur == 0) "Tanpa batas" else "$dur Detik"
                        },
                        subtitleProvider = { dur ->
                            when (dur) {
                                3 -> "Aksi cepat"
                                5 -> "Bawaan"
                                10 -> "Panjang"
                                0 -> "Asli"
                                else -> ""
                            }
                        },
                        testTagProvider = { dur ->
                            when (dur) {
                                3 -> "interval_3s_option"
                                5 -> "interval_5s_option"
                                10 -> "radio_max_scene_10"
                                0 -> "radio_max_scene_unlimited"
                                else -> "radio_max_scene_$dur"
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedSettingsCard(
    segmentDurationOption: SegmentDurationOption,
    onSegmentDurationChanged: (SegmentDurationOption) -> Unit,
    historyList: List<AnimeAnalysisEntity>,
    selectedSeriesSourceId: Long?,
    selectedSeriesSourceName: String,
    selectedSeriesSourceBible: com.example.data.model.SeriesBible?,
    onSelectSeriesSource: (Long?) -> Unit,
    models: List<String>,
    selectedModel: String,
    selectedModelUsage: ModelUsageInfo,
    onSelectModel: (String) -> Unit,
    onOpenManualQuota: (String) -> Unit
) {
    val segSummaryText = when (segmentDurationOption) {
        SegmentDurationOption.AUTO -> "Segmen otomatis"
        SegmentDurationOption.THREE_MIN -> "Segmen 3m"
        SegmentDurationOption.FIVE_MIN -> "Segmen 5m"
    }
    val seriesSummaryText = if (selectedSeriesSourceId != null) selectedSeriesSourceName else "Tanpa seri"
    val modelSummaryText = selectedModel.ifBlank { "Model bawaan" }
    val advancedSummary = "$segSummaryText · $seriesSummaryText · $modelSummaryText"

    var isAdvancedExpanded by remember { mutableStateOf(false) }

    RekapinCard(
        shape = RoundedCornerShape(RekapinRadii.md),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(RekapinSpacing.md)) {
            CollapsibleSection(
                title = "Lanjutan",
                summary = advancedSummary,
                isExpanded = isAdvancedExpanded,
                onToggle = { isAdvancedExpanded = !isAdvancedExpanded }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(RekapinSpacing.md)
                ) {
                    // 1. Panjang Segmen
                    Column(verticalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)) {
                        Text(
                            text = "Panjang Segmen",
                            style = RekapinTypography.bodyBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ChoiceChips(
                            items = listOf(
                                SegmentDurationOption.AUTO,
                                SegmentDurationOption.THREE_MIN,
                                SegmentDurationOption.FIVE_MIN
                            ),
                            selectedItem = segmentDurationOption,
                            onItemSelected = onSegmentDurationChanged,
                            labelProvider = { opt ->
                                when (opt) {
                                    SegmentDurationOption.AUTO -> "Otomatis"
                                    SegmentDurationOption.THREE_MIN -> "3 Menit"
                                    SegmentDurationOption.FIVE_MIN -> "5 Menit"
                                }
                            },
                            subtitleProvider = { opt ->
                                when (opt) {
                                    SegmentDurationOption.AUTO -> "Default"
                                    SegmentDurationOption.THREE_MIN -> "Rinci"
                                    SegmentDurationOption.FIVE_MIN -> "Hemat kuota"
                                }
                            },
                            testTagProvider = { opt ->
                                when (opt) {
                                    SegmentDurationOption.AUTO -> "segment_auto_option"
                                    SegmentDurationOption.THREE_MIN -> "segment_3m_option"
                                    SegmentDurationOption.FIVE_MIN -> "segment_5m_option"
                                }
                            }
                        )
                    }

                    // 2. Seri (Opsional)
                    Column(verticalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)) {
                        Text(
                            text = "Seri (Opsional)",
                            style = RekapinTypography.bodyBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        SeriesSelectorRow(
                            historyList = historyList,
                            selectedSeriesSourceId = selectedSeriesSourceId,
                            selectedSeriesSourceName = selectedSeriesSourceName,
                            selectedSeriesSourceBible = selectedSeriesSourceBible,
                            onSelectSeriesSource = onSelectSeriesSource
                        )
                    }

                    // 3. Model Gemini
                    Column(verticalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)) {
                        Text(
                            text = "Model Gemini",
                            style = RekapinTypography.bodyBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ModelSelectorRow(
                            models = models,
                            selectedModel = selectedModel,
                            selectedModelUsage = selectedModelUsage,
                            onSelectModel = onSelectModel,
                            onOpenManualQuota = onOpenManualQuota
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesSelectorRow(
    historyList: List<AnimeAnalysisEntity>,
    selectedSeriesSourceId: Long?,
    selectedSeriesSourceName: String,
    selectedSeriesSourceBible: com.example.data.model.SeriesBible?,
    onSelectSeriesSource: (Long?) -> Unit
) {
    var isSeriesDropdownExpanded by remember { mutableStateOf(false) }
    val completedSeriesList = remember(historyList) { historyList.filter { it.isCompleted } }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(RekapinRadii.sm),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RekapinRadii.sm))
                .clickable { isSeriesDropdownExpanded = true }
                .padding(RekapinSpacing.sm)
                .testTag("dropdown_series_selector")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selectedSeriesSourceId != null) selectedSeriesSourceName else "Tanpa seri (video mandiri)",
                        style = RekapinTypography.bodyBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (selectedSeriesSourceId != null) "Character sheet dilanjutkan dari video ini" else "Character sheet dimulai dari nol",
                        style = RekapinTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (selectedSeriesSourceId != null) {
                        val bibleInfoText = if (selectedSeriesSourceBible != null && !selectedSeriesSourceBible.isEmpty()) {
                            "Memori seri: ${selectedSeriesSourceBible.locations.size} lokasi, ${selectedSeriesSourceBible.glossary.size} istilah"
                        } else {
                            "Belum ada memori seri (akan dibuat setelah analisis ini selesai)"
                        }
                        Text(
                            text = bibleInfoText,
                            style = RekapinTypography.captionMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Pilih Seri",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        DropdownMenu(
            expanded = isSeriesDropdownExpanded,
            onDismissRequest = { isSeriesDropdownExpanded = false },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(MaterialTheme.colorScheme.surface)
                .testTag("dropdown_series_menu")
        ) {
            val isNoneSelected = (selectedSeriesSourceId == null)
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tanpa seri (video mandiri)",
                                style = if (isNoneSelected) RekapinTypography.bodyBold else RekapinTypography.body,
                                color = if (isNoneSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Character sheet dimulai dari nol",
                                style = RekapinTypography.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isNoneSelected) {
                            Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Terpilih",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                onClick = {
                    onSelectSeriesSource(null)
                    isSeriesDropdownExpanded = false
                },
                modifier = Modifier.testTag("menu_item_series_none")
            )

            if (completedSeriesList.isNotEmpty()) {
                completedSeriesList.forEach { entity ->
                    val isSelected = (selectedSeriesSourceId == entity.id)
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entity.videoName,
                                        style = if (isSelected) RekapinTypography.bodyBold else RekapinTypography.body,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${entity.totalSegments} Segmen • ${entity.formattedDate}",
                                        style = RekapinTypography.caption,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Terpilih",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelectSeriesSource(entity.id)
                            isSeriesDropdownExpanded = false
                        },
                        modifier = Modifier.testTag("menu_item_series_${entity.id}")
                    )
                }
            } else {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Belum ada analisis selesai di riwayat",
                            style = RekapinTypography.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    enabled = false,
                    onClick = {}
                )
            }
        }
    }
}

@Composable
private fun ModelSelectorRow(
    models: List<String>,
    selectedModel: String,
    selectedModelUsage: ModelUsageInfo,
    onSelectModel: (String) -> Unit,
    onOpenManualQuota: (String) -> Unit
) {
    var isModelDropdownExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)) {
        // Status Quota Card
        Surface(
            shape = RoundedCornerShape(RekapinRadii.sm),
            color = if (selectedModelUsage.isQuotaExceeded) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_quota_today_status")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = RekapinSpacing.md, vertical = RekapinSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hari ini: ${selectedModelUsage.modelName} ${selectedModelUsage.used}/${selectedModelUsage.limit}",
                        style = RekapinTypography.captionBold,
                        color = if (selectedModelUsage.isQuotaExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Perkiraan reset: ${selectedModelUsage.resetTimeFormatted}",
                        style = RekapinTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { onOpenManualQuota(selectedModel) },
                    shape = RoundedCornerShape(RekapinRadii.sm),
                    contentPadding = PaddingValues(horizontal = RekapinSpacing.sm, vertical = 2.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("btn_manual_adjust_quota")
                ) {
                    Text(
                        text = "Atur manual",
                        style = RekapinTypography.captionBold
                    )
                }
            }
        }

        // Dropdown Button
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { isModelDropdownExpanded = true },
                shape = RoundedCornerShape(RekapinRadii.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("dropdown_model_button"),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selectedModel.ifBlank { "Pilih Model" },
                        style = RekapinTypography.bodyBold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Pilih Model",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            DropdownMenu(
                expanded = isModelDropdownExpanded,
                onDismissRequest = { isModelDropdownExpanded = false },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .testTag("dropdown_model_menu")
            ) {
                models.forEachIndexed { index, modelName ->
                    val isSelected = (modelName == selectedModel)
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = modelName,
                                    fontFamily = FontFamily.Monospace,
                                    style = if (isSelected) RekapinTypography.bodyBold else RekapinTypography.body,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Terpilih",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelectModel(modelName)
                            isModelDropdownExpanded = false
                        },
                        modifier = Modifier.testTag("menu_item_model_$index")
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanSummaryCard(
    selectedVideo: SelectedVideo?,
    segmentPlanSummary: SegmentPlanSummary?,
    promptCutMode: PromptCutMode,
    detectedSceneCount: Int?,
    selectedModelUsage: ModelUsageInfo
) {
    RekapinCard(
        shape = RoundedCornerShape(RekapinRadii.md),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("segment_plan_summary_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(RekapinSpacing.md),
            verticalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
        ) {
            SectionHeader(
                title = "Ringkasan Rencana",
                subtitle = if (selectedVideo != null) selectedVideo.displayName else "Pilih video untuk menghitung rencana"
            )

            if (selectedVideo != null && segmentPlanSummary != null) {
                val sceneCountText = if (promptCutMode == PromptCutMode.PER_SCENE) {
                    if (detectedSceneCount != null) "$detectedSceneCount adegan" else "Dihitung saat mulai"
                } else {
                    "±${segmentPlanSummary.estimatedScenesCount} adegan"
                }

                PlanSummaryLine(label = "Jumlah Segmen", value = "${segmentPlanSummary.segmentCount} Segmen (${segmentPlanSummary.segmentDurationLabel})")
                PlanSummaryLine(label = "Estimasi Permintaan AI", value = "≈ ${segmentPlanSummary.totalRequestsCount} panggilan")
                PlanSummaryLine(label = "Jumlah Adegan", value = sceneCountText)

                val needed = segmentPlanSummary.totalRequestsCount
                val remaining = selectedModelUsage.remaining
                val isInsufficient = remaining < needed

                if (isInsufficient) {
                    Spacer(modifier = Modifier.height(RekapinSpacing.xs))
                    Surface(
                        shape = RoundedCornerShape(RekapinRadii.sm),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("warning_quota_insufficient")
                    ) {
                        Row(
                            modifier = Modifier.padding(RekapinSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Peringatan Kuota",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                            Text(
                                text = "Sisa kuota model ${selectedModelUsage.modelName} ($remaining) kurang dari estimasi ($needed).",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = RekapinTypography.caption
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Silakan pilih video anime terlebih dahulu untuk melihat ringkasan durasi, pembagian segmen, dan perkiraan jumlah permintaan AI.",
                    style = RekapinTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlanSummaryLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = RekapinTypography.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = RekapinTypography.bodyBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}


@Composable
private fun ProgressContent(
    state: AnalysisState,
    activeKeyName: String,
    activeModelName: String,
    onCancel: () -> Unit,
    onSkipDetection: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(20.dp)
                )
                .testTag("analysis_progress_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Badge Key & Model Aktif
                val currentModelName = when (state) {
                    is AnalysisState.AnalyzingSegment -> state.modelName.ifBlank { activeModelName }
                    is AnalysisState.GeneratingNarration -> state.modelName.ifBlank { activeModelName }
                    else -> activeModelName
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (activeKeyName.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .testTag("badge_active_key_progress")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Key: $activeKeyName",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (currentModelName.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .testTag("badge_active_model_progress")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Model: $currentModelName",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Progress Indicator
                when (state) {
                    is AnalysisState.Uploading -> {
                        CircularProgressIndicator(
                            progress = { state.progressPercent / 100f },
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 6.dp,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Mengunggah Video ke Google AI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${state.progressPercent}% selesai",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val sentMb = state.bytesSent.toDouble() / (1024 * 1024)
                        val totalMb = state.totalBytes.toDouble() / (1024 * 1024)
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f MB / %.1f MB", sentMb, totalMb),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is AnalysisState.PollingFileStatus -> {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 5.dp,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Verifikasi Status Video",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.statusText,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            lineHeight = 16.sp
                        )
                    }

                    is AnalysisState.AnalyzingSegment -> {
                        val progressFraction = state.currentSegment.toFloat() / state.totalSegments.coerceAtLeast(1)
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Segmen ${state.currentSegment} dari ${state.totalSegments}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Rentang Waktu: ${state.timeRange}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }

                    is AnalysisState.GeneratingNarration -> {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 5.dp,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Menyusun Narasi Video",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.message,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is AnalysisState.Preparing -> {
                        if (state.progress != null) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape)
                                    .testTag("preparing_progress_bar")
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            val percent = (state.progress * 100).toInt()
                            Text(
                                text = "Mempersiapkan Analisis Video",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "$percent% selesai",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 5.dp,
                                modifier = Modifier
                                    .size(56.dp)
                                    .testTag("preparing_circular_indicator")
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Mempersiapkan Analisis",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                            modifier = Modifier.testTag("preparing_message_text")
                        )

                        if (onSkipDetection != null && (state.progress != null || state.message.contains("deteksi", ignoreCase = true))) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onSkipDetection,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("btn_skip_scene_detection")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Lewati deteksi, pakai interval tetap",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    else -> {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Mempersiapkan analisis...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("btn_cancel_analysis")
                ) {
                    Text("Batalkan Analisis", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ErrorContent(
    errorState: AnalysisState.Error,
    activeKeyName: String,
    onRetry: () -> Unit,
    onResume: (Long?) -> Unit,
    onFinishWithoutCurrentSegment: (Long?) -> Unit,
    onPickSameVideo: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchKey: (Long?) -> Unit,
    onCheckAgain: (Long?) -> Unit = {},
    onReupload: (Long?) -> Unit = {},
    onCancel: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val isTimeout = errorState.isFileProcessingTimeout
    val isFailed = errorState.isFileProcessingFailed
    val isAccessLost = errorState.isVideoAccessLost

    val headerIcon = when {
        isTimeout -> Icons.Default.Refresh
        isAccessLost -> Icons.Default.VideoFile
        else -> Icons.Default.ErrorOutline
    }
    val iconTint = when {
        isTimeout -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    val iconBg = when {
        isTimeout -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
    }
    val titleText = when {
        isTimeout -> "Video Masih Diproses di Server Google"
        isFailed -> "Pemrosesan Video Gagal"
        isAccessLost -> "Akses Video Terputus"
        else -> "Gagal Menganalisis Video"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = iconTint.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp)
                )
                .testTag("error_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = headerIcon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = titleText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Badges: Tahap Gagal & Kode HTTP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(iconBg)
                            .border(1.dp, iconTint.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (isTimeout) "Status: PROCESSING (15 Menit)" else "Tahap: ${errorState.stage}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = iconTint,
                            maxLines = 2
                        )
                    }

                    if (errorState.httpCode != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "HTTP ${errorState.httpCode}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Badge Nama Key & Model Aktif di Layar Error
                val displayKeyName = errorState.activeKeyName.ifBlank { activeKeyName }
                val displayModelName = errorState.modelName
                if (displayKeyName.isNotBlank() || displayModelName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (displayKeyName.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .testTag("badge_active_key_error")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Key: $displayKeyName",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (displayModelName.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .testTag("badge_active_model_error")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Model: $displayModelName",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Penjelasan Error dalam Bahasa Indonesia (tanpa stack trace)
                Text(
                    text = errorState.errorMessage,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp
                )

                // Jika akses video hilang, tampilkan pemilih berkas dan opsi selesaikan tanpa segmen
                if (errorState.isVideoAccessLost) {
                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = onPickSameVideo,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_pick_same_video")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoFile,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pilih Video yang Sama",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (errorState.canSkipAndFinish) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onFinishWithoutCurrentSegment(errorState.analysisId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_skip_and_finish_error")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Selesaikan tanpa segmen ini",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_error_back")
                    ) {
                        Text("Kembali ke Beranda", fontSize = 12.sp)
                    }

                } else {
                    // Tampilan Error Standar
                    Spacer(modifier = Modifier.height(14.dp))

                    // Detail Pesan Asli dari API / Server
                    if (errorState.rawApiMessage.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Detail Pesan Asli API / Server:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = errorState.rawApiMessage,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Tombol "Salin Detail Error"
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(errorState.getFullReport()))
                                isCopied = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("btn_copy_error_detail")
                        ) {
                            Icon(
                                imageVector = if (isCopied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isCopied) "Detail Error Berhasil Disalin!" else "Salin Detail Error",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Tampilkan hasil segmen yang sudah selesai jika ada
                    if (errorState.completedSegmentsCount > 0 || errorState.completedSummaries.isNotEmpty()) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(vertical = 4.dp)
                                .testTag("card_completed_segments")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Hasil Segmen Tersimpan (${errorState.completedSegmentsCount} dari ${errorState.totalSegments} Segmen)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Data analisis hingga segmen ${errorState.completedSegmentsCount} telah tersimpan aman di database.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (errorState.completedSummaries.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    errorState.completedSummaries.forEachIndexed { idx, summary ->
                                        Text(
                                            text = "• Segmen ${idx + 1}: $summary",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Fitur Lanjutkan jika ada segmen yang tersimpan atau kuota harian habis
                    val showResumeButton = errorState.isDailyQuota || errorState.canResume
                    if (showResumeButton) {
                        Button(
                            onClick = { onResume(errorState.analysisId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_resume_from_error")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (errorState.isDailyQuota) "Lanjutkan" else "Lanjutkan dari Segmen ${errorState.resumeSegment}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Tombol "Selesaikan tanpa segmen ini" jika ada segmen yang tersimpan
                    if (errorState.canSkipAndFinish) {
                        Button(
                            onClick = { onFinishWithoutCurrentSegment(errorState.analysisId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_skip_and_finish_error")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Selesaikan tanpa segmen ini",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Tombol Aksi Kuota Harian vs Timeout 15 Menit vs Failed vs Error Biasa
                    if (errorState.isDailyQuota) {
                        Button(
                            onClick = { onSwitchKey(errorState.analysisId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_switch_key_error")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Ganti Key",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = onOpenSettings,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_error_settings")
                        ) {
                            Text(
                                text = "Kelola API Key",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else if (errorState.isFileProcessingTimeout) {
                        Button(
                            onClick = { onCheckAgain(errorState.analysisId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_check_again")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cek lagi",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (errorState.isFileProcessingFailed) {
                        Button(
                            onClick = { onReupload(errorState.analysisId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_reupload_failed")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Unggah Ulang Video",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onOpenSettings,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("btn_error_settings")
                            ) {
                                Text(
                                    text = "API Key",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (errorState.canResume) MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.primary,
                                    contentColor = if (errorState.canResume) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("btn_error_retry")
                            ) {
                                Text(
                                    text = "Coba Lagi",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_error_back")
                    ) {
                        Text("Kembali ke Beranda", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
