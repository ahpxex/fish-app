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

    // In-memory snapshot of packages that should be blocked RIGHT NOW (full block or quick block).
    // Refreshed periodically + invalidated on any config change, so the hot path is an O(1) lookup.
    @Volatile
    private var instantlyBlocked: Set<String> = emptySet()

    /**
     * Fast, non-suspending check used on the accessibility hot path.
     * Reads only the in-memory cache — no DB, no I/O.
     */
    fun isInstantlyBlocked(packageName: String): Boolean =
        packageName != context.packageName && packageName in instantlyBlocked

    /** Recompute the blocked-now snapshot from the DB. Call on changes + periodically. */
    suspend fun refreshBlockedSnapshot() {
        val now = System.currentTimeMillis()
        val snapshot = HashSet<String>()
        mutex.withLock {
            quickBlockDao.expireOldSessions(now)
            val quickActive = quickBlockDao.getActiveAt(now) != null

            val activePolicyIds = policyDao.getEnabledPolicies()
                .filter { it.isCurrentlyActive() }
                .map { it.id }
                .toSet()

            for (b in blockedAppDao.getAllActiveBlocks()) {
                when {
                    quickActive -> snapshot.add(b.packageName)                         // quick block: all enabled-policy apps
                    b.policyId in activePolicyIds && b.isFullBlock -> snapshot.add(b.packageName) // schedule full block, active now
                    // usage-limited apps (non-full-block) intentionally excluded -> handled by usage poll
                }
            }
        }
        instantlyBlocked = snapshot
    }

    /** Hook called by ViewModels after any blocking config mutation. */
    suspend fun invalidate() = refreshBlockedSnapshot()

    suspend fun isAppBlocked(packageName: String): Boolean {
        if (packageName == context.packageName) return false

        return mutex.withLock {
            val now = System.currentTimeMillis()
            quickBlockDao.expireOldSessions(now)

            val allBlocks = blockedAppDao.getActiveBlocksForPackage(packageName)
            if (allBlocks.isEmpty()) return@withLock false

            // Quick block: any app in any enabled policy is fully blocked
            if (quickBlockDao.getActiveAt(now) != null) return@withLock true

            // Full block: only within an active (enabled + in-window + matching day) policy
            val activeWindowPolicyIds = policyDao.getEnabledPolicies()
                .filter { it.isCurrentlyActive() }
                .map { it.id }
                .toSet()
            if (allBlocks.filter { it.policyId in activeWindowPolicyIds }.any { it.isFullBlock }) {
                return@withLock true
            }

            // Daily limit: applies ALL DAY across any enabled policy (independent of time window).
            // When today's usage meets/exceeds the limit -> blocked.
            val limited = allBlocks.filter { !it.isFullBlock }
            if (limited.isNotEmpty()) {
                val minLimit = limited.minOf { it.dailyLimitMinutes }
                val remaining = UsageTracker.getRemainingMinutes(context, packageName, minLimit)
                if (remaining <= 0) return@withLock true
            }

            return@withLock false
        }
    }

    suspend fun shouldShowBlockOverlay(packageName: String): Boolean =
        packageName != context.packageName && isAppBlocked(packageName)

    /** App carries a daily limit (any enabled policy), regardless of the policy's time window. */
    suspend fun isTimeLimited(packageName: String): Boolean {
        if (packageName == context.packageName) return false
        return mutex.withLock {
            blockedAppDao.getActiveBlocksForPackage(packageName).any { !it.isFullBlock }
        }
    }

    /** Most restrictive daily limit (minutes) across all enabled policies, or null if none. */
    suspend fun getDailyLimitMinutes(packageName: String): Int? = mutex.withLock {
        val limited = blockedAppDao.getActiveBlocksForPackage(packageName).filter { !it.isFullBlock }
        if (limited.isEmpty()) null else limited.minOf { it.dailyLimitMinutes }
    }

    suspend fun getRemainingMinutes(packageName: String): Int {
        val limit = getDailyLimitMinutes(packageName) ?: return 0
        return UsageTracker.getRemainingMinutes(context, packageName, limit)
    }
}
