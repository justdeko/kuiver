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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk.kuiver.sample.NodeColorType
import com.dk.kuiver.sample.NodeColors

@Composable
internal fun <T> ConnectedToggleGroup(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    icon: ((T) -> ImageVector)? = null,
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
                if (icon != null) {
                    Icon(
                        icon(option),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                }
                Text(label(option))
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
    edgeLabel: String = "",
    onEdgeLabelChange: (String) -> Unit = {},
    labelPosition: Float = 0.5f,
    onLabelPositionChange: (Float) -> Unit = {},
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // From/To node selection row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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

                    // Edge label input row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = edgeLabel,
                            onValueChange = onEdgeLabelChange,
                            placeholder = { Text("Label (optional)", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Label position slider
                    if (edgeLabel.isNotBlank()) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Label Position: ${when {
                                    labelPosition < 0.3f -> "Start"
                                    labelPosition > 0.7f -> "End"
                                    else -> "Center"
                                }}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Slider(
                                value = labelPosition,
                                onValueChange = onLabelPositionChange,
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
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

@Composable
internal fun ToolbarButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
        Icon(icon, contentDescription = description)
    }
}
