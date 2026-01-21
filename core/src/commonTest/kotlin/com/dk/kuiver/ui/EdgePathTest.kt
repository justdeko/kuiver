package com.dk.kuiver.ui

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EdgePathTest {

    @Test
    fun `straight path endpoint and length`() {
        val withoutArrow = EdgePathFactory.createStraightPath(
            from = Offset(0f, 0f),
            to = Offset(100f, 0f),
            showArrow = false
        )
        assertEquals(100f, withoutArrow.pathEndpoint.x, 0.01f)
        assertEquals(100f, withoutArrow.edgeLength, 0.01f)

        val withArrow = EdgePathFactory.createStraightPath(
            from = Offset(0f, 0f),
            to = Offset(100f, 0f),
            showArrow = true,
            strokeWidth = 3f
        )
        assertTrue(withArrow.pathEndpoint.x < withArrow.to.x)

        val diagonal = EdgePathFactory.createStraightPath(
            from = Offset(0f, 0f),
            to = Offset(3f, 4f),
            showArrow = false
        )
        assertEquals(5f, diagonal.edgeLength, 0.01f)
    }

    @Test
    fun `curved path control point and endpoint`() {
        val path = EdgePathFactory.createCurvedPath(
            from = Offset(0f, 0f),
            to = Offset(100f, 0f),
            showArrow = true
        )

        assertEquals(50f, path.controlPoint.x, 0.01f)
        assertTrue(abs(path.controlPoint.y) > 0f)

        val expectedOffset = 100f * EdgeDrawingDefaults.CURVE_PROPORTIONAL_OFFSET
        assertEquals(expectedOffset, abs(path.controlPoint.y), 0.01f)

        assertTrue(path.pathEndpoint.x < path.to.x)
    }

    @Test
    fun `curved path handles zero-length edge`() {
        val path = EdgePathFactory.createCurvedPath(
            from = Offset(50f, 50f),
            to = Offset(50f, 50f),
            showArrow = false
        )
        assertEquals(0f, path.edgeLength, 0.01f)
        assertEquals(path.from, path.controlPoint)
    }

    @Test
    fun `self-loop control point positions above node`() {
        val path = EdgePathFactory.createSelfLoopPath(
            from = Offset(100f, 100f),
            to = Offset(110f, 100f),
            loopRadius = 40f,
            showArrow = true
        )

        val centerY = (path.from.y + path.to.y) / 2f
        assertTrue(path.controlPoint.y < centerY)

        val expectedY = centerY - (40f * EdgeDrawingDefaults.SELF_LOOP_HEIGHT_MULTIPLIER)
        assertEquals(expectedY, path.controlPoint.y, 0.01f)
    }

    @Test
    fun `orthogonal path creates S-curve with horizontal tangents`() {
        val rightward = EdgePathFactory.createOrthogonalPath(
            from = Offset(0f, 0f),
            to = Offset(100f, 100f),
            curveFactor = 0.5f,
            showArrow = true
        )

        assertEquals(rightward.from.y, rightward.controlPoint1.y, 0.01f)
        assertEquals(rightward.to.y, rightward.controlPoint2.y, 0.01f)

        val dx = rightward.to.x - rightward.from.x
        val expectedDistance = abs(dx) * 0.5f

        assertTrue(rightward.controlPoint1.x > rightward.from.x)
        assertEquals(rightward.from.x + expectedDistance, rightward.controlPoint1.x, 0.01f)
        assertTrue(rightward.controlPoint2.x < rightward.to.x)
        assertEquals(rightward.to.x - expectedDistance, rightward.controlPoint2.x, 0.01f)

        val leftward = EdgePathFactory.createOrthogonalPath(
            from = Offset(100f, 0f),
            to = Offset(0f, 100f),
            curveFactor = 0.5f,
            showArrow = false
        )

        assertTrue(leftward.controlPoint1.x > leftward.from.x)
        assertTrue(leftward.controlPoint2.x < leftward.to.x)
        assertEquals(leftward.from.y, leftward.controlPoint1.y, 0.01f)
        assertEquals(leftward.to.y, leftward.controlPoint2.y, 0.01f)

        assertTrue(rightward.pathEndpoint.x < rightward.to.x)
    }

    @Test
    fun `labels hide on short edges for all edge types`() {
        val paths = listOf(
            EdgePathFactory.createStraightPath(Offset(0f, 0f), Offset(10f, 0f), false),
            EdgePathFactory.createCurvedPath(Offset(0f, 0f), Offset(10f, 0f), false),
            EdgePathFactory.createSelfLoopPath(Offset(0f, 0f), Offset(5f, 0f), 5f, false),
            EdgePathFactory.createOrthogonalPath(Offset(0f, 0f), Offset(10f, 0f), 0.5f, false)
        )

        paths.forEach { path ->
            assertNull(path.calculateLabelPosition(0.5f, 50f))
        }
    }

    @Test
    fun `labels show on long edges`() {
        val straightPath = EdgePathFactory.createStraightPath(
            from = Offset(0f, 0f),
            to = Offset(100f, 0f),
            showArrow = false
        )

        val labelPos = straightPath.calculateLabelPosition(0.5f, 50f)
        assertNotNull(labelPos)
        assertEquals(50f, labelPos.position.x, 0.01f)
        assertEquals(0f, labelPos.position.y, 0.01f)
        assertEquals(0f, labelPos.angle, 0.01f)

        val curvedPath = EdgePathFactory.createCurvedPath(
            from = Offset(0f, 0f),
            to = Offset(100f, 0f),
            showArrow = false
        )
        assertNotNull(curvedPath.calculateLabelPosition(0.5f, 50f))
    }

    @Test
    fun `label offset clamped to valid range`() {
        val path = EdgePathFactory.createStraightPath(
            from = Offset(0f, 0f),
            to = Offset(100f, 0f),
            showArrow = false
        )

        val atEnd = path.calculateLabelPosition(5.0f, 10f)
        assertNotNull(atEnd)
        assertTrue(atEnd.position.x >= 99.9f)

        val atStart = path.calculateLabelPosition(-2.0f, 10f)
        assertNotNull(atStart)
        assertTrue(atStart.position.x <= 0.1f)
    }

    @Test
    fun `curved edge length estimated reasonably`() {
        val straight = EdgePathFactory.createStraightPath(
            from = Offset(0f, 0f),
            to = Offset(100f, 0f),
            showArrow = false
        )

        val curved = EdgePathFactory.createCurvedPath(
            from = Offset(0f, 0f),
            to = Offset(100f, 0f),
            showArrow = false
        )

        assertTrue(curved.edgeLength > straight.edgeLength)
        assertTrue(curved.edgeLength < straight.edgeLength * 2f)
    }

    @Test
    fun `label uses pathEndpoint not to when arrow shown`() {
        val path = EdgePathFactory.createCurvedPath(
            from = Offset(0f, 0f),
            to = Offset(100f, 0f),
            showArrow = true,
            strokeWidth = 3f
        )

        val labelPos = path.calculateLabelPosition(1.0f, 10f)
        assertNotNull(labelPos)
        assertTrue(labelPos.position.x < path.to.x)
    }

    private fun isReadableAngle(angleRadians: Float): Boolean {
        val twoPi = 2f * PI.toFloat()
        val normalized = ((angleRadians % twoPi) + twoPi) % twoPi
        return normalized <= PI.toFloat() / 2f + 0.01f || normalized >= 3f * PI.toFloat() / 2f - 0.01f
    }

    @Test
    fun `normalizeRotation keeps all angles in readable range`() {
        val testAngles = listOf(
            0f, PI.toFloat() / 4f, PI.toFloat() / 2f,
            3f * PI.toFloat() / 4f, PI.toFloat(),
            5f * PI.toFloat() / 4f, 3f * PI.toFloat() / 2f,
            -PI.toFloat() / 4f, -PI.toFloat(),
            405f * PI.toFloat() / 180f
        )
        testAngles.forEach { angle ->
            assertTrue(isReadableAngle(normalizeRotation(angle)))
        }
    }
}
