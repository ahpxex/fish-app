package com.fish.wellness.data.dao

import androidx.room.Dao
import androidx.room.Delete
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

    @Query("SELECT * FROM policies WHERE enabled = 1")
    suspend fun getEnabledPolicies(): List<PolicyEntity>

    @Query("SELECT * FROM policies WHERE id = :id")
    suspend fun getById(id: Long): PolicyEntity?

    @Query("SELECT * FROM policies WHERE id = :id")
    fun observeById(id: Long): Flow<PolicyEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(policy: PolicyEntity): Long

    @Update
    suspend fun update(policy: PolicyEntity)

    @Delete
    suspend fun delete(policy: PolicyEntity)

    @Query("DELETE FROM policies WHERE id = :id")
    suspend fun deleteById(id: Long)
}
