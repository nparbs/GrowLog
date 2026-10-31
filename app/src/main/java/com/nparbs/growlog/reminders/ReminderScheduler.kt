package com.nparbs.growlog.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nparbs.growlog.data.Reminder
import java.time.ZoneId

/**
 * Uses inexact while-idle alarms: they need no special permission and work through Doze, at the
 * cost of possibly arriving some minutes late, which is fine for plant care.
 */
class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(reminder: Reminder, force: Boolean = false) {
        val alreadyNotified = reminder.lastNotifiedDue == reminder.dueAt
        if (!reminder.enabled || (alreadyNotified && !force)) {
            cancel(reminder.id)
            return
        }
        val triggerAt = reminder.dueAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(reminder.id))
    }

    fun cancel(reminderId: Long) {
        alarmManager.cancel(pendingIntent(reminderId))
    }

    private fun pendingIntent(reminderId: Long): PendingIntent =
        ReminderReceiver.pendingIntent(context, ReminderReceiver.ACTION_FIRE, reminderId)
}
