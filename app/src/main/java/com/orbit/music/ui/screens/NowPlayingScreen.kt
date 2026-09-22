package com.orbit.music.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import coil.compose.SubcomposeAsyncImage
import coil.size.Size
import com.orbit.music.data.provider.AudioCoverProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.orbit.music.R
import com.orbit.music.audio.PlaybackState
import com.orbit.music.audio.RepeatMode
import com.orbit.music.audio.ShuffleStrategy
import com.orbit.music.audio.VisualizerFrame
import com.orbit.music.data.model.ProgressTrailStyle
import com.orbit.music.data.model.Song
import com.orbit.music.data.model.SongAttitude
import com.orbit.music.data.model.VisualizerColorScheme
import com.orbit.music.data.model.VisualizerStyle
import com.orbit.music.ui.components.AppBackgroundLayer
import com.orbit.music.ui.components.EditSongTagsDialog
import com.orbit.music.utils.FastToast
import com.orbit.music.ui.components.PowerampSpectrumVisualizer
import com.orbit.music.ui.components.SelectAlbumCoverDialog
import com.orbit.music.ui.theme.*
import com.orbit.music.ui.utils.swipeToChangeSong
import com.orbit.music.ui.utils.swipeVerticalGesture
import com.orbit.music.ui.viewmodel.EqualizerUiState
import com.orbit.music.ui.viewmodel.MusicPlayerViewModel
import com.orbit.music.utils.LyricLine
import com.orbit.music.utils.LyricParser
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.orbit.music.data.model.AlbumItem
import com.orbit.music.data.model.ArtistItem
import com.orbit.music.data.model.FolderItem
import com.orbit.music.data.model.Playlist
import com.orbit.music.ui.viewmodel.LibraryTab
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
    isTabletMode: Boolean = false,
    onCycleVisualizerStyle: (() -> Unit)? = null,
    onToggleCoverVisualizer: (Boolean) -> Unit = {},
    onToggleShowLyrics: (Boolean) -> Unit = {},
    onToggleVisualizerMaximized: (Boolean) -> Unit = {},
    onToggleMaximizedShowCover: (Boolean) -> Unit = {},
    onToggleMaximizedCoverPosition: (Boolean) -> Unit = {},
    onToggleMaximizedCoverRotating: (Boolean) -> Unit = {},
    onToggleMaximizedShowControls: (Boolean) -> Unit = {},
    onSetMaximizedCoverAlpha: (Float) -> Unit = {},
    onToggleCoverInQueue: (Boolean) -> Unit = {},
    onToggleFollowCoverColor: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playbackState by viewModel.playbackState.collectAsState()
    val visualizerFrame by viewModel.visualizerFlow.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val dislikedSongs by viewModel.dislikedSongs.collectAsState()
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
    var showMoreOptionsMenu by remember { mutableStateOf(false) }
    var showEditSongTagsDialog by remember { mutableStateOf(false) }
    var showSongDetailInfoDialog by remember { mutableStateOf(false) }
    var currentSongMetadata by remember { mutableStateOf(com.orbit.music.data.model.SongMetadata()) }
    var showSelectAlbumCoverDialog by remember { mutableStateOf(false) }
    var candidateCovers by remember { mutableStateOf<List<com.orbit.music.data.cover.MusicBrainzCoverService.AlbumCoverCandidate>>(emptyList()) }
    var candidateArtistName by remember { mutableStateOf("") }
    var candidateSong by remember { mutableStateOf<com.orbit.music.data.model.Song?>(null) }
    var isApplyingCover by remember { mutableStateOf(false) }
    var isDownloadingCover by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // 当前播放队列弹层
    var showQueueBottomSheet by remember { mutableStateOf(false) }

    val currentProgress = if (isDraggingSlider) draggingProgress else playbackState.progress
    val currentPosMs = if (isDraggingSlider) (draggingProgress * playbackState.durationMs).toLong() else playbackState.currentPositionMs

    // 实时异步加载并解析同目录下同名歌词文件
    var lyricLines by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var songTechSpecs by remember { mutableStateOf<com.orbit.music.data.model.AudioTechSpecs?>(null) }
    var showDeleteSongDialog by remember { mutableStateOf(false) }
    var deleteLocalFileChecked by remember { mutableStateOf(false) }
    val coverVer by com.orbit.music.utils.CoverHelper.coverVersion.collectAsState()

    LaunchedEffect(song?.id, song?.path) {
        if (song != null) {
            lyricLines = withContext(Dispatchers.IO) {
                LyricParser.loadLyricForSong(song.path)
            }
            songTechSpecs = withContext(Dispatchers.IO) {
                com.orbit.music.data.model.SongMetadataHelper.extractTechSpecs(song)
            }
        } else {
            lyricLines = emptyList()
            songTechSpecs = null
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
            customSolidBackgroundColor = equalizerUiState.customSolidBackgroundColor,
            blurRadius = equalizerUiState.backgroundBlurRadius,
            blurStyle = equalizerUiState.backgroundBlurStyle,
            dimAlpha = equalizerUiState.backgroundDimAlpha
        )

        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
                configuration.screenWidthDp > configuration.screenHeightDp
        val useTabletThreeColumnLayout = isTabletMode && (isLandscape || configuration.screenWidthDp >= 600)

        Scaffold(
        topBar = {
            if (!isLandscape && !useTabletThreeColumnLayout) {
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
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = song?.album?.takeIf { it.isNotBlank() && it != "Unknown Album" } ?: "未知专辑",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OrbitTheme.colors.textPrimary,
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
            }
        },
        containerColor = OrbitTheme.colors.background
    ) { innerPadding ->
        // 1. 封面与大频谱可视化无缝切换视图 (竖屏横向最大化与呼吸感自适应，大画幅沉浸体验)
        val coverView: @Composable (Modifier) -> Unit = { coverViewModifier ->
            BoxWithConstraints(
                modifier = coverViewModifier,
                contentAlignment = Alignment.TopCenter
            ) {
                val availableW = maxWidth
                val availableH = maxHeight
                val dynamicCoverSize = if (useTabletThreeColumnLayout) {
                    val sizeW = if (availableW > 20.dp) availableW - 16.dp else availableW
                    val sizeH = if (availableH > 16.dp && availableH < 2000.dp) availableH - 16.dp else sizeW
                    minOf(sizeW, sizeH, 260.dp)
                } else if (isLandscape) {
                    150.dp
                } else {
                    val sizeW = if (availableW > 20.dp) availableW - 12.dp else availableW
                    val sizeH = if (availableH > 16.dp && availableH < 2000.dp) availableH - 12.dp else sizeW
                    if (sizeW < sizeH) sizeW else sizeH
                }

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
                                .height(if (useTabletThreeColumnLayout) dynamicCoverSize else (if (isLandscape) 150.dp else dynamicCoverSize))
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

                        // 全屏动态频谱 (若已处于最大化全屏浮层，则底层彻底卸载，避免双层 GLSurfaceView / 着色器重叠穿透与资源争夺)
                        if (!equalizerUiState.isVisualizerMaximized) {
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
                                backgroundLightColor = equalizerUiState.backgroundExtractedLightColor,
                                backgroundDarkColor = equalizerUiState.backgroundExtractedDarkColor,
                                resetTrigger = "${song?.id}_${playbackState.currentIndex}",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                onClick = { onCycleVisualizerStyle?.invoke() }
                            )
                        }

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
                                        VisualizerStyle.TIME_TUNNEL -> stringResource(R.string.visualizer_style_time_tunnel)
                                        VisualizerStyle.OCTGRAMS -> stringResource(R.string.visualizer_style_octgrams)
                                        VisualizerStyle.SOUND_CITY -> stringResource(R.string.visualizer_style_sound_city)
                                        VisualizerStyle.FRACTAL_GALAXY -> stringResource(R.string.visualizer_style_fractal_galaxy)
                                        VisualizerStyle.QUANTUM_VORTEX -> stringResource(R.string.visualizer_style_quantum_vortex)
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
                    // ========== 原生专辑封面视图 (精致正方形居中，横向最大化与呼吸感，支持向上滑动替换为大频谱，轻触切换歌词) ==========
                    Box(
                        modifier = Modifier
                            .size(dynamicCoverSize)
                            .scale(coverScale)
                            .shadow(
                                elevation = 20.dp,
                                shape = RoundedCornerShape(24.dp),
                                spotColor = OrbitTheme.colors.primary.copy(alpha = 0.38f)
                            )
                            .clip(RoundedCornerShape(24.dp))
                            .background(OrbitTheme.colors.surfaceCard)
                            .clickable {
                                onToggleShowLyrics(true)
                            }
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
                            targetState = Pair(song?.albumArtUri, coverVer),
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                            label = "AlbumArtAnim"
                        ) { (artUri, _) ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!artUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                                            .data(artUri)
                                            .memoryCacheKey("${artUri}_$coverVer")
                                            .diskCacheKey("${artUri}_$coverVer")
                                            .build(),
                                        contentDescription = song?.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = OrbitTheme.colors.primary,
                                        modifier = Modifier.size(if (isLandscape) 52.dp else 84.dp)
                                    )
                                }
                            }
                        }

                        // 封面下载进度提示遮罩
                        if (isDownloadingCover) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.65f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(36.dp),
                                        color = OrbitTheme.colors.primary,
                                        strokeWidth = 3.dp
                                    )
                                    Text(
                                        text = stringResource(R.string.cover_downloading),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }
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
        }

        // 2. 独占沉浸式整页歌词展示视图 (支持当前行主色高亮放大、平滑居中滚动、单行进度跳转、顶部与底部羽化渐隐虚化)
        val fullLyricListState = rememberLazyListState()
        LaunchedEffect(currentLyricIndex) {
            if (currentLyricIndex >= 0 && lyricLines.isNotEmpty()) {
                fullLyricListState.animateScrollToItem(maxOf(0, currentLyricIndex - 2))
            }
        }
        val fullLyricsView: @Composable (Modifier) -> Unit = { mod ->
            Box(
                modifier = mod
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onToggleShowLyrics(false) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (lyricLines.isNotEmpty()) {
                    LazyColumn(
                        state = fullLyricListState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        contentPadding = PaddingValues(top = 36.dp, bottom = 48.dp)
                    ) {
                        itemsIndexed(
                            items = lyricLines,
                            key = { idx: Int, line: LyricLine -> "${line.timeMs}_$idx" }
                        ) { index: Int, line: LyricLine ->
                            val isCurrent = index == currentLyricIndex
                            val textColor by animateColorAsState(
                                targetValue = if (isCurrent) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary.copy(alpha = 0.40f),
                                animationSpec = tween(280),
                                label = "FullLyricColor"
                            )
                            val lyricScale by animateFloatAsState(
                                targetValue = if (isCurrent) 1.08f else 1.0f,
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                                label = "FullLyricScale"
                            )

                            Text(
                                text = line.text,
                                fontSize = if (isCurrent) 19.sp else 15.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = textColor,
                                textAlign = TextAlign.Center,
                                lineHeight = if (isCurrent) 26.sp else 22.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .scale(lyricScale)
                                    .padding(vertical = 9.dp, horizontal = 16.dp)
                                    .clickable {
                                        viewModel.seekTo(line.timeMs)
                                    }
                            )
                        }
                    }

                    // 顶部与底部优雅的虚化羽化渐变遮罩 (悬浮虚空质感)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        OrbitTheme.colors.background,
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        OrbitTheme.colors.background
                                    )
                                )
                            )
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = OrbitTheme.colors.textSecondary.copy(alpha = 0.35f),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = stringResource(R.string.lyrics_empty_hint),
                            fontSize = 14.sp,
                            color = OrbitTheme.colors.textSecondary.copy(alpha = 0.45f),
                            textAlign = TextAlign.Center
                        )
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

        // 3.5 歌曲技术规格参数栏 (比特率、时长、格式、采样率等)
        val techSpecsView: @Composable (Modifier) -> Unit = { mod ->
            val specs = songTechSpecs
            val formatText = specs?.format?.uppercase() ?: (song?.mimeType?.takeIf { it.isNotBlank() }?.substringAfterLast('/')?.uppercase() ?: "AUDIO")
            val bitrateText = if ((specs?.bitrateKbps ?: 0) > 0) "${specs?.bitrateKbps} kbps" else ""
            val sampleRateText = if ((specs?.sampleRateHz ?: 0) > 0) {
                val sr = specs!!.sampleRateHz
                if (sr % 1000 == 0) "${sr / 1000} kHz" else "%.1f kHz".format(sr / 1000f)
            } else ""
            val bitDepthText = if ((specs?.bitDepth ?: 0) > 0) "${specs?.bitDepth} bit" else ""
            val durationText = specs?.durationFormatted?.ifBlank { song?.formattedDuration } ?: (song?.formattedDuration ?: "0:00")

            Row(
                modifier = mod,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 格式胶囊 (FLAC, MP3, WAV 等)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = OrbitTheme.colors.primary.copy(alpha = 0.15f),
                    border = BorderStroke(0.6.dp, OrbitTheme.colors.primary.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = formatText,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrbitTheme.colors.primary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }

                if (bitrateText.isNotBlank()) {
                    Text(
                        text = "•",
                        fontSize = 10.sp,
                        color = OrbitTheme.colors.textSecondary.copy(alpha = 0.4f)
                    )
                    Text(
                        text = bitrateText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = OrbitTheme.colors.textSecondary.copy(alpha = 0.85f)
                    )
                }

                if (sampleRateText.isNotBlank() || bitDepthText.isNotBlank()) {
                    val rateAndDepth = listOf(sampleRateText, bitDepthText).filter { it.isNotBlank() }.joinToString(" / ")
                    Text(
                        text = "•",
                        fontSize = 10.sp,
                        color = OrbitTheme.colors.textSecondary.copy(alpha = 0.4f)
                    )
                    Text(
                        text = rateAndDepth,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = OrbitTheme.colors.textSecondary.copy(alpha = 0.75f)
                    )
                }

                Text(
                    text = "•",
                    fontSize = 10.sp,
                    color = OrbitTheme.colors.textSecondary.copy(alpha = 0.4f)
                )
                Text(
                    text = durationText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = OrbitTheme.colors.textSecondary.copy(alpha = 0.85f)
                )
            }
        }

        // 4. 快捷功能行 (红心/态度、EQ、频谱形态、歌词显隐、菜单)
        val quickActionsView: @Composable (Modifier) -> Unit = { mod ->
            Row(
                modifier = mod,
                horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 14.dp else 22.dp, Alignment.CenterHorizontally),
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
                                FastToast.show(context, msgRes)
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
                val vizActive = showCoverVisualizer && equalizerUiState.visualizerEnabled && equalizerUiState.visualizerStyle != VisualizerStyle.OFF
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = {
                                if (vizActive) {
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
                                if (!showCoverVisualizer) {
                                    onToggleCoverVisualizer(true)
                                }
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

                // 歌词显/隐切换
                val lyricsActive = equalizerUiState.showNowPlayingLyrics
                IconButton(
                    onClick = { onToggleShowLyrics(!lyricsActive) },
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = if (lyricsActive) {
                                stringResource(R.string.lyrics_toggle_hide)
                            } else {
                                stringResource(R.string.lyrics_toggle_show)
                            },
                            tint = if (lyricsActive) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        if (lyricsActive) {
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

                // 三点菜单
                IconButton(
                    onClick = { showMoreOptionsMenu = true },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.menu_more_options),
                        tint = OrbitTheme.colors.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
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
                            FastToast.show(context, toastResId)
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

        if (useTabletThreeColumnLayout) {
            // ========== 平板 UI 三栏极致并列布局 (左: 完整播放器 | 中: 歌曲列表与分类库 | 右: 滚动歌词) ==========
            Row(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .swipeToChangeSong(
                        onSwipeNext = { viewModel.playNext() },
                        onSwipePrevious = { viewModel.playPrevious() }
                    ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── 1. 左栏：播放器（手机布局中的完整播放页） ──
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = OrbitTheme.colors.surfaceCard.copy(alpha = 0.52f),
                    border = BorderStroke(1.dp, OrbitTheme.colors.surfaceBorder.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 顶部导航与操作行
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Collapse",
                                    tint = OrbitTheme.colors.textPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = song?.album?.takeIf { it.isNotBlank() && it != "Unknown Album" } ?: "Orbit Player",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OrbitTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.size(34.dp))
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 封面与大频谱切换舞台
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            contentAlignment = Alignment.Center
                        ) {
                            coverView(Modifier.fillMaxWidth())
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 歌曲标题与艺术家
                        trackInfoView()

                        Spacer(modifier = Modifier.height(4.dp))

                        // 技术规格参数
                        techSpecsView(Modifier.fillMaxWidth())

                        Spacer(modifier = Modifier.height(8.dp))

                        // 快捷操作栏 (红心/态度、EQ、频谱样式切换、更多选项)
                        quickActionsView(Modifier.fillMaxWidth())

                        Spacer(modifier = Modifier.height(8.dp))

                        // 进度条与时间
                        progressSliderView()

                        Spacer(modifier = Modifier.height(6.dp))

                        // 播放控制底栏 (Shuffle, Prev, Play/Pause, Next, Repeat)
                        controlsRowView()
                    }
                }

                // ── 2. 中栏：歌曲列表与分类库 ──
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = OrbitTheme.colors.surfaceCard.copy(alpha = 0.52f),
                    border = BorderStroke(1.dp, OrbitTheme.colors.surfaceBorder.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1.22f)
                        .fillMaxHeight()
                ) {
                    NowPlayingLibraryMiddleColumn(
                        viewModel = viewModel,
                        allSongs = allSongs,
                        favoriteSongs = favoriteSongs,
                        dislikedSongs = dislikedSongs,
                        playlists = playlists,
                        playbackState = playbackState,
                        coverVersion = coverVer
                    )
                }

                // ── 3. 右栏：沉浸式歌词 ──
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = OrbitTheme.colors.surfaceCard.copy(alpha = 0.52f),
                    border = BorderStroke(1.dp, OrbitTheme.colors.surfaceBorder.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 歌词顶部标题栏
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lyrics,
                                    contentDescription = null,
                                    tint = OrbitTheme.colors.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "歌词",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OrbitTheme.colors.textPrimary
                                )
                            }
                            if (lyricLines.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = OrbitTheme.colors.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "同步歌词",
                                        fontSize = 10.5.sp,
                                        color = OrbitTheme.colors.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 歌词列表
                        fullLyricsView(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
        } else if (isLandscape) {
            // ========== 专业横屏大画幅可视化与全景歌词分屏布局 (通透呼吸感沉浸重构) ==========
            Row(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 6.dp,
                        bottom = 18.dp
                    )
                    .swipeToChangeSong(
                        onSwipeNext = { viewModel.playNext() },
                        onSwipePrevious = { viewModel.playPrevious() }
                    ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── 左侧：通透大画幅音频可视化舞台 + 纯净悬浮歌曲信息（专辑、歌手、歌曲名） + 轻量快捷操作 ──
                Box(
                    modifier = Modifier
                        .weight(1.08f)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                ) {
                    val currentStyle = if (equalizerUiState.visualizerStyle == VisualizerStyle.OFF) {
                        VisualizerStyle.BARS_WITH_PEAKS
                    } else {
                        equalizerUiState.visualizerStyle
                    }

                    // 1. 底层：大画幅动态可视化频谱 / 专辑封面切换舞台 (全屏通畅无阻碍)
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
                        label = "LandscapeCoverVisualizerSwitchAnim",
                        modifier = Modifier.fillMaxSize()
                    ) { isVisualizerMode ->
                        if (isVisualizerMode) {
                            // 大画幅沉浸频谱 (全屏舒展自由律动)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
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
                                if (!equalizerUiState.isVisualizerMaximized) {
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
                                        backgroundLightColor = equalizerUiState.backgroundExtractedLightColor,
                                        backgroundDarkColor = equalizerUiState.backgroundExtractedDarkColor,
                                        resetTrigger = "${song?.id}_${playbackState.currentIndex}",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 2.dp, vertical = 2.dp),
                                        onClick = { onCycleVisualizerStyle?.invoke() }
                                    )
                                }
                            }
                        } else {
                            // 封面展示模式 (大小与可视化区域一样大，1:1沉浸舞台，上滑切入大画幅动态频谱)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
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
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 2.dp, vertical = 2.dp)
                                        .scale(coverScale)
                                        .shadow(
                                            elevation = 16.dp,
                                            shape = RoundedCornerShape(18.dp),
                                            spotColor = OrbitTheme.colors.primary.copy(alpha = 0.35f)
                                        )
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(OrbitTheme.colors.surfaceCard)
                                        .clickable {
                                            if (equalizerUiState.visualizerStyle == VisualizerStyle.OFF) {
                                                onCycleVisualizerStyle?.invoke()
                                            }
                                            onToggleCoverVisualizer(true)
                                        }
                                ) {
                                    val artUri = song?.albumArtUri
                                    if (artUri != null) {
                                        AsyncImage(
                                            model = coil.request.ImageRequest.Builder(LocalContext.current)
                                                .data(artUri)
                                                .memoryCacheKey("${artUri}_$coverVer")
                                                .diskCacheKey("${artUri}_$coverVer")
                                                .build(),
                                            contentDescription = song?.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = OrbitTheme.colors.primary,
                                                modifier = Modifier.size(56.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. 悬浮歌曲信息排版浮层 (无封闭黑底，纯净通透，带微光阴影，彻底打开呼吸感)
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 12.dp, top = 12.dp, end = 100.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 优雅小巧的收起返回箭头
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.36f),
                            modifier = Modifier.size(32.dp),
                            onClick = onBack
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Collapse",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // 歌曲名、歌手与专辑名称
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            AnimatedContent(
                                targetState = song?.title ?: "No Track Selected",
                                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                                label = "LandscapeSongTitleAnim"
                            ) { title ->
                                Text(
                                    text = title,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    style = TextStyle(
                                        shadow = Shadow(
                                            color = Color.Black.copy(alpha = 0.85f),
                                            offset = Offset(0f, 2f),
                                            blurRadius = 6f
                                        )
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            val artistAndAlbum = buildString {
                                append(song?.artist?.ifBlank { "Unknown Artist" } ?: "Unknown Artist")
                                val albumName = song?.album?.trim()
                                if (!albumName.isNullOrEmpty() && albumName != "Unknown Album") {
                                    append("  •  ")
                                    append(albumName)
                                }
                            }
                            AnimatedContent(
                                targetState = artistAndAlbum,
                                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                                label = "LandscapeArtistAlbumAnim"
                            ) { text ->
                                Text(
                                    text = text,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.82f),
                                    style = TextStyle(
                                        shadow = Shadow(
                                            color = Color.Black.copy(alpha = 0.85f),
                                            offset = Offset(0f, 2f),
                                            blurRadius = 4f
                                        )
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            techSpecsView(Modifier.padding(top = 1.dp))
                        }
                    }

                    // 3. 右上角：样式切换药丸徽标 + 最大化全屏按钮 (半透明轻量微标)
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 12.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Black.copy(alpha = 0.36f),
                            modifier = Modifier.clickable { onCycleVisualizerStyle?.invoke() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                val styleText = when (currentStyle) {
                                    VisualizerStyle.BARS_WITH_PEAKS -> stringResource(R.string.visualizer_style_bars_with_peaks)
                                    VisualizerStyle.AURORA_MOUNTAIN -> stringResource(R.string.visualizer_style_aurora_mountain)
                                    VisualizerStyle.MIRRORED_BARS -> stringResource(R.string.visualizer_style_mirrored_bars)
                                    VisualizerStyle.TIME_TUNNEL -> stringResource(R.string.visualizer_style_time_tunnel)
                                    VisualizerStyle.OCTGRAMS -> stringResource(R.string.visualizer_style_octgrams)
                                    VisualizerStyle.SOUND_CITY -> stringResource(R.string.visualizer_style_sound_city)
                                    VisualizerStyle.FRACTAL_GALAXY -> stringResource(R.string.visualizer_style_fractal_galaxy)
                                    VisualizerStyle.QUANTUM_VORTEX -> stringResource(R.string.visualizer_style_quantum_vortex)
                                    else -> stringResource(R.string.visualizer_style_bars_with_peaks)
                                }
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = OrbitTheme.colors.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = styleText,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.36f),
                            modifier = Modifier
                                .size(26.dp)
                                .clickable { onToggleVisualizerMaximized(true) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = stringResource(R.string.visualizer_maximize),
                                    tint = OrbitTheme.colors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // 4. 左侧底部轻量悬浮快捷操作栏（红心/态度、EQ均衡器、视效开关、更多菜单）
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.30f),
                        border = BorderStroke(
                            width = 0.8.dp,
                            color = Color(0x20FFFFFF)
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                    ) {
                        quickActionsView(Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
                    }
                }

                // ── 右侧：全景沉浸歌词 + 播放队列入口 + 进度滑条 + 播放控制 ──
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 右侧顶部快捷操作行：播放列表队列按钮
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 4.dp, top = 2.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.30f),
                            modifier = Modifier.size(32.dp),
                            onClick = { showQueueBottomSheet = true }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = "Playing Queue",
                                    tint = OrbitTheme.colors.primary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    lyricsView(Modifier.weight(1f).fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    progressSliderView()
                    Spacer(modifier = Modifier.height(6.dp))
                    controlsRowView()
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        } else {
            // ========== 标准竖屏布局 (专辑封面靠顶对齐红框1，歌曲信息紧贴对齐红框2，拉开与小图标间距) ==========
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
                    .padding(top = 2.dp, bottom = 12.dp)
                    .swipeToChangeSong(
                        onSwipeNext = { viewModel.playNext() },
                        onSwipePrevious = { viewModel.playPrevious() }
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 主视觉舞台：根据歌词显/隐状态，在“封面+歌曲信息”与“独占歌词页”之间平滑切换
                AnimatedContent(
                    targetState = equalizerUiState.showNowPlayingLyrics,
                    transitionSpec = {
                        (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 6 })
                            .togetherWith(fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 6 })
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    label = "CoverLyricsSwitchAnim"
                ) { isLyricsMode ->
                    if (isLyricsMode) {
                        // 开启歌词显示后：歌词独占整页大画幅显示
                        fullLyricsView(Modifier.fillMaxSize())
                    } else {
                        // 竖屏专辑封面与歌曲信息：封面顶格靠上 (红框1)，歌曲信息紧随其后 (红框2)
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top
                        ) {
                            Spacer(modifier = Modifier.height(2.dp))
                            coverView(Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(14.dp))
                            trackInfoView()
                            Spacer(modifier = Modifier.height(6.dp))
                            techSpecsView(Modifier.fillMaxWidth())
                        }
                    }
                }

                // 歌词模式下单独在歌词下方紧凑呈现歌曲信息与技术参数
                if (equalizerUiState.showNowPlayingLyrics) {
                    Spacer(modifier = Modifier.height(6.dp))
                    trackInfoView()
                    Spacer(modifier = Modifier.height(4.dp))
                    techSpecsView(Modifier.fillMaxWidth())
                }

                // 歌曲信息与快捷小图标之间的舒展呼吸留白
                Spacer(modifier = Modifier.height(if (equalizerUiState.showNowPlayingLyrics) 12.dp else 16.dp))

                quickActionsView(Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                progressSliderView()
                Spacer(modifier = Modifier.height(10.dp))
                controlsRowView()
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // 当前播放队列弹层 (横屏下使用居中悬浮卡片Dialog，竖屏使用ModalBottomSheet，彻底修复横屏偏在左下角异常)
    if (showQueueBottomSheet) {
        val queueContent: @Composable () -> Unit = {
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
                    .then(if (isLandscape) Modifier.fillMaxHeight() else Modifier.fillMaxHeight(0.75f))
                    .padding(horizontal = 20.dp)
                    .padding(
                        top = if (isLandscape) 16.dp else 0.dp,
                        bottom = if (isLandscape) 16.dp else 24.dp
                    )
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
                                        val originalIndex = currentQueue.indexOfFirst { it.id == qSong.id }
                                        viewModel.playSong(currentQueue, if (originalIndex >= 0) originalIndex else index)
                                    }
                                    .padding(horizontal = 10.dp, vertical = if (showCoverInQueue) 6.dp else 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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

        if (isLandscape) {
            // 横屏下：使用居中自适应宽卡片 Dialog (彻底移除多余的半透明黑边与黑底)
            Dialog(
                onDismissRequest = { showQueueBottomSheet = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showQueueBottomSheet = false }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .widthIn(min = 460.dp, max = 620.dp)
                            .fillMaxWidth(0.65f)
                            .fillMaxHeight(0.88f)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {} // 拦截点击穿透
                            ),
                        shape = RoundedCornerShape(20.dp),
                        color = OrbitTheme.colors.surfaceDialog,
                        tonalElevation = 2.dp,
                        shadowElevation = 10.dp
                    ) {
                        queueContent()
                    }
                }
            }
        } else {
            // 竖屏下：标准优雅的底部抽屉 (ModalBottomSheet)
            ModalBottomSheet(
                onDismissRequest = { showQueueBottomSheet = false },
                containerColor = OrbitTheme.colors.surfaceDialog,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                queueContent()
            }
        }
    }

    // 播放页三点更多操作弹窗
    if (showMoreOptionsMenu && song != null) {
        val targetSong = song
        AlertDialog(
            onDismissRequest = { showMoreOptionsMenu = false },
            title = {
                Text(
                    text = stringResource(R.string.menu_more_options),
                    color = OrbitTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 当前歌曲与歌手简要提示
                    Text(
                        text = "${targetSong.title} • ${targetSong.artist}",
                        fontSize = 12.sp,
                        color = OrbitTheme.colors.primary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    HorizontalDivider(color = OrbitTheme.colors.textSecondary.copy(alpha = 0.15f))

                    // 1. 选择封面 (弹窗内提供预览、下载/重新下载以及保存)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showMoreOptionsMenu = false
                                candidateSong = targetSong
                                val cached = com.orbit.music.data.cover.MusicBrainzCoverService.getCachedCandidates(targetSong.id)
                                if (cached != null) {
                                    candidateArtistName = cached.first
                                    candidateCovers = cached.second
                                } else {
                                    candidateArtistName = targetSong.artist
                                    candidateCovers = emptyList()
                                }
                                showSelectAlbumCoverDialog = true
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = OrbitTheme.colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.menu_select_cover),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = OrbitTheme.colors.textPrimary
                        )
                    }

                    // 2. 编辑歌曲标签
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showMoreOptionsMenu = false
                                coroutineScope.launch {
                                    val meta = viewModel.loadSongMetadata(targetSong)
                                    currentSongMetadata = meta
                                    showEditSongTagsDialog = true
                                }
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = OrbitTheme.colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.menu_edit_tags),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = OrbitTheme.colors.textPrimary
                        )
                    }

                    // 2.5 详细信息
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showMoreOptionsMenu = false
                                showSongDetailInfoDialog = true
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = OrbitTheme.colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.menu_song_details),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = OrbitTheme.colors.textPrimary
                        )
                    }

                    // 3. 添加到播放列表
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showMoreOptionsMenu = false
                                showAddToPlaylistDialog = true
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            tint = OrbitTheme.colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.add_to_playlist),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = OrbitTheme.colors.textPrimary
                        )
                    }

                    // 3.5 查看专辑
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showMoreOptionsMenu = false
                                viewModel.openAlbum(targetSong)
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = null,
                            tint = OrbitTheme.colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.menu_view_album),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = OrbitTheme.colors.textPrimary
                        )
                    }

                    // 4. 删除歌曲
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showMoreOptionsMenu = false
                                deleteLocalFileChecked = false
                                showDeleteSongDialog = true
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = Color(0xFFFF4D4F),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.menu_delete_song),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFFF4D4F)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoreOptionsMenu = false }) {
                    Text(stringResource(R.string.btn_cancel), color = OrbitTheme.colors.textSecondary)
                }
            },
            containerColor = OrbitTheme.colors.surfaceDialog
        )
    }

    // 歌曲详细信息弹窗 (参考图高质感展示)
    if (showSongDetailInfoDialog && song != null) {
        val targetSong = song
        com.orbit.music.ui.components.SongDetailInfoDialog(
            song = targetSong,
            onDismissRequest = { showSongDetailInfoDialog = false },
            onChangeCover = { s ->
                showSongDetailInfoDialog = false
                candidateSong = s
                val cached = com.orbit.music.data.cover.MusicBrainzCoverService.getCachedCandidates(s.id)
                if (cached != null) {
                    candidateArtistName = cached.first
                    candidateCovers = cached.second
                } else {
                    candidateArtistName = s.artist
                    candidateCovers = emptyList()
                }
                showSelectAlbumCoverDialog = true
            },
            onViewLyrics = {
                showSongDetailInfoDialog = false
                onToggleShowLyrics(true)
            }
        )
    }

    // 删除歌曲确认对话框 (可勾选是否同时删除本地文件)
    if (showDeleteSongDialog && song != null) {
        val targetSong = song
        AlertDialog(
            onDismissRequest = { showDeleteSongDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.delete_song_dialog_title),
                    color = OrbitTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.delete_song_confirm_message, targetSong.title),
                        fontSize = 14.sp,
                        color = OrbitTheme.colors.textSecondary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { deleteLocalFileChecked = !deleteLocalFileChecked }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = deleteLocalFileChecked,
                            onCheckedChange = { deleteLocalFileChecked = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = OrbitTheme.colors.primary,
                                uncheckedColor = OrbitTheme.colors.textSecondary
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.delete_local_file_checkbox),
                            fontSize = 13.sp,
                            color = OrbitTheme.colors.textPrimary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deleteLocal = deleteLocalFileChecked
                        showDeleteSongDialog = false

                        if (deleteLocal && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()) {
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        }

                        viewModel.deleteSong(targetSong, deleteLocal) { success ->
                            if (success) {
                                FastToast.show(context, R.string.delete_song_success)
                            } else {
                                FastToast.show(context, R.string.delete_song_failed)
                            }
                            if (playbackState.currentPlaylist.size <= 1) {
                                onBack()
                            }
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.btn_delete),
                        color = Color(0xFFFF4D4F),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSongDialog = false }) {
                    Text(
                        text = stringResource(R.string.btn_cancel),
                        color = OrbitTheme.colors.textSecondary
                    )
                }
            },
            containerColor = OrbitTheme.colors.surfaceDialog
        )
    }

    // 编辑歌曲标签对话框 (Poweramp 风格全套元数据)
    if (showEditSongTagsDialog && song != null) {
        val targetSong = song
        EditSongTagsDialog(
            song = targetSong,
            initialMetadata = currentSongMetadata,
            onDismissRequest = { showEditSongTagsDialog = false },
            onSaveMetadata = { newMeta ->
                viewModel.saveFullSongMetadata(targetSong, newMeta) {
                    Toast.makeText(context, R.string.tags_saved_success, Toast.LENGTH_SHORT).show()
                }
                showEditSongTagsDialog = false
            }
        )
    }

    // 候选专辑封面选择对话框 (展示已有封面或在线检索封面，支持下载/重新下载并保存)
    if (showSelectAlbumCoverDialog && candidateSong != null) {
        val songToApply = candidateSong!!
        val hasDownloadedCover = remember(songToApply.id, coverVer) {
            com.orbit.music.utils.CoverHelper.hasDownloadedCover(context, songToApply.id)
        }
        SelectAlbumCoverDialog(
            song = songToApply,
            artistName = candidateArtistName.ifBlank { songToApply.artist },
            candidates = candidateCovers,
            hasDownloadedCover = hasDownloadedCover,
            isDownloading = isDownloadingCover,
            isApplying = isApplyingCover,
            onDismissRequest = {
                showSelectAlbumCoverDialog = false
            },
            onDownload = {
                // 1. 立即关闭选择封面弹窗
                showSelectAlbumCoverDialog = false
                // 2. 激活封面位置的下载进度提示遮罩
                isDownloadingCover = true
                coroutineScope.launch {
                    try {
                        com.orbit.music.utils.CoverHelper.resetOnlineSearchStatus(songToApply.id)
                        val result = com.orbit.music.data.cover.MusicBrainzCoverService.checkAndFetchLargeCover(
                            context = context,
                            song = songToApply,
                            force = true
                        )
                        withContext(Dispatchers.Main) {
                            isDownloadingCover = false
                            when (result) {
                                is com.orbit.music.data.cover.MusicBrainzCoverService.MatchResult.ArtistAlbumsFound -> {
                                    candidateCovers = result.candidates
                                    candidateArtistName = result.artist
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.cover_fetch_completed_hint, result.candidates.size),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                is com.orbit.music.data.cover.MusicBrainzCoverService.MatchResult.UpdatedLarge -> {
                                    val sizeStr = "${result.netWidth} × ${result.netHeight}"
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.cover_update_success, sizeStr),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                is com.orbit.music.data.cover.MusicBrainzCoverService.MatchResult.NotFound -> {
                                    Toast.makeText(context, R.string.cover_not_found, Toast.LENGTH_SHORT).show()
                                }
                                else -> {
                                    Toast.makeText(context, R.string.cover_search_failed, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            isDownloadingCover = false
                            Toast.makeText(context, R.string.cover_search_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onConfirmSelection = { selectedCandidate ->
                isApplyingCover = true
                coroutineScope.launch {
                    val success = com.orbit.music.data.cover.MusicBrainzCoverService.applyCandidateCover(
                        context = context,
                        songId = songToApply.id,
                        candidate = selectedCandidate
                    )
                    withContext(Dispatchers.Main) {
                        isApplyingCover = false
                        if (success) {
                            Toast.makeText(context, R.string.cover_apply_success, Toast.LENGTH_SHORT).show()
                            showSelectAlbumCoverDialog = false
                        } else {
                            Toast.makeText(context, R.string.cover_apply_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
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
    } // 结束 Scaffold，使全屏浮层彻底覆盖整个窗口

    // 全屏最大化沉浸大频谱浮层 (脱离 Scaffold 约束，真正全屏铺满)
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
            allSongs = allSongs,
            isDownloadingCover = isDownloadingCover,
            onBack = { onToggleVisualizerMaximized(false) },
            onCycleVisualizerStyle = onCycleVisualizerStyle,
            onToggleMaximizedShowCover = onToggleMaximizedShowCover,
            onToggleMaximizedCoverPosition = onToggleMaximizedCoverPosition,
            onToggleMaximizedCoverRotating = onToggleMaximizedCoverRotating,
            onToggleMaximizedShowControls = onToggleMaximizedShowControls,
            onSetMaximizedCoverAlpha = onSetMaximizedCoverAlpha,
            onToggleFollowCoverColor = onToggleFollowCoverColor,
            onTogglePlay = { viewModel.togglePlayPause() },
            onPlayNext = { viewModel.playNext() },
            onPlayPrevious = { viewModel.playPrevious() },
            onSeekTo = { viewModel.seekTo((it * playbackState.durationMs).toLong()) },
            onSongClick = { targetSong, targetIdx ->
                val queue = playbackState.currentPlaylist.ifEmpty { allSongs }
                viewModel.playSong(queue, targetIdx)
            },
            modifier = Modifier.fillMaxSize()
        )
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MaximizedVisualizerOverlay(
    playbackState: PlaybackState,
    visualizerFrame: VisualizerFrame,
    equalizerUiState: EqualizerUiState,
    song: Song?,
    allSongs: List<Song> = emptyList(),
    isDownloadingCover: Boolean = false,
    onBack: () -> Unit,
    onCycleVisualizerStyle: (() -> Unit)?,
    onToggleMaximizedShowCover: (Boolean) -> Unit,
    onToggleMaximizedCoverPosition: (Boolean) -> Unit,
    onToggleMaximizedCoverRotating: (Boolean) -> Unit,
    onToggleMaximizedShowControls: (Boolean) -> Unit,
    onSetMaximizedCoverAlpha: (Float) -> Unit,
    onToggleFollowCoverColor: (Boolean) -> Unit = {},
    onTogglePlay: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onSeekTo: (Float) -> Unit,
    onSongClick: ((Song, Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val coverVer by com.orbit.music.utils.CoverHelper.coverVersion.collectAsState()
    var coverExtractedColors by remember { mutableStateOf<com.orbit.music.utils.PaletteHelper.ExtractedColors?>(null) }

    // Cover Flow 沉浸模式状态
    var isCoverFlowMode by remember { mutableStateOf(false) }

    // 播放列表数据源
    val playlist = remember(playbackState.currentPlaylist, allSongs, song) {
        val queue = playbackState.currentPlaylist.ifEmpty { allSongs }
        if (queue.isNotEmpty()) queue else if (song != null) listOf(song) else emptyList()
    }
    val initialPageIndex = remember(playbackState.currentIndex, playlist.size) {
        if (playlist.isNotEmpty()) {
            playbackState.currentIndex.coerceIn(0, playlist.size - 1)
        } else 0
    }
    val pagerState = rememberPagerState(
        initialPage = initialPageIndex,
        pageCount = { playlist.size.coerceAtLeast(1) }
    )

    // 外部切歌时，同步滚动 Cover Flow
    LaunchedEffect(playbackState.currentIndex, playlist.size) {
        if (playlist.isNotEmpty()) {
            val targetIdx = playbackState.currentIndex.coerceIn(0, playlist.size - 1)
            if (pagerState.currentPage != targetIdx) {
                if (isCoverFlowMode) {
                    pagerState.animateScrollToPage(targetIdx)
                } else {
                    pagerState.scrollToPage(targetIdx)
                }
            }
        }
    }

    // 退出 Cover Flow 时，复位 Pager 到当前播放歌曲
    LaunchedEffect(isCoverFlowMode) {
        if (!isCoverFlowMode && playlist.isNotEmpty()) {
            val targetIdx = playbackState.currentIndex.coerceIn(0, playlist.size - 1)
            if (pagerState.currentPage != targetIdx) {
                pagerState.scrollToPage(targetIdx)
            }
        }
    }

    // 播放时实时从当前专辑封面文件中异步提取亮色与暗色 (不进行持久化)
    LaunchedEffect(song?.id, song?.path, song?.album, coverVer) {
        if (song != null) {
            val coverFile = com.orbit.music.utils.CoverHelper.getOrExtractCoverFile(context, song.id, song.path, song.album)
            if (coverFile != null && coverFile.exists() && coverFile.length() > 0L) {
                coverExtractedColors = com.orbit.music.utils.PaletteHelper.extractColorsFromImage(coverFile.absolutePath)
            } else {
                coverExtractedColors = null
            }
        } else {
            coverExtractedColors = null
        }
    }

    val isDualColor = !equalizerUiState.visualizerSingleColor
    val isFollowingCover = equalizerUiState.followCoverColorInMaximized && isDualColor && coverExtractedColors != null

    val effectiveColorScheme = if (isFollowingCover) {
        VisualizerColorScheme.FOLLOW_BACKGROUND
    } else {
        equalizerUiState.visualizerColorScheme
    }

    val effectiveLightColor = if (isFollowingCover) {
        coverExtractedColors!!.lightColor
    } else {
        equalizerUiState.backgroundExtractedLightColor
    }

    val effectiveDarkColor = if (isFollowingCover) {
        coverExtractedColors!!.darkColor
    } else {
        equalizerUiState.backgroundExtractedDarkColor
    }

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

    // Cover Flow 进入/退出的动画插值量 (0f: 常规全屏大频谱模式, 1f: 沉浸 Cover Flow 模式)
    val coverFlowTransition by animateFloatAsState(
        targetValue = if (isCoverFlowMode) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "CoverFlowTransition"
    )

    // 频谱压暗透明度动画
    val spectrumDimAlpha by animateFloatAsState(
        targetValue = if (isCoverFlowMode) 0.55f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "SpectrumDimAnim"
    )

    val clearScreenHint = stringResource(R.string.maximized_clear_screen_hint)
    val toggleAllControls: () -> Unit = {
        // 若当前上方或下方控制栏正在显示，则执行一键清屏；若都已隐藏，则同时恢复显示
        val willShow = !(showTopControlBar || equalizerUiState.maximizedShowControls)
        showTopControlBar = willShow
        onToggleMaximizedShowControls(willShow)
        if (!willShow) {
            Toast.makeText(context, clearScreenHint, Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // 0. 全局沉浸背景层 (彻底遮挡普通播放页面的大封面与按钮，呈现纯净壁纸/暗黑底色)
        AppBackgroundLayer(
            customBackgroundPath = equalizerUiState.customBackgroundPath,
            customSolidBackgroundColor = equalizerUiState.customSolidBackgroundColor,
            blurRadius = equalizerUiState.backgroundBlurRadius,
            blurStyle = equalizerUiState.backgroundBlurStyle,
            dimAlpha = equalizerUiState.backgroundDimAlpha
        )

        // 1. 全屏底层动态频谱渲染 (完全触底与横向铺满)
        PowerampSpectrumVisualizer(
            magnitudes = visualizerFrame.rawMagnitudes,
            peaks = visualizerFrame.peakCaps,
            style = currentStyle,
            colorScheme = effectiveColorScheme,
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
            backgroundLightColor = effectiveLightColor,
            backgroundDarkColor = effectiveDarkColor,
            resetTrigger = "${song?.id}_${playbackState.currentIndex}",
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (currentStyle == VisualizerStyle.TIME_TUNNEL ||
                        currentStyle == VisualizerStyle.OCTGRAMS ||
                        currentStyle == VisualizerStyle.SOUND_CITY ||
                        currentStyle == VisualizerStyle.FRACTAL_GALAXY ||
                        currentStyle == VisualizerStyle.QUANTUM_VORTEX) {
                        Modifier
                    } else {
                        Modifier.padding(
                            start = 0.dp,
                            end = 0.dp,
                            top = if (isLandscape) 40.dp else 52.dp,
                            bottom = 0.dp
                        )
                    }
                ),
            onClick = null
        )

        // 1.2 全屏左右两侧空白区域分屏交互层 (左侧空白清屏与恢复，右侧空白开关 Cover Flow)
        Row(modifier = Modifier.fillMaxSize()) {
            // 左侧空白区域：清屏与恢复控制条
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            toggleAllControls()
                        }
                    )
            )
            // 右侧空白区域：开关 Cover Flow
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            isCoverFlowMode = !isCoverFlowMode
                        }
                    )
            )
        }

        // 1.5 频谱层在 Cover Flow 沉浸模式下变暗一点 (叠加平滑暗色蒙层)
        if (spectrumDimAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = spectrumDimAlpha))
            )
        }

        val screenWidth = configuration.screenWidthDp.dp
        val screenHeight = configuration.screenHeightDp.dp

        // 1. 精准测量顶部控制条实际占用的底部位置
        val statusBarResId = remember(context) {
            context.resources.getIdentifier("status_bar_height", "dimen", "android")
        }
        val systemStatusBarHeight = remember(context, statusBarResId) {
            if (statusBarResId > 0) {
                val px = context.resources.getDimensionPixelSize(statusBarResId)
                val density = context.resources.displayMetrics.density
                if (density > 0f) (px / density).dp else 0.dp
            } else {
                0.dp
            }
        }
        val composeStatusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val composeSafeDrawing = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
        val composeCutout = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()

        val actualStatusBarHeight = maxOf(systemStatusBarHeight, composeStatusBar, composeSafeDrawing, composeCutout)
            .coerceAtLeast(if (isLandscape) 0.dp else 44.dp)

        val topBarPaddingTop = if (isLandscape) {
            maxOf(actualStatusBarHeight, composeCutout).coerceAtLeast(6.dp) + 6.dp
        } else {
            actualStatusBarHeight + 12.dp
        }
        val topBarBottom = if (showTopControlBar) (topBarPaddingTop + 46.dp + 12.dp) else (topBarPaddingTop + 6.dp)

        // 2. 精准测量底部控制卡片实际占用的高度
        val bottomControlsHeight = if (equalizerUiState.maximizedShowControls) {
            if (isLandscape) 90.dp else 168.dp
        } else {
            if (isLandscape) 20.dp else 24.dp
        }

        // 3. 计算垂直可用净空距离
        val verticalAvailableGap = (screenHeight - topBarBottom - bottomControlsHeight).coerceAtLeast(80.dp)

        // 4. 动态自适应常规状态封面尺寸
        val maxCoverHeight = (verticalAvailableGap - 28.dp).coerceAtLeast(80.dp)
        val maxCoverWidth = if (isLandscape) {
            (screenWidth * 0.40f).coerceAtLeast(80.dp)
        } else {
            (screenWidth - 36.dp).coerceAtLeast(80.dp)
        }
        val coverSize = minOf(maxCoverHeight, maxCoverWidth, if (isLandscape) 250.dp else 240.dp)

        // 5. 常规模式封面位置坐标
        val remainingVerticalGap = (verticalAvailableGap - coverSize).coerceAtLeast(0.dp)
        val coverTopPadding = topBarBottom + (remainingVerticalGap / 2).coerceAtLeast(14.dp)

        val landscapeSideMargin = (screenWidth * 0.21f - coverSize / 2).coerceAtLeast(30.dp)
        val coverStartPadding = if (isLandscape) {
            if (equalizerUiState.maximizedCoverOnRight) 0.dp else landscapeSideMargin
        } else {
            if (equalizerUiState.maximizedCoverOnRight) 0.dp else 18.dp
        }
        val coverEndPadding = if (isLandscape) {
            if (equalizerUiState.maximizedCoverOnRight) landscapeSideMargin else 0.dp
        } else {
            if (equalizerUiState.maximizedCoverOnRight) 18.dp else 0.dp
        }

        // 计算常规角标位置相对于屏幕中央的偏移向量
        val normalCenterX = if (equalizerUiState.maximizedCoverOnRight) {
            screenWidth - coverEndPadding - coverSize / 2
        } else {
            coverStartPadding + coverSize / 2
        }
        val normalCenterY = coverTopPadding + coverSize / 2

        val targetCenterX = screenWidth / 2
        val targetCenterY = screenHeight / 2

        val deltaX = normalCenterX - targetCenterX
        val deltaY = normalCenterY - targetCenterY

        // 实时平滑位移：0f 时位于角标，1f 时严格居中于屏幕正中央
        val currentOffsetX = deltaX * (1f - coverFlowTransition)
        val currentOffsetY = deltaY * (1f - coverFlowTransition)

        // Cover Flow 模式下的封面目标尺寸
        val flowCoverSize = if (isLandscape) {
            minOf(screenHeight * 0.50f, 220.dp)
        } else {
            minOf(screenWidth * 0.65f, screenHeight * 0.38f, 260.dp)
        }
        val currentCoverSize = coverSize + (flowCoverSize - coverSize) * coverFlowTransition
        val currentReflectionHeight = currentCoverSize * 0.42f * coverFlowTransition
        val totalCardHeight = currentCoverSize + currentReflectionHeight

        // 圆角向歌曲列表 Cover Flow 样式 (top=6dp, bottom=1dp) 平滑演化
        val circleCorner = currentCoverSize / 2
        val topCorner = lerp(circleCorner, 6.dp, coverFlowTransition)
        val bottomCorner = lerp(circleCorner, 1.dp, coverFlowTransition)
        val centerCardShape = RoundedCornerShape(
            topStart = topCorner,
            topEnd = topCorner,
            bottomStart = bottomCorner,
            bottomEnd = bottomCorner
        )
        val wingCardShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 1.dp, bottomEnd = 1.dp)

        // 边框宽度过渡
        val borderWidth = if (isLandscape) 5.5.dp else (coverSize * 0.022f).coerceIn(3.5.dp, 5.0.dp)
        val currentBorderWidth = borderWidth + (1.5.dp - borderWidth) * coverFlowTransition

        // 两翼其他封面的渐显度
        val wingsAlpha = coverFlowTransition

        // 黑胶封面旋转动效 (切歌时还原归零回正，仅在开启旋转且处于常规非 Cover Flow 播放状态时匀速旋转，暂停时原地驻留，关闭时顺畅回正)
        val coverRotation = remember { Animatable(0f) }
        val currentSongKey = remember(song?.id, song?.path, playbackState.currentIndex) {
            "${song?.id}_${song?.path}_${playbackState.currentIndex}"
        }
        var lastRotatedSongKey by remember { mutableStateOf(currentSongKey) }

        LaunchedEffect(playbackState.isPlaying, equalizerUiState.maximizedCoverRotating, isCoverFlowMode, currentSongKey) {
            // 切歌（歌曲标识或索引发生变化）时，立即还原旋转角度为 0°
            if (lastRotatedSongKey != currentSongKey) {
                coverRotation.snapTo(0f)
                lastRotatedSongKey = currentSongKey
            }

            if (playbackState.isPlaying && equalizerUiState.maximizedCoverRotating && !isCoverFlowMode) {
                while (true) {
                    coverRotation.animateTo(
                        targetValue = coverRotation.value + 360f,
                        animationSpec = tween(
                            durationMillis = 20000,
                            easing = LinearEasing
                        )
                    )
                }
            }
        }
        LaunchedEffect(equalizerUiState.maximizedCoverRotating) {
            if (!equalizerUiState.maximizedCoverRotating && coverRotation.value != 0f) {
                coverRotation.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                )
            }
        }
        val activeRotation = (coverRotation.value % 360f) * (1f - coverFlowTransition)

        val rawBorderColor = if (coverExtractedColors != null) {
            Color(coverExtractedColors!!.lightColor)
        } else {
            OrbitTheme.colors.primary
        }
        val animatedBorderColor by animateColorAsState(
            targetValue = rawBorderColor,
            animationSpec = tween(400),
            label = "MaximizedCoverBorderColor"
        )

        // 2. 悬浮专辑封面与 3D Cover Flow 唱片墙 (与歌曲列表 Cover Flow 样式完全一致：倒影、微圆角、播放小徽章)
        AnimatedVisibility(
            visible = equalizerUiState.maximizedShowCover,
            enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.85f, animationSpec = tween(250)),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.85f, animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = currentOffsetX, y = currentOffsetY)
        ) {
            val density = LocalDensity.current
            // 核心关键：必须使用与 Pager 内部 PageSize.Fixed 绝对一致的 roundToPx() 整数像素，杜绝亚像素舍入累乘导致的远端卡片剧烈抖动
            val cardWidthPx = with(density) { currentCoverSize.roundToPx().toFloat() }
            val containerWidthPx = with(density) { screenWidth.roundToPx().toFloat() }
            val cameraDistancePx = 20f * density.density

            // 核心突破：将 Pager 测量视口向两侧大幅扩容各 1400dp（总宽扩展 2800dp），
            // 使得两侧各 8~10 张封面均 100% 处于 Pager 原生 Viewport（活跃视口）范围内，
            // 彻底为两侧密集多封面展示提供强力底层渲染保障，绝不触发跳帧优化，彻底根除远端封面弹簧抖动
            val extraViewportWidth = 2800.dp
            val extraViewportWidthPx = with(density) { extraViewportWidth.roundToPx().toFloat() }
            val expandedWidthPx = containerWidthPx + extraViewportWidthPx
            val horizontalPadding = with(density) {
                (((expandedWidthPx - cardWidthPx) / 2f).toInt()).toDp().coerceAtLeast(0.dp)
            }

            val smoothDecay = exponentialDecay<Float>(
                frictionMultiplier = 0.32f,
                absVelocityThreshold = 0.08f
            )
            val smoothSnap = spring<Float>(
                dampingRatio = 0.88f,
                stiffness = 320f
            )
            val flingBehavior = PagerDefaults.flingBehavior(
                pagerState,
                PagerSnapDistance.atMost(20),
                smoothSnap,
                smoothDecay,
                smoothSnap
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalCardHeight + 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                HorizontalPager(
                    state = pagerState,
                    pageSize = PageSize.Fixed(currentCoverSize),
                    userScrollEnabled = isCoverFlowMode,
                    contentPadding = PaddingValues(horizontal = horizontalPadding),
                    pageSpacing = 0.dp,
                    beyondBoundsPageCount = 10,
                    flingBehavior = flingBehavior,
                    modifier = Modifier
                        .fillMaxHeight()
                        .layout { measurable, constraints ->
                            val looseConstraints = constraints.copy(
                                minWidth = expandedWidthPx.toInt(),
                                maxWidth = expandedWidthPx.toInt()
                            )
                            val placeable = measurable.measure(looseConstraints)
                            layout(constraints.maxWidth, placeable.height) {
                                val offsetX = -((expandedWidthPx - constraints.maxWidth) / 2f).toInt()
                                placeable.place(offsetX, 0)
                            }
                        }
                ) { page ->
                    val pageSong = playlist.getOrNull(page) ?: song
                    val isCenterCard = (page == pagerState.currentPage)
                    val isPlayingThis = (playbackState.currentSong?.id == pageSong?.id) && isCenterCard && isCoverFlowMode

                    val baseZIndex = when {
                        page < pagerState.currentPage -> 500f + (page - pagerState.currentPage)
                        page > pagerState.currentPage -> 500f - (page - pagerState.currentPage)
                        else -> 1000f
                    }

                    val currentShape = if (isCenterCard) centerCardShape else wingCardShape

                    val cardBorder = BorderStroke(
                        width = if (isCenterCard) currentBorderWidth else 0.5.dp,
                        color = if (isCenterCard) {
                            animatedBorderColor.copy(alpha = 0.92f)
                        } else {
                            Color.White.copy(alpha = 0.25f * wingsAlpha)
                        }
                    )

                    val currentCardAlpha = if (isCenterCard) equalizerUiState.maximizedCoverAlpha else 1f

                    Box(
                        modifier = Modifier
                            .size(width = currentCoverSize, height = totalCardHeight)
                            .zIndex(baseZIndex)
                            .maximizedCoverFlowTransform(
                                page = page,
                                pagerState = pagerState,
                                cardWidthPx = cardWidthPx,
                                isLandscape = isLandscape,
                                cameraDistancePx = cameraDistancePx,
                                wingsAlpha = wingsAlpha
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (!isCoverFlowMode) {
                                        // 常规模式点击封面：开启 3D Cover Flow
                                        isCoverFlowMode = true
                                    } else {
                                        if (page == pagerState.currentPage) {
                                            // 点击正中央卡片：
                                            // 若当前歌曲未处于播放状态（处于暂停，或翻到的歌曲尚未播放），点击封面进行播放；
                                            // 若当前歌曲已在播放中（再点击一次封面），收起 Cover Flow
                                            val currentPlayingSongId = playbackState.currentSong?.id
                                            val isCurrentSong = (pageSong != null && pageSong.id == currentPlayingSongId)
                                            val isCurrentlyPlaying = isCurrentSong && playbackState.isPlaying

                                            if (!isCurrentlyPlaying) {
                                                if (!isCurrentSong && pageSong != null && onSongClick != null) {
                                                    onSongClick(pageSong, page)
                                                } else if (!playbackState.isPlaying) {
                                                    onTogglePlay()
                                                }
                                            } else {
                                                isCoverFlowMode = false
                                            }
                                        } else {
                                            // 点击两翼卡片：平滑翻页至该歌曲并切歌进行播放
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(page)
                                            }
                                            if (pageSong != null && onSongClick != null) {
                                                onSongClick(pageSong, page)
                                            }
                                        }
                                    }
                                }
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(currentCardAlpha)
                        ) {
                            // 1. 主体封面
                            val artUri = pageSong?.albumArtUri ?: pageSong?.let {
                                AudioCoverProvider.buildSongCoverUri(it.id, it.path, it.album)
                            }

                            Surface(
                                shape = currentShape,
                                shadowElevation = if (isCenterCard) (20.dp * currentCardAlpha) else 6.dp,
                                border = cardBorder,
                                modifier = Modifier.size(currentCoverSize)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(OrbitTheme.colors.surfaceCard),
                                    contentAlignment = Alignment.Center
                                ) {
                                    SubcomposeAsyncImage(
                                        model = coil.request.ImageRequest.Builder(context)
                                            .data(artUri)
                                            .memoryCacheKey("${artUri}_$coverVer")
                                            .diskCacheKey("${artUri}_$coverVer")
                                            .size(Size(360, 360))
                                            .allowHardware(true)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = pageSong?.title,
                                        contentScale = ContentScale.Crop,
                                        loading = {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MusicNote,
                                                    contentDescription = null,
                                                    tint = animatedBorderColor.copy(alpha = 0.35f),
                                                    modifier = Modifier.size(48.dp)
                                                )
                                            }
                                        },
                                        error = {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MusicNote,
                                                    contentDescription = null,
                                                    tint = OrbitTheme.colors.textSecondary.copy(alpha = 0.35f),
                                                    modifier = Modifier.size(48.dp)
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(
                                                if (isCenterCard) Modifier.rotate(activeRotation)
                                                else Modifier
                                            )
                                    )

                                    // 正在播放的当前歌曲徽章 (与歌曲列表 Cover Flow 保持完全一致)
                                    if (isPlayingThis) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(6.dp)
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(animatedBorderColor.copy(alpha = 0.88f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (playbackState.isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    // 封面下载进度提示遮罩
                                    if (isCenterCard && isDownloadingCover) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.65f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(36.dp),
                                                    color = animatedBorderColor,
                                                    strokeWidth = 3.dp
                                                )
                                                Text(
                                                    text = stringResource(R.string.cover_downloading),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. 真实拟物镜面倒影 (与歌曲列表 Cover Flow 保持完全一致)
                            if (currentReflectionHeight > 2.dp) {
                                Box(
                                    modifier = Modifier
                                        .size(width = currentCoverSize, height = currentReflectionHeight)
                                        .clipToBounds()
                                        .graphicsLayer {
                                            compositingStrategy = CompositingStrategy.Offscreen
                                        }
                                        .drawWithContent {
                                            drawContent()
                                            drawRect(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.White.copy(alpha = 0.85f * wingsAlpha),
                                                        Color.White.copy(alpha = 0.35f * wingsAlpha),
                                                        Color.Transparent
                                                    )
                                                ),
                                                blendMode = BlendMode.DstIn
                                            )
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                this.scaleY = -1f // 垂直反转
                                            }
                                            .background(OrbitTheme.colors.surfaceCard)
                                    ) {
                                        SubcomposeAsyncImage(
                                            model = coil.request.ImageRequest.Builder(context)
                                                .data(artUri)
                                                .memoryCacheKey("${artUri}_$coverVer")
                                                .diskCacheKey("${artUri}_$coverVer")
                                                .size(Size(360, 360))
                                                .allowHardware(true)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            alignment = Alignment.BottomCenter,
                                            loading = {
                                                Box(modifier = Modifier.fillMaxSize())
                                            },
                                            error = {
                                                Box(modifier = Modifier.fillMaxSize())
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2.5 Cover Flow 模式下中央当前曲目名称与艺术家优雅渐显 (格式与歌曲列表保持完全一致，稍微向上移动避让底部手势横条)
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val baseOffset = (flowCoverSize / 2) + (flowCoverSize * 0.42f) - (if (isLandscape) 46.dp else 16.dp)
        val maxAllowedOffset = (screenHeight / 2) - navBarBottom - 52.dp
        val infoOffsetY = minOf(baseOffset, maxAllowedOffset)

        AnimatedVisibility(
            visible = isCoverFlowMode,
            enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = infoOffsetY)
        ) {
            val currentViewSong = playlist.getOrNull(pagerState.currentPage) ?: song
            if (currentViewSong != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 440.dp)
                ) {
                    Text(
                        text = currentViewSong.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${currentViewSong.artist} • ${currentViewSong.album}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 3. 顶部悬浮操作胶囊栏 (在 Cover Flow 模式下清屏隐藏)
        MaximizedTopControlBar(
            showTopControlBar = showTopControlBar && !isCoverFlowMode,
            topPadding = topBarPaddingTop,
            equalizerUiState = equalizerUiState,
            isDualColor = isDualColor,
            onToggleMaximizedShowCover = onToggleMaximizedShowCover,
            onToggleMaximizedCoverPosition = onToggleMaximizedCoverPosition,
            onSetMaximizedCoverAlpha = onSetMaximizedCoverAlpha,
            onToggleMaximizedCoverRotating = onToggleMaximizedCoverRotating,
            onToggleClearScreen = toggleAllControls,
            onToggleFollowCoverColor = onToggleFollowCoverColor,
            onCycleVisualizerStyle = onCycleVisualizerStyle,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 4. 底部悬浮播放控制卡片 (在 Cover Flow 模式下清屏隐藏)
        AnimatedVisibility(
            visible = equalizerUiState.maximizedShowControls && !isCoverFlowMode,
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

/**
 * 全屏最大化动态频谱界面的顶部悬浮控制胶囊栏
 * 独立抽取为 Composable 函数，享受 Compose Smart Recomposition 机制，
 * 在 visualizerFrame 以 60FPS 极高频刷新时完全跳过重组，确保触摸手势事件机稳定、极度灵敏响应。
 */
@Composable
private fun MaximizedTopControlBar(
    showTopControlBar: Boolean,
    topPadding: androidx.compose.ui.unit.Dp,
    equalizerUiState: EqualizerUiState,
    isDualColor: Boolean,
    onToggleMaximizedShowCover: (Boolean) -> Unit,
    onToggleMaximizedCoverPosition: (Boolean) -> Unit,
    onSetMaximizedCoverAlpha: (Float) -> Unit,
    onToggleMaximizedCoverRotating: (Boolean) -> Unit,
    onToggleClearScreen: () -> Unit,
    onToggleFollowCoverColor: (Boolean) -> Unit,
    onCycleVisualizerStyle: (() -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AnimatedVisibility(
        visible = showTopControlBar,
        enter = slideInVertically(tween(250)) { -it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(200)) { -it } + fadeOut(tween(150)),
        modifier = modifier.padding(top = topPadding)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color(0xDD181826),
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {} // 拦截空白处点击穿透，防止误触导致全屏控制栏意外隐藏
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. 开关封面
                IconButton(
                    onClick = { onToggleMaximizedShowCover(!equalizerUiState.maximizedShowCover) },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (equalizerUiState.maximizedShowCover) Icons.Default.Image else Icons.Default.HideImage,
                        contentDescription = stringResource(if (equalizerUiState.maximizedShowCover) R.string.maximized_hide_cover else R.string.maximized_show_cover),
                        tint = if (equalizerUiState.maximizedShowCover) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 2. 切换封面位置 (居左 / 居右，仅在显示封面时出现)
                if (equalizerUiState.maximizedShowCover) {
                    IconButton(
                        onClick = { onToggleMaximizedCoverPosition(!equalizerUiState.maximizedCoverOnRight) },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (equalizerUiState.maximizedCoverOnRight) Icons.Default.FormatAlignRight else Icons.Default.FormatAlignLeft,
                            contentDescription = stringResource(R.string.maximized_cover_position),
                            tint = OrbitTheme.colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 3. 调节封面透明度 (循环切换 100% -> 75% -> 50% -> 25%)
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
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Opacity,
                            contentDescription = stringResource(R.string.maximized_cover_alpha),
                            tint = OrbitTheme.colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 4. 切换黑胶唱盘旋转
                    val rotateEnableTip = stringResource(R.string.maximized_cover_rotate_enabled)
                    val rotateDisableTip = stringResource(R.string.maximized_cover_rotate_disabled)
                    IconButton(
                        onClick = {
                            val nextRotating = !equalizerUiState.maximizedCoverRotating
                            onToggleMaximizedCoverRotating(nextRotating)
                            Toast.makeText(
                                context,
                                if (nextRotating) rotateEnableTip else rotateDisableTip,
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = stringResource(R.string.maximized_cover_rotate),
                            tint = if (equalizerUiState.maximizedCoverRotating) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 5. 一键清屏 (同时隐藏/显示上下方控制条)
                IconButton(
                    onClick = onToggleClearScreen,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = stringResource(R.string.maximized_clear_screen),
                        tint = OrbitTheme.colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 6. 频谱颜色跟随专辑封面
                val dualOnlyHint = stringResource(R.string.maximized_follow_cover_dual_only_hint)
                IconButton(
                    onClick = {
                        if (!isDualColor) {
                            Toast.makeText(context, dualOnlyHint, Toast.LENGTH_SHORT).show()
                        } else {
                            onToggleFollowCoverColor(!equalizerUiState.followCoverColorInMaximized)
                        }
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .alpha(if (isDualColor) 1.0f else 0.38f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = stringResource(R.string.maximized_follow_cover_color),
                        tint = if (equalizerUiState.followCoverColorInMaximized && isDualColor) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 7. 频谱样式切换按钮
                IconButton(
                    onClick = { onCycleVisualizerStyle?.invoke() },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = stringResource(R.string.switch_visualizer_style),
                        tint = OrbitTheme.colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                // 8. 退出最大化全屏按钮
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(38.dp)
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
}

/**
 * 3D Cover Flow 页面变换函数：
 * 支持 Y 轴立体旋转倾角 (48°)、两翼层叠推进、景深缩放以及边缘渐变消隐
 * 在常规模式下两翼 alpha 被 wingsAlpha (0f) 完全隐藏，当进入 Cover Flow 模式时两翼平滑渐显展开
 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.maximizedCoverFlowTransform(
    page: Int,
    pagerState: PagerState,
    cardWidthPx: Float,
    isLandscape: Boolean,
    cameraDistancePx: Float,
    wingsAlpha: Float
): Modifier = this.graphicsLayer {
    val pageCount = pagerState.pageCount
    val offset = try {
        if (pageCount > 0 && page in 0 until pageCount) {
            -pagerState.getOffsetFractionForPage(page)
        } else {
            -(pagerState.currentPage - page + pagerState.currentPageOffsetFraction)
        }
    } catch (e: Exception) {
        -(pagerState.currentPage - page + pagerState.currentPageOffsetFraction)
    }
    val absOffset = offset.absoluteValue
    val sign = if (offset >= 0f) 1f else -1f

    val centerSpread = cardWidthPx * (if (isLandscape) 0.60f else 0.56f)
    val wingOverlapStep = cardWidthPx * (if (isLandscape) 0.24f else 0.20f)

    val targetX = if (absOffset <= 1f) {
        offset * centerSpread
    } else {
        sign * (centerSpread + (absOffset - 1f) * wingOverlapStep)
    }
    val rawX = offset * cardWidthPx
    val translationPx = targetX - rawX

    val maxRotationAngle = 48f
    val rotation = if (absOffset <= 1f) {
        -offset * maxRotationAngle
    } else {
        -sign * maxRotationAngle
    }
    val scale = when {
        absOffset <= 1f -> 1f - absOffset * 0.10f
        else -> (0.90f - (absOffset - 1f) * 0.015f).coerceAtLeast(0.76f)
    }

    val edgeAlpha = when {
        absOffset <= 6.2f -> 1f
        absOffset >= 8.2f -> 0f
        else -> (1f - (absOffset - 6.2f) / 2.0f).coerceIn(0f, 1f)
    }

    val finalAlpha = if (absOffset < 0.05f) {
        1f
    } else {
        edgeAlpha * wingsAlpha
    }

    translationX = translationPx
    rotationY = rotation
    scaleX = scale
    scaleY = scale
    cameraDistance = cameraDistancePx
    alpha = finalAlpha
}

enum class NowPlayingMiddleTab(val title: String) {
    SONGS("全部歌曲"),
    FOLDERS("文件夹"),
    ARTISTS("歌手"),
    PLAYLISTS("歌单"),
    ALBUMS("专辑")
}

@Composable
fun NowPlayingLibraryMiddleColumn(
    viewModel: MusicPlayerViewModel,
    allSongs: List<Song>,
    favoriteSongs: List<Song>,
    dislikedSongs: List<Song>,
    playlists: List<Playlist>,
    playbackState: PlaybackState,
    coverVersion: Long,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(NowPlayingMiddleTab.SONGS) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    var openedFolder by remember { mutableStateOf<FolderItem?>(null) }
    var openedArtist by remember { mutableStateOf<ArtistItem?>(null) }
    var openedAlbum by remember { mutableStateOf<AlbumItem?>(null) }
    var openedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var playlistSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    val folders by viewModel.folders.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val albums by viewModel.albums.collectAsState()

    // 监听歌单详情变化
    LaunchedEffect(openedPlaylist, playlists, favoriteSongs, dislikedSongs) {
        val p = openedPlaylist
        if (p != null) {
            playlistSongs = when (p.id) {
                -999L -> favoriteSongs
                -998L -> dislikedSongs
                else -> viewModel.getSongsInPlaylist(p.id)
            }
        } else {
            playlistSongs = emptyList()
        }
    }

    val isDrillDown = openedFolder != null || openedArtist != null || openedAlbum != null || openedPlaylist != null

    val currentSongList: List<Song>? = when {
        openedFolder != null -> allSongs.filter { it.folderPath == openedFolder!!.folderPath }
        openedArtist != null -> allSongs.filter { it.artist.trim().equals(openedArtist!!.name.trim(), ignoreCase = true) }
        openedAlbum != null -> allSongs.filter { it.album.trim().equals(openedAlbum!!.title.trim(), ignoreCase = true) }
        openedPlaylist != null -> playlistSongs
        selectedTab == NowPlayingMiddleTab.SONGS -> allSongs
        else -> null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        // ── 顶部栏：分类 Tabs 或 二级详情面包屑 ──
        if (isDrillDown) {
            val (titleText, subText) = when {
                openedFolder != null -> openedFolder!!.folderName to "${currentSongList?.size ?: 0} 首歌曲"
                openedArtist != null -> openedArtist!!.name to "${openedArtist!!.albumCount} 专辑 • ${currentSongList?.size ?: 0} 首歌曲"
                openedAlbum != null -> openedAlbum!!.title to "${openedAlbum!!.artist} • ${currentSongList?.size ?: 0} 首歌曲"
                openedPlaylist != null -> openedPlaylist!!.name to "${currentSongList?.size ?: 0} 首歌曲"
                else -> "" to ""
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = {
                            openedFolder = null
                            openedArtist = null
                            openedAlbum = null
                            openedPlaylist = null
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = OrbitTheme.colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = titleText,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrbitTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subText,
                            fontSize = 10.5.sp,
                            color = OrbitTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 搜索按钮
                IconButton(
                    onClick = { isSearchActive = !isSearchActive },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (isSearchActive || searchQuery.isNotBlank()) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            // 一级分类胶囊 Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NowPlayingMiddleTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.surface.copy(alpha = 0.5f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.surfaceBorder.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.clickable {
                                selectedTab = tab
                                searchQuery = ""
                            }
                        ) {
                            Text(
                                text = tab.title,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else OrbitTheme.colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = { isSearchActive = !isSearchActive },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (isSearchActive || searchQuery.isNotBlank()) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        // 搜索输入框 (展开时显示)
        AnimatedVisibility(
            visible = isSearchActive || searchQuery.isNotBlank(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                placeholder = {
                    Text(
                        text = "快速搜索...",
                        fontSize = 11.5.sp,
                        color = OrbitTheme.colors.textSecondary
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = OrbitTheme.colors.textSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = OrbitTheme.colors.textSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
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
        }

        // ── 中间列表展示 ──
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            if (currentSongList != null) {
                // 渲染歌曲列表
                val q = searchQuery.trim()
                val filteredSongs = remember(currentSongList, q) {
                    if (q.isBlank()) currentSongList
                    else currentSongList.filter {
                        it.title.contains(q, ignoreCase = true) ||
                        it.artist.contains(q, ignoreCase = true) ||
                        it.album.contains(q, ignoreCase = true)
                    }
                }

                if (filteredSongs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (q.isNotBlank()) "未找到相关歌曲" else "列表暂无歌曲",
                            color = OrbitTheme.colors.textSecondary.copy(alpha = 0.6f),
                            fontSize = 12.5.sp
                        )
                    }
                } else {
                    val listState = rememberLazyListState()
                    val currentPlayingSongId = playbackState.currentSong?.id

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        itemsIndexed(
                            items = filteredSongs,
                            key = { idx, s -> "${s.id}_${s.path}_$idx" }
                        ) { index, s ->
                            val isCurrentPlaying = s.id == currentPlayingSongId
                            CompactSongRow(
                                index = index + 1,
                                song = s,
                                isPlaying = playbackState.isPlaying,
                                isCurrentPlaying = isCurrentPlaying,
                                onClick = {
                                    viewModel.playSong(filteredSongs, index)
                                }
                            )
                        }
                    }
                }
            } else {
                // 渲染分类一级列表（文件夹、歌手、歌单、专辑）
                when (selectedTab) {
                    NowPlayingMiddleTab.FOLDERS -> {
                        val q = searchQuery.trim()
                        val filteredFolders = remember(folders, q) {
                            if (q.isBlank()) folders
                            else folders.filter { it.folderName.contains(q, ignoreCase = true) || it.folderPath.contains(q, ignoreCase = true) }
                        }
                        if (filteredFolders.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = "未找到文件夹", color = OrbitTheme.colors.textSecondary.copy(alpha = 0.6f), fontSize = 12.5.sp)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                items(filteredFolders, key = { it.folderPath }) { folder ->
                                    CategoryRow(
                                        icon = Icons.Default.Folder,
                                        title = folder.folderName,
                                        subtitle = "${folder.songCount} 首歌曲",
                                        onClick = { openedFolder = folder }
                                    )
                                }
                            }
                        }
                    }
                    NowPlayingMiddleTab.ARTISTS -> {
                        val q = searchQuery.trim()
                        val filteredArtists = remember(artists, q) {
                            if (q.isBlank()) artists
                            else artists.filter { it.name.contains(q, ignoreCase = true) }
                        }
                        if (filteredArtists.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = "未找到歌手", color = OrbitTheme.colors.textSecondary.copy(alpha = 0.6f), fontSize = 12.5.sp)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                items(filteredArtists, key = { it.name }) { artist ->
                                    CategoryRow(
                                        icon = Icons.Default.Person,
                                        title = artist.name,
                                        subtitle = "${artist.albumCount} 专辑 • ${artist.songCount} 首歌曲",
                                        onClick = { openedArtist = artist }
                                    )
                                }
                            }
                        }
                    }
                    NowPlayingMiddleTab.PLAYLISTS -> {
                        val systemPlaylists = remember(favoriteSongs.size, dislikedSongs.size) {
                            listOf(
                                Playlist(id = -999L, name = "我喜欢的音乐", songCount = favoriteSongs.size, createdAt = 0L),
                                Playlist(id = -998L, name = "过滤黑名单", songCount = dislikedSongs.size, createdAt = 0L)
                            )
                        }
                        val combinedPlaylists = remember(systemPlaylists, playlists) {
                            systemPlaylists + playlists
                        }
                        val q = searchQuery.trim()
                        val filteredPlaylists = remember(combinedPlaylists, q) {
                            if (q.isBlank()) combinedPlaylists
                            else combinedPlaylists.filter { it.name.contains(q, ignoreCase = true) }
                        }
                        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            items(filteredPlaylists, key = { it.id }) { playlist ->
                                CategoryRow(
                                    icon = if (playlist.id == -999L) Icons.Default.Favorite else Icons.AutoMirrored.Filled.PlaylistPlay,
                                    iconTint = if (playlist.id == -999L) Color(0xFFFF3366) else OrbitTheme.colors.primary,
                                    title = playlist.name,
                                    subtitle = "${playlist.songCount} 首歌曲",
                                    onClick = { openedPlaylist = playlist }
                                )
                            }
                        }
                    }
                    NowPlayingMiddleTab.ALBUMS -> {
                        val q = searchQuery.trim()
                        val filteredAlbums = remember(albums, q) {
                            if (q.isBlank()) albums
                            else albums.filter { it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true) }
                        }
                        if (filteredAlbums.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = "未找到专辑", color = OrbitTheme.colors.textSecondary.copy(alpha = 0.6f), fontSize = 12.5.sp)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                items(filteredAlbums, key = { "${it.id}_${it.title}" }) { album ->
                                    CategoryRow(
                                        icon = Icons.Default.Album,
                                        title = album.title,
                                        subtitle = "${album.artist} • ${album.songCount} 首歌曲",
                                        albumArtUri = album.albumArtUri,
                                        onClick = { openedAlbum = album }
                                    )
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
fun CompactSongRow(
    index: Int,
    song: Song,
    isPlaying: Boolean,
    isCurrentPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isCurrentPlaying) OrbitTheme.colors.primary.copy(alpha = 0.14f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：序号或正在播放跳动图标
        Box(
            modifier = Modifier.width(22.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isCurrentPlaying) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Equalizer else Icons.Default.Pause,
                    contentDescription = null,
                    tint = OrbitTheme.colors.primary,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = "$index",
                    fontSize = 11.sp,
                    color = OrbitTheme.colors.textSecondary.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // 中间：歌曲名与歌手
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 13.sp,
                fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrentPlaying) OrbitTheme.colors.primary else OrbitTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = "${song.artist} • ${song.album}",
                fontSize = 10.5.sp,
                color = OrbitTheme.colors.textSecondary.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // 右侧：时长
        Text(
            text = song.formattedDuration,
            fontSize = 10.5.sp,
            color = OrbitTheme.colors.textSecondary.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun CategoryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = OrbitTheme.colors.primary,
    albumArtUri: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(7.dp),
            color = OrbitTheme.colors.surface,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (!albumArtUri.isNullOrBlank()) {
                    AsyncImage(
                        model = albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = OrbitTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                color = OrbitTheme.colors.textSecondary.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = OrbitTheme.colors.textSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}

