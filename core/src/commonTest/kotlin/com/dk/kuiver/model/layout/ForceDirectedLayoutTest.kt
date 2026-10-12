package com.dk.kuiver.model.layout

import androidx.compose.ui.unit.dp
import com.dk.kuiver.assertDpEquals
import com.dk.kuiver.model.buildKuiver
import com.dk.kuiver.model.edge
import com.dk.kuiver.model.edges
import com.dk.kuiver.model.nodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForceDirectedLayoutTest {

    @Test
    fun `places nodes within bounds`() {
        val kuiver = buildKuiver {
            nodes("A", "B", "C")
            edges(
                "A" to "B",
                "B" to "C"
            )
        }

        val config = LayoutConfig.ForceDirected(
            width = 600.dp,
            height = 400.dp,
            iterations = 100
        )

        val result = layout(kuiver, config)

        result.nodes.values.forEach { node ->
            assertTrue(node.position.x >= 0.dp, "Node ${node.id} x position should be >= 0")
            assertTrue(node.position.x <= config.width, "Node ${node.id} x position should be <= width")
            assertTrue(node.position.y >= 0.dp, "Node ${node.id} y position should be >= 0")
            assertTrue(node.position.y <= config.height, "Node ${node.id} y position should be <= height")
        }
    }

    @Test
    fun `single node centers in canvas`() {
        val kuiver = buildKuiver {
            nodes("A")
        }

        val config = LayoutConfig.ForceDirected(
            width = 600.dp,
            height = 400.dp,
            iterations = 100
        )

        val result = layout(kuiver, config)

        val nodeA = result.nodes["A"]!!
        val expectedX = config.width / 2f
        val expectedY = config.height / 2f

        assertDpEquals(expectedX, nodeA.position.x, absoluteTolerance = 50.dp)
        assertDpEquals(expectedY, nodeA.position.y, absoluteTolerance = 50.dp)
    }

    @Test
    fun `layout completes with cycles`() {
        val kuiver = buildKuiver {
            nodes("A", "B", "C")
            edges(
                "A" to "B",
                "B" to "C",
                "C" to "A"
            )
        }

        val config = LayoutConfig.ForceDirected(
            width = 600.dp,
            height = 400.dp
        )

        val result = layout(kuiver, config)

        // Verify all nodes have positions and layout doesn't crash
        assertEquals(3, result.nodes.size)
        assertEquals(3, result.edges.size)
    }

    @Test
    fun `a parallel edge keeps its id and moves no node`() {
        val single = buildKuiver {
            nodes("A", "B", "C", "D")
            edges("A" to "B", "A" to "C", "B" to "D", "A" to "D")
        }
        val doubled = single.rebuild { edge("A", "D", id = "again") }
        val config = LayoutConfig.ForceDirected(width = 800.dp, height = 600.dp, iterations = 50)

        val result = layout(doubled, config)
        assertEquals(layout(single, config).nodes, result.nodes)
        assertTrue(result.edges.any { it.id == "again" })
    }
}
