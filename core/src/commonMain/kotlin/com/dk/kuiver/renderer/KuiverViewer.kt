package com.dk.kuiver.renderer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import kotlinx.coroutines.delay
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
    val edgeAnimationSpec: AnimationSpec<Offset> = nodeAnimationSpec,
    val animateInitialPlacement: Boolean = false,
    val enterAnimationSpec: AnimationSpec<Float>? = null
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

    var hasInitialMeasurementCompleted by remember { mutableStateOf(false) }

    if (needsMeasurement) {
        val measured = measureNodes(
            kuiver = state.kuiver,
            anchorRegistry = anchorRegistry,
            nodeContent = nodeContent
        )

        LaunchedEffect(state.kuiver) {
            // On web platforms, add a small delay on initial measurement to ensure fonts are loaded
            // This prevents text wrapping issues when fonts haven't finished loading
            if (!hasInitialMeasurementCompleted && config.fontLoadingDelayMs > 0) {
                delay(config.fontLoadingDelayMs)
                hasInitialMeasurementCompleted = true
            }
            val updatedKuiver = state.kuiver.withMeasuredDimensions(measured)
            state.updateKuiver(updatedKuiver)
        }
    }

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
    val density = LocalDensity.current
    // Single progress animatable for both scale and offset in the same frame
    val progressAnim = remember { Animatable(1f) }

    LaunchedEffect(state.pendingAnimation) {
        val request = state.pendingAnimation ?: return@LaunchedEffect
        val startScale = state.scale
        val startOffset = state.offset
        progressAnim.snapTo(0f)
        progressAnim.animateTo(1f, config.scaleAnimationSpec) {
            state.scale = startScale + (request.scale - startScale) * value
            state.offset = Offset(
                startOffset.x + (request.offset.x - startOffset.x) * value,
                startOffset.y + (request.offset.y - startOffset.y) * value
            )
        }
    }

    // Remove anchors for nodes that no longer exist
    LaunchedEffect(state.layoutedKuiver.nodes.keys) {
        val currentNodeIds = state.layoutedKuiver.nodes.keys
        anchorRegistry.anchorPositions.keys.forEach { nodeId ->
            if (nodeId !in currentNodeIds) anchorRegistry.clearNode(nodeId)
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

        val isContentReady = state.hasFittedInitially || kuiver.nodes.isEmpty()

        // initialSnapDone lags one effects-phase behind hasFittedInitially, so the frame
        // where positions first settle (same snapshot as hasFittedInitially=true) still
        // uses snap(), then spring kicks in for all subsequent layout changes.
        var initialSnapDone by remember { mutableStateOf(state.hasFittedInitially) }
        LaunchedEffect(state.hasFittedInitially) {
            if (state.hasFittedInitially) initialSnapDone = true
        }
        val skipInitialAnimation = !initialSnapDone && !config.animateInitialPlacement

        val contentAlpha by animateFloatAsState(
            targetValue = if (isContentReady) 1f else 0f,
            animationSpec = config.enterAnimationSpec ?: snap(),
            label = "graph_content_enter_anim"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer { alpha = contentAlpha }
                .onGloballyPositioned { coordinates ->
                    with(density) {
                        val size = coordinates.size
                        state.viewWidth = size.width.toDp().value
                        state.canvasWidth = size.width.toFloat()
                        state.canvasHeight = size.height.toFloat()
                    }
                }
                .pointerInput(state) {
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
                                val newScale = (state.scale * zoomChange).coerceIn(
                                    config.minScale,
                                    config.maxScale
                                )
                                val actualZoom = newScale / state.scale
                                val halfW = size.width / 2f
                                val halfH = size.height / 2f
                                val newOffset = Offset(
                                    x = (centroid.x - halfW) * (1 - actualZoom) + state.offset.x * actualZoom + panChange.x,
                                    y = (centroid.y - halfH) * (1 - actualZoom) + state.offset.y * actualZoom + panChange.y
                                )
                                progressAnim.snapTo(1f)
                                state.scale = newScale
                                state.offset = newOffset
                            }
                        }
                    }
                }
                .pointerInput(state) {
                    while (true) {
                        val event = awaitPointerEventScope { awaitPointerEvent() }

                        if (event.type == PointerEventType.Scroll) {
                            val change = event.changes.first()
                            val scrollDelta = change.scrollDelta
                            change.consume()

                            if (config.zoomConditionDesktop(event)) {
                                val zoomFactor = exp(-scrollDelta.y * 0.05f)
                                val newScale = (state.scale * zoomFactor).coerceIn(
                                    config.minScale,
                                    config.maxScale
                                )
                                val actualZoom = newScale / state.scale
                                val focalPoint = change.position
                                val halfW = size.width / 2f
                                val halfH = size.height / 2f
                                val newOffset = Offset(
                                    x = (focalPoint.x - halfW) * (1 - actualZoom) + state.offset.x * actualZoom,
                                    y = (focalPoint.y - halfH) * (1 - actualZoom) + state.offset.y * actualZoom
                                )
                                progressAnim.snapTo(1f)
                                state.scale = newScale
                                state.offset = newOffset
                            } else {
                                val panDelta = Offset(
                                    x = -scrollDelta.x * config.panVelocity,
                                    y = -scrollDelta.y * config.panVelocity
                                )
                                progressAnim.snapTo(1f)
                                state.offset += panDelta
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
                            scaleX = state.scale
                            scaleY = state.scale
                            translationX = state.offset.x
                            translationY = state.offset.y
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
                                    animationSpec = if (skipInitialAnimation) snap() else config.edgeAnimationSpec,
                                    skipAnimation = skipInitialAnimation,
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
                                animationSpec = if (skipInitialAnimation) snap() else config.nodeAnimationSpec,
                                skipAnimation = skipInitialAnimation,
                                nodeContent = nodeContent
                            )
                        }
                    }
                }
            }
        }
    }
}
