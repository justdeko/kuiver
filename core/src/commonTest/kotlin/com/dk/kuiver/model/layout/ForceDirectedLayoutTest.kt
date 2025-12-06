package com.dk.kuiver.model.layout

import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForceDirectedLayoutTest {

    @Test
    fun `places nodes within bounds`() {
        val kuiver = Kuiver().apply {
            addNode(KuiverNode(id = "A"))
            addNode(KuiverNode(id = "B"))
            addNode(KuiverNode(id = "C"))
            addEdge(KuiverEdge(fromId = "A", toId = "B"))
            addEdge(KuiverEdge(fromId = "B", toId = "C"))
        }

        val config = LayoutConfig(
            algorithm = LayoutAlgorithm.FORCE_DIRECTED,
            width = 600f,
            height = 400f,
            iterations = 100
        )

        val result = layout(kuiver, config)

        result.nodes.values.forEach { node ->
            assertTrue(node.position.x >= 0f, "Node ${node.id} x position should be >= 0")
            assertTrue(node.position.x <= config.width, "Node ${node.id} x position should be <= width")
            assertTrue(node.position.y >= 0f, "Node ${node.id} y position should be >= 0")
            assertTrue(node.position.y <= config.height, "Node ${node.id} y position should be <= height")
        }
    }

    @Test
    fun `single node centers in canvas`() {
        val kuiver = Kuiver().apply {
            addNode(KuiverNode(id = "A"))
        }

        val config = LayoutConfig(
            algorithm = LayoutAlgorithm.FORCE_DIRECTED,
            width = 600f,
            height = 400f,
            iterations = 100
        )

        val result = layout(kuiver, config)

        val nodeA = result.nodes["A"]!!
        val expectedX = config.width / 2f
        val expectedY = config.height / 2f

        assertEquals(expectedX, nodeA.position.x, absoluteTolerance = 50f)
        assertEquals(expectedY, nodeA.position.y, absoluteTolerance = 50f)
    }

    @Test
    fun `layout completes with cycles`() {
        val kuiver = Kuiver().apply {
            addNode(KuiverNode(id = "A"))
            addNode(KuiverNode(id = "B"))
            addNode(KuiverNode(id = "C"))
            addEdge(KuiverEdge(fromId = "A", toId = "B"))
            addEdge(KuiverEdge(fromId = "B", toId = "C"))
            addEdge(KuiverEdge(fromId = "C", toId = "A"))
        }

        val config = LayoutConfig(
            algorithm = LayoutAlgorithm.FORCE_DIRECTED,
            width = 600f,
            height = 400f
        )

        val result = layout(kuiver, config)

        // Verify all nodes have positions and layout doesn't crash
        assertEquals(3, result.nodes.size)
        assertEquals(3, result.edges.size)
    }
}
