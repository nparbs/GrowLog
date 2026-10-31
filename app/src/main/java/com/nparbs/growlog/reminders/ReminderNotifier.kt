package com.nparbs.growlog.reminders

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nparbs.growlog.MainActivity
import com.nparbs.growlog.R
import com.nparbs.growlog.data.Reminder

class ReminderNotifier(private val context: Context) {
    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Plant reminders",
            NotificationManager.IMPORTANCE_HIGH,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission") // areNotificationsEnabled covers the runtime permission.
    fun show(reminder: Reminder, plantName: String?) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val open = PendingIntent.getActivity(
            context,
            reminder.id.toInt(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val body = listOfNotNull(
            plantName ?: "All plants".takeIf { reminder.plantId == null },
            reminder.notes.takeIf { it.isNotBlank() },
        ).joinToString(" · ")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(reminder.title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(
                0,
                "Done",
                ReminderReceiver.pendingIntent(context, ReminderReceiver.ACTION_DONE, reminder.id),
            )
            .addAction(
                0,
                "Snooze 1 hour",
                ReminderReceiver.pendingIntent(context, ReminderReceiver.ACTION_SNOOZE, reminder.id),
            )
            .build()
        manager.notify(reminder.id.toInt(), notification)
    }

    fun cancel(reminderId: Long) {
        NotificationManagerCompat.from(context).cancel(reminderId.toInt())
    }

    private companion object {
        const val CHANNEL_ID = "reminders"
    }
}
