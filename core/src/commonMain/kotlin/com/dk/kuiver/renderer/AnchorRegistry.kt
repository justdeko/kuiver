package com.dk.kuiver.renderer

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.layout.LayoutCoordinates
import com.dk.kuiver.model.AnchorOffset

/**
 * Registry that tracks anchor positions for all nodes in the graph.
 * This is managed at the KuiverViewer level and accessible via composition local.
 *
 * Anchors are scoped per-node, meaning each node has its own namespace of anchor IDs.
 * Multiple nodes can have anchors with the same ID without conflict.
 */
internal class AnchorPositionRegistry {
    /**
     * Maps nodeId -> anchorId -> offset relative to node's top-left corner.
     * Uses mutableStateMapOf to trigger recomposition when anchor positions change.
     */
    val anchorPositions: Map<String, Map<String, AnchorOffset>>
        field = mutableStateMapOf<String, SnapshotStateMap<String, AnchorOffset>>()

    /**
     * Register an anchor position for a specific node.
     *
     * @param nodeId The ID of the node this anchor belongs to
     * @param anchorId The ID of the anchor within the node's namespace
     * @param offset The position of the anchor relative to the node's top-left corner
     */
    fun registerAnchor(nodeId: String, anchorId: String, offset: AnchorOffset) {
        val nodeAnchors = anchorPositions.getOrPut(nodeId) { mutableStateMapOf() }
        // Skip unchanged offsets
        if (nodeAnchors[anchorId] != offset) nodeAnchors[anchorId] = offset
    }

    /**
     * Remove an anchor that is no longer part of its node content.
     *
     * @param nodeId The ID of the node this anchor belongs to
     * @param anchorId The ID of the anchor within the node's namespace
     */
    fun unregisterAnchor(nodeId: String, anchorId: String) {
        anchorPositions[nodeId]?.remove(anchorId)
    }

    /**
     * Get the offset of a specific anchor within a node.
     *
     * @param nodeId The ID of the node
     * @param anchorId The ID of the anchor within the node
     * @return The anchor offset, or null if not found
     */
    fun getAnchorOffset(nodeId: String, anchorId: String): AnchorOffset? {
        return anchorPositions[nodeId]?.get(anchorId)
    }

    /**
     * Clear all anchors for a specific node.
     * This should be called when a node is removed from the graph.
     *
     * @param nodeId The ID of the node to clear anchors for
     */
    fun clearNode(nodeId: String) {
        anchorPositions.remove(nodeId)
    }

    /**
     * Clear all anchor positions.
     * This should be called when the entire graph is cleared.
     */
    fun clear() {
        anchorPositions.clear()
    }
}

internal val LocalAnchorRegistry = compositionLocalOf<AnchorPositionRegistry> {
    error("CompositionLocal LocalAnchorRegistry not present")
}

/**
 * Layout coordinates of a node's box for anchor origin
 *
 * @property nodeId id of the node
 * @property coordinates layout coordinates of the box, null until it is placed
 */
internal class NodeCoordinates(val nodeId: String) {
    var coordinates: LayoutCoordinates? = null
}

/**
 * The [NodeCoordinates] of the enclosing node content. null outside a node.
 */
internal val LocalNodeCoordinates = staticCompositionLocalOf<NodeCoordinates?> { null }
