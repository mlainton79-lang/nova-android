package com.mlainton.nova.domain.model

data class MemoryItem(
    val memoryId: String,
    val category: String,
    val content: String,
    val importance: Int,
    val createdAt: String
)
