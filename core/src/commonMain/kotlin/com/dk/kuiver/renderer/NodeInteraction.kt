package com.dk.kuiver.renderer

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
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
data class KuiverInteractionCallbacks(
    val onNodeClick: ((KuiverNode) -> Unit)? = null,
    val onNodeLongPress: ((KuiverNode) -> Unit)? = null,
    val onNodeDragStart: ((KuiverNode) -> Unit)? = null,
    val onNodeDrag: ((KuiverNode, DpOffset) -> Unit)? = null,
    val onNodeDragEnd: ((KuiverNode, DpOffset) -> Unit)? = null,
    val onEdgeClick: ((KuiverEdge) -> Unit)? = null,
    val onCanvasClick: (() -> Unit)? = null
) {
    internal val handlesNodeTap: Boolean get() = onNodeClick != null || onNodeLongPress != null

    companion object {
        /**
         * No callbacks at all, the default of every [KuiverViewer]
         */
        val None = KuiverInteractionCallbacks()
    }
}

/**
 * Hover, tap and drag handling for a single node.
 *
 * Attached to the node's own box: hit testing uses the node's real bounds, and gestures register
 * before the viewer's pan and zoom.
 *
 * @param node the node this box renders
 * @param state the viewer state to report into
 * @param config the viewer config, read for which gestures are enabled
 * @param callbacks the caller's callbacks
 */
@Composable
internal fun Modifier.nodeInteraction(
    node: KuiverNode,
    state: KuiverViewerState,
    config: KuiverViewerConfig,
    callbacks: KuiverInteractionCallbacks
): Modifier {
    val nodeId = node.id
    val interaction = state.interaction
    val currentNode by rememberUpdatedState(node)
    val currentConfig by rememberUpdatedState(config)
    val currentCallbacks by rememberUpdatedState(callbacks)

    val tappable = config.selectionMode != SelectionMode.NONE || callbacks.handlesNodeTap
    val hasLongPress = callbacks.onNodeLongPress != null

    var modifier = this

    if (config.hoverEnabled) {
        modifier = modifier.pointerInput(nodeId) {
            awaitPointerEventScope {
                while (true) {
                    when (awaitPointerEvent().type) {
                        PointerEventType.Enter -> interaction.hoveredNodeId = nodeId
                        PointerEventType.Exit ->
                            if (interaction.hoveredNodeId == nodeId) interaction.hoveredNodeId =
                                null
                    }
                }
            }
        }
    }

    if (tappable) {
        modifier = modifier.pointerInput(nodeId, hasLongPress) {
            detectTapGestures(
                onLongPress = if (hasLongPress) {
                    { currentCallbacks.onNodeLongPress?.invoke(currentNode) }
                } else {
                    null
                },
                onTap = {
                    interaction.applySelectionTap(nodeId, currentConfig.selectionMode)
                    currentCallbacks.onNodeClick?.invoke(currentNode)
                }
            )
        }
    }

    if (config.nodeDragEnabled) {
        modifier = modifier.pointerInput(nodeId) {
            detectDragGestures(
                onDragStart = {
                    interaction.startDrag(nodeId)
                    currentCallbacks.onNodeDragStart?.invoke(currentNode)
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    interaction.dragBy(DpOffset(dragAmount.x.toDp(), dragAmount.y.toDp()))
                    currentCallbacks.onNodeDrag?.invoke(currentNode, interaction.dragOffset)
                },
                onDragEnd = {
                    val travelled = interaction.dragOffset
                    state.moveNodeBy(nodeId, travelled)
                    interaction.endDrag()
                    currentCallbacks.onNodeDragEnd?.invoke(currentNode, travelled)
                },
                onDragCancel = { interaction.endDrag() }
            )
        }
    }

    return modifier
}
