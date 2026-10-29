package com.minhquanghp86.frigatenvr.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ------------------------------------------------------------------ theme state

/** Giao diện sáng/tối, lưu trong SharedPreferences để nhớ giữa các lần mở app. */
object ThemeState {
    private const val PREFS = "fricam_theme"
    private const val KEY_LIGHT = "light"

    var isLight by mutableStateOf(false)
        private set

    fun load(context: Context) {
        isLight = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_LIGHT, false)
    }

    fun setLight(value: Boolean, context: Context) {
        isLight = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LIGHT, value)
            .apply()
    }

    fun toggle(context: Context) = setLight(!isLight, context)
}

// ---------------------------------------------------------------- iOS palette

private val DarkBackground = Color(0xFF000000)
private val DarkGroupedBackground = Color(0xFF0B0B0C)
private val DarkCard = Color(0xFF1C1C1E)
private val DarkCardHigh = Color(0xFF2C2C2E)
private val DarkSeparator = Color(0xFF38383A)
private val DarkLabel = Color(0xFFFFFFFF)
private val DarkSecondaryLabel = Color(0x99EBEBF5)
private val DarkTertiaryLabel = Color(0x4DEBEBF5)

private val LightBackground = Color(0xFFF2F2F7)
private val LightGroupedBackground = Color(0xFFF2F2F7)
private val LightCard = Color(0xFFFFFFFF)
private val LightCardHigh = Color(0xFFE9E9EE)
private val LightSeparator = Color(0xFFC6C6C8)
private val LightLabel = Color(0xFF000000)
private val LightSecondaryLabel = Color(0x993C3C43)
private val LightTertiaryLabel = Color(0x4D3C3C43)

private fun pick(dark: Color, light: Color): Color = if (ThemeState.isLight) light else dark

val IOSBackground: Color get() = pick(DarkBackground, LightBackground)
val IOSGroupedBackground: Color get() = pick(DarkGroupedBackground, LightGroupedBackground)
val IOSCard: Color get() = pick(DarkCard, LightCard)
val IOSCardHigh: Color get() = pick(DarkCardHigh, LightCardHigh)
val IOSSeparator: Color get() = pick(DarkSeparator, LightSeparator)
val IOSLabel: Color get() = pick(DarkLabel, LightLabel)
val IOSSecondaryLabel: Color get() = pick(DarkSecondaryLabel, LightSecondaryLabel)
val IOSTertiaryLabel: Color get() = pick(DarkTertiaryLabel, LightTertiaryLabel)
val IOSBlue: Color get() = Color(0xFF0A84FF)
val IOSGreen: Color get() = pick(Color(0xFF30D158), Color(0xFF34C759))
val IOSRed: Color get() = pick(Color(0xFFFF453A), Color(0xFFFF3B30))
val IOSOrange: Color get() = pick(Color(0xFFFF9F0A), Color(0xFFFF9500))
val IOSYellow: Color get() = pick(Color(0xFFFFD60A), Color(0xFFFFCC00))
val IOSGray: Color get() = Color(0xFF8E8E93)

private fun iosColorScheme(light: Boolean) = if (!light) {
    darkColorScheme(
        primary = Color(0xFF0A84FF),
        onPrimary = Color.White,
        secondary = Color(0xFF30D158),
        onSecondary = Color.Black,
        background = DarkBackground,
        onBackground = DarkLabel,
        surface = DarkCard,
        onSurface = DarkLabel,
        surfaceVariant = DarkCardHigh,
        onSurfaceVariant = DarkSecondaryLabel,
        error = Color(0xFFFF453A),
        onError = Color.Black,
        outline = DarkSeparator,
    )
} else {
    lightColorScheme(
        primary = Color(0xFF0A84FF),
        onPrimary = Color.White,
        secondary = Color(0xFF34C759),
        onSecondary = Color.White,
        background = LightBackground,
        onBackground = LightLabel,
        surface = LightCard,
        onSurface = LightLabel,
        surfaceVariant = LightCardHigh,
        onSurfaceVariant = LightSecondaryLabel,
        error = Color(0xFFFF3B30),
        onError = Color.White,
        outline = LightSeparator,
    )
}

private val iosTypography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.1.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

private val iosShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun FrigateTheme(content: @Composable () -> Unit) {
    // Đọc state trong composition để cả cây recompose khi đổi sáng/tối.
    val light = ThemeState.isLight
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
            // Nền cửa sổ (vùng overscroll/splash) khớp theme để không lộ nền đen.
            window.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(
                    if (light) 0xFFF2F2F7.toInt() else android.graphics.Color.BLACK,
                ),
            )
        }
    }
    MaterialTheme(
        colorScheme = iosColorScheme(light),
        typography = iosTypography,
        shapes = iosShapes,
        content = content,
    )
}
