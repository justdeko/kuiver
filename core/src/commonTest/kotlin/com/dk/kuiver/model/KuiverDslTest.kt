package com.dk.kuiver.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KuiverDslTest {

    @Test
    fun `buildKuiver creates graph with DSL`() {
        val kuiver = buildKuiver {
            nodes("A", "B", "C")
            edge("A", "B")
            edge("B", "C")
        }

        assertEquals(3, kuiver.nodes.size)
        assertEquals(2, kuiver.edges.size)
    }

    @Test
    fun `nodes creates multiple nodes from vararg`() {
        val kuiver = buildKuiver {
            nodes("A", "B", "C", "D")
        }

        assertEquals(4, kuiver.nodes.size)
        assertTrue(kuiver.nodes.containsKey("A"))
        assertTrue(kuiver.nodes.containsKey("B"))
        assertTrue(kuiver.nodes.containsKey("C"))
        assertTrue(kuiver.nodes.containsKey("D"))
    }

    @Test
    fun `nodes creates multiple nodes from collection`() {
        val nodeIds = listOf("A", "B", "C")
        val kuiver = buildKuiver {
            nodes(nodeIds)
        }

        assertEquals(3, kuiver.nodes.size)
        nodeIds.forEach { id ->
            assertTrue(kuiver.nodes.containsKey(id))
        }
    }

    @Test
    fun `edge creates edge with anchors`() {
        val kuiver = buildKuiver {
            nodes("A", "B")
            edge("A", "B", fromAnchor = "out-0", toAnchor = "in-0")
        }

        val edge = kuiver.edges.first()
        assertEquals("out-0", edge.fromAnchor)
        assertEquals("in-0", edge.toAnchor)
    }

    @Test
    fun `edges creates multiple edges from vararg pairs`() {
        val kuiver = buildKuiver {
            nodes("A", "B", "C")
            edges(
                "A" to "B",
                "B" to "C"
            )
        }

        assertEquals(2, kuiver.edges.size)
    }

    @Test
    fun `fromEdgeList creates graph from pairs`() {
        val edgeList = listOf(
            "A" to "B",
            "B" to "C",
            "C" to "D"
        )

        val kuiver = buildKuiver {
            fromEdgeList(edgeList)
        }

        assertEquals(4, kuiver.nodes.size)
        assertEquals(3, kuiver.edges.size)
    }

    @Test
    fun `fromEdgeList without createNodes`() {
        val kuiver = buildKuiver {
            nodes("A", "B", "C")
            fromEdgeList(listOf("A" to "B", "B" to "C"), createNodes = false)
        }

        assertEquals(3, kuiver.nodes.size)
        assertEquals(2, kuiver.edges.size)
    }

    @Test
    fun `buildKuiverWithClassifiedEdges builds graph with edge classifications`() {
        val nodes = listOf(
            KuiverNode("A"),
            KuiverNode("B"),
            KuiverNode("C")
        )
        val edges = listOf(
            KuiverEdge("A", "B"),
            KuiverEdge("B", "C"),
            KuiverEdge("C", "A")  // Back edge
        )

        val kuiver = buildKuiverWithClassifiedEdges(nodes, edges)

        assertEquals(3, kuiver.nodes.size)
        assertEquals(3, kuiver.edges.size)

        val edgeTypes = kuiver.edges.map { it.type }.toSet()
        assertTrue(edgeTypes.contains(EdgeType.FORWARD))
        assertTrue(edgeTypes.contains(EdgeType.BACK))
    }
}
