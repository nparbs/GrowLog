@file:OptIn(ExperimentalMaterial3Api::class)

package com.nparbs.growlog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.nparbs.growlog.data.Photo
import com.nparbs.growlog.data.Plant
import com.nparbs.growlog.data.ReminderWithPlant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate

data class DayEntry(
    val date: LocalDate,
    val log: DailyLog?,
    val doses: List<FertilizerDose>,
    val photos: List<Photo>,
)

/** Merges the three per-day tables; a day can have photos or feed without a log row. */
fun buildHistory(
    logs: List<DailyLog>,
    doses: List<FertilizerDose>,
    photos: List<Photo>,
): List<DayEntry> {
    val logsByDate = logs.associateBy { it.date }
    val dosesByDate = doses.groupBy { it.date }
    val photosByDate = photos.groupBy { it.date }
    return (logsByDate.keys + dosesByDate.keys + photosByDate.keys)
        .sortedDescending()
        .map { date ->
            DayEntry(
                date = date,
                log = logsByDate[date],
                doses = dosesByDate[date].orEmpty(),
                photos = photosByDate[date].orEmpty(),
            )
        }
}

class PlantDetailViewModel(
    private val repository: GrowRepository,
    private val plantId: Long,
) : ViewModel() {
    val plant = repository.plant(plantId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val photos = repository.photos(plantId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history = combine(
        repository.logs(plantId),
        repository.doses(plantId),
        repository.photos(plantId),
        ::buildHistory,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reminders = repository.reminders()
        .map { all -> all.filter { it.reminder.plantId == plantId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addPhotos(uris: List<Uri>, onFailed: (Int) -> Unit) {
        viewModelScope.launch {
            val failed = repository.addPhotos(plantId, LocalDate.now(), uris)
            if (failed > 0) onFailed(failed)
        }
    }

    fun saveCaption(photo: Photo, caption: String) {
        viewModelScope.launch { repository.updatePhoto(photo.copy(caption = caption.trim())) }
    }

    fun deletePhoto(photo: Photo) {
        viewModelScope.launch { repository.deletePhoto(photo) }
    }

    fun setArchived(archived: Boolean) {
        viewModelScope.launch { repository.setArchived(plantId, archived) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deletePlant(plantId)
            onDeleted()
        }
    }

    fun setReminderEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setReminderEnabled(id, enabled) }
    }
}

@Composable
fun PlantDetailScreen(
    plantId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenLog: (LocalDate) -> Unit,
    onAddReminder: () -> Unit,
    onEditReminder: (Long) -> Unit,
) {
    val viewModel = appViewModel { PlantDetailViewModel(it, plantId) }
    val plant by viewModel.plant.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(plant?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = { pickDate = true }) {
                        Icon(Icons.Filled.EditCalendar, contentDescription = "Log another date")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit plant")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        val archived = plant?.archived == true
                        DropdownMenuItem(
                            text = { Text(if (archived) "Restore plant" else "Archive plant") },
                            onClick = {
                                menuOpen = false
                                viewModel.setArchived(!archived)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete plant") },
                            onClick = {
                                menuOpen = false
                                confirmDelete = true
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { onOpenLog(LocalDate.now()) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Log today") },
                )
            }
        },
    ) { padding ->
        val current = plant ?: return@Scaffold
        Column(Modifier.padding(padding).fillMaxSize()) {
            PlantHeader(current)
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("History") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Photos") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Reminders") })
            }
            when (tab) {
                0 -> HistoryTab(current, history, onOpenLog)
                1 -> PhotosTab(photos, viewModel)
                else -> PlantRemindersTab(reminders, viewModel, onAddReminder, onEditReminder)
            }
        }
    }

    if (pickDate) {
        DatePickerSheet(
            initial = LocalDate.now(),
            latest = LocalDate.now(),
            onDismiss = { pickDate = false },
            onPicked = {
                pickDate = false
                onOpenLog(it)
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete ${plant?.name}?",
            message = "This permanently deletes the plant with all its logs, photos and reminders. " +
                "Archive it instead to keep its history.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun PlantHeader(plant: Plant) {
    val details = listOf(plant.variety, plant.medium).filter { it.isNotBlank() }.joinToString(" · ")
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        Text(
            plantSubtitle(plant, LocalDate.now()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (details.isNotEmpty()) {
            Text(details, style = MaterialTheme.typography.bodyMedium)
        }
        if (plant.notes.isNotBlank()) {
            Text(
                plant.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HistoryTab(
    plant: Plant,
    history: List<DayEntry>,
    onOpenLog: (LocalDate) -> Unit,
) {
    if (history.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.EventNote,
            title = "No entries yet",
            message = "Tap Log today to record water, feed, pH and PPM.",
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(history, key = { it.date.toString() }) { entry ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenLog(entry.date) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatDay(entry.date),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "Day ${growDay(plant.startDate, entry.date)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val summary = daySummary(entry.log, entry.doses)
                    if (summary.isNotEmpty()) DaySummaryRow(summary)
                    entry.log?.notes?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    if (entry.photos.isNotEmpty()) {
                        PhotoStrip(entry.photos, onClick = { onOpenLog(entry.date) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotosTab(photos: List<Photo>, viewModel: PlantDetailViewModel) {
    val context = LocalContext.current
    var viewingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val viewing = photos.firstOrNull { it.id == viewingId }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            AddPhotoButtons(
                onPicked = { uris ->
                    viewModel.addPhotos(uris) { failed ->
                        Toast.makeText(context, "$failed photo(s) couldn't be added", Toast.LENGTH_LONG).show()
                    }
                },
            )
        }
        if (photos.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                        Text(
                            "Photos you add appear here, newest first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        items(photos, key = { it.id }) { photo ->
            PhotoGridItem(photo, onClick = { viewingId = photo.id })
        }
    }

    viewing?.let { photo ->
        PhotoViewer(
            photo = photo,
            onDismiss = { viewingId = null },
            onSaveCaption = { viewModel.saveCaption(photo, it) },
            onDelete = {
                viewingId = null
                viewModel.deletePhoto(photo)
            },
        )
    }
}

@Composable
private fun PlantRemindersTab(
    reminders: List<ReminderWithPlant>,
    viewModel: PlantDetailViewModel,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedButton(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, Modifier.padding(end = 8.dp))
            Text("Add reminder")
        }
        if (reminders.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Notifications,
                title = "No reminders",
                message = "Set up watering, feeding or check-in schedules for this plant.",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(reminders, key = { it.reminder.id }) { item ->
                    ReminderRow(
                        item = item,
                        onClick = { onEdit(item.reminder.id) },
                        onToggle = { viewModel.setReminderEnabled(item.reminder.id, it) },
                    )
                }
            }
        }
    }
}
