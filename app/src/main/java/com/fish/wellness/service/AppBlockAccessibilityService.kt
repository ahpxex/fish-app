package com.fish.wellness.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView
import com.fish.wellness.R
import com.fish.wellness.manager.AppBlockManager
import com.fish.wellness.util.AppUtils
import com.fish.wellness.util.UsageTracker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.max

@AndroidEntryPoint
class AppBlockAccessibilityService : AccessibilityService() {

    @Inject lateinit var blockManager: AppBlockManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refreshJob: Job? = null
    private var dismissJob: Job? = null
    private var limitJob: Job? = null
    private var lastCheckedPackage: String? = null

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    // ---- full-screen block overlay ----
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    // ---- small floating countdown chip ----
    private var countdownView: View? = null
    private var countdownParams: WindowManager.LayoutParams? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Accessibility config (event types, notificationTimeout=0, canRetrieveWindowContent,
        // flagRetrieveInteractiveWindows) is set via res/xml/accessibility_service_config.xml.
        scope.launch { blockManager.refreshBlockedSnapshot() }
        refreshJob = scope.launch {
            while (isActive) {
                delay(SNAPSHOT_REFRESH_MS)
                blockManager.refreshBlockedSnapshot()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == "com.android.systemui") return
        if (packageName == this.packageName) return
        if (packageName == lastCheckedPackage) return
        lastCheckedPackage = packageName

        // FAST PATH: full block / quick block -> cover instantly
        if (blockManager.isInstantlyBlocked(packageName)) {
            hideCountdown()
            showOverlayWindow(packageName)
            return
        }

        // otherwise tear down any block overlay + countdown
        hideOverlayWindow()
        stopLimitTracking()

        scope.launch {
            if (blockManager.isTimeLimited(packageName)) {
                startLimitTracking(packageName)
            }
        }
    }

    // ---- daily-limit tracking: immediate check + live countdown ----

    private fun startLimitTracking(pkg: String) {
        limitJob?.cancel()
        limitJob = scope.launch {
            val limitMinutes = blockManager.getDailyLimitMinutes(pkg) ?: return@launch

            // Immediate check: if today's usage already meets/exceeds the limit, block right away.
            if (blockManager.isAppBlocked(pkg)) {
                withContext(Dispatchers.Main) { showOverlayWindow(pkg) }
                return@launch
            }

            val limitMs = limitMinutes * 60_000L
            val baselineMs = UsageTracker.getForegroundMillisToday(this@AppBlockAccessibilityService, pkg)
            var remainingMs = max(0L, limitMs - baselineMs)
            if (remainingMs <= 0) {
                withContext(Dispatchers.Main) { showOverlayWindow(pkg) }
                return@launch
            }

            withContext(Dispatchers.Main) { showCountdown(remainingMs) }

            while (isActive) {
                delay(COUNTDOWN_TICK_MS)
                remainingMs -= COUNTDOWN_TICK_MS
                if (remainingMs <= 0) {
                    withContext(Dispatchers.Main) {
                        hideCountdown()
                        showOverlayWindow(pkg)
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) { updateCountdown(remainingMs) }
            }
        }
    }

    private fun stopLimitTracking() {
        limitJob?.cancel()
        limitJob = null
        hideCountdown()
    }

    // ---- full-screen block overlay (WindowManager, near-instant) ----

    private fun showOverlayWindow(packageName: String) {
        if (!Settings.canDrawOverlays(this)) {
            showBlockOverlayActivity(packageName)
            return
        }

        if (overlayView == null) {
            overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_block, null).apply {
                findViewById<Button>(R.id.goHomeButton).setOnClickListener {
                    hideOverlayWindow()
                    AppUtils.goToHome(this@AppBlockAccessibilityService)
                }
            }
            overlayParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
        }

        overlayView?.findViewById<TextView>(R.id.blockedAppName)?.text =
            AppUtils.getAppName(this, packageName)

        try {
            if (overlayView?.parent == null) {
                windowManager.addView(overlayView, overlayParams)
            }
            startDismissWatch(packageName)
        } catch (_: Exception) {
            showBlockOverlayActivity(packageName)
        }
    }

    // Watch the real foreground while the block overlay is up; dismiss the instant the user
    // swipes away (home/recents) instead of waiting for a lagging window-state event.
    private fun startDismissWatch(blockedPackage: String) {
        dismissJob?.cancel()
        dismissJob = scope.launch {
            while (isActive) {
                delay(DISMISS_POLL_MS)
                val active = withContext(Dispatchers.Main) {
                    rootInActiveWindow?.packageName?.toString()
                }
                if (active != null &&
                    active != blockedPackage &&
                    active != this@AppBlockAccessibilityService.packageName
                ) {
                    withContext(Dispatchers.Main) { hideOverlayWindow() }
                    break
                }
            }
        }
    }

    private fun hideOverlayWindow() {
        dismissJob?.cancel()
        dismissJob = null
        val view = overlayView ?: return
        if (view.parent != null) {
            try {
                windowManager.removeView(view)
            } catch (_: Exception) { /* already detached */ }
        }
    }

    // ---- floating countdown chip ----

    private fun showCountdown(remainingMs: Long) {
        if (!Settings.canDrawOverlays(this)) return

        if (countdownView == null) {
            countdownView = LayoutInflater.from(this).inflate(R.layout.overlay_countdown, null)
            countdownParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 48 // px below status bar; ~status-bar height
            }
        }
        updateCountdown(remainingMs)
        try {
            if (countdownView?.parent == null) {
                windowManager.addView(countdownView, countdownParams)
            }
        } catch (_: Exception) { /* overlay not permitted */ }
    }

    private fun updateCountdown(remainingMs: Long) {
        countdownView?.findViewById<TextView>(R.id.countdownText)?.text = formatRemaining(remainingMs)
    }

    private fun hideCountdown() {
        val view = countdownView ?: return
        if (view.parent != null) {
            try {
                windowManager.removeView(view)
            } catch (_: Exception) { /* already detached */ }
        }
    }

    private fun formatRemaining(ms: Long): String {
        val totalSeconds = (ms / 1000).toInt().coerceAtLeast(0)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    // ---- fallback: blocking Activity (when overlay permission missing) ----

    private fun showBlockOverlayActivity(packageName: String) {
        val intent = Intent(this, BlockOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra(BlockOverlayActivity.EXTRA_PACKAGE_NAME, packageName)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        hideOverlayWindow()
        hideCountdown()
        scope.cancel()
    }

    companion object {
        private const val SNAPSHOT_REFRESH_MS = 30_000L
        private const val DISMISS_POLL_MS = 80L
        private const val COUNTDOWN_TICK_MS = 1000L
    }
}
