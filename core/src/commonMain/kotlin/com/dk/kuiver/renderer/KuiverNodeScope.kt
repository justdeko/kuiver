package com.dk.kuiver.renderer

import androidx.compose.runtime.Stable
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
 * Each flag is read where it is used, so a node recomposes only when a flag it actually reads
 * changes.
 */
@Stable
interface KuiverNodeScope {
    /** Whether this node is part of [KuiverInteractionState.selectedNodeIds]. */
    val isSelected: Boolean

    /** Whether the pointer is over this node. Always false on touch, which has no hover. */
    val isHovered: Boolean

    /** Whether this node is the one currently being dragged. */
    val isDragging: Boolean
}

/**
 * Reads the flags straight off the interaction state through getters, so a node subscribes to the
 * ones its content touches instead of to all of them.
 */
internal class NodeScope(
    private val nodeId: String,
    private val interaction: KuiverInteractionState
) : KuiverNodeScope {
    override val isSelected: Boolean get() = interaction.isSelected(nodeId)
    override val isHovered: Boolean get() = interaction.hoveredNodeId == nodeId
    override val isDragging: Boolean get() = interaction.draggedNodeId == nodeId
}
