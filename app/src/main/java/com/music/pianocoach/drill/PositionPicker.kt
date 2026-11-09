package com.music.pianocoach.drill

import kotlin.math.abs

/**
 * 为乐句选择五指把位锚点（右手=拇指白键序；左手=小指白键序）。
 */
object PositionPicker {

    fun pickAnchor(
        phrase: Phrase,
        hand: PianoHand,
        prevAnchorWhite: Int?,
    ): Int {
        val whites = phraseWhites(phrase)
        if (whites.isEmpty()) return 0
        val low = whites.min()
        val high = whites.max()
        val span = high - low

        val candidates = when (hand) {
            PianoHand.RIGHT -> {
                when {
                    span <= 4 -> listOf(low, (high - 4).coerceAtLeast(low))
                    else -> (low..(high - 4)).toList().ifEmpty { listOf(low) }
                }
            }
            PianoHand.LEFT -> {
                when {
                    span <= 4 -> listOf(low, (high - 4).coerceAtLeast(low))
                    else -> (low..(high - 4)).toList().ifEmpty { listOf(low) }
                }
            }
        }.distinct()

        var best = candidates.first()
        var bestShifts = Int.MAX_VALUE
        var bestContinuity = Int.MAX_VALUE

        for (c in candidates) {
            val shifts = FingeringAssigner.countShiftsForPhrase(phrase, c, hand)
            val continuity = prevAnchorWhite?.let { abs(c - it) } ?: 0
            if (shifts < bestShifts || (shifts == bestShifts && continuity < bestContinuity)) {
                bestShifts = shifts
                bestContinuity = continuity
                best = c
            }
        }
        return best
    }

    private fun phraseWhites(phrase: Phrase): List<Int> =
        phrase.steps.flatMap { s -> s.notes.map { FingeringAssigner.whiteIndex(it.midi) } }
}
