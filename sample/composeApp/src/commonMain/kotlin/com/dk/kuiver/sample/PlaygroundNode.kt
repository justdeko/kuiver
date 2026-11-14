package com.dk.kuiver.sample

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.dk.kuiver.model.EdgeType
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.buildKuiver
import com.dk.kuiver.ui.KuiverAnchor

internal enum class PortSide { LEFT, RIGHT, TOP, BOTTOM }

@Composable
internal fun PlaygroundNode(
    data: NodeData,
    ports: Map<PortSide, List<String>>,
    isSelected: Boolean,
    isHighlighted: Boolean
) {
    val background = NodeColors.getColor(data.colorType)
    val shape = RoundedCornerShape(16.dp)
    val borderColor by animateColorAsState(
        when {
            isSelected -> MaterialTheme.colorScheme.onSurface
            isHighlighted -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            else -> Color.Transparent
        },
        label = "node_border"
    )

    Box {
        Box(
            modifier = Modifier
                .shadow(3.dp, shape)
                .clip(shape)
                .background(background)
                .border(2.dp, borderColor, shape)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = data.label,
                color = NodeColors.getTextColor(data.colorType),
                style = MaterialTheme.typography.labelLarge
            )
        }

        ports.forEach { (side, anchorIds) ->
            anchorIds.forEachIndexed { index, anchorId ->
                // Spread the anchors evenly along the side
                val bias = (index + 1f) / (anchorIds.size + 1) * 2f - 1f
                val (alignment, outward) = when (side) {
                    PortSide.LEFT -> BiasAlignment(-1f, bias) to DpOffset((-3).dp, 0.dp)
                    PortSide.RIGHT -> BiasAlignment(1f, bias) to DpOffset(3.dp, 0.dp)
                    PortSide.TOP -> BiasAlignment(bias, -1f) to DpOffset(0.dp, (-3).dp)
                    PortSide.BOTTOM -> BiasAlignment(bias, 1f) to DpOffset(0.dp, 3.dp)
                }
                KuiverAnchor(
                    anchorId = anchorId,
                    modifier = Modifier
                        .align(alignment)
                        .offset(outward.x, outward.y)
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(Color.White, CircleShape)
                            .border(2.dp, background, CircleShape)
                    )
                }
            }
        }
    }
}

/**
 * The graph with its own anchor for every edge end, named by role.
 */
internal fun Kuiver.withPorts(): Kuiver {
    val types = classifyAllEdges()
    val used = mutableMapOf<String, Int>()
    fun next(nodeId: String, prefix: String): String {
        val index = used["$nodeId/$prefix"] ?: 0
        used["$nodeId/$prefix"] = index + 1
        return "$prefix-$index"
    }
    return withEdgesReplaced(
        edges.sortedBy { it.id }.map { edge ->
            when {
                edge.fromId == edge.toId -> edge.copy(fromAnchor = null, toAnchor = null)
                types[edge] == EdgeType.BACK -> edge.copy(
                    fromAnchor = next(edge.fromId, "back-out"),
                    toAnchor = next(edge.toId, "back-in")
                )

                else -> edge.copy(
                    fromAnchor = next(edge.fromId, "out"),
                    toAnchor = next(edge.toId, "in")
                )
            }
        }
    )
}

internal fun Kuiver.withoutPorts(): Kuiver =
    withEdgesReplaced(edges.map { it.copy(fromAnchor = null, toAnchor = null) })

private fun Kuiver.withEdgesReplaced(newEdges: List<KuiverEdge>): Kuiver {
    val graph = this
    return buildKuiver {
        graph.nodes.values.forEach { addNode(it) }
        newEdges.forEach { addEdge(it) }
    }
}

internal fun Kuiver.portsOf(nodeId: String, vertical: Boolean): Map<PortSide, List<String>> =
    edges
        .flatMap { edge ->
            listOfNotNull(
                edge.fromAnchor.takeIf { edge.fromId == nodeId },
                edge.toAnchor.takeIf { edge.toId == nodeId }
            )
        }
        .distinct()
        .sortedWith(
            compareBy(
                { it.substringBeforeLast('-') },
                { it.substringAfterLast('-').toIntOrNull() ?: 0 })
        )
        .groupBy { anchorId ->
            when {
                anchorId.startsWith("back") -> if (vertical) PortSide.LEFT else PortSide.TOP
                anchorId.startsWith("out") -> if (vertical) PortSide.BOTTOM else PortSide.RIGHT
                else -> if (vertical) PortSide.TOP else PortSide.LEFT
            }
        }
