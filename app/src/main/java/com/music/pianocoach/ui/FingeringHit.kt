package com.music.pianocoach.ui

import com.music.pianocoach.drill.SongPiece
import com.music.pianocoach.drill.TimedNote
import com.music.pianocoach.drill.midisSoundingAt
import com.music.pianocoach.drill.primaryTimedNoteAt

/** 与 [PianoRollView] 的 88 键坐标系一致。 */
internal const val ROLL_KEY_LOW = 21
internal const val ROLL_KEY_HIGH = 108

internal fun rollIsBlack(midi: Int): Boolean {
    val pc = midi % 12
    return pc == 1 || pc == 3 || pc == 6 || pc == 8 || pc == 10
}

internal fun rollWhiteKeys(): List<Int> =
    (ROLL_KEY_LOW..ROLL_KEY_HIGH).filter { !rollIsBlack(it) }

/**
 * 点在瀑布音块或当前目标琴键上时，返回那颗音。
 * 瀑布上后画的音盖住先画的，命中取最上面一颗。
 */
internal fun hitFingeringNote(
    piece: SongPiece,
    playheadTick: Int,
    currentChord: Set<Int>,
    tapX: Float,
    tapY: Float,
    width: Float,
    height: Float,
    keyboardHeightPx: Float,
): TimedNote? {
    if (width <= 0f || height <= 0f) return null
    val whiteKeys = rollWhiteKeys()
    if (whiteKeys.isEmpty()) return null
    val ppq = piece.ticksPerQuarter.coerceAtLeast(1)
    val lookAheadTicks = ppq * 8
    val kbH = keyboardHeightPx.coerceAtMost(height * 0.28f)
    val fallH = height - kbH
    val hitY = fallH - 2f
    val whiteW = width / whiteKeys.size
    val pxPerTick = fallH / lookAheadTicks.toFloat()

    if (tapY >= fallH) {
        val midi = midiAtKeyboard(tapX, tapY - fallH, kbH, whiteW, whiteKeys) ?: return null
        val sounding = piece.midisSoundingAt(playheadTick)
        if (midi !in currentChord && midi !in sounding) return null
        return piece.primaryTimedNoteAt(playheadTick, midi)
    }

    var hit: TimedNote? = null
    for (note in piece.timedNotes) {
        if (note.midi !in ROLL_KEY_LOW..ROLL_KEY_HIGH) continue
        val endTick = note.startTick + note.durationTicks
        if (endTick < playheadTick - ppq) continue
        if (note.startTick > playheadTick + lookAheadTicks) continue
        val bottomY = hitY - (note.startTick - playheadTick) * pxPerTick
        val topY = hitY - (endTick - playheadTick) * pxPerTick
        val yTop = minOf(topY, bottomY)
        val yBot = maxOf(topY, bottomY)
        if (yBot < 0f || yTop > fallH) continue
        val cx = rollXCenter(note.midi, whiteKeys, whiteW)
        val nw = if (rollIsBlack(note.midi)) whiteW * 0.55f else whiteW * 0.9f
        val barH = maxOf(10f, yBot - yTop)
        val top = yTop.coerceAtLeast(0f)
        val drawnH = barH.coerceAtMost(fallH)
        val left = cx - nw / 2f
        if (tapX in left..(left + nw) && tapY in top..(top + drawnH)) {
            hit = note
        }
    }
    return hit
}

private fun midiAtKeyboard(
    tapX: Float,
    localY: Float,
    kbH: Float,
    whiteW: Float,
    whiteKeys: List<Int>,
): Int? {
    for (midi in ROLL_KEY_LOW..ROLL_KEY_HIGH) {
        if (!rollIsBlack(midi)) continue
        val leftW = (midi - 1 downTo ROLL_KEY_LOW).first { !rollIsBlack(it) }
        val wi = whiteKeys.indexOf(leftW)
        if (wi < 0) continue
        val x = (wi + 1) * whiteW - whiteW * 0.32f
        val bw = whiteW * 0.58f
        val bh = kbH * 0.58f
        if (tapX in x..(x + bw) && localY in 2f..(2f + bh)) return midi
    }
    if (localY < 2f || localY > kbH - 2f) return null
    val idx = (tapX / whiteW).toInt()
    if (idx !in whiteKeys.indices) return null
    return whiteKeys[idx]
}

internal fun rollXCenter(midi: Int, whiteKeys: List<Int>, whiteW: Float): Float {
    val m = midi.coerceIn(ROLL_KEY_LOW, ROLL_KEY_HIGH)
    if (!rollIsBlack(m)) {
        val wi = whiteKeys.indexOf(m)
        return wi * whiteW + whiteW / 2f
    }
    val leftW = (m - 1 downTo ROLL_KEY_LOW).first { !rollIsBlack(it) }
    val wi = whiteKeys.indexOf(leftW)
    return (wi + 1) * whiteW
}
