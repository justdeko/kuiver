package com.dk.kuiver.renderer

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.dk.kuiver.KuiverInteractionState
import com.dk.kuiver.model.AnchorOffset
import com.dk.kuiver.model.DEFAULT_NODE_SIZE
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode
import kotlin.math.abs
import kotlin.math.sqrt

private const val EDGE_PADDING = 4f

@Composable
internal fun RenderEdge(
    edge: KuiverEdge,
    fromNode: KuiverNode,
    toNode: KuiverNode,
    centerX: Dp,
    centerY: Dp,
    targets: NodePositions,
    transition: LayoutTransition,
    interaction: KuiverInteractionState,
    anchorRegistry: AnchorPositionRegistry,
    skipAnimation: Boolean,
    edgeContent: @Composable (KuiverEdge, Offset, Offset) -> Unit
) {
    // edgeContent takes endpoints by value so a moving edge has to resolve them in composition
    val (edgeStart, edgeEnd) = LocalDensity.current.resolveEdgeEndpoints(
        edge = edge,
        fromNode = fromNode,
        toNode = toNode,
        centerX = centerX,
        centerY = centerY,
        targets = targets,
        transition = transition,
        interaction = interaction,
        anchorRegistry = anchorRegistry,
        skipAnimation = skipAnimation
    )

    edgeContent(edge, edgeStart, edgeEnd)
}

/**
 * Reads node positions from [transition] and the live drag from [interaction], so a caller in the
 * draw or layout phase keeps a moving edge out of composition.
 */
internal fun Density.resolveEdgeEndpoints(
    edge: KuiverEdge,
    fromNode: KuiverNode,
    toNode: KuiverNode,
    centerX: Dp,
    centerY: Dp,
    targets: NodePositions,
    transition: LayoutTransition,
    interaction: KuiverInteractionState,
    anchorRegistry: AnchorPositionRegistry,
    skipAnimation: Boolean
): Pair<Offset, Offset> {
    val fromPosition = transition.positionOf(edge.fromId, targets, skipAnimation) +
            interaction.dragOffsetOf(edge.fromId)
    val toPosition = transition.positionOf(edge.toId, targets, skipAnimation) +
            interaction.dragOffsetOf(edge.toId)

    val fromCenter = Offset(
        centerX.toPx() + fromPosition.x.toPx(),
        centerY.toPx() + fromPosition.y.toPx()
    )
    val toCenter = Offset(
        centerX.toPx() + toPosition.x.toPx(),
        centerY.toPx() + toPosition.y.toPx()
    )

    // Get node dimensions (convert from DP to pixels, use default if dimensions not set)
    val defaultSize = DEFAULT_NODE_SIZE.toPx()
    val fromNodeWidth = fromNode.dimensions?.width?.toPx() ?: defaultSize
    val fromNodeHeight = fromNode.dimensions?.height?.toPx() ?: defaultSize
    val toNodeWidth = toNode.dimensions?.width?.toPx() ?: defaultSize
    val toNodeHeight = toNode.dimensions?.height?.toPx() ?: defaultSize

    return calculateEdgeEndpointsWithAnchors(
        edge = edge,
        fromNode = fromNode,
        toNode = toNode,
        fromCenter = fromCenter,
        toCenter = toCenter,
        fromNodeWidth = fromNodeWidth,
        fromNodeHeight = fromNodeHeight,
        toNodeWidth = toNodeWidth,
        toNodeHeight = toNodeHeight,
        anchorRegistry = anchorRegistry,
        isSelfLoop = edge.fromId == edge.toId,
        density = this
    )
}

/**
 * Calculate edge endpoints with anchor support.
 * Uses anchors if specified, otherwise falls back to center calculation.
 */
internal fun calculateEdgeEndpointsWithAnchors(
    edge: KuiverEdge,
    fromNode: KuiverNode,
    toNode: KuiverNode,
    fromCenter: Offset,
    toCenter: Offset,
    fromNodeWidth: Float,
    fromNodeHeight: Float,
    toNodeWidth: Float,
    toNodeHeight: Float,
    anchorRegistry: AnchorPositionRegistry,
    isSelfLoop: Boolean,
    density: Density
): Pair<Offset, Offset> {
    val fromAnchorOffset = edge.fromAnchor?.let { anchorRegistry.getAnchorOffset(fromNode.id, it) }
    val toAnchorOffset = edge.toAnchor?.let { anchorRegistry.getAnchorOffset(toNode.id, it) }

    val edgeStart = fromAnchorOffset?.let {
        calculateAbsoluteAnchorPosition(
            nodeCenter = fromCenter,
            nodeWidth = fromNodeWidth,
            nodeHeight = fromNodeHeight,
            anchorOffset = it,
            density = density
        )
    }
    val edgeEnd = toAnchorOffset?.let {
        calculateAbsoluteAnchorPosition(
            nodeCenter = toCenter,
            nodeWidth = toNodeWidth,
            nodeHeight = toNodeHeight,
            anchorOffset = it,
            density = density
        )
    }
    return when {
        edgeStart != null && edgeEnd != null -> {
            Pair(edgeStart, edgeEnd)
        }

        edgeStart != null && edgeEnd == null -> {
            val calculatedEnd = calculateGeometricEndpoint(
                from = edgeStart,
                targetCenter = toCenter,
                targetWidth = toNodeWidth,
                targetHeight = toNodeHeight,
                outbound = false
            )
            Pair(edgeStart, calculatedEnd)
        }

        edgeStart == null && edgeEnd != null -> {
            val calculatedStart = calculateGeometricEndpoint(
                from = edgeEnd,
                targetCenter = fromCenter,
                targetWidth = fromNodeWidth,
                targetHeight = fromNodeHeight,
                outbound = true
            )
            Pair(calculatedStart, edgeEnd)
        }

        else -> calculateEdgeEndpoints(
            fromCenter = fromCenter,
            toCenter = toCenter,
            fromNodeWidth = fromNodeWidth,
            fromNodeHeight = fromNodeHeight,
            toNodeWidth = toNodeWidth,
            toNodeHeight = toNodeHeight,
            isSelfLoop = isSelfLoop
        )
    }
}

fun calculateAbsoluteAnchorPosition(
    nodeCenter: Offset,
    nodeWidth: Float,
    nodeHeight: Float,
    anchorOffset: AnchorOffset,
    density: Density
): Offset {
    val nodeTopLeft = Offset(
        nodeCenter.x - nodeWidth / 2f,
        nodeCenter.y - nodeHeight / 2f
    )
    return with(density) {
        Offset(
            nodeTopLeft.x + anchorOffset.x.toPx(),
            nodeTopLeft.y + anchorOffset.y.toPx()
        )
    }
}

/**
 * Calculate geometric endpoint on node boundary when connecting from/to a specific point.
 *
 * @param from The fixed anchor point
 * @param targetCenter Center of the node to calculate endpoint on
 * @param targetWidth Width of the target node
 * @param targetHeight Height of the target node
 * @param outbound If true, calculates outbound endpoint (from anchor); if false, inbound (to anchor)
 */
internal fun calculateGeometricEndpoint(
    from: Offset,
    targetCenter: Offset,
    targetWidth: Float,
    targetHeight: Float,
    outbound: Boolean
): Offset {
    val direction = if (outbound) {
        Offset(from.x - targetCenter.x, from.y - targetCenter.y)
    } else {
        Offset(targetCenter.x - from.x, targetCenter.y - from.y)
    }

    val distance = sqrt(direction.x * direction.x + direction.y * direction.y)

    if (distance == 0f) return targetCenter

    val normalizedDirection = Offset(direction.x / distance, direction.y / distance)
    val nodeRadius = calculateNodeRadius(
        normalizedDirection,
        targetWidth / 2f,
        targetHeight / 2f
    )

    return if (outbound) {
        Offset(
            targetCenter.x + normalizedDirection.x * (nodeRadius + EDGE_PADDING),
            targetCenter.y + normalizedDirection.y * (nodeRadius + EDGE_PADDING)
        )
    } else {
        Offset(
            targetCenter.x - normalizedDirection.x * (nodeRadius + EDGE_PADDING),
            targetCenter.y - normalizedDirection.y * (nodeRadius + EDGE_PADDING)
        )
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
    val fromHalfWidth = fromNodeWidth / 2f
    val fromHalfHeight = fromNodeHeight / 2f
    val toHalfWidth = toNodeWidth / 2f
    val toHalfHeight = toNodeHeight / 2f

    // Calculate which edge of the rectangle the line intersects
    val fromNodeRadius = calculateNodeRadius(normalizedDirection, fromHalfWidth, fromHalfHeight)
    val toNodeRadius = calculateNodeRadius(normalizedDirection, toHalfWidth, toHalfHeight)

    // Calculate edge start and end points at node boundaries (with padding)
    val edgeStart = Offset(
        fromCenter.x + normalizedDirection.x * (fromNodeRadius + EDGE_PADDING),
        fromCenter.y + normalizedDirection.y * (fromNodeRadius + EDGE_PADDING)
    )

    val edgeEnd = Offset(
        toCenter.x - normalizedDirection.x * (toNodeRadius + EDGE_PADDING),
        toCenter.y - normalizedDirection.y * (toNodeRadius + EDGE_PADDING)
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

internal fun calculateNodeRadius(
    normalizedDirection: Offset,
    halfWidth: Float,
    halfHeight: Float
): Float = if (abs(normalizedDirection.x) * halfHeight > abs(normalizedDirection.y) * halfWidth) {
    halfWidth / abs(normalizedDirection.x) // vertical edge first
} else {
    halfHeight / abs(normalizedDirection.y) // horizontal edge first
}
