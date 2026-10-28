package com.minhquanghp86.frigatenvr.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------- iOS palette

val IOSBackground = Color(0xFF000000)
val IOSGroupedBackground = Color(0xFF0B0B0C)
val IOSCard = Color(0xFF1C1C1E)
val IOSCardHigh = Color(0xFF2C2C2E)
val IOSSeparator = Color(0xFF38383A)
val IOSLabel = Color(0xFFFFFFFF)
val IOSSecondaryLabel = Color(0x99EBEBF5)
val IOSTertiaryLabel = Color(0x4DEBEBF5)
val IOSBlue = Color(0xFF0A84FF)
val IOSGreen = Color(0xFF30D158)
val IOSRed = Color(0xFFFF453A)
val IOSOrange = Color(0xFFFF9F0A)
val IOSYellow = Color(0xFFFFD60A)
val IOSGray = Color(0xFF8E8E93)

private val iosColors = darkColorScheme(
    primary = IOSBlue,
    onPrimary = Color.White,
    secondary = IOSGreen,
    onSecondary = Color.Black,
    background = IOSBackground,
    onBackground = IOSLabel,
    surface = IOSCard,
    onSurface = IOSLabel,
    surfaceVariant = IOSCardHigh,
    onSurfaceVariant = IOSSecondaryLabel,
    error = IOSRed,
    onError = Color.Black,
    outline = IOSSeparator,
)

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
    MaterialTheme(
        colorScheme = iosColors,
        typography = iosTypography,
        shapes = iosShapes,
        content = content,
    )
}
