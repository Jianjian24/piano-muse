package com.music.pianocoach.drill

/**
 * 左右手划分：
 * 1) 多音轨：按轨平均音高，低轨左手、高轨右手（最多两簇）
 * 2) 单轨 / 效果差：在音高直方图上找最大空隙作为分界（比固定中央 C 更准）
 */
object HandAssigner {

    fun assign(notes: List<TimedNote>): List<TimedNote> {
        if (notes.isEmpty()) return notes
        val byTrack = notes.groupBy { it.track }.filter { it.value.isNotEmpty() }
        if (byTrack.size >= 2) {
            val ranked = byTrack
                .map { (t, list) -> TrackStat(t, list.map { it.midi }.average(), list.size) }
                .sortedBy { it.avgPitch }
            // 取音高最低的一半轨为左手（至少 1 轨）
            val leftCount = (ranked.size / 2).coerceAtLeast(1).coerceAtMost(ranked.size - 1)
            val leftTracks = ranked.take(leftCount).map { it.track }.toSet()
            // 若两簇平均音高差太小，回退空隙法
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
            return notes.map { it.copy(hand = if (it.midi < 60) PianoHand.LEFT else PianoHand.RIGHT) }
        }
        // 在常用分界区间找最大空隙
        var bestSplit = 60
        var bestGap = -1
        val sorted = used.toList()
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]
            val b = sorted[i + 1]
            // 空隙落在 48..72 更像双手分界
            if (a < 45 || b > 76) continue
            val gap = b - a
            if (gap > bestGap) {
                bestGap = gap
                bestSplit = (a + b) / 2 + 1
            }
        }
        if (bestGap < 2) {
            // 无显著空隙：用音高中位数
            val mid = notes.map { it.midi }.sorted()[notes.size / 2]
            bestSplit = mid
        }
        val split = bestSplit
        return notes.map {
            it.copy(hand = if (it.midi < split) PianoHand.LEFT else PianoHand.RIGHT)
        }
    }

    private data class TrackStat(val track: Int, val avgPitch: Double, val count: Int)
}
