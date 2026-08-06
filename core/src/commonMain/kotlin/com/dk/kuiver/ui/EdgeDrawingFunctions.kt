package com.dk.kuiver.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.sqrt

private fun DrawScope.dashEffect(dashed: Boolean, dashLength: Dp, gapLength: Dp): PathEffect? =
    if (dashed) {
        PathEffect.dashPathEffect(floatArrayOf(dashLength.toPx(), gapLength.toPx()))
    } else {
        null
    }

internal fun DrawScope.drawStraightEdgePath(
    path: EdgePath.Straight,
    color: Color,
    strokeWidth: Dp,
    showArrow: Boolean,
    dashed: Boolean,
    dashLength: Dp,
    gapLength: Dp,
    arrowSize: Dp,
    arrowDrawer: ArrowDrawer
) {
    drawLine(
        color = color.copy(alpha = EdgeDrawingDefaults.LINE_ALPHA),
        start = path.from,
        end = path.pathEndpoint,
        strokeWidth = strokeWidth.toPx(),
        pathEffect = dashEffect(dashed, dashLength, gapLength),
        cap = StrokeCap.Round
    )

    if (showArrow && path.edgeLength > 0f) {
        val direction = Offset(path.to.x - path.from.x, path.to.y - path.from.y)
        val normalized = Offset(direction.x / path.edgeLength, direction.y / path.edgeLength)
        drawArrowAtEnd(path.to, normalized, color, arrowSize.toPx(), arrowDrawer)
    }
}

internal fun DrawScope.drawCurvedEdgePath(
    path: EdgePath.Curved,
    color: Color,
    strokeWidth: Dp,
    showArrow: Boolean,
    dashed: Boolean,
    dashLength: Dp,
    gapLength: Dp,
    arrowSize: Dp,
    arrowDrawer: ArrowDrawer
) {
    val curvePath = Path().apply {
        moveTo(path.from.x, path.from.y)
        quadraticTo(
            path.controlPoint.x,
            path.controlPoint.y,
            path.pathEndpoint.x,
            path.pathEndpoint.y
        )
    }

    drawPath(
        path = curvePath,
        color = color.copy(alpha = EdgeDrawingDefaults.LINE_ALPHA),
        style = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = dashEffect(dashed, dashLength, gapLength),
            cap = StrokeCap.Round
        )
    )

    if (showArrow) {
        val tangent = Offset(path.to.x - path.controlPoint.x, path.to.y - path.controlPoint.y)
        val tangentDist = sqrt(tangent.x * tangent.x + tangent.y * tangent.y)
        if (tangentDist > 0f) {
            val normalized = Offset(tangent.x / tangentDist, tangent.y / tangentDist)
            drawArrowAtEnd(path.to, normalized, color, arrowSize.toPx(), arrowDrawer)
        }
    }
}

internal fun DrawScope.drawSelfLoopEdgePath(
    path: EdgePath.SelfLoop,
    color: Color,
    strokeWidth: Dp,
    showArrow: Boolean,
    dashed: Boolean,
    dashLength: Dp,
    gapLength: Dp,
    arrowSize: Dp,
    arrowDrawer: ArrowDrawer
) {
    val loopPath = Path().apply {
        moveTo(path.from.x, path.from.y)
        quadraticTo(
            path.controlPoint.x,
            path.controlPoint.y,
            path.pathEndpoint.x,
            path.pathEndpoint.y
        )
    }

    drawPath(
        path = loopPath,
        color = color.copy(alpha = EdgeDrawingDefaults.LINE_ALPHA),
        style = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = dashEffect(dashed, dashLength, gapLength),
            cap = StrokeCap.Round
        )
    )

    if (showArrow) {
        val direction = Offset(path.to.x - path.controlPoint.x, path.to.y - path.controlPoint.y)
        val distance = sqrt(direction.x * direction.x + direction.y * direction.y)
        if (distance > 0f) {
            val normalized = Offset(direction.x / distance, direction.y / distance)
            drawArrowAtEnd(path.to, normalized, color, arrowSize.toPx(), arrowDrawer)
        }
    }
}

internal fun DrawScope.drawOrthogonalEdgePath(
    path: EdgePath.Orthogonal,
    color: Color,
    strokeWidth: Dp,
    showArrow: Boolean,
    dashed: Boolean,
    dashLength: Dp,
    gapLength: Dp,
    arrowSize: Dp,
    arrowDrawer: ArrowDrawer
) {
    val orthPath = Path().apply {
        moveTo(path.from.x, path.from.y)
        cubicTo(
            path.controlPoint1.x, path.controlPoint1.y,
            path.controlPoint2.x, path.controlPoint2.y,
            path.pathEndpoint.x, path.pathEndpoint.y
        )
    }

    drawPath(
        path = orthPath,
        color = color.copy(alpha = EdgeDrawingDefaults.LINE_ALPHA),
        style = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = dashEffect(dashed, dashLength, gapLength),
            cap = StrokeCap.Round
        )
    )

    if (showArrow) {
        // Cubic Bezier tangent at t=1 is proportional to (P3 - P2)
        val tangent = Offset(path.to.x - path.controlPoint2.x, path.to.y - path.controlPoint2.y)
        val tangentDist = sqrt(tangent.x * tangent.x + tangent.y * tangent.y)
        if (tangentDist > 0f) {
            val direction = Offset(tangent.x / tangentDist, tangent.y / tangentDist)
            drawArrowAtEnd(path.to, direction, color, arrowSize.toPx(), arrowDrawer)
        }
    }
}

internal fun DrawScope.drawRightAngleEdgePath(
    path: EdgePath.RightAngle,
    color: Color,
    strokeWidth: Dp,
    showArrow: Boolean,
    dashed: Boolean,
    dashLength: Dp,
    gapLength: Dp,
    arrowSize: Dp,
    arrowDrawer: ArrowDrawer
) {
    val rightAnglePath = Path().apply {
        moveTo(path.from.x, path.from.y)
        for (waypoint in path.waypoints) {
            lineTo(waypoint.x, waypoint.y)
        }
        lineTo(path.pathEndpoint.x, path.pathEndpoint.y)
    }

    drawPath(
        path = rightAnglePath,
        color = color.copy(alpha = EdgeDrawingDefaults.LINE_ALPHA),
        style = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = dashEffect(dashed, dashLength, gapLength),
            cap = StrokeCap.Round
        )
    )

    if (showArrow && path.arrowDirection != null) {
        drawArrowAtEnd(path.to, path.arrowDirection, color, arrowSize.toPx(), arrowDrawer)
    }
}

internal fun DrawScope.drawEdgePath(
    path: EdgePath,
    color: Color,
    strokeWidth: Dp,
    showArrow: Boolean,
    dashed: Boolean,
    dashLength: Dp,
    gapLength: Dp,
    arrowSize: Dp,
    arrowDrawer: ArrowDrawer
) {
    when (path) {
        is EdgePath.Straight ->
            drawStraightEdgePath(
                path = path,
                color = color,
                strokeWidth = strokeWidth,
                showArrow = showArrow,
                dashed = dashed,
                dashLength = dashLength,
                gapLength = gapLength,
                arrowSize = arrowSize,
                arrowDrawer = arrowDrawer
            )

        is EdgePath.Curved ->
            drawCurvedEdgePath(
                path = path,
                color = color,
                strokeWidth = strokeWidth,
                showArrow = showArrow,
                dashed = dashed,
                dashLength = dashLength,
                gapLength = gapLength,
                arrowSize = arrowSize,
                arrowDrawer = arrowDrawer
            )

        is EdgePath.SelfLoop ->
            drawSelfLoopEdgePath(
                path = path,
                color = color,
                strokeWidth = strokeWidth,
                showArrow = showArrow,
                dashed = dashed,
                dashLength = dashLength,
                gapLength = gapLength,
                arrowSize = arrowSize,
                arrowDrawer = arrowDrawer
            )

        is EdgePath.Orthogonal ->
            drawOrthogonalEdgePath(
                path = path,
                color = color,
                strokeWidth = strokeWidth,
                showArrow = showArrow,
                dashed = dashed,
                dashLength = dashLength,
                gapLength = gapLength,
                arrowSize = arrowSize,
                arrowDrawer = arrowDrawer
            )

        is EdgePath.RightAngle ->
            drawRightAngleEdgePath(
                path = path,
                color = color,
                strokeWidth = strokeWidth,
                showArrow = showArrow,
                dashed = dashed,
                dashLength = dashLength,
                gapLength = gapLength,
                arrowSize = arrowSize,
                arrowDrawer = arrowDrawer
            )
    }
}
