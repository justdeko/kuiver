package com.dk.kuiver.renderer

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
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
    private val _anchorPositions =
        mutableStateMapOf<String, SnapshotStateMap<String, AnchorOffset>>()
    val anchorPositions: Map<String, Map<String, AnchorOffset>>
        get() = _anchorPositions

    /**
     * Register an anchor position for a specific node.
     *
     * @param nodeId The ID of the node this anchor belongs to
     * @param anchorId The ID of the anchor within the node's namespace
     * @param offset The position of the anchor relative to the node's top-left corner
     */
    fun registerAnchor(nodeId: String, anchorId: String, offset: AnchorOffset) {
        val nodeAnchors = _anchorPositions.getOrPut(nodeId) { mutableStateMapOf() }
        nodeAnchors[anchorId] = offset
    }

    /**
     * Get the offset of a specific anchor within a node.
     *
     * @param nodeId The ID of the node
     * @param anchorId The ID of the anchor within the node
     * @return The anchor offset, or null if not found
     */
    fun getAnchorOffset(nodeId: String, anchorId: String): AnchorOffset? {
        return _anchorPositions[nodeId]?.get(anchorId)
    }

    /**
     * Clear all anchors for a specific node.
     * This should be called when a node is removed from the graph.
     *
     * @param nodeId The ID of the node to clear anchors for
     */
    fun clearNode(nodeId: String) {
        _anchorPositions.remove(nodeId)
    }

    /**
     * Clear all anchor positions.
     * This should be called when the entire graph is cleared.
     */
    fun clear() {
        _anchorPositions.clear()
    }
}

internal val LocalAnchorRegistry = compositionLocalOf<AnchorPositionRegistry> {
    error("CompositionLocal LocalAnchorRegistry not present")
}
