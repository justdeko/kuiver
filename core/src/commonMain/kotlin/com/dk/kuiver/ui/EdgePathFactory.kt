package com.dk.kuiver.ui

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Factory functions to create EdgePath instances.
 *
 * Encapsulates all control point calculation logic that was previously
 * duplicated between drawing functions and label positioning code.
 * Provides a single source of truth for edge geometry.
 */
object EdgePathFactory {

    /**
     * Create a straight edge path.
     *
     * @param from Start point of the edge
     * @param to End point of the edge
     * @param showArrow Whether the edge will have an arrow (affects pathEndpoint)
     * @param strokeWidth Width of the edge line (affects arrow gap calculation)
     * @return EdgePath.Straight with calculated endpoint and length
     */
    fun createStraightPath(
        from: Offset,
        to: Offset,
        showArrow: Boolean = true,
        strokeWidth: Float = 3f
    ): EdgePath.Straight {
        val direction = Offset(to.x - from.x, to.y - from.y)
        val distance = sqrt(direction.x * direction.x + direction.y * direction.y)

        val pathEndpoint = if (showArrow && distance > 0f) {
            val normalized = Offset(direction.x / distance, direction.y / distance)
            val lineGap = strokeWidth / 2f + EdgeDrawingDefaults.ARROW_GAP_FROM_LINE
            val shortenBy = EdgeDrawingDefaults.ARROW_OFFSET + lineGap
            Offset(
                to.x - normalized.x * shortenBy,
                to.y - normalized.y * shortenBy
            )
        } else {
            to
        }

        return EdgePath.Straight(
            from = from,
            to = to,
            pathEndpoint = pathEndpoint,
            edgeLength = distance
        )
    }

    /**
     * Create a curved edge path using quadratic Bezier.
     *
     * The control point is calculated perpendicular to the edge direction,
     * offset proportionally to the edge length to maintain consistent curvature.
     *
     * @param from Start point of the edge
     * @param to End point of the edge
     * @param showArrow Whether the edge will have an arrow
     * @param strokeWidth Width of the edge line
     * @return EdgePath.Curved with calculated control point and endpoint
     */
    fun createCurvedPath(
        from: Offset,
        to: Offset,
        showArrow: Boolean = true,
        strokeWidth: Float = 3f
    ): EdgePath.Curved {
        // Calculate direction and distance
        val direction = Offset(to.x - from.x, to.y - from.y)
        val distance = sqrt(direction.x * direction.x + direction.y * direction.y)

        if (distance == 0f) {
            // Degenerate case: from and to are the same point
            return EdgePath.Curved(
                from = from,
                to = to,
                controlPoint = from,
                pathEndpoint = to,
                edgeLength = 0f
            )
        }

        val normalizedDir = Offset(direction.x / distance, direction.y / distance)

        // Calculate perpendicular direction (rotate 90 degrees)
        val perpendicular = Offset(-normalizedDir.y, normalizedDir.x)

        // Control point offset proportional to edge length (25% of distance)
        val proportionalOffset = distance * EdgeDrawingDefaults.CURVE_PROPORTIONAL_OFFSET

        val midPoint = Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f)
        val controlPoint = Offset(
            midPoint.x + perpendicular.x * proportionalOffset,
            midPoint.y + perpendicular.y * proportionalOffset
        )

        // Calculate tangent at the end of the curve for arrow positioning
        val tangent = Offset(to.x - controlPoint.x, to.y - controlPoint.y)
        val tangentDist = sqrt(tangent.x * tangent.x + tangent.y * tangent.y)

        val pathEndpoint = if (showArrow && tangentDist > 0f) {
            val normalized = Offset(tangent.x / tangentDist, tangent.y / tangentDist)
            val lineGap = strokeWidth / 2f + EdgeDrawingDefaults.ARROW_GAP_FROM_LINE
            Offset(
                to.x - normalized.x * (EdgeDrawingDefaults.ARROW_OFFSET + lineGap),
                to.y - normalized.y * (EdgeDrawingDefaults.ARROW_OFFSET + lineGap)
            )
        } else {
            to
        }

        // Estimate curve length (simple approximation using chord length)
        val curveLength = estimateQuadraticBezierLength(from, controlPoint, to)

        return EdgePath.Curved(
            from = from,
            to = to,
            controlPoint = controlPoint,
            pathEndpoint = pathEndpoint,
            edgeLength = curveLength
        )
    }

    /**
     * Create a self-loop edge path using quadratic Bezier.
     *
     * The control point is positioned above the node to create a visible arc.
     *
     * @param from Start point of the self-loop
     * @param to End point of the self-loop (typically close to from)
     * @param loopRadius Radius that determines the height of the loop
     * @param showArrow Whether the edge will have an arrow
     * @param strokeWidth Width of the edge line
     * @return EdgePath.SelfLoop with calculated control point and endpoint
     */
    fun createSelfLoopPath(
        from: Offset,
        to: Offset,
        loopRadius: Float = 40f,
        showArrow: Boolean = true,
        strokeWidth: Float = 3f
    ): EdgePath.SelfLoop {
        // Calculate center point between from and to
        val center = Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f)

        // Control point well above the node
        val controlPoint = Offset(
            center.x,
            center.y - (loopRadius * EdgeDrawingDefaults.SELF_LOOP_HEIGHT_MULTIPLIER)
        )

        // Calculate tangent at the end for arrow direction
        val direction = Offset(to.x - controlPoint.x, to.y - controlPoint.y)
        val distance = sqrt(direction.x * direction.x + direction.y * direction.y)

        val pathEndpoint = if (showArrow && distance > 0f) {
            val normalizedDirection = Offset(direction.x / distance, direction.y / distance)
            val lineGap = strokeWidth / 2f + EdgeDrawingDefaults.ARROW_GAP_FROM_LINE
            Offset(
                to.x - normalizedDirection.x * (EdgeDrawingDefaults.ARROW_OFFSET + lineGap),
                to.y - normalizedDirection.y * (EdgeDrawingDefaults.ARROW_OFFSET + lineGap)
            )
        } else {
            to
        }

        val loopLength = estimateQuadraticBezierLength(from, controlPoint, to)

        return EdgePath.SelfLoop(
            from = from,
            to = to,
            controlPoint = controlPoint,
            pathEndpoint = pathEndpoint,
            edgeLength = loopLength
        )
    }

    /**
     * Create an orthogonal edge path using cubic Bezier (S-curve).
     *
     * The control points create horizontal tangents at both start and end,
     * resulting in a smooth S-shaped curve ideal for hierarchical layouts.
     *
     * @param from Start point of the edge
     * @param to End point of the edge
     * @param curveFactor How far the control points extend horizontally (0.0 to 1.0)
     * @param showArrow Whether the edge will have an arrow
     * @param strokeWidth Width of the edge line
     * @return EdgePath.Orthogonal with calculated control points and endpoint
     */
    fun createOrthogonalPath(
        from: Offset,
        to: Offset,
        curveFactor: Float = 0.5f,
        showArrow: Boolean = true,
        strokeWidth: Float = 3f
    ): EdgePath.Orthogonal {
        val dx = to.x - from.x

        // Control point distance - how far the curve extends horizontally
        val controlDistance = abs(dx) * curveFactor

        // Calculate control points for S-curve (horizontal tangents)
        val controlPoint1 = Offset(from.x + controlDistance, from.y)
        val controlPoint2 = Offset(to.x - controlDistance, to.y)

        // Calculate endpoint (shortened for arrow)
        val goingRight = dx > 0f
        val lineGap = if (showArrow) strokeWidth / 2f + EdgeDrawingDefaults.ARROW_GAP_FROM_LINE else 0f

        val endX = if (showArrow) {
            if (goingRight) {
                to.x - (EdgeDrawingDefaults.ARROW_OFFSET + lineGap)
            } else {
                to.x + (EdgeDrawingDefaults.ARROW_OFFSET + lineGap)
            }
        } else {
            to.x
        }

        val pathEndpoint = Offset(endX, to.y)

        // Estimate S-curve length
        val curveLength = estimateCubicBezierLength(from, controlPoint1, controlPoint2, to)

        return EdgePath.Orthogonal(
            from = from,
            to = to,
            controlPoint1 = controlPoint1,
            controlPoint2 = controlPoint2,
            pathEndpoint = pathEndpoint,
            edgeLength = curveLength,
            curveFactor = curveFactor
        )
    }

    /**
     * Estimate the length of a quadratic Bezier curve.
     *
     * Uses a simple approximation based on the chord length and control polygon length.
     * This is more accurate than straight-line distance but faster than numerical integration.
     *
     * @param p0 Start point
     * @param p1 Control point
     * @param p2 End point
     * @return Approximate arc length
     */
    private fun estimateQuadraticBezierLength(p0: Offset, p1: Offset, p2: Offset): Float {
        // Chord length (straight line from start to end)
        val chordLength = distance(p0, p2)

        // Control polygon length (p0->p1 + p1->p2)
        val polyLength = distance(p0, p1) + distance(p1, p2)

        // Weighted average provides good approximation
        // (chord + 2*polygon) / 3
        return (chordLength + 2f * polyLength) / 3f
    }

    /**
     * Estimate the length of a cubic Bezier curve.
     *
     * Similar approximation to quadratic, but with two control points.
     *
     * @param p0 Start point
     * @param p1 First control point
     * @param p2 Second control point
     * @param p3 End point
     * @return Approximate arc length
     */
    private fun estimateCubicBezierLength(
        p0: Offset,
        p1: Offset,
        p2: Offset,
        p3: Offset
    ): Float {
        // Chord length
        val chordLength = distance(p0, p3)

        // Control polygon length (p0->p1 + p1->p2 + p2->p3)
        val polyLength = distance(p0, p1) + distance(p1, p2) + distance(p2, p3)

        // Weighted average
        return (chordLength + 2f * polyLength) / 3f
    }

    /**
     * Calculate Euclidean distance between two points.
     */
    private fun distance(a: Offset, b: Offset): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return sqrt(dx * dx + dy * dy)
    }
}
