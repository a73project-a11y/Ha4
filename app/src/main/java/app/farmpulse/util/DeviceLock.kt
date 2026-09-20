package app.farmpulse.util

import android.app.KeyguardManager
import android.content.Context

/**
 * Farm sessions and Travian clicks require the device to be *logically*
 * unlocked: [KeyguardManager.isDeviceLocked] == false.
 *
 * The screen may be off. Smart Lock / Extend Unlock / swipe-only lock are OK.
 * A secure PIN/pattern/password keyguard that is still showing is NOT OK.
 * FarmPulse never dismisses a secure keyguard and never click-throughs it.
 */
object DeviceLock {
    fun isDeviceLocked(context: Context): Boolean {
        val km = context.getSystemService(KeyguardManager::class.java) ?: return true
        return km.isDeviceLocked
    }

    fun isLogicallyUnlocked(context: Context): Boolean = !isDeviceLocked(context)
}
