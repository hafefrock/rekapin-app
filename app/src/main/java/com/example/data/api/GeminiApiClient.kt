package com.example.data.api

import android.content.Context
import android.util.Log
import com.example.data.model.CharacterInfo
import com.example.data.model.NarrationLanguage
import com.example.data.model.NarrationMode
import com.example.data.model.RequestPacingMode
import com.example.data.model.SegmentAnalysisOutput
import com.example.data.model.SelectedVideo
import com.example.data.model.SeriesBible
import com.example.util.QuotaHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RequestPacer(
    private val minIntervalMs: Long = GeminiConstants.REQUEST_DELAY_MS,
    private val nowProviderMs: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private val mutex = Mutex()

    @Volatile
    private var lastRequestStartMs: Long? = null

    suspend fun waitIfNeeded(): Long = mutex.withLock {
        val now = nowProviderMs()
        val wait = GeminiApiClient.computePacingWaitMs(lastRequestStartMs, now, minIntervalMs)
        if (wait > 0L) {
            delay(wait)
        }
        lastRequestStartMs = nowProviderMs()
        wait
    }
}

class GeminiApiClient(
    private val apiKeyProvider: () -> String,
    var onRequestCounted: ((model: String) -> Unit)? = null,
    var onDailyQuotaExceeded: ((model: String, retryDelaySeconds: Long?) -> Unit)? = null,
    var pacingModeProvider: () -> RequestPacingMode = { RequestPacingMode.ADAPTIVE },
    var onPacingWait: ((Long) -> Unit)? = null,
    var thinkingLevelProvider: () -> String? = { null }
) {
    companion object {
        private const val TAG = "RekapinAI"
        private const val THINKING_SUPPORTED_MODEL_PREFIX = "gemini-3.8-flash"
        internal const val KEY_TEST_CALL_TIMEOUT_MS = 25_000L
        internal const val KEY_TEST_STAGE_VALID_QUOTA_MSG = "Key valid, memeriksa kuota…"
        internal const val KEY_TEST_TIMEOUT_MESSAGE =
            "Tidak ada respons dalam 25 detik (jaringan lambat atau server sibuk)."
        internal const val KEY_TEST_CONNECTION_ERROR_MESSAGE =
            "Gagal terhubung. Periksa koneksi internet."

        fun computePacingWaitMs(
            lastStartMs: Long?,
            nowMs: Long,
            minIntervalMs: Long = GeminiConstants.REQUEST_DELAY_MS
        ): Long {
            if (lastStartMs == null || minIntervalMs <= 0L) return 0L
            val elapsed = nowMs - lastStartMs
            return (minIntervalMs - elapsed).coerceAtLeast(0L)
        }

        internal fun supportsThinkingConfig(model: String): Boolean {
            return model.trim().startsWith(THINKING_SUPPORTED_MODEL_PREFIX)
        }

        internal fun applyThinkingConfig(
            genConfig: JSONObject,
            model: String,
            thinkingLevel: String?
        ): JSONObject {
            val cleanLevel = thinkingLevel?.trim()?.takeIf { it.isNotEmpty() }
            if (cleanLevel != null && supportsThinkingConfig(model)) {
                genConfig.put("thinkingConfig", JSONObject().apply {
                    put("thinkingLevel", cleanLevel)
                })
            }
            return genConfig
        }

        internal fun stripThinkingConfig(jsonBody: String): String? {
            return try {
                val root = JSONObject(jsonBody)
                val genConfig = root.optJSONObject("generationConfig") ?: return null
                if (!genConfig.has("thinkingConfig")) return null
                genConfig.remove("thinkingConfig")
                root.toString()
            } catch (_: Exception) {
                null
            }
        }

        internal fun formatKeyTestElapsed(elapsedMs: Long): String {
            val safeMs = elapsedMs.coerceAtLeast(0L)
            val seconds = safeMs / 1000.0
            val formatted = String.format(Locale.US, "%.1f", seconds).replace('.', ',')
            return "$formatted dtk"
        }

        internal fun buildModelsValidationEndpoint(
            generateContentBase: String = GeminiConstants.GENERATE_CONTENT_BASE
        ): String {
            val trimmedBase = generateContentBase.trimEnd('/')
            val v1BetaBase = if (trimmedBase.endsWith("/models")) {
                trimmedBase.removeSuffix("/models")
            } else {
                trimmedBase
            }
            return "$v1BetaBase/models?pageSize=1"
        }

        internal fun isTimeoutThrowable(throwable: Throwable): Boolean {
            var current: Throwable? = throwable
            while (current != null) {
                if (current is SocketTimeoutException || current is InterruptedIOException) {
                    return true
                }
                current = current.cause
            }
            return false
        }
    }

    private val requestPacer = RequestPacer(minIntervalMs = GeminiConstants.REQUEST_DELAY_MS)

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    private val testClient: OkHttpClient = client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    data class UploadedFile(
        val name: String,
        val uri: String,
        val state: String
    )

    enum class RemoteFileState {
        ACTIVE,
        PROCESSING,
        FAILED,
        NOT_FOUND
    }

    data class KeyTestResult(
        val isSuccess: Boolean,
        val httpCode: Int?,
        val message: String,
        val elapsedMs: Long? = null
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
        Log.i(TAG, "[$stageName] Memproses video berukuran ${video.sizeBytes} bytes (${video.sizeFormatted}), MIME: $mimeType.")

        // Langkah 1: Inisialisasi Resumable Upload
        val initUrl = GeminiConstants.UPLOAD_ENDPOINT
        val metadataJson = JSONObject().apply {
            put("file", JSONObject().apply {
                put("display_name", video.displayName)
            })
        }.toString()

        val initRequest = Request.Builder()
            .url(initUrl)
            .addHeader("x-goog-api-key", apiKey)
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
                        Log.e(TAG, "[$stageName] Inisialisasi upload gagal. HTTP ${res.code}; panjang respons ${errBody.length} karakter.")
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
                    Log.d(TAG, "[$stageName] Sesi resumable upload berhasil dibuat.")
                    continuation.resume(url)
                }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }

        // Langkah 2: Unggah byte video secara streaming dari InputStream
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
                        Log.e(TAG, "[$stageName] Finalisasi upload gagal. HTTP ${res.code}; panjang respons ${respBody.length} karakter.")
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

                    Log.d(TAG, "[$stageName] Respons upload diterima (${respBody.length} karakter).")
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
                    Log.i(TAG, "[$stageName] Upload sukses. Status awal: $state.")
                    continuation.resume(UploadedFile(name = name, uri = uri, state = state))
                }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    /**
     * Menunggu pemrosesan berkas video di server Gemini hingga berstatus ACTIVE.
     * Polling setiap 5 detik hingga maksimal 15 menit.
     */
    suspend fun waitForFileActive(
        fileName: String,
        onStatusUpdate: (String) -> Unit
    ): UploadedFile = withContext(Dispatchers.IO) {
        val stageName = "Menunggu File ACTIVE"
        val apiKey = getValidApiKey(stageName)
        val cleanName = if (fileName.startsWith("files/")) fileName else "files/$fileName"
        val statusUrl = "https://generativelanguage.googleapis.com/v1beta/$cleanName"

        Log.i(TAG, "[$stageName] Mulai polling status berkas di thread: ${Thread.currentThread().name}.")
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
                .addHeader("x-goog-api-key", apiKey)
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
                            Log.e(TAG, "[$stageName] Gagal memeriksa status berkas. HTTP ${res.code}; panjang respons ${respBody.length} karakter.")
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
        val statusUrl = "https://generativelanguage.googleapis.com/v1beta/$cleanName"
        Log.i(TAG, "[Cek Status Berkas] Memeriksa status terkini berkas Gemini...")

        val request = Request.Builder()
            .url(statusUrl)
            .addHeader("x-goog-api-key", apiKey)
            .get()
            .build()
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

    suspend fun isFileStillActive(fileName: String): Boolean = withContext(Dispatchers.IO) {
        checkFileRemoteState(fileName) == RemoteFileState.ACTIVE
    }

    /**
     * Menganalisis satu segmen video memakai start/end offset pada file yang sama.
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
        mode: NarrationMode = NarrationMode.REKAP_VOICEOVER,
        language: NarrationLanguage = NarrationLanguage.ENGLISH_USA,
        exactSceneSpans: List<String> = emptyList(),
        seriesBible: SeriesBible? = null,
        speechActivityHints: String = "",
        onStatusUpdate: (String) -> Unit
    ): SegmentAnalysisOutput = withContext(Dispatchers.IO) {
        val startFormatted = GeminiPromptBuilders.formatTimeSeconds(startSec)
        val endFormatted = GeminiPromptBuilders.formatTimeSeconds(endSec)
        val stageName = "Analisis Segmen $currentSegment dari $totalSegments ($startFormatted - $endFormatted)"
        val apiKey = getValidApiKey(stageName)

        Log.i(TAG, "[$stageName] Memulai analisis model '$model' offset: start=${startSec}s, end=${endSec}s, interval=${intervalSeconds}s (adegan: ${exactSceneSpans.size}, bible: ${seriesBible != null}) di thread: ${Thread.currentThread().name}")
        Log.d(TAG, "[$stageName] Karakter yang telah dikenal sebelumnya: ${previousCharacterSheet.map { it.name }}")

        val prompt = buildSegmentPrompt(
            startFormatted = startFormatted,
            endFormatted = endFormatted,
            startSec = startSec,
            endSec = endSec,
            intervalSeconds = intervalSeconds,
            previousCharacterSheet = previousCharacterSheet,
            isFirstSegment = isFirstSegment,
            stylePrompt = stylePrompt,
            mode = mode,
            language = language,
            exactSceneSpans = exactSceneSpans,
            seriesBible = seriesBible,
            speechActivityHints = speechActivityHints
        )

        val requestPayload = buildSegmentRequestBody(
            fileUri = fileUri,
            mimeType = mimeType,
            startSec = startSec,
            endSec = endSec,
            prompt = prompt,
            model = model
        )

        val endpoint = "${GeminiConstants.GENERATE_CONTENT_BASE}/${model.trim()}:generateContent"

        val responseJsonString = executeWithRetry(
            stage = stageName,
            url = endpoint,
            apiKey = apiKey,
            jsonBody = requestPayload,
            modelForCounting = model.trim(),
            onStatusUpdate = onStatusUpdate
        )

        val output = GeminiPromptBuilders.parseSegmentOutput(TAG, stageName, responseJsonString)

        val finalOutput = if (exactSceneSpans.isNotEmpty() && output.timeline.size != exactSceneSpans.size) {
            Log.w(TAG, "[$stageName] Validasi adegan: didapat ${output.timeline.size} item, diminta ${exactSceneSpans.size}. Mencoba ulang satu kali...")
            try {
                val retryJson = executeWithRetry(
                    stage = "$stageName (Coba Ulang Validasi Adegan)",
                    url = endpoint,
                    apiKey = apiKey,
                    jsonBody = requestPayload,
                    modelForCounting = model.trim(),
                    onStatusUpdate = onStatusUpdate
                )
                val retryOutput = GeminiPromptBuilders.parseSegmentOutput(TAG, stageName, retryJson)
                Log.i(TAG, "[$stageName] Hasil coba ulang: didapat ${retryOutput.timeline.size} item.")
                retryOutput
            } catch (e: Exception) {
                Log.w(TAG, "[$stageName] Percobaan ulang validasi adegan gagal (${e.javaClass.simpleName}), tetap memakai hasil pertama.")
                output
            }
        } else {
            output
        }

        Log.i(TAG, "[$stageName] Berhasil! Karakter: ${finalOutput.characterSheet.size}, Timeline items: ${finalOutput.timeline.size}.")
        finalOutput
    }

    /**
     * Membuat narasi rekap episode lengkap dari gabungan ringkasan tiap segmen.
     */
    suspend fun generateFinalNarration(
        segmentSummaries: List<String>,
        characterSheet: List<CharacterInfo>,
        model: String = GeminiConstants.GEMINI_MODEL,
        language: NarrationLanguage = NarrationLanguage.ENGLISH_USA,
        previousRecapContext: String? = null,
        onStatusUpdate: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val stageName = "Penyusunan Narasi Rekap Video"
        val apiKey = getValidApiKey(stageName)

        Log.i(TAG, "[$stageName] Menyusun narasi (${language.displayName}) dari ${segmentSummaries.size} ringkasan segmen model '$model' (recapSebelumnya=${!previousRecapContext.isNullOrBlank()}) di thread: ${Thread.currentThread().name}")

        val prompt = GeminiPromptBuilders.buildFinalNarrationPrompt(
            segmentSummaries = segmentSummaries,
            characterSheet = characterSheet,
            language = language,
            previousRecapContext = previousRecapContext
        )

        val requestPayload = buildFinalNarrationRequestBody(
            prompt = prompt,
            model = model
        )

        val endpoint = "${GeminiConstants.GENERATE_CONTENT_BASE}/${model.trim()}:generateContent"
        val rawResponse = executeWithRetry(
            stage = stageName,
            url = endpoint,
            apiKey = apiKey,
            jsonBody = requestPayload,
            modelForCounting = model.trim(),
            onStatusUpdate = onStatusUpdate
        )

        val narrationText = GeminiPromptBuilders.parseTextCandidate(
            stage = stageName,
            rawResponse = rawResponse,
            parseErrorMessage = "Gagal membaca teks narasi yang dihasilkan AI."
        )
        Log.i(TAG, "[$stageName] Narasi berhasil dibuat (${narrationText.length} karakter)")
        narrationText
    }

    /**
     * Menggabungkan narasi dari beberapa video/analisis menjadi satu narasi yang mengalir mulus dengan AI.
     */
    suspend fun mergeNarrationsWithAi(
        parts: List<Pair<String, String>>,
        model: String = GeminiConstants.GEMINI_MODEL,
        language: NarrationLanguage = NarrationLanguage.INDONESIAN,
        mode: NarrationMode = NarrationMode.REKAP_VOICEOVER,
        onStatusUpdate: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val stageName = "Satukan Narasi dengan AI"
        val apiKey = getValidApiKey(stageName)

        Log.i(TAG, "[$stageName] Menggabungkan ${parts.size} bagian narasi dengan model '$model' di thread: ${Thread.currentThread().name}")

        val prompt = GeminiPromptBuilders.buildMergeNarrationsPrompt(
            parts = parts,
            language = language
        )

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
            put("generationConfig", JSONObject())
        }.toString()

        val endpoint = "${GeminiConstants.GENERATE_CONTENT_BASE}/${model.trim()}:generateContent"
        val rawResponse = executeWithRetry(
            stage = stageName,
            url = endpoint,
            apiKey = apiKey,
            jsonBody = requestPayload,
            modelForCounting = model.trim(),
            onStatusUpdate = onStatusUpdate
        )

        val mergedText = GeminiPromptBuilders.parseTextCandidate(
            stage = stageName,
            rawResponse = rawResponse,
            parseErrorMessage = "Gagal membaca teks narasi gabungan yang dihasilkan AI."
        )
        Log.i(TAG, "[$stageName] Narasi gabungan AI berhasil (${mergedText.length} karakter)")
        mergedText
    }

    /**
     * Memperbarui Memori Seri (Series Bible) secara kumulatif setelah episode selesai.
     */
    suspend fun updateSeriesBible(
        previousBible: SeriesBible?,
        segmentSummaries: List<String>,
        characterSheet: List<CharacterInfo>,
        videoName: String,
        model: String = GeminiConstants.GEMINI_MODEL,
        onStatusUpdate: ((String) -> Unit)? = null
    ): SeriesBible? = withContext(Dispatchers.IO) {
        val stageName = "Pembaruan Memori Seri"
        val apiKey = getValidApiKey(stageName)

        Log.i(TAG, "[$stageName] Memperbarui memori seri dengan model '$model' di thread: ${Thread.currentThread().name}.")

        val prompt = GeminiPromptBuilders.buildSeriesBiblePrompt(
            previousBible = previousBible,
            segmentSummaries = segmentSummaries,
            characterSheet = characterSheet,
            videoName = videoName
        )

        val requestPayload = buildSeriesBibleRequestBody(
            prompt = prompt,
            model = model
        )

        val endpoint = "${GeminiConstants.GENERATE_CONTENT_BASE}/${model.trim()}:generateContent"
        val rawResponse = executeWithRetry(
            stage = stageName,
            url = endpoint,
            apiKey = apiKey,
            jsonBody = requestPayload,
            modelForCounting = model.trim(),
            onStatusUpdate = { onStatusUpdate?.invoke(it) }
        )

        GeminiPromptBuilders.parseSeriesBibleOutput(
            tag = TAG,
            stage = stageName,
            rawResponse = rawResponse,
            previousBible = previousBible,
            videoName = videoName
        )
    }

    internal fun buildSegmentRequestBody(
        fileUri: String,
        mimeType: String,
        startSec: Long,
        endSec: Long,
        prompt: String,
        model: String
    ): String = JSONObject().apply {
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
        }
        applyThinkingConfig(genConfig, model, thinkingLevelProvider())
        put("generationConfig", genConfig)
    }.toString()

    internal fun buildFinalNarrationRequestBody(
        prompt: String,
        model: String
    ): String = JSONObject().apply {
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

        val genConfig = JSONObject()
        applyThinkingConfig(genConfig, model, thinkingLevelProvider())
        put("generationConfig", genConfig)
    }.toString()

    internal fun buildSeriesBibleRequestBody(
        prompt: String,
        model: String
    ): String = JSONObject().apply {
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
            put("responseMimeType", "application/json")
        }
        applyThinkingConfig(genConfig, model, thinkingLevelProvider())
        put("generationConfig", genConfig)
    }.toString()

    /**
     * Eksekusi request dengan jeda dan auto-retry backoff saat rate limit (429/503).
     */
    internal suspend fun executeWithRetry(
        stage: String,
        url: String,
        apiKey: String,
        jsonBody: String,
        modelForCounting: String? = null,
        onStatusUpdate: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        var attempt = 0
        var activeJsonBody = jsonBody
        var retriedWithoutThinking = false
        val retryDelaysMs = listOf(10_000L, 30_000L, 60_000L)
        val maxRetries = GeminiConstants.MAX_RETRIES

        while (true) {
            attempt++
            val pacingMode = pacingModeProvider()
            if (pacingMode == RequestPacingMode.ADAPTIVE) {
                val waitedMs = requestPacer.waitIfNeeded()
                if (waitedMs > 0L) {
                    onPacingWait?.invoke(waitedMs)
                }
            }
            if (modelForCounting != null) {
                onRequestCounted?.invoke(modelForCounting)
            }
            Log.d(TAG, "[$stage] Mengirim permintaan HTTP (Percobaan $attempt) di thread: ${Thread.currentThread().name}...")

            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .post(activeJsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
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
                    if (pacingModeProvider() == RequestPacingMode.LEGACY) {
                        delay(GeminiConstants.REQUEST_DELAY_MS)
                        onPacingWait?.invoke(GeminiConstants.REQUEST_DELAY_MS)
                    }
                    return@withContext responseBody
                }

                // Pengaman thinkingConfig: jika HTTP 400 dan pesan menyebut "thinking", ulangi satu kali tanpa thinkingConfig
                if (statusCode == 400 && !retriedWithoutThinking && responseBody.contains("thinking", ignoreCase = true)) {
                    val strippedBody = stripThinkingConfig(activeJsonBody)
                    if (strippedBody != null) {
                        retriedWithoutThinking = true
                        activeJsonBody = strippedBody
                        attempt = (attempt - 1).coerceAtLeast(0)
                        Log.w(TAG, "[$stage] Server menolak thinkingConfig (HTTP 400). Mengulang permintaan satu kali tanpa thinkingConfig.")
                        continue
                    }
                }

                // 1. Cek kuota harian
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
                        retryDelaySeconds = quotaCheck.retryDelaySeconds,
                        modelName = modelForCounting.orEmpty()
                    )
                }

                if (statusCode == 429 && quotaCheck.retryDelaySeconds != null &&
                    quotaCheck.retryDelaySeconds > QuotaHelper.MAX_AUTOMATIC_RETRY_WAIT_SECONDS
                ) {
                    throw GeminiApiException(
                        stage = stage,
                        httpCode = statusCode,
                        rawMessage = responseBody,
                        userFriendlyMessage = quotaCheck.userMessage,
                        retryDelaySeconds = quotaCheck.retryDelaySeconds,
                        isDailyQuota = false,
                        modelName = modelForCounting.orEmpty()
                    )
                }

                // 2. Retry otomatis HANYA untuk HTTP 503, 500, dan 429 per menit
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
                        Log.e(TAG, "[$stage] Auto-retry maksimal ($maxRetries kali) habis setelah HTTP $statusCode; panjang respons ${responseBody.length} karakter.")
                        throw GeminiApiException(
                            stage = stage,
                            httpCode = statusCode,
                            rawMessage = responseBody,
                            userFriendlyMessage = if (statusCode == 500 || statusCode == 503) {
                                "Server Google AI mengalami gangguan sementara (HTTP $statusCode) setelah $maxRetries kali percobaan."
                            } else {
                                "Batas kuota per menit Gemini terlampaui (HTTP 429) setelah $maxRetries kali percobaan."
                            },
                            isDailyQuota = false,
                            modelName = modelForCounting.orEmpty()
                        )
                    }
                }

                // HTTP error lainnya JANGAN di-retry
                Log.e(TAG, "[$stage] Permintaan API gagal tanpa retry. HTTP $statusCode; panjang respons ${responseBody.length} karakter.")
                throw GeminiApiException(
                    stage = stage,
                    httpCode = statusCode,
                    rawMessage = responseBody,
                    userFriendlyMessage = parseErrorMessage(responseBody, statusCode),
                    isDailyQuota = false,
                    modelName = modelForCounting.orEmpty()
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
                        userFriendlyMessage = "Koneksi ke Gemini terputus: ${e.localizedMessage}. Pastikan perangkat terhubung ke internet.",
                        modelName = modelForCounting.orEmpty()
                    )
                }
            }
        }
        throw GeminiApiException(
            stage = stage,
            rawMessage = "Perulangan tak hingga selesai tanpa hasil.",
            userFriendlyMessage = "Gagal mendapatkan respons dari server Google AI.",
            modelName = modelForCounting.orEmpty()
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
        stylePrompt: String = "2D Japanese anime style, high-definition, clean cel-shaded linework, vibrant colors, detailed background art",
        mode: NarrationMode = NarrationMode.REKAP_VOICEOVER,
        language: NarrationLanguage = NarrationLanguage.ENGLISH_USA,
        exactSceneSpans: List<String> = emptyList(),
        seriesBible: SeriesBible? = null,
        speechActivityHints: String = ""
    ): String = GeminiPromptBuilders.buildSegmentPrompt(
        startFormatted = startFormatted,
        endFormatted = endFormatted,
        startSec = startSec,
        endSec = endSec,
        intervalSeconds = intervalSeconds,
        previousCharacterSheet = previousCharacterSheet,
        isFirstSegment = isFirstSegment,
        stylePrompt = stylePrompt,
        mode = mode,
        language = language,
        exactSceneSpans = exactSceneSpans,
        seriesBible = seriesBible,
        speechActivityHints = speechActivityHints
    )

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

    /**
     * Menguji validitas dan kuota sebuah API Key secara dua tahap:
     * 1) GET /v1beta/models?pageSize=1 untuk memeriksa validitas key secara cepat.
     * 2) POST generateContent ping minimalis untuk memeriksa kuota & akses model.
     */
    suspend fun testApiKey(
        apiKey: String,
        model: String = GeminiConstants.GEMINI_MODEL
    ): KeyTestResult = testApiKey(apiKey, model, onProgress = null)

    suspend fun testApiKey(
        apiKey: String,
        model: String = GeminiConstants.GEMINI_MODEL,
        onProgress: ((String) -> Unit)?
    ): KeyTestResult = testApiKeyWithBase(
        apiKey = apiKey,
        model = model,
        generateContentBase = GeminiConstants.GENERATE_CONTENT_BASE,
        onProgress = onProgress
    )

    internal suspend fun testApiKeyWithBase(
        apiKey: String,
        model: String = GeminiConstants.GEMINI_MODEL,
        generateContentBase: String = GeminiConstants.GENERATE_CONTENT_BASE,
        httpClientOverride: OkHttpClient? = null,
        totalTimeoutMs: Long = KEY_TEST_CALL_TIMEOUT_MS,
        onProgress: ((String) -> Unit)? = null
    ): KeyTestResult = GeminiKeyTester.testApiKeyWithBase(
        apiKey = apiKey,
        model = model,
        generateContentBase = generateContentBase,
        activeTestClient = httpClientOverride ?: testClient,
        totalTimeoutMs = totalTimeoutMs,
        onProgress = onProgress,
        parseErrorMessage = ::parseErrorMessage
    )

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
