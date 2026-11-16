package com.music.pianocoach.drill

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 用户改过的指法。内置曲每次启动都会重新生成，所以不能只写在内存里。
 * 导入曲的曲谱 JSON 也会带上指法，这里再留一份，避免只写到其中一边。
 */
class FingerOverrideStore(context: Context) {
    private val dir = File(context.filesDir, "songs/fingers").also { it.mkdirs() }
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, List<FingerEdit>>()
    private var loaded = false

    suspend fun loadAll(): Map<String, List<FingerEdit>> = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoaded()
            cache.mapValues { it.value.toList() }
        }
    }

    suspend fun upsert(songId: String, edit: FingerEdit) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoaded()
            val next = cache[songId].orEmpty().filterNot { it.sameSlot(edit) } + edit
            cache[songId] = next
            write(songId, next)
        }
    }

    suspend fun clear(songId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoaded()
            cache.remove(songId)
            fileFor(songId).delete()
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty().forEach { f ->
            val id = f.name.removeSuffix(".json")
            val edits = runCatching {
                json.decodeFromString<FingerFile>(f.readText()).edits
            }.getOrDefault(emptyList())
            if (edits.isNotEmpty()) cache[id] = edits
        }
        loaded = true
    }

    private fun write(songId: String, edits: List<FingerEdit>) {
        val file = fileFor(songId)
        if (edits.isEmpty()) {
            file.delete()
            return
        }
        file.writeText(json.encodeToString(FingerFile(edits)))
    }

    private fun fileFor(songId: String): File {
        val safe = songId.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        return File(dir, "$safe.json")
    }

    private fun FingerEdit.sameSlot(other: FingerEdit): Boolean =
        startTick == other.startTick && midi == other.midi && hand == other.hand && track == other.track

    @Serializable
    private data class FingerFile(val edits: List<FingerEdit> = emptyList())
}
