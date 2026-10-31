@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.nparbs.growlog.ui

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
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material3.Button
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nparbs.growlog.data.GrowRepository
import com.nparbs.growlog.data.GrowStage
import com.nparbs.growlog.data.Plant
import com.nparbs.growlog.data.PlantWithPhoto
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class PlantsViewModel(repository: GrowRepository) : ViewModel() {
    val plants = combine(repository.plants(false), repository.plants(true)) { active, archived ->
        active to archived
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun PlantsScreen(onOpenPlant: (Long) -> Unit, onAddPlant: () -> Unit) {
    val viewModel = appViewModel { PlantsViewModel(it) }
    val data by viewModel.plants.collectAsStateWithLifecycle()
    var showArchived by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Plants") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPlant) {
                Icon(Icons.Filled.Add, contentDescription = "Add plant")
            }
        },
    ) { padding ->
        val (active, archived) = data ?: return@Scaffold
        val visible = if (showArchived) archived else active
        Column(Modifier.padding(padding)) {
            if (archived.isNotEmpty()) {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !showArchived,
                        onClick = { showArchived = false },
                        label = { Text("Active (${active.size})") },
                    )
                    FilterChip(
                        selected = showArchived,
                        onClick = { showArchived = true },
                        label = { Text("Archived (${archived.size})") },
                    )
                }
            }
            if (visible.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Yard,
                    title = if (showArchived) "Nothing archived" else "No plants yet",
                    message = if (showArchived) {
                        "Archived plants keep their history and appear here."
                    } else {
                        "Tap + to add your first plant."
                    },
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(visible, key = { it.plant.id }) { item ->
                        PlantRow(item, onClick = { onOpenPlant(item.plant.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PlantRow(item: PlantWithPhoto, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlantThumbnail(item.latestPhoto)
            Column(Modifier.weight(1f)) {
                Text(
                    item.plant.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.plant.variety.isNotBlank()) {
                    Text(
                        item.plant.variety,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    plantSubtitle(item.plant, LocalDate.now()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

class PlantEditViewModel(
    private val repository: GrowRepository,
    private val plantId: Long,
) : ViewModel() {
    var name by mutableStateOf("")
    var variety by mutableStateOf("")
    var stage by mutableStateOf(GrowStage.SEEDLING)
    var location by mutableStateOf("")
    var medium by mutableStateOf("")
    var startDate by mutableStateOf(LocalDate.now())
    var notes by mutableStateOf("")
    var nameError by mutableStateOf(false)
    private var existing: Plant? = null
    private var saving = false

    init {
        if (plantId != 0L) {
            viewModelScope.launch {
                repository.getPlant(plantId)?.let { plant ->
                    existing = plant
                    name = plant.name
                    variety = plant.variety
                    stage = plant.stage
                    location = plant.location
                    medium = plant.medium
                    startDate = plant.startDate
                    notes = plant.notes
                }
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        if (name.isBlank()) {
            nameError = true
            return
        }
        if (saving) return
        saving = true
        viewModelScope.launch {
            val base = existing ?: Plant(name = name, startDate = startDate)
            repository.savePlant(
                base.copy(
                    name = name.trim(),
                    variety = variety.trim(),
                    stage = stage,
                    location = location.trim(),
                    medium = medium.trim(),
                    startDate = startDate,
                    notes = notes.trim(),
                ),
            )
            onSaved()
        }
    }
}

@Composable
fun PlantEditScreen(plantId: Long, onBack: () -> Unit) {
    val viewModel = appViewModel { PlantEditViewModel(it, plantId) }
    var pickDate by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (plantId == 0L) "New plant" else "Edit plant") },
                navigationIcon = { BackButton(onBack) },
                actions = {
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
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = {
                    viewModel.name = it
                    viewModel.nameError = false
                },
                label = { Text("Name") },
                isError = viewModel.nameError,
                supportingText = if (viewModel.nameError) {
                    { Text("Give your plant a name") }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.variety,
                onValueChange = { viewModel.variety = it },
                label = { Text("Variety / strain") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            SectionTitle("Stage")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GrowStage.entries.forEach { stage ->
                    FilterChip(
                        selected = viewModel.stage == stage,
                        onClick = { viewModel.stage = stage },
                        label = { Text(stage.label) },
                    )
                }
            }
            OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, Modifier.padding(end = 8.dp))
                Text("Started: ${formatDay(viewModel.startDate)}")
            }
            OutlinedTextField(
                value = viewModel.location,
                onValueChange = { viewModel.location = it },
                label = { Text("Location (tent, room, shelf)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.medium,
                onValueChange = { viewModel.medium = it },
                label = { Text("Medium (soil, coco, hydro)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.notes,
                onValueChange = { viewModel.notes = it },
                label = { Text("Notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            if (plantId == 0L) {
                Button(onClick = { viewModel.save(onBack) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add plant")
                }
            }
        }
    }

    if (pickDate) {
        DatePickerSheet(
            initial = viewModel.startDate,
            latest = LocalDate.now(),
            onDismiss = { pickDate = false },
            onPicked = {
                viewModel.startDate = it
                pickDate = false
            },
        )
    }
}
