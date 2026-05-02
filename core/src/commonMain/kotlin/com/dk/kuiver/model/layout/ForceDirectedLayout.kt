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

/**
 * Force-directed graph layout using physics simulation.
 *
 * Implements a spring-embedder model where:
 * - Nodes repel each other (simulating electrical charge)
 * - Connected nodes attract each other (simulating springs)
 * - A centering force keeps the graph from drifting
 *
 * The algorithm iteratively applies forces until the system reaches equilibrium,
 * producing an organic layout that reveals graph structure.
 *
 * References:
 * - Fruchterman & Reingold (1991): "Graph Drawing by Force-Directed Placement"
 * - Eades (1984): "A Heuristic for Graph Drawing"
 */
fun forceDirected(
    kuiver: Kuiver,
    layoutConfig: LayoutConfig.ForceDirected = LayoutConfig.ForceDirected()
): Kuiver {
    if (layoutConfig.width <= 0f || layoutConfig.height <= 0f) {
        return kuiver
    }

    val nodeIds = kuiver.nodes.keys.toList()
    val n = nodeIds.size
    if (n == 0) return kuiver

    // avoid Map<String, Offset> lookups and inline-class boxing
    // for large graphs (no JIT, no escape analysis to elide the boxing).
    val idToIndex = HashMap<String, Int>(n).apply {
        nodeIds.forEachIndexed { i, id -> put(id, i) }
    }

    val posX = FloatArray(n)
    val posY = FloatArray(n)
    val velX = FloatArray(n)
    val velY = FloatArray(n)
    val forceX = FloatArray(n)
    val forceY = FloatArray(n)
    val sizeAvg = FloatArray(n)

    // Pre-calculate node dimensions for all nodes (optimization: avoid recalculating in loops)
    var sizeSum = 0f
    for (i in 0 until n) {
        val dims = kuiver.nodes[nodeIds[i]]?.dimensions
        val s =
            if (dims != null) (dims.width.value + dims.height.value) / 2f else layoutConfig.nodeSize
        sizeAvg[i] = s
        sizeSum += s
    }
    val avgNodeSize = (sizeSum / n).takeIf { it.isFinite() } ?: layoutConfig.nodeSize

    // resolve edge endpoints to indices once. Edges referencing missing nodes are skipped
    val edgeCount = kuiver.edges.size
    val edgeFrom = IntArray(edgeCount)
    val edgeTo = IntArray(edgeCount)
    var validEdges = 0
    kuiver.edges.forEach { edge ->
        val f = idToIndex[edge.fromId]
        val t = idToIndex[edge.toId]
        if (f != null && t != null) {
            edgeFrom[validEdges] = f
            edgeTo[validEdges] = t
            validEdges++
        }
    }

    val centerX = layoutConfig.width / 2f
    val centerY = layoutConfig.height / 2f
    val initialRadius = min(layoutConfig.width, layoutConfig.height) * 0.3f

    val maxVelocity = 10f
    val maxVelocitySq = maxVelocity * maxVelocity

    // Distribute nodes in a circle initially with good spacing
    if (n == 1) {
        posX[0] = centerX
        posY[0] = centerY
    } else {
        for (i in 0 until n) {
            val angle = (i * 2.0 * PI / n).toFloat()
            posX[i] = centerX + initialRadius * cos(angle)
            posY[i] = centerY + initialRadius * sin(angle)
        }
    }

    val centeringStrength = 0.01f
    val repulsion = layoutConfig.repulsionStrength
    val extraRepulsionBase = repulsion * 0.5f
    val attraction = layoutConfig.attractionStrength
    val damping = layoutConfig.damping
    val width = layoutConfig.width
    val height = layoutConfig.height
    val margin = avgNodeSize
    val maxXBound = width - margin
    val maxYBound = height - margin

    repeat(layoutConfig.iterations) {
        for (i in 0 until n) {
            forceX[i] = 0f
            forceY[i] = 0f
        }

        for (a in 0 until n) {
            val ax = posX[a]
            val ay = posY[a]
            val aSize = sizeAvg[a]
            var fxA = 0f
            var fyA = 0f
            for (b in 0 until n) {
                if (a == b) continue
                val pairMinDistance = (aSize + sizeAvg[b]) * 0.9f
                val dx = ax - posX[b]
                val dy = ay - posY[b]
                val distSq = dx * dx + dy * dy

                val maxRepulsionDistance = pairMinDistance * MAX_REPULSION_DISTANCE_FACTOR
                if (distSq > maxRepulsionDistance * maxRepulsionDistance) continue
                if (distSq <= 1f) continue

                val distance = sqrt(distSq)
                val effectiveDistance = max(distance, pairMinDistance)
                val repulsionForce = repulsion / (effectiveDistance * effectiveDistance)
                val extraRepulsion = if (distance < pairMinDistance) extraRepulsionBase else 0f
                val totalRepulsion = repulsionForce + extraRepulsion

                fxA += (dx / distance) * totalRepulsion
                fyA += (dy / distance) * totalRepulsion
            }
            forceX[a] += fxA
            forceY[a] += fyA
        }

        for (e in 0 until validEdges) {
            val f = edgeFrom[e]
            val t = edgeTo[e]
            val dx = posX[t] - posX[f]
            val dy = posY[t] - posY[f]
            val distSq = dx * dx + dy * dy
            if (distSq <= 1f) continue
            val distance = sqrt(distSq)
            val attractionForce = distance * attraction
            val fx = (dx / distance) * attractionForce
            val fy = (dy / distance) * attractionForce
            forceX[f] += fx
            forceY[f] += fy
            forceX[t] -= fx
            forceY[t] -= fy
        }

        for (i in 0 until n) {
            forceX[i] += (centerX - posX[i]) * centeringStrength
            forceY[i] += (centerY - posY[i]) * centeringStrength
        }

        for (i in 0 until n) {
            var vx = velX[i] + forceX[i]
            var vy = velY[i] + forceY[i]

            val velMagSq = vx * vx + vy * vy
            if (velMagSq > maxVelocitySq) {
                val scale = maxVelocity / sqrt(velMagSq)
                vx *= scale
                vy *= scale
            }

            vx *= damping
            vy *= damping
            velX[i] = vx
            velY[i] = vy

            var nx = posX[i] + vx
            var ny = posY[i] + vy
            if (nx < margin) nx = margin else if (nx > maxXBound) nx = maxXBound
            if (ny < margin) ny = margin else if (ny > maxYBound) ny = maxYBound
            posX[i] = nx
            posY[i] = ny
        }
    }

    val updatedNodes = kuiver.nodes.mapValues { (nodeId, node) ->
        val idx = idToIndex[nodeId]
        if (idx != null) node.copy(position = Offset(posX[idx], posY[idx])) else node
    }

    return buildKuiverWithClassifiedEdges(
        nodes = updatedNodes.values,
        originalEdges = kuiver.edges
    )
}
