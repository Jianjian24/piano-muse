package com.music.pianocoach.drill

import kotlin.math.abs

/**
 * 乐句定把位指法：切句 → 每句选把位 → 句内填 1–5（计划内移位，禁止随意穿指）。
 * 仅作提示，不参与跟练判定。
 */
object FingeringAssigner {

    fun assign(notes: List<TimedNote>, ticksPerQuarter: Int = 480): List<TimedNote> {
        if (notes.isEmpty()) return notes
        val left = assignHand(notes.filter { it.hand == PianoHand.LEFT }, PianoHand.LEFT, ticksPerQuarter)
        val right = assignHand(notes.filter { it.hand == PianoHand.RIGHT }, PianoHand.RIGHT, ticksPerQuarter)
        val byKey = (left + right).associateBy { Triple(it.startTick, it.midi, it.hand) }
        return notes.map { n ->
            byKey[Triple(n.startTick, n.midi, n.hand)]?.let { n.copy(finger = it.finger) }
                ?: n.copy(finger = defaultFinger(n.midi, n.hand))
        }
    }

    private fun assignHand(
        notes: List<TimedNote>,
        hand: PianoHand,
        ticksPerQuarter: Int,
    ): List<TimedNote> {
        if (notes.isEmpty()) return notes
        val phrases = PhraseSegmenter.segment(notes, ticksPerQuarter)
        val out = ArrayList<TimedNote>(notes.size)
        var prevAnchor: Int? = null

        var prevMidi: Int? = null
        var prevFinger: Int? = null
        for (phrase in phrases) {
            val anchor = PositionPicker.pickAnchor(phrase, hand, prevAnchor)
            val filled = fillPhrase(phrase, anchor, hand, prevMidi, prevFinger)
            out.addAll(filled)
            prevAnchor = endingAnchor(phrase, anchor, hand, prevMidi, prevFinger)
            val last = filled.maxByOrNull { it.startTick }
            if (last != null) {
                prevMidi = last.midi
                prevFinger = last.finger
            }
        }
        return out
    }

    internal fun countShiftsForPhrase(phrase: Phrase, startAnchor: Int, hand: PianoHand): Int {
        var anchor = startAnchor
        var shifts = 0
        var prevMidi: Int? = null
        var prevFinger: Int? = null
        for (step in phrase.steps) {
            for (note in step.notes.sortedBy { it.midi }) {
                val startAnchor = anchor
                val (finger, newAnchor) = resolveMelodicFinger(
                    anchor, note.midi, hand, prevMidi, prevFinger,
                )
                if (newAnchor != startAnchor) shifts++
                anchor = newAnchor
                prevMidi = note.midi
                prevFinger = finger
            }
        }
        return shifts
    }

    private fun fillPhrase(
        phrase: Phrase,
        startAnchor: Int,
        hand: PianoHand,
        carryMidi: Int? = null,
        carryFinger: Int? = null,
    ): List<TimedNote> {
        val out = ArrayList<TimedNote>()
        var anchor = startAnchor
        var prevMidi: Int? = carryMidi
        var prevFinger: Int? = carryFinger
        for (step in phrase.steps) {
            if (step.notes.size == 1) {
                val n = step.notes.first()
                val (finger, newAnchor) = resolveMelodicFinger(anchor, n.midi, hand, prevMidi, prevFinger)
                anchor = newAnchor
                prevMidi = n.midi
                prevFinger = finger
                out.add(n.copy(finger = finger))
            } else {
                val chordOut = assignChordWithAnchor(step.notes, anchor, hand)
                val anchorNote = chordOut.minByOrNull { abs(whiteIndex(it.midi) - anchor) } ?: chordOut.first()
                anchor = when (hand) {
                    PianoHand.RIGHT -> whiteIndex(anchorNote.midi) - (anchorNote.finger - 1)
                    PianoHand.LEFT -> whiteIndex(anchorNote.midi) - (5 - anchorNote.finger)
                }
                out.addAll(chordOut)
            }
        }
        return out
    }

    private fun endingAnchor(
        phrase: Phrase,
        startAnchor: Int,
        hand: PianoHand,
        carryMidi: Int? = null,
        carryFinger: Int? = null,
    ): Int {
        var anchor = startAnchor
        var prevMidi: Int? = carryMidi
        var prevFinger: Int? = carryFinger
        for (step in phrase.steps) {
            for (note in step.notes.sortedBy { it.midi }) {
                val (finger, newAnchor) = resolveMelodicFinger(anchor, note.midi, hand, prevMidi, prevFinger)
                anchor = newAnchor
                prevMidi = note.midi
                prevFinger = finger
            }
        }
        return anchor
    }

    private fun resolveMelodicFinger(
        anchorWhite: Int,
        midi: Int,
        hand: PianoHand,
        prevMidi: Int?,
        prevFinger: Int?,
    ): Pair<Int, Int> {
        val (finger, anchor) = geometricFinger(anchorWhite, midi, hand)
        if (prevMidi == null || prevFinger == null) return finger to anchor

        val prevW = whiteIndex(prevMidi)
        val w = whiteIndex(midi)
        val step = w - prevW
        if (step == 0) {
            val keepAnchor = when (hand) {
                PianoHand.RIGHT -> w - prevFinger + 1
                PianoHand.LEFT -> w - (5 - prevFinger)
            }
            return prevFinger to keepAnchor
        }

        val adjusted = when (hand) {
            PianoHand.RIGHT -> adjustRightTransition(step, prevFinger, w, finger, anchor)
            PianoHand.LEFT -> adjustLeftTransition(step, prevFinger, w, finger, anchor)
        }
        return adjusted
    }

    /** 右手：向右弹允 2→1/3→1 穿指，禁止上行时 4→3 等；向左弹允许指号减小。 */
    private fun adjustRightTransition(
        step: Int,
        prevFinger: Int,
        w: Int,
        finger: Int,
        anchor: Int,
    ): Pair<Int, Int> {
        if (step > 0 && finger < prevFinger) {
            if (prevFinger in 2..3 && step == 1) return 1 to w
            val keepAnchor = w - prevFinger + 1
            val keepFinger = w - keepAnchor + 1
            if (keepFinger in 1..5 && keepFinger >= prevFinger) return keepFinger to keepAnchor
            if (prevFinger >= 4 && step <= 2) {
                val stretchAnchor = w - 4
                if (w - stretchAnchor + 1 == 5) return 5 to stretchAnchor
            }
        }
        if (step < 0 && finger > prevFinger) {
            val target = (prevFinger + step).coerceIn(1, 5)
            val a = w - target + 1
            val f = w - a + 1
            if (f in 1..5 && f <= prevFinger) return f to a
        }
        return finger to anchor
    }

    /** 左手镜像：向高音（右）指号应减小；向低音指号应增大。穿指仅 2→1 / 3→1（指号侧）。 */
    private fun adjustLeftTransition(
        step: Int,
        prevFinger: Int,
        w: Int,
        finger: Int,
        anchor: Int,
    ): Pair<Int, Int> {
        if (step > 0 && finger > prevFinger) {
            if (prevFinger in 2..3 && step == 1) return 1 to (w - 4)
            val keepAnchor = w - (5 - prevFinger)
            val keepFinger = 5 - (w - keepAnchor)
            if (keepFinger in 1..5 && keepFinger <= prevFinger) return keepFinger to keepAnchor
        }
        if (step < 0 && finger < prevFinger) {
            if (prevFinger in 2..3 && step >= -2) return 1 to (w - 4)
            val target = (prevFinger - step).coerceIn(1, 5)
            val a = w - (5 - target)
            val f = 5 - (w - a)
            if (f in 1..5 && f >= prevFinger) return f to a
        }
        return finger to anchor
    }

    private fun geometricFinger(
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
                        val shift = finger - 4
                        anchor += shift
                        finger = 4
                    }
                    finger > 7 -> {
                        val shift = finger - 4
                        anchor += shift
                        finger = 4
                    }
                    finger in -1..0 -> {
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
                        val shift = 2 - finger
                        anchor += shift
                        finger = 2
                    }
                    finger < -1 -> {
                        val shift = 2 - finger
                        anchor += shift
                        finger = 2
                    }
                    finger in 6..7 -> {
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

    private fun assignChordWithAnchor(
        chord: List<TimedNote>,
        anchorWhite: Int,
        hand: PianoHand,
    ): List<TimedNote> {
        val ordered = chord.sortedBy { it.midi }
        return ordered.map { note ->
            val w = whiteIndex(note.midi)
            val finger = when (hand) {
                PianoHand.RIGHT -> (w - anchorWhite + 1).coerceIn(1, 5)
                PianoHand.LEFT -> (5 - (w - anchorWhite)).coerceIn(1, 5)
            }
            note.copy(finger = finger)
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
    fun whiteIndex(midi: Int): Int {
        val octave = midi.floorDiv(12)
        val pc = midi.mod(12)
        val inOct = when (pc) {
            0, 1 -> 0
            2, 3 -> 1
            4 -> 2
            5, 6 -> 3
            7, 8 -> 4
            9, 10 -> 5
            else -> 6
        }
        return octave * 7 + inOct
    }
}

fun SongPiece.withFingerings(): SongPiece {
    if (timedNotes.isEmpty()) return this
    return copy(timedNotes = FingeringAssigner.assign(timedNotes, ticksPerQuarter))
}
