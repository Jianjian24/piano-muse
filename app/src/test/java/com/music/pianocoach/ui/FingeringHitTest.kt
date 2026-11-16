package com.music.pianocoach.ui

import com.music.pianocoach.drill.PianoHand
import com.music.pianocoach.drill.SongPiece
import com.music.pianocoach.drill.TimedNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FingeringHitTest {

    @Test
    fun tapOnWaterfallBarHitsThatNote() {
        val piece = SongPiece(
            id = "s",
            title = "t",
            ticksPerQuarter = 480,
            timedNotes = listOf(
                TimedNote(midi = 60, startTick = 0, durationTicks = 480, hand = PianoHand.RIGHT, finger = 2),
            ),
        )
        val hit = scan(piece, playhead = 0, currentChord = emptySet(), keyboard = false)
        assertNotNull(hit)
        assertEquals(60, hit!!.midi)
        assertEquals(2, hit.finger)
    }

    @Test
    fun tapOnTargetKeyHitsSoundingNote() {
        val piece = SongPiece(
            id = "s",
            title = "t",
            ticksPerQuarter = 480,
            timedNotes = listOf(
                TimedNote(midi = 64, startTick = 0, durationTicks = 480, hand = PianoHand.RIGHT, finger = 3),
            ),
        )
        val hit = scan(piece, playhead = 100, currentChord = setOf(64), keyboard = true)
        assertNotNull(hit)
        assertEquals(64, hit!!.midi)
        assertEquals(3, hit.finger)
    }

    @Test
    fun tapOnIdleKeyDoesNothing() {
        val piece = SongPiece(
            id = "s",
            title = "t",
            ticksPerQuarter = 480,
            timedNotes = listOf(
                TimedNote(midi = 64, startTick = 2000, durationTicks = 480, hand = PianoHand.RIGHT, finger = 1),
            ),
        )
        val hit = scan(piece, playhead = 0, currentChord = emptySet(), keyboard = true)
        assertNull(hit)
    }

    private fun scan(
        piece: SongPiece,
        playhead: Int,
        currentChord: Set<Int>,
        keyboard: Boolean,
    ): TimedNote? {
        val width = 1040f
        val height = 400f
        val keyboardHeight = 88f
        val kbH = minOf(keyboardHeight, height * 0.28f)
        val fallH = height - kbH
        val yStart = if (keyboard) fallH.toInt() else 0
        val yEnd = if (keyboard) height.toInt() else fallH.toInt()
        var y = yStart
        while (y < yEnd) {
            var x = 0
            while (x < width.toInt()) {
                val hit = hitFingeringNote(
                    piece = piece,
                    playheadTick = playhead,
                    currentChord = currentChord,
                    tapX = x.toFloat(),
                    tapY = y.toFloat(),
                    width = width,
                    height = height,
                    keyboardHeightPx = keyboardHeight,
                )
                if (hit != null) return hit
                x += 6
            }
            y += 6
        }
        return null
    }
}
