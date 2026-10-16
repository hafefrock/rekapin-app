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
import com.example.data.prefs.ApiKeyStorage
import com.example.data.repository.AnimeAnalysisRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.ceil

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "RekapinVM"
    }

    private val context: Context get() = getApplication()
    val apiKeyStorage = ApiKeyStorage(context)

    private val geminiClient = GeminiApiClient(
        apiKeyProvider = { apiKeyStorage.getEffectiveApiKey() }
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

    // Pilihan interval visual prompt: 3 detik atau 5 detik (default 5 detik)
    private val _promptIntervalSeconds = MutableStateFlow(5)
    val promptIntervalSeconds: StateFlow<Int> = _promptIntervalSeconds.asStateFlow()

    private val _analysisState = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState: StateFlow<AnalysisState> = _analysisState.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    // Layar Riwayat
    private val _isHistoryOpen = MutableStateFlow(false)
    val isHistoryOpen: StateFlow<Boolean> = _isHistoryOpen.asStateFlow()

    // Detail riwayat yang sedang dibuka
    private val _viewingHistoryItem = MutableStateFlow<AnimeAnalysisEntity?>(null)
    val viewingHistoryItem: StateFlow<AnimeAnalysisEntity?> = _viewingHistoryItem.asStateFlow()

    // ID analisis yang sedang aktif berjalan di database
    private var activeAnalysisId: Long? = null
    private var currentJob: Job? = null

    fun setIntervalSeconds(seconds: Int) {
        if (seconds == 3 || seconds == 5) {
            _promptIntervalSeconds.value = seconds
            Log.d(TAG, "Interval visual prompt diubah menjadi: $seconds detik")
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

    fun saveApiKey(newKey: String) {
        apiKeyStorage.saveApiKey(newKey)
        Log.i(TAG, "API Key baru disimpan (panjang: ${newKey.length} karakter)")
        closeSettings()
    }

    fun onVideoPicked(uri: Uri) {
        viewModelScope.launch {
            Log.i(TAG, "Video dipilih dari picker, URI: $uri")
            val video = withContext(Dispatchers.IO) { loadVideoMetadata(uri) }
            _selectedVideo.value = video
            _analysisState.value = AnalysisState.Idle
            Log.i(TAG, "Metadata video berhasil dimuat: ${video.displayName}, durasi: ${video.durationFormatted}, ukuran: ${video.sizeFormatted}")
        }
    }

    fun clearSelectedVideo() {
        cancelAnalysis()
        _selectedVideo.value = null
        activeAnalysisId = null
        _analysisState.value = AnalysisState.Idle
        Log.d(TAG, "Video terpilih dibersihkan")
    }

    fun resetAnalysis() {
        cancelAnalysis()
        activeAnalysisId = null
        _analysisState.value = AnalysisState.Idle
        Log.d(TAG, "Analisis di-reset kembali ke Idle")
    }

    fun cancelAnalysis() {
        Log.i(TAG, "Membatalkan analisis aktif...")
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
     */
    fun startAnalysis(resumeId: Long? = null) {
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
        currentJob = viewModelScope.launch {
            var currentStage = "Mempersiapkan Berkas"
            var activeEntityId: Long? = resumeId ?: activeAnalysisId

            try {
                var existingEntity: AnimeAnalysisEntity? = null
                if (activeEntityId != null) {
                    existingEntity = repository.getAnalysisById(activeEntityId)
                }

                // Tentukan Video yang akan dianalisis
                var video = _selectedVideo.value
                if (video == null && existingEntity != null) {
                    val parsedUri = Uri.parse(existingEntity.videoUriString)
                    video = withContext(Dispatchers.IO) { loadVideoMetadata(parsedUri) }
                    _selectedVideo.value = video
                    _promptIntervalSeconds.value = existingEntity.intervalSeconds
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

                // Tentukan total detik & total segmen
                val totalSeconds = if (video.durationMs > 0) {
                    video.durationMs / 1000
                } else {
                    1500L
                }

                val totalSegments = ceil(totalSeconds.toDouble() / GeminiConstants.SEGMENT_DURATION_SECONDS)
                    .toInt()
                    .coerceAtLeast(1)

                // Persiapan state data akumulasi
                val allCharacterSheet = mutableListOf<CharacterInfo>()
                val allTimeline = mutableListOf<TimelineItem>()
                val segmentSummaries = mutableListOf<String>()
                var startIndex = 0
                var remoteFileName = ""
                var remoteFileUri = ""

                // Jika Melanjutkan dari database
                if (existingEntity != null && existingEntity.completedSegmentsCount > 0 && existingEntity.completedSegmentsCount < totalSegments) {
                    startIndex = existingEntity.completedSegmentsCount
                    allCharacterSheet.addAll(AnalysisJsonAdapter.jsonToCharacterSheet(existingEntity.characterSheetJson))
                    allTimeline.addAll(AnalysisJsonAdapter.jsonToTimeline(existingEntity.timelineJson))
                    segmentSummaries.addAll(AnalysisJsonAdapter.jsonToSummaries(existingEntity.segmentSummariesJson))
                    remoteFileName = existingEntity.geminiFileRemoteName
                    remoteFileUri = existingEntity.geminiFileUri
                    activeEntityId = existingEntity.id
                    activeAnalysisId = existingEntity.id
                    Log.i(TAG, "Melanjutkan analisis ID ${existingEntity.id} dari segmen ${startIndex + 1} dengan ${allCharacterSheet.size} karakter tersimpan.")
                } else if (existingEntity == null) {
                    // Buat rekaman awal baru di Room database
                    val newEntity = AnimeAnalysisEntity(
                        videoUriString = video.uri.toString(),
                        videoName = video.displayName,
                        videoSizeBytes = video.sizeBytes,
                        durationMs = video.durationMs,
                        mimeType = video.mimeType,
                        intervalSeconds = _promptIntervalSeconds.value,
                        totalSegments = totalSegments,
                        completedSegmentsCount = 0,
                        geminiFileRemoteName = "",
                        geminiFileUri = "",
                        status = "IN_PROGRESS",
                        characterSheetJson = "[]",
                        timelineJson = "[]",
                        segmentSummariesJson = "[]",
                        narration = ""
                    )
                    activeEntityId = repository.insert(newEntity)
                    activeAnalysisId = activeEntityId
                    Log.i(TAG, "Membuat entri analisis baru di Room database ID: $activeEntityId")
                }

                // Validasi File di Gemini API: Cek apakah masih ACTIVE
                currentStage = "Verifikasi Status Berkas di Gemini API"
                _analysisState.value = AnalysisState.Preparing("Memeriksa status video di Google AI...")
                var needUpload = false

                if (remoteFileName.isNotBlank()) {
                    val isStillActive = geminiClient.isFileStillActive(remoteFileName)
                    if (!isStillActive) {
                        Log.w(TAG, "Berkas $remoteFileName di Gemini telah kedaluwarsa atau tidak aktif. Perlu unggah ulang.")
                        needUpload = true
                    } else {
                        Log.i(TAG, "Berkas $remoteFileName di Gemini masih ACTIVE. Siap langsung digunakan kembali.")
                    }
                } else {
                    needUpload = true
                }

                // Jika perlu upload ulang (awal baru atau file lama expired)
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
                                message = if (startIndex > 0)
                                    "Mengunggah ulang video untuk melanjutkan segmen ${startIndex + 1} ($percent%)..."
                                else
                                    "Mengunggah video ke Gemini File API ($percent%)..."
                            )
                        }
                    )

                    currentStage = "Menunggu Status Video ACTIVE"
                    _analysisState.value = AnalysisState.PollingFileStatus(
                        "Menunggu verifikasi status video di server AI..."
                    )
                    val activeFile = geminiClient.waitForFileActive(uploadedFile.name) { statusMsg ->
                        _analysisState.value = AnalysisState.PollingFileStatus(statusMsg)
                    }

                    remoteFileName = activeFile.name
                    remoteFileUri = activeFile.uri

                    // Update informasi file remote ke database
                    activeEntityId?.let { id ->
                        val ent = repository.getAnalysisById(id)
                        if (ent != null) {
                            repository.update(
                                ent.copy(
                                    geminiFileRemoteName = remoteFileName,
                                    geminiFileUri = remoteFileUri,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }

                // Loop Analisis per segmen (mulai dari startIndex)
                for (i in startIndex until totalSegments) {
                    val startSec = i * GeminiConstants.SEGMENT_DURATION_SECONDS
                    val endSec = minOf((i + 1) * GeminiConstants.SEGMENT_DURATION_SECONDS, totalSeconds)
                    val timeRange = "${formatSeconds(startSec)} - ${formatSeconds(endSec)}"
                    currentStage = "Analisis Segmen ${i + 1} dari $totalSegments ($timeRange)"

                    Log.i(TAG, "[$currentStage] Memulai analisis...")
                    _analysisState.value = AnalysisState.AnalyzingSegment(
                        currentSegment = i + 1,
                        totalSegments = totalSegments,
                        timeRange = timeRange,
                        message = "Menganalisis segmen ${i + 1} dari $totalSegments ($timeRange)..."
                    )

                    val segmentOutput = geminiClient.analyzeSegment(
                        currentSegment = i + 1,
                        totalSegments = totalSegments,
                        fileUri = remoteFileUri,
                        mimeType = video.mimeType,
                        startSec = startSec,
                        endSec = endSec,
                        intervalSeconds = _promptIntervalSeconds.value,
                        previousCharacterSheet = allCharacterSheet,
                        isFirstSegment = (i == 0),
                        onStatusUpdate = { statusMsg ->
                            _analysisState.value = AnalysisState.AnalyzingSegment(
                                currentSegment = i + 1,
                                totalSegments = totalSegments,
                                timeRange = timeRange,
                                message = statusMsg
                            )
                        }
                    )

                    segmentSummaries.add(segmentOutput.segmentSummary)

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
                                status = if (i + 1 == totalSegments) "PROCESSING_NARRATION" else "IN_PROGRESS",
                                updatedAt = System.currentTimeMillis()
                            )
                            repository.update(updatedEntity)
                            Log.i(TAG, "[$currentStage] Berhasil disimpan ke Room (Segmen ${i + 1} tersimpan)")
                        }
                    }
                }

                // Tahap Akhir: Menyusun Narasi Lengkap Episode
                currentStage = "Penyusunan Narasi Rekap Episode"
                Log.i(TAG, "[$currentStage] Menyusun narasi episode...")
                _analysisState.value = AnalysisState.GeneratingNarration(
                    "Menyusun narasi episode lengkap berdasarkan ringkasan $totalSegments segmen..."
                )

                val narration = geminiClient.generateFinalNarration(
                    segmentSummaries = segmentSummaries,
                    characterSheet = allCharacterSheet,
                    onStatusUpdate = { msg ->
                        _analysisState.value = AnalysisState.GeneratingNarration(msg)
                    }
                )

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
                    intervalSeconds = _promptIntervalSeconds.value,
                    videoName = video.displayName
                )

                Log.i(TAG, "Analisis seluruh episode selesai dengan sukses!")
                _analysisState.value = AnalysisState.Success(finalResult)

            } catch (e: GeminiApiException) {
                Log.e(TAG, "GeminiApiException pada [$currentStage]: ${e.userFriendlyMessage}", e)
                handleErrorAndSaveToDb(activeEntityId, currentStage, e.userFriendlyMessage)
                val (canResume, resumeSeg) = checkCanResume(activeEntityId)
                _analysisState.value = AnalysisState.Error(
                    stage = e.stage,
                    httpCode = e.httpCode,
                    rawApiMessage = e.rawMessage,
                    errorMessage = e.userFriendlyMessage,
                    canRetry = true,
                    canResume = canResume,
                    resumeSegment = resumeSeg,
                    analysisId = activeEntityId
                )
            } catch (e: CancellationException) {
                Log.i(TAG, "Analisis dibatalkan oleh pengguna.")
                _analysisState.value = AnalysisState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Kesalahan tidak terduga pada [$currentStage]: ${e.message}", e)
                val msg = e.localizedMessage ?: "Terjadi kendala pada sistem."
                handleErrorAndSaveToDb(activeEntityId, currentStage, msg)
                val (canResume, resumeSeg) = checkCanResume(activeEntityId)
                _analysisState.value = AnalysisState.Error(
                    stage = currentStage,
                    httpCode = null,
                    rawApiMessage = e.stackTraceToString(),
                    errorMessage = msg,
                    canRetry = true,
                    canResume = canResume,
                    resumeSegment = resumeSeg,
                    analysisId = activeEntityId
                )
            }
        }
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

    private suspend fun checkCanResume(entityId: Long?): Pair<Boolean, Int> {
        if (entityId == null) return Pair(false, 1)
        return try {
            val entity = repository.getAnalysisById(entityId)
            if (entity != null && entity.completedSegmentsCount > 0 && entity.completedSegmentsCount < entity.totalSegments) {
                Pair(true, entity.completedSegmentsCount + 1)
            } else {
                Pair(false, 1)
            }
        } catch (_: Exception) {
            Pair(false, 1)
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
