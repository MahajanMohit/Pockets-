package com.zendeck.app.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zendeck.app.data.repository.LinkRepository
import com.zendeck.app.domain.model.LinkItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InboxViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = LinkRepository.getInstance(application)

    val topFiveLinks: StateFlow<List<LinkItem>> = repo
        .getTopFiveActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allActiveLinks: StateFlow<List<LinkItem>> = repo
        .getActiveLinks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val archivedLinks: StateFlow<List<LinkItem>> = repo
        .getArchivedLinks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun archiveLink(id: String) = viewModelScope.launch {
        repo.archiveLink(id)
    }

    fun togglePin(id: String, pinned: Boolean) = viewModelScope.launch {
        repo.togglePin(id, pinned)
    }

    fun updateTags(id: String, tags: List<String>) = viewModelScope.launch {
        repo.updateTags(id, tags)
    }

    fun saveTagsAndPin(id: String, tags: List<String>, isPinned: Boolean) = viewModelScope.launch {
        repo.updateTags(id, tags)
        val current = topFiveLinks.value.find { it.id == id }
            ?: allActiveLinks.value.find { it.id == id }
        if (current != null && current.isPinned != isPinned) {
            repo.togglePin(id, isPinned)
        }
    }

    fun openInCustomTab(context: Context, url: String) {
        try {
            val intent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            intent.launchUrl(context, url.toUri())
        } catch (e: Exception) {
            // Fallback: open in default browser
            val fallback = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                url.toUri()
            )
            fallback.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    }

    fun deleteLink(id: String) = viewModelScope.launch {
        repo.deleteLink(id)
    }
}
