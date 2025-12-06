package com.dk.kuiver.model.layout

import androidx.compose.runtime.Immutable
import com.dk.kuiver.model.DEFAULT_NODE_SIZE
import com.dk.kuiver.model.Kuiver

enum class LayoutAlgorithm {
    HIERARCHICAL,
    FORCE_DIRECTED,
}

enum class LayoutDirection {
    HORIZONTAL,  // Left to right (default)
    VERTICAL     // Top to bottom
}

fun layout(kuiver: Kuiver, layoutConfig: LayoutConfig = LayoutConfig()): Kuiver {
    return when (layoutConfig.algorithm) {
        LayoutAlgorithm.HIERARCHICAL -> hierarchical(kuiver, layoutConfig)
        LayoutAlgorithm.FORCE_DIRECTED -> forceDirected(kuiver, layoutConfig)
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
    val nodeSize: Float = DEFAULT_NODE_SIZE
)
