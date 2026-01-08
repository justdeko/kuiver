package com.dk.kuiver.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset

/**
 * Represents the geometric path and metadata for an edge.
 *
 * This abstraction encapsulates all geometric calculations for edges, providing
 * a single source of truth for:
 * - Control points (for curved/orthogonal edges)
 * - Path endpoints (accounting for arrows)
 * - Label positioning
 * - Edge length
 *
 * By centralizing these calculations, we eliminate duplication between edge drawing
 * functions and label positioning logic, ensuring consistency and maintainability.
 *
 * @property from Start point of the edge path
 * @property to End point of the edge path
 * @property pathEndpoint Where the visible line ends (shortened for arrow if applicable)
 * @property edgeLength Total geometric length of the edge
 */
@Immutable
sealed class EdgePath {
    abstract val from: Offset
    abstract val to: Offset
    abstract val pathEndpoint: Offset
    abstract val edgeLength: Float

    /**
     * Calculate label position at a given offset along the path.
     *
     * This method calculates both the position and rotation angle for a label
     * positioned along this edge path. The calculation automatically accounts
     * for edge curvature and ensures the label aligns with the edge tangent.
     *
     * @param offset Position along curve (0.0 = start, 1.0 = end). Will be clamped to [0, 1].
     * @param minEdgeLength Minimum edge length to show label. Returns null if edge is shorter.
     * @return Label position with rotation angle, or null if edge is too short
     */
    abstract fun calculateLabelPosition(
        offset: Float,
        minEdgeLength: Float = 50f
    ): EdgeLabelPosition?

    /**
     * Straight edge between two points using linear interpolation.
     *
     * @property from Start point of the edge
     * @property to End point of the edge
     * @property pathEndpoint Where the line ends (may be shortened for arrow)
     * @property edgeLength Distance between from and to
     */
    @Immutable
    data class Straight(
        override val from: Offset,
        override val to: Offset,
        override val pathEndpoint: Offset,
        override val edgeLength: Float
    ) : EdgePath() {
        override fun calculateLabelPosition(
            offset: Float,
            minEdgeLength: Float
        ): EdgeLabelPosition? {
            if (edgeLength < minEdgeLength) return null
            return calculateStraightEdgeLabelPosition(from, pathEndpoint, offset)
        }
    }

    /**
     * Curved edge using quadratic Bezier curve.
     *
     * Used for back edges to visually distinguish them from forward edges
     * and avoid overlapping parallel edges.
     *
     * @property from Start point of the edge
     * @property to End point of the edge
     * @property controlPoint Control point for the quadratic Bezier curve
     * @property pathEndpoint Where the curve ends (may be shortened for arrow)
     * @property edgeLength Approximate arc length of the curve
     */
    @Immutable
    data class Curved(
        override val from: Offset,
        override val to: Offset,
        val controlPoint: Offset,
        override val pathEndpoint: Offset,
        override val edgeLength: Float
    ) : EdgePath() {
        override fun calculateLabelPosition(
            offset: Float,
            minEdgeLength: Float
        ): EdgeLabelPosition? {
            if (edgeLength < minEdgeLength) return null
            return calculateCurvedEdgeLabelPosition(from, pathEndpoint, controlPoint, offset)
        }
    }

    /**
     * Self-loop edge using quadratic Bezier curve from node back to itself.
     *
     * The control point is typically positioned above the node to create
     * a visible arc.
     *
     * @property from Start point of the self-loop
     * @property to End point of the self-loop (typically close to from)
     * @property controlPoint Control point for the loop (usually above the node)
     * @property pathEndpoint Where the loop ends (may be shortened for arrow)
     * @property edgeLength Approximate arc length of the loop
     */
    @Immutable
    data class SelfLoop(
        override val from: Offset,
        override val to: Offset,
        val controlPoint: Offset,
        override val pathEndpoint: Offset,
        override val edgeLength: Float
    ) : EdgePath() {
        override fun calculateLabelPosition(
            offset: Float,
            minEdgeLength: Float
        ): EdgeLabelPosition? {
            if (edgeLength < minEdgeLength) return null
            // Self-loops use the same quadratic Bezier calculation as curved edges
            return calculateCurvedEdgeLabelPosition(from, pathEndpoint, controlPoint, offset)
        }
    }

    /**
     * Orthogonal edge using cubic Bezier (S-curve) with horizontal tangents.
     *
     * Used in hierarchical layouts where edges should flow horizontally
     * from nodes before curving to connect.
     *
     * @property from Start point of the edge
     * @property to End point of the edge
     * @property controlPoint1 First control point (horizontal tangent from start)
     * @property controlPoint2 Second control point (horizontal tangent to end)
     * @property pathEndpoint Where the curve ends (may be shortened for arrow)
     * @property edgeLength Approximate arc length of the S-curve
     * @property curveFactor How far the control points extend horizontally
     */
    @Immutable
    data class Orthogonal(
        override val from: Offset,
        override val to: Offset,
        val controlPoint1: Offset,
        val controlPoint2: Offset,
        override val pathEndpoint: Offset,
        override val edgeLength: Float,
        val curveFactor: Float
    ) : EdgePath() {
        override fun calculateLabelPosition(
            offset: Float,
            minEdgeLength: Float
        ): EdgeLabelPosition? {
            if (edgeLength < minEdgeLength) return null
            return calculateCubicBezierLabelPosition(
                from, controlPoint1, controlPoint2, pathEndpoint, offset
            )
        }
    }
}
