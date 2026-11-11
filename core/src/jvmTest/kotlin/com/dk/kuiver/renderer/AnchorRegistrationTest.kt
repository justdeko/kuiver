package com.dk.kuiver.renderer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dk.kuiver.KuiverViewerState
import com.dk.kuiver.model.AnchorOffset
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.model.NodeDimensions
import com.dk.kuiver.model.buildKuiver
import com.dk.kuiver.ui.EdgeStyle
import com.dk.kuiver.ui.KuiverAnchor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Anchors are declared inside node content, which the viewer subcomposes. They need the registry
 * from the composition local and a position relative to their node.
 */
@OptIn(ExperimentalTestApi::class)
class AnchorRegistrationTest {

    @Test
    fun `an anchor in node content registers its offset within the node`() = runComposeUiTest {
        lateinit var registry: AnchorPositionRegistry
        val graph = buildKuiver {
            addNode(KuiverNode("A", dimensions = NodeDimensions(NODE_WIDTH.dp, NODE_HEIGHT.dp)))
            addNode(KuiverNode("B", dimensions = NodeDimensions(NODE_WIDTH.dp, NODE_HEIGHT.dp)))
            addEdge(KuiverEdge("A", "B", fromAnchor = "right", toAnchor = "right"))
        }
        val state = KuiverViewerState(graph).apply {
            layoutedKuiver = graph
            hasFittedInitially = true
        }

        setContent {
            registry = remember { AnchorPositionRegistry() }
            ViewerRenderer(
                state = state,
                config = KuiverViewerConfig(fitToContent = false),
                anchorRegistry = registry,
                nodeContent = { node ->
                    Box(Modifier.size(NODE_WIDTH.dp, NODE_HEIGHT.dp)) {
                        KuiverAnchor(
                            anchorId = "right",
                            nodeId = node.id,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Box(Modifier.size(ANCHOR_SIZE.dp))
                        }
                    }
                },
                edges = EdgeRendering.Batched { EdgeStyle() }
            )
        }
        waitForIdle()

        // Centered on the right edge of the node: (80 - 8 / 2, 40 / 2)
        assertEquals(
            AnchorOffset((NODE_WIDTH - ANCHOR_SIZE / 2).dp, (NODE_HEIGHT / 2).dp),
            registry.getAnchorOffset("A", "right"),
            "the anchor did not register where it sits within its node"
        )
    }

    @Test
    fun `a nested anchor registers its offset from the node and not from parent`() =
        runComposeUiTest {
            lateinit var registry: AnchorPositionRegistry
            val graph = buildKuiver {
                addNode(KuiverNode("A", dimensions = NodeDimensions(NODE_WIDTH.dp, NODE_HEIGHT.dp)))
            }
            val state = KuiverViewerState(graph).apply {
                layoutedKuiver = graph
                hasFittedInitially = true
            }

            setContent {
                registry = remember { AnchorPositionRegistry() }
                ViewerRenderer(
                    state = state,
                    config = KuiverViewerConfig(fitToContent = false),
                    anchorRegistry = registry,
                    nodeContent = { node ->
                        Box(Modifier.size(NODE_WIDTH.dp, NODE_HEIGHT.dp).padding(PADDING.dp)) {
                            KuiverAnchor(anchorId = "padded", nodeId = node.id) {
                                Box(Modifier.size(ANCHOR_SIZE.dp))
                            }
                            Box(Modifier.align(Alignment.BottomEnd).size(20.dp)) {
                                KuiverAnchor(
                                    anchorId = "nested",
                                    nodeId = node.id,
                                    modifier = Modifier.align(Alignment.BottomEnd)
                                ) {
                                    Box(Modifier.size(ANCHOR_SIZE.dp))
                                }
                            }
                        }
                    },
                    edges = EdgeRendering.Batched { EdgeStyle() }
                )
            }
            waitForIdle()

            // Top start of the padded area: (10 + 8 / 2, 10 + 8 / 2)
            assertEquals(
                AnchorOffset((PADDING + ANCHOR_SIZE / 2).dp, (PADDING + ANCHOR_SIZE / 2).dp),
                registry.getAnchorOffset("A", "padded"),
                "the padding of the node content was left out"
            )
            // Bottom end of the padded area: (80 - 10 - 8 / 2, 40 - 10 - 8 / 2)
            assertEquals(
                AnchorOffset(
                    (NODE_WIDTH - PADDING - ANCHOR_SIZE / 2).dp,
                    (NODE_HEIGHT - PADDING - ANCHOR_SIZE / 2).dp
                ),
                registry.getAnchorOffset("A", "nested"),
                "the anchor registered relative to its own parent"
            )
        }

    @Test
    fun `anchor that leaves node content is unregistered`() = runComposeUiTest {
        lateinit var registry: AnchorPositionRegistry
        var showAnchor by mutableStateOf(true)
        val graph = buildKuiver {
            addNode(KuiverNode("A", dimensions = NodeDimensions(NODE_WIDTH.dp, NODE_HEIGHT.dp)))
        }
        val state = KuiverViewerState(graph).apply {
            layoutedKuiver = graph
            hasFittedInitially = true
        }

        setContent {
            registry = remember { AnchorPositionRegistry() }
            ViewerRenderer(
                state = state,
                config = KuiverViewerConfig(fitToContent = false),
                anchorRegistry = registry,
                nodeContent = { node ->
                    Box(Modifier.size(NODE_WIDTH.dp, NODE_HEIGHT.dp)) {
                        if (showAnchor) KuiverAnchor(anchorId = "right", nodeId = node.id)
                    }
                },
                edges = EdgeRendering.Batched { EdgeStyle() }
            )
        }
        waitForIdle()
        assertNotNull(registry.getAnchorOffset("A", "right"))

        showAnchor = false
        waitForIdle()
        assertNull(registry.getAnchorOffset("A", "right"), "a removed anchor stayed registered")
    }

    private companion object {
        /** Sizes in dp, which are also px at the test density of 1 */
        const val NODE_WIDTH = 80f
        const val NODE_HEIGHT = 40f
        const val ANCHOR_SIZE = 8f
        const val PADDING = 10f
    }
}
