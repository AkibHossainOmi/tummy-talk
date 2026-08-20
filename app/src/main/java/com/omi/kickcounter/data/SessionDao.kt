package com.omi.kickcounter.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: CountingSession): Long

    @Update
    suspend fun update(session: CountingSession)

    @Query("SELECT * FROM sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActive(): CountingSession?

    @Query("SELECT * FROM sessions WHERE startedAt >= :since ORDER BY startedAt ASC")
    fun observeSince(since: Long): Flow<List<CountingSession>>

    @Query("SELECT * FROM sessions ORDER BY startedAt ASC")
    suspend fun getAll(): List<CountingSession>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
