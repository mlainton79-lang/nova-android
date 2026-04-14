package com.mlainton.nova.presentation.state

import com.mlainton.nova.domain.model.ChatMessage
import com.mlainton.nova.domain.model.ChatSession
import com.mlainton.nova.domain.model.MemoryItem

data class NovaUiState(
    val sessions: List<ChatSession> = emptyList(),
    val activeSessionId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val memories: List<MemoryItem> = emptyList(),
    val draft: String = "",
)
