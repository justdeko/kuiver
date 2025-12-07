package com.dk.kuiver.model.layout

import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.model.buildKuiver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HierarchicalLayoutTest {

    @Test
    fun `simple DAG layout places nodes at correct levels`() {
        // A -> B -> C (linear chain)
        val kuiver = buildKuiver {
            addNode(KuiverNode(id = "A"))
            addNode(KuiverNode(id = "B"))
            addNode(KuiverNode(id = "C"))
            addEdge(KuiverEdge(fromId = "A", toId = "B"))
            addEdge(KuiverEdge(fromId = "B", toId = "C"))
        }

        val config = LayoutConfig(
            algorithm = LayoutAlgorithm.HIERARCHICAL,
            direction = LayoutDirection.HORIZONTAL,
            width = 600f,
            height = 400f
        )

        val result = layout(kuiver, config)

        // Verify nodes are at increasing X positions
        val nodeA = result.nodes["A"]!!
        val nodeB = result.nodes["B"]!!
        val nodeC = result.nodes["C"]!!

        assertTrue(nodeA.position.x < nodeB.position.x, "A should be left of B")
        assertTrue(nodeB.position.x < nodeC.position.x, "B should be left of C")
    }

    @Test
    fun `diamond DAG layout handles multiple paths`() {
        // A -> B -> D
        // A -> C -> D
        val kuiver = buildKuiver {
            addNode(KuiverNode(id = "A"))
            addNode(KuiverNode(id = "B"))
            addNode(KuiverNode(id = "C"))
            addNode(KuiverNode(id = "D"))
            addEdge(KuiverEdge(fromId = "A", toId = "B"))
            addEdge(KuiverEdge(fromId = "A", toId = "C"))
            addEdge(KuiverEdge(fromId = "B", toId = "D"))
            addEdge(KuiverEdge(fromId = "C", toId = "D"))
        }

        val config = LayoutConfig(
            algorithm = LayoutAlgorithm.HIERARCHICAL,
            direction = LayoutDirection.HORIZONTAL,
            width = 600f,
            height = 400f
        )

        val result = layout(kuiver, config)

        val nodeA = result.nodes["A"]!!
        val nodeB = result.nodes["B"]!!
        val nodeC = result.nodes["C"]!!
        val nodeD = result.nodes["D"]!!

        // A should be leftmost
        assertTrue(nodeA.position.x < nodeB.position.x)
        assertTrue(nodeA.position.x < nodeC.position.x)

        // B and C should be at same level
        assertEquals(nodeB.position.x, nodeC.position.x, absoluteTolerance = 1f)

        // D should be rightmost
        assertTrue(nodeD.position.x > nodeB.position.x)
    }

    @Test
    fun `vertical direction swaps positioning`() {
        val kuiver = buildKuiver {
            addNode(KuiverNode(id = "A"))
            addNode(KuiverNode(id = "B"))
            addEdge(KuiverEdge(fromId = "A", toId = "B"))
        }

        val config = LayoutConfig(
            algorithm = LayoutAlgorithm.HIERARCHICAL,
            direction = LayoutDirection.VERTICAL,
            width = 400f,
            height = 600f
        )

        val result = layout(kuiver, config)

        val nodeA = result.nodes["A"]!!
        val nodeB = result.nodes["B"]!!

        // In vertical mode, A should be above B
        assertTrue(nodeA.position.y < nodeB.position.y)
    }

    @Test
    fun `layout handles cycles correctly`() {
        // A -> B -> C -> A (cycle)
        val kuiver = buildKuiver {
            addNode(KuiverNode(id = "A"))
            addNode(KuiverNode(id = "B"))
            addNode(KuiverNode(id = "C"))
            addEdge(KuiverEdge(fromId = "A", toId = "B"))
            addEdge(KuiverEdge(fromId = "B", toId = "C"))
            addEdge(KuiverEdge(fromId = "C", toId = "A"))
        }

        val config = LayoutConfig(
            algorithm = LayoutAlgorithm.HIERARCHICAL,
            width = 600f,
            height = 400f
        )

        val result = layout(kuiver, config)

        // Verify all nodes have positions and layout doesn't crash
        assertEquals(3, result.nodes.size)
        assertEquals(3, result.edges.size)
    }
}
