package com.dk.kuiver.sample

/**
 * Title and subtitle of one tour caption.
 *
 * @property large whether the title is set large, for the intro and outro
 */
internal data class TourText(val title: String, val body: String, val large: Boolean = false)

/**
 * Every text of the showcase screen and its guided tour, in one place for editing. The texts on
 * the order steps themselves live with their data in [OrderSteps] and [ExtraSteps].
 */
internal object ShowcaseCopy {
    const val TITLE = "Kuiver"
    const val PLAY_TOUR = "Play tour"
    const val STOP_TOUR = "Stop tour"
    const val PLAYGROUND = "Playground"
    const val STRESS_TEST = "Stress test"

    /**
     * Subtitle under the screen title
     *
     * @param steps number of steps in the graph
     * @param connections number of edges in the graph
     * @return the subtitle
     */
    fun subtitle(steps: Int, connections: Int): String = "$steps steps · $connections connections"

    // Tour captions, in the order the tour shows them
    val intro = TourText("Kuiver", "Directed graphs for Compose Multiplatform", large = true)
    val grow = TourText("Live Layout", "Every change is laid out and can be animated")
    val composables = TourText("Nodes are composables", "Can be updated like any other composable")
    val measured = TourText("Auto-measured layouts", "Graph layout adapts to implicit node sizes")
    val layoutHierarchical = TourText("Layouts", "Hierarchical")
    val layoutOrganic = TourText("Layouts", "Force-directed")
    val layoutOrbit = TourText("Layouts", "Custom LayoutProvider, e.g. circle")
    val edges = TourText("Edge Customizations", "Ports, labels, loops and parallel edges")
    val interaction = TourText("Graph Interactions", "Hover, select, drag")
    val runtime = TourText("Change graph at runtime", "Add, remove and update nodes and edges")
    val camera = TourText("Zoom, pan, focus", "Programmatic or user-driven")
    val outro = TourText("Kuiver", "github.com/justdeko/kuiver", large = true)
}
