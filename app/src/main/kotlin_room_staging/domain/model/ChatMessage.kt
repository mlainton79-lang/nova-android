package com.mlainton.nova.domain.model

data class ChatMessage(
    val messageId: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val timestamp: String,
    val metadata: Map<String, String>? = null
)
