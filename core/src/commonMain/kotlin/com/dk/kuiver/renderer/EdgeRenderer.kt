package com.dk.kuiver.renderer

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dk.kuiver.model.DEFAULT_NODE_SIZE_DP
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode
import kotlin.math.abs
import kotlin.math.sqrt

@Composable
internal fun RenderEdge(
    edge: KuiverEdge,
    fromNode: KuiverNode,
    toNode: KuiverNode,
    centerX: Dp,
    centerY: Dp,
    graphCenterX: Float,
    graphCenterY: Float,
    animationSpec: AnimationSpec<Offset>,
    edgeContent: @Composable (KuiverEdge, Offset, Offset) -> Unit
) {
    val density = LocalDensity.current
    val isSelfLoop = edge.fromId == edge.toId

    with(density) {
        val targetFromCenter = Offset(
            centerX.toPx() + (fromNode.position.x - graphCenterX).dp.toPx(),
            centerY.toPx() + (fromNode.position.y - graphCenterY).dp.toPx()
        )
        val targetToCenter = Offset(
            centerX.toPx() + (toNode.position.x - graphCenterX).dp.toPx(),
            centerY.toPx() + (toNode.position.y - graphCenterY).dp.toPx()
        )

        val animatedFromCenter by animateOffsetAsState(
            targetValue = targetFromCenter,
            animationSpec = animationSpec,
            label = "edge_from_${edge.fromId}_${edge.toId}"
        )

        val animatedToCenter by animateOffsetAsState(
            targetValue = targetToCenter,
            animationSpec = animationSpec,
            label = "edge_to_${edge.fromId}_${edge.toId}"
        )

        // Get node dimensions (convert from DP to pixels, use default if dimensions not set)
        val defaultSize = DEFAULT_NODE_SIZE_DP.toPx()
        val fromNodeWidth = fromNode.dimensions?.width?.toPx() ?: defaultSize
        val fromNodeHeight = fromNode.dimensions?.height?.toPx() ?: defaultSize
        val toNodeWidth = toNode.dimensions?.width?.toPx() ?: defaultSize
        val toNodeHeight = toNode.dimensions?.height?.toPx() ?: defaultSize

        // Calculate edge endpoints at rectangle boundaries
        val (edgeStart, edgeEnd) = calculateEdgeEndpoints(
            fromCenter = animatedFromCenter,
            toCenter = animatedToCenter,
            fromNodeWidth = fromNodeWidth,
            fromNodeHeight = fromNodeHeight,
            toNodeWidth = toNodeWidth,
            toNodeHeight = toNodeHeight,
            isSelfLoop = isSelfLoop
        )

        edgeContent(edge, edgeStart, edgeEnd)
    }
}

/**
 * Calculate where the edge should start and end based on rectangle intersections.
 * Returns a pair of (start, end) offsets at the boundaries of the node rectangles.
 * For self-loops, returns special endpoints at the top of the node.
 */
private fun calculateEdgeEndpoints(
    fromCenter: Offset,
    toCenter: Offset,
    fromNodeWidth: Float,
    fromNodeHeight: Float,
    toNodeWidth: Float,
    toNodeHeight: Float,
    isSelfLoop: Boolean = false
): Pair<Offset, Offset> {
    // Handle self-loops specially
    if (isSelfLoop) {
        return calculateSelfLoopEndpoints(
            center = fromCenter,
            nodeWidth = fromNodeWidth,
            nodeHeight = fromNodeHeight
        )
    }

    val direction = Offset(toCenter.x - fromCenter.x, toCenter.y - fromCenter.y)
    val distance = sqrt(direction.x * direction.x + direction.y * direction.y)

    if (distance == 0f) return Pair(fromCenter, toCenter)

    val normalizedDirection = Offset(direction.x / distance, direction.y / distance)

    // Calculate intersection with rectangle edges based on direction
    // Small padding to account for borders
    val padding = 4f
    val fromHalfWidth = fromNodeWidth / 2f + padding
    val fromHalfHeight = fromNodeHeight / 2f + padding
    val toHalfWidth = toNodeWidth / 2f + padding
    val toHalfHeight = toNodeHeight / 2f + padding

    // Calculate which edge of the rectangle the line intersects
    val fromNodeRadius = calculateNodeRadius(normalizedDirection, fromHalfWidth, fromHalfHeight)
    val toNodeRadius = calculateNodeRadius(normalizedDirection, toHalfWidth, toHalfHeight)

    // Calculate edge start and end points at node boundaries
    val edgeStart = Offset(
        fromCenter.x + normalizedDirection.x * fromNodeRadius,
        fromCenter.y + normalizedDirection.y * fromNodeRadius
    )

    val edgeEnd = Offset(
        toCenter.x - normalizedDirection.x * toNodeRadius,
        toCenter.y - normalizedDirection.y * toNodeRadius
    )

    return Pair(edgeStart, edgeEnd)
}

/**
 * Calculate endpoints for a self-loop edge.
 * Returns start and end points at the top of the node where the loop should connect.
 */
private fun calculateSelfLoopEndpoints(
    center: Offset,
    nodeWidth: Float,
    nodeHeight: Float
): Pair<Offset, Offset> {
    // Place self-loop at the top of the node
    val halfWidth = nodeWidth / 2f
    val halfHeight = nodeHeight / 2f

    // Start point: top-left of node
    val startPoint = Offset(
        center.x - halfWidth * 0.3f,
        center.y - halfHeight
    )

    // End point: top-right of node
    val endPoint = Offset(
        center.x + halfWidth * 0.3f,
        center.y - halfHeight
    )

    return Pair(startPoint, endPoint)
}

private fun calculateNodeRadius(
    normalizedDirection: Offset,
    halfWidth: Float,
    halfHeight: Float
): Float = if (abs(normalizedDirection.x) * halfHeight > abs(normalizedDirection.y) * halfWidth) {
    halfWidth / abs(normalizedDirection.x) // vertical edge first
} else {
    halfHeight / abs(normalizedDirection.y) // horizontal edge first
}
