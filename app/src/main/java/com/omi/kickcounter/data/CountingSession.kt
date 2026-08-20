package com.omi.kickcounter.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A deliberate counting session, the way clinical guidance describes it: she sits
 * down, starts counting, and the useful number is how long the baby took to reach
 * the goal. A session with [endedAt] still null is the one in progress.
 */
@Entity(tableName = "sessions")
data class CountingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val goal: Int,
    /** True when the goal was reached rather than the session being abandoned. */
    val completed: Boolean = false,
)
