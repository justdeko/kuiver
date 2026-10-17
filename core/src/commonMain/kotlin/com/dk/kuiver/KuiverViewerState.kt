package com.dk.kuiver

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.NodeDimensions
import com.dk.kuiver.model.kuiverSaver
import com.dk.kuiver.model.layout.LayoutConfig
import com.dk.kuiver.model.layout.layout
import com.dk.kuiver.model.manualPositionsSaver
import com.dk.kuiver.renderer.KuiverViewerConfig
import com.dk.kuiver.util.calculateNodeBounds
import com.dk.kuiver.util.calculatePositionBounds
import com.dk.kuiver.util.div
import com.dk.kuiver.util.times
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

internal data class AnimationRequest(val scale: Float, val offset: DpOffset, val version: Int)

/**
 * State holder for the KuiverViewer component.
 *
 * The graph coordinate space is [Dp] end to end: node positions, layout spacing, node dimensions,
 * [canvasWidth]/[canvasHeight] and the [offset] half of the view transform, which makes a graph
 * look the same on every screen density. [scale] is a unitless ratio. Pixels appear
 * only where the platform hands them over, in gestures and in `graphicsLayer`, and are converted
 * at that boundary.
 *
 * @property kuiver The original graph structure (before layout)
 * @property layoutedKuiver The graph after layout positioning has been applied
 * @property scale Current zoom level, updated live during gestures and animations
 * @property offset Current pan offset in graph dp, updated live during gestures and animations
 * @property canvasWidth Canvas width
 * @property canvasHeight Canvas height
 * @property contentOffset Space reserved for UI overlay content
 * @property hasFittedInitially True once the graph has been laid out and auto-centered for the
 * first time. Useful when you have loading UI that should disappear once the graph is ready.
 * @property interaction Selection, hover and drag of the nodes
 * @property manualPositions Positions the user or the caller has set by hand, keyed by node id.
 * Under [RelayoutPolicy.KEEP_MANUAL] they are reapplied after every layout pass.
 */
@Stable
class KuiverViewerState internal constructor(
    initialKuiver: Kuiver,
    initialScale: Float = 1f,
    initialOffset: DpOffset = DpOffset.Zero
) {
    val interaction: KuiverInteractionState = KuiverInteractionState()

    var kuiver: Kuiver by mutableStateOf(initialKuiver)
        internal set

    var layoutedKuiver: Kuiver by mutableStateOf(initialKuiver)
        internal set

    var scale: Float by mutableFloatStateOf(initialScale)
        internal set

    var offset: DpOffset by mutableStateOf(initialOffset)
        internal set

    var canvasWidth: Dp by mutableStateOf(0.dp)
        internal set

    var canvasHeight: Dp by mutableStateOf(0.dp)
        internal set

    var contentOffset: DpOffset by mutableStateOf(DpOffset.Zero)
        internal set

    /**
     * Renderer-measured sizes of the nodes without explicit dimensions. Applied on top of
     * [kuiver] before layout.
     */
    internal var measuredDimensions: Map<String, NodeDimensions> by mutableStateOf(emptyMap())
        private set

    var manualPositions: Map<String, DpOffset> by mutableStateOf(emptyMap())
        private set

    internal var config: KuiverViewerConfig = KuiverViewerConfig()

    private var manualLayout: Kuiver? by mutableStateOf(null, referentialEqualityPolicy())

    // Bumped to ask for a layout pass that nothing else would have triggered
    private var layoutGeneration: Int by mutableIntStateOf(0)

    /** Key of the current layout request, so the renderer relaunches layout when it changes. */
    internal val layoutKey: Int get() = layoutGeneration

    private var animationVersion = 0
    internal var pendingAnimation: AnimationRequest? by mutableStateOf(null)
        private set

    var hasFittedInitially: Boolean by mutableStateOf(false)
        internal set

    /**
     * Swaps in a new graph, which lays it out again. [Kuiver] is immutable: derive the new graph
     * with [Kuiver.withNode], [Kuiver.withEdge] or [Kuiver.rebuild] and pass the result here. A
     * graph equal to the current one is a no-op.
     *
     * @param newKuiver the graph to display
     */
    fun updateKuiver(newKuiver: Kuiver) {
        kuiver = newKuiver
    }

    /**
     * Adopts node sizes from the renderer. Called from the measure phase. Writes nothing when the
     * sizes are unchanged.
     *
     * @param dimensions sizes of all auto-sized nodes, keyed by node id
     */
    internal fun updateMeasuredDimensions(dimensions: Map<String, NodeDimensions>) {
        if (dimensions != measuredDimensions) measuredDimensions = dimensions
    }

    /**
     * Reserves room for overlay UI, which [centerGraph] then compensates for.
     *
     * @param newOffset space taken up by the overlay
     */
    fun updateContentOffset(newOffset: DpOffset) {
        contentOffset = newOffset
    }

    /**
     * Moves [nodeId] to [position] in the dp space the graph is laid out in, and records it as a
     * manual position so [RelayoutPolicy.KEEP_MANUAL] can put it back after the next layout pass.
     * This is what a node drag commits, and it is equally the way to place a node from code.
     *
     * Unknown ids are ignored.
     *
     * @param nodeId id of the node to move
     * @param position where to move it, in graph dp
     */
    fun moveNode(nodeId: String, position: DpOffset) {
        val node = layoutedKuiver.nodes[nodeId] ?: return
        if (node.position == position) return

        val before = layoutedKuiver.nodes.values.calculatePositionBounds()
        val moved = layoutedKuiver.withNode(node.copy(position = position))
        val after = moved.nodes.values.calculatePositionBounds()

        layoutedKuiver = moved
        manualPositions = manualPositions + (nodeId to position)
        // This generation is the move itself, which the caller already performed
        manualLayout = moved

        // Moving a node shifts the graph center. Cancel that shift in the view transform
        offset += DpOffset(
            after.centerX - before.centerX,
            after.centerY - before.centerY
        ) * scale
    }

    /**
     * Moves the node by a specific offset. Also see [moveNode]
     *
     * @param nodeId the node id
     * @param delta how far to move the node, in graph dp
     */
    fun moveNodeBy(nodeId: String, delta: DpOffset) {
        if (delta == DpOffset.Zero) return
        val node = layoutedKuiver.nodes[nodeId] ?: return
        moveNode(nodeId, node.position + delta)
    }

    /**
     * Lays the graph out again from scratch. The viewer does this on its own whenever the graph,
     * the node sizes or the canvas change. Use this when nothing observable changed and you still
     * want the algorithm to have another go.
     */
    fun relayout() {
        layoutGeneration++
    }

    /**
     * Hands every node back to the layout algorithm, dropping the positions collected by dragging
     * and by [moveNode], and lays the graph out again.
     */
    fun clearManualPositions() {
        if (manualPositions.isEmpty()) return
        manualPositions = emptyMap()
        relayout()
    }

    /** Adopts manual positions carried over from a previous instance of this state. */
    internal fun restoreManualPositions(positions: Map<String, DpOffset>) {
        if (positions.isNotEmpty()) manualPositions = positions
    }

    /** Drops manual positions of nodes that are no longer in the graph. */
    private fun pruneManualPositions(nodeIds: Set<String>) {
        if (manualPositions.keys.all { it in nodeIds }) return
        manualPositions = manualPositions.filterKeys { it in nodeIds }
    }

    /**
     * Applies the manual positions to a freshly laid out graph, unless the caller has handed
     * layout full control through [RelayoutPolicy.RELAYOUT_ALL].
     */
    internal fun withRelayoutPolicyApplied(laid: Kuiver): Kuiver {
        pruneManualPositions(laid.nodes.keys)
        if (config.relayoutPolicy == RelayoutPolicy.RELAYOUT_ALL) return laid
        val overrides = manualPositions.mapNotNull { (nodeId, position) ->
            laid.nodes[nodeId]?.takeIf { it.position != position }?.copy(position = position)
        }
        return laid.withNodes(overrides)
    }

    /**
     * Whether [candidate] is a generation produced by [moveNode] and not by the layout
     * algorithm. Those are placed without animating.
     */
    internal fun isManualLayout(candidate: Kuiver): Boolean = manualLayout === candidate

    /**
     * Centers the graph in the viewport and scales it to fit.
     *
     * @param animated whether to animate to the new transform
     */
    fun centerGraph(animated: Boolean = true) {
        val centeringOffset = contentOffset / 2f
        if (layoutedKuiver.nodes.isEmpty() || canvasWidth == 0.dp || canvasHeight == 0.dp) {
            updateTransform(clampScale(1f), centeringOffset, animated)
            return
        }
        val bounds = layoutedKuiver.nodes.values.calculateNodeBounds()
        val padding = config.contentPadding
        val targetScaleX = if (bounds.width > 0.dp) (canvasWidth * padding) / bounds.width else 1f
        val targetScaleY = if (bounds.height > 0.dp) (canvasHeight * padding) / bounds.height else 1f
        updateTransform(clampScale(min(targetScaleX, targetScaleY)), centeringOffset, animated)
    }

    /**
     * Zooms in one [KuiverViewerConfig.zoomStep], animated
     */
    fun zoomIn() {
        val newScale = clampScale(scale * config.zoomStep)
        requestAnimation(newScale, offset * (newScale / scale))
    }

    /**
     * Zooms out one [KuiverViewerConfig.zoomStep], animated
     */
    fun zoomOut() {
        val newScale = clampScale(scale / config.zoomStep)
        requestAnimation(newScale, offset * (newScale / scale))
    }

    /**
     * Sets the view transform, cancelling any zoom or pan animation still running.
     *
     * @param scale zoom level to set, unclamped
     * @param offset pan offset to set, in graph dp
     * @param animated whether to animate to the new transform
     */
    fun updateTransform(scale: Float, offset: DpOffset, animated: Boolean = false) {
        if (animated) {
            requestAnimation(scale, offset)
        } else {
            pendingAnimation = null
            this.scale = scale
            this.offset = offset
        }
    }

    private fun requestAnimation(targetScale: Float, targetOffset: DpOffset) {
        pendingAnimation = AnimationRequest(targetScale, targetOffset, ++animationVersion)
    }

    private fun clampScale(value: Float) = value.coerceIn(config.minScale, config.maxScale)
}

/**
 * Fits the graph to the viewport after layout and measurement if [KuiverViewerConfig.fitToContent]
 *
 * @param canvasWidth canvas width
 * @param canvasHeight canvas height
 */
internal fun KuiverViewerState.applyInitialFit(canvasWidth: Dp, canvasHeight: Dp) {
    if (canvasWidth <= 0.dp || canvasHeight <= 0.dp) return
    if (hasFittedInitially) return
    val nodes = layoutedKuiver.nodes.values
    if (nodes.isEmpty() || nodes.none { it.dimensions != null }) return

    if (config.fitToContent) centerGraph(animated = false)
    hasFittedInitially = true
}

/**
 * Creates and remembers a [KuiverViewerState] with the given initial graph and layout configuration.
 *
 * @param initialKuiver The initial graph to display
 * @param layoutConfig Configuration for the layout algorithm
 */
@Composable
fun rememberKuiverViewerState(
    initialKuiver: Kuiver,
    layoutConfig: LayoutConfig = LayoutConfig.Hierarchical()
): KuiverViewerState {
    val state = remember { KuiverViewerState(initialKuiver) }
    setupLayout(state, layoutConfig)
    return state
}

/**
 * Creates and remembers a saveable [KuiverViewerState] that persists across configuration changes.
 *
 * Saves graph structure, zoom level, and pan position.
 *
 * @param initialKuiver The initial graph to display
 * @param layoutConfig Configuration for the layout algorithm
 */
@Composable
fun rememberSaveableKuiverViewerState(
    initialKuiver: Kuiver,
    layoutConfig: LayoutConfig = LayoutConfig.Hierarchical()
): KuiverViewerState {
    var savedKuiver by rememberSaveable(stateSaver = kuiverSaver()) { mutableStateOf(initialKuiver) }
    var savedScale by rememberSaveable { mutableFloatStateOf(1f) }
    var savedOffsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var savedOffsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var savedHasFitted by rememberSaveable { mutableStateOf(false) }
    var savedManualPositions by rememberSaveable(stateSaver = manualPositionsSaver()) {
        mutableStateOf(emptyMap<String, DpOffset>())
    }

    val state = remember {
        KuiverViewerState(savedKuiver, savedScale, DpOffset(savedOffsetX.dp, savedOffsetY.dp)).also {
            it.hasFittedInitially = savedHasFitted
            it.restoreManualPositions(savedManualPositions)
        }
    }

    // Sync state back to saveable vars via snapshotFlow to avoid composition-phase subscriptions
    LaunchedEffect(state) {
        launch { snapshotFlow { state.kuiver }.collect { savedKuiver = it } }
        launch { snapshotFlow { state.scale }.collect { savedScale = it } }
        launch { snapshotFlow { state.hasFittedInitially }.collect { savedHasFitted = it } }
        launch { snapshotFlow { state.manualPositions }.collect { savedManualPositions = it } }
        launch {
            // Stored as dp to survive a density change across the restore
            snapshotFlow { state.offset }.collect {
                savedOffsetX = it.x.value; savedOffsetY = it.y.value
            }
        }
    }

    setupLayout(state, layoutConfig)
    return state
}

@Suppress("ComposableNaming")
@Composable
private fun setupLayout(state: KuiverViewerState, layoutConfig: LayoutConfig) {
    // Captured here so the effect sees the composition values instead of those written during measure
    val kuiver = state.kuiver
    val measuredDimensions = state.measuredDimensions
    val canvasWidth = state.canvasWidth
    val canvasHeight = state.canvasHeight
    val layoutKey = state.layoutKey
    LaunchedEffect(kuiver, measuredDimensions, layoutConfig, canvasWidth, canvasHeight, layoutKey) {
        val sizedKuiver = if (measuredDimensions.isEmpty()) {
            kuiver
        } else {
            kuiver.withMeasuredDimensions(measuredDimensions)
        }
        val laid = if (canvasWidth > 0.dp && canvasHeight > 0.dp) {
            val configWithDimensions = when (layoutConfig) {
                is LayoutConfig.Hierarchical -> layoutConfig.copy(
                    width = canvasWidth,
                    height = canvasHeight
                )

                is LayoutConfig.ForceDirected -> layoutConfig.copy(
                    width = canvasWidth,
                    height = canvasHeight
                )

                is LayoutConfig.Custom -> layoutConfig.copy(
                    width = canvasWidth,
                    height = canvasHeight
                )
            }
            withContext(Dispatchers.Default) {
                // The layout loop never suspends, cancellation needs an explicit check
                val layoutContext = coroutineContext
                layout(sizedKuiver, configWithDimensions) { layoutContext.ensureActive() }
            }
        } else {
            sizedKuiver
        }
        state.layoutedKuiver = state.withRelayoutPolicyApplied(laid)
        state.applyInitialFit(canvasWidth, canvasHeight)
    }
}
