package com.mlainton.nova.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.SkipQueryVerification
import androidx.room.TypeConverters
import com.mlainton.nova.data.local.converters.NovaTypeConverters
import com.mlainton.nova.data.local.dao.ChatDao
import com.mlainton.nova.data.local.dao.MemoryDao
import com.mlainton.nova.data.local.entity.ChatMessageEntity
import com.mlainton.nova.data.local.entity.ChatSessionEntity
import com.mlainton.nova.data.local.entity.MemoryItemEntity

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        MemoryItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
@SkipQueryVerification
@TypeConverters(NovaTypeConverters::class)
abstract class NovaDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile
        private var INSTANCE: NovaDatabase? = null

        fun getInstance(context: Context): NovaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NovaDatabase::class.java,
                    "nova_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
