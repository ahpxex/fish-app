package com.fish.wellness.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fish.wellness.data.entity.QuickBlockSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickBlockSessionDao {

    @Query("SELECT * FROM quick_block_sessions WHERE isActive = 1 ORDER BY startAt DESC")
    fun observeActive(): Flow<List<QuickBlockSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: QuickBlockSessionEntity): Long

    @Query("UPDATE quick_block_sessions SET isActive = 0 WHERE isActive = 1")
    suspend fun cancelAllActive()

    @Query("UPDATE quick_block_sessions SET isActive = 0 WHERE endAt <= :now AND isActive = 1")
    suspend fun expireOldSessions(now: Long): Int
}
