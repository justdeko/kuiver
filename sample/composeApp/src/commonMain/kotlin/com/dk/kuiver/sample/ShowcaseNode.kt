package com.dk.kuiver.sample

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.dk.kuiver.ui.KuiverAnchor
import kotlinx.coroutines.delay

/**
 * Width of a step card when expanded, also used to pick the focus zoom.
 */
internal val ExpandedStepWidth = 288.dp

/**
 * Card of one order step. Grows to show facts and tracking events when expanded, pops in when it
 * enters the graph and shrinks away when [leaving].
 *
 * @param nodeId the node id, for its anchors
 * @param spec the step's application data
 * @param status state of the step, shown as card color and a status icon
 * @param expanded whether the details are shown
 * @param leaving whether the node is about to be removed
 * @param dimmed whether another node or edge has the focus
 * @param isSelected whether the viewer has the node selected
 * @param isHovered whether the pointer is over the node
 * @param isDragging whether the node is being dragged
 * @param ports anchor ids that edges are attached to, drawn as dots
 * @param appearDelayMillis delay before popping in, to stagger a batch of nodes
 */
@Composable
internal fun StepNodeContent(
    nodeId: String,
    spec: StepSpec,
    status: StepStatus,
    expanded: Boolean,
    leaving: Boolean,
    dimmed: Boolean,
    isSelected: Boolean,
    isHovered: Boolean,
    isDragging: Boolean,
    ports: Set<String>,
    appearDelayMillis: Int
) {
    val motion = MaterialTheme.motionScheme
    val (statusContainer, statusContent) = status.colors()
    val container by animateColorAsState(statusContainer, motion.slowEffectsSpec(), label = "step_container")
    val content by animateColorAsState(statusContent, motion.slowEffectsSpec(), label = "step_content")

    val appear = remember { Animatable(0f) }
    LaunchedEffect(leaving) {
        if (leaving) {
            appear.animateTo(0f, motion.fastEffectsSpec())
        } else {
            delay(appearDelayMillis.toLong())
            appear.animateTo(1f, motion.defaultSpatialSpec())
        }
    }
    val lift by animateFloatAsState(
        if (isDragging) 1.04f else 1f,
        motion.fastSpatialSpec(),
        label = "step_lift"
    )
    val alpha by animateFloatAsState(
        if (dimmed) 0.35f else 1f,
        motion.defaultEffectsSpec(),
        label = "step_alpha"
    )
    val elevation by animateDpAsState(
        when {
            isDragging -> 8.dp
            isHovered -> 3.dp
            else -> 0.dp
        },
        label = "step_elevation"
    )
    val width by animateDpAsState(
        if (expanded) ExpandedStepWidth else 220.dp,
        motion.defaultSpatialSpec(),
        label = "step_width"
    )

    Box(
        Modifier.graphicsLayer {
            val scale = appear.value * lift
            scaleX = scale
            scaleY = scale
            this.alpha = appear.value.coerceIn(0f, 1f) * alpha
        }
    ) {
        Card(
            modifier = Modifier.width(width),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KindIcon(spec, active = status == StepStatus.ACTIVE || status == StepStatus.RETRYING)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = spec.title,
                            style = MaterialTheme.typography.titleSmallEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = spec.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = content.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    StatusIcon(status, content)
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(motion.defaultEffectsSpec()) +
                            expandVertically(motion.defaultSpatialSpec()),
                    exit = fadeOut(motion.fastEffectsSpec()) +
                            shrinkVertically(motion.fastSpatialSpec())
                ) {
                    StepDetails(spec, status, content)
                }
            }
        }

        val portColor = MaterialTheme.colorScheme.outline
        PortDot(nodeId, "left", "left" in ports, portColor, Modifier.align(Alignment.CenterStart).offset(x = (-5).dp))
        PortDot(nodeId, "right", "right" in ports, portColor, Modifier.align(Alignment.CenterEnd).offset(x = 5.dp))
        PortDot(nodeId, "top", "top" in ports, portColor, Modifier.align(Alignment.TopCenter).offset(y = (-5).dp))
        PortDot(nodeId, "bottom", "bottom" in ports, portColor, Modifier.align(Alignment.BottomCenter).offset(y = 5.dp))
    }
}

/**
 * Icon of the step on a Material shape per kind. While the step is in progress the shape morphs
 * into a burst and turns slowly.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KindIcon(spec: StepSpec, active: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val (polygon, color, onColor) = when (spec.kind) {
        StepKind.CUSTOMER -> Triple(MaterialShapes.Cookie9Sided, scheme.tertiary, scheme.onTertiary)
        StepKind.PAYMENT -> Triple(MaterialShapes.Clover4Leaf, scheme.secondary, scheme.onSecondary)
        StepKind.WAREHOUSE -> Triple(MaterialShapes.Cookie4Sided, scheme.primary, scheme.onPrimary)
        StepKind.SHIPPING -> Triple(MaterialShapes.Sunny, scheme.primary, scheme.onPrimary)
    }
    val morph = remember(polygon) { Morph(polygon, MaterialShapes.SoftBurst) }
    val progress = animateFloatAsState(
        if (active) 1f else 0f,
        MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "kind_morph"
    )
    val rotation = if (active) {
        rememberInfiniteTransition(label = "kind_spin").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
            label = "kind_spin_angle"
        )
    } else {
        null
    }
    val path = remember { Path() }
    val scale = remember { Matrix() }

    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(40.dp)
                .graphicsLayer { rotationZ = rotation?.value ?: 0f }
                .drawBehind {
                    // Morph paths are unit sized, scale them to the box and center them
                    path.rewind()
                    morph.toPath(progress.value, path)
                    scale.reset()
                    scale.scale(size.width, size.height)
                    path.transform(scale)
                    path.translate(center - path.getBounds().center)
                    drawPath(path, color)
                }
        )
        Icon(spec.icon, contentDescription = null, tint = onColor, modifier = Modifier.size(20.dp))
    }
}

/**
 * Waiting clock, loading indicator, check mark or error icon.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusIcon(status: StepStatus, content: Color) {
    AnimatedContent(
        targetState = status,
        transitionSpec = {
            (fadeIn(tween(180)) + scaleIn(initialScale = 0.5f)) togetherWith
                    (fadeOut(tween(120)) + scaleOut(targetScale = 0.5f))
        },
        label = "status_icon"
    ) { shown ->
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            when (shown) {
                StepStatus.WAITING -> Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = shown.label,
                    tint = content.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )

                StepStatus.ACTIVE, StepStatus.RETRYING -> LoadingIndicator(
                    modifier = Modifier.size(32.dp),
                    color = content
                )

                StepStatus.DONE -> Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = shown.label,
                    tint = content,
                    modifier = Modifier.size(24.dp)
                )

                StepStatus.FAILED -> Icon(
                    Icons.Filled.Error,
                    contentDescription = shown.label,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Status, facts and tracking events of an expanded step.
 */
@Composable
private fun StepDetails(spec: StepSpec, status: StepStatus, content: Color) {
    Column(
        modifier = Modifier.padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Fact("Status", status.label, content, Modifier.weight(1.3f))
            spec.facts.forEach { (label, value) -> Fact(label, value, content, Modifier.weight(1f)) }
        }

        // Events reached so far are filled in
        val reached = when (status) {
            StepStatus.WAITING -> 0
            StepStatus.DONE -> spec.events.size
            else -> 1
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            spec.events.forEachIndexed { index, (time, text) ->
                val done = index < reached
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(content.copy(alpha = if (done) 1f else 0.25f), CircleShape)
                    )
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = content.copy(alpha = 0.7f),
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .width(48.dp)
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = if (done) 1f else 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (status == StepStatus.ACTIVE || status == StepStatus.RETRYING) {
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth(), color = content)
        }
    }
}

/**
 * One labelled value in the step details.
 */
@Composable
private fun Fact(label: String, value: String, content: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = content.copy(alpha = 0.08f),
        contentColor = content
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.7f),
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Edge anchor on the card border, drawn as a dot while an edge uses it.
 */
@Composable
private fun PortDot(
    nodeId: String,
    anchorId: String,
    visible: Boolean,
    color: Color,
    modifier: Modifier
) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "port_alpha")
    KuiverAnchor(modifier = modifier, anchorId = anchorId, nodeId = nodeId) {
        Box(
            Modifier
                .size(10.dp)
                .graphicsLayer { this.alpha = alpha }
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .border(2.dp, color, CircleShape)
        )
    }
}

/**
 * Card container and content color of a step state. Finished steps turn green.
 */
@Composable
private fun StepStatus.colors(): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    return when (this) {
        StepStatus.WAITING -> scheme.surfaceContainerHighest to scheme.onSurface
        StepStatus.ACTIVE, StepStatus.RETRYING -> scheme.primaryContainer to scheme.onPrimaryContainer
        StepStatus.DONE -> if (dark) {
            Color(0xFF1E4D2B) to Color(0xFFB4F1BE)
        } else {
            Color(0xFFC8F0CF) to Color(0xFF0A2912)
        }

        StepStatus.FAILED -> scheme.errorContainer to scheme.onErrorContainer
    }
}
