package com.dk.kuiver.sample

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.dk.kuiver.KuiverViewerState
import kotlinx.coroutines.delay

/**
 * Time the camera gets to zoom in on the first node before a pan starts.
 */
internal const val PanLeadInMillis = 1000L

/**
 * Room a tour caption takes at the top of the graph.
 */
internal val CaptionHeight: Dp = 132.dp

/**
 * Scripted walk through the features, timed for a screen recording.
 */
internal suspend fun ShowcaseState.playTour(viewer: KuiverViewerState) {
    // Start from the first three nodes
    viewer.interaction.clearSelection()
    viewer.clearManualPositions()
    restore(OrderSteps.keys.take(3))
    fit()
    show(ShowcaseCopy.intro)
    delay(2000)

    // Grow the graph one node at a time
    show(ShowcaseCopy.grow)
    OrderSteps.keys.drop(3).forEach { nodeId ->
        reveal(nodeId)
        delay(450)
    }
    delay(1200)

    // Play the order through
    show(ShowcaseCopy.composables)
    pan("order", "delivered", millis = 9000)
    delay(PanLeadInMillis)
    runOrder()
    fit(awaitLayout = false)
    delay(1600)

    // Expand nodes
    show(ShowcaseCopy.measured)
    toggleExpanded("payment")
    delay(2600)
    toggleExpanded("payment")
    delay(700)
    toggleExpanded("courier")
    delay(2400)
    toggleExpanded("courier")
    delay(1000)

    // Switch layouts
    show(ShowcaseCopy.layoutHierarchical)
    selectLayout(if (homeLayout == ShowcaseLayout.FLOW) ShowcaseLayout.STACK else ShowcaseLayout.FLOW)
    delay(1500)
    show(ShowcaseCopy.layoutOrganic)
    selectLayout(ShowcaseLayout.ORGANIC)
    delay(1500)
    show(ShowcaseCopy.layoutOrbit)
    selectLayout(ShowcaseLayout.ORBIT)
    delay(1700)

    // Edge styles, back on the home layout
    show(ShowcaseCopy.edges)
    selectLayout(homeLayout)
    delay(1800)
    edgeStyle = ShowcaseEdgeStyle.CIRCUIT
    delay(1400)
    edgeStyle = ShowcaseEdgeStyle.STRAIGHT
    delay(1200)
    edgeStyle = ShowcaseEdgeStyle.CURVES
    delay(1000)

    // Interaction
    show(ShowcaseCopy.interaction)
    viewer.interaction.select("pack")
    delay(1800)
    viewer.interaction.selectEdge("courier->hub")
    delay(1800)
    viewer.interaction.clearSelection()
    repeat(45) {
        viewer.moveNodeBy("delivered", DpOffset(0.dp, 3.dp))
        delay(16)
    }
    delay(900)
    viewer.clearManualPositions()
    delay(1400)

    // Add and remove nodes
    show(ShowcaseCopy.runtime)
    repeat(3) {
        addStep()
        delay(1000)
    }
    remove(ExtraSteps.keys.elementAt(1))
    delay(1000)
    remove(ExtraSteps.keys.elementAt(2))
    delay(1400)

    // Camera
    show(ShowcaseCopy.camera)
    focus("hub")
    delay(2000)
    focus("order")
    delay(2000)
    fit()
    delay(1600)

    // Outro
    show(ShowcaseCopy.outro)
    runOrder()
    delay(2500)
}

@Composable
internal fun TourCaption(caption: TourText?, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = caption,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(300, delayMillis = 100)) + scaleIn(initialScale = 0.92f)) togetherWith
                    (fadeOut(tween(150)) + scaleOut(targetScale = 0.92f))
        },
        label = "tour_caption"
    ) { shown ->
        if (shown == null) {
            Spacer(Modifier.height(1.dp))
            return@AnimatedContent
        }
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = shown.title,
                    style = if (shown.large) {
                        MaterialTheme.typography.displaySmallEmphasized
                    } else {
                        MaterialTheme.typography.headlineSmallEmphasized
                    },
                    textAlign = TextAlign.Center
                )
                Text(
                    text = shown.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Tour shown in the gif of README.md
 */
internal suspend fun ShowcaseState.playGifLoop(viewer: KuiverViewerState) {
    viewer.interaction.clearSelection()
    viewer.clearManualPositions()
    while (true) {
        restore(OrderSteps.keys.take(3))
        fit()
        delay(1500)
        OrderSteps.keys.drop(3).forEach { nodeId ->
            reveal(nodeId)
            delay(450)
        }
        delay(1200)
        pan("order", "delivered", millis = 9000)
        delay(PanLeadInMillis)
        runOrder()
        fit(awaitLayout = false)
        delay(1600)
    }
}
