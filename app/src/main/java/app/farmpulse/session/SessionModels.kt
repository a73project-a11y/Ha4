package app.farmpulse.session

import app.farmpulse.data.AppPreferences
import app.farmpulse.data.ButtonBinding

enum class SessionPhase {
    IDLE,
    COUNTING,
    AWAITING_SEND,
}

enum class BindPhase {
    IDLE,
    WAITING_FOR_TRAVIAN_CLICK,
}

data class SessionUiState(
    val running: Boolean = false,
    val phase: SessionPhase = SessionPhase.IDLE,
    val bindPhase: BindPhase = BindPhase.IDLE,
    val intervalMinutes: Int = AppPreferences.DEFAULT_INTERVAL_MINUTES,
    val remainingMillis: Long = 0L,
    val dueAtMillis: Long = 0L,
    val lastStatus: String = "",
    val binding: ButtonBinding? = null,
    val lastSendOk: Boolean? = null,
) {
    val remainingLabel: String
        get() {
            val total = (remainingMillis.coerceAtLeast(0L) / 1000L).toInt()
            val m = total / 60
            val s = total % 60
            return "%d:%02d".format(m, s)
        }
}

sealed interface StartResult {
    data object Started : StartResult
    data object AccessibilityOff : StartResult
    data object NoBinding : StartResult
    data object DeviceLocked : StartResult
}

sealed interface SendResult {
    data object Sent : SendResult
    data object DeviceLocked : SendResult
    data object AccessibilityOff : SendResult
    data object NoBinding : SendResult
    data object RebindRequired : SendResult
}
