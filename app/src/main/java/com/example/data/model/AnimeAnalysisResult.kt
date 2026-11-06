package com.example.data.model

data class CharacterInfo(
    val name: String,
    val description: String
)

data class TimelineItem(
    val timestamp: String,
    val sceneDescription: String,
    val visualPrompt: String,
    val dialogue: String = "",
    val originalNarration: String = ""
)

data class AnimeAnalysisResult(
    val narration: String,
    val characterSheet: List<CharacterInfo>,
    val timeline: List<TimelineItem>,
    val totalSegments: Int,
    val durationFormatted: String,
    val intervalSeconds: Int,
    val videoName: String = "",
    val note: String = "",
    val segmentModels: List<String> = emptyList(),
    val segmentSummaries: List<String> = emptyList(),
    val narrationMode: String = "",
    val maxSceneDuration: Int = 5,
    val seriesBible: SeriesBible? = null
)

data class SegmentAnalysisOutput(
    val segmentSummary: String,
    val characterSheet: List<CharacterInfo>,
    val timeline: List<TimelineItem>
)
