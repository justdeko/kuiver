package com.dk.kuiver.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import com.dk.kuiver.model.AnchorOffset
import com.dk.kuiver.renderer.LocalAnchorRegistry
import com.dk.kuiver.renderer.LocalNodeCoordinates


/**
 * Marks a point in node content that edges can attach to.
 *
 * Edges refer to it through [com.dk.kuiver.model.KuiverEdge.fromAnchor] and
 * [com.dk.kuiver.model.KuiverEdge.toAnchor]. Only works inside the `nodeContent` of a viewer.
 *
 * ```kotlin
 * nodeContent = { node ->
 *     Box(Modifier.size(100.dp)) {
 *         KuiverAnchor("right", Modifier.align(Alignment.CenterEnd))
 *         Text(node.id)
 *     }
 * }
 * ```
 *
 * @param anchorId id of the anchor, unique within its node
 * @param modifier compose modifier for the anchor
 * @param content optional content drawn at the anchor
 */
@Composable
public fun KuiverAnchor(
    anchorId: String,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit)? = null
) {
    val nodeId = LocalNodeCoordinates.current?.nodeId
        ?: error("KuiverAnchor has to be placed inside the nodeContent of a KuiverViewer")
    AnchorBox(modifier, anchorId, nodeId, content)
}

/**
 * Marks a point in node content that edges can attach to.
 *
 * @param modifier compose modifier for the anchor
 * @param anchorId id of the anchor, unique within its node
 * @param nodeId id of the node the anchor belongs to
 * @param content optional content drawn at the anchor
 */
@Deprecated(
    message = "The anchor takes node from the node content it is placed in",
    replaceWith = ReplaceWith("KuiverAnchor(anchorId, modifier, content)")
)
@Composable
public fun KuiverAnchor(
    modifier: Modifier = Modifier,
    anchorId: String,
    nodeId: String,
    content: @Composable (() -> Unit)? = null
) {
    AnchorBox(modifier, anchorId, nodeId, content)
}

/**
 * Registers the center of this box as an anchor of the node.
 */
@Composable
private fun AnchorBox(
    modifier: Modifier,
    anchorId: String,
    nodeId: String,
    content: @Composable (() -> Unit)?
) {
    val anchorRegistry = LocalAnchorRegistry.current
    val nodeCoordinates = LocalNodeCoordinates.current
    val density = LocalDensity.current

    DisposableEffect(anchorRegistry, nodeId, anchorId) {
        onDispose { anchorRegistry.unregisterAnchor(nodeId, anchorId) }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                // Calculate position relative to node's top-left
                val centerInAnchor = Offset(
                    coordinates.size.width / 2f,
                    coordinates.size.height / 2f
                )
                val anchorCenter = nodeCoordinates?.coordinates
                    ?.takeIf { it.isAttached }
                    ?.localPositionOf(coordinates, centerInAnchor)
                    ?: (coordinates.positionInParent() + centerInAnchor)

                with(density) {
                    anchorRegistry.registerAnchor(
                        nodeId,
                        anchorId,
                        AnchorOffset(anchorCenter.x.toDp(), anchorCenter.y.toDp())
                    )
                }
            }
    ) {
        content?.invoke()
    }
}