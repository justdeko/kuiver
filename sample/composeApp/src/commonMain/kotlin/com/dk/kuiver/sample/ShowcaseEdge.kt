package com.dk.kuiver.sample

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dk.kuiver.model.EdgeType
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.ui.AnchorSide
import com.dk.kuiver.ui.EdgeCanvas
import com.dk.kuiver.ui.EdgePath
import com.dk.kuiver.ui.EdgePathFactory
import com.dk.kuiver.ui.LocalEdgeBend
import com.dk.kuiver.ui.RightAngleRouting
import com.dk.kuiver.ui.boundingRect
import kotlin.math.min

/**
 * How lit up an edge is while the pipeline runs.
 */
internal enum class EdgeState { IDLE, LIT, FLOWING }

/**
 * State of an edge from the state of its steps. Parcels flow into an active step, loops flow
 * while retrying or after a failure, and an edge between two finished steps stays lit.
 */
internal fun edgeState(edge: KuiverEdge, statuses: Map<String, StepStatus>): EdgeState {
    val from = statuses[edge.fromId] ?: StepStatus.WAITING
    val to = statuses[edge.toId] ?: StepStatus.WAITING
    val toActive = to == StepStatus.ACTIVE || to == StepStatus.RETRYING
    return when {
        edge.fromId == edge.toId -> if (from == StepStatus.RETRYING) EdgeState.FLOWING else EdgeState.IDLE
        edge.type == EdgeType.BACK -> if (from == StepStatus.FAILED && toActive) EdgeState.FLOWING else EdgeState.IDLE
        toActive && from == StepStatus.DONE -> EdgeState.FLOWING
        from == StepStatus.DONE && to == StepStatus.DONE -> EdgeState.LIT
        else -> EdgeState.IDLE
    }
}

/**
 * Path of an edge in the chosen style, built from the same [EdgePathFactory] the built-in edges
 * use.
 *
 * @param edge the edge, for its type and anchors
 * @param from start point in pixels
 * @param to end point in pixels
 * @param style the chosen edge style
 * @return the path, remembered across frames with the same endpoints
 */
@Composable
internal fun rememberShowcaseEdgePath(
    edge: KuiverEdge,
    from: Offset,
    to: Offset,
    style: ShowcaseEdgeStyle
): EdgePath {
    val density = LocalDensity.current
    val bend = LocalEdgeBend.current
    return remember(edge, from, to, style, bend, density) {
        val fromSide = AnchorSide.fromAnchorId(edge.fromAnchor)
        val toSide = AnchorSide.fromAnchorId(edge.toAnchor)
        when {
            edge.fromId == edge.toId -> EdgePathFactory.createSelfLoopPath(
                from, to, density, loopRadius = 30.dp, strokeWidth = 2.dp, bend = bend ?: 0f
            )

            // Fan parallel edges out wider than the default to give their labels room
            bend != null && bend != 0f -> EdgePathFactory.createCurvedPath(
                from, to, density, strokeWidth = 2.dp, bend = bend * 3f
            )

            edge.type == EdgeType.BACK -> EdgePathFactory.createCurvedPath(
                from, to, density, strokeWidth = 2.dp, bend = 0.3f
            )

            style == ShowcaseEdgeStyle.STRAIGHT -> EdgePathFactory.createStraightPath(
                from, to, density, strokeWidth = 2.dp
            )

            style == ShowcaseEdgeStyle.CIRCUIT -> EdgePathFactory.createRightAnglePath(
                from, to, RightAngleRouting.fromAnchorSides(fromSide, toSide), density,
                strokeWidth = 2.dp
            )

            fromSide == AnchorSide.TOP || fromSide == AnchorSide.BOTTOM ->
                verticalSCurve(from, to, density)

            fromSide == AnchorSide.LEFT || fromSide == AnchorSide.RIGHT ->
                EdgePathFactory.createOrthogonalPath(from, to, density, strokeWidth = 2.dp)

            else -> EdgePathFactory.createCurvedPath(
                from, to, density, strokeWidth = 2.dp, bend = 0.12f
            )
        }
    }
}

/**
 * S-curve with vertical tangents, the top to bottom twin of
 * [EdgePathFactory.createOrthogonalPath].
 */
private fun verticalSCurve(from: Offset, to: Offset, density: Density): EdgePath.Orthogonal {
    val reach = (to.y - from.y) * 0.5f
    val control1 = Offset(from.x, from.y + reach)
    val control2 = Offset(to.x, to.y - reach)
    val direction = (to - control2).let { if (it.getDistance() == 0f) to - from else it }
    val shorten = with(density) { 4.dp.toPx() }
    val endpoint = to - direction / direction.getDistance().coerceAtLeast(1f) * shorten

    // Length along a handful of samples, enough for particle spacing
    var length = 0f
    var previous = from
    for (step in 1..16) {
        val point = cubicPoint(from, control1, control2, to, step / 16f)
        length += (point - previous).getDistance()
        previous = point
    }
    return EdgePath.Orthogonal(from, to, control1, control2, endpoint, length, curveFactor = 0.5f)
}

/**
 * Point on a cubic Bezier curve.
 */
private fun cubicPoint(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
    val u = 1f - t
    return p0 * (u * u * u) + p1 * (3f * u * u * t) + p2 * (3f * u * t * t) + p3 * (t * t * t)
}

/**
 * Edge drawn on an [EdgeCanvas]: a thin line that takes on [color] once lit, marching dashes for
 * loops, dots while parcels flow, and a label.
 *
 * @param path the edge path, also the hit path for tap and hover
 * @param color color of the edge once lit or emphasized
 * @param idleColor color of the edge before its steps run
 * @param state how lit up the edge is
 * @param dashed whether the edge is dashed, for loops
 * @param emphasized whether the edge is hovered or selected
 * @param dimmed whether another node or edge has the focus
 * @param label label text, none when null
 * @param labelAt where the label sits along the edge, 0 at the start and 1 at the end
 * @param flowPhase shared 0..1 phase of the dots, read while drawing only
 */
@Composable
internal fun FlowEdge(
    path: EdgePath,
    color: Color,
    idleColor: Color,
    state: EdgeState,
    dashed: Boolean,
    emphasized: Boolean,
    dimmed: Boolean,
    label: String?,
    labelAt: Float,
    flowPhase: State<Float>
) {
    val density = LocalDensity.current
    val emphasis = animateFloatAsState(if (emphasized) 1f else 0f, label = "edge_emphasis")
    val alpha = animateFloatAsState(if (dimmed) 0.2f else 1f, label = "edge_alpha")
    val tint = animateColorAsState(
        if (state == EdgeState.IDLE && !emphasized) idleColor else color,
        tween(400),
        label = "edge_tint"
    )
    val drawnPath = remember(path) { path.toComposePath() }
    val bounds = remember(path, density) {
        path.boundingRect(density, strokeWidth = 12.dp, arrowSize = 12.dp, extraPadding = 8.dp)
    }
    val flowing = state == EdgeState.FLOWING

    EdgeCanvas(bounds, hitPath = path) {
        val visibility = alpha.value
        val lineColor = tint.value
        val strength = emphasis.value
        val corners = PathEffect.cornerPathEffect(12.dp.toPx())

        // Soft state layer behind hovered and selected edges
        if (strength > 0f) {
            drawPath(
                path = drawnPath,
                color = lineColor.copy(alpha = 0.16f * strength * visibility),
                style = Stroke(
                    width = 10.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = corners
                )
            )
        }

        val dashEffect = if (dashed) {
            val dash = 6.dp.toPx()
            val gap = 5.dp.toPx()
            // March the dashes while the loop is active
            val phase = if (flowing) -flowPhase.value * (dash + gap) * 3f else 0f
            PathEffect.chainPathEffect(PathEffect.dashPathEffect(floatArrayOf(dash, gap), phase), corners)
        } else {
            corners
        }
        drawPath(
            path = drawnPath,
            color = lineColor.copy(alpha = visibility),
            style = Stroke(
                width = (2f + 1.5f * strength).dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = dashEffect
            )
        )
        drawArrowHead(path, lineColor.copy(alpha = visibility), (9f + 2f * strength).dp.toPx())

        if (flowing) drawParticles(path, color, flowPhase.value, visibility)
    }

    if (label != null) EdgeLabelPill(path, label, labelAt, alpha)
}

/**
 * Label in a pill along the edge, hidden on short edges.
 */
@Composable
private fun EdgeLabelPill(path: EdgePath, label: String, at: Float, alpha: State<Float>) {
    val density = LocalDensity.current
    val minLength = with(density) { 70.dp.toPx() }
    val position = path.calculateLabelPosition(at, minLength)?.position ?: return

    Box(
        modifier = Modifier
            .zIndex(1f)
            .wrapContentSize(unbounded = true)
            .graphicsLayer {
                translationX = position.x - size.width / 2f
                translationY = position.y - size.height / 2f
                this.alpha = alpha.value
            }
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

/**
 * Dots travelling from start to end, fading in and out at the nodes.
 */
private fun DrawScope.drawParticles(path: EdgePath, color: Color, phase: Float, alpha: Float) {
    val count = (path.edgeLength / 80.dp.toPx()).toInt().coerceIn(1, 6)
    repeat(count) { index ->
        val t = (phase + index.toFloat() / count) % 1f
        val point = path.calculateLabelPosition(t, 0f)?.position ?: return@repeat
        val fade = (min(t, 1f - t) * 6f).coerceAtMost(1f) * alpha
        drawCircle(color.copy(alpha = fade), radius = 4.dp.toPx(), center = point)
    }
}

/**
 * Notched arrow head at the end of the path.
 */
private fun DrawScope.drawArrowHead(path: EdgePath, color: Color, length: Float) {
    val direction = path.endDirection()
    val distance = direction.getDistance()
    if (distance == 0f) return
    val unit = direction / distance
    val normal = Offset(-unit.y, unit.x) * (length * 0.55f)
    val tip = path.to
    val base = tip - unit * length
    val arrow = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base.x + normal.x, base.y + normal.y)
        val notch = tip - unit * (length * 0.7f)
        lineTo(notch.x, notch.y)
        lineTo(base.x - normal.x, base.y - normal.y)
        close()
    }
    drawPath(arrow, color)
}

/**
 * Direction the path arrives at its end point in.
 */
private fun EdgePath.endDirection(): Offset {
    val direction = when (this) {
        is EdgePath.Straight -> to - from
        is EdgePath.Curved -> to - controlPoint
        is EdgePath.SelfLoop -> to - controlPoint
        is EdgePath.Orthogonal -> to - controlPoint2
        is EdgePath.RightAngle -> arrowDirection ?: (to - (waypoints.lastOrNull() ?: from))
    }
    return if (direction.getDistance() == 0f) to - from else direction
}

/**
 * The path as a drawable [Path], ending where the arrow head starts.
 */
private fun EdgePath.toComposePath(): Path = Path().apply {
    val edgePath = this@toComposePath
    moveTo(from.x, from.y)
    when (edgePath) {
        is EdgePath.Straight -> lineTo(pathEndpoint.x, pathEndpoint.y)
        is EdgePath.Curved -> quadraticTo(
            edgePath.controlPoint.x, edgePath.controlPoint.y, pathEndpoint.x, pathEndpoint.y
        )

        is EdgePath.SelfLoop -> quadraticTo(
            edgePath.controlPoint.x, edgePath.controlPoint.y, pathEndpoint.x, pathEndpoint.y
        )

        is EdgePath.Orthogonal -> cubicTo(
            edgePath.controlPoint1.x, edgePath.controlPoint1.y,
            edgePath.controlPoint2.x, edgePath.controlPoint2.y,
            pathEndpoint.x, pathEndpoint.y
        )

        is EdgePath.RightAngle -> {
            edgePath.waypoints.forEach { lineTo(it.x, it.y) }
            lineTo(pathEndpoint.x, pathEndpoint.y)
        }
    }
}
