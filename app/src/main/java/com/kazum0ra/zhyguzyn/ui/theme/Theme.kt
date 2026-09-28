package com.kazum0ra.zhyguzyn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Палітра темного інтерфейсу. */
object AppColors {
    val Background = Color(0xFF0E1116)
    val Card = Color(0xFF161B22)
    val CardBorder = Color(0xFF2A313B)
    val Track = Color(0xFF232A33)
    val Button = Color(0xFF1E252E)
    val Green = Color(0xFF57B881)
    val Blue = Color(0xFF4C9AFF)
    val TextPrimary = Color(0xFFE8ECF1)
    val TextSecondary = Color(0xFF8B949E)
}

private val DarkColors = darkColorScheme(
    primary = AppColors.Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1F3D2C),
    onPrimaryContainer = Color(0xFFB9EBCB),
    secondary = AppColors.Blue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1C2A3D),
    onSecondaryContainer = Color(0xFFD3E4FF),
    tertiary = AppColors.Blue,
    background = AppColors.Background,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Background,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.Track,
    onSurfaceVariant = AppColors.TextSecondary,
    surfaceContainerLowest = AppColors.Background,
    surfaceContainerLow = AppColors.Card,
    surfaceContainer = AppColors.Card,
    surfaceContainerHigh = Color(0xFF1C222B),
    surfaceContainerHighest = Color(0xFF232A33),
    outline = Color(0xFF3A424D),
    outlineVariant = AppColors.CardBorder,
    error = Color(0xFFF85149),
    onError = Color.White,
)

/** Кольори рівня пального в баку. */
object FuelColors {
    val Low = Color(0xFFF85149)
    val Medium = Color(0xFFE3B341)
}

/** Інтерфейс завжди темний. */
@Composable
fun ZhyguzynTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
