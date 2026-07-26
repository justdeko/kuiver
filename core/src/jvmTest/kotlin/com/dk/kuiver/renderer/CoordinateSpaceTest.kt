package com.dk.kuiver.renderer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.dk.kuiver.KuiverViewerState
import com.dk.kuiver.assertDpOffsetEquals
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.model.layout.LayoutConfig
import com.dk.kuiver.rememberKuiverViewerState
import com.dk.kuiver.ui.EdgeStyle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The graph coordinate space is dp end to end, so a viewport of the same size in dp has to produce
 * the same graph on a 1x screen and on a 3x one. These render the public viewer at two densities
 * and compare what came out, in dp.
 */
@OptIn(ExperimentalTestApi::class)
class CoordinateSpaceTest {

    @Test
    fun `layout positions are the same at every screen density`() {
        val atOne = renderAt(density = 1f)
        val atThree = renderAt(density = 3f)

        assertEquals(
            atOne.canvasSize,
            atThree.canvasSize,
            "the viewer was not given the same viewport in dp, nothing below would mean anything"
        )
        assertEquals(atOne.layoutPositions.keys, atThree.layoutPositions.keys)
        atOne.layoutPositions.forEach { (nodeId, position) ->
            val other = atThree.layoutPositions.getValue(nodeId)
            assertDpOffsetEquals(position, other, POSITION_TOLERANCE, "$nodeId was laid out elsewhere")
        }
    }

    @Test
    fun `the graph is fitted and placed the same way at every screen density`() {
        val atOne = renderAt(density = 1f)
        val atThree = renderAt(density = 3f)

        assertEquals(
            atOne.scale,
            atThree.scale,
            POSITION_TOLERANCE.value,
            "the same graph in the same viewport was fitted to a different zoom"
        )
        assertEquals(atOne.nodeCenters.keys, atThree.nodeCenters.keys)
        atOne.nodeCenters.forEach { (nodeId, center) ->
            val other = atThree.nodeCenters.getValue(nodeId)
            assertDpOffsetEquals(center, other, PLACEMENT_TOLERANCE, "$nodeId was placed elsewhere")
        }
    }

    /**
     * Renders the graph in a viewport of a fixed size in dp at the given density, and reports what
     * the layout and the renderer made of it.
     *
     * @param density screen density to render at
     * @return the laid out positions and the placements, both in dp
     */
    private fun renderAt(density: Float): Rendered {
        val nodeCenters = mutableMapOf<String, DpOffset>()
        lateinit var state: KuiverViewerState
        var layoutPositions: Map<String, DpOffset> = emptyMap()
        var canvasSize = DpSize.Zero
        var scale = 0f

        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(density)) {
                    state = rememberKuiverViewerState(chainGraph(), LayoutConfig.Hierarchical())
                    Box(Modifier.size(VIEWPORT.dp)) {
                        // Default config, so the graph is fitted to the viewport once it is measured
                        KuiverViewer(
                            state = state,
                            nodeContent = { node ->
                                Box(
                                    Modifier
                                        .size(NODE_WIDTH.dp, NODE_HEIGHT.dp)
                                        .onPlaced { coordinates ->
                                            val center = coordinates.positionInRoot() + Offset(
                                                coordinates.size.width / 2f,
                                                coordinates.size.height / 2f
                                            )
                                            // px back to dp, the space the two runs share
                                            nodeCenters[node.id] = DpOffset(
                                                (center.x / density).dp,
                                                (center.y / density).dp
                                            )
                                        }
                                )
                            },
                            edgeStyle = { EdgeStyle() }
                        )
                    }
                }
            }
            waitUntil { state.hasFittedInitially }
            waitForIdle()
            layoutPositions = state.layoutedKuiver.nodes.mapValues { (_, node) -> node.position }
            canvasSize = DpSize(state.canvasWidth, state.canvasHeight)
            scale = state.scale
        }

        return Rendered(layoutPositions, nodeCenters.toMap(), canvasSize, scale)
    }

    /** What one render produced, all in dp. */
    private data class Rendered(
        val layoutPositions: Map<String, DpOffset>,
        val nodeCenters: Map<String, DpOffset>,
        val canvasSize: DpSize,
        val scale: Float
    )

    private fun chainGraph() = Kuiver().apply {
        addNode(KuiverNode("A"))
        addNode(KuiverNode("B"))
        addNode(KuiverNode("C"))
        addEdge(KuiverEdge("A", "B"))
        addEdge(KuiverEdge("A", "C"))
    }

    private companion object {
        /** Node content size in dp */
        const val NODE_WIDTH = 80f
        const val NODE_HEIGHT = 40f

        /** Viewport the viewer is given, in dp. Small enough to fit the test window at 3x too */
        const val VIEWPORT = 200f

        /** Layout arithmetic is the same at both densities, so only float error is allowed for */
        val POSITION_TOLERANCE: Dp = 0.01f.dp

        /** Placement rounds to whole pixels, which is a fraction of a dp on a dense screen */
        val PLACEMENT_TOLERANCE: Dp = 1.dp
    }
}
