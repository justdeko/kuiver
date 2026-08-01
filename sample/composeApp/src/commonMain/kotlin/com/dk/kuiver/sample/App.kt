package com.dk.kuiver.sample

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomInMap
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.model.buildKuiver
import com.dk.kuiver.model.edges
import com.dk.kuiver.model.layout.LayoutConfig
import com.dk.kuiver.model.layout.LayoutDirection
import com.dk.kuiver.model.nodes
import com.dk.kuiver.rememberSaveableKuiverViewerState
import com.dk.kuiver.renderer.KuiverViewer
import com.dk.kuiver.renderer.KuiverViewerConfig
import com.dk.kuiver.sample.components.ConnectionModeBanner
import com.dk.kuiver.sample.components.EdgeCreationForm
import com.dk.kuiver.sample.components.GraphBuilderHeader
import com.dk.kuiver.sample.components.GraphControlMenu
import com.dk.kuiver.sample.components.NodeCreationForm
import com.dk.kuiver.sample.theme.AppTheme
import com.dk.kuiver.ui.DefaultNodeContent
import com.dk.kuiver.ui.StyledEdgeContent
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

enum class Screen {
    GRAPH_BUILDER,
    PROCESS_DIAGRAM,
    STRESS_TEST,
}

// Layout algorithm selection for the sample app UI
enum class LayoutAlgorithm {
    HIERARCHICAL,
    FORCE_DIRECTED
}

@Composable
@Preview
fun App() {
    val systemInDarkTheme = isSystemInDarkTheme()
    var isDarkTheme by rememberSaveable { mutableStateOf(systemInDarkTheme) }
    var currentScreen by rememberSaveable { mutableStateOf(Screen.GRAPH_BUILDER) }

    AppTheme(darkTheme = isDarkTheme) {
        when (currentScreen) {
            Screen.GRAPH_BUILDER -> GraphBuilderScreen(
                isDarkTheme = isDarkTheme,
                onThemeToggle = { isDarkTheme = !isDarkTheme },
                onNavigateToProcessDemo = { currentScreen = Screen.PROCESS_DIAGRAM },
                onNavigateToStressTest = { currentScreen = Screen.STRESS_TEST },
            )

            Screen.PROCESS_DIAGRAM -> ProcessDiagramDemo(
                onNavigateBack = { currentScreen = Screen.GRAPH_BUILDER }
            )

            Screen.STRESS_TEST -> StressTestScreen(
                onNavigateBack = { currentScreen = Screen.GRAPH_BUILDER }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GraphBuilderScreen(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onNavigateToProcessDemo: () -> Unit,
    onNavigateToStressTest: () -> Unit,
) {
    var selectedLayoutAlgorithm by rememberSaveable { mutableStateOf(LayoutAlgorithm.HIERARCHICAL) }
    var selectedLayoutDirection by rememberSaveable { mutableStateOf(LayoutDirection.HORIZONTAL) }

    // Node data is now managed separately from the graph structure
    var nodeData by rememberSaveable(stateSaver = NodeDataMapSaver) {
        mutableStateOf(
            mapOf(
                "1" to NodeData("Start", NodeColorType.PINK),
                "2" to NodeData("Process", NodeColorType.YELLOW),
                "3" to NodeData("End", NodeColorType.BLUE)
            )
        )
    }

    var edgeData by rememberSaveable(stateSaver = EdgeDataMapSaver) { mutableStateOf(mapOf()) }

    val initialKuiver = remember {
        buildKuiver {
            nodes("1", "2", "3")
            edges(
                "1" to "2",
                "2" to "3"
            )
        }
    }

    val layoutConfig = remember(selectedLayoutAlgorithm, selectedLayoutDirection) {
        when (selectedLayoutAlgorithm) {
            LayoutAlgorithm.HIERARCHICAL -> LayoutConfig.Hierarchical(
                direction = selectedLayoutDirection
            )

            LayoutAlgorithm.FORCE_DIRECTED -> LayoutConfig.ForceDirected()
        }
    }

    val kuiverViewerState = rememberSaveableKuiverViewerState(
        initialKuiver = initialKuiver,
        layoutConfig = layoutConfig
    )
    var nextNodeId by rememberSaveable { mutableStateOf(4) } // Start from 4 since we have 3 initial nodes
    var newNodeData by rememberSaveable { mutableStateOf("") }
    var fromNodeLabel by rememberSaveable { mutableStateOf("") }
    var toNodeLabel by rememberSaveable { mutableStateOf("") }
    var newEdgeLabel by rememberSaveable { mutableStateOf("") }
    var newEdgeLabelPosition by rememberSaveable { mutableStateOf(0.5f) }
    var selectedNodeColorType by rememberSaveable { mutableStateOf<NodeColorType?>(null) } // null = random
    var showNodeForm by rememberSaveable { mutableStateOf(false) }
    var showEdgeForm by rememberSaveable { mutableStateOf(false) }
    var showDebugBounds by rememberSaveable { mutableStateOf(false) }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var isAutoGenerating by rememberSaveable { mutableStateOf(false) }
    var shouldGenerateNode by rememberSaveable { mutableStateOf(true) } // Alternates between node and edge

    val connectionState = rememberConnectionState()

    var toolbarExpanded by rememberSaveable { mutableStateOf(false) }

    // Track overlay content height for centering offset
    var overlayContentHeight by rememberSaveable { mutableStateOf(0) }

    val density = LocalDensity.current
    LaunchedEffect(overlayContentHeight, density) {
        val reserved = with(density) { overlayContentHeight.toDp() }
        kuiverViewerState.updateContentOffset(DpOffset(0.dp, reserved))
    }

    // Auto-generation logic
    LaunchedEffect(isAutoGenerating, kuiverViewerState) {
        while (isAutoGenerating) {
            delay(1000.milliseconds)
            val currentKuiver = kuiverViewerState.kuiver
            val created = if (shouldGenerateNode) {
                val label = "Node $nextNodeId"
                val actualColorType = NodeColorType.ALL.random()
                val generatedId = nextNodeId.toString()

                // Add node data to the map
                nodeData = nodeData + (generatedId to NodeData(label, actualColorType))

                // Add node to the graph (structure only)
                val newNode = KuiverNode(id = generatedId)
                kuiverViewerState.updateKuiver(currentKuiver.withNode(newNode))
                nextNodeId++
                true
            } else {
                // Generate a random edge
                val nodesList = currentKuiver.nodes.values.toList()
                if (nodesList.size >= 2) {
                    // Try up to 10 times to find a valid edge
                    var attempts = 0
                    var edgeAdded = false

                    while (!edgeAdded && attempts < 10) {
                        val fromNode = nodesList.random()
                        val toNode = nodesList.filter { it.id != fromNode.id }.random()

                        // Check if edge already exists
                        val edgeExists = currentKuiver.edges.any {
                            it.fromId == fromNode.id && it.toId == toNode.id
                        }

                        if (!edgeExists) {
                            val newEdge = KuiverEdge(fromNode.id, toNode.id)
                            kuiverViewerState.updateKuiver(currentKuiver.withEdge(newEdge))
                            edgeAdded = true
                        }
                        attempts++
                    }

                    edgeAdded // Return true only if we successfully added an edge
                } else {
                    false // Not enough nodes
                }
            }

            // Only alternate if we successfully created something
            if (created) {
                shouldGenerateNode = !shouldGenerateNode
            }

        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {

        Box(modifier = Modifier.fillMaxSize()) {
            KuiverViewer(
                state = kuiverViewerState,
                config = KuiverViewerConfig(
                    showDebugBounds = showDebugBounds,
                    animateInitialPlacement = true
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                nodeContent = { node ->
                    val data = nodeData[node.id]
                    if (data != null) {
                        val backgroundColor = NodeColors.getColor(data.colorType)
                        val textColor = NodeColors.getTextColor(data.colorType)

                        DefaultNodeContent(
                            label = data.label,
                            backgroundColor = backgroundColor,
                            textColor = textColor,
                            onClick = {
                                if (connectionState.isActive) {
                                    // Second click - complete the connection
                                    val fromNode = connectionState.sourceNode
                                    if (fromNode != null) {
                                        val newEdge = KuiverEdge(fromNode.id, node.id)
                                        kuiverViewerState.updateKuiver(
                                            kuiverViewerState.kuiver.withEdge(newEdge)
                                        )
                                    }
                                    // Reset connection state
                                    connectionState.reset()
                                } else {
                                    // First click - start connection mode
                                    connectionState.start(node)
                                }
                            }
                        )
                    }
                },
                edgeContent = { edge, from, to ->
                    val data = edgeData[edge.fromId edgeTo edge.toId]
                    StyledEdgeContent(
                        edge = edge,
                        from = from,
                        to = to,
                        label = data?.label,
                        labelOffset = data?.labelOffset ?: 0.5f
                    )
                }
            )

            BoxWithConstraints {
                val isWideScreen = maxWidth >= 840.dp

                Column(
                    modifier = Modifier.onSizeChanged { size ->
                        overlayContentHeight = size.height
                    }
                ) {
                    AnimatedContent(targetState = isWideScreen) { wide ->
                        if (wide) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Max)
                                    .padding(top = 8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    GraphBuilderHeader(
                                        modifier = Modifier.fillMaxHeight(),
                                        nodeCount = kuiverViewerState.kuiver.nodes.size,
                                        edgeCount = kuiverViewerState.kuiver.edges.size,
                                        isDarkTheme = isDarkTheme,
                                        onThemeToggle = onThemeToggle
                                    )
                                }

                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    GraphControlMenu(
                                        showNodeForm = showNodeForm,
                                        showEdgeForm = showEdgeForm,
                                        selectedLayoutAlgorithm = selectedLayoutAlgorithm,
                                        selectedLayoutDirection = selectedLayoutDirection,
                                        isAutoGenerating = isAutoGenerating,
                                        onToggleNodeForm = {
                                            showNodeForm = !showNodeForm
                                            if (showNodeForm) showEdgeForm = false
                                        },
                                        onToggleEdgeForm = {
                                            showEdgeForm = !showEdgeForm
                                            if (showEdgeForm) showNodeForm = false
                                        },
                                        onToggleDebugBounds = {
                                            showDebugBounds = !showDebugBounds
                                        },
                                        onToggleAutoGenerate = {
                                            isAutoGenerating = !isAutoGenerating
                                            if (isAutoGenerating) {
                                                shouldGenerateNode =
                                                    true // Always start with a node
                                            }
                                        },
                                        onLayoutAlgorithmChange = { selectedLayoutAlgorithm = it },
                                        onLayoutDirectionChange = { selectedLayoutDirection = it },
                                        onClearAll = {
                                            kuiverViewerState.updateKuiver(Kuiver())
                                            showNodeForm = false
                                            showEdgeForm = false
                                        },
                                        onNavigateToDemo = onNavigateToProcessDemo,
                                        onNavigateToStressTest = onNavigateToStressTest,
                                    )
                                }
                            }
                        } else {
                            // Narrow screen: Vertical layout with collapsible menu
                            Column {
                                GraphBuilderHeader(
                                    nodeCount = kuiverViewerState.kuiver.nodes.size,
                                    edgeCount = kuiverViewerState.kuiver.edges.size,
                                    isDarkTheme = isDarkTheme,
                                    onThemeToggle = onThemeToggle
                                )

                                AnimatedVisibility(showMenu) {
                                    GraphControlMenu(
                                        showNodeForm = showNodeForm,
                                        showEdgeForm = showEdgeForm,
                                        selectedLayoutAlgorithm = selectedLayoutAlgorithm,
                                        selectedLayoutDirection = selectedLayoutDirection,
                                        isAutoGenerating = isAutoGenerating,
                                        onToggleNodeForm = {
                                            showNodeForm = !showNodeForm
                                            if (showNodeForm) showEdgeForm = false
                                        },
                                        onToggleEdgeForm = {
                                            showEdgeForm = !showEdgeForm
                                            if (showEdgeForm) showNodeForm = false
                                        },
                                        onToggleDebugBounds = {
                                            showDebugBounds = !showDebugBounds
                                        },
                                        onToggleAutoGenerate = {
                                            isAutoGenerating = !isAutoGenerating
                                            if (isAutoGenerating) {
                                                shouldGenerateNode =
                                                    true // Always start with a node
                                            }
                                        },
                                        onLayoutAlgorithmChange = { selectedLayoutAlgorithm = it },
                                        onLayoutDirectionChange = { selectedLayoutDirection = it },
                                        onClearAll = {
                                            kuiverViewerState.updateKuiver(Kuiver())
                                            showNodeForm = false
                                            showEdgeForm = false
                                        },
                                        onNavigateToDemo = onNavigateToProcessDemo,
                                        onNavigateToStressTest = onNavigateToStressTest,
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(connectionState.isActive) {
                        ConnectionModeBanner(
                            sourceNodeLabel = connectionState.sourceNode?.let { nodeData[it.id]?.label }
                                ?: "",
                            onCancel = { connectionState.reset() },
                        )
                    }

                    // Node Creation Form
                    NodeCreationForm(
                        visible = showNodeForm,
                        newNodeData = newNodeData,
                        selectedNodeColorType = selectedNodeColorType,
                        onNodeDataChange = { newNodeData = it },
                        onColorTypeSelect = { selectedNodeColorType = it },
                        onCreateNode = {
                            val label = newNodeData.ifBlank { "Node $nextNodeId" }

                            // Check if label is unique
                            val existingLabels = nodeData.values.map { it.label }
                            if (label in existingLabels) {
                                // Could show error to user here
                                return@NodeCreationForm
                            }

                            // Determine actual color: if random (null), pick a random one
                            val actualColorType =
                                selectedNodeColorType ?: NodeColorType.ALL.random()

                            val generatedId = nextNodeId.toString()

                            // Add node data to the map
                            nodeData = nodeData + (generatedId to NodeData(label, actualColorType))

                            // Add node to the graph (structure only)
                            val newNode = KuiverNode(id = generatedId)
                            kuiverViewerState.updateKuiver(
                                kuiverViewerState.kuiver.withNode(newNode)
                            )
                            nextNodeId++
                            newNodeData = ""
                        }
                    )

                    // Edge Creation Form
                    EdgeCreationForm(
                        visible = showEdgeForm,
                        hasNodes = kuiverViewerState.kuiver.nodes.isNotEmpty(),
                        fromNodeLabel = fromNodeLabel,
                        toNodeLabel = toNodeLabel,
                        onFromNodeLabelChange = { fromNodeLabel = it },
                        onToNodeLabelChange = { toNodeLabel = it },
                        edgeLabel = newEdgeLabel,
                        onEdgeLabelChange = { newEdgeLabel = it },
                        labelPosition = newEdgeLabelPosition,
                        onLabelPositionChange = { newEdgeLabelPosition = it },
                        onCreateEdge = {
                            if (fromNodeLabel.isNotBlank() && toNodeLabel.isNotBlank()) {
                                // Find IDs for the given labels by looking up in nodeData
                                val fromNodeEntry =
                                    nodeData.entries.find { it.value.label == fromNodeLabel }
                                val toNodeEntry =
                                    nodeData.entries.find { it.value.label == toNodeLabel }

                                if (fromNodeEntry != null && toNodeEntry != null) {
                                    val newEdge = KuiverEdge(fromNodeEntry.key, toNodeEntry.key)
                                    kuiverViewerState.updateKuiver(
                                        kuiverViewerState.kuiver.withEdge(newEdge)
                                    )

                                    if (newEdgeLabel.isNotBlank()) {
                                        edgeData = edgeData + (
                                                (fromNodeEntry.key edgeTo toNodeEntry.key) to
                                                        EdgeData(newEdgeLabel, newEdgeLabelPosition)
                                                )
                                    }

                                    fromNodeLabel = ""
                                    toNodeLabel = ""
                                    newEdgeLabel = ""
                                    newEdgeLabelPosition = 0.5f
                                }
                            }
                        }
                    )
                }
            }

            HorizontalFloatingToolbar(
                expanded = toolbarExpanded,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                floatingActionButton = {
                    FloatingToolbarDefaults.VibrantFloatingActionButton(
                        onClick = {
                            showMenu = !showMenu
                            if (!showMenu) {
                                showNodeForm = false
                                showEdgeForm = false
                            }
                            toolbarExpanded = !toolbarExpanded
                        },
                    ) {
                        Icon(Icons.Filled.Add, "add element to graph")
                    }
                },
                content = {
                    FilledIconButton(
                        modifier = Modifier.width(64.dp),
                        onClick = kuiverViewerState::centerGraph,
                    ) {
                        Icon(Icons.Filled.ZoomInMap, "center graph")
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = kuiverViewerState::zoomOut,
                        ) {
                            Icon(Icons.Filled.ZoomOut, "zoom out")
                        }
                        Text(
                            text = "${(kuiverViewerState.scale * 100).toInt()}%",
                            modifier = Modifier.padding(4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        IconButton(
                            onClick = kuiverViewerState::zoomIn,
                        ) {
                            Icon(Icons.Filled.ZoomIn, "zoom in")
                        }
                    }
                }
            )
        }
    }
}