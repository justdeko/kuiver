package com.dk.kuiver.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.dk.kuiver.model.EdgeType
import com.dk.kuiver.model.KuiverEdge

@Composable
fun EdgeContent(
    from: Offset,
    to: Offset,
    color: Color = Color.Black,
    strokeWidth: Float = 3f,
    showArrow: Boolean = true,
    dashed: Boolean = false,
    dashLength: Float = 10f,
    gapLength: Float = 5f,
    isSelfLoop: Boolean = false,
    loopRadius: Float = 40f,
    enableCurve: Boolean = false,
    arrowDrawer: ArrowDrawer = DefaultArrowDrawer
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (isSelfLoop) {
            drawSelfLoopEdge(
                from = from,
                to = to,
                color = color,
                strokeWidth = strokeWidth,
                showArrow = showArrow,
                dashed = dashed,
                dashLength = dashLength,
                gapLength = gapLength,
                loopRadius = loopRadius,
                arrowDrawer = arrowDrawer
            )
        } else {
            drawEdge(
                from = from,
                to = to,
                color = color,
                strokeWidth = strokeWidth,
                showArrow = showArrow,
                dashed = dashed,
                dashLength = dashLength,
                gapLength = gapLength,
                enableCurve = enableCurve,
                arrowDrawer = arrowDrawer
            )
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
    labelStyle: EdgeLabelStyle = EdgeLabelStyle(),
    labelContent: (@Composable (String) -> Unit)? = null,
    color: Color = Color.Black,
    strokeWidth: Float = 3f,
    showArrow: Boolean = true,
    dashed: Boolean = false,
    dashLength: Float = 10f,
    gapLength: Float = 5f,
    isSelfLoop: Boolean = false,
    loopRadius: Float = 40f,
    enableCurve: Boolean = false,
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
    require(loopRadius > 0f) { "loopRadius must be positive, got $loopRadius" }

    val offset = labelPlacement?.offset ?: labelOffset ?: 0.5f

    val edgePath = remember(from, to, isSelfLoop, enableCurve, loopRadius, showArrow, strokeWidth) {
        when {
            isSelfLoop -> EdgePathFactory.createSelfLoopPath(from, to, loopRadius, showArrow, strokeWidth)
            enableCurve -> EdgePathFactory.createCurvedPath(from, to, showArrow, strokeWidth)
            else -> EdgePathFactory.createStraightPath(from, to, showArrow, strokeWidth)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        EdgeContent(
            from = from,
            to = to,
            color = color,
            strokeWidth = strokeWidth,
            showArrow = showArrow,
            dashed = dashed,
            dashLength = dashLength,
            gapLength = gapLength,
            isSelfLoop = isSelfLoop,
            loopRadius = loopRadius,
            enableCurve = enableCurve,
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
    baseColor: Color = Color.Black,
    backEdgeColor: Color = Color(0xFFFF6B6B),
    strokeWidth: Float = 3f,
    loopRadius: Float = 40f,
    arrowDrawer: ArrowDrawer = DefaultArrowDrawer,
    label: String? = null,
    labelOffset: Float? = null,
    labelPlacement: LabelPlacement? = null,
    labelStyle: EdgeLabelStyle = EdgeLabelStyle(),
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
        arrowDrawer = arrowDrawer
    )
}
