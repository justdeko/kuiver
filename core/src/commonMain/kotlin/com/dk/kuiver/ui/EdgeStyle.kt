package com.dk.kuiver.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
 * @property arrowDrawer draws the arrow head
 */
@Immutable
data class EdgeStyle(
    val color: Color = Color.Black,
    val strokeWidth: Float = 3f,
    val showArrow: Boolean = true,
    val dashed: Boolean = false,
    val dashLength: Float = 10f,
    val gapLength: Float = 5f,
    val shape: EdgeShape = EdgeShape.AUTO,
    val loopRadius: Float = 40f,
    val curveFactor: Float = 0.5f,
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
            strokeWidth: Float = 3f
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

internal fun EdgeStyle.createPath(edge: KuiverEdge, from: Offset, to: Offset): EdgePath {
    if (edge.fromId == edge.toId) {
        return EdgePathFactory.createSelfLoopPath(from, to, loopRadius, showArrow, strokeWidth)
    }
    return when (shape) {
        EdgeShape.AUTO -> if (edge.type == EdgeType.BACK) {
            EdgePathFactory.createCurvedPath(from, to, showArrow, strokeWidth)
        } else {
            EdgePathFactory.createStraightPath(from, to, showArrow, strokeWidth)
        }

        EdgeShape.STRAIGHT -> EdgePathFactory.createStraightPath(from, to, showArrow, strokeWidth)
        EdgeShape.CURVED -> EdgePathFactory.createCurvedPath(from, to, showArrow, strokeWidth)
        EdgeShape.ORTHOGONAL ->
            EdgePathFactory.createOrthogonalPath(from, to, curveFactor, showArrow, strokeWidth)

        EdgeShape.RIGHT_ANGLE -> EdgePathFactory.createRightAnglePath(
            from = from,
            to = to,
            routing = RightAngleRouting.fromAnchorSides(
                AnchorSide.fromAnchorId(edge.fromAnchor),
                AnchorSide.fromAnchorId(edge.toAnchor)
            ),
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
        path = style.createPath(edge, from, to),
        color = style.color,
        strokeWidth = style.strokeWidth,
        showArrow = style.showArrow,
        dashed = style.dashed,
        dashLength = style.dashLength,
        gapLength = style.gapLength,
        arrowDrawer = style.arrowDrawer
    )
}
