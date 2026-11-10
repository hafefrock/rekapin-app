package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RekapinDarkColorScheme = darkColorScheme(
    primary = VioletPrimary,
    onPrimary = VioletOnPrimary,
    primaryContainer = VioletPrimaryContainer,
    onPrimaryContainer = VioletOnPrimaryContainer,
    secondary = VioletSecondary,
    onSecondary = VioletOnSecondary,
    secondaryContainer = VioletSecondaryContainer,
    onSecondaryContainer = VioletOnSecondaryContainer,
    tertiary = CyanAccent,
    onTertiary = CyanOnTertiary,
    background = GunmetalBackground,
    onBackground = GunmetalOnBackground,
    surface = GunmetalSurface,
    onSurface = GunmetalOnSurface,
    surfaceVariant = GunmetalSurfaceVariant,
    onSurfaceVariant = GunmetalOnSurfaceVariant,
    outline = GunmetalOutline,
    outlineVariant = GunmetalOutlineVariant,
    surfaceContainer = GunmetalSurfaceContainer,
    surfaceContainerHigh = GunmetalSurfaceContainerHigh,
    surfaceContainerHighest = GunmetalSurfaceContainerHighest,
    error = GunmetalError
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RekapinDarkColorScheme,
        typography = Typography,
        content = content
    )
}
