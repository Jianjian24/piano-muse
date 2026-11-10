package com.music.pianocoach.drill

/** 同一时刻起奏的一组音（单音或和弦）。 */
data class MelodicStep(
    val startTick: Int,
    val notes: List<TimedNote>,
)

/** 单手连续乐句。 */
data class Phrase(
    val steps: List<MelodicStep>,
)

/**
 * 按时值缝、音域跳、单旋律固定句长切分乐句。
 */
object PhraseSegmenter {

    private const val GAP_QUARTERS = 2
    /** 全单音旋律时每句最多步数（避免每几个音就换把位）。 */
    private const val MONO_PHRASE_STEPS = 16

    fun segment(notes: List<TimedNote>, ticksPerQuarter: Int): List<Phrase> {
        if (notes.isEmpty()) return emptyList()
        val steps = groupSteps(notes)
        if (steps.isEmpty()) return emptyList()

        val gapTicks = GAP_QUARTERS * ticksPerQuarter.coerceAtLeast(1)
        val phrases = mutableListOf<Phrase>()
        var bucket = mutableListOf<MelodicStep>()
        val allMono = steps.all { it.notes.size == 1 }

        fun flush() {
            if (bucket.isNotEmpty()) {
                phrases.add(Phrase(bucket.toList()))
                bucket = mutableListOf()
            }
        }

        steps.forEachIndexed { index, step ->
            if (bucket.isNotEmpty()) {
                val prev = bucket.last()
                val tickGap = step.startTick - prev.startTick
                val monoPhraseFull = allMono && bucket.size >= MONO_PHRASE_STEPS
                // 大跳仍可能在同一句内换把，不因音程 alone 切断（否则每音成句 → 全标 1）
                if (tickGap >= gapTicks || monoPhraseFull) {
                    flush()
                }
            }
            bucket.add(step)
        }
        flush()
        return phrases
    }

    private fun groupSteps(notes: List<TimedNote>): List<MelodicStep> {
        val sorted = notes.sortedWith(compareBy({ it.startTick }, { it.midi }))
        val steps = mutableListOf<MelodicStep>()
        var i = 0
        while (i < sorted.size) {
            val t0 = sorted[i].startTick
            val chord = mutableListOf<TimedNote>()
            while (i < sorted.size && sorted[i].startTick == t0) {
                chord.add(sorted[i])
                i++
            }
            steps.add(MelodicStep(t0, chord))
        }
        return steps
    }
}
