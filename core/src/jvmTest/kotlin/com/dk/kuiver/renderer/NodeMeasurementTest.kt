package com.dk.kuiver.renderer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dk.kuiver.KuiverViewerState
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.model.NodeDimensions
import com.dk.kuiver.model.buildKuiver
import com.dk.kuiver.model.layout.LayoutConfig
import com.dk.kuiver.rememberKuiverViewerState
import com.dk.kuiver.ui.EdgeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The viewer measures nodes while it renders them, so these run the public composable and read the
 * sizes back off the state. Layout runs off the main thread, hence waiting for the sizes to land
 * instead of a single frame.
 */
@OptIn(ExperimentalTestApi::class)
class NodeMeasurementTest {

    @Test
    fun `an auto sized node is laid out and placed at the size of its content`() = runComposeUiTest {
        lateinit var state: KuiverViewerState
        val placedSizes = mutableMapOf<String, IntSize>()

        setContent {
            state = rememberKuiverViewerState(twoNodeGraph(), LayoutConfig.Hierarchical())
            TestViewer(state) { node ->
                Box(
                    Modifier
                        .size(NODE_WIDTH.dp, NODE_HEIGHT.dp)
                        .onPlaced { placedSizes[node.id] = it.size }
                )
            }
        }
        awaitDimensions(state, "A")

        assertEquals(
            NodeDimensions(NODE_WIDTH.dp, NODE_HEIGHT.dp),
            state.layoutedKuiver.nodes.getValue("A").dimensions,
            "the measured size never reached the layout pass"
        )
        assertEquals(
            IntSize(NODE_WIDTH.toInt(), NODE_HEIGHT.toInt()),
            placedSizes["A"],
            "the node was not placed at the size it was measured at"
        )
        assertNull(
            state.kuiver.nodes.getValue("A").dimensions,
            "measured dimensions leaked into the caller's graph"
        )
    }

    @Test
    fun `content of a node with explicit dimensions is held to them`() = runComposeUiTest {
        lateinit var state: KuiverViewerState
        val placedSizes = mutableMapOf<String, IntSize>()
        val explicit = NodeDimensions(200.dp, 120.dp)

        setContent {
            state = rememberKuiverViewerState(
                twoNodeGraph(dimensions = explicit),
                LayoutConfig.Hierarchical()
            )
            // Content that fills its node, so it reports the size of the node box it sits in
            TestViewer(state) { node ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .onPlaced { placedSizes[node.id] = it.size }
                )
            }
        }
        waitUntil { placedSizes.containsKey("A") }

        assertEquals(explicit, state.layoutedKuiver.nodes.getValue("A").dimensions)
        assertEquals(
            IntSize(explicit.width.value.toInt(), explicit.height.value.toInt()),
            placedSizes["A"],
            "the node box did not hold its content to the explicit dimensions"
        )
    }

    @Test
    fun `content that grows is measured again and the graph is laid out anew`() = runComposeUiTest {
        lateinit var state: KuiverViewerState
        var grown by mutableStateOf(false)
        val nodePositions = mutableMapOf<String, Offset>()

        setContent {
            state = rememberKuiverViewerState(twoNodeGraph(), LayoutConfig.Hierarchical())
            TestViewer(state) { node ->
                val width = if (grown) NODE_WIDTH * 3 else NODE_WIDTH
                Box(
                    Modifier
                        .size(width.dp, NODE_HEIGHT.dp)
                        .onPlaced { nodePositions[node.id] = it.positionInRoot() }
                )
            }
        }
        awaitDimensions(state, "A")
        val laidOutAt = state.layoutedKuiver.nodes.getValue("A").position
        val placedAt = nodePositions.getValue("A")

        grown = true
        waitUntil {
            state.layoutedKuiver.nodes.getValue("A").dimensions?.width == (NODE_WIDTH * 3).dp
        }

        assertEquals(
            NodeDimensions((NODE_WIDTH * 3).dp, NODE_HEIGHT.dp),
            state.layoutedKuiver.nodes.getValue("A").dimensions,
            "the node kept the dimensions it was first measured at"
        )
        assertTrue(
            state.layoutedKuiver.nodes.getValue("A").position != laidOutAt ||
                    nodePositions.getValue("A") != placedAt,
            "the wider nodes were neither laid out nor placed again"
        )
    }

    @Test
    fun `measured nodes are fitted and placed exactly where explicit ones are`() {
        // The fit reads node dimensions off the laid out graph, so a measured size has to land there
        // in the same shape an explicit one does
        val explicitPlacement = placeInFittedViewport(NodeDimensions(NODE_WIDTH.dp, NODE_HEIGHT.dp))
        val measuredPlacement = placeInFittedViewport(dimensions = null)

        assertEquals(
            explicitPlacement,
            measuredPlacement,
            "measured nodes were fitted differently from nodes of the same explicit size"
        )
    }

    /**
     * Renders the two node graph in a fitted viewport of its own and reports where the nodes ended
     * up, so two runs can be compared.
     *
     * @param dimensions explicit node dimensions, or null to let the viewer measure the content
     * @return center of each node in root coordinates, keyed by node id
     */
    private fun placeInFittedViewport(dimensions: NodeDimensions?): Map<String, Offset> {
        val nodeCenters = mutableMapOf<String, Offset>()
        runComposeUiTest {
            lateinit var state: KuiverViewerState
            setContent {
                state = rememberKuiverViewerState(
                    twoNodeGraph(dimensions),
                    LayoutConfig.Hierarchical()
                )
                Box(Modifier.size(VIEWPORT.dp)) {
                    // Default config, so this fits to content once the sizes are laid out
                    KuiverViewer(
                        state = state,
                        nodeContent = { node ->
                            Box(
                                Modifier
                                    .size(NODE_WIDTH.dp, NODE_HEIGHT.dp)
                                    .onPlaced { coordinates ->
                                        nodeCenters[node.id] = coordinates.positionInRoot() + Offset(
                                            coordinates.size.width / 2f,
                                            coordinates.size.height / 2f
                                        )
                                    }
                            )
                        },
                        edgeStyle = { EdgeStyle() }
                    )
                }
            }
            waitUntil { state.hasFittedInitially }
            waitForIdle()
        }
        return nodeCenters.toMap()
    }

    @Test
    fun `node content is composed once per layout generation`() = runComposeUiTest {
        lateinit var state: KuiverViewerState
        val compositions = mutableMapOf<String, Int>()

        setContent {
            state = rememberKuiverViewerState(twoNodeGraph(), LayoutConfig.Hierarchical())
            TestViewer(state) { node ->
                compositions[node.id] = (compositions[node.id] ?: 0) + 1
                Box(Modifier.size(NODE_WIDTH.dp, NODE_HEIGHT.dp))
            }
        }
        awaitDimensions(state, "A")
        waitForIdle()

        // Two generations reach the renderer: the graph as given, then the laid out one. Measuring
        // in a pass of its own used to add a third composition of every node.
        assertEquals(mapOf("A" to 2, "B" to 2), compositions.toMap())
    }

    @Test
    fun `a node added after the first frame is measured too`() = runComposeUiTest {
        lateinit var state: KuiverViewerState

        setContent {
            state = rememberKuiverViewerState(twoNodeGraph(), LayoutConfig.Hierarchical())
            TestViewer(state) { Box(Modifier.size(NODE_WIDTH.dp, NODE_HEIGHT.dp)) }
        }
        awaitDimensions(state, "A")

        runOnIdle {
            state.updateKuiver(
                state.kuiver.rebuild {
                    addNode(KuiverNode("C"))
                    addEdge(KuiverEdge("B", "C"))
                }
            )
        }
        awaitDimensions(state, "C")

        assertEquals(
            NodeDimensions(NODE_WIDTH.dp, NODE_HEIGHT.dp),
            state.layoutedKuiver.nodes.getValue("C").dimensions,
            "the node added after the first frame was never measured"
        )
    }

    /**
     * Waits until the measured size of [nodeId] has been through a layout pass.
     *
     * @param state the viewer state under test
     * @param nodeId id of the node to wait for
     */
    private fun ComposeUiTest.awaitDimensions(state: KuiverViewerState, nodeId: String) {
        waitUntil { state.layoutedKuiver.nodes[nodeId]?.dimensions != null }
    }

    /**
     * Renders [nodeContent] through the public viewer, with edges that compose nothing of their own.
     *
     * @param state the viewer state under test
     * @param nodeContent composable content of a node
     */
    @Composable
    private fun TestViewer(
        state: KuiverViewerState,
        nodeContent: @Composable KuiverNodeScope.(KuiverNode) -> Unit
    ) {
        KuiverViewer(
            state = state,
            config = KuiverViewerConfig(fitToContent = false),
            nodeContent = nodeContent,
            edgeStyle = { EdgeStyle() }
        )
    }

    private fun twoNodeGraph(dimensions: NodeDimensions? = null) = buildKuiver {
        addNode(KuiverNode("A", dimensions = dimensions))
        addNode(KuiverNode("B", dimensions = dimensions))
        addEdge(KuiverEdge("A", "B"))
    }

    private companion object {
        /** Node content size in dp, which is also px at the test density of 1 */
        const val NODE_WIDTH = 80f
        const val NODE_HEIGHT = 40f

        /** Viewport the fit test gives the viewer, in dp */
        const val VIEWPORT = 400f
    }
}
