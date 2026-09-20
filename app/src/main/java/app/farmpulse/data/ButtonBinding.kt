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
    /** "node" when captured from TYPE_VIEW_CLICKED; "coordinate" for overlay confirm. */
    val kind: String = KIND_NODE,
) {
    val isCoordinate: Boolean get() = kind == KIND_COORDINATE

    companion object {
        const val KIND_NODE = "node"
        const val KIND_COORDINATE = "coordinate"
    }
}

@Serializable
data class PathSegment(
    val className: String,
    val viewId: String? = null,
    val index: Int = 0,
)
