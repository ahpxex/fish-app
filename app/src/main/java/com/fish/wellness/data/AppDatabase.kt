package com.fish.wellness.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fish.wellness.data.dao.BlockedAppDao
import com.fish.wellness.data.dao.PolicyDao
import com.fish.wellness.data.dao.QuickBlockSessionDao
import com.fish.wellness.data.entity.BlockedAppEntity
import com.fish.wellness.data.entity.PolicyEntity
import com.fish.wellness.data.entity.QuickBlockSessionEntity

@Database(
    entities = [
        PolicyEntity::class,
        BlockedAppEntity::class,
        QuickBlockSessionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun policyDao(): PolicyDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun quickBlockSessionDao(): QuickBlockSessionDao
}
