package com.mlainton.nova.data.mapper

import com.mlainton.nova.data.local.entity.ChatMessageEntity
import com.mlainton.nova.data.local.entity.ChatSessionEntity
import com.mlainton.nova.data.local.entity.MemoryItemEntity
import com.mlainton.nova.domain.model.ChatMessage
import com.mlainton.nova.domain.model.ChatSession
import com.mlainton.nova.domain.model.MemoryItem

fun ChatSessionEntity.toDomain(): ChatSession = ChatSession(
    sessionId = sessionId,
    title = title,
    createdAt = createdAt,
    lastActive = lastActive,
    activeModel = activeModel,
)

fun ChatMessageEntity.toDomain(): ChatMessage = ChatMessage(
    messageId = messageId,
    sessionId = sessionId,
    role = role,
    content = content,
    timestamp = timestamp,
    metadata = metadata,
)

fun MemoryItemEntity.toDomain(): MemoryItem = MemoryItem(
    memoryId = memoryId,
    category = category,
    content = content,
    importance = importance,
    createdAt = createdAt,
)
