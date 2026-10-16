package com.example.data.repository

import com.example.data.db.AnimeAnalysisDao
import com.example.data.db.AnimeAnalysisEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class AnimeAnalysisRepository(
    private val dao: AnimeAnalysisDao
) {

    val allAnalyses: Flow<List<AnimeAnalysisEntity>> = dao.getAllAnalyses().flowOn(Dispatchers.IO)

    suspend fun getAnalysisById(id: Long): AnimeAnalysisEntity? = withContext(Dispatchers.IO) {
        dao.getAnalysisById(id)
    }

    suspend fun getLatestAnalysisForVideo(videoName: String, sizeBytes: Long): AnimeAnalysisEntity? = withContext(Dispatchers.IO) {
        dao.getLatestAnalysisForVideo(videoName, sizeBytes)
    }

    suspend fun getLatestIncompleteAnalysis(): AnimeAnalysisEntity? = withContext(Dispatchers.IO) {
        dao.getLatestIncompleteAnalysis()
    }

    suspend fun insert(entity: AnimeAnalysisEntity): Long = withContext(Dispatchers.IO) {
        dao.insertAnalysis(entity)
    }

    suspend fun update(entity: AnimeAnalysisEntity) = withContext(Dispatchers.IO) {
        dao.updateAnalysis(entity)
    }

    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteAnalysisById(id)
    }
}
