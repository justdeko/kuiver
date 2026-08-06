package com.dk.kuiver.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Bounding box of the edge, in the coordinate space of its endpoints.
 *
 * @param density resolves the dp sizes against the current display
 * @param strokeWidth width of the edge line
 * @param arrowSize Size of the arrow head, ignored when [showArrow] is false
 * @param showArrow whether the edge has an arrow
 * @param extraPadding extra room around the edge
 */
fun EdgePath.boundingRect(
    density: Density,
    strokeWidth: Dp = 3.dp,
    arrowSize: Dp = 16.dp,
    showArrow: Boolean = true,
    extraPadding: Dp = 0.dp
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
        EdgeDrawingDefaults.ARROW_OFFSET + arrowSize
    } else {
        0.dp
    }
    val padding = with(density) { (strokeWidth / 2f + arrowExtent + extraPadding).toPx() }

    return Rect(
        left = left - padding,
        top = top - padding,
        right = right + padding,
        bottom = bottom + padding
    )
}
