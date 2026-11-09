package com.music.pianocoach.drill

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MidiSongImporterTest {

    @Test
    fun importCallOfSilenceKeepsChords() {
        val path = File("""e:\file\music coach\MID\Call of Silence.mid""")
        if (!path.exists()) return // 本机无此文件则跳过
        val piece = MidiSongImporter.import(path.inputStream(), "Call of Silence").getOrThrow()
        assertTrue(piece.timedNotes.size > 100)
        val steps = piece.toChordSteps()
        assertTrue("应有多步", steps.size > 50)
        assertTrue("应有和弦", steps.any { it.notes.size >= 2 })
        assertTrue(piece.pitchRange().first < 50)
    }
}
