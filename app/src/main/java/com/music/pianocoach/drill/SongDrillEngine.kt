package com.music.pianocoach.drill

import com.music.pianocoach.midi.NoteEvent
import com.music.pianocoach.midi.midiNoteToLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SongDrillState(
    val piece: SongPiece? = null,
    val steps: List<ChordStep> = emptyList(),
    val stepIndex: Int = 0,
    val totalSteps: Int = 0,
    /** 当前和弦尚未按住的音 */
    val remaining: Set<Int> = emptySet(),
    /** 当前和弦全部目标音 */
    val expectedChord: Set<Int> = emptySet(),
    val correctSteps: Int = 0,
    val wrongNotes: List<String> = emptyList(),
    val completed: Boolean = false,
    val lastWrong: String? = null,
    val playheadTick: Int = 0,
    /** 当前和弦按时长按住的进度 0~1；音未齐时为 0 */
    val holdProgress: Float = 0f,
    /** 本步要求按住的毫秒（按谱面时值与 BPM） */
    val requiredHoldMs: Long = 0L,
)

/**
 * 和弦判定：目标音须**同时按住**，并持续足够时值才进入下一步。
 * seekToTick / setPlayheadTick 与自动播放、进度条共用时间轴。
 */
class SongDrillEngine {
    private var steps: List<ChordStep> = emptyList()
    private var wrongAccumulator = mutableListOf<String>()
    private var currentPiece: SongPiece? = null
    private val heldNotes = mutableSetOf<Int>()
    private var sawNoteOnThisStep = false
    private var practiceBpm: Float = 100f
    private var chordFullyHeldSinceNanos: Long? = null
    private var requiredHoldMs: Long = 0L

    private val _state = MutableStateFlow(SongDrillState())
    val state: StateFlow<SongDrillState> = _state.asStateFlow()

    fun setPracticeBpm(bpm: Float) {
        practiceBpm = bpm.coerceIn(40f, 200f)
        // BPM 变化时按当前步进重算要求时长
        val st = _state.value
        if (!st.completed && st.stepIndex in steps.indices) {
            requiredHoldMs = computeRequiredHoldMs(steps[st.stepIndex])
            _state.value = st.copy(requiredHoldMs = requiredHoldMs)
            // 已按齐则按新标准继续计时，不重置起点
        }
    }

    fun load(piece: SongPiece) {
        val prepared = piece.withFingerings()
        currentPiece = prepared
        steps = prepared.toChordSteps()
        wrongAccumulator.clear()
        heldNotes.clear()
        sawNoteOnThisStep = false
        clearHoldTimer()
        if (steps.isEmpty()) {
            _state.value = SongDrillState(piece = prepared, completed = true)
            return
        }
        publish(0, correct = 0, playhead = steps[0].startTick)
    }

    fun reset() {
        currentPiece?.let { load(it) }
    }

    fun seekToStep(index: Int) {
        if (steps.isEmpty()) return
        val i = index.coerceIn(0, steps.lastIndex)
        sawNoteOnThisStep = false
        clearHoldTimer()
        publish(i, correct = i.coerceAtLeast(0), playhead = steps[i].startTick)
    }

    fun seekToProgress(progress: Float) {
        val piece = currentPiece
        if (piece != null && piece.timedNotes.isNotEmpty()) {
            val total = piece.totalTicks().coerceAtLeast(1)
            seekToTick(((progress.coerceIn(0f, 1f) * total) + 0.5f).toInt())
            return
        }
        if (steps.isEmpty()) return
        val i = ((progress.coerceIn(0f, 1f) * steps.lastIndex) + 0.5f).toInt()
            .coerceIn(0, steps.lastIndex)
        seekToStep(i)
    }

    fun seekToTick(tick: Int) {
        val t = tick.coerceAtLeast(0)
        if (steps.isEmpty()) {
            _state.value = _state.value.copy(playheadTick = t, holdProgress = 0f)
            return
        }
        val last = steps.last()
        val endTick = last.startTick + (last.notes.maxOfOrNull { it.durationTicks } ?: 0)
        if (t >= endTick) {
            sawNoteOnThisStep = false
            clearHoldTimer()
            _state.value = _state.value.copy(
                piece = currentPiece,
                steps = steps,
                stepIndex = steps.size,
                totalSteps = steps.size,
                remaining = emptySet(),
                expectedChord = emptySet(),
                completed = true,
                playheadTick = t,
                lastWrong = null,
                holdProgress = 0f,
                requiredHoldMs = 0L,
            )
            return
        }
        var idx = 0
        for (i in steps.indices) {
            if (steps[i].startTick <= t) idx = i else break
        }
        sawNoteOnThisStep = false
        clearHoldTimer()
        publish(idx, correct = idx.coerceAtLeast(0), playhead = t)
    }

    fun setPlayheadTick(tick: Int) {
        val st = _state.value
        if (st.piece == null) return
        val t = tick.coerceAtLeast(0)
        if (steps.isEmpty()) {
            _state.value = st.copy(playheadTick = t)
            return
        }
        val last = steps.last()
        val endTick = last.startTick + (last.notes.maxOfOrNull { it.durationTicks } ?: 0)
        if (t >= endTick) {
            seekToTick(t)
            return
        }
        var idx = 0
        for (i in steps.indices) {
            if (steps[i].startTick <= t) idx = i else break
        }
        if (idx != st.stepIndex || st.completed) {
            sawNoteOnThisStep = false
            clearHoldTimer()
            publish(idx, correct = idx.coerceAtLeast(0), playhead = t)
        } else if (st.playheadTick != t) {
            _state.value = st.copy(playheadTick = t)
        }
    }

    fun onEvent(event: NoteEvent) {
        val st = _state.value
        if (st.completed || st.expectedChord.isEmpty()) {
            if (event.on) heldNotes.add(event.note) else heldNotes.remove(event.note)
            return
        }

        if (event.on) {
            heldNotes.add(event.note)
            sawNoteOnThisStep = true
            if (event.note !in st.expectedChord) {
                wrongAccumulator.add(midiNoteToLabel(event.note))
            }
            refreshHoldAndRemaining()
            tickHold()
        } else {
            heldNotes.remove(event.note)
            // 松开目标音：打断时值累计
            if (event.note in st.expectedChord) {
                clearHoldTimer()
            }
            refreshHoldAndRemaining()
        }
    }

    /** 由 ViewModel 定时调用，以便按住期间也能推进。 */
    fun tickHold(nowNanos: Long = System.nanoTime()) {
        val st = _state.value
        if (st.completed || st.expectedChord.isEmpty()) return
        if (!sawNoteOnThisStep || !heldNotes.containsAll(st.expectedChord)) {
            if (chordFullyHeldSinceNanos != null) {
                clearHoldTimer()
                _state.value = st.copy(holdProgress = 0f)
            }
            return
        }
        val since = chordFullyHeldSinceNanos ?: run {
            chordFullyHeldSinceNanos = nowNanos
            requiredHoldMs = computeRequiredHoldMs(steps[st.stepIndex])
            nowNanos
        }
        val heldMs = ((nowNanos - since) / 1_000_000L).coerceAtLeast(0L)
        val need = requiredHoldMs.coerceAtLeast(1L)
        val progress = (heldMs.toFloat() / need).coerceIn(0f, 1f)
        _state.value = st.copy(
            holdProgress = progress,
            requiredHoldMs = need,
            remaining = emptySet(),
            wrongNotes = wrongAccumulator.toList(),
        )
        if (heldMs >= need) {
            advanceFromHold()
        }
    }

    fun takeResult(): DrillResult? {
        val st = _state.value
        if (!st.completed || st.piece == null) return null
        return DrillResult(
            modeId = st.piece.id,
            totalSteps = st.totalSteps,
            correctSteps = st.correctSteps,
            wrongNotes = wrongAccumulator.toList(),
        )
    }

    private fun advanceFromHold() {
        val st = _state.value
        val next = st.stepIndex + 1
        val correct = st.correctSteps + 1
        clearHoldTimer()
        if (next >= steps.size) {
            _state.value = st.copy(
                stepIndex = next,
                correctSteps = correct,
                remaining = emptySet(),
                expectedChord = emptySet(),
                completed = true,
                lastWrong = null,
                holdProgress = 0f,
                requiredHoldMs = 0L,
                playheadTick = steps.lastOrNull()?.let { step ->
                    step.startTick + (step.notes.maxOfOrNull { n -> n.durationTicks } ?: 0)
                } ?: 0,
            )
        } else {
            sawNoteOnThisStep = false
            publish(next, correct, playhead = steps[next].startTick)
        }
    }

    private fun refreshHoldAndRemaining() {
        val st = _state.value
        if (st.completed || st.expectedChord.isEmpty()) return
        val remaining = st.expectedChord - heldNotes
        if (remaining.isEmpty() && sawNoteOnThisStep) {
            if (chordFullyHeldSinceNanos == null) {
                chordFullyHeldSinceNanos = System.nanoTime()
                requiredHoldMs = computeRequiredHoldMs(steps[st.stepIndex])
            }
        } else {
            clearHoldTimer()
        }
        _state.value = st.copy(
            remaining = remaining,
            holdProgress = if (remaining.isEmpty()) st.holdProgress else 0f,
            requiredHoldMs = requiredHoldMs,
            correctSteps = st.correctSteps,
            wrongNotes = wrongAccumulator.toList(),
            lastWrong = null,
        )
    }

    private fun computeRequiredHoldMs(step: ChordStep): Long {
        val ppq = (currentPiece?.ticksPerQuarter ?: 480).coerceAtLeast(1)
        val durTicks = step.notes.minOfOrNull { it.durationTicks.coerceAtLeast(1) }
            ?: (ppq / 2)
        val beats = durTicks.toDouble() / ppq
        // 按当前 BPM 换算；略收 10% 容错，避免人类提前一丁点松手就失败
        val ms = beats * (60_000.0 / practiceBpm) * 0.9
        return ms.toLong().coerceIn(70L, 5_000L)
    }

    private fun clearHoldTimer() {
        chordFullyHeldSinceNanos = null
        requiredHoldMs = 0L
    }

    private fun publish(index: Int, correct: Int, playhead: Int) {
        val step = steps[index]
        val expected = step.midis
        requiredHoldMs = computeRequiredHoldMs(step)
        _state.value = SongDrillState(
            piece = currentPiece,
            steps = steps,
            stepIndex = index,
            totalSteps = steps.size,
            remaining = expected - heldNotes,
            expectedChord = expected,
            correctSteps = correct,
            wrongNotes = wrongAccumulator.toList(),
            completed = false,
            lastWrong = null,
            playheadTick = playhead,
            holdProgress = 0f,
            requiredHoldMs = requiredHoldMs,
        )
    }
}
