package com.music.pianocoach.drill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongPieceSoundingTest {

    @Test
    fun midisSoundingWithinDuration() {
        val piece = SongPiece(
            id = "t",
            title = "t",
            timedNotes = listOf(
                TimedNote(midi = 62, startTick = 100, durationTicks = 400),
            ),
        )
        assertTrue(62 in piece.midisSoundingAt(250))
        assertEquals(emptySet<Int>(), piece.midisSoundingAt(500))
    }
}
