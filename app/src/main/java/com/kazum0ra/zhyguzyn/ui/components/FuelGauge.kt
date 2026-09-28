package com.kazum0ra.zhyguzyn.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kazum0ra.zhyguzyn.R
import com.kazum0ra.zhyguzyn.ui.theme.AppColors
import com.kazum0ra.zhyguzyn.ui.theme.FuelColors

/** Індикатор бака: заокруглена смуга; колір змінюється, коли пального мало. */
@Composable
fun FuelGauge(fraction: Float?, modifier: Modifier = Modifier) {
    val target = (fraction ?: 0f).coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = target, label = "fuel")
    val fillColor = when {
        target < 0.15f -> FuelColors.Low
        target < 0.30f -> FuelColors.Medium
        else -> AppColors.Green
    }
    val percent = fraction?.let { "${(target * 100).toInt()}%" } ?: "?"
    val description = stringResource(R.string.gauge_description, percent)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .semantics { contentDescription = description },
    ) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color = AppColors.Track, cornerRadius = radius)
        if (fraction != null && animated > 0f) {
            val width = (size.width * animated).coerceAtLeast(size.height)
            drawRoundRect(color = fillColor, size = Size(width, size.height), cornerRadius = radius)
        }
    }
}
