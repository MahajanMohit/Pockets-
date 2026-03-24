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

    init {
        _modelAvailable.value = LlmSummarizationService.hasModel(application)
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
        }
    }

    override fun onCleared() {
        super.onCleared()
        llmService.close()
    }
}
