package com.nparbs.growlog.reminders

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nparbs.growlog.GrowLogApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles alarms firing and the Done / Snooze notification buttons. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        if (id < 0) return
        val repository = (context.applicationContext as GrowLogApp).container.repository
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> repository.onReminderFired(id)
                    ACTION_DONE -> repository.completeReminder(id)
                    ACTION_SNOOZE -> repository.snoozeReminder(id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.nparbs.growlog.action.FIRE"
        const val ACTION_DONE = "com.nparbs.growlog.action.DONE"
        const val ACTION_SNOOZE = "com.nparbs.growlog.action.SNOOZE"
        private const val EXTRA_ID = "reminder_id"

        fun pendingIntent(context: Context, action: String, reminderId: Long): PendingIntent {
            val intent = Intent(context, ReminderReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_ID, reminderId)
            return PendingIntent.getBroadcast(
                context,
                reminderId.toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repository = (context.applicationContext as GrowLogApp).container.repository
        // Reboots clear the notification shade, so overdue reminders need showing again.
        val renotify = intent.action == Intent.ACTION_BOOT_COMPLETED
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.rescheduleAll(renotifyOverdue = renotify)
            } finally {
                pending.finish()
            }
        }
    }
}
