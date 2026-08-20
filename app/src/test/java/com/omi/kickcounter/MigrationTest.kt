package com.omi.kickcounter

import com.omi.kickcounter.data.DatabaseSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.DriverManager

/**
 * Exercises the 1 -> 2 upgrade against a real SQLite engine: the taps she already
 * recorded must survive an app update untouched, and the new table has to come out
 * with exactly the shape Room expects, or Room aborts on first use.
 */
class MigrationTest {

    @Test
    fun `existing taps survive the upgrade and the sessions table matches Room`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            db.createStatement().use { it.execute(DatabaseSchema.KICKS_V1) }

            // Three taps recorded by version 1 of the app.
            db.prepareStatement("INSERT INTO kicks (timestamp) VALUES (?)").use { insert ->
                listOf(1_754_000_000_000L, 1_754_000_060_000L, 1_754_000_120_000L).forEach {
                    insert.setLong(1, it)
                    insert.executeUpdate()
                }
            }

            // The upgrade.
            db.createStatement().use { it.execute(DatabaseSchema.SESSIONS_V2) }

            val timestamps = mutableListOf<Long>()
            db.createStatement().use { statement ->
                statement.executeQuery("SELECT timestamp FROM kicks ORDER BY timestamp").use { rs ->
                    while (rs.next()) timestamps.add(rs.getLong(1))
                }
            }
            assertEquals(
                listOf(1_754_000_000_000L, 1_754_000_060_000L, 1_754_000_120_000L),
                timestamps,
            )

            val columns = mutableMapOf<String, Pair<String, Boolean>>()
            db.createStatement().use { statement ->
                statement.executeQuery("PRAGMA table_info(`sessions`)").use { rs ->
                    while (rs.next()) {
                        columns[rs.getString("name")] =
                            rs.getString("type") to (rs.getInt("notnull") == 1)
                    }
                }
            }

            assertEquals(setOf("id", "startedAt", "endedAt", "goal", "completed"), columns.keys)
            assertEquals("INTEGER" to true, columns["startedAt"])
            assertEquals("INTEGER" to true, columns["goal"])
            assertEquals("INTEGER" to true, columns["completed"])
            // endedAt is the only nullable column: an open session has no end yet.
            assertEquals("INTEGER" to false, columns["endedAt"])
        }
    }

    @Test
    fun `the migration is safe to run twice`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            db.createStatement().use { it.execute(DatabaseSchema.KICKS_V1) }
            db.createStatement().use { it.execute(DatabaseSchema.SESSIONS_V2) }
            db.createStatement().use { it.execute(DatabaseSchema.SESSIONS_V2) }

            db.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='sessions'",
                ).use { rs ->
                    rs.next()
                    assertEquals(1, rs.getInt(1))
                }
            }
        }
    }

    @Test
    fun `the exported schema on disk still matches the migration DDL`() {
        val v2 = java.io.File("schemas/com.omi.kickcounter.data.KickDatabase/2.json")
        assertTrue("Room schema export for v2 is missing", v2.exists())
        assertTrue(
            "Sessions DDL has drifted from the schema Room exports",
            v2.readText().contains(DatabaseSchema.SESSIONS_V2.replace("`sessions`", "`\${TABLE_NAME}`")),
        )

        val v3 = java.io.File("schemas/com.omi.kickcounter.data.KickDatabase/3.json")
        assertTrue("Room schema export for v3 is missing", v3.exists())
        assertTrue(
            "Kicks DDL has drifted from the schema Room exports",
            v3.readText().contains(DatabaseSchema.KICKS_V3.replace("`kicks`", "`\${TABLE_NAME}`")),
        )
    }

    @Test
    fun `undo becomes reversible without losing the original time`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            db.createStatement().use { it.execute(DatabaseSchema.KICKS_V1) }
            db.createStatement().use {
                it.execute("INSERT INTO kicks (timestamp) VALUES (1754000000000)")
            }

            // Upgrading to v3 must leave the existing tap live, not removed.
            db.createStatement().use { it.execute(DatabaseSchema.ADD_DELETED_AT_V3) }
            db.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT COUNT(*) FROM kicks WHERE deletedAt IS NULL",
                ).use { rs ->
                    rs.next()
                    assertEquals(1, rs.getInt(1))
                }
            }

            // Undo hides it, redo brings it back with the original timestamp intact.
            db.createStatement().use { it.execute("UPDATE kicks SET deletedAt = 1754000900000") }
            db.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM kicks WHERE deletedAt IS NULL")
                    .use { rs ->
                        rs.next()
                        assertEquals(0, rs.getInt(1))
                    }
            }
            db.createStatement().use { it.execute("UPDATE kicks SET deletedAt = NULL") }
            db.createStatement().use { statement ->
                statement.executeQuery("SELECT timestamp FROM kicks WHERE deletedAt IS NULL")
                    .use { rs ->
                        rs.next()
                        assertEquals(1_754_000_000_000L, rs.getLong(1))
                    }
            }
        }
    }
}
