package com.music.pianocoach.midi

/**
 * 解析标准 MIDI 通道语音消息中的 Note On / Note Off。
 * 忽略时钟 (0xF8)、Active Sensing (0xFE) 等系统实时消息。
 */
class NoteParser(
    private val onNote: (NoteEvent) -> Unit,
) {
    private var runningStatus: Int = -1

    fun reset() {
        runningStatus = -1
    }

    fun feed(data: ByteArray, offset: Int, count: Int, timeNanos: Long) {
        var i = offset
        val end = offset + count
        while (i < end) {
            val b = data[i].toInt() and 0xFF
            when {
                b == 0xF8 || b == 0xF9 || b == 0xFA || b == 0xFB || b == 0xFC || b == 0xFD -> {
                    i++
                }
                b == 0xFE || b == 0xF1 -> {
                    i++
                }
                b >= 0xF0 -> {
                    runningStatus = -1
                    i++
                }
                b >= 0x80 -> {
                    runningStatus = b
                    i++
                    i = parseVoiceMessage(b, data, i, end, timeNanos)
                }
                runningStatus >= 0 -> {
                    i = parseVoiceMessage(runningStatus, data, i, end, timeNanos)
                }
                else -> {
                    i++
                }
            }
        }
    }

    private fun parseVoiceMessage(status: Int, data: ByteArray, start: Int, end: Int, timeNanos: Long): Int {
        val msgType = status and 0xF0
        val channel = status and 0x0F
        return when (msgType) {
            0x90, 0x80 -> {
                if (start + 2 > end) return start
                val note = data[start].toInt() and 0x7F
                val velocity = data[start + 1].toInt() and 0x7F
                val isOn = msgType == 0x90 && velocity > 0
                val isOff = msgType == 0x80 || (msgType == 0x90 && velocity == 0)
                if (isOn) {
                    onNote(NoteEvent(on = true, note = note, velocity = velocity, channel = channel, timeNanos = timeNanos))
                } else if (isOff) {
                    onNote(NoteEvent(on = false, note = note, velocity = velocity, channel = channel, timeNanos = timeNanos))
                }
                start + 2
            }
            0xA0, 0xB0, 0xE0 -> {
                if (start + 2 > end) return start
                start + 2
            }
            0xC0, 0xD0 -> {
                if (start + 1 > end) return start
                start + 1
            }
            else -> start
        }
    }
}
