package com.music.pianocoach.drill

import java.io.InputStream

/**
 * SMF Type 0/1：解析绝对时间与时值，保留和弦；按音轨/音高推断左右手。
 */
object MidiSongImporter {

    fun import(stream: InputStream, title: String = "导入曲谱"): Result<SongPiece> = runCatching {
        val data = stream.readBytes()
        require(data.size >= 14) { "文件过短，不是有效 MIDI" }
        require(data[0] == 'M'.code.toByte() && data[1] == 'T'.code.toByte()) {
            "不是 MIDI 文件（缺少 MThd）"
        }
        val format = readInt16(data, 8)
        val trackCount = readInt16(data, 10)
        val division = readInt16(data, 12)
        require(division and 0x8000 == 0) { "暂不支持 SMPTE 时间码 MIDI" }
        val ppq = division

        val timed = mutableListOf<TimedNote>()
        var i = 14
        var trackIndex = 0
        while (i + 8 < data.size) {
            if (isChunk(data, i, "MTrk")) {
                val len = readInt32(data, i + 4)
                val trackStart = i + 8
                val trackEnd = (trackStart + len).coerceAtMost(data.size)
                parseTrackTimed(data, trackStart, trackEnd, trackIndex, timed)
                trackIndex++
                i = trackEnd
            } else {
                i++
            }
        }
        require(timed.isNotEmpty()) { "未找到音符" }

        val withHands = HandAssigner.assign(timed)
        val withFingers = FingeringAssigner.assign(withHands, ppq)
        val sorted = withFingers.sortedWith(compareBy({ it.startTick }, { it.midi }))
        val chordCount = SongPiece(
            id = "tmp",
            title = title,
            timedNotes = sorted,
            ticksPerQuarter = ppq,
        ).toChordSteps().size

        SongPiece(
            id = "import_${System.currentTimeMillis()}",
            title = title.removeSuffix(".mid").removeSuffix(".midi").removeSuffix(".MID"),
            description = "MIDI · ${sorted.size} 音 · $chordCount 步 · ${format}型/$trackCount 轨",
            notes = sorted.map { SongNote(it.midi, it.durationTicks.toDouble() / ppq) },
            timedNotes = sorted,
            ticksPerQuarter = ppq,
        )
    }

    private fun isChunk(data: ByteArray, offset: Int, name: String): Boolean {
        if (offset + 4 > data.size) return false
        return name.indices.all { data[offset + it] == name[it].code.toByte() }
    }

    private fun parseTrackTimed(
        data: ByteArray,
        start: Int,
        end: Int,
        track: Int,
        out: MutableList<TimedNote>,
    ) {
        var i = start
        var tick = 0
        var runningStatus = -1
        val active = HashMap<Int, ArrayDeque<Pair<Int, Int>>>()

        fun noteOff(note: Int, at: Int) {
            val stack = active[note] ?: return
            if (stack.isEmpty()) return
            val (st, vel) = stack.removeFirst()
            val dur = (at - st).coerceAtLeast(1)
            out.add(
                TimedNote(
                    midi = note,
                    startTick = st,
                    durationTicks = dur,
                    velocity = vel,
                    track = track,
                ),
            )
        }

        while (i < end) {
            val (delta, afterDelta) = readVlq(data, i)
            tick += delta
            i = afterDelta
            if (i >= end) break

            var status = data[i].toInt() and 0xFF
            if (status < 0x80) {
                if (runningStatus < 0) break
                status = runningStatus
            } else {
                runningStatus = status
                i++
            }

            when (val msg = status and 0xF0) {
                0x90, 0x80 -> {
                    if (i + 1 >= end) break
                    val note = data[i].toInt() and 0x7F
                    val vel = data[i + 1].toInt() and 0x7F
                    i += 2
                    val isOn = msg == 0x90 && vel > 0
                    if (isOn) {
                        active.getOrPut(note) { ArrayDeque() }.addLast(tick to vel)
                    } else {
                        noteOff(note, tick)
                    }
                }
                0xA0, 0xB0, 0xE0 -> i += 2
                0xC0, 0xD0 -> i += 1
                0xF0 -> {
                    when (status) {
                        0xFF -> {
                            if (i >= end) break
                            i++
                            val (len, after) = readVlq(data, i)
                            i = after + len
                        }
                        0xF0, 0xF7 -> {
                            val (len, after) = readVlq(data, i)
                            i = after + len
                        }
                        else -> i++
                    }
                    runningStatus = -1
                }
                else -> i++
            }
        }
        active.forEach { (note, stack) ->
            while (stack.isNotEmpty()) {
                val (st, vel) = stack.removeFirst()
                out.add(TimedNote(note, st, 240, vel, track = track))
            }
        }
    }

    private fun readInt16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun readInt32(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)

    private fun readVlq(data: ByteArray, offset: Int): Pair<Int, Int> {
        var value = 0
        var i = offset
        while (i < data.size) {
            val b = data[i].toInt() and 0xFF
            value = (value shl 7) or (b and 0x7F)
            i++
            if (b and 0x80 == 0) break
        }
        return value to i
    }
}
