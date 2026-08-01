package com.dk.kuiver.util

import androidx.compose.ui.unit.DpOffset

/**
 * Scales a [DpOffset] by [factor]. [DpOffset] ships `plus` and `minus` but no scalar multiply,
 * which the view transform needs on every zoom.
 */
internal operator fun DpOffset.times(factor: Float): DpOffset =
    DpOffset(x * factor, y * factor)

/** Divides a [DpOffset] by [factor]. See [times]. */
internal operator fun DpOffset.div(factor: Float): DpOffset =
    DpOffset(x / factor, y / factor)
