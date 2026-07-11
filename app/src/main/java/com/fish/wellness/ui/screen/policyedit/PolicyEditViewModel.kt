package com.fish.wellness.ui.screen.policyedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fish.wellness.data.dao.BlockedAppDao
import com.fish.wellness.data.dao.PolicyDao
import com.fish.wellness.data.entity.PolicyEntity
import com.fish.wellness.model.DayOfWeek
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PolicyEditUiState(
    val id: Long = -1L,
    val name: String = "",
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 7,
    val endMinute: Int = 0,
    val days: Set<DayOfWeek> = DayOfWeek.ALL.toSet(),
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val isLoading: Boolean = true,
    val isSaved: Boolean = false,
    val appCount: Int = 0
) {
    val startMinutes get() = startHour * 60 + startMinute
    val endMinutes get() = endHour * 60 + endMinute
    val startLabel get() = "%02d:%02d".format(startHour, startMinute)
    val endLabel get() = "%02d:%02d".format(endHour, endMinute)
}

@HiltViewModel
class PolicyEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val policyDao: PolicyDao,
    private val blockedAppDao: BlockedAppDao
) : ViewModel() {

    val policyId: Long = savedStateHandle.get<Long>("policyId") ?: -1L

    private val _meta = MutableStateFlow(PolicyEditUiState(isLoading = true))

    // Count uses the effective id (the DB id once the policy is inserted), not the route arg,
    // so that selecting apps on a brand-new policy reflects the count when we return from AppPicker.
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PolicyEditUiState> = _meta
        .flatMapLatest { meta ->
            val effectiveId = when {
                meta.id > 0 -> meta.id
                policyId > 0 -> policyId
                else -> 0
            }
            blockedAppDao.observeCountByPolicy(effectiveId)
                .map { count -> meta.copy(appCount = count) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PolicyEditUiState(isLoading = true))

    init { load() }

    private fun load() {
        if (policyId <= 0) {
            _meta.value = PolicyEditUiState(isLoading = false)
            return
        }
        viewModelScope.launch {
            val policy = policyDao.getById(policyId)
            if (policy != null) {
                _meta.value = PolicyEditUiState(
                    id = policy.id,
                    name = policy.name,
                    startHour = policy.startMinutes / 60,
                    startMinute = policy.startMinutes % 60,
                    endHour = policy.endMinutes / 60,
                    endMinute = policy.endMinutes % 60,
                    days = DayOfWeek.fromBits(policy.daysOfWeek),
                    enabled = policy.enabled,
                    createdAt = policy.createdAt,
                    isLoading = false
                )
            } else {
                _meta.value = PolicyEditUiState(isLoading = false)
            }
        }
    }

    fun updateName(name: String) { _meta.value = _meta.value.copy(name = name) }
    fun updateStart(hour: Int, minute: Int) { _meta.value = _meta.value.copy(startHour = hour, startMinute = minute) }
    fun updateEnd(hour: Int, minute: Int) { _meta.value = _meta.value.copy(endHour = hour, endMinute = minute) }

    fun toggleDay(day: DayOfWeek) {
        val current = _meta.value.days.toMutableSet()
        if (day in current) current.remove(day) else current.add(day)
        if (current.isEmpty()) current.add(day)
        _meta.value = _meta.value.copy(days = current)
    }

    fun selectAllDays() { _meta.value = _meta.value.copy(days = DayOfWeek.ALL.toSet()) }
    fun selectWeekdays() { _meta.value = _meta.value.copy(days = setOf(DayOfWeek.MON, DayOfWeek.TUE, DayOfWeek.WED, DayOfWeek.THU, DayOfWeek.FRI)) }
    fun selectWeekends() { _meta.value = _meta.value.copy(days = setOf(DayOfWeek.SAT, DayOfWeek.SUN)) }

    private fun buildEntity(s: PolicyEditUiState): PolicyEntity = PolicyEntity(
        id = if (s.id > 0) s.id else 0,
        name = s.name.ifBlank { "Policy" },
        startMinutes = s.startMinutes,
        endMinutes = s.endMinutes,
        daysOfWeek = DayOfWeek.toBits(s.days),
        enabled = s.enabled,
        createdAt = s.createdAt
    )

    fun save() {
        val s = _meta.value
        viewModelScope.launch {
            val entity = buildEntity(s)
            if (s.id > 0) policyDao.update(entity)
            else policyDao.insert(entity)
            _meta.value = _meta.value.copy(isSaved = true)
        }
    }

    fun saveAndSelectApps(onResult: (Long) -> Unit) {
        val s = _meta.value
        if (s.id > 0) {
            viewModelScope.launch {
                policyDao.update(buildEntity(s))
                onResult(s.id)
            }
            return
        }
        viewModelScope.launch {
            val newId = policyDao.insert(buildEntity(s))
            _meta.value = _meta.value.copy(id = newId)
            onResult(newId)
        }
    }

    fun delete() {
        if (policyId > 0) {
            viewModelScope.launch {
                policyDao.deleteById(policyId)
                _meta.value = _meta.value.copy(isSaved = true)
            }
        }
    }
}
