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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    var showKeySelectionDialog by remember { mutableStateOf(false) }
    var pendingResumeAnalysisId by remember { mutableStateOf<Long?>(null) }
    var manualQuotaModelToEdit by remember { mutableStateOf<String?>(null) }

    val historyList by viewModel.historyList.collectAsState()
    val isHistoryOpen by viewModel.isHistoryOpen.collectAsState()
    val viewingHistoryItem by viewModel.viewingHistoryItem.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current

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
            onBack = { viewModel.closeHistoryDetail() },
            onResume = {
                requestNotificationAndStart {
                    viewModel.startAnalysis(historyEntity.id)
                }
                viewModel.closeHistory()
            },
            onFinishWithoutCurrentSegment = {
                viewModel.finishWithoutCurrentSegment(historyEntity.id)
                viewModel.closeHistoryDetail()
                viewModel.closeHistory()
            }
        )
        return
    }

    // Tampilkan Layar Riwayat jika sedang dibuka
    if (isHistoryOpen) {
        HistoryScreen(
            analyses = historyList,
            onBack = { viewModel.closeHistory() },
            onSelectAnalysis = { viewModel.openHistoryDetail(it) },
            onDeleteAnalysis = { viewModel.deleteHistoryItem(it) },
            onResumeAnalysis = { resumeId ->
                requestNotificationAndStart {
                    viewModel.startAnalysis(resumeId)
                }
                viewModel.closeHistory()
            },
            getMaxDurationForAnalysis = { viewModel.getMaxDurationForAnalysis(it) },
            failedSeriesBibleBuildIds = failedSeriesBibleBuildIds,
            seriesBibleBuildInProgressId = seriesBibleBuildInProgressId,
            onBuildSeriesBibleNow = { viewModel.buildSeriesBibleNow(it) },
            onFinishWithoutCurrentSegment = { resumeId ->
                viewModel.finishWithoutCurrentSegment(resumeId)
                viewModel.closeHistory()
            },
            onMergeNarrationsWithAi = { parts, onStatus ->
                viewModel.mergeNarrationsWithAi(parts, onStatus)
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
                actions = {
                    // Tombol Riwayat Analisis
                    IconButton(
                        onClick = { viewModel.openHistory() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_open_history")
                    ) {
                        if (historyList.isNotEmpty()) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text("${historyList.size}")
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Riwayat Analisis",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Riwayat Analisis",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Tombol Pengaturan API
                    IconButton(
                        onClick = { viewModel.openSettings() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Pengaturan",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
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
                onDismiss = { viewModel.closeSettings() },
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
                onTogglePrependRecapNarration = { viewModel.togglePrependRecapNarration(it) }
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollState = rememberScrollState()
    val pendingAnalysis = historyList.firstOrNull { !it.isCompleted && it.completedSegmentsCount > 0 }
    var showAdvancedAnalysisOptions by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = "RUANG KERJA",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.4.sp
            )
            Text(
                text = "Mulai rekap baru",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Pilih video, atur analisis, lalu siapkan cerita.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Banner Lanjutkan Analisis Tertunda jika ada yang belum selesai
        if (pendingAnalysis != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        RoundedCornerShape(16.dp)
                    )
                    .testTag("banner_resume_pending")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Lanjutkan Analisis Tertunda",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${pendingAnalysis.videoName} (Tersimpan Segmen ${pendingAnalysis.completedSegmentsCount} dari ${pendingAnalysis.totalSegments})",
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { onResumeAnalysis(pendingAnalysis.id) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
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
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Lanjutkan dari Segmen ${pendingAnalysis.completedSegmentsCount + 1}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { onFinishWithoutCurrentSegment(pendingAnalysis.id) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("btn_finish_without_segment_home")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Selesaikan tanpa segmen ini",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Warning jika API key belum diatur
        if (!hasApiKey) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSettings() }
                    .testTag("api_key_warning_banner")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "API Key Gemini Belum Diatur",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Ketuk untuk memasukkan API Key Gemini gratis Anda agar dapat memulai analisis.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // Section 1: Video Selector
        Text(
            text = "1. Berkas Video Anime",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (selectedVideo == null) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectVideoClicked() }
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .testTag("pick_video_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Pilih Berkas Video Anime",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Target pemakaian: Video anime (.mp4, .mkv, .webm)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onSelectVideoClicked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_select_video")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pilih Video dari Galeri", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            // Video Selected Info Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .testTag("video_info_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedVideo.displayName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${selectedVideo.sizeFormatted} • Durasi: ${selectedVideo.durationFormatted}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onClearVideo,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("clear_video_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Hapus video",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = onSelectVideoClicked,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("btn_change_video")
                        ) {
                            Text("Ganti Video", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Bagian Rentang Analisis (Hanya tampil setelah video dipilih dan durasi terbaca)
        if (selectedVideo != null && selectedVideo.durationMs > 0) {
            val totalVideoSeconds = (selectedVideo.durationMs / 1000).coerceAtLeast(1L)
            val currentRangeStart = analysisRangeStartSec.coerceIn(0L, totalVideoSeconds)
            val currentRangeEnd = if (analysisRangeEndSec <= 0L || analysisRangeEndSec > totalVideoSeconds) totalVideoSeconds else analysisRangeEndSec

            Text(
                text = "Rentang Analisis",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("analysis_range_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ContentCut,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Potong / Batasi Video",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Badge Rentang Aktif
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${SegmentCalculator.formatTimeSeconds(currentRangeEnd - currentRangeStart)} terpilih",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Analisis hanya sebagian video atau seluruhnya. Geser slider atau masukkan waktu manual (mm:ss atau h:mm:ss).",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Thumbnail frame preview saat slider digeser
                    var activeThumbTimeSec by remember { mutableStateOf<Long?>(currentRangeStart) }
                    var activeThumbLabel by remember { mutableStateOf("Awal") }
                    var thumbBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                    var isThumbLoading by remember { mutableStateOf(false) }

                    LaunchedEffect(activeThumbTimeSec) {
                        val sec = activeThumbTimeSec ?: return@LaunchedEffect
                        delay(200) // Jeda 200 ms agar ringan di HP RAM kecil
                        isThumbLoading = true
                        val bmp = VideoThumbnailHelper.getFrameThumbnail(context, selectedVideo.uri, sec)
                        thumbBitmap = bmp
                        isThumbLoading = false
                    }

                    // Tampilan Frame Thumbnail
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (thumbBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    Image(
                                        bitmap = thumbBitmap!!.asImageBitmap(),
                                        contentDescription = "Thumbnail frame posisi $activeThumbLabel",
                                        modifier = Modifier
                                            .width(160.dp)
                                            .height(90.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.75f))
                                            .padding(vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "$activeThumbLabel: ${SegmentCalculator.formatTimeSeconds(activeThumbTimeSec ?: 0L)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .width(160.dp)
                                        .height(90.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isThumbLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Movie,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Geser slider untuk pratinjau",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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

                    // Keterangan Waktu Rentang di bawah slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Awal: ${SegmentCalculator.formatTimeSeconds(currentRangeStart)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Durasi Video: ${SegmentCalculator.formatTimeSeconds(totalVideoSeconds)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Akhir: ${SegmentCalculator.formatTimeSeconds(currentRangeEnd)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Isian Waktu Manual (Awal & Akhir) yang tersinkron dengan slider
                    var startInputText by remember(currentRangeStart) {
                        mutableStateOf(SegmentCalculator.formatTimeSeconds(currentRangeStart))
                    }
                    var endInputText by remember(currentRangeEnd) {
                        mutableStateOf(SegmentCalculator.formatTimeSeconds(currentRangeEnd))
                    }
                    var validationErrorMsg by remember { mutableStateOf<String?>(null) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Input Waktu Awal
                        OutlinedTextField(
                            value = startInputText,
                            onValueChange = { input ->
                                startInputText = input
                                val parsedStart = SegmentCalculator.parseTimeSeconds(input)
                                val parsedEnd = SegmentCalculator.parseTimeSeconds(endInputText) ?: currentRangeEnd
                                if (parsedStart == null) {
                                    validationErrorMsg = "Format waktu awal tidak valid (gunakan mm:ss atau h:mm:ss)"
                                } else if (parsedStart < 0L) {
                                    validationErrorMsg = "Waktu awal tidak boleh kurang dari 0"
                                } else if (parsedEnd - parsedStart < SegmentCalculator.MIN_RANGE_SECONDS) {
                                    validationErrorMsg = "Selisih waktu akhir dan awal minimal 10 detik"
                                } else if (parsedEnd > totalVideoSeconds) {
                                    validationErrorMsg = "Waktu akhir melebihi durasi video (${SegmentCalculator.formatTimeSeconds(totalVideoSeconds)})"
                                } else {
                                    validationErrorMsg = null
                                    activeThumbTimeSec = parsedStart
                                    activeThumbLabel = "Awal"
                                    onRangeChanged(parsedStart, parsedEnd)
                                }
                            },
                            label = { Text("Waktu Awal", fontSize = 12.sp) },
                            placeholder = { Text("00:00", fontSize = 12.sp) },
                            singleLine = true,
                            isError = validationErrorMsg != null,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_range_start")
                        )

                        // Input Waktu Akhir
                        OutlinedTextField(
                            value = endInputText,
                            onValueChange = { input ->
                                endInputText = input
                                val parsedEnd = SegmentCalculator.parseTimeSeconds(input)
                                val parsedStart = SegmentCalculator.parseTimeSeconds(startInputText) ?: currentRangeStart
                                if (parsedEnd == null) {
                                    validationErrorMsg = "Format waktu akhir tidak valid (gunakan mm:ss atau h:mm:ss)"
                                } else if (parsedEnd > totalVideoSeconds) {
                                    validationErrorMsg = "Waktu akhir melebihi durasi video (${SegmentCalculator.formatTimeSeconds(totalVideoSeconds)})"
                                } else if (parsedEnd - parsedStart < SegmentCalculator.MIN_RANGE_SECONDS) {
                                    validationErrorMsg = "Selisih waktu akhir dan awal minimal 10 detik"
                                } else {
                                    validationErrorMsg = null
                                    activeThumbTimeSec = parsedEnd
                                    activeThumbLabel = "Akhir"
                                    onRangeChanged(parsedStart, parsedEnd)
                                }
                            },
                            label = { Text("Waktu Akhir", fontSize = 12.sp) },
                            placeholder = { Text(SegmentCalculator.formatTimeSeconds(totalVideoSeconds), fontSize = 12.sp) },
                            singleLine = true,
                            isError = validationErrorMsg != null,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_range_end")
                        )
                    }

                    // Pesan Validasi Error (jika ada)
                    if (validationErrorMsg != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = validationErrorMsg ?: "",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tombol Reset ke Seluruh Video
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
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("btn_reset_full_video")
                        ) {
                            Text("Pilih Seluruh Video", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Section 2: Pilihan Panjang Segmen
        Text(
            text = "2. Pilihan Panjang Segmen",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Option Otomatis (Default)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSegmentDurationChanged(SegmentDurationOption.AUTO) }
                        .background(
                            if (segmentDurationOption == SegmentDurationOption.AUTO) MaterialTheme.colorScheme.surfaceContainerHighest
                            else MaterialTheme.colorScheme.surface
                        )
                        .padding(12.dp)
                        .testTag("segment_auto_option"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (segmentDurationOption == SegmentDurationOption.AUTO),
                        onClick = { onSegmentDurationChanged(SegmentDurationOption.AUTO) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Otomatis",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Default",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Text(
                            text = "Durasi ≤4,5m = 1 segmen; ≤12m = 3 menit; >12m = 5 menit",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Option 3 Menit
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSegmentDurationChanged(SegmentDurationOption.THREE_MIN) }
                        .background(
                            if (segmentDurationOption == SegmentDurationOption.THREE_MIN) MaterialTheme.colorScheme.surfaceContainerHighest
                            else MaterialTheme.colorScheme.surface
                        )
                        .padding(12.dp)
                        .testTag("segment_3m_option"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (segmentDurationOption == SegmentDurationOption.THREE_MIN),
                        onClick = { onSegmentDurationChanged(SegmentDurationOption.THREE_MIN) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "3 Menit",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Segmen 3 menit per pemanggilan Gemini (standar terinci)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Option 5 Menit
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSegmentDurationChanged(SegmentDurationOption.FIVE_MIN) }
                        .background(
                            if (segmentDurationOption == SegmentDurationOption.FIVE_MIN) MaterialTheme.colorScheme.surfaceContainerHighest
                            else MaterialTheme.colorScheme.surface
                        )
                        .padding(12.dp)
                        .testTag("segment_5m_option"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (segmentDurationOption == SegmentDurationOption.FIVE_MIN),
                        onClick = { onSegmentDurationChanged(SegmentDurationOption.FIVE_MIN) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "5 Menit",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Segmen 5 menit per pemanggilan (mengurangi jumlah request)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Section 3: Pengaturan Interval Visual Prompt / Mode Potong Prompt
        Text(
            text = if (promptCutMode == PromptCutMode.PER_SCENE) "3. Mode Potong Prompt & Batas Durasi Adegan" else "3. Pilihan Interval Visual Prompt",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Pilihan Mode Potong Prompt
                Text(
                    text = "Mode Potong Prompt",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isFixed = (promptCutMode == PromptCutMode.FIXED_INTERVAL)
                    // Chip Interval Tetap
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isFixed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = BorderStroke(
                            1.dp,
                            if (isFixed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPromptCutModeChanged(PromptCutMode.FIXED_INTERVAL) }
                            .testTag("chip_cut_mode_fixed")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isFixed,
                                onClick = { onPromptCutModeChanged(PromptCutMode.FIXED_INTERVAL) },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = "Interval Tetap",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isFixed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Bawaan",
                                    fontSize = 10.sp,
                                    color = if (isFixed) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    val isScene = (promptCutMode == PromptCutMode.PER_SCENE)
                    // Chip Per Adegan
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isScene) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = BorderStroke(
                            1.dp,
                            if (isScene) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPromptCutModeChanged(PromptCutMode.PER_SCENE) }
                            .testTag("chip_cut_mode_scene")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isScene,
                                onClick = { onPromptCutModeChanged(PromptCutMode.PER_SCENE) },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = "Per Adegan",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isScene) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Deteksi lokal",
                                    fontSize = 10.sp,
                                    color = if (isScene) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (promptCutMode == PromptCutMode.PER_SCENE) {
                    // Helper text
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Adegan dideteksi otomatis dari pergantian gambar, tanpa AI dan tanpa kuota.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Batas maksimum durasi satu adegan:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // 1. Option 3 Detik
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onMaxSceneDurationChanged(3) }
                            .background(
                                if (maxSceneDurationSeconds == 3) MaterialTheme.colorScheme.surfaceContainerHighest
                                else MaterialTheme.colorScheme.surface
                            )
                            .padding(12.dp)
                            .testTag("interval_3s_option"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (maxSceneDurationSeconds == 3),
                            onClick = { onMaxSceneDurationChanged(3) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "3 Detik",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Adegan yang lebih panjang dari 3 detik dipecah per 3 detik",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. Option 5 Detik (Bawaan)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onMaxSceneDurationChanged(5) }
                            .background(
                                if (maxSceneDurationSeconds == 5) MaterialTheme.colorScheme.surfaceContainerHighest
                                else MaterialTheme.colorScheme.surface
                            )
                            .padding(12.dp)
                            .testTag("interval_5s_option"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (maxSceneDurationSeconds == 5),
                            onClick = { onMaxSceneDurationChanged(5) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "5 Detik",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Bawaan",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Text(
                                text = "Adegan yang lebih panjang dari 5 detik dipecah per 5 detik",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. Option 10 Detik
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onMaxSceneDurationChanged(10) }
                            .background(
                                if (maxSceneDurationSeconds == 10) MaterialTheme.colorScheme.surfaceContainerHighest
                                else MaterialTheme.colorScheme.surface
                            )
                            .padding(12.dp)
                            .testTag("radio_max_scene_10"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (maxSceneDurationSeconds == 10),
                            onClick = { onMaxSceneDurationChanged(10) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "10 Detik",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Adegan yang lebih panjang dari 10 detik dipecah per 10 detik",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 4. Option Tanpa Batas (0)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onMaxSceneDurationChanged(0) }
                            .background(
                                if (maxSceneDurationSeconds == 0) MaterialTheme.colorScheme.surfaceContainerHighest
                                else MaterialTheme.colorScheme.surface
                            )
                            .padding(12.dp)
                            .testTag("radio_max_scene_unlimited"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (maxSceneDurationSeconds == 0),
                            onClick = { onMaxSceneDurationChanged(0) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Tanpa batas",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Satu adegan = satu prompt, tidak dipecah lagi",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Option 10 Detik
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onIntervalChanged(10) }
                            .background(
                                if (promptInterval == 10) MaterialTheme.colorScheme.surfaceContainerHighest
                                else MaterialTheme.colorScheme.surface
                            )
                            .padding(12.dp)
                            .testTag("interval_10s_option"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (promptInterval == 10),
                            onClick = { onIntervalChanged(10) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "10 Detik",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Stabil & Cepat",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Text(
                                text = "Paling hemat kuota dan stabil untuk mencegah server Google sibuk (503)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Option 5 Detik (Default)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onIntervalChanged(5) }
                            .background(
                                if (promptInterval == 5) MaterialTheme.colorScheme.surfaceContainerHighest
                                else MaterialTheme.colorScheme.surface
                            )
                            .padding(12.dp)
                            .testTag("interval_5s_option"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (promptInterval == 5),
                            onClick = { onIntervalChanged(5) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "5 Detik",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Default",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Text(
                                text = "Rekomendasi optimal untuk video anime (hemat kuota & cepat)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Option 3 Detik
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onIntervalChanged(3) }
                            .background(
                                if (promptInterval == 3) MaterialTheme.colorScheme.surfaceContainerHighest
                                else MaterialTheme.colorScheme.surface
                            )
                            .padding(12.dp)
                            .testTag("interval_3s_option"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (promptInterval == 3),
                            onClick = { onIntervalChanged(3) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "3 Detik",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Detail adegan visual lebih rapat untuk aksi cepat dan animasi dinamis",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAdvancedAnalysisOptions = !showAdvancedAnalysisOptions }
                .testTag("advanced_analysis_options_toggle")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Opsi lanjutan",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Gaya visual dan pengaturan seri",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = if (showAdvancedAnalysisOptions) "Tutup opsi lanjutan" else "Buka opsi lanjutan",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(visible = showAdvancedAnalysisOptions) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Pilihan Gaya Visual Prompt
        Text(
            text = "Gaya Visual",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        var isStyleDropdownExpanded by remember { mutableStateOf(false) }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { isStyleDropdownExpanded = true }
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(12.dp)
                            .testTag("dropdown_style_selector"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = selectedStyle.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (selectedStyle.isBuiltIn) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.secondaryContainer
                                            )
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = if (selectedStyle.isBuiltIn) "Bawaan" else "Kustom",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selectedStyle.isBuiltIn) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = selectedStyle.promptDescription,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Pilih Gaya Visual",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    DropdownMenu(
                        expanded = isStyleDropdownExpanded,
                        onDismissRequest = { isStyleDropdownExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .background(MaterialTheme.colorScheme.surface)
                            .testTag("dropdown_style_menu")
                    ) {
                        visualStyles.forEachIndexed { index, style ->
                            val isSelected = style.id == selectedStyle.id
                            DropdownMenuItem(
                                text = {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = style.name,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                        if (style.isBuiltIn) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                        else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = if (style.isBuiltIn) "Bawaan" else "Kustom",
                                                    fontSize = 9.sp,
                                                    color = if (style.isBuiltIn) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Terpilih",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = style.promptDescription,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                onClick = {
                                    onSelectStyle(style)
                                    isStyleDropdownExpanded = false
                                },
                                modifier = Modifier.testTag("menu_item_style_$index")
                            )
                        }
                    }
                }
            }
        }

        // Pilihan Seri (Opsional)
        Text(
            text = "Seri (Opsional)",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        var isSeriesDropdownExpanded by remember { mutableStateOf(false) }
        val completedSeriesList = remember(historyList) {
            historyList.filter { it.isCompleted }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { isSeriesDropdownExpanded = true }
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(12.dp)
                            .testTag("dropdown_series_selector"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (selectedSeriesSourceId != null) selectedSeriesSourceName
                                    else "Tanpa seri (video mandiri)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (selectedSeriesSourceId != null) "Character sheet dilanjutkan dari video ini"
                                    else "Character sheet dimulai dari nol",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (selectedSeriesSourceId != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val bibleInfoText = if (selectedSeriesSourceBible != null && !selectedSeriesSourceBible.isEmpty()) {
                                        "Memori seri: ${selectedSeriesSourceBible.locations.size} lokasi, ${selectedSeriesSourceBible.glossary.size} istilah"
                                    } else {
                                        "Belum ada memori seri (akan dibuat setelah analisis ini selesai)"
                                    }
                                    Text(
                                        text = bibleInfoText,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Pilih Seri",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    DropdownMenu(
                        expanded = isSeriesDropdownExpanded,
                        onDismissRequest = { isSeriesDropdownExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .background(MaterialTheme.colorScheme.surface)
                            .testTag("dropdown_series_menu")
                    ) {
                        // Pilihan a: Tanpa seri
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
                                            fontWeight = if (isNoneSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp,
                                            color = if (isNoneSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Character sheet dimulai dari nol",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isNoneSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
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

                        // Pilihan b: Daftar analisis selesai
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
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 13.sp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${entity.totalSegments} Segmen • ${entity.formattedDate}",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(6.dp))
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
                            // Pilihan c: Jika belum ada analisis selesai
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Belum ada analisis selesai di riwayat",
                                        fontSize = 12.sp,
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
        }

            }
        }

        // Section 4: Ringkasan Rencana Analisis (Tampil sebelum tombol "Analisis" aktif)
        Text(
            text = "4. Ringkasan Rencana Analisis",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (selectedVideo != null) MaterialTheme.colorScheme.surfaceContainerLow
                else MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (selectedVideo != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(16.dp)
                )
                .testTag("segment_plan_summary_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (selectedVideo != null && segmentPlanSummary != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Animation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Rencana Segmen & Permintaan AI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 1. Durasi video
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Durasi Video",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = segmentPlanSummary.totalDurationFormatted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Rentang Analisis
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Rentang Analisis",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = segmentPlanSummary.rangeFormatted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 2. Panjang segmen terpilih
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Panjang Segmen Terpilih",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = segmentPlanSummary.segmentDurationLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 3. Jumlah segmen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Jumlah Segmen",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${segmentPlanSummary.segmentCount} Segmen",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 4. Perkiraan jumlah adegan / Jumlah adegan terdeteksi
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (promptCutMode == PromptCutMode.PER_SCENE) {
                            Text(
                                text = "Jumlah adegan terdeteksi",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val countLabel = if (detectedSceneCount != null) {
                                "$detectedSceneCount adegan"
                            } else {
                                "Dihitung saat mulai"
                            }
                            Text(
                                text = countLabel,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "Perkiraan Jumlah Adegan",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "±${segmentPlanSummary.estimatedScenesCount} adegan visual",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (promptCutMode == PromptCutMode.PER_SCENE) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Batas Durasi Adegan",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val maxDurLabel = if (maxSceneDurationSeconds == 0) "Tanpa batas" else "$maxSceneDurationSeconds detik"
                            Text(
                                text = maxDurLabel,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 5. Jumlah permintaan (segmen + 1 narasi)
                    val requestBreakdown = buildList {
                        add("${segmentPlanSummary.segmentCount} segmen")
                        if (narrationMode == com.example.data.model.NarrationMode.REKAP_VOICEOVER) add("1 narasi final")
                        if (isAutoBuildSeriesBible) add("1 Memori Seri")
                    }.joinToString(" + ")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Perkiraan Panggilan AI",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "≈ ${segmentPlanSummary.totalRequestsCount} ($requestBreakdown)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "Perkiraan ini tidak mencakup upload/polling video, retry, atau Gabung & Ekspor; kuota resmi ditentukan Google.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 6. Kebutuhan vs Sisa Kuota
                    val needed = segmentPlanSummary.totalRequestsCount
                    val remaining = selectedModelUsage.remaining
                    val isInsufficient = remaining < needed

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pemeriksaan Kuota (perkiraan)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Perlu sekitar $needed; hitungan lokal tersisa $remaining",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isInsufficient) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 7. Gaya Visual Terpilih
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Gaya Visual Prompt",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = selectedStyle.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (selectedSeriesSourceId != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Lanjutan Seri",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Melanjutkan seri dari: $selectedSeriesSourceName",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (isInsufficient) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("warning_quota_insufficient")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Peringatan Kuota",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Peringatan: Sisa kuota model ${selectedModelUsage.modelName} ($remaining) kurang dari yang diperlukan ($needed permintaan). Pertimbangkan beralih model atau menunggu reset kuota.",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Silakan pilih video anime terlebih dahulu untuk melihat ringkasan durasi, pembagian segmen, dan perkiraan jumlah permintaan AI.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Rentang Analisis",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Seluruh video (default)",
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Gaya Visual Aktif",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = selectedStyle.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Section 5: Ringkasan Alur Analisis
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Penyimpanan & Fitur Lanjutkan (Room Database):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Hasil tiap segmen otomatis tersimpan ke database lokal begitu selesai.\n" +
                            "• Jika gagal/terputus di tengah, Anda bisa melanjutkan tanpa mengulang dari awal.\n" +
                            "• Cek otomatis status berkas AI di Gemini (upload ulang otomatis jika kedaluwarsa).\n" +
                            "• Semua riwayat tersimpan permanen dan dapat dibuka kapan saja di menu Riwayat.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }

        // Section 6: Pilihan Model Gemini (Dropdown sebelum tombol Analisis)
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Model Gemini",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Model ini dipakai untuk seluruh proses analisis video dan narasi.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Card Status Penggunaan Hari Ini & Jam Reset
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedModelUsage.isQuotaExceeded) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_quota_today_status")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hari ini: ${selectedModelUsage.modelName} ${selectedModelUsage.used}/${selectedModelUsage.limit}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (selectedModelUsage.isQuotaExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Perkiraan reset: ${selectedModelUsage.resetTimeFormatted} (waktu HP)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { onOpenManualQuota(selectedModel) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("btn_manual_adjust_quota")
                        ) {
                            Text(
                                text = "Atur manual",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                var isModelDropdownExpanded by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { isModelDropdownExpanded = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("dropdown_model_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = selectedModel.ifBlank { "Pilih Model" },
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
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
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = modelName,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            fontWeight = if (modelName == selectedModel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (modelName == selectedModel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (modelName == selectedModel) {
                                            Spacer(modifier = Modifier.width(8.dp))
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

        Spacer(modifier = Modifier.height(8.dp))

        // Tombol Eksekusi "Mulai Analisis Video"
        Button(
            onClick = onStartAnalysis,
            enabled = (selectedVideo != null),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("btn_start_analysis")
        ) {
            Icon(
                imageVector = Icons.Default.Animation,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Mulai Analisis Video",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
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
