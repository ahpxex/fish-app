package com.fish.wellness.manager

import android.content.Context
import com.fish.wellness.data.dao.BlockedAppDao
import com.fish.wellness.data.dao.QuickBlockSessionDao
import com.fish.wellness.data.dao.ScheduleDao
import com.fish.wellness.data.entity.ScheduleEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppBlockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockedAppDao: BlockedAppDao,
    private val scheduleDao: ScheduleDao,
    private val quickBlockDao: QuickBlockSessionDao
) {

    private val mutex = Mutex()

    suspend fun isAppBlocked(packageName: String): Boolean {
        if (packageName == context.packageName) return false

        return mutex.withLock {
            val blockedPackages = blockedAppDao.getAllPackageNames().toHashSet()
            if (packageName !in blockedPackages) return@withLock false

            val now = System.currentTimeMillis()
            quickBlockDao.expireOldSessions(now)

            val activeQuick = quickBlockDao.getActiveAt(now)
            if (activeQuick != null) return@withLock true

            val enabledSchedules = scheduleDao.getEnabledSchedules()
            enabledSchedules.any { it.isCurrentlyActive() }
        }
    }

    suspend fun shouldShowBlockOverlay(packageName: String): Boolean =
        packageName != context.packageName && isAppBlocked(packageName)

    suspend fun getActiveSchedules(): List<ScheduleEntity> =
        scheduleDao.getEnabledSchedules().filter { it.isCurrentlyActive() }
}
