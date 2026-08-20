package com.omi.kickcounter.data

/**
 * Raw DDL, kept free of Android types so migrations can be exercised by plain JVM
 * tests. Each statement is byte-identical to the `createSql` Room exports in
 * app/schemas, which is what Room's identity check compares against at runtime.
 */
object DatabaseSchema {

    const val KICKS_V1 =
        "CREATE TABLE IF NOT EXISTS `kicks` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`timestamp` INTEGER NOT NULL)"

    /** Undo became a soft delete in version 3 so that redo can restore the tap. */
    const val ADD_DELETED_AT_V3 =
        "ALTER TABLE `kicks` ADD COLUMN `deletedAt` INTEGER"

    const val KICKS_V3 =
        "CREATE TABLE IF NOT EXISTS `kicks` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`timestamp` INTEGER NOT NULL, " +
            "`deletedAt` INTEGER)"

    const val SESSIONS_V2 =
        "CREATE TABLE IF NOT EXISTS `sessions` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`startedAt` INTEGER NOT NULL, " +
            "`endedAt` INTEGER, " +
            "`goal` INTEGER NOT NULL, " +
            "`completed` INTEGER NOT NULL)"
}
