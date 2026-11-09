package com.music.pianocoach.drill

/**
 * 左右手划分：
 * 1) 多音轨：按轨平均音高，低轨左手、高轨右手（最多两簇）
 * 2) 单轨：音高空隙足够大才分界；窄音域单旋律整段同一手
 */
object HandAssigner {

    /** 小于此空隙不视作双手分界（避免把小星星一类单手曲从中间切开） */
    private const val MIN_SPLIT_GAP = 5
    /** 整段音域不超过此半音数时，强制单手 */
    private const val SINGLE_HAND_SPAN = 14

    fun assign(notes: List<TimedNote>): List<TimedNote> {
        if (notes.isEmpty()) return notes
        val byTrack = notes.groupBy { it.track }.filter { it.value.isNotEmpty() }
        if (byTrack.size >= 2) {
            val ranked = byTrack
                .map { (t, list) -> TrackStat(t, list.map { it.midi }.average(), list.size) }
                .sortedBy { it.avgPitch }
            val leftCount = (ranked.size / 2).coerceAtLeast(1).coerceAtMost(ranked.size - 1)
            val leftTracks = ranked.take(leftCount).map { it.track }.toSet()
            val lowAvg = ranked.first().avgPitch
            val highAvg = ranked.last().avgPitch
            if (highAvg - lowAvg >= 5.0) {
                return notes.map {
                    it.copy(hand = if (it.track in leftTracks) PianoHand.LEFT else PianoHand.RIGHT)
                }
            }
        }
        return assignByPitchGap(notes)
    }

    private fun assignByPitchGap(notes: List<TimedNote>): List<TimedNote> {
        val used = notes.map { it.midi }.toSortedSet()
        if (used.size <= 1) {
            return notes.map { it.copy(hand = singleHandForPitch(it.midi)) }
        }
        val low = used.first()
        val high = used.last()
        if (high - low <= SINGLE_HAND_SPAN) {
            val center = notes.map { it.midi }.average()
            val hand = if (center < 60.0) PianoHand.LEFT else PianoHand.RIGHT
            return notes.map { it.copy(hand = hand) }
        }

        var bestSplit = 60
        var bestGap = -1
        val sorted = used.toList()
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (a < 45 || b > 76) continue
            val gap = b - a
            if (gap > bestGap) {
                bestGap = gap
                bestSplit = (a + b) / 2 + 1
            }
        }
        if (bestGap < MIN_SPLIT_GAP) {
            val center = notes.map { it.midi }.average()
            val hand = if (center < 60.0) PianoHand.LEFT else PianoHand.RIGHT
            return notes.map { it.copy(hand = hand) }
        }
        val split = bestSplit
        return notes.map {
            it.copy(hand = if (it.midi < split) PianoHand.LEFT else PianoHand.RIGHT)
        }
    }

    private fun singleHandForPitch(midi: Int): PianoHand =
        if (midi < 60) PianoHand.LEFT else PianoHand.RIGHT

    private data class TrackStat(val track: Int, val avgPitch: Double, val count: Int)
}
