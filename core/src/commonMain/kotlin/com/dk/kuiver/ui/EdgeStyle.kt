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
 * Self-loops ignore this and always draw as a loop. Edges that share their nodes, direction and
 * anchors with other edges fan out as arcs with [AUTO], [STRAIGHT] or [CURVED].
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

internal val EdgeShape.fansOut: Boolean
    get() = this == EdgeShape.AUTO || this == EdgeShape.STRAIGHT || this == EdgeShape.CURVED

internal fun EdgeStyle.createPath(
    edge: KuiverEdge,
    bend: Float?,
    from: Offset,
    to: Offset,
    density: Density
): EdgePath {
    if (edge.fromId == edge.toId) {
        return EdgePathFactory.createSelfLoopPath(
            from, to, density, loopRadius, showArrow, strokeWidth, bend ?: 0f
        )
    }
    return when (shape) {
        EdgeShape.AUTO, EdgeShape.STRAIGHT -> when {
            bend != null -> if (bend == 0f) {
                straightPath(from, to, density)
            } else {
                curvedPath(from, to, density, bend)
            }

            shape == EdgeShape.AUTO && edge.type == EdgeType.BACK ->
                curvedPath(from, to, density, EdgeDrawingDefaults.CURVE_PROPORTIONAL_OFFSET)

            else -> straightPath(from, to, density)
        }

        EdgeShape.CURVED -> curvedPath(
            from, to, density, bend ?: EdgeDrawingDefaults.CURVE_PROPORTIONAL_OFFSET
        )

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

private fun EdgeStyle.straightPath(from: Offset, to: Offset, density: Density): EdgePath =
    EdgePathFactory.createStraightPath(from, to, density, showArrow, strokeWidth)

private fun EdgeStyle.curvedPath(
    from: Offset,
    to: Offset,
    density: Density,
    bend: Float
): EdgePath = EdgePathFactory.createCurvedPath(from, to, density, showArrow, strokeWidth, bend)

internal fun DrawScope.drawStyledEdge(path: EdgePath, style: EdgeStyle) {
    drawEdgePath(
        path = path,
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
