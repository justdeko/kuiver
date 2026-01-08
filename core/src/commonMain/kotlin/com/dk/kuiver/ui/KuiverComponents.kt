package com.dk.kuiver.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk.kuiver.model.EdgeType
import com.dk.kuiver.model.KuiverEdge
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal object EdgeDrawingDefaults {
    const val ARROW_SIZE = 20f
    const val ARROW_OFFSET = 8f
    const val ARROW_ANGLE_SPREAD = 0.5f
    const val ARROW_GAP_FROM_LINE = 2f
    const val LINE_ALPHA = 0.8f
    const val CURVE_PROPORTIONAL_OFFSET = 0.25f
    const val SELF_LOOP_HEIGHT_MULTIPLIER = 2f
}

/**
 * Functional type for custom arrow drawing.
 *
 * @param arrowTip The point where the arrow tip should be positioned
 * @param direction The normalized direction vector pointing in the arrow direction
 * @param color The color to use for the arrow
 */
typealias ArrowDrawer = DrawScope.(
    arrowTip: Offset,
    direction: Offset,
    color: Color
) -> Unit

/**
 * Default arrow drawer that draws a filled triangle.
 */
val DefaultArrowDrawer: ArrowDrawer = { arrowTip, direction, color ->
    val angle = atan2(direction.y.toDouble(), direction.x.toDouble()).toFloat()
    val arrowSize = EdgeDrawingDefaults.ARROW_SIZE
    val arrowOffset = EdgeDrawingDefaults.ARROW_OFFSET
    val arrowAngleSpread = EdgeDrawingDefaults.ARROW_ANGLE_SPREAD

    val arrowBasePoint = Offset(
        arrowTip.x - direction.x * arrowOffset,
        arrowTip.y - direction.y * arrowOffset
    )

    val arrowPath = Path().apply {
        moveTo(arrowBasePoint.x, arrowBasePoint.y)
        lineTo(
            arrowBasePoint.x - arrowSize * cos(angle - arrowAngleSpread),
            arrowBasePoint.y - arrowSize * sin(angle - arrowAngleSpread)
        )
        lineTo(
            arrowBasePoint.x - arrowSize * cos(angle + arrowAngleSpread),
            arrowBasePoint.y - arrowSize * sin(angle + arrowAngleSpread)
        )
        close()
    }
    drawPath(path = arrowPath, color = color.copy(alpha = 1.0f))
}

/**
 * Helper function to draw an arrow at the end of an edge.
 */
private fun DrawScope.drawArrowAtEnd(
    endPoint: Offset,
    direction: Offset,
    color: Color,
    arrowDrawer: ArrowDrawer
) {
    arrowDrawer(endPoint, direction, color)
}

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

/**
 * Configuration for edge label styling.
 *
 * @property textColor Color of the label text (default: black)
 * @property backgroundColor Background color of the label box (default: white with 90% opacity)
 * @property fontSize Font size of the label text (default: 12.sp)
 * @property padding Inner padding of the label box (default: 4.dp)
 * @property borderColor Color of the label border, null for no border (default: black with 30% opacity)
 * @property borderWidth Width of the label border (default: 1.dp)
 * @property cornerRadius Corner radius of the label box (default: 4.dp)
 * @property maxLines Maximum number of lines for the label text (default: 1)
 * @property overflow Text overflow behavior (default: TextOverflow.Ellipsis)
 * @property rotateWithEdge Whether to rotate the label to align with edge tangent (default: false)
 */
@Immutable
data class EdgeLabelStyle(
    val textColor: Color = Color.Black,
    val backgroundColor: Color = Color.White.copy(alpha = 0.9f),
    val fontSize: TextUnit = 12.sp,
    val padding: Dp = 4.dp,
    val borderColor: Color? = Color.Black.copy(alpha = 0.3f),
    val borderWidth: Dp = 1.dp,
    val cornerRadius: Dp = 4.dp,
    val maxLines: Int = 1,
    val overflow: TextOverflow = TextOverflow.Ellipsis,
    val rotateWithEdge: Boolean = false
)

/**
 * Default composable for rendering edge labels.
 *
 * Renders text with a background box, optional border, and padding to ensure readability
 * over edges and other graph elements.
 *
 * @param label The text to display in the label
 * @param modifier Modifier to apply to the label container
 * @param style Styling configuration for the label
 */
@Composable
fun DefaultEdgeLabel(
    label: String,
    modifier: Modifier = Modifier,
    style: EdgeLabelStyle = EdgeLabelStyle()
) {
    val shape = RoundedCornerShape(style.cornerRadius)

    Box(
        modifier = modifier
            .background(style.backgroundColor, shape)
            .then(
                if (style.borderColor != null) {
                    Modifier.border(style.borderWidth, style.borderColor, shape)
                } else {
                    Modifier
                }
            )
            .padding(style.padding),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            color = { style.textColor },
            style = TextStyle(
                fontSize = style.fontSize,
                color = style.textColor
            ),
            maxLines = style.maxLines,
            overflow = style.overflow
        )
    }
}

/**
 * Enhanced edge content that supports customizable labels.
 *
 * Renders an edge with optional label positioned along the edge path. Supports
 * all edge types (straight, curved, self-loops) and provides full customization
 * of label position, style, and content. Labels automatically hide on edges
 * shorter than minEdgeLengthForLabel and rotate to stay readable.
 *
 * @param from Start point of the edge
 * @param to End point of the edge
 * @param label Optional label text to display on the edge
 * @param labelOffset Position along the edge (0.0 = from, 1.0 = to). Must be in range [0, 1]
 * @param labelPlacement Preset position (START, CENTER, END). Takes precedence over labelOffset
 * @param labelStyle Styling configuration for the label
 * @param labelContent Optional custom composable for label rendering (overrides default)
 * @param color Edge line color
 * @param strokeWidth Width of the edge line. Must be positive
 * @param showArrow Whether to show an arrow at the end
 * @param dashed Whether the edge should be dashed
 * @param dashLength Length of dashes (if dashed)
 * @param gapLength Length of gaps between dashes (if dashed)
 * @param isSelfLoop Whether this is a self-loop edge
 * @param loopRadius Radius for self-loop arcs. Must be positive
 * @param enableCurve Whether to curve the edge (for back edges)
 * @param arrowDrawer Custom arrow drawing function
 * @param minEdgeLengthForLabel Minimum edge length to show label. Must be non-negative
 */
@Composable
fun EdgeContentWithLabel(
    from: Offset,
    to: Offset,
    label: String? = null,
    labelOffset: Float? = null,
    labelPlacement: LabelPlacement? = null,
    labelStyle: EdgeLabelStyle = EdgeLabelStyle(),
    labelContent: (@Composable (String) -> Unit)? = null,
    color: Color = Color.Black,
    strokeWidth: Float = 3f,
    showArrow: Boolean = true,
    dashed: Boolean = false,
    dashLength: Float = 10f,
    gapLength: Float = 5f,
    isSelfLoop: Boolean = false,
    loopRadius: Float = 40f,
    enableCurve: Boolean = false,
    arrowDrawer: ArrowDrawer = DefaultArrowDrawer,
    minEdgeLengthForLabel: Float = 50f
) {
    require(labelOffset == null || labelOffset in 0f..1f) {
        "labelOffset must be in range [0, 1], got $labelOffset"
    }
    require(strokeWidth > 0f) { "strokeWidth must be positive, got $strokeWidth" }
    require(minEdgeLengthForLabel >= 0f) {
        "minEdgeLengthForLabel must be non-negative, got $minEdgeLengthForLabel"
    }
    require(loopRadius > 0f) { "loopRadius must be positive, got $loopRadius" }

    val offset = labelPlacement?.offset ?: labelOffset ?: 0.5f

    val edgePath = remember(from, to, isSelfLoop, enableCurve, loopRadius, showArrow, strokeWidth) {
        when {
            isSelfLoop -> EdgePathFactory.createSelfLoopPath(from, to, loopRadius, showArrow, strokeWidth)
            enableCurve -> EdgePathFactory.createCurvedPath(from, to, showArrow, strokeWidth)
            else -> EdgePathFactory.createStraightPath(from, to, showArrow, strokeWidth)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        EdgeContent(
            from = from,
            to = to,
            color = color,
            strokeWidth = strokeWidth,
            showArrow = showArrow,
            dashed = dashed,
            dashLength = dashLength,
            gapLength = gapLength,
            isSelfLoop = isSelfLoop,
            loopRadius = loopRadius,
            enableCurve = enableCurve,
            arrowDrawer = arrowDrawer
        )

        if (label != null && label.isNotBlank()) {
            val labelPosition = remember(edgePath, offset, minEdgeLengthForLabel) {
                edgePath.calculateLabelPosition(offset, minEdgeLengthForLabel)
            }

            labelPosition?.let { pos ->
                EdgeLabel(label, pos, labelStyle, labelContent)
            }
        }
    }
}

/**
 * Enhanced EdgeContent that styles edges based on their type.
 * Self-loops are rendered as arcs above nodes.
 * Back edges (cycle-creating) are dashed and curved.
 *
 * @param edge The edge to render
 * @param from Start point of the edge
 * @param to End point of the edge
 * @param baseColor Color for forward/tree edges (default: black)
 * @param backEdgeColor Color for back edges and self-loops (default: red)
 * @param strokeWidth Width of the edge line
 * @param loopRadius Radius for self-loop arcs
 * @param arrowDrawer Custom arrow drawing function
 * @param label Optional label text to display on the edge
 * @param labelOffset Position along the edge (0.0 = from, 1.0 = to). Must be in range [0, 1]
 * @param labelPlacement Preset position (START, CENTER, END). Takes precedence over labelOffset
 * @param labelStyle Styling configuration for the label
 * @param labelContent Optional custom composable for label rendering (overrides default)
 */
@Composable
fun StyledEdgeContent(
    edge: KuiverEdge,
    from: Offset,
    to: Offset,
    baseColor: Color = Color.Black,
    backEdgeColor: Color = Color(0xFFFF6B6B),
    strokeWidth: Float = 3f,
    loopRadius: Float = 40f,
    arrowDrawer: ArrowDrawer = DefaultArrowDrawer,
    label: String? = null,
    labelOffset: Float? = null,
    labelPlacement: LabelPlacement? = null,
    labelStyle: EdgeLabelStyle = EdgeLabelStyle(),
    labelContent: (@Composable (String) -> Unit)? = null
) {
    val (color, dashed) = when (edge.type) {
        EdgeType.SELF_LOOP -> Pair(backEdgeColor, true)
        EdgeType.BACK -> Pair(baseColor.copy(alpha = 0.7f), true)
        else -> Pair(baseColor, false)
    }

    val isSelfLoop = edge.fromId == edge.toId

    EdgeContentWithLabel(
        from = from,
        to = to,
        label = label,
        labelOffset = labelOffset,
        labelPlacement = labelPlacement,
        labelStyle = labelStyle,
        labelContent = labelContent,
        color = color,
        strokeWidth = strokeWidth,
        dashed = dashed,
        isSelfLoop = isSelfLoop,
        loopRadius = loopRadius,
        enableCurve = edge.type == EdgeType.BACK,
        arrowDrawer = arrowDrawer
    )
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
    enableCurve: Boolean = false,
    arrowDrawer: ArrowDrawer = DefaultArrowDrawer
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
                loopRadius = loopRadius,
                arrowDrawer = arrowDrawer
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
                enableCurve = enableCurve,
                arrowDrawer = arrowDrawer
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
    curveFactor: Float = 0.5f,
    arrowDrawer: ArrowDrawer = DefaultArrowDrawer
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
            curveFactor = curveFactor,
            arrowDrawer = arrowDrawer
        )
    }
}

/**
 * Orthogonal edge with S-curve path and label support.
 *
 * Renders an edge with horizontal tangents at both ends, creating a smooth
 * S-curve path. Supports customizable labels positioned along the curve path.
 * Labels automatically hide on edges shorter than minEdgeLengthForLabel and
 * rotate to stay readable.
 *
 * @param from Start point of the edge
 * @param to End point of the edge
 * @param label Optional label text to display on the edge
 * @param labelOffset Position along the edge (0.0 = from, 1.0 = to). Must be in range [0, 1]
 * @param labelPlacement Preset position (START, CENTER, END). Takes precedence over labelOffset
 * @param labelStyle Styling configuration for the label
 * @param labelContent Optional custom composable for label rendering (overrides default)
 * @param color Edge line color
 * @param strokeWidth Width of the edge line. Must be positive
 * @param showArrow Whether to show an arrow at the end
 * @param dashed Whether the edge should be dashed
 * @param dashLength Length of dashes (if dashed)
 * @param gapLength Length of gaps between dashes (if dashed)
 * @param curveFactor How far control points extend horizontally (0.0-1.0, default 0.5)
 * @param arrowDrawer Custom arrow drawing function
 * @param minEdgeLengthForLabel Minimum edge length to show label. Must be non-negative
 */
@Composable
fun OrthogonalEdgeContentWithLabel(
    from: Offset,
    to: Offset,
    label: String? = null,
    labelOffset: Float? = null,
    labelPlacement: LabelPlacement? = null,
    labelStyle: EdgeLabelStyle = EdgeLabelStyle(),
    labelContent: (@Composable (String) -> Unit)? = null,
    color: Color = Color.Black,
    strokeWidth: Float = 3f,
    showArrow: Boolean = true,
    dashed: Boolean = false,
    dashLength: Float = 10f,
    gapLength: Float = 5f,
    curveFactor: Float = 0.5f,
    arrowDrawer: ArrowDrawer = DefaultArrowDrawer,
    minEdgeLengthForLabel: Float = 50f
) {
    require(labelOffset == null || labelOffset in 0f..1f) {
        "labelOffset must be in range [0, 1], got $labelOffset"
    }
    require(strokeWidth > 0f) { "strokeWidth must be positive, got $strokeWidth" }
    require(minEdgeLengthForLabel >= 0f) {
        "minEdgeLengthForLabel must be non-negative, got $minEdgeLengthForLabel"
    }

    val offset = labelPlacement?.offset ?: labelOffset ?: 0.5f

    val edgePath = remember(from, to, curveFactor, showArrow, strokeWidth) {
        EdgePathFactory.createOrthogonalPath(from, to, curveFactor, showArrow, strokeWidth)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        OrthogonalEdgeContent(
            from = from,
            to = to,
            color = color,
            strokeWidth = strokeWidth,
            showArrow = showArrow,
            dashed = dashed,
            dashLength = dashLength,
            gapLength = gapLength,
            curveFactor = curveFactor,
            arrowDrawer = arrowDrawer
        )

        if (label != null && label.isNotBlank()) {
            val labelPosition = remember(edgePath, offset, minEdgeLengthForLabel) {
                edgePath.calculateLabelPosition(offset, minEdgeLengthForLabel)
            }

            labelPosition?.let { pos ->
                EdgeLabel(label, pos, labelStyle, labelContent)
            }
        }
    }
}

private fun DrawScope.drawStraightEdgePath(
    path: EdgePath.Straight,
    color: Color,
    strokeWidth: Float,
    showArrow: Boolean,
    dashed: Boolean,
    dashLength: Float,
    gapLength: Float,
    arrowDrawer: ArrowDrawer
) {
    drawLine(
        color = color.copy(alpha = EdgeDrawingDefaults.LINE_ALPHA),
        start = path.from,
        end = path.pathEndpoint,
        strokeWidth = strokeWidth,
        pathEffect = if (dashed) PathEffect.dashPathEffect(
            floatArrayOf(dashLength, gapLength)
        ) else null,
        cap = StrokeCap.Round
    )

    if (showArrow && path.edgeLength > 0f) {
        val direction = Offset(path.to.x - path.from.x, path.to.y - path.from.y)
        val normalized = Offset(direction.x / path.edgeLength, direction.y / path.edgeLength)
        drawArrowAtEnd(path.to, normalized, color, arrowDrawer)
    }
}

private fun DrawScope.drawCurvedEdgePath(
    path: EdgePath.Curved,
    color: Color,
    strokeWidth: Float,
    showArrow: Boolean,
    dashed: Boolean,
    dashLength: Float,
    gapLength: Float,
    arrowDrawer: ArrowDrawer
) {
    val curvePath = Path().apply {
        moveTo(path.from.x, path.from.y)
        quadraticTo(path.controlPoint.x, path.controlPoint.y, path.pathEndpoint.x, path.pathEndpoint.y)
    }

    drawPath(
        path = curvePath,
        color = color.copy(alpha = EdgeDrawingDefaults.LINE_ALPHA),
        style = Stroke(
            width = strokeWidth,
            pathEffect = if (dashed) PathEffect.dashPathEffect(
                floatArrayOf(dashLength, gapLength)
            ) else null,
            cap = StrokeCap.Round
        )
    )

    if (showArrow) {
        val tangent = Offset(path.to.x - path.controlPoint.x, path.to.y - path.controlPoint.y)
        val tangentDist = sqrt(tangent.x * tangent.x + tangent.y * tangent.y)
        if (tangentDist > 0f) {
            val normalized = Offset(tangent.x / tangentDist, tangent.y / tangentDist)
            drawArrowAtEnd(path.to, normalized, color, arrowDrawer)
        }
    }
}

private fun DrawScope.drawSelfLoopEdgePath(
    path: EdgePath.SelfLoop,
    color: Color,
    strokeWidth: Float,
    showArrow: Boolean,
    dashed: Boolean,
    dashLength: Float,
    gapLength: Float,
    arrowDrawer: ArrowDrawer
) {
    val loopPath = Path().apply {
        moveTo(path.from.x, path.from.y)
        quadraticTo(path.controlPoint.x, path.controlPoint.y, path.pathEndpoint.x, path.pathEndpoint.y)
    }

    drawPath(
        path = loopPath,
        color = color.copy(alpha = EdgeDrawingDefaults.LINE_ALPHA),
        style = Stroke(
            width = strokeWidth,
            pathEffect = if (dashed) PathEffect.dashPathEffect(
                floatArrayOf(dashLength, gapLength)
            ) else null,
            cap = StrokeCap.Round
        )
    )

    if (showArrow) {
        val direction = Offset(path.to.x - path.controlPoint.x, path.to.y - path.controlPoint.y)
        val distance = sqrt(direction.x * direction.x + direction.y * direction.y)
        if (distance > 0f) {
            val normalized = Offset(direction.x / distance, direction.y / distance)
            drawArrowAtEnd(path.to, normalized, color, arrowDrawer)
        }
    }
}

private fun DrawScope.drawOrthogonalEdgePath(
    path: EdgePath.Orthogonal,
    color: Color,
    strokeWidth: Float,
    showArrow: Boolean,
    dashed: Boolean,
    dashLength: Float,
    gapLength: Float,
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
            width = strokeWidth,
            pathEffect = if (dashed) PathEffect.dashPathEffect(
                floatArrayOf(dashLength, gapLength)
            ) else null,
            cap = StrokeCap.Round
        )
    )

    if (showArrow) {
        val goingRight = path.to.x > path.from.x
        val direction = if (goingRight) Offset(1f, 0f) else Offset(-1f, 0f)
        drawArrowAtEnd(path.to, direction, color, arrowDrawer)
    }
}

private fun DrawScope.drawEdge(
    from: Offset,
    to: Offset,
    color: Color,
    strokeWidth: Float,
    showArrow: Boolean,
    dashed: Boolean,
    dashLength: Float,
    gapLength: Float,
    enableCurve: Boolean,
    arrowDrawer: ArrowDrawer
) {
    val edgePath = if (enableCurve) {
        EdgePathFactory.createCurvedPath(from, to, showArrow, strokeWidth)
    } else {
        EdgePathFactory.createStraightPath(from, to, showArrow, strokeWidth)
    }

    when (edgePath) {
        is EdgePath.Straight -> drawStraightEdgePath(
            edgePath, color, strokeWidth, showArrow, dashed, dashLength, gapLength, arrowDrawer
        )
        is EdgePath.Curved -> drawCurvedEdgePath(
            edgePath, color, strokeWidth, showArrow, dashed, dashLength, gapLength, arrowDrawer
        )
        else -> {}
    }
}

private fun DrawScope.drawSelfLoopEdge(
    from: Offset,
    to: Offset,
    color: Color,
    strokeWidth: Float,
    showArrow: Boolean,
    dashed: Boolean,
    dashLength: Float,
    gapLength: Float,
    loopRadius: Float,
    arrowDrawer: ArrowDrawer
) {
    val path = EdgePathFactory.createSelfLoopPath(from, to, loopRadius, showArrow, strokeWidth)
    drawSelfLoopEdgePath(path, color, strokeWidth, showArrow, dashed, dashLength, gapLength, arrowDrawer)
}

private fun DrawScope.drawOrthogonalEdge(
    from: Offset,
    to: Offset,
    color: Color,
    strokeWidth: Float,
    showArrow: Boolean,
    dashed: Boolean,
    dashLength: Float,
    gapLength: Float,
    curveFactor: Float,
    arrowDrawer: ArrowDrawer
) {
    val path = EdgePathFactory.createOrthogonalPath(from, to, curveFactor, showArrow, strokeWidth)
    drawOrthogonalEdgePath(path, color, strokeWidth, showArrow, dashed, dashLength, gapLength, arrowDrawer)
}
