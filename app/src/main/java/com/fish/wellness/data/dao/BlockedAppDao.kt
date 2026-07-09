package com.fish.wellness.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.fish.wellness.data.entity.BlockedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {

    @Query("SELECT * FROM blocked_apps ORDER BY appName ASC")
    fun observeAll(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps")
    suspend fun getAll(): List<BlockedAppEntity>

    @Query("SELECT packageName FROM blocked_apps")
    suspend fun getAllPackageNames(): List<String>

    @Upsert
    suspend fun upsert(app: BlockedAppEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(apps: List<BlockedAppEntity>)

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("DELETE FROM blocked_apps")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM blocked_apps")
    suspend fun count(): Int
}
