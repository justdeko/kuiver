package com.dk.kuiver.renderer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import com.dk.kuiver.model.DEFAULT_NODE_SIZE
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.model.NodeDimensions

/**
 * Measures all nodes using SubcomposeLayout to get their actual dimensions.
 * This allows the layout algorithm to use real measured sizes instead of predefined values.
 *
 * @param kuiver The graph containing nodes to measure
 * @param anchorRegistry The anchor registry to provide during measurement
 * @param nodeContent Composable content for each node
 * @return Map of node IDs to their measured dimensions
 */
@Composable
internal fun measureNodes(
    kuiver: Kuiver,
    anchorRegistry: AnchorPositionRegistry,
    nodeContent: @Composable (KuiverNode) -> Unit
): Map<String, NodeDimensions> {
    val density = LocalDensity.current
    val measuredSizes = mutableMapOf<String, NodeDimensions>()

    SubcomposeLayout {
        kuiver.nodes.forEach { (nodeId, node) ->
            // Use explicit dimensions if provided, otherwise measure
            if (node.dimensions != null) {
                measuredSizes[nodeId] = node.dimensions
            } else {
                // Subcompose and measure this node
                val measurables = subcompose(nodeId) {
                    CompositionLocalProvider(LocalAnchorRegistry provides anchorRegistry) {
                        nodeContent(node)
                    }
                }

                // Measure with unbounded constraints to get intrinsic size
                val placeables = measurables.map {
                    it.measure(Constraints())
                }

                // Calculate actual size from measured placeables
                with(density) {
                    val width =
                        (placeables.maxOfOrNull { it.width } ?: DEFAULT_NODE_SIZE.toInt()).toDp()
                    val height =
                        (placeables.maxOfOrNull { it.height } ?: DEFAULT_NODE_SIZE.toInt()).toDp()
                    measuredSizes[nodeId] = NodeDimensions(width, height)
                }
            }
        }

        // We don't actually place anything here - just measure
        // Return empty layout
        layout(0, 0) {}
    }

    return measuredSizes
}
