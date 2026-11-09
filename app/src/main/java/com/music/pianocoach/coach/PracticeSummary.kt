package com.music.pianocoach.coach

import kotlinx.serialization.Serializable

@Serializable
data class DrillSummary(
    val mode: String? = null,
    val accuracy: Double? = null,
    val wrongNotes: List<String> = emptyList(),
)

@Serializable
data class RhythmSummary(
    val avgIoiMs: Double = 0.0,
    val stdIoiMs: Double = 0.0,
)

@Serializable
data class PracticeSummary(
    val windowSec: Double = 0.0,
    val noteCount: Int = 0,
    val pitchRange: List<String> = emptyList(),
    val detectedKeyHint: String = "unknown",
    val rhythm: RhythmSummary = RhythmSummary(),
    val playedSequence: String = "",
    val drill: DrillSummary? = null,
)
