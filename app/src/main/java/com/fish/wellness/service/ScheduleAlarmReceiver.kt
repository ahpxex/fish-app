package com.fish.wellness.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fish.wellness.data.dao.QuickBlockSessionDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ScheduleAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var quickBlockDao: QuickBlockSessionDao

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                when (intent.action) {
                    ACTION_QUICK_BLOCK_EXPIRE -> {
                        quickBlockDao.expireOldSessions(System.currentTimeMillis())
                    }
                }
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    companion object {
        const val ACTION_QUICK_BLOCK_EXPIRE = "com.fish.wellness.QUICK_BLOCK_EXPIRE"
        const val EXTRA_SESSION_END = "session_end"

        fun scheduleQuickBlockExpiry(context: Context, endAt: Long) {
            val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                action = ACTION_QUICK_BLOCK_EXPIRE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                endAt.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        endAt,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        endAt,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    endAt,
                    pendingIntent
                )
            }
        }
    }
}
