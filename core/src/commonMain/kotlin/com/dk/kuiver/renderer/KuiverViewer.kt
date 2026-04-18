package com.dk.kuiver.renderer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    val anchorRegistry = remember { AnchorPositionRegistry() }

    val needsMeasurement = remember(state.kuiver) {
        state.kuiver.nodes.values.any { it.dimensions == null }
    }

    // Track if this is the first measurement for font loading delay
    var hasInitialMeasurementCompleted by remember { mutableStateOf(false) }

    if (needsMeasurement) {
        val measured = measureNodes(
            kuiver = state.kuiver,
            anchorRegistry = anchorRegistry,
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
        anchorRegistry = anchorRegistry,
        nodeContent = nodeContent,
        edgeContent = edgeContent
    )
}

@Composable
internal fun ViewerRenderer(
    state: KuiverViewerState,
    modifier: Modifier = Modifier,
    config: KuiverViewerConfig = KuiverViewerConfig(),
    anchorRegistry: AnchorPositionRegistry,
    nodeContent: @Composable (KuiverNode) -> Unit,
    edgeContent: @Composable (KuiverEdge, Offset, Offset) -> Unit
) {

    var targetScale by remember { mutableFloatStateOf(state.scale) }
    var targetOffset by remember { mutableStateOf(state.offset) }
    val density = LocalDensity.current
    // Single progress animatable so scale and offset always interpolate by the same factor
    val progressAnim = remember { Animatable(1f) }
    var animStartScale by remember { mutableFloatStateOf(state.scale) }
    var animStartOffset by remember { mutableStateOf(state.offset) }

    LaunchedEffect(state.scale, state.offset) {
        val alreadyAtTarget = targetScale == state.scale && targetOffset == state.offset
        // Capture current animated position before updating targets
        val p = progressAnim.value
        val curScale = animStartScale + (targetScale - animStartScale) * p
        val curOffX = animStartOffset.x + (targetOffset.x - animStartOffset.x) * p
        val curOffY = animStartOffset.y + (targetOffset.y - animStartOffset.y) * p
        targetScale = state.scale
        targetOffset = state.offset
        if (alreadyAtTarget) return@LaunchedEffect
        animStartScale = curScale
        animStartOffset = Offset(curOffX, curOffY)
        progressAnim.snapTo(0f)
        progressAnim.animateTo(1f, config.scaleAnimationSpec)
    }

    // Remove anchors for nodes that no longer exist
    LaunchedEffect(state.layoutedKuiver.nodes.keys) {
        val currentNodeIds = state.layoutedKuiver.nodes.keys
        anchorRegistry.anchorPositions.keys.forEach { nodeId ->
            if (nodeId !in currentNodeIds) {
                anchorRegistry.clearNode(nodeId)
            }
        }
    }


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
                    while (true) {
                        awaitPointerEventScope { awaitFirstDown(requireUnconsumed = false) }

                        while (true) {
                            var panChange = Offset.Zero
                            var zoomChange = 1f
                            var centroid = Offset.Zero
                            var anyPressed = false

                            awaitPointerEventScope {
                                val event = awaitPointerEvent()
                                if (!event.changes.any { it.isConsumed }) {
                                    panChange = event.calculatePan()
                                    zoomChange = event.calculateZoom()
                                    // useCurrent = false: pivot at where fingers were
                                    centroid = event.calculateCentroid(useCurrent = false)
                                    event.changes.forEach { it.consume() }
                                }
                                anyPressed = event.changes.any { it.pressed }
                            }

                            if (!anyPressed) break

                            if (panChange != Offset.Zero || zoomChange != 1f) {
                                val newScale = (targetScale * zoomChange).coerceIn(
                                    config.minScale,
                                    config.maxScale
                                )
                                val actualZoom = newScale / targetScale
                                val halfW = size.width / 2f
                                val halfH = size.height / 2f
                                val newOffset = Offset(
                                    x = (centroid.x - halfW) * (1 - actualZoom) + targetOffset.x * actualZoom + panChange.x,
                                    y = (centroid.y - halfH) * (1 - actualZoom) + targetOffset.y * actualZoom + panChange.y
                                )
                                targetScale = newScale
                                targetOffset = newOffset
                                animStartScale = newScale
                                animStartOffset = newOffset
                                progressAnim.snapTo(1f)
                            }
                        }
                        state.updateTransform(targetScale, targetOffset)
                    }
                }
                .pointerInput(Unit) {
                    // Use awaitPointerEventScope per-event so snapTo can be called
                    // as a direct suspend call in the unrestricted PointerInputScope,
                    // guaranteeing it runs before state.updateTransform notifies Compose
                    while (true) {
                        val event = awaitPointerEventScope { awaitPointerEvent() }

                        if (event.type == PointerEventType.Scroll) {
                            val change = event.changes.first()
                            val scrollDelta = change.scrollDelta
                            change.consume()

                            if (config.zoomConditionDesktop(event)) {
                                val zoomFactor = exp(-scrollDelta.y * 0.05f)
                                val newScale = (targetScale * zoomFactor).coerceIn(
                                    config.minScale,
                                    config.maxScale
                                )
                                val actualZoom = newScale / targetScale
                                val focalPoint = change.position
                                val halfW = size.width / 2f
                                val halfH = size.height / 2f
                                val newOffset = Offset(
                                    x = (focalPoint.x - halfW) * (1 - actualZoom) + targetOffset.x * actualZoom,
                                    y = (focalPoint.y - halfH) * (1 - actualZoom) + targetOffset.y * actualZoom
                                )
                                targetScale = newScale
                                targetOffset = newOffset
                                animStartScale = newScale
                                animStartOffset = newOffset
                                progressAnim.snapTo(1f)
                                state.updateTransform(newScale, newOffset)
                            } else {
                                val panDelta = Offset(
                                    x = -scrollDelta.x * config.panVelocity,
                                    y = -scrollDelta.y * config.panVelocity
                                )
                                val newOffset = targetOffset + panDelta
                                targetOffset = newOffset
                                animStartOffset = newOffset
                                progressAnim.snapTo(1f)
                                state.updateTransform(targetScale, newOffset)
                            }
                        }
                    }
                }
        ) {
            CompositionLocalProvider(LocalAnchorRegistry provides anchorRegistry) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Read animation state in draw phase to avoid recomposition per frame
                            val p = progressAnim.value
                            scaleX = animStartScale + (targetScale - animStartScale) * p
                            scaleY = scaleX
                            translationX = animStartOffset.x + (targetOffset.x - animStartOffset.x) * p
                            translationY = animStartOffset.y + (targetOffset.y - animStartOffset.y) * p
                        }
                ) {
                    // Draw edges first so they are behind nodes
                    kuiver.edges.forEach { edge ->
                        val fromNode = kuiver.nodes[edge.fromId]
                        val toNode = kuiver.nodes[edge.toId]

                        if (fromNode != null && toNode != null) {
                            key(edge.fromId, edge.toId, edge.fromAnchor, edge.toAnchor) {
                                RenderEdge(
                                    edge = edge,
                                    fromNode = fromNode,
                                    toNode = toNode,
                                    centerX = centerX,
                                    centerY = centerY,
                                    graphCenterX = graphCenterX,
                                    graphCenterY = graphCenterY,
                                    anchorRegistry = anchorRegistry,
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
}
