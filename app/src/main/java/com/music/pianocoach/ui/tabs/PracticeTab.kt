package com.music.pianocoach.ui.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.pianocoach.midi.ConnectionState
import com.music.pianocoach.ui.EventLog
import com.music.pianocoach.ui.LastNoteCard
import com.music.pianocoach.ui.MainViewModel
import com.music.pianocoach.ui.PianoKeyboard
import com.music.pianocoach.ui.PianoUiState

@Composable
fun PracticeTab(state: PianoUiState, viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("练习", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        ConnectionSection(state, viewModel)
        if (state.connectionState == ConnectionState.Connected) {
            Spacer(Modifier.height(12.dp))
            LastNoteCard(state.lastEvent)
            Spacer(Modifier.height(8.dp))
            PianoKeyboard(activeNotes = state.activeNotes, focusNote = state.lastEvent?.note)
            Spacer(Modifier.height(8.dp))
            EventLog(state.recentEvents)
        } else {
            HelpCard()
        }
    }
}

@Composable
private fun ConnectionSection(state: PianoUiState, viewModel: MainViewModel) {
    val canScan = state.bluetoothEnabled && state.scanPermissionGranted
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.isScanning) {
            Button(onClick = { viewModel.stopScan() }) {
                Icon(Icons.Default.BluetoothSearching, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("停止")
            }
        } else {
            Button(
                onClick = { viewModel.startScan() },
                enabled = canScan && state.connectionState != ConnectionState.Connecting,
            ) {
                Icon(Icons.Default.Bluetooth, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("扫描")
            }
            OutlinedButton(onClick = { viewModel.refreshPairedDevices() }) {
                Text("刷新")
            }
        }
        if (state.connectionState == ConnectionState.Connected) {
            OutlinedButton(onClick = { viewModel.disconnect() }) {
                Icon(Icons.Default.LinkOff, contentDescription = null)
                Text("断开")
            }
        }
    }
    val status = state.statusMessage ?: state.refreshHint ?: "连接 MIDI 后即可弹奏"
    Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    if (state.connectionState != ConnectionState.Connected) {
        state.devices.forEach { device ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { viewModel.connect(device) },
            ) {
                ListItem(
                    headlineContent = { Text(device.name) },
                    supportingContent = { Text(device.address) },
                )
            }
        }
    }
}

@Composable
private fun HelpCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("连接提示", style = MaterialTheme.typography.titleSmall)
            Text("可先用虫虫钢琴连接 TERENCE，回本 App 点「刷新」选「系统 MIDI 在线」。", style = MaterialTheme.typography.bodySmall)
        }
    }
}
