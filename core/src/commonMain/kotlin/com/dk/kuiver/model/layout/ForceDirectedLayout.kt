package com.dk.kuiver.model.layout

import androidx.compose.ui.geometry.Offset
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.buildKuiverWithClassifiedEdges
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Maximum distance for repulsion calculations (optimization: skip distant nodes)
// Nodes beyond this distance have negligible repulsion force
private const val MAX_REPULSION_DISTANCE_FACTOR = 3.0f

fun forceDirected(kuiver: Kuiver, layoutConfig: LayoutConfig = LayoutConfig()): Kuiver {
    if (layoutConfig.width <= 0f || layoutConfig.height <= 0f) {
        return kuiver // Return original if no valid dimensions
    }

    val nodes = kuiver.nodes.keys.toList()
    if (nodes.isEmpty()) return kuiver

    val positions = mutableMapOf<String, Offset>()
    val velocities = mutableMapOf<String, Offset>()

    // Pre-calculate node dimensions for all nodes (optimization: avoid recalculating in loops)
    val nodeSizes = nodes.associateWith { nodeId ->
        val dims = kuiver.nodes[nodeId]?.dimensions
        if (dims != null) {
            (dims.width.value + dims.height.value) / 2f
        } else {
            layoutConfig.nodeSize
        }
    }

    // Calculate average node size for spacing
    val avgNodeSize =
        nodeSizes.values.average().toFloat().takeIf { it.isFinite() } ?: layoutConfig.nodeSize

    val minDistance = avgNodeSize * 1.8f
    val centerX = layoutConfig.width / 2f
    val centerY = layoutConfig.height / 2f
    val initialRadius = min(layoutConfig.width, layoutConfig.height) * 0.3f

    // Simple, stable force parameters - scaled for better spacing
    val maxVelocity = 10f

    // Distribute nodes in a circle initially with good spacing
    nodes.forEachIndexed { index, nodeId ->
        if (nodes.size == 1) {
            positions[nodeId] = Offset(centerX, centerY)
        } else {
            val angle = (index * 2.0 * PI / nodes.size).toFloat()
            positions[nodeId] = Offset(
                centerX + initialRadius * cos(angle),
                centerY + initialRadius * sin(angle)
            )
        }
        velocities[nodeId] = Offset.Zero
    }

    // Reusable force map (optimization: reuse instead of recreating each iteration)
    val forces = mutableMapOf<String, Offset>()
    nodes.forEach { forces[it] = Offset.Zero }

    repeat(layoutConfig.iterations) {
        // Reset forces to zero (reuse map instead of recreating)
        nodes.forEach { forces[it] = Offset.Zero }

        // Calculate repulsion forces between all node pairs
        nodes.forEach { nodeA ->
            val nodeASizeAvg = nodeSizes[nodeA]!! // Pre-calculated

            nodes.forEach { nodeB ->
                if (nodeA != nodeB) {
                    val nodeBSizeAvg = nodeSizes[nodeB]!! // Pre-calculated

                    // Calculate minimum distance based on actual node sizes
                    val pairMinDistance = (nodeASizeAvg + nodeBSizeAvg) * 0.9f

                    val posA = positions[nodeA] ?: Offset.Zero
                    val posB = positions[nodeB] ?: Offset.Zero
                    val dx = posA.x - posB.x
                    val dy = posA.y - posB.y
                    val distance = sqrt(dx * dx + dy * dy)

                    // Distance culling: skip nodes beyond maximum repulsion distance
                    val maxRepulsionDistance = pairMinDistance * MAX_REPULSION_DISTANCE_FACTOR
                    if (distance > maxRepulsionDistance) return@forEach

                    if (distance > 1f) { // Avoid division by zero
                        // Use minimum distance to ensure nodes don't get too close
                        val effectiveDistance = max(distance, pairMinDistance)
                        val repulsionForce =
                            layoutConfig.repulsionStrength / (effectiveDistance * effectiveDistance)

                        // Add extra repulsion if nodes are too close
                        val extraRepulsion =
                            if (distance < pairMinDistance) layoutConfig.repulsionStrength * 0.5f else 0f
                        val totalRepulsion = repulsionForce + extraRepulsion

                        val fx = (dx / distance) * totalRepulsion
                        val fy = (dy / distance) * totalRepulsion

                        forces[nodeA] = forces[nodeA]!! + Offset(fx, fy)
                    }
                }
            }
        }

        // Calculate attraction forces along edges
        kuiver.edges.forEach { edge ->
            val posFrom = positions[edge.fromId] ?: Offset.Zero
            val posTo = positions[edge.toId] ?: Offset.Zero
            val dx = posTo.x - posFrom.x
            val dy = posTo.y - posFrom.y
            val distance = sqrt(dx * dx + dy * dy)

            if (distance > 1f) {
                val attractionForce = distance * layoutConfig.attractionStrength
                val fx = (dx / distance) * attractionForce
                val fy = (dy / distance) * attractionForce

                forces[edge.fromId] = forces[edge.fromId]!! + Offset(fx, fy)
                forces[edge.toId] = forces[edge.toId]!! - Offset(fx, fy)
            }
        }

        // Apply centering force to keep disconnected nodes from spreading too far
        // This is especially important for nodes with no edges
        val centeringStrength = 0.01f
        nodes.forEach { nodeId ->
            val pos = positions[nodeId] ?: Offset.Zero
            val dx = centerX - pos.x
            val dy = centerY - pos.y
            val centeringForce = Offset(dx * centeringStrength, dy * centeringStrength)
            forces[nodeId] = forces[nodeId]!! + centeringForce
        }

        // Update velocities and positions with bounds checking
        nodes.forEach { nodeId ->
            val force = forces[nodeId] ?: Offset.Zero
            var velocity = velocities[nodeId]!! + force

            // Limit velocity to prevent instability
            val velocityMagnitude = sqrt(velocity.x * velocity.x + velocity.y * velocity.y)
            if (velocityMagnitude > maxVelocity) {
                velocity *= (maxVelocity / velocityMagnitude)
            }

            velocity *= layoutConfig.damping
            velocities[nodeId] = velocity

            // Update position with bounds checking
            var newPos = positions[nodeId]!! + velocity

            // Keep nodes within canvas bounds with margin
            val margin = avgNodeSize
            newPos = Offset(
                newPos.x.coerceIn(margin, layoutConfig.width - margin),
                newPos.y.coerceIn(margin, layoutConfig.height - margin)
            )

            positions[nodeId] = newPos
        }
    }

    val updatedNodes = kuiver.nodes.mapValues { (nodeId, node) ->
        node.copy(position = positions[nodeId] ?: Offset.Zero)
    }

    // Build graph with classified edges in a single pass
    return buildKuiverWithClassifiedEdges(
        nodes = updatedNodes.values,
        originalEdges = kuiver.edges
    )
}
