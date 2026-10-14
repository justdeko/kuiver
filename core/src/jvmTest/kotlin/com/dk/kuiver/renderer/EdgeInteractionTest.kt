package com.dk.kuiver.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.dk.kuiver.KuiverViewerState
import com.dk.kuiver.SelectionMode
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.model.NodeDimensions
import com.dk.kuiver.model.buildKuiver
import com.dk.kuiver.ui.EdgeCanvas
import com.dk.kuiver.ui.EdgePathFactory
import com.dk.kuiver.ui.EdgeStyle
import com.dk.kuiver.ui.RightAngleRouting
import com.dk.kuiver.ui.StyledEdgeContent
import com.dk.kuiver.ui.boundingRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class EdgeInteractionTest {

    @Test
    fun tappingAnEdgeSelectsItInBothRenderingModes() {
        listOf(EdgeMode.BATCHED, EdgeMode.BUILT_IN).forEach { mode ->
            runComposeUiTest {
                val state = state(graph())
                setContent { Scene(state, config(SelectionMode.SINGLE), edgeMode = mode) }
                waitForIdle()

                clickAt(Offset(0f, 4f))
                waitForIdle()
                assertEquals(setOf("A->B"), state.interaction.selectedEdgeIds, "in $mode")

                clickAt(Offset(0f, 200f))
                waitForIdle()
                assertEquals(emptySet(), state.interaction.selectedEdgeIds, "canvas tap in $mode")
            }
        }
    }

    @Test
    fun parallelEdgesAreTappedApartAlongTheirArcs() = runComposeUiTest {
        val state = state(graph(parallel = true))
        setContent { Scene(state, config(SelectionMode.SINGLE)) }
        waitForIdle()

        // Middle of each arc, about 15px off the center line
        clickAt(Offset(0f, -15f))
        waitForIdle()
        assertEquals(setOf("A->B"), state.interaction.selectedEdgeIds)

        clickAt(Offset(0f, 15f))
        waitForIdle()
        assertEquals(setOf("retry"), state.interaction.selectedEdgeIds)
    }

    @Test
    fun edgeClicksAreReportedAndDoNotReachTheCanvas() = runComposeUiTest {
        val state = state(graph())
        val clicked = mutableListOf<String>()
        var canvasClicks = 0
        setContent {
            Scene(
                state = state,
                config = config(),
                callbacks = KuiverInteractionCallbacks(
                    onEdgeClick = { clicked += it.id },
                    onCanvasClick = { canvasClicks++ }
                )
            )
        }
        waitForIdle()

        clickAt(Offset.Zero)
        waitForIdle()
        assertEquals(listOf("A->B"), clicked)
        assertEquals(0, canvasClicks, "an edge tap reached the canvas")
        assertEquals(emptySet(), state.interaction.selectedEdgeIds, "selected with selection off")

        clickAt(Offset(0f, 200f))
        waitForIdle()
        assertEquals(1, canvasClicks)
    }

    @Test
    fun selectingAnEdgeDropsTheSelectedNodes() = runComposeUiTest {
        val state = state(graph())
        setContent {
            Scene(state, config(SelectionMode.SINGLE).copy(selectionMode = SelectionMode.SINGLE))
        }
        waitForIdle()

        clickAt(nodeA)
        waitForIdle()
        assertEquals(setOf("A"), state.interaction.selectedNodeIds)

        clickAt(Offset.Zero)
        waitForIdle()
        assertEquals(emptySet(), state.interaction.selectedNodeIds)
        assertEquals(setOf("A->B"), state.interaction.selectedEdgeIds)
    }

    @Test
    fun aZoomedAndPannedEdgeIsHitWhereItIsDrawn() = runComposeUiTest {
        val state = state(graph()).apply { updateTransform(2f, DpOffset(0.dp, 100.dp)) }
        setContent { Scene(state, config(SelectionMode.SINGLE)) }
        waitForIdle()

        // Where the edge used to be before the pan
        clickAt(Offset.Zero)
        waitForIdle()
        assertEquals(emptySet(), state.interaction.selectedEdgeIds)

        clickAt(Offset(150f, 100f))
        waitForIdle()
        assertEquals(setOf("A->B"), state.interaction.selectedEdgeIds)
    }

    @Test
    fun customEdgesAreHitAlongThePathTheyReport() = runComposeUiTest {
        val state = state(graph())
        setContent {
            Scene(
                state = state,
                config = config(SelectionMode.SINGLE),
                edgeMode = null,
                edgeContent = { _, from, to ->
                    val density = LocalDensity.current
                    // Down 100px, across, and back up
                    val path = remember(from, to, density) {
                        EdgePathFactory.createRightAnglePath(
                            from,
                            to,
                            RightAngleRouting.VERTICAL_HORIZONTAL_VERTICAL,
                            density
                        ).copy(waypoints = listOf(from + Offset(0f, 100f), to + Offset(0f, 100f)))
                    }
                    EdgeCanvas(remember(path, density) { path.boundingRect(density) }, path) {}
                }
            )
        }
        waitForIdle()

        clickAt(Offset.Zero)
        waitForIdle()
        assertEquals(emptySet(), state.interaction.selectedEdgeIds, "hit the straight line")

        clickAt(Offset(0f, 100f))
        waitForIdle()
        assertEquals(setOf("A->B"), state.interaction.selectedEdgeIds)
    }

    @Test
    fun customEdgesWithoutAHitPathAreNotHit() = runComposeUiTest {
        val state = state(graph())
        setContent {
            Scene(
                state = state,
                config = config(SelectionMode.SINGLE),
                edgeMode = null,
                edgeContent = { _, from, to ->
                    val density = LocalDensity.current
                    val path = remember(from, to, density) {
                        EdgePathFactory.createStraightPath(from, to, density)
                    }
                    EdgeCanvas(remember(path, density) { path.boundingRect(density) }) {}
                }
            )
        }
        waitForIdle()

        clickAt(Offset.Zero)
        waitForIdle()
        assertEquals(emptySet(), state.interaction.selectedEdgeIds)
    }

    @Test
    fun hoverTracksTheEdgeUnderThePointerButNotUnderANode() = runComposeUiTest {
        val state = state(graph())
        setContent { Scene(state, config().copy(edgeHoverEnabled = true, hoverEnabled = true)) }
        waitForIdle()

        onRoot().performMouseInput { moveTo(center) }
        waitForIdle()
        assertEquals("A->B", state.interaction.hoveredEdgeId)

        onRoot().performMouseInput { moveTo(center + nodeA) }
        waitForIdle()
        assertNull(state.interaction.hoveredEdgeId, "an edge was hovered through a node")

        onRoot().performMouseInput { moveTo(center + Offset(0f, 200f)) }
        waitForIdle()
        assertNull(state.interaction.hoveredEdgeId)
    }

    @Test
    fun turningHoverOffClearsTheHoveredNodeAndEdge() = runComposeUiTest {
        val state = state(graph())
        var hoverEnabled by mutableStateOf(true)
        setContent {
            Scene(
                state,
                config().copy(edgeHoverEnabled = hoverEnabled, hoverEnabled = hoverEnabled)
            )
        }
        waitForIdle()

        onRoot().performMouseInput { moveTo(center) }
        waitForIdle()
        assertEquals("A->B", state.interaction.hoveredEdgeId)

        hoverEnabled = false
        waitForIdle()
        assertNull(state.interaction.hoveredEdgeId)

        hoverEnabled = true
        waitForIdle()
        onRoot().performMouseInput { moveTo(center + nodeA) }
        waitForIdle()
        assertEquals("A", state.interaction.hoveredNodeId)

        hoverEnabled = false
        waitForIdle()
        assertNull(state.interaction.hoveredNodeId)
    }

    @Test
    fun theEdgeScopeCarriesSelectionAndHoverIntoTheContent() = runComposeUiTest {
        val state = state(graph(parallel = true))
        val selected = mutableMapOf<String, Boolean>()
        val hovered = mutableMapOf<String, Boolean>()
        setContent {
            Scene(
                state = state,
                config = config(SelectionMode.SINGLE).copy(edgeHoverEnabled = true),
                edgeMode = null,
                edgeContent = { edge, from, to ->
                    selected[edge.id] = isSelected
                    hovered[edge.id] = isHovered
                    StyledEdgeContent(edge, from, to)
                }
            )
        }
        waitForIdle()

        onRoot().performMouseInput { moveTo(center + Offset(0f, 15f)) }
        clickAt(Offset(0f, 15f))
        waitForIdle()

        assertEquals(true, selected["retry"])
        assertEquals(false, selected["A->B"])
        assertEquals(true, hovered["retry"])
    }

    @Test
    fun removingAnEdgeForgetsItsSelectionAndHover() = runComposeUiTest {
        val state = state(graph())
        setContent { Scene(state, config(SelectionMode.SINGLE).copy(edgeHoverEnabled = true)) }
        waitForIdle()

        onRoot().performMouseInput { moveTo(center) }
        clickAt(Offset.Zero)
        waitForIdle()
        assertEquals(setOf("A->B"), state.interaction.selectedEdgeIds)

        state.layoutedKuiver = state.layoutedKuiver.withoutEdge("A->B")
        waitForIdle()
        assertEquals(emptySet(), state.interaction.selectedEdgeIds)
        assertNull(state.interaction.hoveredEdgeId)
    }

    private fun config(edgeSelectionMode: SelectionMode = SelectionMode.NONE) = KuiverViewerConfig(
        fitToContent = false,
        edgeSelectionMode = edgeSelectionMode
    )

    @Composable
    private fun Scene(
        state: KuiverViewerState,
        config: KuiverViewerConfig,
        callbacks: KuiverInteractionCallbacks = KuiverInteractionCallbacks.None,
        edgeMode: EdgeMode? = EdgeMode.BATCHED,
        edgeContent: @Composable KuiverEdgeScope.(KuiverEdge, Offset, Offset) -> Unit = { _, _, _ -> }
    ) {
        ViewerRenderer(
            state = state,
            config = config,
            callbacks = callbacks,
            anchorRegistry = remember { AnchorPositionRegistry() },
            nodeContent = { Box(Modifier.fillMaxSize().background(Color.Gray)) },
            edges = when (edgeMode) {
                EdgeMode.BATCHED -> EdgeRendering.Batched { EdgeStyle() }
                EdgeMode.BUILT_IN -> EdgeRendering.PerEdge { edge, from, to ->
                    StyledEdgeContent(edge, from, to)
                }

                else -> EdgeRendering.PerEdge(edgeContent)
            }
        )
    }

    /** Clicks at [offset] from the center of the viewport, with the sub-slop wobble of a real one. */
    private fun ComposeUiTest.clickAt(offset: Offset) {
        onRoot().performMouseInput {
            moveTo(center + offset)
            press()
            moveBy(Offset(1f, 0f))
            release()
        }
    }

    private fun state(kuiver: Kuiver) = KuiverViewerState(kuiver).apply {
        layoutedKuiver = kuiver
        hasFittedInitially = true
    }

    private fun graph(parallel: Boolean = false): Kuiver = buildKuiver {
        addNode(KuiverNode("A", dimensions = NODE_SIZE, position = DpOffset((-150).dp, 0.dp)))
        addNode(KuiverNode("B", dimensions = NODE_SIZE, position = DpOffset(150.dp, 0.dp)))
        addEdge(KuiverEdge("A", "B"))
        if (parallel) addEdge(KuiverEdge("A", "B", id = "retry"))
    }

    private companion object {
        val NODE_SIZE = NodeDimensions(100.dp, 100.dp)

        // Center of node A relative to the viewport center
        val nodeA = Offset(-150f, 0f)
    }
}
