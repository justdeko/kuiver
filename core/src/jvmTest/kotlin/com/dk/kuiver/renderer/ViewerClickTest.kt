package com.dk.kuiver.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.dk.kuiver.KuiverViewerState
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.model.NodeDimensions
import com.dk.kuiver.model.buildKuiver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ViewerClickTest {

    private fun singleNodeState(): KuiverViewerState {
        val kuiver = buildKuiver {
            addNode(KuiverNode(id = "a", dimensions = NodeDimensions(120.dp, 120.dp)))
        }
        return KuiverViewerState(kuiver).apply {
            layoutedKuiver = kuiver
            hasFittedInitially = true
        }
    }

    @Test
    fun clickWithSubSlopMouseMotionStillReachesTheNode() = runComposeUiTest {
        val state = singleNodeState()
        var clicks = 0

        setContent {
            ViewerRenderer(
                state = state,
                config = KuiverViewerConfig(fitToContent = false),
                anchorRegistry = AnchorPositionRegistry(),
                nodeContent = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Gray)
                            .clickable { clicks++ }
                    )
                },
                edges = EdgeRendering.Batched { _ -> error("no edges in this graph") }
            )
        }
        waitForIdle()

        // The node sits at the viewport center. A physical click rarely releases on the
        // exact pixel it pressed, so move a little in between
        onRoot().performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(3f, 2f))
            release()
        }
        waitForIdle()

        assertEquals(1, clicks, "a click with sub-slop motion did not reach the node")
        assertEquals(DpOffset.Zero, state.offset, "a sub-slop click panned the graph")
    }

    @Test
    fun dragPastSlopPansWithoutClicking() = runComposeUiTest {
        val state = singleNodeState()
        var clicks = 0

        setContent {
            ViewerRenderer(
                state = state,
                config = KuiverViewerConfig(fitToContent = false),
                anchorRegistry = AnchorPositionRegistry(),
                nodeContent = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable { clicks++ }
                    )
                },
                edges = EdgeRendering.Batched { _ -> error("no edges in this graph") }
            )
        }
        waitForIdle()

        onRoot().performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(60f, 0f))
            moveBy(Offset(60f, 0f))
            release()
        }
        waitForIdle()

        assertEquals(0, clicks, "a drag counted as a click")
        assertTrue(state.offset.x > 0.dp, "the drag did not pan, offset stayed ${state.offset}")
    }
}
