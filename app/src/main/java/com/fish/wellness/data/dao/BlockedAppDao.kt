package com.fish.wellness.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fish.wellness.data.entity.BlockedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {

    @Query("SELECT * FROM blocked_apps WHERE policyId = :policyId ORDER BY appName ASC")
    fun observeByPolicy(policyId: Long): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps WHERE policyId = :policyId ORDER BY appName ASC")
    suspend fun getByPolicy(policyId: Long): List<BlockedAppEntity>

    @Query("""
        SELECT b.* FROM blocked_apps b
        INNER JOIN policies p ON b.policyId = p.id
        WHERE p.enabled = 1 AND b.packageName = :packageName
    """)
    suspend fun getActiveBlocksForPackage(packageName: String): List<BlockedAppEntity>

    @Query("""
        SELECT b.* FROM blocked_apps b
        INNER JOIN policies p ON b.policyId = p.id
        WHERE p.enabled = 1
    """)
    suspend fun getAllActiveBlocks(): List<BlockedAppEntity>

    @Query("SELECT COUNT(*) FROM blocked_apps WHERE policyId = :policyId")
    fun observeCountByPolicy(policyId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(app: BlockedAppEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(apps: List<BlockedAppEntity>)

    @Update
    suspend fun update(app: BlockedAppEntity)

    @Query("DELETE FROM blocked_apps WHERE policyId = :policyId AND packageName = :packageName")
    suspend fun delete(policyId: Long, packageName: String)

    @Query("DELETE FROM blocked_apps WHERE policyId = :policyId")
    suspend fun deleteByPolicy(policyId: Long)
}
