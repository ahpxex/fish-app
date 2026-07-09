package com.fish.wellness.model

import kotlinx.serialization.Serializable

@Serializable
data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false,
    val isBlocked: Boolean = false,
    val dailyLimitMinutes: Int = 0
)
