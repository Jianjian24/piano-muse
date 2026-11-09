package com.music.pianocoach.drill

import kotlinx.serialization.Serializable

enum class PracticeHandFilter { LEFT, RIGHT, BOTH }

enum class PracticeLayerKind {
    FAMILIAR, // 熟悉
    TACKLE,   // 攻克
    CONNECT,  // 串联
}

@Serializable
data class PracticeDrill(
    val id: String,
    val title: String,
    val goal: String = "",
    val startTick: Int,
    val endTick: Int,
    val hand: PracticeHandFilter = PracticeHandFilter.BOTH,
    val tempoHint: String = "",
)

@Serializable
data class PracticeLayer(
    val kind: PracticeLayerKind,
    val title: String,
    val drills: List<PracticeDrill> = emptyList(),
)

@Serializable
data class PracticePlan(
    val id: String,
    val songId: String,
    val createdAtEpochMs: Long,
    val model: String = "",
    val summary: String = "",
    val layers: List<PracticeLayer> = emptyList(),
)

/** 当前跟练来自整曲还是某条 AI drill。 */
data class ActiveDrillRef(
    val sourceSongId: String,
    val isFullPiece: Boolean,
    val planId: String? = null,
    val drillId: String? = null,
    val label: String = "",
)

fun SongPiece.sliceForDrill(
    drill: PracticeDrill,
    planId: String,
): SongPiece {
    val start = drill.startTick.coerceAtLeast(0)
    val end = drill.endTick.coerceAtLeast(start + 1)
    val hand = when (drill.hand) {
        PracticeHandFilter.LEFT -> PianoHand.LEFT
        PracticeHandFilter.RIGHT -> PianoHand.RIGHT
        PracticeHandFilter.BOTH -> null
    }
    val sliced = timedNotes.mapNotNull { n ->
        if (hand != null && n.hand != hand) return@mapNotNull null
        val noteEnd = n.startTick + n.durationTicks
        if (n.startTick >= end || noteEnd <= start) return@mapNotNull null
        val clippedStart = n.startTick.coerceAtLeast(start)
        val clippedEnd = noteEnd.coerceAtMost(end)
        n.copy(
            startTick = clippedStart - start,
            durationTicks = (clippedEnd - clippedStart).coerceAtLeast(1),
        )
    }.sortedWith(compareBy({ it.startTick }, { it.midi }))
    return SongPiece(
        id = "${id}#${planId}#${drill.id}",
        title = drill.title,
        description = buildString {
            append(drill.goal.ifBlank { "AI 片段练习" })
            if (drill.tempoHint.isNotBlank()) append(" · ").append(drill.tempoHint)
            append(" · ").append(title)
        },
        notes = sliced.map { SongNote(it.midi, it.durationTicks.toDouble() / ticksPerQuarter.coerceAtLeast(1)) },
        timedNotes = FingeringAssigner.assign(sliced, ticksPerQuarter),
        ticksPerQuarter = ticksPerQuarter,
    )
}
