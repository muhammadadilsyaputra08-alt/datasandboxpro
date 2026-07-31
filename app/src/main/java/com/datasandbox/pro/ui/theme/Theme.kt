package com.datasandbox.pro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF1E7B4E)
private val GreenDark = Color(0xFF7FDBA3)

private val LightColors = lightColorScheme(
    primary = Green,
    secondary = Color(0xFF3A5F45),
    background = Color(0xFFF7F9F7),
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = GreenDark,
    secondary = Color(0xFF8FCBA5),
    background = Color(0xFF10130F),
    surface = Color(0xFF191D18)
)

@Composable
fun DataSandboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
