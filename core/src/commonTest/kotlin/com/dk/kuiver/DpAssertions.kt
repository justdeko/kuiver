package com.dk.kuiver

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Float error, for lengths two paths are expected to compute identically. */
internal val DP_TOLERANCE = 0.01f.dp

/**
 * Asserts two lengths are equal within [absoluteTolerance], so layout tests compare typed lengths
 * instead of unwrapping both sides.
 */
internal fun assertDpEquals(
    expected: Dp,
    actual: Dp,
    absoluteTolerance: Dp = DP_TOLERANCE,
    message: String? = null
) {
    assertEquals(expected.value, actual.value, absoluteTolerance.value, message)
}

/** [assertDpEquals] for both axes of a position. */
internal fun assertDpOffsetEquals(
    expected: DpOffset,
    actual: DpOffset,
    absoluteTolerance: Dp = DP_TOLERANCE,
    message: String? = null
) {
    assertDpEquals(expected.x, actual.x, absoluteTolerance, "$message (x)")
    assertDpEquals(expected.y, actual.y, absoluteTolerance, "$message (y)")
}

/** Asserts [actual] lies within [range], which reads better than two comparisons. */
internal fun assertDpIn(range: ClosedRange<Dp>, actual: Dp, message: String? = null) {
    assertTrue(actual in range, "$message: $actual outside $range")
}
