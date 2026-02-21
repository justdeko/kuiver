package com.dk.kuiver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun DefaultNodeContent(
    label: String,
    backgroundColor: Color,
    textColor: Color,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .shadow(3.dp, CircleShape)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            color = { textColor },
        )
    }
}
