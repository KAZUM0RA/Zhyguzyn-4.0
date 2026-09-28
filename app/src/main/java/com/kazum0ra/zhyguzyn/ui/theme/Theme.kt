package com.kazum0ra.zhyguzyn.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6D3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA4F4B4),
    onPrimaryContainer = Color(0xFF00210C),
    secondary = Color(0xFF4F6353),
    tertiary = Color(0xFF3A646F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF88D79A),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF005228),
    onPrimaryContainer = Color(0xFFA4F4B4),
    secondary = Color(0xFFB6CCB8),
    tertiary = Color(0xFFA2CDDA),
)

/** Кольори рівня пального в баку. */
object FuelColors {
    val Low = Color(0xFFD32F2F)
    val Medium = Color(0xFFF9A825)
}

@Composable
fun ZhyguzynTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
