package com.mlainton.nova.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mlainton.nova.data.local.entity.MemoryItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryItemEntity)

    @Query("SELECT * FROM memory_items ORDER BY importance DESC, createdAt DESC")
    fun observeMemory(): Flow<List<MemoryItemEntity>>

    @Query("DELETE FROM memory_items WHERE memoryId = :memoryId")
    suspend fun deleteMemory(memoryId: String)
}
