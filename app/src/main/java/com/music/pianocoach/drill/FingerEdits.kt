package com.music.pianocoach.drill

import kotlinx.serialization.Serializable

/** 用户改过的一颗音。起拍是整曲坐标，不是练习片段里归零后的 tick。 */
@Serializable
data class FingerEdit(
    val startTick: Int,
    val midi: Int,
    val hand: PianoHand,
    val track: Int = 0,
    val finger: Int,
)

/** 整曲起拍。片段里的音符用 [TimedNote.sourceStartTick]。 */
fun TimedNote.pieceStartTick(): Int =
    if (sourceStartTick >= 0) sourceStartTick else startTick

fun TimedNote.sameFingerSlot(startTick: Int, midi: Int, hand: PianoHand, track: Int): Boolean =
    pieceStartTick() == startTick && this.midi == midi && this.hand == hand && this.track == track

/** 1→2→3→4→5→1。未标注从 1 开始。 */
fun nextFinger(current: Int): Int = if (current in 1..4) current + 1 else 1

fun SongPiece.withFingerEdit(edit: FingerEdit): SongPiece {
    val finger = edit.finger.coerceIn(0, 5)
    return copy(
        timedNotes = timedNotes.map { n ->
            if (n.sameFingerSlot(edit.startTick, edit.midi, edit.hand, edit.track)) {
                n.copy(finger = finger)
            } else {
                n
            }
        },
    )
}

fun SongPiece.applyFingerEdits(edits: List<FingerEdit>): SongPiece {
    if (edits.isEmpty() || timedNotes.isEmpty()) return this
    val byKey = edits.associateBy { listOf(it.startTick, it.midi, it.hand, it.track) }
    return copy(
        timedNotes = timedNotes.map { n ->
            val hit = byKey[listOf(n.pieceStartTick(), n.midi, n.hand, n.track)] ?: return@map n
            n.copy(finger = hit.finger.coerceIn(0, 5))
        },
    )
}
