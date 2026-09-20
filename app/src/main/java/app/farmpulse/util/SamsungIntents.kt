package app.farmpulse.util

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Best-effort deep links for Samsung One UI battery and security screens.
 * The OEM does not guarantee these extras; every intent falls back
 * to app details or a generic settings page.
 */
object SamsungIntents {
    fun neverSleepingApps(context: Context): Intent {
        val candidates = listOf(
            Intent().setClassName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.BatteryActivity",
            ),
            Intent().setClassName(
                "com.samsung.android.sm_cn",
                "com.samsung.android.sm.battery.ui.BatteryActivity",
            ),
            Intent("com.samsung.android.sm.ACTION_BATTERY"),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
        return firstResolvable(context, candidates) ?: AppPermissions.appDetails(context)
    }

    fun autoBlocker(context: Context): Intent {
        val candidates = listOf(
            Intent("com.samsung.android.intent.action.AUTO_BLOCKER"),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
        )
        return firstResolvable(context, candidates) ?: AppPermissions.appDetails(context)
    }

    private fun firstResolvable(context: Context, intents: List<Intent>): Intent? {
        val pm = context.packageManager
        return intents.firstOrNull { it.resolveActivity(pm) != null }
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
