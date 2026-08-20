package com.omi.kickcounter.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.omi.kickcounter.data.SettingsStore
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.omi.kickcounter.domain.Formatting
import com.omi.kickcounter.domain.KickSnapshot
import com.omi.kickcounter.domain.Pregnancy
import com.omi.kickcounter.ui.KickViewModel
import com.omi.kickcounter.ui.components.GlassCard
import com.omi.kickcounter.ui.components.KeyValueRow
import com.omi.kickcounter.ui.components.SectionLabel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val longDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

private enum class WindowEdge { Start, End }

@Composable
fun SettingsScreen(
    snapshot: KickSnapshot,
    viewModel: KickViewModel,
    onShare: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }
    var editingEdge by remember { mutableStateOf<WindowEdge?>(null) }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 24.dp),
    ) {
        BabyNameCard(snapshot = snapshot, viewModel = viewModel)

        Spacer(Modifier.height(16.dp))

        ReminderCard(
            snapshot = snapshot,
            viewModel = viewModel,
            onEditEdge = { editingEdge = it },
            context = context,
        )

        Spacer(Modifier.height(16.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                SectionLabel("Pregnancy dates")
                Spacer(Modifier.height(14.dp))
                KeyValueRow("Last menstrual period", snapshot.settings.lmp.format(longDate))
                Spacer(Modifier.height(8.dp))
                KeyValueRow("Estimated due date", snapshot.dueDate.format(longDate))
                Spacer(Modifier.height(8.dp))
                KeyValueRow("Today", snapshot.age.format())
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Text("Change LMP date")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "The due date is derived as LMP plus ${Pregnancy.TERM_DAYS} days. " +
                        "If the clinic gave a scan-adjusted due date, set the LMP to that date " +
                        "minus ${Pregnancy.TERM_DAYS} days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                SectionLabel("Counting")
                Spacer(Modifier.height(14.dp))
                Text("Movements per session", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                ChipRow(
                    options = listOf(6, 10, 12),
                    selected = snapshot.settings.dailyGoal,
                    label = { "$it" },
                    onSelect = viewModel::setDailyGoal,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Standard guidance is ten movements within two hours of focused " +
                        "counting. Count kicks, rolls, jabs and swishes; hiccups do not count, " +
                        "because they are involuntary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                Text("Group repeated taps", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                ChipRow(
                    options = listOf(0, 1, 2, 5),
                    selected = snapshot.settings.groupingMinutes,
                    label = { if (it == 0) "Off" else "$it min" },
                    onSelect = viewModel::setGroupingMinutes,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Off matches the guidance. With grouping on, taps within the window " +
                        "count as one movement. Every tap is still stored, so this can be " +
                        "changed at any time without losing data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                SectionLabel("Data")
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Everything stays on this phone. The app has no internet permission, " +
                        "so the log cannot leave the device unless it is exported deliberately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = {
                    scope.launch { viewModel.exportCsv()?.let(onShare) }
                }) {
                    Text("Export log for the doctor")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Creates a readable spreadsheet: a daily summary, every counting " +
                        "session with its time to goal, then each movement with its time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                SectionLabel("Please remember")
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "This app is a memory aid and a record to show a clinician. It is not " +
                        "a test of the baby's wellbeing. Current guidance puts the weight on " +
                        "noticing a change from what is normal for this baby, not on hitting a " +
                        "number. If movements ever feel reduced or different, contact the " +
                        "midwife or maternity unit straight away, whatever the app shows.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showDatePicker) {
        LmpDatePicker(
            initial = snapshot.settings.lmp,
            onDismiss = { showDatePicker = false },
            onSelect = {
                viewModel.setLmp(it)
                showDatePicker = false
            },
        )
    }

    editingEdge?.let { edge ->
        HourPickerDialog(
            title = if (edge == WindowEdge.Start) "Reminders start at" else "Reminders stop at",
            initialHour = if (edge == WindowEdge.Start) {
                snapshot.settings.reminderStartHour
            } else {
                snapshot.settings.reminderEndHour
            },
            onDismiss = { editingEdge = null },
            onSelect = { hour ->
                if (edge == WindowEdge.Start) {
                    viewModel.setReminderWindow(hour, snapshot.settings.reminderEndHour)
                } else {
                    viewModel.setReminderWindow(snapshot.settings.reminderStartHour, hour)
                }
                editingEdge = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BabyNameCard(snapshot: KickSnapshot, viewModel: KickViewModel) {
    val stored = snapshot.settings.babyName
    // Local state so typing is not fought by the settings flow echoing back.
    var name by remember(stored) { mutableStateOf(stored) }
    val preview = if (name.isBlank()) "Baby moved" else "${name.trim()} moved"

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            SectionLabel("Baby's name")
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = name,
                onValueChange = {
                    val cleaned = it.replace("\n", "").take(SettingsStore.MAX_NAME_LENGTH)
                    name = cleaned
                    viewModel.setBabyName(cleaned)
                },
                singleLine = true,
                label = { Text("Name (optional)") },
                placeholder = { Text("Baby") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                trailingIcon = {
                    if (name.isNotEmpty()) {
                        IconButton(onClick = {
                            name = ""
                            viewModel.setBabyName("")
                        }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Clear name")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "The notification button will read “$preview”.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Leave it empty to keep the wording as Baby. " +
                    "Up to ${SettingsStore.MAX_NAME_LENGTH} characters, so the button still fits.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReminderCard(
    snapshot: KickSnapshot,
    viewModel: KickViewModel,
    onEditEdge: (WindowEdge) -> Unit,
    context: Context,
) {
    val settings = snapshot.settings
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("Hourly reminder")
                Switch(
                    checked = settings.remindersEnabled,
                    onCheckedChange = viewModel::setRemindersEnabled,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "On the hour, if nothing was logged in the hour just gone, the phone " +
                    "sounds a reminder in case logging was forgotten. A quiet hour is normal, " +
                    "so this is a nudge and not a warning.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { onEditEdge(WindowEdge.Start) },
                    enabled = settings.remindersEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("From ${Formatting.hourLabel(settings.reminderStartHour)}")
                }
                OutlinedButton(
                    onClick = { onEditEdge(WindowEdge.End) },
                    enabled = settings.remindersEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Until ${Formatting.hourLabel(settings.reminderEndHour)}")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Nothing sounds outside these hours, so sleep is never interrupted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (settings.remindersEnabled && !canScheduleExactAlarms(context)) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Android is currently allowed to delay these reminders by a few " +
                        "minutes. Granting the alarms permission makes them land on the hour.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { openExactAlarmSettings(context) }) {
                    Text("Allow exact alarms")
                }
            }
        }
    }
}

private fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val manager = context.getSystemService(android.app.AlarmManager::class.java)
    return manager?.canScheduleExactAlarms() ?: false
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    runCatching {
        context.startActivity(
            Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HourPickerDialog(
    title: String,
    initialHour: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = 0, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                TimePicker(state = state)
                Text(
                    text = "Reminders are checked on the hour, so only the hour is used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSelect(state.hour) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LmpDatePicker(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        // The picker works in UTC; read the date back the same way.
                        onSelect(Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate())
                    }
                },
            ) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}
