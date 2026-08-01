package com.dk.kuiver

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpOffset

/** How a tap on a node changes [KuiverInteractionState.selectedNodeIds]. */
enum class SelectionMode {
    /** Taps leave the selection alone. */
    NONE,

    /** A tap selects the node and deselects everything else. */
    SINGLE,

    /** A tap toggles the node, so several nodes can be selected at once. */
    MULTIPLE
}

/** What happens to nodes the user has moved when the graph is laid out again. */
enum class RelayoutPolicy {
    /** Moved nodes keep where the user left them, the rest is laid out as usual. */
    KEEP_MANUAL,

    /** Layout owns every position, so a move survives only until the next layout pass. */
    RELAYOUT_ALL
}

/**
 * Selection, hover and drag of the nodes in a [KuiverViewerState], reachable as
 * [KuiverViewerState.interaction].
 *
 * The state is what a node is, never how it looks: nodes stay rendered by the caller's
 * `nodeContent`, which reads the same flags through its [com.dk.kuiver.renderer.KuiverNodeScope]
 * receiver.
 *
 * @property selectedNodeIds ids of the selected nodes, empty when nothing is selected
 * @property hoveredNodeId id of the node the pointer is over, `null` on touch and when the pointer
 * is over no node
 * @property draggedNodeId id of the node being dragged, `null` while no drag is in progress
 */
@Stable
class KuiverInteractionState internal constructor() {
    var selectedNodeIds: Set<String> by mutableStateOf(emptySet())
        private set

    var hoveredNodeId: String? by mutableStateOf(null)
        internal set

    var draggedNodeId: String? by mutableStateOf(null)
        internal set

    /**
     * How far the dragged node has travelled since the drag started, in the dp space the graph is
     * laid out in. It changes every frame of a drag, and the node it belongs to is placed at its
     * laid out position plus this, so the node itself moves through placement alone and never
     * recomposes. The batched edge layer reads it while drawing and holds still too; per-edge
     * `edgeContent` takes its endpoints by value, so those do recompose once per frame.
     */
    internal var dragOffset: DpOffset by mutableStateOf(DpOffset.Zero)
        private set

    /** True while a node is being dragged. */
    val isDragging: Boolean get() = draggedNodeId != null

    /** Selects [nodeId] alone, dropping whatever was selected before. */
    fun select(nodeId: String) {
        if (selectedNodeIds.size != 1 || nodeId !in selectedNodeIds) {
            selectedNodeIds = setOf(nodeId)
        }
    }

    /** Replaces the selection with [nodeIds]. */
    fun selectAll(nodeIds: Set<String>) {
        if (selectedNodeIds != nodeIds) selectedNodeIds = nodeIds.toSet()
    }

    /** Adds [nodeId] to the selection if it is not selected, removes it if it is. */
    fun toggleSelection(nodeId: String) {
        selectedNodeIds = if (nodeId in selectedNodeIds) {
            selectedNodeIds - nodeId
        } else {
            selectedNodeIds + nodeId
        }
    }

    /** Deselects everything. */
    fun clearSelection() {
        if (selectedNodeIds.isNotEmpty()) selectedNodeIds = emptySet()
    }

    /** Whether [nodeId] is part of the selection. */
    fun isSelected(nodeId: String): Boolean = nodeId in selectedNodeIds

    /** Applies a tap on [nodeId] under [mode]. */
    internal fun applySelectionTap(nodeId: String, mode: SelectionMode) {
        when (mode) {
            SelectionMode.NONE -> Unit
            SelectionMode.SINGLE -> select(nodeId)
            SelectionMode.MULTIPLE -> toggleSelection(nodeId)
        }
    }

    /** Live displacement of [nodeId], [DpOffset.Zero] for every node that is not being dragged. */
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

    /** Forgets the node [nodeId], called when it leaves the graph. */
    internal fun forget(nodeId: String) {
        if (nodeId in selectedNodeIds) selectedNodeIds = selectedNodeIds - nodeId
        if (hoveredNodeId == nodeId) hoveredNodeId = null
        if (draggedNodeId == nodeId) endDrag()
    }
}
