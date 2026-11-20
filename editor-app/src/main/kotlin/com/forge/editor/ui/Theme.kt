package com.forge.editor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ForgeDark = darkColorScheme(
    primary = Color(0xFF3FA9F5),
    onPrimary = Color(0xFF04121D),
    primaryContainer = Color(0xFF12405F),
    onPrimaryContainer = Color(0xFFD6E9FF),
    secondary = Color(0xFFF5A623),
    onSecondary = Color(0xFF241700),
    tertiary = Color(0xFF8A7CFF),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE6E9EF),
    surface = Color(0xFF151A22),
    onSurface = Color(0xFFE6E9EF),
    surfaceVariant = Color(0xFF1E2530),
    onSurfaceVariant = Color(0xFFA8B2C1),
    outline = Color(0xFF39424F),
    error = Color(0xFFFF6B6B)
)

@Composable
fun ForgeEditorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ForgeDark, content = content)
}
