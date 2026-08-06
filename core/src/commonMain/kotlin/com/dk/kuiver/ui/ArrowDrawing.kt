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
 * @param arrowTip The point where the arrow tip should be positioned, in pixels
 * @param direction The normalized direction vector pointing in the arrow direction
 * @param color The color to use for the arrow
 * @param arrowSize Size of the arrow head in pixels
 */
typealias ArrowDrawer = DrawScope.(
    arrowTip: Offset,
    direction: Offset,
    color: Color,
    arrowSize: Float
) -> Unit

/**
 * Default arrow drawer that draws a filled triangle.
 */
val DefaultArrowDrawer: ArrowDrawer = { arrowTip, direction, color, arrowSize ->
    val angle = atan2(direction.y.toDouble(), direction.x.toDouble()).toFloat()
    val arrowOffset = EdgeDrawingDefaults.ARROW_OFFSET.toPx()
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
 *
 * @param endPoint The point where the arrow tip should be positioned, in pixels
 * @param direction The normalized direction vector pointing in the arrow direction
 * @param color The color to use for the arrow
 * @param arrowSize Size of the arrow head in pixels
 * @param arrowDrawer The function to use for drawing the arrow
 */
internal fun DrawScope.drawArrowAtEnd(
    endPoint: Offset,
    direction: Offset,
    color: Color,
    arrowSize: Float,
    arrowDrawer: ArrowDrawer
) {
    arrowDrawer(endPoint, direction, color, arrowSize)
}
