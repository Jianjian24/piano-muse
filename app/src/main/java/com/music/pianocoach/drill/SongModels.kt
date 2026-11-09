package com.music.pianocoach.drill

import kotlinx.serialization.Serializable

enum class PianoHand { LEFT, RIGHT }

/** 带时值的音符（瀑布/卷帘用）。 */
@Serializable
data class TimedNote(
    val midi: Int,
    val startTick: Int,
    val durationTicks: Int,
    val velocity: Int = 80,
    /** 0=未知/单轨；导入时填 MIDI 轨号 */
    val track: Int = 0,
    val hand: PianoHand = if (midi < 60) PianoHand.LEFT else PianoHand.RIGHT,
    /** 指法 1–5；0=未标注（仅提示，不参与判定） */
    val finger: Int = 0,
)

/** 兼容旧字段：单音序列跟练仍可用。 */
@Serializable
data class SongNote(
    val midi: Int,
    val beats: Double = 1.0,
)

/** 同一时刻起奏的一组音（和弦或单音）。 */
data class ChordStep(
    val startTick: Int,
    val notes: List<TimedNote>,
) {
    val midis: Set<Int> get() = notes.map { it.midi }.toSet()
}

@Serializable
data class SongPiece(
    val id: String,
    val title: String,
    val description: String = "",
    /** 旧版线性音列；无 timedNotes 时回退使用 */
    val notes: List<SongNote> = emptyList(),
    val timedNotes: List<TimedNote> = emptyList(),
    val ticksPerQuarter: Int = 480,
)

fun SongPiece.hasRoll(): Boolean = timedNotes.isNotEmpty()

fun SongPiece.pitchRange(): IntRange {
    val pool = if (timedNotes.isNotEmpty()) timedNotes.map { it.midi } else notes.map { it.midi }
    if (pool.isEmpty()) return 48..72
    return pool.min()..pool.max()
}

fun SongPiece.totalTicks(): Int =
    timedNotes.maxOfOrNull { it.startTick + it.durationTicks } ?: 0

/**
 * 将同时起奏（startTick 相同或相差 ≤ snap）的音合并为和弦步进。
 */
fun SongPiece.toChordSteps(snapTicks: Int = 20): List<ChordStep> {
    if (timedNotes.isEmpty()) {
        var tick = 0
        return notes.map { sn ->
            val step = ChordStep(
                startTick = tick,
                notes = listOf(TimedNote(sn.midi, tick, (sn.beats * ticksPerQuarter).toInt().coerceAtLeast(1))),
            )
            tick += (sn.beats * ticksPerQuarter).toInt().coerceAtLeast(ticksPerQuarter / 2)
            step
        }
    }
    val sorted = timedNotes.sortedBy { it.startTick }
    val steps = mutableListOf<ChordStep>()
    var bucket = mutableListOf<TimedNote>()
    var bucketStart = -1
    for (n in sorted) {
        if (bucket.isEmpty()) {
            bucketStart = n.startTick
            bucket.add(n)
        } else if (n.startTick - bucketStart <= snapTicks) {
            bucket.add(n)
        } else {
            steps.add(ChordStep(bucketStart, bucket.sortedBy { it.midi }))
            bucket = mutableListOf(n)
            bucketStart = n.startTick
        }
    }
    if (bucket.isNotEmpty()) {
        steps.add(ChordStep(bucketStart, bucket.sortedBy { it.midi }))
    }
    return steps
}

fun SongPiece.targetMidi(): List<Int> =
    if (timedNotes.isNotEmpty()) timedNotes.sortedBy { it.startTick }.map { it.midi }
    else notes.map { it.midi }
