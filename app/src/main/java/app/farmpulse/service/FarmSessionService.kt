package app.farmpulse.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import app.farmpulse.MainActivity
import app.farmpulse.R
import app.farmpulse.ShowWhenLockedActivity
import app.farmpulse.receiver.RaidAlarmReceiver
import app.farmpulse.session.BindPhase
import app.farmpulse.session.SessionController
import app.farmpulse.session.SessionPhase
import app.farmpulse.util.DeviceLock

/**
 * specialUse foreground service: session host for the raid interval.
 *
 * Why specialUse (not dataSync / mediaPlayback / remoteMessaging):
 * FarmPulse keeps a user-visible farm session alive — a silent chronometer
 * notification, overlay countdown ticks, and AlarmManager scheduling.
 * It is not syncing data, playing media, or messaging. It never auto-clicks
 * Travian and it never talks to Travian servers. WorkManager is the wrong
 * tool for an exact user-facing raid interval.
 */
class FarmSessionService : LifecycleService() {

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            SessionController.onTick()
            refreshUi()
            handler.postDelayed(this, 1_000L)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var overlay: OverlayController? = null
    private var lockReceiverRegistered = false

    private val lockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshUi()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        promoteForeground()
        acquireWakeLock()
        registerLockReceiver()
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                cancelAlarm()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESCHEDULE -> scheduleAlarm()
            ACTION_AWAITING -> {
                maybeShowWhenLocked()
            }
            ACTION_START -> scheduleAlarm()
            else -> if (SessionController.state.value.running) scheduleAlarm()
        }
        promoteForeground()
        refreshUi()
        return START_STICKY
    }

    private fun promoteForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        cancelAlarm()
        overlay?.dismiss()
        overlay = null
        FarmPulseAccessibilityService.instance?.overlay()?.dismiss()
        releaseWakeLock()
        unregisterLockReceiver()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun refreshUi() {
        val state = SessionController.state.value
        if (!state.running || state.bindPhase != BindPhase.IDLE) {
            overlay?.dismiss()
            FarmPulseAccessibilityService.instance?.overlay()?.dismiss()
            return
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())

        val unlocked = DeviceLock.isLogicallyUnlocked(this)
        if (!unlocked) {
            overlay?.dismiss()
            FarmPulseAccessibilityService.instance?.overlay()?.dismiss()
            return
        }

        val a11yOverlay = FarmPulseAccessibilityService.instance?.overlay()
        if (a11yOverlay != null) {
            overlay?.dismiss()
            a11yOverlay.render(state)
        } else if (Settings.canDrawOverlays(this)) {
            ensureWindowOverlay().render(state)
        }
    }

    private fun ensureWindowOverlay(): OverlayController {
        overlay?.let { return it }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val created = OverlayController(
            host = this,
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager,
            windowType = type,
            format = PixelFormat.TRANSLUCENT,
        )
        overlay = created
        return created
    }

    private fun scheduleAlarm() {
        val state = SessionController.state.value
        if (!state.running || state.phase != SessionPhase.COUNTING || state.dueAtMillis <= 0L) {
            return
        }
        val am = getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmPending()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, state.dueAtMillis, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, state.dueAtMillis, pi)
        }
    }

    private fun cancelAlarm() {
        val am = getSystemService(AlarmManager::class.java) ?: return
        am.cancel(alarmPending())
    }

    private fun alarmPending(): PendingIntent {
        val intent = Intent(this, RaidAlarmReceiver::class.java).setAction(RaidAlarmReceiver.ACTION_RAID_DUE)
        return PendingIntent.getBroadcast(
            this,
            REQ_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun maybeShowWhenLocked() {
        val launch = Intent(this, ShowWhenLockedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { startActivity(launch) }
    }

    private fun buildNotification(): Notification {
        val state = SessionController.state.value
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, FarmSessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val awaiting = state.phase == SessionPhase.AWAITING_SEND
        val title = if (awaiting) "Tap Send on the overlay" else "Farm session running"
        val text = if (awaiting) {
            "Timer will not click Travian. Unlock if needed, then tap Send."
        } else {
            "Next pulse in ${state.remainingLabel} · vibrate only, never auto-send"
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setSound(null)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, "Stop", stop)
        if (state.running && state.dueAtMillis > 0L && !awaiting) {
            builder.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(state.dueAtMillis)
        }
        return builder.build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "farmpulse:session").apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun registerLockReceiver() {
        if (lockReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(lockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(lockReceiver, filter)
        }
        lockReceiverRegistered = true
    }

    private fun unregisterLockReceiver() {
        if (!lockReceiverRegistered) return
        runCatching { unregisterReceiver(lockReceiver) }
        lockReceiverRegistered = false
    }

    companion object {
        const val ACTION_START = "app.farmpulse.action.START_SESSION"
        const val ACTION_STOP = "app.farmpulse.action.STOP_SESSION"
        const val ACTION_RESCHEDULE = "app.farmpulse.action.RESCHEDULE"
        const val ACTION_AWAITING = "app.farmpulse.action.AWAITING_SEND"
        const val CHANNEL_ID = "farm_session"
        const val NOTIFICATION_ID = 47
        private const val REQ_ALARM = 710
    }
}
