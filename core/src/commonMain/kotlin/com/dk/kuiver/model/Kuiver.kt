package com.dk.kuiver.model

/**
 * DSL function for building Kuiver graphs with a clean, declarative syntax.
 *
 * This function creates a new Kuiver instance and applies the provided builder block.
 * Once built, the resulting Kuiver should be treated as immutable.
 *
 * Example:
 * ```kotlin
 * val graph = buildKuiver {
 *     addNode(KuiverNode(id = "A"))
 *     addNode(KuiverNode(id = "B"))
 *     addEdge(KuiverEdge(fromId = "A", toId = "B"))
 * }
 * ```
 *
 * @param block Builder block for constructing the graph
 * @return A new Kuiver instance (treat as immutable after construction)
 */
fun buildKuiver(block: Kuiver.() -> Unit): Kuiver = Kuiver().apply(block)

/**
 * Builds a new Kuiver instance with the given nodes and edges from the original graph,
 * automatically classifying all edges in a single optimized pass.
 * This eliminates the need to build the graph twice.
 *
 * @param nodes The nodes to include in the new graph
 * @param originalEdges The edges from the original graph (without type classification)
 * @return A new Kuiver instance with all edges classified by type
 */
fun buildKuiverWithClassifiedEdges(
    nodes: Collection<KuiverNode>,
    originalEdges: Collection<KuiverEdge>
): Kuiver {
    val tempKuiver = Kuiver().apply {
        nodes.forEach { addNode(it) }
        originalEdges.forEach { addEdge(it) }
    }

    val edgeClassifications = tempKuiver.classifyAllEdges()

    return Kuiver().apply {
        nodes.forEach { addNode(it) }
        edgeClassifications.forEach { (edge, type) ->
            addEdge(edge.copy(type = type))
        }
    }
}

/**
 * Graph data structure that supports cycles and self-loops.
 */
class Kuiver {
    private val _nodes = mutableMapOf<String, KuiverNode>()
    private val _edges = mutableSetOf<KuiverEdge>()
    private val _adjacencyList = mutableMapOf<String, MutableSet<String>>()
    private val _edgeMap = mutableMapOf<Pair<String, String>, KuiverEdge>()

    val nodes: Map<String, KuiverNode> get() = _nodes
    val edges: Set<KuiverEdge> get() = _edges

    fun addNode(node: KuiverNode): Boolean {
        if (_nodes.containsKey(node.id)) return false
        _nodes[node.id] = node
        _adjacencyList[node.id] = mutableSetOf()
        return true
    }

    fun addEdge(edge: KuiverEdge): Boolean {
        if (!_nodes.containsKey(edge.fromId) || !_nodes.containsKey(edge.toId)) {
            return false
        }

        _edges.add(edge)
        _adjacencyList[edge.fromId]?.add(edge.toId)
        _edgeMap[edge.fromId to edge.toId] = edge
        return true
    }

    /**
     * Utility method to check if adding an edge would create a cycle.
     * Can be used by callers who want to validate before adding edges.
     */
    fun wouldCreateCycle(from: String, to: String): Boolean {
        // Simple DFS to detect if adding edge would create cycle
        val visited = mutableSetOf<String>()
        return hasPath(to, from, visited)
    }

    private fun hasPath(from: String, to: String, visited: MutableSet<String>): Boolean {
        if (from == to) return true
        if (visited.contains(from)) return false

        visited.add(from)
        _adjacencyList[from]?.forEach { neighbor ->
            if (hasPath(neighbor, to, visited)) return true
        }
        return false
    }

    /**
     * Classifies an edge based on DFS tree structure.
     * Returns the EdgeType for the given edge.
     * Note: For better performance when classifying multiple edges, use classifyAllEdges().
     */
    fun classifyEdge(edge: KuiverEdge): EdgeType {
        return classifyAllEdges()[edge] ?: EdgeType.CROSS
    }

    /**
     * Classifies all edges in the graph and returns a map of edges to their types.
     * Uses a single DFS pass for optimal O(V + E) performance.
     */
    fun classifyAllEdges(): Map<KuiverEdge, EdgeType> {
        val result = mutableMapOf<KuiverEdge, EdgeType>()

        _edges.forEach { edge ->
            if (edge.fromId == edge.toId) {
                result[edge] = EdgeType.SELF_LOOP
            }
        }

        // Single DFS pass to get timestamps for all nodes
        val discoveryTime = mutableMapOf<String, Int>()
        val finishTime = mutableMapOf<String, Int>()
        val inPath = mutableSetOf<String>()
        var time = 0

        fun dfs(nodeId: String) {
            discoveryTime[nodeId] = ++time
            inPath.add(nodeId)

            _adjacencyList[nodeId]?.forEach { neighbor ->
                val edge = _edgeMap[nodeId to neighbor]

                if (edge != null && !result.containsKey(edge)) {
                    when {
                        // Back edge: points to an ancestor currently in the path
                        inPath.contains(neighbor) -> result[edge] = EdgeType.BACK
                        // Tree/Forward edge: neighbor not yet visited
                        !discoveryTime.containsKey(neighbor) -> {
                            result[edge] = EdgeType.FORWARD
                            dfs(neighbor)
                        }
                        // Cross edge or forward edge to already-visited descendant
                        else -> {
                            val neighborDiscovery = discoveryTime[neighbor]!!
                            val neighborFinish = finishTime[neighbor]
                            val currentDiscovery = discoveryTime[nodeId]!!

                            // If neighbor was discovered after current node and already finished,
                            // it's a forward edge to a descendant in our subtree
                            result[edge] = if (neighborFinish != null &&
                                neighborDiscovery > currentDiscovery
                            ) {
                                EdgeType.FORWARD
                            } else {
                                EdgeType.CROSS
                            }
                        }
                    }
                }
            }

            inPath.remove(nodeId)
            finishTime[nodeId] = ++time
        }

        // Run DFS from all unvisited nodes
        _nodes.keys.forEach { nodeId ->
            if (!discoveryTime.containsKey(nodeId)) {
                dfs(nodeId)
            }
        }

        // Classify any remaining edges (shouldn't happen, but safety check)
        _edges.forEach { edge ->
            if (!result.containsKey(edge)) {
                result[edge] = EdgeType.CROSS
            }
        }

        return result
    }

    /**
     * Finds all strongly connected components (SCCs) using Tarjan's algorithm.
     * Returns a list of sets, where each set contains node IDs in the same SCC.
     * SCCs with size > 1 indicate cycles in the graph.
     */
    fun findStronglyConnectedComponents(): List<Set<String>> {
        val index = mutableMapOf<String, Int>()
        val lowLink = mutableMapOf<String, Int>()
        val onStack = mutableSetOf<String>()
        val stack = mutableListOf<String>()
        val sccs = mutableListOf<Set<String>>()
        var currentIndex = 0

        fun strongConnect(nodeId: String) {
            index[nodeId] = currentIndex
            lowLink[nodeId] = currentIndex
            currentIndex++
            stack.add(nodeId)
            onStack.add(nodeId)

            _adjacencyList[nodeId]?.forEach { neighbor ->
                when {
                    !index.containsKey(neighbor) -> {
                        strongConnect(neighbor)
                        lowLink[nodeId] = minOf(lowLink[nodeId]!!, lowLink[neighbor]!!)
                    }

                    onStack.contains(neighbor) -> {
                        lowLink[nodeId] = minOf(lowLink[nodeId]!!, index[neighbor]!!)
                    }
                }
            }

            // If nodeId is a root node, pop the stack and create an SCC
            if (lowLink[nodeId] == index[nodeId]) {
                val scc = mutableSetOf<String>()
                var w: String
                do {
                    w = stack.removeAt(stack.lastIndex)
                    onStack.remove(w)
                    scc.add(w)
                } while (w != nodeId)
                sccs.add(scc)
            }
        }

        _nodes.keys.forEach { nodeId ->
            if (!index.containsKey(nodeId)) {
                strongConnect(nodeId)
            }
        }

        return sccs
    }

    /**
     * Checks if the graph contains any cycles.
     * A cycle exists if there's any SCC with more than one node, or any self-loop.
     */
    fun hasCycles(): Boolean {
        // Check for self-loops first (quick check)
        if (_edges.any { it.fromId == it.toId }) {
            return true
        }
        // Check for SCCs with multiple nodes
        return findStronglyConnectedComponents().any { it.size > 1 }
    }

    fun getTopologicalOrder(): List<String> {
        val inDegree = mutableMapOf<String, Int>()
        val queue = mutableListOf<String>()
        val result = mutableListOf<String>()

        // Initialize in-degrees
        _nodes.keys.forEach { inDegree[it] = 0 }
        _edges.forEach { edge ->
            inDegree[edge.toId] = (inDegree[edge.toId] ?: 0) + 1
        }

        // Find nodes with no incoming edges
        inDegree.filter { it.value == 0 }.forEach { (nodeId, _) ->
            queue.add(nodeId)
        }

        // Process queue
        while (queue.isNotEmpty()) {
            val current = queue.removeAt(0)
            result.add(current)

            _adjacencyList[current]?.forEach { neighbor ->
                inDegree[neighbor] = (inDegree[neighbor] ?: 0) - 1
                if (inDegree[neighbor] == 0) {
                    queue.add(neighbor)
                }
            }
        }

        return result
    }

    /**
     * Creates a new Kuiver with updated node dimensions while preserving structure.
     * Used after measuring node content to update dimensions before layout calculation.
     */
    fun withMeasuredDimensions(measuredDimensions: Map<String, NodeDimensions>): Kuiver {
        return Kuiver().apply {
            this@Kuiver.nodes.forEach { (nodeId, node) ->
                val updatedNode = measuredDimensions[nodeId]?.let { dims ->
                    node.copy(dimensions = dims)
                } ?: node
                addNode(updatedNode)
            }
            this@Kuiver.edges.forEach { addEdge(it) }
        }
    }
}