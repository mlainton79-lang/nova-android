package com.mlainton.nova.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val sessionId: String,
    val title: String,
    val createdAt: String,
    val lastActive: String,
    val activeModel: String,
)
