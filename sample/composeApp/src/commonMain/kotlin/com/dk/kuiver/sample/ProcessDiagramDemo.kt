package com.dk.kuiver.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomInMap
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.model.buildKuiver
import com.dk.kuiver.model.layout.LayoutConfig
import com.dk.kuiver.rememberSaveableKuiverViewerState
import com.dk.kuiver.renderer.KuiverViewer
import com.dk.kuiver.renderer.KuiverViewerConfig
import com.dk.kuiver.ui.KuiverAnchor
import com.dk.kuiver.ui.OrthogonalEdgeContent
import com.dk.kuiver.ui.StyledEdgeContent
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProcessDiagramDemo(
    onNavigateBack: () -> Unit
) {
    var selectedLayoutAlgorithm by rememberSaveable { mutableStateOf(LayoutAlgorithm.HIERARCHICAL) }
    var showAnchors by rememberSaveable { mutableStateOf(false) }

    val processNodeData = remember {
        mapOf(
            "start" to ProcessNode(
                "Wake Up",
                "Time for breakfast!",
                ProcessNodeType.START,
                ProcessIcon.SUN
            ),
            "preheat" to ProcessNode(
                "Preheat Pan",
                "Heat skillet to medium",
                ProcessNodeType.PROCESS,
                ProcessIcon.FIRE
            ),

            // COFFEE TRACK
            "grind_beans" to ProcessNode(
                "Grind Beans",
                "Fresh coffee beans",
                ProcessNodeType.PROCESS,
                ProcessIcon.COFFEE
            ),
            "boil_water" to ProcessNode(
                "Boil Water",
                "Heat to 200°F",
                ProcessNodeType.PROCESS,
                ProcessIcon.WATER
            ),
            "brew_coffee" to ProcessNode(
                "Brew Coffee",
                "Pour over method",
                ProcessNodeType.SUBPROCESS,
                ProcessIcon.COFFEE
            ),
            "coffee_ready" to ProcessNode(
                "Coffee Ready",
                "Perfect temperature",
                ProcessNodeType.PROCESS,
                ProcessIcon.CHECKMARK
            ),

            // BACON & EGGS TRACK
            "cook_bacon" to ProcessNode(
                "Cook Bacon",
                "Crispy strips",
                ProcessNodeType.SUBPROCESS,
                ProcessIcon.BACON
            ),
            "bacon_done" to ProcessNode(
                "Bacon Done",
                "Drain on paper towel",
                ProcessNodeType.PROCESS,
                ProcessIcon.CHECKMARK
            ),
            "crack_eggs" to ProcessNode(
                "Crack Eggs",
                "Into the hot pan",
                ProcessNodeType.PROCESS,
                ProcessIcon.EGG
            ),
            "cook_eggs" to ProcessNode(
                "Cook Eggs",
                "Sunny side up",
                ProcessNodeType.PROCESS,
                ProcessIcon.PAN
            ),
            "eggs_check" to ProcessNode(
                "Eggs Done?",
                "Check the yolks",
                ProcessNodeType.DECISION,
                ProcessIcon.EYES
            ),
            "eggs_done" to ProcessNode(
                "Eggs Perfect",
                "Just right!",
                ProcessNodeType.PROCESS,
                ProcessIcon.CHECKMARK
            ),

            // TOAST TRACK
            "slice_bread" to ProcessNode(
                "Slice Bread",
                "Two thick slices",
                ProcessNodeType.PROCESS,
                ProcessIcon.BREAD
            ),
            "toast" to ProcessNode(
                "Toast Bread",
                "Golden brown",
                ProcessNodeType.PROCESS,
                ProcessIcon.FIRE
            ),
            "butter" to ProcessNode(
                "Butter Toast",
                "Spread while hot",
                ProcessNodeType.PROCESS,
                ProcessIcon.BUTTER
            ),

            // JUICE TRACK
            "squeeze_oranges" to ProcessNode(
                "Squeeze Oranges",
                "Fresh OJ",
                ProcessNodeType.SUBPROCESS,
                ProcessIcon.ORANGE
            ),
            "strain_juice" to ProcessNode(
                "Strain Juice",
                "Remove pulp",
                ProcessNodeType.PROCESS,
                ProcessIcon.JUICE
            ),
            "pour_juice" to ProcessNode(
                "Pour Juice",
                "Into glass",
                ProcessNodeType.PROCESS,
                ProcessIcon.JUICE
            ),

            // CONVERGENCE & PLATING
            "plate_food" to ProcessNode(
                "Plate Food",
                "Arrange nicely",
                ProcessNodeType.SUBPROCESS,
                ProcessIcon.PLATE
            ),
            "add_garnish" to ProcessNode(
                "Add Garnish",
                "Fresh herbs",
                ProcessNodeType.PROCESS,
                ProcessIcon.HERB
            ),
            "serve" to ProcessNode(
                "Serve",
                "Breakfast is ready!",
                ProcessNodeType.PROCESS,
                ProcessIcon.CHEF
            ),
            "enjoy" to ProcessNode(
                "Enjoy!",
                "Bon appétit!",
                ProcessNodeType.END,
                ProcessIcon.CELEBRATE
            )
        )
    }

    val processKuiver = remember(showAnchors) {
        val edgeList = listOf(
            // Setup
            "start" to "preheat",
            "start" to "grind_beans",
            "start" to "slice_bread",
            "start" to "squeeze_oranges",
            // Coffee track
            "grind_beans" to "boil_water",
            "boil_water" to "brew_coffee",
            "brew_coffee" to "coffee_ready",
            "coffee_ready" to "serve",
            // Bacon & Eggs track
            "preheat" to "cook_bacon",
            "cook_bacon" to "bacon_done",
            "bacon_done" to "crack_eggs",
            "crack_eggs" to "cook_eggs",
            "cook_eggs" to "eggs_check",
            "eggs_check" to "eggs_done",
            "eggs_check" to "cook_eggs", // Back edge
            "eggs_done" to "plate_food",
            // Toast track
            "slice_bread" to "toast",
            "toast" to "butter",
            "butter" to "plate_food",
            // Juice track
            "squeeze_oranges" to "strain_juice",
            "strain_juice" to "pour_juice",
            "pour_juice" to "serve",
            // Final plating
            "plate_food" to "add_garnish",
            "add_garnish" to "serve",
            "serve" to "enjoy"
        )

        buildKuiver {
            processNodeData.keys.forEach { id ->
                addNode(KuiverNode(id))
            }

            if (showAnchors) {
                val outgoingEdges = edgeList.groupBy { it.first }
                val incomingEdges = edgeList.groupBy { it.second }

                edgeList.forEach { (from, to) ->
                    // Check if this is a back edge (creates a cycle)
                    val isBackEdge = wouldCreateCycle(from, to)

                    if (isBackEdge) {
                        // Back edges use top anchors
                        val fromBackEdges = outgoingEdges[from]?.filter { (f, t) ->
                            wouldCreateCycle(f, t)
                        } ?: emptyList()
                        val toBackEdges = incomingEdges[to]?.filter { (f, t) ->
                            wouldCreateCycle(f, t)
                        } ?: emptyList()

                        val fromAnchorIndex = fromBackEdges.indexOf(from to to)
                        val toAnchorIndex = toBackEdges.indexOf(from to to)

                        addEdge(
                            KuiverEdge(
                                from,
                                to,
                                fromAnchor = "back-out-$fromAnchorIndex",
                                toAnchor = "back-in-$toAnchorIndex"
                            )
                        )
                    } else {
                        // Regular edges use side anchors
                        val fromEdges = outgoingEdges[from]?.filter { (f, t) ->
                            !wouldCreateCycle(f, t)
                        } ?: emptyList()
                        val toEdges = incomingEdges[to]?.filter { (f, t) ->
                            !wouldCreateCycle(f, t)
                        } ?: emptyList()

                        val fromAnchorIndex = fromEdges.indexOf(from to to)
                        val toAnchorIndex = toEdges.indexOf(from to to)

                        addEdge(
                            KuiverEdge(
                                from,
                                to,
                                fromAnchor = "out-$fromAnchorIndex",
                                toAnchor = "in-$toAnchorIndex"
                            )
                        )
                    }
                }
            } else {
                // Add edges without anchors
                edgeList.forEach { (from, to) ->
                    addEdge(KuiverEdge(from, to))
                }
            }
        }
    }

    val layoutConfig = remember(selectedLayoutAlgorithm) {
        when (selectedLayoutAlgorithm) {
            LayoutAlgorithm.HIERARCHICAL -> LayoutConfig.Hierarchical()
            LayoutAlgorithm.FORCE_DIRECTED -> LayoutConfig.ForceDirected()
        }
    }

    val kuiverViewerState = rememberSaveableKuiverViewerState(
        initialKuiver = processKuiver,
        layoutConfig = layoutConfig
    )

    // Update the graph when anchors toggle changes
    LaunchedEffect(processKuiver) {
        kuiverViewerState.updateKuiver(processKuiver)
    }

    // Auto-center on initial load and algorithm change
    var initialCenter by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(
        kuiverViewerState.canvasWidth > 0f && kuiverViewerState.canvasHeight > 0f,
        selectedLayoutAlgorithm
    ) {
        if (kuiverViewerState.canvasWidth > 0f && kuiverViewerState.canvasHeight > 0f) {
            if (!initialCenter) {
                delay(300) // Wait for layout to settle on first load
                initialCenter = true
            }

            kuiverViewerState.centerGraph()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagram Demo") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    FilterChip(
                        selected = selectedLayoutAlgorithm == LayoutAlgorithm.HIERARCHICAL,
                        onClick = { selectedLayoutAlgorithm = LayoutAlgorithm.HIERARCHICAL },
                        label = { Text("Hierarchical") }
                    )
                    Spacer(Modifier.width(4.dp))
                    FilterChip(
                        selected = selectedLayoutAlgorithm == LayoutAlgorithm.FORCE_DIRECTED,
                        onClick = { selectedLayoutAlgorithm = LayoutAlgorithm.FORCE_DIRECTED },
                        label = { Text("Force") }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = showAnchors,
                        onClick = { showAnchors = !showAnchors },
                        label = { Text("Anchors") }
                    )
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            KuiverViewer(
                state = kuiverViewerState,
                config = KuiverViewerConfig(),
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                nodeContent = { node ->
                    processNodeData[node.id]?.let {
                        ProcessNodeContent(
                            data = it,
                            nodeId = node.id,
                            showAnchors = showAnchors,
                            kuiver = kuiverViewerState.layoutedKuiver
                        )
                    }
                },
                edgeContent = { edge, from, to ->
                    // Use regular styled edges for back edges, orthogonal for others when anchors enabled
                    val isBackEdge = edge.fromAnchor?.startsWith("back-") ?: false

                    if (showAnchors && !isBackEdge) {
                        OrthogonalEdgeContent(
                            from = from,
                            to = to,
                            color = MaterialTheme.colorScheme.outline,
                            strokeWidth = 2.5f,
                        )
                    } else {
                        StyledEdgeContent(
                            edge = edge,
                            from = from,
                            to = to,
                            baseColor = MaterialTheme.colorScheme.outline,
                            backEdgeColor = MaterialTheme.colorScheme.error,
                            strokeWidth = 2.5f
                        )
                    }
                }
            )

            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                floatingActionButton = {
                    FloatingToolbarDefaults.VibrantFloatingActionButton(
                        onClick = kuiverViewerState.centerGraph
                    ) {
                        Icon(Icons.Filled.ZoomInMap, "Center graph")
                    }
                },
                content = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = kuiverViewerState.zoomOut) {
                            Icon(Icons.Filled.ZoomOut, "Zoom out")
                        }
                        Text(
                            text = "${(kuiverViewerState.scale * 100).toInt()}%",
                            modifier = Modifier.padding(4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(onClick = kuiverViewerState.zoomIn) {
                            Icon(Icons.Filled.ZoomIn, "Zoom in")
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ProcessNodeContent(
    data: ProcessNode,
    nodeId: String,
    showAnchors: Boolean,
    kuiver: Kuiver
) {
    val (backgroundColor, shape) = when (data.type) {
        ProcessNodeType.START -> Color(0xFF4CAF50) to CircleShape
        ProcessNodeType.END -> Color(0xFFF44336) to CircleShape
        ProcessNodeType.DECISION -> Color(0xFFFF9800) to RoundedCornerShape(8.dp)
        ProcessNodeType.PROCESS -> Color(0xFF2196F3) to RoundedCornerShape(8.dp)
        ProcessNodeType.SUBPROCESS -> Color(0xFF9C27B0) to RoundedCornerShape(8.dp)
        ProcessNodeType.DATA -> Color(0xFF00BCD4) to RoundedCornerShape(8.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor, shape)
            .border(3.dp, Color.White.copy(alpha = 0.3f), shape),
        contentAlignment = Alignment.Center
    ) {
        // Anchor points when enabled - positioned at the borders
        if (showAnchors) {
            // Count incoming and outgoing edges
            val incomingEdges = kuiver.edges.filter { it.toId == nodeId }
            val outgoingEdges = kuiver.edges.filter { it.fromId == nodeId }

            // Separate regular edges from back edges
            val regularIncoming =
                incomingEdges.filter { !(it.toAnchor?.startsWith("back-") ?: false) }
            val regularOutgoing =
                outgoingEdges.filter { !(it.fromAnchor?.startsWith("back-") ?: false) }
            val backIncoming = incomingEdges.filter { it.toAnchor?.startsWith("back-") ?: false }
            val backOutgoing = outgoingEdges.filter { it.fromAnchor?.startsWith("back-") ?: false }

            // Create regular incoming anchors (left side)
            repeat(regularIncoming.size) { index ->
                val fraction = (index + 1).toFloat() / (regularIncoming.size + 1)
                val verticalBias = (fraction * 2) - 1

                KuiverAnchor(
                    anchorId = "in-$index",
                    nodeId = nodeId,
                    modifier = Modifier
                        .align(BiasAlignment(horizontalBias = -1f, verticalBias = verticalBias))
                        .offset(x = (-3).dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color.White, CircleShape)
                            .border(2.dp, backgroundColor, CircleShape)
                    )
                }
            }

            // Create regular outgoing anchors (right side)
            repeat(regularOutgoing.size) { index ->
                val fraction = (index + 1).toFloat() / (regularOutgoing.size + 1)
                val verticalBias = (fraction * 2) - 1

                KuiverAnchor(
                    anchorId = "out-$index",
                    nodeId = nodeId,
                    modifier = Modifier
                        .align(BiasAlignment(horizontalBias = 1f, verticalBias = verticalBias))
                        .offset(x = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color.White, CircleShape)
                            .border(2.dp, backgroundColor, CircleShape)
                    )
                }
            }

            // Create back edge anchors (top side)
            repeat(backIncoming.size) { index ->
                val fraction = (index + 1).toFloat() / (backIncoming.size + backOutgoing.size + 1)
                val horizontalBias = (fraction * 2) - 1

                KuiverAnchor(
                    anchorId = "back-in-$index",
                    nodeId = nodeId,
                    modifier = Modifier
                        .align(BiasAlignment(horizontalBias = horizontalBias, verticalBias = -1f))
                        .offset(y = (-3).dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFFFF6B6B), CircleShape)
                            .border(2.dp, backgroundColor, CircleShape)
                    )
                }
            }

            repeat(backOutgoing.size) { index ->
                val fraction =
                    (backIncoming.size + index + 1).toFloat() / (backIncoming.size + backOutgoing.size + 1)
                val horizontalBias = (fraction * 2) - 1

                KuiverAnchor(
                    anchorId = "back-out-$index",
                    nodeId = nodeId,
                    modifier = Modifier
                        .align(BiasAlignment(horizontalBias = horizontalBias, verticalBias = -1f))
                        .offset(y = (-3).dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFFFF6B6B), CircleShape)
                            .border(2.dp, backgroundColor, CircleShape)
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = data.icon.imageVector,
                contentDescription = data.title,
                tint = Color.White,
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .width(32.dp)
            )

            Text(
                text = data.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (data.description.isNotEmpty()) {
                Text(
                    text = data.description,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
