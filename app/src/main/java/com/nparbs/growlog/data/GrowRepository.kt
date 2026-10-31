package com.nparbs.growlog.data

import android.net.Uri
import androidx.room.withTransaction
import com.nparbs.growlog.reminders.ReminderNotifier
import com.nparbs.growlog.reminders.ReminderRules
import com.nparbs.growlog.reminders.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

class GrowRepository(
    private val db: GrowLogDatabase,
    private val photoStorage: PhotoStorage,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
) {
    // Plants

    fun plants(archived: Boolean = false): Flow<List<PlantWithPhoto>> =
        db.plants().observeAll(archived)

    fun plant(id: Long): Flow<Plant?> = db.plants().observe(id)

    suspend fun getPlant(id: Long): Plant? = db.plants().get(id)

    suspend fun savePlant(plant: Plant): Long =
        if (plant.id == 0L) {
            db.plants().insert(plant)
        } else {
            db.plants().update(plant)
            plant.id
        }

    /** Archiving pauses the plant's reminders; restoring leaves them paused to re-enable by hand. */
    suspend fun setArchived(plantId: Long, archived: Boolean) {
        val plant = db.plants().get(plantId) ?: return
        db.plants().update(plant.copy(archived = archived))
        if (archived) {
            db.reminders().forPlant(plantId).forEach { setReminderEnabled(it.id, false) }
        }
    }

    suspend fun deletePlant(plantId: Long) {
        val paths = db.photos().pathsForPlant(plantId)
        db.reminders().forPlant(plantId).forEach {
            scheduler.cancel(it.id)
            notifier.cancel(it.id)
        }
        db.plants().delete(plantId)
        photoStorage.delete(paths)
    }

    // Daily logs

    fun logs(plantId: Long): Flow<List<DailyLog>> = db.logs().observeForPlant(plantId)

    fun logsOn(date: LocalDate): Flow<List<DailyLog>> = db.logs().observeForDate(date)

    fun doses(plantId: Long): Flow<List<FertilizerDose>> = db.logs().observeDosesForPlant(plantId)

    fun dosesOn(date: LocalDate): Flow<List<FertilizerDose>> = db.logs().observeDosesForDate(date)

    fun fertilizerNames(): Flow<List<String>> = db.logs().observeFertilizerNames()

    suspend fun getLog(plantId: Long, date: LocalDate): DailyLog? = db.logs().get(plantId, date)

    suspend fun getDoses(plantId: Long, date: LocalDate): List<FertilizerDose> =
        db.logs().getDoses(plantId, date)

    suspend fun saveDay(log: DailyLog, doses: List<FertilizerDose>) {
        db.withTransaction {
            if (log.isEmpty) {
                db.logs().delete(log.plantId, log.date)
            } else {
                db.logs().upsert(log)
            }
            db.logs().deleteDoses(log.plantId, log.date)
            db.logs().insertDoses(doses)
        }
    }

    /** Flips the watered flag for a day, keeping every other recorded value. */
    suspend fun setWatered(plantId: Long, date: LocalDate, watered: Boolean) {
        val existing = db.logs().get(plantId, date) ?: DailyLog(plantId, date)
        val updated = existing.copy(watered = watered, waterMl = if (watered) existing.waterMl else null)
        if (updated.isEmpty) db.logs().delete(plantId, date) else db.logs().upsert(updated)
    }

    // Photos

    fun photos(plantId: Long): Flow<List<Photo>> = db.photos().observeForPlant(plantId)

    fun photosOn(plantId: Long, date: LocalDate): Flow<List<Photo>> =
        db.photos().observeForDay(plantId, date)

    /** Returns how many of the photos could not be imported. */
    suspend fun addPhotos(plantId: Long, date: LocalDate, uris: List<Uri>): Int {
        var failed = 0
        uris.forEach { uri ->
            val path = runCatching { photoStorage.import(uri) }.getOrNull()
            if (path == null) {
                failed++
            } else {
                db.photos().insert(Photo(plantId = plantId, date = date, path = path))
            }
        }
        return failed
    }

    suspend fun updatePhoto(photo: Photo) = db.photos().update(photo)

    suspend fun deletePhoto(photo: Photo) {
        db.photos().delete(photo)
        photoStorage.delete(listOf(photo.path))
    }

    // Reminders

    fun reminders(): Flow<List<ReminderWithPlant>> = db.reminders().observeAll()

    suspend fun getReminder(id: Long): Reminder? = db.reminders().get(id)

    suspend fun saveReminder(reminder: Reminder): Long {
        val id = if (reminder.id == 0L) {
            db.reminders().insert(reminder)
        } else {
            db.reminders().update(reminder)
            reminder.id
        }
        notifier.cancel(id)
        scheduler.schedule(reminder.copy(id = id))
        return id
    }

    suspend fun deleteReminder(id: Long) {
        scheduler.cancel(id)
        notifier.cancel(id)
        db.reminders().delete(id)
    }

    suspend fun setReminderEnabled(
        id: Long,
        enabled: Boolean,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val reminder = db.reminders().get(id) ?: return
        val updated = if (enabled) {
            reminder.copy(
                enabled = true,
                snoozedUntil = null,
                lastNotifiedDue = null,
                nextDue = ReminderRules.rollForward(reminder, now),
            )
        } else {
            reminder.copy(enabled = false, snoozedUntil = null)
        }
        db.reminders().update(updated)
        notifier.cancel(id)
        scheduler.schedule(updated)
    }

    /** Completes the current occurrence. Finishing a watering reminder also logs the watering. */
    suspend fun completeReminder(id: Long, now: LocalDateTime = LocalDateTime.now()) {
        val reminder = db.reminders().get(id) ?: return
        if (reminder.type == ReminderType.WATER) {
            val plantIds = reminder.plantId?.let(::listOf) ?: db.plants().activeIds()
            plantIds.forEach { setWatered(it, now.toLocalDate(), true) }
        }
        val next = ReminderRules.nextDue(reminder, now)
        val updated = if (next == null) {
            reminder.copy(enabled = false, snoozedUntil = null)
        } else {
            reminder.copy(nextDue = next, snoozedUntil = null, lastNotifiedDue = null)
        }
        db.reminders().update(updated)
        notifier.cancel(id)
        scheduler.schedule(updated)
    }

    suspend fun snoozeReminder(
        id: Long,
        minutes: Long = 60,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val reminder = db.reminders().get(id) ?: return
        val updated = reminder.copy(snoozedUntil = now.plusMinutes(minutes), lastNotifiedDue = null)
        db.reminders().update(updated)
        notifier.cancel(id)
        scheduler.schedule(updated)
    }

    /** Called when an alarm fires. Stale alarms for edited or paused reminders are ignored. */
    suspend fun onReminderFired(id: Long, now: LocalDateTime = LocalDateTime.now()) {
        val reminder = db.reminders().get(id) ?: return
        if (!reminder.enabled || reminder.dueAt.isAfter(now)) return
        val plantName = reminder.plantId?.let { db.plants().get(it)?.name }
        notifier.show(reminder, plantName)
        db.reminders().update(reminder.copy(lastNotifiedDue = reminder.dueAt))
    }

    /** Re-registers alarms, which Android drops on reboot, update and clock changes. */
    suspend fun rescheduleAll(renotifyOverdue: Boolean = false) {
        db.reminders().enabled().forEach { scheduler.schedule(it, force = renotifyOverdue) }
    }
}
