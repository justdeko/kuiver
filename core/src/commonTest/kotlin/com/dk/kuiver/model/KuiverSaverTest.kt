package com.dk.kuiver.model

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.dk.kuiver.KuiverViewerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KuiverSaverTest {

    @Test
    fun `restore kuiver with all properties`() {
        val original = buildKuiver {
            addNode(KuiverNode("node1", NodeDimensions(80.dp, 60.dp), DpOffset(100.dp, 200.dp)))
            addNode(KuiverNode("node2", null, DpOffset(300.dp, 400.dp)))
            addEdge(KuiverEdge("node1", "node2", EdgeType.FORWARD, "right", "left"))
            addEdge(KuiverEdge("node2", "node1"))
        }

        val restored = kuiverSaver().roundTrip(original)
        assertNotNull(restored)

        // Node with dimensions: position (100,200), dimensions 80x60
        val node1 = restored.nodes["node1"]!!
        assertEquals(100.dp, node1.position.x)
        assertEquals(200.dp, node1.position.y)
        assertEquals(80.dp, node1.dimensions?.width)
        assertEquals(60.dp, node1.dimensions?.height)

        // Node without dimensions: position (300,400), null dimensions
        val node2 = restored.nodes["node2"]!!
        assertEquals(300.dp, node2.position.x)
        assertEquals(400.dp, node2.position.y)
        assertNull(node2.dimensions)

        // Edge with all properties
        val edge1 = restored.edges.first { it.fromId == "node1" }
        assertEquals("node2", edge1.toId)
        assertEquals(EdgeType.FORWARD, edge1.type)
        assertEquals("right", edge1.fromAnchor)
        assertEquals("left", edge1.toAnchor)

        // Edge with nulls
        val edge2 = restored.edges.first { it.fromId == "node2" }
        assertEquals("node1", edge2.toId)
        assertNull(edge2.type)
        assertNull(edge2.fromAnchor)
        assertNull(edge2.toAnchor)
    }

    @Test
    fun `graph is saved as flat list of primitives`() {
        val graph = buildKuiver {
            addNode(KuiverNode("A", NodeDimensions(80.dp, 60.dp), DpOffset(1.dp, 2.dp)))
            addNode(KuiverNode("B"))
            edge("A", "B")
            edge("A", "B", id = "second")
        }

        val saved = with(kuiverSaver()) { SaverScope { true }.save(graph) }

        assertEquals(
            listOf<Any?>(
                2,
                "A", 1f, 2f, 80f, 60f,
                "B", 0f, 0f, null, null,
                "A", "B", null, null, null, null,
                "A", "B", null, null, null, "second"
            ),
            saved
        )
    }

    @Test
    fun `manual positions survive a save and restore round trip`() {
        val positions = mapOf(
            "node1" to DpOffset(120.dp, (-40).dp),
            "node2" to DpOffset(0.dp, 500.5f.dp)
        )
        assertEquals(positions, manualPositionsSaver().roundTrip(positions))
    }

    @Test
    fun `an empty set of manual positions restores as empty`() {
        assertEquals(emptyMap(), manualPositionsSaver().roundTrip(emptyMap()))
    }

    @Test
    fun `parallel edges keep their ids`() {
        val graph = buildKuiver {
            nodes("A", "B")
            edge("A", "B")
            edge("A", "B", id = "second")
        }
        assertEquals(graph, kuiverSaver().roundTrip(graph))
    }

    @Test
    fun `viewer state restores graph and view and moved nodes`() {
        val graph = buildKuiver {
            nodes("A", "B")
            edge("A", "B")
        }
        val state =
            KuiverViewerState(graph, initialScale = 2f, initialOffset = DpOffset(30.dp, (-12).dp))
        state.moveNode("B", DpOffset(250.dp, 40.dp))
        state.hasFittedInitially = true

        val restored = assertNotNull(kuiverViewerStateSaver().roundTrip(state))

        assertEquals(graph, restored.kuiver)
        assertEquals(state.scale, restored.scale)
        assertEquals(state.offset, restored.offset)
        assertEquals(mapOf("B" to DpOffset(250.dp, 40.dp)), restored.manualPositions)
        assertTrue(restored.hasRestoredTransform, "the restored view would be replaced by a fit")
    }

    @Test
    fun `viewer state that never fitted is fitted after a restore`() {
        val restored = assertNotNull(
            kuiverViewerStateSaver().roundTrip(KuiverViewerState(buildKuiver { nodes("A") }))
        )
        assertFalse(restored.hasRestoredTransform)
    }

    /** Puts [value] through the saver the way `rememberSaveable` does. */
    private fun <T> Saver<T, Any>.roundTrip(value: T): T? =
        restore(assertNotNull(SaverScope { true }.save(value)))
}
