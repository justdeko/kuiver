package com.dk.kuiver.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import kotlin.math.ceil
import kotlin.math.floor

/** Cap on the canvas size, so an edge spanning an extreme graph still fits in [Constraints]. */
private const val MAX_EDGE_EXTENT_PX = 30_000

/**
 * A canvas covering [bounds] instead of the whole viewer, for edge composables.
 * [onDraw] draws in viewer coordinates and is not clipped to [bounds].
 *
 * ```kotlin
 * edgeContent = { _, from, to ->
 *     val path = remember(from, to) { EdgePathFactory.createStraightPath(from, to) }
 *     EdgeCanvas(remember(path) { path.boundingRect() }) {
 *         drawLine(Color.Blue, path.from, path.pathEndpoint, strokeWidth = 2f)
 *     }
 * }
 * ```
 *
 * @param bounds area the edge draws into, see [boundingRect]
 * @param onDraw draws the edge
 */
@Composable
fun EdgeCanvas(bounds: Rect, onDraw: DrawScope.() -> Unit) {
    val left = floor(bounds.left)
    val top = floor(bounds.top)
    val width = (ceil(bounds.right) - left).toInt().coerceIn(0, MAX_EDGE_EXTENT_PX)
    val height = (ceil(bounds.bottom) - top).toInt().coerceIn(0, MAX_EDGE_EXTENT_PX)

    Spacer(
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(Constraints.fixed(width, height))
                // Reporting more than the incoming constraints would make the parent center
                // the overflow, shifting the drawing; report a fitting size and draw past it
                layout(constraints.constrainWidth(width), constraints.constrainHeight(height)) {
                    placeable.place(left.toInt(), top.toInt())
                }
            }
            .drawBehind {
                translate(left = -left, top = -top) { onDraw() }
            }
    )
}

@Composable
internal fun EdgePathCanvas(
    path: EdgePath,
    color: Color,
    strokeWidth: Float,
    showArrow: Boolean,
    dashed: Boolean,
    dashLength: Float,
    gapLength: Float,
    arrowDrawer: ArrowDrawer
) {
    val bounds = remember(path, strokeWidth, showArrow) {
        path.boundingRect(strokeWidth = strokeWidth, showArrow = showArrow)
    }

    EdgeCanvas(bounds) {
        drawEdgePath(
            path = path,
            color = color,
            strokeWidth = strokeWidth,
            showArrow = showArrow,
            dashed = dashed,
            dashLength = dashLength,
            gapLength = gapLength,
            arrowDrawer = arrowDrawer
        )
    }
}
