package com.jarvis.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class JarvisApi(
    private val baseUrlProvider: () -> String,
    private val client: OkHttpClient = defaultClient(),
) {
    private fun root(): String = baseUrlProvider().trimEnd('/')

    suspend fun health(): HealthResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${root()}/health").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException(response.code, response.body?.string().orEmpty())
            }
            val json = JSONObject(response.body!!.string())
            HealthResponse(status = json.getString("status"))
        }
    }

    suspend fun chat(message: String, sessionId: String?): ChatResponse =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("message", message)
                .apply {
                    if (!sessionId.isNullOrBlank()) put("session_id", sessionId)
                }
            val body = payload.toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url("${root()}/v1/chat")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ApiException(response.code, text)
                }
                val json = JSONObject(text)
                ChatResponse(
                    session_id = json.getString("session_id"),
                    reply = json.getString("reply"),
                )
            }
        }

    suspend fun voice(audioFile: File, sessionId: String?): VoiceResponse =
        withContext(Dispatchers.IO) {
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "audio",
                    audioFile.name,
                    audioFile.asRequestBody("audio/wav".toMediaType()),
                )
                .apply {
                    if (!sessionId.isNullOrBlank()) {
                        addFormDataPart("session_id", sessionId)
                    }
                }
                .build()
            val request = Request.Builder()
                .url("${root()}/v1/voice")
                .post(multipart)
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ApiException(response.code, text)
                }
                val json = JSONObject(text)
                VoiceResponse(
                    session_id = json.getString("session_id"),
                    transcript = json.getString("transcript"),
                    reply = json.getString("reply"),
                    audio_base64 = json.getString("audio_base64"),
                    audio_mime = json.optString("audio_mime", "audio/mpeg"),
                )
            }
        }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()
    }
}

class ApiException(val code: Int, val body: String) : Exception("HTTP $code: $body")
