package com.fish.wellness.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fish.wellness.service.ScheduleForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ScheduleForegroundService.start(context)
        }
    }
}
