package com.fish.wellness.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.fish.wellness.model.InstalledAppInfo

object AppUtils {

    fun getInstalledApps(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val ownPackage = context.packageName
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps
            .filter {
                it.packageName != ownPackage &&
                it.packageName != "android" &&
                pm.getLaunchIntentForPackage(it.packageName) != null
            }
            .map {
                val isSystem = it.flags and ApplicationInfo.FLAG_SYSTEM != 0
                InstalledAppInfo(
                    packageName = it.packageName,
                    appName = pm.getApplicationLabel(it).toString(),
                    isSystemApp = isSystem
                )
            }
            .sortedBy { it.appName.lowercase() }
    }

    fun getAppIcon(context: Context, packageName: String): Drawable? = try {
        context.packageManager.getApplicationIcon(packageName)
    } catch (_: Exception) { null }

    fun getAppName(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (_: Exception) { packageName }

    fun goToHome(context: Context) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

}
