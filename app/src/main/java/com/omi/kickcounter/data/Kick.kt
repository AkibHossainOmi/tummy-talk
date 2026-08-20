package com.omi.kickcounter.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One recorded tap.
 *
 * Undo does not delete the row, it stamps [deletedAt]. That keeps the original
 * [timestamp] intact so redo can restore the movement at the moment she actually
 * felt it rather than the moment she noticed the mistake. Every query filters on
 * `deletedAt IS NULL`, so a removed tap is invisible everywhere until restored.
 */
@Entity(tableName = "kicks")
data class Kick(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val deletedAt: Long? = null,
)
