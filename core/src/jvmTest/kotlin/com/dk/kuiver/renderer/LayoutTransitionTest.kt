package com.dk.kuiver.renderer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
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
    fun batchedEdgesDrawWhereTheEdgeComposablesDo() {
        // Endpoints resolved in the draw phase against the same math run in composition
        val graph = ringGraph(NODE_COUNT, withEdges = true, seed = 1)

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
        assertEquals(composed.keys, drawn.keys, "batched layer drew a different set of edges")
        composed.forEach { (edgeKey, end) ->
            val distance = (drawn.getValue(edgeKey) - end).getDistance()
            assertTrue(
                distance <= TOLERANCE_PX,
                "batched edge $edgeKey ended $distance px away from the composed edge"
            )
        }
    }

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
    }
}
