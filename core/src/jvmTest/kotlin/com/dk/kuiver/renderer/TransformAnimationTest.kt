package com.dk.kuiver.renderer

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Animated zoom and pan against transforms set directly, the way gestures set them.
 */
@OptIn(ExperimentalTestApi::class)
class TransformAnimationTest {

    @Test
    fun `a direct transform wins over a running animation from the same frame on`() =
        runComposeUiTest {
            val scene = ViewerScene(ringGraph(nodeCount = 4, withEdges = false, seed = 1))
            val state = scene.state
            setContent { scene.Content() }
            waitForIdle()
            mainClock.autoAdvance = false

            runOnUiThread { state.updateTransform(2f, DpOffset(300.dp, 0.dp), animated = true) }
            repeat(5) { mainClock.advanceTimeByFrame() }

            // A gesture sets the transform while the animation is halfway
            val direct = DpOffset((-40).dp, 10.dp)
            runOnUiThread { state.updateTransform(1.5f, direct) }
            repeat(5) {
                mainClock.advanceTimeByFrame()
                assertEquals(1.5f, state.scale)
                assertEquals(direct, state.offset)
            }
        }

    @Test
    fun `retargeting an animation keeps the camera moving`() = runComposeUiTest {
        val scene = ViewerScene(ringGraph(nodeCount = 4, withEdges = false, seed = 1))
        val state = scene.state
        setContent { scene.Content() }
        waitForIdle()
        mainClock.autoAdvance = false

        runOnUiThread { state.updateTransform(1f, DpOffset(400.dp, 0.dp), animated = true) }
        repeat(4) { mainClock.advanceTimeByFrame() }
        var previous = state.offset.x
        mainClock.advanceTimeByFrame()
        val stepBefore = state.offset.x - previous
        previous = state.offset.x

        // A new target in the same direction, the camera carries on at its speed
        runOnUiThread { state.updateTransform(1f, DpOffset(800.dp, 0.dp), animated = true) }
        repeat(3) { frame ->
            mainClock.advanceTimeByFrame()
            val step = state.offset.x - previous
            previous = state.offset.x
            assertTrue(
                step > stepBefore * MIN_SPEED_KEPT,
                "camera slowed to ${step.value} dp on frame $frame after the retarget, moved ${stepBefore.value} dp before"
            )
        }
    }

    @Test
    fun `a zoom animation settles without a final jump`() = runComposeUiTest {
        val scene = ViewerScene(ringGraph(nodeCount = 4, withEdges = false, seed = 1))
        val state = scene.state
        setContent { scene.Content() }
        waitForIdle()
        mainClock.autoAdvance = false

        runOnUiThread { state.updateTransform(2f, DpOffset.Zero, animated = true) }
        var previous = state.scale
        var lastStep = 0f
        repeat(SETTLE_FRAMES) {
            mainClock.advanceTimeByFrame()
            val step = state.scale - previous
            if (step != 0f) lastStep = step
            previous = state.scale
        }

        assertEquals(2f, state.scale)
        // At a scale step of 0.01 a node 1000 dp out would jump 10 dp
        assertTrue(abs(lastStep) < MAX_FINAL_SCALE_STEP, "zoom ended with a jump of $lastStep")
    }

    private companion object {
        /** Fraction of its speed the camera has to keep through a retarget */
        const val MIN_SPEED_KEPT = 0.5f

        /** Frames for the default zoom spring to come to rest */
        const val SETTLE_FRAMES = 300

        /** Largest scale change allowed on the frame a zoom ends */
        const val MAX_FINAL_SCALE_STEP = 0.001f
    }
}
