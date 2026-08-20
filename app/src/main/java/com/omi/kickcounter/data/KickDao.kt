package com.omi.kickcounter.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KickDao {

    @Insert
    suspend fun insert(kick: Kick): Long

    @Query(
        "SELECT * FROM kicks WHERE deletedAt IS NULL AND timestamp >= :since ORDER BY timestamp ASC",
    )
    fun observeSince(since: Long): Flow<List<Kick>>

    @Query("SELECT * FROM kicks WHERE deletedAt IS NULL ORDER BY timestamp ASC")
    suspend fun getAll(): List<Kick>

    @Query("SELECT * FROM kicks WHERE deletedAt IS NULL ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): Kick?

    @Query(
        "SELECT COUNT(*) FROM kicks WHERE deletedAt IS NULL " +
            "AND timestamp >= :from AND timestamp <= :to",
    )
    suspend fun countBetween(from: Long, to: Long): Int

    @Query(
        "SELECT * FROM kicks WHERE deletedAt IS NULL " +
            "AND timestamp >= :from AND timestamp <= :to ORDER BY timestamp ASC",
    )
    suspend fun getBetween(from: Long, to: Long): List<Kick>

    /** The tap most recently removed by undo, which is the one redo restores. */
    @Query("SELECT * FROM kicks WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC LIMIT 1")
    suspend fun getLastRemoved(): Kick?

    @Query("SELECT * FROM kicks WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC LIMIT 1")
    fun observeLastRemoved(): Flow<Kick?>

    @Query("UPDATE kicks SET deletedAt = :at WHERE id = :id")
    suspend fun markRemoved(id: Long, at: Long)

    @Query("UPDATE kicks SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)
}
