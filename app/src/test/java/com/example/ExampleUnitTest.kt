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
}
