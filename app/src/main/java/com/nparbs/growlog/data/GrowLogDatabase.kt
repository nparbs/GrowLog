package com.nparbs.growlog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class Converters {
    @TypeConverter
    fun dateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    // Truncated to the minute so stored values all share one format and sort correctly as text.
    @TypeConverter
    fun dateTimeToString(value: LocalDateTime?): String? =
        value?.truncatedTo(ChronoUnit.MINUTES)?.toString()

    @TypeConverter
    fun stringToDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)
}

@Database(
    entities = [Plant::class, DailyLog::class, FertilizerDose::class, Photo::class, Reminder::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class GrowLogDatabase : RoomDatabase() {
    abstract fun plants(): PlantDao
    abstract fun logs(): LogDao
    abstract fun photos(): PhotoDao
    abstract fun reminders(): ReminderDao

    companion object {
        fun create(context: Context): GrowLogDatabase =
            Room.databaseBuilder(context, GrowLogDatabase::class.java, "growlog.db").build()
    }
}
