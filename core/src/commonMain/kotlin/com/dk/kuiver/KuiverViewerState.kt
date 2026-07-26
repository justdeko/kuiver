package com.dk.kuiver

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.NodeDimensions
import com.dk.kuiver.model.kuiverSaver
import com.dk.kuiver.model.layout.LayoutConfig
import com.dk.kuiver.model.layout.layout
import com.dk.kuiver.renderer.KuiverViewerConfig
import com.dk.kuiver.util.calculateNodeBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

internal data class AnimationRequest(val scale: Float, val offset: Offset, val version: Int)

/**
 * State holder for the KuiverViewer component.
 *
 * The graph coordinate space is [Dp] end to end: node positions, layout spacing, node dimensions
 * and [canvasWidth]/[canvasHeight], which makes a graph look the same on every screen density.
 * [scale] and [offset] are the view transform on top of it and stay in pixels, the space gestures
 * and `graphicsLayer` work in.
 *
 * @property kuiver The original graph structure (before layout)
 * @property layoutedKuiver The graph after layout positioning has been applied
 * @property scale Current zoom level, updated live during gestures and animations
 * @property offset Current pan offset in pixels, updated live during gestures and animations
 * @property canvasWidth Canvas width
 * @property canvasHeight Canvas height
 * @property contentOffset Offset in pixels reserved for UI overlay content
 * @property hasFittedInitially True once the graph has been laid out and auto-centered for the
 * first time. Useful when you have loading UI that should disappear once the graph is ready.
 */
@Stable
class KuiverViewerState internal constructor(
    initialKuiver: Kuiver,
    initialScale: Float = 1f,
    initialOffset: Offset = Offset.Zero
) {
    var kuiver: Kuiver by mutableStateOf(initialKuiver)
        internal set

    var layoutedKuiver: Kuiver by mutableStateOf(initialKuiver)
        internal set

    var scale: Float by mutableFloatStateOf(initialScale)
        internal set

    var offset: Offset by mutableStateOf(initialOffset)
        internal set

    var canvasWidth: Dp by mutableStateOf(0.dp)
        internal set

    var canvasHeight: Dp by mutableStateOf(0.dp)
        internal set

    var contentOffset: Offset by mutableStateOf(Offset.Zero)
        internal set

    /**
     * Renderer-measured sizes of the nodes without explicit dimensions. Applied on top of
     * [kuiver] before layout, so [kuiver] stays the caller's graph.
     */
    internal var measuredDimensions: Map<String, NodeDimensions> by mutableStateOf(emptyMap())
        private set

    internal var config: KuiverViewerConfig = KuiverViewerConfig()

    private var animationVersion = 0
    internal var pendingAnimation: AnimationRequest? by mutableStateOf(null)
        private set

    var hasFittedInitially: Boolean by mutableStateOf(false)
        internal set

    fun updateKuiver(newKuiver: Kuiver) {
        kuiver = newKuiver
    }

    /**
     * Adopts node sizes from the renderer. Called from the measure phase, so it writes nothing when
     * the sizes are unchanged.
     *
     * @param dimensions sizes of all auto-sized nodes, keyed by node id
     */
    internal fun updateMeasuredDimensions(dimensions: Map<String, NodeDimensions>) {
        if (dimensions != measuredDimensions) measuredDimensions = dimensions
    }

    fun updateContentOffset(newOffset: Offset) {
        contentOffset = newOffset
    }

    fun centerGraph(animated: Boolean = true) {
        val centeringOffset = Offset(contentOffset.x / 2f, contentOffset.y / 2f)
        if (layoutedKuiver.nodes.isEmpty() || canvasWidth == 0.dp || canvasHeight == 0.dp) {
            updateTransform(clampScale(1f), centeringOffset, animated)
            return
        }
        val bounds = layoutedKuiver.nodes.values.calculateNodeBounds()
        val padding = config.contentPadding
        // Graph bounds and canvas are the same space, so dividing them out gives the scale directly
        val targetScaleX = if (bounds.width > 0.dp) (canvasWidth * padding) / bounds.width else 1f
        val targetScaleY = if (bounds.height > 0.dp) (canvasHeight * padding) / bounds.height else 1f
        updateTransform(clampScale(min(targetScaleX, targetScaleY)), centeringOffset, animated)
    }

    fun zoomIn() {
        val newScale = clampScale(scale * config.zoomStep)
        requestAnimation(newScale, offset * (newScale / scale))
    }

    fun zoomOut() {
        val newScale = clampScale(scale / config.zoomStep)
        requestAnimation(newScale, offset * (newScale / scale))
    }

    fun updateTransform(scale: Float, offset: Offset, animated: Boolean = false) {
        if (animated) {
            requestAnimation(scale, offset)
        } else {
            pendingAnimation = null
            this.scale = scale
            this.offset = offset
        }
    }

    private fun requestAnimation(targetScale: Float, targetOffset: Offset) {
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

    val state = remember {
        KuiverViewerState(savedKuiver, savedScale, Offset(savedOffsetX, savedOffsetY)).also {
            it.hasFittedInitially = savedHasFitted
        }
    }

    // Sync state back to saveable vars via snapshotFlow to avoid composition-phase subscriptions
    LaunchedEffect(state) {
        launch { snapshotFlow { state.kuiver }.collect { savedKuiver = it } }
        launch { snapshotFlow { state.scale }.collect { savedScale = it } }
        launch { snapshotFlow { state.hasFittedInitially }.collect { savedHasFitted = it } }
        launch {
            snapshotFlow { state.offset }.collect {
                savedOffsetX = it.x; savedOffsetY = it.y
            }
        }
    }

    setupLayout(state, layoutConfig)
    return state
}

@Suppress("ComposableNaming")
@Composable
private fun setupLayout(state: KuiverViewerState, layoutConfig: LayoutConfig) {
    // Capture at composition time so the effect body uses the snapshot values that
    // triggered this composition, not values written during the measure and layout
    // phases (canvasWidth from onSizeChanged, measuredDimensions from the node layer).
    // Without this, Frame 1's effect would see canvasWidth > 0 and run layout with the
    // still-unmeasured kuiver.
    val kuiver = state.kuiver
    val measuredDimensions = state.measuredDimensions
    val canvasWidth = state.canvasWidth
    val canvasHeight = state.canvasHeight
    LaunchedEffect(kuiver, measuredDimensions, layoutConfig, canvasWidth, canvasHeight) {
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
                // The layout loop never suspends, so a superseded layout would otherwise
                // run to completion before its replacement starts
                val layoutContext = coroutineContext
                layout(sizedKuiver, configWithDimensions) { layoutContext.ensureActive() }
            }
        } else {
            sizedKuiver
        }
        state.layoutedKuiver = laid
        state.applyInitialFit(canvasWidth, canvasHeight)
    }
}
