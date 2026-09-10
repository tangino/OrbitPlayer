package com.antigravity.equalizer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.antigravity.equalizer.data.model.AlbumItem
import com.antigravity.equalizer.data.model.ArtistItem
import com.antigravity.equalizer.ui.components.AlbumItem
import com.antigravity.equalizer.ui.components.MiniPlayerBar
import com.antigravity.equalizer.ui.components.SelectAlbumCoverDialog
import com.antigravity.equalizer.ui.components.SongItem
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.res.stringResource
import com.antigravity.equalizer.R
import com.antigravity.equalizer.data.model.Playlist
import com.antigravity.equalizer.data.model.Song
import com.antigravity.equalizer.ui.components.PowerampViewModeTransitionContainer
import com.antigravity.equalizer.ui.theme.*
import com.antigravity.equalizer.ui.utils.pinchToZoomViewMode
import com.antigravity.equalizer.ui.utils.rememberPinchTransitionState
import android.widget.Toast
import com.antigravity.equalizer.ui.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.LazyGridState

/**
 * 跨 ViewMode 视图模式的同步滚动状态管理器：
 * 为每个 LibraryViewMode 维护独立的 LazyGridState，彻底杜绝 AnimatedContent 交叉淡入淡出期间
 * 两个并发布局共享同一 State 导致的 IllegalStateException，同时无缝延续切换前后的滚动位置。
 */
class SynchronizedGridStateHolder {
    val stateMap = mutableMapOf<LibraryViewMode, LazyGridState>()
    var lastKnownIndex by mutableStateOf(0)
    var lastKnownOffset by mutableStateOf(0)

    @Composable
    fun getOrCreate(mode: LibraryViewMode): LazyGridState {
        val state = remember(mode) {
            stateMap.getOrPut(mode) {
                LazyGridState(lastKnownIndex, lastKnownOffset)
            }
        }
        LaunchedEffect(state) {
            snapshotFlow { Pair(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) }
                .collect { (idx, off) ->
                    lastKnownIndex = idx
                    lastKnownOffset = off
                }
        }
        return state
    }

    val isScrollInProgress: Boolean
        get() = stateMap.values.any { it.isScrollInProgress }

    suspend fun animateScrollToItem(index: Int, mode: LibraryViewMode) {
        lastKnownIndex = index
        lastKnownOffset = 0
        stateMap[mode]?.animateScrollToItem(index)
    }
}

@Composable
fun rememberSynchronizedGridStateHolder(): SynchronizedGridStateHolder {
    return remember { SynchronizedGridStateHolder() }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MusicLibraryScreen(
    viewModel: MusicPlayerViewModel,
    isTabletMode: Boolean = false,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coverVer by com.antigravity.equalizer.utils.CoverHelper.coverVersion.collectAsState()
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            configuration.screenWidthDp > configuration.screenHeightDp
    val useTabletLayout = isTabletMode && (isLandscape || configuration.screenWidthDp >= 600)

    val libraryState by viewModel.libraryUiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    val filteredSongs by viewModel.filteredSongs.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val playlists by viewModel.playlists.collectAsState()

    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    // 播放列表管理状态 (重命名 / 删除)
    var renamingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var renamePlaylistName by remember { mutableStateOf("") }
    var deletingPlaylist by remember { mutableStateOf<Playlist?>(null) }

    // 播放列表下钻状态
    var openedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var playlistSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val playlistSongsListState = rememberLazyListState()

    // 歌曲添加对话框
    var showAddSongsToPlaylistDialog by remember { mutableStateOf(false) }
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    var activeSongForLongClickMenu by remember { mutableStateOf<Song?>(null) }
    var showSelectAlbumCoverDialog by remember { mutableStateOf(false) }
    var candidateCovers by remember { mutableStateOf<List<com.antigravity.equalizer.data.cover.MusicBrainzCoverService.AlbumCoverCandidate>>(emptyList()) }
    var candidateArtistName by remember { mutableStateOf("") }
    var candidateSong by remember { mutableStateOf<Song?>(null) }
    var isApplyingCover by remember { mutableStateOf(false) }
    var searchingSong by remember { mutableStateOf<Song?>(null) }

    // 文件夹下钻：当前展开的文件夹路径
    var openedFolderPath by remember { mutableStateOf<String?>(null) }
    // 专辑下钻：当前展开的专辑
    var openedAlbum by remember { mutableStateOf<AlbumItem?>(null) }
    // 艺术家下钻：当前展开的艺术家
    var openedArtist by remember { mutableStateOf<ArtistItem?>(null) }

    val favoriteSongs by viewModel.favoriteSongs.collectAsState()

    // 监听 openedPlaylist 变化动态获取歌曲列表
    LaunchedEffect(openedPlaylist, playlists, favoriteSongs) {
        val p = openedPlaylist
        if (p != null) {
            if (p.id == -999L) {
                playlistSongs = favoriteSongs
            } else {
                playlistSongs = viewModel.getSongsInPlaylist(p.id)
                val updated = playlists.find { it.id == p.id }
                if (updated != null && updated != p) {
                    openedPlaylist = updated
                }
            }
        } else {
            playlistSongs = emptyList()
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val songsGridHolder = rememberSynchronizedGridStateHolder()
    val folderSongsGridHolder = rememberSynchronizedGridStateHolder()
    val albumSongsGridHolder = rememberSynchronizedGridStateHolder()
    val artistSongsGridHolder = rememberSynchronizedGridStateHolder()
    val albumsGridHolder = rememberSynchronizedGridStateHolder()

    val foldersListState = rememberLazyListState()
    val artistsListState = rememberLazyListState()
    val playlistsListState = rememberLazyListState()


    // 1. 全屏播放页展开状态 -> 侧滑返回收起全屏播放页
    BackHandler(enabled = libraryState.isNowPlayingExpanded) {
        viewModel.setNowPlayingExpanded(false)
    }

    // 2. 文件夹下钻状态 -> 侧滑返回回到文件夹列表
    BackHandler(enabled = openedFolderPath != null) {
        openedFolderPath = null
    }

    // 3. 专辑下钻状态 -> 侧滑返回回到专辑列表
    BackHandler(enabled = openedAlbum != null) {
        openedAlbum = null
    }

    // 4. 艺术家下钻状态 -> 侧滑返回回到艺术家列表
    BackHandler(enabled = openedArtist != null) {
        openedArtist = null
    }

    // 5. 播放列表下钻状态 -> 侧滑返回回到播放列表列表
    BackHandler(enabled = openedPlaylist != null) {
        openedPlaylist = null
    }

    // 6. 搜索栏开启状态 -> 侧滑返回关闭搜索
    BackHandler(enabled = libraryState.isSearching) {
        viewModel.toggleSearch()
    }

    // 🎯 一键平滑滚动定位到当前播放歌曲
    fun locateCurrentPlayingSong() {
        val currentSongId = playbackState.currentSong?.id ?: return
        val targetIndex = filteredSongs.indexOfFirst { it.id == currentSongId }
        if (targetIndex >= 0) {
            if (libraryState.currentTab != LibraryTab.SONGS) {
                viewModel.setTab(LibraryTab.SONGS)
            }
            openedFolderPath = null
            openedAlbum = null
            openedArtist = null
            coroutineScope.launch {
                songsGridHolder.animateScrollToItem(targetIndex, libraryState.viewMode)
            }
        }
    }

    // 🎯 优化单曲点击：如果该歌曲已经是当前播放歌曲，则直接打开播放页面，不再重头播放；否则从该歌曲开始播放
    fun handleSongItemClick(songs: List<Song>, index: Int) {
        val clickedSong = songs.getOrNull(index) ?: return
        if (playbackState.currentSong?.id == clickedSong.id) {
            viewModel.setNowPlayingExpanded(true)
        } else {
            viewModel.playSong(songs, index)
        }
    }

    // 全 Tab 通用 Pinch 手势控制器与修饰符
    val pinchTransitionState = rememberPinchTransitionState()
    val pinchGestureModifier = Modifier.pinchToZoomViewMode(
        currentViewMode = libraryState.viewMode,
        pinchState = pinchTransitionState,
        onViewModeChange = { viewModel.setViewMode(it) }
    )

    // 定位正在播放歌曲的悬浮按钮
    @Composable
    fun LocatePlayingSongFab(fabModifier: Modifier = Modifier) {
        AnimatedVisibility(
            visible = playbackState.currentSong != null,
            enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.8f),
            exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.8f),
            modifier = fabModifier
        ) {
            Surface(
                onClick = { locateCurrentPlayingSong() },
                shape = CircleShape,
                color = OrbitTheme.colors.surfaceCard.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.5f)),
                shadowElevation = 8.dp,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Locate Playing Track",
                        tint = OrbitTheme.colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    // 手机端顶部标题栏与 Tab / 搜索栏
    val mobileTopBar: @Composable () -> Unit = {
        Column(modifier = Modifier.background(OrbitTheme.colors.background)) {
            TopAppBar(
                title = {
                    if (openedFolderPath != null) {
                        // 文件夹下钻标题
                        Column {
                            val folderName = openedFolderPath!!.substringAfterLast("/").ifEmpty { "Folder" }
                            Text(folderName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OrbitTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(openedFolderPath!!, fontSize = 11.sp, color = OrbitTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else if (openedAlbum != null) {
                        // 专辑下钻标题
                        Column {
                            Text(openedAlbum!!.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OrbitTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${openedAlbum!!.artist} • ${openedAlbum!!.songCount} tracks", fontSize = 11.sp, color = OrbitTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else if (openedArtist != null) {
                        // 艺术家下钻标题
                        Column {
                            Text(openedArtist!!.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OrbitTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${openedArtist!!.albumCount} albums • ${openedArtist!!.songCount} tracks", fontSize = 11.sp, color = OrbitTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Music Library",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = OrbitTheme.colors.textPrimary
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (openedFolderPath != null || openedAlbum != null || openedArtist != null) {
                        IconButton(onClick = {
                            openedFolderPath = null
                            openedAlbum = null
                            openedArtist = null
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = OrbitTheme.colors.textPrimary
                            )
                        }
                    }
                },
                actions = {
                    if (openedFolderPath == null && openedAlbum == null && openedArtist == null) {
                        IconButton(onClick = { viewModel.toggleSearch() }) {
                            Icon(
                                imageVector = if (libraryState.isSearching) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (libraryState.isSearching) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary
                            )
                        }
                    }

                    // 一键切换 6 档视图模式
                    IconButton(onClick = { viewModel.cycleViewMode() }) {
                        val icon = when (libraryState.viewMode) {
                            LibraryViewMode.LIST_NO_ART -> Icons.AutoMirrored.Filled.FormatListBulleted
                            LibraryViewMode.LIST_SMALL_ART -> Icons.AutoMirrored.Filled.ViewList
                            LibraryViewMode.LIST_LARGE_ART -> Icons.Default.ViewAgenda
                            LibraryViewMode.GRID_2_COL -> Icons.Default.GridView
                            LibraryViewMode.GRID_3_COL -> Icons.Default.GridOn
                            LibraryViewMode.GRID_4_COL -> Icons.Default.Apps
                        }
                        Icon(imageVector = icon, contentDescription = "View Mode", tint = OrbitTheme.colors.primary)
                    }

                    // 扫描本地媒体
                    IconButton(onClick = { viewModel.scanMedia() }) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Scan",
                            tint = if (isScanning) OrbitTheme.colors.tertiary else OrbitTheme.colors.textSecondary
                        )
                    }

                    // 程序设置入口 (取代原首页均衡器按钮)
                    IconButton(onClick = onOpenSettings) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = OrbitTheme.colors.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OrbitTheme.colors.background)
            )

            // 媒体库 Tab 分页栏 (下钻时隐藏 Tab 保持沉浸)
            if (openedFolderPath == null && openedAlbum == null && openedArtist == null) {
                ScrollableTabRow(
                    selectedTabIndex = libraryState.currentTab.ordinal,
                    containerColor = OrbitTheme.colors.background,
                    contentColor = OrbitTheme.colors.primary,
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[libraryState.currentTab.ordinal]),
                            color = OrbitTheme.colors.primary
                        )
                    }
                ) {
                    LibraryTab.values().forEach { tab ->
                        Tab(
                            selected = libraryState.currentTab == tab,
                            onClick = {
                                openedFolderPath = null
                                openedAlbum = null
                                openedArtist = null
                                viewModel.setTab(tab)
                            },
                            text = {
                                Text(
                                    text = getTabTitle(tab),
                                    fontWeight = if (libraryState.currentTab == tab) FontWeight.Bold else FontWeight.Normal,
                                    color = if (libraryState.currentTab == tab) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
                }
            }

            // 🎯 搜索框置于 Tab 分页栏和音乐列表之间 (支持丝滑垂直展开与即时检索)
            AnimatedVisibility(
                visible = libraryState.isSearching,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    OutlinedTextField(
                        value = libraryState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
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
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (libraryState.searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.setSearchQuery("") },
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
                            focusedContainerColor = OrbitTheme.colors.surfaceCard,
                            unfocusedContainerColor = OrbitTheme.colors.surfaceCard,
                            focusedBorderColor = OrbitTheme.colors.primary.copy(alpha = 0.7f),
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = OrbitTheme.colors.primary,
                            focusedTextColor = OrbitTheme.colors.textPrimary,
                            unfocusedTextColor = OrbitTheme.colors.textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // 核心音乐库内容视图 (支持 Pinch 缩放与 6 档视图切换)
    @Composable
    fun LibraryMainContent(bottomPadding: androidx.compose.ui.unit.Dp = 98.dp) {
            PowerampViewModeTransitionContainer(
                viewMode = libraryState.viewMode,
                pinchState = pinchTransitionState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(pinchGestureModifier)
            ) { currentViewMode ->
                val columnsCount = when {
                    useTabletLayout -> {
                        when (currentViewMode) {
                            // 平板模式下：列表一律采用「双栏列表」(2列)
                            LibraryViewMode.LIST_NO_ART,
                            LibraryViewMode.LIST_SMALL_ART,
                            LibraryViewMode.LIST_LARGE_ART -> 2
                            // 平板模式下：Grid 封面缩小放更多内容 (6~8列，默认GRID_3_COL为7列)
                            LibraryViewMode.GRID_2_COL -> 6
                            LibraryViewMode.GRID_3_COL -> 7
                            LibraryViewMode.GRID_4_COL -> 8
                        }
                    }
                    isLandscape -> {
                        when (currentViewMode) {
                            LibraryViewMode.LIST_NO_ART,
                            LibraryViewMode.LIST_SMALL_ART,
                            LibraryViewMode.LIST_LARGE_ART -> 1
                            LibraryViewMode.GRID_2_COL -> 4
                            LibraryViewMode.GRID_3_COL -> 5
                            LibraryViewMode.GRID_4_COL -> 6
                        }
                    }
                    else -> {
                        when (currentViewMode) {
                            LibraryViewMode.LIST_NO_ART,
                            LibraryViewMode.LIST_SMALL_ART,
                            LibraryViewMode.LIST_LARGE_ART -> 1
                            LibraryViewMode.GRID_2_COL -> 2
                            LibraryViewMode.GRID_3_COL -> 3
                            LibraryViewMode.GRID_4_COL -> 4
                        }
                    }
                }

                val isTabletList = useTabletLayout && (
                    currentViewMode == LibraryViewMode.LIST_NO_ART ||
                    currentViewMode == LibraryViewMode.LIST_SMALL_ART ||
                    currentViewMode == LibraryViewMode.LIST_LARGE_ART
                )

                val hSpacing = when {
                    isTabletList -> 10.dp
                    useTabletLayout -> 8.dp
                    currentViewMode == LibraryViewMode.GRID_4_COL -> 5.dp
                    currentViewMode == LibraryViewMode.GRID_3_COL -> 6.dp
                    currentViewMode == LibraryViewMode.GRID_2_COL -> 8.dp
                    else -> 0.dp
                }

                val vSpacing = when {
                    isTabletList -> 4.dp
                    useTabletLayout -> 8.dp
                    currentViewMode == LibraryViewMode.GRID_4_COL -> 5.dp
                    currentViewMode == LibraryViewMode.GRID_3_COL -> 6.dp
                    currentViewMode == LibraryViewMode.GRID_2_COL -> 8.dp
                    currentViewMode == LibraryViewMode.LIST_LARGE_ART -> 6.dp
                    else -> 3.dp
                }

                val folderSongsGridState = folderSongsGridHolder.getOrCreate(currentViewMode)
                val albumSongsGridState = albumSongsGridHolder.getOrCreate(currentViewMode)
                val artistSongsGridState = artistSongsGridHolder.getOrCreate(currentViewMode)
                val songsGridState = songsGridHolder.getOrCreate(currentViewMode)
                val albumsGridState = albumsGridHolder.getOrCreate(currentViewMode)

                // ========== 如果处于文件夹下钻内部，展示该文件夹的歌曲 ==========
                if (openedFolderPath != null) {
                    val folderSongs = filteredSongs.filter { it.folderPath == openedFolderPath }
                    if (folderSongs.isEmpty()) {
                        EmptyStateView(
                            title = stringResource(R.string.empty_folder_title),
                            subtitle = stringResource(R.string.empty_folder_desc)
                        )
                    } else {
                        LazyVerticalGrid(
                            state = folderSongsGridState,
                            columns = GridCells.Fixed(columnsCount),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 98.dp),
                            horizontalArrangement = Arrangement.spacedBy(hSpacing),
                            verticalArrangement = Arrangement.spacedBy(vSpacing),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(
                                items = folderSongs,
                                key = { index, song -> "${song.id}_$index" }
                            ) { index, song ->
                                SongItem(
                                    song = song,
                                    isPlaying = playbackState.isPlaying,
                                    isCurrent = playbackState.currentSong?.id == song.id,
                                    viewMode = currentViewMode,
                                    coverVersion = coverVer,
                                    onClick = { handleSongItemClick(folderSongs, index) },
                                    onFavoriteClick = { viewModel.cycleSongAttitude(song) },
                                    onLongClick = { activeSongForLongClickMenu = song }
                                )
                            }
                        }
                    }
                } else if (openedAlbum != null) {
                    // ========== 如果处于专辑下钻内部，展示该专辑的歌曲 ==========
                    val currentAlbum = openedAlbum!!
                    val albumSongs = remember(currentAlbum, filteredSongs) {
                        filteredSongs.filter { it.album == currentAlbum.title }
                    }
                    if (albumSongs.isEmpty()) {
                        EmptyStateView(
                            title = stringResource(R.string.empty_album_title),
                            subtitle = stringResource(R.string.empty_album_desc)
                        )
                    } else {
                        LazyVerticalGrid(
                            state = albumSongsGridState,
                            columns = GridCells.Fixed(columnsCount),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 98.dp),
                            horizontalArrangement = Arrangement.spacedBy(hSpacing),
                            verticalArrangement = Arrangement.spacedBy(vSpacing),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(
                                items = albumSongs,
                                key = { index, song -> "${song.id}_$index" }
                            ) { index, song ->
                                SongItem(
                                    song = song,
                                    isPlaying = playbackState.isPlaying,
                                    isCurrent = playbackState.currentSong?.id == song.id,
                                    viewMode = currentViewMode,
                                    coverVersion = coverVer,
                                    onClick = { handleSongItemClick(albumSongs, index) },
                                    onFavoriteClick = { viewModel.cycleSongAttitude(song) },
                                    onLongClick = { activeSongForLongClickMenu = song }
                                )
                            }
                        }
                    }
                } else if (openedArtist != null) {
                    // ========== 如果处于艺术家下钻内部，展示该艺术家的歌曲 ==========
                    val currentArtist = openedArtist!!
                    val artistSongs = remember(currentArtist, filteredSongs) {
                        filteredSongs.filter { it.artist == currentArtist.name }
                    }
                    if (artistSongs.isEmpty()) {
                        EmptyStateView(
                            title = stringResource(R.string.empty_artist_title),
                            subtitle = stringResource(R.string.empty_artist_desc)
                        )
                    } else {
                        LazyVerticalGrid(
                            state = artistSongsGridState,
                            columns = GridCells.Fixed(columnsCount),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 98.dp),
                            horizontalArrangement = Arrangement.spacedBy(hSpacing),
                            verticalArrangement = Arrangement.spacedBy(vSpacing),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(
                                items = artistSongs,
                                key = { index, song -> "${song.id}_$index" }
                            ) { index, song ->
                                SongItem(
                                    song = song,
                                    isPlaying = playbackState.isPlaying,
                                    isCurrent = playbackState.currentSong?.id == song.id,
                                    viewMode = currentViewMode,
                                    coverVersion = coverVer,
                                    onClick = { handleSongItemClick(artistSongs, index) },
                                    onFavoriteClick = { viewModel.cycleSongAttitude(song) },
                                    onLongClick = { activeSongForLongClickMenu = song }
                                )
                            }
                        }
                    }
                } else {
                    when (libraryState.currentTab) {
                        LibraryTab.SONGS -> {
                            // 1. 全部歌曲列表 (全 6 档 Pinch 手势与物理位移形变动效)
                            if (filteredSongs.isEmpty()) {
                                EmptyStateView(
                                    title = if (isScanning) stringResource(R.string.scanning_library_title) else stringResource(R.string.empty_songs_title),
                                    subtitle = stringResource(R.string.empty_songs_desc)
                                )
                            } else {
                                LazyVerticalGrid(
                                    state = songsGridState,
                                    columns = GridCells.Fixed(columnsCount),
                                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 98.dp),
                                    horizontalArrangement = Arrangement.spacedBy(hSpacing),
                                    verticalArrangement = Arrangement.spacedBy(vSpacing),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(
                                        items = filteredSongs,
                                        key = { index, song -> "${song.id}_$index" }
                                    ) { index, song ->
                                        SongItem(
                                            song = song,
                                            isPlaying = playbackState.isPlaying,
                                            isCurrent = playbackState.currentSong?.id == song.id,
                                            viewMode = currentViewMode,
                                            coverVersion = coverVer,
                                            onClick = { handleSongItemClick(filteredSongs, index) },
                                            onFavoriteClick = { viewModel.cycleSongAttitude(song) },
                                            onLongClick = { activeSongForLongClickMenu = song }
                                        )
                                    }
                                }
                            }
                        }

                        LibraryTab.FOLDERS -> {
                            // 2. 文件夹目录树 (支持点击进入下钻歌曲列表，支持 Pinch 手势)
                            LazyColumn(
                                state = foldersListState,
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 86.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(folders, key = { index, folder -> "${folder.folderPath}_$index" }) { _, folder ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(OrbitTheme.colors.surfaceCard)
                                            .clickable {
                                                // 点击进入该文件夹展示歌曲
                                                openedFolderPath = folder.folderPath
                                            }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null, tint = OrbitTheme.colors.tertiary, modifier = Modifier.size(28.dp))
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(folder.folderName, fontWeight = FontWeight.Bold, color = OrbitTheme.colors.textPrimary, fontSize = 14.sp)
                                            Text(folder.folderPath, color = OrbitTheme.colors.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("${folder.songCount} tracks", fontSize = 12.sp, color = OrbitTheme.colors.primary, fontWeight = FontWeight.SemiBold)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = OrbitTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }

                        LibraryTab.ALBUMS -> {
                            // 3. 专辑库 (支持全 6 档自适应 Pinch 手势与点击下钻)
                            if (albums.isEmpty()) {
                                EmptyStateView(
                                    title = if (isScanning) stringResource(R.string.scanning_library_title) else stringResource(R.string.no_albums_found),
                                    subtitle = stringResource(R.string.empty_albums_desc)
                                )
                            } else {
                                LazyVerticalGrid(
                                    state = albumsGridState,
                                    columns = GridCells.Fixed(columnsCount),
                                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 98.dp),
                                    horizontalArrangement = Arrangement.spacedBy(hSpacing),
                                    verticalArrangement = Arrangement.spacedBy(vSpacing),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(albums, key = { index, album -> "${album.id}_${album.title}_$index" }) { _, album ->
                                        val isAlbumPlaying = playbackState.currentSong?.album == album.title

                                        AlbumItem(
                                            album = album,
                                            viewMode = currentViewMode,
                                            isCurrent = isAlbumPlaying,
                                            coverVersion = coverVer,
                                            onClick = {
                                                openedAlbum = album
                                            }
                                        )
                                    }
                                }
                            }
                        }

                    LibraryTab.ARTISTS -> {
                        // 4. 艺术家列表 (支持 Pinch 手势与点击下钻展示全部歌曲)
                        if (artists.isEmpty()) {
                            EmptyStateView(
                                title = if (isScanning) stringResource(R.string.scanning_library_title) else stringResource(R.string.no_artists_found),
                                subtitle = stringResource(R.string.empty_artists_desc)
                            )
                        } else {
                            LazyColumn(
                                state = artistsListState,
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 86.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(pinchGestureModifier)
                            ) {
                                itemsIndexed(artists, key = { index, artist -> "${artist.name}_$index" }) { _, artist ->
                                    val isArtistPlaying = playbackState.currentSong?.artist == artist.name
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isArtistPlaying) OrbitTheme.colors.primary.copy(alpha = 0.12f) else OrbitTheme.colors.surfaceCard)
                                            .clickable {
                                                openedArtist = artist
                                            }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = OrbitTheme.colors.secondary, modifier = Modifier.size(28.dp))
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = artist.name,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isArtistPlaying) OrbitTheme.colors.primary else OrbitTheme.colors.textPrimary,
                                                fontSize = 14.sp
                                            )
                                            Text("${artist.albumCount} albums • ${artist.songCount} tracks", color = OrbitTheme.colors.textSecondary, fontSize = 11.sp)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("${artist.songCount} tracks", fontSize = 12.sp, color = OrbitTheme.colors.primary, fontWeight = FontWeight.SemiBold)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = OrbitTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    LibraryTab.PLAYLISTS -> {
                        // 5. 播放列表 (支持下钻详情、创建、重命名、删除及歌曲管理)
                        if (openedPlaylist != null) {
                            val currentPlaylist = openedPlaylist!!
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                            ) {
                                // 顶部导航栏
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { openedPlaylist = null },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = OrbitTheme.colors.textPrimary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = currentPlaylist.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = OrbitTheme.colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = stringResource(R.string.tracks_count, playlistSongs.size),
                                            fontSize = 12.sp,
                                            color = OrbitTheme.colors.textSecondary
                                        )
                                    }

                                    // 播放全部按钮
                                    if (playlistSongs.isNotEmpty()) {
                                        FilledTonalButton(
                                            onClick = { viewModel.playSong(playlistSongs, 0) },
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = OrbitTheme.colors.primary.copy(alpha = 0.15f),
                                                contentColor = OrbitTheme.colors.primary
                                            ),
                                            shape = RoundedCornerShape(20.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.btn_play_all),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }

                                // 快捷操作栏：添加歌曲按钮
                                // 快捷操作栏：添加歌曲按钮 (红心歌单不展示添加歌曲按钮)
                                if (currentPlaylist.id != -999L) {
                                    OutlinedButton(
                                        onClick = { showAddSongsToPlaylistDialog = true },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OrbitTheme.colors.primary),
                                        border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.add_songs), fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // 歌曲列表或空状态
                                if (playlistSongs.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (currentPlaylist.id == -999L) Icons.Default.Favorite else Icons.AutoMirrored.Filled.PlaylistPlay,
                                                contentDescription = null,
                                                tint = if (currentPlaylist.id == -999L) Color(0xFFFF3366).copy(alpha = 0.4f) else OrbitTheme.colors.textSecondary.copy(alpha = 0.35f),
                                                modifier = Modifier.size(64.dp)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = if (currentPlaylist.id == -999L) stringResource(R.string.favorites_empty_hint) else stringResource(R.string.empty_playlist_hint),
                                                fontSize = 13.sp,
                                                color = OrbitTheme.colors.textSecondary,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        state = playlistSongsListState,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(bottom = 98.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        itemsIndexed(
                                            items = playlistSongs,
                                            key = { index, song -> "${song.id}_$index" }
                                        ) { index, song ->
                                            SongItem(
                                                song = song,
                                                isPlaying = playbackState.isPlaying,
                                                isCurrent = playbackState.currentSong?.id == song.id,
                                                viewMode = libraryState.viewMode,
                                                coverVersion = coverVer,
                                                onClick = { handleSongItemClick(playlistSongs, index) },
                                                onFavoriteClick = { viewModel.cycleSongAttitude(song) },
                                                onLongClick = { activeSongForLongClickMenu = song },
                                                trailingContent = {
                                                    IconButton(
                                                        onClick = {
                                                            if (currentPlaylist.id == -999L) {
                                                                viewModel.toggleFavorite(song)
                                                            } else {
                                                                viewModel.removeSongFromPlaylist(currentPlaylist.id, song.id)
                                                                playlistSongs = playlistSongs.filter { it.id != song.id }
                                                            }
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (currentPlaylist.id == -999L) Icons.Default.Favorite else Icons.Default.Close,
                                                            contentDescription = stringResource(R.string.remove_from_playlist),
                                                            tint = if (currentPlaylist.id == -999L) Color(0xFFFF3366) else OrbitTheme.colors.textSecondary.copy(alpha = 0.6f),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                                    .then(pinchGestureModifier)
                            ) {
                                Button(
                                    onClick = {
                                        newPlaylistName = ""
                                        showNewPlaylistDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        tint = if (OrbitTheme.colors.isDark) DarkBackground else Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.create_new_playlist),
                                        color = if (OrbitTheme.colors.isDark) DarkBackground else Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                LazyColumn(
                                    state = playlistsListState,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 98.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // 置顶显示「我喜欢的音乐」专属卡片
                                    item {
                                        val favTitle = stringResource(R.string.favorite_songs)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(OrbitTheme.colors.surfaceCard)
                                                .clickable {
                                                    openedPlaylist = Playlist(
                                                        id = -999L,
                                                        name = favTitle,
                                                        songCount = favoriteSongs.size,
                                                        createdAt = 0L
                                                    )
                                                }
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFFFF3366).copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Favorite,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFF3366),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = favTitle,
                                                    fontWeight = FontWeight.Bold,
                                                    color = OrbitTheme.colors.textPrimary,
                                                    fontSize = 15.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = stringResource(R.string.tracks_count, favoriteSongs.size),
                                                    color = OrbitTheme.colors.textSecondary,
                                                    fontSize = 12.sp
                                                )
                                            }

                                            // 快捷播放全部喜欢的音乐
                                            if (favoriteSongs.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { viewModel.playSong(favoriteSongs, 0) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = stringResource(R.string.btn_play_all),
                                                        tint = Color(0xFFFF3366),
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.ChevronRight,
                                                    contentDescription = null,
                                                    tint = OrbitTheme.colors.textSecondary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }

                                    items(playlists, key = { it.id }) { playlist ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(OrbitTheme.colors.surfaceCard)
                                                .clickable { openedPlaylist = playlist }
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                                contentDescription = null,
                                                tint = OrbitTheme.colors.primary,
                                                modifier = Modifier.size(30.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = playlist.name,
                                                    fontWeight = FontWeight.Bold,
                                                    color = OrbitTheme.colors.textPrimary,
                                                    fontSize = 15.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = stringResource(R.string.tracks_count, playlist.songCount),
                                                    color = OrbitTheme.colors.textSecondary,
                                                    fontSize = 12.sp
                                                )
                                            }

                                            // 快捷播放该列表按钮
                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        val songs = viewModel.getSongsInPlaylist(playlist.id)
                                                        if (songs.isNotEmpty()) {
                                                            viewModel.playSong(songs, 0)
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayCircle,
                                                    contentDescription = "Play",
                                                    tint = OrbitTheme.colors.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }

                                            // 更多操作菜单 (重命名 / 删除)
                                            var showMenu by remember { mutableStateOf(false) }
                                            Box {
                                                IconButton(
                                                    onClick = { showMenu = true },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.MoreVert,
                                                        contentDescription = "More",
                                                        tint = OrbitTheme.colors.textSecondary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = showMenu,
                                                    onDismissRequest = { showMenu = false },
                                                    modifier = Modifier.background(OrbitTheme.colors.surfaceCard)
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.rename_playlist), color = OrbitTheme.colors.textPrimary) },
                                                        onClick = {
                                                            showMenu = false
                                                            renamingPlaylist = playlist
                                                            renamePlaylistName = playlist.name
                                                        },
                                                        leadingIcon = {
                                                            Icon(Icons.Default.Edit, contentDescription = null, tint = OrbitTheme.colors.textSecondary)
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.delete_playlist), color = Color(0xFFEF4444)) },
                                                        onClick = {
                                                            showMenu = false
                                                            deletingPlaylist = playlist
                                                        },
                                                        leadingIcon = {
                                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFEF4444))
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 平板侧边栏展开/折叠状态管理
    var isSideNavExpanded by rememberSaveable { mutableStateOf(true) }
    val sideNavWidth by animateDpAsState(
        targetValue = if (isSideNavExpanded) 230.dp else 72.dp,
        animationSpec = tween(durationMillis = 220),
        label = "SideNavWidth"
    )

    if (useTabletLayout) {
        // ── 平板专属横屏双栏媒体库 UI ──
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(OrbitTheme.colors.background)
        ) {
            TabletSideNavRail(
                currentTab = libraryState.currentTab,
                onTabSelected = { tab ->
                    openedFolderPath = null
                    openedAlbum = null
                    openedArtist = null
                    viewModel.setTab(tab)
                },
                songCount = filteredSongs.size,
                folderCount = folders.size,
                albumCount = albums.size,
                artistCount = artists.size,
                playlistCount = playlists.size,
                isScanning = isScanning,
                isExpanded = isSideNavExpanded,
                onToggleExpand = { isSideNavExpanded = !isSideNavExpanded },
                onScanMedia = { viewModel.scanMedia() },
                onOpenSettings = onOpenSettings,
                modifier = Modifier
                    .width(sideNavWidth)
                    .fillMaxHeight()
            )

            // 纵向分割细线
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(OrbitTheme.colors.surfaceCard.copy(alpha = 0.6f))
            )

            // 右侧主视图区
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 仅在下钻（文件夹/专辑/艺术家）时展示返回面包屑栏；未下钻时不展示任何TopBar且不保留空间
                    val isDrillDown = openedFolderPath != null || openedAlbum != null || openedArtist != null
                    if (isDrillDown) {
                        TabletDrillDownTopBar(
                            openedFolderPath = openedFolderPath,
                            openedAlbum = openedAlbum,
                            openedArtist = openedArtist,
                            onBack = {
                                openedFolderPath = null
                                openedAlbum = null
                                openedArtist = null
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        LibraryMainContent(bottomPadding = 98.dp)

                        // 🎯 平板专属右下角悬浮控制按钮组（搜索输入框在搜索按钮左侧悬浮展开、双栏/Grid切换、定位当前播放）
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 24.dp, bottom = 106.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 1. 浮动搜索行：搜索框在搜索图标旁边(左侧)悬浮展开
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                AnimatedVisibility(
                                    visible = libraryState.isSearching,
                                    enter = fadeIn(tween(180)) + expandHorizontally(tween(220), expandFrom = Alignment.End),
                                    exit = fadeOut(tween(140)) + shrinkHorizontally(tween(180), shrinkTowards = Alignment.End)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(23.dp),
                                        color = OrbitTheme.colors.surfaceCard.copy(alpha = 0.96f),
                                        border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.55f)),
                                        shadowElevation = 8.dp,
                                        modifier = Modifier
                                            .padding(end = 10.dp)
                                            .width(280.dp)
                                            .height(46.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 14.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = null,
                                                tint = OrbitTheme.colors.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            androidx.compose.foundation.text.BasicTextField(
                                                value = libraryState.searchQuery,
                                                onValueChange = { viewModel.setSearchQuery(it) },
                                                singleLine = true,
                                                textStyle = androidx.compose.ui.text.TextStyle(
                                                    color = OrbitTheme.colors.textPrimary,
                                                    fontSize = 14.sp
                                                ),
                                                decorationBox = { innerTextField ->
                                                    if (libraryState.searchQuery.isEmpty()) {
                                                        Text(
                                                            text = stringResource(R.string.search_hint),
                                                            fontSize = 13.sp,
                                                            color = OrbitTheme.colors.textSecondary
                                                        )
                                                    }
                                                    innerTextField()
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (libraryState.searchQuery.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { viewModel.setSearchQuery("") },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Clear",
                                                        tint = OrbitTheme.colors.textSecondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 浮动搜索按钮
                                Surface(
                                    onClick = { viewModel.toggleSearch() },
                                    shape = CircleShape,
                                    color = if (libraryState.isSearching) OrbitTheme.colors.primary else OrbitTheme.colors.surfaceCard.copy(alpha = 0.94f),
                                    border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.5f)),
                                    shadowElevation = 8.dp,
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (libraryState.isSearching) Icons.Default.Close else Icons.Default.Search,
                                            contentDescription = "Search",
                                            tint = if (libraryState.isSearching) (if (OrbitTheme.colors.isDark) DarkBackground else Color.White) else OrbitTheme.colors.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }

                            // 2. 浮动视图切换按钮（在「双栏列表」和「高密度Grid」之间切换）
                            val isCurrentGrid = libraryState.viewMode == LibraryViewMode.GRID_2_COL ||
                                    libraryState.viewMode == LibraryViewMode.GRID_3_COL ||
                                    libraryState.viewMode == LibraryViewMode.GRID_4_COL

                            Surface(
                                onClick = {
                                    if (isCurrentGrid) {
                                        viewModel.setViewMode(LibraryViewMode.LIST_SMALL_ART)
                                    } else {
                                        viewModel.setViewMode(LibraryViewMode.GRID_3_COL)
                                    }
                                },
                                shape = CircleShape,
                                color = OrbitTheme.colors.surfaceCard.copy(alpha = 0.94f),
                                border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.5f)),
                                shadowElevation = 8.dp,
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    val icon = if (isCurrentGrid) {
                                        Icons.AutoMirrored.Filled.ViewList
                                    } else {
                                        Icons.Default.GridView
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = if (isCurrentGrid) "Switch to 2-Column List" else "Switch to Grid",
                                        tint = OrbitTheme.colors.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // 3. 定位当前播放歌曲按钮（位于最下方）
                            LocatePlayingSongFab()
                        }
                    }
                }
            }
        }
    } else {
        // ── 手机端 Scaffold 布局 ──
        Scaffold(
            topBar = mobileTopBar,
            containerColor = OrbitTheme.colors.background
        ) { innerPadding ->
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LibraryMainContent(bottomPadding = 98.dp)

                LocatePlayingSongFab(
                    fabModifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 106.dp)
                )
            }
        }
    }

    // 1. 新建播放列表弹窗
    if (showNewPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showNewPlaylistDialog = false },
            title = { Text(stringResource(R.string.create_new_playlist), color = OrbitTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text(stringResource(R.string.playlist_name_hint), color = OrbitTheme.colors.textSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName.trim())
                            newPlaylistName = ""
                            showNewPlaylistDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary)
                ) {
                    Text(stringResource(R.string.btn_ok), color = if (OrbitTheme.colors.isDark) DarkBackground else Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewPlaylistDialog = false }) {
                    Text(stringResource(R.string.btn_cancel), color = OrbitTheme.colors.textSecondary)
                }
            },
            containerColor = OrbitTheme.colors.surfaceDialog
        )
    }

    // 2. 重命名播放列表弹窗
    renamingPlaylist?.let { target ->
        AlertDialog(
            onDismissRequest = { renamingPlaylist = null },
            title = { Text(stringResource(R.string.rename_playlist), color = OrbitTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renamePlaylistName,
                    onValueChange = { renamePlaylistName = it },
                    label = { Text(stringResource(R.string.playlist_name_hint), color = OrbitTheme.colors.textSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renamePlaylistName.isNotBlank()) {
                            val newName = renamePlaylistName.trim()
                            viewModel.renamePlaylist(target.id, newName)
                            if (openedPlaylist?.id == target.id) {
                                openedPlaylist = openedPlaylist?.copy(name = newName)
                            }
                            renamingPlaylist = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary)
                ) {
                    Text(stringResource(R.string.btn_ok), color = if (OrbitTheme.colors.isDark) DarkBackground else Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingPlaylist = null }) {
                    Text(stringResource(R.string.btn_cancel), color = OrbitTheme.colors.textSecondary)
                }
            },
            containerColor = OrbitTheme.colors.surfaceDialog
        )
    }

    // 3. 删除播放列表确认弹窗
    deletingPlaylist?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingPlaylist = null },
            title = { Text(stringResource(R.string.delete_playlist), color = OrbitTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = stringResource(R.string.delete_playlist_confirm, target.name),
                    color = OrbitTheme.colors.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePlaylist(target.id)
                        if (openedPlaylist?.id == target.id) {
                            openedPlaylist = null
                        }
                        deletingPlaylist = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text(stringResource(R.string.btn_ok), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingPlaylist = null }) {
                    Text(stringResource(R.string.btn_cancel), color = OrbitTheme.colors.textSecondary)
                }
            },
            containerColor = OrbitTheme.colors.surfaceDialog
        )
    }

    // 4. 播放列表下钻中添加歌曲弹窗
    if (showAddSongsToPlaylistDialog && openedPlaylist != null) {
        val targetPlaylist = openedPlaylist!!
        val allSongs by viewModel.allSongs.collectAsState()
        var songSearchQuery by remember { mutableStateOf("") }
        val selectableSongs = remember(allSongs, songSearchQuery) {
            if (songSearchQuery.isBlank()) allSongs else {
                allSongs.filter {
                    it.title.contains(songSearchQuery, ignoreCase = true) ||
                            it.artist.contains(songSearchQuery, ignoreCase = true)
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showAddSongsToPlaylistDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.add_songs),
                    color = OrbitTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                    OutlinedTextField(
                        value = songSearchQuery,
                        onValueChange = { songSearchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_hint), color = OrbitTheme.colors.textSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = OrbitTheme.colors.textSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(selectableSongs, key = { it.id }) { s ->
                            val alreadyInPlaylist = playlistSongs.any { it.id == s.id }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (alreadyInPlaylist) OrbitTheme.colors.primary.copy(alpha = 0.08f) else Color.Transparent)
                                    .clickable {
                                        if (alreadyInPlaylist) {
                                            viewModel.removeSongFromPlaylist(targetPlaylist.id, s.id)
                                            playlistSongs = playlistSongs.filter { it.id != s.id }
                                        } else {
                                            viewModel.addSongToPlaylist(targetPlaylist.id, s.id)
                                            playlistSongs = playlistSongs + s
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = s.title,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = if (alreadyInPlaylist) OrbitTheme.colors.primary else OrbitTheme.colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = s.artist,
                                        fontSize = 11.sp,
                                        color = OrbitTheme.colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = if (alreadyInPlaylist) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                                    contentDescription = null,
                                    tint = if (alreadyInPlaylist) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAddSongsToPlaylistDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary)
                ) {
                    Text(stringResource(R.string.btn_ok), color = if (OrbitTheme.colors.isDark) DarkBackground else Color.White)
                }
            },
            containerColor = OrbitTheme.colors.surfaceDialog
        )
    }

    // 5. 歌曲列表中单曲添加到播放列表弹窗
    songToAddToPlaylist?.let { targetSong ->
        AlertDialog(
            onDismissRequest = { songToAddToPlaylist = null },
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
                        text = targetSong.title,
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
                                showNewPlaylistDialog = true
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
                                            viewModel.addSongToPlaylist(pl.id, targetSong.id)
                                            songToAddToPlaylist = null
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
                TextButton(onClick = { songToAddToPlaylist = null }) {
                    Text(stringResource(R.string.btn_cancel), color = OrbitTheme.colors.textSecondary)
                }
            },
            containerColor = OrbitTheme.colors.surfaceDialog
        )
    }

    // 6. 长按歌曲弹出的操作面板 (底部抽屉)
    activeSongForLongClickMenu?.let { longClickedSong ->
        ModalBottomSheet(
            onDismissRequest = { activeSongForLongClickMenu = null },
            containerColor = OrbitTheme.colors.surfaceDialog,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                // 顶部歌曲信息行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(OrbitTheme.colors.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        if (longClickedSong.albumArtUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(longClickedSong.albumArtUri)
                                    .memoryCacheKey("${longClickedSong.albumArtUri}_$coverVer")
                                    .diskCacheKey("${longClickedSong.albumArtUri}_$coverVer")
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = longClickedSong.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = OrbitTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${longClickedSong.artist} • ${longClickedSong.album}",
                            fontSize = 12.sp,
                            color = OrbitTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(
                    color = OrbitTheme.colors.textSecondary.copy(alpha = 0.15f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // 操作项 0：收藏 / 取消喜欢
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val target = longClickedSong
                            activeSongForLongClickMenu = null
                            viewModel.toggleFavorite(target)
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isFav = longClickedSong.isFavorite
                    Icon(
                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFav) Color(0xFFFF3366) else OrbitTheme.colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = stringResource(if (isFav) R.string.remove_from_favorites else R.string.add_to_favorites),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = OrbitTheme.colors.textPrimary
                    )
                }

                // 操作项 1：添加到播放列表
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val target = longClickedSong
                            activeSongForLongClickMenu = null
                            songToAddToPlaylist = target
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

                // 操作项 2：立即播放该歌曲
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val target = longClickedSong
                            activeSongForLongClickMenu = null
                            val idx = filteredSongs.indexOfFirst { it.id == target.id }
                            if (idx >= 0) {
                                handleSongItemClick(filteredSongs, idx)
                            }
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = OrbitTheme.colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.btn_play_all),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = OrbitTheme.colors.textPrimary
                    )
                }

                // 操作项 3：选择封面 (弹窗内提供预览、下载/重新下载以及保存)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val target = longClickedSong
                            activeSongForLongClickMenu = null
                            candidateSong = target
                            val cached = com.antigravity.equalizer.data.cover.MusicBrainzCoverService.getCachedCandidates(target.id)
                            if (cached != null) {
                                candidateArtistName = cached.first
                                candidateCovers = cached.second
                            } else {
                                candidateArtistName = target.artist
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
            }
        }
    }

    // 候选专辑封面选择对话框 (展示已有封面或在线检索封面，支持下载/重新下载并保存)
    if (showSelectAlbumCoverDialog && candidateSong != null) {
        val songToApply = candidateSong!!
        val hasDownloadedCover = remember(songToApply.id, coverVer) {
            com.antigravity.equalizer.utils.CoverHelper.hasDownloadedCover(context, songToApply.id)
        }
        val isDownloadingCover = searchingSong?.id == songToApply.id

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
                searchingSong = songToApply
                scope.launch {
                    try {
                        com.antigravity.equalizer.utils.CoverHelper.resetOnlineSearchStatus(songToApply.id)
                        val result = com.antigravity.equalizer.data.cover.MusicBrainzCoverService.checkAndFetchLargeCover(
                            context = context,
                            song = songToApply,
                            force = true
                        )
                        withContext(Dispatchers.Main) {
                            searchingSong = null
                            when (result) {
                                is com.antigravity.equalizer.data.cover.MusicBrainzCoverService.MatchResult.ArtistAlbumsFound -> {
                                    candidateCovers = result.candidates
                                    candidateArtistName = result.artist
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.cover_fetch_completed_hint, result.candidates.size),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                is com.antigravity.equalizer.data.cover.MusicBrainzCoverService.MatchResult.UpdatedLarge -> {
                                    val sizeStr = "${result.netWidth} × ${result.netHeight}"
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.cover_update_success, sizeStr),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                is com.antigravity.equalizer.data.cover.MusicBrainzCoverService.MatchResult.NotFound -> {
                                    Toast.makeText(context, R.string.cover_not_found, Toast.LENGTH_SHORT).show()
                                }
                                else -> {
                                    Toast.makeText(context, R.string.cover_search_failed, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            searchingSong = null
                            Toast.makeText(context, R.string.cover_search_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onConfirmSelection = { selectedCandidate ->
                isApplyingCover = true
                coroutineScope.launch {
                    val success = com.antigravity.equalizer.data.cover.MusicBrainzCoverService.applyCandidateCover(
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

}

@Composable
private fun EmptyStateView(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(subtitle, fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
private fun getTabTitle(tab: LibraryTab): String {
    return when (tab) {
        LibraryTab.SONGS -> stringResource(R.string.tab_songs)
        LibraryTab.FOLDERS -> stringResource(R.string.tab_folders)
        LibraryTab.ALBUMS -> stringResource(R.string.tab_albums)
        LibraryTab.ARTISTS -> stringResource(R.string.tab_artists)
        LibraryTab.PLAYLISTS -> stringResource(R.string.tab_playlists)
    }
}

/**
 * 专为平板大屏打造的侧边导航栏 (Tablet Side Navigation Rail)
 * 支持折叠展开切换：展开时显示完整品牌、文本与数量Badge；折叠时精简为居中图标与状态圆点
 */
@Composable
private fun TabletSideNavRail(
    currentTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    songCount: Int,
    folderCount: Int,
    albumCount: Int,
    artistCount: Int,
    playlistCount: Int,
    isScanning: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onScanMedia: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = OrbitTheme.colors.surfaceCard.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isExpanded) 14.dp else 8.dp, vertical = 14.dp),
            horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally
        ) {
            // 顶部 Logo 与折叠/展开按钮
            if (isExpanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = OrbitTheme.colors.primary.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.35f)),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = OrbitTheme.colors.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "OrBit Player",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = OrbitTheme.colors.textPrimary
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = OrbitTheme.colors.primary.copy(alpha = 0.2f),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = "TABLET HD",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = OrbitTheme.colors.primary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    // 折叠按钮
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuOpen,
                            contentDescription = "Collapse sidebar",
                            tint = OrbitTheme.colors.textSecondary
                        )
                    }
                }
            } else {
                // 折叠状态下顶部只居中放置展开按钮
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Expand sidebar",
                        tint = OrbitTheme.colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 中间 Tab 项导航栏
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LibraryTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    val (icon, count) = when (tab) {
                        LibraryTab.SONGS -> Icons.AutoMirrored.Filled.QueueMusic to songCount
                        LibraryTab.FOLDERS -> Icons.Default.Folder to folderCount
                        LibraryTab.ALBUMS -> Icons.Default.Album to albumCount
                        LibraryTab.ARTISTS -> Icons.Default.Person to artistCount
                        LibraryTab.PLAYLISTS -> Icons.AutoMirrored.Filled.PlaylistPlay to playlistCount
                    }

                    if (isExpanded) {
                        Surface(
                            onClick = { onTabSelected(tab) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) {
                                OrbitTheme.colors.primary.copy(alpha = 0.18f)
                            } else {
                                Color.Transparent
                            },
                            border = if (isSelected) {
                                BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.45f))
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = getTabTitle(tab),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                if (count > 0) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) OrbitTheme.colors.primary.copy(alpha = 0.25f) else OrbitTheme.colors.surfaceCard,
                                        modifier = Modifier.padding(start = 4.dp)
                                    ) {
                                        Text(
                                            text = "$count",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // 折叠状态：紧凑居中方块，带微角标/点提示
                        Surface(
                            onClick = { onTabSelected(tab) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) {
                                OrbitTheme.colors.primary.copy(alpha = 0.18f)
                            } else {
                                Color.Transparent
                            },
                            border = if (isSelected) {
                                BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.45f))
                            } else null,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = getTabTitle(tab),
                                    tint = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                if (count > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 6.dp, end = 6.dp)
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 底部操作区 (扫描本地媒体与偏好设置)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isExpanded) {
                    // 展开状态：扫描媒体库
                    Surface(
                        onClick = onScanMedia,
                        shape = RoundedCornerShape(10.dp),
                        color = OrbitTheme.colors.surfaceCard,
                        border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Scan",
                                tint = if (isScanning) OrbitTheme.colors.tertiary else OrbitTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isScanning) stringResource(R.string.scanning) else stringResource(R.string.scan_media),
                                fontSize = 12.sp,
                                color = OrbitTheme.colors.textPrimary
                            )
                        }
                    }

                    // 展开状态：偏好设置
                    Surface(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(10.dp),
                        color = OrbitTheme.colors.surfaceCard,
                        border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.settings),
                                fontSize = 12.sp,
                                color = OrbitTheme.colors.textPrimary
                            )
                        }
                    }
                } else {
                    // 折叠状态：紧凑居中图标按钮
                    Surface(
                        onClick = onScanMedia,
                        shape = RoundedCornerShape(10.dp),
                        color = OrbitTheme.colors.surfaceCard,
                        border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.15f)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Scan",
                                tint = if (isScanning) OrbitTheme.colors.tertiary else OrbitTheme.colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(10.dp),
                        color = OrbitTheme.colors.surfaceCard,
                        border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.15f)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 平板右侧下钻顶栏 (Tablet Drill Down Top Bar)
 * 仅在下钻查看文件夹、专辑或艺术家详情时展示返回导航与面包屑
 * 未下钻时不占用任何空间，直接顶格展示媒体列表
 */
@Composable
private fun TabletDrillDownTopBar(
    openedFolderPath: String?,
    openedAlbum: AlbumItem?,
    openedArtist: ArtistItem?,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        color = OrbitTheme.colors.background,
        border = BorderStroke(width = 0.5.dp, color = OrbitTheme.colors.surfaceCard.copy(alpha = 0.6f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OrbitTheme.colors.textPrimary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                val title = when {
                    openedFolderPath != null -> openedFolderPath.substringAfterLast("/").ifEmpty { "Folder" }
                    openedAlbum != null -> openedAlbum.title
                    openedArtist != null -> openedArtist.name
                    else -> ""
                }
                val subtitle = when {
                    openedFolderPath != null -> openedFolderPath
                    openedAlbum != null -> "${openedAlbum.artist} • ${openedAlbum.songCount} tracks"
                    openedArtist != null -> "${openedArtist.albumCount} albums • ${openedArtist.songCount} tracks"
                    else -> ""
                }
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = OrbitTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = OrbitTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

