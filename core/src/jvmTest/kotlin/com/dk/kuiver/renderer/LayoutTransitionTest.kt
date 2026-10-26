package com.dk.kuiver.renderer

import androidx.compose.animation.core.keyframes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.edge
import com.dk.kuiver.util.times
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LayoutTransitionTest {

    @Test
    fun edgesStayAttachedToNodesForEveryFrameOfATransition() = runComposeUiTest {
        val scene = ViewerScene(ringGraph(NODE_COUNT, withEdges = true, seed = 1))
        mainClock.autoAdvance = false
        setContent { scene.Content() }
        settle(scene)

        runOnIdle { scene.state.layoutedKuiver = ringGraph(NODE_COUNT, withEdges = true, seed = 2) }

        // An edge starts on its node's boundary, never further out than half diagonal plus padding
        repeat(20) {
            mainClock.advanceTimeByFrame()
            waitForIdle()
            val maxDistance = hypot(scene.nodeHalfExtent.x, scene.nodeHalfExtent.y) +
                    EDGE_PADDING_PX + TOLERANCE_PX
            scene.edgeStarts.forEach { (edgeKey, start) ->
                val center = scene.nodeCenters.getValue(edgeKey.substringBefore("->"))
                val distance = (start - center).getDistance()
                assertTrue(
                    distance <= maxDistance,
                    "edge $edgeKey detached from its node on frame $it: " +
                            "distance $distance > $maxDistance"
                )
            }
        }
    }

    @Test
    fun initialPlacementDoesNotAnimate() = runComposeUiTest {
        val scene = ViewerScene(ringGraph(NODE_COUNT, withEdges = false, seed = 1))
        mainClock.autoAdvance = false
        setContent { scene.Content() }

        mainClock.advanceTimeByFrame()
        waitForIdle()
        val firstFrame = scene.nodeCenters.toMap()

        settle(scene)

        assertEquals(firstFrame, scene.nodeCenters.toMap(), "nodes moved after the first frame")
    }

    @Test
    fun interruptingATransitionContinuesFromTheCurrentPositions() = runComposeUiTest {
        // Successive generations rotate the ring further, so animating on moves nodes off their origin
        val scene = ViewerScene(ringGraph(NODE_COUNT, withEdges = false, seed = 1))
        mainClock.autoAdvance = false
        setContent { scene.Content() }
        settle(scene)
        val settledPositions = scene.nodeCenters.toMap()

        runOnIdle {
            scene.state.layoutedKuiver = ringGraph(NODE_COUNT, withEdges = false, seed = 2)
        }
        repeat(6) {
            mainClock.advanceTimeByFrame()
            waitForIdle()
        }

        // Retarget mid-flight: nodes keep going from where they are, they don't fall back
        val atInterrupt = scene.nodeCenters.toMap()
        runOnIdle {
            scene.state.layoutedKuiver = ringGraph(NODE_COUNT, withEdges = false, seed = 3)
        }

        repeat(4) { frame ->
            mainClock.advanceTimeByFrame()
            waitForIdle()
            scene.nodeCenters.forEach { (id, center) ->
                val origin = settledPositions.getValue(id)
                val travelled = (center - origin).getDistance()
                val travelledAtInterrupt = (atInterrupt.getValue(id) - origin).getDistance()
                assertTrue(
                    travelled > travelledAtInterrupt * MIN_PROGRESS_KEPT,
                    "node $id fell back towards the previous layout on frame $frame: " +
                            "$travelled px from origin, was $travelledAtInterrupt px"
                )
            }
        }
        assertTrue(
            scene.nodeCenters.any { (id, center) ->
                (center - atInterrupt.getValue(id)).getDistance() > 1f
            },
            "expected the interrupted transition to keep animating"
        )
    }

    @Test
    fun retargetingATransitionKeepsNodesMovingAtTheirSpeed() = runComposeUiTest {
        // Each generation spreads the ring a little further, as a node growing frame by frame does
        val base = ringGraph(NODE_COUNT, withEdges = false, seed = 1)
        val scene = ViewerScene(base)
        mainClock.autoAdvance = false
        setContent { scene.Content() }
        settle(scene)

        runOnIdle { scene.state.layoutedKuiver = base.spreadBy(1.5f) }
        repeat(4) {
            mainClock.advanceTimeByFrame()
            waitForIdle()
        }
        var previous = scene.nodeCenters.toMap()
        mainClock.advanceTimeByFrame()
        waitForIdle()
        val stepsBefore = scene.nodeCenters.mapValues { (id, center) ->
            (center - previous.getValue(id)).getDistance()
        }
        previous = scene.nodeCenters.toMap()

        runOnIdle { scene.state.layoutedKuiver = base.spreadBy(1.55f) }
        repeat(3) { frame ->
            mainClock.advanceTimeByFrame()
            waitForIdle()
            scene.nodeCenters.forEach { (id, center) ->
                val step = (center - previous.getValue(id)).getDistance()
                assertTrue(
                    step > stepsBefore.getValue(id) * MIN_SPEED_KEPT,
                    "node $id slowed to $step px on frame $frame after the retarget, " +
                            "moved ${stepsBefore.getValue(id)} px before"
                )
            }
            previous = scene.nodeCenters.toMap()
        }
    }

    @Test
    fun aTransitionSettlesWithoutAFinalJump() = runComposeUiTest {
        val base = ringGraph(NODE_COUNT, withEdges = false, seed = 1)
        val scene = ViewerScene(base)
        mainClock.autoAdvance = false
        setContent { scene.Content() }
        settle(scene)

        runOnIdle { scene.state.layoutedKuiver = base.spreadBy(2f) }
        var previous = scene.nodeCenters.toMap()
        val lastSteps = mutableMapOf<String, Float>()
        repeat(TRANSITION_FRAMES) {
            mainClock.advanceTimeByFrame()
            waitForIdle()
            scene.nodeCenters.forEach { (id, center) ->
                val step = (center - previous.getValue(id)).getDistance()
                if (step > 0f) lastSteps[id] = step
            }
            previous = scene.nodeCenters.toMap()
        }

        // Nodes travel about 1000 px here, the last 1% of that as one step is visible
        lastSteps.forEach { (id, step) ->
            assertTrue(step <= MAX_FINAL_STEP_PX, "node $id ended with a jump of $step px")
        }
    }

    @Test
    fun keyframesDriveTheProgressAsWritten() = runComposeUiTest {
        // Hold halfway for most of the animation
        val spec = keyframes {
            durationMillis = 1600
            0.5f at 160
            0.5f at 1440
        }
        val base = ringGraph(NODE_COUNT, withEdges = false, seed = 1)
        val scene = ViewerScene(
            base,
            config = KuiverViewerConfig(fitToContent = false, layoutAnimationSpec = spec)
        )
        mainClock.autoAdvance = false
        setContent { scene.Content() }
        settle(scene)
        val start = scene.nodeCenters.toMap()

        runOnIdle { scene.state.layoutedKuiver = base.spreadBy(2f) }
        repeat(50) { mainClock.advanceTimeByFrame() }
        waitForIdle()
        val middle = scene.nodeCenters.toMap()
        repeat(60) { mainClock.advanceTimeByFrame() }
        waitForIdle()

        scene.nodeCenters.forEach { (id, end) ->
            val halfway = (start.getValue(id) + end) / 2f
            val offBy = (middle.getValue(id) - halfway).getDistance()
            assertTrue(offBy <= TOLERANCE_PX, "node $id is $offBy px from halfway")
        }
    }

    @Test
    fun batchedEdgesDrawWhereTheEdgeComposablesDo() {
        // Endpoints resolved in the draw phase against the same math run in composition
        val graph = ringGraph(NODE_COUNT, withEdges = true, seed = 1).rebuild {
            edge("0", "3", id = "twin")
        }

        val composed = mutableMapOf<String, Offset>()
        runComposeUiTest {
            val scene = ViewerScene(graph, EdgeMode.BUILT_IN)
            mainClock.autoAdvance = false
            setContent { scene.Content() }
            settle(scene)
            composed.putAll(scene.edgeEnds)
        }

        val drawn = mutableMapOf<String, Offset>()
        runComposeUiTest {
            val scene = ViewerScene(graph, EdgeMode.BATCHED)
            mainClock.autoAdvance = false
            setContent { scene.Content() }
            settle(scene)
            drawn.putAll(scene.edgeArrowTips)
        }

        assertTrue(composed.isNotEmpty(), "no edges were composed")
        assertTrue(
            (composed.getValue("0->3") - composed.getValue("twin")).getDistance() > 1f,
            "parallel edges end at the same point"
        )
        assertEquals(composed.keys, drawn.keys, "batched layer drew a different set of edges")
        composed.forEach { (edgeKey, end) ->
            val distance = (drawn.getValue(edgeKey) - end).getDistance()
            assertTrue(
                distance <= TOLERANCE_PX,
                "batched edge $edgeKey ended $distance px away from the composed edge"
            )
        }
    }

    // Every node moved away from the origin by factor
    private fun Kuiver.spreadBy(factor: Float): Kuiver =
        withNodes(nodes.values.map { it.copy(position = it.position * factor) })

    private fun ComposeUiTest.settle(scene: ViewerScene) {
        repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
        waitForIdle()
        scene.resetCounters()
    }

    private companion object {
        const val NODE_COUNT = 24
        const val SETTLE_FRAMES = 12

        /** `EDGE_PADDING` in the renderer, at the test density of 1 */
        const val EDGE_PADDING_PX = 4f
        const val TOLERANCE_PX = 1.5f

        /** Fraction of the travelled distance a retarget must not give up */
        const val MIN_PROGRESS_KEPT = 0.5f

        /** Fraction of its speed a node has to keep through a retarget */
        const val MIN_SPEED_KEPT = 0.5f

        /** Frames for the default layout spring to come to rest */
        const val TRANSITION_FRAMES = 300

        /** Largest step allowed on the frame a transition ends, about the rounding of a placement */
        const val MAX_FINAL_STEP_PX = 1.5f
    }
}
