package com.nparbs.growlog

import android.app.Application
import android.content.Context
import com.nparbs.growlog.data.GrowLogDatabase
import com.nparbs.growlog.data.GrowRepository
import com.nparbs.growlog.data.PhotoStorage
import com.nparbs.growlog.reminders.ReminderNotifier
import com.nparbs.growlog.reminders.ReminderScheduler

class AppContainer(context: Context) {
    val notifier = ReminderNotifier(context)
    val repository = GrowRepository(
        db = GrowLogDatabase.create(context),
        photoStorage = PhotoStorage(context),
        scheduler = ReminderScheduler(context),
        notifier = notifier,
    )
}

class GrowLogApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.notifier.createChannel()
    }
}
