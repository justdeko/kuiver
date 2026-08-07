package com.dk.kuiver.sample

/**
 * Whether the viewer zooms on Ctrl+scroll here, which is worth telling the user about once.
 * True on the platforms that scroll to pan, false where pinching is the natural gesture.
 */
internal expect val usesCtrlScrollZoom: Boolean
