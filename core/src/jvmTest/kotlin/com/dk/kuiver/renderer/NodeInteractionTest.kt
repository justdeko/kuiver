package com.dk.kuiver.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.dk.kuiver.KuiverViewerState
import com.dk.kuiver.SelectionMode
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.model.NodeDimensions
import com.dk.kuiver.model.buildKuiver
import com.dk.kuiver.ui.EdgeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Node level interaction through the real gesture pipeline: selection, canvas taps and drag.
 *
 * The scene is two 100x100 nodes 150dp either side of the graph center, which the viewer renders
 * around the center of the viewport. Test density is 1, so dp and px are the same number here.
 */
@OptIn(ExperimentalTestApi::class)
class NodeInteractionTest {

    @Test
    fun tappingANodeSelectsItAndTappingAnotherReplacesTheSelection() = runComposeUiTest {
        val state = twoNodeState()
        setContent { Scene(state, config(selectionMode = SelectionMode.SINGLE)) }
        waitForIdle()

        clickAt(nodeA)
        waitForIdle()
        assertEquals(setOf("A"), state.interaction.selectedNodeIds)

        clickAt(nodeB)
        waitForIdle()
        assertEquals(setOf("B"), state.interaction.selectedNodeIds, "single select did not replace")
    }

    @Test
    fun multipleSelectionTogglesEachNode() = runComposeUiTest {
        val state = twoNodeState()
        setContent { Scene(state, config(selectionMode = SelectionMode.MULTIPLE)) }
        waitForIdle()

        clickAt(nodeA)
        clickAt(nodeB)
        waitForIdle()
        assertEquals(setOf("A", "B"), state.interaction.selectedNodeIds)

        clickAt(nodeA)
        waitForIdle()
        assertEquals(setOf("B"), state.interaction.selectedNodeIds, "a second tap did not deselect")
    }

    @Test
    fun tappingTheCanvasDeselectsButTappingANodeDoesNot() = runComposeUiTest {
        val state = twoNodeState()
        var canvasClicks = 0
        var nodeClicks = 0
        setContent {
            Scene(
                state = state,
                config = config(selectionMode = SelectionMode.SINGLE),
                callbacks = KuiverInteractionCallbacks(
                    onNodeClick = { nodeClicks++ },
                    onCanvasClick = { canvasClicks++ }
                )
            )
        }
        waitForIdle()

        clickAt(nodeA)
        waitForIdle()
        assertEquals(1, nodeClicks)
        assertEquals(0, canvasClicks, "a tap on a node reached the canvas")
        assertEquals(setOf("A"), state.interaction.selectedNodeIds)

        // Between and below both nodes, so it hits no node box
        clickAt(Offset(0f, 300f))
        waitForIdle()
        assertEquals(1, canvasClicks, "a tap on empty canvas was not reported")
        assertEquals(emptySet(), state.interaction.selectedNodeIds, "the canvas tap did not deselect")
    }

    @Test
    fun draggingANodeMovesItWithoutPanningTheGraph() = runComposeUiTest {
        val state = twoNodeState()
        var dragEndOffset: DpOffset? = null
        setContent {
            Scene(
                state = state,
                config = config(nodeDragEnabled = true),
                callbacks = KuiverInteractionCallbacks(
                    onNodeDragEnd = { _, offset -> dragEndOffset = offset }
                )
            )
        }
        waitForIdle()

        dragBy(nodeA, Offset(200f, 100f))
        waitForIdle()

        val moved = state.layoutedKuiver.nodes.getValue("A").position
        val travelled = requireNotNull(dragEndOffset) { "the drag never ended" }
        // Slop is spent before the first reported movement, so the exact distance is the one the
        // gesture reported; what matters is that the graph adopted all of it
        assertEquals(
            NODE_A_POSITION + travelled,
            moved,
            "the dragged node did not land where the drag reported"
        )
        assertTrue(travelled.x > 100.dp, "the drag barely moved, reported $travelled")
        assertTrue(travelled.y > 50.dp, "the drag barely moved, reported $travelled")
        assertNull(state.interaction.draggedNodeId, "the drag never finished")
    }

    @Test
    fun draggingANodeLeavesTheOtherNodesWhereTheyWere() = runComposeUiTest {
        val state = twoNodeState()
        var travelled: DpOffset? = null
        setContent {
            Scene(
                state = state,
                config = config(nodeDragEnabled = true),
                callbacks = KuiverInteractionCallbacks(
                    onNodeDragEnd = { _, offset -> travelled = offset }
                )
            )
        }
        waitForIdle()

        dragBy(nodeA, Offset(200f, 0f))
        waitForIdle()

        // Nodes render around the center of the graph bounds, which the move shifts by half of
        // what one node travelled. The viewer takes that back out of the view transform, so the
        // node that was not dragged stays exactly where it was on screen
        val moved = requireNotNull(travelled) { "the drag never ended" }
        assertEquals(
            NODE_B_POSITION,
            state.layoutedKuiver.nodes.getValue("B").position,
            "the node that was not dragged moved in the graph"
        )
        assertEquals(
            moved.x.value / 2f,
            state.offset.x,
            0.5f,
            "the re-centering after the move was not compensated"
        )
    }

    @Test
    fun aDroppedNodeIsNeverRenderedBackWhereItWasPickedUp() = runComposeUiTest {
        val state = twoNodeState()
        val placements = mutableListOf<Offset>()
        setContent {
            Scene(
                state = state,
                config = config(nodeDragEnabled = true),
                nodeContent = { node ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Gray)
                            .onPlaced { if (node.id == "A") placements += it.positionInRoot() }
                    )
                }
            )
        }
        waitForIdle()

        // Drag without letting go, so the release can be watched on its own
        onRoot().performMouseInput {
            moveTo(center + nodeA)
            press()
            moveBy(Offset(120f, 0f))
            moveBy(Offset(120f, 0f))
        }
        waitForIdle()

        val heldAt = placements.last()
        placements.clear()

        onRoot().performMouseInput { release() }
        waitForIdle()

        // Committing the drag hands the position from the interaction state to the graph. Every
        // frame of that handover has to place the node where the pointer left it
        assertTrue(placements.isNotEmpty(), "the drop re-placed nothing, the test proves nothing")
        placements.forEach { placed ->
            assertEquals(
                heldAt.x,
                placed.x,
                1f,
                "the node flashed back to $placed after being dropped at $heldAt"
            )
        }
    }

    @Test
    fun aDragIsReadInGraphSpaceRatherThanScreenSpace() = runComposeUiTest {
        val state = twoNodeState().apply { updateTransform(2f, Offset.Zero) }
        var reported: DpOffset? = null
        setContent {
            Scene(
                state = state,
                config = config(nodeDragEnabled = true),
                callbacks = KuiverInteractionCallbacks(
                    onNodeDragEnd = { _, offset -> reported = offset }
                )
            )
        }
        waitForIdle()

        // The node sits at twice the scale, so its center is twice as far from the viewport center
        dragBy(Offset(NODE_A_POSITION.x.value * 2f, 0f), Offset(400f, 0f))
        waitForIdle()

        val travelled = requireNotNull(reported) { "the drag never ended" }
        // 400 screen px over a 2x scale is 200dp of graph, minus the slop spent getting started
        assertTrue(
            travelled.x.value in 150f..200f,
            "a 400px drag at 2x scale reported ${travelled.x}, expected about 200dp"
        )
    }

    @Test
    fun hoverTracksTheNodeUnderThePointer() = runComposeUiTest {
        val state = twoNodeState()
        setContent { Scene(state, config(hoverEnabled = true)) }
        waitForIdle()

        onRoot().performMouseInput { moveTo(center + nodeA) }
        waitForIdle()
        assertEquals("A", state.interaction.hoveredNodeId)

        onRoot().performMouseInput { moveTo(center + Offset(0f, 300f)) }
        waitForIdle()
        assertNull(state.interaction.hoveredNodeId, "the pointer left the node but hover stuck")
    }

    @Test
    fun interactionIsOffUntilItIsTurnedOn() = runComposeUiTest {
        val state = twoNodeState()
        var canvasClicks = 0
        setContent {
            Scene(
                state = state,
                config = KuiverViewerConfig(fitToContent = false),
                callbacks = KuiverInteractionCallbacks(onCanvasClick = { canvasClicks++ })
            )
        }
        waitForIdle()

        // No selection mode, no node callbacks: nothing claims the tap, so it is the canvas'
        clickAt(nodeA)
        waitForIdle()
        assertEquals(emptySet(), state.interaction.selectedNodeIds)
        assertEquals(1, canvasClicks)

        dragBy(nodeA, Offset(200f, 0f))
        waitForIdle()
        assertEquals(
            NODE_A_POSITION,
            state.layoutedKuiver.nodes.getValue("A").position,
            "a node moved with dragging disabled"
        )
        assertTrue(state.offset.x > 0f, "the drag over a node did not pan the graph")
    }

    @Test
    fun theNodeScopeCarriesSelectionAndHoverIntoTheContent() = runComposeUiTest {
        val state = twoNodeState()
        val selected = mutableMapOf<String, Boolean>()
        val hovered = mutableMapOf<String, Boolean>()
        setContent {
            Scene(
                state = state,
                config = config(selectionMode = SelectionMode.SINGLE, hoverEnabled = true),
                nodeContent = { node ->
                    selected[node.id] = isSelected
                    hovered[node.id] = isHovered
                    Box(Modifier.fillMaxSize().background(Color.Gray))
                }
            )
        }
        waitForIdle()

        onRoot().performMouseInput { moveTo(center + nodeA) }
        clickAt(nodeA)
        waitForIdle()

        assertEquals(true, selected["A"], "the content was not told that A is selected")
        assertEquals(false, selected["B"], "the content was told that B is selected")
        assertEquals(true, hovered["A"], "the content was not told that A is hovered")
    }

    @Test
    fun arrowKeysPanAndPlusMinusZoom() = runComposeUiTest {
        val state = twoNodeState()
        setContent {
            Scene(
                state = state,
                config = KuiverViewerConfig(
                    fitToContent = false,
                    keyboardEnabled = true,
                    keyboardPanStep = 40.dp
                )
            )
        }
        waitForIdle()

        // The viewer takes focus on the first press, the same way clicking into one does
        clickAt(Offset(0f, 250f))
        waitForIdle()

        onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertEquals(-40f, state.offset.x, 0.5f, "right arrow did not pan the view right")

        onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        waitForIdle()
        assertEquals(40f, state.offset.y, 0.5f, "up arrow did not pan the view up")

        onRoot().performKeyInput { pressKey(Key.Equals) }
        waitForIdle()
        assertTrue(state.scale > 1f, "+ did not zoom in, scale is ${state.scale}")

        val zoomedIn = state.scale
        onRoot().performKeyInput { pressKey(Key.Minus) }
        waitForIdle()
        assertTrue(state.scale < zoomedIn, "- did not zoom out, scale is ${state.scale}")
    }

    private fun config(
        selectionMode: SelectionMode = SelectionMode.NONE,
        nodeDragEnabled: Boolean = false,
        hoverEnabled: Boolean = false
    ) = KuiverViewerConfig(
        fitToContent = false,
        selectionMode = selectionMode,
        nodeDragEnabled = nodeDragEnabled,
        hoverEnabled = hoverEnabled
    )

    @Composable
    private fun Scene(
        state: KuiverViewerState,
        config: KuiverViewerConfig,
        callbacks: KuiverInteractionCallbacks = KuiverInteractionCallbacks.None,
        nodeContent: @Composable KuiverNodeScope.(KuiverNode) -> Unit = {
            Box(Modifier.fillMaxSize().background(Color.Gray))
        }
    ) {
        ViewerRenderer(
            state = state,
            config = config,
            callbacks = callbacks,
            anchorRegistry = AnchorPositionRegistry(),
            nodeContent = nodeContent,
            edges = EdgeRendering.Batched { EdgeStyle() }
        )
    }

    /** Clicks at [offset] from the center of the viewport, with the sub-slop wobble of a real one. */
    private fun ComposeUiTest.clickAt(offset: Offset) {
        onRoot().performMouseInput {
            moveTo(center + offset)
            press()
            moveBy(Offset(2f, 1f))
            release()
        }
    }

    /** Presses at [offset] from the center of the viewport and drags [by], in two steps. */
    private fun ComposeUiTest.dragBy(offset: Offset, by: Offset) {
        onRoot().performMouseInput {
            moveTo(center + offset)
            press()
            moveBy(by / 2f)
            moveBy(by / 2f)
            release()
        }
    }

    private fun twoNodeState(): KuiverViewerState {
        val kuiver = twoNodeGraph()
        return KuiverViewerState(kuiver).apply {
            layoutedKuiver = kuiver
            hasFittedInitially = true
        }
    }

    private fun twoNodeGraph(): Kuiver = buildKuiver {
        addNode(
            KuiverNode("A", dimensions = NODE_SIZE, position = NODE_A_POSITION)
        )
        addNode(
            KuiverNode("B", dimensions = NODE_SIZE, position = NODE_B_POSITION)
        )
    }

    private companion object {
        val NODE_SIZE = NodeDimensions(100.dp, 100.dp)
        val NODE_A_POSITION = DpOffset((-150).dp, 0.dp)
        val NODE_B_POSITION = DpOffset(150.dp, 0.dp)

        /** Node centers relative to the viewport center, in px at the test density of 1. */
        val nodeA = Offset(NODE_A_POSITION.x.value, NODE_A_POSITION.y.value)
        val nodeB = Offset(NODE_B_POSITION.x.value, NODE_B_POSITION.y.value)
    }
}
