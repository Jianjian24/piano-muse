package com.music.pianocoach.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.music.pianocoach.midi.BleMidiScanner
import com.music.pianocoach.midi.ConnectionState
import com.music.pianocoach.midi.MidiBleDevice
import com.music.pianocoach.midi.MidiSession
import com.music.pianocoach.midi.NoteEvent
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PianoUiState(
    val bluetoothEnabled: Boolean = false,
    val scanPermissionGranted: Boolean = false,
    val connectPermissionGranted: Boolean = false,
    val isScanning: Boolean = false,
    val devices: List<MidiBleDevice> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val connectedDeviceName: String? = null,
    val statusMessage: String? = null,
    val activeNotes: Set<Int> = emptySet(),
    val lastEvent: NoteEvent? = null,
    val recentEvents: List<NoteEvent> = emptyList(),
    val refreshHint: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(PianoUiState())
    val uiState: StateFlow<PianoUiState> = _uiState.asStateFlow()

    private val scanner = BleMidiScanner(
        context = application,
        onDevicesUpdated = { devices ->
            _uiState.update { it.copy(devices = devices) }
        },
        onScanError = { msg ->
            _uiState.update { it.copy(statusMessage = msg, isScanning = false) }
        },
    )

    private val session = MidiSession(
        context = application,
        onConnection = { state, detail ->
            _uiState.update {
                it.copy(
                    connectionState = state,
                    connectedDeviceName = if (state == ConnectionState.Connected) detail else it.connectedDeviceName,
                    statusMessage = when (state) {
                        ConnectionState.Error -> detail
                        ConnectionState.Disconnected -> null
                        else -> it.statusMessage
                    },
                )
            }
            if (state == ConnectionState.Disconnected || state == ConnectionState.Error) {
                _uiState.update { s ->
                    s.copy(activeNotes = emptySet(), connectedDeviceName = null)
                }
            }
            if (state == ConnectionState.Disconnected) {
                refreshAllDevices()
            }
        },
        onNote = { event ->
            _uiState.update { state ->
                val active = state.activeNotes.toMutableSet()
                if (event.on) {
                    active.add(event.note)
                } else {
                    active.remove(event.note)
                }
                val recent = (listOf(event) + state.recentEvents).take(100)
                state.copy(
                    activeNotes = active,
                    lastEvent = event,
                    recentEvents = recent,
                )
            }
        },
    )

    private var autoRefreshJob: Job? = null

    init {
        scanner.registerMidiTopologyCallbacks { refreshAllDevices() }
        session.registerDeviceCallback { refreshAllDevices() }
        refreshBluetoothState()
        startAutoRefreshWhileIdle()
    }

    fun refreshBluetoothState() {
        _uiState.update {
            it.copy(
                bluetoothEnabled = scanner.isBluetoothEnabled(),
                scanPermissionGranted = scanner.hasScanPermission(),
                connectPermissionGranted = scanner.hasConnectPermission(),
            )
        }
        if (scanner.hasConnectPermission()) {
            refreshAllDevices()
        }
    }

    fun onPermissionsResult() {
        refreshBluetoothState()
    }

    fun refreshAllDevices() {
        val stats = scanner.refreshAllSources()
        val hint = buildRefreshHint(stats)
        _uiState.update { it.copy(refreshHint = hint) }
    }

    fun refreshPairedDevices() = refreshAllDevices()

    private fun buildRefreshHint(stats: BleMidiScanner.RefreshStats): String {
        return when {
            stats.systemOnlineCount > 0 ->
                "系统 MIDI 在线 ${stats.systemOnlineCount} 个（可连接）"
            stats.bondedCount > 0 ->
                "已配对 ${stats.bondedCount} 个，系统 MIDI 0：虫虫连好后保持前台 2 秒再点刷新"
            !scanner.hasConnectPermission() ->
                "未授予蓝牙连接权限，无法读已配对列表"
            else ->
                "未发现设备：先在系统蓝牙配对 TERENCE，虫虫连接后回本 App"
        }
    }

    fun startAutoRefreshWhileIdle() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                if (_uiState.value.connectionState == ConnectionState.Disconnected) {
                    refreshAllDevices()
                }
                delay(2_500)
            }
        }
    }

    fun onAppResumed() {
        refreshBluetoothState()
        refreshAllDevices()
    }

    fun startScan() {
        if (!_uiState.value.bluetoothEnabled) {
            _uiState.update { it.copy(statusMessage = "请先打开手机蓝牙") }
            return
        }
        _uiState.update { it.copy(isScanning = true, statusMessage = null) }
        scanner.startScan()
    }

    fun stopScan() {
        scanner.stopScan()
        _uiState.update { it.copy(isScanning = false) }
    }

    fun connect(device: MidiBleDevice) {
        stopScan()
        val systemId = device.systemMidiDeviceId
        if (systemId != null) {
            val info = scanner.getSystemMidiDeviceInfo(systemId)
            if (info != null) {
                session.connect(info)
                return
            }
        }
        val bt = scanner.getBluetoothDevice(device.address)
        if (bt == null) {
            _uiState.update {
                it.copy(
                    connectionState = ConnectionState.Error,
                    statusMessage = "无法访问蓝牙设备。请点「刷新设备」，或先让虫虫钢琴连接琴。",
                )
            }
            return
        }
        session.connect(bt)
    }

    fun disconnect() {
        session.disconnect()
    }

    override fun onCleared() {
        autoRefreshJob?.cancel()
        stopScan()
        session.disconnect()
        super.onCleared()
    }
}
