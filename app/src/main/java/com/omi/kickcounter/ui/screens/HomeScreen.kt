package com.omi.kickcounter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omi.kickcounter.domain.Formatting
import com.omi.kickcounter.domain.KickSnapshot
import com.omi.kickcounter.domain.Pregnancy
import com.omi.kickcounter.ui.components.GlassCard
import com.omi.kickcounter.ui.components.KickDial
import com.omi.kickcounter.ui.components.SectionLabel
import com.omi.kickcounter.ui.components.StatTile
import com.omi.kickcounter.ui.components.ThinMeter

@Composable
fun HomeScreen(
    snapshot: KickSnapshot,
    onKick: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onStartSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = snapshot.activeSession

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PregnancyCard(snapshot)

        Spacer(Modifier.height(24.dp))

        KickDial(
            count = session?.count ?: snapshot.todayCount,
            progress = if (session == null) {
                0f
            } else {
                session.count.toFloat() / session.goal
            },
            caption = if (session == null) "today" else "this session",
            onTap = onKick,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = dialSubtitle(snapshot),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onUndo, enabled = snapshot.todayCount > 0 || session != null) {
                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Undo")
            }
            TextButton(onClick = onRedo, enabled = snapshot.canRedo) {
                Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Redo")
            }
        }

        if (snapshot.canRedo) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Redo puts back the movement from " +
                    "${Formatting.clock(snapshot.restorableTimestamp!!)}, for 30 minutes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTile(
                label = "Today",
                value = "${snapshot.todayCount}",
                caption = "movements logged",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Last movement",
                value = Formatting.relative(snapshot.lastKick, snapshot.now),
                caption = snapshot.lastKick?.let { "at ${Formatting.clock(it)}" },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTile(
                label = "Busiest 2 hours",
                value = "${snapshot.bestTwoHours?.count ?: 0}",
                caption = snapshot.bestTwoHours
                    ?.takeIf { it.count > 0 }
                    ?.let { "${Formatting.clock(it.from)} to ${Formatting.clock(it.to)}" }
                    ?: "nothing logged today",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "This hour",
                value = "${snapshot.thisHourCount}",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        SessionCard(snapshot, onStartSession)

        if (snapshot.beforeCountingWindow) {
            Spacer(Modifier.height(12.dp))
            GuidanceCard(snapshot)
        }
    }
}

private fun dialSubtitle(s: KickSnapshot): String {
    val session = s.activeSession
        ?: return "Tap when you feel a movement. The count starts on its own."
    val elapsed = Formatting.duration(session.elapsedMillis(s.now))
    val done = session.timeToGoalMillis
    return if (done != null) {
        "${session.goal} movements in ${Formatting.duration(done)}"
    } else {
        "${session.count} of ${session.goal} · counting for $elapsed"
    }
}

@Composable
private fun PregnancyCard(s: KickSnapshot) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            SectionLabel("Gestational age")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${s.age.weeks}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = " weeks ",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    text = "${s.age.days}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = " days",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            ThinMeter(
                progress = s.age.totalDays.toFloat() / Pregnancy.TERM_DAYS,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Trimester ${s.age.trimester}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Due ${Formatting.mediumDate(s.dueDate)} · ${s.daysUntilDue} days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SessionCard(s: KickSnapshot, onStartSession: () -> Unit) {
    val completed = s.todaySessions.filter { it.completed }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            SectionLabel("Counting sessions today")
            Spacer(Modifier.height(12.dp))
            if (s.todaySessions.isEmpty()) {
                Text(
                    text = "None yet today. A count starts by itself the moment you log the " +
                        "first movement, and times how long the next ${s.settings.dailyGoal} " +
                        "take. Count kicks, rolls, jabs and swishes; hiccups do not count.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onStartSession) { Text("Start the clock now") }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Only needed if you want the clock running before the first movement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                s.todaySessions.forEach { session ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = Formatting.clock(session.startedAt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = session.timeToGoalMillis
                                ?.let { "${session.goal} in ${Formatting.duration(it)}" }
                                ?: "${session.count} of ${session.goal}",
                            style = MaterialTheme.typography.bodyMedium,
                            // Reaching the goal is highlighted; falling short is stated
                            // plainly and never coloured as a warning.
                            color = if (session.completed) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Each count is timed from its own first movement and runs for up to " +
                        "two hours. A count that ends below ${s.settings.dailyGoal} is not a " +
                        "concern by itself — it often just means the count began during a quiet " +
                        "spell. The busiest two hours above is the fuller picture.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GuidanceCard(s: KickSnapshot) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            SectionLabel("Before you rely on the numbers")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Formal counting is usually advised from ${KickSnapshot.COUNTING_START_WEEK} " +
                    "weeks, which is ${Formatting.mediumDate(countingStartDate(s))}. Movements " +
                    "before then are still irregular, so quiet days are common and the daily " +
                    "totals do not mean much yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Whatever the app shows, contact the midwife or doctor if movements ever " +
                    "feel reduced or different from usual.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun countingStartDate(s: KickSnapshot) =
    s.settings.lmp.plusWeeks(KickSnapshot.COUNTING_START_WEEK.toLong())
