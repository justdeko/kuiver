package com.dk.kuiver.renderer

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import com.dk.kuiver.KuiverInteractionState

/**
 * Receiver of the `nodeContent` lambda, the interaction state of the node being rendered.
 *
 * The viewer tracks what a node is, the content decides what that looks like:
 *
 * ```kotlin
 * nodeContent = { node ->
 *     val border = if (isSelected) 3.dp else 1.dp
 *     Box(Modifier.border(border, if (isHovered) Color.Blue else Color.Gray)) { Text(node.id) }
 * }
 * ```
 *
 * @property isSelected whether this node is selected. Denoted by being part of [KuiverInteractionState.selectedNodeIds].
 * @property isHovered whether the pointer is over this node. Always false on touch, which has no hover.
 * @property isDragging whether this node is the one currently being dragged.
 */
@Stable
interface KuiverNodeScope {
    val isSelected: Boolean
    val isHovered: Boolean
    val isDragging: Boolean
}

internal class NodeScope(
    private val nodeId: String,
    private val interaction: KuiverInteractionState
) : KuiverNodeScope {
    // Recompose only the nodes that change
    override val isSelected: Boolean by derivedStateOf { interaction.isSelected(nodeId) }
    override val isHovered: Boolean by derivedStateOf { interaction.hoveredNodeId == nodeId }
    override val isDragging: Boolean by derivedStateOf { interaction.draggedNodeId == nodeId }
}
