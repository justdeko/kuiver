package com.dk.kuiver.model

import androidx.compose.runtime.Immutable

/**
 * Immutable graph data structure that supports cycles and self-loops.
 *
 * Create one with [buildKuiver] and derive new ones with [withNode], [withNodes], [withEdge],
 * [withEdges], [withoutNode], [withoutEdge] or [rebuild]. Instances compare structurally and
 * behave as snapshot state and as `remember` keys: a graph with the same nodes and edges is the
 * same value, and any change produces a new instance that triggers recomposition and re-layout.
 *
 * Nodes are unique by id and edges by [KuiverEdge.id]. Edges with the default id connect two nodes
 * at most once per direction. Edges with their own ids can connect the same nodes several times.
 */
@Immutable
class Kuiver internal constructor(
    val nodes: Map<String, KuiverNode>,
    val edges: Set<KuiverEdge>,
    private val adjacency: Map<String, Set<String>>,
    private val edgeMap: Map<String, KuiverEdge>
) {
    /**
     * Creates an empty graph.
     */
    constructor() : this(emptyMap(), emptySet(), emptyMap(), emptyMap())

    private val cachedHashCode: Int by lazy(LazyThreadSafetyMode.PUBLICATION) {
        31 * nodes.hashCode() + edges.hashCode()
    }

    private val edgesByPair: Map<Pair<String, String>, List<KuiverEdge>> by lazy(
        LazyThreadSafetyMode.PUBLICATION
    ) {
        edges.groupBy { it.fromId to it.toId }
    }

    // Whether any two edges share their start and end node
    internal val hasParallelEdges: Boolean
        get() = adjacency.values.sumOf { it.size } < edges.size

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
     * A graph holds at most one edge per [KuiverEdge.id]. If one is already there, this returns
     * the graph unchanged, anchors and type included: adding does not replace. Drop the existing
     * edge with [withoutEdge] first to replace it.
     *
     * @throws IllegalArgumentException if either endpoint is not part of the graph, or if the id
     * already belongs to an edge between other nodes
     */
    fun withEdge(edge: KuiverEdge): Kuiver {
        requireEndpoints(edge)
        val existing = edgeMap[edge.id]
        if (existing != null) {
            requireSameEndpoints(existing, edge)
            return this
        }
        val neighbors = adjacency[edge.fromId].orEmpty() + edge.toId
        return Kuiver(
            nodes,
            edges + edge,
            adjacency + (edge.fromId to neighbors),
            edgeMap + (edge.id to edge)
        )
    }

    /**
     * Returns a copy of this graph with all of [newEdges] added, skipping the ones whose id is
     * already taken. See [withEdge].
     *
     * @throws IllegalArgumentException if any endpoint is not part of the graph, or if an id
     * already belongs to an edge between other nodes
     */
    fun withEdges(newEdges: Collection<KuiverEdge>): Kuiver {
        if (newEdges.isEmpty()) return this
        newEdges.forEach { requireEndpoints(it) }
        val updatedEdges = edges.toMutableSet()
        val updatedAdjacency = adjacency.toMutableMap()
        val updatedEdgeMap = edgeMap.toMutableMap()
        newEdges.forEach { edge ->
            val existing = updatedEdgeMap[edge.id]
            if (existing != null) {
                requireSameEndpoints(existing, edge)
                return@forEach
            }
            updatedEdges.add(edge)
            updatedAdjacency[edge.fromId] = updatedAdjacency[edge.fromId].orEmpty() + edge.toId
            updatedEdgeMap[edge.id] = edge
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
     * Returns a copy of this graph without the edge with the default id `"$fromId->$toId"`,
     * whatever its anchors and type are. Other edges between the two nodes and the nodes are left
     * untouched.
     *
     * @param fromId id of the edge's start node
     * @param toId id of the edge's end node
     */
    @Deprecated(
        "Edges are identified by id",
        ReplaceWith("withoutEdge(\"\$fromId->\$toId\")")
    )
    fun withoutEdge(fromId: String, toId: String): Kuiver = withoutEdge("$fromId->$toId")

    /**
     * Returns a copy of this graph without [edge]. Nodes are left untouched.
     *
     * @param edge the edge to drop, matched by [KuiverEdge.id]
     */
    fun withoutEdge(edge: KuiverEdge): Kuiver = withoutEdge(edge.id)

    /**
     * Returns a copy of this graph without the edge [id]. Nodes are left untouched.
     *
     * @param id [KuiverEdge.id] of the edge to drop. Unknown ids are ignored
     */
    fun withoutEdge(id: String): Kuiver {
        if (!edgeMap.containsKey(id)) return this
        val keptNodes = nodes.values
        val keptEdges = edges.filter { it.id != id }
        return buildKuiver {
            keptNodes.forEach { addNode(it) }
            keptEdges.forEach { addEdge(it) }
        }
    }

    /**
     * Returns the edges from [fromId] to [toId], in the order they were added. Edges in the
     * opposite direction are not included.
     *
     * @param fromId id of the edges' start node
     * @param toId id of the edges' end node
     */
    fun edgesBetween(fromId: String, toId: String): List<KuiverEdge> =
        edgesByPair[fromId to toId].orEmpty()

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
     * Checks whether adding an edge from [from] to [to] would create a cycle.
     *
     * @param from id of the edge's start node
     * @param to id of the edge's end node
     * @return `true` if the edge would close a cycle
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
     * Classifies a single edge. Classifies the whole graph to do so: prefer [classifyAllEdges]
     * when more than one edge is needed.
     *
     * @return the [EdgeType] of [edge]
     */
    fun classifyEdge(edge: KuiverEdge): EdgeType {
        return classifyAllEdges()[edge] ?: EdgeType.CROSS
    }

    /**
     * Classifies every edge of the graph in a single O(V + E) pass. Edges between the same two
     * nodes in the same direction share a type.
     *
     * @return the [EdgeType] of each edge
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

        fun classify(pairEdges: List<KuiverEdge>, type: EdgeType) {
            pairEdges.forEach { result[it] = type }
        }

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
                val pairEdges = edgesByPair[nodeId to neighbor]
                if (pairEdges == null || result.containsKey(pairEdges.first())) continue

                when {
                    // Back edge: points to an ancestor currently in the path
                    inPath.contains(neighbor) -> classify(pairEdges, EdgeType.BACK)
                    // Tree/Forward edge: neighbor not yet visited
                    !discoveryTime.containsKey(neighbor) -> {
                        classify(pairEdges, EdgeType.FORWARD)
                        enter(neighbor)
                    }
                    // Cross edge or forward edge to already-visited descendant
                    else -> {
                        val neighborDiscovery = discoveryTime[neighbor]!!
                        val neighborFinish = finishTime[neighbor]
                        val currentDiscovery = discoveryTime[nodeId]!!
                        // If neighbor was discovered after current node and already finished,
                        // it's a forward edge to a descendant in our subtree
                        val type = if (neighborFinish != null &&
                            neighborDiscovery > currentDiscovery
                        ) {
                            EdgeType.FORWARD
                        } else {
                            EdgeType.CROSS
                        }
                        classify(pairEdges, type)
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
     * Finds all strongly connected components of the graph. A component with more than one node
     * indicates a cycle.
     *
     * @return sets of node ids, one per component
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
     * Checks whether the graph contains a cycle, self-loops included.
     */
    fun hasCycles(): Boolean {
        // Check for self-loops first (quick check)
        if (edges.any { it.fromId == it.toId }) {
            return true
        }
        // Check for SCCs with multiple nodes
        return findStronglyConnectedComponents().any { it.size > 1 }
    }

    /**
     * Returns the node ids in topological order. Nodes on a cycle are left out. The result
     * contains every node only for acyclic graphs.
     */
    fun getTopologicalOrder(): List<String> {
        val inDegree = mutableMapOf<String, Int>()
        val queue = ArrayDeque<String>()
        val result = mutableListOf<String>()

        // Initialize in-degrees, counted per neighbor like the decrements below
        nodes.keys.forEach { inDegree[it] = 0 }
        adjacency.values.forEach { neighbors ->
            neighbors.forEach { inDegree[it] = (inDegree[it] ?: 0) + 1 }
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
     * Returns a copy of this graph with node dimensions replaced by [measuredDimensions] where
     * present. Nodes without an entry keep the dimensions they have.
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
    private val edgeMap = mutableMapOf<String, KuiverEdge>()

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
     * Adds [edge], ignoring it when either endpoint is missing from the graph or when its
     * [KuiverEdge.id] is already taken. A builder holds at most one edge per id, and the first one
     * added wins, anchors and type included.
     *
     * @return `true` if the edge was added
     * @throws IllegalArgumentException if the id already belongs to an edge between other nodes
     */
    fun addEdge(edge: KuiverEdge): Boolean {
        if (!nodes.containsKey(edge.fromId) || !nodes.containsKey(edge.toId)) {
            return false
        }
        val existing = edgeMap[edge.id]
        if (existing != null) {
            requireSameEndpoints(existing, edge)
            return false
        }

        edges.add(edge)
        adjacency[edge.fromId]?.add(edge.toId)
        edgeMap[edge.id] = edge
        return true
    }

    /**
     * Checks whether an edge from [from] to [to] would close a cycle over the nodes and edges
     * added so far.
     *
     * @param from id of the edge's start node
     * @param to id of the edge's end node
     * @return `true` if the edge would close a cycle
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

private fun requireSameEndpoints(existing: KuiverEdge, edge: KuiverEdge) {
    require(existing.fromId == edge.fromId && existing.toId == edge.toId) {
        "Edge id ${edge.id} already connects ${existing.fromId} -> ${existing.toId}"
    }
}

/**
 * Iterative reachability check over an adjacency map
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
