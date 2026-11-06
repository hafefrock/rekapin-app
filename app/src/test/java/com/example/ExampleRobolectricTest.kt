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
@Config(sdk = [35])
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

    @Test
    fun `test migration 2 to 3 adds uploadKeyHash column`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("test_migration.db")
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `anime_analyses` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `videoUriString` TEXT NOT NULL,
                                `videoName` TEXT NOT NULL,
                                `videoSizeBytes` INTEGER NOT NULL,
                                `durationMs` INTEGER NOT NULL,
                                `mimeType` TEXT NOT NULL,
                                `intervalSeconds` INTEGER NOT NULL,
                                `totalSegments` INTEGER NOT NULL,
                                `completedSegmentsCount` INTEGER NOT NULL,
                                `geminiFileRemoteName` TEXT NOT NULL,
                                `geminiFileUri` TEXT NOT NULL,
                                `status` TEXT NOT NULL,
                                `characterSheetJson` TEXT NOT NULL,
                                `timelineJson` TEXT NOT NULL,
                                `segmentSummariesJson` TEXT NOT NULL,
                                `narration` TEXT NOT NULL,
                                `lastErrorStage` TEXT NOT NULL,
                                `lastErrorMessage` TEXT NOT NULL,
                                `segmentPlanJson` TEXT NOT NULL,
                                `segmentDurationMode` TEXT NOT NULL,
                                `createdAt` INTEGER NOT NULL,
                                `updatedAt` INTEGER NOT NULL
                            )
                        """.trimIndent())
                        db.execSQL("""
                            INSERT INTO `anime_analyses` (`videoUriString`, `videoName`, `videoSizeBytes`, `durationMs`, `mimeType`, `intervalSeconds`, `totalSegments`, `completedSegmentsCount`, `geminiFileRemoteName`, `geminiFileUri`, `status`, `characterSheetJson`, `timelineJson`, `segmentSummariesJson`, `narration`, `lastErrorStage`, `lastErrorMessage`, `segmentPlanJson`, `segmentDurationMode`, `createdAt`, `updatedAt`)
                            VALUES ('uri_test', 'Ep 1.mp4', 1000, 60000, 'video/mp4', 5, 3, 2, 'remote', 'uri', 'IN_PROGRESS', '[]', '[]', '[]', '', '', '', '[]', 'AUTO', 100, 200)
                        """.trimIndent())
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )

        val db = helper.writableDatabase
        AppDatabase.MIGRATION_2_3.migrate(db)

        val cursor = db.query("SELECT uploadKeyHash, videoName FROM anime_analyses WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals("", cursor.getString(0))
        assertEquals("Ep 1.mp4", cursor.getString(1))
        cursor.close()
        db.close()
        context.deleteDatabase("test_migration.db")
    }
}
