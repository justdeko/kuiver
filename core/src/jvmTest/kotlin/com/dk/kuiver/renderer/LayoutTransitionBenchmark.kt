package com.dk.kuiver.renderer

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import java.io.File
import java.time.LocalDate
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertTrue

// run: ./gradlew :core:benchmark (excluded from jvmTest, timings are noise on CI)
class LayoutTransitionBenchmark {

    @Test
    fun nodesAndEdges() = benchmark("nodes and edges", nodeCount = 300, withEdges = true)

    @Test
    fun nodesOnly() = benchmark("nodes only", nodeCount = 300, withEdges = false)

    /**
     * Renders a graph, moves every node to a second layout and times the frames it animates for.
     *
     * @param scenario label for the printed report
     * @param nodeCount how many nodes to render
     * @param withEdges whether to render edges between them
     */
    @OptIn(ExperimentalTestApi::class)
    private fun benchmark(scenario: String, nodeCount: Int, withEdges: Boolean) = runComposeUiTest {
        val generationA = ringGraph(nodeCount, withEdges, seed = 1)
        val generationB = ringGraph(nodeCount, withEdges, seed = 2)
        val scene = ViewerScene(generationA)

        mainClock.autoAdvance = false
        setContent { scene.Content() }

        // Warm up: the first transition in a fresh JVM is dominated by JIT
        repeat(WARMUP_TRANSITIONS) { round ->
            runOnIdle { scene.state.layoutedKuiver = if (round % 2 == 0) generationB else generationA }
            repeat(MEASURED_FRAMES) { mainClock.advanceTimeByFrame() }
            waitForIdle()
        }

        // Settle, then measure only the transition to the other generation
        runOnIdle { scene.state.layoutedKuiver = generationA }
        repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
        waitForIdle()
        scene.resetCounters()
        val positionsBefore = scene.nodeCenters.toMap()

        runOnIdle { scene.state.layoutedKuiver = generationB }

        val frameNanos = LongArray(MEASURED_FRAMES) {
            measureNanoTime {
                mainClock.advanceTimeByFrame()
                waitForIdle()
            }
        }

        val sorted = frameNanos.sorted()
        report(
            buildString {
                appendLine()
                appendLine("=== layout transition frame cost: $scenario ===")
                appendLine("graph                       $nodeCount nodes, ${generationB.edges.size} edges")
                appendLine("frames measured             ${frameNanos.size}")
                appendLine("median frame                ${sorted[sorted.size / 2] / 1_000} us")
                appendLine("p90 frame                   ${sorted[(sorted.size * 9) / 10] / 1_000} us")
                appendLine("worst frame                 ${sorted.last() / 1_000} us")
                appendLine("total                       ${frameNanos.sum() / 1_000_000} ms")
                appendLine("node content recompositions ${scene.nodeCompositions}")
                appendLine("edge content recompositions ${scene.edgeCompositions}")
            }
        )

        val moved = scene.nodeCenters.count { (id, center) -> positionsBefore[id] != center }
        assertTrue(moved > nodeCount / 2, "expected the transition to move nodes, moved=$moved")
    }

    /**
     * Prints [text] and appends it to the report file, when the `benchmark` task provided one.
     *
     * @param text the report of a single scenario
     */
    private fun report(text: String) {
        print(text)
        System.getProperty("kuiver.benchmark.report")?.let { path ->
            val file = File(path)
            file.parentFile?.mkdirs()
            if (!file.exists()) file.writeText(header())
            file.appendText(text)
        }
    }

    private fun header(): String = buildString {
        appendLine("layout transition frame cost, see docs/benchmarks/layout-transition.md")
        appendLine(LocalDate.now().toString())
        appendLine(
            listOf("os.arch", "os.name", "java.version")
                .joinToString(", ") { System.getProperty(it).orEmpty() }
        )
    }

    private companion object {
        const val WARMUP_TRANSITIONS = 6
        const val SETTLE_FRAMES = 60
        const val MEASURED_FRAMES = 40
    }
}
