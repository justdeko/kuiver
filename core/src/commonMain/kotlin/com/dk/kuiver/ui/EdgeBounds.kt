package com.dk.kuiver.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Bounding box of the edge, in the coordinate space of its endpoints.
 *
 * @param strokeWidth width of the edge line
 * @param showArrow whether the edge has an arrow
 * @param extraPadding extra room around the edge
 */
fun EdgePath.boundingRect(
    strokeWidth: Float = 3f,
    showArrow: Boolean = true,
    extraPadding: Float = 0f
): Rect {
    var left = from.x
    var top = from.y
    var right = from.x
    var bottom = from.y

    fun include(point: Offset) {
        if (point.x < left) left = point.x
        if (point.x > right) right = point.x
        if (point.y < top) top = point.y
        if (point.y > bottom) bottom = point.y
    }

    include(to)
    include(pathEndpoint)
    when (this) {
        is EdgePath.Straight -> Unit
        is EdgePath.Curved -> include(controlPoint)
        is EdgePath.SelfLoop -> include(controlPoint)
        is EdgePath.Orthogonal -> {
            include(controlPoint1)
            include(controlPoint2)
        }

        is EdgePath.RightAngle -> waypoints.forEach(::include)
    }

    val arrowExtent = if (showArrow) {
        EdgeDrawingDefaults.ARROW_OFFSET + EdgeDrawingDefaults.ARROW_SIZE
    } else {
        0f
    }
    val padding = strokeWidth / 2f + arrowExtent + extraPadding

    return Rect(
        left = left - padding,
        top = top - padding,
        right = right + padding,
        bottom = bottom + padding
    )
}
