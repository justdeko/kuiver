package com.dk.kuiver.renderer

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.dk.kuiver.model.Kuiver
import kotlin.coroutines.coroutineContext
import kotlin.math.hypot

/**
 * Node positions of one layout generation, relative to the center of the graph bounds.
 */
internal typealias NodePositions = Map<String, DpOffset>

/**
 * Positions of all nodes, relative to the center of the graph bounds.
 *
 * @param centerX x center of the graph bounds
 * @param centerY y center of the graph bounds
 * @return positions keyed by node id
 */
internal fun Kuiver.nodePositionsRelativeTo(centerX: Dp, centerY: Dp): NodePositions =
    nodes.mapValues { (_, node) ->
        DpOffset(node.position.x - centerX, node.position.y - centerY)
    }

@Stable
internal class LayoutTransition {
    private var progress by mutableFloatStateOf(1f)

    // Speed of progress when the animation last ran, carried into the next generation
    private var velocity = 0f

    // Frame time the animation last ran at
    private var lastFrameNanos = NO_FRAME

    // Whether an animation was cancelled before it finished
    private var interrupted = false

    private var start by mutableStateOf<NodePositions>(emptyMap())
    private var end by mutableStateOf<NodePositions>(emptyMap())

    /**
     * Position of [nodeId] at the current point of the transition. Progress is only read while a
     * node moves. A node that holds still is invalidated once by the swap and then left alone.
     *
     * @param nodeId id of the node to place
     * @param targets the caller's layout generation, used for nodes the transition has not adopted
     * @param snapToTarget bypasses the transition, for initial placement
     * @return the interpolated position, relative to the graph center
     */
    fun positionOf(
        nodeId: String,
        targets: NodePositions,
        snapToTarget: Boolean = false
    ): DpOffset {
        val target = targets[nodeId] ?: DpOffset.Zero
        if (snapToTarget) return target
        val from = start[nodeId] ?: return target
        val to = end[nodeId] ?: return target
        if (from == to) return to
        return lerp(from, to, progress)
    }

    /**
     * Moves every node to [targets] over one animation. Cancelling the caller supersedes it and the
     * next call resumes from where the nodes were left, never from the previous layout, at the
     * speed they were moving.
     *
     * @param targets the layout generation to animate to
     * @param spec spec driving the shared progress
     * @param snap jumps straight to [targets] without animating, as initial placement does
     */
    suspend fun animateTo(targets: NodePositions, spec: AnimationSpec<Float>, snap: Boolean) {
        val durationScale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        if (snap || start.isEmpty() || durationScale == 0f) {
            start = targets
            end = targets
            progress = 1f
            velocity = 0f
            interrupted = false
            return
        }
        // Carry the speed over to the remaining distance
        val remaining = 1f - progress
        val carried = if (interrupted && remaining > MIN_REMAINING_PROGRESS) {
            (velocity / remaining).coerceIn(-MAX_CARRIED_VELOCITY, MAX_CARRIED_VELOCITY)
        } else {
            0f
        }
        start = targets.keys.associateWith { positionOf(it, targets) }
        end = targets
        // Reset before suspending to land in the same frame as the new generation
        progress = 0f
        velocity = carried

        val animation = TargetBasedAnimation(
            spec.withFinalStep(maxTravel()),
            Float.VectorConverter,
            0f,
            1f,
            carried
        )
        // Continue the timeline of an interrupted animation so the next frame moves on
        var startNanos = if (interrupted) lastFrameNanos else NO_FRAME
        var firstFrame = true
        interrupted = true
        do {
            val finished = withFrameNanos { frameNanos ->
                if (firstFrame && (startNanos == NO_FRAME || frameNanos - startNanos > MAX_FRAME_GAP_NANOS)) {
                    startNanos = frameNanos
                }
                firstFrame = false
                val playTime = ((frameNanos - startNanos) / durationScale).toLong()
                progress = animation.getValueFromNanos(playTime)
                velocity = animation.getVelocityVectorFromNanos(playTime).value
                lastFrameNanos = frameNanos
                animation.isFinishedFromNanos(playTime)
            }
        } while (!finished)
        velocity = 0f
        interrupted = false
    }

    // Distance the farthest node moves in the current generation, in dp
    private fun maxTravel(): Float = end.keys.maxOfOrNull { id ->
        val from = start.getValue(id)
        val to = end.getValue(id)
        hypot((to.x - from.x).value, (to.y - from.y).value)
    } ?: 0f
}

/**
 * Spring with a visibility threshold that stops the farthest node within [FINAL_STEP] of its
 * target. Other specs and springs with their own threshold are returned unchanged.
 *
 * @param maxTravel distance the farthest node moves, in dp
 * @return the spec to drive the 0..1 progress with
 */
private fun AnimationSpec<Float>.withFinalStep(maxTravel: Float): AnimationSpec<Float> {
    if (this !is SpringSpec || visibilityThreshold != null) return this
    // Never loosen the default threshold for short moves
    val threshold = minOf(FINAL_STEP.value / maxTravel, Spring.DefaultDisplacementThreshold)
    return spring(dampingRatio, stiffness, threshold)
}

/**
 * Largest distance a node jumps on the frame a transition ends. A spring over 0..1 stops within
 * 0.01 of its target, and that last 1% is visible on a node that moves far.
 */
private val FINAL_STEP = 0.25.dp

/**
 * Marks a frame time that has not been taken yet.
 */
private const val NO_FRAME = Long.MIN_VALUE

/**
 * Longest pause after an interrupted animation that still continues its timeline.
 */
private const val MAX_FRAME_GAP_NANOS = 100_000_000L

/**
 * Progress left below which a retarget starts from rest.
 */
private const val MIN_REMAINING_PROGRESS = 0.05f

/**
 * Cap on the progress speed carried into a retarget, per second.
 */
private const val MAX_CARRIED_VELOCITY = 10f
