package com.example.data.model

import java.security.MessageDigest
import java.util.UUID

data class ApiKeyItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val key: String,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val maskedKey: String
        get() = maskKey(key)

    val keyHash: String
        get() = computeKeyHash(key)

    companion object {
        fun maskKey(rawKey: String): String {
            val trimmed = rawKey.trim()
            if (trimmed.length <= 8) {
                return if (trimmed.length > 4) "${trimmed.take(2)}...${trimmed.takeLast(2)}" else "****"
            }
            return "${trimmed.take(4)}...${trimmed.takeLast(4)}"
        }

        fun computeKeyHash(rawKey: String): String {
            val trimmed = rawKey.trim()
            if (trimmed.isBlank()) return ""
            return try {
                val digest = MessageDigest.getInstance("SHA-256").digest(trimmed.toByteArray())
                digest.take(4).joinToString("") { "%02x".format(it) } // 8 hex characters, e.g. "a1b2c3d4"
            } catch (_: Exception) {
                trimmed.hashCode().toString(16).takeLast(8)
            }
        }
    }
}
