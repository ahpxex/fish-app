package com.fish.wellness.ui.screen.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fish.wellness.data.dao.PolicyDao
import com.fish.wellness.data.dao.QuickBlockSessionDao
import com.fish.wellness.data.entity.PolicyEntity
import com.fish.wellness.data.entity.QuickBlockSessionEntity
import com.fish.wellness.manager.PasswordManager
import com.fish.wellness.service.ScheduleAlarmReceiver
import com.fish.wellness.service.ScheduleForegroundService
import com.fish.wellness.util.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val policies: List<PolicyEntity> = emptyList(),
    val activeQuickBlocks: List<QuickBlockSessionEntity> = emptyList(),
    val hasOverlayPermission: Boolean = false,
    val hasAccessibilityPermission: Boolean = false,
    val hasUsageStatsPermission: Boolean = false,
    val hasPassword: Boolean = false
) {
    val allPermissionsGranted: Boolean
        get() = hasOverlayPermission && hasAccessibilityPermission && hasUsageStatsPermission
    val activePolicyCount: Int
        get() = policies.count { it.enabled && it.isCurrentlyActive() }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val app: Application,
    private val policyDao: PolicyDao,
    private val quickBlockDao: QuickBlockSessionDao,
    val passwordManager: PasswordManager
) : AndroidViewModel(app) {

    private val _permissions = MutableStateFlow(Triple(false, false, false))

    val uiState: StateFlow<HomeUiState> = combine(
        policyDao.observeAll(),
        quickBlockDao.observeActive(),
        _permissions,
        passwordManager.observeHasPassword()
    ) { policies, quickBlocks, perms, hasPassword ->
        HomeUiState(
            policies = policies,
            activeQuickBlocks = quickBlocks,
            hasOverlayPermission = perms.first,
            hasAccessibilityPermission = perms.second,
            hasUsageStatsPermission = perms.third,
            hasPassword = hasPassword
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    init { refreshPermissions() }

    fun refreshPermissions() {
        val ctx = getApplication<Application>()
        _permissions.value = Triple(
            PermissionChecker.canDrawOverlays(ctx),
            PermissionChecker.isAccessibilityServiceEnabled(ctx),
            PermissionChecker.hasUsageStatsPermission(ctx)
        )
    }

    fun enablePolicy(policy: PolicyEntity) {
        viewModelScope.launch { policyDao.update(policy.copy(enabled = true)) }
    }

    suspend fun needsPasswordToDisable(): Boolean = passwordManager.hasPassword()

    fun disablePolicy(policy: PolicyEntity) {
        viewModelScope.launch { policyDao.update(policy.copy(enabled = false)) }
    }

    suspend fun disableWithPassword(policy: PolicyEntity, password: String): Boolean {
        if (passwordManager.verifyPassword(password)) {
            policyDao.update(policy.copy(enabled = false))
            return true
        }
        return false
    }

    suspend fun setupPasswordAndDisable(policy: PolicyEntity, password: String) {
        passwordManager.setPassword(password)
        policyDao.update(policy.copy(enabled = false))
    }

    fun deletePolicy(id: Long) {
        viewModelScope.launch { policyDao.deleteById(id) }
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
}
