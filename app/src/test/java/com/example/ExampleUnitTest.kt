package com.example

import com.example.data.api.GeminiConstants
import com.example.data.db.AnalysisJsonAdapter
import com.example.data.db.AnimeAnalysisEntity
import com.example.data.model.AnalysisState
import com.example.data.model.CharacterInfo
import com.example.data.model.TimelineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.math.ceil

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {

    @Test
    fun verifyGeminiModelConstant() {
        assertEquals("gemini-2.5-flash", GeminiConstants.GEMINI_MODEL)
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
    fun testVideoMatchingTolerance() {
        fun matches(savedDurationMs: Long, savedSize: Long, newDurationMs: Long, newSize: Long): Boolean {
            val durationDiffMs = kotlin.math.abs(savedDurationMs - newDurationMs)
            val durationMatches = durationDiffMs <= 2000L // toleransi 2 detik
            val sizeMatches = savedSize > 0 && savedSize == newSize
            return durationMatches && sizeMatches
        }

        // Sama persis
        assertTrue(matches(60_000L, 50_000_000L, 60_000L, 50_000_000L))
        // Selisih 1,5 detik (toleransi 2 detik)
        assertTrue(matches(60_000L, 50_000_000L, 61_500L, 50_000_000L))
        // Selisih 2 detik pas
        assertTrue(matches(60_000L, 50_000_000L, 62_000L, 50_000_000L))
        // Selisih 3 detik (melebihi toleransi)
        assertTrue(!matches(60_000L, 50_000_000L, 63_000L, 50_000_000L))
        // Ukuran beda
        assertTrue(!matches(60_000L, 50_000_000L, 60_000L, 50_000_001L))
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
        // Error 429 tanpa kata PerDay, tetapi retryDelay > 5 menit (e.g. 600s = 10 menit)
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
        assertTrue(result.isDailyQuota)
        assertEquals(600L, result.retryDelaySeconds)
        assertEquals("Kuota harian habis. Coba lagi sekitar 10 menit", result.userMessage)
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

        // 1. Jika belum ada daftar tersimpan, getModels() mengembalikan lima model berurutan:
        // gemini-2.5-flash, gemini-3.8-flash, gemini-3.6-flash, gemini-3.7-flash, gemini-3.5-flash-lite
        val initialModels = storage.getModels()
        assertEquals(5, initialModels.size)
        assertEquals("gemini-2.5-flash", initialModels[0])
        assertEquals("gemini-3.8-flash", initialModels[1])
        assertEquals("gemini-3.6-flash", initialModels[2])
        assertEquals("gemini-3.7-flash", initialModels[3])
        assertEquals("gemini-3.5-flash-lite", initialModels[4])

        // Model terpilih bawaan tetap yang pertama (gemini-2.5-flash)
        assertEquals("gemini-2.5-flash", storage.getSelectedModel())

        // 2. Pertahankan DEFAULT_MODEL (gemini-2.5-flash) sebagai cadangan
        assertEquals("gemini-2.5-flash", com.example.data.prefs.ModelStorage.DEFAULT_MODEL)

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
        assertEquals(20, storage.getDefaultLimit("gemini-3.7-flash"))
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
            customPromptDesc
        ) as String

        // Memastikan prompt mengandung deskripsi gaya terpilih
        assertTrue("Prompt harus memuat deskripsi gaya visual yang dipilih", prompt.contains(customPromptDesc))
        // Memastikan aturan kepatuhan gaya baru ada
        assertTrue(
            "Prompt harus berisi aturan pembatasan gaya",
            prompt.contains("Follow only the selected style; do not mix other styles")
        )
        // Memastikan teks lama "Never 3D, never photorealistic" sudah tidak ada
        org.junit.Assert.assertFalse(
            "Prompt tidak boleh mengandung kalimat lama 'Never 3D, never photorealistic'",
            prompt.contains("Never 3D, never photorealistic")
        )
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
}
