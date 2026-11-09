package com.music.pianocoach.drill

import java.io.ByteArrayInputStream

data class ImportedScore(
    val piece: SongPiece,
    val rawBytes: ByteArray,
    val sourceExt: String,
)

/**
 * 根据文件内容选择 MIDI 或 MusicXML 导入。
 */
object ScoreFileImporter {

    fun import(bytes: ByteArray, displayName: String): Result<ImportedScore> {
        val title = sanitizeTitle(displayName)
        return when {
            MusicXmlImporter.isMidi(bytes) -> {
                MidiSongImporter.import(ByteArrayInputStream(bytes), title).map { piece ->
                    ImportedScore(piece, bytes, "mid")
                }
            }
            MusicXmlImporter.isMusicXml(bytes) -> {
                MusicXmlImporter.import(bytes, title).map { piece ->
                    val ext = if (bytes.size >= 2 && bytes[0] == 0x50.toByte()) "mxl" else "musicxml"
                    ImportedScore(piece, bytes, ext)
                }
            }
            else -> Result.failure(
                IllegalArgumentException("不支持的格式，请选 .mid、.midi、.mxl 或 .musicxml"),
            )
        }
    }

    private fun sanitizeTitle(name: String): String = name
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .ifBlank { "导入曲谱" }
}
