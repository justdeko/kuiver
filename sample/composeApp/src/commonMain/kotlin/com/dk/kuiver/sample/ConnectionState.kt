package com.dk.kuiver.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dk.kuiver.model.KuiverNode

@Composable
fun rememberConnectionState(): MutableConnectionState {
    var sourceNode by remember { mutableStateOf<KuiverNode?>(null) }

    return remember(sourceNode) {
        MutableConnectionState(
            sourceNode = sourceNode,
            onStart = { node ->
                sourceNode = node
            },
            onReset = {
                sourceNode = null
            }
        )
    }
}

data class MutableConnectionState(
    val sourceNode: KuiverNode?,
    val onStart: (KuiverNode) -> Unit,
    val onReset: () -> Unit
) {
    val isActive: Boolean get() = sourceNode != null

    fun start(node: KuiverNode) = onStart(node)
    fun reset() = onReset()
}
