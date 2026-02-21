package com.dk.kuiver.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
fun OrthogonalEdgeContent(
    from: Offset,
    to: Offset,
    color: Color = Color.Black,
    strokeWidth: Float = 3f,
    showArrow: Boolean = true,
    dashed: Boolean = false,
    dashLength: Float = 10f,
    gapLength: Float = 5f,
    curveFactor: Float = 0.5f,
    arrowDrawer: ArrowDrawer = DefaultArrowDrawer
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawOrthogonalEdge(
            from = from,
            to = to,
            color = color,
            strokeWidth = strokeWidth,
            showArrow = showArrow,
            dashed = dashed,
            dashLength = dashLength,
            gapLength = gapLength,
            curveFactor = curveFactor,
            arrowDrawer = arrowDrawer
        )
    }
}

/**
 * Orthogonal edge with S-curve path and label support.
 *
 * Renders an edge with horizontal tangents at both ends, creating a smooth
 * S-curve path. Supports customizable labels positioned along the curve path.
 * Labels automatically hide on edges shorter than minEdgeLengthForLabel and
 * rotate to stay readable.
 *
 * Note: This component is designed for forward/tree edges only. It does not support
 * back edges or self-loops. For graphs with back edges, handle the fallback yourself.
 *
 * @param from Start point of the edge
 * @param to End point of the edge
 * @param label Optional label text to display on the edge
 * @param labelOffset Position along the edge (0.0 = from, 1.0 = to). Must be in range [0, 1]
 * @param labelPlacement Preset position (START, CENTER, END). Takes precedence over labelOffset
 * @param labelStyle Styling configuration for the label
 * @param labelContent Optional custom composable for label rendering (overrides default)
 * @param color Edge line color
 * @param strokeWidth Width of the edge line. Must be positive
 * @param showArrow Whether to show an arrow at the end
 * @param dashed Whether the edge should be dashed
 * @param dashLength Length of dashes (if dashed)
 * @param gapLength Length of gaps between dashes (if dashed)
 * @param curveFactor How far control points extend horizontally (0.0-1.0, default 0.5)
 * @param arrowDrawer Custom arrow drawing function
 * @param minEdgeLengthForLabel Minimum edge length to show label. Must be non-negative
 */
@Composable
fun OrthogonalEdgeContentWithLabel(
    from: Offset,
    to: Offset,
    label: String? = null,
    labelOffset: Float? = null,
    labelPlacement: LabelPlacement? = null,
    labelStyle: EdgeLabelStyle = EdgeLabelStyle(),
    labelContent: (@Composable (String) -> Unit)? = null,
    color: Color = Color.Black,
    strokeWidth: Float = 3f,
    showArrow: Boolean = true,
    dashed: Boolean = false,
    dashLength: Float = 10f,
    gapLength: Float = 5f,
    curveFactor: Float = 0.5f,
    arrowDrawer: ArrowDrawer = DefaultArrowDrawer,
    minEdgeLengthForLabel: Float = 50f
) {
    require(labelOffset == null || labelOffset in 0f..1f) {
        "labelOffset must be in range [0, 1], got $labelOffset"
    }
    require(strokeWidth > 0f) { "strokeWidth must be positive, got $strokeWidth" }
    require(minEdgeLengthForLabel >= 0f) {
        "minEdgeLengthForLabel must be non-negative, got $minEdgeLengthForLabel"
    }

    val offset = labelPlacement?.offset ?: labelOffset ?: 0.5f

    val edgePath = remember(from, to, curveFactor, showArrow, strokeWidth) {
        EdgePathFactory.createOrthogonalPath(from, to, curveFactor, showArrow, strokeWidth)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        OrthogonalEdgeContent(
            from = from,
            to = to,
            color = color,
            strokeWidth = strokeWidth,
            showArrow = showArrow,
            dashed = dashed,
            dashLength = dashLength,
            gapLength = gapLength,
            curveFactor = curveFactor,
            arrowDrawer = arrowDrawer
        )

        if (label != null && label.isNotBlank()) {
            val labelPosition = remember(edgePath, offset, minEdgeLengthForLabel) {
                edgePath.calculateLabelPosition(offset, minEdgeLengthForLabel)
            }

            labelPosition?.let { pos ->
                EdgeLabel(label, pos, labelStyle, labelContent)
            }
        }
    }
}

/**
 * Represents which side of a node an anchor is positioned on.
 * Used to determine optimal routing for right-angle edges.
 */
enum class AnchorSide {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM;

    companion object {
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
        fun fromAnchorId(anchorId: String?): AnchorSide? {
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
enum class RightAngleRouting {
    /** Go horizontal first, then vertical */
    HORIZONTAL_FIRST,
    /** Go vertical first, then horizontal */
    VERTICAL_FIRST,
    /** Use a middle waypoint for 3-segment routing (horizontal-vertical-horizontal) */
    HORIZONTAL_VERTICAL_HORIZONTAL,
    /** Use a middle waypoint for 3-segment routing (vertical-horizontal-vertical) */
    VERTICAL_HORIZONTAL_VERTICAL;

    companion object {
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
        fun fromAnchorSides(fromSide: AnchorSide?, toSide: AnchorSide?): RightAngleRouting {
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
 * @param arrowDrawer Custom arrow drawing function
 */
@Composable
fun RightAngleEdgeContent(
    from: Offset,
    to: Offset,
    color: Color = Color.Black,
    strokeWidth: Float = 3f,
    showArrow: Boolean = true,
    dashed: Boolean = false,
    dashLength: Float = 10f,
    gapLength: Float = 5f,
    routing: RightAngleRouting,
    arrowDrawer: ArrowDrawer = DefaultArrowDrawer
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRightAngleEdge(
            from = from,
            to = to,
            color = color,
            strokeWidth = strokeWidth,
            showArrow = showArrow,
            dashed = dashed,
            dashLength = dashLength,
            gapLength = gapLength,
            routing = routing,
            arrowDrawer = arrowDrawer
        )
    }
}

/**
 * Right-angle orthogonal edge with label support (draw.io style).
 *
 * Renders an edge using only horizontal and vertical line segments with
 * optional label positioned along the path.
 *
 * Note: This component requires explicit routing. For automatic routing based on
 * anchor positions, use [StyledRightAngleEdgeContent] with a [KuiverEdge].
 *
 * @param from Start point of the edge
 * @param to End point of the edge
 * @param label Optional label text to display on the edge
 * @param labelOffset Position along the edge (0.0 = from, 1.0 = to). Must be in range [0, 1]
 * @param labelPlacement Preset position (START, CENTER, END). Takes precedence over labelOffset
 * @param labelStyle Styling configuration for the label
 * @param labelContent Optional custom composable for label rendering (overrides default)
 * @param color Edge line color
 * @param strokeWidth Width of the edge line. Must be positive
 * @param showArrow Whether to show an arrow at the end
 * @param dashed Whether the edge should be dashed
 * @param dashLength Length of dashes (if dashed)
 * @param gapLength Length of gaps between dashes (if dashed)
 * @param routing The routing strategy for the edge segments
 * @param arrowDrawer Custom arrow drawing function
 * @param minEdgeLengthForLabel Minimum edge length to show label. Must be non-negative
 */
@Composable
fun RightAngleEdgeContentWithLabel(
    from: Offset,
    to: Offset,
    label: String? = null,
    labelOffset: Float? = null,
    labelPlacement: LabelPlacement? = null,
    labelStyle: EdgeLabelStyle = EdgeLabelStyle(),
    labelContent: (@Composable (String) -> Unit)? = null,
    color: Color = Color.Black,
    strokeWidth: Float = 3f,
    showArrow: Boolean = true,
    dashed: Boolean = false,
    dashLength: Float = 10f,
    gapLength: Float = 5f,
    routing: RightAngleRouting,
    arrowDrawer: ArrowDrawer = DefaultArrowDrawer,
    minEdgeLengthForLabel: Float = 50f
) {
    require(labelOffset == null || labelOffset in 0f..1f) {
        "labelOffset must be in range [0, 1], got $labelOffset"
    }
    require(strokeWidth > 0f) { "strokeWidth must be positive, got $strokeWidth" }
    require(minEdgeLengthForLabel >= 0f) {
        "minEdgeLengthForLabel must be non-negative, got $minEdgeLengthForLabel"
    }

    val offset = labelPlacement?.offset ?: labelOffset ?: 0.5f

    val edgePath = remember(from, to, routing, showArrow, strokeWidth) {
        EdgePathFactory.createRightAnglePath(from, to, routing, showArrow, strokeWidth)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        RightAngleEdgeContent(
            from = from,
            to = to,
            color = color,
            strokeWidth = strokeWidth,
            showArrow = showArrow,
            dashed = dashed,
            dashLength = dashLength,
            gapLength = gapLength,
            routing = routing,
            arrowDrawer = arrowDrawer
        )

        if (label != null && label.isNotBlank()) {
            val labelPosition = remember(edgePath, offset, minEdgeLengthForLabel) {
                edgePath.calculateLabelPosition(offset, minEdgeLengthForLabel)
            }

            labelPosition?.let { pos ->
                EdgeLabel(label, pos, labelStyle, labelContent)
            }
        }
    }
}

/**
 * Right-angle edge that automatically determines routing from anchor information.
 *
 * The routing is automatically determined based on the anchor IDs specified in the
 * edge (e.g., "left", "right", "top", "bottom").
 *
 * Note: This component is designed for forward/tree edges only. It does not support
 * back edges or self-loops. For graphs with back edges, handle the fallback yourself.
 *
 * Supported anchor names (case-insensitive):
 * - Left side: "left", "l", "west", "w", "start"
 * - Right side: "right", "r", "east", "e", "end"
 * - Top side: "top", "t", "north", "n", "up"
 * - Bottom side: "bottom", "b", "south", "s", "down"
 *
 * @param edge The edge containing anchor information (fromAnchor and toAnchor)
 * @param from Start point of the edge (already resolved from anchor position)
 * @param to End point of the edge (already resolved from anchor position)
 * @param color Edge line color
 * @param strokeWidth Width of the edge line
 * @param showArrow Whether to show an arrow at the end
 * @param dashed Whether the edge should be dashed
 * @param dashLength Length of dashes (if dashed)
 * @param gapLength Length of gaps between dashes (if dashed)
 * @param arrowDrawer Custom arrow drawing function
 * @param label Optional label text to display on the edge
 * @param labelOffset Position along the edge (0.0 = from, 1.0 = to)
 * @param labelPlacement Preset position (START, CENTER, END)
 * @param labelStyle Styling configuration for the label
 * @param labelContent Optional custom composable for label rendering
 */
@Composable
fun StyledRightAngleEdgeContent(
    edge: KuiverEdge,
    from: Offset,
    to: Offset,
    color: Color = Color.Black,
    strokeWidth: Float = 3f,
    showArrow: Boolean = true,
    dashed: Boolean = false,
    dashLength: Float = 10f,
    gapLength: Float = 5f,
    arrowDrawer: ArrowDrawer = DefaultArrowDrawer,
    label: String? = null,
    labelOffset: Float? = null,
    labelPlacement: LabelPlacement? = null,
    labelStyle: EdgeLabelStyle = EdgeLabelStyle(),
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
        arrowDrawer = arrowDrawer
    )
}
