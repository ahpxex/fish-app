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

    }
}
