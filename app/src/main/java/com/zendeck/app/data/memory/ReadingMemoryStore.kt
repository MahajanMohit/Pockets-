package com.zendeck.app.data.memory

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class MemoryData(
    /** domain -> number of times user opened a link from this domain */
    val openedDomains: Map<String, Int> = emptyMap(),
    /** tag -> number of times a tag appeared in an opened link */
    val openedTags: Map<String, Int> = emptyMap(),
    /** domain -> number of times user archived without opening */
    val skippedDomains: Map<String, Int> = emptyMap(),
    /** Last 10 titles the user actually opened (for LLM context) */
    val recentTitles: List<String> = emptyList(),
    val totalOpened: Int = 0,
    val totalSkipped: Int = 0
)

/**
 * Lightweight reading-pattern store backed by a JSON file in the app's private filesDir.
 *
 * The store keeps a hot in-memory cache so reads are O(1).
 * Writes go to disk synchronously — call from Dispatchers.IO only.
 */
class ReadingMemoryStore private constructor(context: Context) {

    private val file = File(context.filesDir, "reading_memory.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Volatile private var cache: MemoryData? = null

    // ── Public API ────────────────────────────────────────────────────────────

    /** Call on Dispatchers.IO when the user opens (double-taps) a link. */
    fun recordOpen(domain: String, tags: List<String>, title: String) {
        val m = load()
        val domains = m.openedDomains.toMutableMap()
        domains[domain] = (domains[domain] ?: 0) + 1
        val tagMap = m.openedTags.toMutableMap()
        tags.forEach { tag -> tagMap[tag] = (tagMap[tag] ?: 0) + 1 }
        val titles = (listOf(title.take(80)) + m.recentTitles).take(10)
        save(m.copy(
            openedDomains = domains,
            openedTags = tagMap,
            recentTitles = titles,
            totalOpened = m.totalOpened + 1
        ))
    }

    /** Call on Dispatchers.IO when the user archives a link without opening it. */
    fun recordSkip(domain: String) {
        val m = load()
        val skipped = m.skippedDomains.toMutableMap()
        skipped[domain] = (skipped[domain] ?: 0) + 1
        save(m.copy(skippedDomains = skipped, totalSkipped = m.totalSkipped + 1))
    }

    /** Returns the cached MemoryData — always O(1). */
    fun getMemory(): MemoryData = load()

    /**
     * Returns a compact string for use inside LLM prompts.
     * Returns empty string if not enough reading history yet.
     */
    fun promptContext(): String {
        val m = load()
        if (m.totalOpened < 5) return ""

        val topDomains = m.openedDomains.entries
            .sortedByDescending { it.value }.take(5)
            .joinToString(", ") { it.key }

        val topTags = m.openedTags.entries
            .sortedByDescending { it.value }.take(6)
            .joinToString(", ") { "#${it.key}" }

        val skipped = m.skippedDomains.entries
            .filter { (d, skipCnt) -> skipCnt > (m.openedDomains[d] ?: 0) }
            .take(3)
            .joinToString(", ") { it.key }

        return buildString {
            if (topDomains.isNotEmpty()) append("Sources I read: $topDomains. ")
            if (topTags.isNotEmpty()) append("Topics I care about: $topTags. ")
            if (skipped.isNotEmpty()) append("I usually skip: $skipped.")
        }.trim()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun load(): MemoryData {
        cache?.let { return it }
        return try {
            if (file.exists())
                json.decodeFromString<MemoryData>(file.readText()).also { cache = it }
            else
                MemoryData().also { cache = it }
        } catch (e: Exception) {
            Log.w(TAG, "Load failed: ${e.message}")
            MemoryData().also { cache = it }
        }
    }

    private fun save(data: MemoryData) {
        cache = data
        try { file.writeText(json.encodeToString(data)) }
        catch (e: Exception) { Log.w(TAG, "Save failed: ${e.message}") }
    }

    companion object {
        private const val TAG = "ReadingMemoryStore"

        @Volatile private var INSTANCE: ReadingMemoryStore? = null
        fun getInstance(context: Context): ReadingMemoryStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ReadingMemoryStore(context.applicationContext).also { INSTANCE = it }
            }
    }
}
