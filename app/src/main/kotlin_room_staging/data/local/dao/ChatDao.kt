package com.mlainton.nova.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mlainton.nova.data.local.entity.ChatMessageEntity
import com.mlainton.nova.data.local.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_sessions ORDER BY lastActive DESC")
    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeMessages(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("UPDATE chat_sessions SET title = :title WHERE sessionId = :sessionId")
    suspend fun renameSession(sessionId: String, title: String)

    @Query("UPDATE chat_sessions SET lastActive = :lastActive WHERE sessionId = :sessionId")
    suspend fun updateLastActive(sessionId: String, lastActive: String)

    @Query("DELETE FROM chat_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)
}
