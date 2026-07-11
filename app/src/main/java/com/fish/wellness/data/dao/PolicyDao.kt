package com.fish.wellness.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fish.wellness.data.entity.PolicyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PolicyDao {

    @Query("SELECT * FROM policies ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PolicyEntity>>

    @Query("SELECT * FROM policies WHERE id = :id")
    suspend fun getById(id: Long): PolicyEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(policy: PolicyEntity): Long

    @Update
    suspend fun update(policy: PolicyEntity)

    @Query("DELETE FROM policies WHERE id = :id")
    suspend fun deleteById(id: Long)
}
