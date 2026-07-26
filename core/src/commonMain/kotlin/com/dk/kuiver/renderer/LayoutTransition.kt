package com.dk.kuiver.renderer

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import com.dk.kuiver.model.Kuiver

/** Node positions of one layout generation, in dp relative to the center of the graph bounds. */
internal typealias NodePositions = Map<String, Offset>

/**
 * Positions of all nodes, relative to the center of the graph bounds.
 *
 * @param centerX x center of the graph bounds
 * @param centerY y center of the graph bounds
 * @return positions keyed by node id
 */
internal fun Kuiver.nodePositionsRelativeTo(centerX: Float, centerY: Float): NodePositions =
    nodes.mapValues { (_, node) ->
        Offset(node.position.x - centerX, node.position.y - centerY)
    }

@Stable
internal class LayoutTransition {
    private var progress by mutableFloatStateOf(1f)

    // Snapshot state, so consumers that read a position without reading progress still see a swap
    private var start by mutableStateOf<NodePositions>(emptyMap())
    private var end by mutableStateOf<NodePositions>(emptyMap())

    /**
     * Position of [nodeId] at the current point of the transition. Progress is only read while a
     * node moves, so a node that holds still is invalidated once by the swap and then left alone.
     *
     * @param nodeId id of the node to place
     * @param targets the caller's layout generation, used for nodes the transition has not adopted
     * @param snapToTarget bypasses the transition, so initial placement renders no stale frame
     * @return the interpolated position, in dp relative to the graph center
     */
    fun positionOf(
        nodeId: String,
        targets: NodePositions,
        snapToTarget: Boolean = false
    ): Offset {
        val target = targets[nodeId] ?: Offset.Zero
        if (snapToTarget) return target
        val from = start[nodeId] ?: return target
        val to = end[nodeId] ?: return target
        if (from == to) return to
        return lerp(from, to, progress)
    }

    /**
     * Moves every node to [targets] over one animation. Cancelling the caller supersedes it and the
     * next call resumes from where the nodes were left, never from the previous layout.
     *
     * @param targets the layout generation to animate to
     * @param spec spec driving the shared progress
     * @param snap jumps to [targets] instead of animating, as initial placement does
     */
    suspend fun animateTo(targets: NodePositions, spec: AnimationSpec<Float>, snap: Boolean) {
        if (snap || start.isEmpty()) {
            start = targets
            end = targets
            progress = 1f
            return
        }
        start = targets.keys.associateWith { positionOf(it, targets) }
        end = targets
        // Reset before suspending, so it lands in the same frame as the new generation
        progress = 0f
        animate(initialValue = 0f, targetValue = 1f, animationSpec = spec) { value, _ ->
            progress = value
        }
    }
}
