package com.mlainton.nova.domain.repository

import com.mlainton.nova.domain.model.ChatMessage
import com.mlainton.nova.domain.model.ChatSession
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeSessions(): Flow<List<ChatSession>>
    fun observeMessages(sessionId: String): Flow<List<ChatMessage>>
    suspend fun createSession(title: String = "New Chat", activeModel: String = "local-tony"): String
    suspend fun addMessage(
        sessionId: String,
        role: String,
        content: String,
        metadata: Map<String, String>? = null,
    )
    suspend fun renameSession(sessionId: String, title: String)
    suspend fun deleteSession(sessionId: String)
}
