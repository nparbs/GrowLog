@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.nparbs.growlog.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nparbs.growlog.data.DailyLog
import com.nparbs.growlog.data.FertilizerDose
import com.nparbs.growlog.data.GrowRepository
import com.nparbs.growlog.data.Photo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

data class DoseForm(val name: String = "", val amount: String = "", val unit: String = "ml")

/** Everything editable on the form, as a value, so a change is a simple comparison. */
data class LogFormValues(
    val watered: Boolean = false,
    val waterMl: String = "",
    val ppm: String = "",
    val ph: String = "",
    val runoffPpm: String = "",
    val runoffPh: String = "",
    val temperature: String = "",
    val humidity: String = "",
    val height: String = "",
    val lightHours: String = "",
    val notes: String = "",
    val doses: List<DoseForm> = emptyList(),
) {
    val phError get() = rangeError(ph, 0.0, 14.0, "pH is 0 to 14")
    val runoffPhError get() = rangeError(runoffPh, 0.0, 14.0, "pH is 0 to 14")
    val ppmError get() = rangeError(ppm, 0.0, 100_000.0, "Enter a positive number")
    val runoffPpmError get() = rangeError(runoffPpm, 0.0, 100_000.0, "Enter a positive number")
    val waterError get() = rangeError(waterMl, 0.0, 1_000_000.0, "Enter a positive number")
    val temperatureError get() = rangeError(temperature, -50.0, 100.0, "Check this value")
    val humidityError get() = rangeError(humidity, 0.0, 100.0, "Humidity is 0 to 100")
    val heightError get() = rangeError(height, 0.0, 100_000.0, "Enter a positive number")
    val lightError get() = rangeError(lightHours, 0.0, 24.0, "Light is 0 to 24 hours")

    val isValid: Boolean
        get() = listOf(
            phError, runoffPhError, ppmError, runoffPpmError, waterError,
            temperatureError, humidityError, heightError, lightError,
        ).all { it == null } && doses.all { doseError(it) == null }

    fun doseError(dose: DoseForm): String? = when {
        dose.name.isBlank() && dose.amount.isNotBlank() -> "Name this fertilizer"
        dose.amount.isNotBlank() && dose.amount.toNumberOrNull()?.takeIf { it >= 0 } == null ->
            "Enter a positive amount"
        else -> null
    }

    fun toLog(plantId: Long, date: LocalDate): DailyLog {
        val amount = waterMl.toNumberOrNull()?.roundToInt()
        return DailyLog(
            plantId = plantId,
            date = date,
            // Entering an amount implies watering, even if the switch wasn't flipped.
            watered = watered || amount != null,
            waterMl = amount,
            ppm = ppm.toNumberOrNull()?.roundToInt(),
            ph = ph.toNumberOrNull(),
            runoffPpm = runoffPpm.toNumberOrNull()?.roundToInt(),
            runoffPh = runoffPh.toNumberOrNull(),
            temperatureC = temperature.toNumberOrNull(),
            humidityPct = humidity.toNumberOrNull()?.roundToInt(),
            heightCm = height.toNumberOrNull(),
            lightHours = lightHours.toNumberOrNull(),
            notes = notes.trim(),
        )
    }

    fun toDoses(plantId: Long, date: LocalDate): List<FertilizerDose> =
        doses.filter { it.name.isNotBlank() }.map {
            FertilizerDose(
                plantId = plantId,
                date = date,
                name = it.name.trim(),
                amount = it.amount.toNumberOrNull(),
                unit = it.unit.trim().ifBlank { "ml" },
            )
        }

    companion object {
        private fun rangeError(text: String, min: Double, max: Double, message: String): String? {
            if (text.isBlank()) return null
            val value = text.toNumberOrNull() ?: return message
            return if (value in min..max) null else message
        }

        fun from(log: DailyLog?, doses: List<FertilizerDose>) = LogFormValues(
            watered = log?.watered == true,
            waterMl = log?.waterMl?.toString().orEmpty(),
            ppm = log?.ppm?.toString().orEmpty(),
            ph = log?.ph?.let(::formatNumber).orEmpty(),
            runoffPpm = log?.runoffPpm?.toString().orEmpty(),
            runoffPh = log?.runoffPh?.let(::formatNumber).orEmpty(),
            temperature = log?.temperatureC?.let(::formatNumber).orEmpty(),
            humidity = log?.humidityPct?.toString().orEmpty(),
            height = log?.heightCm?.let(::formatNumber).orEmpty(),
            lightHours = log?.lightHours?.let(::formatNumber).orEmpty(),
            notes = log?.notes.orEmpty(),
            doses = doses.map {
                DoseForm(it.name, it.amount?.let(::formatNumber).orEmpty(), it.unit)
            },
        )
    }
}

class DailyLogViewModel(
    private val repository: GrowRepository,
    private val plantId: Long,
    private val date: LocalDate,
) : ViewModel() {
    var form by mutableStateOf(LogFormValues())
    var showErrors by mutableStateOf(false)
    private var saved by mutableStateOf(LogFormValues())
    private var saving = false

    val isDirty: Boolean get() = form != saved

    val plant = repository.plant(plantId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val photos = repository.photosOn(plantId, date)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val fertilizerNames = repository.fertilizerNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val loaded = LogFormValues.from(
                repository.getLog(plantId, date),
                repository.getDoses(plantId, date),
            )
            saved = loaded
            form = loaded
        }
    }

    fun updateDose(index: Int, dose: DoseForm) {
        form = form.copy(doses = form.doses.toMutableList().also { it[index] = dose })
    }

    fun addDose(name: String = "") {
        form = form.copy(doses = form.doses + DoseForm(name = name))
    }

    fun removeDose(index: Int) {
        form = form.copy(doses = form.doses.toMutableList().also { it.removeAt(index) })
    }

    fun save(onSaved: () -> Unit) {
        if (!form.isValid) {
            showErrors = true
            return
        }
        if (saving) return
        saving = true
        viewModelScope.launch {
            repository.saveDay(form.toLog(plantId, date), form.toDoses(plantId, date))
            saved = form
            onSaved()
        }
    }

    fun addPhotos(uris: List<Uri>, onFailed: (Int) -> Unit) {
        viewModelScope.launch {
            val failed = repository.addPhotos(plantId, date, uris)
            if (failed > 0) onFailed(failed)
        }
    }

    fun saveCaption(photo: Photo, caption: String) {
        viewModelScope.launch { repository.updatePhoto(photo.copy(caption = caption.trim())) }
    }

    fun deletePhoto(photo: Photo) {
        viewModelScope.launch { repository.deletePhoto(photo) }
    }
}

@Composable
fun DailyLogScreen(plantId: Long, date: LocalDate, onBack: () -> Unit) {
    val viewModel = appViewModel { DailyLogViewModel(it, plantId, date) }
    val plant by viewModel.plant.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val knownFertilizers by viewModel.fertilizerNames.collectAsStateWithLifecycle()
    val form = viewModel.form
    val context = LocalContext.current

    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var viewingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val leave = { if (viewModel.isDirty) confirmDiscard = true else onBack() }
    BackHandler(onBack = leave)

    fun errorIfShown(message: String?) = message.takeIf { viewModel.showErrors }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(formatDay(date), maxLines = 1)
                        Text(
                            plant?.name.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = { BackButton(leave) },
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
            SectionTitle("Water")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Watered", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = form.watered,
                    onCheckedChange = { viewModel.form = form.copy(watered = it) },
                )
            }
            NumberField(
                label = "Amount",
                value = form.waterMl,
                onValueChange = { viewModel.form = form.copy(waterMl = it) },
                suffix = "ml",
                decimal = false,
                error = errorIfShown(form.waterError),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Feed solution")
            FieldRow(
                first = {
                    NumberField(
                        label = "pH",
                        value = form.ph,
                        onValueChange = { viewModel.form = form.copy(ph = it) },
                        error = errorIfShown(form.phError),
                        modifier = it,
                    )
                },
                second = {
                    NumberField(
                        label = "PPM",
                        value = form.ppm,
                        onValueChange = { viewModel.form = form.copy(ppm = it) },
                        decimal = false,
                        error = errorIfShown(form.ppmError),
                        modifier = it,
                    )
                },
            )

            SectionTitle("Runoff")
            FieldRow(
                first = {
                    NumberField(
                        label = "Runoff pH",
                        value = form.runoffPh,
                        onValueChange = { viewModel.form = form.copy(runoffPh = it) },
                        error = errorIfShown(form.runoffPhError),
                        modifier = it,
                    )
                },
                second = {
                    NumberField(
                        label = "Runoff PPM",
                        value = form.runoffPpm,
                        onValueChange = { viewModel.form = form.copy(runoffPpm = it) },
                        decimal = false,
                        error = errorIfShown(form.runoffPpmError),
                        modifier = it,
                    )
                },
            )

            SectionTitle("Fertilizer")
            form.doses.forEachIndexed { index, dose ->
                DoseRow(
                    dose = dose,
                    error = errorIfShown(form.doseError(dose)),
                    onChange = { viewModel.updateDose(index, it) },
                    onRemove = { viewModel.removeDose(index) },
                )
            }
            val suggestions = knownFertilizers.filter { name -> form.doses.none { it.name == name } }
            if (suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { name ->
                        AssistChip(onClick = { viewModel.addDose(name) }, label = { Text(name) })
                    }
                }
            }
            OutlinedButton(onClick = { viewModel.addDose() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, Modifier.padding(end = 8.dp))
                Text("Add fertilizer")
            }

            SectionTitle("Environment and growth")
            FieldRow(
                first = {
                    NumberField(
                        label = "Temperature",
                        value = form.temperature,
                        onValueChange = { viewModel.form = form.copy(temperature = it) },
                        suffix = "°C",
                        error = errorIfShown(form.temperatureError),
                        modifier = it,
                    )
                },
                second = {
                    NumberField(
                        label = "Humidity",
                        value = form.humidity,
                        onValueChange = { viewModel.form = form.copy(humidity = it) },
                        suffix = "%",
                        decimal = false,
                        error = errorIfShown(form.humidityError),
                        modifier = it,
                    )
                },
            )
            FieldRow(
                first = {
                    NumberField(
                        label = "Height",
                        value = form.height,
                        onValueChange = { viewModel.form = form.copy(height = it) },
                        suffix = "cm",
                        error = errorIfShown(form.heightError),
                        modifier = it,
                    )
                },
                second = {
                    NumberField(
                        label = "Light",
                        value = form.lightHours,
                        onValueChange = { viewModel.form = form.copy(lightHours = it) },
                        suffix = "h",
                        error = errorIfShown(form.lightError),
                        modifier = it,
                    )
                },
            )

            SectionTitle("Notes")
            OutlinedTextField(
                value = form.notes,
                onValueChange = { viewModel.form = form.copy(notes = it) },
                label = { Text("Observations, training, problems") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Photos")
            if (photos.isNotEmpty()) {
                PhotoStrip(photos, onClick = { viewingId = it.id })
            }
            AddPhotoButtons(
                onPicked = { uris ->
                    viewModel.addPhotos(uris) { failed ->
                        Toast.makeText(context, "$failed photo(s) couldn't be added", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    photos.firstOrNull { it.id == viewingId }?.let { photo ->
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
    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            message = "Your edits to this day haven't been saved.",
            confirmLabel = "Discard",
            onConfirm = {
                confirmDiscard = false
                onBack()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun FieldRow(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        first(Modifier.weight(1f))
        second(Modifier.weight(1f))
    }
}

@Composable
private fun DoseRow(
    dose: DoseForm,
    error: String?,
    onChange: (DoseForm) -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = dose.name,
                onValueChange = { onChange(dose.copy(name = it)) },
                label = { Text("Fertilizer") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove fertilizer")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                label = "Amount",
                value = dose.amount,
                onValueChange = { onChange(dose.copy(amount = it)) },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = dose.unit,
                onValueChange = { onChange(dose.copy(unit = it)) },
                label = { Text("Unit") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
