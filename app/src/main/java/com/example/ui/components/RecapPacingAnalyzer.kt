package com.example.ui.components

import com.example.data.model.AnimeAnalysisResult
import com.example.data.model.TimelineItem
import com.example.data.prefs.StageTimingStorage
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class PacingTargetPreset(
    val code: String,
    val label: String,
    val ratio: Float,
    val description: String
) {
    FULL_VIDEO(
        code = "FULL",
        label = "1:1 Video",
        ratio = 1.0f,
        description = "Sinkron 100% dengan durasi video asli (voiceover / dubbing penuh)"
    ),
    STANDARD_RECAP(
        code = "HALF",
        label = "Rekap 50%",
        ratio = 0.50f,
        description = "Target rekap YouTube standar (~setengah durasi episode asli)"
    ),
    TIGHT_RECAP(
        code = "TIGHT",
        label = "Padat 35%",
        ratio = 0.35f,
        description = "Alur cepat dengan pemotongan jeda antar adegan"
    ),
    SHORTS_CUT(
        code = "SHORTS",
        label = "Kilat 20%",
        ratio = 0.20f,
        description = "Ringkasan padat untuk format Shorts, Reels, atau TikTok"
    );

    companion object {
        val DEFAULT = STANDARD_RECAP
    }
}

enum class PacingStatus(val label: String) {
    EMPTY("Kosong"),
    TOO_SHORT("Terlalu Singkat"),
    BALANCED("Pas & Seimbang"),
    SLIGHTLY_DENSE("Agak Padat"),
    TOO_DENSE("Terlalu Padat")
}

data class BeatPacingItem(
    val index: Int,
    val previewText: String,
    val wordCount: Int,
    val estimatedSpeechSeconds: Long,
    val targetWindowStartSec: Long,
    val targetWindowEndSec: Long,
    val targetDurationSeconds: Long,
    val deltaSeconds: Long,
    val status: PacingStatus
)

data class CuePacingIssue(
    val timelineIndex: Int,
    val timestamp: String,
    val clipDurationSeconds: Long,
    val wordCount: Int,
    val estimatedSpeechSeconds: Long,
    val textPreview: String
)

data class OverallPacingReport(
    val sourceVideoSeconds: Long,
    val targetVideoSeconds: Long,
    val scriptWordCount: Int,
    val estimatedSpeechSeconds: Long,
    val effectiveWpm: Int,
    val deltaSeconds: Long,
    val fillRatio: Float,
    val status: PacingStatus,
    val idealWordCount: Int,
    val recommendedMinWords: Int,
    val recommendedMaxWords: Int,
    val wordsDeltaToTarget: Int,
    val actionableAdvice: String,
    val beats: List<BeatPacingItem>,
    val denseCues: List<CuePacingIssue>
)

object RecapPacingAnalyzer {

    fun formatTimeMMSS(totalSec: Long): String {
        val clamped = totalSec.coerceAtLeast(0L)
        val hours = clamped / 3600L
        val minutes = (clamped % 3600L) / 60L
        val seconds = clamped % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatSignedDelta(deltaSeconds: Long): String {
        return when {
            deltaSeconds == 0L -> "0 dtk (Tepat)"
            deltaSeconds > 0L -> "+${StageTimingStorage.formatDuration(deltaSeconds * 1_000L)}"
            else -> "-${StageTimingStorage.formatDuration(abs(deltaSeconds) * 1_000L)}"
        }
    }

    fun parseVideoDurationSeconds(result: AnimeAnalysisResult): Long {
        val fromFormatted = SubtitleExporter.parseTimestampToMs(result.durationFormatted)
        if (fromFormatted != null && fromFormatted > 0L) {
            return (fromFormatted / 1_000L).coerceAtLeast(1L)
        }
        val maxTimelineEndMs = result.timeline.mapNotNull { item ->
            SubtitleExporter.parseTimeRangeMs(
                timestamp = item.timestamp,
                nextStartMs = null,
                fallbackDurationMs = (result.intervalSeconds.takeIf { it > 0 } ?: 5) * 1_000L
            )?.second
        }.maxOrNull()
        if (maxTimelineEndMs != null && maxTimelineEndMs > 0L) {
            return (maxTimelineEndMs / 1_000L).coerceAtLeast(1L)
        }
        return (result.totalSegments.coerceAtLeast(1) * 180L)
    }

    fun classifyFillRatio(wordCount: Int, fillRatio: Float): PacingStatus {
        if (wordCount <= 0) return PacingStatus.EMPTY
        return when {
            fillRatio < 0.75f -> PacingStatus.TOO_SHORT
            fillRatio <= 1.15f -> PacingStatus.BALANCED
            fillRatio <= 1.35f -> PacingStatus.SLIGHTLY_DENSE
            else -> PacingStatus.TOO_DENSE
        }
    }

    fun analyzeBeats(
        script: String,
        targetVideoSeconds: Long,
        pace: TtsReadingPace = TtsReadingPace.NORMAL
    ): List<BeatPacingItem> {
        val paragraphs = script
            .replace("\r\n", "\n")
            .split(Regex("""\n\s*\n"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (paragraphs.isEmpty()) return emptyList()

        val safeTargetSec = targetVideoSeconds.coerceAtLeast(1L)
        val count = paragraphs.size
        return paragraphs.mapIndexed { index, paragraph ->
            val stats = TtsScriptExporter.computeStats(paragraph, pace)
            val windowStart = (index * safeTargetSec) / count
            val windowEnd = (((index + 1) * safeTargetSec) / count).coerceAtLeast(windowStart + 1L)
            val allocatedSec = (windowEnd - windowStart).coerceAtLeast(1L)
            val ratio = stats.estimatedSeconds.toFloat() / allocatedSec.toFloat()
            val status = classifyFillRatio(stats.wordCount, ratio)
            val preview = paragraph.replace('\n', ' ').let { clean ->
                if (clean.length <= 72) clean else clean.take(69).trimEnd() + "..."
            }
            BeatPacingItem(
                index = index + 1,
                previewText = preview,
                wordCount = stats.wordCount,
                estimatedSpeechSeconds = stats.estimatedSeconds,
                targetWindowStartSec = windowStart,
                targetWindowEndSec = windowEnd,
                targetDurationSeconds = allocatedSec,
                deltaSeconds = stats.estimatedSeconds - allocatedSec,
                status = status
            )
        }
    }

    fun analyzeTimelineCues(
        timeline: List<TimelineItem>,
        intervalSeconds: Int,
        maxSceneDuration: Int,
        pace: TtsReadingPace = TtsReadingPace.NORMAL
    ): List<CuePacingIssue> {
        if (timeline.isEmpty()) return emptyList()
        val hasAnySpeech = SubtitleExporter.hasAnySpeechContent(timeline)
        val fallbackSec = when {
            intervalSeconds > 0 -> intervalSeconds
            maxSceneDuration > 0 -> maxSceneDuration
            else -> 5
        }
        val fallbackDurationMs = fallbackSec * 1_000L

        val parsedStarts = timeline.map { item ->
            val normalized = item.timestamp.trim().replace('–', '-').replace('—', '-')
            val firstToken = if (normalized.contains("-")) normalized.substringBefore("-") else normalized
            SubtitleExporter.parseTimestampToMs(firstToken)
        }

        val issues = mutableListOf<CuePacingIssue>()
        timeline.forEachIndexed { index, item ->
            val cueText = SubtitleExporter.resolveCueText(
                item = item,
                mode = SubtitleSourceMode.AUTO,
                hasAnySpeech = hasAnySpeech
            ).trim()
            if (cueText.isBlank()) return@forEachIndexed

            val nextStart = parsedStarts.drop(index + 1).firstOrNull { it != null }
            val range = SubtitleExporter.parseTimeRangeMs(
                timestamp = item.timestamp,
                nextStartMs = nextStart,
                fallbackDurationMs = fallbackDurationMs
            ) ?: return@forEachIndexed

            val clipDurationSec = ((range.second - range.first) / 1_000L).coerceAtLeast(1L)
            val stats = TtsScriptExporter.computeStats(cueText, pace)
            val isDense = stats.estimatedSeconds > clipDurationSec + 1L &&
                stats.estimatedSeconds.toFloat() > clipDurationSec.toFloat() * 1.25f
            if (isDense) {
                val preview = cueText.replace('\n', ' ').let { clean ->
                    if (clean.length <= 68) clean else clean.take(65).trimEnd() + "..."
                }
                issues.add(
                    CuePacingIssue(
                        timelineIndex = index,
                        timestamp = item.timestamp,
                        clipDurationSeconds = clipDurationSec,
                        wordCount = stats.wordCount,
                        estimatedSpeechSeconds = stats.estimatedSeconds,
                        textPreview = preview
                    )
                )
            }
        }
        return issues
    }

    fun analyzeOverall(
        script: String,
        sourceVideoSeconds: Long,
        targetPreset: PacingTargetPreset = PacingTargetPreset.DEFAULT,
        pace: TtsReadingPace = TtsReadingPace.DEFAULT,
        denseCues: List<CuePacingIssue> = emptyList()
    ): OverallPacingReport {
        val safeSourceSec = sourceVideoSeconds.coerceAtLeast(1L)
        val targetSec = (safeSourceSec.toDouble() * targetPreset.ratio.toDouble())
            .roundToLong()
            .coerceAtLeast(1L)

        val stats = TtsScriptExporter.computeStats(script, pace)
        val fillRatio = if (stats.wordCount <= 0) {
            0f
        } else {
            stats.estimatedSeconds.toFloat() / targetSec.toFloat()
        }
        val status = classifyFillRatio(stats.wordCount, fillRatio)
        val effectiveWpm = if (stats.wordCount <= 0) {
            0
        } else {
            ((stats.wordCount.toDouble() * 60.0) / targetSec.toDouble()).roundToInt()
        }

        val idealWords = ((targetSec.toDouble() / 60.0) * pace.wpm.toDouble()).roundToInt().coerceAtLeast(1)
        val minWords = (idealWords * 0.85).roundToInt().coerceAtLeast(1)
        val maxWords = (idealWords * 1.15).roundToInt().coerceAtLeast(minWords)
        val wordsDelta = stats.wordCount - idealWords
        val deltaSeconds = stats.estimatedSeconds - targetSec

        val advice = when (status) {
            PacingStatus.EMPTY -> "Naskah masih kosong. Pilih sumber teks yang berisi narasi atau dialog."
            PacingStatus.TOO_SHORT -> {
                val needWords = (-wordsDelta).coerceAtLeast(1)
                "Naskah lebih singkat ${StageTimingStorage.formatDuration(abs(deltaSeconds) * 1_000L)} dari target. " +
                    "Beri jeda visual lebih panjang atau tambah ~$needWords kata konteks adegan."
            }
            PacingStatus.BALANCED -> {
                "Ritme pas dan seimbang untuk target ${StageTimingStorage.formatDuration(targetSec * 1_000L)} " +
                    "pada kecepatan ${pace.label}."
            }
            PacingStatus.SLIGHTLY_DENSE -> {
                val trimWords = wordsDelta.coerceAtLeast(1)
                "Sedikit padat (+${StageTimingStorage.formatDuration(deltaSeconds * 1_000L)}). " +
                    "Pangkas sekitar $trimWords kata atau naikkan kecepatan baca."
            }
            PacingStatus.TOO_DENSE -> {
                val trimWords = wordsDelta.coerceAtLeast(1)
                "Terlalu padat (+${StageTimingStorage.formatDuration(deltaSeconds * 1_000L)}). " +
                    "Pangkas ~$trimWords kata lewat Edit Narasi atau pilih rasio target lebih longgar."
            }
        }

        val beats = analyzeBeats(script = script, targetVideoSeconds = targetSec, pace = pace)

        return OverallPacingReport(
            sourceVideoSeconds = safeSourceSec,
            targetVideoSeconds = targetSec,
            scriptWordCount = stats.wordCount,
            estimatedSpeechSeconds = stats.estimatedSeconds,
            effectiveWpm = effectiveWpm,
            deltaSeconds = deltaSeconds,
            fillRatio = fillRatio,
            status = status,
            idealWordCount = idealWords,
            recommendedMinWords = minWords,
            recommendedMaxWords = maxWords,
            wordsDeltaToTarget = wordsDelta,
            actionableAdvice = advice,
            beats = beats,
            denseCues = denseCues
        )
    }

    fun analyzeResult(
        result: AnimeAnalysisResult,
        sourceType: TtsSourceType = if (result.narration.isNotBlank()) TtsSourceType.NARRATION else TtsSourceType.TIMELINE_SPEECH,
        targetPreset: PacingTargetPreset = PacingTargetPreset.DEFAULT,
        pace: TtsReadingPace = TtsReadingPace.DEFAULT
    ): OverallPacingReport {
        val script = TtsScriptExporter.formatTtsScript(
            result = result,
            sourceType = sourceType,
            pauseStyle = TtsPauseStyle.NATURAL
        )
        val sourceDurationSec = parseVideoDurationSeconds(result)
        val denseCues = analyzeTimelineCues(
            timeline = result.timeline,
            intervalSeconds = result.intervalSeconds,
            maxSceneDuration = result.maxSceneDuration,
            pace = pace
        )
        return analyzeOverall(
            script = script,
            sourceVideoSeconds = sourceDurationSec,
            targetPreset = targetPreset,
            pace = pace,
            denseCues = denseCues
        )
    }
}
