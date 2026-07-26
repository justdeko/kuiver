package com.dk.kuiver.model.layout

import androidx.compose.ui.unit.dp
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.model.NodeDimensions
import com.dk.kuiver.model.buildKuiverWithClassifiedEdges
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Guards the spatial grid in [forceDirected] against dropping repulsion pairs
 *
 * The grid must compute the same force model as the plain O(n^2) loop, skipping only pairs
 * the distance cutoff already discards
 */
class ForceDirectedGridTest {

    private companion object {
        // Below the ~0.002px a single dropped pair moves a node, above the float noise the
        // grid's summation order accumulates at the coordinate magnitudes of grown bounds
        const val TOLERANCE = 1e-3f
    }

    private fun testGraph(nodeCount: Int, seed: Int = 42): Kuiver {
        val rng = Random(seed)
        val nodes = (0 until nodeCount).map { i ->
            KuiverNode(
                id = "n$i",
                dimensions = NodeDimensions(
                    (20 + rng.nextInt(200)).toFloat().dp,
                    (20 + rng.nextInt(150)).toFloat().dp
                )
            )
        }
        val edges = (0 until nodeCount * 2)
            .map {
                KuiverEdge(
                    fromId = "n${rng.nextInt(nodeCount)}",
                    toId = "n${rng.nextInt(nodeCount)}"
                )
            }
            .filter { it.fromId != it.toId }
        return buildKuiverWithClassifiedEdges(nodes, edges)
    }

    @Test
    fun `grid matches brute force while the simulation is still deterministic`() {
        // The simulation is chaotic, so equivalence only holds over the first few
        // iterations - reordering the same additions moves nodes ~200px over 200 of them
        // Repulsion at the cutoff is negligible by design, a single dropped pair moves a
        // node ~0.002px, so a loose tolerance would accept a grid that drops interactions
        for (iterations in intArrayOf(1, 3, 5)) {
            // Above GRID_MIN_NODE_COUNT so the grid path is the one under test
            val kuiver = testGraph(nodeCount = 300)
            val config = LayoutConfig.ForceDirected(
                width = 1600.dp,
                height = 1200.dp,
                iterations = iterations
            )

            val grid = forceDirected(kuiver, config)
            val reference = bruteForceReference(kuiver, config)

            grid.nodes.forEach { (id, node) ->
                val (refX, refY) = reference.getValue(id)
                assertTrue(
                    abs(node.position.x.value - refX) < TOLERANCE &&
                            abs(node.position.y.value - refY) < TOLERANCE,
                    "Node $id diverged from brute force after $iterations iteration(s): " +
                            "grid=(${node.position.x}, ${node.position.y}) reference=($refX, $refY)"
                )
            }
        }
    }

    @Test
    fun `large graph spreads beyond the canvas but stays within the grown bounds`() {
        val kuiver = testGraph(nodeCount = 500)
        val config = LayoutConfig.ForceDirected(width = 2000.dp, height = 1500.dp)

        val result = forceDirected(kuiver, config)

        val avgNodeSize = kuiver.nodes.values
            .map { (it.dimensions!!.width.value + it.dimensions!!.height.value) / 2f }
            .average().toFloat()
        val scale = forceDirectedBoundsScale(500, avgNodeSize, 2000f, 1500f)
        assertTrue(scale > 1f, "500 nodes should need more room than a 2000x1500 canvas")

        assertEquals(500, result.nodes.size)
        result.nodes.values.forEach { node ->
            assertTrue(
                node.position.x.value.isFinite() && node.position.y.value.isFinite(),
                "Node ${node.id} has a non-finite position"
            )
            assertTrue(
                node.position.x in 0.dp..config.width * scale,
                "Node ${node.id} x=${node.position.x} outside bounds"
            )
            assertTrue(
                node.position.y in 0.dp..config.height * scale,
                "Node ${node.id} y=${node.position.y} outside bounds"
            )
        }
    }

    @Test
    fun `uniformly sized nodes take the grid path without dropping interactions`() {
        val nodes = (0 until 200).map { i ->
            KuiverNode(id = "n$i", dimensions = NodeDimensions(80f.dp, 60f.dp))
        }
        val kuiver = buildKuiverWithClassifiedEdges(nodes, emptyList())
        val config = LayoutConfig.ForceDirected(width = 1400.dp, height = 1000.dp, iterations = 3)

        val grid = forceDirected(kuiver, config)
        val reference = bruteForceReference(kuiver, config)

        grid.nodes.forEach { (id, node) ->
            val (refX, refY) = reference.getValue(id)
            assertTrue(
                abs(node.position.x.value - refX) < TOLERANCE &&
                        abs(node.position.y.value - refY) < TOLERANCE,
                "Node $id diverged: grid=(${node.position.x}, ${node.position.y}) reference=($refX, $refY)"
            )
        }
    }

    @Test
    fun `cancellation aborts the simulation`() {
        val kuiver = testGraph(nodeCount = 300)
        val config = LayoutConfig.ForceDirected(width = 1600.dp, height = 1200.dp)

        var checks = 0
        assertFailsWith<CancelledLayout> {
            forceDirected(kuiver, config) {
                checks++
                if (checks > 3) throw CancelledLayout()
            }
        }
    }

    @Test
    fun `cancellation is polled often enough to interrupt a single iteration`() {
        val kuiver = testGraph(nodeCount = 500)
        val config = LayoutConfig.ForceDirected(width = 1600.dp, height = 1200.dp, iterations = 1)

        var checks = 0
        forceDirected(kuiver, config) { checks++ }

        assertTrue(
            checks > 1,
            "Expected multiple cancellation checks within one iteration, got $checks"
        )
    }

    private class CancelledLayout : RuntimeException("cancelled")

    private fun bruteForceReference(
        kuiver: Kuiver,
        layoutConfig: LayoutConfig.ForceDirected
    ): Map<String, Pair<Float, Float>> {
        val canvasWidth = layoutConfig.width.value
        val canvasHeight = layoutConfig.height.value
        val fallbackNodeSize = layoutConfig.nodeSize.value

        val nodeIds = kuiver.nodes.keys.toList()
        val n = nodeIds.size
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

        var sizeSum = 0f
        for (i in 0 until n) {
            val dims = kuiver.nodes[nodeIds[i]]?.dimensions
            val s = if (dims != null) {
                (dims.width.value + dims.height.value) / 2f
            } else {
                fallbackNodeSize
            }
            sizeAvg[i] = s
            sizeSum += s
        }
        val avgNodeSize = (sizeSum / n).takeIf { it.isFinite() } ?: fallbackNodeSize

        val boundsScale = forceDirectedBoundsScale(n, avgNodeSize, canvasWidth, canvasHeight)
        val width = canvasWidth * boundsScale
        val height = canvasHeight * boundsScale

        val edgeFrom = IntArray(kuiver.edges.size)
        val edgeTo = IntArray(kuiver.edges.size)
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

        val centerX = width / 2f
        val centerY = height / 2f
        val initialRadius = min(width, height) * 0.3f
        val maxVelocity = 10f

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
        val margin = avgNodeSize
        val maxXBound = width - margin
        val maxYBound = height - margin
        val iterations = layoutConfig.iterations
        val coolingStep = if (iterations > 1) (1f - 0.1f) / (iterations - 1) else 0f

        repeat(iterations) { iteration ->
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
                    val maxRepulsionDistance = pairMinDistance * 3.0f
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

            val vMax = maxVelocity * (1f - coolingStep * iteration)
            val vMaxSq = vMax * vMax
            for (i in 0 until n) {
                var vx = velX[i] + forceX[i]
                var vy = velY[i] + forceY[i]
                val velMagSq = vx * vx + vy * vy
                if (velMagSq > vMaxSq) {
                    val scale = vMax / sqrt(velMagSq)
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

        return nodeIds.mapIndexed { i, id -> id to (posX[i] to posY[i]) }.toMap()
    }
}
