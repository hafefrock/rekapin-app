package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.prefs.ApiKeyStorage
import org.junit.Assert.assertEquals
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
}
