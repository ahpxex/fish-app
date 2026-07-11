package com.fish.wellness.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fish.wellness.data.entity.BlockedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {

    @Query("SELECT * FROM blocked_apps WHERE policyId = :policyId ORDER BY appName ASC")
    suspend fun getByPolicy(policyId: Long): List<BlockedAppEntity>

    @Query("SELECT * FROM blocked_apps ORDER BY policyId, appName ASC")
    fun observeAll(): Flow<List<BlockedAppEntity>>

    @Query("SELECT COUNT(*) FROM blocked_apps WHERE policyId = :policyId")
    fun observeCountByPolicy(policyId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(app: BlockedAppEntity): Long

    @Query("""
        UPDATE blocked_apps
        SET appName = :appName, dailyLimitMinutes = :dailyLimitMinutes
        WHERE policyId = :policyId AND packageName = :packageName
    """)
    suspend fun updateRule(
        policyId: Long,
        packageName: String,
        appName: String,
        dailyLimitMinutes: Int
    ): Int

    @androidx.room.Transaction
    suspend fun upsertRule(app: BlockedAppEntity) {
        val updated = updateRule(
            policyId = app.policyId,
            packageName = app.packageName,
            appName = app.appName,
            dailyLimitMinutes = app.dailyLimitMinutes
        )
        if (updated == 0) insert(app)
    }

    @Query("DELETE FROM blocked_apps WHERE policyId = :policyId AND packageName = :packageName")
    suspend fun delete(policyId: Long, packageName: String)

}
