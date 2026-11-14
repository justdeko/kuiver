package com.dk.kuiver.renderer

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.dk.kuiver.KuiverViewerState

/**
 * Margin added around the viewport, as a fraction of its size.
 */
private const val WINDOW_MARGIN = 0.5f

/**
 * Window size as a multiple of the viewport above which the window is recomputed.
 */
private const val MAX_WINDOW_FACTOR = 4f

/**
 * Tracks the visible area of the graph, extended by a margin. Nodes and edges outside this area
 * are not rendered.
 *
 * @property bounds the window in graph dp, relative to the viewport center. Null while the viewer
 * has no size
 */
internal class CullWindow(private val state: KuiverViewerState) {
    // Last computed window
    private var current: DpRect? = null

    val bounds: DpRect? by derivedStateOf {
        val halfWidth = state.canvasWidth / 2
        val halfHeight = state.canvasHeight / 2
        if (halfWidth <= 0.dp || halfHeight <= 0.dp) return@derivedStateOf null

        // Convert the viewport to graph coordinates
        val scale = state.scale
        val offset = state.offset
        val visible = DpRect(
            left = (-halfWidth - offset.x) / scale,
            top = (-halfHeight - offset.y) / scale,
            right = (halfWidth - offset.x) / scale,
            bottom = (halfHeight - offset.y) / scale
        )

        val last = current
        if (last != null && last.covers(visible)) {
            last
        } else {
            val marginX = visible.width * WINDOW_MARGIN
            val marginY = visible.height * WINDOW_MARGIN
            DpRect(
                left = visible.left - marginX,
                top = visible.top - marginY,
                right = visible.right + marginX,
                bottom = visible.bottom + marginY
            ).also { current = it }
        }
    }

    private fun DpRect.covers(visible: DpRect): Boolean =
        left <= visible.left && top <= visible.top &&
                right >= visible.right && bottom >= visible.bottom &&
                width <= visible.width * MAX_WINDOW_FACTOR &&
                height <= visible.height * MAX_WINDOW_FACTOR
}

/**
 * Whether a box of [width] by [height] centered on [center] reaches into this window.
 *
 * @param center center of the box relative to the viewport center
 * @param width width of the box
 * @param height height of the box
 * @return whether the box overlaps. false if it is entirely outside
 */
internal fun DpRect.overlaps(center: DpOffset, width: Dp, height: Dp): Boolean =
    center.x + width / 2 >= left && center.x - width / 2 <= right &&
            center.y + height / 2 >= top && center.y - height / 2 <= bottom
