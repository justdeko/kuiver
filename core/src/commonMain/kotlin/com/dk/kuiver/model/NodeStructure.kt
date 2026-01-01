package com.dk.kuiver.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp

/**
 * Represents the physical dimensions of a node for layout calculations.
 * The library uses this for positioning only - actual rendering is handled by nodeContent.
 */
@Immutable
data class NodeDimensions(
    val width: Dp,
    val height: Dp
)

/**
 * Represents the offset of an anchor point relative to the node's top-left corner.
 */
@Immutable
data class AnchorOffset(
    val x: Dp,
    val y: Dp
)

/**
 * Represents a node in the Kuiver graph.
 *
 * @property id unique identifier
 * @property dimensions optional physical dimensions, will be measured if null
 * @property position position in the graph coordinate space
 */
@Immutable
data class KuiverNode(
    val id: String,
    val dimensions: NodeDimensions? = null,
    val position: Offset = Offset.Zero
)

/**
 * Classification of edge types in the graph.
 * Used for styling and layout decisions.
 */
enum class EdgeType {
    /** Edge pointing to a descendant in the graph hierarchy */
    FORWARD,

    /** Edge pointing back to an ancestor (creates a cycle) */
    BACK,

    /** Edge between nodes at similar hierarchy levels */
    CROSS,

    /** Edge from a node to itself */
    SELF_LOOP
}

/**
 * Kuiver edge connecting two nodes, optionally via specific anchors.
 *
 * @property fromId start node ID
 * @property toId target node ID
 * @property type optional edge classification, computed during layout, see [EdgeType]
 * @property fromAnchor optional anchor ID on source node
 * @property toAnchor optional anchor ID on target node
 */
@Immutable
data class KuiverEdge(
    val fromId: String,
    val toId: String,
    val type: EdgeType? = null,
    val fromAnchor: String? = null,
    val toAnchor: String? = null
)