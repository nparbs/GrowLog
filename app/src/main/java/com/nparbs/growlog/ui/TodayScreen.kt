@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalCoroutinesApi::class,
)

package com.nparbs.growlog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nparbs.growlog.data.DailyLog
import com.nparbs.growlog.data.FertilizerDose
import com.nparbs.growlog.data.GrowRepository
import com.nparbs.growlog.data.Plant
import com.nparbs.growlog.data.PlantWithPhoto
import com.nparbs.growlog.data.ReminderWithPlant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class TodayPlant(
    val plant: PlantWithPhoto,
    val log: DailyLog?,
    val doses: List<FertilizerDose>,
)

data class TodayState(
    val date: LocalDate = LocalDate.now(),
    val due: List<ReminderWithPlant> = emptyList(),
    val plants: List<TodayPlant> = emptyList(),
    val loaded: Boolean = false,
)

class TodayViewModel(private val repository: GrowRepository) : ViewModel() {
    private val clock: Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(TICK_MILLIS)
        }
    }

    private val day = clock.map { it.toLocalDate() }.distinctUntilChanged()

    private val plantsToday = day.flatMapLatest { date ->
        combine(
            repository.plants(),
            repository.logsOn(date),
            repository.dosesOn(date),
        ) { plants, logs, doses ->
            plants.map { item ->
                TodayPlant(
                    plant = item,
                    log = logs.firstOrNull { it.plantId == item.plant.id },
                    doses = doses.filter { it.plantId == item.plant.id },
                )
            }
        }
    }

    val state = combine(clock, repository.reminders(), plantsToday) { _, reminders, plants ->
        // Read the time here so that a data change is judged against now, not the last tick.
        val now = LocalDateTime.now()
        TodayState(
            date = now.toLocalDate(),
            due = reminders.filter { it.reminder.enabled && !it.reminder.dueAt.isAfter(now) },
            plants = plants,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    fun toggleWatered(plantId: Long, date: LocalDate, watered: Boolean) {
        viewModelScope.launch { repository.setWatered(plantId, date, watered) }
    }

    fun complete(reminderId: Long) {
        viewModelScope.launch { repository.completeReminder(reminderId) }
    }

    fun snooze(reminderId: Long) {
        viewModelScope.launch { repository.snoozeReminder(reminderId) }
    }

    private companion object {
        const val TICK_MILLIS = 30_000L
    }
}

@Composable
fun TodayScreen(
    onOpenLog: (Long, LocalDate) -> Unit,
    onAddPlant: () -> Unit,
) {
    val viewModel = appViewModel { TodayViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Today")
                        Text(
                            state.date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.loaded && state.plants.isEmpty() && state.due.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Yard,
                title = "No plants yet",
                message = "Add your first plant to start logging water, feed, pH and PPM.",
                modifier = Modifier.padding(padding),
                action = { Button(onClick = onAddPlant) { Text("Add a plant") } },
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.due.isNotEmpty()) {
                item { SectionTitle("Due now") }
                items(state.due, key = { "due-${it.reminder.id}" }) { item ->
                    DueReminderCard(
                        item = item,
                        today = state.date,
                        onDone = { viewModel.complete(item.reminder.id) },
                        onSnooze = { viewModel.snooze(item.reminder.id) },
                    )
                }
            }
            if (state.plants.isNotEmpty()) {
                item { SectionTitle("Plants") }
                items(state.plants, key = { "plant-${it.plant.plant.id}" }) { item ->
                    TodayPlantCard(
                        item = item,
                        date = state.date,
                        onOpen = { onOpenLog(item.plant.plant.id, state.date) },
                        onToggleWatered = {
                            viewModel.toggleWatered(
                                item.plant.plant.id,
                                state.date,
                                item.log?.watered != true,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DueReminderCard(
    item: ReminderWithPlant,
    today: LocalDate,
    onDone: () -> Unit,
    onSnooze: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Text(item.reminder.title, style = MaterialTheme.typography.titleSmall)
            Text(
                "${item.plantName ?: "All plants"} · ${formatDue(item.reminder.dueAt, today)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onSnooze) { Text("Snooze 1 hour") }
                TextButton(onClick = onDone) { Text("Done") }
            }
        }
    }
}

@Composable
private fun TodayPlantCard(
    item: TodayPlant,
    date: LocalDate,
    onOpen: () -> Unit,
    onToggleWatered: () -> Unit,
) {
    val plant = item.plant.plant
    val watered = item.log?.watered == true
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlantThumbnail(item.plant.latestPhoto)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    plant.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    plantSubtitle(plant, date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val summary = daySummary(item.log, item.doses)
                if (summary.isEmpty()) {
                    Text(
                        "Nothing logged yet",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    DaySummaryRow(summary)
                }
            }
            if (watered) {
                FilledTonalIconButton(
                    onClick = onToggleWatered,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Icon(Icons.Filled.WaterDrop, contentDescription = "Undo watered ${plant.name}")
                }
            } else {
                OutlinedIconButton(onClick = onToggleWatered) {
                    Icon(Icons.Outlined.WaterDrop, contentDescription = "Mark ${plant.name} watered")
                }
            }
        }
    }
}

fun plantSubtitle(plant: Plant, date: LocalDate): String =
    listOf(plant.stage.label, "Day ${growDay(plant.startDate, date)}", plant.location)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

enum class SummaryKind { WATER, FEED, CHEMISTRY, OTHER }

data class SummaryItem(val kind: SummaryKind, val text: String)

fun daySummary(log: DailyLog?, doses: List<FertilizerDose>): List<SummaryItem> = buildList {
    if (log != null) {
        if (log.watered) {
            add(SummaryItem(SummaryKind.WATER, log.waterMl?.let { "$it ml" } ?: "Watered"))
        }
        log.ph?.let { add(SummaryItem(SummaryKind.CHEMISTRY, "pH ${formatNumber(it)}")) }
        log.ppm?.let { add(SummaryItem(SummaryKind.CHEMISTRY, "$it ppm")) }
        log.runoffPh?.let { add(SummaryItem(SummaryKind.CHEMISTRY, "Runoff pH ${formatNumber(it)}")) }
        log.runoffPpm?.let { add(SummaryItem(SummaryKind.CHEMISTRY, "Runoff $it ppm")) }
    }
    doses.forEach { dose ->
        val amount = dose.amount?.let { " ${formatNumber(it)} ${dose.unit}" }.orEmpty()
        add(SummaryItem(SummaryKind.FEED, dose.name + amount))
    }
    if (log != null) {
        log.temperatureC?.let { add(SummaryItem(SummaryKind.OTHER, "${formatNumber(it)} °C")) }
        log.humidityPct?.let { add(SummaryItem(SummaryKind.OTHER, "$it% RH")) }
        log.heightCm?.let { add(SummaryItem(SummaryKind.OTHER, "${formatNumber(it)} cm")) }
        log.lightHours?.let { add(SummaryItem(SummaryKind.OTHER, "${formatNumber(it)} h light")) }
    }
}

@Composable
fun DaySummaryRow(items: List<SummaryItem>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item ->
            val icon = when (item.kind) {
                SummaryKind.WATER -> Icons.Filled.WaterDrop
                SummaryKind.FEED -> Icons.Filled.Spa
                SummaryKind.CHEMISTRY -> Icons.Filled.Science
                SummaryKind.OTHER -> null
            }
            StatPill(icon, item.text)
        }
    }
}
