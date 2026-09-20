package app.farmpulse.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import app.farmpulse.Travian
import app.farmpulse.data.AppPreferences
import app.farmpulse.session.SendResult
import app.farmpulse.session.SessionController
import app.farmpulse.util.DeviceLock

class FarmPulseAccessibilityService : AccessibilityService() {

    @Volatile
    private var bindMode: Boolean = false

    private var a11yOverlay: OverlayController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        a11yOverlay = OverlayController(
            host = this,
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager,
            windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            },
            extraFlags = 0,
            format = PixelFormat.TRANSLUCENT,
        )
        SessionController.refreshFromDisk()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        a11yOverlay?.dismiss()
        a11yOverlay = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        a11yOverlay?.dismiss()
        a11yOverlay = null
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    fun setBindMode(enabled: Boolean) {
        bindMode = enabled
    }

    fun overlay(): OverlayController? = a11yOverlay

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !bindMode) return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return
        if (event.packageName?.toString() != Travian.PACKAGE_NAME) return
        val source = event.source ?: return
        try {
            val target = NodeFingerprint.clickableTarget(source)
            if (target.packageName?.toString() != Travian.PACKAGE_NAME) return
            val metrics = resources.displayMetrics
            val binding = NodeFingerprint.capture(target, metrics.widthPixels, metrics.heightPixels)
            SessionController.onBindingCaptured(binding)
            bindMode = false
        } finally {
            source.recycle()
        }
    }

    /**
     * Click the persisted Travian farmlist Send control.
     * Called only after the user taps FarmPulse Send, and only when unlocked.
     */
    fun performBoundClick(): SendResult {
        if (DeviceLock.isDeviceLocked(this)) return SendResult.DeviceLocked
        val binding = AppPreferences.binding ?: return SendResult.NoBinding

        val roots = travianRoots()
        if (roots.isEmpty()) return SendResult.RebindRequired

        try {
            // 1. viewIdResourceName
            binding.viewIdResourceName?.takeIf { it.isNotBlank() }?.let { viewId ->
                for (root in roots) {
                    val matches = root.findAccessibilityNodeInfosByViewId(viewId)
                    val clicked = matches?.firstOrNull { clickNode(it) }
                    matches?.forEach { if (it != clicked) it.recycle() }
                    if (clicked != null) {
                        clicked.recycle()
                        return SendResult.Sent
                    }
                }
            }

            // 2. text, then contentDescription
            val labels = listOfNotNull(binding.text, binding.contentDescription)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            for (label in labels) {
                for (root in roots) {
                    val matches = root.findAccessibilityNodeInfosByText(label)
                    val clicked = matches?.firstOrNull { node ->
                        val textOk = node.text?.toString()?.trim() == label ||
                            node.contentDescription?.toString()?.trim() == label
                        textOk && clickNode(node)
                    }
                    matches?.forEach { if (it != clicked) it.recycle() }
                    if (clicked != null) {
                        clicked.recycle()
                        return SendResult.Sent
                    }
                }
            }

            // 3. parent path fingerprint
            if (binding.parentPathFingerprint.isNotBlank()) {
                for (root in roots) {
                    val node = NodeFingerprint.resolveByPath(root, binding.parentPathFingerprint)
                    if (node != null && clickNode(node)) {
                        return SendResult.Sent
                    }
                }
            }

            // 4. fallback: gesture at relative screen coordinates
            val metrics = resources.displayMetrics
            val x = binding.relativeX * metrics.widthPixels
            val y = binding.relativeY * metrics.heightPixels
            if (x > 0f && y > 0f && dispatchClick(x, y)) {
                return SendResult.Sent
            }
        } finally {
            roots.forEach { it.recycle() }
        }

        return SendResult.RebindRequired
    }

    private fun travianRoots(): List<AccessibilityNodeInfo> {
        val found = LinkedHashMap<Int, AccessibilityNodeInfo>()
        windows?.forEach { window ->
            val root = window.root ?: return@forEach
            if (root.packageName?.toString() == Travian.PACKAGE_NAME ||
                window.type == AccessibilityWindowInfo.TYPE_APPLICATION
            ) {
                if (root.packageName?.toString() == Travian.PACKAGE_NAME) {
                    found[System.identityHashCode(root)] = root
                } else {
                    root.recycle()
                }
            } else {
                root.recycle()
            }
        }
        rootInActiveWindow?.let { active ->
            if (active.packageName?.toString() == Travian.PACKAGE_NAME) {
                found[System.identityHashCode(active)] = active
            } else {
                active.recycle()
            }
        }
        return found.values.toList()
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var guard = 0
        while (current != null && guard++ < 16) {
            if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                current.performAction(AccessibilityNodeInfo.ACTION_FOCUS) &&
                current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) {
                return true
            }
            current = current.parent
        }
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.isEmpty) {
            return dispatchClick(bounds.exactCenterX(), bounds.exactCenterY())
        }
        return false
    }

    private fun dispatchClick(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        @Volatile
        var instance: FarmPulseAccessibilityService? = null
            private set
    }
}
