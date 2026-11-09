package com.music.pianocoach.drill

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FingeringAssignerTest {

    @Test
    fun twinkleRightHand_respectsDirectionalErgonomics() {
        val piece = BuiltinSongs.byId("twinkle") ?: error("missing twinkle")
        val notes = piece.timedNotes.filter { it.hand == PianoHand.RIGHT }
            .sortedWith(compareBy({ it.startTick }, { it.midi }))
        val firstSeven = notes.take(7)

        for (i in 0 until firstSeven.size - 2) {
            val triplet = firstSeven.subList(i, i + 3).map { it.finger }
            assertFalse("thumb cross tail ${triplet} at $i", triplet == listOf(5, 1, 5))
        }

        for (i in 0 until firstSeven.size - 1) {
            val a = firstSeven[i]
            val b = firstSeven[i + 1]
            val step = FingeringAssigner.whiteIndex(b.midi) - FingeringAssigner.whiteIndex(a.midi)
            if (step > 0 && b.finger < a.finger) {
                assertTrue(
                    "upward ${a.finger}→${b.finger} at $i only via 2/3→1",
                    a.finger in 2..3 && b.finger == 1,
                )
            }
        }
    }
}
