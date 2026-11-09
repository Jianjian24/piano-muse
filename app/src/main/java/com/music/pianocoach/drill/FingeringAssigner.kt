package com.music.pianocoach.drill

import kotlin.math.abs

/**
 * 规则指法（MVP）：五指位为主，辅以穿指/移位；左右手分别处理。
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
        // 右手：1 在 thumbMidi；左手：5 在 pinkyMidi（低音侧锚点）
        var anchorWhite = initialAnchorWhite(sorted, hand)
        var i = 0
        while (i < sorted.size) {
            val t0 = sorted[i].startTick
            val chord = mutableListOf<TimedNote>()
            while (i < sorted.size && sorted[i].startTick == t0) {
                chord.add(sorted[i])
                i++
            }
            if (chord.size == 1) {
                val n = chord[0]
                val (finger, newAnchor) = nextMelodicFinger(anchorWhite, n.midi, hand)
                anchorWhite = newAnchor
                out.add(n.copy(finger = finger))
            } else {
                val mapped = assignChord(chord, hand)
                val anchorNote = mapped.minByOrNull { abs(whiteIndex(it.midi) - anchorWhite) }
                    ?: mapped.first()
                anchorWhite = when (hand) {
                    PianoHand.RIGHT -> whiteIndex(anchorNote.midi) - (anchorNote.finger - 1)
                    PianoHand.LEFT -> whiteIndex(anchorNote.midi) - (5 - anchorNote.finger)
                }
                out.addAll(mapped)
            }
        }
        return out
    }

    private fun initialAnchorWhite(sorted: List<TimedNote>, hand: PianoHand): Int {
        val first = sorted.first().midi
        val high = sorted.maxOf { it.midi }
        val low = sorted.minOf { it.midi }
        return when (hand) {
            PianoHand.RIGHT -> {
                // 尽量让起音落在 1，且高音仍能用到 5（约 4 个白键跨度）
                val firstW = whiteIndex(first)
                val highW = whiteIndex(high)
                if (highW - firstW <= 4) firstW
                else (highW - 4).coerceAtMost(firstW)
            }
            PianoHand.LEFT -> {
                val firstW = whiteIndex(first)
                val lowW = whiteIndex(low)
                if (firstW - lowW <= 4) firstW - 4 // 5 在低端，1 朝高
                else lowW
            }
        }
    }

    /** 和弦：右手低音近拇指(1)，左手低音近小指(5)。 */
    private fun assignChord(chord: List<TimedNote>, hand: PianoHand): List<TimedNote> {
        val ordered = chord.sortedBy { it.midi }
        val n = ordered.size
        return ordered.mapIndexed { idx, note ->
            val finger = when (hand) {
                PianoHand.RIGHT -> {
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

    /**
     * @return finger to newAnchorWhite（右手=拇指所在白键序；左手=小指所在白键序）
     *
     * 小二度/小三度刚出五指位时优先**移位**（如 G5→A4→G3），
     * 避免小星星式 G5→A1→G5 的来回穿指；大跳才穿指/重置。
     */
    private fun nextMelodicFinger(
        anchorWhite: Int,
        midi: Int,
        hand: PianoHand,
    ): Pair<Int, Int> {
        val w = whiteIndex(midi)
        return when (hand) {
            PianoHand.RIGHT -> {
                var finger = w - anchorWhite + 1
                var anchor = anchorWhite
                when {
                    finger in 1..5 -> Unit
                    finger in 6..7 -> {
                        // 高出 1–2 白键：手右移，落在 4（便于马上下行）
                        val shift = finger - 4
                        anchor += shift
                        finger = 4
                    }
                    finger > 7 -> {
                        finger = 1
                        anchor = w
                    }
                    finger in -1..0 -> {
                        // 低出 1–2 白键：手左移，落在 2
                        val shift = 2 - finger
                        anchor -= shift
                        finger = 2
                    }
                    else -> {
                        finger = 5
                        anchor = w - 4
                    }
                }
                finger to anchor
            }
            PianoHand.LEFT -> {
                var finger = 5 - (w - anchorWhite)
                var anchor = anchorWhite
                when {
                    finger in 1..5 -> Unit
                    finger in -1..0 -> {
                        // 上行刚出位：手右移，拇指侧落在 2
                        val shift = 2 - finger
                        anchor += shift
                        finger = 2
                    }
                    finger < -1 -> {
                        finger = 1
                        anchor = w - 4
                    }
                    finger in 6..7 -> {
                        // 下行刚出位：手左移，落在 4
                        val shift = finger - 4
                        anchor -= shift
                        finger = 4
                    }
                    else -> {
                        finger = 5
                        anchor = w
                    }
                }
                finger to anchor
            }
        }
    }

    private fun defaultFinger(midi: Int, hand: PianoHand): Int {
        val pc = midi % 12
        return when (hand) {
            PianoHand.RIGHT -> when (pc) {
                0, 5 -> 1
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

    /** 白键序号（黑键并入左侧白键），用于五指位推算。 */
    internal fun whiteIndex(midi: Int): Int {
        val octave = midi.floorDiv(12)
        val pc = midi.mod(12)
        val inOct = when (pc) {
            0, 1 -> 0 // C, C#
            2, 3 -> 1 // D, D#
            4 -> 2 // E
            5, 6 -> 3 // F, F#
            7, 8 -> 4 // G, G#
            9, 10 -> 5 // A, A#
            else -> 6 // B
        }
        return octave * 7 + inOct
    }
}

fun SongPiece.withFingerings(): SongPiece {
    if (timedNotes.isEmpty()) return this
    if (timedNotes.all { it.finger in 1..5 }) return this
    return copy(timedNotes = FingeringAssigner.assign(timedNotes))
}
