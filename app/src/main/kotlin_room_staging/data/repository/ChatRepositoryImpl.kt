package com.mlainton.nova.data.repository

import com.mlainton.nova.data.local.dao.ChatDao
import com.mlainton.nova.data.local.entity.ChatMessageEntity
import com.mlainton.nova.data.local.entity.ChatSessionEntity
import com.mlainton.nova.data.mapper.toDomain
import com.mlainton.nova.domain.model.ChatMessage
import com.mlainton.nova.domain.model.ChatSession
import com.mlainton.nova.domain.repository.ChatRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepositoryImpl(
    private val chatDao: ChatDao,
) : ChatRepository {
    override fun observeSessions(): Flow<List<ChatSession>> =
        chatDao.observeSessions().map { list -> list.map { it.toDomain() } }

    override fun observeMessages(sessionId: String): Flow<List<ChatMessage>> =
        chatDao.observeMessages(sessionId).map { list -> list.map { it.toDomain() } }

    override suspend fun createSession(title: String, activeModel: String): String {
        val now = nowText()
        val id = UUID.randomUUID().toString()
        chatDao.insertSession(
            ChatSessionEntity(
                sessionId = id,
                title = title,
                createdAt = now,
                lastActive = now,
                activeModel = activeModel,
            ),
        )
        return id
    }

    override suspend fun addMessage(
        sessionId: String,
        role: String,
        content: String,
        metadata: Map<String, String>?,
    ) {
        val now = nowText()
        chatDao.insertMessage(
            ChatMessageEntity(
                messageId = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = role,
                content = content,
                timestamp = now,
                metadata = metadata,
            ),
        )
        chatDao.updateLastActive(sessionId, now)
    }

    override suspend fun renameSession(sessionId: String, title: String) {
        chatDao.renameSession(sessionId, title)
    }

    override suspend fun deleteSession(sessionId: String) {
        chatDao.deleteSession(sessionId)
    }

    private fun nowText(): String =
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.UK).format(Date())
}
