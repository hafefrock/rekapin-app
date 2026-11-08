package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.prefs.ApiKeyStorage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
  @Test
  fun useAppContext() {
    // Context of the app under test.
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
  }

  @Test
  fun apiKeyStorageEncryptsUserKeyWithAndroidKeystore() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val prefs = context.getSharedPreferences("rekapin_secure_prefs", android.content.Context.MODE_PRIVATE)
    prefs.edit().clear().commit()

    val storage = ApiKeyStorage(context)
    val secret = "AIzaSyInstrumentedSecretKey"
    storage.saveApiKey(secret)

    assertEquals(secret, storage.getCustomApiKey())
    assertFalse(prefs.getString("custom_gemini_api_keys_encrypted", "").orEmpty().contains(secret))
    assertNull(prefs.getString("custom_gemini_api_keys_json", null))
    assertNull(prefs.getString("custom_gemini_api_key", null))
    prefs.edit().clear().commit()
  }

  @Test
  fun apiKeyStorageMigratesLegacyPlaintextPreference() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val prefs = context.getSharedPreferences("rekapin_secure_prefs", android.content.Context.MODE_PRIVATE)
    prefs.edit().clear().putString("custom_gemini_api_key", "AIzaSyLegacyInstrumentedKey").commit()

    val storage = ApiKeyStorage(context)

    assertEquals("AIzaSyLegacyInstrumentedKey", storage.getCustomApiKey())
    assertNull(prefs.getString("custom_gemini_api_key", null))
    assertFalse(prefs.getString("custom_gemini_api_keys_encrypted", "").orEmpty().contains("AIzaSyLegacyInstrumentedKey"))
    prefs.edit().clear().commit()
  }
}
