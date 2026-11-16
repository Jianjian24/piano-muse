package com.music.pianocoach.drill

import org.junit.Assert.assertEquals
import org.junit.Test

class FingerEditTest {

    @Test
    fun sliceKeepsCustomFingerAndOriginalStart() {
        val piece = SongPiece(
            id = "s",
            title = "t",
            ticksPerQuarter = 480,
            timedNotes = listOf(
                TimedNote(
                    midi = 60,
                    startTick = 100,
                    durationTicks = 400,
                    hand = PianoHand.RIGHT,
                    finger = 4,
                ),
            ),
        )
        val drill = PracticeDrill(
            id = "d",
            title = "片段",
            startTick = 200,
            endTick = 600,
        )
        val sliced = piece.sliceForDrill(drill, "plan")
        val note = sliced.timedNotes.single()
        assertEquals(4, note.finger)
        assertEquals(100, note.sourceStartTick)
        assertEquals(0, note.startTick)
        assertEquals(100, note.pieceStartTick())
    }

    @Test
    fun editInWindowShowsOnFullPieceAndAnotherWindow() {
        val piece = SongPiece(
            id = "s",
            title = "t",
            ticksPerQuarter = 480,
            timedNotes = listOf(
                TimedNote(60, 0, 480, hand = PianoHand.RIGHT, finger = 1),
                TimedNote(64, 480, 480, hand = PianoHand.RIGHT, finger = 2),
                TimedNote(67, 960, 480, hand = PianoHand.RIGHT, finger = 3),
            ),
        )
        val window = PracticeDrill(id = "d", title = "中段", startTick = 480, endTick = 1500)
        val sliced = piece.sliceForDrill(window, "p")
        val target = sliced.timedNotes.first { it.midi == 64 }
        val updated = piece.withFingerEdit(
            FingerEdit(
                startTick = target.pieceStartTick(),
                midi = target.midi,
                hand = target.hand,
                track = target.track,
                finger = nextFinger(target.finger),
            ),
        )
        assertEquals(3, updated.timedNotes.first { it.midi == 64 }.finger)
        assertEquals(1, updated.timedNotes.first { it.midi == 60 }.finger)
        val other = PracticeDrill(id = "d2", title = "开头", startTick = 0, endTick = 900)
        val again = updated.sliceForDrill(other, "p")
        assertEquals(3, again.timedNotes.first { it.midi == 64 }.finger)
        assertEquals(480, again.timedNotes.first { it.midi == 64 }.sourceStartTick)
    }

    @Test
    fun nextFingerWrapsAfterFive() {
        assertEquals(1, nextFinger(0))
        assertEquals(2, nextFinger(1))
        assertEquals(5, nextFinger(4))
        assertEquals(1, nextFinger(5))
    }

    @Test
    fun applyEditsOverridesOnlyMatchingSlot() {
        val piece = SongPiece(
            id = "twinkle",
            title = "小星星",
            timedNotes = listOf(
                TimedNote(60, 0, 240, hand = PianoHand.RIGHT, finger = 1),
                TimedNote(48, 0, 240, hand = PianoHand.LEFT, finger = 5),
            ),
        )
        val edited = piece.applyFingerEdits(
            listOf(FingerEdit(startTick = 0, midi = 60, hand = PianoHand.RIGHT, finger = 2)),
        )
        assertEquals(2, edited.timedNotes.first { it.hand == PianoHand.RIGHT }.finger)
        assertEquals(5, edited.timedNotes.first { it.hand == PianoHand.LEFT }.finger)
    }
}
