package app.farmpulse.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.farmpulse.session.SessionController

/**
 * Sessions do not survive reboot. Clear running flags so we never silently
 * resume a farm loop after boot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        SessionController.stopSession(context.applicationContext)
        SessionController.setStatus("Phone rebooted — start a new farm session when you are unlocked.")
    }
}
