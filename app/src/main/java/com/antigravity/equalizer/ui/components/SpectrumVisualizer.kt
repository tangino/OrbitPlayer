package com.antigravity.equalizer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import com.antigravity.equalizer.R
import com.antigravity.equalizer.data.model.VisualizerColorScheme
import com.antigravity.equalizer.data.model.VisualizerStyle
import com.antigravity.equalizer.ui.theme.*
import kotlin.math.exp
import kotlinx.coroutines.isActive

/**
 * 殿堂级 Poweramp 风格音频动态频谱渲染组件
 * 内置基于硬件垂直同步 (V-Sync withFrameNanos) 的 60Hz/120Hz 满帧物理插值引擎
 *
 * 支持三种经典形态：
 * 1. 经典柱状 + 悬浮缓降峰值 (Bars with Peak Caps)
 * 2. 平滑极光山脉曲线 (Smooth Aurora Mountain Wave)
 * 3. 镜面对称律动蝶形频谱 (Mirrored Butterfly Bars)
 */
@Composable
fun PowerampSpectrumVisualizer(
    magnitudes: FloatArray,
    @Suppress("UNUSED_PARAMETER") peaks: FloatArray = FloatArray(32),
    style: VisualizerStyle,
    colorScheme: VisualizerColorScheme,
    peakDecayEnabled: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barWidthDp: Float = 5.0f,
    barAlpha: Float = 1.0f,
    borderWidthDp: Float = 0.0f,
    borderColor: Long = 0xFFFFFFFFL,
    borderAlpha: Float = 0.8f,
    borderOnly: Boolean = false,
    customColor: Long = 0xFF00E5FFL,
    customColor2: Long = 0xFF7C4DFFL,
    isSingleColor: Boolean = false,
    backgroundLightColor: Long? = null,
    backgroundDarkColor: Long? = null,
    onClick: (() -> Unit)? = null
) {
    if (style == VisualizerStyle.OFF) return

    val themeColors = OrbitTheme.colors
    val (primaryColor, secondaryColor, peakColor) = when (colorScheme) {
        VisualizerColorScheme.FOLLOW_BACKGROUND -> {
            val light = backgroundLightColor?.let { Color(it) } ?: themeColors.primary
            val dark = backgroundDarkColor?.let { Color(it) } ?: themeColors.secondary
            val refColor = if (isSingleColor) light else dark
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(refColor.toArgb(), hsv)
            val peakHsv = floatArrayOf(hsv[0], (hsv[1] * 0.20f).coerceIn(0f, 0.35f), 1.0f)
            val peakCol = Color(android.graphics.Color.HSVToColor(peakHsv))
            Triple(light, if (isSingleColor) light else dark, peakCol)
        }
        VisualizerColorScheme.CUSTOM -> {
            val base = Color(customColor)
            val sec = if (isSingleColor) base else Color(customColor2)
            val refColor = if (isSingleColor) customColor else customColor2
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV((refColor and 0xFFFFFFFFL).toInt(), hsv)
            val peakHsv = floatArrayOf(hsv[0], (hsv[1] * 0.20f).coerceIn(0f, 0.35f), 1.0f)
            val peakCol = Color(android.graphics.Color.HSVToColor(peakHsv))
            Triple(base, sec, peakCol)
        }
        else -> {
            Triple(
                colorScheme.primaryColor,
                if (isSingleColor) colorScheme.primaryColor else colorScheme.secondaryColor,
                colorScheme.peakColor
            )
        }
    }

    // 预分配大容量缓冲区，避免协程动态扩容
    val maxBars = 128
    val displayBars = remember { FloatArray(maxBars) }
    val displayPeaks = remember { FloatArray(maxBars) }
    val peakVelocities = remember { FloatArray(maxBars) }
    val peakHoldTimes = remember { FloatArray(maxBars) }

    // 使用 rememberUpdatedState 保证 V-Sync 循环永远读取到最新传入的 FFT 数据和播放状态，杜绝闭包停滞
    val latestMagnitudes by rememberUpdatedState(magnitudes)
    val latestPeaks by rememberUpdatedState(peaks)
    val latestIsPlaying by rememberUpdatedState(isPlaying)

    // 每一帧硬件垂直同步触发器 (60fps / 90fps / 120fps 满帧高刷新率)
    var animationTick by remember { mutableLongStateOf(0L) }

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else {
        Modifier
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        // 根据可用宽度和用户设定的单条宽度动态反推条数：条越窄，绘制得越密集细腻；条越宽，越沉稳少条
        val spacingDp = (barWidthDp * 0.35f).coerceIn(1.2f, 4.0f)
        val barStepDp = barWidthDp + spacingDp
        val availableWidthDp = maxWidth.value
        val dynamicCount = if (availableWidthDp > 0f) {
            ((availableWidthDp + spacingDp) / barStepDp).toInt().coerceIn(16, 128)
        } else {
            32
        }

        val currentDynamicCount by rememberUpdatedState(dynamicCount)

        // Key 为 Unit 确保协程伴随组件生命周期长久保活，切歌与暂停时不中断退出
        LaunchedEffect(Unit) {
            var lastTime = withFrameNanos { it }

            while (isActive) {
                val now = withFrameNanos { it }
                val dt = ((now - lastTime) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastTime = now

                val playing = latestIsPlaying
                val rawMags = latestMagnitudes
                val rawPeaks = latestPeaks
                val activeCount = currentDynamicCount

                // 物理平滑追踪：Attack 极速弹起爆发 (36f)，Decay 优雅阻尼缓降 (14f)
                val attackFactor = (1f - exp(-36f * dt)).coerceIn(0f, 1f)
                val decayFactor = (1f - exp(-14f * dt)).coerceIn(0f, 1f)

                for (i in 0 until activeCount) {
                    val targetMag = if (playing && rawMags.isNotEmpty()) {
                        val pos = i.toFloat() / (activeCount - 1).coerceAtLeast(1) * (rawMags.size - 1)
                        val i0 = pos.toInt().coerceIn(0, rawMags.size - 1)
                        val i1 = (i0 + 1).coerceIn(0, rawMags.size - 1)
                        val frac = pos - i0
                        (rawMags[i0] * (1f - frac) + rawMags[i1] * frac).coerceIn(0f, 1f)
                    } else 0f

                    if (targetMag > displayBars[i]) {
                        displayBars[i] += (targetMag - displayBars[i]) * attackFactor
                    } else {
                        displayBars[i] += (targetMag - displayBars[i]) * decayFactor
                    }

                    // 仿 Poweramp 悬浮顶峰缓降物理 (Peak Cap Hold & Gravity Falloff)
                    val targetPeak = if (playing && rawPeaks.isNotEmpty()) {
                        val pos = i.toFloat() / (activeCount - 1).coerceAtLeast(1) * (rawPeaks.size - 1)
                        val i0 = pos.toInt().coerceIn(0, rawPeaks.size - 1)
                        val i1 = (i0 + 1).coerceIn(0, rawPeaks.size - 1)
                        val frac = pos - i0
                        (rawPeaks[i0] * (1f - frac) + rawPeaks[i1] * frac).coerceIn(0f, 1f)
                    } else 0f
                    val currentBar = displayBars[i]

                    if (currentBar >= displayPeaks[i] || targetPeak > displayPeaks[i]) {
                        displayPeaks[i] = maxOf(currentBar, targetPeak)
                        peakVelocities[i] = 0f
                        peakHoldTimes[i] = 0.12f // 顶峰滞留 120ms
                    } else {
                        if (peakHoldTimes[i] > 0f) {
                            peakHoldTimes[i] -= dt
                        } else {
                            peakVelocities[i] += 2.4f * dt // 自由落体重力加速度
                            displayPeaks[i] = (displayPeaks[i] - peakVelocities[i] * dt).coerceAtLeast(currentBar)
                        }
                    }
                }

                animationTick = now
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .graphicsLayer {
                    alpha = barAlpha.coerceIn(0.1f, 1.0f)
                }
        ) {
            // 依赖 animationTick 保证在 60Hz/90Hz/120Hz 硬件垂直同步下满帧流畅绘制
            if (animationTick < 0) return@Canvas
            val count = dynamicCount
            if (count == 0) return@Canvas
            val totalW = size.width
            val totalH = size.height

            when (style) {
                VisualizerStyle.BARS_WITH_PEAKS -> {
                    // ========== 1. 经典柱状 + 悬浮顶峰缓降 (根据用户设定宽度绘制) ==========
                    val spacing = spacingDp.dp.toPx()
                    val barW = ((totalW - (count - 1) * spacing) / count).coerceAtLeast(1.0f)
                    val capHeight = 2.2.dp.toPx()
                    val corner = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                    val strokeW = if (borderOnly && borderWidthDp < 0.2f) 1.4.dp.toPx() else borderWidthDp.dp.toPx()
                    val hasBorder = (strokeW > 0.05f && borderAlpha > 0.01f) || borderOnly
                    val borderStrokeColor = if (hasBorder) Color(borderColor).copy(alpha = borderAlpha.coerceIn(0f, 1f)) else Color.Transparent

                    for (i in 0 until count) {
                        val mag = displayBars[i]
                        val peak = displayPeaks[i]

                        val barH = (mag * (totalH - capHeight - 4.dp.toPx())).coerceAtLeast(2.5f)
                        val x = i * (barW + spacing)
                        val y = totalH - barH

                        // 绘制单色纯色柱体 / 渐变发光柱体 (仅在未开启仅边框模式时填充)
                        if (!borderOnly) {
                            if (isSingleColor) {
                                drawRoundRect(
                                    color = primaryColor,
                                    topLeft = Offset(x, y),
                                    size = Size(barW, barH),
                                    cornerRadius = corner
                                )
                            } else {
                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            secondaryColor,
                                            primaryColor.copy(alpha = 0.95f),
                                            primaryColor
                                        ),
                                        startY = y,
                                        endY = totalH
                                    ),
                                    topLeft = Offset(x, y),
                                    size = Size(barW, barH),
                                    cornerRadius = corner
                                )
                            }
                        }

                        // 绘制柱体精致圆角轮廓边框 (内缩 halfStroke 避免柱体间像素粘连)
                        if (hasBorder) {
                            val halfStroke = strokeW / 2f
                            if (barW > strokeW && barH > strokeW) {
                                drawRoundRect(
                                    color = borderStrokeColor,
                                    topLeft = Offset(x + halfStroke, y + halfStroke),
                                    size = Size(barW - strokeW, barH - strokeW),
                                    cornerRadius = CornerRadius(
                                        (corner.x - halfStroke).coerceAtLeast(0f),
                                        (corner.y - halfStroke).coerceAtLeast(0f)
                                    ),
                                    style = Stroke(width = strokeW)
                                )
                            } else {
                                drawRoundRect(
                                    color = borderStrokeColor,
                                    topLeft = Offset(x, y),
                                    size = Size(barW, barH),
                                    cornerRadius = corner,
                                    style = Stroke(width = strokeW)
                                )
                            }
                        }

                        // 绘制悬浮缓降峰值点 (Peak Cap)
                        if (peakDecayEnabled && peak > 0.04f) {
                            val peakH = (peak * (totalH - capHeight - 4.dp.toPx())).coerceAtLeast(barH)
                            val peakY = (totalH - peakH - capHeight - 1.5.dp.toPx()).coerceAtLeast(0f)

                            drawRoundRect(
                                color = peakColor,
                                topLeft = Offset(x, peakY),
                                size = Size(barW, capHeight),
                                cornerRadius = corner
                            )
                        }
                    }
                }

                VisualizerStyle.AURORA_MOUNTAIN -> {
                    // ========== 2. 平滑极光山脉波形 (三次贝塞尔曲线拟合) ==========
                    if (count < 2) return@Canvas

                    val stepX = totalW / (count - 1)
                    val path = Path()
                    val fillPath = Path()

                    val points = Array(count) { i ->
                        val mag = displayBars[i]
                        val y = totalH - (mag * (totalH - 8.dp.toPx())).coerceAtLeast(3f)
                        Offset(i * stepX, y)
                    }

                    path.moveTo(points[0].x, points[0].y)
                    fillPath.moveTo(0f, totalH)
                    fillPath.lineTo(points[0].x, points[0].y)

                    for (i in 0 until count - 1) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        val cx = (p0.x + p1.x) / 2f
                        path.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                        fillPath.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                    }

                    fillPath.lineTo(totalW, totalH)
                    fillPath.close()

                    // 1. 底部半透明垂直渐变填充
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                secondaryColor.copy(alpha = 0.50f),
                                primaryColor.copy(alpha = 0.20f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = totalH
                        )
                    )

                    // 2. 顶部平滑流光描边光带
                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(
                            colors = listOf(primaryColor, peakColor, secondaryColor)
                        ),
                        style = Stroke(
                            width = 2.6.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )

                    // 3. 极值浮标星点 (Peak Points)
                    if (peakDecayEnabled) {
                        for (i in 0 until count step 2) {
                            val peak = displayPeaks[i]
                            if (peak > 0.05f) {
                                val peakY = (totalH - (peak * (totalH - 8.dp.toPx())) - 4.dp.toPx()).coerceAtLeast(2f)
                                drawCircle(
                                    color = peakColor,
                                    radius = 2.0.dp.toPx(),
                                    center = Offset(points[i].x, peakY)
                                )
                            }
                        }
                    }
                }

                VisualizerStyle.MIRRORED_BARS -> {
                    // ========== 3. 镜面对称律动蝶形频谱 ==========
                    val spacing = 2.5.dp.toPx()
                    val barW = ((totalW - (count - 1) * spacing) / count).coerceAtLeast(1.5f)
                    val centerY = totalH / 2f
                    val capHeight = 2.0.dp.toPx()
                    val corner = CornerRadius(1.2.dp.toPx(), 1.2.dp.toPx())
                    val strokeW = if (borderOnly && borderWidthDp < 0.2f) 1.4.dp.toPx() else borderWidthDp.dp.toPx()
                    val hasBorder = (strokeW > 0.05f && borderAlpha > 0.01f) || borderOnly
                    val borderStrokeColor = if (hasBorder) Color(borderColor).copy(alpha = borderAlpha.coerceIn(0f, 1f)) else Color.Transparent

                    for (i in 0 until count) {
                        val mag = displayBars[i]
                        val peak = displayPeaks[i]

                        val halfH = (mag * (centerY - capHeight - 3.dp.toPx())).coerceAtLeast(2f)
                        val x = i * (barW + spacing)
                        val fullH = halfH * 2f
                        val topY = centerY - halfH

                        // 上下对称发光柱体 (仅在未开启仅边框模式时填充)
                        if (!borderOnly) {
                            if (isSingleColor) {
                                drawRoundRect(
                                    color = primaryColor,
                                    topLeft = Offset(x, topY),
                                    size = Size(barW, fullH),
                                    cornerRadius = corner
                                )
                            } else {
                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(secondaryColor, primaryColor, secondaryColor),
                                        startY = topY,
                                        endY = centerY + halfH
                                    ),
                                    topLeft = Offset(x, topY),
                                    size = Size(barW, fullH),
                                    cornerRadius = corner
                                )
                            }
                        }

                        // 绘制对称柱体精致轮廓边框
                        if (hasBorder) {
                            val halfStroke = strokeW / 2f
                            if (barW > strokeW && fullH > strokeW) {
                                drawRoundRect(
                                    color = borderStrokeColor,
                                    topLeft = Offset(x + halfStroke, topY + halfStroke),
                                    size = Size(barW - strokeW, fullH - strokeW),
                                    cornerRadius = CornerRadius(
                                        (corner.x - halfStroke).coerceAtLeast(0f),
                                        (corner.y - halfStroke).coerceAtLeast(0f)
                                    ),
                                    style = Stroke(width = strokeW)
                                )
                            } else {
                                drawRoundRect(
                                    color = borderStrokeColor,
                                    topLeft = Offset(x, topY),
                                    size = Size(barW, fullH),
                                    cornerRadius = corner,
                                    style = Stroke(width = strokeW)
                                )
                            }
                        }

                        // 双向悬浮缓降峰值点
                        if (peakDecayEnabled && peak > 0.05f) {
                            val halfPeak = (peak * (centerY - capHeight - 3.dp.toPx())).coerceAtLeast(halfH)

                            // 上峰值
                            drawRoundRect(
                                color = peakColor,
                                topLeft = Offset(x, (centerY - halfPeak - capHeight - 1.5.dp.toPx()).coerceAtLeast(0f)),
                                size = Size(barW, capHeight),
                                cornerRadius = corner
                            )
                            // 下峰值
                            drawRoundRect(
                                color = peakColor,
                                topLeft = Offset(x, (centerY + halfPeak + 1.5.dp.toPx()).coerceAtMost(totalH - capHeight)),
                                size = Size(barW, capHeight),
                                cornerRadius = corner
                            )
                        }
                    }
                }

                VisualizerStyle.OFF -> {}
            }
        }
    }
}

/**
 * 经典均衡器页面实时电平与 32 柱状频谱卡片 (兼容保留)
 */
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
