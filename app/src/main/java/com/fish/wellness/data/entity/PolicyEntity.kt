package com.fish.wellness.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar

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

    private fun isActiveOnDay(dayOfWeek: Int): Boolean {
        val bit = when (dayOfWeek) {
            Calendar.MONDAY -> DAY_MON
            Calendar.TUESDAY -> DAY_TUE
            Calendar.WEDNESDAY -> DAY_WED
            Calendar.THURSDAY -> DAY_THU
            Calendar.FRIDAY -> DAY_FRI
            Calendar.SATURDAY -> DAY_SAT
            Calendar.SUNDAY -> DAY_SUN
            else -> 0
        }
        return daysOfWeek and bit != 0
    }

    fun isCurrentlyActive(now: Calendar = Calendar.getInstance()): Boolean {
        if (!enabled) return false
        if (!isActiveOnDay(now.get(Calendar.DAY_OF_WEEK))) return false
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes until endMinutes
        } else {
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        }
    }

    val startLabel: String get() = "%02d:%02d".format(startMinutes / 60, startMinutes % 60)
    val endLabel: String get() = "%02d:%02d".format(endMinutes / 60, endMinutes % 60)
}
