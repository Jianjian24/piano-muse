package com.music.pianocoach.coach

import com.music.pianocoach.drill.DrillResult
import com.music.pianocoach.midi.NoteEvent
import com.music.pianocoach.midi.midiNoteToLabel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

object PracticeContextBuilder {

    private val json = Json { prettyPrint = false }

    fun build(
        events: List<NoteEvent>,
        drillResult: DrillResult? = null,
        maxSequenceNotes: Int = 48,
    ): PracticeSummary {
        val onEvents = events.filter { it.on }
        if (onEvents.isEmpty()) {
            return PracticeSummary()
        }
        val first = onEvents.first().timeNanos
        val last = onEvents.last().timeNanos
        val windowSec = (last - first).coerceAtLeast(0) / 1_000_000_000.0

        val notes = onEvents.map { it.note }
        val pitchRange = if (notes.isEmpty()) {
            emptyList()
        } else {
            listOf(midiNoteToLabel(notes.min()), midiNoteToLabel(notes.max()))
        }

        val sequence = formatSequence(onEvents, maxSequenceNotes)
        val rhythm = computeRhythm(onEvents)

        return PracticeSummary(
            windowSec = (windowSec * 10).roundToInt() / 10.0,
            noteCount = onEvents.size,
            pitchRange = pitchRange,
            detectedKeyHint = guessKey(onEvents),
            rhythm = rhythm,
            playedSequence = sequence,
            drill = drillResult?.toSummary(),
        )
    }

    fun toJson(summary: PracticeSummary): String = json.encodeToString(summary)

    private fun DrillResult.toSummary() = DrillSummary(
        mode = modeId,
        accuracy = if (totalSteps > 0) correctSteps.toDouble() / totalSteps else null,
        wrongNotes = wrongNotes.distinct().take(8),
    )

    private fun formatSequence(onEvents: List<NoteEvent>, max: Int): String {
        val tail = onEvents.takeLast(max)
        return tail.joinToString(" ") { e ->
            "${midiNoteToLabel(e.note)}(${e.velocity})"
        }
    }

    private fun computeRhythm(onEvents: List<NoteEvent>): RhythmSummary {
        if (onEvents.size < 2) return RhythmSummary()
        val intervals = mutableListOf<Double>()
        for (i in 1 until onEvents.size) {
            val ms = (onEvents[i].timeNanos - onEvents[i - 1].timeNanos) / 1_000_000.0
            if (ms in 20.0..5_000.0) intervals.add(ms)
        }
        if (intervals.isEmpty()) return RhythmSummary()
        val avg = intervals.average()
        val variance = intervals.map { (it - avg).pow(2) }.average()
        val std = sqrt(variance)
        return RhythmSummary(
            avgIoiMs = (avg * 10).roundToInt() / 10.0,
            stdIoiMs = (std * 10).roundToInt() / 10.0,
        )
    }

    private fun guessKey(onEvents: List<NoteEvent>): String {
        val pcs = IntArray(12)
        onEvents.forEach { pcs[it.note % 12]++ }
        val majorProfile = intArrayOf(6, 0, 2, 0, 4, 6, 0, 6, 0, 3, 0, 4)
        var bestRoot = 0
        var bestScore = Int.MIN_VALUE
        for (root in 0 until 12) {
            var score = 0
            for (i in 0 until 12) {
                val idx = (i + root) % 12
                score += pcs[i] * majorProfile[idx]
            }
            if (score > bestScore) {
                bestScore = score
                bestRoot = root
            }
        }
        val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        return if (bestScore <= 0) "unknown" else "${names[bestRoot]} major (heuristic)"
    }
}
