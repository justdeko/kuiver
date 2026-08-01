package com.dk.kuiver.model

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A [Kuiver] is a value: two graphs with the same content are the same graph, and every change
 * hands back a new instance instead of touching the old one. Snapshot state and `remember` keys
 * rely on both halves of that.
 */
class KuiverImmutabilityTest {

    private fun chain() = buildKuiver {
        nodes("A", "B", "C")
        edges("A" to "B", "B" to "C")
    }

    @Test
    fun `graphs with the same content are equal`() {
        val first = chain()
        val second = chain()

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `an added node makes the graph unequal to the one it came from`() {
        val original = chain()
        val extended = original.withNode(KuiverNode("D"))

        assertNotEquals(original, extended)
        assertEquals(3, original.nodes.size, "the original graph was modified in place")
        assertEquals(4, extended.nodes.size)
    }

    @Test
    fun `an added edge makes the graph unequal to the one it came from`() {
        val original = chain()
        val extended = original.withEdge(KuiverEdge("A", "C"))

        assertNotEquals(original, extended)
        assertEquals(2, original.edges.size, "the original graph was modified in place")
        assertEquals(3, extended.edges.size)
        assertTrue(extended.wouldCreateCycle("C", "A"), "the new edge never reached the traversal")
    }

    @Test
    fun `node positions differing makes graphs unequal`() {
        val flat = buildKuiver { addNode(KuiverNode("A")) }
        val moved = flat.withNode(KuiverNode("A", position = DpOffset(10.dp, 10.dp)))

        assertNotEquals(flat, moved)
        assertEquals(DpOffset.Zero, flat.nodes.getValue("A").position)
        assertEquals(DpOffset(10.dp, 10.dp), moved.nodes.getValue("A").position)
    }

    @Test
    fun `withNode replaces an existing node and keeps its edges`() {
        val original = chain()
        val resized = original.withNode(
            KuiverNode("B", dimensions = NodeDimensions(40.dp, 20.dp))
        )

        assertEquals(3, resized.nodes.size)
        assertEquals(NodeDimensions(40.dp, 20.dp), resized.nodes.getValue("B").dimensions)
        assertEquals(original.edges, resized.edges)
        assertEquals(listOf("A", "B", "C"), resized.getTopologicalOrder())
    }

    @Test
    fun `a change that changes nothing returns the same instance`() {
        val original = chain()

        assertSame(original, original.withNode(original.nodes.getValue("A")))
        assertSame(original, original.withEdge(original.edges.first()))
        assertSame(original, original.withoutNode("nope"))
        assertSame(original, original.withoutEdge(KuiverEdge("A", "C")))
        assertSame(original, original.withMeasuredDimensions(emptyMap()))
    }

    @Test
    fun `withEdge rejects an edge into a node that is not in the graph`() {
        val original = chain()

        assertFailsWith<IllegalArgumentException> { original.withEdge(KuiverEdge("A", "Z")) }
        assertFailsWith<IllegalArgumentException> { original.withEdge(KuiverEdge("Z", "A")) }
    }

    @Test
    fun `withEdge keeps the edge already connecting the two nodes`() {
        val original = chain()
        val again = original.withEdge(KuiverEdge("A", "B", fromAnchor = "right", toAnchor = "left"))

        assertSame(original, again, "a second A -> B edge was let in")
        assertEquals(setOf(KuiverEdge("A", "B"), KuiverEdge("B", "C")), again.edges)
    }

    @Test
    fun `withEdges skips the pairs that are already connected`() {
        val original = chain()
        val extended = original.withEdges(
            listOf(
                KuiverEdge("A", "B", fromAnchor = "right"),
                KuiverEdge("A", "C"),
                KuiverEdge("A", "C", toAnchor = "top")
            )
        )

        assertEquals(
            setOf(KuiverEdge("A", "B"), KuiverEdge("B", "C"), KuiverEdge("A", "C")),
            extended.edges
        )
        assertSame(
            extended,
            extended.withEdges(listOf(KuiverEdge("A", "B"), KuiverEdge("A", "C"))),
            "a batch that adds nothing should not produce a new graph"
        )
    }

    @Test
    fun `the builder takes one edge per node pair`() {
        val builder = KuiverBuilder()
        builder.addNode(KuiverNode("A"))
        builder.addNode(KuiverNode("B"))

        assertTrue(builder.addEdge(KuiverEdge("A", "B")))
        assertFalse(
            builder.addEdge(KuiverEdge("A", "B", fromAnchor = "right")),
            "a second A -> B edge was accepted"
        )
        assertTrue(builder.addEdge(KuiverEdge("B", "A")), "the other direction is its own edge")

        val graph = builder.build()
        assertEquals(setOf(KuiverEdge("A", "B"), KuiverEdge("B", "A")), graph.edges)
    }

    @Test
    fun `a rejected edge leaves no shadow in the classification`() {
        val graph = buildKuiver {
            nodes("A", "B", "C")
            edges("A" to "B", "B" to "C", "C" to "A")
            edge("A", "B", fromAnchor = "right", toAnchor = "left")
        }
        val types = graph.classifyAllEdges()

        assertEquals(3, graph.edges.size, "the duplicate A -> B edge was kept")
        assertEquals(graph.edges, types.keys, "an edge was kept but never reached by the traversal")
        assertEquals(EdgeType.FORWARD, types.getValue(KuiverEdge("A", "B")))
        assertEquals(EdgeType.BACK, types.getValue(KuiverEdge("C", "A")))
    }

    @Test
    fun `withoutNode drops the edges that touch it`() {
        val trimmed = chain().withoutNode("B")

        assertEquals(setOf("A", "C"), trimmed.nodes.keys)
        assertTrue(trimmed.edges.isEmpty(), "edges of the removed node were kept")
        assertFalse(trimmed.wouldCreateCycle("C", "A"))
    }

    @Test
    fun `withoutEdge keeps the nodes and the other edges`() {
        val original = chain()
        val trimmed = original.withoutEdge(KuiverEdge("A", "B"))

        assertEquals(original.nodes, trimmed.nodes)
        assertEquals(setOf(KuiverEdge("B", "C")), trimmed.edges)
        assertEquals(listOf("A", "B", "C"), trimmed.getTopologicalOrder())
    }

    @Test
    fun `rebuild starts from the current content`() {
        val original = chain()
        val extended = original.rebuild {
            nodes("D")
            edge("C", "D")
        }

        assertEquals(4, extended.nodes.size)
        assertEquals(3, extended.edges.size)
        assertEquals(chain(), original, "rebuild modified the graph it started from")
        assertEquals(listOf("A", "B", "C", "D"), extended.getTopologicalOrder())
    }

    @Test
    fun `a builder reused after build does not reach into the graph it already made`() {
        val builder = KuiverBuilder()
        builder.addNode(KuiverNode("A"))
        val first = builder.build()

        builder.addNode(KuiverNode("B"))
        val second = builder.build()

        assertEquals(setOf("A"), first.nodes.keys)
        assertEquals(setOf("A", "B"), second.nodes.keys)
    }

    @Test
    fun `withMeasuredDimensions only touches the dimensions`() {
        val original = chain()
        val measured = original.withMeasuredDimensions(
            mapOf("A" to NodeDimensions(80.dp, 40.dp))
        )

        assertNull(original.nodes.getValue("A").dimensions, "the caller's graph was measured over")
        assertEquals(NodeDimensions(80.dp, 40.dp), measured.nodes.getValue("A").dimensions)
        assertEquals(original.edges, measured.edges)
        assertSame(
            measured,
            measured.withMeasuredDimensions(mapOf("A" to NodeDimensions(80.dp, 40.dp))),
            "re-applying the same dimensions should not produce a new graph"
        )
    }
}
