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
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.kuiverSaver
import com.dk.kuiver.model.layout.LayoutConfig
import com.dk.kuiver.model.layout.layout
import com.dk.kuiver.util.calculateNodeBounds
import kotlinx.coroutines.launch
import kotlin.math.min

internal data class AnimationRequest(val scale: Float, val offset: Offset, val version: Int)

/**
 * State holder for the KuiverViewer component.
 *
 * @property kuiver The original graph structure (before layout)
 * @property layoutedKuiver The graph after layout positioning has been applied
 * @property scale Current zoom level, updated live during gestures and animations
 * @property offset Current pan offset in pixels, updated live during gestures and animations
 * @property canvasWidth Physical canvas width in pixels
 * @property canvasHeight Physical canvas height in pixels
 * @property contentOffset Offset reserved for UI overlay content
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

    var canvasWidth: Float by mutableFloatStateOf(0f)
        internal set

    var canvasHeight: Float by mutableFloatStateOf(0f)
        internal set

    var contentOffset: Offset by mutableStateOf(Offset.Zero)
        internal set

    internal var viewWidth: Float by mutableFloatStateOf(0f)

    private var animationVersion = 0
    internal var pendingAnimation: AnimationRequest? by mutableStateOf(null)
        private set

    internal var hasFittedInitially: Boolean by mutableStateOf(false)

    fun updateKuiver(newKuiver: Kuiver) {
        kuiver = newKuiver
    }

    fun updateContentOffset(newOffset: Offset) {
        contentOffset = newOffset
    }

    fun centerGraph(animated: Boolean = true) {
        val centeringOffset = Offset(contentOffset.x / 2f, contentOffset.y / 2f)
        if (layoutedKuiver.nodes.isEmpty() || canvasWidth == 0f || canvasHeight == 0f) {
            if (animated) requestAnimation(1f, centeringOffset) else {
                pendingAnimation = null; scale = 1f; offset = centeringOffset
            }
            return
        }
        val bounds = layoutedKuiver.nodes.values.calculateNodeBounds()
        val density = canvasWidth / viewWidth
        val graphWidthPx = bounds.width * density
        val graphHeightPx = bounds.height * density
        val targetScaleX = if (graphWidthPx > 0) (canvasWidth * 0.8f) / graphWidthPx else 1f
        val targetScaleY = if (graphHeightPx > 0) (canvasHeight * 0.8f) / graphHeightPx else 1f
        val newScale = min(targetScaleX, targetScaleY).coerceIn(0.1f, 2f)
        if (animated) requestAnimation(newScale, centeringOffset) else {
            pendingAnimation = null; scale = newScale; offset = centeringOffset
        }
    }

    fun zoomIn() {
        val newScale = (scale * 1.2f).coerceAtMost(5f)
        requestAnimation(newScale, offset * (newScale / scale))
    }

    fun zoomOut() {
        val newScale = (scale / 1.2f).coerceAtLeast(0.1f)
        requestAnimation(newScale, offset * (newScale / scale))
    }

    private fun requestAnimation(targetScale: Float, targetOffset: Offset) {
        pendingAnimation = AnimationRequest(targetScale, targetOffset, ++animationVersion)
    }
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
        launch {
            snapshotFlow { state.offset }.collect {
                savedOffsetX = it.x; savedOffsetY = it.y
            }
        }
        snapshotFlow { state.hasFittedInitially }.collect { savedHasFitted = it }
    }

    setupLayout(state, layoutConfig)
    return state
}

@Composable
private fun setupLayout(state: KuiverViewerState, layoutConfig: LayoutConfig) {
    // Capture at composition time so the effect body uses the snapshot values that
    // triggered this composition, not values written during the layout phase (e.g.
    // canvasWidth set by onGloballyPositioned). Without this, Frame 1's effect would
    // see canvasWidth > 0 and run layout with the still-unmeasured kuiver.
    val kuiver = state.kuiver
    val canvasWidth = state.canvasWidth
    val canvasHeight = state.canvasHeight
    LaunchedEffect(kuiver, layoutConfig, canvasWidth, canvasHeight) {
        val laid = if (canvasWidth > 0f && canvasHeight > 0f) {
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
            layout(kuiver, configWithDimensions)
        } else {
            kuiver
        }
        state.layoutedKuiver = laid
        if (!state.hasFittedInitially &&
            laid.nodes.isNotEmpty() &&
            laid.nodes.values.any { it.dimensions != null }
        ) {
            state.centerGraph(animated = false)
            state.hasFittedInitially = true
        }
    }
}
