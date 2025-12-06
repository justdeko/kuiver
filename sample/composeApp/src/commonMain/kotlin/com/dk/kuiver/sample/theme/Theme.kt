package com.dk.kuiver.sample.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// Base theme colors - neutral and professional
private val md_theme_light_primary = Color(0xFF2C2C3E) // Deep charcoal
private val md_theme_light_onPrimary = Color(0xFFFFFFFF)
private val md_theme_light_primaryContainer = Color(0xFFE8E8F0)
private val md_theme_light_onPrimaryContainer = Color(0xFF1A1A28)

private val md_theme_light_secondary = Color(0xFF5E5E6E) // Medium gray
private val md_theme_light_onSecondary = Color(0xFFFFFFFF)
private val md_theme_light_secondaryContainer = Color(0xFFE3E3EC)
private val md_theme_light_onSecondaryContainer = Color(0xFF1B1B28)

private val md_theme_light_tertiary = Color(0xFF7E7E92) // Light gray-blue
private val md_theme_light_onTertiary = Color(0xFFFFFFFF)
private val md_theme_light_tertiaryContainer = Color(0xFFE8E8F5)
private val md_theme_light_onTertiaryContainer = Color(0xFF2A2A3E)

private val md_theme_light_error = Color(0xFFBA1A1A)
private val md_theme_light_onError = Color(0xFFFFFFFF)
private val md_theme_light_errorContainer = Color(0xFFFFDAD6)
private val md_theme_light_onErrorContainer = Color(0xFF410002)

private val md_theme_light_background = Color(0xFFFCFCFF) // Near white
private val md_theme_light_onBackground = Color(0xFF1A1A1F)
private val md_theme_light_surface = Color(0xFFFCFCFF)
private val md_theme_light_onSurface = Color(0xFF1A1A1F)

private val md_theme_light_surfaceVariant = Color(0xFFE4E4EB)
private val md_theme_light_onSurfaceVariant = Color(0xFF46464F)
private val md_theme_light_outline = Color(0xFF767680)

private val md_theme_dark_primary = Color(0xFFCFCFE0) // Light gray-blue
private val md_theme_dark_onPrimary = Color(0xFF2C2C3E)
private val md_theme_dark_primaryContainer = Color(0xFF3E3E52)
private val md_theme_dark_onPrimaryContainer = Color(0xFFE8E8F0)

private val md_theme_dark_secondary = Color(0xFFC7C7D4) // Light gray
private val md_theme_dark_onSecondary = Color(0xFF30303E)
private val md_theme_dark_secondaryContainer = Color(0xFF464655)
private val md_theme_dark_onSecondaryContainer = Color(0xFFE3E3EC)

private val md_theme_dark_tertiary = Color(0xFFCCCCDC) // Light gray-blue
private val md_theme_dark_onTertiary = Color(0xFF353545)
private val md_theme_dark_tertiaryContainer = Color(0xFF4C4C5E)
private val md_theme_dark_onTertiaryContainer = Color(0xFFE8E8F5)

private val md_theme_dark_error = Color(0xFFFFB4AB)
private val md_theme_dark_onError = Color(0xFF690005)
private val md_theme_dark_errorContainer = Color(0xFF93000A)
private val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)

private val md_theme_dark_background = Color(0xFF1A1A1F) // Dark gray
private val md_theme_dark_onBackground = Color(0xFFE3E3E8)
private val md_theme_dark_surface = Color(0xFF1A1A1F)
private val md_theme_dark_onSurface = Color(0xFFE3E3E8)

private val md_theme_dark_surfaceVariant = Color(0xFF46464F)
private val md_theme_dark_onSurfaceVariant = Color(0xFFC7C7D0)
private val md_theme_dark_outline = Color(0xFF90909A)

val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
)

val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
)

// Extended colors for DAG nodes
@Immutable
data class ExtendedColors(
    val nodePink: Color,
    val onNodePink: Color,
    val nodeOrange: Color,
    val onNodeOrange: Color,
    val nodeYellow: Color,
    val onNodeYellow: Color,
    val nodeGreen: Color,
    val onNodeGreen: Color,
    val nodeBlue: Color,
    val onNodeBlue: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        nodePink = Color.Unspecified,
        onNodePink = Color.Unspecified,
        nodeOrange = Color.Unspecified,
        onNodeOrange = Color.Unspecified,
        nodeYellow = Color.Unspecified,
        onNodeYellow = Color.Unspecified,
        nodeGreen = Color.Unspecified,
        onNodeGreen = Color.Unspecified,
        nodeBlue = Color.Unspecified,
        onNodeBlue = Color.Unspecified
    )
}

// Node colors as theme extension
private val lightExtendedColors = ExtendedColors(
    nodePink = Color(0xFFFF6B9D),
    onNodePink = Color.White,
    nodeOrange = Color(0xFFFFA06B),
    onNodeOrange = Color.White,
    nodeYellow = Color(0xFFFFC93C),
    onNodeYellow = Color.Black,
    nodeGreen = Color(0xFF6BCF7F),
    onNodeGreen = Color.White,
    nodeBlue = Color(0xFF6B9DFF),
    onNodeBlue = Color.White
)

private val darkExtendedColors = ExtendedColors(
    nodePink = Color(0xFFFF6B9D),
    onNodePink = Color.White,
    nodeOrange = Color(0xFFFFA06B),
    onNodeOrange = Color.White,
    nodeYellow = Color(0xFFFFC93C),
    onNodeYellow = Color.Black,
    nodeGreen = Color(0xFF6BCF7F),
    onNodeGreen = Color.White,
    nodeBlue = Color(0xFF6B9DFF),
    onNodeBlue = Color.White
)

@Composable
fun AppTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) darkExtendedColors else lightExtendedColors

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

// Accessor for extended colors
object ExtendedTheme {
    val colors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}
