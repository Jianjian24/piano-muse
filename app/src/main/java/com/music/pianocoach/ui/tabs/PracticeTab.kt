package com.music.pianocoach.ui.tabs

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.music.pianocoach.midi.ConnectionState
import com.music.pianocoach.ui.EventLog
import com.music.pianocoach.ui.LastNoteCard
import com.music.pianocoach.ui.MainViewModel
import com.music.pianocoach.ui.PianoKeyboard
import com.music.pianocoach.ui.PianoUiState
import com.music.pianocoach.ui.theme.MuseColors

@Composable
fun PracticeTab(state: PianoUiState, viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text("Studio", style = MaterialTheme.typography.titleLarge, color = MuseColors.Ivory)
        Text(
            "Connect your piano · listen to what you play",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        ConnectionSection(state, viewModel)
        if (state.connectionState == ConnectionState.Connected) {
            Spacer(Modifier.height(16.dp))
            LastNoteCard(state.lastEvent)
            Spacer(Modifier.height(12.dp))
            PianoKeyboard(activeNotes = state.activeNotes, focusNote = state.lastEvent?.note)
            Spacer(Modifier.height(12.dp))
            EventLog(state.recentEvents)
        } else {
            HelpPanel()
        }
    }
}

@Composable
private fun ConnectionSection(state: PianoUiState, viewModel: MainViewModel) {
    val canScan = state.bluetoothEnabled && state.scanPermissionGranted
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.isScanning) {
            Button(
                onClick = { viewModel.stopScan() },
                colors = ButtonDefaults.buttonColors(containerColor = MuseColors.Amber),
            ) {
                Icon(Icons.Default.BluetoothSearching, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Stop")
            }
        } else {
            Button(
                onClick = { viewModel.startScan() },
                enabled = canScan && state.connectionState != ConnectionState.Connecting,
                colors = ButtonDefaults.buttonColors(containerColor = MuseColors.Amber),
            ) {
                Icon(Icons.Default.Bluetooth, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Scan")
            }
            OutlinedButton(onClick = { viewModel.refreshPairedDevices() }) {
                Text("Refresh")
            }
        }
        if (state.connectionState == ConnectionState.Connected) {
            OutlinedButton(onClick = { viewModel.disconnect() }) {
                Icon(Icons.Default.LinkOff, contentDescription = null)
                Text("Disconnect")
            }
        }
    }
    val status = state.statusMessage ?: state.refreshHint ?: "连接 MIDI 后即可弹奏"
    Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
    if (state.connectionState != ConnectionState.Connected) {
        state.devices.forEach { device ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MuseColors.InkElevated)
                    .clickable { viewModel.connect(device) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(device.name, style = MaterialTheme.typography.bodyMedium, color = MuseColors.Ivory)
                    Text(device.address, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun HelpPanel() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MuseColors.InkElevated)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("MIDI 连接", style = MaterialTheme.typography.titleSmall, color = MuseColors.Amber)
        Text(
            "可先用虫虫钢琴连接 TERENCE，回本 App 点「刷新」选「系统 MIDI 在线」。",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
