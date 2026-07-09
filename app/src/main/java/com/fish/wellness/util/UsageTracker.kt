package com.fish.wellness.util

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

object UsageTracker {

    fun getForegroundMillisToday(context: Context, packageName: String): Long {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startToday = cal.timeInMillis
        val now = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startToday,
            now
        ) ?: return 0

        return stats
            .filter { it.packageName == packageName }
            .sumOf { it.totalTimeInForeground }
    }

    fun getRemainingMinutes(context: Context, packageName: String, dailyLimitMinutes: Int): Int {
        val usedMillis = getForegroundMillisToday(context, packageName)
        val limitMillis = dailyLimitMinutes * 60_000L
        val remaining = (limitMillis - usedMillis) / 60_000
        return remaining.toInt().coerceAtLeast(0)
    }
}
