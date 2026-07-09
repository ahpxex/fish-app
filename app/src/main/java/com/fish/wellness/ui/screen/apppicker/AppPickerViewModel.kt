package com.fish.wellness.ui.screen.apppicker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fish.wellness.data.dao.BlockedAppDao
import com.fish.wellness.data.entity.BlockedAppEntity
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
    val blockedPackages: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isLoading: Boolean = true
) {
    val filteredApps: List<InstalledAppInfo>
        get() = if (searchQuery.isBlank()) allApps
        else allApps.filter { it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
}

@HiltViewModel
class AppPickerViewModel @Inject constructor(
    private val app: Application,
    private val blockedAppDao: BlockedAppDao
) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(AppPickerUiState())
    val uiState: StateFlow<AppPickerUiState> = _uiState.asStateFlow()

    init { loadApps() }

    private fun loadApps() {
        viewModelScope.launch {
            val blocked = blockedAppDao.getAllPackageNames().toHashSet()
            val apps = withContext(Dispatchers.IO) {
                AppUtils.getInstalledApps(app).map { it.copy(isBlocked = it.packageName in blocked) }
            }
            _uiState.value = AppPickerUiState(
                allApps = apps,
                blockedPackages = blocked,
                isLoading = false
            )
        }
    }

    fun updateSearch(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleBlock(app: InstalledAppInfo) {
        viewModelScope.launch {
            if (app.packageName in _uiState.value.blockedPackages) {
                blockedAppDao.delete(app.packageName)
            } else {
                blockedAppDao.upsert(
                    BlockedAppEntity(packageName = app.packageName, appName = app.appName)
                )
            }
            val newBlocked = blockedAppDao.getAllPackageNames().toHashSet()
            _uiState.value = _uiState.value.copy(
                blockedPackages = newBlocked,
                allApps = _uiState.value.allApps.map { it.copy(isBlocked = it.packageName in newBlocked) }
            )
        }
    }
}
