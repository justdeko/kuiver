package com.dk.kuiver.sample

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.dk.kuiver.sample.theme.ExtendedTheme

// Helper object to access node colors from theme
object NodeColors {
    @Composable
    fun getColor(type: NodeColorType): Color {
        return when (type) {
            NodeColorType.PINK -> ExtendedTheme.colors.nodePink
            NodeColorType.ORANGE -> ExtendedTheme.colors.nodeOrange
            NodeColorType.YELLOW -> ExtendedTheme.colors.nodeYellow
            NodeColorType.GREEN -> ExtendedTheme.colors.nodeGreen
            NodeColorType.BLUE -> ExtendedTheme.colors.nodeBlue
        }
    }

    @Composable
    fun getTextColor(type: NodeColorType): Color {
        return when (type) {
            NodeColorType.PINK -> ExtendedTheme.colors.onNodePink
            NodeColorType.ORANGE -> ExtendedTheme.colors.onNodeOrange
            NodeColorType.YELLOW -> ExtendedTheme.colors.onNodeYellow
            NodeColorType.GREEN -> ExtendedTheme.colors.onNodeGreen
            NodeColorType.BLUE -> ExtendedTheme.colors.onNodeBlue
        }
    }
}
