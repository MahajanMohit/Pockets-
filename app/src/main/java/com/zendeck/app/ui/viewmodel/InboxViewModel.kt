package com.zendeck.app.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zendeck.app.data.repository.LinkRepository
import com.zendeck.app.service.LinkScraperService
import com.zendeck.app.service.SummaryEngine
import com.zendeck.app.widget.ZenDeckWidget
import com.zendeck.app.domain.model.LinkItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InboxViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = LinkRepository.getInstance(application)

    companion object {
        private const val TAG = "InboxViewModel"
    }

    // ── Article count ─────────────────────────────────────────────────────────

    val activeLinkCount: StateFlow<Int> = repo
        .getActiveLinkCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ── Base link flows ───────────────────────────────────────────────────────

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

    val filteredInboxLinks: StateFlow<List<LinkItem>> = combine(
        allActiveLinks, _inboxSearch
    ) { all, q ->
        if (q.isBlank()) all else all.filter { it.matches(q) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredArchivedLinks: StateFlow<List<LinkItem>> = combine(
        archivedLinks, _archiveSearch
    ) { links, q ->
        if (q.isBlank()) links else links.filter { it.matches(q) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Actions ───────────────────────────────────────────────────────────────

    fun archiveLink(id: String) = viewModelScope.launch {
        repo.archiveLink(id)
        ZenDeckWidget.updateAll(getApplication())
    }

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

    fun deleteLink(id: String) = viewModelScope.launch {
        repo.deleteLink(id)
        ZenDeckWidget.updateAll(getApplication())
    }

    fun restoreLink(id: String) = viewModelScope.launch {
        repo.restoreLink(id)
        ZenDeckWidget.updateAll(getApplication())
    }

    fun openInCustomTab(context: Context, url: String) {
        // Don't try to open synthetic image:// or text:// URLs in a browser
        if (url.startsWith("image://") || url.startsWith("text://")) return
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

    // ── Re-summarize ──────────────────────────────────────────────────────────

    /**
     * Re-fetches the page and regenerates the summary with the on-device
     * extractive engine. For notes, re-summarizes the stored text directly.
     */
    fun resummarizeLink(link: LinkItem) = viewModelScope.launch(Dispatchers.IO) {
        try {
            repo.updateSummaryStatus(link.id, "pending")
            val sourceText = when (link.contentType) {
                "link" -> {
                    val scraped = LinkScraperService.scrape(link.url)
                    if (scraped.title.isNotBlank() && scraped.title != link.domain) {
                        repo.updateLinkMetadata(
                            link.id, scraped.title, scraped.description,
                            scraped.domain, scraped.faviconUrl
                        )
                    }
                    scraped.bodyText
                }
                else -> link.description
            }
            val summary = if (sourceText.isNotBlank()) SummaryEngine.summarize(sourceText) else ""
            if (summary.isNotBlank()) {
                repo.updateSummary(link.id, summary)
                repo.updateSummaryStatus(link.id, "done")
            } else {
                repo.updateSummaryStatus(link.id, "unavailable")
            }
        } catch (e: Exception) {
            Log.w(TAG, "resummarizeLink failed: ${e.message}")
            repo.updateSummaryStatus(link.id, "unavailable")
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
