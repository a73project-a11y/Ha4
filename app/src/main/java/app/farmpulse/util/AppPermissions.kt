package app.farmpulse.util

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import app.farmpulse.Travian
import app.farmpulse.service.FarmPulseAccessibilityService

data class PermissionSnapshot(
    val accessibilityEnabled: Boolean,
    val overlayEnabled: Boolean,
    val notificationsEnabled: Boolean,
    val exactAlarmEnabled: Boolean,
    val batteryUnrestricted: Boolean,
    val travianInstalled: Boolean,
    val deviceLocked: Boolean,
)

object AppPermissions {
    fun snapshot(context: Context): PermissionSnapshot {
        return PermissionSnapshot(
            accessibilityEnabled = isAccessibilityEnabled(context),
            overlayEnabled = Settings.canDrawOverlays(context),
            notificationsEnabled = areNotificationsEnabled(context),
            exactAlarmEnabled = canScheduleExactAlarms(context),
            batteryUnrestricted = isIgnoringBatteryOptimizations(context),
            travianInstalled = isTravianInstalled(context),
            deviceLocked = DeviceLock.isDeviceLocked(context),
        )
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val am = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val expected = ComponentName(context, FarmPulseAccessibilityService::class.java)
            .flattenToString()
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        if (enabled.any { info ->
                info.resolveInfo?.serviceInfo?.let { si ->
                    ComponentName(si.packageName, si.name).flattenToString()
                } == expected
            }
        ) {
            return true
        }
        val enabledSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabledSetting.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        return nm?.areNotificationsEnabled() != false
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isTravianInstalled(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(Travian.PACKAGE_NAME, 0)
            true
        }.getOrDefault(false)
    }

    fun launchTravian(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(Travian.PACKAGE_NAME)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun accessibilitySettings(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun overlaySettings(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun notificationSettings(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            appDetails(context)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun exactAlarmSettings(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            appDetails(context)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun batteryOptimizationSettings(context: Context): Intent {
        val ignore = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
        return if (ignore.resolveActivity(context.packageManager) != null) {
            ignore.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            appDetails(context)
        }
    }

    fun appDetails(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
