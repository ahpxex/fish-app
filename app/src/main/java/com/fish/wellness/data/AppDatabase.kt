package com.fish.wellness.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fish.wellness.data.dao.BlockedAppDao
import com.fish.wellness.data.dao.QuickBlockSessionDao
import com.fish.wellness.data.dao.ScheduleDao
import com.fish.wellness.data.entity.BlockedAppEntity
import com.fish.wellness.data.entity.QuickBlockSessionEntity
import com.fish.wellness.data.entity.ScheduleEntity

@Database(
    entities = [
        BlockedAppEntity::class,
        ScheduleEntity::class,
        QuickBlockSessionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun quickBlockSessionDao(): QuickBlockSessionDao
}
