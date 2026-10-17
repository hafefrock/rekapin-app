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
import com.example.util.SegmentCalculator
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

                // 2. Hitung segmen dengan aturan:
                // - End offset segmen terakhir dibatasi ke durasi video sebenarnya.
                // - Jika segmen terakhir lebih pendek dari 90 detik, gabungkan ke segmen sebelumnya.
                val segmentSpans = SegmentCalculator.calculateSegments(totalSeconds)
                val totalSegments = segmentSpans.size
                Log.i(TAG, "Durasi video: ${totalSeconds}s, Total segmen: $totalSegments (rentang: ${segmentSpans.map { "${it.startSec}-${it.endSec}s" }})")

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
                    if (!forceReupload) {
                        remoteFileName = existingEntity.geminiFileRemoteName
                        remoteFileUri = existingEntity.geminiFileUri
                    }
                    activeEntityId = existingEntity.id
                    activeAnalysisId = existingEntity.id
                    Log.i(TAG, "Melanjutkan analisis ID ${existingEntity.id} dari segmen ${startIndex + 1} dengan ${allCharacterSheet.size} karakter tersimpan.")
                } else if (existingEntity == null) {
                    // Buat rekaman awal baru di Room database
                    val newEntity = AnimeAnalysisEntity(
                        videoUriString = video.uri.toString(),
                        videoName = video.displayName,
                        videoSizeBytes = video.sizeBytes,
                        durationMs = if (actualDurationMs > 0) actualDurationMs else video.durationMs,
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

                if (!forceReupload && remoteFileName.isNotBlank()) {
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

                // Jika perlu upload ulang (awal baru atau file lama expired atau forceReupload)
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
                    val segment = segmentSpans[i]
                    val startSec = segment.startSec
                    val endSec = minOf(segment.endSec, totalSeconds)
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
                val resumeInfo = checkCanResume(activeEntityId)
                _analysisState.value = AnalysisState.Error(
                    stage = e.stage,
                    httpCode = e.httpCode,
                    rawApiMessage = e.rawMessage,
                    errorMessage = e.userFriendlyMessage,
                    canRetry = true,
                    canResume = resumeInfo.canResume,
                    resumeSegment = resumeInfo.resumeSegment,
                    canSkipAndFinish = resumeInfo.canSkipAndFinish,
                    analysisId = activeEntityId
                )
            } catch (e: CancellationException) {
                Log.i(TAG, "Analisis dibatalkan oleh pengguna.")
                _analysisState.value = AnalysisState.Idle
            } catch (e: Exception) {
                val isAccessLost = e is SecurityException ||
                        e is java.io.FileNotFoundException ||
                        e.cause is SecurityException ||
                        e.cause is java.io.FileNotFoundException ||
                        (e.message?.contains("Permission Denial", ignoreCase = true) == true) ||
                        (e.message?.contains("requires that you obtain", ignoreCase = true) == true)

                if (isAccessLost) {
                    Log.w(TAG, "Akses berkas video hilang (SecurityException / FileNotFoundException): ${e.message}")
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
                        expectedSizeBytes = savedEntity?.videoSizeBytes ?: 0L
                    )
                } else {
                    Log.e(TAG, "Kesalahan tidak terduga pada [$currentStage]: ${e.message}", e)
                    val msg = e.localizedMessage ?: "Terjadi kendala pada sistem."
                    handleErrorAndSaveToDb(activeEntityId, currentStage, msg)
                    val resumeInfo = checkCanResume(activeEntityId)
                    _analysisState.value = AnalysisState.Error(
                        stage = currentStage,
                        httpCode = null,
                        rawApiMessage = e.stackTraceToString(),
                        errorMessage = msg,
                        canRetry = true,
                        canResume = resumeInfo.canResume,
                        resumeSegment = resumeInfo.resumeSegment,
                        canSkipAndFinish = resumeInfo.canSkipAndFinish,
                        analysisId = activeEntityId
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

                val savedCount = summaries.size.coerceAtLeast(entity.completedSegmentsCount)
                Log.i(TAG, "Menyelesaikan analisis ID $targetId tanpa segmen berikutnya. Segmen tersimpan: $savedCount")

                _analysisState.value = AnalysisState.GeneratingNarration(
                    "Menyusun narasi rekap akhir dari $savedCount segmen yang tersimpan (segmen terakhir dilewati)..."
                )

                val rawNarration = try {
                    geminiClient.generateFinalNarration(
                        segmentSummaries = summaries,
                        characterSheet = characterSheet,
                        onStatusUpdate = { msg ->
                            _analysisState.value = AnalysisState.GeneratingNarration(msg)
                        }
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Gagal generate narasi via AI (${e.message}), memakai rangkuman segmen lokal.")
                    summaries.mapIndexed { idx, s ->
                        "Segmen ${idx + 1}: $s"
                    }.joinToString("\n\n")
                }

                val note = "[Catatan: Segmen terakhir dilewati]\n\n"
                val finalNarration = note + rawNarration

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
                    note = "Segmen terakhir dilewati"
                )
                _analysisState.value = AnalysisState.Success(finalResult)

            } catch (e: Exception) {
                Log.e(TAG, "Kesalahan saat menyelesaikan analisis tanpa segmen: ${e.message}", e)
                _analysisState.value = AnalysisState.Error(
                    stage = "Selesaikan Tanpa Segmen",
                    httpCode = null,
                    rawApiMessage = e.stackTraceToString(),
                    errorMessage = "Gagal menyelesaikan analisis: ${e.localizedMessage}"
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
