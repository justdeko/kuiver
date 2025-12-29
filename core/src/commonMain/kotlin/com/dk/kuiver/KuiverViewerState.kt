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
import androidx.compose.ui.geometry.Offset
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.kuiverSaver
import com.dk.kuiver.model.layout.LayoutConfig
import com.dk.kuiver.model.layout.layout
import com.dk.kuiver.util.calculateNodeBounds

/**
 * State holder for KuiverViewer component.
 *
 * Manages graph visualization state including zoom, pan, and layout.
 * The viewer automatically handles canvas sizing and layout calculations.
 *
 * @property layoutedKuiver The graph after layout positioning has been applied. Use this to access the current graph structure.
 * @property scale Current zoom level (1.0 = 100%, 0.5 = 50%, 2.0 = 200%)
 * @property offset Current pan offset in pixels
 * @property centerGraph Centers and fits the graph in the viewport
 * @property zoomIn Zooms in by 20% (capped at 500%)
 * @property zoomOut Zooms out by 20% (capped at 10%)
 * @property updateKuiver Updates the graph structure and triggers relayout
 * @property updateTransform Directly sets zoom level and pan offset (scale, offset)
 *
 * ## Internal Properties
 *
 * These properties are managed automatically by the viewer. You typically don't need to interact with them:
 *
 * @property kuiver The original graph structure (before layout)
 * @property viewWidth Logical width of the view in DP (for density calculations)
 * @property canvasWidth Physical canvas width in pixels
 * @property canvasHeight Physical canvas height in pixels
 * @property contentOffset Offset for UI overlay content (managed internally)
 * @property updateViewWidth Internal callback for view width updates
 * @property updateCanvasSize Internal callback for canvas size updates
 * @property updateContentOffset Internal callback for content offset updates
 */
@Stable
data class KuiverViewerState(
    val kuiver: Kuiver,
    val layoutedKuiver: Kuiver,
    val scale: Float,
    val offset: Offset,
    val viewWidth: Float,
    val canvasWidth: Float,
    val canvasHeight: Float,
    val contentOffset: Offset,
    val centerGraph: () -> Unit,
    val zoomIn: () -> Unit,
    val zoomOut: () -> Unit,
    val updateKuiver: (Kuiver) -> Unit,
    val updateViewWidth: (Float) -> Unit,
    val updateCanvasSize: (Float, Float) -> Unit,
    val updateContentOffset: (Offset) -> Unit,
    val updateTransform: (Float, Offset) -> Unit
)

/**
 * Creates and remembers a [KuiverViewerState] with the given initial graph and layout configuration.
 *
 * For persistence across configuration changes, use [rememberSaveableKuiverViewerState].
 *
 * @param initialKuiver The initial graph to display
 * @param layoutConfig Configuration for the layout algorithm. Defaults to hierarchical layout.
 * @return A remembered [KuiverViewerState] instance
 *
 * ```kotlin
 * val viewerState = rememberKuiverViewerState(
 *     initialKuiver = myGraph,
 *     layoutConfig = LayoutConfig.Hierarchical()
 * )
 *
 * KuiverViewer(
 *     state = viewerState,
 *     nodeContent = { node -> /* ... */ },
 *     edgeContent = { edge, from, to -> /* ... */ }
 * )
 * ```
 *
 * @see rememberSaveableKuiverViewerState for state that persists across configuration changes
 */
@Composable
fun rememberKuiverViewerState(
    initialKuiver: Kuiver,
    layoutConfig: LayoutConfig = LayoutConfig.Hierarchical()
): KuiverViewerState {
    var kuiver by remember { mutableStateOf(initialKuiver) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewWidth by remember { mutableFloatStateOf(0f) }
    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var canvasHeight by remember { mutableFloatStateOf(0f) }
    var contentOffset by remember { mutableStateOf(Offset.Zero) }
    var layoutedKuiver by remember { mutableStateOf(kuiver) }

    return createKuiverViewerState(
        kuiver = kuiver,
        getLayoutedKuiver = { layoutedKuiver },
        scale = scale,
        offset = offset,
        viewWidth = viewWidth,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        contentOffset = contentOffset,
        layoutConfig = layoutConfig,
        onKuiverChange = { kuiver = it },
        onLayoutedKuiverChange = { layoutedKuiver = it },
        onScaleChange = { scale = it },
        onOffsetChange = { offset = it },
        onViewWidthChange = { viewWidth = it },
        onCanvasSizeChange = { w, h -> canvasWidth = w; canvasHeight = h },
        onContentOffsetChange = { contentOffset = it }
    )
}

/**
 * Creates and remembers a saveable [KuiverViewerState] that persists across configuration changes.
 *
 * Saves graph structure, zoom level, and pan position. Your application data (node labels, colors, etc.)
 * must be saved separately - Kuiver only manages the graph structure and view state.
 *
 * @param initialKuiver The initial graph to display
 * @param layoutConfig Configuration for the layout algorithm. Defaults to hierarchical layout.
 * @return A saveable [KuiverViewerState] instance
 *
 * ## Example
 *
 * ```kotlin
 * // Your app data (saved separately)
 * var nodeData by rememberSaveable(stateSaver = nodeDataSaver()) {
 *     mutableStateOf(mapOf("A" to MyData(...)))
 * }
 *
 * val viewerState = rememberSaveableKuiverViewerState(
 *     initialKuiver = myGraph,
 *     layoutConfig = LayoutConfig.Hierarchical()
 * )
 *
 * KuiverViewer(
 *     state = viewerState,
 *     nodeContent = { node ->
 *         val data = nodeData[node.id] // Look up your data
 *         /* render node using data */
 *     }
 * )
 * ```
 *
 * @see rememberKuiverViewerState for non-persistent state
 */
@Composable
fun rememberSaveableKuiverViewerState(
    initialKuiver: Kuiver,
    layoutConfig: LayoutConfig = LayoutConfig.Hierarchical()
): KuiverViewerState {
    // Saveable state for the entire graph (now possible since it's data-agnostic!)
    var kuiver by rememberSaveable(stateSaver = kuiverSaver()) {
        mutableStateOf(initialKuiver)
    }

    // Saveable state for scale (zoom level)
    var scale by rememberSaveable { mutableFloatStateOf(1f) }

    // Saveable state for offset (pan position) - save as separate x,y values
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var offset by remember { mutableStateOf(Offset(offsetX, offsetY)) }

    // Regular non-saveable state
    var viewWidth by remember { mutableFloatStateOf(0f) }
    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var canvasHeight by remember { mutableFloatStateOf(0f) }
    var contentOffset by remember { mutableStateOf(Offset.Zero) }
    var layoutedKuiver by remember { mutableStateOf(kuiver) }

    // Sync offset changes to saveable state
    LaunchedEffect(offset) {
        offsetX = offset.x
        offsetY = offset.y
    }

    return createKuiverViewerState(
        kuiver = kuiver,
        getLayoutedKuiver = { layoutedKuiver },
        scale = scale,
        offset = offset,
        viewWidth = viewWidth,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        contentOffset = contentOffset,
        layoutConfig = layoutConfig,
        onKuiverChange = { kuiver = it },
        onLayoutedKuiverChange = { layoutedKuiver = it },
        onScaleChange = { scale = it },
        onOffsetChange = { offset = it },
        onViewWidthChange = { viewWidth = it },
        onCanvasSizeChange = { w, h -> canvasWidth = w; canvasHeight = h },
        onContentOffsetChange = { contentOffset = it }
    )
}

@Composable
private fun createKuiverViewerState(
    kuiver: Kuiver,
    getLayoutedKuiver: () -> Kuiver,
    scale: Float,
    offset: Offset,
    viewWidth: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    contentOffset: Offset,
    layoutConfig: LayoutConfig,
    onKuiverChange: (Kuiver) -> Unit,
    onLayoutedKuiverChange: (Kuiver) -> Unit,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Offset) -> Unit,
    onViewWidthChange: (Float) -> Unit,
    onCanvasSizeChange: (Float, Float) -> Unit,
    onContentOffsetChange: (Offset) -> Unit
): KuiverViewerState {
    // Run layout when dependencies change
    LaunchedEffect(kuiver, layoutConfig, canvasWidth, canvasHeight) {
        val newLayouted = if (canvasWidth > 0f && canvasHeight > 0f) {
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
        onLayoutedKuiverChange(newLayouted)
    }

    val centerGraph = remember(contentOffset, canvasWidth, canvasHeight, viewWidth) {
        {
            // Always get the LATEST layoutedKuiver value
            val currentLayoutedKuiver = getLayoutedKuiver()

            // Calculate centering offset: shift down by half the content height
            // to center in the visible area below overlay content
            val centeringOffset = Offset(
                contentOffset.x / 2f,
                contentOffset.y / 2f
            )

            if (currentLayoutedKuiver.nodes.isEmpty() || canvasWidth == 0f || canvasHeight == 0f) {
                onScaleChange(1f)
                onOffsetChange(centeringOffset)
            } else {
                // Calculate optimal scale to fit graph in canvas using actual node dimensions
                val bounds = currentLayoutedKuiver.nodes.values.calculateNodeBounds()
                val graphWidth = bounds.width
                val graphHeight = bounds.height
                val density = canvasWidth / viewWidth

                // Calculate scale to fit graph in canvas with padding
                val graphWidthPx = graphWidth * density
                val graphHeightPx = graphHeight * density
                val targetScaleX = if (graphWidthPx > 0) (canvasWidth * 0.8f) / graphWidthPx else 1f
                val targetScaleY =
                    if (graphHeightPx > 0) (canvasHeight * 0.8f) / graphHeightPx else 1f
                val targetScale = kotlin.math.min(targetScaleX, targetScaleY).coerceIn(0.1f, 2f)

                onScaleChange(targetScale)
                onOffsetChange(centeringOffset)
            }
        }
    }

    val zoomIn = remember(scale) {
        { onScaleChange((scale * 1.2f).coerceAtMost(5f)) }
    }

    val zoomOut = remember(scale) {
        { onScaleChange((scale / 1.2f).coerceAtLeast(0.1f)) }
    }

    val updateKuiver = remember(onKuiverChange) {
        { newKuiver: Kuiver -> onKuiverChange(newKuiver) }
    }

    val updateViewWidth = remember(onViewWidthChange) {
        { width: Float -> onViewWidthChange(width) }
    }

    val updateCanvasSize = remember(onCanvasSizeChange) {
        { width: Float, height: Float -> onCanvasSizeChange(width, height) }
    }

    val updateContentOffset = remember(onContentOffsetChange) {
        { newContentOffset: Offset -> onContentOffsetChange(newContentOffset) }
    }

    val updateTransform = remember(onScaleChange, onOffsetChange) {
        { newScale: Float, newOffset: Offset ->
            onScaleChange(newScale)
            onOffsetChange(newOffset)
        }
    }

    // Create state holder - snapshots current values for consumers
    // Since lambdas are stable (wrapped in remember), we only recreate
    // when the actual state values that matter for rendering change
    return KuiverViewerState(
        kuiver = kuiver,
        layoutedKuiver = getLayoutedKuiver(),
        scale = scale,
        offset = offset,
        viewWidth = viewWidth,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        contentOffset = contentOffset,
        centerGraph = centerGraph,
        zoomIn = zoomIn,
        zoomOut = zoomOut,
        updateKuiver = updateKuiver,
        updateViewWidth = updateViewWidth,
        updateCanvasSize = updateCanvasSize,
        updateContentOffset = updateContentOffset,
        updateTransform = updateTransform
    )
}
