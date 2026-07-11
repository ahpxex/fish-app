package com.fish.wellness.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fish.wellness.domain.blocking.WeeklyScheduleMatcher
import java.time.LocalDateTime

@Entity(tableName = "policies")
data class PolicyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val daysOfWeek: Int = EVERY_DAY,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val EVERY_DAY = 127
        const val WEEKDAYS = 31
        const val WEEKENDS = 96
    }

    fun isCurrentlyActive(now: LocalDateTime = LocalDateTime.now()): Boolean =
        enabled && WeeklyScheduleMatcher.isActive(
            startMinutes = startMinutes,
            endMinutes = endMinutes,
            daysOfWeek = daysOfWeek,
            now = now
        )

    val startLabel: String get() = "%02d:%02d".format(startMinutes / 60, startMinutes % 60)
    val endLabel: String get() = "%02d:%02d".format(endMinutes / 60, endMinutes % 60)
}
