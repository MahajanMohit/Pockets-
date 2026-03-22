package com.zendeck.app.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.zendeck.app.data.memory.ReadingMemoryStore
import com.zendeck.app.data.repository.LinkRepository
import com.zendeck.app.domain.model.LinkItem
import com.zendeck.app.domain.model.ReadingRating
import com.zendeck.app.service.LlmSummarizationService
import com.zendeck.app.service.ReadingAdvisorService
import com.zendeck.app.worker.ModelDownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InboxViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = LinkRepository.getInstance(application)
    private val memoryStore = ReadingMemoryStore.getInstance(application)

    // ── Model availability ────────────────────────────────────────────────────

    private val _modelAvailable = MutableStateFlow(true)
    val modelAvailable: StateFlow<Boolean> = _modelAvailable.asStateFlow()

    /** Re-checks whether the Gemma model file is present on disk. */
    fun checkModelAvailability() {
        _modelAvailable.value = LlmSummarizationService.hasModel(getApplication())
    }

    /**
     * Enqueues a background [ModelDownloadWorker] to fetch the Gemma model.
     * Requires an unmetered (WiFi) connection and won't re-enqueue if already running.
     */
    fun enqueueModelDownload() {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
            )
            .build()
        WorkManager.getInstance(getApplication())
            .enqueueUniqueWork(
                ModelDownloadWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
    }

    // ── Base link flows ───────────────────────────────────────────────────────

    private val topFiveLinks: StateFlow<List<LinkItem>> = repo
        .getTopFiveActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val allActiveLinks: StateFlow<List<LinkItem>> = repo
        .getActiveLinks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val archivedLinks: StateFlow<List<LinkItem>> = repo
        .getArchivedLinks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Search ────────────────────────────────────────────────────────────────

    private val _inboxSearch = MutableStateFlow("")
    val inboxSearch: StateFlow<String> = _inboxSearch.asStateFlow()

    private val _archiveSearch = MutableStateFlow("")
    val archiveSearch: StateFlow<String> = _archiveSearch.asStateFlow()

    fun setInboxSearch(q: String) { _inboxSearch.value = q }
    fun setArchiveSearch(q: String) { _archiveSearch.value = q }

    /** Inbox links: top-5 when not searching, full filtered list while searching. */
    val filteredInboxLinks: StateFlow<List<LinkItem>> = combine(
        topFiveLinks, allActiveLinks, _inboxSearch
    ) { top, all, q ->
        if (q.isBlank()) top
        else all.filter { it.matches(q) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredArchivedLinks: StateFlow<List<LinkItem>> = combine(
        archivedLinks, _archiveSearch
    ) { links, q ->
        if (q.isBlank()) links else links.filter { it.matches(q) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── AI Ratings ────────────────────────────────────────────────────────────

    /** Incremented whenever reading memory is updated so ratings recompute. */
    private val _memoryVersion = MutableStateFlow(0)

    /** LinkId → ReadingRating (null = not enough history yet). */
    val inboxRatings: StateFlow<Map<String, ReadingRating?>> = combine(
        filteredInboxLinks, _memoryVersion
    ) { links, _ ->
        val memory = memoryStore.getMemory()
        links.associate { it.id to ReadingAdvisorService.rate(it, memory) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // ── Engagement recording ──────────────────────────────────────────────────

    /** Call when user double-taps a card (actually reads it). */
    fun recordLinkOpen(link: LinkItem) = viewModelScope.launch(Dispatchers.IO) {
        memoryStore.recordOpen(link.domain, link.tags, link.title)
        _memoryVersion.update { it + 1 }
    }

    /** Call when user archives a link without opening it (implicit skip). */
    fun recordLinkSkip(link: LinkItem) = viewModelScope.launch(Dispatchers.IO) {
        memoryStore.recordSkip(link.domain)
        _memoryVersion.update { it + 1 }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    fun archiveLink(id: String) = viewModelScope.launch { repo.archiveLink(id) }

    fun togglePin(id: String, pinned: Boolean) = viewModelScope.launch {
        repo.togglePin(id, pinned)
    }

    fun saveTagsAndPin(id: String, tags: List<String>, isPinned: Boolean) =
        viewModelScope.launch {
            repo.updateTags(id, tags)
            val current = allActiveLinks.value.find { it.id == id }
                ?: archivedLinks.value.find { it.id == id }
            if (current != null && current.isPinned != isPinned) {
                repo.togglePin(id, isPinned)
            }
        }

    fun deleteLink(id: String) = viewModelScope.launch { repo.deleteLink(id) }

    fun restoreLink(id: String) = viewModelScope.launch { repo.restoreLink(id) }

    fun openInCustomTab(context: Context, url: String) {
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build()
                .launchUrl(context, url.toUri())
        } catch (e: Exception) {
            val fallback = android.content.Intent(
                android.content.Intent.ACTION_VIEW, url.toUri()
            )
            fallback.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun LinkItem.matches(query: String): Boolean {
        val q = query.lowercase()
        return title.lowercase().contains(q) ||
            domain.lowercase().contains(q) ||
            description.lowercase().contains(q) ||
            tags.any { it.lowercase().contains(q) }
    }
}
