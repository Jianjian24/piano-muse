package com.music.pianocoach.drill

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class LibraryIndex(
    val songIds: List<String> = emptyList(),
    val lastSelectedId: String? = null,
)

/**
 * 将导入的曲子持久化到应用私有目录，下次启动自动恢复。
 */
class SongLibraryStore(context: Context) {
    private val root = File(context.filesDir, "songs").also { it.mkdirs() }
    private val indexFile = File(root, "index.json")
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    suspend fun loadImported(): List<SongPiece> = withContext(Dispatchers.IO) {
        val index = readIndex()
        index.songIds.mapNotNull { id ->
            val f = File(root, "$id.json")
            if (!f.exists()) return@mapNotNull null
            runCatching { json.decodeFromString<SongPiece>(f.readText()) }.getOrNull()
        }
    }

    suspend fun lastSelectedId(): String? = withContext(Dispatchers.IO) {
        readIndex().lastSelectedId
    }

    suspend fun saveImported(piece: SongPiece): SongPiece = withContext(Dispatchers.IO) {
        val f = File(root, "${piece.id}.json")
        f.writeText(json.encodeToString(piece))
        val index = readIndex()
        val ids = (listOf(piece.id) + index.songIds).distinct()
        writeIndex(index.copy(songIds = ids, lastSelectedId = piece.id))
        piece
    }

    suspend fun setLastSelected(id: String?) = withContext(Dispatchers.IO) {
        val index = readIndex()
        writeIndex(index.copy(lastSelectedId = id))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(root, "$id.json").delete()
        File(root, "$id.mid").delete()
        File(File(root, "plans"), "$id.json").delete()
        val index = readIndex()
        writeIndex(
            index.copy(
                songIds = index.songIds.filterNot { it == id },
                lastSelectedId = index.lastSelectedId?.takeIf { it != id },
            ),
        )
    }

    /** 顺带保存原始 MIDI，便于以后重新解析 */
    suspend fun saveMidiBytes(id: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        File(root, "$id.mid").writeBytes(bytes)
    }

    private fun readIndex(): LibraryIndex {
        if (!indexFile.exists()) return LibraryIndex()
        return runCatching {
            json.decodeFromString<LibraryIndex>(indexFile.readText())
        }.getOrDefault(LibraryIndex())
    }

    private fun writeIndex(index: LibraryIndex) {
        indexFile.writeText(json.encodeToString(index))
    }
}
