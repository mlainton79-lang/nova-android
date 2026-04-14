package com.mlainton.nova.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_items")
data class MemoryItemEntity(
    @PrimaryKey val memoryId: String,
    val category: String,
    val content: String,
    val importance: Int,
    val createdAt: String,
)
