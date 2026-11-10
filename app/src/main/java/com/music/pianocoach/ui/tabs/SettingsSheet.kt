package com.music.pianocoach.ui.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.music.pianocoach.ui.MainViewModel
import com.music.pianocoach.ui.PianoUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(state: PianoUiState, viewModel: MainViewModel, onDismiss: () -> Unit) {
    val s = state.coachSettings
    var apiKey by remember(s.apiKey) { mutableStateOf(s.apiKey) }
    var baseUrl by remember(s.baseUrl) { mutableStateOf(s.baseUrl) }
    var model by remember(s.model) { mutableStateOf(s.model) }
    var skill by remember(s.skillLevel) { mutableStateOf(s.skillLevel) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp)) {
            Text("Piano Muse · 大模型 API")
            Text(
                "兼容 OpenAI 协议（/chat/completions）：可填 OpenAI、DeepSeek 或中转站",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                placeholder = { Text("https://api.openai.com/v1") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("模型名称") },
                placeholder = { Text("gpt-4o-mini / deepseek-chat") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = skill, onValueChange = { skill = it }, label = { Text("你的水平") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.saveSettings(apiKey, baseUrl, model, skill)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { viewModel.testApiConnection() }, modifier = Modifier.fillMaxWidth()) {
                Text("测试连接")
            }
            Text(
                "Key 仅保存在本机 DataStore。公开发布应用前请改用服务端代理。",
                modifier = Modifier.padding(top = 12.dp),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
