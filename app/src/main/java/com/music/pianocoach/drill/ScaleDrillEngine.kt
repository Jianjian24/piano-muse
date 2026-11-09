package com.music.pianocoach.drill

import com.music.pianocoach.midi.NoteEvent
import com.music.pianocoach.midi.midiNoteToLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ScaleMode(val id: String, val label: String) {
    C_MAJOR_UP("scale_c_major_up", "C 大调上行"),
    C_MAJOR_DOWN("scale_c_major_down", "C 大调下行"),
}

data class DrillState(
    val mode: ScaleMode = ScaleMode.C_MAJOR_UP,
    val stepIndex: Int = 0,
    val totalSteps: Int = 0,
    val expectedNote: Int? = null,
    val correctSteps: Int = 0,
    val wrongNotes: List<String> = emptyList(),
    val completed: Boolean = false,
    val lastWrong: String? = null,
)

data class DrillResult(
    val modeId: String,
    val totalSteps: Int,
    val correctSteps: Int,
    val wrongNotes: List<String>,
)

class ScaleDrillEngine {
    private val cMajorUp = listOf(60, 62, 64, 65, 67, 69, 71, 72)
    private val cMajorDown = cMajorUp.reversed()

    private var target: List<Int> = cMajorUp
    private var wrongAccumulator = mutableListOf<String>()

    private val _state = MutableStateFlow(DrillState())
    val state: StateFlow<DrillState> = _state.asStateFlow()

    fun setMode(mode: ScaleMode) {
        target = when (mode) {
            ScaleMode.C_MAJOR_UP -> cMajorUp
            ScaleMode.C_MAJOR_DOWN -> cMajorDown
        }
        wrongAccumulator.clear()
        _state.value = DrillState(
            mode = mode,
            stepIndex = 0,
            totalSteps = target.size,
            expectedNote = target.firstOrNull(),
        )
    }

    fun reset() {
        val mode = _state.value.mode
        setMode(mode)
    }

    fun onEvent(event: NoteEvent) {
        if (!event.on) return
        val st = _state.value
        if (st.completed || st.expectedNote == null) return

        val expected = st.expectedNote
        if (event.note == expected) {
            val next = st.stepIndex + 1
            if (next >= target.size) {
                _state.value = st.copy(
                    stepIndex = next,
                    correctSteps = st.correctSteps + 1,
                    expectedNote = null,
                    completed = true,
                    lastWrong = null,
                )
            } else {
                _state.value = st.copy(
                    stepIndex = next,
                    correctSteps = st.correctSteps + 1,
                    expectedNote = target[next],
                    lastWrong = null,
                )
            }
        } else {
            val wrong = midiNoteToLabel(event.note)
            wrongAccumulator.add(wrong)
            _state.value = st.copy(
                lastWrong = "期望 ${midiNoteToLabel(expected)}，收到 $wrong",
                wrongNotes = wrongAccumulator.toList(),
            )
        }
    }

    fun takeResult(): DrillResult? {
        val st = _state.value
        if (!st.completed) return null
        return DrillResult(
            modeId = st.mode.id,
            totalSteps = st.totalSteps,
            correctSteps = st.correctSteps,
            wrongNotes = wrongAccumulator.toList(),
        )
    }
}
