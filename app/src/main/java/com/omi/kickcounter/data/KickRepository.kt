package com.omi.kickcounter.data

import androidx.room.withTransaction
import com.omi.kickcounter.domain.KickStats
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

class KickRepository(private val database: KickDatabase) {

    private val kickDao = database.kickDao()
    private val sessionDao = database.sessionDao()

    /**
     * Records a tap and opens a session automatically if none is running, so the
     * notification alone is enough — she never has to open the app to start one.
     * The session clock therefore begins at the first movement, which is exactly
     * how "time to reach ten movements" is defined.
     */
    suspend fun record(
        goal: Int,
        groupingMinutes: Int = 0,
        now: Long = System.currentTimeMillis(),
    ): Long = database.withTransaction {
        // One transaction so Room invalidates both tables together. Written
        // separately, the sessions flow can emit before the kicks flow and the
        // screen flashes "0 of 10" for a moment on the tap that opens a session.
        kickDao.insert(Kick(timestamp = now))
        openSessionIfNeeded(goal, now)
        closeSessionIfGoalReached(groupingMinutes, now)
        now
    }

    private suspend fun openSessionIfNeeded(goal: Int, now: Long) {
        val active = sessionDao.getActive()
        if (active == null) {
            sessionDao.insert(CountingSession(startedAt = now, goal = goal))
            return
        }
        if (now - active.startedAt > SESSION_TIMEOUT_MILLIS) {
            // The previous one ran out of road; retire it and begin again from here.
            sessionDao.update(active.copy(endedAt = active.startedAt + SESSION_TIMEOUT_MILLIS))
            sessionDao.insert(CountingSession(startedAt = now, goal = goal))
        }
    }

    /**
     * Removes the most recent tap (mis-tap correction). The row is kept and stamped
     * so [redoLast] can put it back at its original time. Returns true if one was
     * removed.
     */
    suspend fun undoLast(now: Long = System.currentTimeMillis()): Boolean =
        database.withTransaction {
            val latest = kickDao.getLatest() ?: return@withTransaction false
            kickDao.markRemoved(latest.id, now)

            // Undoing the movement that opened a count leaves an empty session
            // sitting there reading "0 of 10". Treat it as never having started.
            val active = sessionDao.getActive()
            if (active != null && kickDao.countBetween(active.startedAt, Long.MAX_VALUE) == 0) {
                sessionDao.deleteById(active.id)
            }
            true
        }

    /** Restores the tap most recently removed by undo. Returns true if one came back. */
    suspend fun redoLast(): Boolean {
        val removed = kickDao.getLastRemoved() ?: return false
        kickDao.restore(removed.id)
        return true
    }

    /** The tap undo removed most recently, if there is one to restore. */
    fun observeLastRemoved(): Flow<Kick?> = kickDao.observeLastRemoved()

    fun observeKicksSince(since: Long): Flow<List<Kick>> = kickDao.observeSince(since)

    fun observeSessionsSince(since: Long): Flow<List<CountingSession>> =
        sessionDao.observeSince(since)

    suspend fun getAllKicks(): List<Kick> = kickDao.getAll()

    /** Inclusive on both ends. */
    suspend fun countBetween(from: Long, to: Long): Int = kickDao.countBetween(from, to)

    suspend fun getAllSessions(): List<CountingSession> = sessionDao.getAll()

    /** Begins a deliberate counting session, replacing any that was left open. */
    suspend fun startSession(goal: Int, now: Long = System.currentTimeMillis()) {
        sessionDao.getActive()?.let { sessionDao.update(it.copy(endedAt = now)) }
        sessionDao.insert(CountingSession(startedAt = now, goal = goal))
    }

    suspend fun endSession(now: Long = System.currentTimeMillis(), completed: Boolean = false) {
        val active = sessionDao.getActive() ?: return
        sessionDao.update(active.copy(endedAt = now, completed = completed))
    }

    /**
     * Closes an open session once the goal is met, and abandons one that has been
     * left running past the two-hour window the guidance is framed around.
     */
    suspend fun closeSessionIfGoalReached(
        groupingMinutes: Int = 0,
        now: Long = System.currentTimeMillis(),
    ) {
        val active = sessionDao.getActive() ?: return
        if (now - active.startedAt > SESSION_TIMEOUT_MILLIS) {
            sessionDao.update(active.copy(endedAt = active.startedAt + SESSION_TIMEOUT_MILLIS))
            return
        }
        // Must count the same way the screen does, or a session with grouping on
        // would close at ten raw taps while the dial still showed seven movements.
        val counted = KickStats.distinctMovements(
            kickDao.getBetween(active.startedAt, now).map { it.timestamp },
            groupingMinutes,
        ).size
        if (counted >= active.goal) {
            sessionDao.update(active.copy(endedAt = now, completed = true))
        }
    }

    companion object {
        /** ACOG frames the target as ten movements within two hours. */
        val SESSION_TIMEOUT_MILLIS: Long = TimeUnit.HOURS.toMillis(2)
    }
}
