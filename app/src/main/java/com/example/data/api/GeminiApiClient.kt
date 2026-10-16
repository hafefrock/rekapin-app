package com.example.data.api

import android.content.Context
import com.example.data.model.CharacterInfo
import com.example.data.model.SegmentAnalysisOutput
import com.example.data.model.SelectedVideo
import com.example.data.model.TimelineItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
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
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GeminiApiClient(
    private val apiKeyProvider: () -> String
) {
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
     * dengan pemantauan progres real-time.
     */
    suspend fun uploadVideo(
        context: Context,
        video: SelectedVideo,
        onProgress: (progressPercent: Int, bytesSent: Long, totalBytes: Long) -> Unit
    ): UploadedFile {
        val apiKey = getValidApiKey()
        val mimeType = if (video.mimeType.isNotBlank()) video.mimeType else "video/mp4"

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
                    if (!res.isSuccessful) {
                        val errBody = res.body?.string().orEmpty()
                        throw IOException("Gagal memulai upload (HTTP ${res.code}): ${parseErrorMessage(errBody, res.code)}")
                    }
                    val url = res.header("x-goog-upload-url")
                        ?: res.header("X-Goog-Upload-URL")
                        ?: throw IOException("Server tidak mengembalikan URL upload.")
                    continuation.resume(url)
                }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }

        // Langkah 2: Unggah byte video dengan pemantauan progres
        val streamRequestBody = object : RequestBody() {
            override fun contentType() = (mimeType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType())
            override fun contentLength() = video.sizeBytes

            override fun writeTo(sink: BufferedSink) {
                context.contentResolver.openInputStream(video.uri)?.use { inputStream ->
                    val buffer = ByteArray(64 * 1024)
                    var uploaded = 0L
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        sink.write(buffer, 0, read)
                        uploaded += read
                        val percent = if (video.sizeBytes > 0) ((uploaded * 100) / video.sizeBytes).toInt() else 0
                        onProgress(percent.coerceIn(0, 100), uploaded, video.sizeBytes)
                    }
                } ?: throw IOException("Tidak dapat membaca berkas video yang dipilih.")
            }
        }

        val uploadRequest = Request.Builder()
            .url(uploadUrl)
            .addHeader("Content-Length", video.sizeBytes.toString())
            .addHeader("X-Goog-Upload-Offset", "0")
            .addHeader("X-Goog-Upload-Command", "upload, finalize")
            .post(streamRequestBody)
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(uploadRequest)
            continuation.invokeOnCancellation { call.cancel() }
            try {
                val response = call.execute()
                response.use { res ->
                    val respBody = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        throw IOException("Gagal menyelesaikan upload (HTTP ${res.code}): ${parseErrorMessage(respBody, res.code)}")
                    }
                    val json = JSONObject(respBody)
                    val fileObj = json.optJSONObject("file")
                        ?: throw IOException("Format respons berkas tidak valid.")
                    val name = fileObj.getString("name")
                    val uri = fileObj.optString("uri", "https://generativelanguage.googleapis.com/v1beta/$name")
                    val state = fileObj.optString("state", "PROCESSING")
                    continuation.resume(UploadedFile(name = name, uri = uri, state = state))
                }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    /**
     * Menunggu pemrosesan berkas video di server Gemini hingga berstatus ACTIVE.
     */
    suspend fun waitForFileActive(
        fileName: String,
        onStatusUpdate: (String) -> Unit
    ): UploadedFile {
        val apiKey = getValidApiKey()
        val cleanName = if (fileName.startsWith("files/")) fileName else "files/$fileName"
        val statusUrl = "https://generativelanguage.googleapis.com/v1beta/$cleanName?key=$apiKey"

        var attempts = 0
        val maxAttempts = 60 // 60 x 3 detik = 3 menit batas maksimum polling

        while (attempts < maxAttempts) {
            attempts++
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
                            throw IOException("Gagal memeriksa status berkas (HTTP ${res.code}): ${parseErrorMessage(respBody, res.code)}")
                        }
                        val json = JSONObject(respBody)
                        val name = json.optString("name", cleanName)
                        val uri = json.optString("uri", "https://generativelanguage.googleapis.com/v1beta/$name")
                        val state = json.optString("state", "PROCESSING")
                        continuation.resume(UploadedFile(name, uri, state))
                    }
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }

            if (fileResult != null) {
                when (fileResult.state) {
                    "ACTIVE" -> {
                        onStatusUpdate("Video siap dianalisis (Status: ACTIVE)")
                        return fileResult
                    }
                    "FAILED" -> {
                        throw IOException("Pemrosesan video di server Gemini gagal. Format video mungkin tidak didukung.")
                    }
                    else -> {
                        onStatusUpdate("Memproses file video di Google AI (Status: ${fileResult.state})... [$attempts]")
                    }
                }
            }

            delay(3000L)
        }

        throw IOException("Batas waktu pemrosesan video habis (timeout 3 menit). Silakan coba lagi.")
    }

    /**
     * Menganalisis satu segmen 3 menit memakai start/end offset pada file yang sama.
     */
    suspend fun analyzeSegment(
        fileUri: String,
        mimeType: String,
        startSec: Long,
        endSec: Long,
        intervalSeconds: Int,
        previousCharacterSheet: List<CharacterInfo>,
        isFirstSegment: Boolean,
        onStatusUpdate: (String) -> Unit
    ): SegmentAnalysisOutput {
        val apiKey = getValidApiKey()
        val startFormatted = formatTimeSeconds(startSec)
        val endFormatted = formatTimeSeconds(endSec)

        val prompt = buildSegmentPrompt(
            startFormatted = startFormatted,
            endFormatted = endFormatted,
            startSec = startSec,
            endSec = endSec,
            intervalSeconds = intervalSeconds,
            previousCharacterSheet = previousCharacterSheet,
            isFirstSegment = isFirstSegment
        )

        val requestPayload = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        // File Data part dengan videoMetadata offset
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

                        // Text Prompt part
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

        val endpoint = "${GeminiConstants.GENERATE_CONTENT_BASE}/${GeminiConstants.GEMINI_MODEL}:generateContent?key=$apiKey"

        val responseJsonString = executeWithRetry(endpoint, requestPayload, onStatusUpdate)
        return parseSegmentOutput(responseJsonString)
    }

    /**
     * Membuat narasi rekap episode lengkap dari gabungan ringkasan tiap segmen.
     * Permintaan teks saja, tanpa video.
     */
    suspend fun generateFinalNarration(
        segmentSummaries: List<String>,
        characterSheet: List<CharacterInfo>,
        onStatusUpdate: (String) -> Unit
    ): String {
        val apiKey = getValidApiKey()

        val summariesText = segmentSummaries.mapIndexed { idx, sum ->
            "Segment ${idx + 1}: $sum"
        }.joinToString("\n\n")

        val charactersText = characterSheet.joinToString("\n") {
            "- ${it.name}: ${it.description}"
        }

        val prompt = """
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

        val endpoint = "${GeminiConstants.GENERATE_CONTENT_BASE}/${GeminiConstants.GEMINI_MODEL}:generateContent?key=$apiKey"
        val rawResponse = executeWithRetry(endpoint, requestPayload, onStatusUpdate)

        val json = JSONObject(rawResponse)
        val candidates = json.optJSONArray("candidates")
        if (candidates != null && candidates.length() > 0) {
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            if (parts != null && parts.length() > 0) {
                return parts.getJSONObject(0).optString("text", "").trim()
            }
        }
        throw IOException("Gagal menyusun narasi episode: respons kosong dari AI.")
    }

    /**
     * Eksekusi request dengan jeda dan auto-retry backoff saat rate limit (429/503).
     */
    private suspend fun executeWithRetry(
        url: String,
        jsonBody: String,
        onStatusUpdate: (String) -> Unit
    ): String {
        var attempt = 0
        var backoffMs = 3000L

        while (true) {
            attempt++
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

                if (statusCode in 200..299) {
                    // Jeda teratur untuk mematuhi rate-limit tier gratis
                    delay(GeminiConstants.REQUEST_DELAY_MS)
                    return responseBody
                }

                if (statusCode == 429 || statusCode == 503) {
                    if (attempt <= GeminiConstants.MAX_RETRIES) {
                        onStatusUpdate("Mencapai batas kuota sementara (HTTP $statusCode). Mencoba ulang secara otomatis dalam ${backoffMs / 1000} detik (Percobaan $attempt dari ${GeminiConstants.MAX_RETRIES})...")
                        delay(backoffMs)
                        backoffMs *= 2
                        continue
                    } else {
                        throw IOException("Batas permintaan Gemini terlampaui (429/503). Silakan tunggu beberapa menit atau periksa kuota API Key Anda di Pengaturan.")
                    }
                }

                throw IOException("Permintaan AI gagal (HTTP $statusCode): ${parseErrorMessage(responseBody, statusCode)}")

            } catch (e: IOException) {
                if (e.message?.contains("Batas permintaan Gemini") == true || e.message?.contains("Permintaan AI gagal") == true) {
                    throw e
                }
                if (attempt <= GeminiConstants.MAX_RETRIES) {
                    onStatusUpdate("Gangguan koneksi: ${e.localizedMessage}. Mencoba ulang dalam ${backoffMs / 1000} detik...")
                    delay(backoffMs)
                    backoffMs *= 2
                    continue
                } else {
                    throw IOException("Koneksi ke Gemini terputus: ${e.localizedMessage}. Periksa koneksi internet Anda.")
                }
            }
        }
    }

    private fun buildSegmentPrompt(
        startFormatted: String,
        endFormatted: String,
        startSec: Long,
        endSec: Long,
        intervalSeconds: Int,
        previousCharacterSheet: List<CharacterInfo>,
        isFirstSegment: Boolean
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
                  "visualPrompt": "2D Japanese anime style, high-definition, clean cel-shaded linework, vibrant colors, detailed background art. [Subject: Name and repeat their exact visual attributes from character sheet], [Action: specific physical movement or stance], [Expression: emotional face details], [Setting: environment, architecture, background], [Lighting: lighting source, shadows, highlights], [Camera: shot framing and angle], [Mood: emotional atmosphere]."
                }
              ]
            }
            
            CRITICAL VISUAL PROMPT RULES:
            1. Every visualPrompt MUST strictly start with: "2D Japanese anime style, high-definition, clean cel-shaded linework, vibrant colors, detailed background art". Never 3D, never photorealistic.
            2. Repeat the character's exact physical descriptions from the character sheet inside each visual prompt so text-to-image models maintain character consistency across shots.
            3. Each visual prompt must be exactly one descriptive paragraph covering: subject, action, expression, setting, lighting, camera angle, mood.
            4. Do NOT mention any animation studio names or artist names.
        """.trimIndent()
    }

    private fun parseSegmentOutput(rawJson: String): SegmentAnalysisOutput {
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
        return try {
            val json = JSONObject(errorBody)
            val errorObj = json.optJSONObject("error")
            val msg = errorObj?.optString("message") ?: errorBody
            when (statusCode) {
                400 -> "Parameter tidak valid: $msg"
                401, 403 -> "API Key tidak valid atau tidak memiliki izin akses: $msg"
                429 -> "Batas kuota gratis Gemini terlampaui. Silakan tunggu beberapa saat."
                503 -> "Layanan Gemini AI sedang sibuk. Silakan coba kembali."
                else -> msg
            }
        } catch (_: Exception) {
            if (statusCode == 429) "Batas kuota gratis Gemini terlampaui."
            else "Terjadi kendala pada server (kode $statusCode)."
        }
    }

    private fun formatTimeSeconds(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    private fun getValidApiKey(): String {
        val key = apiKeyProvider()
        if (key.isBlank()) {
            throw IOException("API Key Gemini belum diisi. Silakan masukkan API Key Anda di menu Pengaturan.")
        }
        return key
    }
}
