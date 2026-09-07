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
import com.antigravity.equalizer.audio.RepeatMode
import com.antigravity.equalizer.audio.ShuffleStrategy
import com.antigravity.equalizer.data.model.ProgressTrailStyle
import com.antigravity.equalizer.data.model.Song
import com.antigravity.equalizer.data.model.SongAttitude
import com.antigravity.equalizer.ui.theme.*
import com.antigravity.equalizer.ui.utils.swipeToChangeSong
import com.antigravity.equalizer.ui.viewmodel.EqualizerUiState
import com.antigravity.equalizer.ui.viewmodel.MusicPlayerViewModel
import com.antigravity.equalizer.utils.LyricLine
import com.antigravity.equalizer.utils.LyricParser
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: MusicPlayerViewModel,
    equalizerUiState: EqualizerUiState,
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playbackState by viewModel.playbackState.collectAsState()
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
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // 1. 封面视图
        val coverView: @Composable (Modifier) -> Unit = { mod ->
            Box(
                modifier = mod
                    .scale(coverScale)
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(18.dp),
                        spotColor = OrbitTheme.colors.primary.copy(alpha = 0.35f)
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .background(OrbitTheme.colors.surfaceCard),
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
            // ========== 专业横屏唱片与歌词分屏布局 (Vinyl & Lyrics Landscape Layout) ==========
            Row(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .swipeToChangeSong(
                        onSwipeNext = { viewModel.playNext() },
                        onSwipePrevious = { viewModel.playPrevious() }
                    ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧唱片信息区 (占 40% 宽度)
                Column(
                    modifier = Modifier
                        .weight(0.40f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    coverView(Modifier.size(150.dp))
                    trackInfoView()
                    quickActionsView()
                }

                // 右侧全景歌词与控制区 (占 60% 宽度)
                Column(
                    modifier = Modifier
                        .weight(0.60f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    lyricsView(Modifier.weight(1f).fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
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
                    coverView(Modifier.size(190.dp))
                }

                Spacer(modifier = Modifier.height(4.dp))
                lyricsView(Modifier.fillMaxWidth().height(105.dp))
                Spacer(modifier = Modifier.height(6.dp))
                trackInfoView()
                Spacer(modifier = Modifier.height(8.dp))
                quickActionsView()
                Spacer(modifier = Modifier.height(6.dp))
                progressSliderView()
                Spacer(modifier = Modifier.height(12.dp))
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
            var showCoverInQueue by remember { mutableStateOf(true) }

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
                            onClick = { showCoverInQueue = !showCoverInQueue },
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
            containerColor = OrbitTheme.colors.surfaceCard
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
            containerColor = OrbitTheme.colors.surfaceCard
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
    trailStyle: String = ProgressTrailStyle.NEON_PULSE.id,
    startWidthDp: Float = 3.8f,
    endWidthDp: Float = 1.2f,
    orbitRadiusDp: Float = 9.5f,
    color1: Long = 0xFF00FFFFL,
    color2: Long = 0xFF5E72E4L,
    modifier: Modifier = Modifier
) {
    val progress = value.coerceIn(0f, 1f)

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
            animation = tween(8500, easing = LinearEasing),
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
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
        val primaryColor = OrbitTheme.colors.primary
        val secondaryColor = OrbitTheme.colors.secondary
        val surfaceTrackColor = OrbitTheme.colors.surface
        val selectedStyle = ProgressTrailStyle.fromId(trailStyle)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val centerY = h / 2f
            val trackHeight = 4.dp.toPx()
            val thumbX = progress * w

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
                    // ==================== 样式一：图片同款高能霓虹双发光管 (3D 缠绕立体旋转，纯线条无杂点) ====================
                    val maxTailLength = 76.dp.toPx()
                    val tailLength = minOf(thumbX, maxTailLength)

                    if (tailLength > 3f) {
                        val maxAmp = orbitRadiusDp.dp.toPx() // 根据用户设置的环绕半径环抱缠绕轨道
                        val phaseRad = tailRotationPhase * (PI.toFloat() / 180f)
                        val segments = 48 // 48 级高密度平滑微元，保证线条完全平滑一体，圆润无缝

                        class NeonTubeNode(
                            val x: Float,
                            val y: Float,
                            val z: Float,
                            val u: Float,
                            val alpha: Float,
                            val tubeWidth: Float
                        )

                        fun buildNeonTubeNodes(phaseOffset: Float): List<NeonTubeNode> {
                            val list = ArrayList<NeonTubeNode>(segments + 1)
                            for (i in 0..segments) {
                                val u = i / segments.toFloat() // 0(滑块星核) ~ 1(拖尾尾梢)
                                val x = thumbX - u * tailLength
                                val envelope = (sin(u * PI.toFloat())).pow(0.85f) * (1f - 0.16f * u)
                                val amp = maxAmp * envelope
                                val theta = phaseRad - u * (3.2f * PI.toFloat()) + phaseOffset
                                val y = centerY + amp * sin(theta)
                                val z = cos(theta) // 深度: > 0 为前景(穿过轨道正面)，< 0 为后景(穿到轨道背面)

                                val depthAlpha = 0.65f + 0.35f * ((z + 1f) * 0.5f)
                                val alpha = (1f - u).pow(1.05f) * twinkleAlpha * depthAlpha
                                // 霓虹发光管直径：基于用户设定的起点与终点做连续平滑插值，配合三维 z 深度微透视
                                val baseWidthPx = (startWidthDp * (1f - u) + endWidthDp * u).dp.toPx()
                                val width = (baseWidthPx * (1f + 0.28f * z)).coerceAtLeast(0.5f.dp.toPx())
                                list.add(NeonTubeNode(x, y, z, u, alpha.coerceIn(0f, 1f), width))
                            }
                            return list
                        }

                        val neonTube1 = buildNeonTubeNodes(0f)            // 线条 1
                        val neonTube2 = buildNeonTubeNodes(PI.toFloat())  // 线条 2

                        // 动态派生两条线条各自的高饱和四层发光霓虹调色板
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

                        // 绘制图片同款四层物理发光管 (纯 Color 绘制，稳定高亮，零杂点，通透立体)
                        fun drawNeonTube(
                            nodes: List<NeonTubeNode>,
                            isForeground: Boolean,
                            bloomColor: Color,
                            neonColor: Color,
                            solidColor: Color,
                            coreColor: Color
                        ) {
                            if (nodes.size < 2) return
                            for (i in 0 until nodes.size - 1) {
                                val n1 = nodes[i]
                                val n2 = nodes[i + 1]
                                val avgZ = (n1.z + n2.z) * 0.5f
                                val isNodeFg = avgZ >= 0f
                                if (isNodeFg == isForeground) {
                                     val segAlpha = ((n1.alpha + n2.alpha) * 0.5f).coerceIn(0f, 1f)
                                     if (segAlpha > 0.01f) {
                                         val p1 = Offset(n1.x, n1.y)
                                         val p2 = Offset(n2.x, n2.y)
                                         val strokeW = (n1.tubeWidth + n2.tubeWidth) * 0.5f
                                         val fgFactor = if (isForeground) 1.0f else 0.70f

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
                                         // 3. 第三层高饱和实心发光管壁
                                         drawLine(
                                             color = solidColor.copy(alpha = segAlpha * 0.95f * fgFactor),
                                             start = p1,
                                             end = p2,
                                             strokeWidth = strokeW * 0.95f,
                                             cap = StrokeCap.Round
                                         )
                                         // 4. 最内层耀眼纯白高能电弧核心
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

                        // 1. 霓虹双线条·后景绘制 (穿插在进度条后方，背光自然沉降)
                        drawNeonTube(
                            neonTube1,
                            isForeground = false,
                            bloomColor = tube1Bloom,
                            neonColor = tube1Neon,
                            solidColor = tube1Solid,
                            coreColor = tube1Core
                        )
                        drawNeonTube(
                            neonTube2,
                            isForeground = false,
                            bloomColor = tube2Bloom,
                            neonColor = tube2Neon,
                            solidColor = tube2Solid,
                            coreColor = tube2Core
                        )

                        // 2. 已播放流光基座轨道 (作为底衬被前景线条压过)
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

                        // 3. 霓虹双线条·前景绘制 (高亮跨越在轨道正面，通透饱满，纯线条无杂点)
                        drawNeonTube(
                            neonTube1,
                            isForeground = true,
                            bloomColor = tube1Bloom,
                            neonColor = tube1Neon,
                            solidColor = tube1Solid,
                            coreColor = tube1Core
                        )
                        drawNeonTube(
                            neonTube2,
                            isForeground = true,
                            bloomColor = tube2Bloom,
                            neonColor = tube2Neon,
                            solidColor = tube2Solid,
                            coreColor = tube2Core
                        )
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
                    // ==================== 样式二：彗星双拖尾 3D 缠绕模型 (原有样式) ====================
                    val maxTailLength = 68.dp.toPx()
                    val tailLength = minOf(thumbX, maxTailLength)
                    val segments = 32
                    val maxAmp = orbitRadiusDp.dp.toPx()
                    val phaseRad = tailRotationPhase * (PI.toFloat() / 180f)

                    class CometNode(
                        val x: Float,
                        val y: Float,
                        val z: Float,
                        val u: Float,
                        val alpha: Float,
                        val width: Float
                    )

                    fun buildCometNodes(phaseOffset: Float): List<CometNode> {
                        if (tailLength < 3f) return emptyList()
                        val list = ArrayList<CometNode>(segments + 1)
                        for (i in 0..segments) {
                            val u = i / segments.toFloat()
                            val x = thumbX - u * tailLength
                            val envelope = (sin(u * PI.toFloat())).pow(0.85f) * (1f - 0.2f * u)
                            val amp = maxAmp * envelope
                            val theta = phaseRad - u * (3.0f * PI.toFloat()) + phaseOffset
                            val y = centerY + amp * sin(theta)
                            val z = cos(theta)

                            val depthAlpha = 0.55f + 0.45f * ((z + 1f) * 0.5f)
                            val alpha = (1f - u).pow(1.1f) * twinkleAlpha * depthAlpha
                            val baseW = (startWidthDp * (1f - u) + endWidthDp * u).dp.toPx()
                            val strokeW = (baseW * (1f + 0.25f * z)).coerceAtLeast(0.5f)
                            list.add(CometNode(x, y, z, u, alpha.coerceIn(0f, 1f), strokeW))
                        }
                        return list
                    }

                    val trail1 = buildCometNodes(0f)
                    val trail2 = buildCometNodes(PI.toFloat())

                    val cometC1 = Color(color1)
                    val cometC2 = Color(color2)

                    fun drawTrail(nodes: List<CometNode>, isForeground: Boolean, coreColor: Color, glowColor: Color) {
                        if (nodes.size < 2) return
                        for (i in 0 until nodes.size - 1) {
                            val n1 = nodes[i]
                            val n2 = nodes[i + 1]
                            val avgZ = (n1.z + n2.z) * 0.5f
                            val isNodeFg = avgZ >= 0f
                            if (isNodeFg == isForeground) {
                                val segAlpha = ((n1.alpha + n2.alpha) * 0.5f).coerceIn(0f, 1f)
                                if (segAlpha > 0.02f) {
                                    drawLine(
                                        color = glowColor.copy(alpha = segAlpha * 0.45f),
                                        start = Offset(n1.x, n1.y),
                                        end = Offset(n2.x, n2.y),
                                        strokeWidth = (n1.width + n2.width) * 1.5f,
                                        cap = StrokeCap.Round
                                    )
                                    drawLine(
                                        color = coreColor.copy(alpha = segAlpha * 0.95f),
                                        start = Offset(n1.x, n1.y),
                                        end = Offset(n2.x, n2.y),
                                        strokeWidth = (n1.width + n2.width) * 0.55f,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }

                        val sampleIndices = intArrayOf(2, 6, 11, 17, 24)
                        for (idx in sampleIndices) {
                            if (idx < nodes.size) {
                                val node = nodes[idx]
                                val isNodeFg = node.z >= 0f
                                if (isNodeFg == isForeground && node.alpha > 0.05f) {
                                    val r = (if (isNodeFg) 1.8.dp.toPx() else 1.1.dp.toPx()) * (1f - node.u * 0.5f)
                                    drawCircle(
                                        color = glowColor.copy(alpha = node.alpha * 0.65f),
                                        radius = r * 1.8f,
                                        center = Offset(node.x, node.y)
                                    )
                                    drawCircle(
                                        color = Color.White.copy(alpha = node.alpha * 0.95f),
                                        radius = r,
                                        center = Offset(node.x, node.y)
                                    )
                                }
                            }
                        }
                    }

                    // 彗星双拖尾·后景
                    drawTrail(trail1, isForeground = false, coreColor = Color.White, glowColor = cometC1)
                    drawTrail(trail2, isForeground = false, coreColor = androidx.compose.ui.graphics.lerp(cometC2, Color.White, 0.7f), glowColor = cometC2)

                    // 已播放流光轨道
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
                    drawTrail(trail1, isForeground = true, coreColor = Color.White, glowColor = cometC1)
                    drawTrail(trail2, isForeground = true, coreColor = androidx.compose.ui.graphics.lerp(cometC2, Color.White, 0.7f), glowColor = cometC2)
                }

                ProgressTrailStyle.MINIMAL -> {
                    // ==================== 样式三：经典极简 (仅保留流光轨道，无多余拖尾) ====================
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

            // 5. 漫反射外散微光晕 (光晕半径适度减少至 15dp)
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

            // 4. 旋转四角星芒十字光辉 (针长减至 8.5dp，线宽减至 1.4dp)
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
