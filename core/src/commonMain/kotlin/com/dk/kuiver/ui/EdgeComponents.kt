package com.dk.kuiver.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dk.kuiver.model.EdgeType
import com.dk.kuiver.model.KuiverEdge

/** An edge drawn on a canvas bounded to its geometry, see [EdgeCanvas]. */
@Composable
fun EdgeContent(
    from: Offset,
    to: Offset,
    color: Color = LocalKuiverColors.current.edge,
    strokeWidth: Dp = 3.dp,
    showArrow: Boolean = true,
    dashed: Boolean = false,
    dashLength: Dp = 10.dp,
    gapLength: Dp = 5.dp,
    isSelfLoop: Boolean = false,
    loopRadius: Dp = 40.dp,
    enableCurve: Boolean = false,
    arrowSize: Dp = 16.dp,
    arrowDrawer: ArrowDrawer = DefaultArrowDrawer
) {
    val edgePath =
        rememberEdgePath(from, to, isSelfLoop, enableCurve, loopRadius, showArrow, strokeWidth)

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

@Composable
private fun rememberEdgePath(
    from: Offset,
    to: Offset,
    isSelfLoop: Boolean,
    enableCurve: Boolean,
    loopRadius: Dp,
    showArrow: Boolean,
    strokeWidth: Dp
): EdgePath {
    val density = LocalDensity.current
    return remember(
        from, to, density, isSelfLoop, enableCurve, loopRadius, showArrow, strokeWidth
    ) {
        when {
            isSelfLoop -> EdgePathFactory.createSelfLoopPath(
                from,
                to,
                density,
                loopRadius,
                showArrow,
                strokeWidth
            )

            enableCurve ->
                EdgePathFactory.createCurvedPath(from, to, density, showArrow, strokeWidth)

            else -> EdgePathFactory.createStraightPath(from, to, density, showArrow, strokeWidth)
        }
    }
}

/**
 * Enhanced edge content that supports customizable labels.
 *
 * Renders an edge with optional label positioned along the edge path. Supports
 * all edge types (straight, curved, self-loops) and provides full customization
 * of label position, style, and content. Labels automatically hide on edges
 * shorter than minEdgeLengthForLabel and rotate to stay readable.
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
 * @param isSelfLoop Whether this is a self-loop edge
 * @param loopRadius Radius for self-loop arcs. Must be positive
 * @param enableCurve Whether to curve the edge (for back edges)
 * @param arrowSize Size of the arrow head
 * @param arrowDrawer Custom arrow drawing function
 * @param minEdgeLengthForLabel Minimum edge length to show label. Must be non-negative
 */
@Composable
fun EdgeContentWithLabel(
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
    isSelfLoop: Boolean = false,
    loopRadius: Dp = 40.dp,
    enableCurve: Boolean = false,
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
    require(loopRadius > 0.dp) { "loopRadius must be positive, got $loopRadius" }

    val offset = labelPlacement?.offset ?: labelOffset ?: 0.5f

    val edgePath =
        rememberEdgePath(from, to, isSelfLoop, enableCurve, loopRadius, showArrow, strokeWidth)

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

@Composable
internal fun LabeledEdge(
    path: EdgePath,
    color: Color,
    strokeWidth: Dp,
    showArrow: Boolean,
    dashed: Boolean,
    dashLength: Dp,
    gapLength: Dp,
    arrowSize: Dp,
    arrowDrawer: ArrowDrawer,
    label: String?,
    labelOffset: Float,
    labelStyle: EdgeLabelStyle,
    labelContent: (@Composable (String) -> Unit)?,
    minEdgeLengthForLabel: Dp
) {
    val edge = @Composable {
        EdgePathCanvas(
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

    if (label.isNullOrBlank()) {
        edge()
        return
    }

    val density: Density = LocalDensity.current
    val labelPosition = remember(path, density, labelOffset, minEdgeLengthForLabel) {
        // edgeLength is in pixels, so the dp threshold is resolved before comparing
        path.calculateLabelPosition(
            labelOffset,
            with(density) { minEdgeLengthForLabel.toPx() }
        )
    }

    if (labelPosition == null) {
        edge()
        return
    }

    Box {
        edge()
        EdgeLabel(label, labelPosition, labelStyle, labelContent)
    }
}

/**
 * Enhanced EdgeContent that styles edges based on their type.
 * Self-loops are rendered as arcs above nodes.
 * Back edges (cycle-creating) are dashed and curved.
 *
 * @param edge The edge to render
 * @param from Start point of the edge
 * @param to End point of the edge
 * @param baseColor Color for forward/tree edges (default: black)
 * @param backEdgeColor Color for back edges and self-loops (default: red)
 * @param strokeWidth Width of the edge line
 * @param loopRadius Radius for self-loop arcs
 * @param arrowSize Size of the arrow head
 * @param arrowDrawer Custom arrow drawing function
 * @param label Optional label text to display on the edge
 * @param labelOffset Position along the edge (0.0 = from, 1.0 = to). Must be in range [0, 1]
 * @param labelPlacement Preset position (START, CENTER, END). Takes precedence over labelOffset
 * @param labelStyle Styling configuration for the label
 * @param labelContent Optional custom composable for label rendering (overrides default)
 */
@Composable
fun StyledEdgeContent(
    edge: KuiverEdge,
    from: Offset,
    to: Offset,
    baseColor: Color = LocalKuiverColors.current.edge,
    backEdgeColor: Color = LocalKuiverColors.current.backEdge,
    strokeWidth: Dp = 3.dp,
    loopRadius: Dp = 40.dp,
    arrowSize: Dp = 16.dp,
    arrowDrawer: ArrowDrawer = DefaultArrowDrawer,
    label: String? = null,
    labelOffset: Float? = null,
    labelPlacement: LabelPlacement? = null,
    labelStyle: EdgeLabelStyle = KuiverDefaults.edgeLabelStyle(),
    labelContent: (@Composable (String) -> Unit)? = null
) {
    val (color, dashed) = when (edge.type) {
        EdgeType.SELF_LOOP -> Pair(backEdgeColor, true)
        EdgeType.BACK -> Pair(baseColor.copy(alpha = 0.7f), true)
        else -> Pair(baseColor, false)
    }

    val isSelfLoop = edge.fromId == edge.toId

    EdgeContentWithLabel(
        from = from,
        to = to,
        label = label,
        labelOffset = labelOffset,
        labelPlacement = labelPlacement,
        labelStyle = labelStyle,
        labelContent = labelContent,
        color = color,
        strokeWidth = strokeWidth,
        dashed = dashed,
        isSelfLoop = isSelfLoop,
        loopRadius = loopRadius,
        enableCurve = edge.type == EdgeType.BACK,
        arrowSize = arrowSize,
        arrowDrawer = arrowDrawer
    )
}
