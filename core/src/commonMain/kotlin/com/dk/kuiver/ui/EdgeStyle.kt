package com.dk.kuiver.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dk.kuiver.model.EdgeType
import com.dk.kuiver.model.KuiverEdge

/**
 * Shape of the path an edge takes between its endpoints.
 *
 * Self-loops ignore this and always draw as a loop.
 */
enum class EdgeShape {
    /** Curved for [EdgeType.BACK], straight otherwise. */
    AUTO,
    STRAIGHT,
    CURVED,

    /** S-curve with horizontal tangents. */
    ORTHOGONAL,

    /** Horizontal and vertical segments, routed from the edge anchors. */
    RIGHT_ANGLE
}

/**
 * How one edge is drawn for the batched edge layer of `KuiverViewer`.
 *
 * @property color edge line color
 * @property strokeWidth width of the edge line
 * @property showArrow whether to draw an arrow at the end
 * @property dashed whether the edge is dashed
 * @property dashLength length of dashes
 * @property gapLength length of gaps between dashes
 * @property shape path shape
 * @property loopRadius radius of the self-loop arc
 * @property curveFactor control point extent, for [EdgeShape.ORTHOGONAL]
 * @property arrowSize Size of the arrow head
 * @property arrowDrawer draws the arrow head
 */
@Immutable
data class EdgeStyle(
    val color: Color = Color.Black,
    val strokeWidth: Dp = 3.dp,
    val showArrow: Boolean = true,
    val dashed: Boolean = false,
    val dashLength: Dp = 10.dp,
    val gapLength: Dp = 5.dp,
    val shape: EdgeShape = EdgeShape.AUTO,
    val loopRadius: Dp = 40.dp,
    val curveFactor: Float = 0.5f,
    val arrowSize: Dp = 16.dp,
    val arrowDrawer: ArrowDrawer = DefaultArrowDrawer
) {
    companion object {
        /**
         * Returns a default [EdgeStyle] for the given [edge], with different colors for back edges and self-loops.
         *
         * @param edge edge to style
         * @param baseColor color for forward, tree and cross edges
         * @param backEdgeColor color for back edges and self-loops
         * @param strokeWidth width of the edge line
         */
        fun styled(
            edge: KuiverEdge,
            baseColor: Color = Color.Black,
            backEdgeColor: Color = Color(0xFFFF6B6B),
            strokeWidth: Dp = 3.dp
        ): EdgeStyle {
            val (color, dashed) = when (edge.type) {
                EdgeType.SELF_LOOP -> Pair(backEdgeColor, true)
                EdgeType.BACK -> Pair(baseColor.copy(alpha = 0.7f), true)
                else -> Pair(baseColor, false)
            }
            return EdgeStyle(
                color = color,
                strokeWidth = strokeWidth,
                dashed = dashed
            )
        }
    }
}

internal fun EdgeStyle.createPath(
    edge: KuiverEdge,
    from: Offset,
    to: Offset,
    density: Density
): EdgePath {
    if (edge.fromId == edge.toId) {
        return EdgePathFactory.createSelfLoopPath(
            from, to, density, loopRadius, showArrow, strokeWidth
        )
    }
    return when (shape) {
        EdgeShape.AUTO -> if (edge.type == EdgeType.BACK) {
            EdgePathFactory.createCurvedPath(from, to, density, showArrow, strokeWidth)
        } else {
            EdgePathFactory.createStraightPath(from, to, density, showArrow, strokeWidth)
        }

        EdgeShape.STRAIGHT ->
            EdgePathFactory.createStraightPath(from, to, density, showArrow, strokeWidth)

        EdgeShape.CURVED ->
            EdgePathFactory.createCurvedPath(from, to, density, showArrow, strokeWidth)

        EdgeShape.ORTHOGONAL -> EdgePathFactory.createOrthogonalPath(
            from, to, density, curveFactor, showArrow, strokeWidth
        )

        EdgeShape.RIGHT_ANGLE -> EdgePathFactory.createRightAnglePath(
            from = from,
            to = to,
            routing = RightAngleRouting.fromAnchorSides(
                AnchorSide.fromAnchorId(edge.fromAnchor),
                AnchorSide.fromAnchorId(edge.toAnchor)
            ),
            density = density,
            showArrow = showArrow,
            strokeWidth = strokeWidth
        )
    }
}

internal fun DrawScope.drawStyledEdge(
    edge: KuiverEdge,
    from: Offset,
    to: Offset,
    style: EdgeStyle
) {
    drawEdgePath(
        path = style.createPath(edge, from, to, this),
        color = style.color,
        strokeWidth = style.strokeWidth,
        showArrow = style.showArrow,
        dashed = style.dashed,
        dashLength = style.dashLength,
        gapLength = style.gapLength,
        arrowSize = style.arrowSize,
        arrowDrawer = style.arrowDrawer
    )
}
