package app.farmpulse.data

import kotlinx.serialization.Serializable

@Serializable
data class ButtonBinding(
    val packageName: String,
    val viewIdResourceName: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val boundsCenterX: Int = 0,
    val boundsCenterY: Int = 0,
    val relativeX: Float = 0.5f,
    val relativeY: Float = 0.5f,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val parentPathFingerprint: String = "",
    val boundAtMillis: Long = 0L,
)

@Serializable
data class PathSegment(
    val className: String,
    val viewId: String? = null,
    val index: Int = 0,
)
