package com.mlainton.nova.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlainton.nova.domain.model.ChatMessage
import com.mlainton.nova.domain.model.ChatSession
import com.mlainton.nova.domain.model.MemoryItem
import com.mlainton.nova.domain.repository.ChatRepository
import com.mlainton.nova.domain.repository.MemoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NovaViewModel(
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
) : ViewModel() {
    val sessions: StateFlow<List<ChatSession>> = chatRepository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<List<ChatMessage>> = activeSessionId
        .flatMapLatest { sessionId ->
            if (sessionId == null) flowOf(emptyList())
            else chatRepository.observeMessages(sessionId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val memories: StateFlow<List<MemoryItem>> = memoryRepository.observeMemory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setDraft(value: String) {
        _draft.value = value
    }

    fun switchSession(sessionId: String) {
        _activeSessionId.value = sessionId
        _draft.value = ""
    }

    fun createSession(title: String = "New Chat", activeModel: String = "local-tony") {
        viewModelScope.launch {
            val id = chatRepository.createSession(title, activeModel)
            _activeSessionId.value = id
        }
    }

    fun addMessage(role: String, content: String, metadata: Map<String, String>? = null) {
        val sessionId = _activeSessionId.value ?: return
        viewModelScope.launch {
            chatRepository.addMessage(sessionId, role, content, metadata)
        }
    }

    fun renameCurrentSession(title: String) {
        val sessionId = _activeSessionId.value ?: return
        viewModelScope.launch {
            chatRepository.renameSession(sessionId, title)
        }
    }
}
