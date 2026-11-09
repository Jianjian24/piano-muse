package com.music.pianocoach.drill

import kotlin.math.abs

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
    private const val JUMP_WHITE_KEYS = 5
    /** 全单音旋律时每句最多步数（小星星等 7 音一句）。 */
    private const val MONO_PHRASE_STEPS = 7

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

        fun leadWhite(step: MelodicStep): Int {
            val midi = when {
                step.notes.size == 1 -> step.notes.first().midi
                else -> step.notes.maxOf { it.midi }
            }
            return FingeringAssigner.whiteIndex(midi)
        }

        steps.forEachIndexed { index, step ->
            if (bucket.isNotEmpty()) {
                val prev = bucket.last()
                val tickGap = step.startTick - prev.startTick
                val jump = abs(leadWhite(step) - leadWhite(prev))
                val monoPhraseFull = allMono && bucket.size >= MONO_PHRASE_STEPS
                if (tickGap >= gapTicks || jump >= JUMP_WHITE_KEYS || monoPhraseFull) {
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
