package com.dk.kuiver.sample

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kuiver.sample.composeapp.generated.resources.Res
import kuiver.sample.composeapp.generated.resources.kuiver_icon
import org.jetbrains.compose.resources.painterResource
import java.awt.Dimension

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Kuiver Sample",
        icon = painterResource(Res.drawable.kuiver_icon),
        state = rememberWindowState(
            size = DpSize(1920.dp, 1080.dp)
        )
    ) {
        window.minimumSize = Dimension(600, 400)
        App()
    }
}