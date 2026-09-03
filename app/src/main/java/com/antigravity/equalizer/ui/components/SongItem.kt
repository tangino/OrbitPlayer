package com.antigravity.equalizer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.antigravity.equalizer.data.model.Song
import com.antigravity.equalizer.ui.theme.*
import com.antigravity.equalizer.ui.viewmodel.LibraryViewMode

/**
 * 歌曲卡片组件：
 * 1. 列表正常滚动时：零多余开销，120 FPS 满帧无拖影
 * 2. 当前播放歌曲：采用高品质动态音频律动频谱指示器 (PlayingEqualizerIndicator)，彻底取代死板的静态图标
 * 3. 支持短按播放、长按弹出操作面板 (onLongClick)
 * 4. Pinch 缩放切换视图时：触发尺寸位移形变与全 6 档自适应适配
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song: Song,
    isPlaying: Boolean,
    isCurrent: Boolean,
    viewMode: LibraryViewMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val isGrid = viewMode == LibraryViewMode.GRID_2_COL ||
            viewMode == LibraryViewMode.GRID_3_COL ||
            viewMode == LibraryViewMode.GRID_4_COL

    val isNoArt = viewMode == LibraryViewMode.LIST_NO_ART
    val colors = OrbitTheme.colors

    val itemBg = if (isCurrent) {
        colors.primary.copy(alpha = 0.16f)
    } else if (viewMode == LibraryViewMode.LIST_LARGE_ART || isGrid) {
        colors.surfaceCard
    } else {
        Color.Transparent
    }

    val itemShape = RoundedCornerShape(if (viewMode == LibraryViewMode.LIST_LARGE_ART || isGrid) 12.dp else 8.dp)

    val itemPadding = when (viewMode) {
        LibraryViewMode.GRID_4_COL -> 4.dp
        LibraryViewMode.GRID_3_COL -> 6.dp
        LibraryViewMode.GRID_2_COL -> 8.dp
        LibraryViewMode.LIST_LARGE_ART -> 8.dp
        else -> 6.dp
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(itemShape)
            .background(itemBg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(itemPadding)
    ) {
        if (isGrid) {
            // ========== 网格模式 (GRID_2_COL / GRID_3_COL / GRID_4_COL) ==========
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceCard),
                    contentAlignment = Alignment.Center
                ) {
                    if (song.albumArtUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(song.albumArtUri)
                                .size(
                                    when (viewMode) {
                                        LibraryViewMode.GRID_4_COL -> Size(120, 120)
                                        LibraryViewMode.GRID_3_COL -> Size(200, 200)
                                        else -> Size(300, 300)
                                    }
                                )
                                .allowHardware(true)
                                .crossfade(false)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(if (viewMode == LibraryViewMode.GRID_4_COL) 20.dp else 32.dp)
                        )
                    }

                    // 当前播放歌曲的动态封面遮罩指示器
                    if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.48f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPlaying) {
                                // 动态跳动的音频律动频谱柱
                                PlayingEqualizerIndicator(
                                    isPlaying = true,
                                    barCount = if (viewMode == LibraryViewMode.GRID_4_COL) 3 else 4,
                                    barWidth = if (viewMode == LibraryViewMode.GRID_4_COL) 2.4.dp else 3.2.dp,
                                    barSpacing = 2.2.dp,
                                    modifier = Modifier.size(if (viewMode == LibraryViewMode.GRID_4_COL) 16.dp else 24.dp),
                                    color = colors.primary
                                )
                            } else {
                                // 暂停状态下显示静止的暂停指示
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Paused",
                                    tint = colors.primary.copy(alpha = 0.9f),
                                    modifier = Modifier.size(if (viewMode == LibraryViewMode.GRID_4_COL) 16.dp else 22.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = song.title,
                    fontSize = when (viewMode) {
                        LibraryViewMode.GRID_4_COL -> 10.sp
                        LibraryViewMode.GRID_3_COL -> 11.sp
                        else -> 13.sp
                    },
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) colors.primary else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (viewMode != LibraryViewMode.GRID_4_COL) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = song.artist,
                        fontSize = when (viewMode) {
                            LibraryViewMode.GRID_3_COL -> 9.sp
                            else -> 11.sp
                        },
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            // ========== 列表模式 (LIST_NO_ART / LIST_SMALL_ART / LIST_LARGE_ART) ==========
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isNoArt) {
                    val coverSize = if (viewMode == LibraryViewMode.LIST_LARGE_ART) 64.dp else 46.dp

                    Box(
                        modifier = Modifier
                            .size(coverSize)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        if (song.albumArtUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(song.albumArtUri)
                                    .size(if (viewMode == LibraryViewMode.LIST_LARGE_ART) Size(180, 180) else Size(120, 120))
                                    .allowHardware(true)
                                    .crossfade(false)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(if (viewMode == LibraryViewMode.LIST_LARGE_ART) 28.dp else 22.dp)
                            )
                        }

                        // 封面上的当前曲目动态播放指示器
                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isPlaying) {
                                    PlayingEqualizerIndicator(
                                        isPlaying = true,
                                        barCount = 3,
                                        barWidth = 2.5.dp,
                                        barSpacing = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                        color = colors.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Pause,
                                        contentDescription = "Paused",
                                        tint = colors.primary.copy(alpha = 0.9f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (viewMode == LibraryViewMode.LIST_LARGE_ART) 15.sp else 14.sp,
                        color = if (isCurrent) colors.primary else colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${song.artist} • ${song.album}",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 右侧自定义操作内容或时长
                if (trailingContent != null) {
                    trailingContent()
                } else if (isCurrent) {
                    if (isPlaying) {
                        PlayingEqualizerIndicator(
                            isPlaying = true,
                            barCount = 3,
                            barWidth = 2.2.dp,
                            barSpacing = 2.dp,
                            modifier = Modifier.height(14.dp),
                            color = colors.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Paused",
                            tint = colors.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else {
                    Text(
                        text = song.formattedDuration,
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}

/**
 * 动态音乐播放律动频谱指示器 (Animated Equalizer Indicator)
 * 3 ~ 4 根发光音波柱以错落有致的周期跳跃，实时生动反馈当前播放状态
 */
@Composable
fun PlayingEqualizerIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = OrbitTheme.colors.primary,
    barCount: Int = 4,
    barWidth: Dp = 2.5.dp,
    barSpacing: Dp = 2.dp,
    minHeightFraction: Float = 0.2f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SongEqWaveTransition")

    // 为各音波柱配置不同的起伏周期与相位，模拟真实的音频频谱跳跃
    val wave1 by infiniteTransition.animateFloat(
        initialValue = minHeightFraction,
        targetValue = if (isPlaying) 0.95f else minHeightFraction,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 440, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "song_eq1"
    )

    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = if (isPlaying) 0.25f else minHeightFraction,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 350, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "song_eq2"
    )

    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (isPlaying) 1.0f else minHeightFraction,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "song_eq3"
    )

    val wave4 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = if (isPlaying) 0.3f else minHeightFraction,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 390, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "song_eq4"
    )

    val heights = if (barCount <= 3) {
        listOf(wave1, wave2, wave3)
    } else {
        listOf(wave1, wave2, wave3, wave4)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(barSpacing),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight(fraction.coerceIn(minHeightFraction, 1f))
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}
