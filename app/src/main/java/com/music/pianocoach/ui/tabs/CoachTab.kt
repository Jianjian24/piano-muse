package com.music.pianocoach.ui.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.music.pianocoach.coach.UiChatMessage
import com.music.pianocoach.ui.MainViewModel
import com.music.pianocoach.ui.PianoUiState
import com.music.pianocoach.ui.theme.MuseColors

@Composable
fun CoachTab(state: PianoUiState, viewModel: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Muse", style = MaterialTheme.typography.titleLarge, color = MuseColors.Ivory)
                Text(
                    "Ask your muse · 灵感与练习",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = { viewModel.showSettings(true) }) {
                Icon(Icons.Default.Settings, contentDescription = "设置", tint = MuseColors.IvoryDim)
            }
        }
        if (state.suggestAnalyze) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MuseColors.InkElevated)
                    .padding(14.dp),
            ) {
                Text("你刚停手了，要分析这一段吗？")
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.analyzeLastPhrase() },
                        colors = ButtonDefaults.buttonColors(containerColor = MuseColors.Amber),
                    ) { Text("分析刚才这一段") }
                    OutlinedButton(onClick = { viewModel.dismissAnalyzeSuggestion() }) { Text("稍后") }
                }
            }
        }
        state.coachError?.let {
            Text(
                it.take(200),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
            )
        }
        state.coachInfo?.let {
            Text(it, color = MuseColors.Amber, style = MaterialTheme.typography.bodySmall)
        }
        val listState = rememberLazyListState()
        val messages = state.coachMessages + if (state.coachStreaming) {
            listOf(UiChatMessage("assistant", state.coachStreamText + "▌"))
        } else {
            emptyList()
        }
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(messages.size) { index ->
                val msg = messages[index]
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically { it / 4 },
                ) {
                    ChatBubble(msg)
                }
            }
        }
        OutlinedButton(
            onClick = { viewModel.analyzeLastPhrase() },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            enabled = !state.coachStreaming,
        ) {
            Text("分析刚才这一段")
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.coachInput,
                onValueChange = viewModel::setCoachInput,
                modifier = Modifier.weight(1f),
                placeholder = { Text("写下你的问题…") },
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MuseColors.Amber,
                    unfocusedBorderColor = MuseColors.InkElevated,
                    cursorColor = MuseColors.Amber,
                ),
            )
            Button(
                onClick = { viewModel.sendCoachMessage() },
                modifier = Modifier.padding(start = 8.dp),
                enabled = !state.coachStreaming && state.coachInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MuseColors.Amber),
            ) {
                Text("发送")
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: UiChatMessage) {
    val isUser = msg.role == "user"
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isUser) 24.dp else 0.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (isUser) {
                        Modifier
                            .border(1.dp, MuseColors.Amber.copy(alpha = 0.45f), shape)
                            .background(MuseColors.InkElevated, shape)
                    } else {
                        Modifier.background(MuseColors.Sage.copy(alpha = 0.15f), shape)
                    },
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(msg.content, style = MaterialTheme.typography.bodyMedium, color = MuseColors.Ivory)
        }
    }
}
