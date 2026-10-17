package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.AnimeAnalysisResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "anime_analyses")
data class AnimeAnalysisEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val videoUriString: String,
    val videoName: String,
    val videoSizeBytes: Long,
    val durationMs: Long,
    val mimeType: String,
    val intervalSeconds: Int,
    val totalSegments: Int,
    val completedSegmentsCount: Int,
    val geminiFileRemoteName: String,
    val geminiFileUri: String,
    val status: String, // "COMPLETED", "IN_PROGRESS", "FAILED"
    val characterSheetJson: String,
    val timelineJson: String,
    val segmentSummariesJson: String,
    val narration: String,
    val lastErrorStage: String = "",
    val lastErrorMessage: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isCompleted: Boolean
        get() = status == "COMPLETED"

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
            return sdf.format(Date(updatedAt))
        }

    val formattedDuration: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }

    fun toAnimeAnalysisResult(): AnimeAnalysisResult {
        return AnimeAnalysisResult(
            narration = narration,
            characterSheet = AnalysisJsonAdapter.jsonToCharacterSheet(characterSheetJson),
            timeline = AnalysisJsonAdapter.jsonToTimeline(timelineJson),
            totalSegments = totalSegments,
            durationFormatted = formattedDuration,
            intervalSeconds = intervalSeconds,
            videoName = videoName,
            note = if (lastErrorMessage == "Segmen terakhir dilewati" || lastErrorStage.contains("dilewati", ignoreCase = true)) {
                "Segmen terakhir dilewati"
            } else ""
        )
    }
}
