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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sqrt

data class SceneTimeSpan(
    val startSec: Long,
    val endSec: Long
) {
    val durationSec: Long get() = endSec - startSec

    val formattedRange: String
        get() = "${SceneDetector.formatTimeSeconds(startSec)} - ${SceneDetector.formatTimeSeconds(endSec)}"
}

object SceneDetector {
    private const val TAG = "SceneDetector"
    private const val FRAME_WIDTH = 64
    private const val FRAME_HEIGHT = 36
    private const val TOTAL_PIXELS = FRAME_WIDTH * FRAME_HEIGHT // 2304 piksel
    private const val MIN_SCENE_DISTANCE_SEC = 1.5 // Jarak minimum antar potongan adegan
    private const val MIN_ABSOLUTE_THRESHOLD = 0.15f // Ambang batas minimum perbedaan visual (15%)

    fun formatTimeSeconds(totalSec: Long): String {
        val safeSec = totalSec.coerceAtLeast(0L)
        val m = safeSec / 60
        val s = safeSec % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    /**
     * Mendeteksi titik pergantian adegan lokal secara efisien (hemat memori untuk perangkat spesifikasi rendah).
     * Berjalan di Dispatchers.IO dan mendukung pembatalan coroutine secara responsif.
     */
    suspend fun detectSceneCuts(
        context: Context,
        videoUri: Uri,
        startSec: Long,
        endSec: Long,
        maxSceneDurationSec: Int = 5,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): List<SceneTimeSpan> = withContext(Dispatchers.IO) {
        val actualStartSec = startSec.coerceAtLeast(0L)
        val actualEndSec = endSec.coerceAtLeast(actualStartSec + 1L)
        val totalSpanSec = actualEndSec - actualStartSec

        Log.i(TAG, "Memulai deteksi adegan rentang ${actualStartSec}s - ${actualEndSec}s (maksimal $maxSceneDurationSec s/adegan)")
        onProgress(0f, "Mempersiapkan deteksi adegan...")

        val retriever = MediaMetadataRetriever()
        val rawDiffs = mutableListOf<Pair<Double, Float>>() // Pair<Waktu detik, Nilai selisih visual>

        try {
            retriever.setDataSource(context, videoUri)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menginisialisasi MediaMetadataRetriever: ${e.message}")
            // Fallback aman: jika gagal membaca frame video, bagi berdasarkan batas maksimum durasi
            return@withContext splitSpansFallback(actualStartSec, actualEndSec, maxSceneDurationSec)
        }

        val pixelBuffer = IntArray(TOTAL_PIXELS)
        val prevGray = ByteArray(TOTAL_PIXELS)
        val currGray = ByteArray(TOTAL_PIXELS)
        var hasPreviousFrame = false

        try {
            // Sampling 2 frame per detik (tiap 500ms = 500_000 mikrodetik)
            val stepSec = 0.5
            val totalSteps = ((totalSpanSec / stepSec).toInt()).coerceAtLeast(1)
            var currentStep = 0

            var t = actualStartSec.toDouble()
            while (t < actualEndSec) {
                currentCoroutineContext().ensureActive()

                val timeUs = (t * 1_000_000.0).roundToLong()
                val bitmap: Bitmap? = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(
                            timeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST,
                            FRAME_WIDTH,
                            FRAME_HEIGHT
                        )
                    } else {
                        val fullBm = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        if (fullBm != null) {
                            val scaled = Bitmap.createScaledBitmap(fullBm, FRAME_WIDTH, FRAME_HEIGHT, true)
                            if (scaled != fullBm) fullBm.recycle()
                            scaled
                        } else null
                    }
                } catch (e: Exception) {
                    null
                }

                if (bitmap != null) {
                    try {
                        bitmap.getPixels(pixelBuffer, 0, FRAME_WIDTH, 0, 0, FRAME_WIDTH, FRAME_HEIGHT)
                    } finally {
                        bitmap.recycle() // Recycle segera untuk menjaga konsumsi RAM seminimal mungkin
                    }

                    // Konversi piksel ke intensitas grayscale
                    for (i in 0 until TOTAL_PIXELS) {
                        val p = pixelBuffer[i]
                        val r = (p shr 16) and 0xFF
                        val g = (p shr 8) and 0xFF
                        val b = p and 0xFF
                        currGray[i] = ((r * 77 + g * 150 + b * 29) shr 8).toByte()
                    }

                    if (hasPreviousFrame) {
                        var absSum = 0L
                        for (i in 0 until TOTAL_PIXELS) {
                            val v1 = currGray[i].toInt() and 0xFF
                            val v2 = prevGray[i].toInt() and 0xFF
                            absSum += abs(v1 - v2)
                        }
                        val diffScore = absSum.toFloat() / (TOTAL_PIXELS * 255f)
                        rawDiffs.add(Pair(t, diffScore))
                    }

                    System.arraycopy(currGray, 0, prevGray, 0, TOTAL_PIXELS)
                    hasPreviousFrame = true
                }

                currentStep++
                if (currentStep % 10 == 0 || currentStep == totalSteps) {
                    val progress = (currentStep.toFloat() / totalSteps).coerceIn(0f, 1f)
                    val percentInt = (progress * 100).toInt()
                    onProgress(progress, "Mendeteksi adegan... ($percentInt%)")
                }

                t += stepSec
            }
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        // Hitung ambang batas adaptif (Rata-rata + 2 * Standar Deviasi)
        val cutPoints = mutableListOf<Long>()
        if (rawDiffs.isNotEmpty()) {
            val mean = rawDiffs.map { it.second }.average()
            val variance = rawDiffs.map { (it.second - mean) * (it.second - mean) }.average()
            val stdDev = sqrt(variance)
            val adaptiveThreshold = maxOf(MIN_ABSOLUTE_THRESHOLD, (mean + 2.0 * stdDev).toFloat())

            Log.i(TAG, "Statistik selisih visual: Rata-rata=${"%.4f".format(mean)}, StdDev=${"%.4f".format(stdDev)}, Ambang=${"%.4f".format(adaptiveThreshold)}")

            var lastCutTime = actualStartSec.toDouble()
            for ((timeSec, diff) in rawDiffs) {
                if (diff >= adaptiveThreshold && (timeSec - lastCutTime) >= MIN_SCENE_DISTANCE_SEC) {
                    val cutSec = timeSec.roundToLong().coerceIn(actualStartSec + 1L, actualEndSec - 1L)
                    if (!cutPoints.contains(cutSec)) {
                        cutPoints.add(cutSec)
                        lastCutTime = timeSec
                    }
                }
            }
        }

        Log.i(TAG, "Terdeteksi ${cutPoints.size} titik pergantian gambar alami: $cutPoints")

        // Gabungkan dan pecah adegan yang lebih lama dari batas maksimum durasi adegan
        val finalSpans = buildFinalSpans(
            rawCutPoints = cutPoints,
            startSec = actualStartSec,
            endSec = actualEndSec,
            maxDuration = maxSceneDurationSec
        )

        Log.i(TAG, "Hasil akhir rentang adegan: ${finalSpans.size} adegan")
        finalSpans
    }

    /**
     * Membangun daftar rentang adegan lengkap (SceneTimeSpan) dari titik potong terdeteksi
     * dengan pemecahan adegan panjang berdasarkan batas maksimum durasi.
     */
    private fun buildFinalSpans(
        rawCutPoints: List<Long>,
        startSec: Long,
        endSec: Long,
        maxDuration: Int
    ): List<SceneTimeSpan> {
        val cuts = (listOf(startSec) + rawCutPoints + listOf(endSec)).distinct().sorted()
        val result = mutableListOf<SceneTimeSpan>()
        val maxDurLong = maxDuration.toLong().coerceAtLeast(2L)

        for (i in 0 until cuts.size - 1) {
            var segStart = cuts[i]
            val segEnd = cuts[i + 1]

            while (segEnd - segStart > maxDurLong) {
                val next = segStart + maxDurLong
                // Jika sisa adegan terlalu pendek (< 1 detik), gabungkan ke adegan terakhir
                if (segEnd - next < 1L) {
                    break
                }
                result.add(SceneTimeSpan(startSec = segStart, endSec = next))
                segStart = next
            }
            if (segStart < segEnd) {
                result.add(SceneTimeSpan(startSec = segStart, endSec = segEnd))
            }
        }
        return if (result.isEmpty()) {
            listOf(SceneTimeSpan(startSec, endSec))
        } else {
            result
        }
    }

    private fun splitSpansFallback(startSec: Long, endSec: Long, maxDuration: Int): List<SceneTimeSpan> {
        val result = mutableListOf<SceneTimeSpan>()
        val maxDurLong = maxDuration.toLong().coerceAtLeast(2L)
        var curr = startSec
        while (curr < endSec) {
            val next = minOf(curr + maxDurLong, endSec)
            result.add(SceneTimeSpan(curr, next))
            curr = next
        }
        return result
    }

    /**
     * Mengambil daftar adegan yang berada dalam rentang segmen tertentu [segStartSec, segEndSec],
     * diformat sebagai daftar string "MM:SS - MM:SS".
     * Timestamp dipastikan berurutan, tidak ada celah, dan tidak ada tumpang tindih.
     */
    fun getScenesForSegment(
        allScenes: List<SceneTimeSpan>,
        segStartSec: Long,
        segEndSec: Long
    ): List<String> {
        if (allScenes.isEmpty()) {
            return listOf("${formatTimeSeconds(segStartSec)} - ${formatTimeSeconds(segEndSec)}")
        }

        // Filter adegan yang bertumpang tindih dengan rentang segmen ini
        val overlapping = allScenes.filter { span ->
            span.endSec > segStartSec && span.startSec < segEndSec
        }

        if (overlapping.isEmpty()) {
            return listOf("${formatTimeSeconds(segStartSec)} - ${formatTimeSeconds(segEndSec)}")
        }

        val result = mutableListOf<String>()
        var currentStart = segStartSec

        for (i in overlapping.indices) {
            val span = overlapping[i]
            val spanStart = maxOf(segStartSec, span.startSec)
            val spanEnd = minOf(segEndSec, span.endSec)

            // Pastikan tidak ada celah dari currentStart
            val actualStart = currentStart
            val actualEnd = if (i == overlapping.lastIndex) segEndSec else maxOf(actualStart + 1L, spanEnd)

            if (actualStart < actualEnd) {
                result.add("${formatTimeSeconds(actualStart)} - ${formatTimeSeconds(actualEnd)}")
                currentStart = actualEnd
            }
        }

        // Jika masih ada sisa sampai segEndSec
        if (currentStart < segEndSec) {
            result.add("${formatTimeSeconds(currentStart)} - ${formatTimeSeconds(segEndSec)}")
        }

        return result.ifEmpty {
            listOf("${formatTimeSeconds(segStartSec)} - ${formatTimeSeconds(segEndSec)}")
        }
    }
}
