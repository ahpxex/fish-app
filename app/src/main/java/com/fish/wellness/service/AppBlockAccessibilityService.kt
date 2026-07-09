package com.fish.wellness.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import com.fish.wellness.manager.AppBlockManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AppBlockAccessibilityService : AccessibilityService() {

    @Inject lateinit var blockManager: AppBlockManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null
    private var lastCheckedPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        configureAccessibility()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == "com.android.systemui") return
        if (packageName == lastCheckedPackage) return
        lastCheckedPackage = packageName

        pollJob?.cancel()

        scope.launch {
            if (blockManager.shouldShowBlockOverlay(packageName)) {
                showBlockOverlay(packageName)
            } else if (blockManager.isTimeLimited(packageName)) {
                startUsagePolling(packageName)
            }
        }
    }

    private fun startUsagePolling(packageName: String) {
        pollJob = scope.launch {
            while (isActive) {
                delay(15_000)
                if (blockManager.shouldShowBlockOverlay(packageName)) {
                    showBlockOverlay(packageName)
                    break
                }
            }
        }
    }

    private fun showBlockOverlay(packageName: String) {
        val intent = android.content.Intent(this, BlockOverlayActivity::class.java).apply {
            addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or
                android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra(BlockOverlayActivity.EXTRA_PACKAGE_NAME, packageName)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {}

    private fun configureAccessibility() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 200
        }
        serviceInfo = info
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
