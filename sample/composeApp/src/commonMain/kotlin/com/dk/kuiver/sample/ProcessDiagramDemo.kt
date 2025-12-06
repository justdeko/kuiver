package com.dk.kuiver.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.KuiverEdge
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.model.layout.LayoutAlgorithm
import com.dk.kuiver.model.layout.LayoutConfig
import com.dk.kuiver.rememberSaveableKuiverViewerState
import com.dk.kuiver.renderer.KuiverViewer
import com.dk.kuiver.renderer.KuiverViewerConfig
import com.dk.kuiver.ui.StyledEdgeContent
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProcessDiagramDemo(
    onNavigateBack: () -> Unit
) {
    var selectedLayoutAlgorithm by rememberSaveable { mutableStateOf(LayoutAlgorithm.HIERARCHICAL) }

    // Node data managed separately (user responsibility)
    val processNodeData = remember {
        mapOf(
            "start" to ProcessNode("Wake Up", "Time for breakfast!", ProcessNodeType.START, ProcessIcon.SUN),
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
            "cook_eggs" to ProcessNode("Cook Eggs", "Sunny side up", ProcessNodeType.PROCESS, ProcessIcon.PAN),
            "eggs_check" to ProcessNode(
                "Eggs Done?",
                "Check the yolks",
                ProcessNodeType.DECISION,
                ProcessIcon.EYES
            ),
            "eggs_done" to ProcessNode("Eggs Perfect", "Just right!", ProcessNodeType.PROCESS, ProcessIcon.CHECKMARK),

            // TOAST TRACK
            "slice_bread" to ProcessNode(
                "Slice Bread",
                "Two thick slices",
                ProcessNodeType.PROCESS,
                ProcessIcon.BREAD
            ),
            "toast" to ProcessNode("Toast Bread", "Golden brown", ProcessNodeType.PROCESS, ProcessIcon.FIRE),
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
            "pour_juice" to ProcessNode("Pour Juice", "Into glass", ProcessNodeType.PROCESS, ProcessIcon.JUICE),

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
            "serve" to ProcessNode("Serve", "Breakfast is ready!", ProcessNodeType.PROCESS, ProcessIcon.CHEF),
            "enjoy" to ProcessNode("Enjoy!", "Bon appétit!", ProcessNodeType.END, ProcessIcon.CELEBRATE)
        )
    }

    // Graph structure (library manages this)
    val processKuiver = remember {
        Kuiver().apply {
            // Add nodes with just IDs
            processNodeData.keys.forEach { id ->
                addNode(KuiverNode(id))
            }

            // EDGES - Setup
            addEdge(KuiverEdge("start", "preheat"))
            addEdge(KuiverEdge("start", "grind_beans"))
            addEdge(KuiverEdge("start", "slice_bread"))
            addEdge(KuiverEdge("start", "squeeze_oranges"))

            // Coffee track
            addEdge(KuiverEdge("grind_beans", "boil_water"))
            addEdge(KuiverEdge("boil_water", "brew_coffee"))
            addEdge(KuiverEdge("brew_coffee", "coffee_ready"))
            addEdge(KuiverEdge("coffee_ready", "serve"))

            // Bacon & Eggs track
            addEdge(KuiverEdge("preheat", "cook_bacon"))
            addEdge(KuiverEdge("cook_bacon", "bacon_done"))
            addEdge(KuiverEdge("bacon_done", "crack_eggs"))
            addEdge(KuiverEdge("crack_eggs", "cook_eggs"))
            addEdge(KuiverEdge("cook_eggs", "eggs_check"))
            addEdge(KuiverEdge("eggs_check", "eggs_done")) // Yes, done
            addEdge(KuiverEdge("eggs_check", "cook_eggs")) // No, cook more
            addEdge(KuiverEdge("eggs_done", "plate_food"))

            // Toast track
            addEdge(KuiverEdge("slice_bread", "toast"))
            addEdge(KuiverEdge("toast", "butter"))
            addEdge(KuiverEdge("butter", "plate_food"))

            // Juice track
            addEdge(KuiverEdge("squeeze_oranges", "strain_juice"))
            addEdge(KuiverEdge("strain_juice", "pour_juice"))
            addEdge(KuiverEdge("pour_juice", "serve"))

            // Final plating and serving
            addEdge(KuiverEdge("plate_food", "add_garnish"))
            addEdge(KuiverEdge("add_garnish", "serve"))
            addEdge(KuiverEdge("serve", "enjoy"))
        }
    }

    val layoutConfig = remember(selectedLayoutAlgorithm) {
        LayoutConfig(
            algorithm = selectedLayoutAlgorithm
            // No need to manually set spacing - will be calculated from measured node sizes!
        )
    }

    val kuiverViewerState = rememberSaveableKuiverViewerState(
        initialKuiver = processKuiver,
        layoutConfig = layoutConfig
    )

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
                    processNodeData[node.id]?.let { ProcessNodeContent(it) }
                },
                edgeContent = { edge, from, to ->
                    StyledEdgeContent(
                        edge = edge,
                        from = from,
                        to = to,
                        baseColor = MaterialTheme.colorScheme.outline,
                        backEdgeColor = MaterialTheme.colorScheme.error,
                        strokeWidth = 2.5f
                    )
                }
            )

            // Zoom controls using HorizontalFloatingToolbar (matching graph builder)
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
private fun ProcessNodeContent(data: ProcessNode) {
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
            .clip(shape)
            .background(backgroundColor)
            .border(3.dp, Color.White.copy(alpha = 0.3f), shape)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon
            Icon(
                imageVector = data.icon.imageVector,
                contentDescription = data.title,
                tint = Color.White,
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .width(32.dp)
            )

            // Title
            Text(
                text = data.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Description
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
