package com.example.ui.components

import com.example.data.db.AnalysisJsonAdapter
import com.example.data.db.AnimeAnalysisEntity
import com.example.data.model.AnimeAnalysisResult
import com.example.data.model.CharacterInfo
import com.example.data.model.TimelineItem

data class EditMatchSummary(
    val narrationMatches: Int = 0,
    val characterMatches: Int = 0,
    val timelineMatches: Int = 0,
    val segmentMatches: Int = 0
) {
    val totalMatches: Int
        get() = narrationMatches + characterMatches + timelineMatches + segmentMatches
}

data class EditScopeOptions(
    val narration: Boolean = true,
    val characters: Boolean = true,
    val timeline: Boolean = true,
    val segments: Boolean = true
)

object ResultEditHelpers {

    private fun buildSearchRegex(
        findText: String,
        ignoreCase: Boolean,
        wholeWord: Boolean
    ): Regex? {
        if (findText.isEmpty()) return null
        val escaped = Regex.escape(findText)
        val pattern = if (wholeWord) {
            """(?<![\p{L}\p{N}_])$escaped(?![\p{L}\p{N}_])"""
        } else {
            escaped
        }
        val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        return Regex(pattern, options)
    }

    fun countOccurrences(
        text: String,
        findText: String,
        ignoreCase: Boolean = true,
        wholeWord: Boolean = false
    ): Int {
        if (text.isEmpty() || findText.isEmpty()) return 0
        val regex = buildSearchRegex(findText, ignoreCase, wholeWord) ?: return 0
        return regex.findAll(text).count()
    }

    fun previewReplaceSummary(
        result: AnimeAnalysisResult,
        findText: String,
        ignoreCase: Boolean = true,
        wholeWord: Boolean = false,
        scope: EditScopeOptions = EditScopeOptions()
    ): EditMatchSummary {
        if (findText.isEmpty()) return EditMatchSummary()
        val regex = buildSearchRegex(findText, ignoreCase, wholeWord) ?: return EditMatchSummary()

        val narrationCount = if (scope.narration) regex.findAll(result.narration).count() else 0
        val charCount = if (scope.characters) {
            result.characterSheet.sumOf { ch ->
                regex.findAll(ch.name).count() + regex.findAll(ch.description).count()
            }
        } else 0
        val timelineCount = if (scope.timeline) {
            result.timeline.sumOf { item ->
                regex.findAll(item.sceneDescription).count() +
                    regex.findAll(item.visualPrompt).count() +
                    regex.findAll(item.dialogue).count() +
                    regex.findAll(item.originalNarration).count()
            }
        } else 0
        val segmentCount = if (scope.segments) {
            result.segmentSummaries.sumOf { summary -> regex.findAll(summary).count() }
        } else 0

        return EditMatchSummary(
            narrationMatches = narrationCount,
            characterMatches = charCount,
            timelineMatches = timelineCount,
            segmentMatches = segmentCount
        )
    }

    fun replaceTermInResult(
        result: AnimeAnalysisResult,
        findText: String,
        replaceText: String,
        ignoreCase: Boolean = true,
        wholeWord: Boolean = false,
        scope: EditScopeOptions = EditScopeOptions()
    ): AnimeAnalysisResult {
        if (findText.isEmpty() || findText == replaceText && !ignoreCase) return result
        val regex = buildSearchRegex(findText, ignoreCase, wholeWord) ?: return result
        val safeReplacement = Regex.escapeReplacement(replaceText)

        val updatedNarration = if (scope.narration) {
            regex.replace(result.narration, safeReplacement)
        } else {
            result.narration
        }

        val updatedCharacters = if (scope.characters) {
            result.characterSheet.map { ch ->
                ch.copy(
                    name = regex.replace(ch.name, safeReplacement),
                    description = regex.replace(ch.description, safeReplacement)
                )
            }
        } else {
            result.characterSheet
        }

        val updatedTimeline = if (scope.timeline) {
            result.timeline.map { item ->
                item.copy(
                    sceneDescription = regex.replace(item.sceneDescription, safeReplacement),
                    visualPrompt = regex.replace(item.visualPrompt, safeReplacement),
                    dialogue = regex.replace(item.dialogue, safeReplacement),
                    originalNarration = regex.replace(item.originalNarration, safeReplacement)
                )
            }
        } else {
            result.timeline
        }

        val updatedSummaries = if (scope.segments) {
            result.segmentSummaries.map { summary ->
                regex.replace(summary, safeReplacement)
            }
        } else {
            result.segmentSummaries
        }

        return result.copy(
            narration = updatedNarration,
            characterSheet = updatedCharacters,
            timeline = updatedTimeline,
            segmentSummaries = updatedSummaries
        )
    }

    fun updateNarration(
        result: AnimeAnalysisResult,
        newNarration: String
    ): AnimeAnalysisResult = result.copy(narration = newNarration)

    fun updateCharacter(
        result: AnimeAnalysisResult,
        index: Int,
        newName: String,
        newDescription: String,
        propagateRenameGlobally: Boolean = false
    ): AnimeAnalysisResult {
        val current = result.characterSheet.getOrNull(index) ?: return result
        val trimmedName = newName.trim().ifBlank { current.name }
        val trimmedDesc = newDescription.trim()

        val updatedChars = result.characterSheet.toMutableList().apply {
            this[index] = CharacterInfo(name = trimmedName, description = trimmedDesc)
        }
        val baseUpdated = result.copy(characterSheet = updatedChars)

        return if (propagateRenameGlobally && trimmedName != current.name && current.name.isNotBlank()) {
            replaceTermInResult(
                result = baseUpdated,
                findText = current.name,
                replaceText = trimmedName,
                ignoreCase = false,
                wholeWord = true,
                scope = EditScopeOptions(
                    narration = true,
                    characters = true,
                    timeline = true,
                    segments = true
                )
            )
        } else {
            baseUpdated
        }
    }

    fun updateTimelineItem(
        result: AnimeAnalysisResult,
        index: Int,
        newItem: TimelineItem
    ): AnimeAnalysisResult {
        if (index !in result.timeline.indices) return result
        val updatedTimeline = result.timeline.toMutableList().apply {
            this[index] = newItem
        }
        return result.copy(timeline = updatedTimeline)
    }

    fun previewReplaceSummaryInEntities(
        entities: List<AnimeAnalysisEntity>,
        findText: String,
        ignoreCase: Boolean = true,
        wholeWord: Boolean = false
    ): Int {
        if (findText.isEmpty()) return 0
        return entities.sumOf { entity ->
            previewReplaceSummary(
                result = entity.toAnimeAnalysisResult(),
                findText = findText,
                ignoreCase = ignoreCase,
                wholeWord = wholeWord
            ).totalMatches
        }
    }

    fun replaceTermInEntities(
        entities: List<AnimeAnalysisEntity>,
        findText: String,
        replaceText: String,
        ignoreCase: Boolean = true,
        wholeWord: Boolean = false
    ): List<AnimeAnalysisEntity> {
        if (findText.isEmpty()) return entities
        return entities.map { entity ->
            val edited = replaceTermInResult(
                result = entity.toAnimeAnalysisResult(),
                findText = findText,
                replaceText = replaceText,
                ignoreCase = ignoreCase,
                wholeWord = wholeWord
            )
            entity.copy(
                narration = edited.narration,
                characterSheetJson = AnalysisJsonAdapter.characterSheetToJson(edited.characterSheet),
                timelineJson = AnalysisJsonAdapter.timelineToJson(edited.timeline),
                segmentSummariesJson = AnalysisJsonAdapter.summariesToJson(edited.segmentSummaries)
            )
        }
    }
}
