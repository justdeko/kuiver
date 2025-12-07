package com.dk.kuiver.model.layout

import androidx.compose.runtime.Immutable
import com.dk.kuiver.model.DEFAULT_NODE_SIZE
import com.dk.kuiver.model.Kuiver

/**
 * Type alias for custom layout provider functions.
 * Takes a Kuiver graph and LayoutConfig, returns a new Kuiver with updated node positions.
 */
typealias LayoutProvider = (Kuiver, LayoutConfig) -> Kuiver

enum class LayoutAlgorithm {
    /** Built-in hierarchical layout algorithm for DAGs */
    HIERARCHICAL,

    /** Built-in force-directed layout algorithm for general graphs */
    FORCE_DIRECTED,

    /**
     * Use a custom layout provider function.
     * Requires setting [LayoutConfig.customLayoutProvider].
     *
     * Example:
     * ```kotlin
     * val gridLayout: LayoutProvider = { kuiver, config ->
     *     // Position nodes in a grid
     *     val updatedNodes = kuiver.nodes.values.mapIndexed { i, node ->
     *         val x = (i % 3) * 200f
     *         val y = (i / 3) * 200f
     *         node.copy(position = Offset(x, y))
     *     }
     *     buildKuiverWithClassifiedEdges(updatedNodes, kuiver.edges)
     * }
     *
     * val config = LayoutConfig(
     *     algorithm = LayoutAlgorithm.CUSTOM,
     *     customLayoutProvider = gridLayout
     * )
     * ```
     */
    CUSTOM
}

enum class LayoutDirection {
    HORIZONTAL,  // Left to right (default)
    VERTICAL     // Top to bottom
}

fun layout(kuiver: Kuiver, layoutConfig: LayoutConfig = LayoutConfig()): Kuiver {
    return when (layoutConfig.algorithm) {
        LayoutAlgorithm.HIERARCHICAL -> hierarchical(kuiver, layoutConfig)
        LayoutAlgorithm.FORCE_DIRECTED -> forceDirected(kuiver, layoutConfig)
        LayoutAlgorithm.CUSTOM -> {
            requireNotNull(layoutConfig.customLayoutProvider) {
                "customLayoutProvider must be provided when algorithm is CUSTOM. " +
                "Create a LayoutConfig with algorithm = LayoutAlgorithm.CUSTOM and " +
                "customLayoutProvider = your layout function."
            }
            layoutConfig.customLayoutProvider.invoke(kuiver, layoutConfig)
        }
    }
}

@Immutable
data class LayoutConfig(
    val algorithm: LayoutAlgorithm = LayoutAlgorithm.HIERARCHICAL,
    val direction: LayoutDirection = LayoutDirection.HORIZONTAL,
    val levelSpacing: Float = 150f,
    val nodeSpacing: Float = 100f,
    val width: Float = 0f,
    val height: Float = 0f,
    val iterations: Int = 200,
    val repulsionStrength: Float = 500f,
    val attractionStrength: Float = 0.02f,
    val damping: Float = 0.85f,
    val nodeSize: Float = DEFAULT_NODE_SIZE,
    /**
     * Custom layout provider function for [LayoutAlgorithm.CUSTOM].
     * Required when algorithm is [LayoutAlgorithm.CUSTOM], ignored otherwise.
     *
     * The provider function receives the current [Kuiver] graph and this [LayoutConfig],
     * and should return a new [Kuiver] with updated node positions.
     *
     * Example:
     * ```kotlin
     * val circularLayout: LayoutProvider = { kuiver, config ->
     *     val nodesList = kuiver.nodes.values.toList()
     *     val radius = minOf(config.width, config.height) * 0.4f
     *     val centerX = config.width / 2f
     *     val centerY = config.height / 2f
     *
     *     val updatedNodes = nodesList.mapIndexed { index, node ->
     *         val angle = (index.toFloat() / nodesList.size) * 2f * PI.toFloat()
     *         node.copy(
     *             position = Offset(
     *                 x = centerX + radius * cos(angle),
     *                 y = centerY + radius * sin(angle)
     *             )
     *         )
     *     }
     *     buildKuiverWithClassifiedEdges(updatedNodes, kuiver.edges)
     * }
     *
     * LayoutConfig(
     *     algorithm = LayoutAlgorithm.CUSTOM,
     *     customLayoutProvider = circularLayout
     * )
     * ```
     */
    val customLayoutProvider: LayoutProvider? = null
)
