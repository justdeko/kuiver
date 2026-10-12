package com.dk.kuiver.renderer

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.Dp
import com.dk.kuiver.KuiverInteractionState
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.ui.EdgeStyle
import com.dk.kuiver.ui.drawStyledEdge
import com.dk.kuiver.ui.fansOut

/**
 * Draws every edge of [kuiver] from a single canvas.
 *
 * Endpoints are resolved in the draw lambda. A layout change invalidates the draw only.
 */
@Composable
internal fun EdgeLayer(
    kuiver: Kuiver,
    centerX: Dp,
    centerY: Dp,
    targets: NodePositions,
    transition: LayoutTransition,
    interaction: KuiverInteractionState,
    anchorRegistry: AnchorPositionRegistry,
    skipAnimation: Boolean,
    bends: Map<String, EdgeBend>,
    edgeStyle: (KuiverEdge) -> EdgeStyle
) {
    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                kuiver.edges.forEach { edge ->
                    val fromNode = kuiver.nodes[edge.fromId] ?: return@forEach
                    val toNode = kuiver.nodes[edge.toId] ?: return@forEach

                    val style = edgeStyle(edge)
                    val bend = bends[edge.id]?.takeIf {
                        edge.fromId == edge.toId || style.shape.fansOut
                    }
                    val (from, to) = resolveEdgeEndpoints(
                        edge = edge,
                        bend = bend,
                        fromNode = fromNode,
                        toNode = toNode,
                        centerX = centerX,
                        centerY = centerY,
                        targets = targets,
                        transition = transition,
                        interaction = interaction,
                        anchorRegistry = anchorRegistry,
                        skipAnimation = skipAnimation
                    )

                    drawStyledEdge(edge, bend?.amount, from, to, style)
                }
            }
    )
}
