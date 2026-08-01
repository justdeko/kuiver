package com.dk.kuiver.model

/**
 * Scoping function for building Kuiver graphs.
 *
 * Example:
 * ```kotlin
 * val graph = buildKuiver {
 *     nodes("A", "B", "C")
 *     edge("A", "B")
 *     edge("B", "C")
 * }
 * ```
 *
 * @return A new immutable Kuiver instance
 */
fun buildKuiver(block: KuiverBuilder.() -> Unit): Kuiver = KuiverBuilder().apply(block).build()

/**
 * Builds a new Kuiver instance with the given nodes and edges from the original graph,
 * automatically classifying all edges.
 *
 * @param nodes The nodes to include in the new graph
 * @param originalEdges The edges from the original graph (without type classification)
 * @return A new Kuiver instance with all edges classified by type
 */
fun buildKuiverWithClassifiedEdges(
    nodes: Collection<KuiverNode>,
    originalEdges: Collection<KuiverEdge>
): Kuiver {
    val tempKuiver = buildKuiver {
        nodes.forEach { addNode(it) }
        originalEdges.forEach { addEdge(it) }
    }

    val edgeClassifications = tempKuiver.classifyAllEdges()

    return buildKuiver {
        nodes.forEach { addNode(it) }
        edgeClassifications.forEach { (edge, type) ->
            addEdge(edge.copy(type = type))
        }
    }
}

/**
 * Adds multiple nodes by their IDs.
 *
 * Example:
 * ```kotlin
 * buildKuiver {
 *     nodes("A", "B", "C", "D")
 * }
 * ```
 */
fun KuiverBuilder.nodes(vararg ids: String) = nodes(ids.toList())

/**
 * Adds multiple nodes from a collection of IDs.
 *
 * Example:
 * ```kotlin
 * buildKuiver {
 *     nodes(listOf("A", "B", "C"))
 * }
 * ```
 */
fun KuiverBuilder.nodes(ids: Collection<String>) {
    ids.forEach { addNode(KuiverNode(it)) }
}

/**
 * Adds an edge between two nodes, unless they are already connected in that direction. See
 * [KuiverBuilder.addEdge], which returns whether the edge was taken.
 *
 * Example:
 * ```kotlin
 * buildKuiver {
 *     nodes("A", "B")
 *     edge("A", "B")
 * }
 * ```
 *
 * @param from The ID of the starting node
 * @param to The ID of the ending node
 * @param fromAnchor Optional anchor point on the starting node
 * @param toAnchor Optional anchor point on the ending node
 */
fun KuiverBuilder.edge(
    from: String,
    to: String,
    fromAnchor: String? = null,
    toAnchor: String? = null
) {
    addEdge(KuiverEdge(from, to, fromAnchor = fromAnchor, toAnchor = toAnchor))
}

/**
 * Adds multiple edges from pairs, skipping the pairs that are already connected. See [edge].
 *
 * Example:
 * ```kotlin
 * buildKuiver {
 *     nodes("A", "B", "C")
 *     edges(
 *         "A" to "B",
 *         "B" to "C"
 *     )
 * }
 * ```
 */
fun KuiverBuilder.edges(vararg pairs: Pair<String, String>) {
    pairs.forEach { (from, to) ->
        addEdge(KuiverEdge(from, to))
    }
}

/**
 * Adds multiple edges from a list of pairs, skipping the pairs that are already connected.
 * Automatically creates nodes if they don't exist.
 *
 * Example:
 * ```kotlin
 * buildKuiver {
 *     fromEdgeList(
 *         listOf(
 *             "A" to "B",
 *             "B" to "C",
 *             "C" to "D"
 *         )
 *     )
 * }
 * ```
 *
 * @param edges List of pairs representing edges (from, to)
 * @param createNodes Whether to create nodes if they don't exist (default: true)
 */
fun KuiverBuilder.fromEdgeList(edges: List<Pair<String, String>>, createNodes: Boolean = true) {
    if (createNodes) {
        val seenNodes = mutableSetOf<String>()
        edges.forEach { (from, to) ->
            if (from !in seenNodes) {
                addNode(KuiverNode(from))
                seenNodes.add(from)
            }
            if (to !in seenNodes) {
                addNode(KuiverNode(to))
                seenNodes.add(to)
            }
            addEdge(KuiverEdge(from, to))
        }
    } else {
        edges.forEach { (from, to) ->
            addEdge(KuiverEdge(from, to))
        }
    }
}
