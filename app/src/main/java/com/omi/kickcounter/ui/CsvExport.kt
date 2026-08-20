package com.omi.kickcounter.ui

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.omi.kickcounter.data.CountingSession
import com.omi.kickcounter.data.Kick
import com.omi.kickcounter.data.Settings
import com.omi.kickcounter.domain.Formatting
import com.omi.kickcounter.domain.KickStats
import com.omi.kickcounter.domain.Pregnancy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Writes a readable report rather than a raw dump: a daily summary first, then the
 * sessions, then every movement. Dates and times are plain local text, because the
 * audience is a midwife or doctor reading it on a phone.
 */
object CsvExport {

    private val fileStamp = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val dateOut = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    private val weekdayOut = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
    private val timeOut = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    suspend fun write(
        context: Context,
        kicks: List<Kick>,
        sessions: List<CountingSession>,
        settings: Settings,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis(),
    ): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val today = KickStats.today(zone, now)
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "kick-log-${today.format(fileStamp)}.csv")

            file.bufferedWriter().use { out ->
                out.appendLine("Baby movement log")
                out.appendLine(row("Exported", "${today.format(dateOut)}, ${time(now, zone)}"))
                out.appendLine(row("Gestational age", Pregnancy.ageAt(settings.lmp, today).format()))
                out.appendLine(row("Estimated due date", Pregnancy.dueDate(settings.lmp).format(dateOut)))
                out.appendLine(row("Total movements recorded", "${kicks.size}"))
                out.appendLine()

                val byDay = kicks.groupBy { date(it.timestamp, zone) }
                val sessionsByDay = sessions.groupBy { date(it.startedAt, zone) }
                val days = (byDay.keys + sessionsByDay.keys).distinct().sorted()

                out.appendLine("Daily summary")
                out.appendLine("Date,Day,Movements,Sessions completed,Fastest time to goal")
                days.forEach { day ->
                    val dayKicks = byDay[day].orEmpty()
                    val daySessions = sessionsByDay[day].orEmpty()
                    val fastest = daySessions
                        .mapNotNull { timeToGoal(it, kicks) }
                        .minOrNull()
                    out.appendLine(
                        listOf(
                            day.format(dateOut),
                            day.format(weekdayOut),
                            "${dayKicks.size}",
                            "${daySessions.count { it.completed }}",
                            fastest?.let(Formatting::duration) ?: "not reached",
                        ).joinToString(","),
                    )
                }
                out.appendLine()

                out.appendLine("Counting sessions")
                out.appendLine("Date,Started,Ended,Movements,Goal,Time to goal,Goal reached")
                if (sessions.isEmpty()) {
                    out.appendLine("No counting sessions recorded")
                } else {
                    sessions.forEach { session ->
                        val movements = movementsIn(session, kicks)
                        out.appendLine(
                            listOf(
                                date(session.startedAt, zone).format(dateOut),
                                time(session.startedAt, zone),
                                session.endedAt?.let { time(it, zone) } ?: "in progress",
                                "${movements.size}",
                                "${session.goal}",
                                timeToGoal(session, kicks)?.let(Formatting::duration)
                                    ?: "not reached",
                                if (session.completed) "Yes" else "No",
                            ).joinToString(","),
                        )
                    }
                }
                out.appendLine()

                out.appendLine("Every movement")
                out.appendLine("Date,Day,Time")
                kicks.forEach { kick ->
                    val day = date(kick.timestamp, zone)
                    out.appendLine(
                        listOf(
                            day.format(dateOut),
                            day.format(weekdayOut),
                            time(kick.timestamp, zone),
                        ).joinToString(","),
                    )
                }
            }

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }

    private fun row(label: String, value: String) = "$label,$value"

    private fun date(millis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun time(millis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(timeOut)

    private fun movementsIn(session: CountingSession, kicks: List<Kick>): List<Long> {
        val end = session.endedAt ?: Long.MAX_VALUE
        return kicks.map { it.timestamp }.filter { it >= session.startedAt && it <= end }
    }

    private fun timeToGoal(session: CountingSession, kicks: List<Kick>): Long? =
        KickStats.timeToGoalMillis(movementsIn(session, kicks), session.goal)
}
