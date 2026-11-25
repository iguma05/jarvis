package com.jarvis.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

private val JarvisBlue = Color(0xFF1B3A4B)
private val JarvisAccent = Color(0xFF3D8B8B)
private val JarvisBg = Color(0xFF0F1C24)
private val BubbleUser = Color(0xFF244556)
private val BubbleAssistant = Color(0xFF1A2E38)

@Composable
fun JarvisApp(viewModel: JarvisViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.startListening()
    }

    LaunchedEffect(Unit) {
        viewModel.checkHealth()
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = JarvisAccent,
            background = JarvisBg,
            surface = JarvisBlue,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = JarvisBg,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                Header(state = state)
                Spacer(Modifier.height(12.dp))
                ServerRow(
                    baseUrl = state.baseUrl,
                    online = state.serverOnline,
                    onUrlChange = viewModel::onBaseUrlChange,
                    onSave = viewModel::saveBaseUrl,
                    onCheck = viewModel::checkHealth,
                )
                Spacer(Modifier.height(12.dp))
                MessageList(
                    messages = state.messages,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.statusDetail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB7C7D1),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(12.dp))
                MicButton(
                    status = state.status,
                    onPress = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            viewModel.startListening()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onRelease = viewModel::stopListeningAndSend,
                )
                Spacer(Modifier.height(16.dp))
                TextInputRow(
                    draft = state.draft,
                    enabled = state.status != JarvisStatus.Thinking &&
                        state.status != JarvisStatus.Listening,
                    onDraftChange = viewModel::onDraftChange,
                    onSend = viewModel::sendText,
                )
            }
        }
    }
}

@Composable
private fun Header(state: JarvisUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Джарвис",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE8EEF5),
        )
        val dot = when (state.serverOnline) {
            true -> Color(0xFF3DCF8E)
            false -> Color(0xFFE35D5D)
            null -> Color(0xFF8899A6)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dot),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = when (state.serverOnline) {
                    true -> "в сети"
                    false -> "не в сети"
                    null -> "…"
                },
                color = Color(0xFFB7C7D1),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ServerRow(
    baseUrl: String,
    online: Boolean?,
    onUrlChange: (String) -> Unit,
    onSave: () -> Unit,
    onCheck: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Адрес сервера") },
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave) { Text("Сохранить") }
            Button(onClick = onCheck) { Text("Проверить связь") }
        }
        if (online == false) {
            Text(
                text = "Нет связи. Проверьте IP ПК и Docker.",
                color = Color(0xFFE35D5D),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    LazyColumn(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF13232C))
            .padding(12.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(4.dp),
    ) {
        if (messages.isEmpty()) {
            item {
                Text(
                    text = "Удерживайте микрофон и говорите",
                    color = Color(0xFF7F93A1),
                )
            }
        }
        items(messages) { message ->
            val isUser = message.role == "user"
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Text(
                    text = message.text,
                    color = Color(0xFFE8EEF5),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isUser) BubbleUser else BubbleAssistant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MicButton(
    status: JarvisStatus,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    val active = status == JarvisStatus.Listening
    val busy = status == JarvisStatus.Thinking || status == JarvisStatus.Speaking
    val color = when {
        active -> Color(0xFFE35D5D)
        busy -> Color(0xFF5A6F7A)
        else -> JarvisAccent
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(color)
                .pointerInput(busy) {
                    detectTapGestures(
                        onPress = {
                            if (!busy) {
                                onPress()
                                tryAwaitRelease()
                                onRelease()
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Удерживайте, чтобы говорить",
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun TextInputRow(
    draft: String,
    enabled: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            singleLine = true,
            placeholder = { Text("Текст…") },
        )
        IconButton(onClick = onSend, enabled = enabled && draft.isNotBlank()) {
            Icon(
                imageVector = Icons.Filled.Send,
                contentDescription = "Отправить",
                tint = JarvisAccent,
            )
        }
    }
}
