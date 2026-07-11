package com.fish.wellness.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun policyDao(): PolicyDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun quickBlockSessionDao(): QuickBlockSessionDao

    companion object {
        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE blocked_apps RENAME TO blocked_apps_legacy")
                db.execSQL("ALTER TABLE schedules RENAME TO schedules_legacy")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS policies (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        startMinutes INTEGER NOT NULL,
                        endMinutes INTEGER NOT NULL,
                        daysOfWeek INTEGER NOT NULL,
                        enabled INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO policies (id, name, startMinutes, endMinutes, daysOfWeek, enabled, createdAt)
                    SELECT id, name, startMinutes, endMinutes, daysOfWeek, enabled, createdAt
                    FROM schedules_legacy
                    """.trimIndent()
                )

                // Keep apps available to Quick Block even when the old database had no schedule.
                db.execSQL(
                    """
                    INSERT INTO policies (name, startMinutes, endMinutes, daysOfWeek, enabled, createdAt)
                    SELECT 'Imported apps', 0, 0, 127, 0, MIN(addedAt)
                    FROM blocked_apps_legacy
                    HAVING COUNT(*) > 0 AND NOT EXISTS (SELECT 1 FROM policies)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS blocked_apps (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        policyId INTEGER NOT NULL,
                        packageName TEXT NOT NULL,
                        appName TEXT NOT NULL,
                        dailyLimitMinutes INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(policyId) REFERENCES policies(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO blocked_apps (policyId, packageName, appName, dailyLimitMinutes, createdAt)
                    SELECT policies.id, legacy.packageName, legacy.appName, 0, legacy.addedAt
                    FROM policies
                    CROSS JOIN blocked_apps_legacy AS legacy
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_blocked_apps_policyId ON blocked_apps(policyId)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_blocked_apps_policyId_packageName " +
                        "ON blocked_apps(policyId, packageName)"
                )

                db.execSQL("DROP TABLE blocked_apps_legacy")
                db.execSQL("DROP TABLE schedules_legacy")
            }
        }
    }
}
