package com.omi.kickcounter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.omi.kickcounter.domain.Pregnancy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class Settings(
    /** First day of the last menstrual period; gestational age is measured from here. */
    val lmp: LocalDate = Pregnancy.DEFAULT_LMP,
    /** Taps closer together than this are counted as a single movement in the stats. */
    val groupingMinutes: Int = 0,
    /** Movements that make up one counting session. ACOG frames this as ten. */
    val dailyGoal: Int = 10,
    /** Optional. Blank means the app says "Baby" rather than a chosen name. */
    val babyName: String = "",
    val remindersEnabled: Boolean = true,
    /** Waking window for reminders, as local hours. Nothing sounds outside it. */
    val reminderStartHour: Int = 9,
    val reminderEndHour: Int = 21,
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val LMP = longPreferencesKey("lmp_epoch_day")
        val GROUPING = intPreferencesKey("grouping_minutes")
        val GOAL = intPreferencesKey("daily_goal")
        val BABY_NAME = stringPreferencesKey("baby_name")
        val REMINDERS = booleanPreferencesKey("reminders_enabled")
        val REMINDER_START = intPreferencesKey("reminder_start_hour")
        val REMINDER_END = intPreferencesKey("reminder_end_hour")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            lmp = prefs[Keys.LMP]?.let(LocalDate::ofEpochDay) ?: Pregnancy.DEFAULT_LMP,
            groupingMinutes = prefs[Keys.GROUPING] ?: 0,
            dailyGoal = prefs[Keys.GOAL] ?: 10,
            babyName = prefs[Keys.BABY_NAME].orEmpty(),
            remindersEnabled = prefs[Keys.REMINDERS] ?: true,
            reminderStartHour = prefs[Keys.REMINDER_START] ?: 9,
            reminderEndHour = prefs[Keys.REMINDER_END] ?: 21,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setLmp(date: LocalDate) {
        context.dataStore.edit { it[Keys.LMP] = date.toEpochDay() }
    }

    suspend fun setGroupingMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.GROUPING] = minutes.coerceIn(0, 60) }
    }

    suspend fun setDailyGoal(goal: Int) {
        context.dataStore.edit { it[Keys.GOAL] = goal.coerceIn(1, 100) }
    }

    /** Capped so the notification button label cannot outgrow its space. */
    suspend fun setBabyName(name: String) {
        context.dataStore.edit { it[Keys.BABY_NAME] = name.trim().take(MAX_NAME_LENGTH) }
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REMINDERS] = enabled }
    }

    suspend fun setReminderWindow(startHour: Int, endHour: Int) {
        context.dataStore.edit {
            it[Keys.REMINDER_START] = startHour.coerceIn(0, 23)
            it[Keys.REMINDER_END] = endHour.coerceIn(0, 23)
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 14
    }
}
