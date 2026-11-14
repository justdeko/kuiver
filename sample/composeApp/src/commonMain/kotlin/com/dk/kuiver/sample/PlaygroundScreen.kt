package com.dk.kuiver.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomInMap
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk.kuiver.SelectionMode
import com.dk.kuiver.model.EdgeType
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.model.buildKuiver
import com.dk.kuiver.model.layout.LayoutConfig
import com.dk.kuiver.model.layout.LayoutDirection
import com.dk.kuiver.rememberSaveableKuiverViewerState
import com.dk.kuiver.renderer.KuiverInteractionCallbacks
import com.dk.kuiver.renderer.KuiverViewer
import com.dk.kuiver.renderer.KuiverViewerConfig
import com.dk.kuiver.sample.components.ConnectedToggleGroup
import com.dk.kuiver.sample.components.ConnectionModeBanner
import com.dk.kuiver.sample.components.EdgeCreationForm
import com.dk.kuiver.sample.components.NodeCreationForm
import com.dk.kuiver.sample.components.ToolbarButton
import com.dk.kuiver.ui.KuiverDefaults
import com.dk.kuiver.ui.OrthogonalEdgeContentWithLabel
import com.dk.kuiver.ui.StyledEdgeContent
import com.dk.kuiver.ui.StyledRightAngleEdgeContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

private const val ZoomDurationMillis = 250

private enum class PlaygroundEdgeStyle(val label: String) {
    REGULAR("Regular"),
    S_CURVE("S-curve"),
    RIGHT_ANGLE("Right angle")
}

private val InitialNodeData = mapOf(
    "1" to NodeData("Start", NodeColorType.PINK),
    "2" to NodeData("Process", NodeColorType.YELLOW),
    "3" to NodeData("Review", NodeColorType.GREEN),
    "4" to NodeData("End", NodeColorType.BLUE)
)

private val InitialEdges = listOf(
    KuiverEdge("1", "2", id = "e1"),
    KuiverEdge("1", "3", id = "e2"),
    KuiverEdge("2", "4", id = "e3"),
    KuiverEdge("3", "4", id = "e4")
)

/**
 * Tap two nodes to connect them, long-press a node to remove it.
 */
@Composable
fun PlaygroundScreen(onNavigateBack: () -> Unit) {
    BoxWithConstraints {
        PlaygroundContent(
            portrait = maxWidth < maxHeight,
            compact = maxWidth < 600.dp,
            onNavigateBack = onNavigateBack
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaygroundContent(portrait: Boolean, compact: Boolean, onNavigateBack: () -> Unit) {
    var layoutAlgorithm by rememberSaveable { mutableStateOf(LayoutAlgorithm.HIERARCHICAL) }
    var layoutDirection by rememberSaveable {
        mutableStateOf(if (portrait) LayoutDirection.VERTICAL else LayoutDirection.HORIZONTAL)
    }
    var edgeStyle by rememberSaveable { mutableStateOf(PlaygroundEdgeStyle.REGULAR) }
    var showPorts by rememberSaveable { mutableStateOf(true) }
    var showDebugBounds by rememberSaveable { mutableStateOf(false) }

    // App data per id, the graph only holds ids
    var nodeData by rememberSaveable(stateSaver = NodeDataMapSaver) { mutableStateOf(InitialNodeData) }
    var edgeData by rememberSaveable(stateSaver = EdgeDataMapSaver) { mutableStateOf(emptyMap<String, EdgeData>()) }
    var nextNodeId by rememberSaveable { mutableStateOf(InitialNodeData.size + 1) }
    var nextEdgeId by rememberSaveable { mutableStateOf(InitialEdges.size + 1) }

    var showNodeForm by rememberSaveable { mutableStateOf(false) }
    var showEdgeForm by rememberSaveable { mutableStateOf(false) }
    var newNodeLabel by rememberSaveable { mutableStateOf("") }
    var newNodeColor by rememberSaveable { mutableStateOf<NodeColorType?>(null) }
    var fromNodeLabel by rememberSaveable { mutableStateOf("") }
    var toNodeLabel by rememberSaveable { mutableStateOf("") }
    var newEdgeLabel by rememberSaveable { mutableStateOf("") }
    var newEdgeLabelPosition by rememberSaveable { mutableStateOf(0.5f) }
    var isAutoGenerating by rememberSaveable { mutableStateOf(false) }

    val vertical = layoutAlgorithm == LayoutAlgorithm.HIERARCHICAL &&
            layoutDirection == LayoutDirection.VERTICAL
    val layoutConfig = remember(layoutAlgorithm, layoutDirection) {
        when (layoutAlgorithm) {
            LayoutAlgorithm.HIERARCHICAL -> LayoutConfig.Hierarchical(direction = layoutDirection)
            LayoutAlgorithm.FORCE_DIRECTED -> LayoutConfig.ForceDirected()
        }
    }
    val viewer = rememberSaveableKuiverViewerState(
        initialKuiver = remember {
            buildKuiver {
                InitialNodeData.keys.forEach { addNode(KuiverNode(it)) }
                InitialEdges.forEach { addEdge(it) }
            }.withPorts()
        },
        layoutConfig = layoutConfig
    )
    val connection = rememberConnectionState()

    // Apply every edit through here to update the ports
    fun commit(graph: Kuiver) {
        viewer.updateKuiver(if (showPorts) graph.withPorts() else graph.withoutPorts())
    }

    fun addNode(label: String, color: NodeColorType) {
        val id = (nextNodeId++).toString()
        nodeData = nodeData + (id to NodeData(label, color))
        commit(viewer.kuiver.withNode(KuiverNode(id)))
    }

    fun addEdge(fromId: String, toId: String): String {
        val id = "e${nextEdgeId++}"
        commit(viewer.kuiver.withEdge(KuiverEdge(fromId, toId, id = id)))
        return id
    }

    LaunchedEffect(showPorts) { commit(viewer.kuiver) }

    // Recenter once a newly picked layout lands
    var layoutPicked by remember { mutableStateOf(false) }
    LaunchedEffect(layoutConfig) {
        if (!layoutPicked) {
            layoutPicked = true
            return@LaunchedEffect
        }
        val before = viewer.layoutedKuiver
        withTimeoutOrNull(2000.milliseconds) {
            snapshotFlow { viewer.layoutedKuiver }.first { it !== before }
        }
        viewer.centerGraph()
    }

    // Alternate between adding a node and connecting two random nodes
    LaunchedEffect(isAutoGenerating) {
        var nodeStep = true
        while (isAutoGenerating) {
            delay(1000.milliseconds)
            val graph = viewer.kuiver
            if (nodeStep || graph.nodes.size < 2) {
                addNode("Node $nextNodeId", NodeColorType.ALL.random())
            } else {
                val ids = graph.nodes.keys.toList()
                val from = ids.random()
                val to = ids.filter { it != from }.random()
                if (graph.edgesBetween(from, to).isEmpty()) addEdge(from, to)
            }
            nodeStep = !nodeStep
        }
    }

    // Center the graph in the area between the overlays and the toolbar
    var overlayHeight by remember { mutableStateOf(0.dp) }
    var toolbarHeight by remember { mutableStateOf(0.dp) }
    LaunchedEffect(overlayHeight, toolbarHeight) {
        viewer.updateContentOffset(DpOffset(0.dp, overlayHeight - toolbarHeight))
    }
    val density = LocalDensity.current

    val callbacks = KuiverInteractionCallbacks(
        onNodeClick = { node ->
            val source = connection.sourceNode
            if (source == null) {
                connection.start(node)
            } else {
                addEdge(source.id, node.id)
                connection.reset()
            }
        },
        onNodeLongPress = { node ->
            commit(viewer.kuiver.withoutNode(node.id))
            nodeData = nodeData - node.id
            connection.reset()
        },
        onCanvasClick = { connection.reset() }
    )

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Playground") },
                    subtitle = {
                        Text("${viewer.kuiver.nodes.size} nodes · ${viewer.kuiver.edges.size} edges")
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ConnectedToggleGroup(
                        options = LayoutAlgorithm.entries,
                        selected = layoutAlgorithm,
                        label = { if (it == LayoutAlgorithm.HIERARCHICAL) "Hierarchical" else "Force" },
                        onSelect = { layoutAlgorithm = it }
                    )
                    AnimatedVisibility(layoutAlgorithm == LayoutAlgorithm.HIERARCHICAL) {
                        ConnectedToggleGroup(
                            options = LayoutDirection.entries,
                            selected = layoutDirection,
                            label = { if (it == LayoutDirection.HORIZONTAL) "Horizontal" else "Vertical" },
                            icon = {
                                if (it == LayoutDirection.HORIZONTAL) {
                                    Icons.AutoMirrored.Filled.ArrowForward
                                } else {
                                    Icons.Filled.ArrowDownward
                                }
                            },
                            onSelect = { layoutDirection = it }
                        )
                    }
                    ConnectedToggleGroup(
                        options = PlaygroundEdgeStyle.entries,
                        selected = edgeStyle,
                        label = { it.label },
                        onSelect = { edgeStyle = it }
                    )
                    FilterChip(
                        selected = showPorts,
                        onClick = { showPorts = !showPorts },
                        label = { Text("Ports") }
                    )
                    FilterChip(
                        selected = showDebugBounds,
                        onClick = { showDebugBounds = !showDebugBounds },
                        label = { Text("Debug bounds") }
                    )
                    FilterChip(
                        selected = false,
                        enabled = viewer.manualPositions.isNotEmpty(),
                        onClick = viewer::clearManualPositions,
                        label = { Text("Reset positions") }
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
                config = KuiverViewerConfig(
                    showDebugBounds = showDebugBounds,
                    contentPadding = 0.7f,
                    maxScale = 2f,
                    animateInitialPlacement = true,
                    scaleAnimationSpec = tween(ZoomDurationMillis, easing = FastOutSlowInEasing),
                    offsetAnimationSpec = tween(ZoomDurationMillis, easing = FastOutSlowInEasing),
                    selectionMode = SelectionMode.SINGLE,
                    nodeDragEnabled = true,
                    hoverEnabled = true,
                    edgeSelectionMode = SelectionMode.SINGLE,
                    edgeHoverEnabled = true
                ),
                callbacks = callbacks,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .graphKeyboardControls(viewer),
                nodeContent = { node ->
                    val data = nodeData[node.id] ?: return@KuiverViewer
                    PlaygroundNode(
                        data = data,
                        ports = viewer.kuiver.portsOf(node.id, vertical),
                        isSelected = isSelected,
                        isHighlighted = isHovered || isDragging
                    )
                },
                edgeContent = { edge, from, to ->
                    PlaygroundEdge(
                        edge = edge,
                        from = from,
                        to = to,
                        style = edgeStyle,
                        data = edgeData[edge.id],
                        strokeWidth = when {
                            isSelected -> 5.dp
                            isHovered -> 3.5.dp
                            else -> 2.5.dp
                        }
                    )
                }
            )

            Column(
                Modifier.onSizeChanged { overlayHeight = with(density) { it.height.toDp() } }
            ) {
                AnimatedVisibility(connection.isActive) {
                    ConnectionModeBanner(
                        sourceNodeLabel = connection.sourceNode?.let { nodeData[it.id]?.label }
                            .orEmpty(),
                        onCancel = connection::reset
                    )
                }
                NodeCreationForm(
                    visible = showNodeForm,
                    newNodeData = newNodeLabel,
                    selectedNodeColorType = newNodeColor,
                    onNodeDataChange = { newNodeLabel = it },
                    onColorTypeSelect = { newNodeColor = it },
                    onCreateNode = {
                        val label = newNodeLabel.ifBlank { "Node $nextNodeId" }
                        if (nodeData.values.none { it.label == label }) {
                            addNode(label, newNodeColor ?: NodeColorType.ALL.random())
                            newNodeLabel = ""
                        }
                    }
                )
                EdgeCreationForm(
                    visible = showEdgeForm,
                    hasNodes = viewer.kuiver.nodes.isNotEmpty(),
                    fromNodeLabel = fromNodeLabel,
                    toNodeLabel = toNodeLabel,
                    onFromNodeLabelChange = { fromNodeLabel = it },
                    onToNodeLabelChange = { toNodeLabel = it },
                    edgeLabel = newEdgeLabel,
                    onEdgeLabelChange = { newEdgeLabel = it },
                    labelPosition = newEdgeLabelPosition,
                    onLabelPositionChange = { newEdgeLabelPosition = it },
                    onCreateEdge = {
                        val fromId = nodeData.entries.find { it.value.label == fromNodeLabel }?.key
                        val toId = nodeData.entries.find { it.value.label == toNodeLabel }?.key
                        if (fromId != null && toId != null) {
                            val id = addEdge(fromId, toId)
                            if (newEdgeLabel.isNotBlank()) {
                                edgeData =
                                    edgeData + (id to EdgeData(newEdgeLabel, newEdgeLabelPosition))
                            }
                            fromNodeLabel = ""
                            toNodeLabel = ""
                            newEdgeLabel = ""
                            newEdgeLabelPosition = 0.5f
                        }
                    }
                )
            }

            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .onSizeChanged { toolbarHeight = with(density) { it.height.toDp() } + 16.dp },
                floatingActionButton = {
                    FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = viewer::centerGraph) {
                        Icon(Icons.Filled.ZoomInMap, "Center graph")
                    }
                }
            ) {
                ToolbarButton(Icons.Filled.AddCircle, "Add node") {
                    showNodeForm = !showNodeForm
                    if (showNodeForm) showEdgeForm = false
                }
                ToolbarButton(Icons.Filled.AddLink, "Add edge") {
                    showEdgeForm = !showEdgeForm
                    if (showEdgeForm) showNodeForm = false
                }
                ToolbarButton(
                    if (isAutoGenerating) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    if (isAutoGenerating) "Stop generating" else "Generate nodes"
                ) { isAutoGenerating = !isAutoGenerating }
                ToolbarButton(Icons.Filled.Delete, "Clear graph") {
                    isAutoGenerating = false
                    connection.reset()
                    commit(Kuiver())
                }
                if (!compact) {
                    ToolbarButton(Icons.Filled.ZoomOut, "Zoom out", viewer::zoomOut)
                    Text(
                        text = "${(viewer.scale * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    ToolbarButton(Icons.Filled.ZoomIn, "Zoom in", viewer::zoomIn)
                }
            }
        }
    }
}

/**
 * Back edges and self-loops always use the regular edge.
 */
@Composable
private fun PlaygroundEdge(
    edge: KuiverEdge,
    from: Offset,
    to: Offset,
    style: PlaygroundEdgeStyle,
    data: EdgeData?,
    strokeWidth: Dp
) {
    val label = data?.label?.takeIf { it.isNotBlank() }
    val labelStyle = KuiverDefaults.edgeLabelStyle().copy(fontSize = 11.sp)
    val loop = edge.type == EdgeType.BACK || edge.type == EdgeType.SELF_LOOP

    when (style) {
        PlaygroundEdgeStyle.S_CURVE if !loop -> OrthogonalEdgeContentWithLabel(
            from = from,
            to = to,
            strokeWidth = strokeWidth,
            label = label,
            labelOffset = data?.labelOffset,
            labelStyle = labelStyle.copy(rotateWithEdge = true)
        )

        PlaygroundEdgeStyle.RIGHT_ANGLE if !loop -> StyledRightAngleEdgeContent(
            edge = edge,
            from = from,
            to = to,
            strokeWidth = strokeWidth,
            label = label,
            labelOffset = data?.labelOffset,
            labelStyle = labelStyle
        )

        else -> StyledEdgeContent(
            edge = edge,
            from = from,
            to = to,
            strokeWidth = strokeWidth,
            label = label,
            labelOffset = data?.labelOffset,
            labelStyle = labelStyle
        )
    }
}
