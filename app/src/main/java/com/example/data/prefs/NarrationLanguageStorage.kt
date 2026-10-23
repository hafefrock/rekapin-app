package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.NarrationLanguage

class NarrationLanguageStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getLanguage(): NarrationLanguage {
        val saved = prefs.getString(KEY_LANGUAGE, null)
        return NarrationLanguage.fromDisplayName(saved)
    }

    @Synchronized
    fun getLanguageDisplayName(): String {
        return getLanguage().displayName
    }

    @Synchronized
    fun setLanguage(language: NarrationLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.displayName).apply()
    }

    @Synchronized
    fun setLanguage(displayName: String) {
        setLanguage(NarrationLanguage.fromDisplayName(displayName))
    }

    companion object {
        private const val PREFS_NAME = "rekapin_language_prefs"
        private const val KEY_LANGUAGE = "narration_language"
    }
}
