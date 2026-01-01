package com.dk.kuiver.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dk.kuiver.model.EdgeType
import com.dk.kuiver.model.KuiverEdge
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun DefaultNodeContent(
    label: String,
    backgroundColor: Color,
    textColor: Color,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .shadow(3.dp, CircleShape)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            color = { textColor },
        )
    }
}

@Composable
fun EdgeContent(
    from: Offset,
    to: Offset,
    color: Color = Color.Black,
    strokeWidth: Float = 3f,
    showArrow: Boolean = true,
    dashed: Boolean = false,
    dashLength: Float = 10f,
    gapLength: Float = 5f,
    isSelfLoop: Boolean = false,
    loopRadius: Float = 40f,
    curveOffset: Float = 0f  // Offset for curved edges
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (isSelfLoop) {
            drawSelfLoopEdge(
                from = from,
                to = to,
                color = color,
                strokeWidth = strokeWidth,
                showArrow = showArrow,
                dashed = dashed,
                dashLength = dashLength,
                gapLength = gapLength,
                loopRadius = loopRadius
            )
        } else {
            drawEdge(
                from = from,
                to = to,
                color = color,
                strokeWidth = strokeWidth,
                showArrow = showArrow,
                dashed = dashed,
                dashLength = dashLength,
                gapLength = gapLength,
                curveOffset = curveOffset
            )
        }
    }
}

@Composable
fun OrthogonalEdgeContent(
    from: Offset,
    to: Offset,
    color: Color = Color.Black,
    strokeWidth: Float = 3f,
    showArrow: Boolean = true,
    dashed: Boolean = false,
    dashLength: Float = 10f,
    gapLength: Float = 5f,
    curveFactor: Float = 0.5f
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawOrthogonalEdge(
            from = from,
            to = to,
            color = color,
            strokeWidth = strokeWidth,
            showArrow = showArrow,
            dashed = dashed,
            dashLength = dashLength,
            gapLength = gapLength,
            curveFactor = curveFactor
        )
    }
}

// Edge drawing function
private fun DrawScope.drawEdge(
    from: Offset,
    to: Offset,
    color: Color,
    strokeWidth: Float,
    showArrow: Boolean,
    dashed: Boolean,
    dashLength: Float,
    gapLength: Float,
    curveOffset: Float = 0f  // Offset for curved edges to avoid overlap
) {
    val direction = Offset(to.x - from.x, to.y - from.y)
    val distance = sqrt(direction.x * direction.x + direction.y * direction.y)

    if (distance == 0f) return

    val normalizedDirection = Offset(direction.x / distance, direction.y / distance)

    // If curveOffset is specified, draw a curved edge instead of straight
    if (curveOffset != 0f) {
        drawCurvedEdge(from, to, color, strokeWidth, showArrow, dashed, dashLength, gapLength, curveOffset)
        return
    }

    // Arrow configuration
    val arrowSize = 20f
    val arrowOffset = 8f

    // For arrow overlap fix: shorten line by strokeWidth/2 + a small gap
    val lineGap = if (showArrow) strokeWidth / 2f + 2f else 0f

    val lineEnd = if (showArrow) {
        // Line ends before arrow with a small gap to prevent overlap
        Offset(
            to.x - normalizedDirection.x * (arrowOffset + lineGap),
            to.y - normalizedDirection.y * (arrowOffset + lineGap)
        )
    } else {
        to
    }

    // Draw line (solid or dashed)
    drawLine(
        color = color.copy(alpha = 0.8f),
        start = from,
        end = lineEnd,
        strokeWidth = strokeWidth,
        pathEffect = if (dashed) PathEffect.dashPathEffect(
            floatArrayOf(
                dashLength,
                gapLength
            )
        ) else null,
        cap = StrokeCap.Round
    )

    if (showArrow) {
        val angle = atan2(
            normalizedDirection.y.toDouble(),
            normalizedDirection.x.toDouble()
        ).toFloat()

        // Arrow base point is slightly before the end point
        val arrowBasePoint = Offset(
            to.x - normalizedDirection.x * arrowOffset,
            to.y - normalizedDirection.y * arrowOffset
        )

        val arrowPath = Path().apply {
            moveTo(arrowBasePoint.x, arrowBasePoint.y)
            lineTo(
                arrowBasePoint.x - arrowSize * cos(angle - 0.5).toFloat(),
                arrowBasePoint.y - arrowSize * sin(angle - 0.5).toFloat()
            )
            lineTo(
                arrowBasePoint.x - arrowSize * cos(angle + 0.5).toFloat(),
                arrowBasePoint.y - arrowSize * sin(angle + 0.5).toFloat()
            )
            close()
        }

        drawPath(path = arrowPath, color = color.copy(alpha = 1.0f))
    }
}

// Curved edge for parallel/back edges
private fun DrawScope.drawCurvedEdge(
    from: Offset,
    to: Offset,
    color: Color,
    strokeWidth: Float,
    showArrow: Boolean,
    dashed: Boolean,
    dashLength: Float,
    gapLength: Float,
    curveOffset: Float
) {
    // Calculate perpendicular offset for the curve
    val direction = Offset(to.x - from.x, to.y - from.y)
    val distance = sqrt(direction.x * direction.x + direction.y * direction.y)

    if (distance == 0f) return

    val normalizedDir = Offset(direction.x / distance, direction.y / distance)
    // Perpendicular direction (rotate 90 degrees)
    val perpendicular = Offset(-normalizedDir.y, normalizedDir.x)

    // Control point offset to the side
    // Make offset proportional to edge length for consistent arc curvature
    // Use a ratio approach: offset is a fraction of the edge length
    val proportionalOffset = distance * 0.25f  // 25% of edge length
    val actualOffset = if (curveOffset > 0f) proportionalOffset else 0f

    val midPoint = Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f)
    val controlPoint = Offset(
        midPoint.x + perpendicular.x * actualOffset,
        midPoint.y + perpendicular.y * actualOffset
    )

    // Arrow configuration (same as straight edge)
    val arrowSize = 20f
    val arrowOffset = 8f

    // Calculate tangent at the end of the curve for proper line shortening
    val tangent = Offset(to.x - controlPoint.x, to.y - controlPoint.y)
    val tangentDist = sqrt(tangent.x * tangent.x + tangent.y * tangent.y)

    val pathEnd = if (showArrow && tangentDist > 0f) {
        val normalizedTangent = Offset(tangent.x / tangentDist, tangent.y / tangentDist)
        // Shorten line by arrowOffset + gap (same as straight edge)
        val lineGap = strokeWidth / 2f + 2f
        Offset(
            to.x - normalizedTangent.x * (arrowOffset + lineGap),
            to.y - normalizedTangent.y * (arrowOffset + lineGap)
        )
    } else {
        to
    }

    // Create curved path (ending before arrow)
    val path = Path().apply {
        moveTo(from.x, from.y)
        quadraticTo(controlPoint.x, controlPoint.y, pathEnd.x, pathEnd.y)
    }

    // Draw the path
    drawPath(
        path = path,
        color = color.copy(alpha = 0.8f),
        style = Stroke(
            width = strokeWidth,
            pathEffect = if (dashed) PathEffect.dashPathEffect(
                floatArrayOf(dashLength, gapLength)
            ) else null,
            cap = StrokeCap.Round
        )
    )

    // Draw arrow at the end
    if (showArrow && tangentDist > 0f) {
        val normalizedTangent = Offset(tangent.x / tangentDist, tangent.y / tangentDist)
        val angle = atan2(normalizedTangent.y.toDouble(), normalizedTangent.x.toDouble()).toFloat()

        val arrowBasePoint = Offset(
            to.x - normalizedTangent.x * arrowOffset,
            to.y - normalizedTangent.y * arrowOffset
        )

        val arrowPath = Path().apply {
            moveTo(arrowBasePoint.x, arrowBasePoint.y)
            lineTo(
                arrowBasePoint.x - arrowSize * cos(angle - 0.5).toFloat(),
                arrowBasePoint.y - arrowSize * sin(angle - 0.5).toFloat()
            )
            lineTo(
                arrowBasePoint.x - arrowSize * cos(angle + 0.5).toFloat(),
                arrowBasePoint.y - arrowSize * sin(angle + 0.5).toFloat()
            )
            close()
        }

        drawPath(path = arrowPath, color = color.copy(alpha = 1.0f))
    }
}

// Self-loop edge drawing function with Bezier curve
private fun DrawScope.drawSelfLoopEdge(
    from: Offset,
    to: Offset,
    color: Color,
    strokeWidth: Float,
    showArrow: Boolean,
    dashed: Boolean,
    dashLength: Float,
    gapLength: Float,
    loopRadius: Float
) {
    // Calculate the center point between from and to
    val center = Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f)

    // Calculate control point for quadratic Bezier curve
    // Place it well above the node to create a visible arc
    val controlPoint = Offset(
        center.x,
        center.y - (loopRadius * 2f)  // Make it twice as tall for visibility
    )

    // Arrow configuration (same as straight edge)
    val arrowSize = 20f
    val arrowOffset = 8f

    // Calculate tangent at the end of the curve for arrow direction
    // For a quadratic Bezier, the tangent at t=1 is from control point to end point
    val direction = Offset(to.x - controlPoint.x, to.y - controlPoint.y)
    val distance = sqrt(direction.x * direction.x + direction.y * direction.y)

    val pathEnd = if (showArrow && distance > 0f) {
        val normalizedDirection = Offset(direction.x / distance, direction.y / distance)
        // Shorten line by arrowOffset + gap (same as straight edge)
        val lineGap = strokeWidth / 2f + 2f
        Offset(
            to.x - normalizedDirection.x * (arrowOffset + lineGap),
            to.y - normalizedDirection.y * (arrowOffset + lineGap)
        )
    } else {
        to
    }

    // Create path for the curved loop (ending before arrow)
    val path = Path().apply {
        moveTo(from.x, from.y)
        quadraticTo(
            controlPoint.x, controlPoint.y,
            pathEnd.x, pathEnd.y
        )
    }

    // Draw the curved path
    drawPath(
        path = path,
        color = color.copy(alpha = 0.8f),
        style = Stroke(
            width = strokeWidth,
            pathEffect = if (dashed) PathEffect.dashPathEffect(
                floatArrayOf(dashLength, gapLength)
            ) else null,
            cap = StrokeCap.Round
        )
    )

    // Draw arrow at the end point if requested
    if (showArrow && distance > 0f) {
        val normalizedDirection = Offset(direction.x / distance, direction.y / distance)
        val angle = atan2(
            normalizedDirection.y.toDouble(),
            normalizedDirection.x.toDouble()
        ).toFloat()

        val arrowBasePoint = Offset(
            to.x - normalizedDirection.x * arrowOffset,
            to.y - normalizedDirection.y * arrowOffset
        )

        val arrowPath = Path().apply {
            moveTo(arrowBasePoint.x, arrowBasePoint.y)
            lineTo(
                arrowBasePoint.x - arrowSize * cos(angle - 0.5).toFloat(),
                arrowBasePoint.y - arrowSize * sin(angle - 0.5).toFloat()
            )
            lineTo(
                arrowBasePoint.x - arrowSize * cos(angle + 0.5).toFloat(),
                arrowBasePoint.y - arrowSize * sin(angle + 0.5).toFloat()
            )
            close()
        }

        drawPath(path = arrowPath, color = color.copy(alpha = 1.0f))
    }
}

// Orthogonal edge drawing function (S-curve with horizontal tangents)
private fun DrawScope.drawOrthogonalEdge(
    from: Offset,
    to: Offset,
    color: Color,
    strokeWidth: Float,
    showArrow: Boolean,
    dashed: Boolean,
    dashLength: Float,
    gapLength: Float,
    curveFactor: Float
) {
    val dx = to.x - from.x

    val arrowSize = 20f
    val arrowOffset = 8f
    val lineGap = if (showArrow) strokeWidth / 2f + 2f else 0f

    // Control point distance - how far the curve extends horizontally
    val controlDistance = kotlin.math.abs(dx) * curveFactor

    // Calculate end point (shortened for arrow)
    val goingRight = dx > 0f
    val endX = if (showArrow) {
        if (goingRight) to.x - (arrowOffset + lineGap) else to.x + (arrowOffset + lineGap)
    } else {
        to.x
    }

    // Create S-curve using cubic Bezier
    val path = Path().apply {
        moveTo(from.x, from.y)
        cubicTo(
            from.x + controlDistance, from.y,  // First control point (horizontal from start)
            endX - controlDistance, to.y,       // Second control point (horizontal from end)
            endX, to.y                          // End point
        )
    }

    drawPath(
        path = path,
        color = color.copy(alpha = 0.8f),
        style = Stroke(
            width = strokeWidth,
            pathEffect = if (dashed) PathEffect.dashPathEffect(
                floatArrayOf(dashLength, gapLength)
            ) else null,
            cap = StrokeCap.Round
        )
    )

    if (showArrow) {
        val angle = if (goingRight) 0f else kotlin.math.PI.toFloat()

        val arrowBasePoint = Offset(
            if (goingRight) to.x - arrowOffset else to.x + arrowOffset,
            to.y
        )

        val arrowPath = Path().apply {
            moveTo(arrowBasePoint.x, arrowBasePoint.y)
            lineTo(
                arrowBasePoint.x - arrowSize * cos(angle - 0.5).toFloat(),
                arrowBasePoint.y - arrowSize * sin(angle - 0.5).toFloat()
            )
            lineTo(
                arrowBasePoint.x - arrowSize * cos(angle + 0.5).toFloat(),
                arrowBasePoint.y - arrowSize * sin(angle + 0.5).toFloat()
            )
            close()
        }

        drawPath(path = arrowPath, color = color.copy(alpha = 1.0f))
    }
}

/**
 * Enhanced EdgeContent that styles edges based on their type.
 * Self-loops are rendered as arcs above nodes.
 * Back edges (cycle-creating) are dashed with a subtle color tint for distinction.
 */
@Composable
fun StyledEdgeContent(
    edge: KuiverEdge,
    from: Offset,
    to: Offset,
    baseColor: Color = Color.Black,
    backEdgeColor: Color = Color(0xFFFF6B6B),
    strokeWidth: Float = 3f,
    loopRadius: Float = 40f
) {
    // Simplified styling: only self-loops and back edges are treated specially
    val (color, dashed) = when (edge.type) {
        EdgeType.SELF_LOOP -> Pair(backEdgeColor, true)
        EdgeType.BACK -> Pair(baseColor.copy(alpha = 0.7f), true)  // Subtle tint, dashed
        else -> Pair(baseColor, false)  // Normal forward/cross edges
    }

    // Determine if self-loop from edge IDs
    val isSelfLoop = edge.fromId == edge.toId

    EdgeContent(
        from = from,
        to = to,
        color = color,
        strokeWidth = strokeWidth,
        dashed = dashed,
        isSelfLoop = isSelfLoop,
        loopRadius = loopRadius,
        curveOffset = if (edge.type == EdgeType.BACK) 120f else 0f  // Curve back edges
    )
}
