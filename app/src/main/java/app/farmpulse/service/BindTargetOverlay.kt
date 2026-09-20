package app.farmpulse.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.core.view.setPadding
import app.farmpulse.Travian
import app.farmpulse.data.ButtonBinding
import app.farmpulse.session.SessionController

/**
 * Movable crosshair for coordinate bind. Touches outside the chip and
 * panel pass through to Travian ([WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL])
 * so the farm list can still be opened. Confirm only saves a point —
 * it never clicks Travian.
 */
class BindTargetOverlay(
    private val host: Context,
    private val windowManager: WindowManager,
    private val windowType: Int,
) {
    private var panel: LinearLayout? = null
    private var crosshair: CrosshairView? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var targetParams: WindowManager.LayoutParams? = null

    val isShowing: Boolean get() = crosshair != null

    fun show(): Boolean {
        if (isShowing) return true
        attachPanel()
        attachCrosshair()
        return isShowing
    }

    fun dismiss() {
        panel?.let { runCatching { windowManager.removeView(it) } }
        crosshair?.let { runCatching { windowManager.removeView(it) } }
        panel = null
        crosshair = null
        panelParams = null
        targetParams = null
    }

    private fun dp(v: Int): Int = (v * host.resources.displayMetrics.density).toInt()

    private fun attachPanel() {
        val hint = TextView(host).apply {
            text = "Travian may not expose buttons to Accessibility — place the target on Send and confirm."
            setTextColor("#E8EDE9".toColorInt())
            textSize = 13f
        }
        val confirm = Button(host).apply {
            text = "Confirm bind"
            isAllCaps = false
            setTextColor("#0F1612".toColorInt())
            setBackgroundColor("#3DDC84".toColorInt())
            setOnClickListener { confirmBind() }
        }
        val cancel = Button(host).apply {
            text = "Cancel"
            isAllCaps = false
            setTextColor("#E8EDE9".toColorInt())
            setBackgroundColor("#24302A".toColorInt())
            setOnClickListener { SessionController.cancelBind() }
        }
        val buttons = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(confirm, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            })
            addView(cancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val card = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14))
            setBackgroundColor("#F21A2420".toColorInt())
            elevation = dp(8).toFloat()
            addView(hint)
            addView(buttons, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) })
        }

        val params = baseParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(36)
            width = (host.resources.displayMetrics.widthPixels * 0.92f).toInt()
        }
        runCatching {
            windowManager.addView(card, params)
            panel = card
            panelParams = params
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachCrosshair() {
        val size = dp(72)
        val metrics = host.resources.displayMetrics
        val view = CrosshairView(host)
        val params = baseParams(size, size).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ((metrics.widthPixels - size) / 2).coerceAtLeast(0)
            y = ((metrics.heightPixels - size) / 2).coerceAtLeast(0)
        }

        var lastRawX = 0f
        var lastRawY = 0f
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - lastRawX).toInt()
                    val dy = (event.rawY - lastRawY).toInt()
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    params.x = (params.x + dx).coerceIn(0, metrics.widthPixels - size)
                    params.y = (params.y + dy).coerceIn(0, metrics.heightPixels - size)
                    runCatching { windowManager.updateViewLayout(view, params) }
                    true
                }
                else -> true
            }
        }

        runCatching {
            windowManager.addView(view, params)
            crosshair = view
            targetParams = params
        }
    }

    private fun confirmBind() {
        val target = crosshair ?: return
        val loc = IntArray(2)
        target.getLocationOnScreen(loc)
        val cx = loc[0] + target.width / 2f
        val cy = loc[1] + target.height / 2f
        val metrics = host.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val binding = ButtonBinding(
            packageName = Travian.PACKAGE_NAME,
            viewIdResourceName = null,
            text = null,
            contentDescription = null,
            className = null,
            boundsCenterX = cx.toInt(),
            boundsCenterY = cy.toInt(),
            relativeX = (cx / width).coerceIn(0f, 1f),
            relativeY = (cy / height).coerceIn(0f, 1f),
            screenWidth = width,
            screenHeight = height,
            parentPathFingerprint = "",
            boundAtMillis = System.currentTimeMillis(),
            kind = ButtonBinding.KIND_COORDINATE,
        )
        SessionController.onBindingCaptured(binding)
    }

    private fun baseParams(width: Int, height: Int): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        return WindowManager.LayoutParams(
            width,
            height,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private class CrosshairView(context: Context) : View(context) {
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f * resources.displayMetrics.density
            color = "#C6A15B".toColorInt()
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = "#661A2420".toColorInt()
        }
        private val hair = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * resources.displayMetrics.density
            color = "#3DDC84".toColorInt()
        }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = "#3DDC84".toColorInt()
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val radius = (minOf(width, height) / 2f) - ring.strokeWidth
            canvas.drawCircle(cx, cy, radius, fill)
            canvas.drawCircle(cx, cy, radius, ring)
            val arm = radius * 0.72f
            canvas.drawLine(cx - arm, cy, cx + arm, cy, hair)
            canvas.drawLine(cx, cy - arm, cx, cy + arm, hair)
            canvas.drawCircle(cx, cy, 3f * resources.displayMetrics.density, dot)
        }
    }
}
