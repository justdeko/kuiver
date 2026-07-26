package com.dk.kuiver.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.dk.kuiver.KuiverViewerState
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.model.NodeDimensions
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal const val NODE_WIDTH_DP = 80f
internal const val NODE_HEIGHT_DP = 40f

internal class ViewerScene(initial: Kuiver) {
    val state = KuiverViewerState(initial).apply {
        layoutedKuiver = initial
        hasFittedInitially = true
    }

    var nodeCompositions = 0
        private set
    var edgeCompositions = 0
        private set

    // Center of each node in root coordinates
    val nodeCenters = mutableMapOf<String, Offset>()

    // Start point each edge was last drawn from in root coordinates
    val edgeStarts = mutableMapOf<String, Offset>()

    // Half the placed size of a node in px
    var nodeHalfExtent = Offset.Zero
        private set

    fun resetCounters() {
        nodeCompositions = 0
        edgeCompositions = 0
    }

    @Composable
    fun Content() {
        ViewerRenderer(
            state = state,
            config = KuiverViewerConfig(fitToContent = false),
            anchorRegistry = remember { AnchorPositionRegistry() },
            nodeContent = { node ->
                nodeCompositions++
                Box(
                    Modifier
                        .fillMaxSize()
                        .onPlaced { coordinates ->
                            nodeHalfExtent = Offset(
                                coordinates.size.width / 2f,
                                coordinates.size.height / 2f
                            )
                            nodeCenters[node.id] = coordinates.positionInRoot() + nodeHalfExtent
                        }
                        .background(Color.Gray)
                )
            },
            edgeContent = { edge, from, to ->
                edgeCompositions++
                edgeStarts["${edge.fromId}->${edge.toId}"] = from
                Canvas(Modifier.fillMaxSize()) { drawLine(Color.Black, from, to) }
            }
        )
    }
}

/**
 * Ring graph generator for testing. Nodes are placed in a circle, optionally chained with edges.
 *
 * @param nodeCount how many nodes to place on the ring
 * @param withEdges whether to chain the nodes with edges
 * @param seed generation seed, also the rotation of the ring
 * @return the graph
 */
internal fun ringGraph(nodeCount: Int, withEdges: Boolean, seed: Int): Kuiver {
    val random = Random(seed)
    val kuiver = Kuiver()
    repeat(nodeCount) { index ->
        val angle = 2 * PI * index / nodeCount + seed * 0.6
        val radius = 900f + random.nextFloat() * 300f
        kuiver.addNode(
            KuiverNode(
                id = index.toString(),
                dimensions = NodeDimensions(NODE_WIDTH_DP.dp, NODE_HEIGHT_DP.dp),
                position = Offset((radius * cos(angle)).toFloat(), (radius * sin(angle)).toFloat())
            )
        )
    }
    if (withEdges) {
        repeat(nodeCount) { index ->
            kuiver.addEdge(
                KuiverEdge(
                    fromId = index.toString(),
                    toId = ((index * 7 + 3) % nodeCount).toString()
                )
            )
        }
    }
    return kuiver
}
