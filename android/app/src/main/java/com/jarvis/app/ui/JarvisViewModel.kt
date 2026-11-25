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

data class ChatMessage(
    val role: String,
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
    private var baseUrlCache = SettingsStore.DEFAULT_BASE_URL
    private val api = JarvisApi(baseUrlProvider = { baseUrlCache })
    private val recorder = WavRecorder()
    private val player = AudioPlayer()

    private val _state = MutableStateFlow(JarvisUiState())
    val state: StateFlow<JarvisUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.baseUrl.collect { url ->
                baseUrlCache = url
                _state.update { it.copy(baseUrl = url) }
            }
        }
    }

    fun onBaseUrlChange(value: String) {
        _state.update { it.copy(baseUrl = value) }
    }

    fun saveBaseUrl() {
        viewModelScope.launch {
            settings.setBaseUrl(_state.value.baseUrl)
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
            _state.update {
                it.copy(status = JarvisStatus.Listening, statusDetail = "Слушаю…")
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    status = JarvisStatus.Error,
                    statusDetail = e.message ?: "Не удалось начать запись",
                )
            }
        }
    }

    fun stopListeningAndSend() {
        if (_state.value.status != JarvisStatus.Listening) return
        viewModelScope.launch {
            val cacheDir = getApplication<Application>().cacheDir
            val wav = File(cacheDir, "utterance.wav")
            try {
                recorder.stop(wav)
                _state.update {
                    it.copy(status = JarvisStatus.Thinking, statusDetail = "Думаю…")
                }
                val response = api.voice(wav, _state.value.sessionId)
                _state.update {
                    it.copy(
                        sessionId = response.session_id,
                        messages = it.messages + listOf(
                            ChatMessage("user", response.transcript),
                            ChatMessage("assistant", response.reply),
                        ),
                        status = JarvisStatus.Speaking,
                        statusDetail = "Говорю…",
                    )
                }
                playBase64Audio(response.audio_base64)
            } catch (e: Exception) {
                val detail = when (e) {
                    is ApiException -> e.body.ifBlank { e.message.orEmpty() }
                    else -> e.message ?: "Ошибка голоса"
                }
                _state.update {
                    it.copy(status = JarvisStatus.Error, statusDetail = detail)
                }
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
                    messages = it.messages + ChatMessage("user", text),
                )
            }
            try {
                val response = api.chat(text, _state.value.sessionId)
                _state.update {
                    it.copy(
                        sessionId = response.session_id,
                        messages = it.messages + ChatMessage("assistant", response.reply),
                        status = JarvisStatus.Idle,
                        statusDetail = "Готов",
                    )
                }
            } catch (e: Exception) {
                val detail = when (e) {
                    is ApiException -> e.body.ifBlank { e.message.orEmpty() }
                    else -> e.message ?: "Ошибка чата"
                }
                _state.update {
                    it.copy(status = JarvisStatus.Error, statusDetail = detail)
                }
            }
        }
    }

    private fun playBase64Audio(audioBase64: String) {
        val bytes = Base64.decode(audioBase64, Base64.DEFAULT)
        val file = File(getApplication<Application>().cacheDir, "reply.mp3")
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

    override fun onCleared() {
        recorder.cancel()
        player.stop()
        super.onCleared()
    }
}
