package com.dk.kuiver.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.util.calculateNodeBounds

/** Draws the graph bounds in red and the viewport in blue. */
@Composable
internal fun RenderDebugBounds(
    kuiver: Kuiver,
    centerX: Dp,
    centerY: Dp,
    graphCenterX: Dp,
    graphCenterY: Dp
) {
    if (kuiver.nodes.isEmpty()) return

    val bounds = kuiver.nodes.values.calculateNodeBounds()

    Canvas(modifier = Modifier.fillMaxSize()) {
        val boundLeft = centerX.toPx() + (bounds.minX - graphCenterX).toPx()
        val boundRight = centerX.toPx() + (bounds.maxX - graphCenterX).toPx()
        val boundTop = centerY.toPx() + (bounds.minY - graphCenterY).toPx()
        val boundBottom = centerY.toPx() + (bounds.maxY - graphCenterY).toPx()

        drawRect(
            color = Color.Red,
            topLeft = Offset(boundLeft, boundTop),
            size = Size(
                boundRight - boundLeft,
                boundBottom - boundTop
            ),
            style = Stroke(width = 2.dp.toPx())
        )

        drawRect(
            color = Color.Blue,
            topLeft = Offset(0f, 0f),
            size = Size(size.width, size.height),
            style = Stroke(width = 3.dp.toPx())
        )
    }
}
