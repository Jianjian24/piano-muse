package com.music.pianocoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.music.pianocoach.midi.ConnectionState
import com.music.pianocoach.midi.NoteEvent
import com.music.pianocoach.midi.PIANO_KEY_MAX
import com.music.pianocoach.midi.PIANO_KEY_MIN
import com.music.pianocoach.midi.midiNoteToLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PianoScreen(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "蓝牙 MIDI 音符验证",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        ConnectionSection(uiState, viewModel)
        if (uiState.connectionState == ConnectionState.Connected) {
            Spacer(modifier = Modifier.height(12.dp))
            LastNoteCard(uiState.lastEvent)
            Spacer(modifier = Modifier.height(8.dp))
            PianoKeyboard(
                activeNotes = uiState.activeNotes,
                focusNote = uiState.lastEvent?.note,
            )
            Spacer(modifier = Modifier.height(8.dp))
            EventLog(events = uiState.recentEvents)
        } else {
            Spacer(modifier = Modifier.height(12.dp))
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
                Text("停止扫描")
            }
        } else {
            Button(
                onClick = { viewModel.startScan() },
                enabled = canScan && state.connectionState != ConnectionState.Connecting,
            ) {
                Icon(Icons.Default.Bluetooth, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("扫描设备")
            }
            OutlinedButton(onClick = { viewModel.refreshPairedDevices() }) {
                Text("刷新设备")
            }
        }
        if (state.connectionState == ConnectionState.Connected) {
            OutlinedButton(onClick = { viewModel.disconnect() }) {
                Icon(Icons.Default.LinkOff, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("断开")
            }
        }
    }
    val statusLine = when {
        !state.bluetoothEnabled -> "蓝牙未开启"
        !state.scanPermissionGranted -> "需要蓝牙（及定位）权限才能扫描"
        state.connectionState == ConnectionState.Connecting -> "正在连接…"
        state.connectionState == ConnectionState.Connected ->
            "已连接: ${state.connectedDeviceName ?: "MIDI 设备"}"
        state.statusMessage != null -> state.statusMessage
        state.refreshHint != null -> state.refreshHint
        state.isScanning -> "正在扫描蓝牙设备（含已配对）…"
        state.devices.isEmpty() -> "列表为空：虫虫连接琴后切回本 App，点「刷新设备」"
        else -> "点选下方设备连接"
    }
    Text(
        text = statusLine,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    if (state.connectionState != ConnectionState.Connected) {
        DeviceList(state, viewModel)
    }
}

@Composable
private fun DeviceList(state: PianoUiState, viewModel: MainViewModel) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        state.devices.forEach { device ->
            val canConnect = state.connectPermissionGranted &&
                state.connectionState != ConnectionState.Connecting
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .then(
                        if (canConnect) {
                            Modifier.clickable { viewModel.connect(device) }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                ListItem(
                    headlineContent = { Text(device.name) },
                    supportingContent = {
                        val tags = buildList {
                            add(device.address)
                            if (device.bonded) add("已配对")
                            when {
                                device.systemMidiOnline -> add("系统 MIDI 在线")
                                device.systemMidiDeviceId != null -> add("系统 MIDI 离线/被占用")
                            }
                            if (device.advertisesBleMidi) add("广播 BLE MIDI") else if (device.bonded) {
                                add("可尝试连接")
                            }
                        }
                        Text(tags.joinToString(" · "))
                    },
                )
            }
        }
    }
}

@Composable
private fun LastNoteCard(last: NoteEvent?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("当前音", style = MaterialTheme.typography.labelMedium)
            Text(
                text = last?.let { midiNoteToLabel(it.note) } ?: "—",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
            )
            if (last != null) {
                Text(
                    text = "${if (last.on) "按下" else "松开"} · 力度 ${last.velocity} · 通道 ${last.channel + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun isBlackKey(note: Int): Boolean {
    val pc = note % 12
    return pc in setOf(1, 3, 6, 8, 10)
}

@Composable
private fun PianoKeyboard(activeNotes: Set<Int>, focusNote: Int?) {
    val whiteWidth = 28.dp
    val blackWidth = 18.dp
    val keyHeight = 120.dp
    val keys = (PIANO_KEY_MIN..PIANO_KEY_MAX).toList()
    val whiteKeys = keys.filter { !isBlackKey(it) }
    val totalWhiteWidth = whiteWidth * whiteKeys.size
    val scroll = rememberScrollState()
    val focusIndex = remember(focusNote, whiteKeys) {
        focusNote?.let { n -> whiteKeys.indexOf(n).takeIf { it >= 0 } }
    }
    LaunchedEffect(focusIndex) {
        focusIndex?.let { idx ->
            val px = (idx * 28).coerceAtLeast(0)
            scroll.animateScrollTo(px)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
    ) {
        Row(modifier = Modifier.width(totalWhiteWidth)) {
            whiteKeys.forEach { note ->
                val active = note in activeNotes
                Box(
                    modifier = Modifier
                        .width(whiteWidth)
                        .height(keyHeight)
                        .background(
                            if (active) Color(0xFF4CAF50) else Color(0xFFF5F5F5),
                            RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp),
                        ),
                )
            }
        }
        keys.filter(::isBlackKey).forEach { note ->
            val whitesBefore = (PIANO_KEY_MIN until note).count { !isBlackKey(it) }
            val left = whiteWidth * whitesBefore - blackWidth / 2
            val active = note in activeNotes
            Box(
                modifier = Modifier
                    .offset(x = left)
                    .width(blackWidth)
                    .height(keyHeight * 0.62f)
                    .background(
                        if (active) Color(0xFF2E7D32) else Color(0xFF212121),
                        RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp),
                    ),
            )
        }
    }
}

@Composable
private fun EventLog(events: List<NoteEvent>) {
    Text("事件日志", style = MaterialTheme.typography.titleMedium)
    val listState = rememberLazyListState()
    LaunchedEffect(events.firstOrNull()) {
        if (events.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
    ) {
        items(events, key = { "${it.timeNanos}-${it.note}-${it.on}" }) { event ->
            val time = remember(event.timeNanos) {
                SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            }
            Text(
                text = "$time · ${if (event.on) "On" else "Off"} · ${midiNoteToLabel(event.note)} · v${event.velocity} · ch${event.channel + 1}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun HelpCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("连接提示", fontWeight = FontWeight.SemiBold)
            Text(
                "【不经过虫虫】手机「设置→蓝牙」里断开与 TERENCE 的音频/媒体连接（官方要求，否则 MIDI 无效）；重启琴；在此点「刷新设备」→ 点「TERENCE · 已配对」直接连。",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "【仍连不上时】可暂时用虫虫连接琴，再回本 App 刷新，选「系统 MIDI 在线」。断开后若列表空，虫虫重连一次或按上面步骤重启琴。",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "【最稳】Type-C USB OTG 连琴走有线 MIDI（多数特伦斯型号支持），后续版本可加。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
