package com.antigravity.equalizer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.antigravity.equalizer.audio.PlaybackState
import com.antigravity.equalizer.ui.theme.*
import com.antigravity.equalizer.ui.utils.swipeToChangeSong

/**
 * 极具视觉冲击力的 Hi-Fi 悬浮音乐播放 Dock (Pro Neon Floating Music Bar)
 * 1. 动态呼吸荧光光晕与双层拟物高光金属切边，彻底告别低对比度沉底感
 * 2. 环绕星光动效 (Orbiting Starlight Glow)：根据实际音频进度，从左上角起沿四周顺时针移动一圈回到左上角
 * 3. 律动微型音频跳动柱 (Mini Audio Waves)，实时反馈播放动态
 * 4. 完整三键立体控制台（上一曲、高光宝石播放大键、下一曲）
 * 5. 高亮流光渐变进度条，支持全屏展开与左右滑动手势切歌
 */
@Composable
fun MiniPlayerBar(
    playbackState: PlaybackState,
    onTogglePlay: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song = playbackState.currentSong ?: return

    val dockShape = RoundedCornerShape(18.dp)

    // 动态呼吸光晕动画（播放时更强烈的深青色外扩微光）
    val infiniteTransition = rememberInfiniteTransition(label = "DockGlowAnim")
    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (playbackState.isPlaying) 0.75f else 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowIntensity"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)
    ) {
        // ========== 1. 底层核心 Dock 交互卡片 ==========
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 增强的立体悬浮呼吸光晕阴影
                .shadow(
                    elevation = if (playbackState.isPlaying) 18.dp else 12.dp,
                    shape = dockShape,
                    spotColor = PrimaryNeonCyan.copy(alpha = glowIntensity),
                    ambientColor = Color.Black.copy(alpha = 0.65f)
                )
                .clip(dockShape)
                // 高保真深邃双层渐变底色（上层微透夜空蓝灰，下层坚实黑曜石黑）
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xF51E2433),
                            Color(0xFA121622),
                            Color(0xFF0C0F17)
                        )
                    )
                )
                // 边缘科技感双层高光渐变描边 (Top Highlight Neon Border)
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            PrimaryNeonCyan.copy(alpha = if (playbackState.isPlaying) 0.65f else 0.4f),
                            Color(0x4400E5FF),
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    ),
                    shape = dockShape
                )
                // 左右滑动手势切歌
                .swipeToChangeSong(
                    onSwipeNext = onPlayNext,
                    onSwipePrevious = onPlayPrevious
                )
                .clickable(onClick = onClick)
                .padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 9.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：大圆角专辑封面 (48dp x 48dp) + 律动阴影
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(12.dp),
                                spotColor = PrimaryNeonCyan.copy(alpha = 0.3f)
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceDark)
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    listOf(PrimaryNeonCyan.copy(alpha = 0.3f), Color.Transparent)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (song.albumArtUri != null) {
                            AsyncImage(
                                model = song.albumArtUri,
                                contentDescription = song.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = PrimaryNeonCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 中间：歌曲标题、艺术家与动态音频频谱跳动波形
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AnimatedContent(
                                targetState = song.title,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "DockTitleAnim",
                                modifier = Modifier.weight(1f, fill = false)
                            ) { title ->
                                Text(
                                    text = title,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF8FAFC),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // 实时跳动的微型音频波形柱
                            MiniAudioWaves(isPlaying = playbackState.isPlaying)
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        AnimatedContent(
                            targetState = "${song.artist} • ${song.album}",
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "DockArtistAnim"
                        ) { artistAlbum ->
                            Text(
                                text = artistAlbum,
                                fontSize = 11.5.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 右侧：立体三键控制台（上一首 + 荧光青立体圆形播放宝石 + 下一首）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // 上一曲按键
                        IconButton(
                            onClick = onPlayPrevious,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = TextPrimary.copy(alpha = 0.85f),
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        // 核心播放/暂停大按键（立体发光青色宝石形态）
                        IconButton(
                            onClick = onTogglePlay,
                            modifier = Modifier
                                .size(42.dp)
                                .shadow(
                                    elevation = 8.dp,
                                    shape = CircleShape,
                                    spotColor = PrimaryNeonCyan,
                                    ambientColor = PrimaryNeonCyan.copy(alpha = 0.5f)
                                )
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            PrimaryNeonCyan,
                                            Color(0xFF00B0FF),
                                            Color(0xFF0091EA)
                                        )
                                    )
                                )
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                tint = Color(0xFF070B14),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // 下一曲按键
                        IconButton(
                            onClick = onPlayNext,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = TextPrimary.copy(alpha = 0.85f),
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 底部：高亮流光渐变进度条 (3.5dp)
                val currentProgress = playbackState.progress.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(PrimaryNeonCyan.copy(alpha = 0.16f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(currentProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        PrimaryNeonCyan.copy(alpha = 0.75f),
                                        PrimaryNeonCyan,
                                        Color(0xFF80D8FF)
                                    )
                                )
                            )
                    )
                }
            }
        }

        // ========== 2. 上层覆盖：环绕播放条四周移动的璀璨星光与光晕动效 ==========
        OrbitingStarlightOverlay(
            progress = playbackState.progress,
            isPlaying = playbackState.isPlaying,
            cornerRadius = 18.dp,
            modifier = Modifier.matchParentSize()
        )
    }
}

/**
 * 环绕播放条四周移动的璀璨星光 (Orbiting Starlight Effect)
 * 1. 轨迹：从左上角出发，根据音频播放进度沿四周顺时针运动，一圈后回到左上角；
 * 2. 视觉：流光拖尾、闪烁呼吸大光晕、动态旋转四角星芒十字与纯白超亮星核。
 */
@Composable
private fun OrbitingStarlightOverlay(
    progress: Float,
    isPlaying: Boolean,
    cornerRadius: Dp,
    modifier: Modifier = Modifier
) {
    // 平滑插值当前进度，确保星光平稳顺滑滑移，避免生硬跳步
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "StarProgress"
    )

    // 星光高频璀璨闪烁脉冲动画
    val infiniteTransition = rememberInfiniteTransition(label = "StarSparkleAnim")

    val twinkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = if (isPlaying) 1.0f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkleAlpha"
    )

    val twinkleScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = if (isPlaying) 1.3f else 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkleScale"
    )

    val starRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "starRotation"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val r = cornerRadius.toPx().coerceAtMost(minOf(w, h) / 2f)

        // 构造从左上角起点顺时针绕行一周的圆角矩形 Path
        val orbitPath = Path().apply {
            // 起点：左上角圆角终点 / 顶边最左侧 (r, 0)
            moveTo(r, 0f)
            // 顶边向右
            lineTo(w - r, 0f)
            // 右上角圆角
            arcTo(
                rect = Rect(w - 2 * r, 0f, w, 2 * r),
                startAngleDegrees = 270f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            // 右侧边向下
            lineTo(w, h - r)
            // 右下角圆角
            arcTo(
                rect = Rect(w - 2 * r, h - 2 * r, w, h),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            // 底边向左
            lineTo(r, h)
            // 左下角圆角
            arcTo(
                rect = Rect(0f, h - 2 * r, 2 * r, h),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            // 左侧边向上
            lineTo(0f, r)
            // 左上角圆角，回到 (r, 0)
            arcTo(
                rect = Rect(0f, 0f, 2 * r, 2 * r),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            close()
        }

        val pathMeasure = PathMeasure().apply {
            setPath(orbitPath, true)
        }

        val totalLength = pathMeasure.length
        if (totalLength <= 0f) return@Canvas

        val currentDist = (animatedProgress * totalLength).coerceIn(0f, totalLength)
        val pos = pathMeasure.getPosition(currentDist)

        // 1. 绘制星光游走时的渐变彗星流光拖尾 (Comet Stardust Trail)
        val tailLength = (68.dp.toPx()).coerceAtMost(totalLength * 0.22f)
        if (tailLength > 0f && currentDist > 0f) {
            val tailStart = currentDist - tailLength
            if (tailStart >= 0f) {
                val tailPath = Path()
                pathMeasure.getSegment(tailStart, currentDist, tailPath, true)
                drawPath(
                    path = tailPath,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PrimaryNeonCyan.copy(alpha = 0.95f * twinkleAlpha),
                            PrimaryNeonCyan.copy(alpha = 0.4f * twinkleAlpha),
                            Color.Transparent
                        ),
                        center = pos,
                        radius = tailLength
                    ),
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                )
            } else {
                // 跨越 0 点起点的环绕衔接拖尾
                val tailPath1 = Path()
                pathMeasure.getSegment(totalLength + tailStart, totalLength, tailPath1, true)
                drawPath(
                    path = tailPath1,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PrimaryNeonCyan.copy(alpha = 0.95f * twinkleAlpha),
                            PrimaryNeonCyan.copy(alpha = 0.4f * twinkleAlpha),
                            Color.Transparent
                        ),
                        center = pos,
                        radius = tailLength
                    ),
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                )

                val tailPath2 = Path()
                pathMeasure.getSegment(0f, currentDist, tailPath2, true)
                drawPath(
                    path = tailPath2,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PrimaryNeonCyan.copy(alpha = 0.95f * twinkleAlpha),
                            PrimaryNeonCyan.copy(alpha = 0.4f * twinkleAlpha),
                            Color.Transparent
                        ),
                        center = pos,
                        radius = tailLength
                    ),
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // 2. 绘制星光柔和外散发光大光晕 (Soft Outer Halo)
        val haloRadius = 26.dp.toPx() * twinkleScale
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    PrimaryNeonCyan.copy(alpha = 0.8f * twinkleAlpha),
                    PrimaryNeonCyan.copy(alpha = 0.28f * twinkleAlpha),
                    Color.Transparent
                ),
                center = pos,
                radius = haloRadius
            ),
            radius = haloRadius,
            center = pos
        )

        // 3. 绘制旋转四角星芒十字光辉 (Rotating Cross Sparkle)
        rotate(degrees = starRotation, pivot = pos) {
            val rayLength = 11.dp.toPx() * twinkleScale
            val rayWidth = 1.8.dp.toPx()

            // 水平星芒
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.95f * twinkleAlpha),
                        PrimaryNeonCyan,
                        Color.White.copy(alpha = 0.95f * twinkleAlpha),
                        Color.Transparent
                    ),
                    startX = pos.x - rayLength,
                    endX = pos.x + rayLength
                ),
                start = Offset(pos.x - rayLength, pos.y),
                end = Offset(pos.x + rayLength, pos.y),
                strokeWidth = rayWidth,
                cap = StrokeCap.Round
            )

            // 垂直星芒
            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.95f * twinkleAlpha),
                        PrimaryNeonCyan,
                        Color.White.copy(alpha = 0.95f * twinkleAlpha),
                        Color.Transparent
                    ),
                    startY = pos.y - rayLength,
                    endY = pos.y + rayLength
                ),
                start = Offset(pos.x, pos.y - rayLength),
                end = Offset(pos.x, pos.y + rayLength),
                strokeWidth = rayWidth,
                cap = StrokeCap.Round
            )
        }

        // 4. 绘制纯白钻石超亮星核 (Bright Diamond Core)
        drawCircle(
            color = Color.White.copy(alpha = 0.98f * twinkleAlpha),
            radius = 3.8.dp.toPx() * twinkleScale.coerceAtLeast(0.85f),
            center = pos
        )

        // 5. 紧凑外圈高对比度微光环 (Core Glow Rim)
        drawCircle(
            color = PrimaryNeonCyan.copy(alpha = 0.95f * twinkleAlpha),
            radius = 5.5.dp.toPx() * twinkleScale,
            center = pos,
            style = Stroke(width = 1.2.dp.toPx())
        )
    }
}

/**
 * 微型音频跳动柱指示器：实时呈现音波跳跃动画
 */
@Composable
private fun MiniAudioWaves(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WavesTransition")

    val h1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (isPlaying) 1.0f else 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave1"
    )

    val h2 by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = if (isPlaying) 0.25f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave2"
    )

    val h3 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (isPlaying) 0.95f else 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave3"
    )

    Row(
        modifier = modifier.height(13.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(h1, h2, h3).forEach { waveHeight ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .fillMaxHeight(waveHeight)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (isPlaying) PrimaryNeonCyan else TextSecondary.copy(alpha = 0.4f)
                    )
            )
        }
    }
}
