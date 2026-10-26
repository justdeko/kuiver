package com.dk.kuiver.sample

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.dk.kuiver.KuiverViewerState

@Composable
internal fun Modifier.graphKeyboardControls(state: KuiverViewerState): Modifier {
    val focusRequester = remember { FocusRequester() }
    return this
        .focusRequester(focusRequester)
        .onKeyEvent { event -> handleGraphKey(event, state, panStep = 48.dp) }
        .focusable()
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                focusRequester.requestFocus()
            }
        }
}

private fun handleGraphKey(event: KeyEvent, state: KuiverViewerState, panStep: Dp): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    // Move the content opposite to the arrow
    val pan = when (event.key) {
        Key.DirectionLeft -> DpOffset(panStep, 0.dp)
        Key.DirectionRight -> DpOffset(-panStep, 0.dp)
        Key.DirectionUp -> DpOffset(0.dp, panStep)
        Key.DirectionDown -> DpOffset(0.dp, -panStep)
        else -> null
    }
    if (pan != null) {
        state.updateTransform(state.scale, state.offset + pan)
        return true
    }

    // Match the typed character too, macOS reports punctuation keys by US layout position
    val char = event.utf16CodePoint.toChar()
    when {
        event.key in setOf(Key.Plus, Key.Equals, Key.NumPadAdd) || char in "+=" -> state.zoomIn()
        event.key in setOf(Key.Minus, Key.NumPadSubtract) || char == '-' -> state.zoomOut()
        else -> return false
    }
    return true
}
