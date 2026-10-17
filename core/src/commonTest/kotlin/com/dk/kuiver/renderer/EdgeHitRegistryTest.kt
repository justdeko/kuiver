package com.dk.kuiver.renderer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import com.dk.kuiver.KuiverViewerState
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.ui.EdgePathFactory
import com.dk.kuiver.ui.RightAngleRouting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EdgeHitRegistryTest {

    private val density = Density(1f)

    @Test
    fun `distance to a straight path is measured to the nearest point including the arrow tip`() {
        val path = EdgePathFactory.createStraightPath(Offset(0f, 0f), Offset(100f, 0f), density)

        assertEquals(10f, path.distanceTo(Offset(50f, 10f)), 0.01f)
        assertEquals(0f, path.distanceTo(Offset(100f, 0f)), 0.01f)
        assertEquals(5f, path.distanceTo(Offset(105f, 0f)), 0.01f)
    }

    @Test
    fun `distance to a curved path follows the arc`() {
        val path = EdgePathFactory.createCurvedPath(
            Offset(0f, 0f), Offset(100f, 0f), density, showArrow = false, bend = 0.5f
        )

        // Control point 50 below the chord puts the middle of the arc 25 below it
        assertEquals(0f, path.distanceTo(Offset(50f, 25f)), 0.5f)
        assertEquals(25f, path.distanceTo(Offset(50f, 0f)), 0.5f)
    }

    @Test
    fun `distance to a right angle path follows every segment`() {
        val path = EdgePathFactory.createRightAnglePath(
            Offset(0f, 0f),
            Offset(100f, 100f),
            RightAngleRouting.HORIZONTAL_FIRST,
            density
        )

        assertEquals(0f, path.distanceTo(Offset(50f, 0f)), 0.01f)
        assertEquals(0f, path.distanceTo(Offset(100f, 50f)), 0.01f)
        assertEquals(50f, path.distanceTo(Offset(50f, 50f)), 0.01f)
    }

    @Test
    fun `hit test picks the closest edge within the tolerance and follows reported paths`() {
        val registry = EdgeHitRegistry()
        registry.report(
            "near",
            EdgePathFactory.createStraightPath(Offset(0f, 0f), Offset(100f, 0f), density)
        )
        registry.report(
            "far",
            EdgePathFactory.createStraightPath(Offset(0f, 6f), Offset(100f, 6f), density)
        )

        assertEquals("near", registry.hitTest(Offset(50f, 2f), tolerance = 8f))
        assertEquals("far", registry.hitTest(Offset(50f, 5f), tolerance = 8f))
        assertNull(registry.hitTest(Offset(50f, 30f), tolerance = 8f))

        registry.report(
            "near",
            EdgePathFactory.createStraightPath(Offset(0f, 50f), Offset(100f, 50f), density)
        )
        assertEquals("far", registry.hitTest(Offset(50f, 2f), tolerance = 8f))
        assertEquals("near", registry.hitTest(Offset(50f, 50f), tolerance = 8f))

        registry.clear("far")
        assertNull(registry.hitTest(Offset(50f, 2f), tolerance = 8f))

        registry.retain(emptySet())
        assertNull(registry.hitTest(Offset(50f, 50f), tolerance = 8f))
    }

    @Test
    fun `selecting a node or edge drops the other kind and toggling keeps it`() {
        val interaction = KuiverViewerState(Kuiver()).interaction

        interaction.select("A")
        interaction.selectEdge("A->B")
        assertEquals(emptySet(), interaction.selectedNodeIds)
        assertEquals(setOf("A->B"), interaction.selectedEdgeIds)

        interaction.toggleSelection("A")
        assertEquals(setOf("A"), interaction.selectedNodeIds)
        assertEquals(setOf("A->B"), interaction.selectedEdgeIds)

        interaction.selectAll(setOf("B"))
        assertEquals(emptySet(), interaction.selectedEdgeIds)

        interaction.toggleEdgeSelection("A->B")
        interaction.clearSelection()
        assertEquals(emptySet(), interaction.selectedNodeIds)
        assertEquals(emptySet(), interaction.selectedEdgeIds)
    }
}
