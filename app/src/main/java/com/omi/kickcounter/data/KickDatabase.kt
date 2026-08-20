package com.omi.kickcounter.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Kick::class, CountingSession::class], version = 3, exportSchema = true)
abstract class KickDatabase : RoomDatabase() {

    abstract fun kickDao(): KickDao

    abstract fun sessionDao(): SessionDao

    companion object {
        /** Adds deliberate counting sessions. Recorded taps are left untouched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(DatabaseSchema.SESSIONS_V2)
            }
        }

        /** Turns undo into a soft delete. Existing taps stay visible: NULL means live. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(DatabaseSchema.ADD_DELETED_AT_V3)
            }
        }

        @Volatile
        private var instance: KickDatabase? = null

        fun get(context: Context): KickDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                KickDatabase::class.java,
                "kicks.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }
    }
}
