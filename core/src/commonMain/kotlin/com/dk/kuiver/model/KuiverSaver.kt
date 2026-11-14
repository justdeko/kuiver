package com.dk.kuiver.model

import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.dk.kuiver.KuiverViewerState
import com.dk.kuiver.LayoutFrame

/**
 * Saver for the node positions a user has set by hand, flattened to a list of primitives.
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
 * Saver for the layout frame, flattened to a list of floats.
 */
internal fun layoutFrameSaver(): Saver<LayoutFrame?, Any> = Saver(
    save = { frame ->
        frame?.let {
            listOf(
                it.canvasWidth.value,
                it.canvasHeight.value,
                it.center.x.value,
                it.center.y.value
            )
        }
    },
    restore = { savedValue ->
        @Suppress("UNCHECKED_CAST")
        val (width, height, x, y) = savedValue as List<Float>
        LayoutFrame(width.dp, height.dp, DpOffset(x.dp, y.dp))
    }
)

/**
 * Saver for [KuiverViewerState]. Saves the graph, zoom, pan and moved nodes.
 */
internal fun kuiverViewerStateSaver(): Saver<KuiverViewerState, Any> = Saver(
    save = { state ->
        listOf(
            with(kuiverSaver()) { save(state.kuiver) },
            state.scale,
            // Stored as dp to survive a density change across the restore
            state.offset.x.value,
            state.offset.y.value,
            state.hasFittedInitially,
            with(manualPositionsSaver()) { save(state.manualPositions) },
            with(layoutFrameSaver()) { save(state.layoutFrame) }
        )
    },
    restore = { savedValue ->
        val saved = savedValue as List<*>
        val kuiver = saved[0]?.let { kuiverSaver().restore(it) }
        if (kuiver == null) {
            null
        } else {
            KuiverViewerState(
                initialKuiver = kuiver,
                initialScale = saved[1] as Float,
                initialOffset = DpOffset((saved[2] as Float).dp, (saved[3] as Float).dp)
            ).also { state ->
                // Keep the restored view over the initial fit
                state.hasRestoredTransform = saved[4] as Boolean
                state.restoreManualPositions(
                    positions = saved[5]?.let { manualPositionsSaver().restore(it) }.orEmpty(),
                    frame = saved[6]?.let { layoutFrameSaver().restore(it) }
                )
            }
        }
    }
)

/**
 * Values saved per node: id, position x and y, width and height.
 */
private const val NODE_FIELDS = 5

/**
 * Values saved per edge: from, to, type, anchors and id.
 */
private const val EDGE_FIELDS = 6

/**
 * Saver for Kuiver objects to enable saving and restoring state in composables.
 *
 * Saved state is limited to about 1 MB per screen on Android.
 */
@Suppress("UNCHECKED_CAST")
public fun kuiverSaver(): Saver<Kuiver, Any> = Saver(
    save = { kuiver ->
        buildList {
            add(kuiver.nodes.size)
            kuiver.nodes.values.forEach { node ->
                add(node.id)
                add(node.position.x.value)
                add(node.position.y.value)
                add(node.dimensions?.width?.value)
                add(node.dimensions?.height?.value)
            }
            kuiver.edges.forEach { edge ->
                add(edge.fromId)
                add(edge.toId)
                add(edge.type?.name)
                add(edge.fromAnchor)
                add(edge.toAnchor)
                // Default ids are rebuilt on restore
                add(edge.id.takeIf { it != "${edge.fromId}->${edge.toId}" })
            }
        }
    },
    restore = { savedValue ->
        val flat = savedValue as List<Any?>
        val edgesStart = 1 + (flat[0] as Int) * NODE_FIELDS

        buildKuiver {
            // Restore nodes
            for (i in 1 until edgesStart step NODE_FIELDS) {
                val width = flat[i + 3] as Float?
                val height = flat[i + 4] as Float?
                addNode(
                    KuiverNode(
                        id = flat[i] as String,
                        dimensions = if (width != null && height != null) {
                            NodeDimensions(width.dp, height.dp)
                        } else {
                            null
                        },
                        position = DpOffset((flat[i + 1] as Float).dp, (flat[i + 2] as Float).dp)
                    )
                )
            }

            // Restore edges
            for (i in edgesStart until flat.size step EDGE_FIELDS) {
                val fromId = flat[i] as String
                val toId = flat[i + 1] as String
                addEdge(
                    KuiverEdge(
                        fromId = fromId,
                        toId = toId,
                        type = (flat[i + 2] as String?)?.let { EdgeType.valueOf(it) },
                        fromAnchor = flat[i + 3] as String?,
                        toAnchor = flat[i + 4] as String?,
                        id = flat[i + 5] as String? ?: "$fromId->$toId"
                    )
                )
            }
        }
    }
)
