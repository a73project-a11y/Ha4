package app.farmpulse.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Haptics {
    /** Raid-due cue: VibrationEffect only. Never plays a sound. */
    fun raidDue(context: Context) {
        vibrate(context, VibrationEffect.createWaveform(longArrayOf(0, 180, 90, 180, 90, 320), -1))
    }

    fun confirm(context: Context) {
        vibrate(context, VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun error(context: Context) {
        vibrate(context, VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun vibrate(context: Context, effect: VibrationEffect) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(effect)
    }
}
