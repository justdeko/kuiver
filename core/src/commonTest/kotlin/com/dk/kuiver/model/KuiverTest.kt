package com.dk.kuiver.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KuiverTest {

    @Test
    fun `hasCycles detects simple cycle`() {
        val kuiver = Kuiver().apply {
            addNode(KuiverNode(id = "A"))
            addNode(KuiverNode(id = "B"))
            addNode(KuiverNode(id = "C"))
            addEdge(KuiverEdge(fromId = "A", toId = "B"))
            addEdge(KuiverEdge(fromId = "B", toId = "C"))
            addEdge(KuiverEdge(fromId = "C", toId = "A"))
        }

        assertTrue(kuiver.hasCycles(), "Should detect cycle A->B->C->A")
    }

    @Test
    fun `hasCycles returns false for DAG`() {
        val kuiver = Kuiver().apply {
            addNode(KuiverNode(id = "A"))
            addNode(KuiverNode(id = "B"))
            addNode(KuiverNode(id = "C"))
            addEdge(KuiverEdge(fromId = "A", toId = "B"))
            addEdge(KuiverEdge(fromId = "B", toId = "C"))
        }

        assertFalse(kuiver.hasCycles(), "DAG should not have cycles")
    }

    @Test
    fun `hasCycles detects self loop`() {
        val kuiver = Kuiver().apply {
            addNode(KuiverNode(id = "A"))
            addEdge(KuiverEdge(fromId = "A", toId = "A"))
        }

        assertTrue(kuiver.hasCycles(), "Self loop should be detected as cycle")
    }

    @Test
    fun `classifyEdge identifies forward edges`() {
        val kuiver = Kuiver().apply {
            addNode(KuiverNode(id = "A"))
            addNode(KuiverNode(id = "B"))
            addEdge(KuiverEdge(fromId = "A", toId = "B"))
        }

        val edge = kuiver.edges.first()
        assertEquals(EdgeType.FORWARD, kuiver.classifyEdge(edge))
    }

    @Test
    fun `classifyEdge identifies back edges in cycle`() {
        val kuiver = Kuiver().apply {
            addNode(KuiverNode(id = "A"))
            addNode(KuiverNode(id = "B"))
            addEdge(KuiverEdge(fromId = "A", toId = "B"))
            addEdge(KuiverEdge(fromId = "B", toId = "A"))
        }

        val classifications = kuiver.classifyAllEdges()
        val types = classifications.values.toSet()

        assertTrue(types.contains(EdgeType.FORWARD), "Should have forward edge")
        assertTrue(types.contains(EdgeType.BACK), "Should have back edge")
    }

    @Test
    fun `classifyEdge identifies self loop`() {
        val kuiver = Kuiver().apply {
            addNode(KuiverNode(id = "A"))
            addEdge(KuiverEdge(fromId = "A", toId = "A"))
        }

        val edge = kuiver.edges.first()
        assertEquals(EdgeType.SELF_LOOP, kuiver.classifyEdge(edge))
    }

    @Test
    fun `wouldCreateCycle only reports edges that close a loop`() {
        val kuiver = buildKuiver {
            nodes("A", "B", "C", "D")
            edges("A" to "B", "B" to "C")
        }

        assertTrue(kuiver.wouldCreateCycle("C", "A"), "C->A closes the chain into a loop")
        assertTrue(kuiver.wouldCreateCycle("A", "A"), "A->A is a self loop")
        assertFalse(kuiver.wouldCreateCycle("A", "C"), "A->C is a shortcut, not a cycle")
        assertFalse(kuiver.wouldCreateCycle("D", "A"), "D is disconnected from the chain")

        // Traversal must terminate even when the graph already contains a cycle
        kuiver.addEdge(KuiverEdge(fromId = "C", toId = "A"))
        assertTrue(kuiver.wouldCreateCycle("C", "B"))
        assertFalse(kuiver.wouldCreateCycle("C", "D"))
    }

    @Test
    fun `traversals handle a chain too deep for recursion`() {
        val length = 50_000
        val kuiver = buildKuiver {
            nodes((0 until length).map { "n$it" })
            for (i in 0 until length - 1) edge("n$i", "n${i + 1}")
        }
        val head = "n0"
        val tail = "n${length - 1}"

        assertFalse(kuiver.hasCycles())
        assertTrue(kuiver.wouldCreateCycle(tail, head))
        assertFalse(kuiver.wouldCreateCycle(head, tail))
        assertEquals(length, kuiver.findStronglyConnectedComponents().size)

        kuiver.addEdge(KuiverEdge(fromId = tail, toId = head))

        assertTrue(kuiver.hasCycles())
        assertEquals(length, kuiver.findStronglyConnectedComponents().single().size)
    }
}
