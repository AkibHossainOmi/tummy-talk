package com.omi.kickcounter.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.omi.kickcounter.KickCounterApp
import com.omi.kickcounter.appContainer
import com.omi.kickcounter.domain.KickSnapshot
import com.omi.kickcounter.domain.SnapshotProvider
import com.omi.kickcounter.reminder.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class KickViewModel(private val app: Application) : AndroidViewModel(app) {

    private val container = app.appContainer
    private val provider = SnapshotProvider(container.repository, container.settingsStore)

    val snapshot: StateFlow<KickSnapshot?> = provider
        .snapshots(tickMillis = 10_000L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun recordKick() = viewModelScope.launch {
        val settings = container.settingsStore.current()
        container.repository.record(settings.dailyGoal, settings.groupingMinutes)
    }

    fun undoLast() = viewModelScope.launch {
        container.repository.undoLast()
    }

    fun redoLast() = viewModelScope.launch {
        container.repository.redoLast()
    }

    fun startSession(goal: Int) = viewModelScope.launch {
        container.repository.startSession(goal)
    }

    fun endSession() = viewModelScope.launch {
        container.repository.endSession()
    }

    fun setLmp(date: LocalDate) = viewModelScope.launch {
        container.settingsStore.setLmp(date)
    }

    fun setGroupingMinutes(minutes: Int) = viewModelScope.launch {
        container.settingsStore.setGroupingMinutes(minutes)
    }

    fun setDailyGoal(goal: Int) = viewModelScope.launch {
        container.settingsStore.setDailyGoal(goal)
    }

    fun setBabyName(name: String) = viewModelScope.launch {
        container.settingsStore.setBabyName(name)
    }

    fun setRemindersEnabled(enabled: Boolean) = viewModelScope.launch {
        container.settingsStore.setRemindersEnabled(enabled)
        ReminderScheduler.reschedule(app, container.settingsStore.current())
    }

    fun setReminderWindow(startHour: Int, endHour: Int) = viewModelScope.launch {
        container.settingsStore.setReminderWindow(startHour, endHour)
        ReminderScheduler.reschedule(app, container.settingsStore.current())
    }

    suspend fun exportCsv(): Uri? = CsvExport.write(
        context = app,
        kicks = container.repository.getAllKicks(),
        sessions = container.repository.getAllSessions(),
        settings = container.settingsStore.current(),
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                KickViewModel(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as KickCounterApp
                )
            }
        }
    }
}
