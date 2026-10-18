package com.example

import com.example.data.api.GeminiConstants
import com.example.data.db.AnalysisJsonAdapter
import com.example.data.db.AnimeAnalysisEntity
import com.example.data.model.AnalysisState
import com.example.data.model.CharacterInfo
import com.example.data.model.TimelineItem
import org.junit.Assert.assertEquals
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
}
