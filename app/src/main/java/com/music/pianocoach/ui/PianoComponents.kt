package com.music.pianocoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.pianocoach.midi.NoteEvent
import com.music.pianocoach.midi.PIANO_KEY_MAX
import com.music.pianocoach.midi.PIANO_KEY_MIN
import com.music.pianocoach.midi.midiNoteToLabel
import com.music.pianocoach.ui.theme.MuseColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun isBlackKey(note: Int): Boolean {
    val pc = note % 12
    return pc in setOf(1, 3, 6, 8, 10)
}

@Composable
fun LastNoteCard(last: NoteEvent?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MuseColors.InkElevated, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text("当前音", style = MaterialTheme.typography.labelMedium, color = MuseColors.AmberDim)
        Text(
            text = last?.let { midiNoteToLabel(it.note) } ?: "—",
            fontSize = 40.sp,
            fontWeight = FontWeight.Normal,
            color = MuseColors.Ivory,
        )
        if (last != null) {
            Text(
                text = "${if (last.on) "按下" else "松开"} · 力度 ${last.velocity}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun PianoKeyboard(
    activeNotes: Set<Int>,
    focusNote: Int?,
    highlightNotes: Set<Int> = emptySet(),
    modifier: Modifier = Modifier,
) {
    val whiteWidth = 28.dp
    val blackWidth = 18.dp
    val keyHeight = 100.dp
    val keys = (PIANO_KEY_MIN..PIANO_KEY_MAX).toList()
    val whiteKeys = keys.filter { !isBlackKey(it) }
    val totalWhiteWidth = whiteWidth * whiteKeys.size
    val scroll = rememberScrollState()
    val focusIndex = remember(focusNote, whiteKeys) {
        focusNote?.let { n -> whiteKeys.indexOf(n).takeIf { it >= 0 } }
    }
    LaunchedEffect(focusIndex) {
        focusIndex?.let { idx ->
            scroll.animateScrollTo((idx * 28).coerceAtLeast(0))
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MuseColors.InkElevated, RoundedCornerShape(8.dp))
            .horizontalScroll(scroll)
            .padding(4.dp),
    ) {
        Row(modifier = Modifier.width(totalWhiteWidth)) {
            whiteKeys.forEach { note ->
                val active = note in activeNotes
                val highlight = note in highlightNotes
                val color = when {
                    highlight -> MuseColors.Amber
                    active -> MuseColors.SageLight
                    else -> Color(0xFFE0DCD4)
                }
                Box(
                    modifier = Modifier
                        .width(whiteWidth)
                        .height(keyHeight)
                        .background(color, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)),
                )
            }
        }
        keys.filter(::isBlackKey).forEach { note ->
            val whitesBefore = (PIANO_KEY_MIN until note).count { !isBlackKey(it) }
            val left = whiteWidth * whitesBefore - blackWidth / 2
            val active = note in activeNotes
            val highlight = note in highlightNotes
            Box(
                modifier = Modifier
                    .offset(x = left)
                    .width(blackWidth)
                    .height(keyHeight * 0.62f)
                    .background(
                        when {
                            highlight -> MuseColors.AmberDim
                            active -> Color(0xFF3D5A4A)
                            else -> Color(0xFF1A1E22)
                        },
                        RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp),
                    ),
            )
        }
    }
}

@Composable
fun EventLog(events: List<NoteEvent>, height: androidx.compose.ui.unit.Dp = 160.dp) {
    Text("事件日志", style = MaterialTheme.typography.titleSmall, color = MuseColors.IvoryDim)
    val listState = rememberLazyListState()
    LaunchedEffect(events.firstOrNull()) {
        if (events.isNotEmpty()) listState.animateScrollToItem(0)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(MuseColors.InkElevated, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        items(events.take(50), key = { "${it.timeNanos}-${it.note}-${it.on}" }) { event ->
            val time = remember(event.timeNanos) {
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            }
            Text(
                text = "$time · ${midiNoteToLabel(event.note)} · v${event.velocity}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}
