package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiApiClient
import com.example.data.api.GeminiApiException
import com.example.data.db.AnalysisJsonAdapter
import com.example.data.db.AnimeAnalysisEntity
import com.example.data.db.AppDatabase
import com.example.data.model.AnalysisState
import com.example.data.model.ApiKeyItem
import com.example.data.model.ModelUsageInfo
import com.example.data.model.NarrationLanguage
import com.example.data.model.NarrationMode
import com.example.data.model.PromptCutMode
import com.example.data.model.RequestPacingMode
import com.example.data.model.SelectedVideo
import com.example.data.model.SeriesBible
import com.example.data.model.ThinkingLevelMode
import com.example.data.model.TimelineItem
import com.example.data.model.VideoMatchPolicy
import com.example.data.model.VisualStyle
import com.example.data.prefs.ApiKeyStorage
import com.example.data.prefs.ContinuityWarningStorage
import com.example.data.prefs.ModelStorage
import com.example.data.prefs.ModelUsageStorage
import com.example.data.prefs.NarrationLanguageStorage
import com.example.data.prefs.NarrationModeStorage
import com.example.data.prefs.OpeningEndingStorage
import com.example.data.prefs.PromptCutModeStorage
import com.example.data.prefs.RepeatedSceneStorage
import com.example.data.prefs.RequestPacingStorage
import com.example.data.prefs.SeriesBibleStorage
import com.example.data.prefs.SeriesStorage
import com.example.data.prefs.StageTimingStorage
import com.example.data.prefs.ThinkingLevelStorage
import com.example.data.prefs.VisualStyleStorage
import com.example.data.repository.AnimeAnalysisRepository
import com.example.service.AnalysisForegroundService
import com.example.util.SceneDetector
import com.example.util.SceneTimeSpan
import com.example.util.SegmentCalculator
import com.example.util.SegmentDurationOption
import com.example.util.SegmentPlanSummary
import com.example.util.VideoMetadataHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RawSceneCacheKey(
    val videoUri: String,
    val startSec: Long,
    val endSec: Long
)

data class SceneCacheKey(
    val videoUri: String,
    val startSec: Long,
    val endSec: Long,
    val maxSceneDurationSec: Int
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        internal const val TAG = "RekapinVM"
    }

    data class ResumeCheckResult(
        val canResume: Boolean,
        val resumeSegment: Int,
        val canSkipAndFinish: Boolean
    )

    internal val context: Context get() = getApplication()
    val apiKeyStorage = ApiKeyStorage(context)
    val modelStorage = ModelStorage(context)
    val narrationLanguageStorage = NarrationLanguageStorage(context)
    val narrationModeStorage = NarrationModeStorage(context)
    val modelUsageStorage = ModelUsageStorage(context)
    val visualStyleStorage = VisualStyleStorage(context)
    val seriesStorage = SeriesStorage(context)
    val seriesBibleStorage = SeriesBibleStorage(context)
    internal val openingEndingStorage = OpeningEndingStorage(context)
    internal val continuityWarningStorage = ContinuityWarningStorage(context)
    internal val repeatedSceneStorage = RepeatedSceneStorage(context)
    val promptCutModeStorage = PromptCutModeStorage(context)
    val requestPacingStorage = RequestPacingStorage(context)
    val thinkingLevelStorage = ThinkingLevelStorage(context)
    internal val stageTimingStorage = StageTimingStorage(context)

    @Volatile
    internal var currentTimingAnalysisId: Long? = null

    @Volatile
    internal var latestPreScanDurationMs: Long = 0L

    private val _requestPacingMode = MutableStateFlow(requestPacingStorage.getMode())
    val requestPacingMode: StateFlow<RequestPacingMode> = _requestPacingMode.asStateFlow()

    fun setRequestPacingMode(mode: RequestPacingMode) {
        requestPacingStorage.setMode(mode)
        _requestPacingMode.value = mode
        Log.i(TAG, "Mode jeda antar-permintaan diubah: ${mode.label}")
    }

    private val _thinkingLevelMode = MutableStateFlow(thinkingLevelStorage.getMode())
    val thinkingLevelMode: StateFlow<ThinkingLevelMode> = _thinkingLevelMode.asStateFlow()

    fun setThinkingLevelMode(mode: ThinkingLevelMode) {
        thinkingLevelStorage.setMode(mode)
        _thinkingLevelMode.value = mode
        Log.i(TAG, "Tingkat berpikir diubah: ${mode.label}")
    }

    internal val _latestStageTimings = MutableStateFlow<Map<String, Long>?>(null)
    val latestStageTimings: StateFlow<Map<String, Long>?> = _latestStageTimings.asStateFlow()

    fun getStageTimingsForAnalysis(id: Long): Map<String, Long> = stageTimingStorage.getTimings(id)

    internal fun elapsedSinceNanosMs(startNanos: Long): Long =
        ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(0L)

    // Pengaturan Memori Seri (Series Bible)
    private val _isAutoBuildSeriesBible = MutableStateFlow(seriesBibleStorage.isAutoBuildEnabled())
    val isAutoBuildSeriesBible: StateFlow<Boolean> = _isAutoBuildSeriesBible.asStateFlow()

    internal val _failedSeriesBibleBuildIds = MutableStateFlow(seriesBibleStorage.getFailedBuildAnalysisIds())
    val failedSeriesBibleBuildIds: StateFlow<Set<Long>> = _failedSeriesBibleBuildIds.asStateFlow()
    private val _seriesBibleBuildFailureNotice = MutableStateFlow<Long?>(null)
    val seriesBibleBuildFailureNotice: StateFlow<Long?> = _seriesBibleBuildFailureNotice.asStateFlow()
    private val _seriesBibleBuildInProgressId = MutableStateFlow<Long?>(null)
    val seriesBibleBuildInProgressId: StateFlow<Long?> = _seriesBibleBuildInProgressId.asStateFlow()

    fun dismissSeriesBibleBuildFailureNotice() {
        _seriesBibleBuildFailureNotice.value = null
    }

    internal fun recordSeriesBibleBuildFailure(analysisId: Long) {
        seriesBibleStorage.markBuildFailed(analysisId)
        _failedSeriesBibleBuildIds.value = _failedSeriesBibleBuildIds.value + analysisId
        _seriesBibleBuildFailureNotice.value = analysisId
    }

    fun buildSeriesBibleNow(analysisId: Long) {
        if (_seriesBibleBuildInProgressId.value != null) return
        viewModelScope.launch {
            _seriesBibleBuildInProgressId.value = analysisId
            val bibleStartNanos = System.nanoTime()
            val prevTimingId = currentTimingAnalysisId
            currentTimingAnalysisId = analysisId
            try {
                val entity = repository.getAnalysisById(analysisId)
                if (entity == null || !entity.isCompleted) {
                    recordSeriesBibleBuildFailure(analysisId)
                    return@launch
                }
                val result = geminiClient.updateSeriesBible(
                    previousBible = seriesBibleStorage.getBible(analysisId),
                    segmentSummaries = AnalysisJsonAdapter.jsonToSummaries(entity.segmentSummariesJson),
                    characterSheet = AnalysisJsonAdapter.jsonToCharacterSheet(entity.characterSheetJson),
                    videoName = entity.videoName,
                    model = modelStorage.getSelectedModel()
                )
                stageTimingStorage.addStageDuration(
                    analysisId,
                    StageTimingStorage.STAGE_SERIES_BIBLE,
                    elapsedSinceNanosMs(bibleStartNanos)
                )
                if (result != null && !result.isEmpty()) {
                    seriesBibleStorage.saveBible(analysisId, result)
                    _failedSeriesBibleBuildIds.value = _failedSeriesBibleBuildIds.value - analysisId
                    if (_seriesBibleBuildFailureNotice.value == analysisId) {
                        _seriesBibleBuildFailureNotice.value = null
                    }
                    val currentState = _analysisState.value
                    if (currentState is AnalysisState.Success && viewingHistoryItem.value?.id == analysisId) {
                        _analysisState.value = currentState.copy(result = currentState.result.copy(seriesBible = result))
                        _latestStageTimings.value = stageTimingStorage.getTimings(analysisId)
                    }
                } else {
                    recordSeriesBibleBuildFailure(analysisId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Gagal membangun ulang Memori Seri untuk analisis $analysisId (${e.javaClass.simpleName}).")
                recordSeriesBibleBuildFailure(analysisId)
            } finally {
                if (currentTimingAnalysisId == analysisId) {
                    currentTimingAnalysisId = prevTimingId
                }
                _seriesBibleBuildInProgressId.value = null
            }
        }
    }

    fun toggleAutoBuildSeriesBible(enabled: Boolean) {
        seriesBibleStorage.setAutoBuildEnabled(enabled)
        _isAutoBuildSeriesBible.value = enabled
        updatePlanSummary()
        Log.i(TAG, "Pengaturan bangun memori seri otomatis diubah: $enabled")
    }

    private val _isPrependRecapNarration = MutableStateFlow(seriesBibleStorage.isPrependRecapEnabled())
    val isPrependRecapNarration: StateFlow<Boolean> = _isPrependRecapNarration.asStateFlow()

    fun togglePrependRecapNarration(enabled: Boolean) {
        seriesBibleStorage.setPrependRecapEnabled(enabled)
        _isPrependRecapNarration.value = enabled
        Log.i(TAG, "Pengaturan recap 'Sebelumnya...' di awal narasi diubah: $enabled")
    }

    // Pengaturan Lewati OP/ED Otomatis
    internal val _isAutoSkipOpEd = MutableStateFlow(openingEndingStorage.isAutoSkipOpEd())
    val isAutoSkipOpEd: StateFlow<Boolean> = _isAutoSkipOpEd.asStateFlow()

    fun toggleAutoSkipOpEd(enabled: Boolean) {
        openingEndingStorage.setAutoSkipOpEd(enabled)
        _isAutoSkipOpEd.value = enabled
        Log.i(TAG, "Pengaturan lewati OP/ED otomatis diubah: $enabled")
    }

    // Pengaturan Cari Adegan Berulang
    internal val _isDetectRepeatedScenes = MutableStateFlow(repeatedSceneStorage.isDetectRepeatedScenes())
    val isDetectRepeatedScenes: StateFlow<Boolean> = _isDetectRepeatedScenes.asStateFlow()

    fun toggleDetectRepeatedScenes(enabled: Boolean) {
        repeatedSceneStorage.setDetectRepeatedScenes(enabled)
        _isDetectRepeatedScenes.value = enabled
        Log.i(TAG, "Pengaturan cari adegan berulang diubah: $enabled")
    }

    // Status Dekripsi API Key Gagal
    private val _showKeyDecryptFailedDialog = MutableStateFlow(false)
    val showKeyDecryptFailedDialog: StateFlow<Boolean> = _showKeyDecryptFailedDialog.asStateFlow()

    fun dismissKeyDecryptFailedDialog() {
        _showKeyDecryptFailedDialog.value = false
    }

    fun checkKeyDecryptionState() {
        val keys = apiKeyStorage.getAllKeys()
        if (apiKeyStorage.keyDecryptFailed && keys.isEmpty()) {
            _showKeyDecryptFailedDialog.value = true
        }
    }

    init {
        checkKeyDecryptionState()
    }

    fun getSeriesBibleForAnalysis(id: Long): SeriesBible? = seriesBibleStorage.getBible(id)
    fun getContinuityWarningsForAnalysis(id: Long): List<String> = continuityWarningStorage.get(id)
    fun getRepeatedSceneNotesForAnalysis(id: Long): List<String> = repeatedSceneStorage.get(id)
    fun getOpeningEndingSkipNoteForAnalysis(id: Long): String? = openingEndingStorage.getSkipNote(id)

    internal val geminiClient = GeminiApiClient(
        apiKeyProvider = { apiKeyStorage.getEffectiveApiKey() },
        onRequestCounted = { modelName ->
            modelUsageStorage.incrementUsage(modelName)
            refreshSelectedModelUsage()
        },
        onDailyQuotaExceeded = { modelName, retryDelaySeconds ->
            modelUsageStorage.markQuotaExceeded(modelName, retryDelaySeconds)
            refreshSelectedModelUsage()
        },
        pacingModeProvider = { requestPacingStorage.getMode() },
        onPacingWait = { waitedMs ->
            currentTimingAnalysisId?.let { id ->
                stageTimingStorage.addStageDuration(id, StageTimingStorage.STAGE_PACING_WAIT, waitedMs)
            }
        },
        thinkingLevelProvider = { thinkingLevelStorage.getMode().apiValue }
    )

    private val database = AppDatabase.getDatabase(application)
    val repository = AnimeAnalysisRepository(database.animeAnalysisDao())

    val historyList: StateFlow<List<AnimeAnalysisEntity>> = repository.allAnalyses.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    internal val _selectedVideo = MutableStateFlow<SelectedVideo?>(null)
    val selectedVideo: StateFlow<SelectedVideo?> = _selectedVideo.asStateFlow()

    internal val _promptCutMode = MutableStateFlow(promptCutModeStorage.getMode())
    val promptCutMode: StateFlow<PromptCutMode> = _promptCutMode.asStateFlow()

    internal val _maxSceneDurationSeconds = MutableStateFlow(promptCutModeStorage.getMaxSceneDuration())
    val maxSceneDurationSeconds: StateFlow<Int> = _maxSceneDurationSeconds.asStateFlow()

    internal val _detectedSceneCount = MutableStateFlow<Int?>(null)
    val detectedSceneCount: StateFlow<Int?> = _detectedSceneCount.asStateFlow()

    internal var cachedRawCutPoints: List<Long>? = null
    internal var cachedRawKey: RawSceneCacheKey? = null
    internal var cachedSceneSpans: List<SceneTimeSpan>? = null
    internal var cachedSceneKey: SceneCacheKey? = null

    internal var activeDetectionJob: Job? = null
    internal var isDetectionSkipped: Boolean = false

    internal val _promptIntervalSeconds = MutableStateFlow(5)
    val promptIntervalSeconds: StateFlow<Int> = _promptIntervalSeconds.asStateFlow()

    internal val _segmentDurationOption = MutableStateFlow(SegmentDurationOption.AUTO)
    val segmentDurationOption: StateFlow<SegmentDurationOption> = _segmentDurationOption.asStateFlow()

    private val _segmentPlanSummary = MutableStateFlow<SegmentPlanSummary?>(null)
    val segmentPlanSummary: StateFlow<SegmentPlanSummary?> = _segmentPlanSummary.asStateFlow()

    internal val _selectedSeriesSourceId = MutableStateFlow<Long?>(null)
    val selectedSeriesSourceId: StateFlow<Long?> = _selectedSeriesSourceId.asStateFlow()

    val selectedSeriesSourceName: StateFlow<String> = combine(
        _selectedSeriesSourceId,
        historyList
    ) { sourceId, history ->
        if (sourceId != null) {
            history.find { it.id == sourceId }?.videoName.orEmpty()
        } else {
            ""
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    fun selectSeriesSource(sourceAnalysisId: Long?) {
        _selectedSeriesSourceId.value = sourceAnalysisId
        Log.i(TAG, "Pilihan seri sumber diubah ke ID: $sourceAnalysisId")
    }

    data class PendingMatchDialogState(
        val entity: AnimeAnalysisEntity,
        val newVideo: SelectedVideo
    )

    private val _pendingMatchDialogState = MutableStateFlow<PendingMatchDialogState?>(null)
    val pendingMatchDialogState: StateFlow<PendingMatchDialogState?> = _pendingMatchDialogState.asStateFlow()

    internal val _analysisState = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState: StateFlow<AnalysisState> = _analysisState.asStateFlow()

    internal val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _apiKeys = MutableStateFlow<List<ApiKeyItem>>(apiKeyStorage.getAllKeys())
    val apiKeys: StateFlow<List<ApiKeyItem>> = _apiKeys.asStateFlow()

    private val _activeKeyName = MutableStateFlow(apiKeyStorage.getActiveKeyName())
    val activeKeyName: StateFlow<String> = _activeKeyName.asStateFlow()

    private val _models = MutableStateFlow<List<String>>(modelStorage.getModels())
    val models: StateFlow<List<String>> = _models.asStateFlow()

    private val _selectedModel = MutableStateFlow(modelStorage.getSelectedModel())
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _isAutoSwitchModel = MutableStateFlow(modelStorage.isAutoSwitchOnQuota())
    val isAutoSwitchModel: StateFlow<Boolean> = _isAutoSwitchModel.asStateFlow()

    private val _narrationLanguage = MutableStateFlow(narrationLanguageStorage.getLanguage())
    val narrationLanguage: StateFlow<NarrationLanguage> = _narrationLanguage.asStateFlow()

    internal val _narrationMode = MutableStateFlow(narrationModeStorage.getMode())
    val narrationMode: StateFlow<NarrationMode> = _narrationMode.asStateFlow()

    private val _selectedModelUsage = MutableStateFlow(modelUsageStorage.getUsageInfo(modelStorage.getSelectedModel()))
    val selectedModelUsage: StateFlow<ModelUsageInfo> = _selectedModelUsage.asStateFlow()

    fun refreshSelectedModelUsage() {
        _selectedModelUsage.value = modelUsageStorage.getUsageInfo(modelStorage.getSelectedModel())
    }

    private val _visualStyles = MutableStateFlow<List<VisualStyle>>(visualStyleStorage.getAllStyles())
    val visualStyles: StateFlow<List<VisualStyle>> = _visualStyles.asStateFlow()

    private val _selectedStyle = MutableStateFlow<VisualStyle>(visualStyleStorage.getSelectedStyle())
    val selectedStyle: StateFlow<VisualStyle> = _selectedStyle.asStateFlow()

    fun selectStyle(style: VisualStyle) {
        visualStyleStorage.setSelectedStyleId(style.id)
        _selectedStyle.value = visualStyleStorage.getSelectedStyle()
    }

    fun addCustomStyle(name: String, promptDescription: String): VisualStyle {
        val created = visualStyleStorage.addCustomStyle(name, promptDescription)
        refreshVisualStyles()
        selectStyle(created)
        return created
    }

    fun updateCustomStyle(id: String, name: String, promptDescription: String): Boolean {
        val ok = visualStyleStorage.updateCustomStyle(id, name, promptDescription)
        refreshVisualStyles()
        _selectedStyle.value = visualStyleStorage.getSelectedStyle()
        return ok
    }

    fun deleteCustomStyle(id: String): Boolean {
        val ok = visualStyleStorage.deleteCustomStyle(id)
        refreshVisualStyles()
        _selectedStyle.value = visualStyleStorage.getSelectedStyle()
        return ok
    }

    fun refreshVisualStyles() {
        _visualStyles.value = visualStyleStorage.getAllStyles()
    }

    fun getStyleForAnalysis(analysisId: Long): VisualStyle {
        return visualStyleStorage.getStyleForAnalysis(analysisId) ?: VisualStyleStorage.DEFAULT_STYLE
    }

    fun getStyleNameForAnalysis(analysisId: Long): String {
        return getStyleForAnalysis(analysisId).name
    }

    fun getMaxDurationForAnalysis(analysisId: Long): Int {
        return promptCutModeStorage.getMaxDurationForAnalysis(analysisId)
    }

    fun skipSceneDetection() {
        isDetectionSkipped = true
        preScanJob?.cancel()
        activeDetectionJob?.cancel()
        Log.i(TAG, "Pengguna meminta lewati deteksi adegan, beralih ke splitSpansFallback")
    }

    private val _isHistoryOpen = MutableStateFlow(false)
    val isHistoryOpen: StateFlow<Boolean> = _isHistoryOpen.asStateFlow()

    private val _viewingHistoryItem = MutableStateFlow<AnimeAnalysisEntity?>(null)
    val viewingHistoryItem: StateFlow<AnimeAnalysisEntity?> = _viewingHistoryItem.asStateFlow()

    @Volatile
    internal var activeAnalysisId: Long? = null
    internal var currentJob: Job? = null
    @Volatile
    internal var runGeneration: Long = 0L

    internal fun isCurrentRun(runId: Long): Boolean = runId == runGeneration

    internal val _analysisRangeStartSec = MutableStateFlow(0L)
    val analysisRangeStartSec: StateFlow<Long> = _analysisRangeStartSec.asStateFlow()

    internal val _analysisRangeEndSec = MutableStateFlow(0L)
    val analysisRangeEndSec: StateFlow<Long> = _analysisRangeEndSec.asStateFlow()

    fun setAnalysisRange(startSec: Long, endSec: Long): Boolean {
        val video = _selectedVideo.value ?: return false
        val totalSeconds = if (video.durationMs > 0) video.durationMs / 1000 else 1500L
        val safeStart = startSec.coerceIn(0L, totalSeconds)
        val safeEnd = endSec.coerceIn(0L, totalSeconds)

        if (safeEnd - safeStart < SegmentCalculator.MIN_RANGE_SECONDS) {
            Log.w(TAG, "Rentang tidak valid: selisih waktu harus minimal 10 detik ($safeStart to $safeEnd)")
            return false
        }

        _analysisRangeStartSec.value = safeStart
        _analysisRangeEndSec.value = safeEnd
        Log.i(TAG, "Rentang analisis diatur: ${SegmentCalculator.formatTimeSeconds(safeStart)} - ${SegmentCalculator.formatTimeSeconds(safeEnd)} dari ${SegmentCalculator.formatTimeSeconds(totalSeconds)}")
        cachedRawCutPoints = null
        cachedRawKey = null
        cachedSceneSpans = null
        cachedSceneKey = null
        _detectedSceneCount.value = null
        if (_promptCutMode.value == PromptCutMode.PER_SCENE) {
            triggerSceneDetectionIfNeeded()
        }
        updatePlanSummary()
        return true
    }

    fun setIntervalSeconds(seconds: Int) {
        if (seconds == 3 || seconds == 5 || seconds == 10) {
            _promptIntervalSeconds.value = seconds
            Log.d(TAG, "Interval visual prompt diubah menjadi: $seconds detik")
            updatePlanSummary()
        }
    }

    internal var preScanJob: Job? = null

    fun triggerSceneDetectionIfNeeded() {
        val video = _selectedVideo.value ?: return
        if (_promptCutMode.value != PromptCutMode.PER_SCENE) return
        val totalSeconds = if (video.durationMs > 0) video.durationMs / 1000 else 1500L
        val safeStart = _analysisRangeStartSec.value.coerceIn(0L, totalSeconds)
        val currentEnd = _analysisRangeEndSec.value
        val safeEnd = if (currentEnd <= 0L || currentEnd > totalSeconds) totalSeconds else currentEnd
        val maxDur = _maxSceneDurationSeconds.value
        val requiredRawKey = RawSceneCacheKey(video.uri.toString(), safeStart, safeEnd)
        val requiredKey = SceneCacheKey(video.uri.toString(), safeStart, safeEnd, maxDur)

        if (cachedRawCutPoints != null && cachedRawKey == requiredRawKey) {
            val finalSpans = SceneDetector.buildFinalSpans(
                rawCutPoints = cachedRawCutPoints!!,
                startSec = safeStart,
                endSec = safeEnd,
                maxDuration = maxDur
            )
            cachedSceneSpans = finalSpans
            cachedSceneKey = requiredKey
            _detectedSceneCount.value = finalSpans.size
            Log.d(TAG, "Spans adegan dihitung instan dari cachedRawCutPoints: ${finalSpans.size} adegan (maxDur=$maxDur)")
            return
        }

        preScanJob?.cancel()
        latestPreScanDurationMs = 0L
        preScanJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(350L)
            val preScanStartNanos = System.nanoTime()
            try {
                Log.d(TAG, "Memulai pre-scan cutPoints adegan di background: start=$safeStart, end=$safeEnd")
                val rawPoints = SceneDetector.detectRawCutPoints(
                    context = getApplication(),
                    videoUri = video.uri,
                    startSec = safeStart,
                    endSec = safeEnd
                )
                if (rawPoints != null) {
                    cachedRawCutPoints = rawPoints
                    cachedRawKey = requiredRawKey
                    latestPreScanDurationMs = elapsedSinceNanosMs(preScanStartNanos)
                    val finalSpans = SceneDetector.buildFinalSpans(
                        rawCutPoints = rawPoints,
                        startSec = safeStart,
                        endSec = safeEnd,
                        maxDuration = maxDur
                    )
                    cachedSceneSpans = finalSpans
                    cachedSceneKey = requiredKey
                    _detectedSceneCount.value = finalSpans.size
                    Log.d(TAG, "Pre-scan adegan selesai: ${rawPoints.size} cut points, ${finalSpans.size} final adegan")
                }
            } catch (e: CancellationException) {
                // Ignore cancellation
            } catch (e: Exception) {
                Log.w(TAG, "Pre-scan adegan gagal: ${e.message}")
            }
        }
    }

    fun setPromptCutMode(mode: PromptCutMode) {
        _promptCutMode.value = mode
        promptCutModeStorage.setMode(mode)
        Log.i(TAG, "Mode potong prompt diubah ke: ${mode.displayName}")
        if (mode == PromptCutMode.PER_SCENE) {
            _detectedSceneCount.value = cachedSceneSpans?.size
            triggerSceneDetectionIfNeeded()
        }
        updatePlanSummary()
    }

    fun setMaxSceneDuration(seconds: Int) {
        if (seconds == 0 || seconds == 3 || seconds == 5 || seconds == 10) {
            _maxSceneDurationSeconds.value = seconds
            promptCutModeStorage.setMaxSceneDuration(seconds)
            Log.i(TAG, "Batas maksimum durasi adegan diubah ke: ${if (seconds == 0) "Tanpa batas" else "$seconds detik"}")

            val video = _selectedVideo.value
            val totalSeconds = if (video != null && video.durationMs > 0) video.durationMs / 1000 else 1500L
            val safeStart = _analysisRangeStartSec.value.coerceIn(0L, totalSeconds)
            val currentEnd = _analysisRangeEndSec.value
            val safeEnd = if (currentEnd <= 0L || currentEnd > totalSeconds) totalSeconds else currentEnd
            val requiredRawKey = if (video != null) RawSceneCacheKey(video.uri.toString(), safeStart, safeEnd) else null

            if (cachedRawCutPoints != null && cachedRawKey != null && cachedRawKey == requiredRawKey) {
                val finalSpans = SceneDetector.buildFinalSpans(
                    rawCutPoints = cachedRawCutPoints!!,
                    startSec = safeStart,
                    endSec = safeEnd,
                    maxDuration = seconds
                )
                cachedSceneSpans = finalSpans
                cachedSceneKey = SceneCacheKey(cachedRawKey!!.videoUri, safeStart, safeEnd, seconds)
                _detectedSceneCount.value = finalSpans.size
                Log.i(TAG, "Spans adegan diperbarui instan dari raw cutPoints: ${finalSpans.size} adegan (maxDur=$seconds)")
            } else {
                cachedSceneSpans = null
                cachedSceneKey = null
                _detectedSceneCount.value = null
                if (_promptCutMode.value == PromptCutMode.PER_SCENE) {
                    triggerSceneDetectionIfNeeded()
                }
            }
            updatePlanSummary()
        }
    }

    fun setSegmentDurationOption(option: SegmentDurationOption) {
        _segmentDurationOption.value = option
        Log.d(TAG, "Panjang segmen diubah menjadi: ${option.label}")
        updatePlanSummary()
    }

    private fun updatePlanSummary() {
        val video = _selectedVideo.value
        if (video != null) {
            val totalSeconds = if (video.durationMs > 0) video.durationMs / 1000 else 1500L
            val safeStart = _analysisRangeStartSec.value.coerceIn(0L, totalSeconds)
            val currentEnd = _analysisRangeEndSec.value
            val safeEnd = if (currentEnd <= 0L || currentEnd > totalSeconds) totalSeconds else currentEnd
            val plan = SegmentCalculator.calculatePlanSummary(
                totalSeconds = totalSeconds,
                option = _segmentDurationOption.value,
                intervalSeconds = _promptIntervalSeconds.value,
                rangeStartSec = safeStart,
                rangeEndSec = safeEnd
            )
            _segmentPlanSummary.value = plan.copy(
                totalRequestsCount = SegmentCalculator.estimateGeminiRequestCount(
                    segmentCount = plan.segmentCount,
                    includesFinalNarration = _narrationMode.value == NarrationMode.REKAP_VOICEOVER,
                    includesSeriesBibleUpdate = _isAutoBuildSeriesBible.value
                )
            )
        } else {
            _segmentPlanSummary.value = null
        }
    }

    fun openSettings() {
        _isSettingsOpen.value = true
        Log.d(TAG, "Membuka dialog pengaturan API Key")
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    fun openHistory() {
        _isHistoryOpen.value = true
        Log.d(TAG, "Membuka layar riwayat analisis")
    }

    fun closeHistory() {
        _isHistoryOpen.value = false
        _viewingHistoryItem.value = null
    }

    fun openHistoryDetail(entity: AnimeAnalysisEntity) {
        _viewingHistoryItem.value = entity
    }

    fun closeHistoryDetail() {
        _viewingHistoryItem.value = null
    }

    fun saveEditedResult(analysisId: Long?, editedResult: com.example.data.model.AnimeAnalysisResult) {
        val currentState = _analysisState.value
        if (currentState is AnalysisState.Success && (analysisId == null || analysisId == activeAnalysisId)) {
            _analysisState.value = currentState.copy(result = editedResult)
        }
        val targetId = analysisId ?: activeAnalysisId ?: return
        viewModelScope.launch {
            val entity = repository.getAnalysisById(targetId) ?: return@launch
            val updatedEntity = entity.copy(
                narration = editedResult.narration,
                characterSheetJson = AnalysisJsonAdapter.characterSheetToJson(editedResult.characterSheet),
                timelineJson = AnalysisJsonAdapter.timelineToJson(editedResult.timeline),
                segmentSummariesJson = AnalysisJsonAdapter.summariesToJson(editedResult.segmentSummaries),
                updatedAt = System.currentTimeMillis()
            )
            repository.update(updatedEntity)
            if (_viewingHistoryItem.value?.id == targetId) {
                _viewingHistoryItem.value = updatedEntity
            }
            Log.i(TAG, "Perubahan hasil analisis ID $targetId disimpan ke database")
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
            seriesStorage.removeSourceAnalysisId(id)
            seriesBibleStorage.removeBible(id)
            continuityWarningStorage.remove(id)
            repeatedSceneStorage.remove(id)
            openingEndingStorage.remove(id)
            visualStyleStorage.removeStyleForAnalysis(id)
            narrationModeStorage.removeModeForAnalysis(id)
            promptCutModeStorage.removeModeForAnalysis(id)
            stageTimingStorage.remove(id)
            _failedSeriesBibleBuildIds.value = _failedSeriesBibleBuildIds.value - id
            if (_viewingHistoryItem.value?.id == id) {
                _viewingHistoryItem.value = null
            }
            if (activeAnalysisId == id) {
                activeAnalysisId = null
            }
            Log.i(TAG, "Riwayat analisis ID $id dihapus dari database dan memori seri")
        }
    }

    fun refreshApiKeys() {
        _apiKeys.value = apiKeyStorage.getAllKeys()
        _activeKeyName.value = apiKeyStorage.getActiveKeyName()
        if (_apiKeys.value.isNotEmpty()) {
            _showKeyDecryptFailedDialog.value = false
        }
    }

    fun addApiKey(name: String, key: String) {
        apiKeyStorage.addKey(name, key)
        _showKeyDecryptFailedDialog.value = false
        refreshApiKeys()
        Log.i(TAG, "API Key baru '$name' ditambahkan")
    }

    fun renameApiKey(id: String, newName: String) {
        apiKeyStorage.renameKey(id, newName)
        refreshApiKeys()
        Log.i(TAG, "API Key ID $id diganti nama menjadi '$newName'")
    }

    fun deleteApiKey(id: String) {
        apiKeyStorage.deleteKey(id)
        refreshApiKeys()
        Log.i(TAG, "API Key ID $id dihapus")
    }

    fun setActiveApiKey(id: String) {
        apiKeyStorage.setActiveKey(id)
        refreshApiKeys()
        Log.i(TAG, "Key aktif diubah menjadi: ${_activeKeyName.value}")
    }

    fun switchKeyAndResume(keyId: String, analysisId: Long?) {
        setActiveApiKey(keyId)
        Log.i(TAG, "Beralih ke key ${_activeKeyName.value} dan langsung melanjutkan analisis ID $analysisId")
        startAnalysis(resumeId = analysisId, forceReupload = false)
    }

    fun addKeyAndResume(name: String, key: String, analysisId: Long?) {
        val newKey = apiKeyStorage.addKey(name, key)
        setActiveApiKey(newKey.id)
        Log.i(TAG, "Menambahkan key baru '${newKey.name}' dan langsung melanjutkan analisis ID $analysisId")
        startAnalysis(resumeId = analysisId, forceReupload = false)
    }

    fun saveApiKey(newKey: String) {
        apiKeyStorage.saveApiKey(newKey)
        refreshApiKeys()
        Log.i(TAG, "API Key baru disimpan (panjang: ${newKey.length} karakter)")
        closeSettings()
    }

    suspend fun testApiKey(key: String): Pair<Boolean, String> {
        val result = geminiClient.testApiKey(key, _selectedModel.value)
        return Pair(result.isSuccess, result.message)
    }

    suspend fun testApiKey(
        key: String,
        onProgress: (String) -> Unit
    ): Pair<Boolean, String> {
        val result = geminiClient.testApiKey(
            apiKey = key,
            model = _selectedModel.value,
            onProgress = onProgress
        )
        return Pair(result.isSuccess, result.message)
    }

    fun refreshModels() {
        _models.value = modelStorage.getModels()
        _selectedModel.value = modelStorage.getSelectedModel()
        _isAutoSwitchModel.value = modelStorage.isAutoSwitchOnQuota()
        refreshSelectedModelUsage()
    }

    fun setSelectedModel(modelName: String) {
        modelStorage.setSelectedModel(modelName)
        refreshModels()
        Log.i(TAG, "Model terpilih diubah menjadi: $modelName")
    }

    fun addModel(modelName: String) {
        if (modelStorage.addModel(modelName)) {
            refreshModels()
            Log.i(TAG, "Model '$modelName' ditambahkan")
        }
    }

    fun deleteModel(modelName: String) {
        if (modelStorage.deleteModel(modelName)) {
            refreshModels()
            Log.i(TAG, "Model '$modelName' dihapus")
        }
    }

    fun moveModel(fromIndex: Int, toIndex: Int) {
        if (modelStorage.moveModel(fromIndex, toIndex)) {
            refreshModels()
            Log.i(TAG, "Model dipindahkan dari indeks $fromIndex ke $toIndex")
        }
    }

    fun toggleAutoSwitchModel(enabled: Boolean) {
        modelStorage.setAutoSwitchOnQuota(enabled)
        refreshModels()
        Log.i(TAG, "Pindah otomatis model diatur ke: $enabled")
    }

    fun setModelLimit(model: String, limit: Int) {
        modelUsageStorage.setLimit(model, limit)
        refreshSelectedModelUsage()
        Log.i(TAG, "Batas harian model $model diatur ke: $limit")
    }

    fun setModelUsage(model: String, usage: Int) {
        modelUsageStorage.setUsage(model, usage)
        refreshSelectedModelUsage()
        Log.i(TAG, "Hitungan kuota manual model $model diatur ke: $usage")
    }

    fun setNarrationLanguage(language: NarrationLanguage) {
        narrationLanguageStorage.setLanguage(language)
        _narrationLanguage.value = narrationLanguageStorage.getLanguage()
        Log.i(TAG, "Bahasa narasi diatur ke: ${language.displayName}")
    }

    fun setNarrationMode(mode: NarrationMode) {
        narrationModeStorage.setMode(mode)
        _narrationMode.value = narrationModeStorage.getMode()
        updatePlanSummary()
        Log.i(TAG, "Mode narasi diatur ke: ${mode.displayName}")
    }

    fun getModeForAnalysis(analysisId: Long): NarrationMode {
        return narrationModeStorage.getModeForAnalysis(analysisId) ?: _narrationMode.value
    }

    fun getModeNameForAnalysis(analysisId: Long): String {
        return getModeForAnalysis(analysisId).displayName
    }

    internal fun formatTimelineTranscript(timeline: List<TimelineItem>, mode: NarrationMode): String {
        val lines = timeline.mapNotNull { item ->
            val text = when (mode) {
                NarrationMode.DIALOG_KONTEN -> item.dialogue.trim()
                NarrationMode.NARASI_ASLI -> item.originalNarration.trim()
                else -> ""
            }
            if (text.isBlank()) null
            else {
                val rawTime = item.timestamp.substringBefore('-').trim().removePrefix("[").removeSuffix("]").trim()
                val timeTag = if (rawTime.isNotBlank()) "[$rawTime]" else "[00:00]"
                "$timeTag $text"
            }
        }
        return lines.joinToString("\n")
    }

    /**
     * Memanggil GeminiApiClient untuk menyatukan narasi dari beberapa analisis yang dipilih.
     */
    suspend fun mergeNarrationsWithAi(
        parts: List<Pair<String, String>>,
        onStatusUpdate: (String) -> Unit
    ): String {
        val modelList = _models.value
        val isAutoSwitch = _isAutoSwitchModel.value
        val selectedModelName = _selectedModel.value
        var currentModelIndex = modelList.indexOf(selectedModelName).let { if (it >= 0) it else 0 }
        var activeModel = modelList.getOrElse(currentModelIndex) { selectedModelName }
        val lang = narrationLanguageStorage.getLanguage()
        val mode = narrationModeStorage.getMode()

        while (true) {
            activeModel = modelList.getOrElse(currentModelIndex) { selectedModelName }
            try {
                return geminiClient.mergeNarrationsWithAi(
                    parts = parts,
                    model = activeModel,
                    language = lang,
                    mode = mode,
                    onStatusUpdate = onStatusUpdate
                )
            } catch (e: GeminiApiException) {
                if (e.isDailyQuota) {
                    modelUsageStorage.markQuotaExceeded(activeModel, e.retryDelaySeconds)
                    refreshSelectedModelUsage()
                }
                if (isAutoSwitch && e.isDailyQuota) {
                    currentModelIndex++
                    if (currentModelIndex < modelList.size) {
                        onStatusUpdate("Beralih ke model berikutnya (${modelList[currentModelIndex]})...")
                        continue
                    }
                }
                throw e
            }
        }
    }

    fun onVideoPicked(uri: Uri) {
        viewModelScope.launch {
            Log.i(TAG, "Video dipilih dari picker.")
            val video = withContext(Dispatchers.IO) { VideoMetadataHelper.loadVideoMetadata(context, uri) }
            Log.i(TAG, "Metadata video berhasil dimuat: durasi ${video.durationFormatted}, ukuran ${video.sizeFormatted}.")

            val pendingList = try {
                repository.allAnalyses.first()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                historyList.value
            }

            val matchedEntity = pendingList.firstOrNull { entity ->
                !entity.isCompleted && entity.completedSegmentsCount > 0 &&
                    isSameVideo(
                        selectedDurationMs = video.durationMs,
                        selectedSizeBytes = video.sizeBytes,
                        savedDurationMs = entity.durationMs,
                        savedSizeBytes = entity.videoSizeBytes,
                        selectedFingerprint = video.fingerprint,
                        savedFingerprint = entity.videoFingerprint
                    )
            }

            if (matchedEntity != null) {
                Log.i(TAG, "Ditemukan analisis yang belum selesai untuk video ini (ID: ${matchedEntity.id}, tersimpan ${matchedEntity.completedSegmentsCount}/${matchedEntity.totalSegments} segmen). Menampilkan pilihan ke pengguna.")
                val savedSourceId = seriesStorage.getSourceAnalysisId(matchedEntity.id)
                _selectedSeriesSourceId.value = savedSourceId
                _pendingMatchDialogState.value = PendingMatchDialogState(matchedEntity, video)
            } else {
                val totalSeconds = if (video.durationMs > 0) video.durationMs / 1000 else 1500L
                _analysisRangeStartSec.value = 0L
                _analysisRangeEndSec.value = totalSeconds
                _selectedVideo.value = video
                _selectedSeriesSourceId.value = null
                _analysisState.value = AnalysisState.Idle
                cachedRawCutPoints = null
                cachedRawKey = null
                cachedSceneSpans = null
                cachedSceneKey = null
                _detectedSceneCount.value = null
                updatePlanSummary()
                if (_promptCutMode.value == PromptCutMode.PER_SCENE) {
                    triggerSceneDetectionIfNeeded()
                }
            }
        }
    }

    private fun isSameVideo(
        selectedDurationMs: Long,
        selectedSizeBytes: Long,
        savedDurationMs: Long,
        savedSizeBytes: Long,
        selectedFingerprint: String = "",
        savedFingerprint: String = ""
    ): Boolean {
        return VideoMatchPolicy.matches(
            savedDurationMs = savedDurationMs,
            savedSizeBytes = savedSizeBytes,
            selectedDurationMs = selectedDurationMs,
            selectedSizeBytes = selectedSizeBytes,
            savedFingerprint = savedFingerprint,
            selectedFingerprint = selectedFingerprint
        )
    }

    fun resumeMatchedAnalysis() {
        val matchState = _pendingMatchDialogState.value ?: return
        _pendingMatchDialogState.value = null
        val totalSeconds = if (matchState.newVideo.durationMs > 0) matchState.newVideo.durationMs / 1000 else 1500L
        _analysisRangeStartSec.value = 0L
        _analysisRangeEndSec.value = totalSeconds
        _selectedVideo.value = matchState.newVideo
        updatePlanSummary()
        startAnalysis(matchState.entity.id)
    }

    fun startFreshAnalysisWithVideo() {
        val matchState = _pendingMatchDialogState.value ?: return
        _pendingMatchDialogState.value = null
        val totalSeconds = if (matchState.newVideo.durationMs > 0) matchState.newVideo.durationMs / 1000 else 1500L
        _analysisRangeStartSec.value = 0L
        _analysisRangeEndSec.value = totalSeconds
        _selectedVideo.value = matchState.newVideo
        _selectedSeriesSourceId.value = null
        activeAnalysisId = null
        _analysisState.value = AnalysisState.Idle
        updatePlanSummary()
    }

    fun dismissPendingMatchDialog() {
        _pendingMatchDialogState.value = null
    }

    fun clearSelectedVideo() {
        cancelAnalysis()
        preScanJob?.cancel()
        _analysisRangeStartSec.value = 0L
        _analysisRangeEndSec.value = 0L
        _selectedVideo.value = null
        _selectedSeriesSourceId.value = null
        activeAnalysisId = null
        cachedRawCutPoints = null
        cachedRawKey = null
        cachedSceneSpans = null
        cachedSceneKey = null
        latestPreScanDurationMs = 0L
        _detectedSceneCount.value = null
        _latestStageTimings.value = null
        _analysisState.value = AnalysisState.Idle
        updatePlanSummary()
        Log.d(TAG, "Video terpilih dibersihkan")
    }

    fun resetAnalysis() {
        cancelAnalysis()
        _selectedSeriesSourceId.value = null
        activeAnalysisId = null
        _latestStageTimings.value = null
        _analysisState.value = AnalysisState.Idle
        updatePlanSummary()
        Log.d(TAG, "Analisis di-reset kembali ke Idle")
    }

    fun cancelAnalysis() {
        Log.i(TAG, "Membatalkan analisis aktif...")
        runGeneration++
        AnalysisForegroundService.stop(context)
        currentJob?.cancel()
        currentJob = null
        _analysisState.value = AnalysisState.Idle

        val currentId = activeAnalysisId
        if (currentId != null) {
            viewModelScope.launch {
                val entity = repository.getAnalysisById(currentId)
                if (entity != null && entity.status != "COMPLETED") {
                    repository.update(
                        entity.copy(
                            status = "FAILED",
                            lastErrorStage = "Dibatalkan Pengguna",
                            lastErrorMessage = "Analisis dibatalkan oleh pengguna.",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        if (_analysisState.value !is AnalysisState.Success) {
            _analysisState.value = AnalysisState.Idle
        }
    }

    fun startAnalysis(resumeId: Long? = null, forceReupload: Boolean = false) {
        runStartAnalysis(resumeId = resumeId, forceReupload = forceReupload)
    }

    fun onRecoveryVideoPicked(newUri: Uri) {
        runOnRecoveryVideoPicked(newUri)
    }

    fun finishWithoutCurrentSegment(analysisId: Long? = null) {
        runFinishWithoutCurrentSegment(analysisId)
    }

    override fun onCleared() {
        super.onCleared()
        runGeneration++
        AnalysisForegroundService.stop(context)
    }
}
