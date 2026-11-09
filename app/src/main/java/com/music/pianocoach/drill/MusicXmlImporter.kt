package com.music.pianocoach.drill

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * MusicXML（.musicxml / 未压缩 XML）与 Audiveris 常见的 .mxl 压缩包。
 * 覆盖 partwise、多声部、和弦与 backup/forward。
 */
object MusicXmlImporter {

    fun import(bytes: ByteArray, title: String = "导入曲谱"): Result<SongPiece> = runCatching {
        val xmlBytes = unwrapToXmlBytes(bytes)
        importXml(xmlBytes.inputStream(), title)
    }

    fun import(stream: InputStream, title: String = "导入曲谱"): Result<SongPiece> = runCatching {
        val data = stream.readBytes()
        import(data, title).getOrThrow()
    }

    private fun unwrapToXmlBytes(bytes: ByteArray): ByteArray {
        if (!isZip(bytes)) return bytes
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var rootPath: String? = null
            val entries = mutableListOf<Pair<String, ByteArray>>()
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val name = entry.name
                val content = zip.readBytes()
                entries.add(name to content)
                if (name == "META-INF/container.xml") {
                    rootPath = parseContainerRoot(content)
                }
                zip.closeEntry()
            }
            rootPath?.let { path ->
                entries.firstOrNull { it.first == path }?.second?.let { return it }
            }
            return entries
                .filter { (n, _) -> n.endsWith(".xml", ignoreCase = true) && !n.startsWith("META-INF") }
                .maxByOrNull { it.second.size }
                ?.second
                ?: error("压缩包内未找到 MusicXML")
        }
    }

    private fun parseContainerRoot(containerXml: ByteArray): String? {
        val parser = newParser(containerXml.inputStream())
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && tagName(parser) == "rootfile") {
                return parser.getAttributeValue(null, "full-path")
            }
            event = parser.next()
        }
        return null
    }

    private fun importXml(stream: InputStream, title: String): SongPiece {
        val parser = newParser(stream)
        var workTitle: String? = null
        var movementTitle: String? = null
        val allNotes = mutableListOf<TimedNote>()
        var partIndex = -1
        var divisions = 480
        var measureBaseTick = 0
        var timeInMeasure = 0
        var measureOpen = false

        fun closeMeasure() {
            if (!measureOpen) return
            measureBaseTick += timeInMeasure
            timeInMeasure = 0
            measureOpen = false
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (tagName(parser)) {
                    "work-title" -> workTitle = parser.nextText().trim()
                    "movement-title" -> movementTitle = parser.nextText().trim()
                    "part" -> {
                        closeMeasure()
                        partIndex++
                        measureBaseTick = 0
                        timeInMeasure = 0
                        measureOpen = false
                        divisions = 480
                    }
                    "measure" -> {
                        closeMeasure()
                        measureOpen = true
                        timeInMeasure = 0
                    }
                    "attributes" -> {
                        divisions = readAttributes(parser, divisions)
                    }
                    "backup" -> {
                        val d = readDuration(parser) ?: 0
                        timeInMeasure = (timeInMeasure - d).coerceAtLeast(0)
                    }
                    "forward" -> {
                        val d = readDuration(parser) ?: 0
                        timeInMeasure += d
                    }
                    "note" -> {
                        val note = readNote(parser, divisions)
                        if (note != null && !note.isRest) {
                            val start = measureBaseTick + timeInMeasure
                            val hand = when (note.staff) {
                                1 -> PianoHand.RIGHT
                                2 -> PianoHand.LEFT
                                else -> if (note.midi >= 60) PianoHand.RIGHT else PianoHand.LEFT
                            }
                            allNotes.add(
                                TimedNote(
                                    midi = note.midi,
                                    startTick = start,
                                    durationTicks = note.duration.coerceAtLeast(1),
                                    velocity = 80,
                                    track = partIndex.coerceAtLeast(0),
                                    hand = hand,
                                ),
                            )
                        }
                        if (note == null || !note.isChord) {
                            timeInMeasure += note?.duration ?: divisions / 4
                        }
                    }
                }
                XmlPullParser.END_TAG -> if (tagName(parser) == "measure") {
                    closeMeasure()
                }
            }
            event = parser.next()
        }
        closeMeasure()

        require(allNotes.isNotEmpty()) { "MusicXML 中未解析到音符" }

        val withHands = HandAssigner.assign(allNotes)
        val sourcePpq = divisions.coerceAtLeast(1)
        val (scaled, ppq) = TickNormalizer.normalize(withHands, sourcePpq)
        val withFingers = FingeringAssigner.assign(scaled, ppq)
        val sorted = withFingers.sortedWith(compareBy({ it.startTick }, { it.midi }))
        val chordSteps = SongPiece(
            id = "tmp",
            title = title,
            timedNotes = sorted,
            ticksPerQuarter = ppq,
        ).toChordSteps().size

        val cleanTitle = title
            .removeSuffix(".mxl")
            .removeSuffix(".MXL")
            .removeSuffix(".musicxml")
            .removeSuffix(".xml")
        val displayTitle = movementTitle ?: workTitle ?: cleanTitle

        return SongPiece(
            id = "import_${System.currentTimeMillis()}",
            title = displayTitle.ifBlank { cleanTitle },
            description = "MusicXML · ${sorted.size} 音 · $chordSteps 步 · ${partIndex + 1} 声部 · PPQ $ppq",
            notes = sorted.map { SongNote(it.midi, it.durationTicks.toDouble() / ppq) },
            timedNotes = sorted,
            ticksPerQuarter = ppq,
        )
    }

    private data class ParsedNote(
        val midi: Int,
        val duration: Int,
        val isRest: Boolean,
        val isChord: Boolean,
        val staff: Int = 0,
    )

    private fun readNote(parser: XmlPullParser, divisions: Int): ParsedNote? {
        var isRest = false
        var isChord = false
        var duration = divisions
        var midi: Int? = null
        var staff = 0
        var depth = parser.depth
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT && (event != XmlPullParser.END_TAG || parser.depth > depth)) {
            if (event == XmlPullParser.START_TAG) {
                when (tagName(parser)) {
                    "rest" -> isRest = true
                    "chord" -> isChord = true
                    "duration" -> duration = parser.nextText().trim().toIntOrNull() ?: duration
                    "pitch" -> midi = readPitch(parser)
                    "staff" -> staff = parser.nextText().trim().toIntOrNull() ?: 0
                    "grace" -> isRest = true
                }
            }
            event = parser.next()
        }
        if (isRest) return ParsedNote(0, duration, true, isChord, staff)
        return midi?.let { ParsedNote(it, duration, false, isChord, staff) }
    }

    private fun readPitch(parser: XmlPullParser): Int {
        var step = 'C'
        var octave = 4
        var alter = 0
        val depth = parser.depth
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT && (event != XmlPullParser.END_TAG || parser.depth > depth)) {
            if (event == XmlPullParser.START_TAG) {
                when (tagName(parser)) {
                    "step" -> step = parser.nextText().trim().firstOrNull() ?: 'C'
                    "octave" -> octave = parser.nextText().trim().toIntOrNull() ?: 4
                    "alter" -> alter = parser.nextText().trim().toDoubleOrNull()?.toInt() ?: 0
                }
            }
            event = parser.next()
        }
        return pitchToMidi(step, octave, alter)
    }

    private fun readDuration(parser: XmlPullParser): Int? {
        val depth = parser.depth
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT && (event != XmlPullParser.END_TAG || parser.depth > depth)) {
            if (event == XmlPullParser.START_TAG && tagName(parser) == "duration") {
                return parser.nextText().trim().toIntOrNull()
            }
            event = parser.next()
        }
        return null
    }

    private fun readAttributes(parser: XmlPullParser, currentDivisions: Int): Int {
        var divisions = currentDivisions
        val depth = parser.depth
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT && (event != XmlPullParser.END_TAG || parser.depth > depth)) {
            if (event == XmlPullParser.START_TAG && tagName(parser) == "divisions") {
                divisions = parser.nextText().trim().toIntOrNull() ?: divisions
            }
            event = parser.next()
        }
        return divisions
    }

    private fun pitchToMidi(step: Char, octave: Int, alter: Int): Int {
        val pc = when (step.uppercaseChar()) {
            'C' -> 0
            'D' -> 2
            'E' -> 4
            'F' -> 5
            'G' -> 7
            'A' -> 9
            'B' -> 11
            else -> 0
        }
        return (octave + 1) * 12 + pc + alter
    }

    private fun newParser(stream: InputStream): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newPullParser().apply {
            setInput(stream, "UTF-8")
        }
    }

    private fun tagName(parser: XmlPullParser): String {
        val raw = parser.name ?: return ""
        val colon = raw.indexOf(':')
        return if (colon >= 0) raw.substring(colon + 1) else raw
    }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    fun isMusicXml(bytes: ByteArray): Boolean {
        if (isZip(bytes)) return true
        val head = bytes.decodeToString(0, minOf(bytes.size, 256))
        return head.contains("score-partwise", ignoreCase = true) ||
            head.contains("score-timewise", ignoreCase = true)
    }

    fun isMidi(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'M'.code.toByte() && bytes[1] == 'T'.code.toByte()
}
