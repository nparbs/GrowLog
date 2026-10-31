package com.nparbs.growlog.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface PlantDao {
    @Query(
        """
        SELECT p.*, (
            SELECT path FROM photos
            WHERE plantId = p.id
            ORDER BY date DESC, id DESC LIMIT 1
        ) AS latestPhoto
        FROM plants p
        WHERE p.archived = :archived
        ORDER BY p.name COLLATE NOCASE
        """,
    )
    fun observeAll(archived: Boolean): Flow<List<PlantWithPhoto>>

    @Query("SELECT * FROM plants WHERE id = :id")
    fun observe(id: Long): Flow<Plant?>

    @Query("SELECT * FROM plants WHERE id = :id")
    suspend fun get(id: Long): Plant?

    @Query("SELECT id FROM plants WHERE archived = 0")
    suspend fun activeIds(): List<Long>

    @Insert
    suspend fun insert(plant: Plant): Long

    @Update
    suspend fun update(plant: Plant)

    @Query("DELETE FROM plants WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM daily_logs WHERE plantId = :plantId ORDER BY date DESC")
    fun observeForPlant(plantId: Long): Flow<List<DailyLog>>

    @Query("SELECT * FROM daily_logs WHERE date = :date")
    fun observeForDate(date: LocalDate): Flow<List<DailyLog>>

    @Query("SELECT * FROM daily_logs WHERE plantId = :plantId AND date = :date")
    suspend fun get(plantId: Long, date: LocalDate): DailyLog?

    @Upsert
    suspend fun upsert(log: DailyLog)

    @Query("DELETE FROM daily_logs WHERE plantId = :plantId AND date = :date")
    suspend fun delete(plantId: Long, date: LocalDate)

    @Query("SELECT * FROM fertilizer_doses WHERE plantId = :plantId ORDER BY date DESC, id")
    fun observeDosesForPlant(plantId: Long): Flow<List<FertilizerDose>>

    @Query("SELECT * FROM fertilizer_doses WHERE date = :date ORDER BY id")
    fun observeDosesForDate(date: LocalDate): Flow<List<FertilizerDose>>

    @Query("SELECT * FROM fertilizer_doses WHERE plantId = :plantId AND date = :date ORDER BY id")
    suspend fun getDoses(plantId: Long, date: LocalDate): List<FertilizerDose>

    @Query("SELECT DISTINCT name FROM fertilizer_doses ORDER BY name COLLATE NOCASE")
    fun observeFertilizerNames(): Flow<List<String>>

    @Insert
    suspend fun insertDoses(doses: List<FertilizerDose>)

    @Query("DELETE FROM fertilizer_doses WHERE plantId = :plantId AND date = :date")
    suspend fun deleteDoses(plantId: Long, date: LocalDate)
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE plantId = :plantId ORDER BY date DESC, id DESC")
    fun observeForPlant(plantId: Long): Flow<List<Photo>>

    @Query("SELECT * FROM photos WHERE plantId = :plantId AND date = :date ORDER BY id")
    fun observeForDay(plantId: Long, date: LocalDate): Flow<List<Photo>>

    @Query("SELECT path FROM photos WHERE plantId = :plantId")
    suspend fun pathsForPlant(plantId: Long): List<String>

    @Insert
    suspend fun insert(photo: Photo): Long

    @Update
    suspend fun update(photo: Photo)

    @Delete
    suspend fun delete(photo: Photo)
}

@Dao
interface ReminderDao {
    @Query(
        """
        SELECT r.*, p.name AS plantName
        FROM reminders r LEFT JOIN plants p ON r.plantId = p.id
        ORDER BY r.enabled DESC, COALESCE(r.snoozedUntil, r.nextDue)
        """,
    )
    fun observeAll(): Flow<List<ReminderWithPlant>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun enabled(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE plantId = :plantId")
    suspend fun forPlant(plantId: Long): List<Reminder>

    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)
}
