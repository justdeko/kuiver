package com.dk.kuiver.renderer

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import com.dk.kuiver.KuiverInteractionState

/**
 * Receiver of the `edgeContent` lambda (interaction state of the edge being rendered)
 *
 *  ```kotlin
 * edgeContent = { edge, from, to ->
 *      StyledEdgeContent(edge, from, to, baseColor = if (isSelected) Color.Blue else Color.Gray)
 * }
 * ```
 *
 * @property isSelected whether this edge is selected. Denoted by being part of [KuiverInteractionState.selectedEdgeIds].
 * @property isHovered whether the pointer is over this edge. Always false on touch, which has no hover.
 */
@Stable
public interface KuiverEdgeScope {
    public val isSelected: Boolean
    public val isHovered: Boolean
}

internal class EdgeScope(
    private val edgeId: String,
    private val interaction: KuiverInteractionState
) : KuiverEdgeScope {
    // Recompose only the edges that change
    override val isSelected: Boolean by derivedStateOf { interaction.isEdgeSelected(edgeId) }
    override val isHovered: Boolean by derivedStateOf { interaction.hoveredEdgeId == edgeId }
}
