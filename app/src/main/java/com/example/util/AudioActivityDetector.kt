package com.example.util

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.sqrt

/** Locally estimated voice-like activity windows; these are hints, not a transcript. */
data class SpeechActivitySpan(val startSec: Long, val endSec: Long)

object AudioActivityDetector {
    private const val TAG = "AudioActivity"
    private const val WINDOW_SECONDS = 0.5
    private const val MIN_RMS = 0.012
    private const val MIN_ZERO_CROSSING_RATE = 0.008
    private const val MAX_ZERO_CROSSING_RATE = 0.35

    /** Decodes only [startSec, endSec], processes PCM incrementally, and never retains PCM data. */
    suspend fun detect(
        context: Context,
        uri: Uri,
        startSec: Long,
        endSec: Long
    ): List<SpeechActivitySpan> = withContext(Dispatchers.IO) {
        if (endSec <= startSec) return@withContext emptyList()
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrack = -1
            var inputFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrack = index
                    inputFormat = format
                    break
                }
            }
            if (audioTrack < 0 || inputFormat == null) return@withContext emptyList()
            extractor.selectTrack(audioTrack)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext emptyList()
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()
            extractor.seekTo(startSec * 1_000_000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val windowCount = ceil((endSec - startSec) / WINDOW_SECONDS).toInt().coerceAtLeast(1)
            val sumSquares = DoubleArray(windowCount)
            val crossingCounts = IntArray(windowCount)
            val sampleCounts = IntArray(windowCount)
            var sampleRate = inputFormat.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            var channelCount = inputFormat.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 1).coerceAtLeast(1)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var inputDone = false
            var outputDone = false
            val info = MediaCodec.BufferInfo()
            var previousSignByWindow = IntArray(windowCount)
            var hasPreviousByWindow = BooleanArray(windowCount)
            val startUs = startSec * 1_000_000L
            val endUs = endSec * 1_000_000L

            val segmentDurationMs = (endSec - startSec) * 1000L
            val maxDurationMs = maxOf(30_000L, 3 * segmentDurationMs)
            val startTimeMs = System.currentTimeMillis()
            var lastOutputTimeMs = System.currentTimeMillis()

            while (!outputDone) {
                currentCoroutineContext().ensureActive()
                val now = System.currentTimeMillis()
                if (now - startTimeMs > maxDurationMs || now - lastOutputTimeMs > 10_000L) {
                    Log.w(TAG, "Audio decode timeout reached (${now - startTimeMs}ms, last output ${now - lastOutputTimeMs}ms ago); releasing codec and returning emptyList.")
                    return@withContext emptyList()
                }
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        val sampleTime = extractor.sampleTime
                        if (inputBuffer == null || sampleTime < 0L || sampleTime >= endUs) {
                            codec.queueInputBuffer(inputIndex, 0, 0, maxOf(0L, sampleTime), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val size = extractor.readSampleData(inputBuffer, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, sampleTime, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, size, sampleTime, extractor.sampleFlags)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        lastOutputTimeMs = System.currentTimeMillis()
                        val format = codec.outputFormat
                        sampleRate = format.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        channelCount = format.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, channelCount).coerceAtLeast(1)
                        pcmEncoding = format.getIntegerOrDefault(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                    }
                    outputIndex >= 0 -> {
                        lastOutputTimeMs = System.currentTimeMillis()
                        if (info.size > 0) {
                            val output = codec.getOutputBuffer(outputIndex)
                            if (output != null) {
                                output.position(info.offset)
                                output.limit(info.offset + info.size)
                                val bytes = ByteArray(info.size)
                                output.get(bytes)
                                accumulateWindowStats(
                                    bytes = bytes,
                                    presentationTimeUs = info.presentationTimeUs,
                                    startUs = startUs,
                                    endUs = endUs,
                                    sampleRate = sampleRate,
                                    channelCount = channelCount,
                                    pcmEncoding = pcmEncoding,
                                    sumSquares = sumSquares,
                                    crossingCounts = crossingCounts,
                                    sampleCounts = sampleCounts,
                                    previousSignByWindow = previousSignByWindow,
                                    hasPreviousByWindow = hasPreviousByWindow
                                )
                            }
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            val voiceLike = BooleanArray(windowCount) { index ->
                val count = sampleCounts[index]
                if (count == 0) {
                    false
                } else {
                    val rms = sqrt(sumSquares[index] / count)
                    val zcr = crossingCounts[index].toDouble() / count
                    rms >= MIN_RMS && zcr in MIN_ZERO_CROSSING_RATE..MAX_ZERO_CROSSING_RATE
                }
            }
            coalesce(voiceLike, startSec)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Analisis aktivitas suara lokal gagal: ${e.message}")
            emptyList()
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun accumulateWindowStats(
        bytes: ByteArray,
        presentationTimeUs: Long,
        startUs: Long,
        endUs: Long,
        sampleRate: Int,
        channelCount: Int,
        pcmEncoding: Int,
        sumSquares: DoubleArray,
        crossingCounts: IntArray,
        sampleCounts: IntArray,
        previousSignByWindow: IntArray,
        hasPreviousByWindow: BooleanArray
    ) {
        val bytesPerSample = when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> 2
        }
        val frameBytes = bytesPerSample * channelCount
        if (frameBytes <= 0 || sampleRate <= 0) return
        val frameCount = bytes.size / frameBytes
        for (frame in 0 until frameCount) {
            val timeUs = presentationTimeUs + frame.toLong() * 1_000_000L / sampleRate
            if (timeUs < startUs || timeUs >= endUs) continue
            val window = (((timeUs - startUs) / 1_000_000.0) / WINDOW_SECONDS).toInt()
                .coerceIn(0, sumSquares.lastIndex)
            var mono = 0.0
            for (channel in 0 until channelCount) {
                val offset = frame * frameBytes + channel * bytesPerSample
                val sample = when (pcmEncoding) {
                    AudioFormat.ENCODING_PCM_8BIT -> ((bytes[offset].toInt() and 0xff) - 128) / 128.0
                    AudioFormat.ENCODING_PCM_FLOAT -> java.lang.Float.intBitsToFloat(
                        (bytes[offset].toInt() and 0xff) or
                            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
                            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
                            (bytes[offset + 3].toInt() shl 24)
                    ).toDouble()
                    else -> {
                        val value = (bytes[offset].toInt() and 0xff) or (bytes[offset + 1].toInt() shl 8)
                        (value.toShort().toInt() / 32768.0)
                    }
                }
                mono += sample
            }
            mono /= channelCount
            sumSquares[window] += mono * mono
            sampleCounts[window]++
            val sign = if (mono >= 0) 1 else -1
            if (hasPreviousByWindow[window] && sign != previousSignByWindow[window]) crossingCounts[window]++
            previousSignByWindow[window] = sign
            hasPreviousByWindow[window] = true
        }
    }

    private fun coalesce(active: BooleanArray, startSec: Long): List<SpeechActivitySpan> {
        val result = mutableListOf<SpeechActivitySpan>()
        var runStart = -1
        for (index in active.indices) {
            if (active[index] && runStart < 0) runStart = index
            val closes = runStart >= 0 && (!active[index] || index == active.lastIndex)
            if (closes) {
                val finalIndex = if (active[index]) index else index - 1
                val from = startSec + (runStart * WINDOW_SECONDS).toLong()
                val until = startSec + ceil((finalIndex + 1) * WINDOW_SECONDS).toLong()
                if (until > from) result.add(SpeechActivitySpan(from, until))
                runStart = -1
            }
        }
        return result
    }

    private fun MediaFormat.getIntegerOrDefault(key: String, default: Int): Int =
        if (containsKey(key)) getInteger(key) else default
}
