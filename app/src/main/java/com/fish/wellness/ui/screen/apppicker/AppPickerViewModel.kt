package com.fish.wellness.ui.screen.apppicker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.fish.wellness.data.dao.BlockedAppDao
import com.fish.wellness.data.entity.BlockedAppEntity
import com.fish.wellness.manager.AppBlockManager
import com.fish.wellness.model.InstalledAppInfo
import com.fish.wellness.util.AppUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AppPickerUiState(
    val allApps: List<InstalledAppInfo> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true
) {
    val selectedApps: List<InstalledAppInfo> get() = allApps.filter { it.isBlocked }
    val filteredApps: List<InstalledAppInfo>
        get() = if (searchQuery.isBlank()) allApps
        else allApps.filter { it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
}

@HiltViewModel
class AppPickerViewModel @Inject constructor(
    private val app: Application,
    savedStateHandle: SavedStateHandle,
    private val blockedAppDao: BlockedAppDao,
    private val blockManager: AppBlockManager
) : AndroidViewModel(app) {

    val policyId: Long = savedStateHandle.get<Long>("policyId") ?: 0L

    private val _uiState = MutableStateFlow(AppPickerUiState())
    val uiState: StateFlow<AppPickerUiState> = _uiState.asStateFlow()

    init { loadApps() }

    private fun loadApps() {
        viewModelScope.launch {
            val blocked = blockedAppDao.getByPolicy(policyId).associateBy { it.packageName }
            val apps = withContext(Dispatchers.IO) {
                AppUtils.getInstalledApps(app).map { installed ->
                    val block = blocked[installed.packageName]
                    installed.copy(
                        isBlocked = block != null,
                        dailyLimitMinutes = block?.dailyLimitMinutes ?: 0
                    )
                }
            }
            _uiState.value = AppPickerUiState(allApps = apps, isLoading = false)
        }
    }

    fun updateSearch(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleBlock(appInfo: InstalledAppInfo) {
        viewModelScope.launch {
            if (appInfo.isBlocked) {
                blockedAppDao.delete(policyId, appInfo.packageName)
                updateAppList(appInfo.packageName, isBlocked = false, dailyLimitMinutes = 0)
            } else {
                blockedAppDao.insert(
                    BlockedAppEntity(
                        policyId = policyId,
                        packageName = appInfo.packageName,
                        appName = appInfo.appName,
                        dailyLimitMinutes = 0
                    )
                )
                updateAppList(appInfo.packageName, isBlocked = true, dailyLimitMinutes = 0)
            }
            blockManager.invalidate()
        }
    }

    fun updateLimit(packageName: String, minutes: Int) {
        viewModelScope.launch {
            val appInfo = _uiState.value.allApps.find { it.packageName == packageName } ?: return@launch
            blockedAppDao.delete(policyId, packageName)
            blockedAppDao.insert(
                BlockedAppEntity(
                    policyId = policyId,
                    packageName = packageName,
                    appName = appInfo.appName,
                    dailyLimitMinutes = minutes
                )
            )
            updateAppList(packageName, isBlocked = true, dailyLimitMinutes = minutes)
            blockManager.invalidate()
        }
    }

    private fun updateAppList(packageName: String, isBlocked: Boolean, dailyLimitMinutes: Int) {
        _uiState.value = _uiState.value.copy(
            allApps = _uiState.value.allApps.map {
                if (it.packageName == packageName) it.copy(isBlocked = isBlocked, dailyLimitMinutes = dailyLimitMinutes)
                else it
            }
        )
    }
}
