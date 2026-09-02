package com.antigravity.equalizer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.antigravity.equalizer.R
import com.antigravity.equalizer.audio.RepeatMode
import com.antigravity.equalizer.ui.theme.*
import com.antigravity.equalizer.ui.utils.swipeToChangeSong
import com.antigravity.equalizer.ui.viewmodel.EqualizerUiState
import com.antigravity.equalizer.ui.viewmodel.MusicPlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: MusicPlayerViewModel,
    equalizerUiState: EqualizerUiState,
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val song = playbackState.currentSong

    // 播放进度拖拽控制
    var isDraggingSlider by remember { mutableStateOf(false) }
    var draggingProgress by remember { mutableFloatStateOf(0f) }

    val currentProgress = if (isDraggingSlider) draggingProgress else playbackState.progress
    val currentPosMs = if (isDraggingSlider) (draggingProgress * playbackState.durationMs).toLong() else playbackState.currentPositionMs

    // 专辑大图缩放动画 (播放时轻微呼吸放大)
    val coverScale by animateFloatAsState(
        targetValue = if (playbackState.isPlaying) 1.0f else 0.94f,
        animationSpec = tween(400),
        label = "CoverScaleAnim"
    )

    // 当前应用的均衡器配置文案
    val currentPresetName = remember(equalizerUiState.selectedPresetId, equalizerUiState.presets) {
        val preset = equalizerUiState.presets.find { it.id == equalizerUiState.selectedPresetId }
        when {
            preset != null -> preset.name
            equalizerUiState.selectedPresetId == "custom" -> "Custom (自定义)"
            else -> "Flat (标准)"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse",
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "PLAYING FROM LIBRARY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNeonCyan,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = song?.album ?: "Unknown Album",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenEqualizer) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Equalizer",
                            tint = if (equalizerUiState.isEnabled) PrimaryNeonCyan else TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .swipeToChangeSong(
                    onSwipeNext = { viewModel.playNext() },
                    onSwipePrevious = { viewModel.playPrevious() }
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. 260dp 高清圆角大封面 (支持左右滑动手势切歌)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .scale(coverScale)
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(24.dp),
                            spotColor = PrimaryNeonCyan.copy(alpha = 0.45f)
                        )
                        .clip(RoundedCornerShape(24.dp))
                        .background(SurfaceDark),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = song?.albumArtUri,
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                        label = "AlbumArtAnim"
                    ) { artUri ->
                        if (artUri != null) {
                            AsyncImage(
                                model = artUri,
                                contentDescription = song?.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = PrimaryNeonCyan,
                                modifier = Modifier.size(96.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. 歌曲与艺术家标题 (带平滑切换动效)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = song?.title ?: "No Track Selected",
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                    label = "TitleAnim"
                ) { title ->
                    Text(
                        text = title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                AnimatedContent(
                    targetState = song?.artist ?: "Unknown Artist",
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                    label = "ArtistAnim"
                ) { artist ->
                    Text(
                        text = artist,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. 进度条与时间指示 (播放条同款流光星芒光晕进度条)
            Column(modifier = Modifier.fillMaxWidth()) {
                LuminousGlowingSlider(
                    value = currentProgress,
                    onValueChange = {
                        isDraggingSlider = true
                        draggingProgress = it
                    },
                    onValueChangeFinished = {
                        isDraggingSlider = false
                        viewModel.seekTo((draggingProgress * playbackState.durationMs).toLong())
                    },
                    isPlaying = playbackState.isPlaying,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatTime(currentPosMs), fontSize = 12.sp, color = TextSecondary)
                    Text(text = formatTime(playbackState.durationMs), fontSize = 12.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. 播放控制按键栏 (Shuffle, Prev, Play/Pause, Next, Repeat)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 随机播放
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackState.isShuffleEnabled) PrimaryNeonCyan else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 上一曲
                IconButton(
                    onClick = { viewModel.playPrevious() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = TextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // 大播放/暂停圆形按键
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(PrimaryNeonCyan, AccentPurple))
                        )
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = DarkBackground,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // 下一曲
                IconButton(
                    onClick = { viewModel.playNext() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = TextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // 循环模式
                IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                    val (icon, tint) = when (playbackState.repeatMode) {
                        RepeatMode.OFF -> Icons.Default.Repeat to TextSecondary
                        RepeatMode.ALL -> Icons.Default.Repeat to PrimaryNeonCyan
                        RepeatMode.ONE -> Icons.Default.RepeatOne to PrimaryNeonCyan
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repeat",
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. 底部均衡器当前生效配置展示卡片 (Real-time Applied Equalizer Configuration)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
                    .clickable(onClick = onOpenEqualizer)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧：均衡器图标 + 状态药丸
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = null,
                        tint = if (equalizerUiState.isEnabled) PrimaryNeonCyan else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Equalizer DSP",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            // 运行状态小药丸徽章
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (equalizerUiState.isEnabled) PrimaryNeonCyan.copy(alpha = 0.2f) else SurfaceDark
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = if (equalizerUiState.isEnabled) "ACTIVE" else "BYPASS",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (equalizerUiState.isEnabled) PrimaryNeonCyan else TextSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        val preampText = if (equalizerUiState.preampGainDb >= 0) "+%.1f dB".format(equalizerUiState.preampGainDb) else "%.1f dB".format(equalizerUiState.preampGainDb)
                        Text(
                            text = "Preamp: $preampText",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                // 右侧：当前应用的预设名称 + 10 频段微缩推子指示器 + 箭头
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = currentPresetName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (equalizerUiState.isEnabled) PrimaryNeonCyan else TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        // 10 频段微缩增益分布柱 (Mini EQ Sparkline)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val gains = equalizerUiState.bandGains
                            for (i in 0 until minOf(10, gains.size)) {
                                val gain = gains[i].coerceIn(-6f, 6f)
                                // 高度按增益映射在 3dp ~ 14dp 之间
                                val barHeight = (8 + (gain / 6f) * 6).coerceIn(3f, 14f).dp
                                Box(
                                    modifier = Modifier
                                        .width(2.5.dp)
                                        .height(barHeight)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(
                                            if (equalizerUiState.isEnabled) {
                                                if (gain > 0) PrimaryNeonCyan else Color.White.copy(alpha = 0.5f)
                                            } else {
                                                TextSecondary.copy(alpha = 0.4f)
                                            }
                                        )
                                )
                            }
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Edit EQ",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

/**
 * 播放条同款流光星芒光晕进度条 (Luminous Glowing Slider)
 * 1. 轨道：流光双色渐变轨，带荧光青微光
 * 2. 滑块：同款漫反射外散大光晕、动态旋转四角星芒十字与纯白超亮星核
 * 3. 交互：全范围手势支持，单点跳转与丝滑长按拖拽
 */
@Composable
private fun LuminousGlowingSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = value.coerceIn(0f, 1f)

    // 星光高频璀璨闪烁脉冲动画（与 MiniPlayerBar 同款参数与相位）
    val infiniteTransition = rememberInfiniteTransition(label = "NowPlayingStarAnim")

    val twinkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = if (isPlaying) 1.0f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "twinkleAlpha"
    )

    val twinkleScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = if (isPlaying) 1.35f else 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "twinkleScale"
    )

    val starRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "starRotation"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    onValueChange(newProgress)
                    onValueChangeFinished()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        onValueChange(newProgress)
                    },
                    onDragEnd = {
                        onValueChangeFinished()
                    },
                    onDragCancel = {
                        onValueChangeFinished()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val newProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        onValueChange(newProgress)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val centerY = h / 2f
            val trackHeight = 4.dp.toPx()
            val thumbX = progress * w

            // 1. 底层暗调透光轨道 (Inactive Track)
            drawRoundRect(
                color = PrimaryNeonCyan.copy(alpha = 0.16f),
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = androidx.compose.ui.geometry.Size(w, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f, trackHeight / 2f)
            )

            // 2. 已播放流光高光渐变轨道 (Active Track)
            if (thumbX > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            PrimaryNeonCyan.copy(alpha = 0.75f),
                            PrimaryNeonCyan,
                            Color(0xFF80D8FF)
                        ),
                        startX = 0f,
                        endX = thumbX.coerceAtLeast(1f)
                    ),
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = androidx.compose.ui.geometry.Size(thumbX, trackHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f, trackHeight / 2f)
                )
            }

            val thumbPos = Offset(thumbX, centerY)

            // 3. 漫反射外散大光晕 (Soft Outer Halo)
            val haloRadius = 26.dp.toPx() * twinkleScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        PrimaryNeonCyan.copy(alpha = 0.8f * twinkleAlpha),
                        PrimaryNeonCyan.copy(alpha = 0.28f * twinkleAlpha),
                        Color.Transparent
                    ),
                    center = thumbPos,
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = thumbPos
            )

            // 4. 旋转四角星芒十字光辉 (Rotating Cross Sparkle)
            rotate(degrees = starRotation, pivot = thumbPos) {
                val rayLength = 12.dp.toPx() * twinkleScale
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
                        startX = thumbPos.x - rayLength,
                        endX = thumbPos.x + rayLength
                    ),
                    start = Offset(thumbPos.x - rayLength, thumbPos.y),
                    end = Offset(thumbPos.x + rayLength, thumbPos.y),
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
                        startY = thumbPos.y - rayLength,
                        endY = thumbPos.y + rayLength
                    ),
                    start = Offset(thumbPos.x, thumbPos.y - rayLength),
                    end = Offset(thumbPos.x, thumbPos.y + rayLength),
                    strokeWidth = rayWidth,
                    cap = StrokeCap.Round
                )
            }

            // 5. 纯白钻石超亮星核 (Diamond Core)
            drawCircle(
                color = Color.White.copy(alpha = 0.98f * twinkleAlpha),
                radius = 4.2.dp.toPx() * twinkleScale.coerceAtLeast(0.85f),
                center = thumbPos
            )

            // 6. 外圈高对比度微光环 (Core Glow Rim)
            drawCircle(
                color = PrimaryNeonCyan.copy(alpha = 0.95f * twinkleAlpha),
                radius = 6.2.dp.toPx() * twinkleScale,
                center = thumbPos,
                style = Stroke(width = 1.4.dp.toPx())
            )
        }
    }
}
