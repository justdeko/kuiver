package com.dk.kuiver.model

import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Saver for the node positions a user has set by hand, which are otherwise lost on the next layout
 * pass after the state is restored. Flattened to a list of primitives, which every platform's
 * saveable bundle takes.
 */
internal fun manualPositionsSaver(): Saver<Map<String, DpOffset>, Any> = Saver(
    save = { positions ->
        positions.flatMap { (id, position) -> listOf(id, position.x.value, position.y.value) }
    },
    restore = { savedValue ->
        @Suppress("UNCHECKED_CAST")
        val flat = savedValue as List<Any>
        flat.chunked(3).associate { (id, x, y) ->
            id as String to DpOffset((x as Float).dp, (y as Float).dp)
        }
    }
)

/**
 * Saver for Kuiver objects to enable saving and restoring state in composables.
 */
@Suppress("UNCHECKED_CAST")
fun kuiverSaver(): Saver<Kuiver, Any> = Saver(
    save = { kuiver ->
        mapOf(
            "nodes" to kuiver.nodes.map { (id, node) ->
                mapOf(
                    "id" to id,
                    "posX" to node.position.x.value,
                    "posY" to node.position.y.value,
                    "dimWidth" to node.dimensions?.width?.value,
                    "dimHeight" to node.dimensions?.height?.value
                )
            },
            "edges" to kuiver.edges.map { edge ->
                mapOf(
                    "fromId" to edge.fromId,
                    "toId" to edge.toId,
                    "type" to edge.type?.name,
                    "fromAnchor" to edge.fromAnchor,
                    "toAnchor" to edge.toAnchor
                )
            }
        )
    },
    restore = { savedValue ->
        val map = savedValue as Map<String, Any>
        val nodesData = map["nodes"] as List<Map<String, Any?>>
        val edgesData = map["edges"] as List<Map<String, Any?>>

        buildKuiver {
            // Restore nodes
            nodesData.forEach { nodeMap ->
                val id = nodeMap["id"] as String
                val posX = nodeMap["posX"] as Float
                val posY = nodeMap["posY"] as Float
                val dimWidth = nodeMap["dimWidth"] as? Float
                val dimHeight = nodeMap["dimHeight"] as? Float

                val dimensions = if (dimWidth != null && dimHeight != null) {
                    NodeDimensions(dimWidth.dp, dimHeight.dp)
                } else {
                    null
                }

                addNode(
                    KuiverNode(
                        id = id,
                        dimensions = dimensions,
                        position = DpOffset(posX.dp, posY.dp)
                    )
                )
            }

            // Restore edges
            edgesData.forEach { edgeMap ->
                val fromId = edgeMap["fromId"] as String
                val toId = edgeMap["toId"] as String
                val typeName = edgeMap["type"] as? String
                val fromAnchor = edgeMap["fromAnchor"] as? String
                val toAnchor = edgeMap["toAnchor"] as? String

                val type = typeName?.let { EdgeType.valueOf(it) }

                addEdge(
                    KuiverEdge(
                        fromId = fromId,
                        toId = toId,
                        type = type,
                        fromAnchor = fromAnchor,
                        toAnchor = toAnchor
                    )
                )
            }
        }
    }
)
