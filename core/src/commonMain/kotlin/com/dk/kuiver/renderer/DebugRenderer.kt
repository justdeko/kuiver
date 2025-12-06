package com.dk.kuiver.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.util.calculateNodeBounds

@Composable
internal fun RenderDebugBounds(
    kuiver: Kuiver,
    centerX: Dp,
    centerY: Dp,
    graphCenterX: Float,
    graphCenterY: Float,
    showDebugBounds: Boolean,
    onCanvasSize: (Float, Float) -> Unit,
    onRedBoxCenter: (Offset) -> Unit,
    onBoundsChange: (LayoutCoordinates) -> Unit
) {
    if (kuiver.nodes.isEmpty()) return

    val bounds = kuiver.nodes.values.calculateNodeBounds()

    // Canvas for debug drawing
    Canvas(modifier = Modifier.fillMaxSize()) {
        val boundLeft = centerX.toPx() + (bounds.minX - graphCenterX).dp.toPx()
        val boundRight = centerX.toPx() + (bounds.maxX - graphCenterX).dp.toPx()
        val boundTop = centerY.toPx() + (bounds.minY - graphCenterY).dp.toPx()
        val boundBottom = centerY.toPx() + (bounds.maxY - graphCenterY).dp.toPx()

        // Calculate red box center and position relative to parent view
        val redBoxCenterX = (boundLeft + boundRight) / 2f
        val redBoxCenterY = (boundTop + boundBottom) / 2f

        // Report canvas size and red box center to App
        onCanvasSize(size.width, size.height)
        onRedBoxCenter(Offset(redBoxCenterX, redBoxCenterY))

        // Draw debug bounds if enabled
        if (showDebugBounds) {
            // Draw graph bounding box (red)
            drawRect(
                color = Color.Red,
                topLeft = Offset(boundLeft, boundTop),
                size = Size(
                    boundRight - boundLeft,
                    boundBottom - boundTop
                ),
                style = Stroke(width = 2.dp.toPx())
            )

            // Draw canvas bounds (blue) - shows the actual viewport
            drawRect(
                color = Color.Blue,
                topLeft = Offset(0f, 0f),
                size = Size(size.width, size.height),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }

    // Invisible box for tracking bounds
    Box(
        modifier = Modifier
            .offset(
                x = centerX + (bounds.minX - graphCenterX).dp,
                y = centerY + (bounds.minY - graphCenterY).dp
            )
            .size(
                width = (bounds.maxX - bounds.minX).dp,
                height = (bounds.maxY - bounds.minY).dp
            )
            .onGloballyPositioned { coordinates ->
                onBoundsChange(coordinates)
            }
    )
}
