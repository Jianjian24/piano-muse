package com.music.pianocoach.drill

/**
 * 将整曲编成紧凑文本事件，供模型「几乎看见整曲」。
 * 超长曲按步进抽样，保证仍覆盖全曲时间轴。
 */
object MidiPieceEncoder {
    private const val MAX_EVENTS = 1800

    fun encode(piece: SongPiece): String {
        val steps = piece.toChordSteps()
        val totalTicks = piece.totalTicks()
        val header = buildString {
            appendLine("title=${piece.title}")
            appendLine("id=${piece.id}")
            appendLine("ppq=${piece.ticksPerQuarter}")
            appendLine("totalTicks=$totalTicks")
            appendLine("noteCount=${piece.timedNotes.size}")
            appendLine("chordSteps=${steps.size}")
            val range = piece.pitchRange()
            appendLine("pitchRange=${range.first}-${range.last}")
            appendLine("format=note lines: t=startTick d=duration m=midi h=L|R")
        }
        val notes = piece.timedNotes.sortedWith(compareBy({ it.startTick }, { it.midi }))
        val sampled = if (notes.size <= MAX_EVENTS) {
            notes
        } else {
            // 均匀抽样，首尾必留
            val out = ArrayList<TimedNote>(MAX_EVENTS)
            val last = notes.lastIndex
            val step = notes.size.toDouble() / (MAX_EVENTS - 1)
            var i = 0.0
            repeat(MAX_EVENTS) {
                val idx = i.toInt().coerceIn(0, last)
                out.add(notes[idx])
                i += step
            }
            out.distinctBy { Triple(it.startTick, it.midi, it.hand) }
        }
        val body = sampled.joinToString("\n") { n ->
            val h = if (n.hand == PianoHand.LEFT) "L" else "R"
            "t=${n.startTick} d=${n.durationTicks} m=${n.midi} h=$h"
        }
        val trunc = if (notes.size > sampled.size) {
            "\n# truncated: showing ${sampled.size}/${notes.size} notes (timeline preserved)\n"
        } else {
            "\n"
        }
        return header + trunc + body
    }
}
