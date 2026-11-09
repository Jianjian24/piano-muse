package com.music.pianocoach.drill

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

data class ScorePlaybackState(
    val playing: Boolean = false,
    /** 连续播放头（与进度条共享） */
    val tick: Int = 0,
    val totalTicks: Int = 0,
    val bpm: Float = 65f,
)

/**
 * 按谱面时值自动播放；与跟练进度条共用 tick。
 */
class ScorePlaybackEngine(
    private val scope: CoroutineScope,
    context: Context,
    private val onTick: (tick: Int) -> Unit,
    private val tone: MidiTonePlayer = MidiTonePlayer(context.applicationContext),
) {
    private val _state = MutableStateFlow(ScorePlaybackState())
    val state: StateFlow<ScorePlaybackState> = _state.asStateFlow()

    private var piece: SongPiece? = null
    private var notes: List<TimedNote> = emptyList()
    private var playJob: Job? = null
    private val sounding = mutableSetOf<Int>()
    private val cursor = AtomicInteger(0)

    fun attach(piece: SongPiece?, tick: Int = 0) {
        stopInternal(updateUi = false)
        this.piece = piece
        notes = piece?.timedNotes?.sortedWith(compareBy({ it.startTick }, { it.midi })).orEmpty()
        val total = piece?.totalTicks()?.coerceAtLeast(1) ?: 1
        val t = tick.coerceIn(0, total)
        cursor.set(t)
        _state.value = ScorePlaybackState(
            playing = false,
            tick = t,
            totalTicks = total,
            bpm = _state.value.bpm,
        )
        onTick(t)
    }

    fun setBpm(bpm: Float) {
        _state.value = _state.value.copy(bpm = bpm.coerceIn(40f, 200f))
    }

    fun seekToProgress(progress: Float) {
        val total = _state.value.totalTicks.coerceAtLeast(1)
        seekToTick(((progress.coerceIn(0f, 1f) * total) + 0.5f).toInt())
    }

    fun seekToTick(tick: Int) {
        val total = _state.value.totalTicks.coerceAtLeast(1)
        val t = tick.coerceIn(0, total)
        val wasPlaying = _state.value.playing
        cursor.set(t)
        if (wasPlaying) {
            silence()
            resyncSounding(t)
        }
        publishTick(t, playing = wasPlaying && t < total)
        if (wasPlaying && t >= total) {
            pause()
        }
    }

    fun toggle() {
        if (_state.value.playing) pause() else play()
    }

    /** 预加载 SoundFont，避免首次播放卡顿 */
    fun warmUp() {
        scope.launch { runCatching { tone.start() } }
    }

    fun play() {
        val p = piece ?: return
        if (p.timedNotes.isEmpty()) return
        var startTick = cursor.get()
        if (startTick >= _state.value.totalTicks) {
            startTick = 0
            cursor.set(0)
            onTick(0)
        }
        stopInternal(updateUi = false)
        tone.start()
        silence()
        resyncSounding(startTick)
        publishTick(startTick, playing = true)
        playJob = scope.launch {
            var lastMs = System.nanoTime()
            var prevTick = cursor.get()
            while (isActive) {
                val total = _state.value.totalTicks
                if (prevTick >= total) break
                val bpm = _state.value.bpm
                val ppq = (piece?.ticksPerQuarter ?: 480).coerceAtLeast(1)
                val ticksPerMs = (bpm / 60.0) * ppq / 1000.0
                delay(16)
                val now = System.nanoTime()
                val dtMs = (now - lastMs) / 1_000_000.0
                lastMs = now

                val base = cursor.get()
                // 拖动进度条时 cursor 已变，以新位置为基准
                if (base != prevTick) {
                    silence()
                    resyncSounding(base)
                    prevTick = base
                    continue
                }
                val next = (base + dtMs * ticksPerMs).coerceAtMost(total.toDouble())
                val nextInt = next.roundToInt().coerceIn(0, total)
                if (nextInt > base) {
                    advanceNotes(base, nextInt)
                    cursor.set(nextInt)
                    prevTick = nextInt
                    publishTick(nextInt, playing = true)
                }
                if (nextInt >= total) break
            }
            silence()
            val end = _state.value.totalTicks
            cursor.set(end)
            publishTick(end, playing = false)
        }
    }

    fun pause() {
        stopInternal(updateUi = true)
    }

    fun release() {
        stopInternal(updateUi = false)
        tone.release()
    }

    private fun publishTick(tick: Int, playing: Boolean) {
        _state.value = _state.value.copy(playing = playing, tick = tick)
        onTick(tick)
    }

    private fun stopInternal(updateUi: Boolean) {
        playJob?.cancel()
        playJob = null
        silence()
        if (updateUi) {
            _state.value = _state.value.copy(playing = false)
        }
    }

    private fun silence() {
        sounding.toList().forEach { tone.noteOff(it) }
        sounding.clear()
        tone.allNotesOff()
    }

    private fun resyncSounding(tick: Int) {
        notes.forEach { n ->
            val end = n.startTick + n.durationTicks
            if (tick in n.startTick until end) {
                if (sounding.add(n.midi)) {
                    tone.noteOn(n.midi, n.velocity)
                }
            }
        }
    }

    private fun advanceNotes(fromTick: Int, toTick: Int) {
        if (toTick <= fromTick) return
        notes.forEach { n ->
            val end = n.startTick + n.durationTicks
            if (n.startTick in (fromTick + 1)..toTick) {
                if (sounding.add(n.midi)) tone.noteOn(n.midi, n.velocity)
            }
            if (end in (fromTick + 1)..toTick) {
                if (sounding.remove(n.midi)) tone.noteOff(n.midi)
            }
        }
    }
}
