package com.dk.kuiver.model.layout

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.buildKuiverWithClassifiedEdges

// Clearance kept between levels, on top of the largest node along the flow direction
private val LEVEL_CLEARANCE = 60.dp

// Clearance kept between nodes of a level, on top of the largest node across the flow direction
private val NODE_CLEARANCE = 40.dp

/**
 * Offset that centers a layout of [extent] in a canvas of [canvas], or none while the canvas is
 * still unmeasured.
 */
private fun centeringOffset(canvas: Dp, extent: Dp): Dp =
    if (canvas > 0.dp) (canvas - extent) / 2f else 0.dp

/**
 * Sugiyama hierarchical layout algorithm.
 *
 * Implements the four-phase approach for layered graph drawing:
 * 1. Cycle removal - Identify and handle back edges using DFS
 * 2. Layer assignment - Assign nodes to levels using longest path
 * 3. Crossing minimization - Reduce edge crossings with barycenter heuristic
 * 4. Coordinate assignment - Position nodes within their assigned layers
 *
 * Works in [androidx.compose.ui.unit.Dp] throughout, from the canvas size it is given to the
 * positions it writes.
 *
 * References:
 * - Sugiyama et al. (1981): "Methods for Visual Understanding of Hierarchical System Structures"
 * - Battista et al. (1998): "Graph Drawing: Algorithms for the Visualization of Graphs"
 */
internal fun hierarchical(
    kuiver: Kuiver,
    layoutConfig: LayoutConfig.Hierarchical = LayoutConfig.Hierarchical()
): Kuiver {
    // Phase 1: Cycle Removal
    val (acyclicEdges, _) = if (kuiver.hasCycles()) {
        separateBackEdges(kuiver)
    } else {
        Pair(kuiver.edges.toList(), emptyList())
    }

    // Build adjacency maps
    val parentMap = mutableMapOf<String, MutableSet<String>>()
    val childrenMap = mutableMapOf<String, MutableSet<String>>()
    acyclicEdges.forEach { edge ->
        parentMap.getOrPut(edge.toId) { mutableSetOf() }.add(edge.fromId)
        childrenMap.getOrPut(edge.fromId) { mutableSetOf() }.add(edge.toId)
    }

    // Phase 2: Layer Assignment using longest path
    val levels = mutableMapOf<String, Int>()
    val pendingParents = mutableMapOf<String, Int>()
    val ready = ArrayDeque<String>()
    kuiver.nodes.keys.forEach { nodeId ->
        val parentCount = parentMap[nodeId]?.size ?: 0
        levels[nodeId] = 0
        pendingParents[nodeId] = parentCount
        if (parentCount == 0) ready.addLast(nodeId)
    }

    while (ready.isNotEmpty()) {
        val nodeId = ready.removeFirst()
        val childLevel = levels.getValue(nodeId) + 1
        childrenMap[nodeId]?.forEach { child ->
            levels[child] = maxOf(levels.getValue(child), childLevel)
            val pending = pendingParents.getValue(child) - 1
            pendingParents[child] = pending
            if (pending == 0) ready.addLast(child)
        }
    }

    // Handle isolated nodes
    val maxConnectedLevel = levels.values.maxOrNull() ?: 0
    kuiver.nodes.keys.forEach { nodeId ->
        val hasEdges = (parentMap[nodeId]?.isNotEmpty() == true) ||
                (childrenMap[nodeId]?.isNotEmpty() == true)
        if (!hasEdges) {
            levels[nodeId] = maxConnectedLevel + 1
        }
    }

    val nodesByLevel = levels.entries.groupBy({ it.value }, { it.key })
    val maxLevel = nodesByLevel.keys.maxOrNull() ?: 0

    // Phase 3: Crossing Minimization
    val orderedNodes = minimizeCrossings(nodesByLevel, maxLevel, parentMap, childrenMap)

    val adjustedNodes = avoidBypassEdgeObstruction(kuiver, orderedNodes, levels)

    // Phase 4: Coordinate Assignment
    val maxNodeWidth = kuiver.nodes.values.maxOfOrNull {
        it.dimensions?.width ?: layoutConfig.nodeSize
    } ?: layoutConfig.nodeSize
    val maxNodeHeight = kuiver.nodes.values.maxOfOrNull {
        it.dimensions?.height ?: layoutConfig.nodeSize
    } ?: layoutConfig.nodeSize

    val updatedNodes = kuiver.nodes.mapValues { (nodeId, node) ->
        val level = levels[nodeId] ?: 0
        val nodesInLevel = adjustedNodes[level] ?: emptyList()
        val indexInLevel = nodesInLevel
            .indexOfFirst { it is LevelEntry.Node && it.id == nodeId }
            .takeIf { it >= 0 } ?: 0

        val (x, y) = when (layoutConfig.direction) {
            LayoutDirection.HORIZONTAL -> {
                val levelSpacing =
                    maxOf(layoutConfig.levelSpacing, maxNodeWidth + LEVEL_CLEARANCE)
                val nodeSpacing = maxOf(layoutConfig.nodeSpacing, maxNodeHeight + NODE_CLEARANCE)

                // Dp only multiplies with the count on the right, hence the operand order
                val layoutWidth = levelSpacing * maxLevel
                val layoutHeight = nodeSpacing * (nodesByLevel.values.maxOfOrNull { it.size } ?: 1)
                val centerX = centeringOffset(layoutConfig.width, layoutWidth)
                val centerY = centeringOffset(layoutConfig.height, layoutHeight)

                val levelHeight = nodeSpacing * nodesInLevel.size
                val xPos = levelSpacing * level + centerX
                val yPos =
                    nodeSpacing * indexInLevel - levelHeight / 2f + nodeSpacing / 2f + centerY
                Pair(xPos, yPos)
            }

            LayoutDirection.VERTICAL -> {
                val levelSpacing =
                    maxOf(layoutConfig.levelSpacing, maxNodeHeight + LEVEL_CLEARANCE)
                val nodeSpacing = maxOf(layoutConfig.nodeSpacing, maxNodeWidth + NODE_CLEARANCE)

                val layoutWidth = nodeSpacing * (nodesByLevel.values.maxOfOrNull { it.size } ?: 1)
                val layoutHeight = levelSpacing * maxLevel
                val centerX = centeringOffset(layoutConfig.width, layoutWidth)
                val centerY = centeringOffset(layoutConfig.height, layoutHeight)

                val levelWidth = nodeSpacing * nodesInLevel.size
                val xPos = nodeSpacing * indexInLevel - levelWidth / 2f + nodeSpacing / 2f + centerX
                val yPos = levelSpacing * level + centerY
                Pair(xPos, yPos)
            }
        }

        node.copy(position = DpOffset(x, y))
    }

    return buildKuiverWithClassifiedEdges(
        nodes = updatedNodes.values,
        originalEdges = kuiver.edges
    )
}

/**
 * Phase 3: Crossing Minimization using barycenter heuristic
 */
private fun minimizeCrossings(
    nodesByLevel: Map<Int, List<String>>,
    maxLevel: Int,
    parentMap: Map<String, Set<String>>,
    childrenMap: Map<String, Set<String>>
): Map<Int, List<String>> {
    val result = nodesByLevel.toMutableMap()
    var noChangeCount = 0

    repeat(10) {
        val previous = result.toMap()

        // Downward sweep
        for (level in 1..maxLevel) {
            val current = result[level] ?: continue
            val prev = result[level - 1] ?: continue

            result[level] = current.sortedBy { nodeId ->
                val parents = parentMap[nodeId] ?: emptySet()
                val positions = parents.mapNotNull { prev.indexOf(it).takeIf { i -> i >= 0 } }
                positions.average().takeIf { !it.isNaN() } ?: Double.MAX_VALUE
            }
        }

        // Upward sweep
        for (level in maxLevel - 1 downTo 0) {
            val current = result[level] ?: continue
            val next = result[level + 1] ?: continue

            result[level] = current.sortedBy { nodeId ->
                val children = childrenMap[nodeId] ?: emptySet()
                val positions = children.mapNotNull { next.indexOf(it).takeIf { i -> i >= 0 } }
                positions.average().takeIf { !it.isNaN() } ?: Double.MAX_VALUE
            }
        }

        if (result == previous) {
            if (++noChangeCount >= 2) return result
        } else {
            noChangeCount = 0
        }
    }

    return result
}

private sealed interface LevelEntry {
    data class Node(val id: String) : LevelEntry
    data object Spacer : LevelEntry
}

/**
 * Adds spacers at intermediate levels for bypass edges to reduce visual obstruction
 */
private fun avoidBypassEdgeObstruction(
    kuiver: Kuiver,
    nodesByLevel: Map<Int, List<String>>,
    levels: Map<String, Int>
): Map<Int, List<LevelEntry>> {
    val result = nodesByLevel
        .mapValues { (_, ids) -> ids.map { LevelEntry.Node(it) as LevelEntry } }
        .toMutableMap()

    // Find bypass edges (edges spanning multiple levels)
    val bypassEdges = kuiver.edges.filter { edge ->
        val fromLevel = levels[edge.fromId] ?: 0
        val toLevel = levels[edge.toId] ?: 0
        toLevel - fromLevel > 1
    }

    // Add spacers at intermediate levels
    bypassEdges.forEach { bypassEdge ->
        val fromLevel = levels[bypassEdge.fromId] ?: 0
        val toLevel = levels[bypassEdge.toId] ?: 0

        for (intermediateLevel in fromLevel + 1 until toLevel) {
            val nodesAtLevel = result[intermediateLevel] ?: continue

            if (nodesAtLevel.size == 1) {
                result[intermediateLevel] = listOf(LevelEntry.Spacer, nodesAtLevel[0])
            } else {
                val midIndex = nodesAtLevel.size / 2
                val reordered = nodesAtLevel.take(midIndex) +
                        listOf(LevelEntry.Spacer) +
                        nodesAtLevel.drop(midIndex)
                result[intermediateLevel] = reordered
            }
        }
    }

    return result
}

/**
 * Phase 1: Cycle Removal using DFS
 */
private fun separateBackEdges(kuiver: Kuiver): Pair<List<KuiverEdge>, List<KuiverEdge>> {
    val forwardEdges = mutableListOf<KuiverEdge>()
    val backEdges = mutableListOf<KuiverEdge>()
    val visited = mutableSetOf<String>()
    val inPath = mutableSetOf<String>()
    val classified = mutableSetOf<KuiverEdge>()

    val adjacency = mutableMapOf<String, MutableList<KuiverEdge>>()
    kuiver.edges.forEach { edge ->
        adjacency.getOrPut(edge.fromId) { mutableListOf() }.add(edge)
    }

    val iterStack = ArrayDeque<Pair<String, Iterator<KuiverEdge>>>()

    fun enter(nodeId: String) {
        visited.add(nodeId)
        inPath.add(nodeId)
        iterStack.addLast(
            nodeId to (adjacency[nodeId]?.iterator() ?: emptyList<KuiverEdge>().iterator())
        )
    }

    fun runDfs(start: String) {
        enter(start)
        while (iterStack.isNotEmpty()) {
            val (nodeId, iter) = iterStack.last()
            if (!iter.hasNext()) {
                inPath.remove(nodeId)
                iterStack.removeLast()
                continue
            }
            val edge = iter.next()
            if (edge in classified) continue
            classified.add(edge)
            when {
                edge.fromId == edge.toId || edge.toId in inPath -> backEdges.add(edge)
                edge.toId !in visited -> {
                    forwardEdges.add(edge)
                    enter(edge.toId)
                }

                else -> forwardEdges.add(edge)
            }
        }
    }

    kuiver.nodes.keys.forEach { if (it !in visited) runDfs(it) }
    kuiver.edges.forEach { if (it !in classified) forwardEdges.add(it) }

    return Pair(forwardEdges, backEdges)
}
