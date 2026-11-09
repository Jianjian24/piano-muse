package com.music.pianocoach.drill

import org.junit.Assert.assertEquals
import org.junit.Test

class OdeJoyFingeringTest {

    @Test
    fun samePitchRepeats_sameFinger() {
        val piece = BuiltinSongs.byId("ode_to_joy") ?: return
        val notes = FingeringAssigner.assign(
            HandAssigner.assign(piece.timedNotes.map { it.copy(finger = 0) }),
            piece.ticksPerQuarter,
        ).filter { it.hand == PianoHand.RIGHT }
            .sortedWith(compareBy({ it.startTick }, { it.midi }))
        for (i in 1 until notes.size) {
            val cur = notes[i]
            val prev = notes[i - 1]
            if (cur.midi == prev.midi) {
                assertEquals(
                    "midi ${cur.midi} at index $i: ${prev.finger}→${cur.finger}",
                    prev.finger,
                    cur.finger,
                )
            }
        }
    }
}
