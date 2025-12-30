package com.dk.kuiver.model.layout

import androidx.compose.runtime.Immutable
import com.dk.kuiver.model.DEFAULT_NODE_SIZE
import com.dk.kuiver.model.Kuiver

/**
 * Type alias for custom layout provider functions.
 * Takes a Kuiver graph and LayoutConfig, returns a new Kuiver with updated node positions.
 */
typealias LayoutProvider = (Kuiver, LayoutConfig) -> Kuiver

/**
 * Layout direction for hierarchical layouts.
 */
enum class LayoutDirection {
    /** Left to right flow */
    HORIZONTAL,
    /** Top to bottom flow */
    VERTICAL
}

/**
 * Configuration for graph layout algorithms.
 *
 * This sealed class provides type-safe configuration for different layout algorithms.
 * Each algorithm has its own specific configuration with relevant parameters.
 *
 * @see Hierarchical for DAG and tree layouts
 * @see ForceDirected for general graph layouts
 * @see Custom for user-defined layout algorithms
 */
@Immutable
sealed class LayoutConfig {
    /**
     * Canvas width in pixels. Set automatically by the viewer from canvas size.
     * Can be 0 if canvas hasn't been measured yet.
     */
    abstract val width: Float

    /**
     * Canvas height in pixels. Set automatically by the viewer from canvas size.
     * Can be 0 if canvas hasn't been measured yet.
     */
    abstract val height: Float

    /**
     * Fallback node size used when a node doesn't specify explicit dimensions.
     * Most users should rely on measured node dimensions instead.
     */
    internal open val nodeSize: Float = DEFAULT_NODE_SIZE

    /**
     * Hierarchical layout configuration.
     *
     * This layout arranges nodes in levels based on their depth in the graph hierarchy.
     * Automatically handles cycles by classifying back edges.
     *
     * @param direction The flow direction of the layout (HORIZONTAL or VERTICAL)
     * @param levelSpacing Distance between hierarchy levels in pixels
     * @param nodeSpacing Distance between nodes within the same level in pixels
     * @param width Canvas width (usually set automatically by the viewer)
     * @param height Canvas height (usually set automatically by the viewer)
     *
     * Example:
     * ```kotlin
     * val config = LayoutConfig.Hierarchical(
     *     direction = LayoutDirection.HORIZONTAL,
     *     levelSpacing = 150f,
     *     nodeSpacing = 100f
     * )
     * ```
     */
    @Immutable
    data class Hierarchical(
        val direction: LayoutDirection = LayoutDirection.HORIZONTAL,
        val levelSpacing: Float = 150f,
        val nodeSpacing: Float = 100f,
        override val width: Float = 0f,
        override val height: Float = 0f
    ) : LayoutConfig()

    /**
     * Force-directed layout configuration.
     *
     * This layout uses a basic physics simulation to create layouts.
     * Nodes repel each other while edges act as springs pulling connected nodes together.
     *
     * @param iterations Number of simulation steps (more = better layout but slower)
     * @param repulsionStrength How strongly nodes push each other apart
     * @param attractionStrength How strongly connected nodes pull together
     * @param damping Velocity damping factor (0-1). Higher values = more stability, slower convergence
     * @param width Canvas width (usually set automatically by the viewer)
     * @param height Canvas height (usually set automatically by the viewer)
     *
     * Example:
     * ```kotlin
     * val config = LayoutConfig.ForceDirected(
     *     iterations = 200,
     *     repulsionStrength = 500f,
     *     attractionStrength = 0.02f,
     *     damping = 0.85f
     * )
     * ```
     */
    @Immutable
    data class ForceDirected(
        val iterations: Int = 200,
        val repulsionStrength: Float = 500f,
        val attractionStrength: Float = 0.02f,
        val damping: Float = 0.85f,
        override val width: Float = 0f,
        override val height: Float = 0f
    ) : LayoutConfig()

    /**
     * Custom layout configuration using a user-provided layout algorithm.
     *
     * This allows you to implement your own layout logic while still integrating
     * with the Kuiver framework.
     *
     * @param provider Function that takes a Kuiver graph and config, returns positioned Kuiver
     * @param width Canvas width (usually set automatically by the viewer)
     * @param height Canvas height (usually set automatically by the viewer)
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
     * val config = LayoutConfig.Custom(provider = circularLayout)
     * ```
     */
    @Immutable
    data class Custom(
        val provider: LayoutProvider,
        override val width: Float = 0f,
        override val height: Float = 0f
    ) : LayoutConfig()
}

/**
 * Applies the configured layout algorithm to the given graph.
 *
 * @param kuiver The graph to layout
 * @param layoutConfig The layout configuration
 * @return A new Kuiver instance with updated node positions
 */
internal fun layout(kuiver: Kuiver, layoutConfig: LayoutConfig = LayoutConfig.Hierarchical()): Kuiver {
    return when (layoutConfig) {
        is LayoutConfig.Hierarchical -> hierarchical(kuiver, layoutConfig)
        is LayoutConfig.ForceDirected -> forceDirected(kuiver, layoutConfig)
        is LayoutConfig.Custom -> layoutConfig.provider.invoke(kuiver, layoutConfig)
    }
}
