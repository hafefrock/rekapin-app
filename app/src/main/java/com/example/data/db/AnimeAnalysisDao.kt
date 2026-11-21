package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimeAnalysisDao {

    @Query("SELECT * FROM anime_analyses ORDER BY updatedAt DESC")
    fun getAllAnalyses(): Flow<List<AnimeAnalysisEntity>>

    @Query("SELECT * FROM anime_analyses WHERE id = :id LIMIT 1")
    suspend fun getAnalysisById(id: Long): AnimeAnalysisEntity?

    @Query("SELECT * FROM anime_analyses WHERE videoName = :videoName AND videoSizeBytes = :sizeBytes ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestAnalysisForVideo(videoName: String, sizeBytes: Long): AnimeAnalysisEntity?

    @Query("SELECT * FROM anime_analyses WHERE status != 'COMPLETED' AND sourceType = 'VIDEO' ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestIncompleteAnalysis(): AnimeAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysis(entity: AnimeAnalysisEntity): Long

    @Update
    suspend fun updateAnalysis(entity: AnimeAnalysisEntity)

    @Query("DELETE FROM anime_analyses WHERE id = :id")
    suspend fun deleteAnalysisById(id: Long)

    @Query("DELETE FROM anime_analyses")
    suspend fun clearAll()
}
