package com.fish.wellness.ui.screen.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fish.wellness.data.dao.BlockedAppDao
import com.fish.wellness.data.dao.QuickBlockSessionDao
import com.fish.wellness.data.dao.ScheduleDao
import com.fish.wellness.data.entity.BlockedAppEntity
import com.fish.wellness.data.entity.QuickBlockSessionEntity
import com.fish.wellness.data.entity.ScheduleEntity
import com.fish.wellness.service.ScheduleAlarmReceiver
import com.fish.wellness.service.ScheduleForegroundService
import com.fish.wellness.util.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val blockedApps: List<BlockedAppEntity> = emptyList(),
    val schedules: List<ScheduleEntity> = emptyList(),
    val activeQuickBlocks: List<QuickBlockSessionEntity> = emptyList(),
    val hasOverlayPermission: Boolean = false,
    val hasAccessibilityPermission: Boolean = false,
    val hasUsageStatsPermission: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val app: Application,
    private val blockedAppDao: BlockedAppDao,
    private val scheduleDao: ScheduleDao,
    private val quickBlockDao: QuickBlockSessionDao
) : AndroidViewModel(app) {

    private val _permissions = MutableStateFlow(Triple(false, false, false))

    val uiState: StateFlow<HomeUiState> = combine(
        blockedAppDao.observeAll(),
        scheduleDao.observeAll(),
        quickBlockDao.observeActive(),
        _permissions
    ) { blockedApps, schedules, quickBlocks, perms ->
        HomeUiState(
            blockedApps = blockedApps,
            schedules = schedules,
            activeQuickBlocks = quickBlocks,
            hasOverlayPermission = perms.first,
            hasAccessibilityPermission = perms.second,
            hasUsageStatsPermission = perms.third
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    init {
        refreshPermissions()
    }

    fun refreshPermissions() {
        val ctx = getApplication<Application>()
        _permissions.value = Triple(
            PermissionChecker.canDrawOverlays(ctx),
            PermissionChecker.isAccessibilityServiceEnabled(ctx),
            PermissionChecker.hasUsageStatsPermission(ctx)
        )
    }

    fun removeBlockedApp(packageName: String) {
        viewModelScope.launch { blockedAppDao.delete(packageName) }
    }

    fun deleteSchedule(id: Long) {
        viewModelScope.launch { scheduleDao.deleteById(id) }
    }

    fun toggleSchedule(schedule: ScheduleEntity) {
        viewModelScope.launch {
            scheduleDao.update(schedule.copy(enabled = !schedule.enabled))
        }
    }

    fun startQuickBlock(durationMinutes: Int) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val endAt = now + durationMinutes * 60_000L
            quickBlockDao.cancelAllActive()
            quickBlockDao.insert(QuickBlockSessionEntity(startAt = now, endAt = endAt))
            ScheduleAlarmReceiver.scheduleQuickBlockExpiry(getApplication(), endAt)
            ScheduleForegroundService.start(getApplication())
        }
    }

    fun cancelQuickBlock() {
        viewModelScope.launch {
            quickBlockDao.cancelAllActive()
            ScheduleForegroundService.stop(getApplication())
        }
    }

    val hasAllPermissions: Boolean
        get() = uiState.value.let {
            it.hasOverlayPermission && it.hasAccessibilityPermission && it.hasUsageStatsPermission
        }
}
