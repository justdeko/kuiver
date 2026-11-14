package com.dk.kuiver.renderer

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.model.NodeDimensions
import com.dk.kuiver.model.buildKuiver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Nodes and batched edges far outside the viewport are skipped until the view reaches them.
 *
 * The scene is a pair of connected nodes in view and a second pair [FAR] dp to the right. The graph
 * is rendered around the middle of the two pairs, half of [FAR] from each.
 */
@OptIn(ExperimentalTestApi::class)
class ViewportCullingTest {

    @Test
    fun `a node outside the viewport is placed again once the view reaches it`() =
        runComposeUiTest {
            val scene = ViewerScene(graph(farShift = 0.dp), EdgeMode.BATCHED, cullOffscreen = true)
            scene.state.updateTransform(1f, DpOffset(FAR / 2, 0.dp))
            setContent { scene.Content() }
            waitForIdle()
            val initial = scene.nodeCenters.getValue("C")

            runOnIdle { scene.state.layoutedKuiver = graph(farShift = 60.dp) }
            waitForIdle()
            assertEquals(initial, scene.nodeCenters.getValue("C"), "an offscreen node was placed")

            runOnIdle { scene.state.updateTransform(1f, DpOffset(-FAR / 2, 0.dp)) }
            waitForIdle()
            assertNotEquals(initial, scene.nodeCenters.getValue("C"), "the node did not come back")
        }

    @Test
    fun `a batched edge outside the viewport is drawn once the view reaches it`() =
        runComposeUiTest {
            val scene = ViewerScene(graph(farShift = 0.dp), EdgeMode.BATCHED, cullOffscreen = true)
            scene.state.updateTransform(1f, DpOffset(FAR / 2, 0.dp))
            setContent { scene.Content() }
            waitForIdle()

            assertTrue("A->B" in scene.edgeArrowTips, "the visible edge was not drawn")
            assertFalse("C->D" in scene.edgeArrowTips, "an offscreen edge was drawn")

            runOnIdle { scene.state.updateTransform(1f, DpOffset(-FAR / 2, 0.dp)) }
            waitForIdle()
            assertTrue("C->D" in scene.edgeArrowTips, "the edge did not come back")
        }

    private fun graph(farShift: Dp): Kuiver = buildKuiver {
        val size = NodeDimensions(NODE_WIDTH_DP.dp, NODE_HEIGHT_DP.dp)
        addNode(KuiverNode("A", size, DpOffset((-100).dp, 0.dp)))
        addNode(KuiverNode("B", size, DpOffset(100.dp, 0.dp)))
        addNode(KuiverNode("C", size, DpOffset(FAR - 100.dp, farShift)))
        addNode(KuiverNode("D", size, DpOffset(FAR + 100.dp, farShift)))
        addEdge(KuiverEdge("A", "B"))
        addEdge(KuiverEdge("C", "D"))
    }

    private companion object {
        val FAR = 20_000.dp
    }
}
