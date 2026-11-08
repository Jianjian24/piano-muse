package com.music.pianocoach.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteParserTest {

    @Test
    fun noteOnAndOff() {
        val events = mutableListOf<NoteEvent>()
        val parser = NoteParser { events.add(it) }
        val on = byteArrayOf(0x90.toByte(), 60, 80)
        parser.feed(on, 0, on.size, 1L)
        assertEquals(1, events.size)
        assertTrue(events[0].on)
        assertEquals(60, events[0].note)
        assertEquals(80, events[0].velocity)

        val off = byteArrayOf(0x80.toByte(), 60, 40)
        parser.feed(off, 0, off.size, 2L)
        assertEquals(2, events.size)
        assertEquals(false, events[1].on)
    }

    @Test
    fun ignoresActiveSensing() {
        val events = mutableListOf<NoteEvent>()
        val parser = NoteParser { events.add(it) }
        parser.feed(byteArrayOf(0xFE.toByte()), 0, 1, 0L)
        parser.feed(byteArrayOf(0xF8.toByte()), 0, 1, 0L)
        assertEquals(0, events.size)
    }

    @Test
    fun noteOnVelocityZeroIsOff() {
        val events = mutableListOf<NoteEvent>()
        val parser = NoteParser { events.add(it) }
        parser.feed(byteArrayOf(0x90.toByte(), 64, 0), 0, 3, 0L)
        assertEquals(1, events.size)
        assertEquals(false, events[0].on)
    }
}
