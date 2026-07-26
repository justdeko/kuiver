package com.dk.kuiver.util

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.dk.kuiver.assertDpEquals
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.model.NodeDimensions
import kotlin.test.Test
import kotlin.test.assertEquals

class BoundsUtilsTest {

    private fun node(id: String, x: Int, y: Int, size: Int = 20) = KuiverNode(
        id = id,
        dimensions = NodeDimensions(size.dp, size.dp),
        position = DpOffset(x.dp, y.dp)
    )

    @Test
    fun `bounds span the nodes on both axes`() {
        val bounds = listOf(node("A", -100, -50), node("B", 100, 50)).calculateNodeBounds()

        assertDpEquals((-110).dp, bounds.minX)
        assertDpEquals(110.dp, bounds.maxX)
        assertDpEquals((-60).dp, bounds.minY)
        assertDpEquals(60.dp, bounds.maxY)
        assertDpEquals(220.dp, bounds.width)
        assertDpEquals(120.dp, bounds.height)
        assertDpEquals(0.dp, bounds.centerX)
        assertDpEquals(0.dp, bounds.centerY)
    }

    @Test
    fun `a graph entirely left of the origin is bounded by its own nodes`() {
        // The maxima used to start at the smallest positive float, so a graph that never crossed
        // into positive coordinates was bounded at ~0 and measured far too wide
        val bounds = listOf(node("A", -400, -300), node("B", -200, -100)).calculateNodeBounds()

        assertDpEquals((-190).dp, bounds.maxX)
        assertDpEquals((-90).dp, bounds.maxY)
        assertDpEquals(220.dp, bounds.width)
        assertDpEquals(220.dp, bounds.height)
    }

    @Test
    fun `position bounds ignore node dimensions`() {
        val bounds = listOf(node("A", -100, -50), node("B", 100, 50)).calculatePositionBounds()

        assertDpEquals(200.dp, bounds.width)
        assertDpEquals(100.dp, bounds.height)
    }

    @Test
    fun `an empty graph has empty bounds`() {
        assertEquals(Bounds.EMPTY, emptyList<KuiverNode>().calculateNodeBounds())
    }
}
