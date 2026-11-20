package com.example.data.api

import com.example.util.QuotaHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object GeminiKeyTester {

    suspend fun testApiKeyWithBase(
        apiKey: String,
        model: String,
        generateContentBase: String,
        activeTestClient: OkHttpClient,
        totalTimeoutMs: Long,
        onProgress: ((String) -> Unit)?,
        parseErrorMessage: (String, Int) -> String
    ): GeminiApiClient.KeyTestResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext GeminiApiClient.KeyTestResult(false, null, "API Key kosong.")
        }
        val trimmedKey = apiKey.trim()
        val cleanModel = model.trim().ifBlank { GeminiConstants.GEMINI_MODEL }
        val startNanos = System.nanoTime()

        fun elapsedMillis(): Long = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(0L)

        try {
            val timedResult = withTimeoutOrNull(totalTimeoutMs) {
                // Tahap 1: Cek validitas key dengan GET /models?pageSize=1
                val validationUrl = GeminiApiClient.buildModelsValidationEndpoint(generateContentBase)
                val validationRequest = Request.Builder()
                    .url(validationUrl)
                    .addHeader("x-goog-api-key", trimmedKey)
                    .get()
                    .build()

                val (stage1Code, stage1Body) = executeCancellable(activeTestClient, validationRequest)
                if (stage1Code !in 200..299) {
                    val elapsedMs = elapsedMillis()
                    val timeLabel = GeminiApiClient.formatKeyTestElapsed(elapsedMs)
                    val msg = if (stage1Code == 400 || stage1Code == 401 || stage1Code == 403) {
                        "Key tidak valid ($timeLabel)"
                    } else {
                        "${buildKeyTestHttpErrorMessage(stage1Code, stage1Body, parseErrorMessage)} ($timeLabel)"
                    }
                    return@withTimeoutOrNull GeminiApiClient.KeyTestResult(
                        isSuccess = false,
                        httpCode = stage1Code,
                        message = msg,
                        elapsedMs = elapsedMs
                    )
                }

                onProgress?.invoke(GeminiApiClient.KEY_TEST_STAGE_VALID_QUOTA_MSG)

                // Tahap 2: Ping generateContent untuk cek kuota & ketersediaan model
                val trimmedGenerateBase = generateContentBase.trimEnd('/')
                val endpoint = "$trimmedGenerateBase/$cleanModel:generateContent"
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
                    .addHeader("x-goog-api-key", trimmedKey)
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                val (stage2Code, stage2Body) = executeCancellable(activeTestClient, request)
                val elapsedMs = elapsedMillis()
                val timeLabel = GeminiApiClient.formatKeyTestElapsed(elapsedMs)

                if (stage2Code in 200..299) {
                    GeminiApiClient.KeyTestResult(
                        isSuccess = true,
                        httpCode = stage2Code,
                        message = "Key aktif & kuota tersedia ($timeLabel)",
                        elapsedMs = elapsedMs
                    )
                } else {
                    val baseErrorMsg = buildKeyTestHttpErrorMessage(stage2Code, stage2Body, parseErrorMessage)
                    GeminiApiClient.KeyTestResult(
                        isSuccess = false,
                        httpCode = stage2Code,
                        message = "$baseErrorMsg ($timeLabel)",
                        elapsedMs = elapsedMs
                    )
                }
            }

            val timeoutElapsed = elapsedMillis()
            timedResult ?: GeminiApiClient.KeyTestResult(
                isSuccess = false,
                httpCode = null,
                message = "${GeminiApiClient.KEY_TEST_TIMEOUT_MESSAGE} (${GeminiApiClient.formatKeyTestElapsed(timeoutElapsed)})",
                elapsedMs = timeoutElapsed
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val elapsedMs = elapsedMillis()
            val baseMsg = if (GeminiApiClient.isTimeoutThrowable(e)) {
                GeminiApiClient.KEY_TEST_TIMEOUT_MESSAGE
            } else {
                GeminiApiClient.KEY_TEST_CONNECTION_ERROR_MESSAGE
            }
            GeminiApiClient.KeyTestResult(
                isSuccess = false,
                httpCode = null,
                message = "$baseMsg (${GeminiApiClient.formatKeyTestElapsed(elapsedMs)})",
                elapsedMs = elapsedMs
            )
        }
    }

    private fun buildKeyTestHttpErrorMessage(
        statusCode: Int,
        body: String,
        parseErrorMessage: (String, Int) -> String
    ): String {
        val quotaCheck = QuotaHelper.checkQuotaError(statusCode, body)
        return when {
            quotaCheck.isDailyQuota -> quotaCheck.userMessage
            statusCode == 429 -> "Batas kuota per menit terlampaui (HTTP 429)."
            statusCode == 400 || statusCode == 401 || statusCode == 403 ->
                "Key tidak valid atau belum aktif (HTTP $statusCode)."
            statusCode == 404 -> "Model tidak ditemukan (HTTP 404)."
            statusCode == 503 -> "Server Google AI sibuk (HTTP 503)."
            else -> parseErrorMessage(body, statusCode)
        }
    }

    private suspend fun executeCancellable(
        httpClient: OkHttpClient,
        request: Request
    ): Pair<Int, String> = suspendCancellableCoroutine { continuation ->
        val call = httpClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    response.use { res ->
                        val body = res.body?.string().orEmpty()
                        if (continuation.isActive) {
                            continuation.resume(res.code to body)
                        }
                    }
                } catch (e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                } catch (t: Throwable) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(t)
                    }
                }
            }
        })
    }
}
