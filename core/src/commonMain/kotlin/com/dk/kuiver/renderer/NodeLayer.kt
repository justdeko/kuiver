package com.dk.kuiver.renderer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.dk.kuiver.KuiverViewerState
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.model.NodeDimensions
import kotlin.math.roundToInt

/**
 * Hosts every node: one subcomposition per node, measured and placed in the same pass.
 *
 * Nodes without explicit dimensions are measured with unbounded constraints and their sizes
 * reported through [onMeasured] on every change, including sizes that settle late (web fonts).
 * Positions are read in the placement block only, so a layout transition re-places the nodes
 * without recomposing or re-measuring them.
 *
 * @param kuiver the laid out graph to host
 * @param source the graph as the caller supplied it; a node is auto-sized exactly when it has no
 * dimensions here
 * @param centerX x center of the viewport
 * @param centerY y center of the viewport
 * @param targets the caller's layout generation
 * @param transition shared progress driving node movement
 * @param state the viewer state, for the interaction a node reports into
 * @param config viewer configuration, read for which node gestures are enabled
 * @param callbacks the caller's interaction callbacks
 * @param skipAnimation places nodes at [targets] directly, as initial placement does
 * @param onMeasured receives the sizes of all auto-sized nodes, whenever they change
 * @param nodeContent composable content of a node
 */
@Composable
internal fun NodeLayer(
    kuiver: Kuiver,
    source: Kuiver,
    centerX: Dp,
    centerY: Dp,
    targets: NodePositions,
    transition: LayoutTransition,
    state: KuiverViewerState,
    config: KuiverViewerConfig,
    callbacks: KuiverInteractionCallbacks,
    skipAnimation: Boolean,
    onMeasured: (Map<String, NodeDimensions>) -> Unit,
    nodeContent: @Composable KuiverNodeScope.(KuiverNode) -> Unit
) {
    // Plain map, not snapshot state: only compared to decide whether to report
    val reported = remember { mutableMapOf<String, NodeDimensions>() }
    val interaction = state.interaction

    SubcomposeLayout(modifier = Modifier.fillMaxSize()) { constraints ->
        val placed = ArrayList<PlacedNode>(kuiver.nodes.size)
        val measured = mutableMapOf<String, NodeDimensions>()

        kuiver.nodes.forEach { (nodeId, node) ->
            val explicit = source.nodes[nodeId]?.dimensions
            val content = subcompose(nodeId) {
                // Explicit dimensions fix the node box; otherwise it wraps its content
                val sizeModifier = if (explicit != null) {
                    Modifier.size(explicit.width, explicit.height)
                } else {
                    Modifier
                }
                // Remembered, so the content is not handed a new receiver on every measure pass
                val scope = remember(nodeId) { NodeScope(nodeId, interaction) }
                Box(sizeModifier.nodeInteraction(node, state, config, callbacks)) {
                    scope.nodeContent(node)
                }
            }
            // The box above is the single root of the slot
            val placeable = content.firstOrNull()?.measure(Constraints()) ?: return@forEach
            placed.add(PlacedNode(nodeId, placeable))
            if (explicit == null) {
                measured[nodeId] = NodeDimensions(placeable.width.toDp(), placeable.height.toDp())
            }
        }

        if (measured != reported) {
            reported.clear()
            reported.putAll(measured)
            onMeasured(measured)
        }

        val centerXPx = centerX.toPx()
        val centerYPx = centerY.toPx()
        // The placement block is not a Density, so capture this pass's for the toPx calls
        val measureScope = this

        // Fills the viewport; nodes may reach outside it, which the viewer clips
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val height =
            if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight

        layout(width, height) {
            with(measureScope) {
                placed.forEach { (nodeId, placeable) ->
                    // The live drag offset is added here rather than written into the graph, so a
                    // node follows the pointer through placement alone, without recomposing
                    val position = transition.positionOf(nodeId, targets, skipAnimation) +
                            interaction.dragOffsetOf(nodeId)
                    placeable.place(
                        x = (centerXPx + position.x.toPx() - placeable.width / 2f).roundToInt(),
                        y = (centerYPx + position.y.toPx() - placeable.height / 2f).roundToInt()
                    )
                }
            }
        }
    }
}

/** A node's subcomposition, measured and waiting to be placed. */
private data class PlacedNode(val nodeId: String, val placeable: Placeable)
