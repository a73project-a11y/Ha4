package app.farmpulse.session

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import app.farmpulse.data.AppPreferences
import app.farmpulse.data.ButtonBinding
import app.farmpulse.service.FarmPulseAccessibilityService
import app.farmpulse.service.FarmSessionService
import app.farmpulse.util.AppPermissions
import app.farmpulse.util.DeviceLock
import app.farmpulse.util.Haptics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Single source of session truth. The foreground service hosts the ticking
 * UI and AlarmManager. This object never clicks Travian on a timer fire —
 * only [onUserTappedSend] may ask the AccessibilityService to click.
 */
object SessionController {
    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    fun attach(context: Context) {
        appContext = context.applicationContext
        refreshFromDisk()
    }

    fun refreshFromDisk() {
        _state.update {
            it.copy(
                intervalMinutes = AppPreferences.intervalMinutes,
                lastStatus = AppPreferences.lastStatus,
                binding = AppPreferences.binding,
            )
        }
    }

    fun setIntervalMinutes(minutes: Int) {
        val value = minutes.coerceAtLeast(1)
        AppPreferences.intervalMinutes = value
        _state.update { current ->
            val nextDue = if (current.running && current.phase == SessionPhase.COUNTING) {
                System.currentTimeMillis() + value * 60_000L
            } else {
                current.dueAtMillis
            }
            current.copy(
                intervalMinutes = value,
                dueAtMillis = nextDue,
                remainingMillis = if (current.running && current.phase == SessionPhase.COUNTING) {
                    nextDue - System.currentTimeMillis()
                } else {
                    current.remainingMillis
                },
            )
        }
        if (_state.value.running && _state.value.phase == SessionPhase.COUNTING) {
            appContext?.let { FarmSessionService.reschedule(it) }
        }
    }

    fun startSession(context: Context): StartResult {
        refreshFromDisk()
        if (!AppPermissions.isAccessibilityEnabled(context)) {
            return StartResult.AccessibilityOff.also {
                setStatus("Turn on the FarmPulse accessibility service first.")
            }
        }
        if (!AppPreferences.hasBinding) {
            return StartResult.NoBinding.also {
                setStatus("Bind the Travian farmlist Send button first.")
            }
        }
        if (DeviceLock.isDeviceLocked(context)) {
            return StartResult.DeviceLocked.also {
                setStatus("Unlock the phone (PIN / swipe / Smart Lock) before starting a farm session.")
            }
        }
        val interval = AppPreferences.intervalMinutes
        val dueAt = System.currentTimeMillis() + interval * 60_000L
        _state.update {
            it.copy(
                running = true,
                phase = SessionPhase.COUNTING,
                intervalMinutes = interval,
                dueAtMillis = dueAt,
                remainingMillis = dueAt - System.currentTimeMillis(),
                lastSendOk = null,
            )
        }
        setStatus("Session started — ${interval} min interval. Timer will vibrate only; it will not send.")
        FarmSessionService.start(context.applicationContext)
        return StartResult.Started
    }

    fun stopSession(context: Context) {
        _state.update {
            it.copy(
                running = false,
                phase = SessionPhase.IDLE,
                remainingMillis = 0L,
                dueAtMillis = 0L,
                bindPhase = BindPhase.IDLE,
            )
        }
        FarmPulseAccessibilityService.instance?.setBindMode(false)
        FarmSessionService.stop(context.applicationContext)
        setStatus("Session stopped.")
    }

    /**
     * AlarmManager callback. Vibrates and reveals the big Send control.
     * Must never click Travian.
     */
    fun onRaidDue(context: Context) {
        if (!_state.value.running) return
        if (_state.value.phase == SessionPhase.AWAITING_SEND) return
        _state.update {
            it.copy(
                phase = SessionPhase.AWAITING_SEND,
                remainingMillis = 0L,
            )
        }
        Haptics.raidDue(context)
        setStatus("Interval elapsed — vibrate only. Tap Send on the overlay when you are ready.")
        FarmSessionService.notifyAwaitingSend(context.applicationContext)
    }

    fun onUserTappedSend(context: Context): SendResult {
        if (DeviceLock.isDeviceLocked(context)) {
            Haptics.error(context)
            setStatus("Device is locked. Unlock first — FarmPulse will not click through a secure keyguard.")
            return SendResult.DeviceLocked
        }
        val service = FarmPulseAccessibilityService.instance
        if (service == null || !AppPermissions.isAccessibilityEnabled(context)) {
            Haptics.error(context)
            setStatus("Accessibility service is off. Re-enable it, then try Send again.")
            return SendResult.AccessibilityOff
        }
        if (!AppPreferences.hasBinding) {
            Haptics.error(context)
            setStatus("No binding saved. Rebind the farmlist Send button.")
            return SendResult.NoBinding
        }
        val result = service.performBoundClick()
        return when (result) {
            SendResult.Sent -> {
                Haptics.confirm(context)
                beginNextInterval(context)
                setStatus("Send forwarded to Travian. Next interval armed.")
                result
            }
            SendResult.RebindRequired -> {
                Haptics.error(context)
                _state.update { it.copy(lastSendOk = false) }
                setStatus("Could not find the bound button. Rebind required.")
                result
            }
            else -> result
        }
    }

    fun beginBind(context: Context): Boolean {
        if (!AppPermissions.isAccessibilityEnabled(context)) {
            setStatus("Turn on Accessibility before binding.")
            return false
        }
        if (!AppPermissions.isTravianInstalled(context)) {
            setStatus("Travian Legends ($TRAVIAN_HINT) is not installed.")
            return false
        }
        _state.update { it.copy(bindPhase = BindPhase.WAITING_FOR_TRAVIAN_CLICK) }
        val service = FarmPulseAccessibilityService.instance
        if (service == null) {
            _state.update { it.copy(bindPhase = BindPhase.IDLE) }
            setStatus("Accessibility service is not connected. Toggle it off and on, then bind again.")
            return false
        }
        if (!service.setBindMode(true)) {
            _state.update { it.copy(bindPhase = BindPhase.IDLE) }
            setStatus("Could not show the bind target overlay. Turn Accessibility off and on, then try again.")
            return false
        }
        AppPermissions.launchTravian(context)
        setStatus("Bind mode: open the farmlist, drag the target onto Send, then tap Confirm bind. Travian may not expose buttons to Accessibility.")
        return true
    }

    fun cancelBind() {
        FarmPulseAccessibilityService.instance?.setBindMode(false)
        _state.update { it.copy(bindPhase = BindPhase.IDLE) }
        setStatus("Bind cancelled.")
    }

    fun onBindingCaptured(binding: ButtonBinding) {
        AppPreferences.binding = binding
        FarmPulseAccessibilityService.instance?.setBindMode(false)
        _state.update {
            it.copy(
                bindPhase = BindPhase.IDLE,
                binding = binding,
            )
        }
        appContext?.let { Haptics.confirm(it) }
        val how = if (binding.isCoordinate) {
            "Screen target saved at ${pct(binding.relativeX)}, ${pct(binding.relativeY)}."
        } else {
            "Accessibility node saved."
        }
        setStatus("$how You can start a session when the phone is unlocked.")
    }

    fun clearBinding() {
        AppPreferences.binding = null
        _state.update { it.copy(binding = null) }
        setStatus("Binding cleared.")
    }

    fun onTick(nowElapsedRealtime: Long = SystemClock.elapsedRealtime()) {
        val current = _state.value
        if (!current.running || current.phase != SessionPhase.COUNTING) return
        val remaining = (current.dueAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        _state.update { it.copy(remainingMillis = remaining) }
        if (remaining == 0L && current.phase == SessionPhase.COUNTING) {
            // AlarmManager is authoritative; this is a UI fallback if the alarm
            // is late. Still must not click Travian.
            appContext?.let { onRaidDue(it) }
        }
        @Suppress("UNUSED_VARIABLE")
        val unused = nowElapsedRealtime
    }

    fun setStatus(message: String) {
        AppPreferences.lastStatus = message
        _state.update { it.copy(lastStatus = message) }
    }

    private fun beginNextInterval(context: Context) {
        val interval = AppPreferences.intervalMinutes
        val dueAt = System.currentTimeMillis() + interval * 60_000L
        _state.update {
            it.copy(
                running = true,
                phase = SessionPhase.COUNTING,
                dueAtMillis = dueAt,
                remainingMillis = dueAt - System.currentTimeMillis(),
                lastSendOk = true,
            )
        }
        FarmSessionService.reschedule(context.applicationContext)
    }

    private fun pct(value: Float): String = "${(value * 100f).toInt()}%"

    private const val TRAVIAN_HINT = "com.traviangames.travianlegendsmobile"
}

fun FarmSessionService.Companion.start(context: Context) {
    val intent = Intent(context, FarmSessionService::class.java).setAction(FarmSessionService.ACTION_START)
    context.startForegroundService(intent)
}

fun FarmSessionService.Companion.stop(context: Context) {
    val intent = Intent(context, FarmSessionService::class.java).setAction(FarmSessionService.ACTION_STOP)
    context.startService(intent)
}

fun FarmSessionService.Companion.reschedule(context: Context) {
    val intent = Intent(context, FarmSessionService::class.java).setAction(FarmSessionService.ACTION_RESCHEDULE)
    context.startService(intent)
}

fun FarmSessionService.Companion.notifyAwaitingSend(context: Context) {
    val intent = Intent(context, FarmSessionService::class.java).setAction(FarmSessionService.ACTION_AWAITING)
    context.startService(intent)
}
