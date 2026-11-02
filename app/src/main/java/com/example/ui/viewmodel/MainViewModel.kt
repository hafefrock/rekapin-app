package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiApiClient
import com.example.data.api.GeminiApiException
import com.example.data.api.GeminiConstants
import com.example.data.db.AnalysisJsonAdapter
import com.example.data.db.AnimeAnalysisEntity
import com.example.data.db.AppDatabase
import com.example.data.model.AnalysisState
import com.example.data.model.AnimeAnalysisResult
import com.example.data.model.CharacterInfo
import com.example.data.model.SelectedVideo
import com.example.data.model.TimelineItem
import com.example.data.model.ApiKeyItem
import com.example.data.model.ModelUsageInfo
import com.example.data.model.NarrationLanguage
import com.example.data.model.NarrationMode
import com.example.data.model.VisualStyle
import com.example.data.prefs.ApiKeyStorage
import com.example.data.prefs.ModelStorage
import com.example.data.prefs.ModelUsageStorage
import com.example.data.prefs.NarrationLanguageStorage
import com.example.data.prefs.NarrationModeStorage
import com.example.data.prefs.SeriesStorage
import com.example.data.prefs.VisualStyleStorage
import com.example.data.repository.AnimeAnalysisRepository
import com.example.data.model.PromptCutMode
import com.example.data.prefs.PromptCutModeStorage
import com.example.service.AnalysisForegroundService
import com.example.util.SceneDetector
import com.example.util.SceneTimeSpan
import com.example.util.SegmentCalculator
import com.example.util.SegmentDurationOption
import com.example.util.SegmentPlanSummary
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
import java.util.Locale
import kotlin.math.ceil

data class SceneCacheKey(
    val videoUri: String,
    val startSec: Long,
    val endSec: Long,
    val maxSceneDurationSec: Int
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "RekapinVM"
    }

    private val context: Context get() = getApplication()
    val apiKeyStorage = ApiKeyStorage(context)
    val modelStorage = ModelStorage(context)
    val narrationLanguageStorage = NarrationLanguageStorage(context)
    val narrationModeStorage = NarrationModeStorage(context)
    val modelUsageStorage = ModelUsageStorage(context)
    val visualStyleStorage = VisualStyleStorage(context)
    val seriesStorage = SeriesStorage(context)
    val promptCutModeStorage = PromptCutModeStorage(context)

    private val geminiClient = GeminiApiClient(
        apiKeyProvider = { apiKeyStorage.getEffectiveApiKey() },
        onRequestCounted = { modelName ->
            modelUsageStorage.incrementUsage(modelName)
            refreshSelectedModelUsage()
        },
        onDailyQuotaExceeded = { modelName, retryDelaySeconds ->
            modelUsageStorage.markQuotaExceeded(modelName, retryDelaySeconds)
            refreshSelectedModelUsage()
        }
    )

    private val database = AppDatabase.getDatabase(application)
    val repository = AnimeAnalysisRepository(database.animeAnalysisDao())

    // Flow riwayat analisis tersimpan di Room database
    val historyList: StateFlow<List<AnimeAnalysisEntity>> = repository.allAnalyses.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedVideo = MutableStateFlow<SelectedVideo?>(null)
    val selectedVideo: StateFlow<SelectedVideo?> = _selectedVideo.asStateFlow()

    // Mode potong prompt: Interval Tetap (default) atau Per Adegan
    private val _promptCutMode = MutableStateFlow(promptCutModeStorage.getMode())
    val promptCutMode: StateFlow<PromptCutMode> = _promptCutMode.asStateFlow()

    // Batas maksimum durasi adegan saat mode Per Adegan (default 5 detik)
    private val _maxSceneDurationSeconds = MutableStateFlow(promptCutModeStorage.getMaxSceneDuration())
    val maxSceneDurationSeconds: StateFlow<Int> = _maxSceneDurationSeconds.asStateFlow()

    // Jumlah adegan terdeteksi untuk video saat ini
    private val _detectedSceneCount = MutableStateFlow<Int?>(null)
    val detectedSceneCount: StateFlow<Int?> = _detectedSceneCount.asStateFlow()

    // Cache rentang adegan di memori selama sesi beserta kuncinya
    private var cachedSceneSpans: List<SceneTimeSpan>? = null
    private var cachedSceneKey: SceneCacheKey? = null

    // Pilihan interval visual prompt: 3 detik atau 5 detik (default 5 detik)
    private val _promptIntervalSeconds = MutableStateFlow(5)
    val promptIntervalSeconds: StateFlow<Int> = _promptIntervalSeconds.asStateFlow()

    // Pilihan panjang segmen: Otomatis (default), 3 menit, 5 menit
    private val _segmentDurationOption = MutableStateFlow(SegmentDurationOption.AUTO)
    val segmentDurationOption: StateFlow<SegmentDurationOption> = _segmentDurationOption.asStateFlow()

    // Ringkasan rencana segmen sebelum analisis dimulai
    private val _segmentPlanSummary = MutableStateFlow<SegmentPlanSummary?>(null)
    val segmentPlanSummary: StateFlow<SegmentPlanSummary?> = _segmentPlanSummary.asStateFlow()

    // Pilihan seri video sumber (membawa character sheet dari video selesai sebelumnya)
    private val _selectedSeriesSourceId = MutableStateFlow<Long?>(null)
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

    // Dialog konfirmasi jika video yang dipilih cocok dengan analisis yang belum selesai
    data class PendingMatchDialogState(
        val entity: AnimeAnalysisEntity,
        val newVideo: SelectedVideo
    )

    private val _pendingMatchDialogState = MutableStateFlow<PendingMatchDialogState?>(null)
    val pendingMatchDialogState: StateFlow<PendingMatchDialogState?> = _pendingMatchDialogState.asStateFlow()

    private val _analysisState = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState: StateFlow<AnalysisState> = _analysisState.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _apiKeys = MutableStateFlow<List<ApiKeyItem>>(apiKeyStorage.getAllKeys())
    val apiKeys: StateFlow<List<ApiKeyItem>> = _apiKeys.asStateFlow()

    private val _activeKeyName = MutableStateFlow(apiKeyStorage.getActiveKeyName())
    val activeKeyName: StateFlow<String> = _activeKeyName.asStateFlow()

    // Model Gemini
    private val _models = MutableStateFlow<List<String>>(modelStorage.getModels())
    val models: StateFlow<List<String>> = _models.asStateFlow()

    private val _selectedModel = MutableStateFlow(modelStorage.getSelectedModel())
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _isAutoSwitchModel = MutableStateFlow(modelStorage.isAutoSwitchOnQuota())
    val isAutoSwitchModel: StateFlow<Boolean> = _isAutoSwitchModel.asStateFlow()

    // Bahasa Narasi
    private val _narrationLanguage = MutableStateFlow(narrationLanguageStorage.getLanguage())
    val narrationLanguage: StateFlow<NarrationLanguage> = _narrationLanguage.asStateFlow()

    // Mode Narasi
    private val _narrationMode = MutableStateFlow(narrationModeStorage.getMode())
    val narrationMode: StateFlow<NarrationMode> = _narrationMode.asStateFlow()

    // Penggunaan & Kuota Model Terpilih Hari Ini
    private val _selectedModelUsage = MutableStateFlow(modelUsageStorage.getUsageInfo(modelStorage.getSelectedModel()))
    val selectedModelUsage: StateFlow<ModelUsageInfo> = _selectedModelUsage.asStateFlow()

    fun refreshSelectedModelUsage() {
        _selectedModelUsage.value = modelUsageStorage.getUsageInfo(modelStorage.getSelectedModel())
    }

    // Gaya Visual (Preset bawaan & Buatan sendiri)
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

    // Layar Riwayat
    private val _isHistoryOpen = MutableStateFlow(false)
    val isHistoryOpen: StateFlow<Boolean> = _isHistoryOpen.asStateFlow()

    // Detail riwayat yang sedang dibuka
    private val _viewingHistoryItem = MutableStateFlow<AnimeAnalysisEntity?>(null)
    val viewingHistoryItem: StateFlow<AnimeAnalysisEntity?> = _viewingHistoryItem.asStateFlow()

    // ID analisis yang sedang aktif berjalan di database
    private var activeAnalysisId: Long? = null
    private var currentJob: Job? = null

    // Rentang analisis video (dalam detik absolut dari awal video)
    private val _analysisRangeStartSec = MutableStateFlow(0L)
    val analysisRangeStartSec: StateFlow<Long> = _analysisRangeStartSec.asStateFlow()

    private val _analysisRangeEndSec = MutableStateFlow(0L)
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

    private var preScanJob: kotlinx.coroutines.Job? = null

    fun triggerSceneDetectionIfNeeded() {
        val video = _selectedVideo.value ?: return
        if (_promptCutMode.value != PromptCutMode.PER_SCENE) return
        val totalSeconds = if (video.durationMs > 0) video.durationMs / 1000 else 1500L
        val safeStart = _analysisRangeStartSec.value.coerceIn(0L, totalSeconds)
        val currentEnd = _analysisRangeEndSec.value
        val safeEnd = if (currentEnd <= 0L || currentEnd > totalSeconds) totalSeconds else currentEnd
        val maxDur = _maxSceneDurationSeconds.value

        preScanJob?.cancel()
        preScanJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Memulai pre-scan adegan di background: start=$safeStart, end=$safeEnd, maxDur=$maxDur")
                val detected = SceneDetector.detectSceneCuts(
                    context = getApplication(),
                    videoUri = video.uri,
                    startSec = safeStart,
                    endSec = safeEnd,
                    maxSceneDurationSec = maxDur
                )
                cachedSceneSpans = detected
                cachedSceneKey = SceneCacheKey(video.uri.toString(), safeStart, safeEnd, maxDur)
                _detectedSceneCount.value = detected.size
                Log.d(TAG, "Pre-scan adegan selesai: ${detected.size} adegan")
            } catch (e: kotlinx.coroutines.CancellationException) {
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
            cachedSceneSpans = null
            cachedSceneKey = null
            _detectedSceneCount.value = null
            Log.i(TAG, "Batas maksimum durasi adegan diubah ke: ${if (seconds == 0) "Tanpa batas" else "$seconds detik"}")
            if (_promptCutMode.value == PromptCutMode.PER_SCENE) {
                triggerSceneDetectionIfNeeded()
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
            _segmentPlanSummary.value = SegmentCalculator.calculatePlanSummary(
                totalSeconds = totalSeconds,
                option = _segmentDurationOption.value,
                intervalSeconds = _promptIntervalSeconds.value,
                rangeStartSec = safeStart,
                rangeEndSec = safeEnd
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

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
            if (_viewingHistoryItem.value?.id == id) {
                _viewingHistoryItem.value = null
            }
            if (activeAnalysisId == id) {
                activeAnalysisId = null
            }
            Log.i(TAG, "Riwayat analisis ID $id dihapus dari database")
        }
    }

    fun refreshApiKeys() {
        _apiKeys.value = apiKeyStorage.getAllKeys()
        _activeKeyName.value = apiKeyStorage.getActiveKeyName()
    }

    fun addApiKey(name: String, key: String) {
        apiKeyStorage.addKey(name, key)
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
        Log.i(TAG, "Mode narasi diatur ke: ${mode.displayName}")
    }

    fun getModeForAnalysis(analysisId: Long): NarrationMode {
        return narrationModeStorage.getModeForAnalysis(analysisId) ?: _narrationMode.value
    }

    fun getModeNameForAnalysis(analysisId: Long): String {
        return getModeForAnalysis(analysisId).displayName
    }

    private fun formatTimelineTranscript(timeline: List<TimelineItem>, mode: NarrationMode): String {
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
     * Menggunakan multi-key dan model aktif dengan penanganan kuota otomatis.
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

        var lastException: Exception? = null

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
                lastException = e
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
            } catch (e: Exception) {
                lastException = e
                throw e
            }
        }
    }

    /**
     * Memproses video yang dipilih: membaca metadata dengan MediaMetadataRetriever tanpa memanggil Gemini.
     * Jika cocok dengan analisis yang belum selesai (durasi & ukuran sama), tampilkan dialog pilihan:
     * "Lanjutkan analisis lama" atau "Mulai baru".
     */
    fun onVideoPicked(uri: Uri) {
        viewModelScope.launch {
            Log.i(TAG, "Video dipilih dari picker, URI: $uri")
            val video = withContext(Dispatchers.IO) { loadVideoMetadata(uri) }
            Log.i(TAG, "Metadata video berhasil dimuat: ${video.displayName}, durasi: ${video.durationFormatted}, ukuran: ${video.sizeFormatted}")

            // Cek apakah video ini cocok dengan analisis yang belum selesai di Room database
            val pendingList = try {
                repository.allAnalyses.first()
            } catch (_: Exception) {
                historyList.value
            }

            val matchedEntity = pendingList.firstOrNull { entity ->
                !entity.isCompleted && entity.completedSegmentsCount > 0 &&
                        isSameVideo(video.durationMs, video.sizeBytes, entity.durationMs, entity.videoSizeBytes)
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
        dur1: Long,
        size1: Long,
        dur2: Long,
        size2: Long
    ): Boolean {
        val durationMatches = if (dur1 > 0 && dur2 > 0) {
            kotlin.math.abs(dur1 - dur2) <= 2000L // toleransi 2 detik
        } else {
            true
        }
        val sizeMatches = if (size1 > 0 && size2 > 0) {
            kotlin.math.abs(size1 - size2) <= 2048L || size1 == size2
        } else {
            true
        }
        return durationMatches && sizeMatches
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
        cachedSceneSpans = null
        cachedSceneKey = null
        _detectedSceneCount.value = null
        _analysisState.value = AnalysisState.Idle
        updatePlanSummary()
        Log.d(TAG, "Video terpilih dibersihkan")
    }

    fun resetAnalysis() {
        cancelAnalysis()
        _selectedSeriesSourceId.value = null
        activeAnalysisId = null
        _analysisState.value = AnalysisState.Idle
        updatePlanSummary()
        Log.d(TAG, "Analisis di-reset kembali ke Idle")
    }

    fun cancelAnalysis() {
        Log.i(TAG, "Membatalkan analisis aktif...")
        AnalysisForegroundService.stop(context)
        currentJob?.cancel()
        currentJob = null

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

    /**
     * Memulai analisis baru atau melanjutkan analisis yang tertunda.
     * @param resumeId ID analisis di Room jika ingin melanjutkan dari segmen yang gagal.
     * @param forceReupload Paksa unggah ulang berkas video ke Gemini API (misalnya setelah ganti URI).
     */
    fun startAnalysis(resumeId: Long? = null, forceReupload: Boolean = false) {
        if (!apiKeyStorage.hasApiKey()) {
            Log.w(TAG, "Percobaan analisis gagal: API Key belum diatur")
            _isSettingsOpen.value = true
            _analysisState.value = AnalysisState.Error(
                stage = "Pemeriksaan API Key",
                httpCode = null,
                rawApiMessage = "Kunci API kosong pada SharedPreferences dan BuildConfig.",
                errorMessage = "API Key Gemini belum diatur. Silakan masukkan API Key Gemini Anda di menu API Key.",
                canRetry = true
            )
            return
        }

        currentJob?.cancel()
        val currentModel = modelStorage.getSelectedModel()
        AnalysisForegroundService.onCancelRequested = {
            cancelAnalysis()
        }
        AnalysisForegroundService.start(
            context = context,
            model = currentModel,
            title = if (resumeId != null) "Melanjutkan Analisis Video" else "Memulai Analisis Video",
            message = "Sedang menyiapkan berkas video..."
        )

        currentJob = viewModelScope.launch {
            var currentStage = "Mempersiapkan Berkas"
            var activeEntityId: Long? = resumeId ?: activeAnalysisId
            var remoteFileName = ""
            var remoteFileUri = ""

            try {
                var existingEntity: AnimeAnalysisEntity? = null
                if (activeEntityId != null) {
                    existingEntity = repository.getAnalysisById(activeEntityId)
                }

                // Tentukan Video yang akan dianalisis
                var video = _selectedVideo.value

                // Jika melanjutkan analisis tersimpan, verifikasi izin akses berkas video terlebih dahulu
                if (existingEntity != null) {
                    val parsedUri = Uri.parse(existingEntity.videoUriString)
                    val canAccess = withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openInputStream(parsedUri)?.use { stream ->
                                stream.read()
                            }
                            true
                        } catch (_: SecurityException) {
                            false
                        } catch (_: java.io.FileNotFoundException) {
                            false
                        } catch (_: Exception) {
                            false
                        }
                    }

                    if (!canAccess) {
                        Log.w(TAG, "Berkas video pada analisis ID $activeEntityId tidak bisa dibuka lagi (SecurityException / FileNotFoundException).")
                        val resumeInfo = checkCanResume(activeEntityId)
                        _analysisState.value = AnalysisState.Error(
                            stage = "Akses Berkas Video",
                            httpCode = null,
                            rawApiMessage = "", // JANGAN tampilkan stack trace sesuai instruksi nomor 3!
                            errorMessage = "Video tidak bisa dibuka lagi. Pilih video yang sama.",
                            canRetry = false,
                            canResume = false,
                            canSkipAndFinish = resumeInfo.canSkipAndFinish || existingEntity.completedSegmentsCount > 0,
                            analysisId = activeEntityId,
                            isVideoAccessLost = true,
                            expectedDurationMs = existingEntity.durationMs,
                            expectedSizeBytes = existingEntity.videoSizeBytes
                        )
                        return@launch
                    }

                    if (video == null || video.uri != parsedUri) {
                        video = withContext(Dispatchers.IO) { loadVideoMetadata(parsedUri) }
                        _selectedVideo.value = video
                    }
                    if (existingEntity.intervalSeconds == 0) {
                        _promptCutMode.value = PromptCutMode.PER_SCENE
                        promptCutModeStorage.setMode(PromptCutMode.PER_SCENE)
                        _maxSceneDurationSeconds.value = promptCutModeStorage.getMaxDurationForAnalysis(existingEntity.id)
                    } else {
                        _promptCutMode.value = PromptCutMode.FIXED_INTERVAL
                        promptCutModeStorage.setMode(PromptCutMode.FIXED_INTERVAL)
                        _promptIntervalSeconds.value = existingEntity.intervalSeconds
                    }
                }

                if (video == null) {
                    _analysisState.value = AnalysisState.Error(
                        stage = "Pemilihan Video",
                        httpCode = null,
                        rawApiMessage = "Berkas video belum dipilih.",
                        errorMessage = "Silakan pilih video anime terlebih dahulu."
                    )
                    return@launch
                }

                // 1. Sebelum mulai, baca durasi video asli menggunakan MediaMetadataRetriever
                val actualDurationMs = withContext(Dispatchers.IO) {
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(context, video.uri)
                        val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        retriever.release()
                        durStr?.toLongOrNull() ?: video.durationMs
                    } catch (e: Exception) {
                        Log.w(TAG, "Gagal membaca durasi video via MediaMetadataRetriever: ${e.message}")
                        video.durationMs
                    }
                }

                if (actualDurationMs > 0 && video.durationMs <= 0) {
                    video = video.copy(durationMs = actualDurationMs)
                    _selectedVideo.value = video
                }

                val totalSeconds = if (actualDurationMs > 0) {
                    actualDurationMs / 1000
                } else {
                    1500L
                }

                // 2. Tentukan Rencana Segmen:
                // - Jika melanjutkan analisis dari database, PAKAI RENCANA TERSIMPAN (jangan hitung ulang!)
                // - Jika analisis baru, hitung berdasarkan durasi asli dan opsi panjang segmen terpilih
                val segmentSpans = if (existingEntity != null) {
                    val savedSpans = AnalysisJsonAdapter.jsonToSegmentPlan(existingEntity.segmentPlanJson)
                    if (savedSpans.isNotEmpty()) {
                        Log.i(TAG, "Menggunakan rencana segmen tersimpan dari database (${savedSpans.size} segmen). Tidak menghitung ulang.")
                        savedSpans
                    } else {
                        val opt = try {
                            SegmentDurationOption.valueOf(existingEntity.segmentDurationMode)
                        } catch (_: Exception) {
                            _segmentDurationOption.value
                        }
                        SegmentCalculator.calculateSegments(totalSeconds, opt)
                    }
                } else {
                    val safeStart = _analysisRangeStartSec.value.coerceIn(0L, totalSeconds)
                    val currentEnd = _analysisRangeEndSec.value
                    val safeEnd = if (currentEnd <= 0L || currentEnd > totalSeconds) totalSeconds else currentEnd
                    SegmentCalculator.calculateSegments(
                        totalSeconds = totalSeconds,
                        option = _segmentDurationOption.value,
                        rangeStartSec = safeStart,
                        rangeEndSec = safeEnd
                    )
                }

                val totalSegments = segmentSpans.size
                Log.i(TAG, "Durasi video: ${totalSeconds}s, Total segmen: $totalSegments (rentang: ${segmentSpans.map { "${it.startSec}-${it.endSec}s" }})")

                // Persiapan state data akumulasi
                val allCharacterSheet = mutableListOf<CharacterInfo>()
                val allTimeline = mutableListOf<TimelineItem>()
                val segmentSummaries = mutableListOf<String>()
                val segmentModels = mutableListOf<String>()
                var startIndex = 0

                val activeStyle: VisualStyle = if (existingEntity != null) {
                    visualStyleStorage.getStyleForAnalysis(existingEntity.id)
                        ?: visualStyleStorage.getSelectedStyle().also {
                            visualStyleStorage.saveStyleForAnalysis(existingEntity.id, it)
                        }
                } else {
                    visualStyleStorage.getSelectedStyle()
                }

                val activeMode: NarrationMode = if (existingEntity != null) {
                    narrationModeStorage.getModeForAnalysis(existingEntity.id)
                        ?: _narrationMode.value.also {
                            narrationModeStorage.saveModeForAnalysis(existingEntity.id, it)
                        }
                } else {
                    _narrationMode.value
                }

                val activeCutMode: PromptCutMode = if (existingEntity != null) {
                    if (existingEntity.intervalSeconds == 0) PromptCutMode.PER_SCENE else PromptCutMode.FIXED_INTERVAL
                } else {
                    _promptCutMode.value
                }

                val activeMaxSceneDur: Int = if (existingEntity != null) {
                    promptCutModeStorage.getMaxDurationForAnalysis(existingEntity.id)
                } else {
                    _maxSceneDurationSeconds.value
                }

                // Jika Melanjutkan dari database
                if (existingEntity != null) {
                    activeEntityId = existingEntity.id
                    activeAnalysisId = existingEntity.id
                    if (!forceReupload) {
                        remoteFileName = existingEntity.geminiFileRemoteName
                        remoteFileUri = existingEntity.geminiFileUri
                    }
                    if (existingEntity.completedSegmentsCount > 0 && existingEntity.completedSegmentsCount < totalSegments) {
                        startIndex = existingEntity.completedSegmentsCount
                        allCharacterSheet.addAll(AnalysisJsonAdapter.jsonToCharacterSheet(existingEntity.characterSheetJson))
                        allTimeline.addAll(AnalysisJsonAdapter.jsonToTimeline(existingEntity.timelineJson))
                        segmentSummaries.addAll(AnalysisJsonAdapter.jsonToSummaries(existingEntity.segmentSummariesJson))
                        segmentModels.addAll(AnalysisJsonAdapter.jsonToSummaries(existingEntity.segmentModelsJson))
                        Log.i(TAG, "Melanjutkan analisis ID ${existingEntity.id} dari segmen ${startIndex + 1} dengan ${allCharacterSheet.size} karakter tersimpan. Gaya: ${activeStyle.name}, Mode: ${activeMode.displayName}, CutMode: ${activeCutMode.displayName}")
                    }
                } else {
                    // Buat rekaman awal baru di Room database dengan rencana segmen tersimpan
                    val effectiveInterval = if (activeCutMode == PromptCutMode.PER_SCENE) 0 else _promptIntervalSeconds.value
                    val newEntity = AnimeAnalysisEntity(
                        videoUriString = video.uri.toString(),
                        videoName = video.displayName,
                        videoSizeBytes = video.sizeBytes,
                        durationMs = if (actualDurationMs > 0) actualDurationMs else video.durationMs,
                        mimeType = video.mimeType,
                        intervalSeconds = effectiveInterval,
                        totalSegments = totalSegments,
                        completedSegmentsCount = 0,
                        geminiFileRemoteName = "",
                        geminiFileUri = "",
                        uploadKeyHash = apiKeyStorage.getActiveKeyHash(),
                        status = "IN_PROGRESS",
                        characterSheetJson = "[]",
                        timelineJson = "[]",
                        segmentSummariesJson = "[]",
                        segmentModelsJson = "[]",
                        narration = "",
                        segmentPlanJson = AnalysisJsonAdapter.segmentPlanToJson(segmentSpans),
                        segmentDurationMode = _segmentDurationOption.value.name
                    )
                    activeEntityId = repository.insert(newEntity)
                    activeAnalysisId = activeEntityId
                    visualStyleStorage.saveStyleForAnalysis(activeEntityId, activeStyle)
                    narrationModeStorage.saveModeForAnalysis(activeEntityId, activeMode)
                    promptCutModeStorage.saveModeForAnalysis(activeEntityId, activeCutMode, activeMaxSceneDur)
                    Log.i(TAG, "Membuat entri analisis baru di Room database ID: $activeEntityId dengan rencana $totalSegments segmen, gaya: ${activeStyle.name}, mode: ${activeMode.displayName}, cutMode: ${activeCutMode.displayName}")

                    // Simpan id analisis sumber di SharedPreferences (kunci = id analisis baru)
                    val chosenSeriesSourceId = _selectedSeriesSourceId.value
                    if (chosenSeriesSourceId != null) {
                        seriesStorage.saveSourceAnalysisId(activeEntityId, chosenSeriesSourceId)
                        Log.i(TAG, "Menyimpan relasi seri di SharedPreferences: key=$activeEntityId, source=$chosenSeriesSourceId")
                    }
                }

                // Tentukan nilai awal character sheet SEBELUM loop dimulai
                // Jika allCharacterSheet masih kosong (analisis baru atau melanjutkan analisis yang belum sempat menyelesaikan segmen 1)
                if (allCharacterSheet.isEmpty() && activeEntityId != null) {
                    val sourceAnalysisId = seriesStorage.getSourceAnalysisId(activeEntityId)
                    if (sourceAnalysisId != null) {
                        _selectedSeriesSourceId.value = sourceAnalysisId
                        val sourceEntity = repository.getAnalysisById(sourceAnalysisId)
                        if (sourceEntity != null && sourceEntity.characterSheetJson.isNotBlank()) {
                            val initialChars = AnalysisJsonAdapter.jsonToCharacterSheet(sourceEntity.characterSheetJson)
                            if (initialChars.isNotEmpty()) {
                                allCharacterSheet.addAll(initialChars)
                                Log.i(TAG, "Inisialisasi character sheet awal: ${allCharacterSheet.size} karakter dimuat dari seri sumber ID $sourceAnalysisId (${sourceEntity.videoName}) untuk analisis ID $activeEntityId")
                            }
                        }
                    }
                }

                // Validasi File di Gemini API: Cek apakah masih ACTIVE dan apakah key aktif masih sama
                currentStage = "Verifikasi Status Berkas di Gemini API"
                val currentKeyHash = apiKeyStorage.getActiveKeyHash()
                val savedKeyHash = existingEntity?.uploadKeyHash.orEmpty()
                val isKeyChanged = savedKeyHash.isNotBlank() && currentKeyHash.isNotBlank() && savedKeyHash != currentKeyHash

                _analysisState.value = AnalysisState.Preparing(
                    if (isKeyChanged) "Key berubah, mengunggah ulang video..." else "Memeriksa status video di Google AI..."
                )
                var needUpload = false
                var needWaitActive = false

                if (forceReupload || isKeyChanged) {
                    if (isKeyChanged) {
                        Log.i(TAG, "Key aktif ($currentKeyHash) berbeda dari key saat upload ($savedKeyHash). Mengunggah ulang video otomatis.")
                    }
                    needUpload = true
                } else if (remoteFileName.isNotBlank()) {
                    val remoteState = geminiClient.checkFileRemoteState(remoteFileName)
                    when (remoteState) {
                        GeminiApiClient.RemoteFileState.ACTIVE -> {
                            Log.i(TAG, "Berkas $remoteFileName di Gemini masih ACTIVE. Siap langsung digunakan kembali.")
                            needUpload = false
                            needWaitActive = false
                        }
                        GeminiApiClient.RemoteFileState.PROCESSING -> {
                            Log.i(TAG, "Berkas $remoteFileName di Gemini masih PROCESSING. Melanjutkan tunggu tanpa upload ulang...")
                            needUpload = false
                            needWaitActive = true
                        }
                        GeminiApiClient.RemoteFileState.FAILED -> {
                            Log.e(TAG, "Berkas $remoteFileName di Gemini berstatus FAILED.")
                            throw GeminiApiException(
                                stage = "Verifikasi Status Berkas",
                                httpCode = null,
                                rawMessage = "File state: FAILED di server Google AI.",
                                userFriendlyMessage = "Pemrosesan video di server Google AI gagal (Status: FAILED). Format atau kodek video mungkin tidak didukung oleh server AI. Silakan unggah ulang video untuk mencoba kembali.",
                                isFileProcessingFailed = true
                            )
                        }
                        GeminiApiClient.RemoteFileState.NOT_FOUND -> {
                            Log.w(TAG, "Berkas $remoteFileName di Gemini telah kedaluwarsa atau tidak ditemukan (>48 jam). Mengunggah ulang video...")
                            needUpload = true
                        }
                    }
                } else {
                    needUpload = true
                }

                // Jika perlu upload ulang (awal baru, file lama expired, forceReupload, atau key aktif berbeda)
                if (needUpload) {
                    currentStage = "Upload Video ke Gemini File API"
                    Log.i(TAG, "[$currentStage] Mengunggah ${video.sizeBytes} bytes secara streaming...")
                    val uploadedFile = geminiClient.uploadVideo(
                        context = context,
                        video = video,
                        onProgress = { percent, sent, total ->
                            _analysisState.value = AnalysisState.Uploading(
                                progressPercent = percent,
                                bytesSent = sent,
                                totalBytes = total,
                                message = if (isKeyChanged)
                                    "Key berubah, mengunggah ulang video ($percent%)..."
                                else if (startIndex > 0)
                                    "Mengunggah ulang video untuk melanjutkan segmen ${startIndex + 1} ($percent%)..."
                                else
                                    "Mengunggah video ke Gemini File API ($percent%)..."
                            )
                            if (percent % 10 == 0 || percent == 100) {
                                AnalysisForegroundService.updateStatus(
                                    context = context,
                                    title = "Mengunggah Video ($percent%)",
                                    message = "Mengunggah berkas video ke Google AI...",
                                    model = currentModel
                                )
                            }
                        }
                    )

                    remoteFileName = uploadedFile.name
                    remoteFileUri = uploadedFile.uri

                    // Simpan nama berkas remote dan hash key aktif ke Room segera setelah upload selesai
                    activeEntityId?.let { id ->
                        val ent = repository.getAnalysisById(id)
                        if (ent != null) {
                            repository.update(
                                ent.copy(
                                    geminiFileRemoteName = remoteFileName,
                                    geminiFileUri = remoteFileUri,
                                    uploadKeyHash = currentKeyHash,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    needWaitActive = true
                }

                if (needWaitActive) {
                    currentStage = "Menunggu Status Video ACTIVE"
                    val pollingInitialMsg = "Memproses video di server Google... (00:00)"
                    _analysisState.value = AnalysisState.PollingFileStatus(pollingInitialMsg)
                    AnalysisForegroundService.updateStatus(
                        context = context,
                        title = "Memproses Berkas di AI",
                        message = pollingInitialMsg,
                        model = currentModel
                    )
                    val activeFile = geminiClient.waitForFileActive(remoteFileName) { statusMsg ->
                        _analysisState.value = AnalysisState.PollingFileStatus(statusMsg)
                        AnalysisForegroundService.updateStatus(
                            context = context,
                            title = "Memproses Berkas di AI",
                            message = statusMsg,
                            model = currentModel
                        )
                    }

                    remoteFileName = activeFile.name
                    remoteFileUri = activeFile.uri

                    // Update informasi file remote dan identitas key saat upload (hash singkat) ke database
                    activeEntityId?.let { id ->
                        val ent = repository.getAnalysisById(id)
                        if (ent != null) {
                            repository.update(
                                ent.copy(
                                    geminiFileRemoteName = remoteFileName,
                                    geminiFileUri = remoteFileUri,
                                    uploadKeyHash = currentKeyHash,
                                    status = "IN_PROGRESS",
                                    lastErrorStage = "",
                                    lastErrorMessage = "",
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }

                // Model terpilih saat analisis dimulai atau dilanjutkan
                val selectedModelName = modelStorage.getSelectedModel()
                val modelList = modelStorage.getModels().ifEmpty { listOf(selectedModelName) }
                val isAutoSwitch = modelStorage.isAutoSwitchOnQuota()
                var currentModelIndex = modelList.indexOf(selectedModelName).coerceAtLeast(0)
                var activeModel = modelList.getOrElse(currentModelIndex) { selectedModelName }
                Log.i(TAG, "Memulai analisis dengan model '$activeModel' (auto-switch: $isAutoSwitch, daftar: $modelList)")

                // Deteksi adegan video sekali untuk seluruh rentang jika mode Per Adegan
                if (activeCutMode == PromptCutMode.PER_SCENE) {
                    val safeStart = segmentSpans.firstOrNull()?.startSec ?: 0L
                    val safeEnd = segmentSpans.lastOrNull()?.endSec ?: totalSeconds
                    val requiredKey = SceneCacheKey(
                        videoUri = video.uri.toString(),
                        startSec = safeStart,
                        endSec = safeEnd,
                        maxSceneDurationSec = activeMaxSceneDur
                    )

                    // Jika preScanJob masih aktif, tunggu job selesai (join) dan gunakan hasilnya
                    val activePreScan = preScanJob
                    if (activePreScan != null && activePreScan.isActive) {
                        currentStage = "Mendeteksi Adegan Video"
                        _analysisState.value = AnalysisState.Preparing("Mendeteksi adegan video...")
                        AnalysisForegroundService.updateStatus(
                            context = context,
                            title = "Mendeteksi Adegan Video",
                            message = "Mendeteksi transisi adegan visual otomatis...",
                            model = activeModel
                        )
                        Log.i(TAG, "Menunggu preScanJob latar belakang selesai (join)...")
                        activePreScan.join()
                    }

                    // Pakai cache hanya jika kuncinya cocok dengan analisis yang akan berjalan
                    if (cachedSceneSpans != null && cachedSceneKey != requiredKey) {
                        Log.i(TAG, "Cache adegan tidak cocok dengan analisis yang akan berjalan. Membuang cache lama...")
                        cachedSceneSpans = null
                        cachedSceneKey = null
                        _detectedSceneCount.value = null
                    }

                    if (cachedSceneSpans == null || cachedSceneKey != requiredKey) {
                        currentStage = "Mendeteksi Adegan Video"
                        _analysisState.value = AnalysisState.Preparing("Mendeteksi adegan video...")
                        AnalysisForegroundService.updateStatus(
                            context = context,
                            title = "Mendeteksi Adegan Video",
                            message = "Mendeteksi transisi adegan visual otomatis...",
                            model = activeModel
                        )
                        val detected = SceneDetector.detectSceneCuts(
                            context = context,
                            videoUri = video.uri,
                            startSec = safeStart,
                            endSec = safeEnd,
                            maxSceneDurationSec = activeMaxSceneDur,
                            onProgress = { _, msg ->
                                _analysisState.value = AnalysisState.Preparing(msg)
                                AnalysisForegroundService.updateStatus(
                                    context = context,
                                    title = "Mendeteksi Adegan Video",
                                    message = msg,
                                    model = activeModel
                                )
                            }
                        )
                        cachedSceneSpans = detected
                        cachedSceneKey = requiredKey
                        _detectedSceneCount.value = detected.size
                        Log.i(TAG, "Deteksi adegan lokal selesai: ${detected.size} adegan terdeteksi")
                    }
                }

                // Loop Analisis per segmen (mulai dari startIndex - segmen tersimpan TIDAK dianalisis ulang)
                for (i in startIndex until totalSegments) {
                    val segment = segmentSpans[i]
                    val startSec = segment.startSec
                    val endSec = minOf(segment.endSec, totalSeconds)
                    val timeRange = "${formatSeconds(startSec)} - ${formatSeconds(endSec)}"
                    currentStage = "Analisis Segmen ${i + 1} dari $totalSegments ($timeRange)"

                    val exactScenes = if (activeCutMode == PromptCutMode.PER_SCENE) {
                        val safeStart = segmentSpans.firstOrNull()?.startSec ?: 0L
                        val safeEnd = segmentSpans.lastOrNull()?.endSec ?: totalSeconds
                        val requiredKey = SceneCacheKey(
                            videoUri = video.uri.toString(),
                            startSec = safeStart,
                            endSec = safeEnd,
                            maxSceneDurationSec = activeMaxSceneDur
                        )
                        val spans = if (cachedSceneSpans != null && cachedSceneKey == requiredKey) {
                            cachedSceneSpans!!
                        } else {
                            SceneDetector.detectSceneCuts(
                                context = context,
                                videoUri = video.uri,
                                startSec = safeStart,
                                endSec = safeEnd,
                                maxSceneDurationSec = activeMaxSceneDur
                            ).also {
                                cachedSceneSpans = it
                                cachedSceneKey = requiredKey
                                _detectedSceneCount.value = it.size
                            }
                        }
                        SceneDetector.getScenesForSegment(spans, startSec, endSec)
                    } else {
                        emptyList()
                    }

                    var segmentSuccess = false
                    var lastQuotaException: GeminiApiException? = null

                    while (!segmentSuccess) {
                        activeModel = modelList.getOrElse(currentModelIndex) { selectedModelName }
                        Log.i(TAG, "[$currentStage] Memulai analisis dengan model '$activeModel'...")
                        _analysisState.value = AnalysisState.AnalyzingSegment(
                            currentSegment = i + 1,
                            totalSegments = totalSegments,
                            timeRange = timeRange,
                            message = "Menganalisis segmen ${i + 1} dari $totalSegments ($timeRange)...",
                            modelName = activeModel
                        )
                        AnalysisForegroundService.updateProgress(
                            context = context,
                            currentSegment = i + 1,
                            totalSegments = totalSegments,
                            model = activeModel,
                            message = "Menganalisis rentang $timeRange"
                        )

                        try {
                            val activeLanguage = narrationLanguageStorage.getLanguage()
                            val effectiveInterval = if (activeCutMode == PromptCutMode.PER_SCENE) 0 else _promptIntervalSeconds.value
                            val segmentOutput = geminiClient.analyzeSegment(
                                currentSegment = i + 1,
                                totalSegments = totalSegments,
                                fileUri = remoteFileUri,
                                mimeType = video.mimeType,
                                startSec = startSec,
                                endSec = endSec,
                                intervalSeconds = effectiveInterval,
                                previousCharacterSheet = allCharacterSheet,
                                isFirstSegment = (i == 0),
                                model = activeModel,
                                stylePrompt = activeStyle.promptDescription,
                                mode = activeMode,
                                language = activeLanguage,
                                exactSceneSpans = exactScenes,
                                onStatusUpdate = { statusMsg ->
                                    _analysisState.value = AnalysisState.AnalyzingSegment(
                                        currentSegment = i + 1,
                                        totalSegments = totalSegments,
                                        timeRange = timeRange,
                                        message = statusMsg,
                                        modelName = activeModel
                                    )
                                }
                            )

                            segmentSummaries.add(segmentOutput.segmentSummary)
                            segmentModels.add(activeModel)

                            // Menggabungkan karakter baru / pembaruan karakter
                            segmentOutput.characterSheet.forEach { newChar ->
                                val existingIdx = allCharacterSheet.indexOfFirst {
                                    it.name.equals(newChar.name, ignoreCase = true)
                                }
                                if (existingIdx >= 0) {
                                    if (newChar.description.isNotBlank()) {
                                        allCharacterSheet[existingIdx] = newChar
                                    }
                                } else {
                                    allCharacterSheet.add(newChar)
                                }
                            }

                            allTimeline.addAll(segmentOutput.timeline)

                            // Simpan hasil setiap segmen ke database Room SEGERA setelah segmen selesai!
                            activeEntityId?.let { id ->
                                val current = repository.getAnalysisById(id)
                                if (current != null) {
                                    val updatedEntity = current.copy(
                                        completedSegmentsCount = i + 1,
                                        characterSheetJson = AnalysisJsonAdapter.characterSheetToJson(allCharacterSheet),
                                        timelineJson = AnalysisJsonAdapter.timelineToJson(allTimeline),
                                        segmentSummariesJson = AnalysisJsonAdapter.summariesToJson(segmentSummaries),
                                        segmentModelsJson = AnalysisJsonAdapter.summariesToJson(segmentModels),
                                        status = if (i + 1 == totalSegments) "PROCESSING_NARRATION" else "IN_PROGRESS",
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    repository.update(updatedEntity)
                                    Log.i(TAG, "[$currentStage] Berhasil disimpan ke Room (Segmen ${i + 1} tersimpan dengan model $activeModel)")
                                }
                            }

                            segmentSuccess = true
                        } catch (e: GeminiApiException) {
                            if (e.isDailyQuota) {
                                modelUsageStorage.markQuotaExceeded(activeModel, e.retryDelaySeconds)
                                refreshSelectedModelUsage()
                            }
                            if (isAutoSwitch && e.isDailyQuota) {
                                Log.w(TAG, "[$currentStage] Kuota harian habis pada model '$activeModel'. Mencoba beralih ke model berikutnya...")
                                lastQuotaException = e
                                currentModelIndex++
                                if (currentModelIndex >= modelList.size) {
                                    Log.e(TAG, "[$currentStage] Semua model dalam daftar telah habis kuotanya.")
                                    throw lastQuotaException ?: e
                                }
                                continue
                            } else if (isAutoSwitch && lastQuotaException != null && (e.httpCode == 404 || e.userFriendlyMessage.contains("tidak dikenal", ignoreCase = true))) {
                                Log.w(TAG, "[$currentStage] Model '$activeModel' tidak dikenal (HTTP 404). Melewati ke model berikutnya...")
                                currentModelIndex++
                                if (currentModelIndex >= modelList.size) {
                                    Log.e(TAG, "[$currentStage] Semua model berikutnya tidak valid.")
                                    throw lastQuotaException ?: e
                                }
                                continue
                            } else {
                                throw e
                            }
                        }
                    }
                }

                // Tahap Akhir: Menyusun Narasi Lengkap Video
                currentStage = "Penyusunan Narasi Rekap Video"
                val currentLang = narrationLanguageStorage.getLanguage()

                var narration = ""
                var narrationSuccess = false

                if (activeMode != NarrationMode.REKAP_VOICEOVER) {
                    Log.i(TAG, "[$currentStage] Mode ${activeMode.displayName} dipilih. Menyusun transkrip lokal dari timeline.")
                    narration = formatTimelineTranscript(allTimeline, activeMode)
                    narrationSuccess = true
                } else {
                    Log.i(TAG, "[$currentStage] Menyusun narasi video dengan model '$activeModel' (mode: ${activeMode.displayName})...")
                    _analysisState.value = AnalysisState.GeneratingNarration(
                        "Menyusun narasi video lengkap berdasarkan ringkasan $totalSegments segmen...",
                        modelName = activeModel
                    )
                    AnalysisForegroundService.updateStatus(
                        context = context,
                        title = "Penyusunan Narasi Rekap",
                        message = "Menyusun narasi lengkap berdasarkan $totalSegments segmen...",
                        model = activeModel
                    )

                    while (!narrationSuccess) {
                        activeModel = modelList.getOrElse(currentModelIndex) { selectedModelName }
                        try {
                            narration = geminiClient.generateFinalNarration(
                                segmentSummaries = segmentSummaries,
                                characterSheet = allCharacterSheet,
                                model = activeModel,
                                language = currentLang,
                                onStatusUpdate = { msg ->
                                    _analysisState.value = AnalysisState.GeneratingNarration(msg, modelName = activeModel)
                                }
                            )
                            narrationSuccess = true
                        } catch (e: GeminiApiException) {
                            if (e.isDailyQuota) {
                                modelUsageStorage.markQuotaExceeded(activeModel, e.retryDelaySeconds)
                                refreshSelectedModelUsage()
                            }
                            if (isAutoSwitch && e.isDailyQuota) {
                                currentModelIndex++
                                if (currentModelIndex < modelList.size) {
                                    continue
                                }
                            } else if (isAutoSwitch && (e.httpCode == 404 || e.userFriendlyMessage.contains("tidak dikenal", ignoreCase = true))) {
                                currentModelIndex++
                                if (currentModelIndex < modelList.size) {
                                    continue
                                }
                            }
                            throw e
                        }
                    }
                }

                // Simpan status COMPLETED ke database Room
                activeEntityId?.let { id ->
                    val current = repository.getAnalysisById(id)
                    if (current != null) {
                        repository.update(
                            current.copy(
                                narration = narration,
                                status = "COMPLETED",
                                lastErrorStage = "",
                                lastErrorMessage = "",
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        Log.i(TAG, "Analisis ID $id ditandai COMPLETED di Room database")
                    }
                }

                val finalResult = AnimeAnalysisResult(
                    narration = narration,
                    characterSheet = allCharacterSheet,
                    timeline = allTimeline,
                    totalSegments = totalSegments,
                    durationFormatted = formatSeconds(totalSeconds),
                    intervalSeconds = if (activeCutMode == PromptCutMode.PER_SCENE) 0 else _promptIntervalSeconds.value,
                    videoName = video.displayName,
                    segmentModels = segmentModels,
                    segmentSummaries = segmentSummaries,
                    narrationMode = activeMode.displayName,
                    maxSceneDuration = activeMaxSceneDur
                )

                Log.i(TAG, "Analisis seluruh video selesai dengan sukses!")
                _analysisState.value = AnalysisState.Success(finalResult, styleName = activeStyle.name)
                AnalysisForegroundService.stop(context)
                AnalysisForegroundService.notifyCompleted(context, video.displayName)

            } catch (e: GeminiApiException) {
                Log.e(TAG, "GeminiApiException pada [$currentStage]: ${e.userFriendlyMessage}", e)
                AnalysisForegroundService.stop(context)
                if (e.isDailyQuota) {
                    val waitTime = if (e.retryDelaySeconds != null && e.retryDelaySeconds > 0) {
                        val hours = e.retryDelaySeconds / 3600
                        val minutes = (e.retryDelaySeconds % 3600) / 60
                        if (hours > 0) "$hours jam $minutes menit" else "$minutes menit"
                    } else {
                        modelUsageStorage.getFormattedResetTime(modelStorage.getSelectedModel())
                    }
                    AnalysisForegroundService.notifyQuotaExceeded(
                        context = context,
                        model = modelStorage.getSelectedModel(),
                        waitTimeFormatted = waitTime
                    )
                } else {
                    AnalysisForegroundService.notifyFailed(
                        context = context,
                        stage = e.stage,
                        errorMessage = e.userFriendlyMessage
                    )
                }
                if (e.isFileProcessingTimeout) {
                    // JANGAN hapus sesi! Simpan analisis dengan status menunggu ("WAITING")
                    activeEntityId?.let { id ->
                        val ent = repository.getAnalysisById(id)
                        if (ent != null) {
                            repository.update(
                                ent.copy(
                                    status = "WAITING",
                                    lastErrorStage = "Menunggu Pemrosesan Video",
                                    lastErrorMessage = e.userFriendlyMessage,
                                    geminiFileRemoteName = remoteFileName,
                                    geminiFileUri = remoteFileUri,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                } else {
                    handleErrorAndSaveToDb(activeEntityId, currentStage, e.userFriendlyMessage)
                }
                val resumeInfo = checkCanResume(activeEntityId)
                val savedEntity = if (activeEntityId != null) repository.getAnalysisById(activeEntityId) else null
                val savedSummaries = if (savedEntity != null) {
                    AnalysisJsonAdapter.jsonToSummaries(savedEntity.segmentSummariesJson)
                } else emptyList()

                _analysisState.value = AnalysisState.Error(
                    stage = e.stage,
                    httpCode = e.httpCode,
                    rawApiMessage = e.rawMessage,
                    errorMessage = e.userFriendlyMessage,
                    canRetry = !e.isDailyQuota && !e.isFileProcessingTimeout && !e.isFileProcessingFailed,
                    canResume = if (e.isDailyQuota) true else resumeInfo.canResume,
                    resumeSegment = resumeInfo.resumeSegment,
                    canSkipAndFinish = resumeInfo.canSkipAndFinish,
                    analysisId = activeEntityId,
                    isDailyQuota = e.isDailyQuota,
                    retryDelaySeconds = e.retryDelaySeconds,
                    completedSegmentsCount = savedEntity?.completedSegmentsCount ?: 0,
                    totalSegments = savedEntity?.totalSegments ?: 0,
                    completedSummaries = savedSummaries,
                    activeKeyName = apiKeyStorage.getActiveKeyName(),
                    modelName = modelStorage.getSelectedModel(),
                    isFileProcessingTimeout = e.isFileProcessingTimeout,
                    isFileProcessingFailed = e.isFileProcessingFailed
                )
            } catch (e: CancellationException) {
                Log.i(TAG, "Analisis dibatalkan oleh pengguna.")
                AnalysisForegroundService.stop(context)
                _analysisState.value = AnalysisState.Idle
            } catch (e: Exception) {
                AnalysisForegroundService.stop(context)
                val isAccessLost = e is SecurityException ||
                        e is java.io.FileNotFoundException ||
                        e.cause is SecurityException ||
                        e.cause is java.io.FileNotFoundException ||
                        (e.message?.contains("Permission Denial", ignoreCase = true) == true) ||
                        (e.message?.contains("requires that you obtain", ignoreCase = true) == true)

                if (isAccessLost) {
                    Log.w(TAG, "Akses berkas video hilang (SecurityException / FileNotFoundException): ${e.message}")
                    AnalysisForegroundService.notifyFailed(
                        context = context,
                        stage = "Akses Berkas Video",
                        errorMessage = "Video tidak bisa dibuka lagi. Pilih video yang sama."
                    )
                    val resumeInfo = checkCanResume(activeEntityId)
                    val savedEntity = if (activeEntityId != null) repository.getAnalysisById(activeEntityId) else null
                    _analysisState.value = AnalysisState.Error(
                        stage = "Akses Berkas Video",
                        httpCode = null,
                        rawApiMessage = "", // JANGAN tampilkan stack trace sesuai instruksi nomor 3!
                        errorMessage = "Video tidak bisa dibuka lagi. Pilih video yang sama.",
                        canRetry = false,
                        canResume = false,
                        canSkipAndFinish = resumeInfo.canSkipAndFinish || (savedEntity != null && savedEntity.completedSegmentsCount > 0),
                        analysisId = activeEntityId,
                        isVideoAccessLost = true,
                        expectedDurationMs = savedEntity?.durationMs ?: 0L,
                        expectedSizeBytes = savedEntity?.videoSizeBytes ?: 0L,
                        activeKeyName = apiKeyStorage.getActiveKeyName(),
                        modelName = modelStorage.getSelectedModel()
                    )
                } else {
                    Log.e(TAG, "Kesalahan tidak terduga pada [$currentStage]: ${e.message}", e)
                    val msg = e.localizedMessage ?: "Terjadi kendala pada sistem."
                    AnalysisForegroundService.notifyFailed(
                        context = context,
                        stage = currentStage,
                        errorMessage = msg
                    )
                    handleErrorAndSaveToDb(activeEntityId, currentStage, msg)
                    val resumeInfo = checkCanResume(activeEntityId)
                    val savedEntity = if (activeEntityId != null) repository.getAnalysisById(activeEntityId) else null
                    val savedSummaries = if (savedEntity != null) {
                        AnalysisJsonAdapter.jsonToSummaries(savedEntity.segmentSummariesJson)
                    } else emptyList()

                    _analysisState.value = AnalysisState.Error(
                        stage = currentStage,
                        httpCode = null,
                        rawApiMessage = e.stackTraceToString(),
                        errorMessage = msg,
                        canRetry = true,
                        canResume = resumeInfo.canResume,
                        resumeSegment = resumeInfo.resumeSegment,
                        canSkipAndFinish = resumeInfo.canSkipAndFinish,
                        analysisId = activeEntityId,
                        completedSegmentsCount = savedEntity?.completedSegmentsCount ?: 0,
                        totalSegments = savedEntity?.totalSegments ?: 0,
                        completedSummaries = savedSummaries,
                        activeKeyName = apiKeyStorage.getActiveKeyName(),
                        modelName = modelStorage.getSelectedModel()
                    )
                }
            }
        }
    }

    /**
     * Memproses video yang dipilih saat pemulihan video hilang ("Video tidak bisa dibuka lagi. Pilih video yang sama.").
     * Mencocokkan durasi dan ukuran berkas dengan data tersimpan (toleransi durasi 2 detik).
     * Jika cocok, upload ulang lalu lanjutkan analisis dari segmen yang belum selesai.
     * Jika tidak cocok, tampilkan peringatan bahwa itu video berbeda.
     */
    fun onRecoveryVideoPicked(newUri: Uri) {
        val currentErrorState = _analysisState.value as? AnalysisState.Error
        val targetId = currentErrorState?.analysisId ?: activeAnalysisId
        if (targetId == null) {
            Log.w(TAG, "onRecoveryVideoPicked: ID analisis target tidak ditemukan")
            return
        }

        viewModelScope.launch {
            try {
                val savedEntity = repository.getAnalysisById(targetId)
                if (savedEntity == null) {
                    _analysisState.value = AnalysisState.Error(
                        stage = "Verifikasi Video",
                        httpCode = null,
                        rawApiMessage = "",
                        errorMessage = "Data analisis tidak ditemukan di database.",
                        canRetry = false
                    )
                    return@launch
                }

                _analysisState.value = AnalysisState.Preparing("Memeriksa kesesuaian video anime yang dipilih...")

                // Baca metadata berkas video yang baru dipilih
                val newVideo = withContext(Dispatchers.IO) { loadVideoMetadata(newUri) }
                val selectedDurationMs = newVideo.durationMs
                val selectedSizeBytes = newVideo.sizeBytes
                val savedDurationMs = savedEntity.durationMs
                val savedSizeBytes = savedEntity.videoSizeBytes

                Log.i(TAG, "Mencocokkan video: durasi=${selectedDurationMs}ms, ukuran=${selectedSizeBytes}bytes vs tersimpan: durasi=${savedDurationMs}ms, ukuran=${savedSizeBytes}bytes")

                // 1. Cocokkan durasi dengan toleransi 2 detik (2000 ms)
                val durationDiffMs = kotlin.math.abs(selectedDurationMs - savedDurationMs)
                val durationMatches = if (savedDurationMs > 0 && selectedDurationMs > 0) {
                    durationDiffMs <= 2000L
                } else {
                    true
                }

                // 2. Cocokkan ukuran berkas (toleransi wajar)
                val sizeDiffBytes = kotlin.math.abs(selectedSizeBytes - savedSizeBytes)
                val sizeMatches = if (savedSizeBytes > 0 && selectedSizeBytes > 0) {
                    sizeDiffBytes <= 2048L || selectedSizeBytes == savedSizeBytes
                } else {
                    true
                }

                val isMatch = durationMatches && sizeMatches

                if (!isMatch) {
                    Log.w(TAG, "Video yang dipilih berbeda! Durasi beda: ${durationDiffMs}ms, ukuran beda: ${sizeDiffBytes}bytes")
                    _analysisState.value = AnalysisState.Error(
                        stage = "Verifikasi Video",
                        httpCode = null,
                        rawApiMessage = "", // JANGAN tampilkan stack trace!
                        errorMessage = "Video yang Anda pilih berbeda dengan video analisis sebelumnya! Durasi (${newVideo.durationFormatted} vs ${savedEntity.formattedDuration}) atau ukuran berkas tidak cocok.\n\nSilakan pilih berkas video yang sama persis.",
                        canRetry = false,
                        canResume = false,
                        canSkipAndFinish = savedEntity.completedSegmentsCount > 0,
                        analysisId = targetId,
                        isVideoAccessLost = true,
                        expectedDurationMs = savedDurationMs,
                        expectedSizeBytes = savedSizeBytes
                    )
                } else {
                    Log.i(TAG, "Video cocok dengan rekaman analisis ID $targetId! Memperbarui URI dan mengunggah ulang...")
                    val updatedEntity = savedEntity.copy(
                        videoUriString = newUri.toString(),
                        videoName = newVideo.displayName,
                        videoSizeBytes = if (selectedSizeBytes > 0) selectedSizeBytes else savedSizeBytes,
                        durationMs = if (selectedDurationMs > 0) selectedDurationMs else savedDurationMs,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.update(updatedEntity)
                    _selectedVideo.value = newVideo
                    activeAnalysisId = targetId

                    // Upload ulang lalu lanjutkan dari segmen yang belum selesai
                    startAnalysis(resumeId = targetId, forceReupload = true)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Gagal memproses berkas video yang dipilih: ${e.message}", e)
                _analysisState.value = AnalysisState.Error(
                    stage = "Verifikasi Video",
                    httpCode = null,
                    rawApiMessage = "",
                    errorMessage = "Gagal membaca berkas video yang dipilih. Pastikan berkas video masih dapat diakses.",
                    canRetry = false,
                    canResume = false,
                    canSkipAndFinish = true,
                    analysisId = targetId,
                    isVideoAccessLost = true
                )
            }
        }
    }

    /**
     * Menyelesaikan analisis tanpa segmen saat ini (misalnya saat gagal atau tertunda di segmen 9).
     * Langsung membuat narasi akhir dari segmen yang sudah tersimpan (contoh: segmen 1-8)
     * dan menandai analisis sebagai COMPLETED dengan catatan "segmen terakhir dilewati".
     */
    fun finishWithoutCurrentSegment(analysisId: Long? = null) {
        val targetId = analysisId ?: activeAnalysisId ?: historyList.value.firstOrNull { !it.isCompleted && it.completedSegmentsCount > 0 }?.id
        if (targetId == null) {
            Log.w(TAG, "finishWithoutCurrentSegment: Target ID analisis tidak ditemukan")
            return
        }

        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            try {
                val entity = repository.getAnalysisById(targetId)
                if (entity == null) {
                    Log.e(TAG, "finishWithoutCurrentSegment: Entri analisis ID $targetId tidak ada di database")
                    return@launch
                }

                val summaries = AnalysisJsonAdapter.jsonToSummaries(entity.segmentSummariesJson)
                val characterSheet = AnalysisJsonAdapter.jsonToCharacterSheet(entity.characterSheetJson)
                val timeline = AnalysisJsonAdapter.jsonToTimeline(entity.timelineJson)
                val segmentModels = AnalysisJsonAdapter.jsonToSummaries(entity.segmentModelsJson)
                val selectedModelName = modelStorage.getSelectedModel()

                val savedCount = summaries.size.coerceAtLeast(entity.completedSegmentsCount)
                Log.i(TAG, "Menyelesaikan analisis ID $targetId tanpa segmen berikutnya dengan model '$selectedModelName'. Segmen tersimpan: $savedCount")

                val analysisMode = narrationModeStorage.getModeForAnalysis(targetId) ?: narrationModeStorage.getMode()
                val currentLang = narrationLanguageStorage.getLanguage()

                val finalNarration = if (analysisMode != NarrationMode.REKAP_VOICEOVER) {
                    Log.i(TAG, "finishWithoutCurrentSegment: Mode ${analysisMode.displayName} dipilih. Menyusun transkrip lokal dari timeline.")
                    val note = "[Catatan: Segmen terakhir dilewati]\n\n"
                    val transcript = formatTimelineTranscript(timeline, analysisMode)
                    if (transcript.isNotBlank()) note + transcript else ""
                } else {
                    _analysisState.value = AnalysisState.GeneratingNarration(
                        message = "Menyusun narasi rekap akhir dari $savedCount segmen yang tersimpan (segmen terakhir dilewati)...",
                        modelName = selectedModelName
                    )

                    val rawNarration = try {
                        geminiClient.generateFinalNarration(
                            segmentSummaries = summaries,
                            characterSheet = characterSheet,
                            model = selectedModelName,
                            language = currentLang,
                            onStatusUpdate = { msg ->
                                _analysisState.value = AnalysisState.GeneratingNarration(msg, modelName = selectedModelName)
                            }
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Gagal generate narasi via AI (${e.message}), memakai rangkuman segmen lokal.")
                        summaries.mapIndexed { idx, s ->
                            "Segmen ${idx + 1}: $s"
                        }.joinToString("\n\n")
                    }

                    val note = "[Catatan: Segmen terakhir dilewati]\n\n"
                    note + rawNarration
                }

                val updatedEntity = entity.copy(
                    narration = finalNarration,
                    status = "COMPLETED",
                    lastErrorStage = "Selesai (Segmen dilewati)",
                    lastErrorMessage = "Segmen terakhir dilewati",
                    completedSegmentsCount = savedCount,
                    totalSegments = savedCount,
                    updatedAt = System.currentTimeMillis()
                )
                repository.update(updatedEntity)
                activeAnalysisId = null
                Log.i(TAG, "Analisis ID $targetId selesai tanpa segmen terakhir dan ditandai COMPLETED di Room.")

                val finalResult = AnimeAnalysisResult(
                    narration = finalNarration,
                    characterSheet = characterSheet,
                    timeline = timeline,
                    totalSegments = savedCount,
                    durationFormatted = entity.formattedDuration,
                    intervalSeconds = entity.intervalSeconds,
                    videoName = entity.videoName,
                    note = "Segmen terakhir dilewati",
                    segmentModels = segmentModels,
                    segmentSummaries = summaries,
                    narrationMode = analysisMode.displayName,
                    maxSceneDuration = promptCutModeStorage.getMaxDurationForAnalysis(targetId)
                )
                val savedStyle = visualStyleStorage.getStyleForAnalysis(targetId) ?: visualStyleStorage.getSelectedStyle()
                _analysisState.value = AnalysisState.Success(finalResult, styleName = savedStyle.name)
                AnalysisForegroundService.stop(context)
                AnalysisForegroundService.notifyCompleted(context, entity.videoName)

            } catch (e: GeminiApiException) {
                Log.e(TAG, "GeminiApiException saat menyelesaikan analisis tanpa segmen: ${e.userFriendlyMessage}", e)
                AnalysisForegroundService.stop(context)
                AnalysisForegroundService.notifyFailed(context, e.stage, e.userFriendlyMessage)
                _analysisState.value = AnalysisState.Error(
                    stage = e.stage,
                    httpCode = e.httpCode,
                    rawApiMessage = e.rawMessage,
                    errorMessage = e.userFriendlyMessage,
                    activeKeyName = apiKeyStorage.getActiveKeyName(),
                    modelName = modelStorage.getSelectedModel()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Kesalahan saat menyelesaikan analisis tanpa segmen: ${e.message}", e)
                AnalysisForegroundService.stop(context)
                AnalysisForegroundService.notifyFailed(context, "Selesaikan Tanpa Segmen", e.localizedMessage ?: "Gagal")
                _analysisState.value = AnalysisState.Error(
                    stage = "Selesaikan Tanpa Segmen",
                    httpCode = null,
                    rawApiMessage = e.stackTraceToString(),
                    errorMessage = "Gagal menyelesaikan analisis: ${e.localizedMessage}",
                    activeKeyName = apiKeyStorage.getActiveKeyName(),
                    modelName = modelStorage.getSelectedModel()
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        AnalysisForegroundService.stop(context)
    }

    private suspend fun handleErrorAndSaveToDb(entityId: Long?, stage: String, errorMsg: String) {
        if (entityId == null) return
        try {
            val entity = repository.getAnalysisById(entityId)
            if (entity != null && entity.status != "COMPLETED") {
                repository.update(
                    entity.copy(
                        status = "FAILED",
                        lastErrorStage = stage,
                        lastErrorMessage = errorMsg,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal memperbarui status error ke database: ${e.message}")
        }
    }

    data class ResumeCheckResult(
        val canResume: Boolean,
        val resumeSegment: Int,
        val canSkipAndFinish: Boolean
    )

    private suspend fun checkCanResume(entityId: Long?): ResumeCheckResult {
        if (entityId == null) return ResumeCheckResult(false, 1, false)
        return try {
            val entity = repository.getAnalysisById(entityId)
            if (entity != null && entity.completedSegmentsCount > 0 && entity.status != "COMPLETED") {
                ResumeCheckResult(
                    canResume = entity.completedSegmentsCount < entity.totalSegments,
                    resumeSegment = entity.completedSegmentsCount + 1,
                    canSkipAndFinish = true
                )
            } else {
                ResumeCheckResult(false, 1, false)
            }
        } catch (_: Exception) {
            ResumeCheckResult(false, 1, false)
        }
    }

    private fun loadVideoMetadata(uri: Uri): SelectedVideo {
        var displayName = "video_anime.mp4"
        var sizeBytes = 0L
        val contentResolver = context.contentResolver

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)?.let { displayName = it }
                    }
                    if (sizeIndex != -1) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal query cursor metadata video: ${e.message}")
        }

        val mimeType = contentResolver.getType(uri) ?: "video/mp4"

        var durationMs = 0L
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull() ?: 0L
            retriever.release()
        } catch (e: Exception) {
            Log.w(TAG, "Gagal membaca durasi video via MediaMetadataRetriever: ${e.message}")
        }

        if (sizeBytes <= 0L) {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    sizeBytes = stream.available().toLong()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gagal membaca estimasi ukuran video via stream: ${e.message}")
            }
        }

        return SelectedVideo(
            uri = uri,
            displayName = displayName,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            mimeType = mimeType
        )
    }

    private fun formatSeconds(sec: Long): String {
        val m = sec / 60
        val s = sec % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }
}
