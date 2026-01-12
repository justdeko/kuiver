package com.dk.kuiver.model.layout

import androidx.compose.ui.geometry.Offset
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.buildKuiverWithClassifiedEdges

/**
 * Sugiyama hierarchical layout algorithm.
 *
 * Implements the four-phase approach for layered graph drawing:
 * 1. Cycle removal - Identify and handle back edges using DFS
 * 2. Layer assignment - Assign nodes to levels using longest path
 * 3. Crossing minimization - Reduce edge crossings with barycenter heuristic
 * 4. Coordinate assignment - Position nodes within their assigned layers
 *
 * References:
 * - Sugiyama et al. (1981): "Methods for Visual Understanding of Hierarchical System Structures"
 * - Battista et al. (1998): "Graph Drawing: Algorithms for the Visualization of Graphs"
 */
internal fun hierarchical(kuiver: Kuiver, layoutConfig: LayoutConfig.Hierarchical = LayoutConfig.Hierarchical()): Kuiver {
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
    fun calculateLongestPath(nodeId: String, memo: MutableMap<String, Int>): Int {
        memo[nodeId]?.let { return it }
        val parents = parentMap[nodeId] ?: emptySet()
        val level = if (parents.isEmpty()) 0
        else parents.maxOf { calculateLongestPath(it, memo) } + 1
        memo[nodeId] = level
        return level
    }

    val memo = mutableMapOf<String, Int>()
    kuiver.nodes.keys.forEach { nodeId ->
        levels[nodeId] = calculateLongestPath(nodeId, memo)
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
        it.dimensions?.width?.value ?: layoutConfig.nodeSize
    } ?: layoutConfig.nodeSize
    val maxNodeHeight = kuiver.nodes.values.maxOfOrNull {
        it.dimensions?.height?.value ?: layoutConfig.nodeSize
    } ?: layoutConfig.nodeSize

    val updatedNodes = kuiver.nodes.mapValues { (nodeId, node) ->
        val level = levels[nodeId] ?: 0
        val nodesInLevel = adjustedNodes[level] ?: emptyList()
        val indexInLevel = nodesInLevel.indexOf(nodeId).takeIf { it >= 0 } ?: 0

        val (x, y) = when (layoutConfig.direction) {
            LayoutDirection.HORIZONTAL -> {
                val levelSpacing = maxOf(layoutConfig.levelSpacing, maxNodeWidth + 60f)
                val nodeSpacing = maxOf(layoutConfig.nodeSpacing, maxNodeHeight + 40f)

                val layoutWidth = maxLevel * levelSpacing
                val layoutHeight = (nodesByLevel.values.maxOfOrNull { it.size } ?: 1) * nodeSpacing
                val centerX = if (layoutConfig.width > 0f) (layoutConfig.width - layoutWidth) / 2f else 0f
                val centerY = if (layoutConfig.height > 0f) (layoutConfig.height - layoutHeight) / 2f else 0f

                val levelHeight = nodesInLevel.size * nodeSpacing
                val xPos = level * levelSpacing + centerX
                val yPos =
                    indexInLevel * nodeSpacing - levelHeight / 2f + nodeSpacing / 2f + centerY
                Pair(xPos, yPos)
            }

            LayoutDirection.VERTICAL -> {
                val levelSpacing = maxOf(layoutConfig.levelSpacing, maxNodeHeight + 60f)
                val nodeSpacing = maxOf(layoutConfig.nodeSpacing, maxNodeWidth + 40f)

                val layoutWidth = (nodesByLevel.values.maxOfOrNull { it.size } ?: 1) * nodeSpacing
                val layoutHeight = maxLevel * levelSpacing
                val centerX = if (layoutConfig.width > 0f) (layoutConfig.width - layoutWidth) / 2f else 0f
                val centerY = if (layoutConfig.height > 0f) (layoutConfig.height - layoutHeight) / 2f else 0f

                val levelWidth = nodesInLevel.size * nodeSpacing
                val xPos = indexInLevel * nodeSpacing - levelWidth / 2f + nodeSpacing / 2f + centerX
                val yPos = level * levelSpacing + centerY
                Pair(xPos, yPos)
            }
        }

        node.copy(position = Offset(x, y))
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

/**
 * Adds spacers at intermediate levels for bypass edges to reduce visual obstruction
 */
private fun avoidBypassEdgeObstruction(
    kuiver: Kuiver,
    nodesByLevel: Map<Int, List<String>>,
    levels: Map<String, Int>
): Map<Int, List<String>> {
    val result = nodesByLevel.toMutableMap()

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
            val nodesAtLevel = result[intermediateLevel]?.toMutableList() ?: continue

            if (nodesAtLevel.size == 1) {
                result[intermediateLevel] = listOf("__bypass_spacer__", nodesAtLevel[0])
            } else {
                val midIndex = nodesAtLevel.size / 2
                val reordered = nodesAtLevel.take(midIndex) +
                        listOf("__bypass_spacer__") +
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

    fun dfs(nodeId: String) {
        visited.add(nodeId)
        inPath.add(nodeId)

        adjacency[nodeId]?.forEach { edge ->
            if (edge !in classified) {
                classified.add(edge)
                when {
                    edge.fromId == edge.toId || edge.toId in inPath -> backEdges.add(edge)
                    edge.toId !in visited -> {
                        forwardEdges.add(edge)
                        dfs(edge.toId)
                    }

                    else -> forwardEdges.add(edge)
                }
            }
        }
        inPath.remove(nodeId)
    }

    kuiver.nodes.keys.forEach { if (it !in visited) dfs(it) }
    kuiver.edges.forEach { if (it !in classified) forwardEdges.add(it) }

    return Pair(forwardEdges, backEdges)
}
