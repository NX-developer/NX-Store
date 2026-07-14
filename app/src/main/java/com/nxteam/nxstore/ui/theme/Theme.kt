package com.nxteam.nxstore.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NxColors = darkColorScheme(
    primary = NxPrimary,
    onPrimary = Color(0xFFFFFFFF),
    secondary = NxPrimaryVariant,
    background = NxBackground,
    onBackground = NxOnBackground,
    surface = NxSurface,
    onSurface = NxOnBackground,
    surfaceVariant = NxSurfaceVariant,
    onSurfaceVariant = NxOnSurfaceMuted
)

@Composable
fun NxStoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NxColors,
        typography = NxTypography,
        content = content
    )
}
