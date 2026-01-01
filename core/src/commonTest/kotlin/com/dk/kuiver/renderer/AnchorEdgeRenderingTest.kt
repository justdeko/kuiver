package com.dk.kuiver.renderer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dk.kuiver.model.AnchorOffset
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode
import kotlin.test.Test
import kotlin.test.assertEquals

class AnchorEdgeRenderingTest {

    private val testDensity = object : Density {
        override val density: Float = 1f
        override val fontScale: Float = 1f
    }

    @Test
    fun `both anchors registered`() {
        val registry = AnchorPositionRegistry()
        registry.registerAnchor("node1", "right", AnchorOffset(50.dp, 25.dp))
        registry.registerAnchor("node2", "left", AnchorOffset(0.dp, 25.dp))

        val (start, end) = calculateEdgeEndpointsWithAnchors(
            edge = KuiverEdge("node1", "node2", fromAnchor = "right", toAnchor = "left"),
            fromNode = KuiverNode("node1"),
            toNode = KuiverNode("node2"),
            fromCenter = Offset(100f, 100f),
            toCenter = Offset(200f, 100f),
            fromNodeWidth = 50f,
            fromNodeHeight = 50f,
            toNodeWidth = 50f,
            toNodeHeight = 50f,
            anchorRegistry = registry,
            isSelfLoop = false,
            density = testDensity
        )

        // Start: node1 top-left (75,75) + anchor offset (50,25) = (125,100)
        assertEquals(125f, start.x, 0.01f)
        assertEquals(100f, start.y, 0.01f)
        // End: node2 top-left (175,75) + anchor offset (0,25) = (175,100)
        assertEquals(175f, end.x, 0.01f)
        assertEquals(100f, end.y, 0.01f)
    }

    @Test
    fun `only start anchor registered`() {
        val registry = AnchorPositionRegistry()
        registry.registerAnchor("node1", "bottom", AnchorOffset(25.dp, 50.dp))

        val (start, end) = calculateEdgeEndpointsWithAnchors(
            edge = KuiverEdge("node1", "node2", fromAnchor = "bottom", toAnchor = null),
            fromNode = KuiverNode("node1"),
            toNode = KuiverNode("node2"),
            fromCenter = Offset(100f, 100f),
            toCenter = Offset(100f, 200f),
            fromNodeWidth = 50f,
            fromNodeHeight = 50f,
            toNodeWidth = 50f,
            toNodeHeight = 50f,
            anchorRegistry = registry,
            isSelfLoop = false,
            density = testDensity
        )

        // Start: node1 top-left (75,75) + anchor offset (25,50) = (100,125)
        assertEquals(100f, start.x, 0.01f)
        assertEquals(125f, start.y, 0.01f)
        // End: calculated geometrically from start point toward node2 center, node2 top - padding = 175 - 4 = 171
        assertEquals(100f, end.x, 0.01f)
        assertEquals(171f, end.y, 0.01f)
    }

    @Test
    fun `only end anchor registered`() {
        val registry = AnchorPositionRegistry()
        registry.registerAnchor("node2", "top", AnchorOffset(25.dp, 0.dp))

        val (start, end) = calculateEdgeEndpointsWithAnchors(
            edge = KuiverEdge("node1", "node2", fromAnchor = null, toAnchor = "top"),
            fromNode = KuiverNode("node1"),
            toNode = KuiverNode("node2"),
            fromCenter = Offset(100f, 100f),
            toCenter = Offset(100f, 200f),
            fromNodeWidth = 50f,
            fromNodeHeight = 50f,
            toNodeWidth = 50f,
            toNodeHeight = 50f,
            anchorRegistry = registry,
            isSelfLoop = false,
            density = testDensity
        )

        // Start: calculated geometrically from end point back toward node1 center, node1 bottom + padding = 125 + 4 = 129
        assertEquals(100f, start.x, 0.01f)
        assertEquals(129f, start.y, 0.01f)
        // End: node2 top-left (75,175) + anchor offset (25,0) = (100,175)
        assertEquals(100f, end.x, 0.01f)
        assertEquals(175f, end.y, 0.01f)
    }

    @Test
    fun `no anchors registered`() {
        val registry = AnchorPositionRegistry()

        val (start, end) = calculateEdgeEndpointsWithAnchors(
            edge = KuiverEdge("node1", "node2"),
            fromNode = KuiverNode("node1"),
            toNode = KuiverNode("node2"),
            fromCenter = Offset(100f, 100f),
            toCenter = Offset(200f, 100f),
            fromNodeWidth = 50f,
            fromNodeHeight = 50f,
            toNodeWidth = 50f,
            toNodeHeight = 50f,
            anchorRegistry = registry,
            isSelfLoop = false,
            density = testDensity
        )

        // Start: node1 right edge + padding = 125 + 4 = 129, End: node2 left edge - padding = 175 - 4 = 171
        assertEquals(129f, start.x, 0.01f)
        assertEquals(100f, start.y, 0.01f)
        assertEquals(171f, end.x, 0.01f)
        assertEquals(100f, end.y, 0.01f)
    }
}
