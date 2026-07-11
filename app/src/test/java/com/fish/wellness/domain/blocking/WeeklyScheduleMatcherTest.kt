package com.fish.wellness.domain.blocking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class WeeklyScheduleMatcherTest {
    private val mondayOnly = 1

    @Test
    fun sameDayWindowUsesSelectedCalendarDay() {
        assertTrue(activeAt(9 * 60, 17 * 60, "2026-07-06T09:00"))
        assertTrue(activeAt(9 * 60, 17 * 60, "2026-07-06T16:59"))
        assertFalse(activeAt(9 * 60, 17 * 60, "2026-07-06T17:00"))
        assertFalse(activeAt(9 * 60, 17 * 60, "2026-07-07T10:00"))
    }

    @Test
    fun overnightWindowCarriesTheStartingDaysSelectionPastMidnight() {
        assertTrue(activeAt(22 * 60, 14 * 60, "2026-07-06T22:00"))
        assertTrue(activeAt(22 * 60, 14 * 60, "2026-07-07T00:00"))
        assertTrue(activeAt(22 * 60, 14 * 60, "2026-07-07T13:59"))
        assertFalse(activeAt(22 * 60, 14 * 60, "2026-07-07T14:00"))
        assertFalse(activeAt(22 * 60, 14 * 60, "2026-07-07T22:00"))
    }

    @Test
    fun equalEndpointsRepresentTwentyFourHoursFromSelectedStart() {
        assertTrue(activeAt(22 * 60, 22 * 60, "2026-07-06T23:00"))
        assertTrue(activeAt(22 * 60, 22 * 60, "2026-07-07T21:59"))
        assertFalse(activeAt(22 * 60, 22 * 60, "2026-07-07T22:00"))
    }

    @Test
    fun invalidStoredTimesFailClosedInsteadOfCrashing() {
        assertFalse(activeAt(-1, 60, "2026-07-06T00:30"))
        assertFalse(activeAt(60, 24 * 60, "2026-07-06T01:30"))
        assertFalse(WeeklyScheduleMatcher.isActive(0, 60, 0, LocalDateTime.parse("2026-07-06T00:30")))
    }

    private fun activeAt(start: Int, end: Int, timestamp: String): Boolean =
        WeeklyScheduleMatcher.isActive(
            startMinutes = start,
            endMinutes = end,
            daysOfWeek = mondayOnly,
            now = LocalDateTime.parse(timestamp)
        )
}
