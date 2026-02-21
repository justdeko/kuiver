package com.dk.kuiver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Configuration for edge label styling.
 *
 * @property textColor Color of the label text (default: black)
 * @property backgroundColor Background color of the label box (default: white with 90% opacity)
 * @property fontSize Font size of the label text (default: 12.sp)
 * @property padding Inner padding of the label box (default: 4.dp)
 * @property borderColor Color of the label border, null for no border (default: black with 30% opacity)
 * @property borderWidth Width of the label border (default: 1.dp)
 * @property cornerRadius Corner radius of the label box (default: 4.dp)
 * @property maxLines Maximum number of lines for the label text (default: 1)
 * @property overflow Text overflow behavior (default: TextOverflow.Ellipsis)
 * @property rotateWithEdge Whether to rotate the label to align with edge tangent (default: false)
 */
@Immutable
data class EdgeLabelStyle(
    val textColor: Color = Color.Black,
    val backgroundColor: Color = Color.White.copy(alpha = 0.9f),
    val fontSize: TextUnit = 12.sp,
    val padding: Dp = 4.dp,
    val borderColor: Color? = Color.Black.copy(alpha = 0.3f),
    val borderWidth: Dp = 1.dp,
    val cornerRadius: Dp = 4.dp,
    val maxLines: Int = 1,
    val overflow: TextOverflow = TextOverflow.Ellipsis,
    val rotateWithEdge: Boolean = false
)

/**
 * Default composable for rendering edge labels.
 *
 * Renders text with a background box, optional border, and padding to ensure readability
 * over edges and other graph elements.
 *
 * @param label The text to display in the label
 * @param modifier Modifier to apply to the label container
 * @param style Styling configuration for the label
 */
@Composable
fun DefaultEdgeLabel(
    label: String,
    modifier: Modifier = Modifier,
    style: EdgeLabelStyle = EdgeLabelStyle()
) {
    val shape = RoundedCornerShape(style.cornerRadius)

    Box(
        modifier = modifier
            .background(style.backgroundColor, shape)
            .then(
                if (style.borderColor != null) {
                    Modifier.border(style.borderWidth, style.borderColor, shape)
                } else {
                    Modifier
                }
            )
            .padding(style.padding),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            color = { style.textColor },
            style = TextStyle(
                fontSize = style.fontSize,
                color = style.textColor
            ),
            maxLines = style.maxLines,
            overflow = style.overflow
        )
    }
}
