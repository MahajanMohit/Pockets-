package com.zendeck.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zendeck.app.service.LlmSummarizationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(val text: String, val isUser: Boolean)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val llmService = LlmSummarizationService(application)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _modelAvailable = MutableStateFlow(false)
    val modelAvailable: StateFlow<Boolean> = _modelAvailable.asStateFlow()

    private val _activeModelName = MutableStateFlow<String?>(null)
    val activeModelName: StateFlow<String?> = _activeModelName.asStateFlow()

    init { refreshModelState() }

    /** Re-reads model presence from disk — updates automatically after import. */
    fun refreshModelState() {
        val name = LlmSummarizationService.getActiveModelName(getApplication())
        _activeModelName.value = name
        _modelAvailable.value = name != null
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _isLoading.value) return

        _messages.value = _messages.value + ChatMessage(trimmed, isUser = true)
        _isLoading.value = true

        viewModelScope.launch {
            val reply = llmService.chat(trimmed)
            _messages.value = _messages.value + ChatMessage(reply, isUser = false)
            _isLoading.value = false
            refreshModelState() // update name if model just loaded for the first time
        }
    }

    override fun onCleared() {
        super.onCleared()
        llmService.close()
    }
}
