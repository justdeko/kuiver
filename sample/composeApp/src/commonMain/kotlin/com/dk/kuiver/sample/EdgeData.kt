package com.dk.kuiver.sample

import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.Serializable

@Serializable
data class EdgeData(
    val label: String = "",
    val labelOffset: Float = 0.5f
)

val EdgeDataMapSaver = Saver<Map<String, EdgeData>, List<Any>>(
    save = { map ->
        map.flatMap { (edgeId, data) ->
            listOf(edgeId, data.label, data.labelOffset)
        }
    },
    restore = { list ->
        list.chunked(3).associate { chunk ->
            chunk[0] as String to EdgeData(chunk[1] as String, (chunk[2] as Number).toFloat())
        }
    }
)
