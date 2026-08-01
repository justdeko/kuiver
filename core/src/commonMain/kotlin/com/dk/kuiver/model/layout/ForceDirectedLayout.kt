package com.dk.kuiver.model.layout

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.buildKuiverWithClassifiedEdges
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Maximum distance for repulsion calculations (optimization: skip distant nodes)
// Nodes beyond this distance have negligible repulsion force
private const val MAX_REPULSION_DISTANCE_FACTOR = 3.0f

// Minimum pair separation, as a fraction of the two nodes' combined average size
private const val MIN_DISTANCE_FACTOR = 0.9f

// Below this node count the grid costs more than it saves, so a single cell is used
private const val GRID_MIN_NODE_COUNT = 128

// Cell width as a fraction of the largest interaction radius
// The scanned box is 2 * radius + cellSize wide, so cells near the radius waste most of it
private const val GRID_CELLS_PER_RADIUS = 4f

// Cell count ceiling, as a multiple of the node count
private const val MAX_CELLS_PER_NODE = 8f

// Velocity ceiling at the final iteration, as a fraction of the initial ceiling
private const val FINAL_COOLING_FACTOR = 0.1f

// Cancellation polling interval, in nodes
// Checking only between iterations leaves a superseded layout running a full O(n^2) pass
private const val CANCELLATION_CHECK_INTERVAL = 256

// Room the bounds grant each node, as a multiple of its average size
// Enough for the packing the centering force settles into, plus slack
private const val NODE_ROOM_FACTOR = 2.5f

/**
 * Factor by which the canvas grows into the simulation bounds. 1 while the graph has room on the
 * canvas; beyond that the bounds grow with the node count, so the boundary clamp does not fold
 * a large graph into overlap. The viewer's initial fit zooms out to match.
 */
internal fun forceDirectedBoundsScale(
    nodeCount: Int,
    avgNodeSize: Float,
    width: Float,
    height: Float
): Float {
    val nodeRoom = avgNodeSize * NODE_ROOM_FACTOR
    return max(1f, sqrt(nodeCount * nodeRoom * nodeRoom / (width * height)))
}

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
 * Every distance is a dp value, so the same graph lays out the same on every screen density.
 *
 * The canvas sets the aspect ratio and the minimum size of the simulation bounds. A graph with
 * more nodes than the canvas has room for is laid out over proportionally larger bounds, see
 * [forceDirectedBoundsScale].
 *
 * References:
 * - Fruchterman & Reingold (1991): "Graph Drawing by Force-Directed Placement"
 * - Eades (1984): "A Heuristic for Graph Drawing"
 */
fun forceDirected(
    kuiver: Kuiver,
    layoutConfig: LayoutConfig.ForceDirected = LayoutConfig.ForceDirected()
): Kuiver = forceDirected(kuiver, layoutConfig, checkCancellation = {})

internal fun forceDirected(
    kuiver: Kuiver,
    layoutConfig: LayoutConfig.ForceDirected,
    checkCancellation: () -> Unit
): Kuiver {
    if (layoutConfig.width <= 0.dp || layoutConfig.height <= 0.dp) {
        return kuiver
    }

    val nodeIds = kuiver.nodes.keys.toList()
    val n = nodeIds.size
    if (n == 0) return kuiver

    // One float per dp from here on, wrapped back up at the end
    val canvasWidth = layoutConfig.width.value
    val canvasHeight = layoutConfig.height.value
    val fallbackNodeSize = layoutConfig.nodeSize.value

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
    var sizeMax = 0f
    for (i in 0 until n) {
        val dims = kuiver.nodes[nodeIds[i]]?.dimensions
        val s =
            if (dims != null) (dims.width.value + dims.height.value) / 2f else fallbackNodeSize
        sizeAvg[i] = s
        sizeSum += s
        if (s > sizeMax) sizeMax = s
    }
    val avgNodeSize = (sizeSum / n).takeIf { it.isFinite() } ?: fallbackNodeSize

    // The canvas sets the aspect ratio and the minimum bounds, not a hard cage
    val boundsScale = forceDirectedBoundsScale(n, avgNodeSize, canvasWidth, canvasHeight)
    val width = canvasWidth * boundsScale
    val height = canvasHeight * boundsScale

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

    val centerX = width / 2f
    val centerY = height / 2f
    val initialRadius = min(width, height) * 0.3f

    val maxVelocity = 10f

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
    val margin = avgNodeSize
    val maxXBound = width - margin
    val maxYBound = height - margin

    // Spatial grid over the repulsion pass, skipping only pairs the cutoff already discards
    val maxInteractionDistance =
        2f * sizeMax * MIN_DISTANCE_FACTOR * MAX_REPULSION_DISTANCE_FACTOR
    // margin can exceed the canvas when it is tiny, so size the grid off the reachable extent
    val extentX = max(width, margin) + 1f
    val extentY = max(height, margin) + 1f
    val cellSize = if (n <= GRID_MIN_NODE_COUNT) {
        max(extentX, extentY)
    } else {
        max(
            max(maxInteractionDistance / GRID_CELLS_PER_RADIUS, 1f),
            // growing cells is safe, the ring count shrinks to match
            sqrt(extentX * extentY / (MAX_CELLS_PER_NODE * n))
        )
    }
    val cols = max(1, ceil(extentX / cellSize).toInt())
    val rows = max(1, ceil(extentY / cellSize).toInt())
    val cellCount = cols * rows

    // Cells each node scans out, bounding the partner size by sizeMax
    // The +1 covers cell-index rounding at both ends
    val cellRing = IntArray(n)
    for (i in 0 until n) {
        val radius = (sizeAvg[i] + sizeMax) * MIN_DISTANCE_FACTOR * MAX_REPULSION_DISTANCE_FACTOR
        cellRing[i] = (radius / cellSize).toInt() + 1
    }

    // Counting-sort buckets
    val nodeCell = IntArray(n)
    val cellStart = IntArray(cellCount + 1)
    val cellCursor = IntArray(cellCount)
    val cellItems = IntArray(n)

    // Node data mirrored in cell order so the repulsion pass reads contiguous memory
    val gridPosX = FloatArray(n)
    val gridPosY = FloatArray(n)
    val gridSize = FloatArray(n)
    val gridRing = IntArray(n)
    val gridCell = IntArray(n)
    val gridForceX = FloatArray(n)
    val gridForceY = FloatArray(n)

    val iterations = layoutConfig.iterations
    // Linear cooling, so late iterations refine the layout instead of overshooting it
    val coolingStep =
        if (iterations > 1) (1f - FINAL_COOLING_FACTOR) / (iterations - 1) else 0f

    repeat(iterations) { iteration ->
        checkCancellation()

        // Bin nodes into cells for this iteration's positions
        cellCursor.fill(0)
        for (i in 0 until n) {
            val cx = (posX[i] / cellSize).toInt().coerceIn(0, cols - 1)
            val cy = (posY[i] / cellSize).toInt().coerceIn(0, rows - 1)
            val cell = cy * cols + cx
            nodeCell[i] = cell
            cellCursor[cell]++
        }
        var offset = 0
        for (c in 0 until cellCount) {
            cellStart[c] = offset
            offset += cellCursor[c]
            cellCursor[c] = cellStart[c]
        }
        cellStart[cellCount] = offset
        for (i in 0 until n) {
            val cell = nodeCell[i]
            val slot = cellCursor[cell]
            cellCursor[cell] = slot + 1
            cellItems[slot] = i
            gridPosX[slot] = posX[i]
            gridPosY[slot] = posY[i]
            gridSize[slot] = sizeAvg[i]
            gridRing[slot] = cellRing[i]
            gridCell[slot] = cell
        }

        for (a in 0 until n) {
            if (a % CANCELLATION_CHECK_INTERVAL == 0) checkCancellation()

            val ax = gridPosX[a]
            val ay = gridPosY[a]
            val aSize = gridSize[a]
            var fxA = 0f
            var fyA = 0f

            val cell = gridCell[a]
            val cellX = cell % cols
            val cellY = cell / cols
            val ring = gridRing[a]
            val minCellY = max(0, cellY - ring)
            val maxCellY = min(rows - 1, cellY + ring)
            val minCellX = max(0, cellX - ring)
            val maxCellX = min(cols - 1, cellX + ring)

            for (ny in minCellY..maxCellY) {
                // Cells in a row are consecutive buckets, so a row is one contiguous range
                val rowBase = ny * cols
                var slot = cellStart[rowBase + minCellX]
                val slotEnd = cellStart[rowBase + maxCellX + 1]
                while (slot < slotEnd) {
                    val b = slot++
                    if (a == b) continue
                    val pairMinDistance = (aSize + gridSize[b]) * MIN_DISTANCE_FACTOR
                    val dx = ax - gridPosX[b]
                    val dy = ay - gridPosY[b]
                    val distSq = dx * dx + dy * dy

                    val maxRepulsionDistance = pairMinDistance * MAX_REPULSION_DISTANCE_FACTOR
                    if (distSq > maxRepulsionDistance * maxRepulsionDistance) continue
                    if (distSq <= 1f) continue

                    val distance = sqrt(distSq)
                    val effectiveDistance = max(distance, pairMinDistance)
                    val repulsionForce = repulsion / (effectiveDistance * effectiveDistance)
                    val extraRepulsion =
                        if (distance < pairMinDistance) extraRepulsionBase else 0f
                    val totalRepulsion = repulsionForce + extraRepulsion

                    fxA += (dx / distance) * totalRepulsion
                    fyA += (dy / distance) * totalRepulsion
                }
            }
            gridForceX[a] = fxA
            gridForceY[a] = fyA
        }

        // Scatter cell-ordered repulsion back onto node indices
        for (slot in 0 until n) {
            val i = cellItems[slot]
            forceX[i] = gridForceX[slot]
            forceY[i] = gridForceY[slot]
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

        val coolingMaxVelocity = maxVelocity * (1f - coolingStep * iteration)
        val coolingMaxVelocitySq = coolingMaxVelocity * coolingMaxVelocity

        for (i in 0 until n) {
            var vx = velX[i] + forceX[i]
            var vy = velY[i] + forceY[i]

            val velMagSq = vx * vx + vy * vy
            if (velMagSq > coolingMaxVelocitySq) {
                val scale = coolingMaxVelocity / sqrt(velMagSq)
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
        if (idx != null) node.copy(position = DpOffset(posX[idx].dp, posY[idx].dp)) else node
    }

    return buildKuiverWithClassifiedEdges(
        nodes = updatedNodes.values,
        originalEdges = kuiver.edges
    )
}
