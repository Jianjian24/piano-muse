package com.music.pianocoach.ui.tabs

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.pianocoach.coach.UiChatMessage
import com.music.pianocoach.ui.MainViewModel
import com.music.pianocoach.ui.PianoUiState

@Composable
fun CoachTab(state: PianoUiState, viewModel: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("教练", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { viewModel.showSettings(true) }) {
                Icon(Icons.Default.Settings, contentDescription = "设置")
            }
        }
        if (state.suggestAnalyze) {
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("你刚停手了，要分析这一段吗？")
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.analyzeLastPhrase() }) { Text("分析刚才这一段") }
                        OutlinedButton(onClick = { viewModel.dismissAnalyzeSuggestion() }) { Text("稍后") }
                    }
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
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages) { msg -> ChatBubble(msg) }
        }
        OutlinedButton(
            onClick = { viewModel.analyzeLastPhrase() },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            enabled = !state.coachStreaming,
        ) {
            Text("分析刚才这一段")
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.coachInput,
                onValueChange = viewModel::setCoachInput,
                modifier = Modifier.weight(1f),
                placeholder = { Text("问教练：怎么加花、怎么练…") },
                maxLines = 3,
            )
            Button(
                onClick = { viewModel.sendCoachMessage() },
                modifier = Modifier.padding(start = 8.dp),
                enabled = !state.coachStreaming && state.coachInput.isNotBlank(),
            ) {
                Text("发送")
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: UiChatMessage) {
    val isUser = msg.role == "user"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isUser) 32.dp else 0.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .background(
                    if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.shapes.medium,
                )
                .padding(12.dp),
        ) {
            Text(msg.content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
