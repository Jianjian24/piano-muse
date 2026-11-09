package com.music.pianocoach.midi

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiDeviceStatus
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
class MidiSession(
    private val context: Context,
    private val onConnection: (ConnectionState, String?) -> Unit,
    private val onNote: (NoteEvent) -> Unit,
) {
    private val midiManager: MidiManager? =
        context.getSystemService(Context.MIDI_SERVICE) as? MidiManager

    private var midiDevice: MidiDevice? = null
    private var openOutputPort: android.media.midi.MidiOutputPort? = null
    private val parser = NoteParser(onNote)

    private val receiver = object : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            parser.feed(msg, offset, count, timestamp)
        }
    }

    fun connect(device: MidiDeviceInfo) {
        disconnect()
        val manager = midiManager
        if (manager == null) {
            onConnection(ConnectionState.Error, "此设备不支持 Android MIDI")
            return
        }
        onConnection(ConnectionState.Connecting, null)
        manager.openDevice(
            device,
            { opened ->
                if (opened == null) {
                    onConnection(
                        ConnectionState.Error,
                        "无法打开系统 MIDI。请确认琴已连接并被系统识别，点「刷新设备」后重试。",
                    )
                    return@openDevice
                }
                openPorts(opened)
            },
            null,
        )
    }

    fun connect(device: BluetoothDevice) {
        disconnect()
        val manager = midiManager
        if (manager == null) {
            onConnection(ConnectionState.Error, "此设备不支持 Android MIDI")
            return
        }
        onConnection(ConnectionState.Connecting, null)
        openBluetoothWithWake(device, manager, retriedAfterGatt = false)
    }

    private fun openBluetoothWithWake(
        device: BluetoothDevice,
        manager: MidiManager,
        retriedAfterGatt: Boolean,
    ) {
        manager.openBluetoothDevice(
            device,
            { opened ->
                if (opened != null) {
                    openPorts(opened)
                    return@openBluetoothDevice
                }
                if (!retriedAfterGatt) {
                    val wake = BleMidiGattWake(context).ensureBleMidiReady(device)
                    if (wake.isSuccess) {
                        openBluetoothWithWake(device, manager, retriedAfterGatt = true)
                    } else {
                        onConnection(
                            ConnectionState.Error,
                            buildDirectConnectHint(wake.exceptionOrNull()?.message),
                        )
                    }
                } else {
                    onConnection(
                        ConnectionState.Error,
                        buildDirectConnectHint(null),
                    )
                }
            },
            null,
        )
    }

    private fun buildDirectConnectHint(detail: String?): String {
        val extra = detail?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
        return "无法直接打开蓝牙 MIDI$extra。请：①手机设置里断开与琴的「蓝牙音频/媒体」仅保留配对；" +
            "②关琴再开；③在本 App 点「刷新设备」后连接。仍失败可尝试 USB OTG 或其它 MIDI 桥接应用建立系统连接后再刷新。"
    }

    private fun openPorts(device: MidiDevice) {
        midiDevice = device
        val ports = device.info.ports
        var outputPortNumber = -1
        for (port in ports) {
            if (port.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT) {
                outputPortNumber = port.portNumber
                break
            }
        }
        if (outputPortNumber < 0) {
            device.close()
            midiDevice = null
            onConnection(ConnectionState.Error, "设备没有 MIDI 输出端口（琴→手机）")
            return
        }
        try {
            openOutputPort = device.openOutputPort(outputPortNumber)
            openOutputPort?.connect(receiver)
            parser.reset()
            onConnection(ConnectionState.Connected, deviceDisplayName(device.info))
        } catch (e: Exception) {
            disconnect()
            onConnection(ConnectionState.Error, "打开 MIDI 端口失败: ${e.message}")
        }
    }

    fun disconnect() {
        try {
            openOutputPort?.disconnect(receiver)
        } catch (_: Exception) {
        }
        try {
            openOutputPort?.close()
        } catch (_: Exception) {
        }
        openOutputPort = null
        try {
            midiDevice?.close()
        } catch (_: Exception) {
        }
        midiDevice = null
        parser.reset()
        onConnection(ConnectionState.Disconnected, null)
    }

    fun registerDeviceCallback(onTopologyChanged: () -> Unit) {
        midiManager?.registerDeviceCallback(
            object : MidiManager.DeviceCallback() {
                override fun onDeviceAdded(device: MidiDeviceInfo) {
                    onTopologyChanged()
                }

                override fun onDeviceRemoved(device: MidiDeviceInfo) {
                    if (midiDevice?.info?.id == device.id) {
                        disconnect()
                    }
                    onTopologyChanged()
                }

                override fun onDeviceStatusChanged(status: MidiDeviceStatus) = Unit
            },
            null,
        )
    }

    private fun deviceDisplayName(info: MidiDeviceInfo): String {
        val props = info.properties
        return props.getString(MidiDeviceInfo.PROPERTY_NAME)
            ?: props.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
            ?: "MIDI #${info.id}"
    }
}
