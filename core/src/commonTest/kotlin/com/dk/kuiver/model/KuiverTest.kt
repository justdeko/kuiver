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
}
