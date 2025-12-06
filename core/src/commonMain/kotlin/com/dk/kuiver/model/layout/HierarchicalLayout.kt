package com.dk.kuiver.model.layout

import androidx.compose.ui.geometry.Offset
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.buildKuiverWithClassifiedEdges

internal fun hierarchical(kuiver: Kuiver, layoutConfig: LayoutConfig = LayoutConfig()): Kuiver {
    // Separate back edges temporarily for level calculation
    // (hierarchical layout requires acyclic structure for level assignment)
    val (acyclicEdges, _) = if (kuiver.hasCycles()) {
        separateBackEdges(kuiver)
    } else {
        Pair(kuiver.edges.toList(), emptyList())
    }

    // Build adjacency maps using only forward edges
    val parentMap = mutableMapOf<String, MutableSet<String>>()
    val childrenMap = mutableMapOf<String, MutableSet<String>>()
    acyclicEdges.forEach { edge ->
        parentMap.getOrPut(edge.toId) { mutableSetOf() }.add(edge.fromId)
        childrenMap.getOrPut(edge.fromId) { mutableSetOf() }.add(edge.toId)
    }

    val levels = mutableMapOf<String, Int>()

    // Calculate the longest path to each node using dynamic programming
    // This ensures nodes like C in A->B->C and A->C end up at different levels
    fun calculateLongestPath(nodeId: String, memo: MutableMap<String, Int>): Int {
        if (memo.containsKey(nodeId)) return memo[nodeId]!!

        val parents = parentMap[nodeId] ?: emptySet()
        val level = if (parents.isEmpty()) {
            0
        } else {
            parents.maxOfOrNull { parentId ->
                calculateLongestPath(parentId, memo)
            }!! + 1
        }

        memo[nodeId] = level
        return level
    }

    // Calculate levels for all nodes
    val memo = mutableMapOf<String, Int>()
    kuiver.nodes.keys.forEach { nodeId ->
        levels[nodeId] = calculateLongestPath(nodeId, memo)
    }

    val nodesByLevel = levels.entries.groupBy({ it.value }, { it.key })
    val maxLevel = nodesByLevel.keys.maxOrNull() ?: 0

    val sortedNodes = minimizeCrossings(nodesByLevel, maxLevel, parentMap, childrenMap)

    // Detect bypass edges and adjust node positions to avoid visual obstruction
    val adjustedNodes = avoidBypassEdgeObstruction(kuiver, sortedNodes, levels)

    // Calculate actual spacing based on node dimensions
    val maxNodeWidth = kuiver.nodes.values.maxOfOrNull {
        it.dimensions?.width?.value ?: layoutConfig.nodeSize
    } ?: layoutConfig.nodeSize

    val maxNodeHeight = kuiver.nodes.values.maxOfOrNull {
        it.dimensions?.height?.value ?: layoutConfig.nodeSize
    } ?: layoutConfig.nodeSize

    // Use larger of config spacing or actual node size + padding
    val actualLevelSpacing = maxOf(layoutConfig.levelSpacing, maxNodeWidth + 60f)
    val actualNodeSpacing = maxOf(layoutConfig.nodeSpacing, maxNodeHeight + 40f)

    // Calculate layout bounds for centering
    val layoutWidth = maxLevel * actualLevelSpacing
    val maxLevelSize = nodesByLevel.values.maxOfOrNull { it.size } ?: 1
    val layoutHeight = maxLevelSize * actualNodeSpacing

    val centerOffsetX = if (layoutConfig.width > 0f) (layoutConfig.width - layoutWidth) / 2f else 0f
    val centerOffsetY =
        if (layoutConfig.height > 0f) (layoutConfig.height - layoutHeight) / 2f else 0f

    val updatedNodes = kuiver.nodes.mapValues { (nodeId, node) ->
        val level = levels[nodeId] ?: 0
        val nodesInLevel = adjustedNodes[level] ?: emptyList()

        // Use full list (including spacers) for spacing calculation to create gaps
        val indexInLevel = nodesInLevel.indexOf(nodeId).takeIf { it >= 0 } ?: 0

        // Calculate position based on direction
        val (x, y) = when (layoutConfig.direction) {
            LayoutDirection.HORIZONTAL -> {
                // Horizontal: levels progress left to right (current behavior)
                val levelHeight = nodesInLevel.size * actualNodeSpacing
                val xPos = level * actualLevelSpacing + centerOffsetX
                val yPos =
                    indexInLevel * actualNodeSpacing - levelHeight / 2f + actualNodeSpacing / 2f + centerOffsetY
                Pair(xPos, yPos)
            }

            LayoutDirection.VERTICAL -> {
                // Vertical: levels progress top to bottom
                val levelWidth = nodesInLevel.size * actualNodeSpacing
                val xPos =
                    indexInLevel * actualNodeSpacing - levelWidth / 2f + actualNodeSpacing / 2f + centerOffsetX
                val yPos = level * actualLevelSpacing + centerOffsetY
                Pair(xPos, yPos)
            }
        }

        node.copy(position = Offset(x, y))
    }

    // Build graph with classified edges in a single pass
    return buildKuiverWithClassifiedEdges(
        nodes = updatedNodes.values,
        originalEdges = kuiver.edges
    )
}

private fun minimizeCrossings(
    nodesByLevel: Map<Int, List<String>>,
    maxLevel: Int,
    parentMap: Map<String, Set<String>>,
    childrenMap: Map<String, Set<String>>
): Map<Int, List<String>> {
    val result = nodesByLevel.toMutableMap()
    var previousResult: Map<Int, List<String>>

    repeat(10) {
        previousResult = result.toMap()

        // Top-down pass
        for (level in 1..maxLevel) {
            val currentLevel = result[level] ?: continue
            val prevLevel = result[level - 1] ?: continue

            val sortedLevel = currentLevel.sortedBy { nodeId ->
                val parents = parentMap[nodeId] ?: emptySet()
                val positions = parents.mapNotNull { parentId ->
                    prevLevel.indexOf(parentId).takeIf { it >= 0 }
                }
                if (positions.isNotEmpty()) positions.average() else Double.MAX_VALUE
            }

            result[level] = sortedLevel
        }

        // Bottom-up pass
        for (level in maxLevel - 1 downTo 0) {
            val currentLevel = result[level] ?: continue
            val nextLevel = result[level + 1] ?: continue

            val sortedLevel = currentLevel.sortedBy { nodeId ->
                val children = childrenMap[nodeId] ?: emptySet()
                val positions = children.mapNotNull { childId ->
                    nextLevel.indexOf(childId).takeIf { it >= 0 }
                }
                if (positions.isNotEmpty()) positions.average() else Double.MAX_VALUE
            }

            result[level] = sortedLevel
        }

        // Early termination if no changes
        if (result == previousResult) return result
    }

    return result
}

private fun avoidBypassEdgeObstruction(
    kuiver: Kuiver,
    nodesByLevel: Map<Int, List<String>>,
    levels: Map<String, Int>
): Map<Int, List<String>> {
    val result = nodesByLevel.toMutableMap()

    // Find bypass edges (edges that skip intermediate levels)
    val bypassEdges = kuiver.edges.filter { edge ->
        val fromLevel = levels[edge.fromId] ?: 0
        val toLevel = levels[edge.toId] ?: 0
        toLevel - fromLevel > 1 // Skips at least one level
    }

    // For each bypass edge, ensure intermediate nodes don't obstruct the visual path
    bypassEdges.forEach { bypassEdge ->
        val fromLevel = levels[bypassEdge.fromId] ?: 0
        val toLevel = levels[bypassEdge.toId] ?: 0

        // For each intermediate level, offset nodes to avoid the visual path
        for (intermediateLevel in fromLevel + 1 until toLevel) {
            val nodesAtLevel = result[intermediateLevel]?.toMutableList() ?: continue

            // For single node at this level, create an offset by duplicating it
            // This will force it to be positioned away from center in the Y calculation
            if (nodesAtLevel.size == 1) {
                val nodeId = nodesAtLevel[0]
                // Create a virtual offset by reordering - put the node at index 1 instead of 0
                // This will shift it in the Y positioning calculation
                result[intermediateLevel] = listOf("__bypass_spacer__", nodeId)
            } else {
                // For multiple nodes, sort them to create space in the middle
                val midIndex = nodesAtLevel.size / 2
                val reordered = mutableListOf<String>()

                // Put first half at beginning
                reordered.addAll(nodesAtLevel.take(midIndex))
                // Add spacer
                reordered.add("__bypass_spacer__")
                // Put second half at end
                reordered.addAll(nodesAtLevel.drop(midIndex))

                result[intermediateLevel] = reordered
            }
        }
    }

    return result
}

/**
 * Separates edges into forward edges (acyclic) and back edges (that create cycles).
 * Uses a greedy feedback arc set algorithm to minimize the number of back edges.
 */
private fun separateBackEdges(kuiver: Kuiver): Pair<List<KuiverEdge>, List<KuiverEdge>> {
    val forwardEdges = mutableListOf<KuiverEdge>()
    val backEdges = mutableListOf<KuiverEdge>()

    // Track visited nodes and nodes in current DFS path (for cycle detection)
    val visited = mutableSetOf<String>()
    val inPath = mutableSetOf<String>()
    val classified = mutableMapOf<KuiverEdge, Boolean>() // true = forward, false = back

    // Build adjacency list
    val adjacency = mutableMapOf<String, MutableList<KuiverEdge>>()
    kuiver.edges.forEach { edge ->
        adjacency.getOrPut(edge.fromId) { mutableListOf() }.add(edge)
    }

    // DFS to classify edges
    fun dfs(nodeId: String) {
        visited.add(nodeId)
        inPath.add(nodeId)

        adjacency[nodeId]?.forEach { edge ->
            if (!classified.containsKey(edge)) {
                when {
                    // Self-loop: always a back edge
                    edge.fromId == edge.toId -> {
                        classified[edge] = false
                        backEdges.add(edge)
                    }
                    // Node in current path: back edge (creates cycle)
                    inPath.contains(edge.toId) -> {
                        classified[edge] = false
                        backEdges.add(edge)
                    }
                    // Not yet visited: forward edge, continue DFS
                    !visited.contains(edge.toId) -> {
                        classified[edge] = true
                        forwardEdges.add(edge)
                        dfs(edge.toId)
                    }
                    // Already visited (but not in current path): forward edge
                    else -> {
                        classified[edge] = true
                        forwardEdges.add(edge)
                    }
                }
            }
        }

        inPath.remove(nodeId)
    }

    // Run DFS from all unvisited nodes
    kuiver.nodes.keys.forEach { nodeId ->
        if (!visited.contains(nodeId)) {
            dfs(nodeId)
        }
    }

    // Add any remaining unclassified edges as forward edges
    kuiver.edges.forEach { edge ->
        if (!classified.containsKey(edge)) {
            forwardEdges.add(edge)
        }
    }

    return Pair(forwardEdges, backEdges)
}