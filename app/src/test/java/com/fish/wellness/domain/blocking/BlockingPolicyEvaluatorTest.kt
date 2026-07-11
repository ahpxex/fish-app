package com.fish.wellness.domain.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BlockingPolicyEvaluatorTest {
    private val mondayMorning = LocalDateTime.parse("2026-07-06T09:30")

    @Test
    fun activeFullBlockReturnsItsPolicyAsTheReason() {
        val decision = evaluate(
            policy(name = "Focus", start = 9 * 60, end = 10 * 60, limit = 0)
        )

        assertTrue(decision is PreliminaryDecision.Blocked)
        decision as PreliminaryDecision.Blocked
        assertEquals(BlockReason.SCHEDULE, decision.reason)
        assertEquals("Focus", decision.policyName)
    }

    @Test
    fun inactiveAndDisabledFullBlocksAllowTheApp() {
        assertSame(
            PreliminaryDecision.Allowed,
            evaluate(policy(start = 10 * 60, end = 11 * 60, limit = 0))
        )
        assertSame(
            PreliminaryDecision.Allowed,
            evaluate(policy(start = 9 * 60, end = 10 * 60, limit = 0, enabled = false))
        )
    }

    @Test
    fun dailyLimitIsAnAllDayRuleAndMostRestrictiveLimitWins() {
        val rules = listOf(
            policy(start = 20 * 60, end = 21 * 60, limit = 30),
            policy(id = 2, start = 22 * 60, end = 23 * 60, limit = 10)
        )

        assertEquals(PreliminaryDecision.CheckDailyLimit(10), evaluate(*rules.toTypedArray()))
    }

    @Test
    fun quickBlockIncludesAppsSelectedInDisabledPolicies() {
        val rules = BlockingRuleSet(
            policies = listOf(policy(enabled = false, limit = 0)),
            quickBlock = QuickBlockWindow(startAtMillis = 1_000L, endAtMillis = 2_000L)
        )

        val decision = BlockingPolicyEvaluator.preliminaryDecision(
            packageName = PACKAGE,
            rules = rules,
            now = mondayMorning,
            nowEpochMillis = 1_500L
        )

        assertEquals(PreliminaryDecision.Blocked(BlockReason.QUICK_BLOCK), decision)
    }

    @Test
    fun quickBlockEndIsExclusive() {
        val rules = BlockingRuleSet(
            policies = listOf(policy(limit = 0)),
            quickBlock = QuickBlockWindow(startAtMillis = 1_000L, endAtMillis = 2_000L)
        )

        val decision = BlockingPolicyEvaluator.preliminaryDecision(
            packageName = PACKAGE,
            rules = rules,
            now = mondayMorning,
            nowEpochMillis = 2_000L
        )

        assertEquals(PreliminaryDecision.Blocked(BlockReason.SCHEDULE, "Policy"), decision)
    }

    @Test
    fun dailyLimitUsesExactMillisecondsWithoutBlockingTheLastPartialMinute() {
        val almostUsed = BlockingPolicyEvaluator.resolveDailyLimit(
            limitMinutes = 10,
            usedMillis = 9 * 60_000L + 59_000L
        )
        assertFalse(almostUsed.isBlocked)
        assertEquals(1_000L, almostUsed.remainingMillis)

        val exhausted = BlockingPolicyEvaluator.resolveDailyLimit(
            limitMinutes = 10,
            usedMillis = 10 * 60_000L
        )
        assertTrue(exhausted.isBlocked)
        assertEquals(BlockReason.DAILY_LIMIT, exhausted.reason)
    }

    private fun evaluate(vararg policies: PolicyRule): PreliminaryDecision =
        BlockingPolicyEvaluator.preliminaryDecision(
            packageName = PACKAGE,
            rules = BlockingRuleSet(policies.toList(), quickBlock = null),
            now = mondayMorning,
            nowEpochMillis = 1_500L
        )

    private fun policy(
        id: Long = 1,
        name: String = "Policy",
        start: Int = 9 * 60,
        end: Int = 10 * 60,
        limit: Int,
        enabled: Boolean = true
    ) = PolicyRule(
        id = id,
        name = name,
        startMinutes = start,
        endMinutes = end,
        daysOfWeek = 1,
        enabled = enabled,
        apps = listOf(AppBlockingRule(PACKAGE, limit))
    )

    companion object {
        private const val PACKAGE = "com.example.target"
    }
}
