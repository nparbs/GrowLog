package com.nparbs.growlog.reminders

import com.nparbs.growlog.data.Reminder
import com.nparbs.growlog.data.RepeatRule
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

object ReminderRules {
    private const val DAYS_IN_WEEK = 7L

    fun weekdayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    /** When a newly edited schedule should first come due, starting from [start]. */
    fun firstDue(
        repeat: RepeatRule,
        intervalDays: Int,
        weekdaysMask: Int,
        start: LocalDateTime,
        now: LocalDateTime,
    ): LocalDateTime = when (repeat) {
        RepeatRule.ONCE -> start
        RepeatRule.EVERY_N_DAYS -> {
            var due = start
            while (due.isBefore(now)) due = due.plusDays(intervalDays.coerceAtLeast(1).toLong())
            due
        }
        RepeatRule.WEEKLY -> {
            val earliest = if (start.isBefore(now)) now else start
            nextMatchingWeekday(start.minusDays(1), weekdaysMask, earliest) ?: start
        }
    }

    /** The occurrence after the current one, or null when the reminder doesn't repeat. */
    fun nextDue(reminder: Reminder, now: LocalDateTime): LocalDateTime? {
        val floor = if (reminder.nextDue.isAfter(now)) reminder.nextDue else now
        return when (reminder.repeat) {
            RepeatRule.ONCE -> null
            RepeatRule.EVERY_N_DAYS -> {
                val step = reminder.intervalDays.coerceAtLeast(1).toLong()
                var due = reminder.nextDue.plusDays(step)
                while (!due.isAfter(floor)) due = due.plusDays(step)
                due
            }
            RepeatRule.WEEKLY -> nextMatchingWeekday(reminder.nextDue, reminder.weekdaysMask, floor)
        }
    }

    /** Moves a resumed reminder past occurrences missed while paused. One-offs stay put. */
    fun rollForward(reminder: Reminder, now: LocalDateTime): LocalDateTime =
        if (reminder.repeat == RepeatRule.ONCE || !reminder.nextDue.isBefore(now)) {
            reminder.nextDue
        } else {
            nextDue(reminder, now) ?: reminder.nextDue
        }

    /** First matching weekday strictly after [from]'s date, keeping its time, not before [floor]. */
    private fun nextMatchingWeekday(
        from: LocalDateTime,
        mask: Int,
        floor: LocalDateTime,
    ): LocalDateTime? {
        if (mask == 0) return null
        var due = from.plusDays(1)
        // Two weeks always covers a match, even when this week's day has already passed.
        repeat((DAYS_IN_WEEK * 2).toInt() + daysBetween(from, floor)) {
            if (mask and weekdayBit(due.dayOfWeek) != 0 && !due.isBefore(floor)) return due
            due = due.plusDays(1)
        }
        return null
    }

    private fun daysBetween(from: LocalDateTime, to: LocalDateTime): Int =
        ChronoUnit.DAYS.between(from, to).coerceAtLeast(0).toInt()
}
