package com.example.data.api

import android.content.Context
import android.util.Log
import com.example.data.model.CharacterInfo
import com.example.data.model.SeriesBible
import com.example.data.prefs.SeriesBibleStorage
import com.example.data.model.NarrationLanguage
import com.example.data.model.NarrationMode
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
        Log.i(TAG, "[$stageName] Berkas: ${video.displayName}, Ukuran: ${video.sizeBytes} bytes (${video.sizeFormatted}), MIME: $mimeType")

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
     * Dijalankan sepenuhnya di Dispatchers.IO.
     */
    suspend fun waitForFileActive(
        fileName: String,
        onStatusUpdate: (String) -> Unit
    ): UploadedFile = withContext(Dispatchers.IO) {
        val stageName = "Menunggu File ACTIVE"
        val apiKey = getValidApiKey(stageName)
        val cleanName = if (fileName.startsWith("files/")) fileName else "files/$fileName"
        val statusUrl = "https://generativelanguage.googleapis.com/v1beta/$cleanName"

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
        val statusUrl = "https://generativelanguage.googleapis.com/v1beta/$cleanName"
        Log.i(TAG, "[Cek Status Berkas] Memeriksa status terkini $cleanName...")

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
        mode: NarrationMode = NarrationMode.REKAP_VOICEOVER,
        language: NarrationLanguage = NarrationLanguage.ENGLISH_USA,
        exactSceneSpans: List<String> = emptyList(),
        seriesBible: SeriesBible? = null,
        speechActivityHints: String = "",
        onStatusUpdate: (String) -> Unit
    ): SegmentAnalysisOutput = withContext(Dispatchers.IO) {
        val startFormatted = formatTimeSeconds(startSec)
        val endFormatted = formatTimeSeconds(endSec)
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

        val endpoint = "${GeminiConstants.GENERATE_CONTENT_BASE}/${model.trim()}:generateContent"

        val responseJsonString = executeWithRetry(
            stage = stageName,
            url = endpoint,
            apiKey = apiKey,
            jsonBody = requestPayload,
            modelForCounting = model.trim(),
            onStatusUpdate = onStatusUpdate
        )

        val output = parseSegmentOutput(stageName, responseJsonString)

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
                val retryOutput = parseSegmentOutput(stageName, retryJson)
                Log.i(TAG, "[$stageName] Hasil coba ulang: didapat ${retryOutput.timeline.size} item.")
                retryOutput
            } catch (e: Exception) {
                Log.w(TAG, "[$stageName] Percobaan ulang validasi adegan gagal: ${e.message}, tetap memakai hasil pertama.")
                output
            }
        } else {
            output
        }

        Log.i(TAG, "[$stageName] Berhasil! Ringkasan: \"${finalOutput.segmentSummary.take(60)}...\", Karakter: ${finalOutput.characterSheet.size}, Timeline items: ${finalOutput.timeline.size}")
        finalOutput
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
        previousRecapContext: String? = null,
        onStatusUpdate: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val stageName = "Penyusunan Narasi Rekap Video"
        val apiKey = getValidApiKey(stageName)

        Log.i(TAG, "[$stageName] Menyusun narasi (${language.displayName}) dari ${segmentSummaries.size} ringkasan segmen model '$model' (recapSebelumnya=${!previousRecapContext.isNullOrBlank()}) di thread: ${Thread.currentThread().name}")

        val summariesText = segmentSummaries.mapIndexed { idx, sum ->
            "Segment ${idx + 1}: $sum"
        }.joinToString("\n\n")

        val charactersText = characterSheet.joinToString("\n") {
            "- ${it.name}: ${it.description}"
        }

        val youtubeSafeRules = """
        YOUTUBE & ADVERTISER-FRIENDLY SAFETY GUIDELINES:
        - Write for a general audience, with a family-friendly and advertiser-friendly tone.
        - Never use profanity, slurs, or vulgar words. If a character swears, convey the emotion without the words.
        - Describe violence in a non-graphic way: focus on stakes, emotion, and outcome. No gore, no detailed injuries, no torture, no step-by-step methods.
        - No sexual content. Keep romance PG. Never sexualize any character, especially youthful-looking ones; skip fan-service details.
        - Do not promote or glorify hate, harassment, drugs, or dangerous acts. Mention heavy topics (death, self-harm) briefly and sensitively, never with methods.
        - Use your own original wording. Do not quote dialogue, lyrics, or official synopsis text verbatim.
        - Stay faithful to the events. No clickbait, no exaggerated or misleading claims, and do not present the recap as official.
        - Treat everything as fiction. Do not mention real people or real-world groups.
        """.trimIndent()

        val previousRecapInstruction = if (!previousRecapContext.isNullOrBlank()) {
            if (language == NarrationLanguage.INDONESIAN) {
                """
                PREVIOUS STORYLINE CONTEXT FROM SERIES BIBLE:
                $previousRecapContext

                OPENING RECAP INSTRUCTION ("SEBELUMNYA DI..."):
                - Mulai narasi secara alami dengan 2 sampai 3 kalimat "sebelumnya..." yang merangkum peristiwa penting dari cerita sebelumnya (diambil dari konteks di atas).
                - Tulis dalam Bahasa Indonesia alami, hidup, dan bebas spoiler berlebihan untuk episode sekarang.
                - Setelah 2-3 kalimat kilas balik tersebut, lanjutkan narasi langsung mengalir ke peristiwa episode ini.
                """.trimIndent()
            } else {
                """
                PREVIOUS STORYLINE CONTEXT FROM SERIES BIBLE:
                $previousRecapContext

                OPENING RECAP INSTRUCTION ("PREVIOUSLY ON..."):
                - Begin the narration naturally with 2 to 3 concise "previously..." sentences summarizing relevant past events (drawn from the context above).
                - Write in natural American English, completely spoiler-free for current events.
                - After those 2-3 opening recap sentences, seamlessly transition directly into the events of this episode.
                """.trimIndent()
            }
        } else {
            ""
        }

        val instructionBlock = if (language == NarrationLanguage.INDONESIAN) {
            """
            INSTRUCTIONS:
            Write an engaging, fluid, and captivating story recap of the entire episode in natural, highly expressive Bahasa Indonesia (gaya pencerita anime yang hidup, emosional, dramatis, dan mengalir natural, bukan terjemahan harfiah/kaku).
            - Gunakan Bahasa Indonesia yang sopan, hidup, dan natural, tanpa umpatan, kata kasar, atau hinaan.
            - Form an organic narrative arc capturing character motives, rising action, emotional drama, visual tension, and the climax.
            - Keep official character names and terms intact.
            """.trimIndent()
        } else {
            """
            INSTRUCTIONS:
            Write an engaging, fluid, and captivating story recap of the entire episode in natural American English (native USA storytelling voice).
            - Form an organic narrative arc capturing character motives, rising action, emotional drama, visual tension, and the climax.
            - Keep official character names and terms intact.
            """.trimIndent()
        }

        val introHint = if (language == NarrationLanguage.INDONESIAN) {
            "e.g. do NOT include \"Berikut adalah rekap...\", \"Here is the recap...\", or similar phrases"
        } else {
            "e.g. do NOT include \"Here is the recap...\", \"Here is the story...\", or similar opening remarks"
        }

        val formatBlock = """
        VOICE-OVER OUTPUT FORMAT:
        - Provide output ready for voice-over: clean prose paragraphs with short-to-medium sentences.
        - Exactly 3 to 5 flowing paragraphs.
        - Absolutely NO markdown (no asterisks, no bold/italics), NO titles or headings, NO emojis, NO parentheses, NO sound effects, and NO timestamps.
        - Absolutely NO conversational intro or greeting ($introHint). Start directly with the narration.
        """.trimIndent()

        val prompt = """
        You are a master anime storyteller and narrator.
        Based on the chronological progression of this complete anime episode:

        CHRONOLOGICAL SEGMENTS:
        $summariesText

        KEY CHARACTERS:
        $charactersText

        ${if (previousRecapInstruction.isNotBlank()) "$previousRecapInstruction\n\n        " else ""}$instructionBlock

        $youtubeSafeRules

        $formatBlock
        """.trimIndent()

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

        val endpoint = "${GeminiConstants.GENERATE_CONTENT_BASE}/${model.trim()}:generateContent"
        val rawResponse = executeWithRetry(
            stage = stageName,
            url = endpoint,
            apiKey = apiKey,
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
     * Menggabungkan narasi dari beberapa video/analisis menjadi satu narasi yang mengalir mulus dengan AI.
     * Permintaan teks saja, tanpa unggah video. Dijalankan sepenuhnya di Dispatchers.IO.
     */
    suspend fun mergeNarrationsWithAi(
        parts: List<Pair<String, String>>, // Pair(videoTitle, narrationText)
        model: String = GeminiConstants.GEMINI_MODEL,
        language: NarrationLanguage = NarrationLanguage.INDONESIAN,
        mode: NarrationMode = NarrationMode.REKAP_VOICEOVER,
        onStatusUpdate: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val stageName = "Satukan Narasi dengan AI"
        val apiKey = getValidApiKey(stageName)

        Log.i(TAG, "[$stageName] Menggabungkan ${parts.size} bagian narasi dengan model '$model' di thread: ${Thread.currentThread().name}")

        val partsText = parts.mapIndexed { idx, (title, text) ->
            "--- BAGIAN ${idx + 1}: $title ---\n$text"
        }.joinToString("\n\n")

        val youtubeSafeRules = """
        YOUTUBE & ADVERTISER-FRIENDLY SAFETY GUIDELINES:
        - Write for a general audience, with a family-friendly and advertiser-friendly tone.
        - Never use profanity, slurs, or vulgar words. If a character swears, convey the emotion without the words.
        - Describe violence in a non-graphic way: focus on stakes, emotion, and outcome. No gore, no detailed injuries, no torture, no step-by-step methods.
        - No sexual content. Keep romance PG. Never sexualize any character, especially youthful-looking ones; skip fan-service details.
        - Do not promote or glorify hate, harassment, drugs, or dangerous acts. Mention heavy topics (death, self-harm) briefly and sensitively, never with methods.
        - Use your own original wording. Do not quote dialogue, lyrics, or official synopsis text verbatim.
        - Stay faithful to the events. No clickbait, no exaggerated or misleading claims, and do not present the recap as official.
        - Treat everything as fiction. Do not mention real people or real-world groups.
        """.trimIndent()

        val instructionBlock = if (language == NarrationLanguage.INDONESIAN) {
            """
            INSTRUCTIONS:
            Gabungkan narasi dari setiap bagian di atas menjadi satu kesatuan narasi yang utuh, dinamis, dan mengalir mulus.
            - Tambahkan transisi cerita yang luwes dan alami saat berpindah antar bagian, menghubungkan alur secara logis dan emosional.
            - JANGAN MENAMBAH KEJADIAN BARU, fakta fiktif baru, atau spekulasi yang tidak ada pada bagian-bagian tersebut.
            - Gunakan Bahasa Indonesia yang sopan, hidup, dan natural, tanpa umpatan atau kata kasar.
            - Pertahankan nama karakter dan istilah penting tetap konsisten.
            """.trimIndent()
        } else {
            """
            INSTRUCTIONS:
            Seamlessly combine the story narrations from each section above into a single, cohesive, unified narrative arc.
            - Add natural and fluid narrative transitions between sections so the story flows organically.
            - DO NOT INVENT NEW EVENTS, scenes, or facts not present in the provided sections.
            - Write in natural American English storytelling voice.
            - Keep character names and established terms consistent.
            """.trimIndent()
        }

        val formatBlock = """
        OUTPUT FORMAT:
        - Output ready for voice-over / reading: clean prose paragraphs.
        - Absolutely NO markdown (no asterisks, no bold/italics), NO titles or headings (do not include 'Bagian 1', etc.), NO emojis, NO parentheses, NO sound effects, and NO timestamps.
        - Absolutely NO conversational greeting or intro (do NOT write 'Berikut adalah...', 'Here is the combined story...', etc.). Start immediately with the narration text.
        """.trimIndent()

        val prompt = """
        You are a master anime storyteller and narrator.
        Combine and harmonize the following story sections from consecutive episodes into one seamless continuous narrative:

        $partsText

        $instructionBlock

        $youtubeSafeRules

        $formatBlock
        """.trimIndent()

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

        val endpoint = "${GeminiConstants.GENERATE_CONTENT_BASE}/${model.trim()}:generateContent"
        val rawResponse = executeWithRetry(
            stage = stageName,
            url = endpoint,
            apiKey = apiKey,
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
                val partsArr = content?.optJSONArray("parts")
                if (partsArr != null && partsArr.length() > 0) {
                    val mergedText = partsArr.getJSONObject(0).optString("text", "").trim()
                    Log.i(TAG, "[$stageName] Narasi gabungan AI berhasil (${mergedText.length} karakter)")
                    return@withContext mergedText
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
                userFriendlyMessage = "Gagal membaca teks narasi gabungan yang dihasilkan AI.",
                cause = e
            )
        }
    }

    /**
     * Memperbarui Memori Seri (Series Bible) secara kumulatif setelah episode selesai.
     * Panggilan teks saja (tanpa video), meniru struktur generateFinalNarration.
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

        Log.i(TAG, "[$stageName] Memperbarui memori seri untuk '$videoName' model '$model' di thread: ${Thread.currentThread().name}")

        val previousBibleText = if (previousBible != null && !previousBible.isEmpty()) {
            SeriesBibleStorage.toJson(previousBible)
        } else {
            "None (this is the first episode in the series)."
        }

        val summariesText = segmentSummaries.mapIndexed { idx, sum ->
            "Segment ${idx + 1}: $sum"
        }.joinToString("\n\n")

        val charactersText = characterSheet.joinToString("\n") {
            "- ${it.name}: ${it.description}"
        }

        val prompt = """
        You are an expert anime continuity editor and series bible archivist.
        Your task is to update the cumulative "Series Bible" (world lore, terminology, character dynamics, and overarching plot) by integrating the events of this newly concluded episode with any established continuity lore from previous episodes.

        CURRENT EPISODE TITLE / IDENTIFIER:
        $videoName

        PREVIOUS SERIES BIBLE (IF ANY):
        $previousBibleText

        THIS EPISODE'S CHRONOLOGICAL SEGMENT SUMMARIES:
        $summariesText

        ESTABLISHED CHARACTERS:
        $charactersText

        INSTRUCTIONS:
        1. LOCATIONS: Merge previous locations with any newly introduced or expanded key locations. Deduplicate names (case-insensitive). Keep descriptions concise and distinct. Retain established lore. Max 20 locations.
        2. GLOSSARY: Merge previous terminology, abilities, titles, magic systems, technology, or lore terms. Retain official naming, spellings, and concise definitions. Max 30 terms.
        3. RELATIONSHIPS: Merge and update character dynamics (e.g. rival, mentor, allied, estranged). Max 20 relationships.
        4. PLOT SO FAR: Synthesize an updated, cumulative high-level summary of the entire series story up through this episode. Keep it concise, focused on key stakes and milestones, free of excessive minutiae or spoilers beyond this point. MUST be strictly under 1200 characters.
        5. EPISODE RECAP: Write a tight 2 to 3 sentence recap summarizing specifically what occurred in THIS episode ($videoName). Under 300 characters.
        6. General rule: do not invent facts; only record what is established in the summaries, character sheet, or existing bible.

        OUTPUT FORMAT:
        Return ONLY valid JSON (no markdown formatting, no code fences, no extra commentary):
        {
          "locations": [
            { "name": "Location Name", "description": "Brief description" }
          ],
          "glossary": [
            { "term": "Term/Concept", "definition": "Brief definition" }
          ],
          "relationships": [
            { "characterA": "Name A", "characterB": "Name B", "relationship": "Dynamic" }
          ],
          "plotSoFar": "Cumulative high-level summary up through this episode under 1200 chars.",
          "episodeRecap": "2-3 sentences summarizing this specific episode."
        }
        """.trimIndent()

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
                put("responseMimeType", "application/json")
                put("temperature", 0.3)
            }
            put("generationConfig", genConfig)
        }.toString()

        val endpoint = "${GeminiConstants.GENERATE_CONTENT_BASE}/${model.trim()}:generateContent"
        val rawResponse = executeWithRetry(
            stage = stageName,
            url = endpoint,
            apiKey = apiKey,
            jsonBody = requestPayload,
            modelForCounting = model.trim(),
            onStatusUpdate = { onStatusUpdate?.invoke(it) }
        )

        try {
            val json = JSONObject(rawResponse)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val text = parts.getJSONObject(0).optString("text", "").trim()
                    val parsedBible = SeriesBibleStorage.fromJson(text)
                    if (parsedBible != null) {
                        var cleaned = text
                        if (cleaned.startsWith("```json", ignoreCase = true)) cleaned = cleaned.substring(7)
                        else if (cleaned.startsWith("```")) cleaned = cleaned.substring(3)
                        if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length - 3)
                        val textJson = JSONObject(cleaned.trim())
                        val episodeRecap = textJson.optString("episodeRecap", "").trim().take(SeriesBible.MAX_EPISODE_RECAP_CHARS)

                        val finalBible = parsedBible
                            .copy(episodes = previousBible?.episodes.orEmpty())
                            .withUpdatedEpisode(videoName, episodeRecap)
                        Log.i(TAG, "[$stageName] Memori seri berhasil diperbarui (${finalBible.locations.size} lokasi, ${finalBible.glossary.size} istilah, ${finalBible.relationships.size} relasi, ${finalBible.episodes.size} episode)")
                        return@withContext finalBible
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "[$stageName] Gagal membaca atau memproses respons memori seri: ${e.message}")
            null
        }
    }

    /**
     * Eksekusi request dengan jeda dan auto-retry backoff saat rate limit (429/503).
     * Dijalankan sepenuhnya di Dispatchers.IO.
     */
    private suspend fun executeWithRetry(
        stage: String,
        url: String,
        apiKey: String,
        jsonBody: String,
        modelForCounting: String? = null,
        onStatusUpdate: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        var attempt = 0
        // Jeda bertahap: 10, 30, 60 detik untuk HTTP 500 / 503 (dan 429)
        val retryDelaysMs = listOf(10_000L, 30_000L, 60_000L)
        val maxRetries = GeminiConstants.MAX_RETRIES

        while (true) {
            attempt++
            if (modelForCounting != null) {
                onRequestCounted?.invoke(modelForCounting)
            }
            Log.d(TAG, "[$stage] Mengirim permintaan HTTP (Percobaan $attempt) di thread: ${Thread.currentThread().name}...")

            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", apiKey)
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

                // HTTP error lainnya (400, 401, 403, dll) JANGAN di-retry
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
    ): String {
        val sampleTimestamps = if (isFirstSegment) {
            "00:00 - 00:05, 00:05 - 00:10"
        } else {
            "$startFormatted - ${formatTimeSeconds(startSec + intervalSeconds)}"
        }

        val timestampsBlock = if (exactSceneSpans.isNotEmpty()) {
            val listFormatted = exactSceneSpans.joinToString("\n") { "  - \"$it\"" }
            """
            TIMESTAMPS (PER ADEGAN - EXACT REQUIRED LIST):
            You MUST create exactly one timeline entry for each of the following required timestamp ranges, in this exact sequence, with no gaps and no overlaps:
$listFormatted
            - Each timeline entry's "timestamp" field MUST exactly match its assigned range from the list above.
            - Total timeline items required: ${exactSceneSpans.size}.
            """.trimIndent()
        } else {
            """
            TIMESTAMPS:
            - Must be continuous and consecutive in intervals of $intervalSeconds seconds (e.g., $sampleTimestamps ...).
            - Start exactly at $startFormatted and proceed consecutively until $endFormatted.
            """.trimIndent()
        }

        val seriesBibleBlock = if (seriesBible != null && !seriesBible.isEmpty()) {
            buildString {
                appendLine("SERIES BIBLE FROM PREVIOUS EPISODES:")
                appendLine("Use this only to keep names, terms, spellings, and locations CONSISTENT. Do NOT describe anything from it unless it is actually visible or audible in this segment. Do NOT reveal or hint at future events. Keep official names exactly as written.")
                appendLine()
                if (seriesBible.locations.isNotEmpty()) {
                    appendLine("ESTABLISHED LOCATIONS:")
                    seriesBible.locations.forEach { loc ->
                        appendLine("- ${loc.name}: ${loc.description}")
                    }
                    appendLine()
                }
                if (seriesBible.glossary.isNotEmpty()) {
                    appendLine("GLOSSARY / TERMS:")
                    seriesBible.glossary.forEach { g ->
                        appendLine("- ${g.term}: ${g.definition}")
                    }
                    appendLine()
                }
                if (seriesBible.relationships.isNotEmpty()) {
                    appendLine("CHARACTER RELATIONSHIPS:")
                    seriesBible.relationships.forEach { rel ->
                        appendLine("- ${rel.characterA} & ${rel.characterB}: ${rel.relationship}")
                    }
                    appendLine()
                }
                if (seriesBible.plotSoFar.isNotBlank()) {
                    appendLine("PLOT CONTEXT SO FAR:")
                    appendLine(seriesBible.plotSoFar.trim())
                    appendLine()
                }
            }.trim()
        } else {
            ""
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

        val targetLanguage = if (language == NarrationLanguage.INDONESIAN) "Indonesian" else "American English"

        val speechActivityBlock = if (mode == NarrationMode.DIALOG_KONTEN && speechActivityHints.isNotBlank()) {
            """
            LOCAL AUDIO ACTIVITY HINT (approximate, not a transcript):
            $speechActivityHints
            Use this only to focus dialogue listening. Confirm whether the sound is actually character speech in the video; music/noise may be misclassified. Never invent dialogue, and keep the dialogue field empty where no speech is audible.
            """.trimIndent()
        } else ""

        val transcriptInstruction = when (language) {
            NarrationLanguage.ORIGINAL -> {
                "Transcribe exactly what is spoken, in the ORIGINAL spoken language of the audio. Do NOT translate. Keep it faithful and do not invent lines. If the language is unclear, use the language that is most clearly heard."
            }
            NarrationLanguage.INDONESIAN -> {
                "Translate faithfully into Indonesian: keep the meaning, tone, and speaker, and do not invent lines. The text MUST be in Indonesian even if the audio is in another language. Use natural and standard Indonesian, not stiff machine translation."
            }
            NarrationLanguage.ENGLISH_USA -> {
                "Translate faithfully into American English: keep the meaning, tone, and speaker, and do not invent lines. The text MUST be in American English even if the audio is in another language."
            }
        }

        val timelineItemSchema = when (mode) {
            NarrationMode.DIALOG_KONTEN -> """
                {
                  "timestamp": "MM:SS - MM:SS",
                  "sceneDescription": "Short, vivid description of the scene in natural American English.",
                  "visualPrompt": "$stylePrompt. [Subject: name and exact established appearance], [Action: specific physical movement or stance], [Camera: framing, angle, and focus], [Lighting: source, direction, shadows, and highlights], [Mood: emotional atmosphere], [Background: environment, architecture, and relevant details], [Expression: visible facial emotion].",
                  "dialogue": "Spoken lines of characters heard in this time window, formatted as 'Speaker: line' joined by ' / '. Use the character names from the character sheet, or 'Unknown' if the speaker cannot be identified. Use an empty string if nobody speaks. $transcriptInstruction"
                }
            """.trimIndent()
            NarrationMode.NARASI_ASLI -> """
                {
                  "timestamp": "MM:SS - MM:SS",
                  "sceneDescription": "Short, vivid description of the scene in natural American English.",
                  "visualPrompt": "$stylePrompt. [Subject: name and exact established appearance], [Action: specific physical movement or stance], [Camera: framing, angle, and focus], [Lighting: source, direction, shadows, and highlights], [Mood: emotional atmosphere], [Background: environment, architecture, and relevant details], [Expression: visible facial emotion].",
                  "originalNarration": "Only the off-screen narrator's voice-over heard in this time window, not character dialogue. Use an empty string if there is no narrator. $transcriptInstruction"
                }
            """.trimIndent()
            NarrationMode.REKAP_VOICEOVER -> """
                {
                  "timestamp": "MM:SS - MM:SS",
                  "sceneDescription": "Short, vivid description of the scene in natural American English.",
                  "visualPrompt": "$stylePrompt. [Subject: name and exact established appearance], [Action: specific physical movement or stance], [Camera: framing, angle, and focus], [Lighting: source, direction, shadows, and highlights], [Mood: emotional atmosphere], [Background: environment, architecture, and relevant details], [Expression: visible facial emotion]."
                }
            """.trimIndent()
        }

        val languageBlock = if (mode == NarrationMode.REKAP_VOICEOVER) {
            """
            LANGUAGE:
            - ALL output text (summary, descriptions, visual prompts, and character sheet) MUST be written in natural American English.
            """.trimIndent()
        } else if (language == NarrationLanguage.ORIGINAL) {
            """
            LANGUAGE (STRICT):
            - Transcript fields ('dialogue' and 'originalNarration') MUST be written in the ORIGINAL spoken language of the audio. Do NOT translate them.
            - ALL other fields ('segmentSummary', 'characterSheet', 'sceneDescription', and 'visualPrompt') MUST be written in natural American English.
            """.trimIndent()
        } else {
            """
            LANGUAGE (STRICT):
            - The output language is $targetLanguage. This applies to the transcript fields 'dialogue' and 'originalNarration'.
            - ALL other fields ('segmentSummary', 'characterSheet', 'sceneDescription', and 'visualPrompt') MUST be written in natural American English.
            - The audio in the video may be in a different language than $targetLanguage. You MUST translate everything you hear into $targetLanguage. Never copy the original spoken language.
            - Only proper nouns (character names, place names, technique names) stay unchanged.
            """.trimIndent()
        }

        val finalCheckReminder = when {
            mode == NarrationMode.REKAP_VOICEOVER -> ""
            language == NarrationLanguage.ORIGINAL -> "Final check: 'dialogue' and 'originalNarration' must be in the original spoken language, never translated.\n"
            else -> "Final check: every 'dialogue' and 'originalNarration' value must be written in $targetLanguage.\n"
        }

        return """
            You are an expert anime visual director and storyboard analyst.
            Analyze the provided video segment between $startFormatted and $endFormatted.
            
            $timestampsBlock
            
            $languageBlock

            ${if (speechActivityBlock.isNotBlank()) "$speechActivityBlock\n\n            " else ""}
            
            ${if (seriesBibleBlock.isNotBlank()) "$seriesBibleBlock\n\n            " else ""}$characterContext
            
            OUTPUT JSON FORMAT:
            ${finalCheckReminder}Return ONLY a valid JSON object with exactly this schema:
            {
              "segmentSummary": "A concise, fluid 2-3 sentence story summary of what happened in this 3-minute segment in natural American English.",
              "characterSheet": [
                {
                  "name": "Character Name",
                  "description": "Hair color/style, eye color/shape, facial features, exact signature anime attire, color palette, accessories."
                }
              ],
              "timeline": [
$timelineItemSchema
              ]
            }
            
            CRITICAL VISUAL PROMPT RULES:
            1. Every visualPrompt MUST strictly start with: "$stylePrompt". Follow only the selected style; do not mix other styles.
            2. Repeat the character's exact physical descriptions from the character sheet inside each visual prompt so text-to-image models maintain character consistency across shots.
            3. Every visual prompt must be one paragraph in this order, with every labeled part present: [Subject], [Action], [Camera], [Lighting], [Mood], [Background]. Add [Expression] when a face is visible. Repeat stable physical appearance in [Subject] and keep it consistent with the character sheet.
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
                    val dialogue = item.optString("dialogue", "")
                    val originalNarration = item.optString("originalNarration", "")
                    if (timestamp.isNotBlank() || sceneDescription.isNotBlank()) {
                        timelineList.add(
                            TimelineItem(
                                timestamp = timestamp,
                                sceneDescription = sceneDescription,
                                visualPrompt = visualPrompt,
                                dialogue = dialogue,
                                originalNarration = originalNarration
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
            Log.e(TAG, "[$stage] Gagal memparsing respons JSON (${rawJson.length} karakter): ${e.message}", e)
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

    data class KeyTestResult(
        val isSuccess: Boolean,
        val httpCode: Int?,
        val message: String
    )

    /**
     * Menguji validitas dan kuota sebuah API Key dengan request teks minimalis ("ping").
     * Mengembalikan KeyTestResult dengan status jelas (Sukses, Kuota Habis 429, Invalid 400/401/403, Sibuk 503, dll).
     */
    suspend fun testApiKey(apiKey: String, model: String = GeminiConstants.GEMINI_MODEL): KeyTestResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext KeyTestResult(false, null, "API Key kosong.")
        }
        val cleanModel = model.trim().ifBlank { GeminiConstants.GEMINI_MODEL }
        val endpoint = "${GeminiConstants.GENERATE_CONTENT_BASE}/$cleanModel:generateContent"
        val payload = JSONObject().apply {
            val contents = JSONArray().apply {
                val item = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", "ping") })
                    }
                    put("parts", parts)
                }
                put(item)
            }
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 5)
            })
        }.toString()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("x-goog-api-key", apiKey.trim())
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            response.use { res ->
                val body = res.body?.string().orEmpty()
                if (res.isSuccessful) {
                    KeyTestResult(
                        isSuccess = true,
                        httpCode = res.code,
                        message = "Key aktif & kuota tersedia!"
                    )
                } else {
                    val quotaCheck = QuotaHelper.checkQuotaError(res.code, body)
                    val errorMsg = when {
                        quotaCheck.isDailyQuota -> quotaCheck.userMessage
                        res.code == 429 -> "Batas kuota per menit terlampaui (HTTP 429)."
                        res.code == 400 || res.code == 401 || res.code == 403 -> "Key tidak valid atau belum aktif (HTTP ${res.code})."
                        res.code == 404 -> "Model tidak ditemukan (HTTP 404)."
                        res.code == 503 -> "Server Google AI sibuk (HTTP 503)."
                        else -> parseErrorMessage(body, res.code)
                    }
                    KeyTestResult(
                        isSuccess = false,
                        httpCode = res.code,
                        message = errorMsg
                    )
                }
            }
        } catch (e: Exception) {
            KeyTestResult(
                isSuccess = false,
                httpCode = null,
                message = "Gagal terhubung: ${e.localizedMessage ?: "Gangguan internet"}"
            )
        }
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
