package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper ringan untuk mengambil 1 frame thumbnail video anime pada posisi waktu tertentu.
 * Dijalankan di thread background (Dispatchers.IO) dan menghasilkan bitmap berukuran kecil (160x90)
 * agar hemat memori pada perangkat Android low-end tanpa memutar video.
 */
object VideoThumbnailHelper {
    private const val TAG = "VideoThumbnailHelper"
    private const val THUMB_WIDTH = 160
    private const val THUMB_HEIGHT = 90

    suspend fun getFrameThumbnail(
        context: Context,
        videoUri: Uri,
        timeSec: Long
    ): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val timeUs = timeSec.coerceAtLeast(0L) * 1_000_000L

            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    THUMB_WIDTH,
                    THUMB_HEIGHT
                )
            } else {
                val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (raw != null) {
                    val scaled = try {
                        Bitmap.createScaledBitmap(raw, THUMB_WIDTH, THUMB_HEIGHT, true)
                    } catch (e: Exception) {
                        raw.recycle()
                        throw e
                    }
                    if (scaled != raw) {
                        raw.recycle()
                    }
                    scaled
                } else null
            }
            bitmap
        } catch (e: Exception) {
            Log.w(TAG, "Gagal mengekstrak thumbnail frame video pada ${timeSec}s: ${e.message}")
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }
}
