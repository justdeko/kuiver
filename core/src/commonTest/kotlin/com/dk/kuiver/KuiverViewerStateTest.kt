package com.dk.kuiver

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.model.NodeDimensions
import com.dk.kuiver.renderer.KuiverViewerConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KuiverViewerStateTest {

    private fun wideGraph(measured: Boolean = true) = Kuiver().apply {
        val dimensions = if (measured) NodeDimensions(100.dp, 100.dp) else null
        addNode(KuiverNode(id = "A", dimensions = dimensions, position = DpOffset(-350.dp, 0.dp)))
        addNode(KuiverNode(id = "B", dimensions = dimensions, position = DpOffset(350.dp, 0.dp)))
    }

    private fun stateWith(
        viewerConfig: KuiverViewerConfig,
        graph: Kuiver = wideGraph()
    ) = KuiverViewerState(graph).apply {
        layoutedKuiver = graph
        canvasWidth = 1000.dp
        canvasHeight = 1000.dp
        config = viewerConfig
    }

    @Test
    fun `centerGraph fits content using configured contentPadding`() {
        // Width is the constraint: (1000 * 0.8) / 800 = 1.0
        val state = stateWith(KuiverViewerConfig(contentPadding = 0.8f))
        state.centerGraph(animated = false)
        assertEquals(1f, state.scale, 0.001f)

        // Halving the padding halves the fitted scale: (1000 * 0.4) / 800 = 0.5
        val tighter = stateWith(KuiverViewerConfig(contentPadding = 0.4f))
        tighter.centerGraph(animated = false)
        assertEquals(0.5f, tighter.scale, 0.001f)
    }

    @Test
    fun `centerGraph clamps the fitted scale to maxScale`() {
        // Unclamped this would fit at (1000 * 0.8) / 800 = 1.0
        val state = stateWith(KuiverViewerConfig(minScale = 0.1f, maxScale = 0.5f))
        state.centerGraph(animated = false)
        assertEquals(0.5f, state.scale, 0.001f)
    }

    @Test
    fun `centerGraph clamps the fitted scale to minScale`() {
        val state = stateWith(KuiverViewerConfig(minScale = 2f, maxScale = 5f))
        state.centerGraph(animated = false)
        assertEquals(2f, state.scale, 0.001f)
    }

    @Test
    fun `centerGraph clamps to minScale when there is nothing to fit`() {
        val state = stateWith(KuiverViewerConfig(minScale = 1.5f, maxScale = 5f), Kuiver())
        state.centerGraph(animated = false)
        assertEquals(1.5f, state.scale, 0.001f)
    }

    @Test
    fun `centerGraph animated requests an animation instead of snapping`() {
        val state = stateWith(KuiverViewerConfig(contentPadding = 0.4f))
        state.centerGraph(animated = true)
        assertEquals(1f, state.scale, 0.001f, "scale should not change until the animation runs")
        assertEquals(0.5f, state.pendingAnimation?.scale ?: 0f, 0.001f)
    }

    @Test
    fun `zoomIn uses the configured step and clamps to maxScale`() {
        val state = stateWith(KuiverViewerConfig(maxScale = 3f, zoomStep = 2f))
        state.zoomIn()
        assertEquals(2f, state.pendingAnimation?.scale ?: 0f, 0.001f)

        state.scale = 2f
        state.zoomIn()
        assertEquals(3f, state.pendingAnimation?.scale ?: 0f, 0.001f)
    }

    @Test
    fun `zoomOut uses the configured step and clamps to minScale`() {
        val state = stateWith(KuiverViewerConfig(minScale = 0.5f, zoomStep = 2f))
        state.zoomOut()
        assertEquals(0.5f, state.pendingAnimation?.scale ?: 0f, 0.001f)

        state.scale = 0.5f
        state.zoomOut()
        assertEquals(0.5f, state.pendingAnimation?.scale ?: 0f, 0.001f)
    }

    @Test
    fun `applyInitialFit centers the graph when fitToContent is enabled`() {
        val state = stateWith(KuiverViewerConfig(fitToContent = true, contentPadding = 0.8f))
        state.applyInitialFit(state.canvasWidth, state.canvasHeight)

        assertTrue(state.hasFittedInitially)
        assertEquals(1f, state.scale, 0.001f)
    }

    @Test
    fun `applyInitialFit leaves the transform alone when fitToContent is disabled`() {
        val state = stateWith(KuiverViewerConfig(fitToContent = false))
        state.updateTransform(scale = 1.75f, offset = Offset(30f, 40f))

        state.applyInitialFit(state.canvasWidth, state.canvasHeight)

        assertTrue(state.hasFittedInitially, "content must still become visible")
        assertEquals(1.75f, state.scale, 0.001f)
        assertEquals(Offset(30f, 40f), state.offset)
        assertNull(state.pendingAnimation)
    }

    @Test
    fun `applyInitialFit waits for the canvas and for measured nodes`() {
        val unmeasured = wideGraph(measured = false)
        val state = stateWith(KuiverViewerConfig(), unmeasured)

        state.applyInitialFit(0.dp, 0.dp)
        assertFalse(state.hasFittedInitially, "canvas has not been measured yet")

        state.applyInitialFit(state.canvasWidth, state.canvasHeight)
        assertFalse(state.hasFittedInitially, "nodes have not been measured yet")
        assertEquals(1f, state.scale, 0.001f)
    }
}
