package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    background = PureWhite,
    onBackground = StatusPromptText,
    surface = PureWhite,
    onSurface = StatusPromptText,
    surfaceVariant = StatusPromptBg,
    onSurfaceVariant = StatusPromptText
)

@Composable
fun CloudOrbTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
