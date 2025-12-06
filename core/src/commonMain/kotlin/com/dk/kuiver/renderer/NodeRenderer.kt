package com.dk.kuiver.renderer

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dk.kuiver.model.DEFAULT_NODE_SIZE_DP
import com.dk.kuiver.model.KuiverNode

@Composable
internal fun RenderNode(
    node: KuiverNode,
    centerX: Dp,
    centerY: Dp,
    graphCenterX: Float,
    graphCenterY: Float,
    animationSpec: AnimationSpec<Offset>,
    nodeContent: @Composable (KuiverNode) -> Unit
) {
    val nodeWidth = node.dimensions?.width ?: DEFAULT_NODE_SIZE_DP
    val nodeHeight = node.dimensions?.height ?: DEFAULT_NODE_SIZE_DP

    val targetOffsetX = centerX + (node.position.x - graphCenterX).dp - nodeWidth / 2
    val targetOffsetY = centerY + (node.position.y - graphCenterY).dp - nodeHeight / 2

    val animatedOffsetX by animateOffsetAsState(
        targetValue = Offset(targetOffsetX.value, targetOffsetY.value),
        animationSpec = animationSpec,
        label = "node_position_${node.id}"
    )

    Box(
        modifier = Modifier
            .offset(
                x = animatedOffsetX.x.dp,
                y = animatedOffsetX.y.dp
            )
            .size(width = nodeWidth, height = nodeHeight)
    ) {
        nodeContent(node)
    }
}
