package com.fish.wellness.manager

import android.content.Context
import com.fish.wellness.data.dao.BlockedAppDao
import com.fish.wellness.data.dao.PolicyDao
import com.fish.wellness.data.dao.QuickBlockSessionDao
import com.fish.wellness.data.entity.BlockedAppEntity
import com.fish.wellness.util.UsageTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppBlockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockedAppDao: BlockedAppDao,
    private val policyDao: PolicyDao,
    private val quickBlockDao: QuickBlockSessionDao
) {

    private val mutex = Mutex()

    suspend fun isAppBlocked(packageName: String): Boolean {
        if (packageName == context.packageName) return false

        return mutex.withLock {
            val now = System.currentTimeMillis()
            quickBlockDao.expireOldSessions(now)

            val allBlocks = blockedAppDao.getActiveBlocksForPackage(packageName)
            if (allBlocks.isEmpty()) return@withLock false

            // Quick block: any app in any enabled policy is fully blocked
            val activeQuick = quickBlockDao.getActiveAt(now)
            if (activeQuick != null) return@withLock true

            // Schedule-based: only check policies active right now
            val activePolicyIds = policyDao.getEnabledPolicies()
                .filter { it.isCurrentlyActive() }
                .map { it.id }
                .toSet()

            val activeBlocks = allBlocks.filter { it.policyId in activePolicyIds }
            if (activeBlocks.isEmpty()) return@withLock false

            // Full block wins immediately
            if (activeBlocks.any { it.isFullBlock }) return@withLock true

            // Time-limited: check usage against most restrictive active limit
            val minLimit = activeBlocks.minOf { it.dailyLimitMinutes }
            val remaining = UsageTracker.getRemainingMinutes(context, packageName, minLimit)
            return@withLock remaining <= 0
        }
    }

    suspend fun shouldShowBlockOverlay(packageName: String): Boolean =
        packageName != context.packageName && isAppBlocked(packageName)

    suspend fun isTimeLimited(packageName: String): Boolean {
        if (packageName == context.packageName) return false
        return mutex.withLock {
            val activePolicyIds = policyDao.getEnabledPolicies()
                .filter { it.isCurrentlyActive() }
                .map { it.id }
                .toSet()
            if (activePolicyIds.isEmpty()) return@withLock false

            val activeBlocks = blockedAppDao.getActiveBlocksForPackage(packageName)
                .filter { it.policyId in activePolicyIds }

            activeBlocks.isNotEmpty() && activeBlocks.all { !it.isFullBlock }
        }
    }

    suspend fun getRemainingMinutes(packageName: String): Int {
        return mutex.withLock {
            val activePolicyIds = policyDao.getEnabledPolicies()
                .filter { it.isCurrentlyActive() }
                .map { it.id }
                .toSet()
            val activeBlocks = blockedAppDao.getActiveBlocksForPackage(packageName)
                .filter { it.policyId in activePolicyIds && !it.isFullBlock }
            if (activeBlocks.isEmpty()) return@withLock 0
            val minLimit = activeBlocks.minOf { it.dailyLimitMinutes }
            UsageTracker.getRemainingMinutes(context, packageName, minLimit)
        }
    }
}
