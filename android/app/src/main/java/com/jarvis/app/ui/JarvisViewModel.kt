package com.jarvis.app.ui

import android.app.Application
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.app.audio.AudioPlayer
import com.jarvis.app.audio.WavRecorder
import com.jarvis.app.data.SettingsStore
import com.jarvis.app.net.ApiException
import com.jarvis.app.net.JarvisApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class JarvisStatus {
    Idle,
    Listening,
    Thinking,
    Speaking,
    Error,
}

enum class MessageRole {
    User,
    Assistant,
}

data class ChatMessage(
    val role: MessageRole,
    val text: String,
)

data class JarvisUiState(
    val baseUrl: String = SettingsStore.DEFAULT_BASE_URL,
    val status: JarvisStatus = JarvisStatus.Idle,
    val statusDetail: String = "Готов",
    val serverOnline: Boolean? = null,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val sessionId: String? = null,
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsStore(application)
    private val api = JarvisApi(baseUrlProvider = { _state.value.baseUrl })
    private val recorder = WavRecorder()
    private val player = AudioPlayer()

    private val _state = MutableStateFlow(JarvisUiState())
    val state: StateFlow<JarvisUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.baseUrl.collect { url ->
                _state.update { it.copy(baseUrl = url) }
            }
        }
    }

    fun onBaseUrlChange(value: String) {
        _state.update { it.copy(baseUrl = value) }
    }

    fun saveBaseUrl() {
        viewModelScope.launch {
            val normalized = _state.value.baseUrl.trim().trimEnd('/')
            _state.update { it.copy(baseUrl = normalized) }
            settings.setBaseUrl(normalized)
            checkHealth()
        }
    }

    fun onDraftChange(value: String) {
        _state.update { it.copy(draft = value) }
    }

    fun checkHealth() {
        viewModelScope.launch {
            try {
                val health = api.health()
                val online = health.status == "ok"
                _state.update {
                    it.copy(
                        serverOnline = online,
                        statusDetail = if (online) "Сервер в сети" else "Сервер ответил странно",
                        status = if (it.status == JarvisStatus.Error) JarvisStatus.Idle else it.status,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        serverOnline = false,
                        status = JarvisStatus.Error,
                        statusDetail = e.message ?: "Нет связи с сервером",
                    )
                }
            }
        }
    }

    fun startListening() {
        if (_state.value.status == JarvisStatus.Listening ||
            _state.value.status == JarvisStatus.Thinking
        ) {
            return
        }
        try {
            player.stop()
            recorder.start()
            setStatus(JarvisStatus.Listening, "Слушаю…")
        } catch (e: Exception) {
            setStatus(JarvisStatus.Error, e.message ?: "Не удалось начать запись")
        }
    }

    fun stopListeningAndSend() {
        if (_state.value.status != JarvisStatus.Listening) return
        viewModelScope.launch {
            val cacheDir = getApplication<Application>().cacheDir
            val wav = File(cacheDir, "utterance.wav")
            try {
                recorder.stop(wav)
                setStatus(JarvisStatus.Thinking, "Думаю…")
                val response = api.voice(wav, _state.value.sessionId)
                _state.update {
                    it.copy(
                        sessionId = response.sessionId,
                        messages = it.messages + listOf(
                            ChatMessage(MessageRole.User, response.transcript),
                            ChatMessage(MessageRole.Assistant, response.reply),
                        ),
                        status = JarvisStatus.Speaking,
                        statusDetail = "Говорю…",
                    )
                }
                playBase64Audio(response.audioBase64, response.audioMime)
            } catch (e: Exception) {
                setStatus(JarvisStatus.Error, errorDetail(e, "Ошибка голоса"))
            } finally {
                wav.delete()
            }
        }
    }

    fun sendText() {
        val text = _state.value.draft.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    draft = "",
                    status = JarvisStatus.Thinking,
                    statusDetail = "Думаю…",
                    messages = it.messages + ChatMessage(MessageRole.User, text),
                )
            }
            try {
                val response = api.chat(text, _state.value.sessionId)
                _state.update {
                    it.copy(
                        sessionId = response.sessionId,
                        messages = it.messages + ChatMessage(MessageRole.Assistant, response.reply),
                        status = JarvisStatus.Idle,
                        statusDetail = "Готов",
                    )
                }
            } catch (e: Exception) {
                setStatus(JarvisStatus.Error, errorDetail(e, "Ошибка чата"))
            }
        }
    }

    private fun playBase64Audio(audioBase64: String, audioMime: String) {
        val bytes = Base64.decode(audioBase64, Base64.DEFAULT)
        val extension = when {
            audioMime.contains("wav") -> "wav"
            audioMime.contains("ogg") -> "ogg"
            else -> "mp3"
        }
        val file = File(getApplication<Application>().cacheDir, "reply.$extension")
        file.writeBytes(bytes)
        player.playFile(file) {
            _state.update {
                if (it.status == JarvisStatus.Speaking) {
                    it.copy(status = JarvisStatus.Idle, statusDetail = "Готов")
                } else {
                    it
                }
            }
        }
    }

    private fun setStatus(status: JarvisStatus, detail: String) {
        _state.update { it.copy(status = status, statusDetail = detail) }
    }

    private fun errorDetail(e: Exception, fallback: String): String =
        when (e) {
            is ApiException -> e.body.ifBlank { e.message.orEmpty() }.ifBlank { fallback }
            else -> e.message ?: fallback
        }

    override fun onCleared() {
        recorder.cancel()
        player.stop()
        super.onCleared()
    }
}
