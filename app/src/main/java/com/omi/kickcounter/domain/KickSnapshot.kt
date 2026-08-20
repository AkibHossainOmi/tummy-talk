package com.omi.kickcounter.domain

import com.omi.kickcounter.data.CountingSession
import com.omi.kickcounter.data.Kick
import com.omi.kickcounter.data.KickRepository
import com.omi.kickcounter.data.Settings
import com.omi.kickcounter.data.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/** A counting session with its movements resolved. */
data class SessionView(
    val startedAt: Long,
    val endedAt: Long?,
    val goal: Int,
    val completed: Boolean,
    val movements: List<Long>,
) {
    val count: Int get() = movements.size
    val timeToGoalMillis: Long? get() = KickStats.timeToGoalMillis(movements, goal)
    fun elapsedMillis(now: Long): Long = (endedAt ?: now) - startedAt
}

/** One row of the history list. */
data class DaySummary(
    val date: LocalDate,
    val count: Int,
    /** Fastest time to the goal among that day's sessions, if any reached it. */
    val bestTimeToGoalMillis: Long?,
    val sessionsCompleted: Int,
)

/** Everything both the notification and the UI need, computed in one place. */
data class KickSnapshot(
    val now: Long,
    val today: LocalDate,
    val age: GestationalAge,
    val dueDate: LocalDate,
    val daysUntilDue: Int,
    val todayCount: Int,
    val thisHourCount: Int,
    val lastKick: Long?,
    /** Busiest two hours of today, wherever they fell. Null before any movement. */
    val bestTwoHours: KickStats.BestWindow?,
    val activeSession: SessionView?,
    val todaySessions: List<SessionView>,
    val hourlyToday: List<Int>,
    val dailyHistory: List<DaySummary>,
    /** Original time of the tap undo removed most recently, if one can be restored. */
    val restorableTimestamp: Long?,
    val restorableRemovedAt: Long?,
    val settings: Settings,
) {
    /**
     * Redo stays available for half an hour after the undo. Without an expiry the
     * button would sit enabled for days and could resurrect a tap into a long-past
     * day, which is worse than the mis-tap it was meant to fix.
     */
    val canRedo: Boolean
        get() = restorableTimestamp != null && redoIsFresh

    val redoIsFresh: Boolean
        get() = restorableRemovedAt?.let { now - it <= REDO_WINDOW_MILLIS } == true

    /** Counting is conventionally advised from 28 weeks. */
    val beforeCountingWindow: Boolean get() = age.weeks < 28

    companion object {
        const val HISTORY_DAYS = 14
        const val COUNTING_START_WEEK = 28
        val REDO_WINDOW_MILLIS: Long = java.util.concurrent.TimeUnit.MINUTES.toMillis(30)
    }
}

class SnapshotProvider(
    private val repository: KickRepository,
    private val settingsStore: SettingsStore,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    /**
     * Emits on every recorded tap, every session change, every settings change, and
     * on a slow tick so relative times and the midnight rollover stay honest.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun snapshots(tickMillis: Long = 30_000L): Flow<KickSnapshot> {
        val ticks = flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(tickMillis)
            }
        }

        val windowStart = ticks
            .map { now ->
                val firstDay = KickStats.today(zone, now)
                    .minusDays((KickSnapshot.HISTORY_DAYS - 1).toLong())
                KickStats.startOfDay(firstDay, zone)
            }
            .distinctUntilChanged()

        val data = combine(windowStart, settingsStore.settings) { start, s -> start to s }
            .flatMapLatest { (start, settings) ->
                combine(
                    repository.observeKicksSince(start),
                    repository.observeSessionsSince(start),
                    repository.observeLastRemoved(),
                ) { kicks, sessions, removed ->
                    Snapshotinputs(kicks.map { it.timestamp }, sessions, removed, settings)
                }
            }

        return combine(data, ticks) { inputs, _ ->
            // The tick is only a heartbeat to refresh relative times. Reading the
            // clock here rather than using the tick's value matters: a tick can be
            // half a minute old, and a stale "now" silently excluded a movement
            // that had just been recorded from its own session.
            build(
                rawTimestamps = inputs.timestamps,
                sessions = inputs.sessions,
                settings = inputs.settings,
                now = System.currentTimeMillis(),
                restorable = inputs.removed,
            )
        }.distinctUntilChanged()
    }

    private data class Snapshotinputs(
        val timestamps: List<Long>,
        val sessions: List<CountingSession>,
        val removed: Kick?,
        val settings: Settings,
    )

    fun build(
        rawTimestamps: List<Long>,
        sessions: List<CountingSession>,
        settings: Settings,
        now: Long,
        restorable: Kick? = null,
    ): KickSnapshot {
        val movements = KickStats.distinctMovements(rawTimestamps, settings.groupingMinutes)
        val today = KickStats.today(zone, now)
        val todayStart = KickStats.startOfDay(today, zone)
        val hourStart = KickStats.startOfHour(now, zone)

        // A session that ran out of its two hours is treated as finished on sight,
        // even though the row is only closed the next time a tap arrives.
        val views = sessions.map { session ->
            val expiry = session.startedAt + KickRepository.SESSION_TIMEOUT_MILLIS
            val effectiveEnd = session.endedAt ?: expiry.takeIf { it <= now }
            SessionView(
                startedAt = session.startedAt,
                endedAt = effectiveEnd,
                goal = session.goal,
                completed = session.completed,
                // An open session has no upper bound at all. Bounding it by "now"
                // makes the count vulnerable to any clock skew between the write
                // and the read.
                movements = KickStats.movementsIn(
                    movements,
                    session.startedAt,
                    effectiveEnd ?: Long.MAX_VALUE,
                ),
            )
        }

        val byDay = movements.groupBy { KickStats.localDate(it, zone) }
        val sessionsByDay = views.groupBy { KickStats.localDate(it.startedAt, zone) }

        val history = (KickSnapshot.HISTORY_DAYS - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            val daySessions = sessionsByDay[date].orEmpty()
            DaySummary(
                date = date,
                count = byDay[date]?.size ?: 0,
                bestTimeToGoalMillis = daySessions.mapNotNull { it.timeToGoalMillis }.minOrNull(),
                sessionsCompleted = daySessions.count { it.completed },
            )
        }

        return KickSnapshot(
            now = now,
            today = today,
            age = Pregnancy.ageAt(settings.lmp, today),
            dueDate = Pregnancy.dueDate(settings.lmp),
            daysUntilDue = Pregnancy.daysUntilDue(settings.lmp, today),
            todayCount = movements.count { it >= todayStart },
            thisHourCount = movements.count { it >= hourStart },
            lastKick = movements.lastOrNull(),
            bestTwoHours = KickStats.bestWindow(
                movements.filter { it >= todayStart },
                KickRepository.SESSION_TIMEOUT_MILLIS,
            ),
            activeSession = views.lastOrNull { it.endedAt == null },
            todaySessions = sessionsByDay[today].orEmpty(),
            hourlyToday = KickStats.hourlyCounts(movements, today, zone).toList(),
            dailyHistory = history,
            restorableTimestamp = restorable?.timestamp,
            restorableRemovedAt = restorable?.deletedAt,
            settings = settings,
        )
    }
}
