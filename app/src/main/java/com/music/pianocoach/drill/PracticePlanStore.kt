package com.music.pianocoach.drill

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class PlanBundle(
    val plans: List<PracticePlan> = emptyList(),
)

class PracticePlanStore(context: Context) {
    private val root = File(context.filesDir, "songs/plans").also { it.mkdirs() }
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    suspend fun loadAll(): Map<String, List<PracticePlan>> = withContext(Dispatchers.IO) {
        root.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { f ->
                val songId = f.nameWithoutExtension
                val plans = readBundle(f).plans.sortedByDescending { it.createdAtEpochMs }
                if (plans.isEmpty()) null else songId to plans
            }
            ?.toMap()
            .orEmpty()
    }

    suspend fun loadForSong(songId: String): List<PracticePlan> = withContext(Dispatchers.IO) {
        readBundle(fileFor(songId)).plans.sortedByDescending { it.createdAtEpochMs }
    }

    suspend fun append(plan: PracticePlan): List<PracticePlan> = withContext(Dispatchers.IO) {
        val existing = readBundle(fileFor(plan.songId)).plans
        val next = (listOf(plan) + existing.filterNot { it.id == plan.id })
            .sortedByDescending { it.createdAtEpochMs }
        writeBundle(plan.songId, PlanBundle(next))
        next
    }

    suspend fun deletePlan(songId: String, planId: String): List<PracticePlan> =
        withContext(Dispatchers.IO) {
            val next = readBundle(fileFor(songId)).plans.filterNot { it.id == planId }
            if (next.isEmpty()) {
                fileFor(songId).delete()
            } else {
                writeBundle(songId, PlanBundle(next))
            }
            next.sortedByDescending { it.createdAtEpochMs }
        }

    suspend fun deleteAllForSong(songId: String) = withContext(Dispatchers.IO) {
        fileFor(songId).delete()
    }

    private fun fileFor(songId: String) = File(root, "$songId.json")

    private fun readBundle(file: File): PlanBundle {
        if (!file.exists()) return PlanBundle()
        return runCatching { json.decodeFromString<PlanBundle>(file.readText()) }
            .getOrDefault(PlanBundle())
    }

    private fun writeBundle(songId: String, bundle: PlanBundle) {
        fileFor(songId).writeText(json.encodeToString(bundle))
    }
}
