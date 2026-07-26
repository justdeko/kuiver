package com.dk.kuiver.renderer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dk.kuiver.model.DEFAULT_NODE_SIZE_DP
import com.dk.kuiver.model.KuiverNode
import kotlin.math.roundToInt

@Composable
internal fun RenderNode(
    node: KuiverNode,
    centerX: Dp,
    centerY: Dp,
    targets: NodePositions,
    transition: LayoutTransition,
    skipAnimation: Boolean,
    nodeContent: @Composable (KuiverNode) -> Unit
) {
    val nodeWidth = node.dimensions?.width ?: DEFAULT_NODE_SIZE_DP
    val nodeHeight = node.dimensions?.height ?: DEFAULT_NODE_SIZE_DP
    val nodeId = node.id

    Box(
        modifier = Modifier
            .offset {
                val position = transition.positionOf(nodeId, targets, skipAnimation)
                IntOffset(
                    x = (centerX.toPx() + position.x.dp.toPx() - nodeWidth.toPx() / 2f).roundToInt(),
                    y = (centerY.toPx() + position.y.dp.toPx() - nodeHeight.toPx() / 2f).roundToInt()
                )
            }
            .size(width = nodeWidth, height = nodeHeight)
    ) {
        nodeContent(node)
    }
}
