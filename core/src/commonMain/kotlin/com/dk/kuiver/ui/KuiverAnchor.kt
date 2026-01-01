package com.dk.kuiver.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import com.dk.kuiver.model.AnchorOffset
import com.dk.kuiver.renderer.LocalAnchorRegistry


/**
 * Marks a point within a node's content as an anchor point for edges.
 *
 * Place this composable within your node content to define where edges can connect.
 * The anchor's position is calculated relative to the node's top-left corner.
 *
 * @param anchorId Unique identifier for this anchor within the node (e.g., "left", "right")
 * @param nodeId The ID of the node this anchor belongs to
 * @param modifier Modifier for positioning the anchor (use alignment modifiers to place it)
 * @param content Optional content of the anchor
 *
 * Example:
 * ```kotlin
 * KuiverViewer(
 *     state = viewerState,
 *     nodeContent = { node ->
 *         Box(modifier = Modifier.size(100.dp)) {
 *             // Anchor on the left side
 *             KuiverAnchor(
 *                 anchorId = "left",
 *                 nodeId = node.id,
 *                 modifier = Modifier.align(Alignment.CenterStart)
 *             ) {
 *                 Box(Modifier.size(8.dp).background(Color.White, CircleShape))
 *             }
 *
 *             // Anchor on the right side
 *             KuiverAnchor(
 *                 anchorId = "right",
 *                 nodeId = node.id,
 *                 modifier = Modifier.align(Alignment.CenterEnd)
 *             )
 *
 *             // Node content
 *             Text("My Node")
 *         }
 *     }
 * )
 *
 * // Create edges with anchor references
 * val graph = buildKuiver {
 *     addNode(KuiverNode("A"))
 *     addNode(KuiverNode("B"))
 *     addEdge(KuiverEdge(
 *         fromId = "A",
 *         toId = "B",
 *         fromAnchor = "right",
 *         toAnchor = "left"
 *     ))
 * }
 * ```
 */
@Composable
fun KuiverAnchor(
    modifier: Modifier = Modifier,
    anchorId: String,
    nodeId: String,
    content: @Composable (() -> Unit)? = null
) {
    val anchorRegistry = LocalAnchorRegistry.current
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                // Calculate position relative to node's top-left
                val anchorCenter = Offset(
                    coordinates.positionInParent().x + coordinates.size.width / 2f,
                    coordinates.positionInParent().y + coordinates.size.height / 2f
                )

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