package com.music.pianocoach.drill

import kotlin.math.roundToInt

/** 统一为应用内 PPQ（与 MIDI 默认一致），避免 MusicXML divisions=4 等导致步进/播放错乱。 */
object TickNormalizer {
    const val STANDARD_PPQ = 480

    fun normalize(notes: List<TimedNote>, sourcePpq: Int, targetPpq: Int = STANDARD_PPQ): Pair<List<TimedNote>, Int> {
        if (sourcePpq <= 0 || sourcePpq == targetPpq) return notes to targetPpq
        val scale = targetPpq.toDouble() / sourcePpq
        val scaled = notes.map { n ->
            n.copy(
                startTick = (n.startTick * scale).roundToInt(),
                durationTicks = (n.durationTicks * scale).roundToInt().coerceAtLeast(1),
            )
        }
        return scaled to targetPpq
    }
}
