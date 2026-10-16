package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AnimeAnalysisEntity
import com.example.data.db.AppDatabase
import com.example.data.prefs.ApiKeyStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Rekapin", appName)
    }

    @Test
    fun `apiKeyStorage saves and retrieves custom key`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = ApiKeyStorage(context)
        val testKey = "AIzaSyTestApiKey123456"
        storage.saveApiKey(testKey)

        assertEquals(testKey, storage.getCustomApiKey())
        assertEquals(testKey, storage.getEffectiveApiKey())
        assertTrue(storage.hasApiKey())
    }

    @Test
    fun `test Room database insert update and delete`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val dao = db.animeAnalysisDao()

        val entity = AnimeAnalysisEntity(
            videoUriString = "content://media/test",
            videoName = "Anime Episode 1.mp4",
            videoSizeBytes = 50_000_000L,
            durationMs = 1500_000L,
            mimeType = "video/mp4",
            intervalSeconds = 5,
            totalSegments = 9,
            completedSegmentsCount = 2,
            geminiFileRemoteName = "files/test123",
            geminiFileUri = "https://generativelanguage.googleapis.com/v1beta/files/test123",
            status = "IN_PROGRESS",
            characterSheetJson = "[]",
            timelineJson = "[]",
            segmentSummariesJson = "[]",
            narration = ""
        )

        val id = dao.insertAnalysis(entity)
        val loaded = dao.getAnalysisById(id)
        assertNotNull(loaded)
        assertEquals(2, loaded!!.completedSegmentsCount)
        assertEquals("IN_PROGRESS", loaded.status)

        // Update to completed
        dao.updateAnalysis(loaded.copy(status = "COMPLETED", completedSegmentsCount = 9))
        val updated = dao.getAnalysisById(id)
        assertEquals("COMPLETED", updated!!.status)
        assertEquals(9, updated.completedSegmentsCount)

        // Delete
        dao.deleteAnalysisById(id)
        assertNull(dao.getAnalysisById(id))

        db.close()
    }
}
