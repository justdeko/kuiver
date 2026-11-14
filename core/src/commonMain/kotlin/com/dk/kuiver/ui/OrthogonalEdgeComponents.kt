package com.dk.kuiver.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dk.kuiver.model.KuiverEdge

/**
 * Orthogonal edge with S-curve path (smooth curved connections).
 *
 * Renders an edge with horizontal tangents at both ends, creating a smooth
 * S-curve path using cubic Bezier curves.
 *
 * Note: This component is designed for forward/tree edges only. It does not support
 * back edges or self-loops. For graphs with back edges, handle the fallback yourself.
 */
@Composable
public fun OrthogonalEdgeContent(
    from: Offset,
    to: Offset,
    color: Color = LocalKuiverColors.current.edge,
    strokeWidth: Dp = 3.dp,
    showArrow: Boolean = true,
    dashed: Boolean = false,
    dashLength: Dp = 10.dp,
    gapLength: Dp = 5.dp,
    curveFactor: Float = 0.5f,
    arrowSize: Dp = 16.dp,
    arrowDrawer: ArrowDrawer = DefaultArrowDrawer
) {
    val density = LocalDensity.current
    val edgePath = remember(from, to, density, curveFactor, showArrow, strokeWidth) {
        EdgePathFactory.createOrthogonalPath(
            from, to, density, curveFactor, showArrow, strokeWidth
        )
    }

    EdgePathCanvas(
        path = edgePath,
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

/**
 * S-curve edge with horizontal tangents and an optional label.
 *
 * The label follows the curve and is hidden on edges shorter than [minEdgeLengthForLabel]. Back
 * edges and self-loops are not supported.
 *
 * @param from start point of the edge
 * @param to end point of the edge
 * @param label text shown on the edge, none when null
 * @param labelOffset position along the edge from 0 (start) to 1 (end), 0.5 by default
 * @param labelPlacement preset position, takes precedence over [labelOffset]
 * @param labelStyle style of the label
 * @param labelContent replaces the default label
 * @param color edge line color
 * @param strokeWidth width of the edge line, must be positive
 * @param showArrow whether to draw an arrow at the end
 * @param dashed whether the edge is dashed
 * @param dashLength length of dashes
 * @param gapLength length of gaps between dashes
 * @param curveFactor how far the control points extend horizontally, 0 to 1
 * @param arrowSize size of the arrow head
 * @param arrowDrawer draws the arrow head
 * @param minEdgeLengthForLabel shortest edge that still shows its label
 */
@Composable
public fun OrthogonalEdgeContentWithLabel(
    from: Offset,
    to: Offset,
    label: String? = null,
    labelOffset: Float? = null,
    labelPlacement: LabelPlacement? = null,
    labelStyle: EdgeLabelStyle = KuiverDefaults.edgeLabelStyle(),
    labelContent: (@Composable (String) -> Unit)? = null,
    color: Color = LocalKuiverColors.current.edge,
    strokeWidth: Dp = 3.dp,
    showArrow: Boolean = true,
    dashed: Boolean = false,
    dashLength: Dp = 10.dp,
    gapLength: Dp = 5.dp,
    curveFactor: Float = 0.5f,
    arrowSize: Dp = 16.dp,
    arrowDrawer: ArrowDrawer = DefaultArrowDrawer,
    minEdgeLengthForLabel: Dp = 50.dp
) {
    require(labelOffset == null || labelOffset in 0f..1f) {
        "labelOffset must be in range [0, 1], got $labelOffset"
    }
    require(strokeWidth > 0.dp) { "strokeWidth must be positive, got $strokeWidth" }
    require(minEdgeLengthForLabel >= 0.dp) {
        "minEdgeLengthForLabel must be non-negative, got $minEdgeLengthForLabel"
    }

    val offset = labelPlacement?.offset ?: labelOffset ?: 0.5f

    val density = LocalDensity.current
    val edgePath = remember(from, to, density, curveFactor, showArrow, strokeWidth) {
        EdgePathFactory.createOrthogonalPath(
            from, to, density, curveFactor, showArrow, strokeWidth
        )
    }

    LabeledEdge(
        path = edgePath,
        color = color,
        strokeWidth = strokeWidth,
        showArrow = showArrow,
        dashed = dashed,
        dashLength = dashLength,
        gapLength = gapLength,
        arrowSize = arrowSize,
        arrowDrawer = arrowDrawer,
        label = label,
        labelOffset = offset,
        labelStyle = labelStyle,
        labelContent = labelContent,
        minEdgeLengthForLabel = minEdgeLengthForLabel
    )
}

/**
 * Represents which side of a node an anchor is positioned on.
 * Used to determine optimal routing for right-angle edges.
 */
public enum class AnchorSide {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM;

    public companion object {
        /**
         * Infer anchor side from common anchor naming conventions.
         *
         * Supports directions and common abbreviations:
         * "left"/"l"/"west"/"w"/"start", "right"/"r"/"east"/"e"/"end",
         * "top"/"t"/"north"/"n"/"up", "bottom"/"b"/"south"/"s"/"down".
         *
         * Returns null for any unrecognized anchor ID, in which case
         * [RightAngleRouting.fromAnchorSides] falls back to a default routing.
         *
         * @param anchorId The anchor identifier string
         * @return The inferred AnchorSide, or null if cannot be determined
         */
        public fun fromAnchorId(anchorId: String?): AnchorSide? {
            if (anchorId == null) return null
            val id = anchorId.lowercase().trim()
            return when (id) {
                "left", "l", "west", "w", "start" -> LEFT
                "right", "r", "east", "e", "end" -> RIGHT
                "top", "t", "north", "n", "up" -> TOP
                "bottom", "b", "south", "s", "down" -> BOTTOM
                else -> null
            }
        }
    }
}

/**
 * Routing strategy for right-angle edges.
 */
public enum class RightAngleRouting {
    /** Go horizontal first, then vertical */
    HORIZONTAL_FIRST,

    /** Go vertical first, then horizontal */
    VERTICAL_FIRST,

    /** Use a middle waypoint for 3-segment routing (horizontal-vertical-horizontal) */
    HORIZONTAL_VERTICAL_HORIZONTAL,

    /** Use a middle waypoint for 3-segment routing (vertical-horizontal-vertical) */
    VERTICAL_HORIZONTAL_VERTICAL;

    public companion object {
        /**
         * Determine optimal routing based on source and target anchor sides.
         *
         * The routing is chosen to create natural-looking connections:
         * - Horizontal anchors (LEFT/RIGHT) prefer starting horizontally
         * - Vertical anchors (TOP/BOTTOM) prefer starting vertically
         * - Mixed anchors use 3-segment routing for clean corners
         *
         * @param fromSide The anchor side on the source node
         * @param toSide The anchor side on the target node
         * @return The optimal routing strategy
         */
        public fun fromAnchorSides(fromSide: AnchorSide?, toSide: AnchorSide?): RightAngleRouting {
            return when {
                // Both horizontal anchors: use H-V-H for parallel nodes
                fromSide == AnchorSide.RIGHT && toSide == AnchorSide.LEFT -> HORIZONTAL_VERTICAL_HORIZONTAL
                fromSide == AnchorSide.LEFT && toSide == AnchorSide.RIGHT -> HORIZONTAL_VERTICAL_HORIZONTAL
                fromSide == AnchorSide.RIGHT && toSide == AnchorSide.RIGHT -> HORIZONTAL_VERTICAL_HORIZONTAL
                fromSide == AnchorSide.LEFT && toSide == AnchorSide.LEFT -> HORIZONTAL_VERTICAL_HORIZONTAL

                // Both vertical anchors: use V-H-V for stacked nodes
                fromSide == AnchorSide.BOTTOM && toSide == AnchorSide.TOP -> VERTICAL_HORIZONTAL_VERTICAL
                fromSide == AnchorSide.TOP && toSide == AnchorSide.BOTTOM -> VERTICAL_HORIZONTAL_VERTICAL
                fromSide == AnchorSide.BOTTOM && toSide == AnchorSide.BOTTOM -> VERTICAL_HORIZONTAL_VERTICAL
                fromSide == AnchorSide.TOP && toSide == AnchorSide.TOP -> VERTICAL_HORIZONTAL_VERTICAL

                // Horizontal to vertical: single corner
                fromSide == AnchorSide.RIGHT && toSide == AnchorSide.TOP -> HORIZONTAL_FIRST
                fromSide == AnchorSide.RIGHT && toSide == AnchorSide.BOTTOM -> HORIZONTAL_FIRST
                fromSide == AnchorSide.LEFT && toSide == AnchorSide.TOP -> HORIZONTAL_FIRST
                fromSide == AnchorSide.LEFT && toSide == AnchorSide.BOTTOM -> HORIZONTAL_FIRST

                // Vertical to horizontal: single corner
                fromSide == AnchorSide.TOP && toSide == AnchorSide.LEFT -> VERTICAL_FIRST
                fromSide == AnchorSide.TOP && toSide == AnchorSide.RIGHT -> VERTICAL_FIRST
                fromSide == AnchorSide.BOTTOM && toSide == AnchorSide.LEFT -> VERTICAL_FIRST
                fromSide == AnchorSide.BOTTOM && toSide == AnchorSide.RIGHT -> VERTICAL_FIRST

                // Only source anchor known: start in the direction it faces
                fromSide == AnchorSide.LEFT || fromSide == AnchorSide.RIGHT -> HORIZONTAL_VERTICAL_HORIZONTAL
                fromSide == AnchorSide.TOP || fromSide == AnchorSide.BOTTOM -> VERTICAL_HORIZONTAL_VERTICAL

                // Only target anchor known: end in the direction it faces
                toSide == AnchorSide.LEFT || toSide == AnchorSide.RIGHT -> HORIZONTAL_VERTICAL_HORIZONTAL
                toSide == AnchorSide.TOP || toSide == AnchorSide.BOTTOM -> VERTICAL_HORIZONTAL_VERTICAL

                // Fallback: use horizontal-first as default
                else -> HORIZONTAL_VERTICAL_HORIZONTAL
            }
        }
    }
}

/**
 * Right-angle orthogonal edge with straight line segments (draw.io style).
 *
 * Renders an edge using only horizontal and vertical line segments,
 * creating a clean right-angle path like in draw.io or similar tools.
 *
 * Note: This component requires explicit routing. For automatic routing based on
 * anchor positions, use [StyledRightAngleEdgeContent] with a [KuiverEdge].
 *
 * @param from Start point of the edge
 * @param to End point of the edge
 * @param color Edge line color
 * @param strokeWidth Width of the edge line
 * @param showArrow Whether to show an arrow at the end
 * @param dashed Whether the edge should be dashed
 * @param dashLength Length of dashes (if dashed)
 * @param gapLength Length of gaps between dashes (if dashed)
 * @param routing The routing strategy for the edge segments
 * @param arrowSize Size of the arrow head
 * @param arrowDrawer Custom arrow drawing function
 */
@Composable
public fun RightAngleEdgeContent(
    from: Offset,
    to: Offset,
    color: Color = LocalKuiverColors.current.edge,
    strokeWidth: Dp = 3.dp,
    showArrow: Boolean = true,
    dashed: Boolean = false,
    dashLength: Dp = 10.dp,
    gapLength: Dp = 5.dp,
    routing: RightAngleRouting,
    arrowSize: Dp = 16.dp,
    arrowDrawer: ArrowDrawer = DefaultArrowDrawer
) {
    val density = LocalDensity.current
    val edgePath = remember(from, to, density, routing, showArrow, strokeWidth) {
        EdgePathFactory.createRightAnglePath(from, to, routing, density, showArrow, strokeWidth)
    }

    EdgePathCanvas(
        path = edgePath,
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

/**
 * Edge of horizontal and vertical segments with an optional label.
 *
 * Use [StyledRightAngleEdgeContent] to pick the routing from the edge anchors.
 *
 * @param from start point of the edge
 * @param to end point of the edge
 * @param label text shown on the edge, none when null
 * @param labelOffset position along the edge from 0 (start) to 1 (end), 0.5 by default
 * @param labelPlacement preset position, takes precedence over [labelOffset]
 * @param labelStyle style of the label
 * @param labelContent replaces the default label
 * @param color edge line color
 * @param strokeWidth width of the edge line, must be positive
 * @param showArrow whether to draw an arrow at the end
 * @param dashed whether the edge is dashed
 * @param dashLength length of dashes
 * @param gapLength length of gaps between dashes
 * @param routing order of the horizontal and vertical segments
 * @param arrowSize size of the arrow head
 * @param arrowDrawer draws the arrow head
 * @param minEdgeLengthForLabel shortest edge that still shows its label
 */
@Composable
public fun RightAngleEdgeContentWithLabel(
    from: Offset,
    to: Offset,
    label: String? = null,
    labelOffset: Float? = null,
    labelPlacement: LabelPlacement? = null,
    labelStyle: EdgeLabelStyle = KuiverDefaults.edgeLabelStyle(),
    labelContent: (@Composable (String) -> Unit)? = null,
    color: Color = LocalKuiverColors.current.edge,
    strokeWidth: Dp = 3.dp,
    showArrow: Boolean = true,
    dashed: Boolean = false,
    dashLength: Dp = 10.dp,
    gapLength: Dp = 5.dp,
    routing: RightAngleRouting,
    arrowSize: Dp = 16.dp,
    arrowDrawer: ArrowDrawer = DefaultArrowDrawer,
    minEdgeLengthForLabel: Dp = 50.dp
) {
    require(labelOffset == null || labelOffset in 0f..1f) {
        "labelOffset must be in range [0, 1], got $labelOffset"
    }
    require(strokeWidth > 0.dp) { "strokeWidth must be positive, got $strokeWidth" }
    require(minEdgeLengthForLabel >= 0.dp) {
        "minEdgeLengthForLabel must be non-negative, got $minEdgeLengthForLabel"
    }

    val offset = labelPlacement?.offset ?: labelOffset ?: 0.5f

    val density = LocalDensity.current
    val edgePath = remember(from, to, density, routing, showArrow, strokeWidth) {
        EdgePathFactory.createRightAnglePath(from, to, routing, density, showArrow, strokeWidth)
    }

    LabeledEdge(
        path = edgePath,
        color = color,
        strokeWidth = strokeWidth,
        showArrow = showArrow,
        dashed = dashed,
        dashLength = dashLength,
        gapLength = gapLength,
        arrowSize = arrowSize,
        arrowDrawer = arrowDrawer,
        label = label,
        labelOffset = offset,
        labelStyle = labelStyle,
        labelContent = labelContent,
        minEdgeLengthForLabel = minEdgeLengthForLabel
    )
}

/**
 * Right-angle edge routed from the anchor ids of [edge], see [AnchorSide.fromAnchorId] for the
 * names it understands.
 *
 * Back edges and self-loops are not supported.
 *
 * @param edge edge to draw, read for its anchors
 * @param from start point of the edge
 * @param to end point of the edge
 * @param color edge line color
 * @param strokeWidth width of the edge line, must be positive
 * @param showArrow whether to draw an arrow at the end
 * @param dashed whether the edge is dashed
 * @param dashLength length of dashes
 * @param gapLength length of gaps between dashes
 * @param arrowSize size of the arrow head
 * @param arrowDrawer draws the arrow head
 * @param label text shown on the edge, none when null
 * @param labelOffset position along the edge from 0 (start) to 1 (end), 0.5 by default
 * @param labelPlacement preset position, takes precedence over [labelOffset]
 * @param labelStyle style of the label
 * @param labelContent replaces the default label
 */
@Composable
public fun StyledRightAngleEdgeContent(
    edge: KuiverEdge,
    from: Offset,
    to: Offset,
    color: Color = LocalKuiverColors.current.edge,
    strokeWidth: Dp = 3.dp,
    showArrow: Boolean = true,
    dashed: Boolean = false,
    dashLength: Dp = 10.dp,
    gapLength: Dp = 5.dp,
    arrowSize: Dp = 16.dp,
    arrowDrawer: ArrowDrawer = DefaultArrowDrawer,
    label: String? = null,
    labelOffset: Float? = null,
    labelPlacement: LabelPlacement? = null,
    labelStyle: EdgeLabelStyle = KuiverDefaults.edgeLabelStyle(),
    labelContent: (@Composable (String) -> Unit)? = null
) {
    // Infer anchor sides from anchor IDs
    val fromSide = AnchorSide.fromAnchorId(edge.fromAnchor)
    val toSide = AnchorSide.fromAnchorId(edge.toAnchor)

    // Determine routing based on anchor sides
    val routing = RightAngleRouting.fromAnchorSides(fromSide, toSide)

    RightAngleEdgeContentWithLabel(
        from = from,
        to = to,
        label = label,
        labelOffset = labelOffset,
        labelPlacement = labelPlacement,
        labelStyle = labelStyle,
        labelContent = labelContent,
        color = color,
        strokeWidth = strokeWidth,
        showArrow = showArrow,
        dashed = dashed,
        dashLength = dashLength,
        gapLength = gapLength,
        routing = routing,
        arrowSize = arrowSize,
        arrowDrawer = arrowDrawer
    )
}
