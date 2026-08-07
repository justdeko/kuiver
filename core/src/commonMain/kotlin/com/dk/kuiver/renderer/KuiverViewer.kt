package com.dk.kuiver.renderer

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.dk.kuiver.KuiverViewerState
import com.dk.kuiver.RelayoutPolicy
import com.dk.kuiver.SelectionMode
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.ui.EdgeStyle
import com.dk.kuiver.util.calculatePositionBounds
import com.dk.kuiver.util.times
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

/**
 * How a [KuiverViewer] looks and what it lets the user do.
 *
 * Every interaction beyond pan and zoom is off by default: [selectionMode], [nodeDragEnabled] and
 * [hoverEnabled] each add their gesture handling only when enabled.
 *
 * @property showDebugBounds whether to draw the graph and viewport bounds, for debugging
 * @property fitToContent whether the graph is scaled to fit the viewport once it is first laid out
 * @property contentPadding fraction of the viewport the graph fills when fitted
 * @property minScale lowest zoom level
 * @property maxScale highest zoom level
 * @property zoomStep zoom factor applied per [KuiverViewerState.zoomIn] and
 * [KuiverViewerState.zoomOut]
 * @property panVelocity scroll pan sensitivity in dp per scroll unit, with a platform-specific
 * default
 * @property zoomVelocity scroll zoom sensitivity per scroll unit, scale changes exponentially
 * @property selectionMode whether and how tapping a node selects it
 * @property nodeDragEnabled whether nodes can be dragged to a new position
 * @property hoverEnabled whether the pointer entering a node updates
 * [com.dk.kuiver.KuiverInteractionState.hoveredNodeId]
 * @property relayoutPolicy what happens to dragged nodes when the graph is laid out again
 * @property zoomConditionDesktop when a scroll event zooms instead of pans, Ctrl+scroll by default
 * @property scaleAnimationSpec spec for animated zoom
 * @property offsetAnimationSpec spec for animated pan
 * @property layoutAnimationSpec spec for node movement when the layout changes
 * @property animateInitialPlacement whether the very first placement animates too
 * @property enterAnimationSpec fade-in of the graph once it is ready, or none when `null`
 */
@Immutable
data class KuiverViewerConfig(
    val showDebugBounds: Boolean = false,
    val fitToContent: Boolean = true,
    val contentPadding: Float = 0.8f,
    val minScale: Float = 0.1f,
    val maxScale: Float = 5f,
    val zoomStep: Float = 1.2f,
    val panVelocity: Float = PlatformDefaults.defaultPanVelocity,
    val zoomVelocity: Float = PlatformDefaults.defaultZoomVelocity,
    val selectionMode: SelectionMode = SelectionMode.NONE,
    val nodeDragEnabled: Boolean = false,
    val hoverEnabled: Boolean = false,
    val relayoutPolicy: RelayoutPolicy = RelayoutPolicy.KEEP_MANUAL,
    val zoomConditionDesktop: (PointerEvent) -> Boolean = { event ->
        event.keyboardModifiers.isCtrlPressed
    },
    val scaleAnimationSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    ),
    val offsetAnimationSpec: AnimationSpec<DpOffset> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
        visibilityThreshold = DpOffset.VisibilityThreshold
    ),
    val layoutAnimationSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    ),
    val animateInitialPlacement: Boolean = false,
    val enterAnimationSpec: AnimationSpec<Float>? = null
)

/** How the viewer renders edges, one per [KuiverViewer] overload. */
@Immutable
internal sealed interface EdgeRendering {
    /** One composable per edge. */
    @Immutable
    class PerEdge(val content: @Composable (KuiverEdge, Offset, Offset) -> Unit) : EdgeRendering

    /** All edges from one canvas. */
    @Immutable
    class Batched(val style: (KuiverEdge) -> EdgeStyle) : EdgeRendering
}

/**
 * Kuiver viewer - Interactive viewer for directed graphs.
 *
 * Nodes that don't have explicit dimensions are measured while they render, and the graph is laid
 * out again whenever those measurements change.
 *
 * @param state kuiver viewer state
 * @param modifier generic modifier for the viewer
 * @param config viewer configuration
 * @param callbacks what the viewer reports back about clicks, drags and long presses
 * @param nodeContent composable content for rendering nodes, with the node's selection, hover and
 * drag state as its [KuiverNodeScope] receiver
 * @param edgeContent composable content for rendering edges
 */
@Composable
fun KuiverViewer(
    state: KuiverViewerState,
    modifier: Modifier = Modifier,
    config: KuiverViewerConfig = KuiverViewerConfig(),
    callbacks: KuiverInteractionCallbacks = KuiverInteractionCallbacks.None,
    nodeContent: @Composable KuiverNodeScope.(KuiverNode) -> Unit,
    edgeContent: @Composable (KuiverEdge, Offset, Offset) -> Unit
) {
    KuiverViewer(
        state = state,
        modifier = modifier,
        config = config,
        callbacks = callbacks,
        nodeContent = nodeContent,
        edges = remember(edgeContent) { EdgeRendering.PerEdge(edgeContent) }
    )
}

/**
 * Kuiver viewer that draws all edges from a single canvas.
 *
 * Edges are described by [EdgeStyle] values instead of composables, which renders large edge sets
 * much cheaper but supports no edge labels. Suited to graphs of several hundred nodes and up.
 *
 * ```kotlin
 * KuiverViewer(
 *     state = state,
 *     nodeContent = { node -> Text(node.id) },
 *     edgeStyle = { edge -> EdgeStyle.styled(edge, baseColor = Color.Gray) }
 * )
 * ```
 *
 * @param state kuiver viewer state
 * @param modifier generic modifier for the viewer
 * @param config viewer configuration
 * @param callbacks what the viewer reports back about clicks, drags and long presses
 * @param nodeContent composable content for rendering nodes, with the node's selection, hover and
 * drag state as its [KuiverNodeScope] receiver
 * @param edgeStyle how each edge is drawn
 */
@Composable
fun KuiverViewer(
    state: KuiverViewerState,
    modifier: Modifier = Modifier,
    config: KuiverViewerConfig = KuiverViewerConfig(),
    callbacks: KuiverInteractionCallbacks = KuiverInteractionCallbacks.None,
    nodeContent: @Composable KuiverNodeScope.(KuiverNode) -> Unit,
    edgeStyle: (KuiverEdge) -> EdgeStyle
) {
    KuiverViewer(
        state = state,
        modifier = modifier,
        config = config,
        callbacks = callbacks,
        nodeContent = nodeContent,
        edges = remember(edgeStyle) { EdgeRendering.Batched(edgeStyle) }
    )
}

@Composable
private fun KuiverViewer(
    state: KuiverViewerState,
    modifier: Modifier,
    config: KuiverViewerConfig,
    callbacks: KuiverInteractionCallbacks,
    nodeContent: @Composable KuiverNodeScope.(KuiverNode) -> Unit,
    edges: EdgeRendering
) {
    val anchorRegistry = remember { AnchorPositionRegistry() }

    ViewerRenderer(
        state = state,
        modifier = modifier,
        config = config,
        callbacks = callbacks,
        anchorRegistry = anchorRegistry,
        nodeContent = nodeContent,
        edges = edges
    )
}

@Composable
internal fun ViewerRenderer(
    state: KuiverViewerState,
    modifier: Modifier = Modifier,
    config: KuiverViewerConfig = KuiverViewerConfig(),
    callbacks: KuiverInteractionCallbacks = KuiverInteractionCallbacks.None,
    anchorRegistry: AnchorPositionRegistry,
    nodeContent: @Composable KuiverNodeScope.(KuiverNode) -> Unit,
    edges: EdgeRendering
) {
    val density = LocalDensity.current
    val interaction = state.interaction
    // Read from the gesture loops, which must not restart when a callback changes
    val currentConfig by rememberUpdatedState(config)
    val currentCallbacks by rememberUpdatedState(callbacks)
    // Single progress animation for all node positions, see LayoutTransition
    val layoutTransition = remember { LayoutTransition() }
    // Stable, so a new generation of node targets does not re-measure the node layer
    val reportMeasured = remember(state) { state::updateMeasuredDimensions }

    // run before LaunchedEffect so the initial auto-fit already has config
    SideEffect {
        state.config = config
    }

    LaunchedEffect(state.pendingAnimation) {
        val request = state.pendingAnimation ?: return@LaunchedEffect
        launch {
            animate(
                initialValue = state.scale,
                targetValue = request.scale,
                animationSpec = config.scaleAnimationSpec
            ) { value, _ ->
                state.scale = value
            }
        }
        launch {
            animate(
                typeConverter = DpOffset.VectorConverter,
                initialValue = state.offset,
                targetValue = request.offset,
                animationSpec = config.offsetAnimationSpec
            ) { value, _ ->
                state.offset = value
            }
        }
    }

    // Remove anchors and interaction state for nodes that no longer exist
    LaunchedEffect(state.layoutedKuiver.nodes.keys) {
        val currentNodeIds = state.layoutedKuiver.nodes.keys
        // Collect first: removing while iterating the state map's keys throws
        anchorRegistry.anchorPositions.keys
            .filter { it !in currentNodeIds }
            .forEach { anchorRegistry.clearNode(it) }
        (interaction.selectedNodeIds + listOfNotNull(
            interaction.hoveredNodeId,
            interaction.draggedNodeId
        )).forEach { nodeId ->
            if (nodeId !in currentNodeIds) interaction.forget(nodeId)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val centerX = maxWidth / 2
        val centerY = maxHeight / 2

        val kuiver = state.layoutedKuiver
        val bounds = remember(kuiver.nodes) { kuiver.nodes.values.calculatePositionBounds() }
        val graphCenterX = bounds.centerX
        val graphCenterY = bounds.centerY

        // Immutable target generation, so placement lambdas can capture it
        val nodeTargets = remember(kuiver.nodes) {
            kuiver.nodePositionsRelativeTo(graphCenterX, graphCenterY)
        }

        val isContentReady = state.hasFittedInitially || kuiver.nodes.isEmpty()

        // initialSnapDone lags one effects-phase behind hasFittedInitially, so the frame
        // where positions first settle (same snapshot as hasFittedInitially=true) still
        // uses snap(), then spring kicks in for all subsequent layout changes.
        var initialSnapDone by remember { mutableStateOf(state.hasFittedInitially) }
        LaunchedEffect(state.hasFittedInitially) {
            if (state.hasFittedInitially) initialSnapDone = true
        }
        val skipInitialAnimation = !initialSnapDone && !config.animateInitialPlacement
        // Decided here rather than inside the effect below: the effect runs after this frame has
        // already been placed, and the transition still holds the previous generation until it
        // does, which would render the moved nodes back where they started for a frame
        val skipAnimation = skipInitialAnimation || state.isManualLayout(kuiver)

        LaunchedEffect(nodeTargets, skipAnimation) {
            layoutTransition.animateTo(
                targets = nodeTargets,
                spec = config.layoutAnimationSpec,
                snap = skipAnimation
            )
        }

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
                .onSizeChanged { size ->
                    // dp, the space the graph is laid out and positioned in
                    with(density) {
                        state.canvasWidth = size.width.toDp()
                        state.canvasHeight = size.height.toDp()
                    }
                }
                // One awaitPointerEventScope for the whole gesture: leaving and re-entering the
                // scope between events loses the ones dispatched in between, which drops every
                // other gesture
                .pointerInput(state) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)

                        // A press is only a pan/zoom past touch slop. Consuming earlier would
                        // cancel taps on nodes, which check the final pass for consumption,
                        // and a physical click nearly always moves a pixel before release
                        var pastSlop = false
                        var slopPan = Offset.Zero
                        var slopZoom = 1f
                        // A node handling the press consumes it, which is what tells the canvas
                        // the press was not its own
                        var handledByNode = false

                        while (true) {
                            var panChange = Offset.Zero
                            var zoomChange = 1f
                            var centroid = Offset.Zero

                            val event = awaitPointerEvent()
                            if (event.changes.any { it.isConsumed }) {
                                // Checked before this handler consumes anything itself, so
                                // only a child's consumption lands here
                                if (!pastSlop) handledByNode = true
                            } else {
                                if (!pastSlop) {
                                    slopPan += event.calculatePan()
                                    slopZoom *= event.calculateZoom()
                                    val zoomMotion = abs(1f - slopZoom) *
                                            event.calculateCentroidSize(useCurrent = false)
                                    pastSlop =
                                        slopPan.getDistance() > viewConfiguration.touchSlop ||
                                                zoomMotion > viewConfiguration.touchSlop
                                    if (pastSlop) {
                                        // The motion accumulated while still under slop
                                        panChange = slopPan
                                        zoomChange = slopZoom
                                    }
                                } else {
                                    panChange = event.calculatePan()
                                    zoomChange = event.calculateZoom()
                                }
                                if (pastSlop) {
                                    // useCurrent = false: pivot at where fingers were
                                    centroid = event.calculateCentroid(useCurrent = false)
                                    event.changes.forEach { it.consume() }
                                }
                            }

                            if (panChange != Offset.Zero || zoomChange != 1f) {
                                val newScale = (state.scale * zoomChange).coerceIn(
                                    currentConfig.minScale,
                                    currentConfig.maxScale
                                )
                                val actualZoom = newScale / state.scale
                                // Pointer values arrive in pixels, the transform lives in dp:
                                // convert the two inputs, then the whole expression is dp
                                val pivot = DpOffset(
                                    (centroid.x - size.width / 2f).toDp(),
                                    (centroid.y - size.height / 2f).toDp()
                                )
                                val pan = DpOffset(panChange.x.toDp(), panChange.y.toDp())
                                state.updateTransform(
                                    scale = newScale,
                                    offset = pivot * (1 - actualZoom) +
                                            state.offset * actualZoom + pan
                                )
                            }

                            if (event.changes.none { it.pressed }) {
                                // Released without ever becoming a pan or reaching a node: a tap
                                // on the empty canvas
                                if (!pastSlop && !handledByNode) {
                                    if (currentConfig.selectionMode != SelectionMode.NONE) {
                                        interaction.clearSelection()
                                    }
                                    currentCallbacks.onCanvasClick?.invoke()
                                }
                                break
                            }
                        }
                    }
                }
                .pointerInput(state) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue

                            val change = event.changes.first()
                            val scrollDelta = change.scrollDelta
                            change.consume()

                            if (currentConfig.zoomConditionDesktop(event)) {
                                val zoomFactor = exp(-scrollDelta.y * currentConfig.zoomVelocity)
                                val newScale = (state.scale * zoomFactor).coerceIn(
                                    currentConfig.minScale,
                                    currentConfig.maxScale
                                )
                                val actualZoom = newScale / state.scale
                                val focalPoint = change.position
                                val pivot = DpOffset(
                                    (focalPoint.x - size.width / 2f).toDp(),
                                    (focalPoint.y - size.height / 2f).toDp()
                                )
                                state.updateTransform(
                                    scale = newScale,
                                    offset = pivot * (1 - actualZoom) + state.offset * actualZoom
                                )
                            } else {
                                state.updateTransform(
                                    scale = state.scale,
                                    offset = state.offset + DpOffset(
                                        x = (-scrollDelta.x * currentConfig.panVelocity).dp,
                                        y = (-scrollDelta.y * currentConfig.panVelocity).dp
                                    )
                                )
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
                            translationX = state.offset.x.toPx()
                            translationY = state.offset.y.toPx()
                        }
                ) {
                    // Draw edges first so they are behind nodes
                    when (edges) {
                        is EdgeRendering.Batched -> EdgeLayer(
                            kuiver = kuiver,
                            centerX = centerX,
                            centerY = centerY,
                            targets = nodeTargets,
                            transition = layoutTransition,
                            interaction = interaction,
                            anchorRegistry = anchorRegistry,
                            skipAnimation = skipAnimation,
                            edgeStyle = edges.style
                        )

                        is EdgeRendering.PerEdge -> kuiver.edges.forEach { edge ->
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
                                        targets = nodeTargets,
                                        transition = layoutTransition,
                                        interaction = interaction,
                                        anchorRegistry = anchorRegistry,
                                        skipAnimation = skipAnimation,
                                        edgeContent = edges.content
                                    )
                                }
                            }
                        }
                    }

                    if (config.showDebugBounds) {
                        RenderDebugBounds(
                            kuiver = kuiver,
                            centerX = centerX,
                            centerY = centerY,
                            graphCenterX = graphCenterX,
                            graphCenterY = graphCenterY
                        )
                    }

                    NodeLayer(
                        kuiver = kuiver,
                        source = state.kuiver,
                        centerX = centerX,
                        centerY = centerY,
                        targets = nodeTargets,
                        transition = layoutTransition,
                        state = state,
                        config = config,
                        callbacks = callbacks,
                        skipAnimation = skipAnimation,
                        onMeasured = reportMeasured,
                        nodeContent = nodeContent
                    )
                }
            }
        }
    }
}

