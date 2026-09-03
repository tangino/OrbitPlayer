package com.antigravity.equalizer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.antigravity.equalizer.R
import com.antigravity.equalizer.ui.theme.*

@Composable
fun SpectrumVisualizer(
    spectrumBars: FloatArray,
    peakLeftDb: Float,
    peakRightDb: Float,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceCard)
            .padding(12.dp)
    ) {
        // 顶部电平指示
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.realtime_spectrum),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary,
                letterSpacing = 1.sp
            )

            // L / R Peak Meters
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PeakMeterBar(label = "L", peakDb = peakLeftDb, isEnabled = isEnabled)
                PeakMeterBar(label = "R", peakDb = peakRightDb, isEnabled = isEnabled)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 32 柱状频谱
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            val count = spectrumBars.size
            if (count == 0) return@Canvas

            val totalW = size.width
            val totalH = size.height
            val spacing = 2.dp.toPx()
            val barW = (totalW - (count - 1) * spacing) / count

            for (i in 0 until count) {
                val magnitude = if (isEnabled) spectrumBars[i].coerceIn(0f, 1f) else 0.05f
                val barH = (magnitude * totalH).coerceAtLeast(4f)
                val x = i * (barW + spacing)
                val y = totalH - barH

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = if (isEnabled) {
                            listOf(colors.primary, colors.secondary)
                        } else {
                            listOf(colors.textSecondary.copy(alpha = 0.3f), colors.textSecondary.copy(alpha = 0.1f))
                        },
                        startY = y,
                        endY = totalH
                    ),
                    topLeft = Offset(x, y),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun PeakMeterBar(label: String, peakDb: Float, isEnabled: Boolean) {
    val colors = OrbitTheme.colors
    val normalized = if (isEnabled) ((peakDb + 60f) / 60f).coerceIn(0f, 1f) else 0f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, fontSize = 9.sp, color = colors.textSecondary, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(normalized)
                    .background(
                        if (normalized > 0.9f) colors.danger
                        else if (normalized > 0.75f) colors.tertiary
                        else colors.primary
                    )
            )
        }
    }
}
