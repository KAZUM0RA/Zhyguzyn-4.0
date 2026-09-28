package com.kazum0ra.zhyguzyn.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kazum0ra.zhyguzyn.R
import com.kazum0ra.zhyguzyn.ui.theme.FuelColors

/** Індикатор бака: горизонтальна шкала з поділками 1/4, 1/2, 3/4. */
@Composable
fun FuelGauge(fraction: Float?, modifier: Modifier = Modifier) {
    val target = (fraction ?: 0f).coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = target, label = "fuel")
    val fillColor = when {
        fraction == null -> Color.Transparent
        target < 0.15f -> FuelColors.Low
        target < 0.30f -> FuelColors.Medium
        else -> MaterialTheme.colorScheme.primary
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val percent = fraction?.let { "${(target * 100).toInt()}%" } ?: "?"
    val description = stringResource(R.string.gauge_description, percent)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .semantics { contentDescription = description },
    ) {
        val radius = CornerRadius(12.dp.toPx())
        drawRoundRect(color = trackColor, cornerRadius = radius)
        if (animated > 0f) {
            drawRoundRect(
                color = fillColor,
                size = Size(size.width * animated, size.height),
                cornerRadius = radius,
            )
        }
        for (i in 1..3) {
            val x = size.width * i / 4f
            val tick = if (i == 2) size.height * 0.45f else size.height * 0.3f
            drawLine(outline, Offset(x, 0f), Offset(x, tick), strokeWidth = 2.dp.toPx())
            drawLine(outline, Offset(x, size.height - tick), Offset(x, size.height), strokeWidth = 2.dp.toPx())
        }
        drawRoundRect(color = outline, cornerRadius = radius, style = Stroke(width = 1.5.dp.toPx()))
    }
}
