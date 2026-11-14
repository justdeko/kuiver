package com.dk.kuiver.renderer

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.DpOffset
import com.dk.kuiver.KuiverViewerState
import com.dk.kuiver.SelectionMode
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode

/**
 * What the viewer reports back while the user works with the graph. Every callback is optional and
 * none of them has to change any state: selection, hover and drag are already tracked in
 * [com.dk.kuiver.KuiverInteractionState], and these are for the side effects around them.
 *
 * @property onNodeClick a node was tapped or clicked
 * @property onNodeLongPress a node was long pressed. Setting this makes a long press its own
 * gesture and [onNodeClick] won't fire
 * @property onNodeDragStart a node drag passed the touch slop
 * @property onNodeDrag the dragged node moved, with its total displacement so far in graph dp
 * @property onNodeDragEnd the drag finished, with the total displacement it covered. The node has
 * already been moved by then, see [com.dk.kuiver.RelayoutPolicy] for how long that survives
 * @property onEdgeClick an edge was tapped or clicked. Setting this makes edges tappable without
 * [KuiverViewerConfig.edgeSelectionMode]
 * @property onCanvasClick the canvas was tapped (nodes/edges excluded).
 * This fires for taps on nodes only when no node interaction is enabled
 */
@Immutable
public data class KuiverInteractionCallbacks(
    val onNodeClick: ((KuiverNode) -> Unit)? = null,
    val onNodeLongPress: ((KuiverNode) -> Unit)? = null,
    val onNodeDragStart: ((KuiverNode) -> Unit)? = null,
    val onNodeDrag: ((KuiverNode, DpOffset) -> Unit)? = null,
    val onNodeDragEnd: ((KuiverNode, DpOffset) -> Unit)? = null,
    val onEdgeClick: ((KuiverEdge) -> Unit)? = null,
    val onCanvasClick: (() -> Unit)? = null
) {
    internal val handlesNodeTap: Boolean get() = onNodeClick != null || onNodeLongPress != null

    public companion object {
        /**
         * No callbacks at all, the default of every [KuiverViewer]
         */
        public val None: KuiverInteractionCallbacks = KuiverInteractionCallbacks()
    }
}

/**
 * Hover, tap and drag handling for a single node, with the matching semantics.
 *
 * Attached to the node's own box: hit testing uses the node's real bounds, and gestures register
 * before the viewer's pan and zoom.
 *
 * @param node the node this box renders
 * @param state the viewer state to report into
 * @param config the viewer config, read for which gestures are enabled
 * @param callbacks the caller's callbacks
 */
internal fun Modifier.nodeInteraction(
    node: KuiverNode,
    state: KuiverViewerState,
    config: KuiverViewerConfig,
    callbacks: KuiverInteractionCallbacks
): Modifier = this then NodeInteractionElement(node, state, config, callbacks)

/**
 * Creates and updates the [NodeInteractionNode] of a node box.
 */
private data class NodeInteractionElement(
    val node: KuiverNode,
    val state: KuiverViewerState,
    val config: KuiverViewerConfig,
    val callbacks: KuiverInteractionCallbacks
) : ModifierNodeElement<NodeInteractionNode>() {
    override fun create() = NodeInteractionNode(node, state, config, callbacks)

    override fun update(node: NodeInteractionNode) {
        node.update(this.node, state, config, callbacks)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "nodeInteraction"
        properties["nodeId"] = node.id
    }
}

/**
 * Handles pointer input and semantics for a node box.
 */
private class NodeInteractionNode(
    private var graphNode: KuiverNode,
    private var state: KuiverViewerState,
    private var config: KuiverViewerConfig,
    private var callbacks: KuiverInteractionCallbacks
) : DelegatingNode(), SemanticsModifierNode, ObserverModifierNode {
    private var hoverInput: SuspendingPointerInputModifierNode? = null
    private var tapInput: SuspendingPointerInputModifierNode? = null
    private var dragInput: SuspendingPointerInputModifierNode? = null

    // Whether the tap detector handles long presses
    private var tapHasLongPress = false

    // Whether the node is selected, exposed to semantics
    private var selected = false

    private val interaction get() = state.interaction
    private val tracksPointer get() = config.hoverEnabled || config.edgeHoverEnabled
    private val tappable
        get() = config.selectionMode != SelectionMode.NONE || callbacks.handlesNodeTap
    private val hasLongPress get() = callbacks.onNodeLongPress != null

    override val shouldMergeDescendantSemantics: Boolean get() = tappable

    override fun onAttach() {
        syncInputs()
        observeSelection()
    }

    fun update(
        node: KuiverNode,
        state: KuiverViewerState,
        config: KuiverViewerConfig,
        callbacks: KuiverInteractionCallbacks
    ) {
        val restart = node.id != graphNode.id || state !== this.state
        graphNode = node
        this.state = state
        this.config = config
        this.callbacks = callbacks

        // Restart gesture detection when the node or state changes
        if (restart) {
            hoverInput?.resetPointerInputHandler()
            tapInput?.resetPointerInputHandler()
            dragInput?.resetPointerInputHandler()
        }
        syncInputs()
        observeSelection()
        invalidateSemantics()
    }

    override fun onObservedReadsChanged() {
        val before = selected
        observeSelection()
        if (selected != before) invalidateSemantics()
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        if (config.selectionMode != SelectionMode.NONE) {
            selected = this@NodeInteractionNode.selected
        }
        if (tappable) {
            onClick {
                tap()
                true
            }
        }
        if (hasLongPress) {
            onLongClick {
                callbacks.onNodeLongPress?.invoke(graphNode)
                true
            }
        }
    }

    private fun observeSelection() {
        if (config.selectionMode == SelectionMode.NONE) {
            selected = false
            return
        }
        observeReads { selected = interaction.isSelected(graphNode.id) }
    }

    private fun tap() {
        interaction.applySelectionTap(graphNode.id, config.selectionMode)
        callbacks.onNodeClick?.invoke(graphNode)
    }

    // Add and remove the detectors to match the config
    private fun syncInputs() {
        val wantsTap = tappable
        val wantsDrag = config.nodeDragEnabled
        val unchanged = tracksPointer == (hoverInput != null) &&
                wantsTap == (tapInput != null) &&
                wantsDrag == (dragInput != null) &&
                (!wantsTap || tapHasLongPress == hasLongPress)
        if (unchanged) return

        // Rebuild all inputs to keep their order
        hoverInput?.let(::undelegate)
        tapInput?.let(::undelegate)
        dragInput?.let(::undelegate)
        tapHasLongPress = hasLongPress
        hoverInput = if (tracksPointer) delegate(hoverDetector()) else null
        tapInput = if (wantsTap) delegate(tapDetector(tapHasLongPress)) else null
        dragInput = if (wantsDrag) delegate(dragDetector()) else null
    }

    private fun hoverDetector() = SuspendingPointerInputModifierNode {
        awaitPointerEventScope {
            while (true) {
                val type = awaitPointerEvent().type
                val nodeId = graphNode.id
                when (type) {
                    PointerEventType.Enter -> {
                        interaction.pointerNodeId = nodeId
                        if (config.hoverEnabled) interaction.hoveredNodeId = nodeId
                    }

                    PointerEventType.Exit -> {
                        if (interaction.pointerNodeId == nodeId) interaction.pointerNodeId = null
                        if (interaction.hoveredNodeId == nodeId) interaction.hoveredNodeId = null
                    }
                }
            }
        }
    }

    private fun tapDetector(withLongPress: Boolean) = SuspendingPointerInputModifierNode {
        detectTapGestures(
            onLongPress = if (withLongPress) {
                { callbacks.onNodeLongPress?.invoke(graphNode) }
            } else {
                null
            },
            onTap = { tap() }
        )
    }

    private fun dragDetector() = SuspendingPointerInputModifierNode {
        detectDragGestures(
            onDragStart = {
                interaction.startDrag(graphNode.id)
                callbacks.onNodeDragStart?.invoke(graphNode)
            },
            onDrag = { change, dragAmount ->
                change.consume()
                interaction.dragBy(DpOffset(dragAmount.x.toDp(), dragAmount.y.toDp()))
                callbacks.onNodeDrag?.invoke(graphNode, interaction.dragOffset)
            },
            onDragEnd = {
                val travelled = interaction.dragOffset
                state.moveNodeBy(graphNode.id, travelled)
                interaction.endDrag()
                callbacks.onNodeDragEnd?.invoke(graphNode, travelled)
            },
            onDragCancel = { interaction.endDrag() }
        )
    }
}
