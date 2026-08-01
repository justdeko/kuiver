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
import com.dk.kuiver.model.KuiverNode

/**
 * What the viewer reports back while the user works with the graph. Every callback is optional and
 * none of them has to change any state: selection, hover and drag are already tracked in
 * [com.dk.kuiver.KuiverInteractionState], and these are for the side effects around them.
 *
 * @property onNodeClick a node was tapped or clicked
 * @property onNodeLongPress a node was long pressed. Setting this makes a long press its own
 * gesture, so it no longer also fires [onNodeClick]
 * @property onNodeDragStart a node drag passed the touch slop
 * @property onNodeDrag the dragged node moved, with its total displacement so far in graph dp
 * @property onNodeDragEnd the drag finished, with the total displacement it covered. The node has
 * already been moved by then, see [com.dk.kuiver.RelayoutPolicy] for how long that survives
 * @property onCanvasClick the canvas was tapped, the usual place to deselect. A tap a node handled
 * does not reach it, so this fires for taps on nodes only when no node interaction is enabled at
 * all
 */
@Immutable
data class KuiverInteractionCallbacks(
    val onNodeClick: ((KuiverNode) -> Unit)? = null,
    val onNodeLongPress: ((KuiverNode) -> Unit)? = null,
    val onNodeDragStart: ((KuiverNode) -> Unit)? = null,
    val onNodeDrag: ((KuiverNode, DpOffset) -> Unit)? = null,
    val onNodeDragEnd: ((KuiverNode, DpOffset) -> Unit)? = null,
    val onCanvasClick: (() -> Unit)? = null
) {
    internal val handlesNodeTap: Boolean get() = onNodeClick != null || onNodeLongPress != null

    companion object {
        /** No callbacks at all, the default of every [KuiverViewer]. */
        val None = KuiverInteractionCallbacks()
    }
}

/**
 * Hover, tap and drag handling for a single node.
 *
 * Attached to the node's own box, so hit testing is the node's real bounds and the gestures land
 * before the viewer's pan and zoom sees them: consuming a tap or a drag here is what keeps the
 * graph still while a node is worked with.
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
    // Kept fresh without restarting the gesture loops, which would drop a drag in progress
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
                            if (interaction.hoveredNodeId == nodeId) interaction.hoveredNodeId = null
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
                    // The node sits inside the viewer's scaled layer, so the pointer deltas that
                    // reach it are already in graph space and only need the density applied
                    interaction.dragBy(DpOffset(dragAmount.x.toDp(), dragAmount.y.toDp()))
                    currentCallbacks.onNodeDrag?.invoke(currentNode, interaction.dragOffset)
                },
                onDragEnd = {
                    val travelled = interaction.dragOffset
                    // Move first, clear second: both land in the same snapshot, so the node never
                    // renders for a frame back at where it was picked up
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
