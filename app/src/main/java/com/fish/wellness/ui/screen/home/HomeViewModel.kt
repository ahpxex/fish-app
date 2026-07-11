package com.fish.wellness.ui.screen.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fish.wellness.data.dao.BlockedAppDao
import com.fish.wellness.data.dao.PolicyDao
import com.fish.wellness.data.dao.QuickBlockSessionDao
import com.fish.wellness.data.entity.PolicyEntity
import com.fish.wellness.data.entity.QuickBlockSessionEntity
import com.fish.wellness.manager.PasswordManager
import com.fish.wellness.util.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

data class HomeUiState(
    val policies: List<PolicyEntity> = emptyList(),
    val activeQuickBlocks: List<QuickBlockSessionEntity> = emptyList(),
    val hasAccessibilityPermission: Boolean = false,
    val hasUsageStatsPermission: Boolean = false,
    val hasDailyLimits: Boolean = false,
    val now: LocalDateTime = LocalDateTime.now(),
    val nowEpochMillis: Long = System.currentTimeMillis()
) {
    val allPermissionsGranted: Boolean
        get() = hasAccessibilityPermission && (!hasDailyLimits || hasUsageStatsPermission)
}

private data class HomeData(
    val policies: List<PolicyEntity>,
    val quickBlocks: List<QuickBlockSessionEntity>,
    val hasDailyLimits: Boolean
)

private data class PermissionState(
    val accessibility: Boolean = false,
    val usageStats: Boolean = false
)

private data class ClockTick(val localDateTime: LocalDateTime, val epochMillis: Long)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val app: Application,
    private val policyDao: PolicyDao,
    blockedAppDao: BlockedAppDao,
    private val quickBlockDao: QuickBlockSessionDao,
    val passwordManager: PasswordManager
) : AndroidViewModel(app) {

    private val _permissions = MutableStateFlow(PermissionState())

    private val homeData = combine(
        policyDao.observeAll(),
        quickBlockDao.observeActive(),
        blockedAppDao.observeAll()
    ) { policies, quickBlocks, blockedApps ->
        HomeData(
            policies = policies,
            quickBlocks = quickBlocks,
            hasDailyLimits = blockedApps.any { !it.isFullBlock }
        )
    }

    private val clock = flow {
        while (currentCoroutineContext().isActive) {
            emit(ClockTick(LocalDateTime.now(), System.currentTimeMillis()))
            delay(1_000L)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        homeData,
        _permissions,
        clock
    ) { data, permissions, tick ->
        HomeUiState(
            policies = data.policies,
            activeQuickBlocks = data.quickBlocks.filter {
                it.isActive && tick.epochMillis >= it.startAt && tick.epochMillis < it.endAt
            },
            hasAccessibilityPermission = permissions.accessibility,
            hasUsageStatsPermission = permissions.usageStats,
            hasDailyLimits = data.hasDailyLimits,
            now = tick.localDateTime,
            nowEpochMillis = tick.epochMillis
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    init {
        refreshPermissions()
        viewModelScope.launch {
            quickBlockDao.expireOldSessions(System.currentTimeMillis())
        }
    }

    fun refreshPermissions() {
        val ctx = getApplication<Application>()
        _permissions.value = PermissionState(
            accessibility = PermissionChecker.isAccessibilityServiceEnabled(ctx),
            usageStats = PermissionChecker.hasUsageStatsPermission(ctx)
        )
    }

    fun enablePolicy(policy: PolicyEntity) {
        viewModelScope.launch {
            policyDao.update(policy.copy(enabled = true))
        }
    }

    suspend fun needsPasswordToDisable(): Boolean = passwordManager.hasPassword()

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

    fun startQuickBlock(durationMinutes: Int) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val endAt = now + durationMinutes * 60_000L
            quickBlockDao.expireOldSessions(now)
            quickBlockDao.cancelAllActive()
            quickBlockDao.insert(QuickBlockSessionEntity(startAt = now, endAt = endAt))
        }
    }

    fun cancelQuickBlock() {
        viewModelScope.launch {
            quickBlockDao.cancelAllActive()
        }
    }
}
