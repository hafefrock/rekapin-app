package com.example.data.api

import android.content.Context
import android.util.Log
import com.example.data.model.CharacterInfo
import com.example.data.model.NarrationLanguage
import com.example.data.model.SegmentAnalysisOutput
import com.example.data.model.SelectedVideo
import com.example.data.model.TimelineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import com.example.util.QuotaHelper
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GeminiApiClient(
    private val apiKeyProvider: () -> String,
    var onRequestCounted: ((model: String) -> Unit)? = null,
    var onDailyQuotaExceeded: ((model: String, retryDelaySeconds: Long?) -> Unit)? = null
) {
    companion object {
        private const val TAG = "RekapinAI"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    data class UploadedFile(
        val name: String,
        val uri: String,
        val state: String
    )

    /**
     * Mengunggah video anime ke Gemini File API via Resumable Upload
     * dengan streaming langsung dari URI (buffer 64KB, ramah RAM kecil).
     * Dijalankan sepenuhnya di Dispatchers.IO untuk mencegah NetworkOnMainThreadException.
     */
    suspend fun uploadVideo(
        context: Context,
        video: SelectedVideo,
        onProgress: (progressPercent: Int, bytesSent: Long, totalBytes: Long) -> Unit
    ): UploadedFile = withContext(Dispatchers.IO) {
        val stageName = "Upload Video"
        val apiKey = getValidApiKey(stageName)
        val mimeType = if (video.mimeType.isNotBlank()) video.mimeType else "video/mp4"

        Log.i(TAG, "[$stageName] Memulai pengunggahan video di thread: ${Thread.currentThread().name}")
        Log.i(TAG, "[$stageName] Berkas: ${video.displayName}, Ukuran: ${video.sizeBytes} bytes (${video.sizeFormatted}), MIME: $mimeType, URI: ${video.uri}")

        // Langkah 1: Inisialisasi Resumable Upload
        val initUrl = "${GeminiConstants.UPLOAD_ENDPOINT}?key=$apiKey"
        val metadataJson = JSONObject().apply {
            put("file", JSONObject().apply {
                put("display_name", video.displayName)
            })
        }.toString()

        val initRequest = Request.Builder()
            .url(initUrl)
            .addHeader("X-Goog-Upload-Protocol", "resumable")
            .addHeader("X-Goog-Upload-Command", "start")
            .addHeader("X-Goog-Upload-Header-Content-Length", video.sizeBytes.toString())
            .addHeader("X-Goog-Upload-Header-Content-Type", mimeType)
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .post(metadataJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val uploadUrl = suspendCancellableCoroutine<String> { continuation ->
            val call = client.newCall(initRequest)
            continuation.invokeOnCancellation { call.cancel() }
            try {
                val response = call.execute()
                response.use { res ->
                    val errBody = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        Log.e(TAG, "[$stageName] Inisialisasi upload gagal. HTTP ${res.code}: $errBody")
                        val quotaCheck = QuotaHelper.checkQuotaError(res.code, errBody)
                        throw GeminiApiException(
                            stage = stageName,
                            httpCode = res.code,
                            rawMessage = errBody,
                            userFriendlyMessage = if (quotaCheck.isDailyQuota) quotaCheck.userMessage
                                else "Gagal memulai upload video ke server Google AI: ${parseErrorMessage(errBody, res.code)}",
                            isDailyQuota = quotaCheck.isDailyQuota,
                            retryDelaySeconds = quotaCheck.retryDelaySeconds
                        )
                    }
                    val url = res.header("x-goog-upload-url")
                        ?: res.header("X-Goog-Upload-URL")
                        ?: throw GeminiApiException(
                            stage = stageName,
                            httpCode = res.code,
                            rawMessage = "Header x-goog-upload-url tidak ditemukan dalam respons: $errBody",
                            userFriendlyMessage = "Server Google AI tidak mengembalikan alamat URL upload resumable."
                        )
                    Log.d(TAG, "[$stageName] Sesi resumable upload berhasil dibuat: $url")
                    continuation.resume(url)
                }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }

        // Langkah 2: Unggah byte video secara streaming dari InputStream
        // Menggunakan buffer 64KB dan tidak pernah membaca seluruh berkas ke RAM
        val streamRequestBody = object : RequestBody() {
            override fun contentType() = (mimeType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType())
            override fun contentLength() = video.sizeBytes

            override fun writeTo(sink: BufferedSink) {
                Log.d(TAG, "[$stageName] Membuka InputStream dari URI untuk streaming data di thread: ${Thread.currentThread().name}")
                context.contentResolver.openInputStream(video.uri)?.use { inputStream ->
                    val buffer = ByteArray(64 * 1024) // 64 KB buffer hemat RAM
                    var uploaded = 0L
                    var read: Int
                    var lastLoggedPercent = -1

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        sink.write(buffer, 0, read)
                        uploaded += read
                        val percent = if (video.sizeBytes > 0) ((uploaded * 100) / video.sizeBytes).toInt() else 0
                        onProgress(percent.coerceIn(0, 100), uploaded, video.sizeBytes)

                        if (percent % 25 == 0 && percent != lastLoggedPercent) {
                            Log.d(TAG, "[$stageName] Streaming progres: $percent% ($uploaded / ${video.sizeBytes} bytes)")
                            lastLoggedPercent = percent
                        }
                    }
                    Log.i(TAG, "[$stageName] Selesai streaming semua byte ($uploaded bytes)")
                } ?: throw GeminiApiException(
                    stage = stageName,
                    httpCode = null,
                    rawMessage = "ContentResolver gagal membuka InputStream dari URI: ${video.uri}",
                    userFriendlyMessage = "Tidak dapat membaca berkas video dari penyimpanan perangkat."
                )
            }
        }

        val uploadRequest = Request.Builder()
            .url(uploadUrl)
            .addHeader("Content-Length", video.sizeBytes.toString())
            .addHeader("X-Goog-Upload-Offset", "0")
            .addHeader("X-Goog-Upload-Command", "upload, finalize")
            .post(streamRequestBody)
            .build()

        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(uploadRequest)
            continuation.invokeOnCancellation { call.cancel() }
            try {
                val response = call.execute()
                response.use { res ->
                    val respBody = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        Log.e(TAG, "[$stageName] Finalisasi upload gagal. HTTP ${res.code}: $respBody")
                        val quotaCheck = QuotaHelper.checkQuotaError(res.code, respBody)
                        throw GeminiApiException(
                            stage = stageName,
                            httpCode = res.code,
                            rawMessage = respBody,
                            userFriendlyMessage = if (quotaCheck.isDailyQuota) quotaCheck.userMessage
                                else "Gagal menyelesaikan pengunggahan video: ${parseErrorMessage(respBody, res.code)}",
                            isDailyQuota = quotaCheck.isDailyQuota,
                            retryDelaySeconds = quotaCheck.retryDelaySeconds
                        )
                    }

                    Log.d(TAG, "[$stageName] Respons upload: $respBody")
                    val json = JSONObject(respBody)
                    val fileObj = json.optJSONObject("file")
                        ?: throw GeminiApiException(
                            stage = stageName,
                            httpCode = res.code,
                            rawMessage = respBody,
                            userFriendlyMessage = "Format JSON berkas dari server tidak valid."
                        )
                    val name = fileObj.getString("name")
                    val uri = fileObj.optString("uri", "https://generativelanguage.googleapis.com/v1beta/$name")
                    val state = fileObj.optString("state", "PROCESSING")
                    Log.i(TAG, "[$stageName] Upload sukses! Nama: $name, Status Awal: $state, URI: $uri")
                    continuation.resume(UploadedFile(name = name, uri = uri, state = state))
                }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    /**
     * Menunggu pemrosesan berkas video di server Gemini hingga berstatus ACTIVE.
     * Dijalankan sepenuhnya di Dispatchers.IO.
     */
    /**
     * Menunggu pemrosesan berkas video di server Gemini hingga berstatus ACTIVE.
     * Polling setiap 5 detik hingga maksimal 15 menit.
     * Dijalankan sepenuhnya di Dispatchers.IO.
     */
    suspend fun waitForFileActive(
        fileName: String,
        onStatusUpdate: (String) -> Unit
    ): UploadedFile = withContext(Dispatchers.IO) {
        val stageName = "Menunggu File ACTIVE"
        val apiKey = getValidApiKey(stageName)
        val cleanName = if (fileName.startsWith("files/")) fileName else "files/$fileName"
        val statusUrl = "https://generativelanguage.googleapis.com/v1beta/$cleanName?key=$apiKey"

        Log.i(TAG, "[$stageName] Mulai polling status berkas: $cleanName di thread: ${Thread.currentThread().name}")
        val startTime = System.currentTimeMillis()
        val maxDurationMs = 15 * 60 * 1000L // 15 menit
        val pollIntervalMs = 5000L // 5 detik

        var attempts = 0
        while (System.currentTimeMillis() - startTime < maxDurationMs) {
            attempts++
            val elapsedMs = System.currentTimeMillis() - startTime
            val elapsedSec = (elapsedMs / 1000L).coerceAtLeast(0L)
            val mm = elapsedSec / 60L
            val ss = elapsedSec % 60L
            val timeStr = String.format(Locale.US, "%02d:%02d", mm, ss)
            val statusMsg = "Memproses video di server Google... ($timeStr)"
            onStatusUpdate(statusMsg)

            val request = Request.Builder()
                .url(statusUrl)
                .get()
                .build()

            val fileResult = suspendCancellableCoroutine<UploadedFile?> { continuation ->
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                try {
                    val response = call.execute()
                    response.use { res ->
                        val respBody = res.body?.string().orEmpty()
                        if (!res.isSuccessful) {
                            Log.e(TAG, "[$stageName] Gagal memeriksa status berkas. HTTP ${res.code}: $respBody")
                            val quotaCheck = QuotaHelper.checkQuotaError(res.code, respBody)
                            throw GeminiApiException(
                                stage = stageName,
                                httpCode = res.code,
                                rawMessage = respBody,
                                userFriendlyMessage = if (quotaCheck.isDailyQuota) quotaCheck.userMessage
                                    else "Gagal memeriksa status video: ${parseErrorMessage(respBody, res.code)}",
                                isDailyQuota = quotaCheck.isDailyQuota,
                                retryDelaySeconds = quotaCheck.retryDelaySeconds
                            )
                        }
                        val json = JSONObject(respBody)
                        val name = json.optString("name", cleanName)
                        val uri = json.optString("uri", "https://generativelanguage.googleapis.com/v1beta/$name")
                        val state = json.optString("state", "PROCESSING")
                        Log.d(TAG, "[$stageName] Polling [$attempts] ($timeStr): state = $state")
                        continuation.resume(UploadedFile(name, uri, state))
                    }
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }

            if (fileResult != null) {
                when (fileResult.state) {
                    "ACTIVE" -> {
                        Log.i(TAG, "[$stageName] Berkas video telah ACTIVE dan siap dianalisis! ($timeStr)")
                        onStatusUpdate("Video siap dianalisis (Status: ACTIVE)")
                        return@withContext fileResult
                    }
                    "FAILED" -> {
                        Log.e(TAG, "[$stageName] Berkas video berstatus FAILED di server Google AI")
                        throw GeminiApiException(
                            stage = stageName,
                            httpCode = null,
                            rawMessage = "File state: FAILED. Google AI tidak dapat memproses video ini.",
                            userFriendlyMessage = "Pemrosesan video di server Google AI gagal (Status: FAILED). Format atau kodek video mungkin tidak didukung oleh server AI. Silakan unggah ulang video untuk mencoba kembali.",
                            isFileProcessingFailed = true
                        )
                    }
                    else -> {
                        // Status PROCESSING, lanjutkan tunggu
                    }
                }
            }

            delay(pollIntervalMs)
        }

        Log.w(TAG, "[$stageName] Timeout 15 menit terlampaui. File masih berstatus PROCESSING.")
        throw GeminiApiException(
            stage = stageName,
            httpCode = null,
            rawMessage = "Batas waktu pemrosesan 15 menit terlampaui. File $cleanName masih PROCESSING.",
            userFriendlyMessage = "Video masih diproses di server Google setelah 15 menit. Berkas tersimpan di server Google selama 48 jam. Silakan ketuk tombol \"Cek lagi\" untuk memeriksa statusnya kembali tanpa mengunggah ulang.",
            isFileProcessingTimeout = true
        )
    }

    enum class RemoteFileState {
        ACTIVE,
        PROCESSING,
        FAILED,
        NOT_FOUND
    }

    /**
     * Memeriksa status terkini berkas di Gemini Files API: ACTIVE, PROCESSING, FAILED, atau NOT_FOUND.
     */
    suspend fun checkFileRemoteState(fileName: String): RemoteFileState = withContext(Dispatchers.IO) {
        if (fileName.isBlank()) return@withContext RemoteFileState.NOT_FOUND
        val cleanName = if (fileName.startsWith("files/")) fileName else "files/$fileName"
        val apiKey = try {
            getValidApiKey("Cek Status Berkas")
        } catch (_: Exception) {
            return@withContext RemoteFileState.NOT_FOUND
        }
        val statusUrl = "https://generativelanguage.googleapis.com/v1beta/$cleanName?key=$apiKey"
        Log.i(TAG, "[Cek Status Berkas] Memeriksa status terkini $cleanName...")

        val request = Request.Builder().url(statusUrl).get().build()
        try {
            val response = client.newCall(request).execute()
            response.use { res ->
                if (!res.isSuccessful) {
                    Log.d(TAG, "[Cek Status Berkas] Berkas $cleanName tidak dapat diakses (HTTP ${res.code})")
                    return@withContext RemoteFileState.NOT_FOUND
                }
                val body = res.body?.string().orEmpty()
                val json = JSONObject(body)
                val state = json.optString("state", "").uppercase(Locale.US)
                Log.i(TAG, "[Cek Status Berkas] Status terkini berkas $cleanName: $state")
                when (state) {
                    "ACTIVE" -> RemoteFileState.ACTIVE
                    "PROCESSING" -> RemoteFileState.PROCESSING
                    "FAILED" -> RemoteFileState.FAILED
                    else -> RemoteFileState.PROCESSING
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[Cek Status Berkas] Gagal memeriksa status berkas: ${e.message}")
            RemoteFileState.NOT_FOUND
        }
    }

    /**
     * Memeriksa apakah berkas video di Gemini API masih berstatus ACTIVE.
     * Mengembalikan true jika masih aktif dan dapat digunakan kembali tanpa upload ulang.
     */
    suspend fun isFileStillActive(fileName: String): Boolean = withContext(Dispatchers.IO) {
        checkFileRemoteState(fileName) == RemoteFileState.ACTIVE
    }

    /**
     * Menganalisis satu segmen 3 menit memakai start/end offset pada file yang sama.
     * Dijalankan sepenuhnya di Dispatchers.IO.
     */
    suspend fun analyzeSegment(
        currentSegment: Int,
        totalSegments: Int,
        fileUri: String,
        mimeType: String,
        startSec: Long,
        endSec: Long,
        intervalSeconds: Int,
        previousCharacterSheet: List<CharacterInfo>,
        isFirstSegment: Boolean,
        model: String = GeminiConstants.GEMINI_MODEL,
        stylePrompt: String = "2D Japanese anime style, high-definition, clean cel-shaded linework, vibrant colors, detailed background art",
        onStatusUpdate: (String) -> Unit
    ): SegmentAnalysisOutput = withContext(Dispatchers.IO) {
        val startFormatted = formatTimeSeconds(startSec)
        val endFormatted = formatTimeSeconds(endSec)
        val stageName = "Analisis Segmen $currentSegment dari $totalSegments ($startFormatted - $endFormatted)"
        val apiKey = getValidApiKey(stageName)

        Log.i(TAG, "[$stageName] Memulai analisis model '$model' offset: start=${startSec}s, end=${endSec}s, interval=${intervalSeconds}s di thread: ${Thread.currentThread().name}")
        Log.d(TAG, "[$stageName] Karakter yang telah dikenal sebelumnya: ${previousCharacterSheet.map { it.name }}")

        val prompt = buildSegmentPrompt(
            startFormatted = startFormatted,
            endFormatted = endFormatted,
            startSec = startSec,
            endSec = endSec,
            intervalSeconds = intervalSeconds,
            previousCharacterSheet = previousCharacterSheet,
            isFirstSegment = isFirstSegment,
            stylePrompt = stylePrompt
        )

        val requestPayload = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        val fileDataPart = JSONObject().apply {
                            put("fileData", JSONObject().apply {
                                put("fileUri", fileUri)
                                put("mimeType", mimeType.ifEmpty { "video/mp4" })
                            })
                            put("videoMetadata", JSONObject().apply {
                                put("startOffset", "${startSec}s")
                                put("endOffset", "${endSec}s")
                            })
                        }
                        put(fileDataPart)

                        val textPart = JSONObject().apply {
                            put("text", prompt)
                        }
                        put(textPart)
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)

            val genConfig = JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.3)
            }
            put("generationConfig", genConfig)
        }.toString()

        val endpoint = "${GeminiConstants.GENERATE_CONTENT_BASE}/${model.trim()}:generateContent?key=$apiKey"

        val responseJsonString = executeWithRetry(
            stage = stageName,
            url = endpoint,
            jsonBody = requestPayload,
            modelForCounting = model.trim(),
            onStatusUpdate = onStatusUpdate
        )

        val output = parseSegmentOutput(stageName, responseJsonString)
        Log.i(TAG, "[$stageName] Berhasil! Ringkasan: \"${output.segmentSummary.take(60)}...\", Karakter: ${output.characterSheet.size}, Timeline items: ${output.timeline.size}")
        output
    }

    /**
     * Membuat narasi rekap episode lengkap dari gabungan ringkasan tiap segmen.
     * Permintaan teks saja, tanpa video. Dijalankan sepenuhnya di Dispatchers.IO.
     */
    suspend fun generateFinalNarration(
        segmentSummaries: List<String>,
        characterSheet: List<CharacterInfo>,
        model: String = GeminiConstants.GEMINI_MODEL,
        language: NarrationLanguage = NarrationLanguage.ENGLISH_USA,
        onStatusUpdate: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val stageName = "Penyusunan Narasi Rekap Video"
        val apiKey = getValidApiKey(stageName)

        Log.i(TAG, "[$stageName] Menyusun narasi (${language.displayName}) dari ${segmentSummaries.size} ringkasan segmen model '$model' di thread: ${Thread.currentThread().name}")

        val summariesText = segmentSummaries.mapIndexed { idx, sum ->
            "Segment ${idx + 1}: $sum"
        }.joinToString("\n\n")

        val charactersText = characterSheet.joinToString("\n") {
            "- ${it.name}: ${it.description}"
        }

        val prompt = if (language == NarrationLanguage.INDONESIAN) {
            """
            You are a master anime storyteller and narrator.
            Based on the chronological progression of this complete anime episode:
            
            CHRONOLOGICAL SEGMENTS:
            $summariesText
            
            KEY CHARACTERS:
            $charactersText
            
            INSTRUCTIONS:
            Write an engaging, fluid, and captivating story recap of the entire episode in natural, highly expressive Bahasa Indonesia (gaya pencerita anime yang hidup, emosional, dramatis, dan mengalir natural, bukan terjemahan harfiah/kaku).
            - Form an organic narrative arc capturing character motives, rising action, emotional drama, visual tension, and the climax.
            - Write in natural narrative prose paragraphs (3 to 5 flowing paragraphs) in Indonesian.
            - Keep official character names and terms intact.
            - Do NOT use bullet points, robotic scene tags, or dry summaries. Make it read like an exciting, immersive recap for anime fans.
            """.trimIndent()
        } else {
            """
            You are a master anime storyteller and narrator. 
            Based on the chronological progression of this complete anime episode:
            
            CHRONOLOGICAL SEGMENTS:
            $summariesText
            
            KEY CHARACTERS:
            $charactersText
            
            INSTRUCTIONS:
            Write an engaging, fluid, and captivating story recap of the entire episode in natural American English (native USA storytelling voice).
            - Form an organic narrative arc capturing character motives, rising action, emotional drama, visual tension, and the climax.
            - Write in natural narrative prose paragraphs (3 to 5 flowing paragraphs).
            - Do NOT use bullet points, robotic scene tags, or dry summaries. Make it read like an exciting, immersive recap for anime fans.
            """.trimIndent()
        }

        val requestPayload = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)

            val genConfig = JSONObject().apply {
                put("temperature", 0.7)
            }
            put("generationConfig", genConfig)
        }.toString()

        val endpoint = "${GeminiConstants.GENERATE_CONTENT_BASE}/${model.trim()}:generateContent?key=$apiKey"
        val rawResponse = executeWithRetry(
            stage = stageName,
            url = endpoint,
            jsonBody = requestPayload,
            modelForCounting = model.trim(),
            onStatusUpdate = onStatusUpdate
        )

        try {
            val json = JSONObject(rawResponse)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val narrationText = parts.getJSONObject(0).optString("text", "").trim()
                    Log.i(TAG, "[$stageName] Narasi berhasil dibuat (${narrationText.length} karakter)")
                    return@withContext narrationText
                }
            }
            throw GeminiApiException(
                stage = stageName,
                httpCode = 200,
                rawMessage = rawResponse,
                userFriendlyMessage = "Respons dari AI tidak memuat kandidat teks narasi."
            )
        } catch (e: GeminiApiException) {
            throw e
        } catch (e: Exception) {
            throw GeminiApiException(
                stage = stageName,
                httpCode = 200,
                rawMessage = "Gagal memproses JSON respons: ${e.message}\nRespons Mentah:\n$rawResponse",
                userFriendlyMessage = "Gagal membaca teks narasi yang dihasilkan AI.",
                cause = e
            )
        }
    }

    /**
     * Eksekusi request dengan jeda dan auto-retry backoff saat rate limit (429/503).
     * Dijalankan sepenuhnya di Dispatchers.IO.
     */
    private suspend fun executeWithRetry(
        stage: String,
        url: String,
        jsonBody: String,
        modelForCounting: String? = null,
        onStatusUpdate: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        var attempt = 0
        // Jeda bertahap: 10, 30, 60 detik untuk HTTP 500 / 503 (dan 429)
        val retryDelaysMs = listOf(10_000L, 30_000L, 60_000L)
        val maxRetries = 3

        while (true) {
            attempt++
            if (modelForCounting != null) {
                onRequestCounted?.invoke(modelForCounting)
            }
            Log.d(TAG, "[$stage] Mengirim permintaan HTTP (Percobaan $attempt) di thread: ${Thread.currentThread().name}...")

            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            try {
                val result = suspendCancellableCoroutine<Pair<Int, String>> { cont ->
                    val call = client.newCall(request)
                    cont.invokeOnCancellation { call.cancel() }
                    try {
                        val response = call.execute()
                        response.use { res ->
                            val body = res.body?.string().orEmpty()
                            cont.resume(Pair(res.code, body))
                        }
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }

                val (statusCode, responseBody) = result
                Log.d(TAG, "[$stage] Mendapat HTTP $statusCode, ukuran respons: ${responseBody.length} karakter")

                if (statusCode in 200..299) {
                    delay(GeminiConstants.REQUEST_DELAY_MS)
                    return@withContext responseBody
                }

                // 1. Cek kuota harian: Jika error 429 berisi "PerDay" atau retryDelay > 5 menit, JANGAN retry.
                val quotaCheck = QuotaHelper.checkQuotaError(statusCode, responseBody)
                if (quotaCheck.isDailyQuota) {
                    Log.e(TAG, "[$stage] Kuota harian habis / retryDelay > 5 menit (${quotaCheck.retryDelaySeconds}s). JANGAN retry.")
                    if (modelForCounting != null) {
                        onDailyQuotaExceeded?.invoke(modelForCounting, quotaCheck.retryDelaySeconds)
                    }
                    throw GeminiApiException(
                        stage = stage,
                        httpCode = statusCode,
                        rawMessage = responseBody,
                        userFriendlyMessage = quotaCheck.userMessage,
                        isDailyQuota = true,
                        retryDelaySeconds = quotaCheck.retryDelaySeconds
                    )
                }

                // 2. Retry otomatis HANYA untuk HTTP 503, 500, dan 429 per menit,
                // maksimal 3 kali dengan jeda bertahap (10, 30, 60 detik)
                if (statusCode == 500 || statusCode == 503 || statusCode == 429) {
                    if (attempt <= maxRetries) {
                        val waitMs = retryDelaysMs[attempt - 1]
                        val waitSec = (waitMs / 1000).toInt()
                        Log.w(TAG, "[$stage] Server merespons HTTP $statusCode. Auto-retry dalam ${waitSec}s (Percobaan $attempt dari $maxRetries)")
                        for (sec in waitSec downTo 1) {
                            val statusText = if (statusCode == 500 || statusCode == 503) {
                                "Server Google AI sibuk / kendala internal (HTTP $statusCode). Mencoba ulang otomatis dalam $sec detik ($attempt/$maxRetries)..."
                            } else {
                                "Mencapai batas kuota per menit (HTTP 429). Mencoba ulang otomatis dalam $sec detik ($attempt/$maxRetries)..."
                            }
                            onStatusUpdate(statusText)
                            delay(1000L)
                        }
                        continue
                    } else {
                        Log.e(TAG, "[$stage] Auto-retry maksimal ($maxRetries kali) habis setelah HTTP $statusCode: $responseBody")
                        throw GeminiApiException(
                            stage = stage,
                            httpCode = statusCode,
                            rawMessage = responseBody,
                            userFriendlyMessage = if (statusCode == 500 || statusCode == 503) {
                                "Server Google AI mengalami gangguan sementara (HTTP $statusCode) setelah $maxRetries kali percobaan."
                            } else {
                                "Batas kuota per menit Gemini terlampaui (HTTP 429) setelah $maxRetries kali percobaan."
                            },
                            isDailyQuota = false
                        )
                    }
                }

                // HTTP error lainnya (400, 401, 403, dll) JANGAN di-retry
                Log.e(TAG, "[$stage] Permintaan API gagal tanpa retry. HTTP $statusCode: $responseBody")
                throw GeminiApiException(
                    stage = stage,
                    httpCode = statusCode,
                    rawMessage = responseBody,
                    userFriendlyMessage = parseErrorMessage(responseBody, statusCode),
                    isDailyQuota = false
                )

            } catch (e: GeminiApiException) {
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "[$stage] Gangguan IO/Koneksi pada percobaan $attempt: ${e.message}", e)
                if (attempt <= maxRetries) {
                    val waitMs = retryDelaysMs[attempt - 1]
                    val waitSec = (waitMs / 1000).toInt()
                    for (sec in waitSec downTo 1) {
                        onStatusUpdate("Gangguan koneksi internet (${e.localizedMessage ?: "timeout"}). Mencoba ulang dalam $sec detik ($attempt/$maxRetries)...")
                        delay(1000L)
                    }
                    continue
                } else {
                    throw GeminiApiException(
                        stage = stage,
                        httpCode = null,
                        rawMessage = "IOException: ${e.message}\n${e.stackTraceToString()}",
                        userFriendlyMessage = "Koneksi ke Gemini terputus: ${e.localizedMessage}. Pastikan perangkat terhubung ke internet."
                    )
                }
            }
        }
        throw GeminiApiException(
            stage = stage,
            rawMessage = "Perulangan tak hingga selesai tanpa hasil.",
            userFriendlyMessage = "Gagal mendapatkan respons dari server Google AI."
        )
    }

    private fun buildSegmentPrompt(
        startFormatted: String,
        endFormatted: String,
        startSec: Long,
        endSec: Long,
        intervalSeconds: Int,
        previousCharacterSheet: List<CharacterInfo>,
        isFirstSegment: Boolean,
        stylePrompt: String = "2D Japanese anime style, high-definition, clean cel-shaded linework, vibrant colors, detailed background art"
    ): String {
        val sampleTimestamps = if (isFirstSegment) {
            "00:00 - 00:05, 00:05 - 00:10"
        } else {
            "$startFormatted - ${formatTimeSeconds(startSec + intervalSeconds)}"
        }

        val characterContext = if (previousCharacterSheet.isNotEmpty()) {
            val jsonList = JSONArray().apply {
                previousCharacterSheet.forEach { c ->
                    put(JSONObject().apply {
                        put("name", c.name)
                        put("description", c.description)
                    })
                }
            }.toString(2)
            """
            EXISTING CHARACTER SHEET FROM PREVIOUS SEGMENTS:
            $jsonList
            
            INSTRUCTIONS FOR CHARACTER SHEET:
            - Retain all established characters from the previous sheet.
            - Only add new characters who actively appear in this segment, or update significant clothing/form changes.
            - Ensure hair, eye color, and outfits remain identical and consistent.
            """.trimIndent()
        } else {
            """
            INSTRUCTIONS FOR CHARACTER SHEET:
            - Identify all prominent anime characters appearing in this first segment.
            - For each character, detail immutable physical features: hair color and style, eye shape and color, facial structure, exact signature clothing, colors, and key accessories.
            """.trimIndent()
        }

        return """
            You are an expert anime visual director and storyboard analyst.
            Analyze the provided video segment between $startFormatted and $endFormatted.
            
            TIMESTAMPS:
            - Must be continuous and consecutive in intervals of $intervalSeconds seconds (e.g., $sampleTimestamps ...).
            - Start exactly at $startFormatted and proceed consecutively until $endFormatted.
            
            LANGUAGE:
            - ALL output text (descriptions, visual prompts, and character sheet) MUST be written in natural American English (native USA speaker style, rich vocabulary).
            
            $characterContext
            
            OUTPUT JSON FORMAT:
            Return ONLY a valid JSON object with exactly this schema:
            {
              "segmentSummary": "A concise, fluid 2-3 sentence story summary of what happened in this 3-minute segment in natural American English.",
              "characterSheet": [
                {
                  "name": "Character Name",
                  "description": "Hair color/style, eye color/shape, facial features, exact signature anime attire, color palette, accessories."
                }
              ],
              "timeline": [
                {
                  "timestamp": "MM:SS - MM:SS",
                  "sceneDescription": "Short, vivid description of the scene in natural American English.",
                  "visualPrompt": "$stylePrompt. [Subject: Name and repeat their exact visual attributes from character sheet], [Action: specific physical movement or stance], [Expression: emotional face details], [Setting: environment, architecture, background], [Lighting: lighting source, shadows, highlights], [Camera: shot framing and angle], [Mood: emotional atmosphere]."
                }
              ]
            }
            
            CRITICAL VISUAL PROMPT RULES:
            1. Every visualPrompt MUST strictly start with: "$stylePrompt". Follow only the selected style; do not mix other styles.
            2. Repeat the character's exact physical descriptions from the character sheet inside each visual prompt so text-to-image models maintain character consistency across shots.
            3. Each visual prompt must be exactly one descriptive paragraph covering: subject, action, expression, setting, lighting, camera angle, mood.
            4. Do NOT mention any animation studio names or artist names.
        """.trimIndent()
    }

    private fun parseSegmentOutput(stage: String, rawJson: String): SegmentAnalysisOutput {
        try {
            val json = JSONObject(rawJson)
            val candidates = json.optJSONArray("candidates")
            val rawText = if (candidates != null && candidates.length() > 0) {
                val content = candidates.getJSONObject(0).optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    parts.getJSONObject(0).optString("text", "")
                } else ""
            } else {
                rawJson
            }

            val cleaned = cleanJsonString(rawText)
            val parsed = JSONObject(cleaned)

            val segmentSummary = parsed.optString("segmentSummary", "Summary of anime segment.")

            val characterSheetList = mutableListOf<CharacterInfo>()
            val charactersArray = parsed.optJSONArray("characterSheet")
            if (charactersArray != null) {
                for (i in 0 until charactersArray.length()) {
                    val item = charactersArray.optJSONObject(i) ?: continue
                    val name = item.optString("name", "Unknown Character")
                    val desc = item.optString("description", "")
                    if (name.isNotBlank()) {
                        characterSheetList.add(CharacterInfo(name = name, description = desc))
                    }
                }
            }

            val timelineList = mutableListOf<TimelineItem>()
            val timelineArray = parsed.optJSONArray("timeline")
            if (timelineArray != null) {
                for (i in 0 until timelineArray.length()) {
                    val item = timelineArray.optJSONObject(i) ?: continue
                    val timestamp = item.optString("timestamp", "")
                    val sceneDescription = item.optString("sceneDescription", "")
                    val visualPrompt = item.optString("visualPrompt", "")
                    if (timestamp.isNotBlank() || sceneDescription.isNotBlank()) {
                        timelineList.add(
                            TimelineItem(
                                timestamp = timestamp,
                                sceneDescription = sceneDescription,
                                visualPrompt = visualPrompt
                            )
                        )
                    }
                }
            }

            return SegmentAnalysisOutput(
                segmentSummary = segmentSummary,
                characterSheet = characterSheetList,
                timeline = timelineList
            )
        } catch (e: Exception) {
            Log.e(TAG, "[$stage] Gagal memparsing respons JSON: ${e.message}\nRespons Mentah:\n$rawJson", e)
            throw GeminiApiException(
                stage = stage,
                httpCode = 200,
                rawMessage = "Gagal memproses JSON respons: ${e.message}\nRespons Mentah:\n$rawJson",
                userFriendlyMessage = "Format JSON hasil analisis tidak sesuai: ${e.message}",
                cause = e
            )
        }
    }

    private fun cleanJsonString(text: String): String {
        val trimmed = text.trim()
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        return if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            trimmed.substring(firstBrace, lastBrace + 1)
        } else {
            trimmed
        }
    }

    private fun parseErrorMessage(errorBody: String, statusCode: Int): String {
        val quotaCheck = QuotaHelper.checkQuotaError(statusCode, errorBody)
        if (quotaCheck.isDailyQuota) {
            return quotaCheck.userMessage
        }
        if (statusCode == 404) {
            return "Nama model tidak dikenal, periksa ejaannya"
        }
        return try {
            val json = JSONObject(errorBody)
            val errorObj = json.optJSONObject("error")
            val msg = errorObj?.optString("message") ?: errorBody
            if (msg.contains("not found", ignoreCase = true) && (msg.contains("models/", ignoreCase = true) || msg.contains("model", ignoreCase = true))) {
                return "Nama model tidak dikenal, periksa ejaannya"
            }
            when (statusCode) {
                400 -> "Parameter permintaan tidak valid: $msg"
                401, 403 -> "API Key tidak valid atau tidak diizinkan: $msg"
                404 -> "Nama model tidak dikenal, periksa ejaannya"
                429 -> "Batas kuota per menit Gemini terlampaui. Silakan tunggu beberapa saat."
                503 -> "Layanan Gemini AI sedang sibuk. Silakan coba kembali."
                else -> msg
            }
        } catch (_: Exception) {
            if (statusCode == 404) "Nama model tidak dikenal, periksa ejaannya"
            else if (statusCode == 429) "Batas kuota per menit Gemini terlampaui."
            else if (statusCode == 503) "Layanan Google AI sedang sibuk."
            else "Terjadi kendala pada server (Kode HTTP $statusCode)."
        }
    }

    private fun formatTimeSeconds(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    private fun getValidApiKey(stage: String): String {
        val key = apiKeyProvider()
        if (key.isBlank()) {
            throw GeminiApiException(
                stage = stage,
                httpCode = null,
                rawMessage = "API Key kosong / belum diatur",
                userFriendlyMessage = "API Key Gemini belum diatur. Silakan masukkan API Key Anda di menu API Key."
            )
        }
        return key
    }
}
