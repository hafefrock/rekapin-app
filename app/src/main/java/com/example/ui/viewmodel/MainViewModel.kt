package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiApiClient
import com.example.data.api.GeminiConstants
import com.example.data.model.AnalysisState
import com.example.data.model.AnimeAnalysisResult
import com.example.data.model.CharacterInfo
import com.example.data.model.SelectedVideo
import com.example.data.model.TimelineItem
import com.example.data.prefs.ApiKeyStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.ceil

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    val apiKeyStorage = ApiKeyStorage(context)

    private val geminiClient = GeminiApiClient(
        apiKeyProvider = { apiKeyStorage.getEffectiveApiKey() }
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

    private var currentJob: Job? = null

    fun setIntervalSeconds(seconds: Int) {
        if (seconds == 3 || seconds == 5) {
            _promptIntervalSeconds.value = seconds
        }
    }

    fun openSettings() {
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    fun saveApiKey(newKey: String) {
        apiKeyStorage.saveApiKey(newKey)
        closeSettings()
    }

    fun onVideoPicked(uri: Uri) {
        viewModelScope.launch {
            val video = loadVideoMetadata(uri)
            _selectedVideo.value = video
            _analysisState.value = AnalysisState.Idle
        }
    }

    fun clearSelectedVideo() {
        cancelAnalysis()
        _selectedVideo.value = null
        _analysisState.value = AnalysisState.Idle
    }

    fun resetAnalysis() {
        cancelAnalysis()
        _analysisState.value = AnalysisState.Idle
    }

    fun cancelAnalysis() {
        currentJob?.cancel()
        currentJob = null
        if (_analysisState.value !is AnalysisState.Success) {
            _analysisState.value = AnalysisState.Idle
        }
    }

    fun startAnalysis() {
        val video = _selectedVideo.value ?: run {
            _analysisState.value = AnalysisState.Error("Silakan pilih video anime terlebih dahulu.")
            return
        }

        if (!apiKeyStorage.hasApiKey()) {
            _isSettingsOpen.value = true
            _analysisState.value = AnalysisState.Error(
                "API Key Gemini belum diatur. Silakan masukkan API Key Gemini Anda di menu Pengaturan.",
                canRetry = true
            )
            return
        }

        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            try {
                // Tahap 1: Mempersiapkan upload
                _analysisState.value = AnalysisState.Preparing("Mempersiapkan pengunggahan video anime...")

                // Tahap 2: Upload file sekali via File API
                val uploadedFile = geminiClient.uploadVideo(
                    context = context,
                    video = video,
                    onProgress = { percent, sent, total ->
                        _analysisState.value = AnalysisState.Uploading(
                            progressPercent = percent,
                            bytesSent = sent,
                            totalBytes = total,
                            message = "Mengunggah video ke Gemini File API ($percent%)..."
                        )
                    }
                )

                // Tahap 3: Menunggu status ACTIVE
                _analysisState.value = AnalysisState.PollingFileStatus(
                    "Menunggu verifikasi status video di server AI..."
                )
                val activeFile = geminiClient.waitForFileActive(uploadedFile.name) { statusMsg ->
                    _analysisState.value = AnalysisState.PollingFileStatus(statusMsg)
                }

                // Menentukan segmen 3 menit (180 detik)
                // Standar anime 25-28 menit (~1500-1680 detik)
                val totalSeconds = if (video.durationMs > 0) {
                    video.durationMs / 1000
                } else {
                    // Default fallback jika metadata durasi tidak terbaca di beberapa perangkat
                    1500L
                }

                val totalSegments = ceil(totalSeconds.toDouble() / GeminiConstants.SEGMENT_DURATION_SECONDS)
                    .toInt()
                    .coerceAtLeast(1)

                val allCharacterSheet = mutableListOf<CharacterInfo>()
                val allTimeline = mutableListOf<TimelineItem>()
                val segmentSummaries = mutableListOf<String>()

                // Tahap 4: Analisis per segmen 3 menit
                for (i in 0 until totalSegments) {
                    val startSec = i * GeminiConstants.SEGMENT_DURATION_SECONDS
                    val endSec = minOf((i + 1) * GeminiConstants.SEGMENT_DURATION_SECONDS, totalSeconds)
                    val timeRange = "${formatSeconds(startSec)} - ${formatSeconds(endSec)}"

                    _analysisState.value = AnalysisState.AnalyzingSegment(
                        currentSegment = i + 1,
                        totalSegments = totalSegments,
                        timeRange = timeRange,
                        message = "Menganalisis segmen ${i + 1} dari $totalSegments ($timeRange)..."
                    )

                    val segmentOutput = geminiClient.analyzeSegment(
                        fileUri = activeFile.uri,
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

                    // Menggabungkan dan memperbarui character sheet secara berkesinambungan
                    segmentOutput.characterSheet.forEach { newChar ->
                        val existingIndex = allCharacterSheet.indexOfFirst {
                            it.name.equals(newChar.name, ignoreCase = true)
                        }
                        if (existingIndex >= 0) {
                            if (newChar.description.isNotBlank()) {
                                allCharacterSheet[existingIndex] = newChar
                            }
                        } else {
                            allCharacterSheet.add(newChar)
                        }
                    }

                    // Menambahkan item timeline berurutan
                    allTimeline.addAll(segmentOutput.timeline)
                }

                // Tahap 5: Menyusun narasi rekap episode lengkap
                _analysisState.value = AnalysisState.GeneratingNarration(
                    "Menyusun narasi episode lengkap berdasarkan ringkasan ${totalSegments} segmen..."
                )

                val narration = geminiClient.generateFinalNarration(
                    segmentSummaries = segmentSummaries,
                    characterSheet = allCharacterSheet,
                    onStatusUpdate = { msg ->
                        _analysisState.value = AnalysisState.GeneratingNarration(msg)
                    }
                )

                // Selesai dengan sukses!
                val result = AnimeAnalysisResult(
                    narration = narration,
                    characterSheet = allCharacterSheet,
                    timeline = allTimeline,
                    totalSegments = totalSegments,
                    durationFormatted = formatSeconds(totalSeconds),
                    intervalSeconds = _promptIntervalSeconds.value
                )

                _analysisState.value = AnalysisState.Success(result)

            } catch (e: CancellationException) {
                _analysisState.value = AnalysisState.Idle
            } catch (e: Exception) {
                _analysisState.value = AnalysisState.Error(
                    errorMessage = e.message ?: "Terjadi kesalahan yang tidak terduga saat menganalisis video.",
                    canRetry = true
                )
            }
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
        } catch (_: Exception) {}

        val mimeType = contentResolver.getType(uri) ?: "video/mp4"

        var durationMs = 0L
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull() ?: 0L
            retriever.release()
        } catch (_: Exception) {}

        if (sizeBytes <= 0L) {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    sizeBytes = stream.available().toLong()
                }
            } catch (_: Exception) {}
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
