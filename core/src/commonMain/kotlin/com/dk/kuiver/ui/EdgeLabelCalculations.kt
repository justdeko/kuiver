package com.dk.kuiver.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.atan2

/**
 * Calculated position for an edge label along the edge path.
 *
 * @property position The center point for the label in canvas coordinates
 * @property angle The rotation angle in radians for tangent alignment (0 = horizontal right)
 */
@Immutable
data class EdgeLabelPosition(
    val position: Offset,
    val angle: Float = 0f
)

/**
 * Preset positions for label placement along an edge.
 *
 * Each preset maps to a parameter value t ∈ [0,1] along the edge curve:
 * - START: t = 0.25 (25% from source node)
 * - CENTER: t = 0.5 (midpoint)
 * - END: t = 0.75 (75% toward target node)
 */
enum class LabelPlacement(val offset: Float) {
    /** Label positioned near the start of the edge (25% along) */
    START(0.25f),

    /** Label positioned at the center of the edge (50% along) */
    CENTER(0.5f),

    /** Label positioned near the end of the edge (75% along) */
    END(0.75f)
}

/**
 * Calculates the label position along a straight edge.
 *
 * Uses linear interpolation between start and end points.
 *
 * @param from Start point of the edge
 * @param to End point of the edge
 * @param offset Position along the edge (0.0 = from, 1.0 = to)
 * @return Position and tangent angle for the label
 */
fun calculateStraightEdgeLabelPosition(
    from: Offset,
    to: Offset,
    offset: Float = 0.5f
): EdgeLabelPosition {
    // Clamp offset to [0, 1]
    val t = offset.coerceIn(0f, 1f)

    // Linear interpolation
    val position = Offset(
        from.x + (to.x - from.x) * t,
        from.y + (to.y - from.y) * t
    )

    // Calculate angle from direction vector
    val direction = Offset(to.x - from.x, to.y - from.y)
    val angle = atan2(direction.y, direction.x)

    return EdgeLabelPosition(position, angle)
}

/**
 * Calculates the label position along a curved edge using quadratic Bezier curve.
 *
 * Uses the quadratic Bezier formula:
 * B(t) = (1-t)²P₀ + 2(1-t)tP₁ + t²P₂
 *
 * where P₀ = from, P₁ = controlPoint, P₂ = to
 *
 * @param from Start point of the edge
 * @param to End point of the edge
 * @param controlPoint Control point for the quadratic Bezier curve
 * @param offset Position along the curve (0.0 = from, 1.0 = to)
 * @return Position and tangent angle for the label
 */
fun calculateCurvedEdgeLabelPosition(
    from: Offset,
    to: Offset,
    controlPoint: Offset,
    offset: Float = 0.5f
): EdgeLabelPosition {
    // Clamp offset to [0, 1]
    val t = offset.coerceIn(0f, 1f)

    // Quadratic Bezier curve formula: B(t) = (1-t)²P₀ + 2(1-t)tP₁ + t²P₂
    val oneMinusT = 1f - t
    val term0 = oneMinusT * oneMinusT
    val term1 = 2f * oneMinusT * t
    val term2 = t * t

    val position = Offset(
        term0 * from.x + term1 * controlPoint.x + term2 * to.x,
        term0 * from.y + term1 * controlPoint.y + term2 * to.y
    )

    // Calculate tangent vector (derivative): B'(t) = 2(1-t)(P₁-P₀) + 2t(P₂-P₁)
    val tangent = Offset(
        2f * oneMinusT * (controlPoint.x - from.x) + 2f * t * (to.x - controlPoint.x),
        2f * oneMinusT * (controlPoint.y - from.y) + 2f * t * (to.y - controlPoint.y)
    )

    val angle = atan2(tangent.y, tangent.x)

    return EdgeLabelPosition(position, angle)
}

/**
 * Calculates the label position along an orthogonal edge using cubic Bezier curve (S-curve).
 *
 * Orthogonal edges use a cubic Bezier with horizontal tangents at start and end points.
 * Formula: B(t) = (1-t)³P₀ + 3(1-t)²tP₁ + 3(1-t)t²P₂ + t³P₃
 *
 * @param p0 Start point of the curve
 * @param p1 First control point
 * @param p2 Second control point
 * @param p3 End point of the curve
 * @param offset Position along the curve (0.0 = start, 1.0 = end)
 * @return Position and tangent angle for the label
 */
fun calculateCubicBezierLabelPosition(
    p0: Offset,
    p1: Offset,
    p2: Offset,
    p3: Offset,
    offset: Float = 0.5f
): EdgeLabelPosition {
    val t = offset.coerceIn(0f, 1f)
    val oneMinusT = 1f - t
    val term0 = oneMinusT * oneMinusT * oneMinusT
    val term1 = 3f * oneMinusT * oneMinusT * t
    val term2 = 3f * oneMinusT * t * t
    val term3 = t * t * t

    val position = Offset(
        term0 * p0.x + term1 * p1.x + term2 * p2.x + term3 * p3.x,
        term0 * p0.y + term1 * p1.y + term2 * p2.y + term3 * p3.y
    )

    val tangent = Offset(
        3f * oneMinusT * oneMinusT * (p1.x - p0.x) +
                6f * oneMinusT * t * (p2.x - p1.x) +
                3f * t * t * (p3.x - p2.x),
        3f * oneMinusT * oneMinusT * (p1.y - p0.y) +
                6f * oneMinusT * t * (p2.y - p1.y) +
                3f * t * t * (p3.y - p2.y)
    )

    return EdgeLabelPosition(position, atan2(tangent.y, tangent.x))
}

private const val PI_FLOAT = PI.toFloat()
private const val TWO_PI_FLOAT = 2f * PI_FLOAT

/**
 * Flips angles in (90°, 270°) by 180° so labels stay readable.
 */
internal fun normalizeRotation(angleRadians: Float): Float {
    val normalized = ((angleRadians % TWO_PI_FLOAT) + TWO_PI_FLOAT) % TWO_PI_FLOAT
    val halfPi = PI_FLOAT / 2f
    val threeHalfPi = 3f * PI_FLOAT / 2f
    return if (normalized > halfPi && normalized < threeHalfPi) {
        (normalized + PI_FLOAT) % TWO_PI_FLOAT
    } else {
        normalized
    }
}
