package com.music.pianocoach.drill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordFingeringUniqueTest {

    @Test
    fun sameTickChord_hasDistinctFingers() {
        val notes = listOf(
            TimedNote(midi = 67, startTick = 100, durationTicks = 480, hand = PianoHand.RIGHT),
            TimedNote(midi = 68, startTick = 100, durationTicks = 480, hand = PianoHand.RIGHT),
            TimedNote(midi = 71, startTick = 100, durationTicks = 480, hand = PianoHand.RIGHT),
        )
        val out = FingeringAssigner.assign(notes, 480)
        val fingers = out.map { it.finger }.toSet()
        assertEquals(3, fingers.size)
        assertTrue(fingers.all { it in 1..5 })
    }

    @Test
    fun overlappingVoices_noDuplicateFinger() {
        val notes = listOf(
            TimedNote(midi = 60, startTick = 0, durationTicks = 800, hand = PianoHand.RIGHT),
            TimedNote(midi = 64, startTick = 200, durationTicks = 800, hand = PianoHand.RIGHT),
        )
        val out = FingeringAssigner.assign(notes, 480)
        val a = out.first { it.midi == 60 }
        val b = out.first { it.midi == 64 }
        assertTrue(a.finger != b.finger)
    }
}
