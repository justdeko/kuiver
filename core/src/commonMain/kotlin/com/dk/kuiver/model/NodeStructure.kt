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

@Immutable
data class KuiverNode(
    val id: String,
    val dimensions: NodeDimensions? = null,  // optional - will be measured if null
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

@Immutable
data class KuiverEdge(
    val fromId: String,
    val toId: String,
    val type: EdgeType? = null  // Optional classification, computed during layout
)