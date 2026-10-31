package com.nparbs.growlog.data

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nparbs.growlog.reminders.ReminderNotifier
import com.nparbs.growlog.reminders.ReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class GrowRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val today = LocalDate.of(2026, 11, 2)
    private val nineAm = LocalDateTime.of(2026, 11, 2, 9, 0)

    private lateinit var db: GrowLogDatabase
    private lateinit var repository: GrowRepository

    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, GrowLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val notifier = ReminderNotifier(context).apply { createChannel() }
        repository = GrowRepository(db, PhotoStorage(context), ReminderScheduler(context), notifier)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun addPlant(name: String = "Tomato"): Long =
        repository.savePlant(Plant(name = name, startDate = today.minusDays(9)))

    @Test
    fun eachPlantKeepsItsOwnLogPerDay() = runBlocking {
        val a = addPlant("A")
        val b = addPlant("B")
        repository.saveDay(DailyLog(a, today, watered = true, waterMl = 500, ppm = 800, ph = 6.2), emptyList())
        repository.saveDay(DailyLog(b, today, ppm = 1200), emptyList())
        repository.saveDay(DailyLog(a, today.minusDays(1), ph = 5.8), emptyList())

        assertEquals(800, repository.getLog(a, today)?.ppm)
        assertEquals(6.2, repository.getLog(a, today)?.ph)
        assertEquals(1200, repository.getLog(b, today)?.ppm)
        assertEquals(listOf(today, today.minusDays(1)), repository.logs(a).first().map { it.date })
    }

    @Test
    fun savingADayReplacesItsPreviousValuesAndDoses() = runBlocking {
        val id = addPlant()
        repository.saveDay(
            DailyLog(id, today, ppm = 800),
            listOf(FertilizerDose(plantId = id, date = today, name = "Grow", amount = 5.0)),
        )
        repository.saveDay(
            DailyLog(id, today, ppm = 900),
            listOf(
                FertilizerDose(plantId = id, date = today, name = "Bloom", amount = 2.5),
                FertilizerDose(plantId = id, date = today, name = "CalMag", amount = 1.0),
            ),
        )
        assertEquals(900, repository.getLog(id, today)?.ppm)
        assertEquals(listOf("Bloom", "CalMag"), repository.getDoses(id, today).map { it.name })
        assertEquals(listOf("Bloom", "CalMag"), repository.fertilizerNames().first())
    }

    @Test
    fun clearingEverythingFromADayRemovesItsRow() = runBlocking {
        val id = addPlant()
        repository.saveDay(DailyLog(id, today, ppm = 800), emptyList())
        repository.saveDay(DailyLog(id, today), emptyList())
        assertNull(repository.getLog(id, today))
    }

    @Test
    fun quickWaterKeepsOtherReadings() = runBlocking {
        val id = addPlant()
        repository.saveDay(DailyLog(id, today, ph = 6.0), emptyList())
        repository.setWatered(id, today, true)
        assertEquals(true, repository.getLog(id, today)?.watered)
        assertEquals(6.0, repository.getLog(id, today)?.ph)
        repository.setWatered(id, today, false)
        assertEquals(false, repository.getLog(id, today)?.watered)
        assertEquals(6.0, repository.getLog(id, today)?.ph)
    }

    @Test
    fun photosAreCopiedAndDeletingAPlantRemovesFilesAndData() = runBlocking {
        val id = addPlant()
        val source = File(context.cacheDir, "source.jpg").apply { writeText("image") }
        assertEquals(0, repository.addPhotos(id, today, listOf(Uri.fromFile(source))))
        source.delete()

        val stored = repository.photos(id).first().single()
        assertEquals("image", File(stored.path).readText())
        assertEquals(stored.path, repository.plants().first().single().latestPhoto)

        repository.saveDay(DailyLog(id, today, ppm = 1), emptyList())
        repository.deletePlant(id)
        assertFalse(File(stored.path).exists())
        assertNull(repository.getLog(id, today))
    }

    @Test
    fun unreadablePhotoIsReportedAsFailed() = runBlocking {
        val id = addPlant()
        val missing = Uri.fromFile(File(context.cacheDir, "missing.jpg"))
        assertEquals(1, repository.addPhotos(id, today, listOf(missing)))
        assertTrue(repository.photos(id).first().isEmpty())
    }

    @Test
    fun savingAReminderSchedulesAnAlarm() = runBlocking {
        repository.saveReminder(Reminder(title = "Feed", nextDue = nineAm))
        assertNotNull(alarms.nextScheduledAlarm)
    }

    @Test
    fun firedReminderNotifiesOnceAndStaysDueUntilDone() = runBlocking {
        val id = repository.saveReminder(
            Reminder(title = "Feed", repeat = RepeatRule.EVERY_N_DAYS, intervalDays = 2, nextDue = nineAm),
        )
        repository.onReminderFired(id, nineAm)
        assertEquals(1, notifications.size())
        assertEquals(nineAm, repository.getReminder(id)?.nextDue)
        assertEquals(nineAm, repository.getReminder(id)?.lastNotifiedDue)

        // Reopening the app reschedules alarms but must not re-arm one already shown.
        alarms.scheduledAlarms.clear()
        repository.rescheduleAll()
        assertNull(alarms.nextScheduledAlarm)

        repository.rescheduleAll(renotifyOverdue = true)
        assertNotNull(alarms.nextScheduledAlarm)

        repository.completeReminder(id, nineAm.plusHours(1))
        assertEquals(nineAm.plusDays(2), repository.getReminder(id)?.nextDue)
        assertEquals(0, notifications.size())
    }

    @Test
    fun staleAlarmDoesNotNotify() = runBlocking {
        val id = repository.saveReminder(Reminder(title = "Feed", nextDue = nineAm.plusDays(1)))
        repository.onReminderFired(id, nineAm)
        assertEquals(0, notifications.size())
    }

    @Test
    fun completingOneOffReminderDisablesIt() = runBlocking {
        val id = repository.saveReminder(Reminder(title = "Flush", nextDue = nineAm))
        repository.completeReminder(id, nineAm)
        assertEquals(false, repository.getReminder(id)?.enabled)
    }

    @Test
    fun completingWaterReminderLogsWateringForItsPlant() = runBlocking {
        val a = addPlant("A")
        val b = addPlant("B")
        val id = repository.saveReminder(
            Reminder(plantId = a, title = "Water", type = ReminderType.WATER, nextDue = nineAm),
        )
        repository.completeReminder(id, nineAm)
        assertEquals(true, repository.getLog(a, today)?.watered)
        assertNull(repository.getLog(b, today))
    }

    @Test
    fun completingAllPlantsWaterReminderLogsOnlyActivePlants() = runBlocking {
        val active = addPlant("A")
        val archived = addPlant("B")
        repository.setArchived(archived, true)
        val id = repository.saveReminder(
            Reminder(title = "Water", type = ReminderType.WATER, nextDue = nineAm),
        )
        repository.completeReminder(id, nineAm)
        assertEquals(true, repository.getLog(active, today)?.watered)
        assertNull(repository.getLog(archived, today))
    }

    @Test
    fun snoozeDelaysWithoutChangingTheSchedule() = runBlocking {
        val id = repository.saveReminder(
            Reminder(title = "Feed", repeat = RepeatRule.EVERY_N_DAYS, intervalDays = 2, nextDue = nineAm),
        )
        repository.snoozeReminder(id, minutes = 60, now = nineAm)
        val reminder = repository.getReminder(id)!!
        assertEquals(nineAm.plusHours(1), reminder.dueAt)
        assertEquals(nineAm, reminder.nextDue)

        repository.completeReminder(id, nineAm.plusHours(1))
        assertEquals(nineAm.plusDays(2), repository.getReminder(id)?.dueAt)
    }

    @Test
    fun archivingPausesThePlantsReminders() = runBlocking {
        val id = addPlant()
        val reminderId = repository.saveReminder(Reminder(plantId = id, title = "Feed", nextDue = nineAm))
        repository.setArchived(id, true)
        assertEquals(false, repository.getReminder(reminderId)?.enabled)
        assertTrue(repository.plants().first().isEmpty())
        assertEquals(1, repository.plants(archived = true).first().size)
    }
}
