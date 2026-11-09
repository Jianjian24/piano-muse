package com.music.pianocoach.drill

import kotlin.math.abs
import kotlin.math.sign

/**
 * 规则指法（MVP）：按左右手、时序与音程启发式标 1–5。
 * 仅作提示，不参与跟练判定。
 */
object FingeringAssigner {

    fun assign(notes: List<TimedNote>): List<TimedNote> {
        if (notes.isEmpty()) return notes
        val left = assignHand(notes.filter { it.hand == PianoHand.LEFT }, PianoHand.LEFT)
        val right = assignHand(notes.filter { it.hand == PianoHand.RIGHT }, PianoHand.RIGHT)
        val byKey = (left + right).associateBy { Triple(it.startTick, it.midi, it.hand) }
        return notes.map { n ->
            byKey[Triple(n.startTick, n.midi, n.hand)]?.let { n.copy(finger = it.finger) }
                ?: n.copy(finger = defaultFinger(n.midi, n.hand))
        }
    }

    private fun assignHand(notes: List<TimedNote>, hand: PianoHand): List<TimedNote> {
        if (notes.isEmpty()) return notes
        val sorted = notes.sortedWith(compareBy({ it.startTick }, { it.midi }))
        val out = ArrayList<TimedNote>(sorted.size)
        var prevMidi: Int? = null
        var prevFinger: Int? = null
        var i = 0
        while (i < sorted.size) {
            val t0 = sorted[i].startTick
            val chord = mutableListOf<TimedNote>()
            while (i < sorted.size && sorted[i].startTick == t0) {
                chord.add(sorted[i])
                i++
            }
            val fingered = if (chord.size == 1) {
                val n = chord[0]
                val f = nextMelodicFinger(prevMidi, prevFinger, n.midi, hand)
                prevMidi = n.midi
                prevFinger = f
                listOf(n.copy(finger = f))
            } else {
                val mapped = assignChord(chord, hand)
                // 旋律连贯：用和弦中最靠近前一音的音更新状态
                val anchor = if (prevMidi != null) {
                    mapped.minByOrNull { abs(it.midi - prevMidi!!) } ?: mapped.first()
                } else {
                    mapped.first()
                }
                prevMidi = anchor.midi
                prevFinger = anchor.finger
                mapped
            }
            out.addAll(fingered)
        }
        return out
    }

    /** 和弦：右手低音近拇指(1)，左手低音近小指(5)。 */
    private fun assignChord(chord: List<TimedNote>, hand: PianoHand): List<TimedNote> {
        val ordered = chord.sortedBy { it.midi }
        val n = ordered.size
        return ordered.mapIndexed { idx, note ->
            val finger = when (hand) {
                PianoHand.RIGHT -> {
                    // 1..5 均匀铺在从低到高
                    val span = (n - 1).coerceAtLeast(1)
                    (1 + (idx * 4.0 / span).toInt()).coerceIn(1, 5)
                }
                PianoHand.LEFT -> {
                    val span = (n - 1).coerceAtLeast(1)
                    (5 - (idx * 4.0 / span).toInt()).coerceIn(1, 5)
                }
            }
            note.copy(finger = finger)
        }
    }

    private fun nextMelodicFinger(
        prevMidi: Int?,
        prevFinger: Int?,
        midi: Int,
        hand: PianoHand,
    ): Int {
        if (prevMidi == null || prevFinger == null) {
            return defaultFinger(midi, hand)
        }
        val delta = midi - prevMidi
        if (delta == 0) return prevFinger

        val dir = sign(delta.toFloat()).toInt()
        val steps = when {
            abs(delta) <= 2 -> 1
            abs(delta) <= 4 -> 2
            abs(delta) <= 7 -> 3
            else -> 4
        }
        return when (hand) {
            PianoHand.RIGHT -> {
                var f = prevFinger + dir * steps
                when {
                    f > 5 -> if (dir > 0) 1 else 5 // 穿指/重置
                    f < 1 -> if (dir < 0) 5 else 1
                    else -> f
                }
            }
            PianoHand.LEFT -> {
                // 左手音高升时常向拇指(1)方向，与右手镜像感
                var f = prevFinger - dir * steps
                when {
                    f > 5 -> 5
                    f < 1 -> 1
                    else -> f
                }
            }
        }
    }

    private fun defaultFinger(midi: Int, hand: PianoHand): Int {
        val pc = midi % 12
        return when (hand) {
            PianoHand.RIGHT -> when (pc) {
                0, 5 -> 1 // C/F 常拇指
                2, 7 -> 2
                4, 9 -> 3
                11 -> 4
                else -> 2
            }
            PianoHand.LEFT -> when (pc) {
                0, 5 -> 5
                2, 7 -> 4
                4, 9 -> 3
                11 -> 2
                else -> 4
            }
        }.coerceIn(1, 5)
    }
}

fun SongPiece.withFingerings(): SongPiece {
    if (timedNotes.isEmpty()) return this
    if (timedNotes.all { it.finger in 1..5 }) return this
    return copy(timedNotes = FingeringAssigner.assign(timedNotes))
}
