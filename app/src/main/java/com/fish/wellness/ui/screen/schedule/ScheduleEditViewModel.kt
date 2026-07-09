package com.fish.wellness.ui.screen.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.fish.wellness.data.dao.ScheduleDao
import com.fish.wellness.data.entity.ScheduleEntity
import com.fish.wellness.model.DayOfWeek
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ScheduleEditUiState(
    val id: Long = -1L,
    val name: String = "",
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 7,
    val endMinute: Int = 0,
    val days: Set<DayOfWeek> = DayOfWeek.ALL.toSet(),
    val enabled: Boolean = true,
    val isLoading: Boolean = true,
    val isSaved: Boolean = false
) {
    val startMinutes get() = startHour * 60 + startMinute
    val endMinutes get() = endHour * 60 + endMinute
    val startLabel get() = "%02d:%02d".format(startHour, startMinute)
    val endLabel get() = "%02d:%02d".format(endHour, endMinute)
}

@HiltViewModel
class ScheduleEditViewModel @Inject constructor(
    private val app: Application,
    savedStateHandle: SavedStateHandle,
    private val scheduleDao: ScheduleDao
) : AndroidViewModel(app) {

    private val scheduleId: Long = savedStateHandle.get<Long>("scheduleId") ?: -1L

    private val _uiState = MutableStateFlow(ScheduleEditUiState())
    val uiState: StateFlow<ScheduleEditUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        if (scheduleId <= 0) {
            _uiState.value = ScheduleEditUiState(isLoading = false)
            return
        }
        viewModelScope.launch {
            val schedule = withContext(Dispatchers.IO) { scheduleDao.getById(scheduleId) }
            if (schedule != null) {
                _uiState.value = ScheduleEditUiState(
                    id = schedule.id,
                    name = schedule.name,
                    startHour = schedule.startMinutes / 60,
                    startMinute = schedule.startMinutes % 60,
                    endHour = schedule.endMinutes / 60,
                    endMinute = schedule.endMinutes % 60,
                    days = DayOfWeek.fromBits(schedule.daysOfWeek),
                    enabled = schedule.enabled,
                    isLoading = false
                )
            } else {
                _uiState.value = ScheduleEditUiState(isLoading = false)
            }
        }
    }

    fun updateName(name: String) { _uiState.value = _uiState.value.copy(name = name) }
    fun updateStart(hour: Int, minute: Int) { _uiState.value = _uiState.value.copy(startHour = hour, startMinute = minute) }
    fun updateEnd(hour: Int, minute: Int) { _uiState.value = _uiState.value.copy(endHour = hour, endMinute = minute) }

    fun toggleDay(day: DayOfWeek) {
        val current = _uiState.value.days.toMutableSet()
        if (day in current) current.remove(day) else current.add(day)
        if (current.isEmpty()) current.add(day)
        _uiState.value = _uiState.value.copy(days = current)
    }

    fun selectAllDays() { _uiState.value = _uiState.value.copy(days = DayOfWeek.ALL.toSet()) }
    fun selectWeekdays() { _uiState.value = _uiState.value.copy(days = setOf(DayOfWeek.MON, DayOfWeek.TUE, DayOfWeek.WED, DayOfWeek.THU, DayOfWeek.FRI)) }
    fun selectWeekends() { _uiState.value = _uiState.value.copy(days = setOf(DayOfWeek.SAT, DayOfWeek.SUN)) }

    fun save() {
        val s = _uiState.value
        val name = s.name.ifBlank { "Schedule" }
        viewModelScope.launch {
            val entity = ScheduleEntity(
                id = if (s.id > 0) s.id else 0,
                name = name,
                startMinutes = s.startMinutes,
                endMinutes = s.endMinutes,
                daysOfWeek = DayOfWeek.toBits(s.days),
                enabled = s.enabled
            )
            if (s.id > 0) scheduleDao.update(entity)
            else scheduleDao.insert(entity)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    fun delete() {
        if (scheduleId > 0) {
            viewModelScope.launch {
                scheduleDao.deleteById(scheduleId)
                _uiState.value = _uiState.value.copy(isSaved = true)
            }
        }
    }
}
