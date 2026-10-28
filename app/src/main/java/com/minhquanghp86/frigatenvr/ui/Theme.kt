package com.minhquanghp86.frigatenvr.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DeepBg = Color(0xFF0E1116)
private val SurfaceDark = Color(0xFF161B22)
private val SurfaceHigh = Color(0xFF1F2630)
private val Accent = Color(0xFF3B82F6)
private val AccentLight = Color(0xFF60A5FA)
private val Danger = Color(0xFFF87171)
private val Ok = Color(0xFF34D399)
private val OnDark = Color(0xFFE6EDF3)
private val Muted = Color(0xFF8B98A9)

val FrigateColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = AccentLight,
    background = DeepBg,
    onBackground = OnDark,
    surface = SurfaceDark,
    onSurface = OnDark,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = Muted,
    error = Danger,
    onError = Color.Black,
)

val OkColor = Ok

@Composable
fun FrigateTheme(content: @Composable () -> Unit) {
    // App NVR luôn dùng nền tối cho phù hợp màn hình camera.
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = FrigateColors, content = content)
}
