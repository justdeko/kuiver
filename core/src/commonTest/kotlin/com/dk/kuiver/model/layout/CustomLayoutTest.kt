package com.dk.kuiver.model.layout

import androidx.compose.ui.geometry.Offset
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
            width = 800f,
            height = 600f
        )

        layout(kuiver, config)

        assertEquals(kuiver, receivedKuiver)
        assertEquals(800f, receivedConfig?.width)
        assertEquals(600f, receivedConfig?.height)
    }

    @Test
    fun `custom layout can modify node positions`() {
        val kuiver = buildKuiver {
            addNode(KuiverNode(id = "A"))
            addNode(KuiverNode(id = "B"))
        }

        val customLayout: LayoutProvider = { k, _ ->
            val updatedNodes = k.nodes.values.map { node ->
                node.copy(position = Offset(100f, 200f))
            }
            buildKuiverWithClassifiedEdges(updatedNodes, k.edges)
        }

        val config = LayoutConfig.Custom(
            provider = customLayout
        )

        val result = layout(kuiver, config)

        result.nodes.values.forEach { node ->
            assertEquals(Offset(100f, 200f), node.position)
        }
    }

    @Test
    fun `custom layout can use LayoutConfig parameters`() {
        val kuiver = buildKuiver {
            addNode(KuiverNode(id = "A"))
            addNode(KuiverNode(id = "B"))
        }

        // Simple grid layout using width from config
        val spacing = 150f
        val gridLayout: LayoutProvider = { k, c ->
            val updatedNodes = k.nodes.values.mapIndexed { index, node ->
                node.copy(
                    position = Offset(
                        x = index * spacing,
                        y = c.height / 2f
                    )
                )
            }
            buildKuiverWithClassifiedEdges(updatedNodes, k.edges)
        }

        val config = LayoutConfig.Custom(
            provider = gridLayout,
            width = 600f,
            height = 400f
        )

        val result = layout(kuiver, config)

        // Nodes should be spaced 150 units apart horizontally, centered vertically
        val nodesList = result.nodes.values.toList()
        assertEquals(0f, nodesList[0].position.x)
        assertEquals(150f, nodesList[1].position.x)
        assertEquals(200f, nodesList[0].position.y) // height/2
        assertEquals(200f, nodesList[1].position.y)
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
            val radius = 100f
            val centerX = c.width / 2f
            val centerY = c.height / 2f

            val updatedNodes = nodesList.mapIndexed { index, node ->
                val angle = (index.toFloat() / nodesList.size) * 2f * kotlin.math.PI.toFloat()
                node.copy(
                    position = Offset(
                        x = centerX + radius * kotlin.math.cos(angle),
                        y = centerY + radius * kotlin.math.sin(angle)
                    )
                )
            }
            buildKuiverWithClassifiedEdges(updatedNodes, k.edges)
        }

        val config = LayoutConfig.Custom(
            provider = circularLayout,
            width = 400f,
            height = 400f
        )

        val result = layout(kuiver, config)

        // Verify all nodes have positions (basic sanity check)
        assertEquals(4, result.nodes.size)
        result.nodes.values.forEach { node ->
            assertTrue(node.position.x > 0f, "Node should have positive X position")
            assertTrue(node.position.y != 0f, "Node should have non-zero Y position")
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
                node.copy(position = Offset(100f, 100f))
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
                    position = Offset(
                        x = col * cellWidth + cellWidth / 2,
                        y = row * cellHeight + cellHeight / 2
                    )
                )
            }
            buildKuiverWithClassifiedEdges(updatedNodes, k.edges)
        }

        val config = LayoutConfig.Custom(
            provider = gridLayout,
            width = 600f,
            height = 600f
        )

        val result = layout(kuiver, config)

        assertEquals(9, result.nodes.size)
        // Verify nodes are positioned in a grid (basic check - all have different positions except those in same column)
        val positions = result.nodes.values.map { it.position }.toSet()
        assertEquals(positions.size, 9, "All nodes should have positions")
    }
}
