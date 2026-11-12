package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Compact local visual fingerprints sampled only from the likely OP/ED windows. */
data class FrameFingerprint(val timeSec: Long, val hash: Long)

data class OpeningEndingFingerprint(
    val openingFrames: List<FrameFingerprint>,
    val endingFrames: List<FrameFingerprint>
)

data class OpeningEndingSkip(
    val openingEndSec: Long? = null,
    val endingStartSec: Long? = null
)

object OpeningEndingDetector {
    private const val WINDOW_SECONDS = 180L
    private const val SAMPLE_STEP_SECONDS = 3L
    private const val HASH_WIDTH = 9
    private const val HASH_HEIGHT = 8
    private const val MIN_MATCH_FRAMES = 8
    private const val MAX_HASH_DISTANCE = 9

    /** Extracts small grayscale dHash signatures; each returned Bitmap is recycled immediately. */
    suspend fun capture(
        context: Context,
        videoUri: Uri,
        durationSec: Long
    ): OpeningEndingFingerprint? = withContext(Dispatchers.IO) {
        val safeDuration = durationSec.coerceAtLeast(1L)
        val openingEnd = minOf(WINDOW_SECONDS, safeDuration)
        val endingStart = (safeDuration - WINDOW_SECONDS).coerceAtLeast(0L)
        val openingTimes = (0L until openingEnd step SAMPLE_STEP_SECONDS).toList()
        val endingTimes = (endingStart until safeDuration step SAMPLE_STEP_SECONDS).toList()
        val allTimes = (openingTimes + endingTimes).distinct().sorted()
        val retriever = MediaMetadataRetriever()
        val framesByTime = mutableMapOf<Long, FrameFingerprint>()

        try {
            retriever.setDataSource(context, videoUri)
            fun fingerprintAt(timeSec: Long): FrameFingerprint? {
                val bitmap = try {
                    val timeUs = timeSec * 1_000_000L
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, HASH_WIDTH, HASH_HEIGHT)
                    } else {
                        val full = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: return null
                        val scaled = try {
                            Bitmap.createScaledBitmap(full, HASH_WIDTH, HASH_HEIGHT, true)
                        } catch (e: Exception) {
                            full.recycle()
                            throw e
                        }
                        if (scaled !== full) full.recycle()
                        scaled
                    }
                } catch (_: Exception) {
                    null
                } ?: return null
                return try {
                    FrameFingerprint(timeSec, dHash(bitmap))
                } finally {
                    bitmap.recycle()
                }
            }

            for (time in allTimes) {
                currentCoroutineContext().ensureActive()
                fingerprintAt(time)?.let { framesByTime[time] = it }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            return@withContext null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
        val opening = openingTimes.mapNotNull(framesByTime::get)
        val ending = endingTimes.mapNotNull(framesByTime::get)
        if (opening.size < MIN_MATCH_FRAMES && ending.size < MIN_MATCH_FRAMES) null
        else OpeningEndingFingerprint(opening, ending)
    }

    internal fun isInformative(hash: Long): Boolean {
        val ones = java.lang.Long.bitCount(hash)
        return ones in 8..56
    }

    /** Finds a long, ordered run of visually similar frames in both likely OP and ED windows. */
    fun findSkips(
        template: OpeningEndingFingerprint,
        current: OpeningEndingFingerprint,
        currentDurationSec: Long
    ): OpeningEndingSkip {
        val opening = longestOrderedRun(template.openingFrames, current.openingFrames)
            ?.takeIf { run ->
                (run.second - run.first + 1 >= MIN_MATCH_FRAMES) &&
                    (run.first..run.second).count { isInformative(current.openingFrames[it].hash) } >= MIN_MATCH_FRAMES
            }
        val ending = longestOrderedRun(template.endingFrames, current.endingFrames)
            ?.takeIf { run ->
                (run.second - run.first + 1 >= MIN_MATCH_FRAMES) &&
                    (run.first..run.second).count { isInformative(current.endingFrames[it].hash) } >= MIN_MATCH_FRAMES
            }

        val openingEndSec = opening?.let { current.openingFrames[it.second].timeSec + SAMPLE_STEP_SECONDS }
            ?.coerceAtMost(currentDurationSec)
        val endingStartSec = ending?.let { current.endingFrames[it.first].timeSec }
            ?.coerceAtLeast(0L)

        return if (openingEndSec != null && endingStartSec != null && endingStartSec <= openingEndSec) {
            OpeningEndingSkip()
        } else {
            OpeningEndingSkip(openingEndSec, endingStartSec)
        }
    }

    /** Pure helper requires consecutive current frames and increasing template order. */
    internal fun longestOrderedRun(
        template: List<FrameFingerprint>,
        current: List<FrameFingerprint>
    ): Pair<Int, Int>? {
        if (template.isEmpty() || current.isEmpty()) return null
        var previous = IntArray(template.size)
        var bestLength = 0
        var bestCurrentEnd = -1
        for (currentIndex in current.indices) {
            val next = IntArray(template.size)
            var bestEarlierTemplateRun = 0
            val hasContiguousPreviousFrame = currentIndex > 0 &&
                current[currentIndex].timeSec - current[currentIndex - 1].timeSec == SAMPLE_STEP_SECONDS
            val currentHash = current[currentIndex].hash
            val currentInformative = isInformative(currentHash)
            for (templateIndex in template.indices) {
                if (hasContiguousPreviousFrame && templateIndex > 0) {
                    bestEarlierTemplateRun = maxOf(bestEarlierTemplateRun, previous[templateIndex - 1])
                }
                val templateHash = template[templateIndex].hash
                if (currentInformative && isInformative(templateHash)) {
                    val distance = java.lang.Long.bitCount(templateHash xor currentHash)
                    if (distance <= MAX_HASH_DISTANCE) {
                        next[templateIndex] = bestEarlierTemplateRun + 1
                        if (next[templateIndex] > bestLength) {
                            bestLength = next[templateIndex]
                            bestCurrentEnd = currentIndex
                        }
                    }
                }
            }
            previous = next
        }
        if (bestLength == 0) return null
        return (bestCurrentEnd - bestLength + 1) to bestCurrentEnd
    }

    private fun dHash(bitmap: Bitmap): Long {
        val pixels = IntArray(HASH_WIDTH * HASH_HEIGHT)
        bitmap.getPixels(pixels, 0, HASH_WIDTH, 0, 0, HASH_WIDTH, HASH_HEIGHT)
        var result = 0L
        var bit = 0
        for (y in 0 until HASH_HEIGHT) {
            for (x in 0 until HASH_WIDTH - 1) {
                val left = luminance(pixels[y * HASH_WIDTH + x])
                val right = luminance(pixels[y * HASH_WIDTH + x + 1])
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
