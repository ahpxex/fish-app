package com.fish.wellness.domain.blocking

import java.time.DayOfWeek
import java.time.LocalDateTime

data class AppBlockingRule(
    val packageName: String,
    val dailyLimitMinutes: Int
) {
    val isFullBlock: Boolean get() = dailyLimitMinutes <= 0
}

data class PolicyRule(
    val id: Long,
    val name: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val daysOfWeek: Int,
    val enabled: Boolean,
    val apps: List<AppBlockingRule>
)

data class QuickBlockWindow(
    val startAtMillis: Long,
    val endAtMillis: Long
) {
    fun contains(epochMillis: Long): Boolean =
        epochMillis >= startAtMillis && epochMillis < endAtMillis
}

data class BlockingRuleSet(
    val policies: List<PolicyRule>,
    val quickBlock: QuickBlockWindow?
)

enum class BlockReason {
    QUICK_BLOCK,
    SCHEDULE,
    DAILY_LIMIT
}

sealed interface PreliminaryDecision {
    data object Allowed : PreliminaryDecision

    data class Blocked(
        val reason: BlockReason,
        val policyName: String? = null
    ) : PreliminaryDecision

    data class CheckDailyLimit(val limitMinutes: Int) : PreliminaryDecision
}

data class BlockingDecision(
    val isBlocked: Boolean,
    val reason: BlockReason? = null,
    val remainingMillis: Long? = null
)

object WeeklyScheduleMatcher {
    private const val MINUTES_PER_DAY = 24 * 60

    fun isActive(
        startMinutes: Int,
        endMinutes: Int,
        daysOfWeek: Int,
        now: LocalDateTime
    ): Boolean {
        if (startMinutes !in 0 until MINUTES_PER_DAY) return false
        if (endMinutes !in 0 until MINUTES_PER_DAY) return false
        if (daysOfWeek == 0) return false

        val currentMinutes = now.hour * 60 + now.minute
        return when {
            startMinutes < endMinutes -> {
                isSelected(daysOfWeek, now.dayOfWeek) &&
                    currentMinutes >= startMinutes && currentMinutes < endMinutes
            }

            startMinutes > endMinutes -> when {
                currentMinutes >= startMinutes -> isSelected(daysOfWeek, now.dayOfWeek)
                currentMinutes < endMinutes -> isSelected(daysOfWeek, now.minusDays(1).dayOfWeek)
                else -> false
            }

            // Equal endpoints mean a 24-hour window beginning on each selected day.
            currentMinutes >= startMinutes -> isSelected(daysOfWeek, now.dayOfWeek)
            else -> isSelected(daysOfWeek, now.minusDays(1).dayOfWeek)
        }
    }

    private fun isSelected(daysOfWeek: Int, day: DayOfWeek): Boolean {
        val bit = 1 shl (day.value - 1)
        return daysOfWeek and bit != 0
    }
}

object BlockingPolicyEvaluator {
    fun preliminaryDecision(
        packageName: String,
        rules: BlockingRuleSet,
        now: LocalDateTime,
        nowEpochMillis: Long
    ): PreliminaryDecision {
        val policiesForApp = rules.policies.filter { policy ->
            policy.apps.any { it.packageName == packageName }
        }
        if (policiesForApp.isEmpty()) return PreliminaryDecision.Allowed

        if (rules.quickBlock?.contains(nowEpochMillis) == true) {
            return PreliminaryDecision.Blocked(BlockReason.QUICK_BLOCK)
        }

        val enabledPolicies = policiesForApp.filter { it.enabled }
        for (policy in enabledPolicies) {
            val appRule = policy.apps.first { it.packageName == packageName }
            if (appRule.isFullBlock && WeeklyScheduleMatcher.isActive(
                    startMinutes = policy.startMinutes,
                    endMinutes = policy.endMinutes,
                    daysOfWeek = policy.daysOfWeek,
                    now = now
                )
            ) {
                return PreliminaryDecision.Blocked(
                    reason = BlockReason.SCHEDULE,
                    policyName = policy.name
                )
            }
        }

        val dailyLimit = enabledPolicies
            .flatMap { it.apps }
            .filter { it.packageName == packageName && !it.isFullBlock }
            .minOfOrNull { it.dailyLimitMinutes }

        return if (dailyLimit == null) {
            PreliminaryDecision.Allowed
        } else {
            PreliminaryDecision.CheckDailyLimit(dailyLimit)
        }
    }

    fun resolveDailyLimit(limitMinutes: Int, usedMillis: Long): BlockingDecision {
        val limitMillis = limitMinutes * 60_000L
        val remaining = (limitMillis - usedMillis).coerceAtLeast(0L)
        return if (usedMillis >= limitMillis) {
            BlockingDecision(isBlocked = true, reason = BlockReason.DAILY_LIMIT, remainingMillis = 0L)
        } else {
            BlockingDecision(isBlocked = false, remainingMillis = remaining)
        }
    }
}
