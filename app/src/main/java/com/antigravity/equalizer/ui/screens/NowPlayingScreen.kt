package com.antigravity.equalizer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.antigravity.equalizer.R
import com.antigravity.equalizer.audio.PlaybackState
import com.antigravity.equalizer.audio.RepeatMode
import com.antigravity.equalizer.audio.ShuffleStrategy
import com.antigravity.equalizer.audio.VisualizerFrame
import com.antigravity.equalizer.data.model.ProgressTrailStyle
import com.antigravity.equalizer.data.model.Song
import com.antigravity.equalizer.data.model.SongAttitude
import com.antigravity.equalizer.data.model.VisualizerColorScheme
import com.antigravity.equalizer.data.model.VisualizerStyle
import com.antigravity.equalizer.ui.components.AppBackgroundLayer
import com.antigravity.equalizer.ui.components.PowerampSpectrumVisualizer
import com.antigravity.equalizer.ui.theme.*
import com.antigravity.equalizer.ui.utils.swipeToChangeSong
import com.antigravity.equalizer.ui.utils.swipeVerticalGesture
import com.antigravity.equalizer.ui.viewmodel.EqualizerUiState
import com.antigravity.equalizer.ui.viewmodel.MusicPlayerViewModel
import com.antigravity.equalizer.utils.LyricLine
import com.antigravity.equalizer.utils.LyricParser
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    viewModel: MusicPlayerViewModel,
    equalizerUiState: EqualizerUiState,
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onCycleVisualizerStyle: (() -> Unit)? = null,
    onToggleCoverVisualizer: (Boolean) -> Unit = {},
    onToggleVisualizerMaximized: (Boolean) -> Unit = {},
    onToggleMaximizedShowCover: (Boolean) -> Unit = {},
    onToggleMaximizedCoverPosition: (Boolean) -> Unit = {},
    onToggleMaximizedShowControls: (Boolean) -> Unit = {},
    onSetMaximizedCoverAlpha: (Float) -> Unit = {},
    onToggleCoverInQueue: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playbackState by viewModel.playbackState.collectAsState()
    val visualizerFrame by viewModel.visualizerFlow.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val rawSong = playbackState.currentSong
    val song = remember(rawSong, allSongs, favoriteSongs) {
        if (rawSong == null) null
        else {
            val fromLib = allSongs.find { it.path == rawSong.path }
            val isFavorite = favoriteSongs.any { it.path == rawSong.path }
            (fromLib ?: rawSong).copy(isFavorite = isFavorite)
        }
    }

    // 播放进度拖拽控制
    var isDraggingSlider by remember { mutableStateOf(false) }
    var draggingProgress by remember { mutableFloatStateOf(0f) }

    val playlists by viewModel.playlists.collectAsState()

    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    // 当前播放队列弹层
    var showQueueBottomSheet by remember { mutableStateOf(false) }

    val currentProgress = if (isDraggingSlider) draggingProgress else playbackState.progress
    val currentPosMs = if (isDraggingSlider) (draggingProgress * playbackState.durationMs).toLong() else playbackState.currentPositionMs

    // 实时异步加载并解析同目录下同名歌词文件
    var lyricLines by remember { mutableStateOf<List<LyricLine>>(emptyList()) }

    LaunchedEffect(song?.id, song?.path) {
        if (song != null) {
            lyricLines = withContext(Dispatchers.IO) {
                LyricParser.loadLyricForSong(song.path)
            }
        } else {
            lyricLines = emptyList()
        }
    }

    // 根据当前播放时间精确定位歌词行
    val currentLyricIndex = remember(lyricLines, currentPosMs) {
        if (lyricLines.isEmpty()) -1
        else lyricLines.indexOfLast { it.timeMs <= currentPosMs }
    }

    // 专辑大图缩放动画 (播放时轻微呼吸放大)
    val coverScale by animateFloatAsState(
        targetValue = if (playbackState.isPlaying) 1.0f else 0.94f,
        animationSpec = tween(400),
        label = "CoverScaleAnim"
    )

    // 专辑封面是否被上滑替换为全尺寸沉浸式大频谱可视化 (持久化配置驱动)
    val showCoverVisualizer = equalizerUiState.showNowPlayingVisualizer

    // 当频谱视效被彻底关闭时，若当前处于封面大频谱模式，自动平滑退回专辑封面
    LaunchedEffect(equalizerUiState.visualizerStyle, equalizerUiState.visualizerEnabled) {
        if ((equalizerUiState.visualizerStyle == VisualizerStyle.OFF || !equalizerUiState.visualizerEnabled) && showCoverVisualizer) {
            onToggleCoverVisualizer(false)
        }
    }

    // 当前应用的均衡器配置文案
    val currentPresetName = remember(equalizerUiState.selectedPresetId, equalizerUiState.presets) {
        val preset = equalizerUiState.presets.find { it.id == equalizerUiState.selectedPresetId }
        when {
            preset != null -> preset.name
            equalizerUiState.selectedPresetId == "custom" -> "Custom (自定义)"
            else -> "Flat (标准)"
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AppBackgroundLayer(
            customBackgroundPath = equalizerUiState.customBackgroundPath,
            blurRadius = equalizerUiState.backgroundBlurRadius,
            blurStyle = equalizerUiState.backgroundBlurStyle,
            dimAlpha = equalizerUiState.backgroundDimAlpha
        )

        Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse",
                            tint = OrbitTheme.colors.textPrimary,
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
                            color = OrbitTheme.colors.primary,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = song?.album ?: "Unknown Album",
                            fontSize = 13.sp,
                            color = OrbitTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    // 右上角改为当前播放列表队列按钮
                    IconButton(onClick = { showQueueBottomSheet = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Playing Queue",
                            tint = OrbitTheme.colors.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OrbitTheme.colors.background)
            )
        },
        containerColor = OrbitTheme.colors.background
    ) { innerPadding ->
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
                configuration.screenWidthDp > configuration.screenHeightDp

        // 1. 封面与大频谱可视化无缝切换视图 (大频谱横向占满页面四周留空，封面保持精致正方形)
        val coverView: @Composable () -> Unit = {
            AnimatedContent(
                targetState = showCoverVisualizer,
                transitionSpec = {
                    if (targetState) {
                        (slideInVertically(animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)) { height -> height } + fadeIn(tween(250)))
                            .togetherWith(slideOutVertically(animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)) { height -> -height } + fadeOut(tween(200)))
                    } else {
                        (slideInVertically(animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)) { height -> -height } + fadeIn(tween(250)))
                            .togetherWith(slideOutVertically(animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)) { height -> height } + fadeOut(tween(200)))
                    }
                },
                label = "CoverVisualizerSwitchAnim"
            ) { isVisualizerMode ->
                if (isVisualizerMode) {
                    // ========== 沉浸式大频谱可视化 (纯净无边框，横向舒展自然融入页面) ==========
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isLandscape) 150.dp else 195.dp)
                            .swipeVerticalGesture(
                                onSwipeUp = {},
                                onSwipeDown = { onToggleCoverVisualizer(false) }
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onCycleVisualizerStyle?.invoke() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val currentStyle = if (equalizerUiState.visualizerStyle == VisualizerStyle.OFF) {
                            VisualizerStyle.BARS_WITH_PEAKS
                        } else {
                            equalizerUiState.visualizerStyle
                        }

                        // 全屏动态频谱
                        PowerampSpectrumVisualizer(
                            magnitudes = visualizerFrame.rawMagnitudes,
                            peaks = visualizerFrame.peakCaps,
                            style = currentStyle,
                            colorScheme = equalizerUiState.visualizerColorScheme,
                            peakDecayEnabled = equalizerUiState.visualizerPeakDecayEnabled,
                            isPlaying = playbackState.isPlaying,
                            barWidthDp = equalizerUiState.visualizerBarWidthDp,
                            barAlpha = equalizerUiState.visualizerBarAlpha,
                            borderWidthDp = equalizerUiState.visualizerBarBorderWidthDp,
                            borderColor = equalizerUiState.visualizerBarBorderColor,
                            borderAlpha = equalizerUiState.visualizerBarBorderAlpha,
                            borderOnly = equalizerUiState.visualizerBarBorderOnly,
                            customColor = equalizerUiState.visualizerCustomColor,
                            customColor2 = equalizerUiState.visualizerCustomColor2,
                            isSingleColor = equalizerUiState.visualizerSingleColor,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            onClick = { onCycleVisualizerStyle?.invoke() }
                        )

                        // 顶部操作组：样式药丸徽标 + 最大化全屏按钮
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 样式切换徽标
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = OrbitTheme.colors.background.copy(alpha = 0.70f),
                                modifier = Modifier.clickable { onCycleVisualizerStyle?.invoke() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val styleText = when (currentStyle) {
                                        VisualizerStyle.BARS_WITH_PEAKS -> stringResource(R.string.visualizer_style_bars_with_peaks)
                                        VisualizerStyle.AURORA_MOUNTAIN -> stringResource(R.string.visualizer_style_aurora_mountain)
                                        VisualizerStyle.MIRRORED_BARS -> stringResource(R.string.visualizer_style_mirrored_bars)
                                        VisualizerStyle.OFF -> stringResource(R.string.visualizer_style_off)
                                    }
                                    Text(
                                        text = styleText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = OrbitTheme.colors.primary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = OrbitTheme.colors.primary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }

                            // 最大化全屏按钮
                            Surface(
                                shape = CircleShape,
                                color = OrbitTheme.colors.background.copy(alpha = 0.70f),
                                modifier = Modifier
                                    .size(26.dp)
                                    .clickable { onToggleVisualizerMaximized(true) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = stringResource(R.string.visualizer_maximize),
                                        tint = OrbitTheme.colors.primary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }

                        // 底部优雅向下滑动提示浮标
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(OrbitTheme.colors.background.copy(alpha = 0.50f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = OrbitTheme.colors.textSecondary.copy(alpha = 0.75f),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = stringResource(R.string.visualizer_swipe_down_hint),
                                fontSize = 9.sp,
                                color = OrbitTheme.colors.textSecondary.copy(alpha = 0.75f)
                            )
                        }
                    }
                } else {
                    // ========== 原生专辑封面视图 (精致正方形居中，支持向上滑动替换为大频谱) ==========
                    val coverSize = if (isLandscape) 150.dp else 190.dp
                    Box(
                        modifier = Modifier
                            .size(coverSize)
                            .scale(coverScale)
                            .shadow(
                                elevation = 16.dp,
                                shape = RoundedCornerShape(18.dp),
                                spotColor = OrbitTheme.colors.primary.copy(alpha = 0.35f)
                            )
                            .clip(RoundedCornerShape(18.dp))
                            .background(OrbitTheme.colors.surfaceCard)
                            .swipeVerticalGesture(
                                onSwipeUp = {
                                    if (equalizerUiState.visualizerStyle == VisualizerStyle.OFF) {
                                        onCycleVisualizerStyle?.invoke()
                                    }
                                    onToggleCoverVisualizer(true)
                                },
                                onSwipeDown = {}
                            ),
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
                                    tint = OrbitTheme.colors.primary,
                                    modifier = Modifier.size(if (isLandscape) 52.dp else 72.dp)
                                )
                            }
                        }

                        // 底部优雅的上滑提示小浮标
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(OrbitTheme.colors.surfaceCard.copy(alpha = 0.75f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = OrbitTheme.colors.textSecondary.copy(alpha = 0.85f),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = stringResource(R.string.visualizer_swipe_up_hint),
                                fontSize = 9.sp,
                                color = OrbitTheme.colors.textSecondary.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }

        // 2. 歌词展示视图
        val lyricListState = rememberLazyListState()
        LaunchedEffect(currentLyricIndex) {
            if (currentLyricIndex >= 0 && lyricLines.isNotEmpty()) {
                lyricListState.animateScrollToItem(maxOf(0, currentLyricIndex - 1))
            }
        }
        val lyricsView: @Composable (Modifier) -> Unit = { mod ->
            Box(
                modifier = mod.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (lyricLines.isNotEmpty()) {
                    LazyColumn(
                        state = lyricListState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        itemsIndexed(
                            items = lyricLines,
                            key = { idx: Int, line: LyricLine -> "${line.timeMs}_$idx" }
                        ) { index: Int, line: LyricLine ->
                            val isCurrent = index == currentLyricIndex
                            Text(
                                text = line.text,
                                fontSize = if (isCurrent) (if (isLandscape) 17.sp else 16.sp) else (if (isLandscape) 13.5.sp else 12.5.sp),
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary.copy(alpha = 0.45f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = if (isLandscape) 4.dp else 3.dp)
                                    .clickable {
                                        viewModel.seekTo(line.timeMs)
                                    },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Text(
                        text = "纯音乐或暂无同名歌词",
                        fontSize = 13.sp,
                        color = OrbitTheme.colors.textSecondary.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 3. 歌曲标题与艺术家视图
        val trackInfoView: @Composable () -> Unit = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                AnimatedContent(
                    targetState = song?.title ?: "No Track Selected",
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                    label = "TitleAnim"
                ) { title ->
                    Text(
                        text = title,
                        fontSize = if (isLandscape) 18.sp else 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrbitTheme.colors.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                AnimatedContent(
                    targetState = song?.artist ?: "Unknown Artist",
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                    label = "ArtistAnim"
                ) { artist ->
                    Text(
                        text = artist,
                        fontSize = if (isLandscape) 13.sp else 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = OrbitTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 4. 快捷功能行 (红心/态度、EQ、菜单)
        val quickActionsView: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 24.dp else 32.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentAttitude = song?.attitude ?: SongAttitude.NONE
                IconButton(
                    onClick = {
                        val target = song ?: rawSong
                        if (target != null) {
                            viewModel.cycleSongAttitude(target) { nextAttitude ->
                                val msgRes = when (nextAttitude) {
                                    SongAttitude.FAVORITE -> R.string.attitude_favorite
                                    SongAttitude.DISLIKED -> R.string.attitude_disliked
                                    SongAttitude.NONE -> R.string.attitude_none
                                }
                                Toast.makeText(context, context.getString(msgRes) as CharSequence, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    val favScale by animateFloatAsState(
                        targetValue = if (currentAttitude == SongAttitude.FAVORITE) 1.25f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "FavoriteBounceAnim"
                    )
                    when (currentAttitude) {
                        SongAttitude.FAVORITE -> {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Favorite",
                                tint = Color(0xFFFF3366),
                                modifier = Modifier
                                    .size(24.dp)
                                    .scale(favScale)
                            )
                        }
                        SongAttitude.DISLIKED -> {
                            Icon(
                                imageVector = Icons.Default.ThumbDown,
                                contentDescription = "Disliked",
                                tint = Color(0xFFE57373),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        SongAttitude.NONE -> {
                            Icon(
                                imageVector = Icons.Default.FavoriteBorder,
                                contentDescription = "Neutral",
                                tint = OrbitTheme.colors.textSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // 均衡器入口
                IconButton(
                    onClick = onOpenEqualizer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = "Equalizer ($currentPresetName)",
                            tint = if (equalizerUiState.isEnabled) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        if (equalizerUiState.isEnabled) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(OrbitTheme.colors.primary)
                            )
                        }
                    }
                }

                // 频谱视效形态切换与封面可视化控制 (单击切换封面位置可视化展示/关闭，长按切换频谱样式)
                val vizActive = showCoverVisualizer || (equalizerUiState.visualizerEnabled && equalizerUiState.visualizerStyle != VisualizerStyle.OFF)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = {
                                if (showCoverVisualizer) {
                                    onToggleCoverVisualizer(false)
                                } else {
                                    if (equalizerUiState.visualizerStyle == VisualizerStyle.OFF) {
                                        onCycleVisualizerStyle?.invoke()
                                    }
                                    onToggleCoverVisualizer(true)
                                }
                            },
                            onLongClick = {
                                onCycleVisualizerStyle?.invoke()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = stringResource(R.string.switch_visualizer_style),
                        tint = if (vizActive) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    if (vizActive) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(OrbitTheme.colors.tertiary)
                        )
                    }
                }

                // 三点菜单
                IconButton(
                    onClick = { showAddToPlaylistDialog = true },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = OrbitTheme.colors.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 4.5 仿 Poweramp 殿堂级动态频谱视效视图 (当封面替换为全尺寸大频谱时自动折叠，避免视觉重复)
        val spectrumVisualizerView: @Composable () -> Unit = {
            AnimatedVisibility(
                visible = !showCoverVisualizer && equalizerUiState.visualizerEnabled && equalizerUiState.visualizerStyle != VisualizerStyle.OFF,
                enter = expandVertically(tween(250)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
            ) {
                PowerampSpectrumVisualizer(
                    magnitudes = visualizerFrame.rawMagnitudes,
                    peaks = visualizerFrame.peakCaps,
                    style = equalizerUiState.visualizerStyle,
                    colorScheme = equalizerUiState.visualizerColorScheme,
                    peakDecayEnabled = equalizerUiState.visualizerPeakDecayEnabled,
                    isPlaying = playbackState.isPlaying,
                    barWidthDp = equalizerUiState.visualizerBarWidthDp,
                    barAlpha = equalizerUiState.visualizerBarAlpha,
                    borderWidthDp = equalizerUiState.visualizerBarBorderWidthDp,
                    borderColor = equalizerUiState.visualizerBarBorderColor,
                    borderAlpha = equalizerUiState.visualizerBarBorderAlpha,
                    borderOnly = equalizerUiState.visualizerBarBorderOnly,
                    customColor = equalizerUiState.visualizerCustomColor,
                    customColor2 = equalizerUiState.visualizerCustomColor2,
                    isSingleColor = equalizerUiState.visualizerSingleColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLandscape) 40.dp else 46.dp)
                        .padding(horizontal = 4.dp),
                    onClick = { onCycleVisualizerStyle?.invoke() }
                )
            }
        }

        // 5. 进度条与时间视图
        val progressSliderView: @Composable () -> Unit = {
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
                    currentPositionMs = currentPosMs,
                    durationMs = playbackState.durationMs,
                    trailStyle = equalizerUiState.progressTrailStyle,
                    startWidthDp = equalizerUiState.trailStartWidth,
                    endWidthDp = equalizerUiState.trailEndWidth,
                    orbitRadiusDp = equalizerUiState.trailOrbitRadius,
                    color1 = equalizerUiState.trailColor1,
                    color2 = equalizerUiState.trailColor2,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatTime(currentPosMs), fontSize = 11.sp, color = OrbitTheme.colors.textSecondary)
                    Text(text = formatTime(playbackState.durationMs), fontSize = 11.sp, color = OrbitTheme.colors.textSecondary)
                }
            }
        }

        // 6. 播放控制按键栏 (Shuffle, Prev, Play/Pause, Next, Repeat)
        val controlsRowView: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 随机播放
                Box(contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = {
                            val toastResId = viewModel.toggleShuffle()
                            Toast.makeText(context, context.getString(toastResId) as CharSequence, Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (playbackState.isShuffleEnabled) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // 开启随机播放且非标准策略时，右上角浮现微标提示 (♥ / ★)，无背景色
                    if (playbackState.isShuffleEnabled && playbackState.shuffleStrategy != ShuffleStrategy.STANDARD) {
                        val badgeColor = if (playbackState.shuffleStrategy == ShuffleStrategy.FAVORITE_FIRST) {
                            Color(0xFFFF3366)
                        } else {
                            OrbitTheme.colors.primary
                        }
                        Text(
                            text = if (playbackState.shuffleStrategy == ShuffleStrategy.FAVORITE_FIRST) "♥" else "★",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = badgeColor,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 1.dp, y = 2.dp)
                        )
                    }
                }

                // 上一曲
                IconButton(
                    onClick = { viewModel.playPrevious() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = OrbitTheme.colors.textPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // 大播放/暂停圆形按键
                val playBtnSize = if (isLandscape) 56.dp else 66.dp
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(playBtnSize)
                        .clip(CircleShape)
                        .background(
                            if (OrbitTheme.colors.isDark) Color(0xFFFF6A3D) else Color(0xFFF2541B)
                        )
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(if (isLandscape) 32.dp else 38.dp)
                    )
                }

                // 下一曲
                IconButton(
                    onClick = { viewModel.playNext() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = OrbitTheme.colors.textPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // 循环模式
                IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                    val (icon, tint) = when (playbackState.repeatMode) {
                        RepeatMode.OFF -> Icons.Default.Repeat to OrbitTheme.colors.textSecondary
                        RepeatMode.ALL -> Icons.Default.Repeat to OrbitTheme.colors.primary
                        RepeatMode.ONE -> Icons.Default.RepeatOne to OrbitTheme.colors.primary
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repeat",
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        if (isLandscape) {
            // ========== 专业横屏唱片与歌词分屏布局 (左右 1:1 对称均分平衡布局) ==========
            Row(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .swipeToChangeSong(
                        onSwipeNext = { viewModel.playNext() },
                        onSwipePrevious = { viewModel.playPrevious() }
                    ),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧唱片信息区 (对称均分 50% 空间)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        coverView()
                    }
                    trackInfoView()
                    quickActionsView()
                }

                // 右侧全景歌词与控制区 (对称均分 50% 空间)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    lyricsView(Modifier.weight(1f).fillMaxWidth())
                    spectrumVisualizerView()
                    Spacer(modifier = Modifier.height(2.dp))
                    progressSliderView()
                    Spacer(modifier = Modifier.height(4.dp))
                    controlsRowView()
                }
            }
        } else {
            // ========== 标准竖屏布局 ==========
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 4.dp)
                    .swipeToChangeSong(
                        onSwipeNext = { viewModel.playNext() },
                        onSwipePrevious = { viewModel.playPrevious() }
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp, bottom = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    coverView()
                }

                Spacer(modifier = Modifier.height(4.dp))
                lyricsView(Modifier.fillMaxWidth().height(105.dp))
                Spacer(modifier = Modifier.height(6.dp))
                trackInfoView()
                Spacer(modifier = Modifier.height(6.dp))
                quickActionsView()
                spectrumVisualizerView()
                progressSliderView()
                Spacer(modifier = Modifier.height(10.dp))
                controlsRowView()
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // 当前播放队列弹层 (点击右上角按钮弹出)
    if (showQueueBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueBottomSheet = false },
            containerColor = OrbitTheme.colors.surfaceCard,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            val currentQueue: List<Song> = playbackState.currentPlaylist
            var queueSearchQuery by remember { mutableStateOf("") }
            val showCoverInQueue = equalizerUiState.showCoverInQueue

            val filteredQueue = remember(currentQueue, queueSearchQuery) {
                if (queueSearchQuery.isBlank()) {
                    currentQueue
                } else {
                    val q = queueSearchQuery.trim()
                    currentQueue.filter { s ->
                        s.title.contains(q, ignoreCase = true) ||
                        s.artist.contains(q, ignoreCase = true) ||
                        s.album.contains(q, ignoreCase = true)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
            ) {
                // 顶部标题与控制行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = OrbitTheme.colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "当前播放队列",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrbitTheme.colors.textPrimary
                        )
                        Text(
                            text = if (queueSearchQuery.isBlank()) "(${currentQueue.size})" else "(${filteredQueue.size}/${currentQueue.size})",
                            fontSize = 13.sp,
                            color = OrbitTheme.colors.textSecondary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 专辑封面显示/隐藏开关
                        IconButton(
                            onClick = { onToggleCoverInQueue(!showCoverInQueue) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (showCoverInQueue) Icons.Default.Image else Icons.Default.HideImage,
                                contentDescription = if (showCoverInQueue) "隐藏封面" else "显示封面",
                                tint = if (showCoverInQueue) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = { showQueueBottomSheet = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = OrbitTheme.colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 队列内搜索栏 (Search Bar)
                OutlinedTextField(
                    value = queueSearchQuery,
                    onValueChange = { queueSearchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_hint),
                            fontSize = 13.sp,
                            color = OrbitTheme.colors.textSecondary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = OrbitTheme.colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (queueSearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { queueSearchQuery = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = OrbitTheme.colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = OrbitTheme.colors.surface,
                        unfocusedContainerColor = OrbitTheme.colors.surface,
                        focusedBorderColor = OrbitTheme.colors.primary.copy(alpha = 0.6f),
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = OrbitTheme.colors.primary,
                        focusedTextColor = OrbitTheme.colors.textPrimary,
                        unfocusedTextColor = OrbitTheme.colors.textPrimary
                    )
                )

                HorizontalDivider(color = OrbitTheme.colors.textSecondary.copy(alpha = 0.15f))

                if (filteredQueue.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (queueSearchQuery.isNotBlank()) "未找到相关歌曲" else "播放队列为空",
                            color = OrbitTheme.colors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    val queueListState = rememberLazyListState()
                    val currentSongId = playbackState.currentSong?.id

                    // 自动滚动到当前播放歌曲位置 (仅未搜索时触发)
                    if (queueSearchQuery.isBlank()) {
                        LaunchedEffect(Unit) {
                            val idx = currentQueue.indexOfFirst { it.id == currentSongId }
                            if (idx >= 0) {
                                queueListState.scrollToItem(maxOf(0, idx - 2))
                            }
                        }
                    }

                    LazyColumn(
                        state = queueListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(
                            items = filteredQueue,
                            key = { index: Int, s: Song -> "${s.id}_$index" }
                        ) { index: Int, qSong: Song ->
                            val isCurrent = qSong.id == currentSongId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isCurrent) OrbitTheme.colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                                    .clickable {
                                        // 获取其在原完整播放队列中的实际索引进行播放
                                        val originalIndex = currentQueue.indexOfFirst { it.id == qSong.id }
                                        viewModel.playSong(currentQueue, if (originalIndex >= 0) originalIndex else index)
                                    }
                                    .padding(horizontal = 10.dp, vertical = if (showCoverInQueue) 6.dp else 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 序号或正在播放指示
                                if (isCurrent) {
                                    Icon(
                                        imageVector = if (playbackState.isPlaying) Icons.Default.Equalizer else Icons.Default.Pause,
                                        contentDescription = null,
                                        tint = OrbitTheme.colors.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 12.sp,
                                        color = OrbitTheme.colors.textSecondary,
                                        modifier = Modifier.width(18.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // 可选显示的专辑封面微缩图
                                if (showCoverInQueue) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(OrbitTheme.colors.surface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (qSong.albumArtUri != null) {
                                            AsyncImage(
                                                model = qSong.albumArtUri,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = OrbitTheme.colors.primary.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = qSong.title,
                                        fontSize = 14.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isCurrent) OrbitTheme.colors.primary else OrbitTheme.colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = "${qSong.artist} • ${qSong.album}",
                                        fontSize = 11.sp,
                                        color = OrbitTheme.colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                if (qSong.isFavorite) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = "Favorite",
                                        tint = Color(0xFFFF3366),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                } else if (qSong.isDisliked) {
                                    Icon(
                                        imageVector = Icons.Default.ThumbDown,
                                        contentDescription = "Disliked",
                                        tint = Color(0xFFE57373),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                Text(
                                    text = qSong.formattedDuration,
                                    fontSize = 11.sp,
                                    color = OrbitTheme.colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 添加到播放列表对话框
    if (showAddToPlaylistDialog && song != null) {
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.add_to_playlist),
                    color = OrbitTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    Text(
                        text = song.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = OrbitTheme.colors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // 快速新建播放列表入口
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                newPlaylistName = ""
                                showCreatePlaylistDialog = true
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = OrbitTheme.colors.primary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.create_new_playlist),
                            color = OrbitTheme.colors.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    HorizontalDivider(
                        color = OrbitTheme.colors.textSecondary.copy(alpha = 0.15f),
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    if (playlists.isEmpty()) {
                        Text(
                            text = stringResource(R.string.empty_playlist_hint),
                            fontSize = 12.sp,
                            color = OrbitTheme.colors.textSecondary,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(playlists, key = { it.id }) { pl ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            viewModel.addSongToPlaylist(pl.id, song.id)
                                            showAddToPlaylistDialog = false
                                            Toast.makeText(context, context.getString(R.string.added_to_playlist_success, pl.name), Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                        contentDescription = null,
                                        tint = OrbitTheme.colors.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pl.name,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = OrbitTheme.colors.textPrimary
                                        )
                                        Text(
                                            text = stringResource(R.string.tracks_count, pl.songCount),
                                            fontSize = 11.sp,
                                            color = OrbitTheme.colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddToPlaylistDialog = false }) {
                    Text(stringResource(R.string.btn_cancel), color = OrbitTheme.colors.textSecondary)
                }
            },
            containerColor = OrbitTheme.colors.surfaceDialog
        )
    }

    // 新建播放列表弹窗
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.create_new_playlist),
                    color = OrbitTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text(stringResource(R.string.playlist_name_hint)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OrbitTheme.colors.primary,
                        unfocusedBorderColor = OrbitTheme.colors.textSecondary.copy(alpha = 0.5f),
                        focusedLabelColor = OrbitTheme.colors.primary,
                        cursorColor = OrbitTheme.colors.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newPlaylistName.trim()
                        if (name.isNotEmpty()) {
                            viewModel.createPlaylist(name)
                            showCreatePlaylistDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary)
                ) {
                    Text(stringResource(R.string.btn_ok), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text(stringResource(R.string.btn_cancel), color = OrbitTheme.colors.textSecondary)
                }
            },
            containerColor = OrbitTheme.colors.surfaceDialog
        )
    }

    // 全屏最大化沉浸大频谱浮层
    AnimatedVisibility(
        visible = equalizerUiState.isVisualizerMaximized,
        enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.96f, animationSpec = tween(300)),
        exit = fadeOut(tween(250)) + scaleOut(targetScale = 0.96f, animationSpec = tween(250))
    ) {
        MaximizedVisualizerOverlay(
            playbackState = playbackState,
            visualizerFrame = visualizerFrame,
            equalizerUiState = equalizerUiState,
            song = song,
            onBack = { onToggleVisualizerMaximized(false) },
            onCycleVisualizerStyle = onCycleVisualizerStyle,
            onToggleMaximizedShowCover = onToggleMaximizedShowCover,
            onToggleMaximizedCoverPosition = onToggleMaximizedCoverPosition,
            onToggleMaximizedShowControls = onToggleMaximizedShowControls,
            onSetMaximizedCoverAlpha = onSetMaximizedCoverAlpha,
            onTogglePlay = { viewModel.togglePlayPause() },
            onPlayNext = { viewModel.playNext() },
            onPlayPrevious = { viewModel.playPrevious() },
            onSeekTo = { viewModel.seekTo((it * playbackState.durationMs).toLong()) }
        )
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
    currentPositionMs: Long = 0L,
    durationMs: Long = 0L,
    trailStyle: String = ProgressTrailStyle.NEON_PULSE.id,
    startWidthDp: Float = 3.8f,
    endWidthDp: Float = 1.2f,
    orbitRadiusDp: Float = 9.5f,
    color1: Long = 0xFF00FFFFL,
    color2: Long = 0xFF5E72E4L,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    // 高精度时间戳外推基准点，确保随屏幕 60/120Hz 刷新率无级平滑推移
    var lastSyncPositionMs by remember { mutableLongStateOf(currentPositionMs) }
    var lastSyncTimestamp by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }

    LaunchedEffect(currentPositionMs, value, isPlaying) {
        lastSyncPositionMs = if (currentPositionMs > 0L) currentPositionMs else (value * durationMs).toLong()
        lastSyncTimestamp = android.os.SystemClock.elapsedRealtime()
    }

    // 星光柔和脉冲动画 (缩放速度与旋转速度适度放缓)
    val infiniteTransition = rememberInfiniteTransition(label = "NowPlayingStarAnim")

    val twinkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = if (isPlaying) 0.95f else 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "twinkleAlpha"
    )

    val twinkleScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = if (isPlaying) 1.12f else 0.94f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "twinkleScale"
    )

    val starRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "starRotation"
    )

    // 双拖尾三维围绕旋转相位 (Double Helix Swirling Phase)
    val tailRotationPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "tailRotationPhase"
    )

    // 预分配复用数据缓冲 (完全消除每帧对象分配与 GC 压力)
    // 霓虹管: 32 段 (33 个点，每点 5 个 Float: x, y, z, alpha, width)
    val neonSegments = 32
    val neon1Buf = remember { FloatArray((neonSegments + 1) * 5) }
    val neon2Buf = remember { FloatArray((neonSegments + 1) * 5) }

    // 彗星双拖尾: 24 段 (25 个点，每点 6 个 Float: x, y, z, u, alpha, width)
    val cometSegments = 24
    val comet1Buf = remember { FloatArray((cometSegments + 1) * 6) }
    val comet2Buf = remember { FloatArray((cometSegments + 1) * 6) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    dragProgress = newProgress
                    onValueChange(newProgress)
                    onValueChangeFinished()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        onValueChange(dragProgress)
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinished()
                    },
                    onDragCancel = {
                        isDragging = false
                        onValueChangeFinished()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        onValueChange(dragProgress)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val primaryColor = OrbitTheme.colors.primary
        val secondaryColor = OrbitTheme.colors.secondary
        val surfaceTrackColor = OrbitTheme.colors.surface
        val selectedStyle = ProgressTrailStyle.fromId(trailStyle)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val centerY = h / 2f
            val trackHeight = 4.dp.toPx()

            // 核心平滑进度计算：当未手动拖动且处于播放态时，利用系统真实运行时间戳高频插值推进，彻底消除跳帧感
            val activeProgress = if (isDragging) {
                dragProgress.coerceIn(0f, 1f)
            } else if (isPlaying && durationMs > 0L) {
                val now = android.os.SystemClock.elapsedRealtime()
                val elapsed = (now - lastSyncTimestamp).coerceAtLeast(0L)
                val estimatedMs = (lastSyncPositionMs + elapsed).coerceAtMost(durationMs)
                (estimatedMs.toFloat() / durationMs).coerceIn(0f, 1f)
            } else {
                value.coerceIn(0f, 1f)
            }
            val thumbX = activeProgress * w

            // 1. 底层暗调透光轨道 (Inactive Track)
            drawRoundRect(
                color = surfaceTrackColor,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = androidx.compose.ui.geometry.Size(w, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f, trackHeight / 2f)
            )

            // 2. 根据用户设置渲染不同拖尾动效
            when (selectedStyle) {
                ProgressTrailStyle.NEON_PULSE -> {
                    // ==================== 样式一：高能霓虹双发光管 (零对象分配极速绘制) ====================
                    val maxTailLength = 76.dp.toPx()
                    val tailLength = minOf(thumbX, maxTailLength)

                    if (tailLength > 3f) {
                        val maxAmp = orbitRadiusDp.dp.toPx()
                        val phaseRad = tailRotationPhase * (PI.toFloat() / 180f)
                        val startWidthPx = startWidthDp.dp.toPx()
                        val endWidthPx = endWidthDp.dp.toPx()
                        val minWidthPx = 0.5f.dp.toPx()

                        // 原地填充缓冲数据 (0 GC Allocation)
                        fun fillNeon(buf: FloatArray, phaseOffset: Float) {
                            val pi = PI.toFloat()
                            for (i in 0..neonSegments) {
                                val u = i / neonSegments.toFloat()
                                val x = thumbX - u * tailLength
                                val envelope = (sin(u * pi)).pow(0.85f) * (1f - 0.16f * u)
                                val amp = maxAmp * envelope
                                val theta = phaseRad - u * (3.2f * pi) + phaseOffset
                                val y = centerY + amp * sin(theta)
                                val z = cos(theta)
                                val depthAlpha = 0.65f + 0.35f * ((z + 1f) * 0.5f)
                                val alpha = ((1f - u).pow(1.05f) * twinkleAlpha * depthAlpha).coerceIn(0f, 1f)
                                val baseW = startWidthPx * (1f - u) + endWidthPx * u
                                val width = (baseW * (1f + 0.28f * z)).coerceAtLeast(minWidthPx)

                                val baseIdx = i * 5
                                buf[baseIdx] = x
                                buf[baseIdx + 1] = y
                                buf[baseIdx + 2] = z
                                buf[baseIdx + 3] = alpha
                                buf[baseIdx + 4] = width
                            }
                        }

                        fillNeon(neon1Buf, 0f)
                        fillNeon(neon2Buf, PI.toFloat())

                        val c1 = Color(color1)
                        val c2 = Color(color2)

                        val tube1Bloom = c1.copy(alpha = 0.9f)
                        val tube1Neon = c1
                        val tube1Solid = androidx.compose.ui.graphics.lerp(c1, Color.White, 0.22f)
                        val tube1Core = androidx.compose.ui.graphics.lerp(c1, Color.White, 0.82f)

                        val tube2Bloom = c2.copy(alpha = 0.9f)
                        val tube2Neon = c2
                        val tube2Solid = androidx.compose.ui.graphics.lerp(c2, Color.White, 0.22f)
                        val tube2Core = androidx.compose.ui.graphics.lerp(c2, Color.White, 0.82f)

                        fun drawNeonFromBuf(
                            buf: FloatArray,
                            isForeground: Boolean,
                            bloomColor: Color,
                            neonColor: Color,
                            solidColor: Color,
                            coreColor: Color
                        ) {
                            val fgFactor = if (isForeground) 1.0f else 0.70f
                            for (i in 0 until neonSegments) {
                                val idx1 = i * 5
                                val idx2 = (i + 1) * 5
                                val z1 = buf[idx1 + 2]
                                val z2 = buf[idx2 + 2]
                                val avgZ = (z1 + z2) * 0.5f
                                val isFg = avgZ >= 0f
                                if (isFg == isForeground) {
                                    val a1 = buf[idx1 + 3]
                                    val a2 = buf[idx2 + 3]
                                    val segAlpha = ((a1 + a2) * 0.5f).coerceIn(0f, 1f)
                                    if (segAlpha > 0.01f) {
                                        val p1 = Offset(buf[idx1], buf[idx1 + 1])
                                        val p2 = Offset(buf[idx2], buf[idx2 + 1])
                                        val strokeW = (buf[idx1 + 4] + buf[idx2 + 4]) * 0.5f

                                        // 1. 最外层广域柔光漫射光晕
                                        drawLine(
                                            color = bloomColor.copy(alpha = segAlpha * 0.40f * fgFactor),
                                            start = p1,
                                            end = p2,
                                            strokeWidth = strokeW * 2.8f,
                                            cap = StrokeCap.Round
                                        )
                                        // 2. 次外层鲜艳电离辉光
                                        drawLine(
                                            color = neonColor.copy(alpha = segAlpha * 0.85f * fgFactor),
                                            start = p1,
                                            end = p2,
                                            strokeWidth = strokeW * 1.55f,
                                            cap = StrokeCap.Round
                                        )
                                        // 3. 第三层实心发光管壁
                                        drawLine(
                                            color = solidColor.copy(alpha = segAlpha * 0.95f * fgFactor),
                                            start = p1,
                                            end = p2,
                                            strokeWidth = strokeW * 0.95f,
                                            cap = StrokeCap.Round
                                        )
                                        // 4. 最内层耀眼纯白电弧核心
                                        drawLine(
                                            color = coreColor.copy(alpha = segAlpha * 0.98f * fgFactor),
                                            start = p1,
                                            end = p2,
                                            strokeWidth = strokeW * 0.38f,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }
                        }

                        // 霓虹双线条·后景
                        drawNeonFromBuf(neon1Buf, false, tube1Bloom, tube1Neon, tube1Solid, tube1Core)
                        drawNeonFromBuf(neon2Buf, false, tube2Bloom, tube2Neon, tube2Solid, tube2Core)

                        // 流光轨道基座
                        if (thumbX > 0f) {
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        primaryColor.copy(alpha = 0.75f),
                                        primaryColor,
                                        secondaryColor
                                    ),
                                    startX = 0f,
                                    endX = thumbX.coerceAtLeast(1f)
                                ),
                                topLeft = Offset(0f, centerY - trackHeight / 2f),
                                size = androidx.compose.ui.geometry.Size(thumbX, trackHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f, trackHeight / 2f)
                            )
                        }

                        // 霓虹双线条·前景
                        drawNeonFromBuf(neon1Buf, true, tube1Bloom, tube1Neon, tube1Solid, tube1Core)
                        drawNeonFromBuf(neon2Buf, true, tube2Bloom, tube2Neon, tube2Solid, tube2Core)
                    } else if (thumbX > 0f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    primaryColor.copy(alpha = 0.75f),
                                    primaryColor,
                                    secondaryColor
                                ),
                                startX = 0f,
                                endX = thumbX.coerceAtLeast(1f)
                            ),
                            topLeft = Offset(0f, centerY - trackHeight / 2f),
                            size = androidx.compose.ui.geometry.Size(thumbX, trackHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f, trackHeight / 2f)
                        )
                    }
                }

                ProgressTrailStyle.COMET_HELIX -> {
                    // ==================== 样式二：彗星双拖尾 3D 缠绕模型 (零内存分配) ====================
                    val maxTailLength = 68.dp.toPx()
                    val tailLength = minOf(thumbX, maxTailLength)
                    val maxAmp = orbitRadiusDp.dp.toPx()
                    val phaseRad = tailRotationPhase * (PI.toFloat() / 180f)
                    val startWidthPx = startWidthDp.dp.toPx()
                    val endWidthPx = endWidthDp.dp.toPx()

                    if (tailLength > 3f) {
                        val pi = PI.toFloat()
                        fun fillComet(buf: FloatArray, phaseOffset: Float) {
                            for (i in 0..cometSegments) {
                                val u = i / cometSegments.toFloat()
                                val x = thumbX - u * tailLength
                                val envelope = (sin(u * pi)).pow(0.85f) * (1f - 0.2f * u)
                                val amp = maxAmp * envelope
                                val theta = phaseRad - u * (3.0f * pi) + phaseOffset
                                val y = centerY + amp * sin(theta)
                                val z = cos(theta)

                                val depthAlpha = 0.55f + 0.45f * ((z + 1f) * 0.5f)
                                val alpha = ((1f - u).pow(1.1f) * twinkleAlpha * depthAlpha).coerceIn(0f, 1f)
                                val baseW = startWidthPx * (1f - u) + endWidthPx * u
                                val strokeW = (baseW * (1f + 0.25f * z)).coerceAtLeast(0.5f)

                                val baseIdx = i * 6
                                buf[baseIdx] = x
                                buf[baseIdx + 1] = y
                                buf[baseIdx + 2] = z
                                buf[baseIdx + 3] = u
                                buf[baseIdx + 4] = alpha
                                buf[baseIdx + 5] = strokeW
                            }
                        }

                        fillComet(comet1Buf, 0f)
                        fillComet(comet2Buf, PI.toFloat())

                        val cometC1 = Color(color1)
                        val cometC2 = Color(color2)

                        fun drawCometFromBuf(buf: FloatArray, isForeground: Boolean, coreColor: Color, glowColor: Color) {
                            for (i in 0 until cometSegments) {
                                val idx1 = i * 6
                                val idx2 = (i + 1) * 6
                                val z1 = buf[idx1 + 2]
                                val z2 = buf[idx2 + 2]
                                val avgZ = (z1 + z2) * 0.5f
                                val isFg = avgZ >= 0f
                                if (isFg == isForeground) {
                                    val segAlpha = ((buf[idx1 + 4] + buf[idx2 + 4]) * 0.5f).coerceIn(0f, 1f)
                                    if (segAlpha > 0.02f) {
                                        val p1 = Offset(buf[idx1], buf[idx1 + 1])
                                        val p2 = Offset(buf[idx2], buf[idx2 + 1])
                                        val strokeW = (buf[idx1 + 5] + buf[idx2 + 5]) * 0.5f
                                        drawLine(
                                            color = glowColor.copy(alpha = segAlpha * 0.45f),
                                            start = p1,
                                            end = p2,
                                            strokeWidth = strokeW * 1.5f,
                                            cap = StrokeCap.Round
                                        )
                                        drawLine(
                                            color = coreColor.copy(alpha = segAlpha * 0.95f),
                                            start = p1,
                                            end = p2,
                                            strokeWidth = strokeW * 0.55f,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }

                            val sampleIndices = intArrayOf(2, 6, 11, 17)
                            for (idx in sampleIndices) {
                                if (idx <= cometSegments) {
                                    val baseIdx = idx * 6
                                    val z = buf[baseIdx + 2]
                                    val isFg = z >= 0f
                                    val a = buf[baseIdx + 4]
                                    if (isFg == isForeground && a > 0.05f) {
                                        val u = buf[baseIdx + 3]
                                        val r = (if (isFg) 1.8.dp.toPx() else 1.1.dp.toPx()) * (1f - u * 0.5f)
                                        val center = Offset(buf[baseIdx], buf[baseIdx + 1])
                                        drawCircle(
                                            color = glowColor.copy(alpha = a * 0.65f),
                                            radius = r * 1.8f,
                                            center = center
                                        )
                                        drawCircle(
                                            color = Color.White.copy(alpha = a * 0.95f),
                                            radius = r,
                                            center = center
                                        )
                                    }
                                }
                            }
                        }

                        // 彗星双拖尾·后景
                        drawCometFromBuf(comet1Buf, false, Color.White, cometC1)
                        drawCometFromBuf(comet2Buf, false, androidx.compose.ui.graphics.lerp(cometC2, Color.White, 0.7f), cometC2)

                        // 流光基座轨道
                        if (thumbX > 0f) {
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        primaryColor.copy(alpha = 0.75f),
                                        primaryColor,
                                        secondaryColor
                                    ),
                                    startX = 0f,
                                    endX = thumbX.coerceAtLeast(1f)
                                ),
                                topLeft = Offset(0f, centerY - trackHeight / 2f),
                                size = androidx.compose.ui.geometry.Size(thumbX, trackHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f, trackHeight / 2f)
                            )
                        }

                        // 彗星双拖尾·前景
                        drawCometFromBuf(comet1Buf, true, Color.White, cometC1)
                        drawCometFromBuf(comet2Buf, true, androidx.compose.ui.graphics.lerp(cometC2, Color.White, 0.7f), cometC2)
                    }
                }

                ProgressTrailStyle.MINIMAL -> {
                    // ==================== 样式三：经典极简 ====================
                    if (thumbX > 0f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    primaryColor.copy(alpha = 0.75f),
                                    primaryColor,
                                    secondaryColor
                                ),
                                startX = 0f,
                                endX = thumbX.coerceAtLeast(1f)
                            ),
                            topLeft = Offset(0f, centerY - trackHeight / 2f),
                            size = androidx.compose.ui.geometry.Size(thumbX, trackHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f, trackHeight / 2f)
                        )
                    }
                }
            }

            val thumbPos = Offset(thumbX, centerY)

            // 3. 漫反射外散微光晕
            val haloRadius = 15.dp.toPx() * twinkleScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.7f * twinkleAlpha),
                        primaryColor.copy(alpha = 0.22f * twinkleAlpha),
                        Color.Transparent
                    ),
                    center = thumbPos,
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = thumbPos
            )

            // 4. 旋转四角星芒十字光辉
            rotate(degrees = starRotation, pivot = thumbPos) {
                val rayLength = 8.5.dp.toPx() * twinkleScale
                val rayWidth = 1.4.dp.toPx()

                // 水平星芒
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.95f * twinkleAlpha),
                            primaryColor,
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
                            primaryColor,
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

            // 5. 纯白钻石超亮星核 (微调至 3.2dp)
            drawCircle(
                color = Color.White.copy(alpha = 0.98f * twinkleAlpha),
                radius = 3.2.dp.toPx() * twinkleScale.coerceAtLeast(0.9f),
                center = thumbPos
            )

            // 6. 外圈微光环 (微调至 4.8dp)
            drawCircle(
                color = primaryColor.copy(alpha = 0.92f * twinkleAlpha),
                radius = 4.8.dp.toPx() * twinkleScale,
                center = thumbPos,
                style = Stroke(width = 1.1.dp.toPx())
            )
        }
    }
}

/**
 * 全屏最大化沉浸大频谱视图 (Maximized Visualizer Overlay)
 * 1. 背景铺满全尺寸动态频谱，支持自适应单条宽度与自定义颜色
 * 2. 悬浮专辑封面：支持显隐控制、居左/居右自由切换
 * 3. 悬浮底部控制栏：支持显隐控制、流畅进度拖拽与切歌控制
 * 4. 顶部悬浮操作胶囊栏：一键开关封面、切换位置、开关控制栏、切换样式、退出全屏
 */
@Composable
private fun MaximizedVisualizerOverlay(
    playbackState: PlaybackState,
    visualizerFrame: VisualizerFrame,
    equalizerUiState: EqualizerUiState,
    song: Song?,
    onBack: () -> Unit,
    onCycleVisualizerStyle: (() -> Unit)?,
    onToggleMaximizedShowCover: (Boolean) -> Unit,
    onToggleMaximizedCoverPosition: (Boolean) -> Unit,
    onToggleMaximizedShowControls: (Boolean) -> Unit,
    onSetMaximizedCoverAlpha: (Float) -> Unit,
    onTogglePlay: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onSeekTo: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            configuration.screenWidthDp > configuration.screenHeightDp
    val currentStyle = if (equalizerUiState.visualizerStyle == VisualizerStyle.OFF) {
        VisualizerStyle.BARS_WITH_PEAKS
    } else {
        equalizerUiState.visualizerStyle
    }

    var isDraggingSlider by remember { mutableStateOf(false) }
    var draggingProgress by remember { mutableFloatStateOf(0f) }
    val progress = if (isDraggingSlider) draggingProgress else playbackState.progress
    val currentPosMs = if (isDraggingSlider) (draggingProgress * playbackState.durationMs).toLong() else playbackState.currentPositionMs

    var showTopControlBar by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { showTopControlBar = !showTopControlBar }
            )
    ) {
        // 0. 全局沉浸背景层 (彻底遮挡普通播放页面的大封面与按钮，呈现纯净壁纸/暗黑底色)
        AppBackgroundLayer(
            customBackgroundPath = equalizerUiState.customBackgroundPath,
            blurRadius = equalizerUiState.backgroundBlurRadius,
            blurStyle = equalizerUiState.backgroundBlurStyle,
            dimAlpha = equalizerUiState.backgroundDimAlpha
        )

        // 1. 全屏底层动态频谱渲染 (完全触底与横向铺满，点击屏幕任意位置可显/隐上方设置条)
        PowerampSpectrumVisualizer(
            magnitudes = visualizerFrame.rawMagnitudes,
            peaks = visualizerFrame.peakCaps,
            style = currentStyle,
            colorScheme = equalizerUiState.visualizerColorScheme,
            peakDecayEnabled = equalizerUiState.visualizerPeakDecayEnabled,
            isPlaying = playbackState.isPlaying,
            barWidthDp = equalizerUiState.visualizerBarWidthDp,
            barAlpha = equalizerUiState.visualizerBarAlpha,
            borderWidthDp = equalizerUiState.visualizerBarBorderWidthDp,
            borderColor = equalizerUiState.visualizerBarBorderColor,
            borderAlpha = equalizerUiState.visualizerBarBorderAlpha,
            borderOnly = equalizerUiState.visualizerBarBorderOnly,
            customColor = equalizerUiState.visualizerCustomColor,
            customColor2 = equalizerUiState.visualizerCustomColor2,
            isSingleColor = equalizerUiState.visualizerSingleColor,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 0.dp,
                    end = 0.dp,
                    top = if (isLandscape) 40.dp else 52.dp,
                    bottom = 0.dp
                ),
            onClick = { showTopControlBar = !showTopControlBar }
        )

        // 2. 悬浮专辑封面 (支持居左/居右与自定义透明度)
        AnimatedVisibility(
            visible = equalizerUiState.maximizedShowCover,
            enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.85f, animationSpec = tween(250)),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.85f, animationSpec = tween(200)),
            modifier = Modifier
                .align(
                    if (equalizerUiState.maximizedCoverOnRight) Alignment.CenterEnd else Alignment.CenterStart
                )
                .padding(
                    start = if (equalizerUiState.maximizedCoverOnRight) 0.dp else 18.dp,
                    end = if (equalizerUiState.maximizedCoverOnRight) 18.dp else 0.dp,
                    bottom = if (equalizerUiState.maximizedShowControls) (if (isLandscape) 48.dp else 84.dp) else 0.dp
                )
        ) {
            val coverSize = if (isLandscape) 210.dp else 240.dp
            Box(
                modifier = Modifier
                    .size(coverSize)
                    .alpha(equalizerUiState.maximizedCoverAlpha)
                    .shadow(24.dp, RoundedCornerShape(22.dp), spotColor = OrbitTheme.colors.primary.copy(alpha = 0.50f * equalizerUiState.maximizedCoverAlpha))
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF1A1A26).copy(alpha = equalizerUiState.maximizedCoverAlpha))
                    .border(1.5.dp, OrbitTheme.colors.primary.copy(alpha = 0.38f * equalizerUiState.maximizedCoverAlpha), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (song?.albumArtUri != null) {
                    AsyncImage(
                        model = song.albumArtUri,
                        contentDescription = song.album,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = OrbitTheme.colors.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        }

        // 3. 顶部悬浮操作胶囊栏 (支持点击屏幕平滑显示/隐藏)
        AnimatedVisibility(
            visible = showTopControlBar,
            enter = slideInVertically(tween(250)) { -it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(200)) { -it } + fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (isLandscape) 10.dp else 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color(0xDD181826),
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                modifier = Modifier
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 开关封面
                    IconButton(
                        onClick = { onToggleMaximizedShowCover(!equalizerUiState.maximizedShowCover) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (equalizerUiState.maximizedShowCover) Icons.Default.Image else Icons.Default.HideImage,
                            contentDescription = stringResource(if (equalizerUiState.maximizedShowCover) R.string.maximized_hide_cover else R.string.maximized_show_cover),
                            tint = if (equalizerUiState.maximizedShowCover) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 切换封面位置 (居左 / 居右，仅在显示封面时出现)
                    if (equalizerUiState.maximizedShowCover) {
                        IconButton(
                            onClick = { onToggleMaximizedCoverPosition(!equalizerUiState.maximizedCoverOnRight) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (equalizerUiState.maximizedCoverOnRight) Icons.Default.FormatAlignRight else Icons.Default.FormatAlignLeft,
                                contentDescription = stringResource(R.string.maximized_cover_position),
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 调节封面透明度 (循环切换 100% -> 75% -> 50% -> 25%)
                        IconButton(
                            onClick = {
                                val nextAlpha = when {
                                    equalizerUiState.maximizedCoverAlpha > 0.85f -> 0.75f
                                    equalizerUiState.maximizedCoverAlpha > 0.60f -> 0.50f
                                    equalizerUiState.maximizedCoverAlpha > 0.35f -> 0.25f
                                    else -> 1.0f
                                }
                                onSetMaximizedCoverAlpha(nextAlpha)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Opacity,
                                contentDescription = stringResource(R.string.maximized_cover_alpha),
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 开关底部播放控件
                    IconButton(
                        onClick = { onToggleMaximizedShowControls(!equalizerUiState.maximizedShowControls) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (equalizerUiState.maximizedShowControls) Icons.Default.PlayCircle else Icons.Default.PlayDisabled,
                            contentDescription = stringResource(if (equalizerUiState.maximizedShowControls) R.string.maximized_hide_controls else R.string.maximized_show_controls),
                            tint = if (equalizerUiState.maximizedShowControls) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 频谱样式切换药丸
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = OrbitTheme.colors.primary.copy(alpha = 0.15f),
                        modifier = Modifier.clickable { onCycleVisualizerStyle?.invoke() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            val styleText = when (currentStyle) {
                                VisualizerStyle.BARS_WITH_PEAKS -> stringResource(R.string.visualizer_style_bars_with_peaks)
                                VisualizerStyle.AURORA_MOUNTAIN -> stringResource(R.string.visualizer_style_aurora_mountain)
                                VisualizerStyle.MIRRORED_BARS -> stringResource(R.string.visualizer_style_mirrored_bars)
                                VisualizerStyle.OFF -> stringResource(R.string.visualizer_style_off)
                            }
                            Text(
                                text = styleText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = OrbitTheme.colors.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    // 退出最大化全屏按钮
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = stringResource(R.string.btn_cancel),
                            tint = OrbitTheme.colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // 4. 底部悬浮播放控制卡片 (纯净无背景，直接悬浮在动态频谱之上)
        AnimatedVisibility(
            visible = equalizerUiState.maximizedShowControls,
            enter = slideInVertically(tween(250)) { height -> height } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(200)) { height -> height } + fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = if (isLandscape) 48.dp else 16.dp, vertical = 12.dp)
                .widthIn(max = 580.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                // 歌曲信息行：当封面在左边显示时，歌曲名称和作者在右边显示
                val isCoverOnLeft = equalizerUiState.maximizedShowCover && !equalizerUiState.maximizedCoverOnRight
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (isCoverOnLeft) Arrangement.End else Arrangement.Start
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (isCoverOnLeft) Alignment.End else Alignment.Start
                    ) {
                        Text(
                            text = song?.title ?: "No Song",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = if (isCoverOnLeft) TextAlign.End else TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = song?.artist ?: "Unknown Artist",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = if (isCoverOnLeft) TextAlign.End else TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 进度条与时间
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(currentPosMs),
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    LuminousGlowingSlider(
                        value = progress,
                        onValueChange = {
                            isDraggingSlider = true
                            draggingProgress = it
                        },
                        onValueChangeFinished = {
                            isDraggingSlider = false
                            onSeekTo(draggingProgress)
                        },
                        isPlaying = playbackState.isPlaying,
                        currentPositionMs = currentPosMs,
                        durationMs = playbackState.durationMs,
                        trailStyle = equalizerUiState.progressTrailStyle,
                        startWidthDp = equalizerUiState.trailStartWidth,
                        endWidthDp = equalizerUiState.trailEndWidth,
                        orbitRadiusDp = equalizerUiState.trailOrbitRadius,
                        color1 = equalizerUiState.trailColor1,
                        color2 = equalizerUiState.trailColor2,
                        modifier = Modifier
                            .weight(1f)
                            .height(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatTime(playbackState.durationMs),
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }

                // 核心播放控制按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPlayPrevious,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(18.dp))

                    Surface(
                        shape = CircleShape,
                        color = OrbitTheme.colors.primary,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .size(46.dp)
                            .clickable { onTogglePlay() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(18.dp))

                    IconButton(
                        onClick = onPlayNext,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

