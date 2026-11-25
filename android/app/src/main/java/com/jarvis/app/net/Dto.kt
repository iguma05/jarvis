package com.jarvis.app.net

data class HealthResponse(
    val status: String,
)

data class ChatResponse(
    val sessionId: String,
    val reply: String,
)

data class VoiceResponse(
    val sessionId: String,
    val transcript: String,
    val reply: String,
    val audioBase64: String,
    val audioMime: String,
)
