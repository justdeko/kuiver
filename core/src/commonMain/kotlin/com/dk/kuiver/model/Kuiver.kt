package com.dk.kuiver.model

import androidx.compose.runtime.Immutable

/**
 * Immutable graph data structure that supports cycles and self-loops.
 *
 * Create one with [buildKuiver] and derive new ones with [withNode], [withNodes], [withEdge],
 * [withEdges], [withoutNode], [withoutEdge] or [rebuild]. Instances compare structurally, so they
 * behave as snapshot state and as `remember` keys: a graph with the same nodes and edges is the
 * same value, and any change produces a new instance that triggers recomposition and re-layout.
 *
 * Nodes are unique by id and edges by their `(fromId, toId)` pair, so two nodes are connected at
 * most once per direction. Parallel edges are not supported yet, and the ones that would create
 * them are rejected rather than half-added.
 */
@Immutable
class Kuiver internal constructor(
    val nodes: Map<String, KuiverNode>,
    val edges: Set<KuiverEdge>,
    private val adjacency: Map<String, Set<String>>,
    private val edgeMap: Map<Pair<String, String>, KuiverEdge>
) {
    /**
     * Creates an empty graph.
     */
    constructor() : this(emptyMap(), emptySet(), emptyMap(), emptyMap())

    // Graphs are compared on every recomposition that keys off them, so the hash is computed once
    // and reused to keep unequal graphs an O(1) comparison
    private val cachedHashCode: Int by lazy(LazyThreadSafetyMode.PUBLICATION) {
        31 * nodes.hashCode() + edges.hashCode()
    }

    /**
     * Returns a copy of this graph with [node] added, replacing any existing node with the same id.
     * Edges are left untouched, which makes this the way to update a node's position or dimensions.
     */
    fun withNode(node: KuiverNode): Kuiver {
        if (nodes[node.id] == node) return this
        val updatedAdjacency = if (adjacency.containsKey(node.id)) {
            adjacency
        } else {
            adjacency + (node.id to emptySet())
        }
        return Kuiver(nodes + (node.id to node), edges, updatedAdjacency, edgeMap)
    }

    /**
     * Returns a copy of this graph with all of [newNodes] added, replacing existing nodes with the
     * same ids. See [withNode].
     */
    fun withNodes(newNodes: Collection<KuiverNode>): Kuiver {
        if (newNodes.isEmpty()) return this
        val updatedNodes = nodes.toMutableMap()
        val updatedAdjacency = adjacency.toMutableMap()
        newNodes.forEach { node ->
            updatedNodes[node.id] = node
            updatedAdjacency.getOrPut(node.id) { emptySet() }
        }
        return Kuiver(updatedNodes, edges, updatedAdjacency, edgeMap)
    }

    /**
     * Returns a copy of this graph with [edge] added.
     *
     * A graph holds at most one edge per `(fromId, toId)` pair. If one is already there, this
     * returns the graph unchanged, anchors and type included: adding does not replace. Drop the
     * existing edge with [withoutEdge] first to connect the same two nodes differently.
     *
     * @throws IllegalArgumentException if either endpoint is not part of the graph
     */
    fun withEdge(edge: KuiverEdge): Kuiver {
        requireEndpoints(edge)
        if (edgeMap.containsKey(edge.fromId to edge.toId)) return this
        val neighbors = adjacency[edge.fromId].orEmpty() + edge.toId
        return Kuiver(
            nodes,
            edges + edge,
            adjacency + (edge.fromId to neighbors),
            edgeMap + ((edge.fromId to edge.toId) to edge)
        )
    }

    /**
     * Returns a copy of this graph with all of [newEdges] added, skipping the ones whose
     * `(fromId, toId)` pair is already connected. See [withEdge].
     *
     * @throws IllegalArgumentException if any endpoint is not part of the graph
     */
    fun withEdges(newEdges: Collection<KuiverEdge>): Kuiver {
        if (newEdges.isEmpty()) return this
        newEdges.forEach { requireEndpoints(it) }
        val updatedEdges = edges.toMutableSet()
        val updatedAdjacency = adjacency.toMutableMap()
        val updatedEdgeMap = edgeMap.toMutableMap()
        newEdges.forEach { edge ->
            val key = edge.fromId to edge.toId
            if (updatedEdgeMap.containsKey(key)) return@forEach
            updatedEdges.add(edge)
            updatedAdjacency[edge.fromId] = updatedAdjacency[edge.fromId].orEmpty() + edge.toId
            updatedEdgeMap[key] = edge
        }
        if (updatedEdges.size == edges.size) return this
        return Kuiver(nodes, updatedEdges, updatedAdjacency, updatedEdgeMap)
    }

    /**
     * Returns a copy of this graph without the node [id] and without the edges touching it.
     */
    fun withoutNode(id: String): Kuiver {
        if (!nodes.containsKey(id)) return this
        val keptNodes = nodes.values.filter { it.id != id }
        val keptEdges = edges.filter { it.fromId != id && it.toId != id }
        return buildKuiver {
            keptNodes.forEach { addNode(it) }
            keptEdges.forEach { addEdge(it) }
        }
    }

    /**
     * Returns a copy of this graph without [edge]. Nodes are left untouched.
     */
    fun withoutEdge(edge: KuiverEdge): Kuiver {
        if (edge !in edges) return this
        val keptNodes = nodes.values
        val keptEdges = edges.filter { it != edge }
        return buildKuiver {
            keptNodes.forEach { addNode(it) }
            keptEdges.forEach { addEdge(it) }
        }
    }

    /**
     * Reopens this graph in a [KuiverBuilder] and returns the result, for changes that are easier
     * to express as a batch than as a chain of `with` calls.
     *
     * Example:
     * ```kotlin
     * val extended = graph.rebuild {
     *     nodes("D", "E")
     *     edge("C", "D")
     * }
     * ```
     */
    fun rebuild(block: KuiverBuilder.() -> Unit): Kuiver =
        KuiverBuilder().also { it.addAll(this) }.apply(block).build()

    /**
     * Utility method to check if adding an edge would create a cycle.
     *
     * @param from starting node ID
     * @param to ending node ID
     * @return `true` if the condition holds, `false` otherwise
     */
    fun wouldCreateCycle(from: String, to: String): Boolean {
        return hasPath(to, from)
    }

    private fun requireEndpoints(edge: KuiverEdge) {
        require(nodes.containsKey(edge.fromId)) {
            "Edge ${edge.fromId} -> ${edge.toId} references unknown node ${edge.fromId}"
        }
        require(nodes.containsKey(edge.toId)) {
            "Edge ${edge.fromId} -> ${edge.toId} references unknown node ${edge.toId}"
        }
    }

    private fun hasPath(from: String, to: String): Boolean = adjacency.hasPath(from, to)

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

        edges.forEach { edge ->
            if (edge.fromId == edge.toId) {
                result[edge] = EdgeType.SELF_LOOP
            }
        }

        // Single DFS pass to get timestamps for all nodes
        val discoveryTime = mutableMapOf<String, Int>()
        val finishTime = mutableMapOf<String, Int>()
        val inPath = mutableSetOf<String>()
        var time = 0

        val iterStack = ArrayDeque<Pair<String, Iterator<String>>>()

        fun enter(nodeId: String) {
            discoveryTime[nodeId] = ++time
            inPath.add(nodeId)
            iterStack.addLast(
                nodeId to (adjacency[nodeId]?.iterator() ?: emptyList<String>().iterator())
            )
        }

        fun runDfs(start: String) {
            enter(start)
            while (iterStack.isNotEmpty()) {
                val (nodeId, iter) = iterStack.last()
                if (!iter.hasNext()) {
                    inPath.remove(nodeId)
                    finishTime[nodeId] = ++time
                    iterStack.removeLast()
                    continue
                }
                val neighbor = iter.next()
                val edge = edgeMap[nodeId to neighbor]
                if (edge == null || result.containsKey(edge)) continue

                when {
                    // Back edge: points to an ancestor currently in the path
                    inPath.contains(neighbor) -> result[edge] = EdgeType.BACK
                    // Tree/Forward edge: neighbor not yet visited
                    !discoveryTime.containsKey(neighbor) -> {
                        result[edge] = EdgeType.FORWARD
                        enter(neighbor)
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
        // Run DFS from all unvisited nodes
        nodes.keys.forEach { nodeId ->
            if (!discoveryTime.containsKey(nodeId)) {
                runDfs(nodeId)
            }
        }

        // Classify any remaining edges (shouldn't happen, but safety check)
        edges.forEach { edge ->
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

        val iterStack = ArrayDeque<Pair<String, Iterator<String>>>()

        fun enter(nodeId: String) {
            index[nodeId] = currentIndex
            lowLink[nodeId] = currentIndex
            currentIndex++
            stack.add(nodeId)
            onStack.add(nodeId)
            iterStack.addLast(
                nodeId to (adjacency[nodeId]?.iterator() ?: emptyList<String>().iterator())
            )
        }

        fun strongConnect(start: String) {
            enter(start)
            while (iterStack.isNotEmpty()) {
                val (nodeId, iter) = iterStack.last()
                if (iter.hasNext()) {
                    val neighbor = iter.next()
                    when {
                        !index.containsKey(neighbor) -> enter(neighbor)
                        onStack.contains(neighbor) ->
                            lowLink[nodeId] = minOf(lowLink[nodeId]!!, index[neighbor]!!)
                    }
                    continue
                }

                iterStack.removeLast()

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

                // Propagate the lowLink up, as returning from the recursive call did
                iterStack.lastOrNull()?.let { (parentId, _) ->
                    lowLink[parentId] = minOf(lowLink[parentId]!!, lowLink[nodeId]!!)
                }
            }
        }

        nodes.keys.forEach { nodeId ->
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
        if (edges.any { it.fromId == it.toId }) {
            return true
        }
        // Check for SCCs with multiple nodes
        return findStronglyConnectedComponents().any { it.size > 1 }
    }

    fun getTopologicalOrder(): List<String> {
        val inDegree = mutableMapOf<String, Int>()
        val queue = ArrayDeque<String>()
        val result = mutableListOf<String>()

        // Initialize in-degrees
        nodes.keys.forEach { inDegree[it] = 0 }
        edges.forEach { edge ->
            inDegree[edge.toId] = (inDegree[edge.toId] ?: 0) + 1
        }

        // Find nodes with no incoming edges
        inDegree.filter { it.value == 0 }.forEach { (nodeId, _) ->
            queue.addLast(nodeId)
        }

        // Process queue
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            result.add(current)

            adjacency[current]?.forEach { neighbor ->
                inDegree[neighbor] = (inDegree[neighbor] ?: 0) - 1
                if (inDegree[neighbor] == 0) {
                    queue.addLast(neighbor)
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
        if (measuredDimensions.isEmpty()) return this
        var changed = false
        val updatedNodes = nodes.mapValues { (nodeId, node) ->
            val dimensions = measuredDimensions[nodeId]
            if (dimensions == null || dimensions == node.dimensions) {
                node
            } else {
                changed = true
                node.copy(dimensions = dimensions)
            }
        }
        // Only the nodes change, so the edges and the derived structure carry over as they are
        return if (changed) Kuiver(updatedNodes, edges, adjacency, edgeMap) else this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Kuiver) return false
        if (cachedHashCode != other.cachedHashCode) return false
        return nodes == other.nodes && edges == other.edges
    }

    override fun hashCode(): Int = cachedHashCode

    override fun toString(): String = "Kuiver(nodes=${nodes.size}, edges=${edges.size})"
}

/**
 * Mutable builder for [Kuiver] graphs, the receiver of the [buildKuiver] and [Kuiver.rebuild]
 * blocks. Collect nodes and edges here, then call [build] to freeze them into an immutable graph.
 */
class KuiverBuilder {
    private val nodes = mutableMapOf<String, KuiverNode>()
    private val edges = mutableSetOf<KuiverEdge>()
    private val adjacency = mutableMapOf<String, MutableSet<String>>()
    private val edgeMap = mutableMapOf<Pair<String, String>, KuiverEdge>()

    /**
     * Adds [node] unless a node with the same id is already present.
     *
     * @return `true` if the node was added
     */
    fun addNode(node: KuiverNode): Boolean {
        if (nodes.containsKey(node.id)) return false
        nodes[node.id] = node
        adjacency[node.id] = mutableSetOf()
        return true
    }

    /**
     * Adds [edge], ignoring it when either endpoint is missing from the graph or when the two
     * nodes are already connected in that direction.
     *
     * A builder holds at most one edge per `(fromId, toId)` pair, so a second edge over the same
     * pair is dropped whatever its anchors or type are: the first one added wins. Until parallel
     * edges are supported, connecting the same two nodes twice is a caller mistake, and the
     * `false` return is where it shows up.
     *
     * @return `true` if the edge was added
     */
    fun addEdge(edge: KuiverEdge): Boolean {
        if (!nodes.containsKey(edge.fromId) || !nodes.containsKey(edge.toId)) {
            return false
        }
        val key = edge.fromId to edge.toId
        if (edgeMap.containsKey(key)) return false

        edges.add(edge)
        adjacency[edge.fromId]?.add(edge.toId)
        edgeMap[key] = edge
        return true
    }

    /**
     * Checks whether an edge would close a loop over what the builder holds so far, so it can be
     * skipped while the graph is still being assembled.
     *
     * @param from starting node ID
     * @param to ending node ID
     * @return `true` if the condition holds, `false` otherwise
     */
    fun wouldCreateCycle(from: String, to: String): Boolean = adjacency.hasPath(to, from)

    /**
     * Adds every node and edge of [kuiver], keeping the nodes already present in the builder.
     */
    fun addAll(kuiver: Kuiver) {
        kuiver.nodes.values.forEach { addNode(it) }
        kuiver.edges.forEach { addEdge(it) }
    }

    /**
     * Freezes the collected nodes and edges into an immutable [Kuiver]. The builder stays usable
     * afterwards and later changes do not affect the graphs it already produced.
     */
    fun build(): Kuiver = Kuiver(
        nodes.toMap(),
        edges.toSet(),
        adjacency.mapValues { (_, neighbors) -> neighbors.toSet() },
        edgeMap.toMap()
    )
}

/**
 * Iterative reachability check over an adjacency map, shared by the graph and the builder. Runs on
 * an explicit stack, so a chain of any depth is safe.
 */
private fun Map<String, Set<String>>.hasPath(from: String, to: String): Boolean {
    val visited = mutableSetOf<String>()
    val pending = ArrayDeque<String>()
    pending.addLast(from)

    while (pending.isNotEmpty()) {
        val nodeId = pending.removeLast()
        if (nodeId == to) return true
        if (!visited.add(nodeId)) continue
        this[nodeId]?.forEach { pending.addLast(it) }
    }
    return false
}
