package com.dk.kuiver.sample

import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.Serializable

@Serializable
data class EdgeData(
    val label: String = "",
    val labelOffset: Float = 0.5f
)

@Serializable
data class EdgeKey(
    val fromId: String,
    val toId: String
) {
    override fun toString() = "$fromId->$toId"
}

infix fun String.edgeTo(toId: String) = EdgeKey(this, toId)

val EdgeDataMapSaver = Saver<Map<EdgeKey, EdgeData>, List<Any>>(
    save = { map ->
        map.flatMap { (key, data) ->
            listOf(key.fromId, key.toId, data.label, data.labelOffset)
        }
    },
    restore = { list ->
        list.chunked(4).associate { chunk ->
            EdgeKey(chunk[0] as String, chunk[1] as String) to
                EdgeData(chunk[2] as String, (chunk[3] as Number).toFloat())
        }
    }
)
