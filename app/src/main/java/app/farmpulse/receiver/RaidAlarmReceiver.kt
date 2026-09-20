package app.farmpulse.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.farmpulse.session.SessionController

/**
 * Exact-alarm fire for the raid interval. Vibrates and reveals Send.
 * Must never click Travian.
 */
class RaidAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RAID_DUE) return
        SessionController.onRaidDue(context.applicationContext)
    }

    companion object {
        const val ACTION_RAID_DUE = "app.farmpulse.action.RAID_DUE"
    }
}
