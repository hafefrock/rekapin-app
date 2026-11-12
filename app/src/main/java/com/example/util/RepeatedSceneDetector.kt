package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** A visual similarity candidate, not a claim that two scenes are semantically identical. */
data class RepeatedSceneMatch(
    val firstStartSec: Long,
    val firstEndSec: Long,
    val secondStartSec: Long,
    val secondEndSec: Long
) {
    fun toUserMessage(): String =
        "${SceneDetector.formatTimeSeconds(firstStartSec)}-${SceneDetector.formatTimeSeconds(firstEndSec)} mirip dengan " +
            "${SceneDetector.formatTimeSeconds(secondStartSec)}-${SceneDetector.formatTimeSeconds(secondEndSec)} (kemiripan visual; tinjau manual)."
}

object RepeatedSceneDetector {
    private const val TAG = "RepeatedSceneDetector"
    private const val WIDTH = 9
    private const val HEIGHT = 8
    private const val HASH_PIXELS = WIDTH * HEIGHT
    private const val MAX_HASH_DISTANCE = 10
    private const val MIN_RUN_FRAMES = 4
    private const val MIN_SEPARATION_SEC = 30L
    private const val MAX_RESULTS = 5

    /** Samples only the selected time range, stores compact hashes, and never retains decoded bitmaps. */
    suspend fun detect(
        context: Context,
        uri: Uri,
        startSec: Long,
        endSec: Long
    ): List<RepeatedSceneMatch> = withContext(Dispatchers.IO) {
        val start = startSec.coerceAtLeast(0L)
        val end = endSec.coerceAtLeast(start)
        if (end - start < MIN_SEPARATION_SEC * 2) return@withContext emptyList()
        val span = end - start
        val stepSec = when {
            span <= 300L -> 3L
            span <= 900L -> 5L
            span <= 1800L -> 10L
            else -> 15L
        }
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<FrameFingerprint>()
        val pixelBuffer = IntArray(HASH_PIXELS)
        try {
            retriever.setDataSource(context, uri)
            var time = start
            while (time < end) {
                currentCoroutineContext().ensureActive()
                val bitmap: Bitmap? = try {
                    val timeUs = time * 1_000_000L
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, WIDTH, HEIGHT)
                    } else {
                        val full = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        if (full == null) null else {
                            val scaled = try {
                                Bitmap.createScaledBitmap(full, WIDTH, HEIGHT, true)
                            } catch (e: Exception) {
                                full.recycle()
                                throw e
                            }
                            if (scaled !== full) full.recycle()
                            scaled
                        }
                    }
                } catch (_: Exception) {
                    null
                }
                if (bitmap != null) {
                    try {
                        bitmap.getPixels(pixelBuffer, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
                        frames += FrameFingerprint(time, dHash(pixelBuffer))
                    } finally {
                        bitmap.recycle()
                    }
                }
                time += stepSec
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Pemindaian adegan berulang lokal gagal: ${e.message}")
            return@withContext emptyList()
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        findRepeatedRuns(frames, stepSec)
    }

    /** Pure helper for bounded O(n²) sequence comparison and unit tests. */
    internal fun findRepeatedRuns(
        frames: List<FrameFingerprint>,
        stepSec: Long
    ): List<RepeatedSceneMatch> {
        if (frames.size < MIN_RUN_FRAMES * 2) return emptyList()
        val usedAlignedPairs = mutableSetOf<Long>()
        val candidates = mutableListOf<Pair<Int, RepeatedSceneMatch>>()
        for (first in 0 until frames.size - MIN_RUN_FRAMES) {
            for (second in first + MIN_RUN_FRAMES until frames.size - MIN_RUN_FRAMES + 1) {
                if (frames[second].timeSec - frames[first].timeSec < MIN_SEPARATION_SEC) continue
                val pairKey = key(first, second)
                if (pairKey in usedAlignedPairs || !similar(frames[first].hash, frames[second].hash)) continue
                var length = 0
                while (first + length < frames.size && second + length < frames.size &&
                    similar(frames[first + length].hash, frames[second + length].hash)
                ) {
                    length++
                }
                if (length < MIN_RUN_FRAMES) continue
                for (offset in 0 until length) usedAlignedPairs += key(first + offset, second + offset)
                val firstEnd = frames[first + length - 1].timeSec + stepSec
                val secondEnd = frames[second + length - 1].timeSec + stepSec
                candidates += length to RepeatedSceneMatch(
                    frames[first].timeSec,
                    firstEnd,
                    frames[second].timeSec,
                    secondEnd
                )
            }
        }
        return candidates.sortedByDescending { it.first }
            .map { it.second }
            .distinctBy { listOf(it.firstStartSec, it.secondStartSec) }
            .take(MAX_RESULTS)
    }

    internal fun isInformative(hash: Long): Boolean {
        val ones = java.lang.Long.bitCount(hash)
        return ones in 8..56
    }

    private fun similar(first: Long, second: Long): Boolean =
        isInformative(first) && isInformative(second) &&
            java.lang.Long.bitCount(first xor second) <= MAX_HASH_DISTANCE

    private fun key(first: Int, second: Int): Long = (first.toLong() shl 32) or (second.toLong() and 0xffffffffL)

    private fun dHash(pixels: IntArray): Long {
        var result = 0L
        var bit = 0
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH - 1) {
                val left = luminance(pixels[y * WIDTH + x])
                val right = luminance(pixels[y * WIDTH + x + 1])
                if (left > right) result = result or (1L shl bit)
                bit++
            }
        }
        return result
    }

    private fun luminance(pixel: Int): Int {
        val red = (pixel shr 16) and 0xff
        val green = (pixel shr 8) and 0xff
        val blue = pixel and 0xff
        return (red * 77 + green * 150 + blue * 29) shr 8
    }
}
