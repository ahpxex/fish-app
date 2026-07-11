package com.fish.wellness.manager

import com.fish.wellness.data.dao.BlockedAppDao
import com.fish.wellness.data.dao.PolicyDao
import com.fish.wellness.data.dao.QuickBlockSessionDao
import com.fish.wellness.domain.blocking.AppBlockingRule
import com.fish.wellness.domain.blocking.BlockingPolicyEvaluator
import com.fish.wellness.domain.blocking.BlockingRuleSet
import com.fish.wellness.domain.blocking.PolicyRule
import com.fish.wellness.domain.blocking.PreliminaryDecision
import com.fish.wellness.domain.blocking.QuickBlockWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockingPolicyStore @Inject constructor(
    policyDao: PolicyDao,
    blockedAppDao: BlockedAppDao,
    private val quickBlockDao: QuickBlockSessionDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val rules: StateFlow<BlockingRuleSet?> = combine(
        policyDao.observeAll(),
        blockedAppDao.observeAll(),
        quickBlockDao.observeActive()
    ) { policies, blockedApps, quickBlocks ->
        val appsByPolicy = blockedApps.groupBy { it.policyId }
        BlockingRuleSet(
            policies = policies.map { policy ->
                PolicyRule(
                    id = policy.id,
                    name = policy.name,
                    startMinutes = policy.startMinutes,
                    endMinutes = policy.endMinutes,
                    daysOfWeek = policy.daysOfWeek,
                    enabled = policy.enabled,
                    apps = appsByPolicy[policy.id].orEmpty().map { app ->
                        AppBlockingRule(
                            packageName = app.packageName,
                            dailyLimitMinutes = app.dailyLimitMinutes
                        )
                    }
                )
            },
            quickBlock = quickBlocks
                .maxByOrNull { it.endAt }
                ?.let { QuickBlockWindow(it.startAt, it.endAt) }
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            quickBlockDao.expireOldSessions(System.currentTimeMillis())
        }
    }

    fun observeRules(): Flow<BlockingRuleSet> = rules.filterNotNull()

    fun cachedDecision(
        packageName: String,
        now: LocalDateTime = LocalDateTime.now(),
        nowEpochMillis: Long = System.currentTimeMillis()
    ): PreliminaryDecision? = rules.value?.let {
        BlockingPolicyEvaluator.preliminaryDecision(packageName, it, now, nowEpochMillis)
    }

    suspend fun awaitDecision(packageName: String): PreliminaryDecision {
        val loadedRules = rules.filterNotNull().first()
        return BlockingPolicyEvaluator.preliminaryDecision(
            packageName = packageName,
            rules = loadedRules,
            now = LocalDateTime.now(),
            nowEpochMillis = System.currentTimeMillis()
        )
    }
}
