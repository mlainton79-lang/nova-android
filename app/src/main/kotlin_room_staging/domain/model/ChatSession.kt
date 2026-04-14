package com.mlainton.nova.domain.model

data class ChatSession(
    val sessionId: String,
    val title: String,
    val createdAt: String,
    val lastActive: String,
    val activeModel: String
)
