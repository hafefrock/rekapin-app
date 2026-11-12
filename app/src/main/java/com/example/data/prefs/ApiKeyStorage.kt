package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.ApiKeyItem
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface ApiKeyCipher {
    fun encrypt(plainText: String): String
    fun decrypt(encryptedText: String): String?
}

private object AndroidKeyStoreApiKeyCipher : ApiKeyCipher {
    private const val KEY_ALIAS = "rekapin_api_keys_aes_gcm"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH_BYTES = 12

    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val packed = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, packed, 0, iv.size)
        System.arraycopy(encrypted, 0, packed, iv.size, encrypted.size)
        return "v1:" + Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    override fun decrypt(encryptedText: String): String? {
        if (!encryptedText.startsWith("v1:")) return null
        return try {
            val packed = Base64.decode(encryptedText.removePrefix("v1:"), Base64.NO_WRAP)
            if (packed.size <= IV_LENGTH_BYTES) return null
            val iv = packed.copyOfRange(0, IV_LENGTH_BYTES)
            val ciphertext = packed.copyOfRange(IV_LENGTH_BYTES, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Tidak dapat membuka penyimpanan API key terenkripsi.", e)
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private const val TAG = "ApiKeyStorage"
}

class ApiKeyStorage internal constructor(
    context: Context,
    private val cipher: ApiKeyCipher = AndroidKeyStoreApiKeyCipher
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var keyDecryptFailed: Boolean = false
        private set

    init {
        migrateExistingKeyIfNeeded()
    }

    /** Migrates legacy plaintext preferences once; secrets are only deleted after encryption succeeds. */
    @Synchronized
    private fun migrateExistingKeyIfNeeded() {
        val existingEncrypted = prefs.getString(KEY_API_KEYS_ENCRYPTED, null)
        if (!existingEncrypted.isNullOrBlank()) {
            val testDecrypt = cipher.decrypt(existingEncrypted)
            if (testDecrypt != null) {
                keyDecryptFailed = false
                removeLegacyPlaintextKeys()
            } else {
                keyDecryptFailed = true
            }
            return
        }

        val legacyJson = prefs.getString(KEY_API_KEYS_JSON, null)
        if (!legacyJson.isNullOrBlank()) {
            val oldKeys = parseKeysJson(legacyJson)
            if (oldKeys.isNotEmpty()) {
                val activeId = getActiveKeyId().takeIf { id -> oldKeys.any { it.id == id } }
                    ?: oldKeys.firstOrNull { it.isActive }?.id
                    ?: oldKeys.first().id
                try {
                    saveKeysInternal(oldKeys, activeId)
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "Migrasi daftar API key ke penyimpanan terenkripsi gagal.", e)
                    return
                }
            }
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
            val initialKey = ApiKeyItem(name = "Key 1", key = keyToMigrate, isActive = true)
            try {
                saveKeysInternal(listOf(initialKey), initialKey.id)
            } catch (e: Exception) {
                Log.e(TAG, "Migrasi API key lama ke penyimpanan terenkripsi gagal.", e)
            }
        }
    }

    private fun getLegacyPlaintextKeys(): List<ApiKeyItem> {
        val legacyJson = prefs.getString(KEY_API_KEYS_JSON, null)
        if (!legacyJson.isNullOrBlank()) {
            val oldKeys = parseKeysJson(legacyJson)
            if (oldKeys.isNotEmpty()) return oldKeys
        }
        val legacyKey = prefs.getString(KEY_GEMINI_API_KEY, null)?.trim().orEmpty()
        if (legacyKey.isNotBlank()) {
            return listOf(ApiKeyItem(name = "Key 1", key = legacyKey, isActive = true))
        }
        return emptyList()
    }

    fun hasStoredKeysThatCannotBeDecrypted(): Boolean {
        val encrypted = prefs.getString(KEY_API_KEYS_ENCRYPTED, null)
        return !encrypted.isNullOrBlank() && keyDecryptFailed && getAllKeys().isEmpty()
    }

    @Synchronized
    fun getAllKeys(): List<ApiKeyItem> {
        val encrypted = prefs.getString(KEY_API_KEYS_ENCRYPTED, null)
        if (!encrypted.isNullOrBlank()) {
            val json = cipher.decrypt(encrypted)
            if (json != null) {
                keyDecryptFailed = false
                removeLegacyPlaintextKeys()
                return parseKeysJson(json)
            } else {
                keyDecryptFailed = true
                Log.e(TAG, "Gagal mendekripsi data API key dari KeyStore.")
                val legacy = getLegacyPlaintextKeys()
                if (legacy.isNotEmpty()) {
                    return legacy
                }
                return emptyList()
            }
        }

        migrateExistingKeyIfNeeded()
        val postMigrate = prefs.getString(KEY_API_KEYS_ENCRYPTED, null)
        if (!postMigrate.isNullOrBlank()) {
            val json = cipher.decrypt(postMigrate)
            if (json != null) {
                keyDecryptFailed = false
                removeLegacyPlaintextKeys()
                return parseKeysJson(json)
            } else {
                keyDecryptFailed = true
                val legacy = getLegacyPlaintextKeys()
                if (legacy.isNotEmpty()) {
                    return legacy
                }
                return emptyList()
            }
        }

        val legacy = getLegacyPlaintextKeys()
        if (legacy.isNotEmpty()) return legacy

        return emptyList()
    }

    @Synchronized
    fun addKey(name: String, key: String): ApiKeyItem {
        val currentKeys = getAllKeys().toMutableList()
        val cleanName = name.trim().ifBlank { "Key ${currentKeys.size + 1}" }
        val cleanKey = key.trim()
        val isFirst = currentKeys.isEmpty()
        val newKey = ApiKeyItem(name = cleanName, key = cleanKey, isActive = isFirst)
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
        if (currentKeys.none { it.id == id }) return false
        val updated = currentKeys.map { item -> if (item.id == id) item.copy(name = cleanName) else item }
        saveKeysInternal(updated, getActiveKeyId())
        return true
    }

    @Synchronized
    fun deleteKey(id: String): Boolean {
        val currentKeys = getAllKeys()
        if (currentKeys.none { it.id == id }) return false
        val remaining = currentKeys.filterNot { it.id == id }
        val activeId = if (getActiveKeyId() == id) remaining.firstOrNull()?.id.orEmpty() else getActiveKeyId()
        saveKeysInternal(remaining, activeId)
        return true
    }

    @Synchronized
    fun setActiveKey(id: String): Boolean {
        val currentKeys = getAllKeys()
        if (currentKeys.none { it.id == id }) return false
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

    fun getActiveKeyName(): String = getActiveKeyItem()?.name ?: "Key 1"

    fun getActiveKeyHash(): String {
        val active = getActiveKeyItem()
        return active?.keyHash ?: ApiKeyItem.computeKeyHash(getEffectiveApiKey())
    }

    fun getEffectiveApiKey(): String {
        val activeKey = getActiveKeyItem()?.key?.trim().orEmpty()
        if (activeKey.isNotBlank()) return activeKey
        val buildKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }
        return buildKey.trim().takeUnless { it.isBlank() || it == "MY_GEMINI_API_KEY" }.orEmpty()
    }

    fun getCustomApiKey(): String = getActiveKeyItem()?.key.orEmpty()

    @Synchronized
    fun saveApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        val keys = getAllKeys()
        if (keys.isEmpty()) {
            addKey("Key 1", trimmed)
        } else {
            val active = getActiveKeyItem() ?: keys.first()
            val updated = keys.map { item -> if (item.id == active.id) item.copy(key = trimmed) else item }
            saveKeysInternal(updated, active.id)
        }
    }

    fun hasApiKey(): Boolean = getEffectiveApiKey().isNotBlank()

    private fun getActiveKeyId(): String = prefs.getString(KEY_ACTIVE_KEY_ID, "") ?: ""

    private fun saveKeysInternal(keys: List<ApiKeyItem>, activeId: String) {
        val jsonArray = JSONArray()
        val resolvedActiveId = if (keys.any { it.id == activeId }) activeId else keys.firstOrNull()?.id.orEmpty()
        keys.forEach { item ->
            jsonArray.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("key", item.key)
                put("isActive", item.id == resolvedActiveId)
                put("createdAt", item.createdAt)
            })
        }

        // Encrypt before editing preferences. If encryption fails, never persist a plaintext fallback.
        val encrypted = cipher.encrypt(jsonArray.toString())
        prefs.edit()
            .putString(KEY_API_KEYS_ENCRYPTED, encrypted)
            .putString(KEY_ACTIVE_KEY_ID, resolvedActiveId)
            .remove(KEY_API_KEYS_JSON)
            .remove(KEY_GEMINI_API_KEY)
            .apply()
        keyDecryptFailed = false
    }

    private fun removeLegacyPlaintextKeys() {
        prefs.edit().remove(KEY_API_KEYS_JSON).remove(KEY_GEMINI_API_KEY).apply()
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
                    list.add(ApiKeyItem(id = id, name = name, key = key, isActive = isActive, createdAt = createdAt))
                }
            }
        } catch (_: Exception) {
            // Ignore malformed legacy or encrypted JSON without logging secret material.
        }
        return list
    }

    companion object {
        private const val TAG = "ApiKeyStorage"
        private const val PREFS_NAME = "rekapin_secure_prefs"
        private const val KEY_GEMINI_API_KEY = "custom_gemini_api_key"
        private const val KEY_API_KEYS_JSON = "custom_gemini_api_keys_json"
        private const val KEY_API_KEYS_ENCRYPTED = "custom_gemini_api_keys_encrypted"
        private const val KEY_ACTIVE_KEY_ID = "active_gemini_key_id"
    }
}
