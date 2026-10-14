package com.dk.kuiver

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpOffset

/**
 * How a tap on a node or edge changes [KuiverInteractionState.selectedNodeIds] and
 * [KuiverInteractionState.selectedEdgeIds].
 */
enum class SelectionMode {
    /** Taps don't affect selection. */
    NONE,

    /** A tap selects the node or edge and deselects everything else. */
    SINGLE,

    /** A tap toggles the node or edge. */
    MULTIPLE
}

/**
 * What happens to nodes the user has moved when the graph is laid out again
 */
enum class RelayoutPolicy {
    /** Moved nodes keep where the user left them, the rest is laid out as usual. */
    KEEP_MANUAL,

    /** A move survives only until the next layout pass. */
    RELAYOUT_ALL
}

/**
 * Selection, hover and drag of the nodes and edges in a [KuiverViewerState], reachable as
 * [KuiverViewerState.interaction].
 *
 * The state is what a node is, never how it looks: nodes stay rendered by the caller's
 * `nodeContent`, which reads the same flags through its [com.dk.kuiver.renderer.KuiverNodeScope]
 * receiver. Edges do the same through [com.dk.kuiver.renderer.KuiverEdgeScope].
 *
 * Nodes and edges share one selection. Selecting one kind clears the other.
 *
 * @property selectedNodeIds ids of the selected nodes, empty when nothing is selected
 * @property selectedEdgeIds ids of the selected edges, empty when no edge is selected
 * @property hoveredNodeId id of the node the pointer is over, `null` on touch and when the pointer
 * is over no node
 * @property hoveredEdgeId id of the edge the pointer is over, `null` on touch and when the pointer
 * is over no edge
 * @property draggedNodeId id of the node being dragged, `null` while no drag is in progress
 * @property isDragging true while a node is being dragged
 */
@Stable
class KuiverInteractionState internal constructor() {
    var selectedNodeIds: Set<String> by mutableStateOf(emptySet())
        private set

    var selectedEdgeIds: Set<String> by mutableStateOf(emptySet())
        private set

    var hoveredNodeId: String? by mutableStateOf(null)
        internal set

    var hoveredEdgeId: String? by mutableStateOf(null)
        internal set

    var draggedNodeId: String? by mutableStateOf(null)
        internal set

    // How far the dragged node has travelled since the drag started, in graph dp.
    // Applied during placement so the node never recomposes mid-drag.
    internal var dragOffset: DpOffset by mutableStateOf(DpOffset.Zero)
        private set

    val isDragging: Boolean get() = draggedNodeId != null

    /**
     * Selects the node alone, dropping whatever was selected before
     *
     * @param nodeId the node id
     */
    fun select(nodeId: String) {
        if (selectedNodeIds.size != 1 || nodeId !in selectedNodeIds) {
            selectedNodeIds = setOf(nodeId)
        }
        clearEdgeSelection()
    }

    /**
     * Selects the edge alone, dropping whatever was selected before
     *
     * @param edgeId the edge id
     */
    fun selectEdge(edgeId: String) {
        if (selectedEdgeIds.size != 1 || edgeId !in selectedEdgeIds) {
            selectedEdgeIds = setOf(edgeId)
        }
        clearNodeSelection()
    }

    /**
     * Replaces the selection with the nodes
     *
     * @param nodeIds the node ids
     */
    fun selectAll(nodeIds: Set<String>) {
        if (selectedNodeIds != nodeIds) selectedNodeIds = nodeIds.toSet()
        clearEdgeSelection()
    }

    /**
     * Replaces the selection with the edges
     *
     * @param edgeIds the edge ids
     */
    fun selectAllEdges(edgeIds: Set<String>) {
        if (selectedEdgeIds != edgeIds) selectedEdgeIds = edgeIds.toSet()
        clearNodeSelection()
    }

    /**
     * Toggles the node selection
     *
     * @param nodeId the node id
     */
    fun toggleSelection(nodeId: String) {
        selectedNodeIds = if (nodeId in selectedNodeIds) {
            selectedNodeIds - nodeId
        } else {
            selectedNodeIds + nodeId
        }
    }

    /**
     * Toggles the edge selection
     *
     * @param edgeId the edge id
     */
    fun toggleEdgeSelection(edgeId: String) {
        selectedEdgeIds = if (edgeId in selectedEdgeIds) {
            selectedEdgeIds - edgeId
        } else {
            selectedEdgeIds + edgeId
        }
    }

    /**
     * Deselects all nodes and edges
     */
    fun clearSelection() {
        clearNodeSelection()
        clearEdgeSelection()
    }

    /**
     * Checks whether the node is selected
     *
     * @param nodeId the node id
     * @return true if the node is part of the selection
     */
    fun isSelected(nodeId: String): Boolean = nodeId in selectedNodeIds

    /**
     * Checks whether the edge is selected
     *
     * @param edgeId the edge id
     * @return true if the edge is part of the selection
     */
    fun isEdgeSelected(edgeId: String): Boolean = edgeId in selectedEdgeIds

    private fun clearNodeSelection() {
        if (selectedNodeIds.isNotEmpty()) selectedNodeIds = emptySet()
    }

    private fun clearEdgeSelection() {
        if (selectedEdgeIds.isNotEmpty()) selectedEdgeIds = emptySet()
    }

    // Apply a tap on the node under mode
    internal fun applySelectionTap(nodeId: String, mode: SelectionMode) {
        when (mode) {
            SelectionMode.NONE -> Unit
            SelectionMode.SINGLE -> select(nodeId)
            SelectionMode.MULTIPLE -> toggleSelection(nodeId)
        }
    }

    // Apply a tap on the edge under mode
    internal fun applyEdgeSelectionTap(edgeId: String, mode: SelectionMode) {
        when (mode) {
            SelectionMode.NONE -> Unit
            SelectionMode.SINGLE -> selectEdge(edgeId)
            SelectionMode.MULTIPLE -> toggleEdgeSelection(edgeId)
        }
    }

    // Apply a tap on the empty canvas
    internal fun clearSelectionOnCanvasTap(nodeMode: SelectionMode, edgeMode: SelectionMode) {
        if (nodeMode != SelectionMode.NONE) clearNodeSelection()
        if (edgeMode != SelectionMode.NONE) clearEdgeSelection()
    }

    // Live displacement of the node, zero for every node that is not being dragged
    internal fun dragOffsetOf(nodeId: String): DpOffset =
        if (draggedNodeId == nodeId) dragOffset else DpOffset.Zero

    internal fun startDrag(nodeId: String) {
        draggedNodeId = nodeId
        dragOffset = DpOffset.Zero
    }

    internal fun dragBy(delta: DpOffset) {
        if (draggedNodeId != null) dragOffset += delta
    }

    internal fun endDrag() {
        draggedNodeId = null
        dragOffset = DpOffset.Zero
    }

    // Forget the node when it leaves the graph
    internal fun forget(nodeId: String) {
        if (nodeId in selectedNodeIds) selectedNodeIds = selectedNodeIds - nodeId
        if (hoveredNodeId == nodeId) hoveredNodeId = null
        if (draggedNodeId == nodeId) endDrag()
    }

    // Forget every edge not in edgeIds when edges leave the graph
    internal fun retainEdges(edgeIds: Set<String>) {
        if (selectedEdgeIds.any { it !in edgeIds }) {
            selectedEdgeIds = selectedEdgeIds intersect edgeIds
        }
        if (hoveredEdgeId != null && hoveredEdgeId !in edgeIds) hoveredEdgeId = null
    }
}
