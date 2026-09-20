package app.farmpulse.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import app.farmpulse.data.ButtonBinding
import app.farmpulse.data.PathSegment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object NodeFingerprint {
    private val json = Json { encodeDefaults = true }

    fun capture(node: AccessibilityNodeInfo, screenWidth: Int, screenHeight: Int): ButtonBinding {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val cx = bounds.centerX()
        val cy = bounds.centerY()
        val relX = if (screenWidth > 0) cx.toFloat() / screenWidth else 0.5f
        val relY = if (screenHeight > 0) cy.toFloat() / screenHeight else 0.5f
        return ButtonBinding(
            packageName = node.packageName?.toString().orEmpty(),
            viewIdResourceName = node.viewIdResourceName,
            text = node.text?.toString()?.takeIf { it.isNotBlank() },
            contentDescription = node.contentDescription?.toString()?.takeIf { it.isNotBlank() },
            className = node.className?.toString(),
            boundsCenterX = cx,
            boundsCenterY = cy,
            relativeX = relX.coerceIn(0f, 1f),
            relativeY = relY.coerceIn(0f, 1f),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            parentPathFingerprint = encode(walk(node)),
            boundAtMillis = System.currentTimeMillis(),
        )
    }

    fun walk(node: AccessibilityNodeInfo): List<PathSegment> {
        val segments = ArrayList<PathSegment>()
        var current: AccessibilityNodeInfo? = node
        var guard = 0
        while (current != null && guard++ < 32) {
            val parent = current.parent
            val index = parent?.let { indexAmongSiblings(it, current!!) } ?: 0
            segments.add(
                PathSegment(
                    className = current.className?.toString().orEmpty(),
                    viewId = current.viewIdResourceName,
                    index = index,
                ),
            )
            current = parent
        }
        return segments.asReversed()
    }

    fun encode(segments: List<PathSegment>): String = json.encodeToString(segments)

    fun decode(raw: String): List<PathSegment> {
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<PathSegment>>(raw) }.getOrDefault(emptyList())
    }

    fun resolveByPath(root: AccessibilityNodeInfo, fingerprint: String): AccessibilityNodeInfo? {
        val segments = decode(fingerprint)
        if (segments.isEmpty()) return null
        var current: AccessibilityNodeInfo = root
        // First segment should describe the root-ish container; skip mismatches by scanning.
        val start = if (classMatches(current, segments.first())) 1 else 0
        for (i in start until segments.size) {
            val wanted = segments[i]
            val next = childMatching(current, wanted) ?: return null
            current = next
        }
        return current
    }

    private fun childMatching(parent: AccessibilityNodeInfo, wanted: PathSegment): AccessibilityNodeInfo? {
        val byIndex = parent.getChild(wanted.index)
        if (byIndex != null && classMatches(byIndex, wanted)) {
            return byIndex
        }
        for (i in 0 until parent.childCount) {
            val child = parent.getChild(i) ?: continue
            if (classMatches(child, wanted)) return child
        }
        return null
    }

    private fun classMatches(node: AccessibilityNodeInfo, wanted: PathSegment): Boolean {
        val cls = node.className?.toString().orEmpty()
        if (wanted.className.isNotBlank() && cls != wanted.className) return false
        if (!wanted.viewId.isNullOrBlank() && node.viewIdResourceName != wanted.viewId) return false
        return true
    }

    private fun indexAmongSiblings(parent: AccessibilityNodeInfo, child: AccessibilityNodeInfo): Int {
        val childBounds = Rect().also { child.getBoundsInScreen(it) }
        val childId = child.viewIdResourceName
        val childClass = child.className?.toString()
        for (i in 0 until parent.childCount) {
            val sibling = parent.getChild(i) ?: continue
            val b = Rect().also { sibling.getBoundsInScreen(it) }
            if (b == childBounds &&
                sibling.viewIdResourceName == childId &&
                sibling.className?.toString() == childClass
            ) {
                return i
            }
        }
        return 0
    }

    fun clickableTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var current: AccessibilityNodeInfo? = node
        var last = node
        var guard = 0
        while (current != null && guard++ < 16) {
            if (current.isClickable) return current
            last = current
            current = current.parent
        }
        return last
    }
}
