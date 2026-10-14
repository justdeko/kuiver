package com.dk.kuiver.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import com.dk.kuiver.renderer.LocalEdgePathReporter
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Cap on the canvas size, keeps extreme edges within [Constraints].
 */
private const val MAX_EDGE_EXTENT_PX = 30_000

/**
 * A canvas covering [bounds] instead of the whole viewer, for edge composables.
 * [onDraw] draws in viewer coordinates and is not clipped to [bounds].
 *
 * ```kotlin
 * edgeContent = { _, from, to ->
 *     val density = LocalDensity.current
 *     val path = remember(from, to, density) {
 *         EdgePathFactory.createStraightPath(from, to, density)
 *     }
 *     EdgeCanvas(remember(path, density) { path.boundingRect(density) }, hitPath = path) {
 *         drawLine(Color.Blue, path.from, path.pathEndpoint, strokeWidth = 2.dp.toPx())
 *     }
 * }
 * ```
 *
 * @param bounds area the edge draws into in pixels, see [boundingRect]
 * @param hitPath hit path for edge tap and hover detection, the edge can't be tapped or hovered
 * when null
 * @param onDraw draws the edge
 */
@Composable
fun EdgeCanvas(bounds: Rect, hitPath: EdgePath? = null, onDraw: DrawScope.() -> Unit) {
    val reporter = LocalEdgePathReporter.current
    if (reporter != null && hitPath != null) {
        SideEffect { reporter.report(hitPath) }
        DisposableEffect(reporter) { onDispose { reporter.report(null) } }
    }

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
    strokeWidth: Dp,
    showArrow: Boolean,
    dashed: Boolean,
    dashLength: Dp,
    gapLength: Dp,
    arrowSize: Dp,
    arrowDrawer: ArrowDrawer
) {
    val density = LocalDensity.current
    val bounds = remember(path, density, strokeWidth, arrowSize, showArrow) {
        path.boundingRect(
            density = density,
            strokeWidth = strokeWidth,
            arrowSize = arrowSize,
            showArrow = showArrow
        )
    }

    EdgeCanvas(bounds, hitPath = path) {
        drawEdgePath(
            path = path,
            color = color,
            strokeWidth = strokeWidth,
            showArrow = showArrow,
            dashed = dashed,
            dashLength = dashLength,
            gapLength = gapLength,
            arrowSize = arrowSize,
            arrowDrawer = arrowDrawer
        )
    }
}
