package com.mlainton.nova.domain.repository

import com.mlainton.nova.domain.model.MemoryItem
import kotlinx.coroutines.flow.Flow

interface MemoryRepository {
    fun observeMemory(): Flow<List<MemoryItem>>
    suspend fun saveMemory(
        category: String,
        content: String,
        importance: Int = 3,
    )
    suspend fun forget(memoryId: String)
}
