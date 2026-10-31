@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.nparbs.growlog.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nparbs.growlog.data.GrowRepository
import com.nparbs.growlog.data.Reminder
import com.nparbs.growlog.data.ReminderType
import com.nparbs.growlog.data.ReminderWithPlant
import com.nparbs.growlog.data.RepeatRule
import com.nparbs.growlog.reminders.ReminderRules
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

class RemindersViewModel(private val repository: GrowRepository) : ViewModel() {
    val reminders = repository.reminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setReminderEnabled(id, enabled) }
    }
}

@Composable
fun RemindersScreen(onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    val viewModel = appViewModel { RemindersViewModel(it) }
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Reminders") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add reminder")
            }
        },
    ) { padding ->
        val all = reminders ?: return@Scaffold
        if (all.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Notifications,
                title = "No reminders yet",
                message = "Tap + to schedule watering, feeding or anything else.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        val (scheduled, paused) = all.partition { it.reminder.enabled }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (scheduled.isNotEmpty()) {
                item { SectionTitle("Scheduled") }
                items(scheduled, key = { it.reminder.id }) { item ->
                    ReminderRow(
                        item = item,
                        showPlant = true,
                        onClick = { onEdit(item.reminder.id) },
                        onToggle = { viewModel.setEnabled(item.reminder.id, it) },
                    )
                }
            }
            if (paused.isNotEmpty()) {
                item { SectionTitle("Paused or finished", Modifier.padding(top = 8.dp)) }
                items(paused, key = { it.reminder.id }) { item ->
                    ReminderRow(
                        item = item,
                        showPlant = true,
                        onClick = { onEdit(item.reminder.id) },
                        onToggle = { viewModel.setEnabled(item.reminder.id, it) },
                    )
                }
            }
        }
    }
}

fun describeRepeat(reminder: Reminder): String = when (reminder.repeat) {
    RepeatRule.ONCE -> "Once"
    RepeatRule.EVERY_N_DAYS ->
        if (reminder.intervalDays == 1) "Every day" else "Every ${reminder.intervalDays} days"
    RepeatRule.WEEKLY -> DayOfWeek.entries
        .filter { reminder.weekdaysMask and ReminderRules.weekdayBit(it) != 0 }
        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

@Composable
fun ReminderRow(
    item: ReminderWithPlant,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    showPlant: Boolean = false,
) {
    val reminder = item.reminder
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    reminder.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showPlant) {
                    Text(
                        item.plantName ?: "All plants",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val schedule = if (reminder.enabled) {
                    "${formatDue(reminder.dueAt)} · ${describeRepeat(reminder)}"
                } else {
                    describeRepeat(reminder)
                }
                Text(
                    schedule,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = reminder.enabled, onCheckedChange = onToggle)
        }
    }
}

class ReminderEditViewModel(
    private val repository: GrowRepository,
    private val reminderId: Long,
    initialPlantId: Long?,
) : ViewModel() {
    var title by mutableStateOf("")
    var type by mutableStateOf(ReminderType.WATER)
    var plantId by mutableStateOf(initialPlantId)
    var notes by mutableStateOf("")
    var repeat by mutableStateOf(RepeatRule.EVERY_N_DAYS)
    var intervalDays by mutableStateOf("2")
    var weekdaysMask by mutableIntStateOf(ReminderRules.weekdayBit(LocalDate.now().dayOfWeek))
    var startDate by mutableStateOf(LocalDate.now())
    var time by mutableStateOf(LocalTime.of(9, 0))
    var error by mutableStateOf<String?>(null)
    private var saving = false

    val plants = repository.plants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (reminderId != 0L) {
            viewModelScope.launch {
                repository.getReminder(reminderId)?.let { reminder ->
                    title = reminder.title
                    type = reminder.type
                    plantId = reminder.plantId
                    notes = reminder.notes
                    repeat = reminder.repeat
                    intervalDays = reminder.intervalDays.toString()
                    weekdaysMask = reminder.weekdaysMask
                    startDate = reminder.nextDue.toLocalDate()
                    time = reminder.nextDue.toLocalTime()
                }
            }
        }
    }

    fun toggleWeekday(day: DayOfWeek) {
        weekdaysMask = weekdaysMask xor ReminderRules.weekdayBit(day)
    }

    fun save(onSaved: () -> Unit) {
        val now = LocalDateTime.now()
        val interval = intervalDays.toIntOrNull()
        val start = LocalDateTime.of(startDate, time)
        error = when {
            repeat == RepeatRule.EVERY_N_DAYS && (interval == null || interval < 1) ->
                "Enter how many days between reminders"
            repeat == RepeatRule.WEEKLY && weekdaysMask == 0 -> "Pick at least one day"
            repeat == RepeatRule.ONCE && start.isBefore(now) -> "Pick a time in the future"
            else -> null
        }
        if (error != null || saving) return
        saving = true

        val due = ReminderRules.firstDue(repeat, interval ?: 1, weekdaysMask, start, now)
        viewModelScope.launch {
            repository.saveReminder(
                Reminder(
                    id = reminderId,
                    plantId = plantId,
                    title = title.trim().ifBlank { type.label },
                    type = type,
                    notes = notes.trim(),
                    repeat = repeat,
                    intervalDays = interval ?: 1,
                    weekdaysMask = weekdaysMask,
                    nextDue = due,
                ),
            )
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteReminder(reminderId)
            onDeleted()
        }
    }
}

@Composable
fun ReminderEditScreen(reminderId: Long, initialPlantId: Long?, onBack: () -> Unit) {
    val viewModel = appViewModel { ReminderEditViewModel(it, reminderId, initialPlantId) }
    val plants by viewModel.plants.collectAsStateWithLifecycle()
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    RequestNotificationPermission()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (reminderId == 0L) "New reminder" else "Edit reminder") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (reminderId != 0L) {
                        TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
                    }
                    TextButton(onClick = { viewModel.save(onBack) }) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReminderType.entries.forEach { type ->
                    FilterChip(
                        selected = viewModel.type == type,
                        onClick = { viewModel.type = type },
                        label = { Text(type.label) },
                    )
                }
            }
            if (viewModel.type == ReminderType.WATER) {
                Text(
                    "Marking this done also logs the plant as watered that day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = viewModel.title,
                onValueChange = { viewModel.title = it },
                label = { Text("Title") },
                placeholder = { Text(viewModel.type.label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Plant")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = viewModel.plantId == null,
                    onClick = { viewModel.plantId = null },
                    label = { Text("All plants") },
                )
                plants.forEach { item ->
                    FilterChip(
                        selected = viewModel.plantId == item.plant.id,
                        onClick = { viewModel.plantId = item.plant.id },
                        label = { Text(item.plant.name) },
                    )
                }
            }

            SectionTitle("Schedule")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf(
                    RepeatRule.ONCE to "Once",
                    RepeatRule.EVERY_N_DAYS to "Every N days",
                    RepeatRule.WEEKLY to "Weekly",
                )
                options.forEachIndexed { index, (rule, label) ->
                    SegmentedButton(
                        selected = viewModel.repeat == rule,
                        onClick = {
                            viewModel.repeat = rule
                            viewModel.error = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) { Text(label, maxLines = 1) }
                }
            }
            when (viewModel.repeat) {
                RepeatRule.EVERY_N_DAYS -> NumberField(
                    label = "Repeat every",
                    value = viewModel.intervalDays,
                    onValueChange = { viewModel.intervalDays = it },
                    suffix = "days",
                    decimal = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                RepeatRule.WEEKLY -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = viewModel.weekdaysMask and ReminderRules.weekdayBit(day) != 0,
                            onClick = { viewModel.toggleWeekday(day) },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        )
                    }
                }
                RepeatRule.ONCE -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, Modifier.padding(end = 8.dp))
                    Text(formatDay(viewModel.startDate), maxLines = 1)
                }
                OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, Modifier.padding(end = 8.dp))
                    Text(formatTime(viewModel.time), maxLines = 1)
                }
            }
            viewModel.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(
                value = viewModel.notes,
                onValueChange = { viewModel.notes = it },
                label = { Text("Notes") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (pickDate) {
        DatePickerSheet(
            initial = viewModel.startDate,
            earliest = LocalDate.now(),
            onDismiss = { pickDate = false },
            onPicked = {
                viewModel.startDate = it
                pickDate = false
            },
        )
    }
    if (pickTime) {
        TimePickerSheet(
            initial = viewModel.time,
            onDismiss = { pickTime = false },
            onPicked = {
                viewModel.time = it
                pickTime = false
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete reminder?",
            message = "This schedule will stop. Logged history isn't affected.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Android 13+ blocks notifications until asked, and this screen is where they start to matter. */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        if (granted != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
