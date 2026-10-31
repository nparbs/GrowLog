package com.nparbs.growlog.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

enum class GrowStage(val label: String) {
    GERMINATION("Germination"),
    SEEDLING("Seedling"),
    VEGETATIVE("Vegetative"),
    FLOWERING("Flowering"),
    HARVESTED("Harvested"),
    OTHER("Other"),
}

@Entity(tableName = "plants")
data class Plant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val variety: String = "",
    val stage: GrowStage = GrowStage.SEEDLING,
    val location: String = "",
    val medium: String = "",
    val startDate: LocalDate,
    val notes: String = "",
    val archived: Boolean = false,
)

data class PlantWithPhoto(
    @Embedded val plant: Plant,
    val latestPhoto: String?,
)

/** One row per plant per day. Measurements are stored metric; null means "not recorded". */
@Entity(
    tableName = "daily_logs",
    primaryKeys = ["plantId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = Plant::class,
            parentColumns = ["id"],
            childColumns = ["plantId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("date")],
)
data class DailyLog(
    val plantId: Long,
    val date: LocalDate,
    val watered: Boolean = false,
    val waterMl: Int? = null,
    val ppm: Int? = null,
    val ph: Double? = null,
    val runoffPpm: Int? = null,
    val runoffPh: Double? = null,
    val temperatureC: Double? = null,
    val humidityPct: Int? = null,
    val heightCm: Double? = null,
    val lightHours: Double? = null,
    val notes: String = "",
) {
    val isEmpty: Boolean
        get() = !watered && waterMl == null && ppm == null && ph == null &&
            runoffPpm == null && runoffPh == null && temperatureC == null &&
            humidityPct == null && heightCm == null && lightHours == null && notes.isBlank()
}

@Entity(
    tableName = "fertilizer_doses",
    foreignKeys = [
        ForeignKey(
            entity = Plant::class,
            parentColumns = ["id"],
            childColumns = ["plantId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("plantId", "date")],
)
data class FertilizerDose(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantId: Long,
    val date: LocalDate,
    val name: String,
    val amount: Double? = null,
    val unit: String = "ml",
)

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = Plant::class,
            parentColumns = ["id"],
            childColumns = ["plantId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("plantId", "date")],
)
data class Photo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantId: Long,
    val date: LocalDate,
    val path: String,
    val caption: String = "",
)

enum class ReminderType(val label: String) {
    WATER("Water"),
    FERTILIZE("Fertilize"),
    CHECK_PH_PPM("Check pH / PPM"),
    PHOTO("Take photo"),
    CUSTOM("Custom"),
}

enum class RepeatRule { ONCE, EVERY_N_DAYS, WEEKLY }

/**
 * A reminder stays due until it is completed or snoozed, so a missed one can't silently
 * roll forward. [plantId] null means it applies to all active plants.
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = Plant::class,
            parentColumns = ["id"],
            childColumns = ["plantId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("plantId")],
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantId: Long? = null,
    val title: String,
    val type: ReminderType = ReminderType.CUSTOM,
    val notes: String = "",
    val repeat: RepeatRule = RepeatRule.ONCE,
    val intervalDays: Int = 1,
    /** Bit 0 is Monday, bit 6 is Sunday. */
    val weekdaysMask: Int = 0,
    val nextDue: LocalDateTime,
    val snoozedUntil: LocalDateTime? = null,
    /** The [dueAt] value last notified, so app restarts don't repeat a notification. */
    val lastNotifiedDue: LocalDateTime? = null,
    val enabled: Boolean = true,
) {
    val dueAt: LocalDateTime get() = snoozedUntil ?: nextDue
}

data class ReminderWithPlant(
    @Embedded val reminder: Reminder,
    @ColumnInfo(name = "plantName") val plantName: String?,
)
