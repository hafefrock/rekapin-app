package com.example.ui.components

import com.example.data.db.AnimeAnalysisEntity
import com.example.data.model.TimelineItem
import java.util.Locale

enum class SubtitleSourceMode(
    val code: String,
    val label: String,
    val description: String
) {
    AUTO(
        code = "AUTO",
        label = "Otomatis",
        description = "Pakai dialog/narasi asli bila ada, jika kosong pakai deskripsi adegan"
    ),
    DIALOGUE_ONLY(
        code = "DIALOGUE_ONLY",
        label = "Dialog / Narasi",
        description = "Hanya timestamp yang memiliki dialog karakter atau narasi asli"
    ),
    SCENE_DESCRIPTION(
        code = "SCENE_DESCRIPTION",
        label = "Deskripsi Adegan",
        description = "Tampilkan deskripsi adegan pada setiap timestamp"
    );

    companion object {
        val DEFAULT = AUTO
    }
}

object SubtitleExporter {

    private data class SrtCue(
        val startMs: Long,
        val endMs: Long,
        val text: String
    )

    fun defaultSrtFileName(videoName: String): String {
        val rawName = videoName.trim()
        return if (rawName.isNotBlank()) {
            val base = if (rawName.contains('.')) rawName.substringBeforeLast('.') else rawName
            "$base.srt"
        } else {
            "subtitle_anime.srt"
        }
    }

    fun formatSrtTimestamp(totalMs: Long): String {
        val clamped = totalMs.coerceAtLeast(0L)
        val hours = clamped / 3_600_000L
        val minutes = (clamped % 3_600_000L) / 60_000L
        val seconds = (clamped % 60_000L) / 1_000L
        val millis = clamped % 1_000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    fun parseTimestampToMs(token: String): Long? {
        val cleaned = token.trim()
            .replace(',', '.')
            .trim('[', ']', '(', ')')
        if (cleaned.isBlank()) return null

        val dotIndex = cleaned.lastIndexOf('.')
        val mainPart: String
        val millisPart: Long
        if (dotIndex != -1) {
            mainPart = cleaned.substring(0, dotIndex)
            val fracRaw = cleaned.substring(dotIndex + 1).filter { it.isDigit() }
            millisPart = when {
                fracRaw.isEmpty() -> 0L
                fracRaw.length == 1 -> (fracRaw.toLongOrNull() ?: 0L) * 100L
                fracRaw.length == 2 -> (fracRaw.toLongOrNull() ?: 0L) * 10L
                else -> fracRaw.take(3).toLongOrNull() ?: 0L
            }
        } else {
            mainPart = cleaned
            millisPart = 0L
        }

        val parts = mainPart.split(":")
        val baseSeconds = when (parts.size) {
            2 -> {
                val m = parts[0].trim().toLongOrNull() ?: return null
                val s = parts[1].trim().toLongOrNull() ?: return null
                if (m < 0L || s < 0L) return null
                m * 60L + s
            }
            3 -> {
                val h = parts[0].trim().toLongOrNull() ?: return null
                val m = parts[1].trim().toLongOrNull() ?: return null
                val s = parts[2].trim().toLongOrNull() ?: return null
                if (h < 0L || m < 0L || s < 0L) return null
                h * 3600L + m * 60L + s
            }
            else -> return null
        }

        return baseSeconds * 1000L + millisPart
    }

    fun parseTimeRangeMs(
        timestamp: String,
        nextStartMs: Long? = null,
        fallbackDurationMs: Long = 5_000L
    ): Pair<Long, Long>? {
        val normalized = timestamp.trim()
            .replace('–', '-')
            .replace('—', '-')
        if (normalized.isBlank()) return null

        val safeFallback = fallbackDurationMs.coerceAtLeast(1_000L)
        if (normalized.contains("-")) {
            val parts = normalized.split("-", limit = 2)
            val start = parseTimestampToMs(parts[0])
            val end = parseTimestampToMs(parts.getOrNull(1).orEmpty())
            if (start != null) {
                val resolvedEnd = when {
                    end != null && end > start -> end
                    nextStartMs != null && nextStartMs > start -> nextStartMs
                    else -> start + safeFallback
                }
                return start to resolvedEnd
            }
        }

        val singleStart = parseTimestampToMs(normalized) ?: return null
        val singleEnd = if (nextStartMs != null && nextStartMs > singleStart) {
            nextStartMs
        } else {
            singleStart + safeFallback
        }
        return singleStart to singleEnd
    }

    internal fun hasAnySpeechContent(timeline: List<TimelineItem>): Boolean {
        return timeline.any { it.dialogue.isNotBlank() || it.originalNarration.isNotBlank() }
    }

    internal fun resolveCueText(
        item: TimelineItem,
        mode: SubtitleSourceMode,
        hasAnySpeech: Boolean
    ): String {
        val speechCombined = buildList {
            val orig = item.originalNarration.trim()
            val dlg = item.dialogue.trim()
            if (orig.isNotBlank()) add(orig)
            if (dlg.isNotBlank() && dlg != orig) add(dlg)
        }.joinToString("\n")

        return when (mode) {
            SubtitleSourceMode.DIALOGUE_ONLY -> speechCombined
            SubtitleSourceMode.SCENE_DESCRIPTION -> item.sceneDescription.trim()
            SubtitleSourceMode.AUTO -> {
                if (hasAnySpeech) speechCombined else item.sceneDescription.trim()
            }
        }
    }

    private fun extractCues(
        timeline: List<TimelineItem>,
        mode: SubtitleSourceMode,
        intervalSeconds: Int,
        maxSceneDuration: Int,
        offsetMs: Long
    ): List<SrtCue> {
        if (timeline.isEmpty()) return emptyList()

        val hasAnySpeech = hasAnySpeechContent(timeline)
        val fallbackSec = when {
            intervalSeconds > 0 -> intervalSeconds
            maxSceneDuration > 0 -> maxSceneDuration
            else -> 5
        }
        val fallbackDurationMs = fallbackSec * 1_000L

        val parsedStarts = timeline.map { item ->
            val normalized = item.timestamp.trim().replace('–', '-').replace('—', '-')
            val firstToken = if (normalized.contains("-")) normalized.substringBefore("-") else normalized
            parseTimestampToMs(firstToken)
        }

        val cues = mutableListOf<SrtCue>()
        timeline.forEachIndexed { index, item ->
            val cueText = resolveCueText(item, mode, hasAnySpeech).trim()
            if (cueText.isBlank()) return@forEachIndexed

            val nextStart = parsedStarts.drop(index + 1).firstOrNull { it != null }
            val range = parseTimeRangeMs(
                timestamp = item.timestamp,
                nextStartMs = nextStart,
                fallbackDurationMs = fallbackDurationMs
            ) ?: return@forEachIndexed

            cues.add(
                SrtCue(
                    startMs = range.first + offsetMs,
                    endMs = range.second + offsetMs,
                    text = cueText
                )
            )
        }
        return cues
    }

    private fun renderCuesToSrt(cues: List<SrtCue>): String {
        if (cues.isEmpty()) return ""
        return buildString {
            cues.forEachIndexed { idx, cue ->
                appendLine(idx + 1)
                appendLine("${formatSrtTimestamp(cue.startMs)} --> ${formatSrtTimestamp(cue.endMs)}")
                appendLine(cue.text)
                if (idx < cues.size - 1) appendLine()
            }
        }.trimEnd()
    }

    fun formatSrt(
        timeline: List<TimelineItem>,
        mode: SubtitleSourceMode = SubtitleSourceMode.AUTO,
        intervalSeconds: Int = 5,
        maxSceneDuration: Int = 5,
        offsetMs: Long = 0L
    ): String {
        val cues = extractCues(
            timeline = timeline,
            mode = mode,
            intervalSeconds = intervalSeconds,
            maxSceneDuration = maxSceneDuration,
            offsetMs = offsetMs
        )
        return renderCuesToSrt(cues)
    }

    fun formatMergedSrt(
        items: List<AnimeAnalysisEntity>,
        mode: SubtitleSourceMode = SubtitleSourceMode.AUTO
    ): String {
        if (items.isEmpty()) return ""
        val allCues = mutableListOf<SrtCue>()
        var currentOffsetMs = 0L
        items.forEach { entity ->
            val res = entity.toAnimeAnalysisResult()
            allCues += extractCues(
                timeline = res.timeline,
                mode = mode,
                intervalSeconds = res.intervalSeconds,
                maxSceneDuration = res.maxSceneDuration,
                offsetMs = currentOffsetMs
            )
            val videoDurMs = ((entity.durationMs / 1_000L) * 1_000L).coerceAtLeast(0L)
            currentOffsetMs += videoDurMs
        }
        return renderCuesToSrt(allCues)
    }

    fun countSrtCues(srtContent: String): Int {
        if (srtContent.isBlank()) return 0
        return srtContent
            .trim()
            .split(Regex("\\r?\\n\\r?\\n"))
            .count { it.contains("-->") }
    }
}
