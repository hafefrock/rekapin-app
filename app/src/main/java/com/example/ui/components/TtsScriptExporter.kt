package com.example.ui.components

import com.example.data.db.AnimeAnalysisEntity
import com.example.data.model.AnimeAnalysisResult
import com.example.data.prefs.StageTimingStorage
import java.util.Locale

enum class TtsSourceType(
    val code: String,
    val label: String,
    val description: String
) {
    NARRATION(
        code = "NARRATION",
        label = "Narasi Utama",
        description = "Naskah narasi hasil analisis yang dibersihkan dari tanda format dan timestamp"
    ),
    TIMELINE_SPEECH(
        code = "TIMELINE_SPEECH",
        label = "Dialog / Timeline",
        description = "Susun ucapan atau deskripsi adegan timeline secara berurutan tanpa timestamp"
    );

    companion object {
        val DEFAULT = NARRATION
    }
}

enum class TtsPauseStyle(
    val code: String,
    val label: String,
    val description: String
) {
    NATURAL(
        code = "NATURAL",
        label = "Paragraf Alami",
        description = "Pisahkan antar paragraf dengan baris kosong untuk jeda baca alami"
    ),
    PER_SENTENCE(
        code = "PER_SENTENCE",
        label = "Per Kalimat",
        description = "Satu kalimat per baris agar mudah direkam atau dicek bertahap"
    ),
    SSML_BREAK(
        code = "SSML_BREAK",
        label = "Tag Jeda SSML",
        description = "Sisipkan tag <break time=\"0.6s\" /> antar paragraf (ElevenLabs / Cloud TTS)"
    );

    companion object {
        val DEFAULT = NATURAL
    }
}

enum class TtsReadingPace(
    val wpm: Int,
    val label: String
) {
    SLOW(120, "Santai (120 kpm)"),
    NORMAL(145, "Normal (145 kpm)"),
    FAST(170, "Cepat (170 kpm)");

    companion object {
        val DEFAULT = NORMAL
    }
}

data class TtsScriptStats(
    val wordCount: Int,
    val charCount: Int,
    val lineCount: Int,
    val estimatedSeconds: Long,
    val formattedDuration: String
)

object TtsScriptExporter {

    private val timestampLinePrefixRegex = Regex(
        """^\s*[\[(]?\d{1,2}:\d{2}(?::\d{2})?(?:[.,]\d{1,3})?(?:\s*[-–—]\s*\d{1,2}:\d{2}(?::\d{2})?(?:[.,]\d{1,3})?)?[\])]?\s*[:\-–—]?\s*"""
    )
    private val inlineTimestampBracketRegex = Regex(
        """\[\d{1,2}:\d{2}(?::\d{2})?(?:[.,]\d{1,3})?(?:\s*[-–—]\s*\d{1,2}:\d{2}(?::\d{2})?(?:[.,]\d{1,3})?)?]\s*"""
    )
    private val markdownHeaderRegex = Regex("""^\s{0,3}#{1,6}\s+""", RegexOption.MULTILINE)
    private val bulletPrefixRegex = Regex("""^\s*[-*•]\s+""", RegexOption.MULTILINE)
    private val markdownEmphasisRegex = Regex("""(\*\*|__|~~|`)""")
    private val singleAsteriskItalicRegex = Regex("""(?<!\*)\*(?!\s)(.+?)(?<!\s)\*(?!\*)""")
    private val multiSpacesRegex = Regex("""[ \t]{2,}""")
    private val sentenceSplitRegex = Regex("""(?<=[.!?])\s+(?=[\p{L}\p{N}"“'(])""")
    private val ssmlTagRegex = Regex("""<break\b[^>]*/>""", RegexOption.IGNORE_CASE)

    fun defaultTtsFileName(videoName: String): String {
        val rawName = videoName.trim()
        return if (rawName.isNotBlank()) {
            val base = if (rawName.contains('.')) rawName.substringBeforeLast('.') else rawName
            "${base}_tts.txt"
        } else {
            "naskah_tts_anime.txt"
        }
    }

    fun cleanForTts(rawText: String): String {
        if (rawText.isBlank()) return ""

        val normalizedNewlines = rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        val cleanedLines = normalizedNewlines
            .split('\n')
            .map { line ->
                var current = line
                    .replace(markdownHeaderRegex, "")
                    .replace(bulletPrefixRegex, "")
                    .replace(inlineTimestampBracketRegex, "")
                current = timestampLinePrefixRegex.replace(current, "")
                current = current.replace(markdownEmphasisRegex, "")
                current = singleAsteriskItalicRegex.replace(current, "$1")
                current = current.replace(multiSpacesRegex, " ").trim()
                current
            }

        // Gabungkan baris kosong berlebih menjadi pemisah paragraf tunggal (\n\n)
        val paragraphs = mutableListOf<String>()
        val currentParagraph = mutableListOf<String>()
        cleanedLines.forEach { line ->
            if (line.isBlank()) {
                if (currentParagraph.isNotEmpty()) {
                    paragraphs.add(currentParagraph.joinToString(" "))
                    currentParagraph.clear()
                }
            } else {
                currentParagraph.add(line)
            }
        }
        if (currentParagraph.isNotEmpty()) {
            paragraphs.add(currentParagraph.joinToString(" "))
        }

        return paragraphs.joinToString("\n\n").trim()
    }

    internal fun applyPauseStyle(paragraphs: List<String>, pauseStyle: TtsPauseStyle): String {
        val nonEmpty = paragraphs.map { it.trim() }.filter { it.isNotBlank() }
        if (nonEmpty.isEmpty()) return ""

        return when (pauseStyle) {
            TtsPauseStyle.NATURAL -> nonEmpty.joinToString("\n\n")
            TtsPauseStyle.PER_SENTENCE -> {
                nonEmpty.flatMap { paragraph ->
                    paragraph.split(sentenceSplitRegex)
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                }.joinToString("\n")
            }
            TtsPauseStyle.SSML_BREAK -> {
                nonEmpty.joinToString("\n<break time=\"0.6s\" />\n")
            }
        }
    }

    fun formatTtsScript(
        result: AnimeAnalysisResult,
        sourceType: TtsSourceType = TtsSourceType.NARRATION,
        pauseStyle: TtsPauseStyle = TtsPauseStyle.NATURAL
    ): String {
        val rawParagraphs: List<String> = when (sourceType) {
            TtsSourceType.NARRATION -> {
                val cleaned = cleanForTts(result.narration)
                if (cleaned.isBlank()) emptyList() else cleaned.split("\n\n")
            }
            TtsSourceType.TIMELINE_SPEECH -> {
                val hasSpeech = SubtitleExporter.hasAnySpeechContent(result.timeline)
                result.timeline.mapNotNull { item ->
                    val raw = SubtitleExporter.resolveCueText(
                        item = item,
                        mode = SubtitleSourceMode.AUTO,
                        hasAnySpeech = hasSpeech
                    )
                    cleanForTts(raw).takeIf { it.isNotBlank() }
                }
            }
        }
        return applyPauseStyle(rawParagraphs, pauseStyle)
    }

    fun formatMergedTtsScript(
        items: List<AnimeAnalysisEntity>,
        customMergedNarration: String? = null,
        sourceType: TtsSourceType = TtsSourceType.NARRATION,
        pauseStyle: TtsPauseStyle = TtsPauseStyle.NATURAL
    ): String {
        if (sourceType == TtsSourceType.NARRATION && !customMergedNarration.isNullOrBlank()) {
            val cleaned = cleanForTts(customMergedNarration)
            return if (cleaned.isBlank()) "" else applyPauseStyle(cleaned.split("\n\n"), pauseStyle)
        }

        val allParagraphs = mutableListOf<String>()
        items.forEach { entity ->
            val res = entity.toAnimeAnalysisResult()
            when (sourceType) {
                TtsSourceType.NARRATION -> {
                    val cleaned = cleanForTts(res.narration)
                    if (cleaned.isNotBlank()) {
                        allParagraphs += cleaned.split("\n\n")
                    }
                }
                TtsSourceType.TIMELINE_SPEECH -> {
                    val hasSpeech = SubtitleExporter.hasAnySpeechContent(res.timeline)
                    res.timeline.forEach { item ->
                        val raw = SubtitleExporter.resolveCueText(
                            item = item,
                            mode = SubtitleSourceMode.AUTO,
                            hasAnySpeech = hasSpeech
                        )
                        val cleaned = cleanForTts(raw)
                        if (cleaned.isNotBlank()) {
                            allParagraphs.add(cleaned)
                        }
                    }
                }
            }
        }
        return applyPauseStyle(allParagraphs, pauseStyle)
    }

    fun computeStats(
        script: String,
        pace: TtsReadingPace = TtsReadingPace.NORMAL
    ): TtsScriptStats {
        if (script.isBlank()) {
            return TtsScriptStats(
                wordCount = 0,
                charCount = 0,
                lineCount = 0,
                estimatedSeconds = 0L,
                formattedDuration = "0 dtk"
            )
        }
        val spokenOnly = script.replace(ssmlTagRegex, " ").trim()
        val words = if (spokenOnly.isBlank()) {
            0
        } else {
            spokenOnly.split(Regex("""\s+""")).count { it.isNotBlank() }
        }
        val chars = spokenOnly.length
        val lines = script.lines().count { it.isNotBlank() }
        val estimatedSeconds = if (words == 0) {
            0L
        } else {
            ((words.toDouble() / pace.wpm.toDouble()) * 60.0).toLong().coerceAtLeast(1L)
        }
        val formattedDuration = if (estimatedSeconds <= 0L) {
            "0 dtk"
        } else {
            StageTimingStorage.formatDuration(estimatedSeconds * 1_000L)
        }

        return TtsScriptStats(
            wordCount = words,
            charCount = chars,
            lineCount = lines,
            estimatedSeconds = estimatedSeconds,
            formattedDuration = formattedDuration
        )
    }

    fun formatStatsSummary(stats: TtsScriptStats): String {
        return String.format(
            Locale("id", "ID"),
            "%d kata • %d karakter • ~%s",
            stats.wordCount,
            stats.charCount,
            stats.formattedDuration
        )
    }
}
