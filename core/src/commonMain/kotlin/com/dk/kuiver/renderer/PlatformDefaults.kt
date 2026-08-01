package com.dk.kuiver.renderer

/**
 * Platform-specific defaults for KuiverViewer configuration.
 */
internal expect object PlatformDefaults {
    /**
     * Default pan velocity for scroll/trackpad gestures, in dp per scroll unit.
     * Web platforms use a lower value to compensate for higher sensitivity.
     */
    val defaultPanVelocity: Float
}
