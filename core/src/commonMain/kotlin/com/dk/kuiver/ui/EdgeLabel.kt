package com.dk.kuiver.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import kotlin.math.PI

@Composable
internal fun EdgeLabel(
    label: String,
    position: EdgeLabelPosition,
    style: EdgeLabelStyle,
    content: (@Composable (String) -> Unit)?
) {
    val normalizedAngle = if (style.rotateWithEdge) {
        normalizeRotation(position.angle)
    } else {
        0f
    }

    Box(
        modifier = Modifier
            .zIndex(1f)
            .wrapContentSize(unbounded = true)
            .graphicsLayer {
                translationX = position.position.x - size.width / 2f
                translationY = position.position.y - size.height / 2f

                if (style.rotateWithEdge) {
                    rotationZ = normalizedAngle * 180f / PI.toFloat()
                }

                transformOrigin = TransformOrigin.Center
            }
    ) {
        if (content != null) {
            content(label)
        } else {
            DefaultEdgeLabel(label = label, style = style)
        }
    }
}
