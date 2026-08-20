package com.omi.kickcounter.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omi.kickcounter.domain.DaySummary
import com.omi.kickcounter.domain.Formatting
import com.omi.kickcounter.domain.KickSnapshot
import com.omi.kickcounter.ui.components.ColumnChart
import com.omi.kickcounter.ui.components.GlassCard
import com.omi.kickcounter.ui.components.SectionLabel
import com.omi.kickcounter.ui.components.StatTile

@Composable
fun HistoryScreen(snapshot: KickSnapshot, modifier: Modifier = Modifier) {
    val daily = snapshot.dailyHistory
    val logged = daily.filter { it.count > 0 }
    val average = if (logged.isEmpty()) 0 else logged.sumOf { it.count } / logged.size
    val fastest = daily.mapNotNull { it.bestTimeToGoalMillis }.minOrNull()

    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var selectedHour by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 24.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = "Daily average",
                value = "$average",
                caption = "over ${logged.size} logged days",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Fastest to goal",
                value = fastest?.let(Formatting::duration) ?: "-",
                caption = if (fastest != null) "best session" else "no session finished",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(16.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                SectionLabel("Today by hour")
                Spacer(Modifier.height(16.dp))
                ColumnChart(
                    values = snapshot.hourlyToday,
                    labels = (0..23).map { if (it % 6 == 0) Formatting.hourLabel(it) else "" },
                    selectedIndex = selectedHour,
                    onSelect = { selectedHour = if (selectedHour == it) null else it },
                    showZeroValues = false,
                    height = 130.dp,
                )
                Spacer(Modifier.height(12.dp))
                val hour = selectedHour
                Text(
                    text = if (hour != null) {
                        "${Formatting.hourLabel(hour)} to ${Formatting.hourLabel((hour + 1) % 24)}" +
                            " · ${snapshot.hourlyToday[hour]} movements"
                    } else {
                        "Tap a bar to see that hour. Totals are for ${Formatting.dayAndDate(snapshot.today)}."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                SectionLabel("Last ${KickSnapshot.HISTORY_DAYS} days")
                Spacer(Modifier.height(16.dp))
                ColumnChart(
                    values = daily.map { it.count },
                    labels = daily.map { "${it.date.dayOfMonth}" },
                    selectedIndex = selectedDay,
                    onSelect = { selectedDay = if (selectedDay == it) null else it },
                    height = 140.dp,
                )
                Spacer(Modifier.height(14.dp))
                AnimatedVisibility(visible = selectedDay != null) {
                    selectedDay?.let { index ->
                        SelectedDayDetail(daily[index])
                    }
                }
                if (selectedDay == null) {
                    Text(
                        text = "Tap any bar for that day's detail.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 20.dp)) {
                SectionLabel("Day by day", Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(12.dp))
                DayTableHeader()
                daily.asReversed().forEachIndexed { offset, day ->
                    if (offset > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                    DayRow(day = day, isToday = day.date == snapshot.today)
                }
            }
        }
    }
}

@Composable
private fun SelectedDayDetail(day: DaySummary) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
    ) {
        Column {
            Text(
                text = Formatting.dayAndDate(day.date),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${day.count} movements · ${day.sessionsCompleted} session" +
                    (if (day.sessionsCompleted == 1) "" else "s") + " completed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            day.bestTimeToGoalMillis?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Fastest to goal: ${Formatting.duration(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun DayTableHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp),
    ) {
        Text(
            text = "DATE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "MOVEMENTS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = "TO GOAL",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp),
        )
    }
}

@Composable
private fun DayRow(day: DaySummary, isToday: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isToday) "Today" else Formatting.dayAndDate(day.date),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${day.count}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (day.count == 0) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.End,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = day.bestTimeToGoalMillis?.let(Formatting::duration) ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            color = if (day.bestTimeToGoalMillis != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp),
        )
    }
}
