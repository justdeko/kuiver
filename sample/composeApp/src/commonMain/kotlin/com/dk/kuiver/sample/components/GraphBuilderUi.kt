package com.dk.kuiver.sample.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk.kuiver.model.layout.LayoutDirection
import com.dk.kuiver.sample.LayoutAlgorithm
import com.dk.kuiver.sample.NodeColorType
import com.dk.kuiver.sample.NodeColors

@Composable
fun GraphBuilderHeader(
    modifier: Modifier = Modifier,
    nodeCount: Int,
    edgeCount: Int,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Kuiver",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-2).sp,
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Nodes: $nodeCount | Edges: $edgeCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = onThemeToggle
            ) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    contentDescription = if (isDarkTheme) "Switch to light mode" else "Switch to dark mode",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GraphControlMenu(
    showNodeForm: Boolean,
    showEdgeForm: Boolean,
    selectedLayoutAlgorithm: LayoutAlgorithm,
    selectedLayoutDirection: LayoutDirection,
    isAutoGenerating: Boolean,
    onToggleNodeForm: () -> Unit,
    onToggleEdgeForm: () -> Unit,
    onToggleDebugBounds: () -> Unit,
    onToggleAutoGenerate: () -> Unit,
    onLayoutAlgorithmChange: (LayoutAlgorithm) -> Unit,
    onLayoutDirectionChange: (LayoutDirection) -> Unit,
    onClearAll: () -> Unit,
    onNavigateToDemo: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
        ) {
            ButtonGroup(
                expandedRatio = 0f, // TODO remove when this works
                overflowIndicator = { menuState ->
                    FilledIconButton(
                        onClick = {
                            if (menuState.isExpanded) {
                                menuState.dismiss()
                            } else {
                                menuState.show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More options",
                        )
                    }
                }
            ) {
                clickableItem(
                    onClick = onToggleNodeForm,
                    label = "Node",
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.AddCircle,
                            contentDescription = "${if (showNodeForm) "Hide" else "Add"} Node"
                        )
                    }
                )
                clickableItem(
                    onClick = onToggleEdgeForm,
                    label = "Edge",
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.AddLink,
                            contentDescription = "${if (showEdgeForm) "Hide" else "Add"} Edge"
                        )
                    }
                )
                clickableItem(
                    onClick = onClearAll,
                    label = "Clear All",
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Clear All"
                        )
                    }
                )
                clickableItem(
                    onClick = onToggleAutoGenerate,
                    label = if (isAutoGenerating) "Stop" else "Auto",
                    icon = {
                        Icon(
                            imageVector = if (isAutoGenerating) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = if (isAutoGenerating) "Stop auto-generation" else "Start auto-generation"
                        )
                    }
                )
                clickableItem(
                    onClick = onNavigateToDemo,
                    label = "Demo",
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Visibility,
                            contentDescription = "View Process Demo"
                        )
                    }
                )
                clickableItem(
                    onClick = onToggleDebugBounds,
                    label = "Debug",
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Debug"
                        )
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Layout:", fontWeight = FontWeight.Medium)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    LayoutAlgorithm.entries.forEachIndexed { index, algorithm ->
                        ToggleButton(
                            checked = selectedLayoutAlgorithm == algorithm,
                            onCheckedChange = { onLayoutAlgorithmChange(algorithm) },
                            modifier = Modifier.semantics { role = Role.RadioButton },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                LayoutAlgorithm.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        ) {
                            Text(
                                when (algorithm) {
                                    LayoutAlgorithm.HIERARCHICAL -> "Hierarchical"
                                    LayoutAlgorithm.FORCE_DIRECTED -> "Force"
                                }
                            )
                        }
                    }
                }

                AnimatedVisibility(selectedLayoutAlgorithm == LayoutAlgorithm.HIERARCHICAL) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    ) {
                        LayoutDirection.entries.forEachIndexed { index, direction ->
                            ToggleButton(
                                checked = selectedLayoutDirection == direction,
                                onCheckedChange = { onLayoutDirectionChange(direction) },
                                modifier = Modifier.semantics { role = Role.RadioButton },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    LayoutDirection.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                            ) {
                                Icon(
                                    imageVector = when (direction) {
                                        LayoutDirection.HORIZONTAL -> Icons.AutoMirrored.Filled.ArrowForward
                                        LayoutDirection.VERTICAL -> Icons.Filled.ArrowDownward
                                    },
                                    contentDescription = when (direction) {
                                        LayoutDirection.HORIZONTAL -> "Horizontal layout"
                                        LayoutDirection.VERTICAL -> "Vertical layout"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NodeCreationForm(
    visible: Boolean,
    newNodeData: String,
    selectedNodeColorType: NodeColorType?,
    onNodeDataChange: (String) -> Unit,
    onColorTypeSelect: (NodeColorType?) -> Unit,
    onCreateNode: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newNodeData,
                    onValueChange = onNodeDataChange,
                    placeholder = { Text("Node label", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = NodeColorType.ALL.map { NodeColors.getColor(it) }
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { onColorTypeSelect(null) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent
                            ),
                            modifier = Modifier.size(28.dp),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (selectedNodeColorType == null) {
                                Text("✓", fontSize = 10.sp, color = Color.White)
                            }
                        }
                    }

                    NodeColorType.ALL.forEach { colorType ->
                        val color = NodeColors.getColor(colorType)
                        Button(
                            onClick = { onColorTypeSelect(colorType) },
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            modifier = Modifier.size(28.dp),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (selectedNodeColorType == colorType) {
                                Text("✓", fontSize = 10.sp, color = Color.White)
                            }
                        }
                    }
                }

                Button(
                    onClick = onCreateNode,
                    modifier = Modifier.size(height = 40.dp, width = 56.dp),
                    contentPadding = PaddingValues(4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("+", fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
fun EdgeCreationForm(
    visible: Boolean,
    hasNodes: Boolean,
    fromNodeLabel: String,
    toNodeLabel: String,
    onFromNodeLabelChange: (String) -> Unit,
    onToNodeLabelChange: (String) -> Unit,
    onCreateEdge: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (!hasNodes) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Create nodes first!",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = fromNodeLabel,
                        onValueChange = onFromNodeLabelChange,
                        placeholder = { Text("From", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text("→", fontSize = 18.sp)

                    OutlinedTextField(
                        value = toNodeLabel,
                        onValueChange = onToNodeLabelChange,
                        placeholder = { Text("To", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = onCreateEdge,
                        modifier = Modifier.size(height = 40.dp, width = 56.dp),
                        contentPadding = PaddingValues(4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("+", fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionModeBanner(
    sourceNodeLabel: String,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Click another node to connect from \"$sourceNodeLabel\"",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text("Cancel", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
