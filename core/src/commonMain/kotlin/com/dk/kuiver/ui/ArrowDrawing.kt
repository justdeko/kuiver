package com.dk.kuiver.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Functional type for custom arrow drawing.
 *
 * @param arrowTip The point where the arrow tip should be positioned
 * @param direction The normalized direction vector pointing in the arrow direction
 * @param color The color to use for the arrow
 */
typealias ArrowDrawer = DrawScope.(
    arrowTip: Offset,
    direction: Offset,
    color: Color
) -> Unit

/**
 * Default arrow drawer that draws a filled triangle.
 */
val DefaultArrowDrawer: ArrowDrawer = { arrowTip, direction, color ->
    val angle = atan2(direction.y.toDouble(), direction.x.toDouble()).toFloat()
    val arrowSize = EdgeDrawingDefaults.ARROW_SIZE
    val arrowOffset = EdgeDrawingDefaults.ARROW_OFFSET
    val arrowAngleSpread = EdgeDrawingDefaults.ARROW_ANGLE_SPREAD

    val arrowBasePoint = Offset(
        arrowTip.x - direction.x * arrowOffset,
        arrowTip.y - direction.y * arrowOffset
    )

    val arrowPath = Path().apply {
        moveTo(arrowBasePoint.x, arrowBasePoint.y)
        lineTo(
            arrowBasePoint.x - arrowSize * cos(angle - arrowAngleSpread),
            arrowBasePoint.y - arrowSize * sin(angle - arrowAngleSpread)
        )
        lineTo(
            arrowBasePoint.x - arrowSize * cos(angle + arrowAngleSpread),
            arrowBasePoint.y - arrowSize * sin(angle + arrowAngleSpread)
        )
        close()
    }
    drawPath(path = arrowPath, color = color.copy(alpha = 1.0f))
}

/**
 * Helper function to draw an arrow at the end of an edge.
 */
internal fun DrawScope.drawArrowAtEnd(
    endPoint: Offset,
    direction: Offset,
    color: Color,
    arrowDrawer: ArrowDrawer
) {
    arrowDrawer(endPoint, direction, color)
}
