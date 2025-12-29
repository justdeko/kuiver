package com.dk.kuiver.model.layout

import androidx.compose.ui.unit.dp
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.model.NodeDimensions
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

    @Test
    fun `complex graph with potential edge occlusion`() {
        // Graph: a->e, a->b, a->d, b->c, b->d, b->e, c->d, d->e
        // Testing scenario where edge b->d might be visually obscured
        val kuiver = buildKuiver {
            addNode(KuiverNode(id = "a"))
            addNode(KuiverNode(id = "b"))
            addNode(KuiverNode(id = "c"))
            addNode(KuiverNode(id = "d"))
            addNode(KuiverNode(id = "e"))
            addEdge(KuiverEdge(fromId = "a", toId = "e"))
            addEdge(KuiverEdge(fromId = "a", toId = "b"))
            addEdge(KuiverEdge(fromId = "a", toId = "d"))
            addEdge(KuiverEdge(fromId = "b", toId = "c"))
            addEdge(KuiverEdge(fromId = "b", toId = "d"))
            addEdge(KuiverEdge(fromId = "b", toId = "e"))
            addEdge(KuiverEdge(fromId = "c", toId = "d"))
            addEdge(KuiverEdge(fromId = "d", toId = "e"))
        }

        val config = LayoutConfig(
            algorithm = LayoutAlgorithm.HIERARCHICAL,
            direction = LayoutDirection.HORIZONTAL,
            width = 800f,
            height = 600f
        )

        val result = layout(kuiver, config)

        val nodeA = result.nodes["a"]!!
        val nodeB = result.nodes["b"]!!
        val nodeC = result.nodes["c"]!!
        val nodeD = result.nodes["d"]!!
        val nodeE = result.nodes["e"]!!

        // Print positions for debugging
        println("Node positions (x, y):")
        println("a: (${nodeA.position.x}, ${nodeA.position.y})")
        println("b: (${nodeB.position.x}, ${nodeB.position.y})")
        println("c: (${nodeC.position.x}, ${nodeC.position.y})")
        println("d: (${nodeD.position.x}, ${nodeD.position.y})")
        println("e: (${nodeE.position.x}, ${nodeE.position.y})")

        // Print all nodes including dummies
        println("\nAll nodes (including dummies):")
        result.nodes.entries.sortedBy { it.value.position.x }.forEach { (id, node) ->
            println("  $id: (${node.position.x}, ${node.position.y})")
        }
        println("Total nodes: ${result.nodes.size}")

        // Verify hierarchical levels based on longest path
        // Expected levels: a=0, b=1, c=2, d=3, e=4
        assertTrue(nodeA.position.x < nodeB.position.x, "a should be before b")
        assertTrue(nodeB.position.x < nodeC.position.x, "b should be before c")
        assertTrue(nodeC.position.x < nodeD.position.x, "c should be before d")
        assertTrue(nodeD.position.x < nodeE.position.x, "d should be before e")

        // Verify layout completed without crashing
        assertEquals(5, result.nodes.size, "All nodes should be positioned")
        assertEquals(8, result.edges.size, "All edges should be present")
    }

    @Test
    fun `isolated nodes should not interfere with connected components`() {
        // Graph: a->b->c, plus isolated nodes d and e
        // Problem: d and e at level 0 push 'a' to different Y position, causing edge overlap
        val kuiver = buildKuiver {
            addNode(KuiverNode(id = "a"))
            addNode(KuiverNode(id = "b"))
            addNode(KuiverNode(id = "c"))
            addNode(KuiverNode(id = "d"))  // isolated
            addNode(KuiverNode(id = "e"))  // isolated
            addEdge(KuiverEdge(fromId = "a", toId = "b"))
            addEdge(KuiverEdge(fromId = "a", toId = "c"))
            addEdge(KuiverEdge(fromId = "b", toId = "c"))
        }

        val config = LayoutConfig(
            algorithm = LayoutAlgorithm.HIERARCHICAL,
            direction = LayoutDirection.HORIZONTAL,
            width = 800f,
            height = 600f
        )

        val result = layout(kuiver, config)

        println("\nNode positions:")
        result.nodes.entries.sortedBy { it.value.position.x }.forEach { (id, node) ->
            println("  $id: x=${node.position.x}, y=${node.position.y}")
        }

        val nodeA = result.nodes["a"]!!
        val nodeB = result.nodes["b"]!!
        val nodeC = result.nodes["c"]!!
        val nodeD = result.nodes["d"]!!
        val nodeE = result.nodes["e"]!!

        // Isolated nodes (d, e) should be at the rightmost level
        val maxConnectedX = maxOf(nodeA.position.x, nodeB.position.x, nodeC.position.x)
        val isolatedX = nodeD.position.x

        println("\nMax connected node x: $maxConnectedX")
        println("Isolated nodes x: d=${nodeD.position.x}, e=${nodeE.position.x}")

        // Isolated nodes should be placed after all connected nodes
        assertTrue(isolatedX > maxConnectedX, "Isolated nodes should be placed at the rightmost level")
        assertEquals(
            nodeD.position.x,
            nodeE.position.x,
            "Isolated nodes d and e should be at same level"
        )

        // Node 'a' should be alone at level 0 (not with d and e)
        val level0Nodes = result.nodes.filter { it.value.position.x == nodeA.position.x }
        println("Level 0 contains: ${level0Nodes.keys}")
        assertEquals(level0Nodes.size, 1, "Level 0 should only contain 'a', not isolated nodes")
    }

    @Test
    fun `layout respects node dimensions`() {
        // Test with varying node sizes
        val kuiver = buildKuiver {
            addNode(KuiverNode(id = "small", dimensions = NodeDimensions(50.dp, 30.dp)))
            addNode(KuiverNode(id = "large", dimensions = NodeDimensions(200.dp, 100.dp)))
            addNode(KuiverNode(id = "medium", dimensions = NodeDimensions(100.dp, 60.dp)))
            addEdge(KuiverEdge(fromId = "small", toId = "large"))
            addEdge(KuiverEdge(fromId = "large", toId = "medium"))
        }

        val config = LayoutConfig(
            algorithm = LayoutAlgorithm.HIERARCHICAL,
            direction = LayoutDirection.HORIZONTAL,
            width = 800f,
            height = 600f
        )

        val result = layout(kuiver, config)

        println("\nNode dimensions and positions:")
        result.nodes.values.sortedBy { it.position.x }.forEach { node ->
            val width = node.dimensions?.width?.value ?: 0f
            val height = node.dimensions?.height?.value ?: 0f
            println("  ${node.id}: (${node.position.x}, ${node.position.y}) - ${width}x${height}")
        }

        // Check that spacing is based on largest node (200 width + 60 padding = 260)
        val small = result.nodes["small"]!!
        val large = result.nodes["large"]!!
        val medium = result.nodes["medium"]!!

        val spacing = large.position.x - small.position.x
        println("\nLevel spacing: $spacing")

        // Spacing should be at least largestWidth + padding (200 + 60 = 260)
        assertTrue(spacing >= 260f, "Spacing should accommodate largest node (200) + padding (60)")

        // Verify no overlap (nodes at different levels shouldn't overlap)
        assertTrue(small.position.x < large.position.x, "Nodes should be at different X positions")
        assertTrue(large.position.x < medium.position.x, "Nodes should be at different X positions")
    }
}
