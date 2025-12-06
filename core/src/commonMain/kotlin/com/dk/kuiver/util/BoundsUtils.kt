package com.dk.kuiver.util

import androidx.compose.runtime.Immutable
import com.dk.kuiver.model.DEFAULT_NODE_SIZE
import com.dk.kuiver.model.KuiverNode

/**
 * Represents the bounding box of a set of nodes
 */
@Immutable
data class Bounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float
) {
    val centerX: Float get() = (minX + maxX) / 2f
    val centerY: Float get() = (minY + maxY) / 2f
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY

    companion object {
        val EMPTY = Bounds(0f, 0f, 0f, 0f)
    }
}

/**
 * Calculate bounds from node positions, optionally including node dimensions.
 *
 * @param includeDimensions If true, expands bounds by node dimensions (position ± dimensions/2).
 *                          If false, only considers exact node positions.
 * @return Bounds encompassing all nodes
 */
fun Collection<KuiverNode>.calculateBounds(includeDimensions: Boolean = true): Bounds {
    if (isEmpty()) return Bounds.EMPTY

    var minX = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var minY = Float.MAX_VALUE
    var maxY = Float.MIN_VALUE

    for (node in this) {
        if (includeDimensions) {
            // Include node dimensions in bounds calculation
            val nodeWidth = node.dimensions?.width?.value ?: DEFAULT_NODE_SIZE
            val nodeHeight = node.dimensions?.height?.value ?: DEFAULT_NODE_SIZE

            val nodeMinX = node.position.x - nodeWidth / 2
            val nodeMaxX = node.position.x + nodeWidth / 2
            val nodeMinY = node.position.y - nodeHeight / 2
            val nodeMaxY = node.position.y + nodeHeight / 2

            minX = minOf(minX, nodeMinX)
            maxX = maxOf(maxX, nodeMaxX)
            minY = minOf(minY, nodeMinY)
            maxY = maxOf(maxY, nodeMaxY)
        } else {
            // Only use node positions
            minX = minOf(minX, node.position.x)
            maxX = maxOf(maxX, node.position.x)
            minY = minOf(minY, node.position.y)
            maxY = maxOf(maxY, node.position.y)
        }
    }

    return Bounds(minX, maxX, minY, maxY)
}

/**
 * Calculate bounds from node positions only (ignoring dimensions).
 * Convenience method for calculateBounds(includeDimensions = false).
 */
fun Collection<KuiverNode>.calculatePositionBounds(): Bounds =
    calculateBounds(includeDimensions = false)

/**
 * Calculate bounds including node dimensions (position ± dimensions/2).
 * Convenience method for calculateBounds(includeDimensions = true).
 */
fun Collection<KuiverNode>.calculateNodeBounds(): Bounds =
    calculateBounds(includeDimensions = true)
