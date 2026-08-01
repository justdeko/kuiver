package com.dk.kuiver.renderer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dk.kuiver.KuiverViewerState
import com.dk.kuiver.RelayoutPolicy
import com.dk.kuiver.SelectionMode
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.ui.EdgeStyle
import com.dk.kuiver.util.calculatePositionBounds
import kotlin.math.abs
import kotlin.math.exp

/**
 * How a [KuiverViewer] looks and what it lets the user do.
 *
 * Every interaction beyond pan and zoom is off by default, so a viewer behaves the same as before
 * these knobs existed until one of them is turned on: [selectionMode], [nodeDragEnabled],
 * [hoverEnabled] and [keyboardEnabled] each add their gesture handling only when enabled.
 *
 * @property selectionMode whether and how tapping a node selects it
 * @property nodeDragEnabled whether nodes can be dragged to a new position
 * @property hoverEnabled whether the pointer entering a node updates
 * [com.dk.kuiver.KuiverInteractionState.hoveredNodeId]
 * @property keyboardEnabled whether the viewer takes focus and pans with the arrow keys and zooms
 * with `+` and `-`. Adds one focus stop to the surrounding layout
 * @property keyboardPanStep how far one arrow key press pans
 * @property relayoutPolicy what happens to dragged nodes when the graph is laid out again
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
    val selectionMode: SelectionMode = SelectionMode.NONE,
    val nodeDragEnabled: Boolean = false,
    val hoverEnabled: Boolean = false,
    val keyboardEnabled: Boolean = false,
    val keyboardPanStep: Dp = 48.dp,
    val relayoutPolicy: RelayoutPolicy = RelayoutPolicy.KEEP_MANUAL,
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
 * Edges are [EdgeStyle] values instead of a composable each: one layout node and one draw pass
 * for the whole edge set, no edge labels. For graphs of several hundred nodes and up.
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
    val focusRequester = remember { FocusRequester() }
    // Read from the gesture loops, which must not restart when a callback changes
    val currentConfig by rememberUpdatedState(config)
    val currentCallbacks by rememberUpdatedState(callbacks)
    // Single progress animatable for both scale and offset in the same frame
    val progressAnim = remember { Animatable(1f) }
    // Single progress animation for all node positions, see LayoutTransition
    val layoutTransition = remember { LayoutTransition() }
    // Stable, so a new generation of node targets does not re-measure the node layer
    val reportMeasured = remember(state) { state::updateMeasuredDimensions }

    // run before LaunchedEffect so the initial auto-fit already has config
    SideEffect {
        state.config = config
        state.density = density
    }

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

    // Remove anchors and interaction state for nodes that no longer exist
    LaunchedEffect(state.layoutedKuiver.nodes.keys) {
        val currentNodeIds = state.layoutedKuiver.nodes.keys
        anchorRegistry.anchorPositions.keys.forEach { nodeId ->
            if (nodeId !in currentNodeIds) anchorRegistry.clearNode(nodeId)
        }
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
                .then(
                    if (config.keyboardEnabled) {
                        Modifier
                            .focusRequester(focusRequester)
                            .onKeyEvent { event ->
                                handleViewerKey(event, state, config.keyboardPanStep, density)
                            }
                            .focusable()
                    } else {
                        Modifier
                    }
                )
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

                        if (currentConfig.keyboardEnabled) focusRequester.requestFocus()

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
                                val halfW = size.width / 2f
                                val halfH = size.height / 2f
                                state.updateTransform(
                                    scale = newScale,
                                    offset = Offset(
                                        x = (centroid.x - halfW) * (1 - actualZoom) + state.offset.x * actualZoom + panChange.x,
                                        y = (centroid.y - halfH) * (1 - actualZoom) + state.offset.y * actualZoom + panChange.y
                                    )
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
                                val zoomFactor = exp(-scrollDelta.y * 0.05f)
                                val newScale = (state.scale * zoomFactor).coerceIn(
                                    currentConfig.minScale,
                                    currentConfig.maxScale
                                )
                                val actualZoom = newScale / state.scale
                                val focalPoint = change.position
                                val halfW = size.width / 2f
                                val halfH = size.height / 2f
                                state.updateTransform(
                                    scale = newScale,
                                    offset = Offset(
                                        x = (focalPoint.x - halfW) * (1 - actualZoom) + state.offset.x * actualZoom,
                                        y = (focalPoint.y - halfH) * (1 - actualZoom) + state.offset.y * actualZoom
                                    )
                                )
                            } else {
                                state.updateTransform(
                                    scale = state.scale,
                                    offset = state.offset + Offset(
                                        x = -scrollDelta.x * currentConfig.panVelocity,
                                        y = -scrollDelta.y * currentConfig.panVelocity
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
                            translationX = state.offset.x
                            translationY = state.offset.y
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
                            graphCenterY = graphCenterY,
                            showDebugBounds = config.showDebugBounds,
                            onCanvasSize = { _, _ -> },
                            onRedBoxCenter = { _ -> },
                            onBoundsChange = { _ -> }
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
 * Arrow keys pan the view, `+` and `-` zoom it around the center.
 *
 * @return whether the key was one of those, so the event stops here
 */
private fun handleViewerKey(
    event: KeyEvent,
    state: KuiverViewerState,
    panStep: Dp,
    density: Density
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    val step = with(density) { panStep.toPx() }
    // The offset moves the content, so panning the view one way moves the graph the other
    val pan = when (event.key) {
        Key.DirectionLeft -> Offset(step, 0f)
        Key.DirectionRight -> Offset(-step, 0f)
        Key.DirectionUp -> Offset(0f, step)
        Key.DirectionDown -> Offset(0f, -step)
        else -> null
    }
    if (pan != null) {
        state.updateTransform(state.scale, state.offset + pan)
        return true
    }

    return when (event.key) {
        Key.Plus, Key.Equals, Key.NumPadAdd -> {
            state.zoomIn()
            true
        }

        Key.Minus, Key.NumPadSubtract -> {
            state.zoomOut()
            true
        }

        else -> false
    }
}
