package com.music.pianocoach.midi

data class NoteEvent(
    val on: Boolean,
    val note: Int,
    val velocity: Int,
    val channel: Int,
    val timeNanos: Long,
)

data class MidiBleDevice(
    val name: String,
    val address: String,
    val bonded: Boolean,
    /** 广播包中可见标准 BLE MIDI 服务 */
    val advertisesBleMidi: Boolean = false,
    /** Android MidiManager 已枚举到的系统 MIDI 设备 */
    val systemMidiDeviceId: String? = null,
    /** 本次刷新时系统报告该 MIDI 设备在线（被其它应用占用时可能为 false 但条目仍保留） */
    val systemMidiOnline: Boolean = false,
)

enum class ConnectionState {
    Disconnected,
    Connecting,
    Connected,
    Error,
}

private val NOTE_NAMES = arrayOf(
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
)

fun midiNoteToLabel(note: Int): String {
    if (note !in 0..127) return "?"
    val name = NOTE_NAMES[note % 12]
    val octave = note / 12 - 1
    return "$name$octave"
}

const val PIANO_KEY_MIN = 21
const val PIANO_KEY_MAX = 108
