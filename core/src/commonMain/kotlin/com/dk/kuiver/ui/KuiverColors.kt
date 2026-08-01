package com.dk.kuiver.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Color palette for graph elements rendered without an explicit color override.
 *
 * kuiver depends on compose `runtime` + `foundation` + `ui` only, so this is a
 * foundation-only theming seam rather than a read from `MaterialTheme`. The
 * defaults reproduce kuiver's original hardcoded colors, so providing no
 * [LocalKuiverColors] value is source- and behavior-compatible with prior versions.
 *
 * @property edge Color for forward/tree edges (default: black)
 * @property backEdge Color for back edges and self-loops (default: red)
 * @property labelText Color of edge label text (default: black)
 * @property labelBackground Background color of the edge label box (default: white with 90% opacity)
 * @property labelBorder Color of the edge label border, null for no border (default: black with 30% opacity)
 */
@Immutable
data class KuiverColors(
    val edge: Color = Color.Black,
    val backEdge: Color = Color(0xFFFF6B6B),
    val labelText: Color = Color.Black,
    val labelBackground: Color = Color.White.copy(alpha = 0.9f),
    val labelBorder: Color? = Color.Black.copy(alpha = 0.3f)
)

/**
 * The [KuiverColors] used by composable defaults that don't receive an explicit color.
 *
 * Provide this to theme a graph once instead of overriding colors in every
 * `edgeContent` lambda:
 *
 * ```
 * CompositionLocalProvider(
 *     LocalKuiverColors provides KuiverColors(
 *         edge = MaterialTheme.colorScheme.onSurface,
 *         backEdge = MaterialTheme.colorScheme.tertiary,
 *         labelText = MaterialTheme.colorScheme.onSurface,
 *         labelBackground = MaterialTheme.colorScheme.surface,
 *     ),
 * ) {
 *     KuiverViewer(state = viewerState)
 * }
 * ```
 */
val LocalKuiverColors: ProvidableCompositionLocal<KuiverColors> =
    staticCompositionLocalOf { KuiverColors() }

/** Default values used by kuiver's edge composables, themeable via [LocalKuiverColors]. */
object KuiverDefaults {

    /** An [EdgeLabelStyle] built from the current [LocalKuiverColors]. */
    @Composable
    fun edgeLabelStyle(): EdgeLabelStyle = LocalKuiverColors.current.let { colors ->
        EdgeLabelStyle(
            textColor = colors.labelText,
            backgroundColor = colors.labelBackground,
            borderColor = colors.labelBorder
        )
    }
}
