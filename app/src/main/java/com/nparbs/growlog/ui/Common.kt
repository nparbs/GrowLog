@file:OptIn(ExperimentalMaterial3Api::class)

package com.nparbs.growlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nparbs.growlog.GrowLogApp
import com.nparbs.growlog.data.GrowRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val MILLIS_PER_DAY = 86_400_000L

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: (GrowRepository) -> VM,
): VM {
    val app = LocalContext.current.applicationContext as GrowLogApp
    return viewModel(
        factory = viewModelFactory {
            initializer { create(app.container.repository) }
        },
    )
}

// Formatting

fun String.toNumberOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

fun formatDay(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    today.plusDays(1) -> "Tomorrow"
    else -> date.format(
        DateTimeFormatter.ofPattern(if (date.year == today.year) "EEE, MMM d" else "MMM d, yyyy"),
    )
}

fun formatTime(time: LocalTime): String = time.format(DateTimeFormatter.ofPattern("h:mm a"))

fun formatDue(due: LocalDateTime, today: LocalDate = LocalDate.now()): String =
    "${formatDay(due.toLocalDate(), today)} · ${formatTime(due.toLocalTime())}"

/** Grow day number, with the start date as day 1. */
fun growDay(start: LocalDate, date: LocalDate): Long = ChronoUnit.DAYS.between(start, date) + 1

// Shared components

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
}

@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    decimal: Boolean = true,
    error: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

/** Small icon-and-value label used to summarize a day's readings. */
@Composable
fun StatPill(icon: ImageVector?, text: String) {
    Row(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            action?.invoke()
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DatePickerSheet(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit,
    latest: LocalDate? = null,
    earliest: LocalDate? = null,
) {
    // The picker works in UTC midnights, so epoch-day arithmetic avoids time zone shifts.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toEpochDay() * MILLIS_PER_DAY,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = LocalDate.ofEpochDay(utcTimeMillis / MILLIS_PER_DAY)
                return (latest == null || !date.isAfter(latest)) &&
                    (earliest == null || !date.isBefore(earliest))
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPicked(LocalDate.ofEpochDay(it / MILLIS_PER_DAY)) }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun TimePickerSheet(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onPicked: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(initial.hour, initial.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}
