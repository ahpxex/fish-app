package com.fish.wellness.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.PixelFormat
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView
import com.fish.wellness.MainActivity
import com.fish.wellness.R
import com.fish.wellness.domain.blocking.BlockReason
import com.fish.wellness.domain.blocking.BlockingPolicyEvaluator
import com.fish.wellness.domain.blocking.PreliminaryDecision
import com.fish.wellness.manager.BlockingPolicyStore
import com.fish.wellness.util.AppUtils
import com.fish.wellness.util.UsageTracker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.max

@AndroidEntryPoint
class AppBlockAccessibilityService : AccessibilityService() {

    @Inject lateinit var policyStore: BlockingPolicyStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val keyguardManager by lazy { getSystemService(KEYGUARD_SERVICE) as KeyguardManager }
    private val powerManager by lazy { getSystemService(POWER_SERVICE) as PowerManager }

    private var foregroundPackage: String? = null
    private var evaluationJob: Job? = null
    private var usageJob: Job? = null
    private var usagePackage: String? = null
    private var usageLimitMinutes: Int? = null
    private var lastDecisionLog: String? = null

    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var fallbackPackage: String? = null

    private var countdownView: View? = null
    private var countdownParams: WindowManager.LayoutParams? = null

    private val usageFloors = ConcurrentHashMap<String, UsageFloor>()
    private val exhaustedLimits = ConcurrentHashMap<UsageLimitKey, Boolean>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility enforcement connected")

        scope.launch {
            policyStore.observeRules().collectLatest {
                withContext(Dispatchers.Main.immediate) {
                    foregroundPackage?.let(::scheduleEnforcement)
                }
            }
        }

        scope.launch {
            while (isActive) {
                withContext(Dispatchers.Main.immediate) {
                    if (!shouldEnforceNow()) {
                        clearEnforcement()
                    } else {
                        handleDetectedPackage(rootInActiveWindow?.packageName?.toString())
                    }
                }
                delay(REEVALUATE_INTERVAL_MS)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }
        if (!shouldEnforceNow()) {
            clearEnforcement()
            return
        }

        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                event.className?.toString() == MainActivity::class.java.name
            ) {
                clearEnforcement()
            }
            return
        }
        handleDetectedPackage(packageName)
    }

    private fun shouldEnforceNow(): Boolean =
        powerManager.isInteractive && !keyguardManager.isKeyguardLocked

    private fun handleDetectedPackage(packageName: String?) {
        when {
            packageName.isNullOrBlank() -> foregroundPackage?.let(::scheduleEnforcement)
            packageName == this.packageName && overlayView?.parent != null ->
                foregroundPackage?.let(::scheduleEnforcement)
            packageName == this.packageName -> clearEnforcement()
            packageName == SYSTEM_UI_PACKAGE -> foregroundPackage?.let(::scheduleEnforcement)
            packageName == currentInputMethodPackage() -> foregroundPackage?.let(::scheduleEnforcement)
            else -> handleForegroundPackage(packageName)
        }
    }

    private fun currentInputMethodPackage(): String? = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.DEFAULT_INPUT_METHOD
    )?.substringBefore('/')

    private fun clearEnforcement() {
        if (foregroundPackage != null) {
            Log.i(TAG, "Enforcement paused")
        }
        foregroundPackage = null
        lastDecisionLog = null
        evaluationJob?.cancel()
        evaluationJob = null
        stopUsageTracking()
        hideOverlayWindow()
    }

    private fun handleForegroundPackage(packageName: String) {
        if (foregroundPackage != packageName) {
            Log.i(TAG, "Foreground package: $packageName")
            foregroundPackage = packageName
            lastDecisionLog = null
            evaluationJob?.cancel()
            evaluationJob = null
            stopUsageTracking()
        }
        scheduleEnforcement(packageName)
    }

    private fun scheduleEnforcement(packageName: String) {
        policyStore.cachedDecision(packageName)?.let { decision ->
            evaluationJob?.cancel()
            evaluationJob = null
            if (foregroundPackage == packageName) {
                applyDecision(packageName, decision)
            }
            return
        }
        if (evaluationJob?.isActive == true) return
        evaluationJob = scope.launch {
            val decision = policyStore.awaitDecision(packageName)
            withContext(Dispatchers.Main.immediate) {
                if (foregroundPackage == packageName) {
                    applyDecision(packageName, decision)
                }
            }
        }
    }

    private fun applyDecision(packageName: String, decision: PreliminaryDecision) {
        val decisionLog = "$packageName: $decision"
        if (lastDecisionLog != decisionLog) {
            Log.i(TAG, "Decision: $decisionLog")
            lastDecisionLog = decisionLog
        }
        when (decision) {
            PreliminaryDecision.Allowed -> {
                stopUsageTracking()
                hideOverlayWindow()
            }

            is PreliminaryDecision.Blocked -> {
                stopUsageTracking()
                showOverlayWindow(packageName, decision.reason, decision.policyName)
            }

            is PreliminaryDecision.CheckDailyLimit -> {
                ensureUsageTracking(packageName, decision.limitMinutes)
            }
        }
    }

    private fun ensureUsageTracking(packageName: String, limitMinutes: Int) {
        val key = UsageLimitKey(packageName, limitMinutes, LocalDate.now())
        exhaustedLimits.keys.removeIf { it.day != key.day }
        if (exhaustedLimits.containsKey(key)) {
            stopUsageTracking()
            showOverlayWindow(packageName, BlockReason.DAILY_LIMIT)
            return
        }
        hideOverlayWindow()
        if (usagePackage == packageName && usageLimitMinutes == limitMinutes && usageJob?.isActive == true) {
            return
        }

        stopUsageTracking()
        usagePackage = packageName
        usageLimitMinutes = limitMinutes
        usageJob = scope.launch {
            val today = LocalDate.now()
            val floorAtStart = usageFloors[packageName]
                ?.takeIf { it.day == today }
                ?.usedMillis
                ?: 0L
            val observedAtStart = UsageTracker.getForegroundMillisToday(
                this@AppBlockAccessibilityService,
                packageName
            )
            val baselineMillis = max(floorAtStart, observedAtStart)
            val startedAtElapsed = SystemClock.elapsedRealtime()
            var ticks = 0

            while (isActive && foregroundPackage == packageName) {
                val elapsed = SystemClock.elapsedRealtime() - startedAtElapsed
                var usedMillis = baselineMillis + elapsed
                if (ticks % USAGE_RESYNC_TICKS == 0) {
                    usedMillis = max(
                        usedMillis,
                        UsageTracker.getForegroundMillisToday(
                            this@AppBlockAccessibilityService,
                            packageName
                        )
                    )
                }
                usageFloors.compute(packageName) { _, current ->
                    val currentMillis = current?.takeIf { it.day == today }?.usedMillis ?: 0L
                    UsageFloor(today, max(currentMillis, usedMillis))
                }

                val result = BlockingPolicyEvaluator.resolveDailyLimit(limitMinutes, usedMillis)
                if (result.isBlocked) {
                    exhaustedLimits[key] = true
                    withContext(Dispatchers.Main.immediate) {
                        if (foregroundPackage == packageName && usageLimitMinutes == limitMinutes) {
                            hideCountdown()
                            showOverlayWindow(packageName, BlockReason.DAILY_LIMIT)
                        }
                    }
                    return@launch
                }

                withContext(Dispatchers.Main.immediate) {
                    if (foregroundPackage == packageName && usageLimitMinutes == limitMinutes) {
                        showCountdown(result.remainingMillis ?: 0L)
                    }
                }
                ticks += 1
                delay(COUNTDOWN_TICK_MS)
            }
        }
    }

    private fun stopUsageTracking() {
        usageJob?.cancel()
        usageJob = null
        usagePackage = null
        usageLimitMinutes = null
        hideCountdown()
    }

    private fun showOverlayWindow(
        packageName: String,
        reason: BlockReason,
        policyName: String? = null
    ) {
        if (overlayView == null) {
            overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_block, null).apply {
                findViewById<Button>(R.id.goHomeButton).setOnClickListener {
                    hideOverlayWindow()
                    val handled = performGlobalAction(GLOBAL_ACTION_HOME)
                    Log.i(TAG, "Go home requested: handled=$handled")
                }
            }
            overlayParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
        }

        overlayView?.findViewById<TextView>(R.id.blockedAppName)?.text =
            AppUtils.getAppName(this, packageName)
        overlayView?.findViewById<TextView>(R.id.blockReason)?.text = when (reason) {
            BlockReason.QUICK_BLOCK -> "Quick Block is active."
            BlockReason.SCHEDULE -> policyName
                ?.takeIf { it.isNotBlank() }
                ?.let { "Restricted by $it." }
                ?: "Restricted by an active policy."
            BlockReason.DAILY_LIMIT -> "Today's app limit has been reached."
        }

        try {
            if (overlayView?.parent == null) {
                windowManager.addView(overlayView, overlayParams)
            }
            fallbackPackage = null
        } catch (error: Exception) {
            Log.e(TAG, "Unable to attach accessibility overlay", error)
            showFallbackActivity(packageName, reason)
        }
    }

    private fun hideOverlayWindow() {
        fallbackPackage = null
        val view = overlayView ?: return
        if (view.parent != null) {
            try {
                windowManager.removeView(view)
            } catch (error: IllegalArgumentException) {
                Log.w(TAG, "Overlay was already detached", error)
            }
        }
    }

    private fun showCountdown(remainingMillis: Long) {
        if (countdownView == null) {
            countdownView = LayoutInflater.from(this).inflate(R.layout.overlay_countdown, null)
            countdownParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (48 * resources.displayMetrics.density).toInt()
            }
        }
        countdownView?.findViewById<TextView>(R.id.countdownText)?.text =
            formatRemaining(remainingMillis)
        try {
            if (countdownView?.parent == null) {
                windowManager.addView(countdownView, countdownParams)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to attach usage countdown", error)
        }
    }

    private fun hideCountdown() {
        val view = countdownView ?: return
        if (view.parent != null) {
            try {
                windowManager.removeView(view)
            } catch (error: IllegalArgumentException) {
                Log.w(TAG, "Countdown was already detached", error)
            }
        }
    }

    private fun showFallbackActivity(packageName: String, reason: BlockReason) {
        if (fallbackPackage == packageName) return
        fallbackPackage = packageName
        startActivity(Intent(this, BlockOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra(BlockOverlayActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(BlockOverlayActivity.EXTRA_BLOCK_REASON, reason.name)
        })
    }

    private fun formatRemaining(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1000).toInt().coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        evaluationJob?.cancel()
        stopUsageTracking()
        hideOverlayWindow()
        scope.cancel()
        super.onDestroy()
    }

    private data class UsageFloor(val day: LocalDate, val usedMillis: Long)
    private data class UsageLimitKey(
        val packageName: String,
        val limitMinutes: Int,
        val day: LocalDate
    )

    companion object {
        private const val TAG = "FishBlockService"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val REEVALUATE_INTERVAL_MS = 500L
        private const val COUNTDOWN_TICK_MS = 1000L
        private const val USAGE_RESYNC_TICKS = 15
    }
}
