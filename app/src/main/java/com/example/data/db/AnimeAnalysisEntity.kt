package com.example.data.db

import androidx.room.ColumnInfo
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
    val uploadKeyHash: String = "",
    val status: String, // "COMPLETED", "IN_PROGRESS", "FAILED"
    val characterSheetJson: String,
    val timelineJson: String,
    val segmentSummariesJson: String,
    val segmentModelsJson: String = "[]",
    val narration: String,
    val lastErrorStage: String = "",
    val lastErrorMessage: String = "",
    val segmentPlanJson: String = "[]",
    val segmentDurationMode: String = "AUTO",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''")
    val videoFingerprint: String = "",
    // VIDEO (default) atau SCRIPT. Untuk SCRIPT, videoUriString berisi path file naskah di penyimpanan aplikasi.
    @ColumnInfo(defaultValue = "'VIDEO'")
    val sourceType: String = "VIDEO"
) {
    val isCompleted: Boolean
        get() = status == "COMPLETED"

    val isScript: Boolean
        get() = sourceType == "SCRIPT"

    val formattedDate: String
        get() {
            val sdf = DATE_FORMATTER.get() ?: createDateFormatter()
            return sdf.format(Date(updatedAt))
        }

    companion object {
        private val DATE_FORMATTER: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
            createDateFormatter()
        }

        private fun createDateFormatter(): SimpleDateFormat =
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"))
    }

    val formattedDuration: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }

    fun toAnimeAnalysisResult(
        maxSceneDuration: Int = 5,
        seriesBible: com.example.data.model.SeriesBible? = null
    ): AnimeAnalysisResult {
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
            } else "",
            segmentModels = AnalysisJsonAdapter.jsonToSummaries(segmentModelsJson),
            segmentSummaries = AnalysisJsonAdapter.jsonToSummaries(segmentSummariesJson),
            maxSceneDuration = maxSceneDuration,
            seriesBible = seriesBible
        )
    }
}
