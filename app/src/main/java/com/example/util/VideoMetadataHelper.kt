package com.example.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.model.SelectedVideo
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale

internal object VideoMetadataHelper {
    private const val TAG = "RekapinVideoMeta"

    fun loadVideoMetadata(context: Context, uri: Uri): SelectedVideo {
        var displayName = "video_anime.mp4"
        var sizeBytes = 0L
        val contentResolver = context.contentResolver

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)?.let { displayName = it }
                    }
                    if (sizeIndex != -1) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal query cursor metadata video (${e.javaClass.simpleName}).")
        }

        val mimeType = contentResolver.getType(uri) ?: "video/mp4"

        var durationMs = 0L
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Gagal membaca durasi video via MediaMetadataRetriever (${e.javaClass.simpleName}).")
        } finally {
            try {
                retriever.release()
            } catch (releaseError: Exception) {
                Log.w(TAG, "Gagal melepas MediaMetadataRetriever (${releaseError.javaClass.simpleName}).")
            }
        }

        if (sizeBytes <= 0L) {
            try {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                    if (descriptor.length > 0L) sizeBytes = descriptor.length
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gagal membaca ukuran video dari file descriptor (${e.javaClass.simpleName}).")
            }
        }

        val fingerprint = calculateVideoFingerprint(context, uri, sizeBytes)
        return SelectedVideo(
            uri = uri,
            displayName = displayName,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            mimeType = mimeType,
            fingerprint = fingerprint
        )
    }

    /**
     * Hashes small samples from the beginning and end of seekable local content.
     * This avoids reading an entire multi-GB video just to identify it. Providers
     * backed by pipes or unknown-length streams may not support seeking; in that
     * case an empty fingerprint is returned and the metadata policy remains usable.
     */
    fun calculateVideoFingerprint(context: Context, uri: Uri, knownSizeBytes: Long): String {
        if (knownSizeBytes <= 0L) return ""
        return try {
            val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r") ?: return ""
            descriptor.use { asset ->
                val length = asset.length.takeIf { it > 0L } ?: knownSizeBytes
                if (length <= 0L) return ""
                FileInputStream(asset.fileDescriptor).use { input ->
                    val channel = input.channel
                    val sampleSize = minOf(64 * 1024L, length).toInt()
                    val firstBuffer = ByteBuffer.allocate(sampleSize)
                    channel.position(asset.startOffset)
                    while (firstBuffer.hasRemaining()) {
                        val count = channel.read(firstBuffer)
                        if (count <= 0) break
                    }
                    val first = firstBuffer.array().copyOf(firstBuffer.position())

                    val lastBuffer = ByteBuffer.allocate(sampleSize)
                    channel.position(asset.startOffset + (length - sampleSize))
                    while (lastBuffer.hasRemaining()) {
                        val count = channel.read(lastBuffer)
                        if (count <= 0) break
                    }
                    val last = lastBuffer.array().copyOf(lastBuffer.position())

                    if (first.isEmpty() || last.isEmpty()) return ""
                    val digest = MessageDigest.getInstance("SHA-256")
                    digest.update(length.toString().toByteArray(Charsets.UTF_8))
                    digest.update(first)
                    digest.update(last)
                    digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Fingerprint video tidak tersedia untuk sumber ini: ${e.javaClass.simpleName}")
            ""
        }
    }

    fun formatSeconds(sec: Long): String {
        val m = sec / 60
        val s = sec % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }
}
