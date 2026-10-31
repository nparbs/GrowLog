package com.nparbs.growlog.reminders

import com.nparbs.growlog.data.Reminder
import com.nparbs.growlog.data.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDateTime

class ReminderRulesTest {
    // 2026-11-02 is a Monday.
    private val mondayNine = LocalDateTime.of(2026, 11, 2, 9, 0)
    private val monWedFri = ReminderRules.weekdayBit(MONDAY) or
        ReminderRules.weekdayBit(WEDNESDAY) or ReminderRules.weekdayBit(FRIDAY)

    private fun reminder(
        repeat: RepeatRule,
        nextDue: LocalDateTime = mondayNine,
        interval: Int = 1,
        mask: Int = 0,
    ) = Reminder(
        title = "t",
        repeat = repeat,
        intervalDays = interval,
        weekdaysMask = mask,
        nextDue = nextDue,
    )

    @Test
    fun onceHasNoNextOccurrence() {
        assertNull(ReminderRules.nextDue(reminder(RepeatRule.ONCE), mondayNine))
    }

    @Test
    fun everyNDaysAdvancesOneInterval() {
        val next = ReminderRules.nextDue(reminder(RepeatRule.EVERY_N_DAYS, interval = 3), mondayNine)
        assertEquals(mondayNine.plusDays(3), next)
    }

    @Test
    fun everyNDaysSkipsOccurrencesMissedWhileOverdue() {
        val now = mondayNine.plusDays(7).plusHours(1)
        val next = ReminderRules.nextDue(reminder(RepeatRule.EVERY_N_DAYS, interval = 3), now)
        assertEquals(mondayNine.plusDays(9), next)
    }

    @Test
    fun weeklyMovesToNextSelectedDay() {
        val next = ReminderRules.nextDue(reminder(RepeatRule.WEEKLY, mask = monWedFri), mondayNine)
        assertEquals(mondayNine.plusDays(2), next)
    }

    @Test
    fun weeklyWrapsIntoNextWeek() {
        val friday = mondayNine.plusDays(4)
        val next = ReminderRules.nextDue(reminder(RepeatRule.WEEKLY, friday, mask = monWedFri), friday)
        assertEquals(mondayNine.plusDays(7), next)
    }

    @Test
    fun weeklySkipsOccurrencesMissedLongAgo() {
        val now = mondayNine.plusDays(30)
        val next = ReminderRules.nextDue(reminder(RepeatRule.WEEKLY, mask = monWedFri), now)!!
        assert(!next.isBefore(now)) { "$next is before $now" }
        assert(next.isBefore(now.plusDays(4))) { "$next skipped too far past $now" }
    }

    @Test
    fun firstDueWeeklyIncludesStartDayWhenStillAhead() {
        val due = ReminderRules.firstDue(
            RepeatRule.WEEKLY, 1, monWedFri, mondayNine, mondayNine.minusHours(1),
        )
        assertEquals(mondayNine, due)
    }

    @Test
    fun firstDueWeeklySkipsStartDayWhenTimePassed() {
        val due = ReminderRules.firstDue(
            RepeatRule.WEEKLY, 1, monWedFri, mondayNine, mondayNine.plusHours(1),
        )
        assertEquals(mondayNine.plusDays(2), due)
    }

    @Test
    fun firstDueEveryNDaysRollsPastStartToTheFuture() {
        val now = mondayNine.plusDays(5)
        val due = ReminderRules.firstDue(RepeatRule.EVERY_N_DAYS, 2, 0, mondayNine, now)
        assertEquals(mondayNine.plusDays(6), due)
    }

    @Test
    fun rollForwardLeavesFutureAndOneOffRemindersAlone() {
        val future = reminder(RepeatRule.EVERY_N_DAYS, mondayNine.plusDays(1))
        assertEquals(future.nextDue, ReminderRules.rollForward(future, mondayNine))
        val once = reminder(RepeatRule.ONCE)
        assertEquals(once.nextDue, ReminderRules.rollForward(once, mondayNine.plusDays(9)))
    }
}
