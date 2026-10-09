package com.dk.kuiver.renderer

import com.dk.kuiver.model.EdgeType
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.ui.EdgeDrawingDefaults
import kotlin.math.atan

/**
 * Bend of a parallel edge, see [com.dk.kuiver.ui.LocalEdgeBend], with the tangent angle of the
 * curve at its endpoints.
 */
internal class EdgeBend(val amount: Float) {
    val tangentAngle: Float = atan(2f * amount)
}

/**
 * Returns the bend of each parallel edge mapped to its id. Edges without a twin are left out.
 */
internal fun Collection<KuiverEdge>.parallelBends(
    step: Float = EdgeDrawingDefaults.PARALLEL_EDGE_BEND
): Map<String, EdgeBend> {
    val bends = mutableMapOf<String, EdgeBend>()
    groupBy { listOf(it.fromId, it.toId, it.fromAnchor, it.toAnchor) }.values.forEach { group ->
        if (group.size < 2) return@forEach
        val sorted = group.sortedBy { it.id }
        val count = sorted.size

        // Nest self-loops with increasing height
        if (sorted.first().fromId == sorted.first().toId) {
            sorted.forEachIndexed { index, edge ->
                bends[edge.id] = EdgeBend(index * EdgeDrawingDefaults.SELF_LOOP_NESTED_GROWTH)
            }
            return@forEach
        }

        // Back edges fan out on the side of their usual curve and never go straight
        val halfSpan = (count - 1) / 2f * step
        val first = if (sorted.any { it.type == EdgeType.BACK }) {
            (EdgeDrawingDefaults.CURVE_PROPORTIONAL_OFFSET - halfSpan).coerceAtLeast(step / 2f)
        } else {
            -halfSpan
        }
        sorted.forEachIndexed { index, edge ->
            bends[edge.id] = EdgeBend(first + index * step)
        }
    }
    return bends
}
