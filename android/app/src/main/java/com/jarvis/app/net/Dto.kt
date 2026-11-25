package com.jarvis.app.net

data class HealthResponse(
    val status: String,
)

data class ChatRequest(
    val message: String,
    val session_id: String? = null,
)

data class ChatResponse(
    val session_id: String,
    val reply: String,
)

data class VoiceResponse(
    val session_id: String,
    val transcript: String,
    val reply: String,
    val audio_base64: String,
    val audio_mime: String,
)
