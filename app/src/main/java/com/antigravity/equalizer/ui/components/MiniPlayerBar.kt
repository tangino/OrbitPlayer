package com.antigravity.equalizer.ui.components

import android.content.res.Configuration
import android.graphics.Paint as FrameworkPaint
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.antigravity.equalizer.audio.PlaybackState
import com.antigravity.equalizer.data.provider.AudioCoverProvider
import com.antigravity.equalizer.ui.theme.*
import com.antigravity.equalizer.utils.CoverHelper
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 歌曲切换手势方向
 */
enum class MiniPlayerSlideDirection {
    UP,
    DOWN
}

/**
 * 殿堂级 Hi-Fi 拟物悬浮音乐播放 Dock (Master Glassmorphic Floating Music Bar)
 * 1. 拟物多层磨砂亚克力玻璃底板：双层物理光影阴影、1px 顶受光晶体亮刃与金属倒角；
 * 2. 上下滑动切歌：AnimatedContent 双层平行推拉（旧歌退场 + 新歌推入），彻底告别黑屏与手势死锁；
 * 3. 右滑物理变形收起：横屏自适应对齐屏幕右侧定位图标正下方，竖屏对齐手机右下角定位图标正下方；
 * 4. 精密光纤流光轮廓：展开时长条流光，收缩时自适应转为圆形极光星芒光环。
 */
@Composable
fun MiniPlayerBar(
    playbackState: PlaybackState,
    onTogglePlay: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onClick: () -> Unit,
    isTabletMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val song = playbackState.currentSong ?: return
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val coverVer by CoverHelper.coverVersion.collectAsState()
    val artUri = song.albumArtUri ?: AudioCoverProvider.buildSongCoverUri(song.id, song.path, song.album)
    val colors = OrbitTheme.colors
    val isPlaying = playbackState.isPlaying

    // 屏幕横竖屏与平板适配
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            configuration.screenWidthDp > configuration.screenHeightDp
    // 是否正在使用平板UI布局（必须开启平板模式且处于横屏/宽屏，与 MusicLibraryScreen 中的 useTabletLayout 保持严格一致）
    val isTabletLayout = isTabletMode && (isLandscape || configuration.screenWidthDp >= 600)

    // 收起/展开物理变形状态
    var isCollapsed by rememberSaveable { mutableStateOf(false) }

    val morphProgress by animateFloatAsState(
        targetValue = if (isCollapsed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "MiniPlayerMorph"
    )

    // 切歌方向与防抖时间戳
    var slideDirection by remember { mutableStateOf(MiniPlayerSlideDirection.UP) }
    var lastSwitchTime by remember { mutableLongStateOf(0L) }
    val verticalDragAnim = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    // 新歌曲到来时，自外侧边缘平滑推入居中归位
    LaunchedEffect(song.id) {
        val targetEntry = if (slideDirection == MiniPlayerSlideDirection.UP) 140f else -140f
        verticalDragAnim.snapTo(targetEntry)
        verticalDragAnim.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    // 顺势飞出并触发切歌（绝不瞬移回弹原位）
    fun triggerSwitch(direction: MiniPlayerSlideDirection, action: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastSwitchTime < 240L) return
        lastSwitchTime = now
        slideDirection = direction
        coroutineScope.launch {
            val targetExit = if (direction == MiniPlayerSlideDirection.UP) -160f else 160f
            verticalDragAnim.animateTo(
                targetValue = targetExit,
                animationSpec = tween(durationMillis = 130, easing = FastOutLinearInEasing)
            )
            action()

            // 超时保底复位（防止单曲循环等特殊情况下未触发新歌 id）
            kotlinx.coroutines.delay(500)
            if (verticalDragAnim.value != 0f) {
                verticalDragAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            }
        }
    }

    // 旋转微型黑胶角度
    var discRotation by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var lastTime = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { time ->
                    val dt = (time - lastTime) / 1_000_000_000f
                    lastTime = time
                    discRotation = (discRotation + dt * 45f) % 360f
                }
            }
        }
    }

    // 悬浮环境微光呼吸动画
    val infiniteTransition = rememberInfiniteTransition(label = "DockBreathingAnim")
    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (isPlaying) 0.65f else 0.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowIntensity"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.navigationBars
                    .union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            )
    ) {
        val maxAllowedWidth = 680.dp
        val fullWidth = (maxWidth - 24.dp).coerceAtMost(maxAllowedWidth)
        val collapsedSize = 46.dp

        // 展开状态的右边距：横屏/宽屏时居中展示；手机竖屏为 12.dp
        val expandedEndPadding = if (maxWidth > fullWidth + 24.dp) {
            (maxWidth - fullWidth) / 2
        } else {
            12.dp
        }

        // 收起状态的右边距：平板UI下定位图标右边距为 24.dp；非平板UI（手机竖屏与横屏）定位图标右边距均为 16.dp
        val collapsedEndPadding = if (isTabletLayout) 24.dp else 16.dp

        val currentWidth = lerp(fullWidth, collapsedSize, morphProgress)
        val currentHeight = lerp(68.dp, collapsedSize, morphProgress)
        val currentCorner = lerp(20.dp, 23.dp, morphProgress)
        val currentEndPadding = lerp(expandedEndPadding, collapsedEndPadding, morphProgress)
        val currentBottomPadding = lerp(12.dp, 48.dp, morphProgress)
        val dockShape = RoundedCornerShape(currentCorner)

        // ========== 1. 底层核心 Dock 交互卡片 (双层 Skia 高斯模糊投影 + 变形位移) ==========
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = currentEndPadding, bottom = currentBottomPadding)
                .size(width = currentWidth, height = currentHeight)
                // 1. Skia 原生真实高斯模糊环境散焦阴影
                .then(
                    if (colors.isDark) {
                        Modifier
                            .masterDropShadow(
                                color = Color.Black,
                                alpha = 0.70f,
                                cornerRadius = currentCorner,
                                shadowBlur = lerp(20.dp, 12.dp, morphProgress),
                                offsetY = 6.dp
                            )
                            .masterDropShadow(
                                color = colors.primary,
                                alpha = glowIntensity * 0.40f,
                                cornerRadius = currentCorner,
                                shadowBlur = lerp(12.dp, 8.dp, morphProgress),
                                offsetY = 2.dp
                            )
                    } else {
                        Modifier
                            .masterDropShadow(
                                color = Color.Black,
                                alpha = if (isPlaying) 0.32f else 0.26f,
                                cornerRadius = currentCorner,
                                shadowBlur = lerp(18.dp, 10.dp, morphProgress),
                                offsetY = 8.dp
                            )
                            .masterDropShadow(
                                color = Color.Black,
                                alpha = 0.20f,
                                cornerRadius = currentCorner,
                                shadowBlur = lerp(7.dp, 5.dp, morphProgress),
                                offsetY = 3.dp
                            )
                    }
                )
                // 2. 原生系统 Elevation 辅以增强
                .shadow(
                    elevation = if (isPlaying) 12.dp else 8.dp,
                    shape = dockShape,
                    spotColor = if (colors.isDark) colors.primary.copy(alpha = glowIntensity * 0.5f) else Color(0x55000000),
                    ambientColor = if (colors.isDark) Color(0xAA000000) else Color(0x30000000)
                )
                .clip(dockShape)
                // 物理亚克力多层微光渐变背景
                .background(
                    brush = if (colors.isDark) {
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFA202636),
                                Color(0xFC131722),
                                Color(0xFF0C0F17)
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFFFFFFFF),
                                Color(0xFFFCFDFE),
                                Color(0xFFF6F8FA)
                            )
                        )
                    }
                )
                .then(
                    if (colors.isDark) {
                        Modifier.border(
                            width = 1.3.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    colors.primary.copy(alpha = if (isPlaying) 0.75f else 0.45f),
                                    Color(0x2500E5FF),
                                    Color(0x15FFFFFF),
                                    colors.secondary.copy(alpha = if (isPlaying) 0.75f else 0.45f)
                                )
                            ),
                            shape = dockShape
                        )
                    } else {
                        Modifier
                    }
                )
                // 智能手势监听：上下滑动切歌（即时微动反馈，绝不死锁） + 右滑收起 + 左滑展开
                .pointerInput(isCollapsed) {
                    if (isCollapsed) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            if (dragAmount < -15f) {
                                isCollapsed = false
                                change.consume()
                            }
                        }
                    } else {
                        var totalX = 0f
                        var totalY = 0f
                        var gestureMode = 0 // 0: 未定, 1: 垂直切歌, 2: 水平右滑收起

                        detectDragGestures(
                            onDragStart = {
                                totalX = 0f
                                totalY = 0f
                                gestureMode = 0
                            },
                            onDragEnd = {
                                if (gestureMode == 1) {
                                    val threshold = 40f
                                    if (totalY < -threshold) {
                                        // 向上滑达到阈值：直接加速飞出并切下一首，绝不弹回原位！
                                        triggerSwitch(MiniPlayerSlideDirection.UP, onPlayNext)
                                    } else if (totalY > threshold) {
                                        // 向下滑达到阈值：直接加速飞出并切上一首，绝不弹回原位！
                                        triggerSwitch(MiniPlayerSlideDirection.DOWN, onPlayPrevious)
                                    } else {
                                        // 未达到阈值：平滑弹回原位
                                        coroutineScope.launch {
                                            verticalDragAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                        }
                                    }
                                } else if (gestureMode == 2) {
                                    val collapseThreshold = 80f
                                    if (totalX > collapseThreshold) {
                                        isCollapsed = true
                                    }
                                }
                                totalX = 0f
                                totalY = 0f
                                gestureMode = 0
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    verticalDragAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                                totalX = 0f
                                totalY = 0f
                                gestureMode = 0
                            },
                            onDrag = { change, dragAmount ->
                                totalX += dragAmount.x
                                totalY += dragAmount.y

                                if (gestureMode == 0) {
                                    if (abs(totalY) > abs(totalX) && abs(totalY) > 8f) {
                                        gestureMode = 1
                                    } else if (totalX > abs(totalY) && totalX > 12f) {
                                        gestureMode = 2
                                    }
                                }

                                if (gestureMode == 1) {
                                    coroutineScope.launch {
                                        // 充分跟随手指拖拽，最大范围可达 160px，彻底放开拖拽自由度
                                        verticalDragAnim.snapTo((totalY * 0.85f).coerceIn(-160f, 160f))
                                    }
                                    change.consume()
                                } else if (gestureMode == 2) {
                                    change.consume()
                                }
                            }
                        )
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = if (isCollapsed) rememberRipple(bounded = true, radius = 24.dp) else rememberRipple(bounded = true, color = colors.primary.copy(alpha = 0.15f)),
                    onClick = {
                        if (isCollapsed) {
                            isCollapsed = false
                        } else {
                            onClick()
                        }
                    }
                )
        ) {
            // A. 展开状态下的完整内容 (随 morphProgress 渐隐)
            if (morphProgress < 0.85f) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
                        .graphicsLayer {
                            alpha = ((1f - morphProgress * 1.5f)).coerceIn(0f, 1f)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧与中间：连续物理推拉容器（手势位移 -> 顺势滑出卡片边缘 -> 新歌从对面平滑推入）
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clipToBounds()
                            .offset { IntOffset(0, verticalDragAnim.value.roundToInt()) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. 封面
                            val currentArtUri = song.albumArtUri ?: AudioCoverProvider.buildSongCoverUri(song.id, song.path, song.album)
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .shadow(
                                        elevation = 4.dp,
                                        shape = RoundedCornerShape(10.dp),
                                        spotColor = colors.primary.copy(alpha = 0.35f),
                                        ambientColor = Color.Black.copy(alpha = 0.25f)
                                    )
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surfaceCard)
                                    .border(
                                        width = 1.dp,
                                        brush = Brush.verticalGradient(
                                            listOf(
                                                if (colors.isDark) Color(0x55FFFFFF) else Color(0x99FFFFFF),
                                                Color(0x10FFFFFF)
                                            )
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (currentArtUri.isNotBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(currentArtUri)
                                            .memoryCacheKey("${currentArtUri}_$coverVer")
                                            .diskCacheKey("${currentArtUri}_$coverVer")
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = song.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // 2. 歌名 + 歌手 + 4柱音频波形
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = song.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    MiniAudioWaves(isPlaying = isPlaying)
                                }

                                Spacer(modifier = Modifier.height(2.5.dp))

                                Text(
                                    text = "${song.artist} • ${song.album}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 右侧：立体三键精工控制台
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 上一曲按键
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (colors.isDark) Color(0x18FFFFFF) else Color(0x0A000000)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(bounded = true, radius = 17.dp),
                                    onClick = {
                                        triggerSwitch(MiniPlayerSlideDirection.DOWN, onPlayPrevious)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = colors.textPrimary.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 核心播放/暂停大按键
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (colors.isDark) Color(0xFFFF6A3D) else Color(0xFFF2541B)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(bounded = true, radius = 21.dp),
                                    onClick = onTogglePlay
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // 下一曲按键
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (colors.isDark) Color(0x18FFFFFF) else Color(0x0A000000)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(bounded = true, radius = 17.dp),
                                    onClick = {
                                        triggerSwitch(MiniPlayerSlideDirection.UP, onPlayNext)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = colors.textPrimary.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // B. 收缩状态下的微型黑胶小图标
            if (morphProgress > 0.15f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = ((morphProgress - 0.25f) / 0.75f).coerceIn(0f, 1f)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .rotate(discRotation)
                            .background(Color(0xFF101318)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (artUri.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(artUri)
                                    .memoryCacheKey("${artUri}_$coverVer")
                                    .diskCacheKey("${artUri}_$coverVer")
                                    .crossfade(false)
                                    .build(),
                                contentDescription = song.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 黑胶同心微轴芯
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xEE1E2430))
                                .border(1.dp, colors.primary.copy(alpha = 0.85f), CircleShape)
                        )
                    }
                }
            }
        }

        // ========== 2. 边缘精密光纤极光流光 (随 currentCorner 变大自动平滑成为圆形流光光环) ==========
        PrecisionFiberOpticBeam(
            progress = playbackState.progress,
            isPlaying = isPlaying,
            cornerRadius = currentCorner,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = currentEndPadding, bottom = currentBottomPadding)
                .size(width = currentWidth, height = currentHeight)
        )
    }
}

/**
 * 殿堂级边缘光纤激光极光束 (Precision Fiber-Optic Edge Beam)
 * 1. 紧贴圆角矩形外框（1.4dp 纤细激光光纤，彻底移除原版 52dp 严重失焦的糙白大光斑）；
 * 2. 40dp 优雅流光衰减拖尾；
 * 3. 头部为高精密旋转四角钻石星核（纯白 3dp 核心 + 6dp 锐利光芒针）。
 */
@Composable
private fun PrecisionFiberOpticBeam(
    progress: Float,
    isPlaying: Boolean,
    cornerRadius: Dp,
    modifier: Modifier = Modifier
) {
    // 平滑插值进度
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 280, easing = LinearEasing),
        label = "FiberProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "FiberLaserAnim")

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = if (isPlaying) 1.0f else 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val starRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "starRotation"
    )

    val beamColor = OrbitTheme.colors.primary

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val r = cornerRadius.toPx().coerceAtMost(minOf(w, h) / 2f)

        // 沿底板轮廓闭合顺时针路径
        val beamPath = Path().apply {
            moveTo(r, 0f)
            lineTo(w - r, 0f)
            arcTo(rect = Rect(w - 2 * r, 0f, w, 2 * r), startAngleDegrees = 270f, sweepAngleDegrees = 90f, forceMoveTo = false)
            lineTo(w, h - r)
            arcTo(rect = Rect(w - 2 * r, h - 2 * r, w, h), startAngleDegrees = 0f, sweepAngleDegrees = 90f, forceMoveTo = false)
            lineTo(r, h)
            arcTo(rect = Rect(0f, h - 2 * r, 2 * r, h), startAngleDegrees = 90f, sweepAngleDegrees = 90f, forceMoveTo = false)
            lineTo(0f, r)
            arcTo(rect = Rect(0f, 0f, 2 * r, 2 * r), startAngleDegrees = 180f, sweepAngleDegrees = 90f, forceMoveTo = false)
            close()
        }

        val pathMeasure = PathMeasure().apply {
            setPath(beamPath, true)
        }

        val totalLen = pathMeasure.length
        if (totalLen <= 0f) return@Canvas

        val currentDist = (animatedProgress * totalLen).coerceIn(0f, totalLen)
        val headPos = pathMeasure.getPosition(currentDist)

        // 1. 极光光纤微拖尾 (Beam Trail) - 48dp 优雅流光拖尾
        val trailLen = (48.dp.toPx()).coerceAtMost(totalLen * 0.18f)
        if (trailLen > 0f && currentDist > 0f) {
            val trailStart = currentDist - trailLen
            if (trailStart >= 0f) {
                val segPath = Path()
                pathMeasure.getSegment(trailStart, currentDist, segPath, true)
                drawPath(
                    path = segPath,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            beamColor.copy(alpha = 0.90f * pulseAlpha),
                            beamColor.copy(alpha = 0.25f * pulseAlpha),
                            Color.Transparent
                        ),
                        center = headPos,
                        radius = trailLen
                    ),
                    style = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round)
                )
            } else {
                val seg1 = Path()
                pathMeasure.getSegment(totalLen + trailStart, totalLen, seg1, true)
                drawPath(
                    path = seg1,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            beamColor.copy(alpha = 0.90f * pulseAlpha),
                            beamColor.copy(alpha = 0.25f * pulseAlpha),
                            Color.Transparent
                        ),
                        center = headPos,
                        radius = trailLen
                    ),
                    style = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round)
                )

                val seg2 = Path()
                pathMeasure.getSegment(0f, currentDist, seg2, true)
                drawPath(
                    path = seg2,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            beamColor.copy(alpha = 0.90f * pulseAlpha),
                            beamColor.copy(alpha = 0.25f * pulseAlpha),
                            Color.Transparent
                        ),
                        center = headPos,
                        radius = trailLen
                    ),
                    style = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // 2. 细腻外圈微光晕 (扩大至 12dp，肉眼清晰柔和)
        val microHalo = 12.dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    beamColor.copy(alpha = 0.65f * pulseAlpha),
                    beamColor.copy(alpha = 0.20f * pulseAlpha),
                    Color.Transparent
                ),
                center = headPos,
                radius = microHalo
            ),
            radius = microHalo,
            center = headPos
        )

        // 3. 精雕十字微星芒 (针芒延长至 10dp)
        rotate(degrees = starRotation, pivot = headPos) {
            val needleLen = 10.dp.toPx()
            val needleWidth = 1.8.dp.toPx()

            // 水平光针
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.95f * pulseAlpha),
                        beamColor,
                        Color.White.copy(alpha = 0.95f * pulseAlpha),
                        Color.Transparent
                    ),
                    startX = headPos.x - needleLen,
                    endX = headPos.x + needleLen
                ),
                start = Offset(headPos.x - needleLen, headPos.y),
                end = Offset(headPos.x + needleLen, headPos.y),
                strokeWidth = needleWidth,
                cap = StrokeCap.Round
            )

            // 垂直光针
            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.95f * pulseAlpha),
                        beamColor,
                        Color.White.copy(alpha = 0.95f * pulseAlpha),
                        Color.Transparent
                    ),
                    startY = headPos.y - needleLen,
                    endY = headPos.y + needleLen
                ),
                start = Offset(headPos.x, headPos.y - needleLen),
                end = Offset(headPos.x, headPos.y + needleLen),
                strokeWidth = needleWidth,
                cap = StrokeCap.Round
            )
        }

        // 4. 纯白钻石微星核 (半径提升至 3.6dp，清透醒目)
        drawCircle(
            color = Color.White.copy(alpha = 0.98f * pulseAlpha),
            radius = 3.6.dp.toPx(),
            center = headPos
        )

        // 5. 超细微光环 (半径 5.5dp)
        drawCircle(
            color = beamColor.copy(alpha = 0.90f * pulseAlpha),
            radius = 5.5.dp.toPx(),
            center = headPos,
            style = Stroke(width = 1.2.dp.toPx())
        )
    }
}

/**
 * 殿堂级 4 柱微型动态音频律动音柱
 */
@Composable
private fun MiniAudioWaves(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WavesTransition")

    val h1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (isPlaying) 0.95f else 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave1"
    )

    val h2 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = if (isPlaying) 0.30f else 0.40f,
        animationSpec = infiniteRepeatable(
            animation = tween(320, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave2"
    )

    val h3 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (isPlaying) 1.0f else 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(440, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave3"
    )

    val h4 by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = if (isPlaying) 0.20f else 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave4"
    )

    val primaryColor = OrbitTheme.colors.primary

    Row(
        modifier = modifier.height(13.dp),
        horizontalArrangement = Arrangement.spacedBy(1.8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(h1, h2, h3, h4).forEach { waveHeight ->
            Box(
                modifier = Modifier
                    .width(2.2.dp)
                    .fillMaxHeight(waveHeight)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (isPlaying) {
                            Brush.verticalGradient(
                                listOf(primaryColor, primaryColor.copy(alpha = 0.6f))
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(Color.Gray.copy(alpha = 0.4f), Color.Gray.copy(alpha = 0.2f))
                            )
                        }
                    )
            )
        }
    }
}

/**
 * 基于原生 Skia 引擎（setShadowLayer）的高斯模糊真实物理投影扩展修饰符
 * 彻底摆脱系统 elevation 对环境光限制导致的“白底上几乎看不见”问题，
 * 生成饱满、柔和、肉眼绝对清晰可见的真实立体阴影。
 */
private fun Modifier.masterDropShadow(
    color: Color,
    alpha: Float,
    cornerRadius: Dp,
    shadowBlur: Dp,
    offsetY: Dp,
    spread: Dp = 0.dp
): Modifier = this.drawBehind {
    val cornerRadiusPx = cornerRadius.toPx()
    val shadowBlurPx = shadowBlur.toPx()
    val offsetYPx = offsetY.toPx()
    val spreadPx = spread.toPx()

    val shadowColorArgb = android.graphics.Color.argb(
        (alpha * 255f).toInt().coerceIn(0, 255),
        (color.red * 255f).toInt().coerceIn(0, 255),
        (color.green * 255f).toInt().coerceIn(0, 255),
        (color.blue * 255f).toInt().coerceIn(0, 255)
    )

    drawIntoCanvas { canvas ->
        val paint = FrameworkPaint().apply {
            isAntiAlias = true
            this.color = android.graphics.Color.TRANSPARENT
            setShadowLayer(
                shadowBlurPx,
                0f,
                offsetYPx,
                shadowColorArgb
            )
        }
        canvas.nativeCanvas.drawRoundRect(
            -spreadPx,
            -spreadPx,
            size.width + spreadPx,
            size.height + spreadPx,
            cornerRadiusPx,
            cornerRadiusPx,
            paint
        )
    }
}

