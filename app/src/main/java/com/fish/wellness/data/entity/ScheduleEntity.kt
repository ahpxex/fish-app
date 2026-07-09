package com.fish.wellness.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val daysOfWeek: Int,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DAY_MON = 1
        const val DAY_TUE = 2
        const val DAY_WED = 4
        const val DAY_THU = 8
        const val DAY_FRI = 16
        const val DAY_SAT = 32
        const val DAY_SUN = 64
        const val EVERY_DAY = 127
        const val WEEKDAYS = 31
        const val WEEKENDS = 96
    }

    fun isActiveOnDay(dayOfWeek: Int): Boolean {
        val bit = when (dayOfWeek) {
            java.util.Calendar.MONDAY -> DAY_MON
            java.util.Calendar.TUESDAY -> DAY_TUE
            java.util.Calendar.WEDNESDAY -> DAY_WED
            java.util.Calendar.THURSDAY -> DAY_THU
            java.util.Calendar.FRIDAY -> DAY_FRI
            java.util.Calendar.SATURDAY -> DAY_SAT
            java.util.Calendar.SUNDAY -> DAY_SUN
            else -> 0
        }
        return daysOfWeek and bit != 0
    }

    fun isCurrentlyActive(now: java.util.Calendar = java.util.Calendar.getInstance()): Boolean {
        if (!enabled) return false
        if (!isActiveOnDay(now.get(java.util.Calendar.DAY_OF_WEEK))) return false

        val currentMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)

        return if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes until endMinutes
        } else {
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        }
    }
}
