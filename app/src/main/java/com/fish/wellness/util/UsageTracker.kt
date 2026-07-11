package com.fish.wellness.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar
import kotlin.math.max

object UsageTracker {

    @Suppress("DEPRECATION")
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

        val aggregateMillis = usageStatsManager
            .queryAndAggregateUsageStats(startToday, now)[packageName]
            ?.totalTimeInForeground
            ?: 0L

        var eventMillis = 0L
        var foregroundSince: Long? = null
        val events = usageStatsManager.queryEvents(startToday, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != packageName) continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    if (foregroundSince == null) {
                        foregroundSince = max(startToday, event.timeStamp)
                    }
                }

                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    foregroundSince?.let { startedAt ->
                        eventMillis += (event.timeStamp - startedAt).coerceAtLeast(0L)
                        foregroundSince = null
                    }
                }
            }
        }
        foregroundSince?.let { eventMillis += (now - it).coerceAtLeast(0L) }

        // Aggregate stats are durable, while events include the current session more quickly.
        return max(aggregateMillis, eventMillis)
    }

}
