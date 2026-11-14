package com.dk.kuiver.sample

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schema
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomInMap
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.dk.kuiver.KuiverViewerState
import com.dk.kuiver.SelectionMode
import com.dk.kuiver.model.EdgeType
import com.dk.kuiver.rememberKuiverViewerState
import com.dk.kuiver.renderer.KuiverInteractionCallbacks
import com.dk.kuiver.renderer.KuiverViewer
import com.dk.kuiver.renderer.KuiverViewerConfig
import com.dk.kuiver.sample.components.ToolbarButton
import com.dk.kuiver.util.calculateNodeBounds
import com.dk.kuiver.util.calculatePositionBounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

/**
 * Where the camera should go: the whole graph, one node, or a pan from one node to another.
 *
 * @property awaitLayout whether to move only once a pending relayout has settled
 * @property panTo node to pan to from [focusId]
 * @property panMillis duration of the pan
 */
internal data class CameraRequest(
    val focusId: String?,
    val version: Int,
    val awaitLayout: Boolean = true,
    val panTo: String? = null,
    val panMillis: Int = 0
)

/**
 * Everything the showcase shows besides node positions, driven by the controls and the guided
 * tour alike.
 */
@Stable
internal class ShowcaseState(
    private val scope: CoroutineScope,
    var homeLayout: ShowcaseLayout
) {
    var visibleIds by mutableStateOf(OrderSteps.keys.toList())
        private set
    var layout by mutableStateOf(homeLayout)
        private set
    var edgeStyle by mutableStateOf(ShowcaseEdgeStyle.CURVES)
    var statuses by mutableStateOf(emptyMap<String, StepStatus>())
        private set
    var expanded by mutableStateOf(emptySet<String>())
        private set
    var leaving by mutableStateOf(emptySet<String>())
        private set
    var caption by mutableStateOf<TourText?>(null)
        private set
    var camera by mutableStateOf(CameraRequest(null, 0))
        private set
    var isRunning by mutableStateOf(false)
        private set
    var isTouring by mutableStateOf(false)
        private set

    // Pans, drags and scrolls by the user, a camera move requested before one is dropped
    var userCameraMoves = 0
        private set

    // Stagger of the nodes that pop in together
    var entranceDelays by mutableStateOf(staggered(OrderSteps.keys.toList()))
        private set

    private var runJob: Job? = null
    private var tourJob: Job? = null

    // Switch the layout and refit once it lands
    fun selectLayout(newLayout: ShowcaseLayout) {
        if (newLayout == layout) return
        layout = newLayout
        fit()
    }

    // Expand and focus a step, or collapse it and refit
    fun toggleExpanded(nodeId: String) {
        if (nodeId in expanded) {
            expanded = expanded - nodeId
            fit()
        } else {
            expanded = expanded + nodeId
            focus(nodeId)
        }
    }

    // Collapse every expanded step
    fun collapseAll() {
        if (expanded.isEmpty()) return
        expanded = emptySet()
        fit()
    }

    // Show one more order step and refit
    fun reveal(nodeId: String) {
        visibleIds = visibleIds + nodeId
        fit()
    }

    // Add the next extra step
    fun addStep() {
        val next = ExtraSteps.keys.firstOrNull { it !in visibleIds } ?: return
        visibleIds = visibleIds + next
        fit()
    }

    // Shrink the step away, then drop it from the graph
    fun remove(nodeId: String) {
        // Keep the order itself, everything hangs off it
        if (nodeId == "order" || nodeId in leaving) return
        leaving = leaving + nodeId
        scope.launch {
            delay(240)
            visibleIds = visibleIds - nodeId
            leaving = leaving - nodeId
            expanded = expanded - nodeId
            fit()
        }
    }

    // Back to the plain order in the flow layout
    fun reset(viewer: KuiverViewerState) {
        stopTour()
        viewer.clearManualPositions()
        restore(OrderSteps.keys.toList())
        fit()
    }

    // Start or stop playing the order through
    fun toggleRun() {
        if (isRunning) {
            runJob?.cancel()
            return
        }
        runJob = scope.launch {
            isRunning = true
            try {
                runOrder()
            } finally {
                isRunning = false
            }
        }
    }

    // Start or stop the guided tour
    fun toggleTour(viewer: KuiverViewerState) {
        if (isTouring) {
            stopTour()
            return
        }
        tourJob = scope.launch {
            isTouring = true
            try {
                playTour(viewer)
                // for playing loop in gif
                //playGifLoop(viewer)
            } finally {
                isTouring = false
                caption = null
            }
        }
    }

    // Count a camera move by the user
    fun onUserCameraMove() {
        userCameraMoves++
    }

    // Fit the whole graph, by default once the layout settles
    fun fit(awaitLayout: Boolean = true) {
        camera = CameraRequest(null, camera.version + 1, awaitLayout)
    }

    // Center the step once the layout settles
    fun focus(nodeId: String) {
        camera = CameraRequest(nodeId, camera.version + 1)
    }

    // Zoom in on fromId, then pan slowly to toId
    fun pan(fromId: String, toId: String, millis: Int) {
        camera = CameraRequest(
            fromId,
            camera.version + 1,
            awaitLayout = false,
            panTo = toId,
            panMillis = millis
        )
    }

    // Cancel the tour and its run
    private fun stopTour() {
        tourJob?.cancel()
        runJob?.cancel()
    }

    // Reset the run, expansion and styles, showing ids
    fun restore(ids: List<String>) {
        runJob?.cancel()
        statuses = emptyMap()
        expanded = emptySet()
        leaving = emptySet()
        layout = homeLayout
        edgeStyle = ShowcaseEdgeStyle.CURVES
        entranceDelays = staggered(ids)
        visibleIds = ids
    }

    // Show a tour caption
    fun show(text: TourText) {
        caption = text
    }

    // Plays the order through, with a declined card and a missed delivery
    suspend fun runOrder() {
        statuses = emptyMap()
        step("order", 700)
        coroutineScope {
            launch { step("stock", 1300) }
            launch {
                step("payment", 900, fails = true)
                step("payment", 1000, retry = true)
                launch { step("receipt", 800) }
            }
        }
        step("pack", 1200)
        stepExtras(after = "pack")
        step("hub", 1000)
        coroutineScope {
            launch { step("notify", 900) }
            launch {
                step("courier", 1100, fails = true)
                step("hub", 700)
                step("courier", 1000)
                step("delivered", 900)
            }
        }
    }

    // Run the visible extra steps that follow after, side by side
    private suspend fun stepExtras(after: String) = coroutineScope {
        visibleIds.filter { ExtraSteps[it]?.after == after }.forEach { stepId ->
            launch { step(stepId, 900) }
        }
    }

    // Run one step for millis, then mark it done or failed
    private suspend fun step(
        nodeId: String,
        millis: Long,
        fails: Boolean = false,
        retry: Boolean = false
    ) {
        if (nodeId !in visibleIds) return
        statuses = statuses + (nodeId to if (retry) StepStatus.RETRYING else StepStatus.ACTIVE)
        delay(millis)
        statuses = statuses + (nodeId to if (fails) StepStatus.FAILED else StepStatus.DONE)
    }

    private companion object {
        // Entrance delay per node, 70 ms apart
        fun staggered(ids: List<String>): Map<String, Int> =
            ids.withIndex().associate { (index, id) -> id to index * 70 }
    }
}

/**
 * Showcase of most Kuiver features on an online order travelling from checkout to the front
 * door, with a guided tour for screen recordings. The start screen of the sample.
 *
 * @param isDarkTheme whether the app is in dark mode
 * @param onThemeToggle switches between light and dark mode
 * @param onOpenPlayground opens the playground
 * @param onOpenStressTest opens the stress test
 */
@Composable
fun ShowcaseDemo(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onOpenPlayground: () -> Unit,
    onOpenStressTest: () -> Unit
) {
    MaterialExpressiveTheme(colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()) {
        BoxWithConstraints {
            ShowcaseScreen(
                maxWidth,
                maxHeight,
                isDarkTheme,
                onThemeToggle,
                onOpenPlayground,
                onOpenStressTest
            )
        }
    }
}

/**
 * Content of [ShowcaseDemo] inside the expressive theme.
 */
@OptIn(FlowPreview::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShowcaseScreen(
    windowWidth: Dp,
    windowHeight: Dp,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onOpenPlayground: () -> Unit,
    onOpenStressTest: () -> Unit
) {
    val scope = rememberCoroutineScope()
    // Left to right flow leaves a portrait screen mostly empty
    val homeLayout = if (windowWidth < windowHeight) ShowcaseLayout.STACK else ShowcaseLayout.FLOW
    val showcase = remember { ShowcaseState(scope, homeLayout) }
    showcase.homeLayout = homeLayout
    val graph = remember(showcase.visibleIds, showcase.layout) {
        buildShowcaseGraph(showcase.visibleIds, showcase.layout)
    }

    // Room taken by the caption at the top and the toolbar at the bottom
    var bottomInset by remember { mutableStateOf(0.dp) }
    val topInset = if (showcase.caption != null) CaptionHeight else 0.dp

    val viewer = rememberKuiverViewerState(
        initialKuiver = graph,
        layoutConfig = remember(showcase.layout) { showcase.layout.config() }
    )
    LaunchedEffect(graph) { viewer.updateKuiver(graph) }

    // Move the camera once the layout stops changing
    LaunchedEffect(showcase.camera) {
        val request = showcase.camera
        if (request.version == 0) return@LaunchedEffect
        val userMoves = showcase.userCameraMoves
        if (request.awaitLayout) withTimeoutOrNull(1200) {
            snapshotFlow { viewer.layoutedKuiver }.drop(1).debounce(80).first()
        }
        // The user took over the camera in the meantime
        if (showcase.userCameraMoves != userMoves) return@LaunchedEffect
        val focusId = request.focusId
        val panTo = request.panTo
        val top = topInset
        when {
            focusId == null -> viewer.fitBetween(top, bottomInset)
            panTo != null -> viewer.panBetween(focusId, panTo, request.panMillis, top, bottomInset)
            else -> viewer.focusOn(focusId, top, bottomInset)
        }
    }
    LaunchedEffect(topInset, bottomInset) {
        viewer.updateContentOffset(DpOffset(0.dp, topInset - bottomInset))
    }
    val density = LocalDensity.current

    val flowPhase = rememberInfiniteTransition(label = "flow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "flow_phase"
    )

    val motion = MaterialTheme.motionScheme
    val viewerConfig = remember(motion) {
        KuiverViewerConfig(
            contentPadding = 0.86f,
            minScale = 0.2f,
            maxScale = 2f,
            selectionMode = SelectionMode.SINGLE,
            nodeDragEnabled = true,
            hoverEnabled = true,
            edgeSelectionMode = SelectionMode.SINGLE,
            edgeHoverEnabled = true,
            scaleAnimationSpec = spring(dampingRatio = 1f, stiffness = 70f),
            offsetAnimationSpec = spring(
                dampingRatio = 1f,
                stiffness = 70f,
                visibilityThreshold = DpOffset.VisibilityThreshold
            ),
            layoutAnimationSpec = motion.slowSpatialSpec(),
            enterAnimationSpec = motion.slowEffectsSpec()
        )
    }
    val callbacks = remember(showcase) {
        KuiverInteractionCallbacks(
            onNodeClick = { node -> showcase.toggleExpanded(node.id) },
            onNodeLongPress = { node -> showcase.remove(node.id) },
            onCanvasClick = { showcase.collapseAll() }
        )
    }

    // Nodes and edges outside the hovered or selected neighbourhood fade back
    val interaction = viewer.interaction
    val focusNode = interaction.hoveredNodeId ?: interaction.selectedNodeIds.singleOrNull()
    val focusEdge = if (focusNode == null) {
        interaction.hoveredEdgeId ?: interaction.selectedEdgeIds.singleOrNull()
    } else {
        null
    }
    val laidOut = viewer.layoutedKuiver
    val related = remember(focusNode, focusEdge, laidOut.edges) {
        when {
            focusNode != null -> laidOut.edges
                .filter { it.fromId == focusNode || it.toId == focusNode }
                .flatMap { listOf(it.fromId, it.toId) }
                .toSet() + focusNode

            focusEdge != null -> laidOut.edges.firstOrNull { it.id == focusEdge }
                ?.let { setOf(it.fromId, it.toId) }

            else -> null
        }
    }
    val ports = remember(graph) {
        graph.edges
            .flatMap {
                listOfNotNull(
                    it.fromAnchor?.let { anchor -> it.fromId to anchor },
                    it.toAnchor?.let { anchor -> it.toId to anchor }
                )
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.toSet() }
    }

    val scheme = MaterialTheme.colorScheme
    Scaffold(
        containerColor = scheme.surfaceContainerLow,
        topBar = {
            Column(Modifier.background(scheme.surfaceContainerLow)) {
                TopAppBar(
                    title = {
                        Text(
                            ShowcaseCopy.TITLE,
                            style = MaterialTheme.typography.titleLargeEmphasized
                        )
                    },
                    subtitle = {
                        Text(
                            ShowcaseCopy.subtitle(
                                viewer.kuiver.nodes.size,
                                viewer.kuiver.edges.size
                            )
                        )
                    },
                    actions = {
                        FilledTonalButton(onClick = { showcase.toggleTour(viewer) }) {
                            Icon(
                                if (showcase.isTouring) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(if (showcase.isTouring) ShowcaseCopy.STOP_TOUR else ShowcaseCopy.PLAY_TOUR)
                        }
                        IconButton(onClick = onThemeToggle) {
                            Icon(
                                if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = if (isDarkTheme) "Switch to light mode" else "Switch to dark mode"
                            )
                        }
                        Box {
                            var menuOpen by remember { mutableStateOf(false) }
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More screens")
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(ShowcaseCopy.PLAYGROUND) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onOpenPlayground()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(ShowcaseCopy.STRESS_TEST) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Speed,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onOpenStressTest()
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surfaceContainerLow)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ConnectedToggleGroup(
                        options = ShowcaseLayout.entries,
                        selected = showcase.layout,
                        label = { it.label },
                        icon = { it.icon },
                        onSelect = showcase::selectLayout
                    )
                    ConnectedToggleGroup(
                        options = ShowcaseEdgeStyle.entries,
                        selected = showcase.edgeStyle,
                        label = { it.label },
                        icon = { it.icon },
                        onSelect = { showcase.edgeStyle = it }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            KuiverViewer(
                state = viewer,
                modifier = Modifier
                    .fillMaxSize()
                    .onUserCameraMove(showcase::onUserCameraMove),
                config = viewerConfig,
                callbacks = callbacks,
                nodeContent = { node ->
                    val spec = AllSteps[node.id] ?: return@KuiverViewer
                    StepNodeContent(
                        spec = spec,
                        status = showcase.statuses[node.id] ?: StepStatus.WAITING,
                        expanded = node.id in showcase.expanded,
                        leaving = node.id in showcase.leaving,
                        dimmed = related != null && node.id !in related,
                        isSelected = isSelected,
                        isHovered = isHovered,
                        isDragging = isDragging,
                        ports = ports[node.id].orEmpty(),
                        appearDelayMillis = showcase.entranceDelays[node.id] ?: 0
                    )
                },
                edgeContent = { edge, from, to ->
                    val loop = edge.type == EdgeType.BACK || edge.fromId == edge.toId
                    val spec = EdgeSpecs[edge.id]
                    FlowEdge(
                        path = rememberShowcaseEdgePath(edge, from, to, showcase.edgeStyle),
                        color = if (loop) scheme.tertiary else scheme.primary,
                        idleColor = scheme.outlineVariant,
                        state = edgeState(edge, showcase.statuses),
                        dashed = loop,
                        emphasized = isSelected || isHovered,
                        dimmed = when {
                            focusNode != null -> edge.fromId != focusNode && edge.toId != focusNode
                            focusEdge != null -> edge.id != focusEdge
                            else -> false
                        },
                        label = spec?.label,
                        labelAt = spec?.labelAt ?: 0.5f,
                        flowPhase = flowPhase
                    )
                }
            )

            TourCaption(
                caption = showcase.caption,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp)
            )

            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .onSizeChanged { bottomInset = with(density) { it.height.toDp() } + 16.dp },
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                floatingActionButton = {
                    FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = showcase::toggleRun) {
                        Icon(
                            if (showcase.isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = if (showcase.isRunning) "Stop order" else "Play order"
                        )
                    }
                }
            ) {
                ToolbarButton(Icons.Filled.Add, "Add step", showcase::addStep)
                ToolbarButton(Icons.Filled.ZoomOut, "Zoom out", viewer::zoomOut)
                ToolbarButton(Icons.Filled.ZoomIn, "Zoom in", viewer::zoomIn)
                ToolbarButton(Icons.Filled.ZoomInMap, "Fit graph") {
                    viewer.fitBetween(
                        topInset,
                        bottomInset
                    )
                }
                ToolbarButton(Icons.Filled.Refresh, "Reset") { showcase.reset(viewer) }
            }
        }
    }
}

/**
 * Reports pans, drags and scrolls the user starts on the viewer, taps excluded.
 */
private fun Modifier.onUserCameraMove(onMove: () -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        var travelled = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            when (event.type) {
                PointerEventType.Scroll -> onMove()
                PointerEventType.Press -> travelled = 0f
                PointerEventType.Move -> event.changes.firstOrNull { it.pressed }?.let { change ->
                    travelled += (change.position - change.previousPosition).getDistance()
                    if (travelled > viewConfiguration.touchSlop) onMove()
                }
            }
        }
    }
}

/**
 * Icon of a layout option.
 */
private val ShowcaseLayout.icon: ImageVector
    get() = when (this) {
        ShowcaseLayout.FLOW -> Icons.Filled.AccountTree
        ShowcaseLayout.STACK -> Icons.Filled.Schema
        ShowcaseLayout.ORGANIC -> Icons.Filled.BubbleChart
        ShowcaseLayout.ORBIT -> Icons.Filled.DonutLarge
    }

/**
 * Icon of an edge style option.
 */
private val ShowcaseEdgeStyle.icon: ImageVector
    get() = when (this) {
        ShowcaseEdgeStyle.CURVES -> Icons.Filled.Gesture
        ShowcaseEdgeStyle.CIRCUIT -> Icons.Filled.Polyline
        ShowcaseEdgeStyle.STRAIGHT -> Icons.AutoMirrored.Filled.ShowChart
    }

/**
 * Fits the graph between the overlays at the top and bottom, animated.
 */
private fun KuiverViewerState.fitBetween(top: Dp, bottom: Dp) {
    val bounds = layoutedKuiver.nodes.values.calculateNodeBounds()
    if (bounds.width <= 0.dp || bounds.height <= 0.dp || canvasWidth <= 0.dp) return
    val scale = min(
        canvasWidth * 0.9f / bounds.width,
        (canvasHeight - top - bottom) * 0.88f / bounds.height
    ).coerceIn(0.2f, 1.6f)
    moveCamera(bounds.centerX, bounds.centerY, scale, top, bottom)
}

/**
 * Moves the camera to [nodeId], zoomed in enough to read an expanded step.
 */
private fun KuiverViewerState.focusOn(nodeId: String, top: Dp, bottom: Dp) {
    val node = layoutedKuiver.nodes[nodeId] ?: return
    moveCamera(node.position.x, node.position.y, focusScale(), top, bottom)
}

/**
 * Zooms in on [fromId], then pans to [toId] over [millis] at the same zoom.
 */
private suspend fun KuiverViewerState.panBetween(
    fromId: String,
    toId: String,
    millis: Int,
    top: Dp,
    bottom: Dp
) {
    val from = layoutedKuiver.nodes[fromId]?.position ?: return
    val to = layoutedKuiver.nodes[toId]?.position ?: return
    val scale = focusScale()
    moveCamera(from.x, from.y, scale, top, bottom)
    delay(PanLeadInMillis)
    animate(0f, 1f, animationSpec = tween(millis, easing = EaseInOutSine)) { fraction, _ ->
        moveCamera(
            lerp(from.x, to.x, fraction),
            lerp(from.y, to.y, fraction),
            scale,
            top,
            bottom,
            animated = false
        )
    }
}

/**
 * Zoom that fits an expanded step comfortably.
 */
private fun KuiverViewerState.focusScale(): Float =
    min(1.1f, canvasWidth * 0.45f / ExpandedStepWidth).coerceAtLeast(0.4f)

/**
 * Centers the graph point at [x], [y] between the overlays.
 */
private fun KuiverViewerState.moveCamera(
    x: Dp,
    y: Dp,
    scale: Float,
    top: Dp,
    bottom: Dp,
    animated: Boolean = true
) {
    // Node positions are drawn relative to the centre of the position bounds
    val origin = layoutedKuiver.nodes.values.calculatePositionBounds()
    updateTransform(
        scale = scale,
        offset = DpOffset(
            -(x - origin.centerX) * scale,
            (top - bottom) / 2 - (y - origin.centerY) * scale
        ),
        animated = animated
    )
}

/**
 * Single choice row of connected toggle buttons.
 */
@Composable
private fun <T> ConnectedToggleGroup(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    icon: (T) -> ImageVector,
    onSelect: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                modifier = Modifier.semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
            ) {
                Icon(
                    icon(option),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(label(option))
            }
        }
    }
}
