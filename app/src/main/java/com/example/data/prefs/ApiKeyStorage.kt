package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.example.data.model.ApiKeyItem
import org.json.JSONArray
import org.json.JSONObject

class ApiKeyStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateExistingKeyIfNeeded()
    }

    /**
     * Migrasi key lama dari KEY_GEMINI_API_KEY atau BuildConfig menjadi "Key 1" dan aktif.
     */
    @Synchronized
    private fun migrateExistingKeyIfNeeded() {
        val keysJson = prefs.getString(KEY_API_KEYS_JSON, null)
        if (!keysJson.isNullOrBlank()) {
            return
        }

        val existingCustomKey = prefs.getString(KEY_GEMINI_API_KEY, "")?.trim().orEmpty()
        val buildKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }.trim()

        val keyToMigrate = when {
            existingCustomKey.isNotBlank() -> existingCustomKey
            buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY" -> buildKey
            else -> ""
        }

        if (keyToMigrate.isNotBlank()) {
            val initialKey = ApiKeyItem(
                name = "Key 1",
                key = keyToMigrate,
                isActive = true
            )
            saveKeysInternal(listOf(initialKey), initialKey.id)
        }
    }

    @Synchronized
    fun getAllKeys(): List<ApiKeyItem> {
        val jsonStr = prefs.getString(KEY_API_KEYS_JSON, null)
        if (jsonStr.isNullOrBlank()) {
            migrateExistingKeyIfNeeded()
            val recheck = prefs.getString(KEY_API_KEYS_JSON, null)
            if (recheck.isNullOrBlank()) return emptyList()
            return parseKeysJson(recheck)
        }
        return parseKeysJson(jsonStr)
    }

    @Synchronized
    fun addKey(name: String, key: String): ApiKeyItem {
        val currentKeys = getAllKeys().toMutableList()
        val cleanName = name.trim().ifBlank { "Key ${currentKeys.size + 1}" }
        val cleanKey = key.trim()

        val isFirst = currentKeys.isEmpty()
        val newKey = ApiKeyItem(
            name = cleanName,
            key = cleanKey,
            isActive = isFirst
        )
        currentKeys.add(newKey)
        val activeId = if (isFirst) newKey.id else getActiveKeyId()
        saveKeysInternal(currentKeys, activeId)
        return newKey
    }

    @Synchronized
    fun renameKey(id: String, newName: String): Boolean {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return false

        val currentKeys = getAllKeys()
        val updated = currentKeys.map { item ->
            if (item.id == id) item.copy(name = cleanName) else item
        }
        saveKeysInternal(updated, getActiveKeyId())
        return true
    }

    @Synchronized
    fun deleteKey(id: String): Boolean {
        val currentKeys = getAllKeys()
        val remaining = currentKeys.filterNot { it.id == id }
        var activeId = getActiveKeyId()

        if (activeId == id) {
            // Jika key aktif yang dihapus, alihkan aktif ke key pertama yang tersisa
            activeId = remaining.firstOrNull()?.id ?: ""
        }

        saveKeysInternal(remaining, activeId)
        return true
    }

    @Synchronized
    fun setActiveKey(id: String): Boolean {
        val currentKeys = getAllKeys()
        val exists = currentKeys.any { it.id == id }
        if (!exists) return false

        saveKeysInternal(currentKeys, id)
        return true
    }

    @Synchronized
    fun getActiveKeyItem(): ApiKeyItem? {
        val keys = getAllKeys()
        if (keys.isEmpty()) return null
        val activeId = getActiveKeyId()
        return keys.firstOrNull { it.id == activeId } ?: keys.firstOrNull()
    }

    fun getActiveKeyName(): String {
        return getActiveKeyItem()?.name ?: "Key 1"
    }

    fun getActiveKeyHash(): String {
        val active = getActiveKeyItem()
        return active?.keyHash ?: ApiKeyItem.computeKeyHash(getEffectiveApiKey())
    }

    fun getEffectiveApiKey(): String {
        val activeKey = getActiveKeyItem()?.key?.trim().orEmpty()
        if (activeKey.isNotBlank()) {
            return activeKey
        }
        val buildKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }
        if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") {
            return buildKey.trim()
        }
        return ""
    }

    fun getCustomApiKey(): String {
        return getActiveKeyItem()?.key ?: prefs.getString(KEY_GEMINI_API_KEY, "")?.trim().orEmpty()
    }

    fun saveApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        val keys = getAllKeys()
        if (keys.isEmpty()) {
            addKey("Key 1", trimmed)
        } else {
            val active = getActiveKeyItem() ?: keys.first()
            val updated = keys.map { item ->
                if (item.id == active.id) item.copy(key = trimmed) else item
            }
            saveKeysInternal(updated, active.id)
        }
    }

    fun hasApiKey(): Boolean {
        return getEffectiveApiKey().isNotBlank()
    }

    private fun getActiveKeyId(): String {
        return prefs.getString(KEY_ACTIVE_KEY_ID, "") ?: ""
    }

    private fun saveKeysInternal(keys: List<ApiKeyItem>, activeId: String) {
        val jsonArray = JSONArray()
        val resolvedActiveId = if (keys.any { it.id == activeId }) activeId else keys.firstOrNull()?.id ?: ""

        keys.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("key", item.key)
                put("isActive", item.id == resolvedActiveId)
                put("createdAt", item.createdAt)
            }
            jsonArray.put(obj)
        }

        val activeKeyString = keys.firstOrNull { it.id == resolvedActiveId }?.key?.trim().orEmpty()

        prefs.edit()
            .putString(KEY_API_KEYS_JSON, jsonArray.toString())
            .putString(KEY_ACTIVE_KEY_ID, resolvedActiveId)
            .putString(KEY_GEMINI_API_KEY, activeKeyString)
            .apply()
    }

    private fun parseKeysJson(jsonStr: String): List<ApiKeyItem> {
        val list = mutableListOf<ApiKeyItem>()
        val activeId = getActiveKeyId()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", "")
                val name = obj.optString("name", "Key ${i + 1}")
                val key = obj.optString("key", "")
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                val isActive = if (activeId.isNotBlank()) id == activeId else obj.optBoolean("isActive", i == 0)

                if (id.isNotBlank() && key.isNotBlank()) {
                    list.add(
                        ApiKeyItem(
                            id = id,
                            name = name,
                            key = key,
                            isActive = isActive,
                            createdAt = createdAt
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // fallback gracefully
        }
        return list
    }

    companion object {
        private const val PREFS_NAME = "rekapin_secure_prefs"
        private const val KEY_GEMINI_API_KEY = "custom_gemini_api_key"
        private const val KEY_API_KEYS_JSON = "custom_gemini_api_keys_json"
        private const val KEY_ACTIVE_KEY_ID = "active_gemini_key_id"
    }
}
