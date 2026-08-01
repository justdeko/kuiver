package com.dk.kuiver.model.layout

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.model.buildKuiver
import com.dk.kuiver.model.buildKuiverWithClassifiedEdges
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomLayoutTest {

    @Test
    fun `custom layout is invoked when algorithm is CUSTOM`() {
        val kuiver = buildKuiver {
            addNode(KuiverNode(id = "A"))
            addNode(KuiverNode(id = "B"))
        }

        var customLayoutCalled = false
        val customLayout: LayoutProvider = { k, _ ->
            customLayoutCalled = true
            k
        }

        val config = LayoutConfig.Custom(
            provider = customLayout
        )

        layout(kuiver, config)
        assertTrue(customLayoutCalled, "Custom layout should be called")
    }

    @Test
    fun `custom layout receives correct parameters`() {
        val kuiver = buildKuiver {
            addNode(KuiverNode(id = "A"))
            addNode(KuiverNode(id = "B"))
        }

        var receivedKuiver: Kuiver? = null
        var receivedConfig: LayoutConfig? = null

        val customLayout: LayoutProvider = { k, c ->
            receivedKuiver = k
            receivedConfig = c
            k
        }

        val config = LayoutConfig.Custom(
            provider = customLayout,
            width = 800.dp,
            height = 600.dp
        )

        layout(kuiver, config)

        assertEquals(kuiver, receivedKuiver)
        assertEquals(800.dp, receivedConfig?.width)
        assertEquals(600.dp, receivedConfig?.height)
    }

    @Test
    fun `custom layout can modify node positions`() {
        val kuiver = buildKuiver {
            addNode(KuiverNode(id = "A"))
            addNode(KuiverNode(id = "B"))
        }

        val customLayout: LayoutProvider = { k, _ ->
            val updatedNodes = k.nodes.values.map { node ->
                node.copy(position = DpOffset(100.dp, 200.dp))
            }
            buildKuiverWithClassifiedEdges(updatedNodes, k.edges)
        }

        val config = LayoutConfig.Custom(
            provider = customLayout
        )

        val result = layout(kuiver, config)

        result.nodes.values.forEach { node ->
            assertEquals(DpOffset(100.dp, 200.dp), node.position)
        }
    }

    @Test
    fun `custom layout can use LayoutConfig parameters`() {
        val kuiver = buildKuiver {
            addNode(KuiverNode(id = "A"))
            addNode(KuiverNode(id = "B"))
        }

        // Simple grid layout using width from config
        val spacing = 150.dp
        val gridLayout: LayoutProvider = { k, c ->
            val updatedNodes = k.nodes.values.mapIndexed { index, node ->
                node.copy(
                    position = DpOffset(
                        x = spacing * index,
                        y = c.height / 2f
                    )
                )
            }
            buildKuiverWithClassifiedEdges(updatedNodes, k.edges)
        }

        val config = LayoutConfig.Custom(
            provider = gridLayout,
            width = 600.dp,
            height = 400.dp
        )

        val result = layout(kuiver, config)

        // Nodes should be spaced 150 units apart horizontally, centered vertically
        val nodesList = result.nodes.values.toList()
        assertEquals(0.dp, nodesList[0].position.x)
        assertEquals(150.dp, nodesList[1].position.x)
        assertEquals(200.dp, nodesList[0].position.y) // height/2
        assertEquals(200.dp, nodesList[1].position.y)
    }

    @Test
    fun `custom circular layout positions nodes correctly`() {
        val kuiver = buildKuiver {
            addNode(KuiverNode(id = "A"))
            addNode(KuiverNode(id = "B"))
            addNode(KuiverNode(id = "C"))
            addNode(KuiverNode(id = "D"))
        }

        val circularLayout: LayoutProvider = { k, c ->
            val nodesList = k.nodes.values.toList()
            val radius = 100.dp
            val centerX = c.width / 2f
            val centerY = c.height / 2f

            val updatedNodes = nodesList.mapIndexed { index, node ->
                val angle = (index.toFloat() / nodesList.size) * 2f * kotlin.math.PI.toFloat()
                node.copy(
                    position = DpOffset(
                        x = centerX + radius * kotlin.math.cos(angle),
                        y = centerY + radius * kotlin.math.sin(angle)
                    )
                )
            }
            buildKuiverWithClassifiedEdges(updatedNodes, k.edges)
        }

        val config = LayoutConfig.Custom(
            provider = circularLayout,
            width = 400.dp,
            height = 400.dp
        )

        val result = layout(kuiver, config)

        // Verify all nodes have positions (basic sanity check)
        assertEquals(4, result.nodes.size)
        result.nodes.values.forEach { node ->
            assertTrue(node.position.x > 0.dp, "Node should have positive X position")
            assertTrue(node.position.y != 0.dp, "Node should have non-zero Y position")
        }
    }

    @Test
    fun `custom layout preserves edges`() {
        val kuiver = buildKuiver {
            addNode(KuiverNode(id = "A"))
            addNode(KuiverNode(id = "B"))
            addNode(KuiverNode(id = "C"))
            addEdge(KuiverEdge(fromId = "A", toId = "B"))
            addEdge(KuiverEdge(fromId = "B", toId = "C"))
        }

        val customLayout: LayoutProvider = { k, _ ->
            val updatedNodes = k.nodes.values.map { node ->
                node.copy(position = DpOffset(100.dp, 100.dp))
            }
            buildKuiverWithClassifiedEdges(updatedNodes, k.edges)
        }

        val config = LayoutConfig.Custom(
            provider = customLayout
        )

        val result = layout(kuiver, config)

        assertEquals(3, result.nodes.size, "All nodes should be preserved")
        assertEquals(2, result.edges.size, "All edges should be preserved")
    }

    @Test
    fun `custom layout can handle empty graph`() {
        val kuiver = Kuiver()

        val customLayout: LayoutProvider = { k, _ -> k }

        val config = LayoutConfig.Custom(
            provider = customLayout
        )

        val result = layout(kuiver, config)

        assertEquals(0, result.nodes.size)
        assertEquals(0, result.edges.size)
    }

    @Test
    fun `custom layout with grid arrangement`() {
        val kuiver = buildKuiver {
            repeat(9) { i ->
                addNode(KuiverNode(id = "N$i"))
            }
        }

        val gridLayout: LayoutProvider = { k, c ->
            val columns = 3
            val cellWidth = c.width / columns
            val cellHeight = c.height / kotlin.math.ceil(k.nodes.size.toFloat() / columns)

            val updatedNodes = k.nodes.values.mapIndexed { index, node ->
                val row = index / columns
                val col = index % columns
                node.copy(
                    position = DpOffset(
                        x = cellWidth * col + cellWidth / 2,
                        y = cellHeight * row + cellHeight / 2
                    )
                )
            }
            buildKuiverWithClassifiedEdges(updatedNodes, k.edges)
        }

        val config = LayoutConfig.Custom(
            provider = gridLayout,
            width = 600.dp,
            height = 600.dp
        )

        val result = layout(kuiver, config)

        assertEquals(9, result.nodes.size)
        // Verify nodes are positioned in a grid (basic check - all have different positions except those in same column)
        val positions = result.nodes.values.map { it.position }.toSet()
        assertEquals(positions.size, 9, "All nodes should have positions")
    }
}
