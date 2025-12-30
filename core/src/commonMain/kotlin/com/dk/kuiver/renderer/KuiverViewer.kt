package com.dk.kuiver.renderer

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import com.dk.kuiver.KuiverViewerState
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.util.calculatePositionBounds
import kotlin.math.exp

@Immutable
data class KuiverViewerConfig(
    val showDebugBounds: Boolean = false,
    val fitToContent: Boolean = true,
    val contentPadding: Float = 0.8f,
    val minScale: Float = 0.1f,
    val maxScale: Float = 5f,
    val panVelocity: Float = PlatformDefaults.defaultPanVelocity,
    val fontLoadingDelayMs: Long = PlatformDefaults.defaultFontLoadingDelayMs,
    val zoomConditionDesktop: (PointerEvent) -> Boolean = { eventType ->
        eventType.keyboardModifiers.isCtrlPressed
    },
    val scaleAnimationSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    ),
    val offsetAnimationSpec: AnimationSpec<Offset> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    ),
    val nodeAnimationSpec: AnimationSpec<Offset> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    ),
    val edgeAnimationSpec: AnimationSpec<Offset> = nodeAnimationSpec
)

/**
 * Kuiver viewer - Interactive viewer for directed graphs.
 *
 * Automatically measures nodes that don't have explicit dimensions
 * before rendering, ensuring optimal layout spacing.
 *
 * @param state kuiver viewer state
 * @param modifier generic modifier for the viewer
 * @param config viewer configuration
 * @param nodeContent composable content for rendering nodes
 * @param edgeContent composable content for rendering edges
 */
@Composable
fun KuiverViewer(
    state: KuiverViewerState,
    modifier: Modifier = Modifier,
    config: KuiverViewerConfig = KuiverViewerConfig(),
    nodeContent: @Composable (KuiverNode) -> Unit,
    edgeContent: @Composable (KuiverEdge, Offset, Offset) -> Unit
) {
    val needsMeasurement = remember(state.kuiver) {
        state.kuiver.nodes.values.any { it.dimensions == null }
    }

    // Track if this is the first measurement for font loading delay
    var hasInitialMeasurementCompleted by remember { mutableStateOf(false) }

    if (needsMeasurement) {
        val measured = measureNodes(
            kuiver = state.kuiver,
            nodeContent = nodeContent
        )

        LaunchedEffect(measured) {
            // On web platforms, add a small delay on initial measurement to ensure fonts are loaded
            // This prevents text wrapping issues when fonts haven't finished loading
            if (!hasInitialMeasurementCompleted && config.fontLoadingDelayMs > 0) {
                kotlinx.coroutines.delay(config.fontLoadingDelayMs)
                hasInitialMeasurementCompleted = true
            }

            val updatedKuiver = state.kuiver.withMeasuredDimensions(measured)
            state.updateKuiver(updatedKuiver)
        }
    }

    // Always render - don't skip frames during measurement
    ViewerRenderer(
        state = state,
        modifier = modifier,
        config = config,
        nodeContent = nodeContent,
        edgeContent = edgeContent
    )
}

@Composable
internal fun ViewerRenderer(
    state: KuiverViewerState,
    modifier: Modifier = Modifier,
    config: KuiverViewerConfig = KuiverViewerConfig(),
    nodeContent: @Composable (KuiverNode) -> Unit,
    edgeContent: @Composable (KuiverEdge, Offset, Offset) -> Unit
) {
    var targetScale by remember { mutableFloatStateOf(state.scale) }
    var targetOffset by remember { mutableStateOf(state.offset) }
    val density = LocalDensity.current

    LaunchedEffect(state.scale, state.offset) {
        targetScale = state.scale
        targetOffset = state.offset
    }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = config.scaleAnimationSpec,
        label = "camera_scale"
    )

    val animatedOffset by animateOffsetAsState(
        targetValue = targetOffset,
        animationSpec = config.offsetAnimationSpec,
        label = "camera_offset"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val centerX = maxWidth / 2
        val centerY = maxHeight / 2

        val kuiver = state.layoutedKuiver
        val bounds by remember(kuiver.nodes) {
            derivedStateOf { kuiver.nodes.values.calculatePositionBounds() }
        }
        val graphCenterX = bounds.centerX
        val graphCenterY = bounds.centerY

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .onGloballyPositioned { coordinates ->
                    with(density) {
                        val size = coordinates.size
                        state.updateViewWidth(size.width.toDp().value)
                        state.updateCanvasSize(size.width.toFloat(), size.height.toFloat())
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale =
                            (targetScale * zoom).coerceIn(config.minScale, config.maxScale)
                        val newOffset = targetOffset + pan
                        targetScale = newScale
                        targetOffset = newOffset
                        state.updateTransform(newScale, newOffset)
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        while (true) {
                            val event = awaitPointerEvent()

                            // Handle scroll events for desktop trackpad/mouse wheel
                            if (event.type == PointerEventType.Scroll) {
                                val change = event.changes.first()
                                val scrollDelta = change.scrollDelta

                                // Check if Ctrl is pressed (pinch zoom on macOS trackpad)
                                if (config.zoomConditionDesktop(event)) {
                                    // Zoom: Ctrl + scroll or pinch gesture on trackpad
                                    val zoomFactor = exp(-scrollDelta.y * 0.05f)
                                    val newScale = (targetScale * zoomFactor).coerceIn(
                                        config.minScale,
                                        config.maxScale
                                    )
                                    targetScale = newScale
                                    state.updateTransform(newScale, targetOffset)
                                } else {
                                    val panDelta = Offset(
                                        x = -scrollDelta.x * config.panVelocity,
                                        y = -scrollDelta.y * config.panVelocity
                                    )
                                    val newOffset = targetOffset + panDelta
                                    targetOffset = newOffset
                                    state.updateTransform(targetScale, newOffset)
                                }
                                change.consume()
                            }
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = animatedScale,
                        scaleY = animatedScale,
                        translationX = animatedOffset.x,
                        translationY = animatedOffset.y
                    )
            ) {
                // Draw edges first so they are behind nodes
                kuiver.edges.forEach { edge ->
                    val fromNode = kuiver.nodes[edge.fromId]
                    val toNode = kuiver.nodes[edge.toId]

                    if (fromNode != null && toNode != null) {
                        key(edge.fromId, edge.toId) {
                            RenderEdge(
                                edge = edge,
                                fromNode = fromNode,
                                toNode = toNode,
                                centerX = centerX,
                                centerY = centerY,
                                graphCenterX = graphCenterX,
                                graphCenterY = graphCenterY,
                                animationSpec = config.edgeAnimationSpec,
                                edgeContent = edgeContent
                            )
                        }
                    }
                }

                if (config.showDebugBounds) {
                    RenderDebugBounds(
                        kuiver = kuiver,
                        centerX = centerX,
                        centerY = centerY,
                        graphCenterX = graphCenterX,
                        graphCenterY = graphCenterY,
                        showDebugBounds = config.showDebugBounds,
                        onCanvasSize = { _, _ -> },
                        onRedBoxCenter = { _ -> },
                        onBoundsChange = { _ -> }
                    )
                }

                kuiver.nodes.values.forEach { node ->
                    key(node.id) {
                        RenderNode(
                            node = node,
                            centerX = centerX,
                            centerY = centerY,
                            graphCenterX = graphCenterX,
                            graphCenterY = graphCenterY,
                            animationSpec = config.nodeAnimationSpec,
                            nodeContent = nodeContent
                        )
                    }
                }
            }
        }
    }
}
