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
import androidx.compose.ui.input.pointer.PointerInputScope
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
 * Every interaction beyond pan and zoom is off by default: [selectionMode], [nodeDragEnabled],
 * [hoverEnabled], [edgeSelectionMode] and [edgeHoverEnabled] each add their gesture handling only
 * when enabled.
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
 * @property edgeSelectionMode whether and how tapping an edge selects it
 * @property edgeHoverEnabled whether the pointer moving over an edge updates
 * [com.dk.kuiver.KuiverInteractionState.hoveredEdgeId]
 * @property relayoutPolicy what happens to dragged nodes when the graph is laid out again
 * @property zoomConditionDesktop when a scroll event zooms instead of panning, Ctrl+scroll by default
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
    val edgeSelectionMode: SelectionMode = SelectionMode.NONE,
    val edgeHoverEnabled: Boolean = false,
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

/**
 * How the viewer renders edges, one per [KuiverViewer] overload.
 */
@Immutable
internal sealed interface EdgeRendering {
    // One composable per edge
    @Immutable
    class PerEdge(
        val content: @Composable KuiverEdgeScope.(KuiverEdge, Offset, Offset) -> Unit
    ) : EdgeRendering

    // All edges from one canvas
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
 * @param edgeContent composable content for rendering edges, with the edge's selection and hover
 * state as its [KuiverEdgeScope] receiver
 */
@Composable
fun KuiverViewer(
    state: KuiverViewerState,
    modifier: Modifier = Modifier,
    config: KuiverViewerConfig = KuiverViewerConfig(),
    callbacks: KuiverInteractionCallbacks = KuiverInteractionCallbacks.None,
    nodeContent: @Composable KuiverNodeScope.(KuiverNode) -> Unit,
    edgeContent: @Composable KuiverEdgeScope.(KuiverEdge, Offset, Offset) -> Unit
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
 * [edgeStyle] can read edge selection and hover from [KuiverViewerState.interaction].
 *
 * ```kotlin
 * KuiverViewer(
 *     state = state,
 *     nodeContent = { node -> Text(node.id) },
 *     edgeStyle = { edge ->
 *         val selected = state.interaction.isEdgeSelected(edge.id)
 *         EdgeStyle.styled(edge, baseColor = if (selected) Color.Blue else Color.Gray)
 *     }
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
    val currentConfig by rememberUpdatedState(config)
    val currentCallbacks by rememberUpdatedState(callbacks)
    // Single progress animation for all node positions, see LayoutTransition
    val layoutTransition = remember { LayoutTransition() }
    val reportMeasured = remember(state) { state::updateMeasuredDimensions }
    val edgeHitRegistry = remember { EdgeHitRegistry() }
    val edgesInteractive = config.edgeSelectionMode != SelectionMode.NONE ||
            config.edgeHoverEnabled || callbacks.onEdgeClick != null

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

    // Remove interaction state and hit paths for edges that no longer exist
    LaunchedEffect(state.layoutedKuiver.edges) {
        val currentEdgeIds = state.layoutedKuiver.edges.mapTo(HashSet()) { it.id }
        interaction.retainEdges(currentEdgeIds)
        edgeHitRegistry.retain(currentEdgeIds)
    }

    // Clear hover when it is turned off
    LaunchedEffect(config.hoverEnabled) {
        if (!config.hoverEnabled) interaction.hoveredNodeId = null
    }
    LaunchedEffect(config.edgeHoverEnabled) {
        if (!config.edgeHoverEnabled) interaction.hoveredEdgeId = null
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val centerX = maxWidth / 2
        val centerY = maxHeight / 2

        val kuiver = state.layoutedKuiver
        val bounds = remember(kuiver.nodes) { kuiver.nodes.values.calculatePositionBounds() }
        val graphCenterX = bounds.centerX
        val graphCenterY = bounds.centerY

        val nodeTargets = remember(kuiver.nodes) {
            kuiver.nodePositionsRelativeTo(graphCenterX, graphCenterY)
        }

        val isContentReady = state.hasFittedInitially || kuiver.nodes.isEmpty()

        // initialSnapDone lags one effects-phase behind hasFittedInitially: the frame where
        // positions first settle still snaps, later layout changes spring
        var initialSnapDone by remember { mutableStateOf(state.hasFittedInitially) }
        LaunchedEffect(state.hasFittedInitially) {
            if (state.hasFittedInitially) initialSnapDone = true
        }
        val skipInitialAnimation = !initialSnapDone && !config.animateInitialPlacement
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
                // One awaitPointerEventScope per gesture, re-entering it between events drops them
                .pointerInput(state) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)

                        // Consume only past touch slop, earlier would cancel taps on nodes
                        var pastSlop = false
                        var slopPan = Offset.Zero
                        var slopZoom = 1f
                        var handledByNode = false

                        while (true) {
                            var panChange = Offset.Zero
                            var zoomChange = 1f
                            var centroid = Offset.Zero

                            val event = awaitPointerEvent()
                            if (event.changes.any { it.isConsumed }) {
                                // Consumed by a node, this handler has not consumed anything yet
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
                                // Pointer values are pixels, the transform is dp
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
                                if (!pastSlop && !handledByNode) {
                                    val edgeMode = currentConfig.edgeSelectionMode
                                    val onEdgeClick = currentCallbacks.onEdgeClick
                                    val edgeId = if (
                                        edgeMode != SelectionMode.NONE || onEdgeClick != null
                                    ) {
                                        edgeAt(down.position, state, edgeHitRegistry)
                                    } else {
                                        null
                                    }
                                    val edge = edgeId?.let { state.layoutedKuiver.edgeById(it) }

                                    if (edge != null) {
                                        // A tap on an edge
                                        interaction.applyEdgeSelectionTap(edge.id, edgeMode)
                                        onEdgeClick?.invoke(edge)
                                    } else {
                                        // A tap on the empty canvas
                                        interaction.clearSelectionOnCanvasTap(
                                            currentConfig.selectionMode,
                                            edgeMode
                                        )
                                        currentCallbacks.onCanvasClick?.invoke()
                                    }
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
                .then(
                    if (config.edgeHoverEnabled) {
                        Modifier.pointerInput(state) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    interaction.hoveredEdgeId = when (event.type) {
                                        PointerEventType.Exit -> null
                                        PointerEventType.Enter, PointerEventType.Move ->
                                            // Nodes are drawn over edges
                                            if (interaction.hoveredNodeId != null) {
                                                null
                                            } else {
                                                edgeAt(
                                                    event.changes.first().position,
                                                    state,
                                                    edgeHitRegistry
                                                )
                                            }

                                        else -> continue
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                )
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
                    val bends = remember(kuiver.edges) {
                        if (kuiver.hasParallelEdges) kuiver.edges.parallelBends() else emptyMap()
                    }

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
                            bends = bends,
                            hitRegistry = edgeHitRegistry.takeIf { edgesInteractive },
                            edgeStyle = edges.style
                        )

                        is EdgeRendering.PerEdge -> {
                            kuiver.edges.forEach { edge ->
                                val fromNode = kuiver.nodes[edge.fromId]
                                val toNode = kuiver.nodes[edge.toId]

                                if (fromNode != null && toNode != null) {
                                    key(edge.id) {
                                        RenderEdge(
                                            edge = edge,
                                            bend = bends[edge.id],
                                            fromNode = fromNode,
                                            toNode = toNode,
                                            centerX = centerX,
                                            centerY = centerY,
                                            targets = nodeTargets,
                                            transition = layoutTransition,
                                            interaction = interaction,
                                            anchorRegistry = anchorRegistry,
                                            skipAnimation = skipAnimation,
                                            hitRegistry = edgeHitRegistry.takeIf {
                                                edgesInteractive
                                            },
                                            edgeContent = edges.content
                                        )
                                    }
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

/**
 * How far from an edge a tap still hits it, in screen dp.
 */
private val EDGE_HIT_TOLERANCE = 8.dp

/**
 * Returns the id of the edge under [position] in the viewer, or null if there is none.
 */
private fun PointerInputScope.edgeAt(
    position: Offset,
    state: KuiverViewerState,
    registry: EdgeHitRegistry
): String? {
    // Map from the screen to the graph layer
    val center = Offset(size.width / 2f, size.height / 2f)
    val pan = Offset(state.offset.x.toPx(), state.offset.y.toPx())
    val graphPoint = (position - center - pan) / state.scale + center
    return registry.hitTest(graphPoint, EDGE_HIT_TOLERANCE.toPx() / state.scale)
}
