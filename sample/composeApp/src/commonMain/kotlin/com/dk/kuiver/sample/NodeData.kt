package com.dk.kuiver.sample

import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.Serializable
import kotlin.collections.chunked
import kotlin.collections.component1
import kotlin.collections.component2

@Serializable
data class NodeData(
    val label: String,
    val colorType: NodeColorType
)

// Custom Saver for Map<String, NodeData>
val NodeDataMapSaver = Saver<Map<String, NodeData>, List<Any>>(
    save = { map ->
        map.flatMap { (key, data) ->
            listOf(key, data.label, data.colorType.name)
        }
    },
    restore = { list ->
        list.chunked(3).associate { chunk ->
            val key = chunk[0] as String
            val label = chunk[1] as String
            val colorTypeName = chunk[2] as String
            key to NodeData(label, NodeColorType.valueOf(colorTypeName))
        }
    }
)
