package com.marrakechguide.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Marrakech Guide theme configuration.
 *
 * Uses Material 3 with custom color scheme based on Marrakech's
 * warm terracotta palette.
 */

private val LightColors = lightColorScheme(
    primary = Terracotta500,
    onPrimary = Neutral50,
    primaryContainer = Terracotta100,
    onPrimaryContainer = Terracotta900,

    secondary = Terracotta400,
    onSecondary = Neutral50,
    secondaryContainer = Terracotta50,
    onSecondaryContainer = Terracotta800,

    tertiary = MarrakechOlive,
    onTertiary = Neutral50,

    background = Neutral50,
    onBackground = Neutral900,

    surface = Neutral50,
    onSurface = Neutral900,
    surfaceVariant = Neutral100,
    onSurfaceVariant = Neutral700,

    outline = Neutral300,
    outlineVariant = Neutral200,

    error = SemanticError,
    onError = Neutral50,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
)

private val DarkColors = darkColorScheme(
    primary = Terracotta400,
    onPrimary = Terracotta900,
    primaryContainer = Terracotta800,
    onPrimaryContainer = Terracotta100,

    secondary = Terracotta300,
    onSecondary = Terracotta900,
    secondaryContainer = Terracotta700,
    onSecondaryContainer = Terracotta100,

    tertiary = MarrakechOlive,
    onTertiary = Neutral900,

    background = Neutral900,
    onBackground = Neutral50,

    surface = Neutral800,
    onSurface = Neutral50,
    surfaceVariant = Neutral700,
    onSurfaceVariant = Neutral300,

    outline = Neutral600,
    outlineVariant = Neutral700,

    error = Color(0xFFF87171),
    onError = Neutral900,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
)

@Composable
fun MarrakechGuideTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MarrakechTypography,
        content = content,
    )
}

/**
 * Spacing scale for consistent layout.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

/**
 * Corner radius scale for consistent shapes.
 */
object CornerRadius {
    val sm = 4.dp
    val md = 8.dp
    val lg = 12.dp
    val xl = 16.dp
}

