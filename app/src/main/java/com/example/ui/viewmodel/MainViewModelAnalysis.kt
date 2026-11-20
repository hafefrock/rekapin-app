package com.example.ui.viewmodel

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiApiClient
import com.example.data.api.GeminiApiException
import com.example.data.db.AnalysisJsonAdapter
import com.example.data.db.AnimeAnalysisEntity
import com.example.data.model.AnalysisResumePolicy
import com.example.data.model.AnalysisState
import com.example.data.model.AnimeAnalysisResult
import com.example.data.model.CharacterInfo
import com.example.data.model.NarrationMode
import com.example.data.model.PromptCutMode
import com.example.data.model.SeriesBible
import com.example.data.model.TimelineItem
import com.example.data.model.VisualStyle
import com.example.data.prefs.StageTimingStorage
import com.example.service.AnalysisForegroundService
import com.example.util.AudioActivityDetector
import com.example.util.CharacterContinuityChecker
import com.example.util.OpeningEndingDetector
import com.example.util.RepeatedSceneDetector
import com.example.util.RepeatedSceneMatch
import com.example.util.SceneDetector
import com.example.util.SceneTimeSpan
import com.example.util.SegmentCalculator
import com.example.util.SegmentDurationOption
import com.example.util.VideoMetadataHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun MainViewModel.runStartAnalysis(resumeId: Long? = null, forceReupload: Boolean = false) {
    if (!apiKeyStorage.hasApiKey()) {
        Log.w(MainViewModel.TAG, "Percobaan analisis gagal: API Key belum diatur")
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

    val runId = ++runGeneration
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

    _latestStageTimings.value = null
    currentJob = viewModelScope.launch {
        val analysisStartNanos = System.nanoTime()
        var currentStage = "Mempersiapkan Berkas"
        var activeEntityId: Long? = resumeId ?: activeAnalysisId
        var remoteFileName = ""
        var remoteFileUri = ""
        val pendingPreInsertTimings = mutableMapOf<String, Long>()
        var totalSavedForRun = false
        currentTimingAnalysisId = activeEntityId

        try {
            if (!isCurrentRun(runId)) return@launch
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
                    Log.w(MainViewModel.TAG, "Berkas video pada analisis ID $activeEntityId tidak bisa dibuka lagi (SecurityException / FileNotFoundException).")
                    val resumeInfo = checkCanResume(activeEntityId)
                    _analysisState.value = AnalysisState.Error(
                        stage = "Akses Berkas Video",
                        httpCode = null,
                        rawApiMessage = "",
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
                    video = withContext(Dispatchers.IO) { VideoMetadataHelper.loadVideoMetadata(context, parsedUri) }
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
                    Log.w(MainViewModel.TAG, "Gagal membaca durasi video via MediaMetadataRetriever (${e.javaClass.simpleName}).")
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

            var effectiveRangeStartSec = _analysisRangeStartSec.value.coerceIn(0L, totalSeconds)
            val configuredRangeEndSec = _analysisRangeEndSec.value
            var effectiveRangeEndSec = if (configuredRangeEndSec <= 0L || configuredRangeEndSec > totalSeconds) {
                totalSeconds
            } else {
                configuredRangeEndSec
            }
            var repeatedSceneMatches: List<RepeatedSceneMatch> = emptyList()
            var activeOpEdSkipNote: String? = null

            // Local-only OP/ED matching is applied to new analyses only; saved resume plans stay unchanged.
            if (existingEntity == null && _isAutoSkipOpEd.value) {
                val sourceAnalysisId = _selectedSeriesSourceId.value
                if (sourceAnalysisId != null) {
                    val opEdStartNanos = System.nanoTime()
                    try {
                        _analysisState.value = AnalysisState.Preparing("Mencari OP/ED yang berulang di seri secara lokal...")
                        val sourceEntity = repository.getAnalysisById(sourceAnalysisId)
                        var template = openingEndingStorage.get(sourceAnalysisId)
                        if (template == null && sourceEntity != null && sourceEntity.videoUriString != video.uri.toString()) {
                            val sourceDuration = (sourceEntity.durationMs / 1000L).coerceAtLeast(1L)
                            template = OpeningEndingDetector.capture(
                                context,
                                Uri.parse(sourceEntity.videoUriString),
                                sourceDuration
                            )
                            if (template != null) openingEndingStorage.save(sourceAnalysisId, template!!)
                        }

                        val currentFingerprint = OpeningEndingDetector.capture(context, video.uri, totalSeconds)
                        if (template != null && currentFingerprint != null) {
                            val skips = OpeningEndingDetector.findSkips(template!!, currentFingerprint, totalSeconds)
                            val adjustedStart = maxOf(effectiveRangeStartSec, skips.openingEndSec ?: effectiveRangeStartSec)
                            val adjustedEnd = minOf(effectiveRangeEndSec, skips.endingStartSec ?: effectiveRangeEndSec)
                            if (adjustedEnd - adjustedStart >= SegmentCalculator.MIN_RANGE_SECONDS &&
                                (skips.openingEndSec != null || skips.endingStartSec != null)
                            ) {
                                effectiveRangeStartSec = adjustedStart
                                effectiveRangeEndSec = adjustedEnd
                                _analysisRangeStartSec.value = adjustedStart
                                _analysisRangeEndSec.value = adjustedEnd
                                val skipParts = mutableListOf<String>()
                                if (skips.openingEndSec != null) {
                                    skipParts += "OP dilewati sampai ${VideoMetadataHelper.formatSeconds(skips.openingEndSec)}"
                                }
                                if (skips.endingStartSec != null) {
                                    skipParts += "ED mulai ${VideoMetadataHelper.formatSeconds(skips.endingStartSec)}"
                                }
                                val skipDesc = skipParts.joinToString(", ")
                                _analysisState.value = AnalysisState.Preparing(skipDesc)
                                activeOpEdSkipNote = "Rentang yang dilewati: $skipDesc"
                                Log.i(MainViewModel.TAG, "OP/ED berulang terdeteksi secara lokal; $skipDesc; rentang analisis disetel ke ${VideoMetadataHelper.formatSeconds(adjustedStart)}-${VideoMetadataHelper.formatSeconds(adjustedEnd)}")
                            }
                        } else if (template == null && currentFingerprint != null) {
                            // If the source file is no longer readable, use this episode as the local template.
                            openingEndingStorage.save(sourceAnalysisId, currentFingerprint)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(MainViewModel.TAG, "Deteksi OP/ED lokal dilewati; analisis memakai rentang semula: ${e.message}")
                    } finally {
                        pendingPreInsertTimings[StageTimingStorage.STAGE_OP_ED] =
                            (pendingPreInsertTimings[StageTimingStorage.STAGE_OP_ED] ?: 0L) + elapsedSinceNanosMs(opEdStartNanos)
                    }
                }
            }

            if (existingEntity == null && _isDetectRepeatedScenes.value) {
                val repeatedStartNanos = System.nanoTime()
                try {
                    _analysisState.value = AnalysisState.Preparing("Mencari adegan berulang secara lokal...")
                    repeatedSceneMatches = RepeatedSceneDetector.detect(
                        context = context,
                        uri = video.uri,
                        startSec = effectiveRangeStartSec,
                        endSec = effectiveRangeEndSec
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(MainViewModel.TAG, "Deteksi adegan berulang dilewati; analisis tetap dilanjutkan: ${e.message}")
                } finally {
                    pendingPreInsertTimings[StageTimingStorage.STAGE_REPEATED_SCENE] =
                        (pendingPreInsertTimings[StageTimingStorage.STAGE_REPEATED_SCENE] ?: 0L) + elapsedSinceNanosMs(repeatedStartNanos)
                }
            }

            // 2. Tentukan Rencana Segmen
            val segmentSpans = if (existingEntity != null) {
                val savedSpans = AnalysisJsonAdapter.jsonToSegmentPlan(existingEntity.segmentPlanJson)
                if (savedSpans.isNotEmpty()) {
                    Log.i(MainViewModel.TAG, "Menggunakan rencana segmen tersimpan dari database (${savedSpans.size} segmen). Tidak menghitung ulang.")
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
            Log.i(MainViewModel.TAG, "Durasi video: ${totalSeconds}s, Total segmen: $totalSegments (rentang: ${segmentSpans.map { "${it.startSec}-${it.endSec}s" }})")

            // Persiapan state data akumulasi
            val allCharacterSheet = mutableListOf<CharacterInfo>()
            val allTimeline = mutableListOf<TimelineItem>()
            val segmentSummaries = mutableListOf<String>()
            val segmentModels = mutableListOf<String>()
            val continuityWarnings = mutableListOf<String>().apply {
                activeEntityId?.let { addAll(continuityWarningStorage.get(it)) }
            }
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
                currentTimingAnalysisId = existingEntity.id
                if (pendingPreInsertTimings.isNotEmpty()) {
                    stageTimingStorage.mergeTimings(existingEntity.id, pendingPreInsertTimings)
                    pendingPreInsertTimings.clear()
                }
                if (!forceReupload) {
                    remoteFileName = existingEntity.geminiFileRemoteName
                    remoteFileUri = existingEntity.geminiFileUri
                }
                if (existingEntity.completedSegmentsCount > 0) {
                    startIndex = AnalysisResumePolicy.restoredSegmentIndex(
                        existingEntity.completedSegmentsCount,
                        totalSegments
                    )
                    allCharacterSheet.addAll(AnalysisJsonAdapter.jsonToCharacterSheet(existingEntity.characterSheetJson))
                    allTimeline.addAll(AnalysisJsonAdapter.jsonToTimeline(existingEntity.timelineJson))
                    segmentSummaries.addAll(AnalysisJsonAdapter.jsonToSummaries(existingEntity.segmentSummariesJson))
                    segmentModels.addAll(AnalysisJsonAdapter.jsonToSummaries(existingEntity.segmentModelsJson))
                    Log.i(MainViewModel.TAG, "Memulihkan ${startIndex} segmen tersimpan untuk analisis ID ${existingEntity.id}. Gaya: ${activeStyle.name}, Mode: ${activeMode.displayName}, CutMode: ${activeCutMode.displayName}")
                }
            } else {
                // Buat rekaman awal baru di Room database dengan rencana segmen tersimpan
                val effectiveInterval = if (activeCutMode == PromptCutMode.PER_SCENE) 0 else _promptIntervalSeconds.value
                val newEntity = AnimeAnalysisEntity(
                    videoUriString = video.uri.toString(),
                    videoName = video.displayName,
                    videoSizeBytes = video.sizeBytes,
                    durationMs = if (actualDurationMs > 0) actualDurationMs else video.durationMs,
                    videoFingerprint = video.fingerprint,
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
                currentTimingAnalysisId = activeEntityId
                if (pendingPreInsertTimings.isNotEmpty()) {
                    stageTimingStorage.mergeTimings(activeEntityId, pendingPreInsertTimings)
                    pendingPreInsertTimings.clear()
                }
                if (repeatedSceneMatches.isNotEmpty()) {
                    repeatedSceneStorage.save(activeEntityId, repeatedSceneMatches.map { it.toUserMessage() })
                }
                if (!activeOpEdSkipNote.isNullOrBlank()) {
                    openingEndingStorage.saveSkipNote(activeEntityId, activeOpEdSkipNote)
                }
                visualStyleStorage.saveStyleForAnalysis(activeEntityId, activeStyle)
                narrationModeStorage.saveModeForAnalysis(activeEntityId, activeMode)
                promptCutModeStorage.saveModeForAnalysis(activeEntityId, activeCutMode, activeMaxSceneDur)
                Log.i(MainViewModel.TAG, "Membuat entri analisis baru di Room database ID: $activeEntityId dengan rencana $totalSegments segmen, gaya: ${activeStyle.name}, mode: ${activeMode.displayName}, cutMode: ${activeCutMode.displayName}")

                val chosenSeriesSourceId = _selectedSeriesSourceId.value
                if (chosenSeriesSourceId != null) {
                    seriesStorage.saveSourceAnalysisId(activeEntityId, chosenSeriesSourceId)
                    Log.i(MainViewModel.TAG, "Menyimpan relasi seri di SharedPreferences: key=$activeEntityId, source=$chosenSeriesSourceId")
                }
            }

            // Tentukan nilai awal character sheet dan memori seri SEBELUM loop dimulai
            var activeSeriesBible: SeriesBible? = null
            if (activeEntityId != null) {
                val sourceAnalysisId = seriesStorage.getSourceAnalysisId(activeEntityId)
                if (sourceAnalysisId != null) {
                    _selectedSeriesSourceId.value = sourceAnalysisId
                    activeSeriesBible = seriesBibleStorage.getBible(sourceAnalysisId)
                    if (activeSeriesBible != null) {
                        if (seriesBibleStorage.getBible(activeEntityId) == null) {
                            seriesBibleStorage.saveBible(activeEntityId, activeSeriesBible!!)
                        }
                        Log.i(MainViewModel.TAG, "Memori seri dimuat dari analisis sumber ID $sourceAnalysisId untuk analisis ID $activeEntityId: ${activeSeriesBible.locations.size} lokasi, ${activeSeriesBible.glossary.size} istilah")
                    }
                    if (allCharacterSheet.isEmpty()) {
                        val sourceEntity = repository.getAnalysisById(sourceAnalysisId)
                        if (sourceEntity != null && sourceEntity.characterSheetJson.isNotBlank()) {
                            val initialChars = AnalysisJsonAdapter.jsonToCharacterSheet(sourceEntity.characterSheetJson)
                            if (initialChars.isNotEmpty()) {
                                allCharacterSheet.addAll(initialChars)
                                Log.i(MainViewModel.TAG, "Inisialisasi character sheet awal: ${allCharacterSheet.size} karakter dimuat dari seri sumber ID $sourceAnalysisId untuk analisis ID $activeEntityId.")
                            }
                        }
                    }
                }
            }

            // Validasi & upload file ke Gemini Files API jika ada segmen tersisa
            if (AnalysisResumePolicy.shouldAnalyzeSegments(startIndex, totalSegments)) {
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
                        Log.i(MainViewModel.TAG, "Key aktif ($currentKeyHash) berbeda dari key saat upload ($savedKeyHash). Mengunggah ulang video otomatis.")
                    }
                    needUpload = true
                } else if (remoteFileName.isNotBlank()) {
                    val verifyCheckStartNanos = System.nanoTime()
                    val remoteState = try {
                        geminiClient.checkFileRemoteState(remoteFileName)
                    } finally {
                        activeEntityId?.let { id ->
                            stageTimingStorage.addStageDuration(
                                id,
                                StageTimingStorage.STAGE_VERIFY_FILE,
                                elapsedSinceNanosMs(verifyCheckStartNanos)
                            )
                        }
                    }
                    when (remoteState) {
                        GeminiApiClient.RemoteFileState.ACTIVE -> {
                            Log.i(MainViewModel.TAG, "Berkas Gemini masih ACTIVE. Siap langsung digunakan kembali.")
                            needUpload = false
                            needWaitActive = false
                        }
                        GeminiApiClient.RemoteFileState.PROCESSING -> {
                            Log.i(MainViewModel.TAG, "Berkas Gemini masih PROCESSING. Melanjutkan tunggu tanpa upload ulang...")
                            needUpload = false
                            needWaitActive = true
                        }
                        GeminiApiClient.RemoteFileState.FAILED -> {
                            Log.e(MainViewModel.TAG, "Berkas Gemini berstatus FAILED.")
                            throw GeminiApiException(
                                stage = "Verifikasi Status Berkas",
                                httpCode = null,
                                rawMessage = "File state: FAILED di server Google AI.",
                                userFriendlyMessage = "Pemrosesan video di server Google AI gagal (Status: FAILED). Format atau kodek video mungkin tidak didukung oleh server AI. Silakan unggah ulang video untuk mencoba kembali.",
                                isFileProcessingFailed = true
                            )
                        }
                        GeminiApiClient.RemoteFileState.NOT_FOUND -> {
                            Log.w(MainViewModel.TAG, "Berkas Gemini telah kedaluwarsa atau tidak ditemukan (>48 jam). Mengunggah ulang video...")
                            needUpload = true
                        }
                    }
                } else {
                    needUpload = true
                }

                if (needUpload) {
                    currentStage = "Upload Video ke Gemini File API"
                    Log.i(MainViewModel.TAG, "[$currentStage] Mengunggah ${video.sizeBytes} bytes secara streaming...")
                    val uploadStartNanos = System.nanoTime()
                    val uploadedFile = try {
                        geminiClient.uploadVideo(
                            context = context,
                            video = video,
                            onProgress = { percent, sent, total ->
                                if (!isCurrentRun(runId)) return@uploadVideo
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
                    } finally {
                        activeEntityId?.let { id ->
                            stageTimingStorage.addStageDuration(
                                id,
                                StageTimingStorage.STAGE_UPLOAD,
                                elapsedSinceNanosMs(uploadStartNanos)
                            )
                        }
                    }

                    remoteFileName = uploadedFile.name
                    remoteFileUri = uploadedFile.uri

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
                    val waitActiveStartNanos = System.nanoTime()
                    val activeFile = try {
                        geminiClient.waitForFileActive(remoteFileName) { statusMsg ->
                            if (!isCurrentRun(runId)) return@waitForFileActive
                            _analysisState.value = AnalysisState.PollingFileStatus(statusMsg)
                            AnalysisForegroundService.updateStatus(
                                context = context,
                                title = "Memproses Berkas di AI",
                                message = statusMsg,
                                model = currentModel
                            )
                        }
                    } finally {
                        activeEntityId?.let { id ->
                            stageTimingStorage.addStageDuration(
                                id,
                                StageTimingStorage.STAGE_VERIFY_FILE,
                                elapsedSinceNanosMs(waitActiveStartNanos)
                            )
                        }
                    }

                    remoteFileName = activeFile.name
                    remoteFileUri = activeFile.uri

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
            } else {
                Log.i(MainViewModel.TAG, "Semua segmen sudah tersimpan; melewati pemeriksaan/upload video dan langsung menyusun narasi final.")
            }

            // Model terpilih saat analisis dimulai atau dilanjutkan
            val selectedModelName = modelStorage.getSelectedModel()
            val modelList = modelStorage.getModels().ifEmpty { listOf(selectedModelName) }
            val isAutoSwitch = modelStorage.isAutoSwitchOnQuota()
            var currentModelIndex = modelList.indexOf(selectedModelName).coerceAtLeast(0)
            var activeModel = modelList.getOrElse(currentModelIndex) { selectedModelName }
            Log.i(MainViewModel.TAG, "Memulai analisis dengan model '$activeModel' (auto-switch: $isAutoSwitch, daftar: $modelList)")

            // Deteksi adegan video sekali untuk seluruh rentang jika mode Per Adegan
            if (activeCutMode == PromptCutMode.PER_SCENE && AnalysisResumePolicy.shouldAnalyzeSegments(startIndex, totalSegments)) {
                val sceneStageStartNanos = System.nanoTime()
                try {
                    val safeStart = segmentSpans.firstOrNull()?.startSec ?: 0L
                    val safeEnd = segmentSpans.lastOrNull()?.endSec ?: totalSeconds
                    val requiredRawKey = RawSceneCacheKey(
                        videoUri = video.uri.toString(),
                        startSec = safeStart,
                        endSec = safeEnd
                    )
                    val requiredKey = SceneCacheKey(
                        videoUri = video.uri.toString(),
                        startSec = safeStart,
                        endSec = safeEnd,
                        maxSceneDurationSec = activeMaxSceneDur
                    )

                    if (cachedRawCutPoints != null && cachedRawKey == requiredRawKey && latestPreScanDurationMs > 0L) {
                        activeEntityId?.let { id ->
                            stageTimingStorage.addStageDuration(id, StageTimingStorage.STAGE_SCENE_DETECTION, latestPreScanDurationMs)
                        }
                        latestPreScanDurationMs = 0L
                    }

                    val activePreScan = preScanJob
                    if (activePreScan != null && activePreScan.isActive) {
                        currentStage = "Mendeteksi Adegan Video"
                        _analysisState.value = AnalysisState.Preparing(message = "Mendeteksi adegan video...", progress = 0f)
                        AnalysisForegroundService.updateStatus(
                            context = context,
                            title = "Mendeteksi Adegan Video",
                            message = "Mendeteksi transisi adegan visual otomatis...",
                            model = activeModel
                        )
                        Log.i(MainViewModel.TAG, "Menunggu preScanJob latar belakang selesai (join)...")
                        activePreScan.join()
                        latestPreScanDurationMs = 0L
                    }

                    if (cachedRawCutPoints != null && cachedRawKey == requiredRawKey) {
                        if (cachedSceneSpans == null || cachedSceneKey != requiredKey) {
                            val spans = SceneDetector.buildFinalSpans(
                                rawCutPoints = cachedRawCutPoints!!,
                                startSec = safeStart,
                                endSec = safeEnd,
                                maxDuration = activeMaxSceneDur
                            )
                            cachedSceneSpans = spans
                            cachedSceneKey = requiredKey
                            _detectedSceneCount.value = spans.size
                            Log.i(MainViewModel.TAG, "Menggunakan cachedRawCutPoints: dihitung ulang ${spans.size} adegan untuk analisis")
                        }
                    }

                    if (cachedSceneSpans == null || cachedSceneKey != requiredKey) {
                        currentStage = "Mendeteksi Adegan Video"
                        _analysisState.value = AnalysisState.Preparing(message = "Mendeteksi adegan video...", progress = 0f)
                        AnalysisForegroundService.updateStatus(
                            context = context,
                            title = "Mendeteksi Adegan Video",
                            message = "Mendeteksi transisi adegan visual otomatis...",
                            model = activeModel
                        )

                        isDetectionSkipped = false
                        val spans: List<SceneTimeSpan> = try {
                            coroutineScope {
                                val detectJob = async(Dispatchers.IO) {
                                    val rawPoints = SceneDetector.detectRawCutPoints(
                                        context = context,
                                        videoUri = video.uri,
                                        startSec = safeStart,
                                        endSec = safeEnd,
                                        onProgress = { progress, msg ->
                                            if (!isCurrentRun(runId)) return@detectRawCutPoints
                                            _analysisState.value = AnalysisState.Preparing(message = msg, progress = progress)
                                            AnalysisForegroundService.updateStatus(
                                                context = context,
                                                title = "Mendeteksi Adegan Video",
                                                message = msg,
                                                model = activeModel
                                            )
                                        }
                                    )
                                    if (rawPoints != null) {
                                        cachedRawCutPoints = rawPoints
                                        cachedRawKey = requiredRawKey
                                        SceneDetector.buildFinalSpans(rawPoints, safeStart, safeEnd, activeMaxSceneDur)
                                    } else {
                                        SceneDetector.splitSpansFallback(safeStart, safeEnd, activeMaxSceneDur)
                                    }
                                }
                                activeDetectionJob = detectJob
                                detectJob.await()
                            }
                        } catch (e: CancellationException) {
                            if (isDetectionSkipped) {
                                Log.i(MainViewModel.TAG, "Deteksi dibatalkan oleh pengguna (Lewati deteksi). Menggunakan splitSpansFallback dengan durasi $activeMaxSceneDur detik")
                                SceneDetector.splitSpansFallback(safeStart, safeEnd, activeMaxSceneDur)
                            } else {
                                throw e
                            }
                        } finally {
                            activeDetectionJob = null
                        }

                        cachedSceneSpans = spans
                        cachedSceneKey = requiredKey
                        _detectedSceneCount.value = spans.size
                        Log.i(MainViewModel.TAG, "Deteksi adegan lokal selesai: ${spans.size} adegan terdeteksi")
                    }
                } finally {
                    activeEntityId?.let { id ->
                        stageTimingStorage.addStageDuration(
                            id,
                            StageTimingStorage.STAGE_SCENE_DETECTION,
                            elapsedSinceNanosMs(sceneStageStartNanos)
                        )
                    }
                }
            }

            // Loop Analisis per segmen
            for (i in startIndex until totalSegments) {
                if (!isCurrentRun(runId)) return@launch
                val segment = segmentSpans[i]
                val startSec = segment.startSec
                val endSec = minOf(segment.endSec, totalSeconds)
                val timeRange = "${VideoMetadataHelper.formatSeconds(startSec)} - ${VideoMetadataHelper.formatSeconds(endSec)}"
                currentStage = "Analisis Segmen ${i + 1} dari $totalSegments ($timeRange)"

                val exactScenes = if (activeCutMode == PromptCutMode.PER_SCENE) {
                    val safeStart = segmentSpans.firstOrNull()?.startSec ?: 0L
                    val safeEnd = segmentSpans.lastOrNull()?.endSec ?: totalSeconds
                    val requiredRawKey = RawSceneCacheKey(video.uri.toString(), safeStart, safeEnd)
                    val requiredKey = SceneCacheKey(
                        videoUri = video.uri.toString(),
                        startSec = safeStart,
                        endSec = safeEnd,
                        maxSceneDurationSec = activeMaxSceneDur
                    )
                    val spans = if (cachedSceneSpans != null && cachedSceneKey == requiredKey) {
                        cachedSceneSpans!!
                    } else if (cachedRawCutPoints != null && cachedRawKey == requiredRawKey) {
                        val s = SceneDetector.buildFinalSpans(cachedRawCutPoints!!, safeStart, safeEnd, activeMaxSceneDur)
                        cachedSceneSpans = s
                        cachedSceneKey = requiredKey
                        _detectedSceneCount.value = s.size
                        s
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
                    Log.i(MainViewModel.TAG, "[$currentStage] Memulai analisis dengan model '$activeModel'...")
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
                        val speechActivityHints = if (activeMode == NarrationMode.DIALOG_KONTEN) {
                            val audioStartNanos = System.nanoTime()
                            try {
                                _analysisState.value = AnalysisState.AnalyzingSegment(
                                    currentSegment = i + 1,
                                    totalSegments = totalSegments,
                                    timeRange = timeRange,
                                    message = "Menganalisis bagian suara lokal...",
                                    modelName = activeModel
                                )
                                val speechSpans = AudioActivityDetector.detect(
                                    context = context,
                                    uri = video.uri,
                                    startSec = startSec,
                                    endSec = endSec
                                )
                                if (speechSpans.isEmpty()) "" else "Voice-like activity detected around: " +
                                    speechSpans.joinToString(", ") { "${VideoMetadataHelper.formatSeconds(it.startSec)}-${VideoMetadataHelper.formatSeconds(it.endSec)}" } +
                                    ". Other intervals may be quiet or contain only non-voice audio."
                            } finally {
                                activeEntityId?.let { id ->
                                    stageTimingStorage.addStageDuration(
                                        id,
                                        StageTimingStorage.STAGE_AUDIO_DETECTION,
                                        elapsedSinceNanosMs(audioStartNanos)
                                    )
                                }
                            }
                        } else ""
                        val segStartNanos = System.nanoTime()
                        val segmentOutput = try {
                            geminiClient.analyzeSegment(
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
                                seriesBible = activeSeriesBible,
                                speechActivityHints = speechActivityHints,
                                onStatusUpdate = { statusMsg ->
                                    if (!isCurrentRun(runId)) return@analyzeSegment
                                    _analysisState.value = AnalysisState.AnalyzingSegment(
                                        currentSegment = i + 1,
                                        totalSegments = totalSegments,
                                        timeRange = timeRange,
                                        message = statusMsg,
                                        modelName = activeModel
                                    )
                                }
                            )
                        } finally {
                            activeEntityId?.let { id ->
                                stageTimingStorage.addStageDuration(
                                    id,
                                    StageTimingStorage.STAGE_SEGMENT_ANALYSIS,
                                    elapsedSinceNanosMs(segStartNanos)
                                )
                            }
                        }
                        activeEntityId?.let { id ->
                            stageTimingStorage.addSegmentTiming(id, durationMs = 0L, segmentCount = 1)
                        }

                        val continuityIssues = CharacterContinuityChecker.findContradictions(
                            previous = allCharacterSheet,
                            current = segmentOutput.characterSheet
                        )
                        if (continuityIssues.isNotEmpty()) {
                            continuityWarnings.addAll(continuityIssues)
                            activeEntityId?.let { continuityWarningStorage.save(it, continuityWarnings) }
                            Log.w(MainViewModel.TAG, "Pemeriksa kontinuitas karakter: ${continuityIssues.joinToString()}")
                        }

                        segmentSummaries.add(segmentOutput.segmentSummary)
                        segmentModels.add(activeModel)

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
                                Log.i(MainViewModel.TAG, "[$currentStage] Berhasil disimpan ke Room (Segmen ${i + 1} tersimpan dengan model $activeModel)")
                            }
                        }

                        segmentSuccess = true
                    } catch (e: GeminiApiException) {
                        if (e.isDailyQuota) {
                            modelUsageStorage.markQuotaExceeded(activeModel, e.retryDelaySeconds)
                            refreshSelectedModelUsage()
                        }
                        if (isAutoSwitch && e.isDailyQuota) {
                            Log.w(MainViewModel.TAG, "[$currentStage] Kuota harian habis pada model '$activeModel'. Mencoba beralih ke model berikutnya...")
                            lastQuotaException = e
                            currentModelIndex++
                            if (currentModelIndex >= modelList.size) {
                                Log.e(MainViewModel.TAG, "[$currentStage] Semua model dalam daftar telah habis kuotanya.")
                                throw lastQuotaException ?: e
                            }
                            continue
                        } else if (isAutoSwitch && lastQuotaException != null && (e.httpCode == 404 || e.userFriendlyMessage.contains("tidak dikenal", ignoreCase = true))) {
                            Log.w(MainViewModel.TAG, "[$currentStage] Model '$activeModel' tidak dikenal (HTTP 404). Melewati ke model berikutnya...")
                            currentModelIndex++
                            if (currentModelIndex >= modelList.size) {
                                Log.e(MainViewModel.TAG, "[$currentStage] Semua model berikutnya tidak valid.")
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
            val narrationStageStartNanos = System.nanoTime()

            try {
                if (activeMode != NarrationMode.REKAP_VOICEOVER) {
                    Log.i(MainViewModel.TAG, "[$currentStage] Mode ${activeMode.displayName} dipilih. Menyusun transkrip lokal dari timeline.")
                    narration = formatTimelineTranscript(allTimeline, activeMode)
                    narrationSuccess = true
                } else {
                    Log.i(MainViewModel.TAG, "[$currentStage] Menyusun narasi video dengan model '$activeModel' (mode: ${activeMode.displayName})...")
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
                            val previousRecapContext = if (seriesBibleStorage.isPrependRecapEnabled()) {
                                activeSeriesBible?.plotSoFar?.takeIf { it.isNotBlank() }
                            } else null

                            narration = geminiClient.generateFinalNarration(
                                segmentSummaries = segmentSummaries,
                                characterSheet = allCharacterSheet,
                                model = activeModel,
                                language = currentLang,
                                previousRecapContext = previousRecapContext,
                                onStatusUpdate = { msg ->
                                    if (!isCurrentRun(runId)) return@generateFinalNarration
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
            } finally {
                activeEntityId?.let { id ->
                    stageTimingStorage.addStageDuration(
                        id,
                        StageTimingStorage.STAGE_FINAL_NARRATION,
                        elapsedSinceNanosMs(narrationStageStartNanos)
                    )
                }
            }

            // Simpan status COMPLETED ke database Room
            if (!isCurrentRun(runId)) return@launch
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
                    Log.i(MainViewModel.TAG, "Analisis ID $id ditandai COMPLETED di Room database")
                }
            }

            val currentBible = (activeEntityId?.let { seriesBibleStorage.getBible(it) }) ?: activeSeriesBible
            val repeatedSceneNotes = activeEntityId?.let { repeatedSceneStorage.get(it) }.orEmpty()
                .ifEmpty { repeatedSceneMatches.map { it.toUserMessage() } }
            val opEdSkipNote = (activeEntityId?.let { openingEndingStorage.getSkipNote(it) }) ?: activeOpEdSkipNote
            val analysisNotes = mutableListOf<String>()
            continuityWarnings.distinct().takeIf { it.isNotEmpty() }?.let { warnings ->
                analysisNotes += "Pemeriksa kontinuitas karakter menemukan perbedaan yang perlu ditinjau:\n- ${warnings.joinToString("\n- ")}"
            }
            repeatedSceneNotes.takeIf { it.isNotEmpty() }?.let { notes ->
                analysisNotes += "Kandidat adegan berulang berdasarkan kemiripan visual (tinjau manual):\n- ${notes.joinToString("\n- ")}"
            }
            opEdSkipNote?.takeIf { it.isNotBlank() }?.let { note ->
                analysisNotes += note
            }
            val finalResult = AnimeAnalysisResult(
                narration = narration,
                characterSheet = allCharacterSheet,
                timeline = allTimeline,
                totalSegments = totalSegments,
                durationFormatted = VideoMetadataHelper.formatSeconds(totalSeconds),
                intervalSeconds = if (activeCutMode == PromptCutMode.PER_SCENE) 0 else _promptIntervalSeconds.value,
                videoName = video.displayName,
                note = analysisNotes.joinToString("\n\n"),
                segmentModels = segmentModels,
                segmentSummaries = segmentSummaries,
                narrationMode = activeMode.displayName,
                maxSceneDuration = activeMaxSceneDur,
                seriesBible = currentBible
            )

            if (!isCurrentRun(runId)) return@launch
            activeEntityId?.let { id ->
                if (!totalSavedForRun) {
                    stageTimingStorage.addStageDuration(
                        id,
                        StageTimingStorage.STAGE_TOTAL,
                        elapsedSinceNanosMs(analysisStartNanos)
                    )
                    totalSavedForRun = true
                }
                _latestStageTimings.value = stageTimingStorage.getTimings(id)
            }
            Log.i(MainViewModel.TAG, "Analisis seluruh video selesai dengan sukses!")
            _analysisState.value = AnalysisState.Success(finalResult, styleName = activeStyle.name)
            AnalysisForegroundService.stop(context)
            AnalysisForegroundService.notifyCompleted(context, video.displayName)

            // Pembaruan memori seri otomatis (best-effort)
            if (seriesBibleStorage.isAutoBuildEnabled() && activeEntityId != null) {
                val finalActiveEntityId = activeEntityId
                val finalVideoName = video.displayName
                val finalModel = activeModel
                viewModelScope.launch(Dispatchers.IO) {
                    val bibleStartNanos = System.nanoTime()
                    currentTimingAnalysisId = finalActiveEntityId
                    try {
                        Log.i(MainViewModel.TAG, "Memulai pembaruan memori seri otomatis untuk analisis ID $finalActiveEntityId (model: $finalModel)...")
                        val updatedBible = geminiClient.updateSeriesBible(
                            previousBible = activeSeriesBible,
                            segmentSummaries = segmentSummaries,
                            characterSheet = allCharacterSheet,
                            videoName = finalVideoName,
                            model = finalModel
                        )
                        val bibleElapsedMs = elapsedSinceNanosMs(bibleStartNanos)
                        stageTimingStorage.addStageDuration(
                            finalActiveEntityId,
                            StageTimingStorage.STAGE_SERIES_BIBLE,
                            bibleElapsedMs
                        )
                        if (activeAnalysisId == finalActiveEntityId) {
                            _latestStageTimings.value = stageTimingStorage.getTimings(finalActiveEntityId)
                        }
                        if (updatedBible != null && !updatedBible.isEmpty()) {
                            seriesBibleStorage.saveBible(finalActiveEntityId, updatedBible)
                            _failedSeriesBibleBuildIds.value = _failedSeriesBibleBuildIds.value - finalActiveEntityId
                            Log.i(MainViewModel.TAG, "Memori seri untuk analisis ID $finalActiveEntityId berhasil disimpan: ${updatedBible.locations.size} lokasi, ${updatedBible.glossary.size} istilah, ${updatedBible.relationships.size} relasi, ${updatedBible.episodes.size} episode.")
                            val currentState = _analysisState.value
                            if (currentState is AnalysisState.Success &&
                                activeAnalysisId == finalActiveEntityId &&
                                currentState.result.videoName == finalVideoName
                            ) {
                                _analysisState.value = currentState.copy(
                                    result = currentState.result.copy(seriesBible = updatedBible)
                                )
                            }
                        } else {
                            Log.w(MainViewModel.TAG, "Pembaruan otomatis Memori Seri mengembalikan hasil kosong/null untuk analisis $finalActiveEntityId")
                            recordSeriesBibleBuildFailure(finalActiveEntityId)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val bibleElapsedMs = elapsedSinceNanosMs(bibleStartNanos)
                        stageTimingStorage.addStageDuration(
                            finalActiveEntityId,
                            StageTimingStorage.STAGE_SERIES_BIBLE,
                            bibleElapsedMs
                        )
                        Log.w(MainViewModel.TAG, "Gagal memperbarui memori seri secara otomatis (dilewati): ${e.javaClass.simpleName}")
                        recordSeriesBibleBuildFailure(finalActiveEntityId)
                    } finally {
                        if (currentTimingAnalysisId == finalActiveEntityId) {
                            currentTimingAnalysisId = null
                        }
                    }
                }
            }

        } catch (e: GeminiApiException) {
            if (!isCurrentRun(runId)) {
                Log.i(MainViewModel.TAG, "Mengabaikan error Gemini dari run analisis yang sudah digantikan.")
                return@launch
            }
            Log.e(MainViewModel.TAG, "GeminiApiException pada [$currentStage], HTTP ${e.httpCode ?: "tidak diketahui"}.")
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
            if (isCurrentRun(runId)) {
                Log.i(MainViewModel.TAG, "Analisis aktif dibatalkan.")
                AnalysisForegroundService.stop(context)
                _analysisState.value = AnalysisState.Idle
            } else {
                Log.i(MainViewModel.TAG, "Cleanup cancellation run lama dilewati.")
            }
        } catch (e: Exception) {
            if (!isCurrentRun(runId)) {
                Log.i(MainViewModel.TAG, "Mengabaikan exception dari run analisis yang sudah digantikan.")
                return@launch
            }
            AnalysisForegroundService.stop(context)
            val isAccessLost = e is SecurityException ||
                e is java.io.FileNotFoundException ||
                e.cause is SecurityException ||
                e.cause is java.io.FileNotFoundException ||
                (e.message?.contains("Permission Denial", ignoreCase = true) == true) ||
                (e.message?.contains("requires that you obtain", ignoreCase = true) == true)

            if (isAccessLost) {
                Log.w(MainViewModel.TAG, "Akses berkas video hilang (${e.javaClass.simpleName}).")
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
                    rawApiMessage = "",
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
                Log.e(MainViewModel.TAG, "Kesalahan tidak terduga pada [$currentStage]: ${e.javaClass.simpleName}.")
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
        } finally {
            if (!totalSavedForRun) {
                activeEntityId?.let { id ->
                    stageTimingStorage.addStageDuration(
                        id,
                        StageTimingStorage.STAGE_TOTAL,
                        elapsedSinceNanosMs(analysisStartNanos)
                    )
                    totalSavedForRun = true
                }
            }
            if (currentTimingAnalysisId == activeEntityId) {
                currentTimingAnalysisId = null
            }
        }
    }
}
