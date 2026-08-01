package com.dk.kuiver.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.dk.kuiver.KuiverViewerState
import com.dk.kuiver.RelayoutPolicy
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.buildKuiver
import com.dk.kuiver.model.edges
import com.dk.kuiver.model.nodes
import com.dk.kuiver.rememberKuiverViewerState
import com.dk.kuiver.ui.EdgeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** What a laid out graph does with the positions the user set by hand. */
@OptIn(ExperimentalTestApi::class)
class RelayoutPolicyTest {

    @Test
    fun keepManualPutsAMovedNodeBackAfterTheGraphChanges() = runComposeUiTest {
        val scene = policyScene(RelayoutPolicy.KEEP_MANUAL)
        setContent { scene.Content() }
        waitUntil { scene.state.layoutedKuiver.nodes.getValue("A").dimensions != null }

        scene.state.moveNode("A", MOVED_TO)
        scene.grow()
        waitUntil { scene.state.layoutedKuiver.nodes.size == 3 }

        assertEquals(
            MOVED_TO,
            scene.state.layoutedKuiver.nodes.getValue("A").position,
            "the layout pass overwrote a manual position"
        )
        assertEquals(mapOf("A" to MOVED_TO), scene.state.manualPositions)
    }

    @Test
    fun relayoutAllHandsEveryPositionBackToTheLayout() = runComposeUiTest {
        val scene = policyScene(RelayoutPolicy.RELAYOUT_ALL)
        setContent { scene.Content() }
        waitUntil { scene.state.layoutedKuiver.nodes.getValue("A").dimensions != null }

        scene.state.moveNode("A", MOVED_TO)
        assertEquals(
            MOVED_TO,
            scene.state.layoutedKuiver.nodes.getValue("A").position,
            "the move did not take effect at all"
        )

        scene.grow()
        waitUntil { scene.state.layoutedKuiver.nodes.size == 3 }

        assertNotEquals(
            MOVED_TO,
            scene.state.layoutedKuiver.nodes.getValue("A").position,
            "the layout pass did not take the position back"
        )
    }

    @Test
    fun clearingManualPositionsReleasesTheNodeAgain() = runComposeUiTest {
        val scene = policyScene(RelayoutPolicy.KEEP_MANUAL)
        setContent { scene.Content() }
        waitUntil { scene.state.layoutedKuiver.nodes.getValue("A").dimensions != null }

        scene.state.moveNode("A", MOVED_TO)
        scene.state.clearManualPositions()
        scene.grow()
        waitUntil { scene.state.layoutedKuiver.nodes.size == 3 }

        assertTrue(scene.state.manualPositions.isEmpty())
        assertNotEquals(
            MOVED_TO,
            scene.state.layoutedKuiver.nodes.getValue("A").position,
            "a cleared manual position was still reapplied"
        )
    }

    @Test
    fun aManualPositionIsDroppedWithItsNode() = runComposeUiTest {
        val scene = policyScene(RelayoutPolicy.KEEP_MANUAL)
        setContent { scene.Content() }
        waitUntil { scene.state.layoutedKuiver.nodes.getValue("A").dimensions != null }

        scene.state.moveNode("A", MOVED_TO)
        scene.update { it.withoutNode("A") }
        waitUntil { scene.state.layoutedKuiver.nodes.size == 1 }

        assertTrue(
            scene.state.manualPositions.isEmpty(),
            "the manual position outlived its node: ${scene.state.manualPositions}"
        )
    }

    private fun policyScene(policy: RelayoutPolicy) = PolicyScene(policy)

    private class PolicyScene(private val policy: RelayoutPolicy) {
        lateinit var state: KuiverViewerState
            private set

        private var graph by mutableStateOf(
            buildKuiver {
                nodes("A", "B")
                edges("A" to "B")
            }
        )

        /** Adds a node, which is a graph change and so a layout pass. */
        fun grow() = update { it.rebuild { nodes("C"); edges("B" to "C") } }

        fun update(transform: (Kuiver) -> Kuiver) {
            graph = transform(graph)
        }

        @Composable
        fun Content() {
            state = rememberKuiverViewerState(graph)
            LaunchedEffect(graph) { state.updateKuiver(graph) }
            KuiverViewer(
                state = state,
                config = KuiverViewerConfig(fitToContent = false, relayoutPolicy = policy),
                nodeContent = { Box(Modifier.size(60.dp).background(Color.Gray)) },
                edgeStyle = { EdgeStyle() }
            )
        }
    }

    private companion object {
        val MOVED_TO = DpOffset(500.dp, 400.dp)
    }
}
