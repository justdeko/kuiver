package com.dk.kuiver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EdgeRenderingTest {

    @Test
    fun boundedCanvasDrawsInTheCoordinatesItWasGiven() = runComposeUiTest {
        setContent {
            Box(Modifier.size(100.dp).background(Color.White)) {
                // Canvas placed away from the origin, drawing in parent coordinates
                EdgeCanvas(Rect(left = 30f, top = 30f, right = 70f, bottom = 70f)) {
                    drawRect(Color.Red, topLeft = Offset(40f, 40f), size = Size(20f, 20f))
                }
            }
        }

        val pixels = onRoot().captureToImage().toPixelMap()
        assertEquals(Color.Red, pixels[50, 50], "the drawn rect moved off its coordinates")
        assertEquals(Color.White, pixels[75, 75], "something was drawn outside the rect")
        assertEquals(Color.White, pixels[35, 35], "something was drawn outside the rect")
    }

    @Test
    fun canvasLargerThanTheViewportStillDrawsInPlace() = runComposeUiTest {
        setContent {
            Box(Modifier.size(100.dp).background(Color.White)) {
                // Bounds far beyond the 100px viewport, as a long edge of a large graph has
                EdgeCanvas(Rect(left = -2000f, top = -2000f, right = 3000f, bottom = 3000f)) {
                    drawRect(Color.Red, topLeft = Offset(40f, 40f), size = Size(20f, 20f))
                }
            }
        }

        val pixels = onRoot().captureToImage().toPixelMap()
        assertEquals(Color.Red, pixels[50, 50], "the drawn rect moved off its coordinates")
        assertEquals(Color.White, pixels[75, 75], "something was drawn outside the rect")
    }

    @Test
    fun labelledEdgeDrawsBothTheLineAndTheLabel() = runComposeUiTest {
        setContent {
            Box(Modifier.size(200.dp).background(Color.White)) {
                EdgeContentWithLabel(
                    from = Offset(20f, 100f),
                    to = Offset(180f, 100f),
                    label = "edge",
                    color = Color.Red,
                    showArrow = false
                )
            }
        }

        onNodeWithText("edge").assertIsDisplayed()

        // Sampled away from the label, which sits at the center of the edge
        val onTheLine = onRoot().captureToImage().toPixelMap()[40, 100]
        assertTrue(
            onTheLine.red > 0.5f && onTheLine.green < 0.5f,
            "expected the edge line at (40, 100), got $onTheLine"
        )
    }
}
