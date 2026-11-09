package com.music.pianocoach.session

import com.music.pianocoach.midi.NoteEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 保留最近一段时间内的音符事件，并检测「停手」。
 */
class PracticeSession(
    private val windowNanos: Long = 60_000_000_000L,
    private val maxEvents: Int = 500,
    private val idleThresholdNanos: Long = 2_000_000_000L,
) {
    private val events = ArrayDeque<NoteEvent>()
    private var lastActivityNanos: Long = 0L

    private val _handsIdle = MutableStateFlow(true)
    val handsIdle: StateFlow<Boolean> = _handsIdle.asStateFlow()

    private val _suggestAnalyze = MutableStateFlow(false)
    val suggestAnalyze: StateFlow<Boolean> = _suggestAnalyze.asStateFlow()

    fun onEvent(event: NoteEvent) {
        if (event.on) {
            lastActivityNanos = event.timeNanos
            _handsIdle.value = false
            _suggestAnalyze.value = false
        }
        events.addLast(event)
        while (events.size > maxEvents) {
            events.removeFirst()
        }
        trimWindow(event.timeNanos)
        updateIdle(event.timeNanos)
    }

    fun tickIdle(nowNanos: Long) {
        updateIdle(nowNanos)
    }

    fun dismissAnalyzeSuggestion() {
        _suggestAnalyze.value = false
    }

    fun snapshot(): List<NoteEvent> = events.toList()

    fun clear() {
        events.clear()
        _handsIdle.value = true
        _suggestAnalyze.value = false
    }

    private fun trimWindow(nowNanos: Long) {
        val cutoff = nowNanos - windowNanos
        while (events.isNotEmpty() && events.first().timeNanos < cutoff) {
            events.removeFirst()
        }
    }

    private fun updateIdle(nowNanos: Long) {
        val reference = if (lastActivityNanos > 0) lastActivityNanos else nowNanos
        val idle = nowNanos - reference >= idleThresholdNanos
        if (idle && !_handsIdle.value && events.isNotEmpty()) {
            _suggestAnalyze.value = true
        }
        _handsIdle.value = idle || lastActivityNanos == 0L
    }
}
