package com.example

import com.example.data.api.GeminiConstants
import com.example.data.db.AnalysisJsonAdapter
import com.example.data.db.AnimeAnalysisEntity
import com.example.data.model.AnalysisState
import com.example.data.model.CharacterInfo
import com.example.data.model.TimelineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.math.ceil

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleUnitTest {

    @Test
    fun verifyGeminiModelConstant() {
        assertEquals("gemini-3.8-flash", GeminiConstants.GEMINI_MODEL)
    }

    @Test
    fun verifySegmentCountFor25MinuteEpisode() {
        // 25 menit = 1500 detik. Rentang segmen = 180 detik (3 menit)
        val totalSeconds = 25 * 60L
        val segmentDuration = GeminiConstants.SEGMENT_DURATION_SECONDS
        val totalSegments = ceil(totalSeconds.toDouble() / segmentDuration).toInt()
        assertEquals(9, totalSegments)
    }

    @Test
    fun verifySegmentCountFor28MinuteEpisode() {
        // 28 menit = 1680 detik. Rentang segmen = 180 detik (3 menit)
        val totalSeconds = 28 * 60L
        val segmentDuration = GeminiConstants.SEGMENT_DURATION_SECONDS
        val totalSegments = ceil(totalSeconds.toDouble() / segmentDuration).toInt()
        assertEquals(10, totalSegments)
    }

    @Test
    fun verifyContinuousTimestampsAcrossSegments() {
        val totalSeconds = 25 * 60L
        val totalSegments = ceil(totalSeconds.toDouble() / GeminiConstants.SEGMENT_DURATION_SECONDS).toInt()
        var lastEnd = 0L

        for (i in 0 until totalSegments) {
            val startSec = i * GeminiConstants.SEGMENT_DURATION_SECONDS
            val endSec = minOf((i + 1) * GeminiConstants.SEGMENT_DURATION_SECONDS, totalSeconds)

            assertEquals("Start of segment must be continuous with previous end", lastEnd, startSec)
            assertTrue("End must be greater than start", endSec > startSec)
            lastEnd = endSec
        }

        assertEquals(totalSeconds, lastEnd)
    }

    @Test
    fun verifyTimeFormatting() {
        fun format(sec: Long): String {
            val m = sec / 60
            val s = sec % 60
            return String.format(Locale.US, "%02d:%02d", m, s)
        }

        assertEquals("00:00", format(0))
        assertEquals("03:00", format(180))
        assertEquals("06:00", format(360))
        assertEquals("25:00", format(1500))
    }

    @Test
    fun verifyErrorReportFormatting() {
        val error = AnalysisState.Error(
            stage = "Analisis Segmen 2 dari 9",
            httpCode = 429,
            rawApiMessage = "{\"error\": {\"code\": 429, \"message\": \"RESOURCE_EXHAUSTED\"}}",
            errorMessage = "Batas kuota gratis Gemini terlampaui.",
            canResume = true,
            resumeSegment = 3,
            analysisId = 12L
        )

        val report = error.getFullReport()
        assertTrue(report.contains("Tahap: Analisis Segmen 2 dari 9"))
        assertTrue(report.contains("Kode HTTP: 429"))
        assertTrue(report.contains("RESOURCE_EXHAUSTED"))
        assertTrue(report.contains("Batas kuota gratis Gemini terlampaui."))
        assertTrue(error.canResume)
        assertEquals(3, error.resumeSegment)
    }

    @Test
    fun verifyAnalysisJsonAdapter() {
        val characters = listOf(
            CharacterInfo(name = "Taro", description = "Spiky black hair, dark eyes, black uniform")
        )
        val charJson = AnalysisJsonAdapter.characterSheetToJson(characters)
        val parsedChars = AnalysisJsonAdapter.jsonToCharacterSheet(charJson)
        assertEquals(1, parsedChars.size)
        assertEquals("Taro", parsedChars[0].name)

        val timeline = listOf(
            TimelineItem(
                timestamp = "00:00 - 00:05",
                sceneDescription = "Opening shot of school rooftop",
                visualPrompt = "2D Japanese anime style, high-definition, clean cel-shaded linework, vibrant colors, detailed background art. Taro standing on rooftop."
            )
        )
        val timelineJson = AnalysisJsonAdapter.timelineToJson(timeline)
        val parsedTimeline = AnalysisJsonAdapter.jsonToTimeline(timelineJson)
        assertEquals(1, parsedTimeline.size)
        assertEquals("00:00 - 00:05", parsedTimeline[0].timestamp)

        val entity = AnimeAnalysisEntity(
            id = 1L,
            videoUriString = "content://media/1",
            videoName = "Episode 01.mp4",
            videoSizeBytes = 100_000_000L,
            durationMs = 1500_000L,
            mimeType = "video/mp4",
            intervalSeconds = 5,
            totalSegments = 9,
            completedSegmentsCount = 9,
            geminiFileRemoteName = "files/test1234",
            geminiFileUri = "https://generativelanguage.googleapis.com/v1beta/files/test1234",
            status = "COMPLETED",
            characterSheetJson = charJson,
            timelineJson = timelineJson,
            segmentSummariesJson = "[\"Summary 1\"]",
            narration = "The episode begins on a dramatic note..."
        )

        val result = entity.toAnimeAnalysisResult()
        assertEquals(entity.narration, result.narration)
        assertEquals(entity.videoName, result.videoName)
        assertEquals(1, result.characterSheet.size)
        assertEquals(1, result.timeline.size)
        assertTrue(entity.isCompleted)

        // Test ExportFormatters
        val exportText = com.example.ui.components.ExportFormatters.formatFullExport(result)
        assertTrue(exportText.contains("Episode 01.mp4"))
        assertTrue(exportText.contains("Taro"))

        val visualPrompts = com.example.ui.components.ExportFormatters.formatAllVisualPromptsOnly(result.timeline)
        assertTrue(visualPrompts.contains("Taro standing on rooftop"))
    }

    @Test
    fun testSegmentCalculator_mergesLastSegmentWhenUnder90Seconds() {
        // Durasi 1500 detik (25 menit pas) dengan opsi 3 menit:
        // 1500 / 180 = 8 segmen penuh (1440s), sisa 60s (< 90s).
        // Sisa 60s harus digabung ke segmen 8 (1260s - 1500s), total segmen = 8
        val segments25Min = com.example.util.SegmentCalculator.calculateSegments(1500L, com.example.util.SegmentDurationOption.THREE_MIN)
        assertEquals(8, segments25Min.size)
        assertEquals(0L, segments25Min.first().startSec)
        assertEquals(180L, segments25Min.first().endSec)
        assertEquals(1260L, segments25Min.last().startSec)
        assertEquals(1500L, segments25Min.last().endSec)
        assertEquals(240L, segments25Min.last().durationSec)
        // End offset segmen terakhir tidak boleh melebihi durasi sebenarnya
        assertTrue(segments25Min.last().endSec <= 1500L)

        // Durasi 1550 detik dengan opsi 3 menit:
        // Sisa setelah 1440s adalah 110s (>= 90s).
        // Maka segmen 9 berdiri sendiri (1440s - 1550s, 110s). Total = 9
        val segmentsWith9th = com.example.util.SegmentCalculator.calculateSegments(1550L, com.example.util.SegmentDurationOption.THREE_MIN)
        assertEquals(9, segmentsWith9th.size)
        assertEquals(1440L, segmentsWith9th.last().startSec)
        assertEquals(1550L, segmentsWith9th.last().endSec)
        assertEquals(110L, segmentsWith9th.last().durationSec)

        // Video pendek 70 detik (< 90s)
        val shortVideo = com.example.util.SegmentCalculator.calculateSegments(70L)
        assertEquals(1, shortVideo.size)
        assertEquals(0L, shortVideo[0].startSec)
        assertEquals(70L, shortVideo[0].endSec)

        // Video 200 detik: segmen 1 (180s), sisa 20s (< 90s) digabung -> 1 segmen (0-200s)
        val mergedVideo = com.example.util.SegmentCalculator.calculateSegments(200L)
        assertEquals(1, mergedVideo.size)
        assertEquals(0L, mergedVideo[0].startSec)
        assertEquals(200L, mergedVideo[0].endSec)
    }

    @Test
    fun testSkippedLastSegmentNote() {
        val entity = AnimeAnalysisEntity(
            id = 2L,
            videoUriString = "content://media/2",
            videoName = "Video 09.mp4",
            videoSizeBytes = 50_000_000L,
            durationMs = 1500_000L,
            mimeType = "video/mp4",
            intervalSeconds = 3,
            totalSegments = 8,
            completedSegmentsCount = 8,
            geminiFileRemoteName = "files/test999",
            geminiFileUri = "https://generativelanguage.googleapis.com/v1beta/files/test999",
            status = "COMPLETED",
            characterSheetJson = "[]",
            timelineJson = "[]",
            segmentSummariesJson = "[\"Summary\"]",
            narration = "[Catatan: Segmen terakhir dilewati]\n\nRecap",
            lastErrorStage = "Selesai (Segmen dilewati)",
            lastErrorMessage = "Segmen terakhir dilewati"
        )

        val result = entity.toAnimeAnalysisResult()
        assertEquals("Segmen terakhir dilewati", result.note)
        assertTrue(result.narration.contains("Segmen terakhir dilewati"))
    }

    @Test
    fun testAutoSegmentRules() {
        // Durasi sampai 4,5 menit (<= 270s) = 1 segmen
        val segmentsUnder4_5Min = com.example.util.SegmentCalculator.calculateSegments(240L, com.example.util.SegmentDurationOption.AUTO)
        assertEquals(1, segmentsUnder4_5Min.size)
        assertEquals(0L, segmentsUnder4_5Min[0].startSec)
        assertEquals(240L, segmentsUnder4_5Min[0].endSec)

        // Durasi 270s tepat = 1 segmen
        val segmentsExact4_5Min = com.example.util.SegmentCalculator.calculateSegments(270L, com.example.util.SegmentDurationOption.AUTO)
        assertEquals(1, segmentsExact4_5Min.size)
        assertEquals(270L, segmentsExact4_5Min[0].endSec)

        // Durasi sampai 12 menit (<= 720s, e.g. 600s = 10 menit) = segmen 3 menit (180s)
        val segments10Min = com.example.util.SegmentCalculator.calculateSegments(600L, com.example.util.SegmentDurationOption.AUTO)
        // 600s: 180, 180, 180, sisa 60s (<90s) digabung ke segmen ke-3 -> 3 segmen
        assertEquals(3, segments10Min.size)
        assertEquals(0L, segments10Min[0].startSec)
        assertEquals(180L, segments10Min[0].endSec)
        assertEquals(180L, segments10Min[1].startSec)
        assertEquals(360L, segments10Min[1].endSec)
        assertEquals(360L, segments10Min[2].startSec)
        assertEquals(600L, segments10Min[2].endSec)

        // Durasi di atas 12 menit (> 720s, e.g. 1500s = 25 menit) = segmen 5 menit (300s)
        val segments25MinAuto = com.example.util.SegmentCalculator.calculateSegments(1500L, com.example.util.SegmentDurationOption.AUTO)
        // 1500s / 300s = 5 segmen (300, 300, 300, 300, 300)
        assertEquals(5, segments25MinAuto.size)
        assertEquals(0L, segments25MinAuto[0].startSec)
        assertEquals(300L, segments25MinAuto[0].endSec)
        assertEquals(1200L, segments25MinAuto.last().startSec)
        assertEquals(1500L, segments25MinAuto.last().endSec)

        // Opsi eksplisit 3 Menit untuk video 25 menit (1500s)
        val segments3MinExplicit = com.example.util.SegmentCalculator.calculateSegments(1500L, com.example.util.SegmentDurationOption.THREE_MIN)
        // 1500: 8 segmen (sisa 60s digabung)
        assertEquals(8, segments3MinExplicit.size)

        // Opsi eksplisit 5 Menit
        val segments5MinExplicit = com.example.util.SegmentCalculator.calculateSegments(1500L, com.example.util.SegmentDurationOption.FIVE_MIN)
        assertEquals(5, segments5MinExplicit.size)
    }

    @Test
    fun testPlanSummaryCalculation() {
        val summary = com.example.util.SegmentCalculator.calculatePlanSummary(
            totalSeconds = 1500L,
            option = com.example.util.SegmentDurationOption.AUTO,
            intervalSeconds = 5
        )
        assertEquals(1500L, summary.totalSeconds)
        assertEquals("25:00 (1500 detik)", summary.totalDurationFormatted)
        assertEquals("Otomatis (5 Menit)", summary.segmentDurationLabel)
        assertEquals(5, summary.segmentCount)
        assertEquals(6, summary.totalRequestsCount) // 5 segmen + 1 narasi
        assertEquals(300, summary.estimatedScenesCount) // 1500 / 5
    }

    @Test
    fun testRequestEstimateMatchesNarrationAndSeriesFeatures() {
        assertEquals(5, com.example.util.SegmentCalculator.estimateGeminiRequestCount(5, false, false))
        assertEquals(6, com.example.util.SegmentCalculator.estimateGeminiRequestCount(5, true, false))
        assertEquals(7, com.example.util.SegmentCalculator.estimateGeminiRequestCount(5, true, true))
        assertEquals(Int.MAX_VALUE, com.example.util.SegmentCalculator.estimateGeminiRequestCount(Int.MAX_VALUE, true, true))
    }

    @Test
    fun testSegmentRangeAtOrBeyondVideoEndIsNormalizedSafely() {
        val atEnd = com.example.util.SegmentCalculator.calculateSegments(
            totalSeconds = 100L,
            rangeStartSec = 100L,
            rangeEndSec = 100L
        )
        assertEquals(99L, atEnd.single().startSec)
        assertEquals(100L, atEnd.single().endSec)

        val beyondEnd = com.example.util.SegmentCalculator.calculatePlanSummary(
            totalSeconds = 100L,
            rangeStartSec = 120L,
            rangeEndSec = 150L
        )
        assertEquals(99L, beyondEnd.rangeStartSec)
        assertEquals(100L, beyondEnd.rangeEndSec)
        assertEquals(1, beyondEnd.segmentCount)
    }

    @Test
    fun testResumePolicySkipsSegmentsWhenAllCheckpointsAreComplete() {
        val policy = com.example.data.model.AnalysisResumePolicy
        assertEquals(3, policy.restoredSegmentIndex(3, 8))
        assertEquals(8, policy.restoredSegmentIndex(8, 8))
        assertEquals(8, policy.restoredSegmentIndex(12, 8))
        assertTrue(policy.shouldAnalyzeSegments(3, 8))
        assertTrue(!policy.shouldAnalyzeSegments(8, 8))
        assertTrue(!policy.shouldAnalyzeSegments(0, 0))
    }

    @Test
    fun testParseTimeSecondsRejectsOverflowAndUnreasonableDuration() {
        assertNull(com.example.util.SegmentCalculator.parseTimeSeconds("9223372036854775807:59"))
        assertNull(com.example.util.SegmentCalculator.parseTimeSeconds("2562047788015215:30:00"))
        assertNull(com.example.util.SegmentCalculator.parseTimeSeconds("9223372036854775800"))
        assertEquals(3661L, com.example.util.SegmentCalculator.parseTimeSeconds("1:01:01"))
    }

    @Test
    fun testVideoMatchingRequiresKnownMetadataAndExactSize() {
        val policy = com.example.data.model.VideoMatchPolicy

        assertTrue(policy.matches(60_000L, 50_000_000L, 60_000L, 50_000_000L))
        assertTrue(policy.matches(60_000L, 50_000_000L, 61_500L, 50_000_000L))
        assertTrue(policy.matches(60_000L, 50_000_000L, 62_000L, 50_000_000L))

        assertTrue(!policy.matches(60_000L, 50_000_000L, 63_000L, 50_000_000L))
        assertTrue(!policy.matches(60_000L, 50_000_000L, 60_000L, 50_000_001L))
        assertTrue(!policy.matches(0L, 50_000_000L, 60_000L, 50_000_000L))
        assertTrue(!policy.matches(60_000L, 0L, 60_000L, 50_000_000L))
        assertTrue(!policy.matches(60_000L, 50_000_000L, 0L, 50_000_000L))
        assertTrue(!policy.matches(60_000L, 50_000_000L, 60_000L, 0L))

        // When both fingerprints are available, metadata matches must not override content mismatch.
        assertTrue(policy.matches(
            60_000L, 50_000_000L, 60_000L, 50_000_000L,
            savedFingerprint = "aaa", selectedFingerprint = "aaa"
        ))
        assertTrue(!policy.matches(
            60_000L, 50_000_000L, 60_000L, 50_000_000L,
            savedFingerprint = "aaa", selectedFingerprint = "bbb"
        ))
        // Legacy rows and providers without seek support continue using strict metadata matching.
        assertTrue(policy.matches(
            60_000L, 50_000_000L, 60_000L, 50_000_000L,
            savedFingerprint = "", selectedFingerprint = "bbb"
        ))
    }

    @Test
    fun testQuotaHelperDailyQuotaPerDay() {
        val errorJson = """
            {
              "error": {
                "code": 429,
                "message": "Resource has been exhausted (e.g. check quota).",
                "status": "RESOURCE_EXHAUSTED",
                "details": [
                  {
                    "@type": "type.googleapis.com/google.rpc.QuotaFailure",
                    "violations": [
                      {
                        "quotaId": "GenerateContentRequestsPerDayPerProject"
                      }
                    ]
                  },
                  {
                    "@type": "type.googleapis.com/google.rpc.RetryInfo",
                    "retryDelay": "82451.782049381s"
                  }
                ]
              }
            }
        """.trimIndent()

        val result = com.example.util.QuotaHelper.checkQuotaError(429, errorJson)
        assertTrue(result.isDailyQuota)
        assertEquals(82451L, result.retryDelaySeconds)
        assertTrue(result.userMessage.startsWith("Kuota harian habis. Coba lagi sekitar"))
        assertTrue(result.userMessage.contains("jam"))
        assertTrue(result.userMessage.contains("menit"))
    }

    @Test
    fun testQuotaHelperRetryDelayOver5MinWithoutPerDay() {
        // Retry delay panjang tidak otomatis berarti kuota harian tanpa indikator PerDay.
        val errorJson = """
            {
              "error": {
                "code": 429,
                "message": "Too many requests. Please slow down.",
                "details": [
                  {
                    "@type": "type.googleapis.com/google.rpc.RetryInfo",
                    "retryDelay": "600s"
                  }
                ]
              }
            }
        """.trimIndent()

        val result = com.example.util.QuotaHelper.checkQuotaError(429, errorJson)
        assertTrue(!result.isDailyQuota)
        assertEquals(600L, result.retryDelaySeconds)
        assertEquals("Batas permintaan sedang aktif. Coba lagi sekitar 10 menit.", result.userMessage)
    }

    @Test
    fun testQuotaHelperPerMinuteQuotaAllowsRetry() {
        // Error 429 per menit (retryDelay <= 300s dan tidak ada PerDay)
        val errorJson = """
            {
              "error": {
                "code": 429,
                "message": "Rate limit exceeded: Requests per minute",
                "details": [
                  {
                    "@type": "type.googleapis.com/google.rpc.RetryInfo",
                    "retryDelay": "20s"
                  }
                ]
              }
            }
        """.trimIndent()

        val result = com.example.util.QuotaHelper.checkQuotaError(429, errorJson)
        // Bukan daily quota -> boleh auto-retry 3 kali
        assertTrue(!result.isDailyQuota)
        assertEquals(20L, result.retryDelaySeconds)
        assertTrue(result.userMessage.contains("per menit"))
    }

    @Test
    fun testQuotaHelperFormatting() {
        // 7500 detik = 2 jam 5 menit
        assertEquals("2 jam 5 menit", com.example.util.QuotaHelper.formatWaitTimeHoursAndMinutes(7500L))
        // 3600 detik = 1 jam
        assertEquals("1 jam", com.example.util.QuotaHelper.formatWaitTimeHoursAndMinutes(3600L))
        // 540 detik = 9 menit
        assertEquals("9 menit", com.example.util.QuotaHelper.formatWaitTimeHoursAndMinutes(540L))
        // null / 0 -> 24 jam fallback
        assertEquals("24 jam", com.example.util.QuotaHelper.formatWaitTimeHoursAndMinutes(null))
        assertEquals("24 jam", com.example.util.QuotaHelper.formatWaitTimeHoursAndMinutes(0L))
    }

    @Test
    fun testErrorContentResumeAndCompletedSegmentsState() {
        val error = AnalysisState.Error(
            stage = "Analisis Segmen 3 dari 9",
            httpCode = 429,
            rawApiMessage = "Resource exhausted: RequestsPerDay",
            errorMessage = "Kuota harian habis. Coba lagi sekitar 2 jam 15 menit",
            canRetry = false,
            canResume = true,
            resumeSegment = 3,
            analysisId = 42L,
            isDailyQuota = true,
            completedSegmentsCount = 2,
            totalSegments = 9,
            completedSummaries = listOf("Ringkasan Segmen 1", "Ringkasan Segmen 2")
        )

        assertTrue(error.isDailyQuota)
        assertEquals(2, error.completedSegmentsCount)
        assertEquals(9, error.totalSegments)
        assertEquals(2, error.completedSummaries.size)
        assertEquals(3, error.resumeSegment)
        assertEquals("Kuota harian habis. Coba lagi sekitar 2 jam 15 menit", error.errorMessage)
    }

    @Test
    fun testGeminiErrorReportUsesActualModelWhenAvailable() {
        val error = com.example.data.api.GeminiApiException(
            stage = "Analisis segmen",
            httpCode = 429,
            rawMessage = "rate limited",
            userFriendlyMessage = "Batas permintaan",
            modelName = "gemini-2.5-flash"
        )

        assertTrue(error.toErrorReport().contains("Model: gemini-2.5-flash"))
    }

    @Test
    fun testQuotaCheckPerDayDetection() {
        val bodyWithPerDay = """
            {
              "error": {
                "code": 429,
                "message": "Resource has been exhausted (e.g. check quota). Quota exceeded for quota metric 'GenerateContent requests' and limit 'GenerateContent requests per day' of service 'generativelanguage.googleapis.com'.",
                "status": "RESOURCE_EXHAUSTED",
                "details": [
                  {
                    "@type": "type.googleapis.com/google.rpc.QuotaFailure",
                    "violations": [
                      {
                        "quotaMetric": "GenerateContentRequestsPerDay"
                      }
                    ]
                  },
                  {
                    "@type": "type.googleapis.com/google.rpc.RetryInfo",
                    "retryDelay": "82451.78s"
                  }
                ]
              }
            }
        """.trimIndent()

        val result = com.example.util.QuotaHelper.checkQuotaError(429, bodyWithPerDay)
        assertTrue(result.isDailyQuota)
        assertEquals(82451L, result.retryDelaySeconds)
        assertTrue(result.userMessage.startsWith("Kuota harian habis. Coba lagi sekitar"))
        assertTrue(result.formattedWaitTime.contains("jam"))
    }

    @Test
    fun testQuotaCheckPerMinuteDetection() {
        val bodyPerMinute = """
            {
              "error": {
                "code": 429,
                "message": "Resource has been exhausted: rate limit exceeded per minute.",
                "status": "RESOURCE_EXHAUSTED",
                "details": [
                  {
                    "@type": "type.googleapis.com/google.rpc.RetryInfo",
                    "retryDelay": "45s"
                  }
                ]
              }
            }
        """.trimIndent()

        val result = com.example.util.QuotaHelper.checkQuotaError(429, bodyPerMinute)
        org.junit.Assert.assertFalse(result.isDailyQuota)
        assertEquals(45L, result.retryDelaySeconds)
    }

    @Test
    fun testApiKeyItemMaskingAndHash() {
        val key = "AIzaSyB1234567890abcdefx7Q2"
        val item = com.example.data.model.ApiKeyItem(
            name = "Key 1",
            key = key,
            isActive = true
        )
        assertEquals("AIza...x7Q2", item.maskedKey)
        assertTrue(item.keyHash.isNotBlank())
        assertEquals(8, item.keyHash.length)
    }

    @Test
    fun testModelStorageDefaultsAndManagement() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        // Bersihkan SharedPreferences untuk menguji kondisi awal bawaan belum ada daftar tersimpan
        context.getSharedPreferences("rekapin_model_prefs", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val storage = com.example.data.prefs.ModelStorage(context)

        // 1. Jika belum ada daftar tersimpan, getModels() mengembalikan tiga model berurutan:
        // gemini-3.8-flash, gemini-3.6-flash, gemini-3.5-flash-lite
        val initialModels = storage.getModels()
        assertEquals(3, initialModels.size)
        assertEquals("gemini-3.8-flash", initialModels[0])
        assertEquals("gemini-3.6-flash", initialModels[1])
        assertEquals("gemini-3.5-flash-lite", initialModels[2])

        // Model terpilih bawaan tetap yang pertama (gemini-3.8-flash)
        assertEquals("gemini-3.8-flash", storage.getSelectedModel())

        // 2. Pertahankan DEFAULT_MODEL (gemini-3.8-flash) sebagai cadangan
        assertEquals("gemini-3.8-flash", com.example.data.prefs.ModelStorage.DEFAULT_MODEL)

        // Default auto-switch: mati (false)
        org.junit.Assert.assertFalse(storage.isAutoSwitchOnQuota())

        // Tambah model bebas
        storage.addModel("gemini-2.5-pro")
        storage.addModel("custom-model-test")
        val updatedModels = storage.getModels()
        assertTrue(updatedModels.contains("gemini-2.5-pro"))
        assertTrue(updatedModels.contains("custom-model-test"))

        // Pilih model
        storage.setSelectedModel("gemini-2.5-pro")
        assertEquals("gemini-2.5-pro", storage.getSelectedModel())

        // Ubah urutan
        val idxPro = storage.getModels().indexOf("gemini-2.5-pro")
        if (idxPro > 0) {
            storage.moveModel(idxPro, 0)
            assertEquals("gemini-2.5-pro", storage.getModels().first())
        }

        // Hapus model
        storage.deleteModel("custom-model-test")
        org.junit.Assert.assertFalse(storage.getModels().contains("custom-model-test"))

        // Toggle auto-switch
        storage.setAutoSwitchOnQuota(true)
        assertTrue(storage.isAutoSwitchOnQuota())
    }

    @Test
    fun testModelStorageMigratesRemoved37FlashOnceAndAllowsManualReAdd() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = context.getSharedPreferences("rekapin_model_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        // Simulasikan HP dengan daftar model lama dan model terpilih gemini-3.7-flash
        val oldListJson = org.json.JSONArray(
            listOf(
                "gemini-3.8-flash",
                "gemini-3.6-flash",
                "gemini-3.7-flash",
                "gemini-3.5-flash-lite"
            )
        ).toString()
        prefs.edit()
            .putString("gemini_models_list_json", oldListJson)
            .putString("selected_gemini_model", "gemini-3.7-flash")
            .commit()

        val firstStorage = com.example.data.prefs.ModelStorage(context)
        assertEquals(
            listOf("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash-lite"),
            firstStorage.getModels()
        )
        assertEquals("gemini-3.8-flash", firstStorage.getSelectedModel())
        assertTrue(prefs.getBoolean("migrated_remove_gemini_3_7_flash", false))

        // Membuat ModelStorage kedua kali tidak mengubah apa pun
        val secondStorage = com.example.data.prefs.ModelStorage(context)
        assertEquals(
            listOf("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash-lite"),
            secondStorage.getModels()
        )
        assertEquals("gemini-3.8-flash", secondStorage.getSelectedModel())

        // Penambahan manual gemini-3.7-flash setelah migrasi tetap berhasil dan bertahan saat dibuat ulang
        assertTrue(secondStorage.addModel("gemini-3.7-flash"))
        assertTrue(secondStorage.getModels().contains("gemini-3.7-flash"))

        val thirdStorage = com.example.data.prefs.ModelStorage(context)
        assertEquals(
            listOf("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-3.7-flash"),
            thirdStorage.getModels()
        )
    }

    @Test
    fun testUnrecognizedModel404ErrorParsing() {
        val client = com.example.data.api.GeminiApiClient(
            apiKeyProvider = { "fake-key" }
        )

        // Refleksi parseErrorMessage untuk memverifikasi HTTP 404 dan pesan model tidak dikenal
        val method = com.example.data.api.GeminiApiClient::class.java.getDeclaredMethod(
            "parseErrorMessage",
            String::class.java,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true

        val error404Json = """
            {
              "error": {
                "code": 404,
                "message": "models/gemini-invalid is not found for API version v1beta, or is not supported for generateContent.",
                "status": "NOT_FOUND"
              }
            }
        """.trimIndent()

        val parsedMsg = method.invoke(client, error404Json, 404) as String
        assertEquals("Nama model tidak dikenal, periksa ejaannya", parsedMsg)

        val nonJson404 = method.invoke(client, "Not Found", 404) as String
        assertEquals("Nama model tidak dikenal, periksa ejaannya", nonJson404)
    }

    @Test
    fun testSegmentModelsInResultAndExport() {
        val result = com.example.data.model.AnimeAnalysisResult(
            narration = "Narasi lengkap.",
            characterSheet = emptyList(),
            timeline = emptyList(),
            totalSegments = 2,
            durationFormatted = "06:00",
            intervalSeconds = 5,
            videoName = "Anime.mp4",
            segmentModels = listOf("gemini-3.8-flash", "gemini-2.5-pro"),
            segmentSummaries = listOf("Segmen 1 summary", "Segmen 2 summary")
        )

        assertEquals("gemini-3.8-flash", result.segmentModels[0])
        assertEquals("gemini-2.5-pro", result.segmentModels[1])

        val fullExport = com.example.ui.components.ExportFormatters.formatFullExport(result)
        assertTrue(fullExport.contains("D. HASIL RINGKASAN PER SEGMEN"))
        assertTrue(fullExport.contains("Segmen 1 (Model: gemini-3.8-flash)"))
        assertTrue(fullExport.contains("Segmen 2 (Model: gemini-2.5-pro)"))

        val segExport = com.example.ui.components.ExportFormatters.formatSegmentSummaries(result.segmentSummaries, result.segmentModels)
        assertTrue(segExport.contains("Segmen 1 [Model: gemini-3.8-flash]"))
        assertTrue(segExport.contains("Segmen 2 [Model: gemini-2.5-pro]"))
    }

    @Test
    fun testNarrationLanguageAndStorage() {
        // Test NarrationLanguage enum values and parsing
        assertEquals("English (USA)", com.example.data.model.NarrationLanguage.ENGLISH_USA.displayName)
        assertEquals("Bahasa Indonesia", com.example.data.model.NarrationLanguage.INDONESIAN.displayName)
        assertEquals(com.example.data.model.NarrationLanguage.ENGLISH_USA, com.example.data.model.NarrationLanguage.DEFAULT)

        assertEquals(
            com.example.data.model.NarrationLanguage.ENGLISH_USA,
            com.example.data.model.NarrationLanguage.fromDisplayName("English (USA)")
        )
        assertEquals(
            com.example.data.model.NarrationLanguage.INDONESIAN,
            com.example.data.model.NarrationLanguage.fromDisplayName("Bahasa Indonesia")
        )
        // Fallback for null or unknown
        assertEquals(
            com.example.data.model.NarrationLanguage.ENGLISH_USA,
            com.example.data.model.NarrationLanguage.fromDisplayName(null)
        )
        assertEquals(
            com.example.data.model.NarrationLanguage.ENGLISH_USA,
            com.example.data.model.NarrationLanguage.fromDisplayName("Unknown Language")
        )

        // Test NarrationLanguageStorage persistence
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("rekapin_language_prefs", android.content.Context.MODE_PRIVATE).edit().clear().commit()

        val storage = com.example.data.prefs.NarrationLanguageStorage(context)
        // Default must be English (USA)
        assertEquals(com.example.data.model.NarrationLanguage.ENGLISH_USA, storage.getLanguage())
        assertEquals("English (USA)", storage.getLanguageDisplayName())

        // Set to Bahasa Indonesia
        storage.setLanguage(com.example.data.model.NarrationLanguage.INDONESIAN)
        assertEquals(com.example.data.model.NarrationLanguage.INDONESIAN, storage.getLanguage())
        assertEquals("Bahasa Indonesia", storage.getLanguageDisplayName())

        // Re-read with a new storage instance to verify persistence in SharedPreferences
        val reloadedStorage = com.example.data.prefs.NarrationLanguageStorage(context)
        assertEquals(com.example.data.model.NarrationLanguage.INDONESIAN, reloadedStorage.getLanguage())

        // Switch back to English (USA)
        reloadedStorage.setLanguage("English (USA)")
        assertEquals(com.example.data.model.NarrationLanguage.ENGLISH_USA, reloadedStorage.getLanguage())
    }

    @Test
    fun testModelUsageStorageDefaultsLimitsAndIncrement() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("rekapin_model_usage_prefs", android.content.Context.MODE_PRIVATE).edit().clear().commit()

        val storage = com.example.data.prefs.ModelUsageStorage(context)

        // 1. Batas harian default: 20 untuk model biasa, 500 untuk flash-lite
        assertEquals(20, storage.getDefaultLimit("gemini-3.8-flash"))
        assertEquals(20, storage.getDefaultLimit("gemini-3.6-flash"))
        assertEquals(20, storage.getDefaultLimit("gemini-3.5-flash"))
        assertEquals(500, storage.getDefaultLimit("gemini-3.5-flash-lite"))
        assertEquals(500, storage.getDefaultLimit("models/GEMINI-2.5-FLASH-LITE-preview"))

        // Batas default tersimpan
        assertEquals(20, storage.getLimit("gemini-3.8-flash"))
        assertEquals(500, storage.getLimit("gemini-3.5-flash-lite"))

        // Edit batas harian
        storage.setLimit("gemini-3.8-flash", 50)
        assertEquals(50, storage.getLimit("gemini-3.8-flash"))

        // Initial usage: 0
        assertEquals(0, storage.getUsage("gemini-3.8-flash"))
        val infoBefore = storage.getUsageInfo("gemini-3.8-flash")
        assertEquals(0, infoBefore.used)
        assertEquals(50, infoBefore.limit)
        assertEquals(50, infoBefore.remaining)
        org.junit.Assert.assertFalse(infoBefore.isQuotaExceeded)

        // Increment usage
        val count1 = storage.incrementUsage("gemini-3.8-flash")
        assertEquals(1, count1)
        val count2 = storage.incrementUsage("gemini-3.8-flash")
        assertEquals(2, count2)
        assertEquals(2, storage.getUsage("gemini-3.8-flash"))

        val infoAfter = storage.getUsageInfo("gemini-3.8-flash")
        assertEquals(2, infoAfter.used)
        assertEquals(48, infoAfter.remaining)
        assertTrue(infoAfter.resetTimeFormatted.startsWith("~") || infoAfter.resetTimeFormatted.startsWith("besok ~"))
    }

    @Test
    fun testModelUsageStorageQuotaExceededAndRetryDelayReset() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("rekapin_model_usage_prefs", android.content.Context.MODE_PRIVATE).edit().clear().commit()

        val storage = com.example.data.prefs.ModelUsageStorage(context)
        val model = "gemini-3.8-flash"

        // Set quota exceeded dengan retryDelay 3600 detik (1 jam)
        storage.markQuotaExceeded(model, 3600L)
        val usageInfo = storage.getUsageInfo(model)

        // Hitungan menjadi penuh
        assertEquals(20, usageInfo.used)
        assertEquals(0, usageInfo.remaining)
        assertTrue(usageInfo.isQuotaExceeded)

        // Waktu reset harus menggunakan retryDelay (~1 jam dari sekarang), bukan tengah malam Pasifik
        val resetEpoch = storage.getResetEpochMillis(model)
        val expectedMinEpoch = System.currentTimeMillis() + 3500_000L
        val expectedMaxEpoch = System.currentTimeMillis() + 3700_000L
        assertTrue("Reset epoch should be within 1 hour", resetEpoch in expectedMinEpoch..expectedMaxEpoch)

        // Atur manual hitungan ke 5
        storage.setUsage(model, 5)
        assertEquals(5, storage.getUsage(model))
        assertEquals(15, storage.getUsageInfo(model).remaining)
    }

    @Test
    fun testModelUsageStoragePacificDateAutoReset() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = context.getSharedPreferences("rekapin_model_usage_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val storage = com.example.data.prefs.ModelUsageStorage(context)
        val model = "gemini-3.6-flash"

        // Simpan hitungan dengan tanggal hari kemarin (kemarin di Pasifik)
        prefs.edit()
            .putInt("usage_count_$model", 18)
            .putString("usage_date_$model", "2020-01-01")
            .commit()

        // Saat dibaca, karena tanggal Pasifik berbeda, hitungan harus otomatis di-reset ke 0!
        assertEquals(0, storage.getUsage(model))
        assertEquals(20, storage.getUsageInfo(model).remaining)
    }

    @Test
    fun testVisualStyleStorageBuiltInStyles() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("rekapin_visual_styles", android.content.Context.MODE_PRIVATE).edit().clear().commit()

        val storage = com.example.data.prefs.VisualStyleStorage(context)
        val styles = storage.getAllStyles()

        assertEquals(8, styles.size)
        assertEquals("Anime 2D standar", styles[0].name)
        assertEquals("Shonen (aksi)", styles[1].name)
        assertEquals("Shoujo (romantis)", styles[2].name)
        assertEquals("Seinen (dewasa)", styles[3].name)
        assertEquals("Slice of life (lembut)", styles[4].name)
        assertEquals("Retro 90-an", styles[5].name)
        assertEquals("Komedi / chibi", styles[6].name)
        assertEquals("Fantasi gelap", styles[7].name)

        // Default selected style is "Anime 2D standar"
        val selected = storage.getSelectedStyle()
        assertEquals("Anime 2D standar", selected.name)
        assertTrue(selected.isBuiltIn)
    }

    @Test
    fun testVisualStyleStorageCustomCRUDAndSelection() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("rekapin_visual_styles", android.content.Context.MODE_PRIVATE).edit().clear().commit()

        val storage = com.example.data.prefs.VisualStyleStorage(context)

        // 1. Tambah gaya kustom
        val customStyle = storage.addCustomStyle(
            name = "Mecha Cyberpunk",
            promptDescription = "2D Japanese cyberpunk mecha style, neon lighting, gritty mechanical details"
        )
        assertNotNull(customStyle)
        org.junit.Assert.assertFalse(customStyle.isBuiltIn)

        val allStyles = storage.getAllStyles()
        assertEquals(9, allStyles.size)
        assertEquals("Mecha Cyberpunk", allStyles.last().name)

        // 2. Pilih gaya kustom
        storage.setSelectedStyle(customStyle)
        assertEquals(customStyle.id, storage.getSelectedStyle().id)

        // 3. Ubah gaya kustom
        val updated = storage.updateCustomStyle(
            id = customStyle.id,
            name = "Mecha Cyberpunk HD",
            promptDescription = "2D Japanese cyberpunk anime style with glowing neon"
        )
        assertTrue(updated)
        assertEquals("Mecha Cyberpunk HD", storage.getSelectedStyle().name)

        // 4. Hapus gaya kustom
        val deleted = storage.deleteStyle(customStyle.id)
        assertTrue(deleted)
        assertEquals(8, storage.getAllStyles().size)
        // Setelah dihapus, gaya terpilih kembali ke bawaan
        assertEquals("Anime 2D standar", storage.getSelectedStyle().name)

        // 5. Coba hapus gaya bawaan (tidak boleh terhapus)
        val builtInDelete = storage.deleteStyle("builtin_shonen")
        org.junit.Assert.assertFalse(builtInDelete)
        assertEquals(8, storage.getAllStyles().size)
    }

    @Test
    fun testVisualStylePerAnalysisIsolation() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("rekapin_visual_styles", android.content.Context.MODE_PRIVATE).edit().clear().commit()

        val storage = com.example.data.prefs.VisualStyleStorage(context)
        val shonen = storage.getAllStyles().first { it.id == "builtin_shonen_action" }
        val retro = storage.getAllStyles().first { it.id == "builtin_retro_90s" }

        // Simpan gaya shonen untuk analisis ID 100
        storage.saveStyleForAnalysis(100L, shonen)

        // Ubah gaya terpilih global menjadi retro
        storage.setSelectedStyle(retro)
        assertEquals("Retro 90-an", storage.getSelectedStyle().name)

        // Gaya untuk analisis ID 100 harus tetap shonen, tidak terpengaruh pilihan global terbaru
        val retrievedStyle = storage.getStyleForAnalysis(100L)
        assertNotNull(retrievedStyle)
        assertEquals("Shonen (aksi)", retrievedStyle?.name)
        assertEquals(shonen.promptDescription, retrievedStyle?.promptDescription)
    }

    @Test
    fun testGeminiApiClientPromptUsesSelectedStyle() {
        val client = com.example.data.api.GeminiApiClient(apiKeyProvider = { "dummy-key" })

        val method = com.example.data.api.GeminiApiClient::class.java.getDeclaredMethod(
            "buildSegmentPrompt",
            String::class.java,
            String::class.java,
            Long::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            List::class.java,
            Boolean::class.javaPrimitiveType,
            String::class.java,
            com.example.data.model.NarrationMode::class.java,
            com.example.data.model.NarrationLanguage::class.java,
            List::class.java,
            com.example.data.model.SeriesBible::class.java,
            String::class.java
        )
        method.isAccessible = true

        val customPromptDesc = "2D Japanese dark fantasy anime style, dramatic chiaroscuro lighting"
        val prompt = method.invoke(
            client,
            "00:00",
            "03:00",
            0L,
            180L,
            5,
            emptyList<com.example.data.model.CharacterInfo>(),
            true,
            customPromptDesc,
            com.example.data.model.NarrationMode.REKAP_VOICEOVER,
            com.example.data.model.NarrationLanguage.ENGLISH_USA,
            emptyList<String>(),
            null,
            ""
        ) as String

        // Memastikan prompt mengandung deskripsi gaya terpilih
        assertTrue("Prompt harus memuat deskripsi gaya visual yang dipilih", prompt.contains(customPromptDesc))
        // Memastikan aturan kepatuhan gaya baru ada
        assertTrue(
            "Prompt harus berisi aturan pembatasan gaya",
            prompt.contains("Follow only the selected style; do not mix other styles")
        )
        assertTrue(prompt.contains("[Subject], [Action], [Camera], [Lighting], [Mood], [Background]"))
        assertTrue(prompt.contains("[Background: environment, architecture, and relevant details]"))
        // Memastikan teks lama "Never 3D, never photorealistic" sudah tidak ada
        org.junit.Assert.assertFalse(
            "Prompt tidak boleh mengandung kalimat lama 'Never 3D, never photorealistic'",
            prompt.contains("Never 3D, never photorealistic")
        )
    }

    @Test
    fun testSegmentPromptTranscriptLanguage() {
        val client = com.example.data.api.GeminiApiClient(apiKeyProvider = { "dummy-key" })

        val method = com.example.data.api.GeminiApiClient::class.java.getDeclaredMethod(
            "buildSegmentPrompt",
            String::class.java,
            String::class.java,
            Long::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            List::class.java,
            Boolean::class.javaPrimitiveType,
            String::class.java,
            com.example.data.model.NarrationMode::class.java,
            com.example.data.model.NarrationLanguage::class.java,
            List::class.java,
            com.example.data.model.SeriesBible::class.java,
            String::class.java
        )
        method.isAccessible = true

        // 1. DIALOG_KONTEN dengan Bahasa Indonesia
        val promptDialogId = method.invoke(
            client, "00:00", "03:00", 0L, 180L, 5,
            emptyList<com.example.data.model.CharacterInfo>(), true, "",
            com.example.data.model.NarrationMode.DIALOG_KONTEN,
            com.example.data.model.NarrationLanguage.INDONESIAN,
            emptyList<String>(),
            null,
            "Voice-like activity detected around 00:10-00:13."
        ) as String
        assertTrue(promptDialogId.contains("LOCAL AUDIO ACTIVITY HINT (approximate, not a transcript):"))
        assertTrue(promptDialogId.contains("Voice-like activity detected around 00:10-00:13."))

        assertTrue(
            "Prompt Dialog Konten Indonesia harus memuat aturan LANGUAGE (STRICT) dengan Indonesian",
            promptDialogId.contains("LANGUAGE (STRICT):") &&
                    promptDialogId.contains("The output language is Indonesian. This applies to the transcript fields 'dialogue' and 'originalNarration'.") &&
                    promptDialogId.contains("ALL other fields ('segmentSummary', 'characterSheet', 'sceneDescription', and 'visualPrompt') MUST be written in natural American English.") &&
                    promptDialogId.contains("You MUST translate everything you hear into Indonesian. Never copy the original spoken language.")
        )
        assertTrue(
            "Field dialogue harus memuat instruksi Translate faithfully into Indonesian",
            promptDialogId.contains("Translate faithfully into Indonesian: keep the meaning, tone, and speaker, and do not invent lines. The text MUST be in Indonesian even if the audio is in another language.")
        )
        assertTrue(
            "Prompt Dialog Konten harus memuat pengingat Final check di akhir sebelum JSON format",
            promptDialogId.contains("Final check: every 'dialogue' and 'originalNarration' value must be written in Indonesian.")
        )

        // 2. NARASI_ASLI dengan Bahasa Inggris USA
        val promptNarasiAsliEn = method.invoke(
            client, "00:00", "03:00", 0L, 180L, 5,
            emptyList<com.example.data.model.CharacterInfo>(), true, "",
            com.example.data.model.NarrationMode.NARASI_ASLI,
            com.example.data.model.NarrationLanguage.ENGLISH_USA,
            emptyList<String>(),
            null,
            ""
        ) as String

        assertTrue(
            "Prompt Narasi Asli English USA harus memuat aturan LANGUAGE (STRICT) dengan American English",
            promptNarasiAsliEn.contains("LANGUAGE (STRICT):") &&
                    promptNarasiAsliEn.contains("The output language is American English. This applies to the transcript fields 'dialogue' and 'originalNarration'.") &&
                    promptNarasiAsliEn.contains("ALL other fields ('segmentSummary', 'characterSheet', 'sceneDescription', and 'visualPrompt') MUST be written in natural American English.") &&
                    promptNarasiAsliEn.contains("You MUST translate everything you hear into American English. Never copy the original spoken language.")
        )
        assertTrue(
            "Field originalNarration harus memuat instruksi Translate faithfully into American English",
            promptNarasiAsliEn.contains("Translate faithfully into American English: keep the meaning, tone, and speaker, and do not invent lines. The text MUST be in American English even if the audio is in another language.")
        )
        assertTrue(
            "Prompt Narasi Asli harus memuat pengingat Final check di akhir sebelum JSON format",
            promptNarasiAsliEn.contains("Final check: every 'dialogue' and 'originalNarration' value must be written in American English.")
        )

        // 3. REKAP_VOICEOVER tidak boleh memuat LANGUAGE (STRICT) dan tidak boleh memuat pengingat Final check
        val promptRekap = method.invoke(
            client, "00:00", "03:00", 0L, 180L, 5,
            emptyList<com.example.data.model.CharacterInfo>(), true, "",
            com.example.data.model.NarrationMode.REKAP_VOICEOVER,
            com.example.data.model.NarrationLanguage.INDONESIAN,
            emptyList<String>(),
            null,
            ""
        ) as String

        assertTrue(
            "Prompt Rekap Voiceover harus tetap memakai aturan bahasa umum",
            promptRekap.contains("ALL output text (summary, descriptions, visual prompts, and character sheet) MUST be written in natural American English.")
        )
        org.junit.Assert.assertFalse(
            "Prompt Rekap Voiceover tidak boleh memuat Final check dialog/narasi",
            promptRekap.contains("Final check: every 'dialogue' and 'originalNarration' value must be written in")
        )
        org.junit.Assert.assertFalse(promptRekap.contains("LOCAL AUDIO ACTIVITY HINT"))

        // 4. DIALOG_KONTEN dengan Bahasa Asli (ORIGINAL)
        val promptDialogOriginal = method.invoke(
            client, "00:00", "03:00", 0L, 180L, 5,
            emptyList<com.example.data.model.CharacterInfo>(), true, "",
            com.example.data.model.NarrationMode.DIALOG_KONTEN,
            com.example.data.model.NarrationLanguage.ORIGINAL,
            emptyList<String>(),
            null,
            ""
        ) as String

        assertTrue(
            "Prompt Dialog Konten Bahasa Asli harus memuat aturan LANGUAGE (STRICT) dua kelompok",
            promptDialogOriginal.contains("Transcript fields ('dialogue' and 'originalNarration') MUST be written in the ORIGINAL spoken language of the audio. Do NOT translate them.") &&
                    promptDialogOriginal.contains("ALL other fields ('segmentSummary', 'characterSheet', 'sceneDescription', and 'visualPrompt') MUST be written in natural American English.")
        )
        assertTrue(
            "Prompt Bahasa Asli harus memuat 'Do NOT translate' dan 'ORIGINAL spoken language'",
            promptDialogOriginal.contains("Do NOT translate") &&
                    promptDialogOriginal.contains("ORIGINAL spoken language")
        )
        assertTrue(
            "Field dialogue Bahasa Asli harus meminta transkrip persis apa adanya tanpa terjemahan",
            promptDialogOriginal.contains("Transcribe exactly what is spoken, in the ORIGINAL spoken language of the audio. Do NOT translate. Keep it faithful and do not invent lines. If the language is unclear, use the language that is most clearly heard.")
        )
        assertTrue(
            "Prompt Dialog Konten Bahasa Asli harus memuat pengingat Final check original spoken language",
            promptDialogOriginal.contains("Final check: 'dialogue' and 'originalNarration' must be in the original spoken language, never translated.")
        )
    }

    @Test
    fun testNarrationLanguageOriginalEnum() {
        assertEquals("original", com.example.data.model.NarrationLanguage.ORIGINAL.code)
        assertEquals("Bahasa Asli", com.example.data.model.NarrationLanguage.ORIGINAL.displayName)
        assertEquals(com.example.data.model.NarrationLanguage.ORIGINAL, com.example.data.model.NarrationLanguage.fromDisplayName("Bahasa Asli"))
        assertEquals(com.example.data.model.NarrationLanguage.ORIGINAL, com.example.data.model.NarrationLanguage.fromDisplayName("original"))
        assertEquals(com.example.data.model.NarrationLanguage.ENGLISH_USA, com.example.data.model.NarrationLanguage.fromDisplayName("English (USA)"))
        assertEquals(com.example.data.model.NarrationLanguage.INDONESIAN, com.example.data.model.NarrationLanguage.fromDisplayName("Bahasa Indonesia"))
        assertEquals(com.example.data.model.NarrationLanguage.DEFAULT, com.example.data.model.NarrationLanguage.fromDisplayName("invalid"))
    }

    @Test
    fun testInterval10SecondsSupported() {
        val application = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.ui.viewmodel.MainViewModel(application)
        vm.setIntervalSeconds(10)
        assertEquals(10, vm.promptIntervalSeconds.value)

        // Verifikasi kalkulasi estimasi adegan dengan interval 10 detik
        val plan = com.example.util.SegmentCalculator.calculatePlanSummary(
            totalSeconds = 180L,
            intervalSeconds = 10
        )
        // 180 detik / 10 = 18 adegan
        assertEquals(18, plan.estimatedScenesCount)
    }

    @Test
    fun testApiKeyValidationEmpty() = kotlinx.coroutines.runBlocking {
        val client = com.example.data.api.GeminiApiClient(apiKeyProvider = { "" })
        val res = client.testApiKey("")
        org.junit.Assert.assertFalse(res.isSuccess)
        assertEquals("API Key kosong.", res.message)
    }

    @Test
    fun testShiftTimestamp() {
        // Single timestamp
        assertEquals("03:00", com.example.ui.components.ExportFormatters.shiftTimestamp("00:00", 180L))
        assertEquals("03:15", com.example.ui.components.ExportFormatters.shiftTimestamp("00:15", 180L))
        assertEquals("01:05:00", com.example.ui.components.ExportFormatters.shiftTimestamp("05:00", 3600L))

        // Range timestamp
        assertEquals(
            "03:00 - 03:05",
            com.example.ui.components.ExportFormatters.shiftTimestamp("00:00 - 00:05", 180L)
        )
        assertEquals(
            "06:10 - 06:20",
            com.example.ui.components.ExportFormatters.shiftTimestamp("03:10 - 03:20", 180L)
        )
    }

    @Test
    fun testFormatMergedExport_characterDeduplicationAndTimestampShift() {
        val char1 = listOf(
            CharacterInfo("Luffy", "Wears a red vest and straw hat"),
            CharacterInfo("Zoro", "Green hair, carries 3 swords")
        )
        val char2 = listOf(
            // Luffy repeated in lowercase with newer description
            CharacterInfo("luffy", "Wears an open red shirt with chest scar"),
            CharacterInfo("Nami", "Orange hair, navigator")
        )

        val timeline1 = listOf(
            TimelineItem("00:00 - 00:05", "Opening ship shot", "Visual prompt 1")
        )
        val timeline2 = listOf(
            TimelineItem("00:00 - 00:10", "Island appears", "Visual prompt 2")
        )

        val entity1 = AnimeAnalysisEntity(
            id = 101L,
            videoUriString = "uri1",
            videoName = "Episode 1",
            videoSizeBytes = 10_000_000L,
            durationMs = 120_000L, // 2 minutes = 120s
            mimeType = "video/mp4",
            intervalSeconds = 5,
            totalSegments = 1,
            completedSegmentsCount = 1,
            geminiFileRemoteName = "file1",
            geminiFileUri = "uri1",
            status = "COMPLETED",
            characterSheetJson = AnalysisJsonAdapter.characterSheetToJson(char1),
            timelineJson = AnalysisJsonAdapter.timelineToJson(timeline1),
            segmentSummariesJson = "[\"Summary Ep 1\"]",
            narration = "Narration 1 for Ep 1"
        )

        val entity2 = AnimeAnalysisEntity(
            id = 102L,
            videoUriString = "uri2",
            videoName = "Episode 2",
            videoSizeBytes = 10_000_000L,
            durationMs = 180_000L, // 3 minutes = 180s
            mimeType = "video/mp4",
            intervalSeconds = 5,
            totalSegments = 1,
            completedSegmentsCount = 1,
            geminiFileRemoteName = "file2",
            geminiFileUri = "uri2",
            status = "COMPLETED",
            characterSheetJson = AnalysisJsonAdapter.characterSheetToJson(char2),
            timelineJson = AnalysisJsonAdapter.timelineToJson(timeline2),
            segmentSummariesJson = "[\"Summary Ep 2\"]",
            narration = "Narration 2 for Ep 2"
        )

        val mergedText = com.example.ui.components.ExportFormatters.formatMergedExport(
            items = listOf(entity1, entity2),
            includeNarration = true,
            includeCharacters = true,
            includeTimeline = true,
            includeVisualPromptsOnly = false,
            includeSegmentSummaries = true
        )

        // 1. Overview
        assertTrue(mergedText.contains("Jumlah Video Gabungan: 2"))
        assertTrue(mergedText.contains("1. Episode 1 (02:00)"))
        assertTrue(mergedText.contains("2. Episode 2 (03:00)"))
        assertTrue(mergedText.contains("Total Durasi Gabungan : 05:00"))

        // 2. Narasi
        assertTrue(mergedText.contains("--- Bagian 1: Episode 1 ---"))
        assertTrue(mergedText.contains("Narration 1 for Ep 1"))
        assertTrue(mergedText.contains("--- Bagian 2: Episode 2 ---"))
        assertTrue(mergedText.contains("Narration 2 for Ep 2"))

        // 3. Characters: Luffy deduplicated, latest description used
        assertTrue(mergedText.contains("Wears an open red shirt with chest scar"))
        // Old description replaced
        org.junit.Assert.assertFalse(mergedText.contains("Wears a red vest and straw hat"))
        assertTrue(mergedText.contains("Zoro"))
        assertTrue(mergedText.contains("Nami"))

        // 4. Timeline continuous: Episode 2 timeline starts shifted by 120s (02:00)
        // timeline1 was 00:00 - 00:05
        assertTrue(mergedText.contains("[00:00 - 00:05] Adegan #1: Opening ship shot"))
        // timeline2 was 00:00 - 00:10, shifted by 120s -> 02:00 - 02:10
        assertTrue(mergedText.contains("[02:00 - 02:10] Adegan #2: Island appears"))
    }

    @Test
    fun testPromptCutModeModelAndStorage() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val storage = com.example.data.prefs.PromptCutModeStorage(app)

        // Default mode must be FIXED_INTERVAL
        assertEquals(com.example.data.model.PromptCutMode.DEFAULT, com.example.data.model.PromptCutMode.FIXED_INTERVAL)
        storage.setMode(com.example.data.model.PromptCutMode.FIXED_INTERVAL)
        assertEquals(com.example.data.model.PromptCutMode.FIXED_INTERVAL, storage.getMode())

        // Switch to PER_SCENE
        storage.setMode(com.example.data.model.PromptCutMode.PER_SCENE)
        assertEquals(com.example.data.model.PromptCutMode.PER_SCENE, storage.getMode())

        // Max scene duration default is 5s
        storage.setMaxSceneDuration(3)
        assertEquals(3, storage.getMaxSceneDuration())
        storage.setMaxSceneDuration(5)
        assertEquals(5, storage.getMaxSceneDuration())

        // Per analysis mode locking
        storage.saveModeForAnalysis(555L, com.example.data.model.PromptCutMode.PER_SCENE)
        assertEquals(com.example.data.model.PromptCutMode.PER_SCENE, storage.getModeForAnalysis(555L))
        storage.setMode(com.example.data.model.PromptCutMode.FIXED_INTERVAL)
        // Locked mode for analysis 555 should remain PER_SCENE
        assertEquals(com.example.data.model.PromptCutMode.PER_SCENE, storage.getModeForAnalysis(555L))
    }

    @Test
    fun testSceneDetectorGetScenesForSegment() {
        val allScenes = listOf(
            com.example.util.SceneTimeSpan(0, 3),
            com.example.util.SceneTimeSpan(3, 7),
            com.example.util.SceneTimeSpan(7, 10),
            com.example.util.SceneTimeSpan(10, 15),
            com.example.util.SceneTimeSpan(180, 185)
        )

        // Segment 0s to 10s (00:00 to 00:10)
        val seg1 = com.example.util.SceneDetector.getScenesForSegment(allScenes, 0L, 10L)
        assertEquals(3, seg1.size)
        assertEquals("00:00 - 00:03", seg1[0])
        assertEquals("00:03 - 00:07", seg1[1])
        assertEquals("00:07 - 00:10", seg1[2])

        // Empty segment should fall back to single range or full segment
        val emptySeg = com.example.util.SceneDetector.getScenesForSegment(allScenes, 60L, 75L)
        assertEquals(1, emptySeg.size)
        assertEquals("01:00 - 01:15", emptySeg[0])
    }

    @Test
    fun testPromptCutModeExactSceneSpansInPrompt() {
        val client = com.example.data.api.GeminiApiClient(apiKeyProvider = { "dummy-key" })

        val method = com.example.data.api.GeminiApiClient::class.java.getDeclaredMethod(
            "buildSegmentPrompt",
            String::class.java,
            String::class.java,
            Long::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            List::class.java,
            Boolean::class.javaPrimitiveType,
            String::class.java,
            com.example.data.model.NarrationMode::class.java,
            com.example.data.model.NarrationLanguage::class.java,
            List::class.java,
            com.example.data.model.SeriesBible::class.java,
            String::class.java
        )
        method.isAccessible = true

        val exactScenes = listOf("00:00 - 00:04", "00:04 - 00:07", "00:07 - 00:10")
        val prompt = method.invoke(
            client, "00:00", "00:10", 0L, 10L, 0,
            emptyList<com.example.data.model.CharacterInfo>(), true, "",
            com.example.data.model.NarrationMode.REKAP_VOICEOVER,
            com.example.data.model.NarrationLanguage.ENGLISH_USA,
            exactScenes,
            null,
            ""
        ) as String

        assertTrue(prompt.contains("TIMESTAMPS (PER ADEGAN - EXACT REQUIRED LIST):"))
        assertTrue(prompt.contains("\"00:00 - 00:04\""))
        assertTrue(prompt.contains("\"00:04 - 00:07\""))
        assertTrue(prompt.contains("\"00:07 - 00:10\""))
        assertTrue(prompt.contains("Total timeline items required: 3"))
    }

    @Test
    fun testExportFormattersPerSceneModeNeverShowsZeroDetik() {
        val result = com.example.data.model.AnimeAnalysisResult(
            durationFormatted = "05:00",
            totalSegments = 2,
            intervalSeconds = 0, // 0 = Per Adegan
            videoName = "Test Anime",
            characterSheet = emptyList(),
            timeline = listOf(
                com.example.data.model.TimelineItem(
                    timestamp = "00:00 - 00:04",
                    sceneDescription = "Character stands on rooftop",
                    visualPrompt = "2D anime style, wind blowing"
                )
            ),
            narration = "Recap text",
            segmentSummaries = emptyList()
        )

        val fullExport = com.example.ui.components.ExportFormatters.formatFullExport(result)
        assertTrue("Export harus menuliskan 'Per Adegan'", fullExport.contains("Interval Prompt    : Per Adegan"))
        assertTrue("Timeline header harus menuliskan 'PER ADEGAN'", fullExport.contains("C. TIMELINE VISUAL PROMPT (PER ADEGAN)"))
        org.junit.Assert.assertFalse("Tidak boleh ada teks '0 detik'", fullExport.contains("0 detik"))
        org.junit.Assert.assertFalse("Tidak boleh ada teks 'INTERVAL 0s'", fullExport.contains("INTERVAL 0s"))

        val timelineExport = com.example.ui.components.ExportFormatters.formatTimeline(result.timeline, result.intervalSeconds)
        assertTrue(timelineExport.contains("=== TIMELINE VISUAL PROMPT (Per Adegan) ==="))
        org.junit.Assert.assertFalse(timelineExport.contains("0 Detik"))
    }

    @Test
    fun testBuildFinalSpansMaxDuration() {
        val startSec = 0L
        val endSec = 20L
        val rawCutPoints = emptyList<Long>() // satu adegan 20 detik tanpa titik potong di tengah

        // 1. maxDuration = 0 (Tanpa batas -> adegan 20 detik tetap satu rentang)
        val spansUnlimited = com.example.util.SceneDetector.buildFinalSpans(rawCutPoints, startSec, endSec, maxDuration = 0)
        assertEquals(1, spansUnlimited.size)
        assertEquals(0L, spansUnlimited[0].startSec)
        assertEquals(20L, spansUnlimited[0].endSec)

        // 2. maxDuration = 10 (adegan 20 detik menjadi dua rentang masing-masing 10 detik)
        val spans10 = com.example.util.SceneDetector.buildFinalSpans(rawCutPoints, startSec, endSec, maxDuration = 10)
        assertEquals(2, spans10.size)
        assertEquals(com.example.util.SceneTimeSpan(0L, 10L), spans10[0])
        assertEquals(com.example.util.SceneTimeSpan(10L, 20L), spans10[1])

        // 3. maxDuration = 5 (hasil lama tidak berubah -> adegan 20 detik dipecah per 5 detik menjadi 4 rentang)
        val spans5 = com.example.util.SceneDetector.buildFinalSpans(rawCutPoints, startSec, endSec, maxDuration = 5)
        assertEquals(4, spans5.size)
        assertEquals(com.example.util.SceneTimeSpan(0L, 5L), spans5[0])
        assertEquals(com.example.util.SceneTimeSpan(5L, 10L), spans5[1])
        assertEquals(com.example.util.SceneTimeSpan(10L, 15L), spans5[2])
        assertEquals(com.example.util.SceneTimeSpan(15L, 20L), spans5[3])

        // Fallback test saat deteksi gagal: maxDuration = 0 fallback ke 10 detik
        val fallbackSpans = com.example.util.SceneDetector.splitSpansFallback(startSec, endSec, maxDuration = 0)
        assertEquals(2, fallbackSpans.size)
        assertEquals(com.example.util.SceneTimeSpan(0L, 10L), fallbackSpans[0])
        assertEquals(com.example.util.SceneTimeSpan(10L, 20L), fallbackSpans[1])
    }

    @Test
    fun testPromptCutModeStorageMaxSceneDurationZeroAndTen() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val storage = com.example.data.prefs.PromptCutModeStorage(context)

        // Simpan 0 (Tanpa batas)
        storage.setMaxSceneDuration(0)
        assertEquals(0, storage.getMaxSceneDuration())

        // Simpan per analisis (0 dan 10)
        storage.saveModeForAnalysis(888L, com.example.data.model.PromptCutMode.PER_SCENE, maxDurationSec = 0)
        assertEquals(0, storage.getMaxDurationForAnalysis(888L))

        storage.saveModeForAnalysis(889L, com.example.data.model.PromptCutMode.PER_SCENE, maxDurationSec = 10)
        assertEquals(10, storage.getMaxDurationForAnalysis(889L))
    }

    @Test
    fun testExportFormattersTanpaBatasLabel() {
        val resultUnlimited = com.example.data.model.AnimeAnalysisResult(
            narration = "Recap text",
            characterSheet = emptyList(),
            timeline = listOf(
                com.example.data.model.TimelineItem("00:00 - 00:20", "Long scene", "Visual prompt")
            ),
            totalSegments = 1,
            durationFormatted = "00:20",
            intervalSeconds = 0,
            maxSceneDuration = 0
        )
        val fullExport = com.example.ui.components.ExportFormatters.formatFullExport(resultUnlimited)
        assertTrue(fullExport.contains("Batas Durasi Adegan: Tanpa batas"))
        org.junit.Assert.assertFalse(fullExport.contains("0 detik"))

        val timelineExport = com.example.ui.components.ExportFormatters.formatTimeline(
            resultUnlimited.timeline,
            resultUnlimited.intervalSeconds,
            resultUnlimited.maxSceneDuration
        )
        assertTrue(timelineExport.contains("Batas Durasi Adegan: Tanpa batas"))
        org.junit.Assert.assertFalse(timelineExport.contains("0 Detik"))
    }

    @Test
    fun testMaxSceneDurationChangeWithoutRedetection() {
        val startSec = 0L
        val endSec = 30L
        // Titik potong mentah hasil deteksi: 12L (satu titik potong di detik 12)
        val cutPoints = listOf(12L)

        // 1. maxDuration = 5: adegan 0..12 dipecah (0-5, 5-10, 10-12), 12..30 dipecah (12-17, 17-22, 22-27, 27-30) -> 7 spans
        val spans5 = com.example.util.SceneDetector.buildFinalSpans(cutPoints, startSec, endSec, maxDuration = 5)
        assertEquals(7, spans5.size)

        // 2. maxDuration = 10: adegan 0..12 dipecah (0-10, 10-12), 12..30 dipecah (12-22, 22-30) -> 4 spans
        val spans10 = com.example.util.SceneDetector.buildFinalSpans(cutPoints, startSec, endSec, maxDuration = 10)
        assertEquals(4, spans10.size)

        // 3. maxDuration = 0 (Tanpa batas): adegan 0..12 dan 12..30 tidak dipecah -> 2 spans
        val spans0 = com.example.util.SceneDetector.buildFinalSpans(cutPoints, startSec, endSec, maxDuration = 0)
        assertEquals(2, spans0.size)
        assertEquals(com.example.util.SceneTimeSpan(0L, 12L), spans0[0])
        assertEquals(com.example.util.SceneTimeSpan(12L, 30L), spans0[1])

        // Memverifikasi bahwa ketiga konfigurasi menghasilkan spans yang berbeda dari cutPoints yang sama tanpa perlu pemindaian ulang
        org.junit.Assert.assertNotEquals(spans5, spans10)
        org.junit.Assert.assertNotEquals(spans10, spans0)
        org.junit.Assert.assertNotEquals(spans5, spans0)
    }

    @Test
    fun testSeriesBibleJsonSerializationAndParsing() {
        val bible = com.example.data.model.SeriesBible(
            locations = listOf(
                com.example.data.model.BibleLocation("Hidden Leaf Village", "Ninja village in Fire Country"),
                com.example.data.model.BibleLocation("Valley of the End", "Waterfall gorge border")
            ),
            glossary = listOf(
                com.example.data.model.BibleGlossaryItem("Chakra", "Spiritual and physical energy"),
                com.example.data.model.BibleGlossaryItem("Rasengan", "Spiraling sphere of pure chakra")
            ),
            relationships = listOf(
                com.example.data.model.BibleRelationship("Naruto", "Sasuke", "Rivals and best friends")
            ),
            plotSoFar = "Naruto and Sasuke clash at the Valley of the End after Sasuke decides to leave the village.",
            episodes = listOf(
                com.example.data.model.BibleEpisode("Episode 1", "The departure from the village."),
                com.example.data.model.BibleEpisode("Episode 2", "The battle commences at the waterfall.")
            )
        )

        // 1. Serialization
        val json = com.example.data.prefs.SeriesBibleStorage.toJson(bible)
        assertTrue(json.contains("Hidden Leaf Village"))
        assertTrue(json.contains("Rasengan"))
        assertTrue(json.contains("Valley of the End"))

        // 2. Normal parsing
        val parsed = com.example.data.prefs.SeriesBibleStorage.fromJson(json)
        org.junit.Assert.assertNotNull(parsed)
        assertEquals(2, parsed?.locations?.size)
        assertEquals("Hidden Leaf Village", parsed?.locations?.get(0)?.name)
        assertEquals(2, parsed?.glossary?.size)
        assertEquals("Chakra", parsed?.glossary?.get(0)?.term)
        assertEquals(1, parsed?.relationships?.size)
        assertEquals(2, parsed?.episodes?.size)

        // 3. Tolerant parsing with markdown code fences
        val fencedJson = "```json\n$json\n```"
        val parsedFenced = com.example.data.prefs.SeriesBibleStorage.fromJson(fencedJson)
        org.junit.Assert.assertNotNull(parsedFenced)
        assertEquals("Rasengan", parsedFenced?.glossary?.get(1)?.term)

        // 4. Malformed JSON returns null without crashing
        val invalidJson = "This is not valid json { [ ]"
        val parsedInvalid = com.example.data.prefs.SeriesBibleStorage.fromJson(invalidJson)
        org.junit.Assert.assertNull(parsedInvalid)

        val emptyString = ""
        org.junit.Assert.assertNull(com.example.data.prefs.SeriesBibleStorage.fromJson(emptyString))
    }

    @Test
    fun testSeriesBibleLimitsEnforced() {
        // Buat daftar melebihi batas: 25 lokasi (>20), 35 istilah (>30), 25 relasi (>20), 15 episode (>10)
        val excessiveLocations = (1..25).map { com.example.data.model.BibleLocation("Loc $it", "Desc $it") }
        val excessiveGlossary = (1..35).map { com.example.data.model.BibleGlossaryItem("Term $it", "Def $it") }
        val excessiveRelations = (1..25).map { com.example.data.model.BibleRelationship("A $it", "B $it", "Rel $it") }
        val longPlot = "A".repeat(1500) // > 1200
        val excessiveEpisodes = (1..15).map { com.example.data.model.BibleEpisode("Ep $it", "Recap $it " + "X".repeat(400)) }

        val bigBible = com.example.data.model.SeriesBible(
            locations = excessiveLocations,
            glossary = excessiveGlossary,
            relationships = excessiveRelations,
            plotSoFar = longPlot,
            episodes = excessiveEpisodes
        )

        val trimmed = bigBible.trimToLimits()
        assertEquals(com.example.data.model.SeriesBible.MAX_LOCATIONS, trimmed.locations.size)
        assertEquals(com.example.data.model.SeriesBible.MAX_GLOSSARY, trimmed.glossary.size)
        assertEquals(com.example.data.model.SeriesBible.MAX_RELATIONSHIPS, trimmed.relationships.size)
        assertTrue(trimmed.plotSoFar.length <= com.example.data.model.SeriesBible.MAX_PLOT_CHARS)
        assertEquals(com.example.data.model.SeriesBible.MAX_EPISODES, trimmed.episodes.size)
        // Memastikan 10 episode terakhir dipertahankan
        assertEquals("Ep 6", trimmed.episodes.first().videoName)
        assertEquals("Ep 15", trimmed.episodes.last().videoName)
        assertTrue(trimmed.episodes.first().recap.length <= com.example.data.model.SeriesBible.MAX_EPISODE_RECAP_CHARS)
    }

    @Test
    fun testSeriesBibleEpisodeUpsertDoesNotDuplicateVideoName() {
        val bible = com.example.data.model.SeriesBible(
            episodes = listOf(
                com.example.data.model.BibleEpisode("Episode 01.mp4", "Old recap"),
                com.example.data.model.BibleEpisode("Episode 02.mp4", "Other recap")
            )
        )

        val updated = bible.withUpdatedEpisode("episode 01.MP4", "Updated recap")

        assertEquals(2, updated.episodes.size)
        assertEquals("Updated recap", updated.episodes.first().recap)
        assertEquals("Episode 02.mp4", updated.episodes.last().videoName)
    }

    @Test
    fun testOpeningEndingFingerprintRequiresOrderedFrameRun() {
        val random = java.util.Random(42L)
        val template = (0 until 10).map { index ->
            com.example.util.FrameFingerprint(index * 3L, random.nextLong())
        }
        val current = listOf(com.example.util.FrameFingerprint(0L, -1L)) +
            template.take(8).mapIndexed { index, frame -> frame.copy(timeSec = (index + 1) * 3L) }

        val match = com.example.util.OpeningEndingDetector.longestOrderedRun(template, current)

        assertEquals(1 to 8, match)
    }

    @Test
    fun testOpeningEndingFingerprintDoesNotBridgeUnmatchedCurrentFrame() {
        val random = java.util.Random(912L)
        val template = (0 until 10).map { index ->
            com.example.util.FrameFingerprint(index * 3L, random.nextLong())
        }
        val unrelatedHash = Long.MIN_VALUE
        assertTrue(template.none { java.lang.Long.bitCount(it.hash xor unrelatedHash) <= 9 })
        val current = template.take(4) +
            com.example.util.FrameFingerprint(12L, unrelatedHash) +
            template.drop(4).mapIndexed { index, frame -> frame.copy(timeSec = (15 + index * 3).toLong()) }

        val match = com.example.util.OpeningEndingDetector.longestOrderedRun(template, current)

        assertEquals(5 to 10, match)
    }

    @Test
    fun testCharacterContinuityCheckerFlagsConflictingHairColor() {
        val previous = listOf(
            com.example.data.model.CharacterInfo("Aiko", "Aiko has short blue hair, bright green eyes, and a red coat.")
        )
        val current = listOf(
            com.example.data.model.CharacterInfo("aiko", "Aiko has long red hair, bright green eyes, and a red coat.")
        )

        val warnings = com.example.util.CharacterContinuityChecker.findContradictions(previous, current)

        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("warna rambut"))
    }

    @Test
    fun testRepeatedSceneDetectorRequiresSeparatedOrderedRun() {
        val random = java.util.Random(17L)
        val repeated = (0 until 4).map { random.nextLong() }
        val frames = buildList {
            repeated.forEachIndexed { index, hash -> add(com.example.util.FrameFingerprint(index * 5L, hash)) }
            repeat(4) { index -> add(com.example.util.FrameFingerprint((index + 4) * 5L, random.nextLong())) }
            repeated.forEachIndexed { index, hash -> add(com.example.util.FrameFingerprint((index + 8) * 5L, hash)) }
        }

        val matches = com.example.util.RepeatedSceneDetector.findRepeatedRuns(frames, 5L)

        assertEquals(1, matches.size)
        assertEquals(0L, matches.single().firstStartSec)
        assertEquals(40L, matches.single().secondStartSec)
    }

    @Test
    fun testBuildSegmentPromptWithAndWithoutSeriesBible() {
        val client = com.example.data.api.GeminiApiClient(apiKeyProvider = { "dummy-key" })

        val method = com.example.data.api.GeminiApiClient::class.java.getDeclaredMethod(
            "buildSegmentPrompt",
            String::class.java,
            String::class.java,
            Long::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            List::class.java,
            Boolean::class.javaPrimitiveType,
            String::class.java,
            com.example.data.model.NarrationMode::class.java,
            com.example.data.model.NarrationLanguage::class.java,
            List::class.java,
            com.example.data.model.SeriesBible::class.java,
            String::class.java
        )
        method.isAccessible = true

        val bible = com.example.data.model.SeriesBible(
            locations = listOf(com.example.data.model.BibleLocation("Hueco Mundo", "Desert realm of Hollows")),
            glossary = listOf(com.example.data.model.BibleGlossaryItem("Zanpakuto", "Soul-cutter sword")),
            relationships = listOf(com.example.data.model.BibleRelationship("Ichigo", "Rukia", "Trusted allies")),
            plotSoFar = "Ichigo broke into Hueco Mundo to rescue Orihime."
        )

        // 1. Prompt dengan SeriesBible
        val promptWithBible = method.invoke(
            client, "00:00", "03:00", 0L, 180L, 5,
            emptyList<com.example.data.model.CharacterInfo>(), true, "",
            com.example.data.model.NarrationMode.REKAP_VOICEOVER,
            com.example.data.model.NarrationLanguage.ENGLISH_USA,
            emptyList<String>(),
            bible,
            ""
        ) as String

        assertTrue(promptWithBible.contains("SERIES BIBLE FROM PREVIOUS EPISODES:"))
        assertTrue(promptWithBible.contains("Hueco Mundo"))
        assertTrue(promptWithBible.contains("Zanpakuto"))
        assertTrue(promptWithBible.contains("Ichigo & Rukia"))
        assertTrue(promptWithBible.contains("Use this only to keep names, terms, spellings, and locations CONSISTENT."))
        assertTrue(promptWithBible.contains("Do NOT describe anything from it unless it is actually visible or audible in this segment."))

        // 2. Prompt tanpa SeriesBible (null)
        val promptWithoutBible = method.invoke(
            client, "00:00", "03:00", 0L, 180L, 5,
            emptyList<com.example.data.model.CharacterInfo>(), true, "",
            com.example.data.model.NarrationMode.REKAP_VOICEOVER,
            com.example.data.model.NarrationLanguage.ENGLISH_USA,
            emptyList<String>(),
            null,
            ""
        ) as String

        org.junit.Assert.assertFalse(promptWithoutBible.contains("SERIES BIBLE FROM PREVIOUS EPISODES:"))
        org.junit.Assert.assertFalse(promptWithoutBible.contains("Hueco Mundo"))

        // 3. Prompt dengan SeriesBible kosong (empty)
        val emptyBible = com.example.data.model.SeriesBible()
        val promptEmptyBible = method.invoke(
            client, "00:00", "03:00", 0L, 180L, 5,
            emptyList<com.example.data.model.CharacterInfo>(), true, "",
            com.example.data.model.NarrationMode.REKAP_VOICEOVER,
            com.example.data.model.NarrationLanguage.ENGLISH_USA,
            emptyList<String>(),
            emptyBible,
            ""
        ) as String

        org.junit.Assert.assertFalse(promptEmptyBible.contains("SERIES BIBLE FROM PREVIOUS EPISODES:"))
    }

    @Test
    fun testExportFormattersSeriesBible() {
        val bible = com.example.data.model.SeriesBible(
            locations = listOf(com.example.data.model.BibleLocation("Wall Maria", "Outermost human settlement wall")),
            glossary = listOf(com.example.data.model.BibleGlossaryItem("ODM Gear", "Omni-directional mobility gear")),
            relationships = listOf(com.example.data.model.BibleRelationship("Eren", "Mikasa", "Adoptive siblings and protectors")),
            plotSoFar = "Wall Maria was breached by the Colossal Titan.",
            episodes = listOf(com.example.data.model.BibleEpisode("Episode 1", "The fall of Shiganshina."))
        )

        val result = com.example.data.model.AnimeAnalysisResult(
            narration = "Narrative test",
            characterSheet = emptyList(),
            timeline = emptyList(),
            totalSegments = 1,
            durationFormatted = "03:00",
            intervalSeconds = 5,
            videoName = "Attack on Titan Ep 1",
            seriesBible = bible
        )

        val exportWithBible = com.example.ui.components.ExportFormatters.formatFullExport(result)
        assertTrue(exportWithBible.contains("E. MEMORI SERI (SERIES BIBLE)"))
        assertTrue(exportWithBible.contains("Wall Maria"))
        assertTrue(exportWithBible.contains("ODM Gear"))
        assertTrue(exportWithBible.contains("Eren & Mikasa"))
        assertTrue(exportWithBible.contains("Colossal Titan"))

        val resultNoBible = result.copy(seriesBible = null)
        val exportNoBible = com.example.ui.components.ExportFormatters.formatFullExport(resultNoBible)
        org.junit.Assert.assertFalse(exportNoBible.contains("E. MEMORI SERI (SERIES BIBLE)"))
    }

    @Test
    fun testOpeningEndingUniformFramesDoNotProduceSkip() {
        // Deret frame hitam (hash = 0L, bitCount = 0 < 8) dan putih (hash = -1L, bitCount = 64 > 56)
        val blackFrames = (0 until 12).map { index ->
            com.example.util.FrameFingerprint(index * 3L, 0L)
        }
        val templateBlack = com.example.util.OpeningEndingFingerprint(blackFrames, emptyList())
        val currentBlack = com.example.util.OpeningEndingFingerprint(blackFrames, emptyList())

        val skipsBlack = com.example.util.OpeningEndingDetector.findSkips(templateBlack, currentBlack, 1800L)
        assertNull("Dua deret frame hitam seragam tidak boleh menghasilkan skip opening", skipsBlack.openingEndSec)
        assertNull("Dua deret frame hitam seragam tidak boleh menghasilkan skip ending", skipsBlack.endingStartSec)

        // Deret frame bervariasi (informative, 8 <= bitCount <= 56) yang sama tetap terdeteksi
        val random = java.util.Random(12345L)
        val variedFrames = (0 until 10).map { index ->
            var h: Long
            do {
                h = random.nextLong()
            } while (java.lang.Long.bitCount(h) !in 8..56)
            com.example.util.FrameFingerprint(index * 3L, h)
        }
        val templateVaried = com.example.util.OpeningEndingFingerprint(variedFrames, emptyList())
        val currentVaried = com.example.util.OpeningEndingFingerprint(variedFrames, emptyList())

        val skipsVaried = com.example.util.OpeningEndingDetector.findSkips(templateVaried, currentVaried, 1800L)
        assertNotNull("Deret frame bervariasi yang sama harus terdeteksi sebagai skip", skipsVaried.openingEndSec)
        assertEquals(30L, skipsVaried.openingEndSec)
    }

    @Test
    fun testRepeatedSceneDetectorBlackFramesIgnored() {
        // Dua deret frame hitam (hash = 0L) berjarak > 30 detik
        val frames = buildList {
            repeat(6) { index -> add(com.example.util.FrameFingerprint(index * 5L, 0L)) }
            repeat(10) { index -> add(com.example.util.FrameFingerprint((index + 6) * 5L, 0L)) }
        }
        val matches = com.example.util.RepeatedSceneDetector.findRepeatedRuns(frames, 5L)
        assertTrue("Deret frame hitam tidak boleh menghasilkan kandidat adegan berulang", matches.isEmpty())
    }

    @Test
    fun testApiKeyStorageDecryptFailureWithFakeCipher() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = context.getSharedPreferences("rekapin_secure_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val failingCipher = object : com.example.data.prefs.ApiKeyCipher {
            override fun encrypt(plainText: String): String = "v1:fake_encrypted"
            override fun decrypt(encryptedText: String): String? = null
        }

        // Skenario A: Ada data terenkripsi dan ada data plaintext lama sebagai cadangan baca sesi ini
        prefs.edit()
            .putString("custom_gemini_api_keys_encrypted", "v1:unreadable_payload")
            .putString("custom_gemini_api_key", "AIzaSyLegacyFallbackKey")
            .commit()

        val storageWithFallback = com.example.data.prefs.ApiKeyStorage(context, failingCipher)
        val keysWithFallback = storageWithFallback.getAllKeys()

        assertTrue("Status keyDecryptFailed harus true saat dekripsi gagal", storageWithFallback.keyDecryptFailed)
        assertEquals(1, keysWithFallback.size)
        assertEquals("AIzaSyLegacyFallbackKey", keysWithFallback.first().key)
        assertEquals("AIzaSyLegacyFallbackKey", storageWithFallback.getEffectiveApiKey())
        // Plaintext tidak boleh dihapus
        assertEquals("AIzaSyLegacyFallbackKey", prefs.getString("custom_gemini_api_key", null))

        // Skenario B: Tidak ada data plaintext cadangan (hanya data terenkripsi yang gagal dibuka)
        prefs.edit().remove("custom_gemini_api_key").remove("custom_gemini_api_keys_json").commit()

        val storageNoFallback = com.example.data.prefs.ApiKeyStorage(context, failingCipher)
        val keysNoFallback = storageNoFallback.getAllKeys()

        assertTrue(storageNoFallback.keyDecryptFailed)
        assertTrue(keysNoFallback.isEmpty())
        assertTrue("hasStoredKeysThatCannotBeDecrypted harus true", storageNoFallback.hasStoredKeysThatCannotBeDecrypted())
    }

    @Test
    fun testSettingsSwitchesForOpEdAndRepeatedScenes() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val opEdStorage = com.example.data.prefs.OpeningEndingStorage(context)
        assertTrue(opEdStorage.isAutoSkipOpEd())
        opEdStorage.setAutoSkipOpEd(false)
        org.junit.Assert.assertFalse(opEdStorage.isAutoSkipOpEd())
        opEdStorage.setAutoSkipOpEd(true)
        assertTrue(opEdStorage.isAutoSkipOpEd())

        val repeatedStorage = com.example.data.prefs.RepeatedSceneStorage(context)
        assertTrue(repeatedStorage.isDetectRepeatedScenes())
        repeatedStorage.setDetectRepeatedScenes(false)
        org.junit.Assert.assertFalse(repeatedStorage.isDetectRepeatedScenes())
        repeatedStorage.setDetectRepeatedScenes(true)
        assertTrue(repeatedStorage.isDetectRepeatedScenes())

        // Skip note persistence
        opEdStorage.saveSkipNote(999L, "Rentang yang dilewati: OP dilewati sampai 01:30")
        assertEquals("Rentang yang dilewati: OP dilewati sampai 01:30", opEdStorage.getSkipNote(999L))
        opEdStorage.remove(999L)
        assertNull(opEdStorage.getSkipNote(999L))
    }

    @Test
    fun testKeyTestElapsedTimeFormatting() {
        assertEquals("0,0 dtk", com.example.data.api.GeminiApiClient.formatKeyTestElapsed(0L))
        assertEquals("0,0 dtk", com.example.data.api.GeminiApiClient.formatKeyTestElapsed(-250L))
        assertEquals("0,5 dtk", com.example.data.api.GeminiApiClient.formatKeyTestElapsed(500L))
        assertEquals("1,8 dtk", com.example.data.api.GeminiApiClient.formatKeyTestElapsed(1800L))
        assertEquals("12,3 dtk", com.example.data.api.GeminiApiClient.formatKeyTestElapsed(12340L))
        assertEquals("25,0 dtk", com.example.data.api.GeminiApiClient.formatKeyTestElapsed(25000L))
    }

    @Test
    fun testKeyTestResultDefaultElapsedMsAndClientTimeouts() {
        val defaultResult = com.example.data.api.GeminiApiClient.KeyTestResult(
            isSuccess = true,
            httpCode = 200,
            message = "ok"
        )
        assertNull(defaultResult.elapsedMs)

        val client = com.example.data.api.GeminiApiClient(apiKeyProvider = { "" })
        val mainClientField = com.example.data.api.GeminiApiClient::class.java.getDeclaredField("client")
        mainClientField.isAccessible = true
        val mainHttpClient = mainClientField.get(client) as okhttp3.OkHttpClient

        val testClientField = com.example.data.api.GeminiApiClient::class.java.getDeclaredField("testClient")
        testClientField.isAccessible = true
        val testHttpClient = testClientField.get(client) as okhttp3.OkHttpClient

        assertEquals(60_000, mainHttpClient.connectTimeoutMillis)
        assertEquals(300_000, mainHttpClient.writeTimeoutMillis)
        assertEquals(300_000, mainHttpClient.readTimeoutMillis)

        assertEquals(10_000, testHttpClient.connectTimeoutMillis)
        assertEquals(10_000, testHttpClient.writeTimeoutMillis)
        assertEquals(20_000, testHttpClient.readTimeoutMillis)
        assertEquals(25_000, testHttpClient.callTimeoutMillis)

        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1",
            com.example.data.api.GeminiApiClient.buildModelsValidationEndpoint()
        )
    }

    @Test
    fun testKeyTestTimeoutAndConnectionFailureMessage() = kotlinx.coroutines.runBlocking {
        assertEquals(
            "Tidak ada respons dalam 25 detik (jaringan lambat atau server sibuk).",
            com.example.data.api.GeminiApiClient.KEY_TEST_TIMEOUT_MESSAGE
        )
        assertEquals(
            "Gagal terhubung. Periksa koneksi internet.",
            com.example.data.api.GeminiApiClient.KEY_TEST_CONNECTION_ERROR_MESSAGE
        )

        val client = com.example.data.api.GeminiApiClient(apiKeyProvider = { "AIzaTestKey" })

        // 1. Gagal terhubung ke port tertutup -> "Gagal terhubung. Periksa koneksi internet. (... dtk)"
        val closedPort = java.net.ServerSocket(0).use { it.localPort }
        val connectFailResult = client.testApiKeyWithBase(
            apiKey = "AIzaTestKey",
            generateContentBase = "http://127.0.0.1:$closedPort/v1beta/models"
        )
        org.junit.Assert.assertFalse(connectFailResult.isSuccess)
        assertNull(connectFailResult.httpCode)
        assertTrue(
            "Pesan gagal terhubung harus diawali KEY_TEST_CONNECTION_ERROR_MESSAGE, aktual: ${connectFailResult.message}",
            connectFailResult.message.startsWith("Gagal terhubung. Periksa koneksi internet. (")
        )
        assertTrue(connectFailResult.message.endsWith(" dtk)"))
        assertNotNull(connectFailResult.elapsedMs)

        // 2. Simulasi timeout pada tahap pertama GET /models?pageSize=1 -> "Tidak ada respons dalam 25 detik..."
        java.net.ServerSocket(0).use { server ->
            val acceptThread = Thread {
                try {
                    server.accept().use { socket ->
                        Thread.sleep(500)
                        socket.getOutputStream()
                    }
                } catch (_: Exception) {
                }
            }
            acceptThread.isDaemon = true
            acceptThread.start()

            val shortTimeoutClient = okhttp3.OkHttpClient.Builder()
                .connectTimeout(1, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(150, java.util.concurrent.TimeUnit.MILLISECONDS)
                .callTimeout(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()

            val timeoutResult = client.testApiKeyWithBase(
                apiKey = "AIzaTestKey",
                generateContentBase = "http://127.0.0.1:${server.localPort}/v1beta/models",
                httpClientOverride = shortTimeoutClient,
                totalTimeoutMs = 300L
            )
            org.junit.Assert.assertFalse(timeoutResult.isSuccess)
            assertNull(timeoutResult.httpCode)
            assertTrue(
                "Pesan timeout harus diawali KEY_TEST_TIMEOUT_MESSAGE, aktual: ${timeoutResult.message}",
                timeoutResult.message.startsWith("Tidak ada respons dalam 25 detik (jaringan lambat atau server sibuk). (")
            )
            assertTrue(timeoutResult.message.endsWith(" dtk)"))
            assertNotNull(timeoutResult.elapsedMs)
        }
    }

    @Test
    fun testKeyTestTwoStageFlowAndErrorMessages() = kotlinx.coroutines.runBlocking {
        val client = com.example.data.api.GeminiApiClient(apiKeyProvider = { "AIzaTestKey" })

        // Kasus A: Tahap 1 mengembalikan 401 -> berhenti cepat dengan "Key tidak valid (... dtk)"
        java.net.ServerSocket(0).use { server ->
            val requestPaths = java.util.Collections.synchronizedList(mutableListOf<String>())
            val serverThread = Thread {
                try {
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        val requestLine = reader.readLine().orEmpty()
                        requestPaths.add(requestLine)
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val body = """{"error":{"code":401,"message":"API key not valid"}}"""
                        val response = "HTTP/1.1 401 Unauthorized\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${body.toByteArray().size}\r\n" +
                            "Connection: close\r\n\r\n" +
                            body
                        socket.getOutputStream().write(response.toByteArray())
                        socket.getOutputStream().flush()
                    }
                } catch (_: Exception) {
                }
            }
            serverThread.isDaemon = true
            serverThread.start()

            val progressUpdates = mutableListOf<String>()
            val res = client.testApiKeyWithBase(
                apiKey = "AIzaInvalid",
                generateContentBase = "http://127.0.0.1:${server.localPort}/v1beta/models",
                onProgress = { progressUpdates.add(it) }
            )
            org.junit.Assert.assertFalse(res.isSuccess)
            assertEquals(401, res.httpCode)
            assertTrue(res.message.startsWith("Key tidak valid ("))
            assertTrue(res.message.endsWith(" dtk)"))
            assertNotNull(res.elapsedMs)
            assertTrue("Tahap 2 tidak boleh dijalankan jika tahap 1 gagal 401", progressUpdates.isEmpty())
            assertEquals(1, requestPaths.size)
            assertTrue(requestPaths.first().startsWith("GET /v1beta/models?pageSize=1"))
        }

        // Kasus B: Tahap 1 sukses 200 -> emit progress -> Tahap 2 sukses 200
        java.net.ServerSocket(0).use { server ->
            val serverThread = Thread {
                try {
                    repeat(2) { step ->
                        server.accept().use { socket ->
                            val reader = socket.getInputStream().bufferedReader()
                            val requestLine = reader.readLine().orEmpty()
                            var contentLength = 0
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                                }
                            }
                            if (contentLength > 0) {
                                val buf = CharArray(contentLength)
                                var read = 0
                                while (read < contentLength) {
                                    val r = reader.read(buf, read, contentLength - read)
                                    if (r <= 0) break
                                    read += r
                                }
                            }
                            val body = if (step == 0 && requestLine.startsWith("GET ")) {
                                """{"models":[{"name":"models/gemini-3.8-flash"}]}"""
                            } else {
                                """{"candidates":[{"content":{"parts":[{"text":"pong"}]}}]}"""
                            }
                            val response = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: application/json\r\n" +
                                "Content-Length: ${body.toByteArray().size}\r\n" +
                                "Connection: close\r\n\r\n" +
                                body
                            socket.getOutputStream().write(response.toByteArray())
                            socket.getOutputStream().flush()
                        }
                    }
                } catch (_: Exception) {
                }
            }
            serverThread.isDaemon = true
            serverThread.start()

            val progressUpdates = mutableListOf<String>()
            val res = client.testApiKeyWithBase(
                apiKey = "AIzaValidKey",
                model = "gemini-3.8-flash",
                generateContentBase = "http://127.0.0.1:${server.localPort}/v1beta/models",
                onProgress = { progressUpdates.add(it) }
            )
            assertTrue(res.isSuccess)
            assertEquals(200, res.httpCode)
            assertEquals(listOf("Key valid, memeriksa kuota…"), progressUpdates)
            assertTrue(res.message.startsWith("Key aktif & kuota tersedia ("))
            assertTrue(res.message.endsWith(" dtk)"))
            assertNotNull(res.elapsedMs)
        }
    }

    @Test
    fun testComputePacingWaitMs() {
        // Mulai pertama: tanpa jeda
        assertEquals(
            0L,
            com.example.data.api.GeminiApiClient.computePacingWaitMs(
                lastStartMs = null,
                nowMs = 5_000L,
                minIntervalMs = 12_000L
            )
        )

        // Permintaan sebelumnya sudah memakan waktu >= 12 detik: tidak menunggu
        assertEquals(
            0L,
            com.example.data.api.GeminiApiClient.computePacingWaitMs(
                lastStartMs = 1_000L,
                nowMs = 15_000L,
                minIntervalMs = 12_000L
            )
        )
        assertEquals(
            0L,
            com.example.data.api.GeminiApiClient.computePacingWaitMs(
                lastStartMs = 1_000L,
                nowMs = 13_000L,
                minIntervalMs = 12_000L
            )
        )

        // Permintaan cepat (selisih 4 detik): menunggu sisa 8 detik
        assertEquals(
            8_000L,
            com.example.data.api.GeminiApiClient.computePacingWaitMs(
                lastStartMs = 10_000L,
                nowMs = 14_000L,
                minIntervalMs = 12_000L
            )
        )
    }

    @Test
    fun testFormatStageDuration() {
        assertEquals("0 dtk", com.example.data.prefs.StageTimingStorage.formatDuration(0L))
        assertEquals("45 dtk", com.example.data.prefs.StageTimingStorage.formatDuration(45_000L))
        assertEquals("2 mnt 10 dtk", com.example.data.prefs.StageTimingStorage.formatDuration(130_000L))
        assertEquals("1 j 5 mnt", com.example.data.prefs.StageTimingStorage.formatDuration(3_900_000L))
    }

    @Test
    fun testStageTimingStorageAccumulatesOnResume() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val storage = com.example.data.prefs.StageTimingStorage(context)
        val analysisId = 9001L
        storage.remove(analysisId)

        // Sesi awal sebelum gagal
        storage.addStageDuration(analysisId, com.example.data.prefs.StageTimingStorage.STAGE_UPLOAD, 5_000L)
        storage.addStageDuration(analysisId, com.example.data.prefs.StageTimingStorage.STAGE_VERIFY_FILE, 2_000L)
        storage.addSegmentTiming(analysisId, durationMs = 10_000L, segmentCount = 1)
        storage.addStageDuration(analysisId, com.example.data.prefs.StageTimingStorage.STAGE_TOTAL, 20_000L)

        // Lanjutkan (resume) sesi
        storage.addStageDuration(analysisId, com.example.data.prefs.StageTimingStorage.STAGE_VERIFY_FILE, 500L)
        storage.addSegmentTiming(analysisId, durationMs = 14_000L, segmentCount = 1)
        storage.addStageDuration(analysisId, com.example.data.prefs.StageTimingStorage.STAGE_PACING_WAIT, 3_000L)
        storage.addStageDuration(analysisId, com.example.data.prefs.StageTimingStorage.STAGE_FINAL_NARRATION, 4_000L)
        storage.addStageDuration(analysisId, com.example.data.prefs.StageTimingStorage.STAGE_TOTAL, 22_000L)

        val timings = storage.getTimings(analysisId)
        assertEquals(5_000L, timings[com.example.data.prefs.StageTimingStorage.STAGE_UPLOAD])
        assertEquals(2_500L, timings[com.example.data.prefs.StageTimingStorage.STAGE_VERIFY_FILE])
        assertEquals(24_000L, timings[com.example.data.prefs.StageTimingStorage.STAGE_SEGMENT_ANALYSIS])
        assertEquals(2L, timings[com.example.data.prefs.StageTimingStorage.KEY_SEGMENT_COUNT])
        assertEquals(3_000L, timings[com.example.data.prefs.StageTimingStorage.STAGE_PACING_WAIT])
        assertEquals(4_000L, timings[com.example.data.prefs.StageTimingStorage.STAGE_FINAL_NARRATION])
        assertEquals(42_000L, timings[com.example.data.prefs.StageTimingStorage.STAGE_TOTAL])

        storage.remove(analysisId)
        assertTrue(storage.getTimings(analysisId).isEmpty())
    }

    @Test
    fun testApiKeyStorageDecryptionCacheAndInvalidation() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        context.getSharedPreferences("rekapin_secure_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        var encryptCalls = 0
        var decryptCalls = 0
        var failDecrypt = false

        val fakeCipher = object : com.example.data.prefs.ApiKeyCipher {
            override fun encrypt(plainText: String): String {
                encryptCalls++
                return "mock:$plainText"
            }

            override fun decrypt(encryptedText: String): String? {
                decryptCalls++
                if (failDecrypt) return null
                return if (encryptedText.startsWith("mock:")) encryptedText.removePrefix("mock:") else null
            }
        }

        val storage = com.example.data.prefs.ApiKeyStorage(context, fakeCipher)
        storage.addKey("Key Utama", "AIzaTestKeyOne")
        assertEquals(1, encryptCalls)

        // Panggilan pertama getAllKeys mendekripsi sekali
        val firstRead = storage.getAllKeys()
        assertEquals(1, firstRead.size)
        assertEquals(1, decryptCalls)

        // Panggilan kedua memakai cache tanpa memanggil cipher.decrypt lagi
        val secondRead = storage.getAllKeys()
        assertEquals(1, secondRead.size)
        assertEquals("AIzaTestKeyOne", secondRead.first().key)
        assertEquals(1, decryptCalls)

        // Perubahan (renameKey / addKey) membuang cache
        storage.renameKey(firstRead.first().id, "Key Ganti Nama")
        val afterRename = storage.getAllKeys()
        assertEquals("Key Ganti Nama", afterRename.first().name)
        assertEquals(2, decryptCalls)

        // Panggilan berikutnya kembali memakai cache
        storage.getAllKeys()
        assertEquals(2, decryptCalls)

        // Jika dekripsi gagal, cache tidak disimpan
        storage.addKey("Key Kedua", "AIzaTestKeyTwo")
        val countBeforeFailure = decryptCalls
        failDecrypt = true
        val failedRead = storage.getAllKeys()
        assertTrue(failedRead.isEmpty())
        assertTrue(storage.keyDecryptFailed)
        assertEquals(countBeforeFailure + 1, decryptCalls)

        // Panggilan berikutnya setelah gagal tetap mencoba mendekripsi lagi
        failDecrypt = false
        val recoveredRead = storage.getAllKeys()
        assertEquals(2, recoveredRead.size)
        assertEquals(countBeforeFailure + 2, decryptCalls)
    }

    @Test
    fun testThinkingLevelStorageDefaultsToAutoAndPersists() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        context.getSharedPreferences("rekapin_thinking_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        val storage = com.example.data.prefs.ThinkingLevelStorage(context)
        assertEquals(com.example.data.model.ThinkingLevelMode.AUTO, storage.getMode())
        assertNull(storage.getMode().apiValue)

        storage.setMode(com.example.data.model.ThinkingLevelMode.LOW)
        assertEquals(com.example.data.model.ThinkingLevelMode.LOW, storage.getMode())
        assertEquals("low", storage.getMode().apiValue)

        val reloaded = com.example.data.prefs.ThinkingLevelStorage(context)
        assertEquals(com.example.data.model.ThinkingLevelMode.LOW, reloaded.getMode())

        reloaded.setMode(com.example.data.model.ThinkingLevelMode.MEDIUM)
        assertEquals("medium", reloaded.getMode().apiValue)

        reloaded.setMode(com.example.data.model.ThinkingLevelMode.HIGH)
        assertEquals("high", reloaded.getMode().apiValue)

        assertEquals(
            com.example.data.model.ThinkingLevelMode.AUTO,
            com.example.data.model.ThinkingLevelMode.fromCode("UNKNOWN")
        )
    }

    @Test
    fun testThinkingConfigPayloadRulesAcrossBuildersAndModels() {
        var currentLevel: String? = "low"
        val client = com.example.data.api.GeminiApiClient(
            apiKeyProvider = { "fake-key" },
            thinkingLevelProvider = { currentLevel }
        )

        // 1. gemini-3.8-flash dengan provider non-null -> thinkingConfig terkirim di ketiga jalur
        val segBody38 = org.json.JSONObject(
            client.buildSegmentRequestBody("https://files/v1", "video/mp4", 0L, 60L, "Prompt segmen", "gemini-3.8-flash")
        )
        assertEquals(
            "low",
            segBody38.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel")
        )

        currentLevel = "medium"
        val narBody38 = org.json.JSONObject(
            client.buildFinalNarrationRequestBody("Prompt narasi", "gemini-3.8-flash")
        )
        assertEquals(
            "medium",
            narBody38.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel")
        )

        currentLevel = "high"
        val bibleBody38 = org.json.JSONObject(
            client.buildSeriesBibleRequestBody("Prompt memori", "gemini-3.8-flash-preview")
        )
        assertEquals(
            "high",
            bibleBody38.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel")
        )

        // 2. AUTO (provider == null) -> TIDAK mengirim thinkingConfig bahkan untuk gemini-3.8-flash
        currentLevel = null
        val autoSeg = org.json.JSONObject(
            client.buildSegmentRequestBody("https://files/v1", "video/mp4", 0L, 60L, "Prompt segmen", "gemini-3.8-flash")
        )
        org.junit.Assert.assertFalse(autoSeg.getJSONObject("generationConfig").has("thinkingConfig"))

        val autoNar = org.json.JSONObject(
            client.buildFinalNarrationRequestBody("Prompt narasi", "gemini-3.8-flash")
        )
        org.junit.Assert.assertFalse(autoNar.getJSONObject("generationConfig").has("thinkingConfig"))

        val autoBible = org.json.JSONObject(
            client.buildSeriesBibleRequestBody("Prompt memori", "gemini-3.8-flash")
        )
        org.junit.Assert.assertFalse(autoBible.getJSONObject("generationConfig").has("thinkingConfig"))

        // 3. Model lain (gemini-3.6-flash, gemini-3.5-flash-lite) -> TIDAK mengirim thinkingConfig walau provider non-null
        currentLevel = "high"
        for (otherModel in listOf("gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-2.5-pro")) {
            val segOther = org.json.JSONObject(
                client.buildSegmentRequestBody("https://files/v1", "video/mp4", 0L, 60L, "Prompt segmen", otherModel)
            )
            org.junit.Assert.assertFalse(segOther.getJSONObject("generationConfig").has("thinkingConfig"))

            val narOther = org.json.JSONObject(
                client.buildFinalNarrationRequestBody("Prompt narasi", otherModel)
            )
            org.junit.Assert.assertFalse(narOther.getJSONObject("generationConfig").has("thinkingConfig"))

            val bibleOther = org.json.JSONObject(
                client.buildSeriesBibleRequestBody("Prompt memori", otherModel)
            )
            org.junit.Assert.assertFalse(bibleOther.getJSONObject("generationConfig").has("thinkingConfig"))
        }
    }

    @Test
    fun testThinkingConfigHttp400FallbackRetriesOnceWithoutThinkingConfig() = kotlinx.coroutines.runBlocking {
        val receivedBodies = mutableListOf<String>()
        java.net.ServerSocket(0).use { server ->
            val serverThread = Thread {
                try {
                    for (step in 0..1) {
                        server.accept().use { socket ->
                            val input = socket.getInputStream().bufferedReader()
                            var contentLength = 0
                            while (true) {
                                val line = input.readLine() ?: break
                                if (line.isEmpty()) break
                                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                                }
                            }
                            val buf = CharArray(contentLength)
                            var read = 0
                            while (read < contentLength) {
                                val r = input.read(buf, read, contentLength - read)
                                if (r <= 0) break
                                read += r
                            }
                            receivedBodies.add(String(buf, 0, read))

                            val (statusLine, body) = if (step == 0) {
                                "HTTP/1.1 400 Bad Request" to
                                    """{"error":{"code":400,"message":"Unknown name \"thinkingLevel\" at thinkingConfig"}}"""
                            } else {
                                "HTTP/1.1 200 OK" to
                                    """{"candidates":[{"content":{"parts":[{"text":"berhasil"}]}}]}"""
                            }
                            val response = "$statusLine\r\n" +
                                "Content-Type: application/json\r\n" +
                                "Content-Length: ${body.toByteArray().size}\r\n" +
                                "Connection: close\r\n\r\n" +
                                body
                            socket.getOutputStream().write(response.toByteArray())
                            socket.getOutputStream().flush()
                        }
                    }
                } catch (_: Exception) {
                }
            }
            serverThread.isDaemon = true
            serverThread.start()

            val client = com.example.data.api.GeminiApiClient(
                apiKeyProvider = { "AIzaFakeKey" },
                pacingModeProvider = { com.example.data.model.RequestPacingMode.NONE },
                thinkingLevelProvider = { "low" }
            )
            val initialPayload = client.buildFinalNarrationRequestBody(
                prompt = "Test thinking fallback",
                model = "gemini-3.8-flash"
            )

            val response = client.executeWithRetry(
                stage = "Uji Fallback Thinking",
                url = "http://127.0.0.1:${server.localPort}/v1beta/models/gemini-3.8-flash:generateContent",
                apiKey = "AIzaFakeKey",
                jsonBody = initialPayload,
                modelForCounting = "gemini-3.8-flash",
                onStatusUpdate = {}
            )

            assertTrue(response.contains("berhasil"))
            assertEquals(2, receivedBodies.size)

            val firstGenConfig = org.json.JSONObject(receivedBodies[0]).getJSONObject("generationConfig")
            assertEquals("low", firstGenConfig.getJSONObject("thinkingConfig").getString("thinkingLevel"))

            val secondGenConfig = org.json.JSONObject(receivedBodies[1]).getJSONObject("generationConfig")
            org.junit.Assert.assertFalse(secondGenConfig.has("thinkingConfig"))
        }
    }

    @Test
    fun testSubtitleTimestampFormattingAndParsing() {
        assertEquals("00:00:00,000", com.example.ui.components.SubtitleExporter.formatSrtTimestamp(-50L))
        assertEquals("00:01:05,250", com.example.ui.components.SubtitleExporter.formatSrtTimestamp(65_250L))
        assertEquals("01:02:03,004", com.example.ui.components.SubtitleExporter.formatSrtTimestamp(3_723_004L))

        assertEquals(65_000L, com.example.ui.components.SubtitleExporter.parseTimestampToMs("01:05"))
        assertEquals(65_500L, com.example.ui.components.SubtitleExporter.parseTimestampToMs("[01:05.5]"))
        assertEquals(3_723_040L, com.example.ui.components.SubtitleExporter.parseTimestampToMs("01:02:03,04"))
        assertNull(com.example.ui.components.SubtitleExporter.parseTimestampToMs("bukan-waktu"))

        assertEquals(
            5_000L to 12_000L,
            com.example.ui.components.SubtitleExporter.parseTimeRangeMs("00:05 – 00:12")
        )
        assertEquals(
            10_000L to 14_000L,
            com.example.ui.components.SubtitleExporter.parseTimeRangeMs("00:10", nextStartMs = 14_000L)
        )
        assertEquals(
            10_000L to 18_000L,
            com.example.ui.components.SubtitleExporter.parseTimeRangeMs("00:10", nextStartMs = null, fallbackDurationMs = 8_000L)
        )

        assertEquals("Episode_01.srt", com.example.ui.components.SubtitleExporter.defaultSrtFileName("Episode_01.mp4"))
        assertEquals("subtitle_anime.srt", com.example.ui.components.SubtitleExporter.defaultSrtFileName("   "))
    }

    @Test
    fun testSubtitleExporterModesAndDeduplication() {
        val timelineWithSpeech = listOf(
            TimelineItem(
                timestamp = "00:00 - 00:05",
                sceneDescription = "Pemandangan kota di malam hari.",
                visualPrompt = "night city anime",
                dialogue = "Kota ini tidak pernah tidur.",
                originalNarration = ""
            ),
            TimelineItem(
                timestamp = "00:05 - 00:10",
                sceneDescription = "Tokoh utama berjalan melewati gang.",
                visualPrompt = "alley walk anime",
                dialogue = "",
                originalNarration = ""
            ),
            TimelineItem(
                timestamp = "00:10 - 00:15",
                sceneDescription = "Dia berhenti di depan gerbang.",
                visualPrompt = "gate stop anime",
                dialogue = "Aku harus masuk.",
                originalNarration = "Aku harus masuk."
            ),
            TimelineItem(
                timestamp = "00:15 - 00:20",
                sceneDescription = "Gerbang terbuka perlahan.",
                visualPrompt = "gate opens anime",
                dialogue = "Siapa di sana?",
                originalNarration = "Dia mendengar suara langkah."
            )
        )

        // AUTO dengan ucapan: pakai ucapan saja, dedup identik, dan gabung originalNarration + dialogue
        val autoWithSpeech = com.example.ui.components.SubtitleExporter.formatSrt(
            timeline = timelineWithSpeech,
            mode = com.example.ui.components.SubtitleSourceMode.AUTO
        )
        assertEquals(3, com.example.ui.components.SubtitleExporter.countSrtCues(autoWithSpeech))
        assertTrue(autoWithSpeech.contains("1\n00:00:00,000 --> 00:00:05,000\nKota ini tidak pernah tidur."))
        assertTrue(!autoWithSpeech.contains("gang"))
        assertTrue(autoWithSpeech.contains("2\n00:00:10,000 --> 00:00:15,000\nAku harus masuk.\n\n"))
        assertTrue(autoWithSpeech.contains("3\n00:00:15,000 --> 00:00:20,000\nDia mendengar suara langkah.\nSiapa di sana?"))

        // DIALOGUE_ONLY harus sama hasilnya untuk daftar dengan ucapan
        val dialogueOnly = com.example.ui.components.SubtitleExporter.formatSrt(
            timeline = timelineWithSpeech,
            mode = com.example.ui.components.SubtitleSourceMode.DIALOGUE_ONLY
        )
        assertEquals(autoWithSpeech, dialogueOnly)

        // SCENE_DESCRIPTION memakai deskripsi adegan pada semua entri
        val sceneOnly = com.example.ui.components.SubtitleExporter.formatSrt(
            timeline = timelineWithSpeech,
            mode = com.example.ui.components.SubtitleSourceMode.SCENE_DESCRIPTION
        )
        assertEquals(4, com.example.ui.components.SubtitleExporter.countSrtCues(sceneOnly))
        assertTrue(sceneOnly.contains("Tokoh utama berjalan melewati gang."))

        // AUTO tanpa ucapan sama sekali: fallback ke deskripsi adegan
        val silentTimeline = listOf(
            TimelineItem(timestamp = "00:00 - 00:06", sceneDescription = "Pembukaan episode.", visualPrompt = "opening"),
            TimelineItem(timestamp = "00:06", sceneDescription = "Judul muncul di layar.", visualPrompt = "title")
        )
        val autoSilent = com.example.ui.components.SubtitleExporter.formatSrt(
            timeline = silentTimeline,
            mode = com.example.ui.components.SubtitleSourceMode.AUTO,
            intervalSeconds = 4
        )
        assertEquals(2, com.example.ui.components.SubtitleExporter.countSrtCues(autoSilent))
        assertTrue(autoSilent.contains("00:00:06,000 --> 00:00:10,000\nJudul muncul di layar."))
        assertEquals(
            "",
            com.example.ui.components.SubtitleExporter.formatSrt(
                timeline = silentTimeline,
                mode = com.example.ui.components.SubtitleSourceMode.DIALOGUE_ONLY
            )
        )
    }

    @Test
    fun testSubtitleExporterMergedSrtShiftsTimestampsAcrossEpisodes() {
        val ep1 = AnimeAnalysisEntity(
            id = 1L,
            videoUriString = "",
            videoName = "ep1.mp4",
            videoSizeBytes = 100L,
            durationMs = 90_000L, // 1 menit 30 detik
            mimeType = "video/mp4",
            intervalSeconds = 5,
            totalSegments = 1,
            completedSegmentsCount = 1,
            geminiFileRemoteName = "",
            geminiFileUri = "",
            status = "COMPLETED",
            characterSheetJson = AnalysisJsonAdapter.characterSheetToJson(emptyList()),
            timelineJson = AnalysisJsonAdapter.timelineToJson(
                listOf(
                    TimelineItem(
                        timestamp = "00:05 - 00:10",
                        sceneDescription = "Adegan awal ep 1.",
                        visualPrompt = "ep1 opening",
                        dialogue = "Halo dunia."
                    )
                )
            ),
            segmentSummariesJson = "[]",
            narration = "",
            createdAt = 1000L
        )
        val ep2 = AnimeAnalysisEntity(
            id = 2L,
            videoUriString = "",
            videoName = "ep2.mp4",
            videoSizeBytes = 100L,
            durationMs = 60_000L,
            mimeType = "video/mp4",
            intervalSeconds = 5,
            totalSegments = 1,
            completedSegmentsCount = 1,
            geminiFileRemoteName = "",
            geminiFileUri = "",
            status = "COMPLETED",
            characterSheetJson = AnalysisJsonAdapter.characterSheetToJson(emptyList()),
            timelineJson = AnalysisJsonAdapter.timelineToJson(
                listOf(
                    TimelineItem(
                        timestamp = "00:02 - 00:08",
                        sceneDescription = "Adegan awal ep 2.",
                        visualPrompt = "ep2 opening",
                        dialogue = "Lanjut bertarung!"
                    )
                )
            ),
            segmentSummariesJson = "[]",
            narration = "",
            createdAt = 2000L
        )

        val mergedSrt = com.example.ui.components.SubtitleExporter.formatMergedSrt(
            items = listOf(ep1, ep2),
            mode = com.example.ui.components.SubtitleSourceMode.AUTO
        )

        assertEquals(2, com.example.ui.components.SubtitleExporter.countSrtCues(mergedSrt))
        assertTrue(mergedSrt.contains("1\n00:00:05,000 --> 00:00:10,000\nHalo dunia."))
        // Ep2 bergeser +90 detik -> 01:32 - 01:38
        assertTrue(mergedSrt.contains("2\n00:01:32,000 --> 00:01:38,000\nLanjut bertarung!"))
    }

    @Test
    fun testTtsScriptExporterCleaningPauseStylesAndStats() {
        assertEquals(
            "Episode_01_tts.txt",
            com.example.ui.components.TtsScriptExporter.defaultTtsFileName("Episode_01.mp4")
        )
        assertEquals(
            "naskah_tts_anime.txt",
            com.example.ui.components.TtsScriptExporter.defaultTtsFileName("   ")
        )

        val raw = """
            ### Bagian Awal
            [00:00 - 00:10] **Riku** memasuki gerbang tua. Dia menatap langit malam!
            - Suara angin terdengar pelan.

            00:10 - 00:20: "Kita tidak boleh mundur," ucap Riku dengan *tegas*.
        """.trimIndent()

        val cleaned = com.example.ui.components.TtsScriptExporter.cleanForTts(raw)
        assertTrue(!cleaned.contains("###"))
        assertTrue(!cleaned.contains("[00:00 - 00:10]"))
        assertTrue(!cleaned.contains("00:10 - 00:20"))
        assertTrue(!cleaned.contains("**"))
        assertTrue(!cleaned.contains("*tegas*"))
        assertTrue(cleaned.contains("Riku memasuki gerbang tua."))
        assertTrue(cleaned.contains("tegas."))

        val result = com.example.data.model.AnimeAnalysisResult(
            narration = raw,
            characterSheet = emptyList(),
            timeline = listOf(
                TimelineItem(
                    timestamp = "00:05 - 00:10",
                    sceneDescription = "Gerbang terbuka.",
                    visualPrompt = "gate",
                    dialogue = "Ayo masuk."
                )
            ),
            totalSegments = 1,
            durationFormatted = "00:20",
            intervalSeconds = 5
        )

        val perSentence = com.example.ui.components.TtsScriptExporter.formatTtsScript(
            result = result,
            sourceType = com.example.ui.components.TtsSourceType.NARRATION,
            pauseStyle = com.example.ui.components.TtsPauseStyle.PER_SENTENCE
        )
        assertTrue(perSentence.contains("Riku memasuki gerbang tua.\nDia menatap langit malam!\nSuara angin terdengar pelan."))

        val ssml = com.example.ui.components.TtsScriptExporter.formatTtsScript(
            result = result,
            sourceType = com.example.ui.components.TtsSourceType.NARRATION,
            pauseStyle = com.example.ui.components.TtsPauseStyle.SSML_BREAK
        )
        assertTrue(ssml.contains("<break time=\"0.6s\" />"))

        val timelineTts = com.example.ui.components.TtsScriptExporter.formatTtsScript(
            result = result,
            sourceType = com.example.ui.components.TtsSourceType.TIMELINE_SPEECH
        )
        assertEquals("Ayo masuk.", timelineTts)

        // Tag SSML tidak boleh dihitung sebagai kata terucap
        val statsNoTag = com.example.ui.components.TtsScriptExporter.computeStats(
            cleaned,
            com.example.ui.components.TtsReadingPace.NORMAL
        )
        val statsWithTag = com.example.ui.components.TtsScriptExporter.computeStats(
            ssml,
            com.example.ui.components.TtsReadingPace.NORMAL
        )
        assertEquals(statsNoTag.wordCount, statsWithTag.wordCount)
        assertTrue(statsWithTag.estimatedSeconds >= 1L)
    }

    @Test
    fun testTtsScriptExporterMergedScript() {
        val ep1 = AnimeAnalysisEntity(
            id = 1L,
            videoUriString = "",
            videoName = "ep1.mp4",
            videoSizeBytes = 100L,
            durationMs = 60_000L,
            mimeType = "video/mp4",
            intervalSeconds = 5,
            totalSegments = 1,
            completedSegmentsCount = 1,
            geminiFileRemoteName = "",
            geminiFileUri = "",
            status = "COMPLETED",
            characterSheetJson = "[]",
            timelineJson = "[]",
            segmentSummariesJson = "[]",
            narration = "[00:00] Pertama, hero tiba di desa."
        )
        val ep2 = ep1.copy(
            id = 2L,
            videoName = "ep2.mp4",
            narration = "[00:00] Kedua, pertempuran dimulai."
        )

        val mergedDefault = com.example.ui.components.TtsScriptExporter.formatMergedTtsScript(
            items = listOf(ep1, ep2)
        )
        assertEquals("Pertama, hero tiba di desa.\n\nKedua, pertempuran dimulai.", mergedDefault)
        assertTrue(!mergedDefault.contains("Bagian 1"))

        val mergedCustom = com.example.ui.components.TtsScriptExporter.formatMergedTtsScript(
            items = listOf(ep1, ep2),
            customMergedNarration = "**Versi AI**: Hero bertarung di desa."
        )
        assertEquals("Versi AI: Hero bertarung di desa.", mergedCustom)
    }

    @Test
    fun testResultEditHelpersFindReplaceAndCharacterPropagation() {
        val initial = com.example.data.model.AnimeAnalysisResult(
            narration = "Kael berjalan bersama Kaelis. Kael menoleh cepat.",
            characterSheet = listOf(
                CharacterInfo(name = "Kael", description = "Kael membawa pedang."),
                CharacterInfo(name = "Kaelis", description = "Teman Kael.")
            ),
            timeline = listOf(
                TimelineItem(
                    timestamp = "00:00 - 00:05",
                    sceneDescription = "Kael tersenyum.",
                    visualPrompt = "Kael smiling anime",
                    dialogue = "Aku Kael!",
                    originalNarration = "Kael maju."
                )
            ),
            totalSegments = 1,
            durationFormatted = "00:05",
            intervalSeconds = 5,
            segmentSummaries = listOf("Kael muncul di layar.")
        )

        // wholeWord = true tidak boleh menyentuh "Kaelis"
        val summaryWhole = com.example.ui.components.ResultEditHelpers.previewReplaceSummary(
            result = initial,
            findText = "Kael",
            ignoreCase = false,
            wholeWord = true
        )
        assertEquals(2, summaryWhole.narrationMatches)
        assertEquals(3, summaryWhole.characterMatches) // Kael.name, Kael.desc, Kaelis.desc
        assertEquals(4, summaryWhole.timelineMatches)
        assertEquals(1, summaryWhole.segmentMatches)
        assertEquals(10, summaryWhole.totalMatches)

        val replaced = com.example.ui.components.ResultEditHelpers.replaceTermInResult(
            result = initial,
            findText = "Kael",
            replaceText = "Cael$",
            ignoreCase = false,
            wholeWord = true
        )
        assertTrue(replaced.narration.contains("Cael$ berjalan bersama Kaelis."))
        assertEquals("Cael$", replaced.characterSheet[0].name)
        assertEquals("Cael$ smiling anime", replaced.timeline[0].visualPrompt)
        assertEquals("Cael$ muncul di layar.", replaced.segmentSummaries[0])

        // Rename karakter dengan propagasi global
        val renamed = com.example.ui.components.ResultEditHelpers.updateCharacter(
            result = initial,
            index = 0,
            newName = "Kyle",
            newDescription = "Pendekar pedang biru.",
            propagateRenameGlobally = true
        )
        assertEquals("Kyle", renamed.characterSheet[0].name)
        assertEquals("Pendekar pedang biru.", renamed.characterSheet[0].description)
        assertEquals("Teman Kyle.", renamed.characterSheet[1].description)
        assertTrue(renamed.narration.contains("Kyle berjalan bersama Kaelis. Kyle menoleh cepat."))
        assertEquals("Aku Kyle!", renamed.timeline[0].dialogue)
    }

    @Test
    fun testRecapPacingParseVideoDurationFallbacks() {
        val fromFormatted = com.example.data.model.AnimeAnalysisResult(
            narration = "Tes",
            characterSheet = emptyList(),
            timeline = emptyList(),
            totalSegments = 4,
            durationFormatted = "20:00",
            intervalSeconds = 5
        )
        assertEquals(1200L, com.example.ui.components.RecapPacingAnalyzer.parseVideoDurationSeconds(fromFormatted))

        val fromTimeline = com.example.data.model.AnimeAnalysisResult(
            narration = "Tes",
            characterSheet = emptyList(),
            timeline = listOf(
                TimelineItem(timestamp = "00:00 - 00:45", sceneDescription = "A", visualPrompt = ""),
                TimelineItem(timestamp = "00:45 - 01:30", sceneDescription = "B", visualPrompt = "")
            ),
            totalSegments = 1,
            durationFormatted = "",
            intervalSeconds = 5
        )
        assertEquals(90L, com.example.ui.components.RecapPacingAnalyzer.parseVideoDurationSeconds(fromTimeline))

        val fromSegments = com.example.data.model.AnimeAnalysisResult(
            narration = "Tes",
            characterSheet = emptyList(),
            timeline = emptyList(),
            totalSegments = 3,
            durationFormatted = "tidak valid",
            intervalSeconds = 5
        )
        assertEquals(540L, com.example.ui.components.RecapPacingAnalyzer.parseVideoDurationSeconds(fromSegments))
    }

    @Test
    fun testRecapPacingOverallClassificationAndPresets() {
        val analyzer = com.example.ui.components.RecapPacingAnalyzer

        val emptyReport = analyzer.analyzeOverall(
            script = "   ",
            sourceVideoSeconds = 120L,
            targetPreset = com.example.ui.components.PacingTargetPreset.FULL_VIDEO,
            pace = com.example.ui.components.TtsReadingPace.NORMAL
        )
        assertEquals(com.example.ui.components.PacingStatus.EMPTY, emptyReport.status)
        assertEquals(0, emptyReport.scriptWordCount)

        // Target 60 detik (120 * 0.50), kecepatan Normal 145 wpm -> ~145 kata harus pas (BALANCED)
        val balancedWords = (1..145).joinToString(" ") { "kata" }
        val balancedReport = analyzer.analyzeOverall(
            script = balancedWords,
            sourceVideoSeconds = 120L,
            targetPreset = com.example.ui.components.PacingTargetPreset.STANDARD_RECAP,
            pace = com.example.ui.components.TtsReadingPace.NORMAL
        )
        assertEquals(60L, balancedReport.targetVideoSeconds)
        assertEquals(com.example.ui.components.PacingStatus.BALANCED, balancedReport.status)

        // Terlalu singkat: 10 kata untuk target 60 detik
        val shortWords = (1..10).joinToString(" ") { "singkat" }
        val shortReport = analyzer.analyzeOverall(
            script = shortWords,
            sourceVideoSeconds = 120L,
            targetPreset = com.example.ui.components.PacingTargetPreset.STANDARD_RECAP,
            pace = com.example.ui.components.TtsReadingPace.NORMAL
        )
        assertEquals(com.example.ui.components.PacingStatus.TOO_SHORT, shortReport.status)

        // Terlalu padat: 260 kata untuk target 60 detik (~108 detik > 1.35 * 60)
        val denseWords = (1..260).joinToString(" ") { "padat" }
        val denseReport = analyzer.analyzeOverall(
            script = denseWords,
            sourceVideoSeconds = 120L,
            targetPreset = com.example.ui.components.PacingTargetPreset.STANDARD_RECAP,
            pace = com.example.ui.components.TtsReadingPace.NORMAL
        )
        assertEquals(com.example.ui.components.PacingStatus.TOO_DENSE, denseReport.status)
        assertTrue(denseReport.deltaSeconds > 0L)
    }

    @Test
    fun testRecapPacingBeatsAndDenseTimelineCues() {
        val analyzer = com.example.ui.components.RecapPacingAnalyzer

        val p1 = (1..36).joinToString(" ") { "awal" }
        val p2 = (1..36).joinToString(" ") { "tengah" }
        val p3 = (1..36).joinToString(" ") { "akhir" }
        val beats = analyzer.analyzeBeats(
            script = "$p1\n\n$p2\n\n$p3",
            targetVideoSeconds = 45L,
            pace = com.example.ui.components.TtsReadingPace.NORMAL
        )
        assertEquals(3, beats.size)
        assertEquals(1, beats[0].index)
        assertEquals(0L, beats[0].targetWindowStartSec)
        assertEquals(15L, beats[0].targetWindowEndSec)
        assertEquals(15L, beats[0].targetDurationSeconds)
        assertEquals(com.example.ui.components.PacingStatus.BALANCED, beats[0].status)

        val denseDialogue = (1..25).joinToString(" ") { "dialog" }
        val result = com.example.data.model.AnimeAnalysisResult(
            narration = "$p1\n\n$p2",
            characterSheet = emptyList(),
            timeline = listOf(
                TimelineItem(
                    timestamp = "00:00 - 00:03",
                    sceneDescription = "Adegan cepat",
                    visualPrompt = "",
                    dialogue = denseDialogue
                ),
                TimelineItem(
                    timestamp = "00:03 - 00:08",
                    sceneDescription = "Adegan santai",
                    visualPrompt = "",
                    dialogue = "Halo."
                )
            ),
            totalSegments = 1,
            durationFormatted = "01:00",
            intervalSeconds = 5
        )
        val report = analyzer.analyzeResult(
            result = result,
            sourceType = com.example.ui.components.TtsSourceType.NARRATION,
            targetPreset = com.example.ui.components.PacingTargetPreset.STANDARD_RECAP,
            pace = com.example.ui.components.TtsReadingPace.NORMAL
        )
        assertEquals(1, report.denseCues.size)
        assertEquals(0, report.denseCues[0].timelineIndex)
        assertEquals("00:00 - 00:03", report.denseCues[0].timestamp)
        assertEquals(3L, report.denseCues[0].clipDurationSeconds)
    }
}



