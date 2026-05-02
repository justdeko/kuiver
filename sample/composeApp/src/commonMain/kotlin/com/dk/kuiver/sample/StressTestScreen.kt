package com.dk.kuiver.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomInMap
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk.kuiver.KuiverViewerState
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.model.buildKuiver
import com.dk.kuiver.model.layout.LayoutConfig
import com.dk.kuiver.model.layout.LayoutDirection
import com.dk.kuiver.rememberSaveableKuiverViewerState
import com.dk.kuiver.renderer.KuiverViewer
import com.dk.kuiver.renderer.KuiverViewerConfig
import com.dk.kuiver.ui.StyledEdgeContent
import kotlin.random.Random

enum class StressPreset(val nodeCount: Int, val label: String) {
    TINY(25, "25"),
    SMALL(50, "50"),
    MEDIUM(100, "100"),
    LARGE(200, "200"),
    XL(500, "500"),
}

private enum class StressNodeSize { SMALL, MEDIUM, LARGE }

private data class StressNodeData(
    val label: String,
    val colorType: NodeColorType,
    val size: StressNodeSize,
)

private fun generateStressTestGraph(
    nodeCount: Int,
    seed: Long,
): Pair<Map<String, StressNodeData>, com.dk.kuiver.model.Kuiver> {
    val rng = Random(seed)
    val nodeDataMap = mutableMapOf<String, StressNodeData>()
    val kuiverNodes = mutableListOf<com.dk.kuiver.model.KuiverNode>()

    for (i in 1..nodeCount) {
        val id = i.toString()
        val size = StressNodeSize.entries[rng.nextInt(StressNodeSize.entries.size)]
        val label = when (size) {
            StressNodeSize.SMALL -> "$i"
            StressNodeSize.MEDIUM -> "Node $i"
            StressNodeSize.LARGE -> "Node $i\nTask $i"
        }
        val colorType = NodeColorType.ALL[rng.nextInt(NodeColorType.ALL.size)]
        nodeDataMap[id] = StressNodeData(label, colorType, size)
        kuiverNodes.add(KuiverNode(id = id))
    }

    // Spanning tree for connectivity, then extra edges (~40% more)
    val shuffled = kuiverNodes.shuffled(rng)
    val edges = mutableSetOf<Pair<String, String>>()
    for (i in 1 until shuffled.size) {
        edges.add(shuffled[i - 1].id to shuffled[i].id)
    }
    val extraCount = (nodeCount * 0.4).toInt()
    var attempts = 0
    while (edges.size < nodeCount - 1 + extraCount && attempts < extraCount * 10) {
        val from = kuiverNodes[rng.nextInt(kuiverNodes.size)].id
        val to = kuiverNodes[rng.nextInt(kuiverNodes.size)].id
        if (from != to) edges.add(from to to)
        attempts++
    }

    val kuiver = buildKuiver {
        kuiverNodes.forEach { addNode(it) }
        edges.forEach { (from, to) -> addEdge(KuiverEdge(from, to)) }
    }
    return nodeDataMap to kuiver
}

@Composable
private fun StressNodeContent(data: StressNodeData) {
    val bgColor = NodeColors.getColor(data.colorType)
    val textColor = NodeColors.getTextColor(data.colorType)
    val cornerPercent = when (data.size) {
        StressNodeSize.SMALL -> 50
        StressNodeSize.MEDIUM -> 30
        StressNodeSize.LARGE -> 20
    }
    val shape = RoundedCornerShape(cornerPercent)
    val horizontalPadding = when (data.size) {
        StressNodeSize.SMALL -> 12.dp
        StressNodeSize.MEDIUM -> 14.dp
        StressNodeSize.LARGE -> 16.dp
    }
    val verticalPadding = when (data.size) {
        StressNodeSize.SMALL -> 8.dp
        StressNodeSize.MEDIUM -> 10.dp
        StressNodeSize.LARGE -> 12.dp
    }
    Box(
        modifier = Modifier
            .shadow(2.dp, shape)
            .clip(shape)
            .background(bgColor)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = data.label,
            color = textColor,
            fontSize = when (data.size) {
                StressNodeSize.SMALL -> 11.sp
                StressNodeSize.MEDIUM -> 12.sp
                StressNodeSize.LARGE -> 12.sp
            },
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun StressTestControlsCard(
    selectedPreset: StressPreset,
    onPresetSelected: (StressPreset) -> Unit,
    selectedLayoutAlgorithm: LayoutAlgorithm,
    onLayoutAlgorithmSelected: (LayoutAlgorithm) -> Unit,
    nodeCount: Int,
    edgeCount: Int,
    showDebugBounds: Boolean,
    onToggleDebugBounds: () -> Unit,
    onRegenerate: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            StressTestTitleRow(
                nodeCount = nodeCount,
                edgeCount = edgeCount,
                showDebugBounds = showDebugBounds,
                onToggleDebugBounds = onToggleDebugBounds,
                onRegenerate = onRegenerate,
                onNavigateBack = onNavigateBack,
            )
            StressTestPresetRow(
                selectedPreset = selectedPreset,
                onPresetSelected = onPresetSelected,
            )
            StressTestLayoutRow(
                selectedLayoutAlgorithm = selectedLayoutAlgorithm,
                onLayoutAlgorithmSelected = onLayoutAlgorithmSelected,
            )
        }
    }
}

@Composable
private fun StressTestTitleRow(
    nodeCount: Int,
    edgeCount: Int,
    showDebugBounds: Boolean,
    onToggleDebugBounds: () -> Unit,
    onRegenerate: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Stress Test",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "$nodeCount nodes · $edgeCount edges",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRegenerate) {
            Icon(Icons.Filled.Refresh, contentDescription = "Regenerate graph")
        }
        IconButton(onClick = onToggleDebugBounds) {
            Icon(Icons.Filled.Settings, contentDescription = "Toggle debug bounds")
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun StressTestPresetRow(
    selectedPreset: StressPreset,
    onPresetSelected: (StressPreset) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Nodes:",
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier.padding(end = 8.dp),
        )
        Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                ButtonGroupDefaults.ConnectedSpaceBetween
            ),
        ) {
            StressPreset.entries.forEachIndexed { index, preset ->
                ToggleButton(
                    checked = selectedPreset == preset,
                    onCheckedChange = { onPresetSelected(preset) },
                    modifier = Modifier.semantics { role = Role.RadioButton },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        StressPreset.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    Text(preset.label, fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun StressTestLayoutRow(
    selectedLayoutAlgorithm: LayoutAlgorithm,
    onLayoutAlgorithmSelected: (LayoutAlgorithm) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Layout:",
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier.padding(end = 8.dp),
        )
        Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                ButtonGroupDefaults.ConnectedSpaceBetween
            ),
        ) {
            LayoutAlgorithm.entries.forEachIndexed { index, algo ->
                ToggleButton(
                    checked = selectedLayoutAlgorithm == algo,
                    onCheckedChange = { onLayoutAlgorithmSelected(algo) },
                    modifier = Modifier.semantics { role = Role.RadioButton },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        LayoutAlgorithm.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    Text(
                        text = when (algo) {
                            LayoutAlgorithm.HIERARCHICAL -> "Hierarchical"
                            LayoutAlgorithm.FORCE_DIRECTED -> "Force"
                        },
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StressTestLoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                text = "Calculating layout…",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StressTestZoomToolbar(
    state: KuiverViewerState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = expanded,
        modifier = modifier,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onToggleExpanded) {
                Icon(Icons.Filled.ZoomInMap, contentDescription = "Zoom controls")
            }
        },
        content = {
            FilledIconButton(
                modifier = Modifier.width(64.dp),
                onClick = state::centerGraph,
            ) {
                Icon(Icons.Filled.ZoomInMap, contentDescription = "Center graph")
            }
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = state::zoomOut) {
                    Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out")
                }
                Text(
                    text = "${(state.scale * 100).toInt()}%",
                    modifier = Modifier.padding(4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                IconButton(onClick = state::zoomIn) {
                    Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in")
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StressTestScreen(onNavigateBack: () -> Unit) {
    var selectedPreset by rememberSaveable { mutableStateOf(StressPreset.MEDIUM) }
    var selectedLayoutAlgorithm by rememberSaveable { mutableStateOf(LayoutAlgorithm.FORCE_DIRECTED) }
    var seed by rememberSaveable { mutableStateOf(0L) }
    var showDebugBounds by rememberSaveable { mutableStateOf(false) }
    var toolbarExpanded by rememberSaveable { mutableStateOf(false) }
    var overlayContentHeight by rememberSaveable { mutableStateOf(0) }

    val (nodeDataMap, stressKuiver) = remember(selectedPreset, seed) {
        generateStressTestGraph(selectedPreset.nodeCount, seed)
    }

    val layoutConfig = remember(selectedLayoutAlgorithm) {
        when (selectedLayoutAlgorithm) {
            LayoutAlgorithm.HIERARCHICAL -> LayoutConfig.Hierarchical(direction = LayoutDirection.HORIZONTAL)
            LayoutAlgorithm.FORCE_DIRECTED -> LayoutConfig.ForceDirected()
        }
    }

    // key() resets state (zoom, pan, fit) when preset or seed changes
    val kuiverViewerState = key(selectedPreset, seed) {
        rememberSaveableKuiverViewerState(
            initialKuiver = stressKuiver,
            layoutConfig = layoutConfig,
        )
    }

    LaunchedEffect(overlayContentHeight) {
        kuiverViewerState.updateContentOffset(Offset(0f, overlayContentHeight.toFloat()))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        KuiverViewer(
            state = kuiverViewerState,
            config = KuiverViewerConfig(
                showDebugBounds = showDebugBounds,
                animateInitialPlacement = false,
            ),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            nodeContent = { node ->
                val data = nodeDataMap[node.id] ?: return@KuiverViewer
                StressNodeContent(data)
            },
            edgeContent = { edge, from, to ->
                StyledEdgeContent(
                    edge = edge,
                    from = from,
                    to = to,
                    baseColor = MaterialTheme.colorScheme.outline,
                    backEdgeColor = MaterialTheme.colorScheme.error,
                )
            },
        )

        Column(modifier = Modifier.onSizeChanged { overlayContentHeight = it.height }) {
            StressTestControlsCard(
                selectedPreset = selectedPreset,
                onPresetSelected = { selectedPreset = it },
                selectedLayoutAlgorithm = selectedLayoutAlgorithm,
                onLayoutAlgorithmSelected = { selectedLayoutAlgorithm = it },
                nodeCount = kuiverViewerState.kuiver.nodes.size,
                edgeCount = kuiverViewerState.kuiver.edges.size,
                showDebugBounds = showDebugBounds,
                onToggleDebugBounds = { showDebugBounds = !showDebugBounds },
                onRegenerate = { seed++ },
                onNavigateBack = onNavigateBack,
            )
        }

        AnimatedVisibility(
            visible = !kuiverViewerState.hasFittedInitially,
            modifier = Modifier.fillMaxSize(),
            enter = EnterTransition.None,
            exit = fadeOut(),
        ) {
            StressTestLoadingOverlay()
        }

        StressTestZoomToolbar(
            state = kuiverViewerState,
            expanded = toolbarExpanded,
            onToggleExpanded = { toolbarExpanded = !toolbarExpanded },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }
}
