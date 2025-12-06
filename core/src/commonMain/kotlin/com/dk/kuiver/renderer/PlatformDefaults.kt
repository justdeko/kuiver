package com.dk.kuiver.renderer

/**
 * Platform-specific defaults for KuiverViewer configuration.
 */
internal expect object PlatformDefaults {
    /**
     * Default pan velocity for scroll/trackpad gestures.
     * Web platforms use a lower value to compensate for higher sensitivity.
     */
    val defaultPanVelocity: Float

    /**
     * Default font loading delay in milliseconds.
     * Web platforms use a delay to ensure fonts are loaded before measurement.
     */
    val defaultFontLoadingDelayMs: Long
}
