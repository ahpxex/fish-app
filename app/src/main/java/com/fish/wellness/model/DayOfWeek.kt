package com.fish.wellness.model

enum class DayOfWeek(val calValue: Int, val bit: Int, val shortName: String) {
    MON(java.util.Calendar.MONDAY, 1, "Mon"),
    TUE(java.util.Calendar.TUESDAY, 2, "Tue"),
    WED(java.util.Calendar.WEDNESDAY, 4, "Wed"),
    THU(java.util.Calendar.THURSDAY, 8, "Thu"),
    FRI(java.util.Calendar.FRIDAY, 16, "Fri"),
    SAT(java.util.Calendar.SATURDAY, 32, "Sat"),
    SUN(java.util.Calendar.SUNDAY, 64, "Sun");

    companion object {
        val ALL = listOf(MON, TUE, WED, THU, FRI, SAT, SUN)

        fun fromBits(bits: Int): Set<DayOfWeek> =
            ALL.filter { it.bit and bits != 0 }.toSet()

        fun toBits(days: Set<DayOfWeek>): Int =
            days.sumOf { it.bit }

        const val EVERY_DAY = 127
        const val WEEKDAYS = 31
        const val WEEKENDS = 96
    }
}

data class TimeRange(val startMinutes: Int, val endMinutes: Int) {
    val startHour get() = startMinutes / 60
    val startMinute get() = startMinutes % 60
    val endHour get() = endMinutes / 60
    val endMinute get() = endMinutes % 60

    fun formatStart(): String = "%02d:%02d".format(startHour, startMinute)
    fun formatEnd(): String = "%02d:%02d".format(endHour, endMinute)

    companion object {
        fun fromTime(hour: Int, minute: Int) = hour * 60 + minute
    }
}
