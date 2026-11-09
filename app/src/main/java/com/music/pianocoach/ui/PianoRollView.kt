package com.music.pianocoach.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.pianocoach.drill.PianoHand
import com.music.pianocoach.drill.SongPiece
import com.music.pianocoach.midi.midiNoteToLabel
import com.music.pianocoach.ui.theme.MuseColors

private val WaterfallBg = MuseColors.WaterfallBg
private val HitLine = Color(0xEEFFFFFF)
private val LeftHand = MuseColors.HandLeft
private val RightHand = MuseColors.HandRight
private val LeftHandDim = MuseColors.HandLeftDim
private val RightHandDim = MuseColors.HandRightDim
private val KeyWhite = Color(0xFFF3F3F3)
private val KeyBlack = Color(0xFF111111)
private val KeyWhiteTargetL = Color(0xFF3D6B58)
private val KeyWhiteTargetR = Color(0xFF3D5F6B)
private val KeyPressed = MuseColors.Amber

/** 标准 88 键：A0–C8，一页铺满，避免越界 */
private const val KEY_LOW = 21
private const val KEY_HIGH = 108

private fun isBlack(midi: Int): Boolean {
    val pc = midi % 12
    return pc in setOf(1, 3, 6, 8, 10)
}

private fun shortName(midi: Int): String {
    val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    return names[midi % 12]
}

private fun octaveLabel(midi: Int): String = midiNoteToLabel(midi)

/**
 * 统一坐标系：瀑布 + 底部琴键在同一 Canvas 宽度内计算，杜绝错位。
 */
@Composable
fun PianoRollView(
    piece: SongPiece,
    playheadTick: Int,
    currentChord: Set<Int>,
    remaining: Set<Int>,
    pressed: Set<Int>,
    modifier: Modifier = Modifier,
    compactHeader: Boolean = false,
    showFingering: Boolean = false,
) {
    val whiteKeys = (KEY_LOW..KEY_HIGH).filter { !isBlack(it) }
    val ppq = piece.ticksPerQuarter.coerceAtLeast(1)
    val lookAheadTicks = ppq * 8
    val density = LocalDensity.current
    val keyboardHeightPx = with(density) { 88.dp.toPx() }

    Column(modifier = modifier.fillMaxWidth()) {
        if (!compactHeader) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    piece.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                HandLegend(LeftHand, "左手")
                Spacer(Modifier.width(10.dp))
                HandLegend(RightHand, "右手")
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .background(WaterfallBg, RoundedCornerShape(8.dp)),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val kbH = keyboardHeightPx.coerceAtMost(h * 0.28f)
                val fallH = h - kbH
                val hitY = fallH - 2f
                val whiteW = w / whiteKeys.size.coerceAtLeast(1)
                val pxPerTick = fallH / lookAheadTicks.toFloat()

                fun xCenter(midi: Int): Float {
                    val m = midi.coerceIn(KEY_LOW, KEY_HIGH)
                    if (!isBlack(m)) {
                        val wi = whiteKeys.indexOf(m)
                        return wi * whiteW + whiteW / 2f
                    }
                    val leftW = (m - 1 downTo KEY_LOW).first { !isBlack(it) }
                    val wi = whiteKeys.indexOf(leftW)
                    return (wi + 1) * whiteW
                }

                fun noteWidth(midi: Int): Float =
                    if (isBlack(midi)) whiteW * 0.55f else whiteW * 0.9f

                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }

                // —— 瀑布区 ——
                drawRect(WaterfallBg, size = Size(w, fallH))
                whiteKeys.forEachIndexed { idx, _ ->
                    drawLine(
                        Color(0x18FFFFFF),
                        Offset(idx * whiteW, 0f),
                        Offset(idx * whiteW, fallH),
                        strokeWidth = 1f,
                    )
                }

                piece.timedNotes.forEach { note ->
                    if (note.midi !in KEY_LOW..KEY_HIGH) return@forEach
                    val endTick = note.startTick + note.durationTicks
                    if (endTick < playheadTick - ppq) return@forEach
                    if (note.startTick > playheadTick + lookAheadTicks) return@forEach

                    val bottomY = hitY - (note.startTick - playheadTick) * pxPerTick
                    val topY = hitY - (endTick - playheadTick) * pxPerTick
                    val yTop = minOf(topY, bottomY)
                    val yBot = maxOf(topY, bottomY)
                    if (yBot < 0f || yTop > fallH) return@forEach

                    val cx = xCenter(note.midi)
                    val nw = noteWidth(note.midi)
                    val isCurrent = note.midi in currentChord &&
                        kotlin.math.abs(note.startTick - playheadTick) <= ppq / 2
                    val base = if (note.hand == PianoHand.LEFT) LeftHand else RightHand
                    val color = when {
                        note.midi in pressed && isCurrent -> KeyPressed
                        isCurrent -> base
                        note.startTick < playheadTick -> base.copy(alpha = 0.3f)
                        else -> if (note.hand == PianoHand.LEFT) LeftHandDim else RightHandDim
                    }
                    val barH = maxOf(10f, yBot - yTop)
                    drawRoundRect(
                        color,
                        Offset(cx - nw / 2f, yTop.coerceAtLeast(0f)),
                        Size(nw, barH.coerceAtMost(fallH)),
                        CornerRadius(3f, 3f),
                    )
                    // 下落块：指法优先，否则音名
                    if (barH >= 16f && nw >= 10f) {
                        val label = if (showFingering && note.finger in 1..5) {
                            note.finger.toString()
                        } else {
                            shortName(note.midi)
                        }
                        labelPaint.textSize = if (showFingering && note.finger in 1..5) {
                            (nw * 0.55f).coerceIn(11f, 20f)
                        } else {
                            (nw * 0.42f).coerceIn(9f, 16f)
                        }
                        labelPaint.color = Color.White.toArgb()
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            cx,
                            (yTop.coerceAtLeast(0f) + barH / 2f + labelPaint.textSize / 3f)
                                .coerceAtMost(fallH - 2f),
                            labelPaint,
                        )
                    }
                }

                drawLine(HitLine, Offset(0f, hitY), Offset(w, hitY), strokeWidth = 3f)

                // —— 琴键区（同一宽度坐标系）——
                val kbTop = fallH
                drawRect(Color(0xFF141414), Offset(0f, kbTop), Size(w, kbH))

                whiteKeys.forEachIndexed { idx, midi ->
                    val x = idx * whiteW
                    val target = midi in currentChord
                    val active = midi in pressed
                    val curNote = piece.timedNotes
                        .firstOrNull { it.midi == midi && kotlin.math.abs(it.startTick - playheadTick) < ppq }
                    val hand = curNote?.hand
                        ?: if (midi < 60) PianoHand.LEFT else PianoHand.RIGHT
                    val fill = when {
                        active -> KeyPressed
                        target && hand == PianoHand.LEFT -> KeyWhiteTargetL
                        target && hand == PianoHand.RIGHT -> KeyWhiteTargetR
                        else -> KeyWhite
                    }
                    drawRoundRect(
                        fill,
                        Offset(x + 0.5f, kbTop + 2f),
                        Size(whiteW - 1f, kbH - 4f),
                        CornerRadius(2f, 2f),
                    )
                    labelPaint.textSize = (whiteW * 0.35f).coerceIn(7f, 12f)
                    labelPaint.color = Color(0xFF222222).toArgb()
                    val label = when {
                        showFingering && target && (curNote?.finger ?: 0) in 1..5 ->
                            curNote!!.finger.toString()
                        midi % 12 == 0 -> octaveLabel(midi)
                        else -> shortName(midi)
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        x + whiteW / 2f,
                        kbTop + kbH - 8f,
                        labelPaint,
                    )
                }

                (KEY_LOW..KEY_HIGH).filter { isBlack(it) }.forEach { midi ->
                    val leftW = (midi - 1 downTo KEY_LOW).first { !isBlack(it) }
                    val wi = whiteKeys.indexOf(leftW)
                    val x = (wi + 1) * whiteW - whiteW * 0.32f
                    val bw = whiteW * 0.58f
                    val bh = kbH * 0.58f
                    val target = midi in currentChord
                    val active = midi in pressed
                    val curNote = piece.timedNotes
                        .firstOrNull { it.midi == midi && kotlin.math.abs(it.startTick - playheadTick) < ppq }
                    val hand = curNote?.hand ?: PianoHand.LEFT
                    val fill = when {
                        active -> KeyPressed
                        target && hand == PianoHand.LEFT -> LeftHand
                        target && hand == PianoHand.RIGHT -> RightHand
                        else -> KeyBlack
                    }
                    drawRoundRect(
                        fill,
                        Offset(x, kbTop + 2f),
                        Size(bw, bh),
                        CornerRadius(2f, 2f),
                    )
                    if (bw >= 9f) {
                        labelPaint.textSize = (bw * 0.4f).coerceIn(7f, 11f)
                        labelPaint.color = Color.White.toArgb()
                        val label = if (showFingering && target && (curNote?.finger ?: 0) in 1..5) {
                            curNote!!.finger.toString()
                        } else {
                            shortName(midi)
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            x + bw / 2f,
                            kbTop + bh * 0.55f,
                            labelPaint,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HandLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp)
    }
}
