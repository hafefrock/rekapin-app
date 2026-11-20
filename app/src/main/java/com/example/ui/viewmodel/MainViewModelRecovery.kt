package com.example.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiApiException
import com.example.data.db.AnalysisJsonAdapter
import com.example.data.model.AnalysisState
import com.example.data.model.AnimeAnalysisResult
import com.example.data.model.NarrationMode
import com.example.data.model.VideoMatchPolicy
import com.example.data.prefs.StageTimingStorage
import com.example.service.AnalysisForegroundService
import com.example.util.VideoMetadataHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun MainViewModel.runOnRecoveryVideoPicked(newUri: Uri) {
    val currentErrorState = _analysisState.value as? AnalysisState.Error
    val targetId = currentErrorState?.analysisId ?: activeAnalysisId
    if (targetId == null) {
        Log.w(MainViewModel.TAG, "onRecoveryVideoPicked: ID analisis target tidak ditemukan")
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

            val newVideo = withContext(Dispatchers.IO) { VideoMetadataHelper.loadVideoMetadata(context, newUri) }
            val selectedDurationMs = newVideo.durationMs
            val selectedSizeBytes = newVideo.sizeBytes
            val savedDurationMs = savedEntity.durationMs
            val savedSizeBytes = savedEntity.videoSizeBytes

            Log.i(MainViewModel.TAG, "Mencocokkan video: durasi=${selectedDurationMs}ms, ukuran=${selectedSizeBytes}bytes vs tersimpan: durasi=${savedDurationMs}ms, ukuran=${savedSizeBytes}bytes")

            val isMatch = VideoMatchPolicy.matches(
                savedDurationMs = savedDurationMs,
                savedSizeBytes = savedSizeBytes,
                selectedDurationMs = selectedDurationMs,
                selectedSizeBytes = selectedSizeBytes,
                savedFingerprint = savedEntity.videoFingerprint,
                selectedFingerprint = newVideo.fingerprint
            )

            if (!isMatch) {
                Log.w(MainViewModel.TAG, "Video yang dipilih tidak cocok dengan metadata analisis tersimpan; resume dibatalkan.")
                _analysisState.value = AnalysisState.Error(
                    stage = "Verifikasi Video",
                    httpCode = null,
                    rawApiMessage = "",
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
                Log.i(MainViewModel.TAG, "Video cocok dengan rekaman analisis ID $targetId! Memperbarui URI dan mengunggah ulang...")
                val updatedEntity = savedEntity.copy(
                    videoUriString = newUri.toString(),
                    videoName = newVideo.displayName,
                    videoSizeBytes = if (selectedSizeBytes > 0) selectedSizeBytes else savedSizeBytes,
                    durationMs = if (selectedDurationMs > 0) selectedDurationMs else savedDurationMs,
                    videoFingerprint = newVideo.fingerprint.ifBlank { savedEntity.videoFingerprint },
                    updatedAt = System.currentTimeMillis()
                )
                repository.update(updatedEntity)
                _selectedVideo.value = newVideo
                activeAnalysisId = targetId

                startAnalysis(resumeId = targetId, forceReupload = true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(MainViewModel.TAG, "Gagal memproses berkas video yang dipilih (${e.javaClass.simpleName}).")
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

internal fun MainViewModel.runFinishWithoutCurrentSegment(analysisId: Long? = null) {
    val targetId = analysisId ?: activeAnalysisId ?: historyList.value.firstOrNull { !it.isCompleted && it.completedSegmentsCount > 0 }?.id
    if (targetId == null) {
        Log.w(MainViewModel.TAG, "finishWithoutCurrentSegment: Target ID analisis tidak ditemukan")
        return
    }

    val runId = ++runGeneration
    currentJob?.cancel()
    AnalysisForegroundService.onCancelRequested = { cancelAnalysis() }
    AnalysisForegroundService.start(
        context = context,
        model = modelStorage.getSelectedModel(),
        title = "Menyelesaikan Analisis",
        message = "Menyusun hasil dari segmen yang tersimpan..."
    )
    currentJob = viewModelScope.launch {
        val finishStartNanos = System.nanoTime()
        var finishTotalSaved = false
        currentTimingAnalysisId = targetId
        try {
            val entity = repository.getAnalysisById(targetId)
            if (entity == null) {
                Log.e(MainViewModel.TAG, "finishWithoutCurrentSegment: Entri analisis ID $targetId tidak ada di database")
                return@launch
            }

            val summaries = AnalysisJsonAdapter.jsonToSummaries(entity.segmentSummariesJson)
            val characterSheet = AnalysisJsonAdapter.jsonToCharacterSheet(entity.characterSheetJson)
            val timeline = AnalysisJsonAdapter.jsonToTimeline(entity.timelineJson)
            val segmentModels = AnalysisJsonAdapter.jsonToSummaries(entity.segmentModelsJson)
            val selectedModelName = modelStorage.getSelectedModel()

            val savedCount = summaries.size.coerceAtLeast(entity.completedSegmentsCount)
            Log.i(MainViewModel.TAG, "Menyelesaikan analisis ID $targetId tanpa segmen berikutnya dengan model '$selectedModelName'. Segmen tersimpan: $savedCount")

            val analysisMode = narrationModeStorage.getModeForAnalysis(targetId) ?: narrationModeStorage.getMode()
            val currentLang = narrationLanguageStorage.getLanguage()

            val narrationStageStartNanos = System.nanoTime()
            val finalNarration = try {
                if (analysisMode != NarrationMode.REKAP_VOICEOVER) {
                    Log.i(MainViewModel.TAG, "finishWithoutCurrentSegment: Mode ${analysisMode.displayName} dipilih. Menyusun transkrip lokal dari timeline.")
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
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(MainViewModel.TAG, "Gagal generate narasi via AI (${e.javaClass.simpleName}), memakai rangkuman segmen lokal.")
                        summaries.mapIndexed { idx, s ->
                            "Segmen ${idx + 1}: $s"
                        }.joinToString("\n\n")
                    }

                    val note = "[Catatan: Segmen terakhir dilewati]\n\n"
                    note + rawNarration
                }
            } finally {
                stageTimingStorage.addStageDuration(
                    targetId,
                    StageTimingStorage.STAGE_FINAL_NARRATION,
                    elapsedSinceNanosMs(narrationStageStartNanos)
                )
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
            if (!isCurrentRun(runId)) return@launch
            activeAnalysisId = null
            Log.i(MainViewModel.TAG, "Analisis ID $targetId selesai tanpa segmen terakhir dan ditandai COMPLETED di Room.")

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
            stageTimingStorage.addStageDuration(
                targetId,
                StageTimingStorage.STAGE_TOTAL,
                elapsedSinceNanosMs(finishStartNanos)
            )
            finishTotalSaved = true
            _latestStageTimings.value = stageTimingStorage.getTimings(targetId)
            _analysisState.value = AnalysisState.Success(finalResult, styleName = savedStyle.name)
            AnalysisForegroundService.stop(context)
            AnalysisForegroundService.notifyCompleted(context, entity.videoName)

        } catch (e: CancellationException) {
            if (isCurrentRun(runId)) {
                AnalysisForegroundService.stop(context)
                _analysisState.value = AnalysisState.Idle
            }
        } catch (e: GeminiApiException) {
            if (!isCurrentRun(runId)) return@launch
            Log.e(MainViewModel.TAG, "GeminiApiException saat menyelesaikan analisis tanpa segmen, HTTP ${e.httpCode ?: "tidak diketahui"}.")
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
            if (!isCurrentRun(runId)) return@launch
            Log.e(MainViewModel.TAG, "Kesalahan saat menyelesaikan analisis tanpa segmen: ${e.javaClass.simpleName}.")
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
        } finally {
            if (!finishTotalSaved) {
                stageTimingStorage.addStageDuration(
                    targetId,
                    StageTimingStorage.STAGE_TOTAL,
                    elapsedSinceNanosMs(finishStartNanos)
                )
                finishTotalSaved = true
            }
            if (currentTimingAnalysisId == targetId) {
                currentTimingAnalysisId = null
            }
        }
    }
}

internal suspend fun MainViewModel.handleErrorAndSaveToDb(entityId: Long?, stage: String, errorMsg: String) {
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
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(MainViewModel.TAG, "Gagal memperbarui status error ke database: ${e.javaClass.simpleName}")
    }
}

internal suspend fun MainViewModel.checkCanResume(entityId: Long?): MainViewModel.ResumeCheckResult {
    if (entityId == null) return MainViewModel.ResumeCheckResult(false, 1, false)
    return try {
        val entity = repository.getAnalysisById(entityId)
        if (entity != null && entity.completedSegmentsCount > 0 && entity.status != "COMPLETED") {
            MainViewModel.ResumeCheckResult(
                canResume = entity.completedSegmentsCount < entity.totalSegments,
                resumeSegment = entity.completedSegmentsCount + 1,
                canSkipAndFinish = true
            )
        } else {
            MainViewModel.ResumeCheckResult(false, 1, false)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        MainViewModel.ResumeCheckResult(false, 1, false)
    }
}
