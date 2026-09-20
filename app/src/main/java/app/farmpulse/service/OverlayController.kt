package app.farmpulse.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.core.view.setPadding
import app.farmpulse.session.SessionController
import app.farmpulse.session.SessionPhase
import app.farmpulse.session.SessionUiState
import app.farmpulse.util.DeviceLock

/**
 * Floating countdown + big Send. Uses TYPE_APPLICATION_OVERLAY or
 * TYPE_ACCESSIBILITY_OVERLAY depending on the host. Hidden while the
 * device is logically locked — we never click through a secure keyguard.
 */
class OverlayController(
    private val host: Context,
    private val windowManager: WindowManager,
    private val windowType: Int,
    private val extraFlags: Int = 0,
    private val format: Int = PixelFormat.TRANSLUCENT,
) {
    private var root: FrameLayout? = null
    private var countdown: TextView? = null
    private var caption: TextView? = null
    private var send: Button? = null

    val isShowing: Boolean get() = root != null

    fun render(state: SessionUiState) {
        if (!state.running || DeviceLock.isDeviceLocked(host)) {
            dismiss()
            return
        }
        ensureAttached()
        val awaiting = state.phase == SessionPhase.AWAITING_SEND
        countdown?.text = if (awaiting) "0:00" else state.remainingLabel
        caption?.text = if (awaiting) {
            "Tap Send — the timer will not click Travian for you"
        } else {
            "Next farm pulse"
        }
        send?.visibility = if (awaiting) android.view.View.VISIBLE else android.view.View.GONE
    }

    fun dismiss() {
        val view = root ?: return
        runCatching { windowManager.removeView(view) }
        root = null
        countdown = null
        caption = null
        send = null
    }

    @SuppressLint("SetTextI18n")
    private fun ensureAttached() {
        if (root != null) return
        val density = host.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val card = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16))
            setBackgroundColor("#E61A2420".toColorInt())
            elevation = dp(8).toFloat()
        }

        caption = TextView(host).apply {
            text = "Next farm pulse"
            setTextColor("#C6A15B".toColorInt())
            textSize = 12f
        }
        countdown = TextView(host).apply {
            text = "0:00"
            setTextColor("#E8EDE9".toColorInt())
            textSize = 28f
            setPadding(0, dp(4), 0, dp(4))
        }
        send = Button(host).apply {
            text = "SEND"
            textSize = 22f
            isAllCaps = true
            visibility = android.view.View.GONE
            setTextColor("#0F1612".toColorInt())
            setBackgroundColor("#3DDC84".toColorInt())
            setPadding(dp(12))
            setOnClickListener {
                SessionController.onUserTappedSend(host.applicationContext)
            }
        }

        card.addView(caption)
        card.addView(countdown)
        card.addView(send)

        val frame = FrameLayout(host).apply {
            addView(
                card,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            extraFlags

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            flags,
            format,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(72)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        runCatching {
            windowManager.addView(frame, params)
            root = frame
        }
    }
}
