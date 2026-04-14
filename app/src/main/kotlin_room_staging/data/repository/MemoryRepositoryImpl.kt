package com.mlainton.nova.data.repository

import com.mlainton.nova.data.local.dao.MemoryDao
import com.mlainton.nova.data.local.entity.MemoryItemEntity
import com.mlainton.nova.data.mapper.toDomain
import com.mlainton.nova.domain.model.MemoryItem
import com.mlainton.nova.domain.repository.MemoryRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MemoryRepositoryImpl(
    private val memoryDao: MemoryDao,
) : MemoryRepository {
    override fun observeMemory(): Flow<List<MemoryItem>> =
        memoryDao.observeMemory().map { list -> list.map { it.toDomain() } }

    override suspend fun saveMemory(category: String, content: String, importance: Int) {
        memoryDao.insertMemory(
            MemoryItemEntity(
                memoryId = UUID.randomUUID().toString(),
                category = category,
                content = content,
                importance = importance,
                createdAt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.UK).format(Date()),
            ),
        )
    }

    override suspend fun forget(memoryId: String) {
        memoryDao.deleteMemory(memoryId)
    }
}
