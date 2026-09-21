package com.orbit.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
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
import com.orbit.music.data.model.AlbumItem
import com.orbit.music.data.model.ArtistItem
import com.orbit.music.ui.components.AlbumCoverFlowLayout
import com.orbit.music.ui.components.AlbumItem
import com.orbit.music.ui.components.CoverFlowLayout
import com.orbit.music.ui.components.MiniPlayerBar
import com.orbit.music.ui.components.SelectAlbumCoverDialog
import com.orbit.music.ui.components.SongItem
import com.orbit.music.utils.FastToast
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.res.stringResource
import com.orbit.music.R
import com.orbit.music.data.model.Playlist
import com.orbit.music.data.model.Song
import com.orbit.music.ui.components.PowerampViewModeTransitionContainer
import com.orbit.music.ui.theme.*
import com.orbit.music.ui.utils.pinchToZoomViewMode
import com.orbit.music.ui.utils.rememberPinchTransitionState
import android.widget.Toast
import com.orbit.music.ui.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.LazyGridState

private const val FAVORITE_PLAYLIST_ID = -999L
private const val DISLIKED_PLAYLIST_ID = -998L

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
    val coverVer by com.orbit.music.utils.CoverHelper.coverVersion.collectAsState()
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            configuration.screenWidthDp > configuration.screenHeightDp
    val useTabletLayout = isTabletMode && (isLandscape || configuration.screenWidthDp >= 600)

    val libraryState by viewModel.libraryUiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    val allSongs by viewModel.allSongs.collectAsState()
    val filteredSongs by viewModel.filteredSongs.collectAsState()
    val folders by viewModel.filteredFolders.collectAsState()
    val albums by viewModel.filteredAlbums.collectAsState()
    val artists by viewModel.filteredArtists.collectAsState()
    val allPlaylists by viewModel.playlists.collectAsState()
    val playlists by viewModel.filteredPlaylists.collectAsState()

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

    // 多选批量操作状态
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedSongIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var batchDeleteLocalFileChecked by remember { mutableStateOf(false) }
    var showBatchAddToPlaylistDialog by remember { mutableStateOf(false) }

    // 歌曲添加对话框
    var showAddSongsToPlaylistDialog by remember { mutableStateOf(false) }
    var showClearDislikesDialog by remember { mutableStateOf(false) }
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    var activeSongForLongClickMenu by remember { mutableStateOf<Song?>(null) }
    var showSelectAlbumCoverDialog by remember { mutableStateOf(false) }
    var candidateCovers by remember { mutableStateOf<List<com.orbit.music.data.cover.MusicBrainzCoverService.AlbumCoverCandidate>>(emptyList()) }
    var candidateArtistName by remember { mutableStateOf("") }
    var candidateSong by remember { mutableStateOf<Song?>(null) }
    var isApplyingCover by remember { mutableStateOf(false) }
    var searchingSong by remember { mutableStateOf<Song?>(null) }
    var songToDelete by remember { mutableStateOf<Song?>(null) }
    var deleteLocalFileChecked by remember { mutableStateOf(false) }
    var songForDetailInfo by remember { mutableStateOf<Song?>(null) }

    // 文件夹下钻：当前展开的文件夹路径
    var openedFolderPath by remember { mutableStateOf<String?>(null) }
    // 专辑下钻：当前展开的专辑
    var openedAlbum by remember { mutableStateOf<AlbumItem?>(null) }
    // 艺术家下钻：当前展开的艺术家
    var openedArtist by remember { mutableStateOf<ArtistItem?>(null) }

    // 监听 ViewModel 中专辑下钻跳转（来自播放页或长按操作）
    LaunchedEffect(libraryState.selectedAlbum) {
        if (libraryState.selectedAlbum != null) {
            openedAlbum = libraryState.selectedAlbum
            openedFolderPath = null
            openedArtist = null
            openedPlaylist = null
        }
    }

    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val dislikedSongs by viewModel.dislikedSongs.collectAsState()

    // 监听 openedPlaylist 变化动态获取歌曲列表
    LaunchedEffect(openedPlaylist, playlists, favoriteSongs, dislikedSongs) {
        val p = openedPlaylist
        if (p != null) {
            if (p.id == FAVORITE_PLAYLIST_ID) {
                playlistSongs = favoriteSongs
            } else if (p.id == DISLIKED_PLAYLIST_ID) {
                playlistSongs = dislikedSongs
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

    // 0. 多选模式 -> 返回键退出多选模式
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedSongIds = emptySet()
    }

    val coroutineScope = rememberCoroutineScope()
    val songsGridHolder = rememberSynchronizedGridStateHolder()
    val folderSongsGridHolder = rememberSynchronizedGridStateHolder()
    val albumSongsGridHolder = rememberSynchronizedGridStateHolder()
    val artistSongsGridHolder = rememberSynchronizedGridStateHolder()
    val albumsGridHolder = rememberSynchronizedGridStateHolder()
    val playlistSongsGridHolder = rememberSynchronizedGridStateHolder()

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

    // Cover Flow 定位触发信号
    var coverFlowLocateTrigger by remember { mutableStateOf(0L) }

    // 计算当前活跃页面的 Key 与对应的独立视图模式
    val currentPageKey = when {
        openedFolderPath != null -> "detail_folder"
        openedAlbum != null -> "detail_album"
        openedArtist != null -> "detail_artist"
        openedPlaylist != null -> "detail_playlist"
        else -> libraryState.currentTab.pageKey
    }
    val currentActiveViewMode = libraryState.getViewModeFor(currentPageKey)

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
            coverFlowLocateTrigger = System.currentTimeMillis()
            coroutineScope.launch {
                songsGridHolder.animateScrollToItem(targetIndex, libraryState.getViewModeFor("tab_songs"))
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

    // 全 Tab 通用 Pinch 手势控制器与修饰符（绑定当前页面的独立视图模式）
    val pinchTransitionState = rememberPinchTransitionState()
    val pinchGestureModifier = Modifier.pinchToZoomViewMode(
        currentViewMode = currentActiveViewMode,
        pinchState = pinchTransitionState,
        onViewModeChange = { viewModel.setViewMode(it, currentPageKey) }
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

    val selectedSongsList = remember(selectedSongIds, allSongs) {
        allSongs.filter { it.id in selectedSongIds }
    }

    val multiSelectActionBar: @Composable (Modifier) -> Unit = { mod ->
        AnimatedVisibility(
            visible = isSelectionMode && selectedSongIds.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = mod
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = OrbitTheme.colors.surfaceCard.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.45f)),
                shadowElevation = 14.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .height(64.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. 播放
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (selectedSongsList.isNotEmpty()) {
                                    viewModel.playSong(selectedSongsList, 0)
                                    isSelectionMode = false
                                    selectedSongIds = emptySet()
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = OrbitTheme.colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.batch_play),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            color = OrbitTheme.colors.textPrimary
                        )
                    }

                    // 2. 下一首播放
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (selectedSongsList.isNotEmpty()) {
                                    viewModel.addSongsToQueueNext(selectedSongsList)
                                    FastToast.show(context, context.getString(R.string.batch_play_next))
                                    isSelectionMode = false
                                    selectedSongIds = emptySet()
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Play Next",
                            tint = OrbitTheme.colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.batch_play_next),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            color = OrbitTheme.colors.textPrimary
                        )
                    }

                    // 3. 添加到歌单
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                showBatchAddToPlaylistDialog = true
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = "Add to Playlist",
                            tint = OrbitTheme.colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.batch_add_to_playlist),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            color = OrbitTheme.colors.textPrimary
                        )
                    }

                    // 4. 批量喜欢
                    val allAreFav = selectedSongsList.isNotEmpty() && selectedSongsList.all { it.isFavorite }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                viewModel.setFavoriteBatch(selectedSongsList, !allAreFav)
                                isSelectionMode = false
                                selectedSongIds = emptySet()
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (allAreFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (allAreFav) Color(0xFFFF3366) else OrbitTheme.colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(if (allAreFav) R.string.batch_unfavorite else R.string.batch_favorite),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            color = OrbitTheme.colors.textPrimary
                        )
                    }

                    // 5. 批量删除
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                batchDeleteLocalFileChecked = false
                                showBatchDeleteDialog = true
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = Color(0xFFFF4D4F),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.batch_delete),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            color = Color(0xFFFF4D4F)
                        )
                    }
                }
            }
        }
    }

    // 手机端顶部标题栏与 Tab / 搜索栏（现代化紧凑设计，精简高度与图标排版）
    val mobileTopBar: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .background(OrbitTheme.colors.background)
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            // 1. 顶栏主操作行 (紧凑 48dp 高度)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 导航图标 / 返回 / 退出多选
                if (isSelectionMode) {
                    IconButton(
                        onClick = {
                            isSelectionMode = false
                            selectedSongIds = emptySet()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit Selection",
                            tint = OrbitTheme.colors.textPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.selected_count, selectedSongIds.size),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrbitTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    val currentVisibleSongs: List<Song> = when {
                        openedFolderPath != null -> allSongs.filter { it.folderPath == openedFolderPath }
                        openedAlbum != null -> {
                            val t = openedAlbum!!.title.trim()
                            allSongs.filter { it.album.trim().equals(t, ignoreCase = true) || (t == "未知专辑" && it.album.isBlank()) }
                        }
                        openedArtist != null -> {
                            val n = openedArtist!!.name.trim()
                            allSongs.filter { it.artist.contains(n, ignoreCase = true) }
                        }
                        openedPlaylist != null -> playlistSongs
                        else -> filteredSongs
                    }
                    val isAllSelected = currentVisibleSongs.isNotEmpty() && currentVisibleSongs.all { it.id in selectedSongIds }
                    TextButton(
                        onClick = {
                            selectedSongIds = if (isAllSelected) emptySet() else currentVisibleSongs.map { it.id }.toSet()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = stringResource(if (isAllSelected) R.string.deselect_all else R.string.select_all),
                            color = OrbitTheme.colors.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                } else if (openedFolderPath != null || openedAlbum != null || openedArtist != null || openedPlaylist != null) {
                    // 下钻模式：返回键 + 标题与副标题
                    IconButton(
                        onClick = {
                            openedFolderPath = null
                            openedAlbum = null
                            openedArtist = null
                            openedPlaylist = null
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = OrbitTheme.colors.textPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val (title, sub) = when {
                            openedFolderPath != null -> {
                                val fn = openedFolderPath!!.substringAfterLast("/").ifEmpty { "Folder" }
                                fn to openedFolderPath!!
                            }
                            openedAlbum != null -> {
                                openedAlbum!!.title to "${openedAlbum!!.artist} • ${openedAlbum!!.songCount} tracks"
                            }
                            openedArtist != null -> {
                                openedArtist!!.name to "${openedArtist!!.albumCount} albums • ${openedArtist!!.songCount} tracks"
                            }
                            else -> {
                                openedPlaylist!!.name to stringResource(R.string.tracks_count, playlistSongs.size)
                            }
                        }
                        Text(
                            text = title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrbitTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = sub,
                            fontSize = 11.sp,
                            color = OrbitTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 下钻模式右侧操作图标群 (紧凑 32dp 圆形点击区域)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. 搜索开关
                        IconButton(
                            onClick = { viewModel.toggleSearch() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (libraryState.isSearching) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (libraryState.isSearching) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 2. 多选模式开关
                        IconButton(
                            onClick = { isSelectionMode = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = stringResource(R.string.menu_multi_select),
                                tint = OrbitTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 3. 视图模式切换
                        IconButton(
                            onClick = { viewModel.cycleViewMode(currentPageKey) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            val icon = when (currentActiveViewMode) {
                                LibraryViewMode.LIST_NO_ART -> Icons.AutoMirrored.Filled.FormatListBulleted
                                LibraryViewMode.LIST_SMALL_ART -> Icons.AutoMirrored.Filled.ViewList
                                LibraryViewMode.LIST_LARGE_ART -> Icons.Default.ViewAgenda
                                LibraryViewMode.GRID_2_COL -> Icons.Default.GridView
                                LibraryViewMode.GRID_3_COL -> Icons.Default.GridOn
                                LibraryViewMode.GRID_4_COL -> Icons.Default.Apps
                                LibraryViewMode.COVER_FLOW -> Icons.Default.Flip
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = "View Mode",
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))

                    // 紧凑操作图标群 (32dp 精致圆形点击区域，间距均匀，极简现代)

                    // 右侧紧凑操作图标群 (32dp 精致圆形点击区域，间距均匀，毫无臃肿感)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. 搜索开关
                        IconButton(
                            onClick = { viewModel.toggleSearch() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (libraryState.isSearching) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (libraryState.isSearching) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 2. 多选模式开关
                        IconButton(
                            onClick = { isSelectionMode = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = stringResource(R.string.menu_multi_select),
                                tint = OrbitTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 3. 视图模式切换
                        IconButton(
                            onClick = { viewModel.cycleViewMode(currentPageKey) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            val icon = when (currentActiveViewMode) {
                                LibraryViewMode.LIST_NO_ART -> Icons.AutoMirrored.Filled.FormatListBulleted
                                LibraryViewMode.LIST_SMALL_ART -> Icons.AutoMirrored.Filled.ViewList
                                LibraryViewMode.LIST_LARGE_ART -> Icons.Default.ViewAgenda
                                LibraryViewMode.GRID_2_COL -> Icons.Default.GridView
                                LibraryViewMode.GRID_3_COL -> Icons.Default.GridOn
                                LibraryViewMode.GRID_4_COL -> Icons.Default.Apps
                                LibraryViewMode.COVER_FLOW -> Icons.Default.Flip
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = "View Mode",
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 4. 扫描媒体库
                        IconButton(
                            onClick = { viewModel.scanMedia() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Scan",
                                tint = if (isScanning) OrbitTheme.colors.tertiary else OrbitTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 5. 设置
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            val isDrillDown = openedFolderPath != null || openedAlbum != null || openedArtist != null || openedPlaylist != null

            // 2. 现代 Segmented Pill 胶囊标签栏 (下钻时隐藏)
            if (!isDrillDown) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LibraryTab.values().forEach { tab ->
                        val isSelected = libraryState.currentTab == tab
                        val pillBgColor = if (isSelected) {
                            OrbitTheme.colors.primary.copy(alpha = 0.16f)
                        } else {
                            OrbitTheme.colors.surfaceCard.copy(alpha = 0.5f)
                        }
                        val pillBorderColor = if (isSelected) {
                            OrbitTheme.colors.primary.copy(alpha = 0.45f)
                        } else {
                            Color.Transparent
                        }
                        val pillTextColor = if (isSelected) {
                            OrbitTheme.colors.primary
                        } else {
                            OrbitTheme.colors.textSecondary
                        }

                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(pillBgColor)
                                .border(BorderStroke(1.dp, pillBorderColor), RoundedCornerShape(16.dp))
                                .clickable {
                                    openedFolderPath = null
                                    openedAlbum = null
                                    openedArtist = null
                                    viewModel.setTab(tab)
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = getTabTitle(tab),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = pillTextColor,
                                fontSize = 12.5.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 3. 搜索栏 (紧凑胶囊搜索栏，仅在未下钻主库页面显示；下钻详情页使用其卡片下方的专属搜索栏)
            AnimatedVisibility(
                visible = libraryState.isSearching && !isDrillDown,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = OrbitTheme.colors.surfaceCard,
                    border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .height(38.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = OrbitTheme.colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = libraryState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = OrbitTheme.colors.textPrimary,
                                fontSize = 13.sp
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
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = OrbitTheme.colors.textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 核心音乐库内容视图 (支持 Pinch 缩放与 6 档视图切换)
    @Composable
    fun LibraryMainContent(bottomPadding: androidx.compose.ui.unit.Dp = 98.dp) {
            PowerampViewModeTransitionContainer(
                pageKey = currentPageKey,
                viewMode = currentActiveViewMode,
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
                            LibraryViewMode.COVER_FLOW -> 1
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
                            LibraryViewMode.COVER_FLOW -> 1
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
                            LibraryViewMode.COVER_FLOW -> 1
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
                val playlistSongsGridState = playlistSongsGridHolder.getOrCreate(currentViewMode)

                // ========== 如果处于文件夹下钻内部，展示该文件夹的歌曲 ==========
                if (openedFolderPath != null) {
                    val currentPath = openedFolderPath!!
                    val folderSongs = remember(currentPath, allSongs, libraryState.searchQuery) {
                        val baseSongs = allSongs.filter { it.folderPath == currentPath }
                        val q = libraryState.searchQuery.trim()
                        if (q.isBlank()) {
                            baseSongs
                        } else {
                            baseSongs.filter {
                                it.title.contains(q, ignoreCase = true) ||
                                it.artist.contains(q, ignoreCase = true) ||
                                it.album.contains(q, ignoreCase = true)
                            }
                        }
                    }
                    if (folderSongs.isEmpty()) {
                        EmptyStateView(
                            title = if (libraryState.searchQuery.isNotBlank()) stringResource(R.string.search_no_results_title) else stringResource(R.string.empty_folder_title),
                            subtitle = if (libraryState.searchQuery.isNotBlank()) stringResource(R.string.search_no_results_desc) else stringResource(R.string.empty_folder_desc)
                        )
                    } else if (currentViewMode == LibraryViewMode.COVER_FLOW) {
                        CoverFlowLayout(
                            songs = folderSongs,
                            currentPlayingSongId = playbackState.currentSong?.id,
                            isPlaying = playbackState.isPlaying,
                            coverVersion = coverVer,
                            onSongClick = { song, index -> handleSongItemClick(folderSongs, index) },
                            onFavoriteClick = { viewModel.cycleSongAttitude(it) },
                            onLongClick = { activeSongForLongClickMenu = it },
                            bottomPadding = 98.dp,
                            isInertiaEnabled = libraryState.isCoverFlowInertiaEnabled,
                            onToggleInertia = { viewModel.setCoverFlowInertiaEnabled(it) },
                            locateTrigger = coverFlowLocateTrigger
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
                                    isSelectionMode = isSelectionMode,
                                    isSelected = song.id in selectedSongIds,
                                    onSelectToggle = {
                                        selectedSongIds = if (song.id in selectedSongIds) selectedSongIds - song.id else selectedSongIds + song.id
                                    },
                                    onClick = { handleSongItemClick(folderSongs, index) },
                                    onFavoriteClick = { viewModel.cycleSongAttitude(song) },
                                    onLongClick = { activeSongForLongClickMenu = song }
                                )
                            }
                        }
                    }
                } else if (openedAlbum != null) {
                    // ========== 如果处于专辑下钻内部，展示该专辑的歌曲（参照播放列表详情页精致重构） ==========
                    val currentAlbum = openedAlbum!!
                    val targetTitle = currentAlbum.title.trim()
                    val isUnknown = targetTitle == "未知专辑" || targetTitle.equals("Unknown Album", ignoreCase = true)
                    val baseAlbumSongs = remember(currentAlbum, allSongs) {
                        allSongs.filter { song ->
                            val songAlbum = song.album.trim()
                            songAlbum.equals(targetTitle, ignoreCase = true) ||
                            (isUnknown && (songAlbum.isBlank() || songAlbum.equals("Unknown Album", ignoreCase = true)))
                        }
                    }
                    val filteredAlbumSongs = remember(baseAlbumSongs, libraryState.searchQuery) {
                        val q = libraryState.searchQuery.trim()
                        if (q.isBlank()) {
                            baseAlbumSongs
                        } else {
                            baseAlbumSongs.filter {
                                it.title.contains(q, ignoreCase = true) ||
                                it.artist.contains(q, ignoreCase = true)
                            }
                        }
                    }

                    // 专辑时长统计
                    val totalDurationMs = remember(baseAlbumSongs) {
                        baseAlbumSongs.sumOf { it.durationMs }
                    }
                    val totalDurationText = remember(totalDurationMs) {
                        val totalSeconds = totalDurationMs / 1000
                        val hours = totalSeconds / 3600
                        val minutes = (totalSeconds % 3600) / 60
                        if (hours > 0) {
                            "${hours}小时 ${minutes}分钟"
                        } else {
                            "${minutes}分钟"
                        }
                    }

                    // 专辑封面 URI
                    val albumArtUri = remember(currentAlbum.id, currentAlbum.albumArtUri, baseAlbumSongs) {
                        currentAlbum.albumArtUri?.takeIf { it.isNotBlank() }
                            ?: baseAlbumSongs.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp)
                    ) {
                        // 1. 精致专辑详情 Header 卡片
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = OrbitTheme.colors.surfaceCard.copy(alpha = 0.75f),
                            border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.15f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 左侧：专辑封面大图 (100.dp x 100.dp)
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    OrbitTheme.colors.surface,
                                                    OrbitTheme.colors.primary.copy(alpha = 0.2f)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (albumArtUri != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(albumArtUri)
                                                .memoryCacheKey("${albumArtUri}_$coverVer")
                                                .diskCacheKey("${albumArtUri}_$coverVer")
                                                .build(),
                                            contentDescription = currentAlbum.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Album,
                                            contentDescription = null,
                                            tint = OrbitTheme.colors.primary.copy(alpha = 0.6f),
                                            modifier = Modifier.size(44.dp)
                                        )
                                    }

                                    // 右下角唱片徽章
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(OrbitTheme.colors.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Album,
                                            contentDescription = null,
                                            tint = if (OrbitTheme.colors.isDark) DarkBackground else Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                // 右侧：专辑信息与主要操作
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // 专辑名称
                                    Text(
                                        text = currentAlbum.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = OrbitTheme.colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // 艺术家名称
                                    Text(
                                        text = currentAlbum.artist.ifBlank { "未知艺术家" },
                                        fontSize = 13.sp,
                                        color = OrbitTheme.colors.primary,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // 统计信息：歌曲数 · 总时长
                                    Text(
                                        text = if (baseAlbumSongs.isNotEmpty()) {
                                            stringResource(R.string.tracks_count, baseAlbumSongs.size) + " · " + totalDurationText
                                        } else {
                                            stringResource(R.string.tracks_count, 0)
                                        },
                                        fontSize = 12.sp,
                                        color = OrbitTheme.colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // 底部操作行
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // 播放全部按钮
                                        if (filteredAlbumSongs.isNotEmpty()) {
                                            Button(
                                                onClick = { viewModel.playSong(filteredAlbumSongs, 0) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = OrbitTheme.colors.primary,
                                                    contentColor = if (OrbitTheme.colors.isDark) DarkBackground else Color.White
                                                ),
                                                shape = RoundedCornerShape(20.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = stringResource(R.string.btn_play_all),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.weight(1f))

                                        // 搜索状态统计
                                        if (libraryState.searchQuery.isNotBlank()) {
                                            Text(
                                                text = "${filteredAlbumSongs.size}/${baseAlbumSongs.size}",
                                                fontSize = 11.sp,
                                                color = OrbitTheme.colors.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        // 专辑内快捷搜索按钮
                                        IconButton(
                                            onClick = { viewModel.toggleSearch() },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (libraryState.isSearching) Icons.Default.Close else Icons.Default.Search,
                                                contentDescription = "Search In Album",
                                                tint = if (libraryState.isSearching) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. 专辑内即时搜索输入框
                        AnimatedVisibility(
                            visible = libraryState.isSearching,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 3. 歌曲列表或空状态
                        if (filteredAlbumSongs.isEmpty()) {
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
                                        imageVector = Icons.Default.Album,
                                        contentDescription = null,
                                        tint = OrbitTheme.colors.textSecondary.copy(alpha = 0.35f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (libraryState.searchQuery.isNotBlank()) {
                                            stringResource(R.string.search_no_results_title)
                                        } else {
                                            stringResource(R.string.empty_album_title)
                                        },
                                        fontSize = 13.sp,
                                        color = OrbitTheme.colors.textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                    if (libraryState.searchQuery.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = stringResource(R.string.search_no_results_desc),
                                            fontSize = 11.sp,
                                            color = OrbitTheme.colors.textSecondary.copy(alpha = 0.7f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else if (currentViewMode == LibraryViewMode.COVER_FLOW) {
                            CoverFlowLayout(
                                songs = filteredAlbumSongs,
                                currentPlayingSongId = playbackState.currentSong?.id,
                                isPlaying = playbackState.isPlaying,
                                coverVersion = coverVer,
                                onSongClick = { song, index -> handleSongItemClick(filteredAlbumSongs, index) },
                                onFavoriteClick = { viewModel.cycleSongAttitude(it) },
                                onLongClick = { activeSongForLongClickMenu = it },
                                bottomPadding = 98.dp,
                                isInertiaEnabled = libraryState.isCoverFlowInertiaEnabled,
                                onToggleInertia = { viewModel.setCoverFlowInertiaEnabled(it) },
                                locateTrigger = coverFlowLocateTrigger
                            )
                        } else {
                            LazyVerticalGrid(
                                state = albumSongsGridState,
                                columns = GridCells.Fixed(columnsCount),
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 98.dp),
                                horizontalArrangement = Arrangement.spacedBy(hSpacing),
                                verticalArrangement = Arrangement.spacedBy(vSpacing),
                                modifier = Modifier.fillMaxSize().weight(1f)
                            ) {
                                itemsIndexed(
                                    items = filteredAlbumSongs,
                                    key = { index, song -> "${song.id}_$index" }
                                ) { index, song ->
                                    SongItem(
                                        song = song,
                                        isPlaying = playbackState.isPlaying,
                                        isCurrent = playbackState.currentSong?.id == song.id,
                                        viewMode = currentViewMode,
                                        coverVersion = coverVer,
                                        isSelectionMode = isSelectionMode,
                                        isSelected = song.id in selectedSongIds,
                                        onSelectToggle = {
                                            selectedSongIds = if (song.id in selectedSongIds) selectedSongIds - song.id else selectedSongIds + song.id
                                        },
                                        onClick = { handleSongItemClick(filteredAlbumSongs, index) },
                                        onFavoriteClick = { viewModel.cycleSongAttitude(song) },
                                        onLongClick = { activeSongForLongClickMenu = song }
                                    )
                                }
                            }
                        }
                    }
                } else if (openedArtist != null) {
                    // ========== 如果处于艺术家下钻内部，展示该艺术家的歌曲 ==========
                    val currentArtist = openedArtist!!
                    val artistSongs = remember(currentArtist, allSongs, libraryState.searchQuery) {
                        val targetName = currentArtist.name.trim()
                        val baseSongs = allSongs.filter { song ->
                            val songArtist = song.artist.trim()
                            songArtist.equals(targetName, ignoreCase = true) ||
                            (targetName == "未知艺术家" && songArtist.isBlank()) ||
                            songArtist.split('/', ',', '&', '、', ';').any { it.trim().equals(targetName, ignoreCase = true) }
                        }
                        val q = libraryState.searchQuery.trim()
                        if (q.isBlank()) {
                            baseSongs
                        } else {
                            baseSongs.filter {
                                it.title.contains(q, ignoreCase = true) ||
                                it.artist.contains(q, ignoreCase = true) ||
                                it.album.contains(q, ignoreCase = true)
                            }
                        }
                    }
                    if (artistSongs.isEmpty()) {
                        EmptyStateView(
                            title = if (libraryState.searchQuery.isNotBlank()) stringResource(R.string.search_no_results_title) else stringResource(R.string.empty_artist_title),
                            subtitle = if (libraryState.searchQuery.isNotBlank()) stringResource(R.string.search_no_results_desc) else stringResource(R.string.empty_artist_desc)
                        )
                    } else if (currentViewMode == LibraryViewMode.COVER_FLOW) {
                        CoverFlowLayout(
                            songs = artistSongs,
                            currentPlayingSongId = playbackState.currentSong?.id,
                            isPlaying = playbackState.isPlaying,
                            coverVersion = coverVer,
                            onSongClick = { song, index -> handleSongItemClick(artistSongs, index) },
                            onFavoriteClick = { viewModel.cycleSongAttitude(it) },
                            onLongClick = { activeSongForLongClickMenu = it },
                            bottomPadding = 98.dp,
                            isInertiaEnabled = libraryState.isCoverFlowInertiaEnabled,
                            onToggleInertia = { viewModel.setCoverFlowInertiaEnabled(it) },
                            locateTrigger = coverFlowLocateTrigger
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
                                    isSelectionMode = isSelectionMode,
                                    isSelected = song.id in selectedSongIds,
                                    onSelectToggle = {
                                        selectedSongIds = if (song.id in selectedSongIds) selectedSongIds - song.id else selectedSongIds + song.id
                                    },
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
                            // 1. 全部歌曲列表 (全 7 档 Pinch 手势、Cover Flow 与物理位移形变动效)
                            if (filteredSongs.isEmpty()) {
                                EmptyStateView(
                                    title = if (isScanning) stringResource(R.string.scanning_library_title) else stringResource(R.string.empty_songs_title),
                                    subtitle = stringResource(R.string.empty_songs_desc)
                                )
                            } else if (currentViewMode == LibraryViewMode.COVER_FLOW) {
                                CoverFlowLayout(
                                    songs = filteredSongs,
                                    currentPlayingSongId = playbackState.currentSong?.id,
                                    isPlaying = playbackState.isPlaying,
                                    coverVersion = coverVer,
                                    onSongClick = { song, index -> handleSongItemClick(filteredSongs, index) },
                                    onFavoriteClick = { viewModel.cycleSongAttitude(it) },
                                    onLongClick = { activeSongForLongClickMenu = it },
                                    bottomPadding = 98.dp,
                                    isInertiaEnabled = libraryState.isCoverFlowInertiaEnabled,
                                    onToggleInertia = { viewModel.setCoverFlowInertiaEnabled(it) },
                                    locateTrigger = coverFlowLocateTrigger
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
                                            isSelectionMode = isSelectionMode,
                                            isSelected = song.id in selectedSongIds,
                                            onSelectToggle = {
                                                selectedSongIds = if (song.id in selectedSongIds) selectedSongIds - song.id else selectedSongIds + song.id
                                            },
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
                            if (folders.isEmpty()) {
                                EmptyStateView(
                                    title = if (libraryState.searchQuery.isNotBlank()) stringResource(R.string.search_no_results_title) else stringResource(R.string.empty_folder_title),
                                    subtitle = if (libraryState.searchQuery.isNotBlank()) stringResource(R.string.search_no_results_desc) else stringResource(R.string.empty_folder_desc)
                                )
                            } else {
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
                                                    viewModel.setSearchQuery("")
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
                        }

                        LibraryTab.ALBUMS -> {
                            // 3. 专辑库 (支持全 7 档自适应 Pinch 手势、Cover Flow 与点击下钻)
                            if (albums.isEmpty()) {
                                EmptyStateView(
                                    title = if (libraryState.searchQuery.isNotBlank()) {
                                        stringResource(R.string.search_no_results_title)
                                    } else if (isScanning) {
                                        stringResource(R.string.scanning_library_title)
                                    } else {
                                        stringResource(R.string.no_albums_found)
                                    },
                                    subtitle = if (libraryState.searchQuery.isNotBlank()) {
                                        stringResource(R.string.search_no_results_desc)
                                    } else {
                                        stringResource(R.string.empty_albums_desc)
                                    }
                                )
                            } else if (currentViewMode == LibraryViewMode.COVER_FLOW) {
                                AlbumCoverFlowLayout(
                                    albums = albums,
                                    currentPlayingAlbumTitle = playbackState.currentSong?.album,
                                    coverVersion = coverVer,
                                    onAlbumClick = { album ->
                                        viewModel.setSearchQuery("")
                                        openedAlbum = album
                                    },
                                    bottomPadding = 98.dp,
                                    isInertiaEnabled = libraryState.isCoverFlowInertiaEnabled,
                                    onToggleInertia = { viewModel.setCoverFlowInertiaEnabled(it) }
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
                                                viewModel.setSearchQuery("")
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
                                title = if (libraryState.searchQuery.isNotBlank()) {
                                    stringResource(R.string.search_no_results_title)
                                } else if (isScanning) {
                                    stringResource(R.string.scanning_library_title)
                                } else {
                                    stringResource(R.string.no_artists_found)
                                },
                                subtitle = if (libraryState.searchQuery.isNotBlank()) {
                                    stringResource(R.string.search_no_results_desc)
                                } else {
                                    stringResource(R.string.empty_artists_desc)
                                }
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
                                                viewModel.setSearchQuery("")
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
                            val filteredPlaylistSongs = remember(playlistSongs, libraryState.searchQuery) {
                                val q = libraryState.searchQuery.trim()
                                if (q.isBlank()) {
                                    playlistSongs
                                } else {
                                    playlistSongs.filter {
                                        it.title.contains(q, ignoreCase = true) ||
                                        it.artist.contains(q, ignoreCase = true) ||
                                        it.album.contains(q, ignoreCase = true)
                                    }
                                }
                            }

                            // 随机获取歌单封面 (从歌单现有歌曲中挑选)
                            val randomCoverArtUri = remember(currentPlaylist.id, playlistSongs) {
                                val songsWithArt = playlistSongs.filter { !it.albumArtUri.isNullOrBlank() }
                                if (songsWithArt.isNotEmpty()) {
                                    songsWithArt.random().albumArtUri
                                } else if (playlistSongs.isNotEmpty()) {
                                    playlistSongs.random().albumArtUri
                                } else {
                                    null
                                }
                            }

                            // 歌单时长统计
                            val totalDurationMs = remember(playlistSongs) {
                                playlistSongs.sumOf { it.durationMs }
                            }
                            val totalDurationText = remember(totalDurationMs) {
                                val totalSeconds = totalDurationMs / 1000
                                val hours = totalSeconds / 3600
                                val minutes = (totalSeconds % 3600) / 60
                                if (hours > 0) {
                                    "${hours}小时 ${minutes}分钟"
                                } else {
                                    "${minutes}分钟"
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                            ) {
                                // 精致歌单详情 Header 卡片
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = OrbitTheme.colors.surfaceCard.copy(alpha = 0.75f),
                                    border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.15f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 左侧：随机封面大图 (100.dp x 100.dp)
                                        Box(
                                            modifier = Modifier
                                                .size(100.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        colors = listOf(
                                                            OrbitTheme.colors.surface,
                                                            OrbitTheme.colors.primary.copy(alpha = 0.2f)
                                                        )
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (randomCoverArtUri != null) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(randomCoverArtUri)
                                                        .memoryCacheKey("${randomCoverArtUri}_$coverVer")
                                                        .diskCacheKey("${randomCoverArtUri}_$coverVer")
                                                        .build(),
                                                    contentDescription = currentPlaylist.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = when (currentPlaylist.id) {
                                                        FAVORITE_PLAYLIST_ID -> Icons.Default.Favorite
                                                        DISLIKED_PLAYLIST_ID -> Icons.Default.ThumbDown
                                                        else -> Icons.AutoMirrored.Filled.PlaylistPlay
                                                    },
                                                    contentDescription = null,
                                                    tint = when (currentPlaylist.id) {
                                                        FAVORITE_PLAYLIST_ID -> Color(0xFFFF3366).copy(alpha = 0.7f)
                                                        DISLIKED_PLAYLIST_ID -> Color(0xFFE57373).copy(alpha = 0.7f)
                                                        else -> OrbitTheme.colors.primary.copy(alpha = 0.6f)
                                                    },
                                                    modifier = Modifier.size(44.dp)
                                                )
                                            }

                                            // 右下角徽章（我喜欢的音乐 / 不喜欢列表）
                                            if (currentPlaylist.id == FAVORITE_PLAYLIST_ID) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(4.dp)
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFFF3366)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Favorite,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            } else if (currentPlaylist.id == DISLIKED_PLAYLIST_ID) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(4.dp)
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFE57373)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ThumbDown,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        // 右侧：歌单信息与主要操作
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // 歌单标题
                                            Text(
                                                text = currentPlaylist.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 17.sp,
                                                color = OrbitTheme.colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            // 统计信息：歌曲数 · 总时长
                                            Text(
                                                text = if (playlistSongs.isNotEmpty()) {
                                                    stringResource(R.string.tracks_count, playlistSongs.size) + " · " + totalDurationText
                                                } else {
                                                    stringResource(R.string.tracks_count, 0)
                                                },
                                                fontSize = 12.sp,
                                                color = OrbitTheme.colors.textSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))

                                            // 底部操作按钮行
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // 播放全部按钮
                                                if (filteredPlaylistSongs.isNotEmpty()) {
                                                    Button(
                                                        onClick = { viewModel.playSong(filteredPlaylistSongs, 0) },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = OrbitTheme.colors.primary,
                                                            contentColor = Color.White
                                                        ),
                                                        shape = RoundedCornerShape(20.dp),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PlayArrow,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Text(
                                                            text = stringResource(R.string.btn_play_all),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp
                                                        )
                                                    }
                                                }

                                                // 添加歌曲按钮 (普通歌单)
                                                if (currentPlaylist.id != FAVORITE_PLAYLIST_ID && currentPlaylist.id != DISLIKED_PLAYLIST_ID) {
                                                    OutlinedButton(
                                                        onClick = { showAddSongsToPlaylistDialog = true },
                                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OrbitTheme.colors.primary),
                                                        border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.5f)),
                                                        shape = RoundedCornerShape(20.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(32.dp)
                                                    ) {
                                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Text(stringResource(R.string.add_songs), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                                    }
                                                } else if (currentPlaylist.id == DISLIKED_PLAYLIST_ID && filteredPlaylistSongs.isNotEmpty()) {
                                                    OutlinedButton(
                                                        onClick = { showClearDislikesDialog = true },
                                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE57373)),
                                                        border = BorderStroke(1.dp, Color(0xFFE57373).copy(alpha = 0.5f)),
                                                        shape = RoundedCornerShape(20.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(32.dp)
                                                    ) {
                                                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Text(stringResource(R.string.clear_all_dislikes), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                                    }
                                                }

                                                Spacer(modifier = Modifier.weight(1f))

                                                // 搜索状态统计 / 快捷搜索按钮
                                                if (libraryState.searchQuery.isNotBlank()) {
                                                    Text(
                                                        text = "${filteredPlaylistSongs.size}/${playlistSongs.size}",
                                                        fontSize = 11.sp,
                                                        color = OrbitTheme.colors.primary,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { viewModel.toggleSearch() },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (libraryState.isSearching) Icons.Default.Close else Icons.Default.Search,
                                                        contentDescription = "Search In Playlist",
                                                        tint = if (libraryState.isSearching) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 歌单内即时搜索输入框
                                AnimatedVisibility(
                                    visible = libraryState.isSearching,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
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
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // 歌曲列表或空状态
                                if (filteredPlaylistSongs.isEmpty()) {
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
                                                imageVector = when (currentPlaylist.id) {
                                                    FAVORITE_PLAYLIST_ID -> Icons.Default.Favorite
                                                    DISLIKED_PLAYLIST_ID -> Icons.Default.ThumbDown
                                                    else -> Icons.AutoMirrored.Filled.PlaylistPlay
                                                },
                                                contentDescription = null,
                                                tint = when (currentPlaylist.id) {
                                                    FAVORITE_PLAYLIST_ID -> Color(0xFFFF3366).copy(alpha = 0.4f)
                                                    DISLIKED_PLAYLIST_ID -> Color(0xFFE57373).copy(alpha = 0.4f)
                                                    else -> OrbitTheme.colors.textSecondary.copy(alpha = 0.35f)
                                                },
                                                modifier = Modifier.size(64.dp)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = if (libraryState.searchQuery.isNotBlank()) {
                                                    stringResource(R.string.search_no_results_title)
                                                } else when (currentPlaylist.id) {
                                                    FAVORITE_PLAYLIST_ID -> stringResource(R.string.favorites_empty_hint)
                                                    DISLIKED_PLAYLIST_ID -> stringResource(R.string.disliked_empty_hint)
                                                    else -> stringResource(R.string.empty_playlist_hint)
                                                },
                                                fontSize = 13.sp,
                                                color = OrbitTheme.colors.textSecondary,
                                                textAlign = TextAlign.Center
                                            )
                                            if (libraryState.searchQuery.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = stringResource(R.string.search_no_results_desc),
                                                    fontSize = 11.sp,
                                                    color = OrbitTheme.colors.textSecondary.copy(alpha = 0.7f),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                } else if (currentViewMode == LibraryViewMode.COVER_FLOW) {
                                    CoverFlowLayout(
                                        songs = filteredPlaylistSongs,
                                        currentPlayingSongId = playbackState.currentSong?.id,
                                        isPlaying = playbackState.isPlaying,
                                        coverVersion = coverVer,
                                        onSongClick = { song, index -> handleSongItemClick(filteredPlaylistSongs, index) },
                                        onFavoriteClick = { viewModel.cycleSongAttitude(it) },
                                        onLongClick = { activeSongForLongClickMenu = it },
                                        bottomPadding = 98.dp,
                                        isInertiaEnabled = libraryState.isCoverFlowInertiaEnabled,
                                        onToggleInertia = { viewModel.setCoverFlowInertiaEnabled(it) },
                                        locateTrigger = coverFlowLocateTrigger
                                    )
                                } else {
                                    LazyVerticalGrid(
                                        state = playlistSongsGridState,
                                        columns = GridCells.Fixed(columnsCount),
                                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 98.dp),
                                        horizontalArrangement = Arrangement.spacedBy(hSpacing),
                                        verticalArrangement = Arrangement.spacedBy(vSpacing),
                                        modifier = Modifier.fillMaxSize().weight(1f)
                                    ) {
                                        itemsIndexed(
                                            items = filteredPlaylistSongs,
                                            key = { index, song -> "${song.id}_$index" }
                                        ) { index, song ->
                                            SongItem(
                                                song = song,
                                                isPlaying = playbackState.isPlaying,
                                                isCurrent = playbackState.currentSong?.id == song.id,
                                                viewMode = currentViewMode,
                                                coverVersion = coverVer,
                                                isSelectionMode = isSelectionMode,
                                                isSelected = song.id in selectedSongIds,
                                                onSelectToggle = {
                                                    selectedSongIds = if (song.id in selectedSongIds) selectedSongIds - song.id else selectedSongIds + song.id
                                                },
                                                onClick = { handleSongItemClick(filteredPlaylistSongs, index) },
                                                onFavoriteClick = { viewModel.cycleSongAttitude(song) },
                                                onLongClick = { activeSongForLongClickMenu = song },
                                                trailingContent = {
                                                    IconButton(
                                                        onClick = {
                                                            if (currentPlaylist.id == FAVORITE_PLAYLIST_ID) {
                                                                viewModel.toggleFavorite(song)
                                                            } else if (currentPlaylist.id == DISLIKED_PLAYLIST_ID) {
                                                                viewModel.removeDislike(song)
                                                            } else {
                                                                viewModel.removeSongFromPlaylist(currentPlaylist.id, song.id)
                                                                playlistSongs = playlistSongs.filter { it.id != song.id }
                                                            }
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = when (currentPlaylist.id) {
                                                                FAVORITE_PLAYLIST_ID -> Icons.Default.Favorite
                                                                DISLIKED_PLAYLIST_ID -> Icons.Default.ThumbDown
                                                                else -> Icons.Default.Close
                                                            },
                                                            contentDescription = when (currentPlaylist.id) {
                                                                FAVORITE_PLAYLIST_ID -> stringResource(R.string.remove_from_favorites)
                                                                DISLIKED_PLAYLIST_ID -> stringResource(R.string.remove_dislike)
                                                                else -> stringResource(R.string.remove_from_playlist)
                                                            },
                                                            tint = when (currentPlaylist.id) {
                                                                FAVORITE_PLAYLIST_ID -> Color(0xFFFF3366)
                                                                DISLIKED_PLAYLIST_ID -> Color(0xFFE57373)
                                                                else -> OrbitTheme.colors.textSecondary.copy(alpha = 0.6f)
                                                            },
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

                                val q = libraryState.searchQuery.trim()
                                val favTitle = stringResource(R.string.favorite_songs)
                                val favMatches = favoriteSongs.isNotEmpty() && (q.isBlank() || favTitle.contains(q, ignoreCase = true) || favoriteSongs.any {
                                    it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true)
                                })
                                val dislikedTitle = stringResource(R.string.disliked_songs)
                                val dislikedMatches = dislikedSongs.isNotEmpty() && (q.isBlank() || dislikedTitle.contains(q, ignoreCase = true) || dislikedSongs.any {
                                    it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true)
                                })
                                val filteredPlaylists = remember(playlists, libraryState.searchQuery) {
                                    if (q.isBlank()) {
                                        playlists
                                    } else {
                                        playlists.filter { it.name.contains(q, ignoreCase = true) }
                                    }
                                }

                                if (filteredPlaylists.isEmpty() && !favMatches && !dislikedMatches) {
                                    EmptyStateView(
                                        title = stringResource(R.string.search_no_results_title),
                                        subtitle = stringResource(R.string.search_no_results_desc)
                                    )
                                } else {
                                    LazyColumn(
                                        state = playlistsListState,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(bottom = 98.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // 置顶显示「我喜欢的音乐」专属卡片
                                        if (favMatches) {
                                            item {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(OrbitTheme.colors.surfaceCard)
                                                        .clickable {
                                                            viewModel.setSearchQuery("")
                                                            openedPlaylist = Playlist(
                                                                id = FAVORITE_PLAYLIST_ID,
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
                                        }

                                        // 置顶显示「不喜欢的歌曲」专属卡片（当存在被标记为不喜欢的歌曲时显示）
                                        if (dislikedMatches) {
                                            item {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(OrbitTheme.colors.surfaceCard)
                                                        .clickable {
                                                            viewModel.setSearchQuery("")
                                                            openedPlaylist = Playlist(
                                                                id = DISLIKED_PLAYLIST_ID,
                                                                name = dislikedTitle,
                                                                songCount = dislikedSongs.size,
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
                                                            .background(Color(0xFFE57373).copy(alpha = 0.15f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.ThumbDown,
                                                            contentDescription = null,
                                                            tint = Color(0xFFE57373),
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(14.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = dislikedTitle,
                                                            fontWeight = FontWeight.Bold,
                                                            color = OrbitTheme.colors.textPrimary,
                                                            fontSize = 15.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = stringResource(R.string.tracks_count, dislikedSongs.size),
                                                            color = OrbitTheme.colors.textSecondary,
                                                            fontSize = 12.sp
                                                        )
                                                    }

                                                    // 快捷播放全部不喜欢的音乐（方便重新试听确认）
                                                    if (dislikedSongs.isNotEmpty()) {
                                                        IconButton(
                                                            onClick = { viewModel.playSong(dislikedSongs, 0) },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.PlayArrow,
                                                                contentDescription = stringResource(R.string.btn_play_all),
                                                                tint = Color(0xFFE57373),
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
                                        }

                                        items(filteredPlaylists, key = { it.id }) { playlist ->
                                            Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(OrbitTheme.colors.surfaceCard)
                                                .clickable {
                                                    viewModel.setSearchQuery("")
                                                    openedPlaylist = playlist
                                                }
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
    }

    // 平板侧边栏展开/折叠状态管理
    var isSideNavExpanded by rememberSaveable { mutableStateOf(true) }
    val sideNavWidth by animateDpAsState(
        targetValue = if (isSideNavExpanded) 180.dp else 60.dp,
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
                    .zIndex(10f)
            )

            // 纵向分割细线
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(OrbitTheme.colors.surfaceCard.copy(alpha = 0.6f))
            )

            // 右侧主视图区（严格裁剪边界，杜绝内部组件手势与绘制溢出影响左侧导航栏）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
            ) {
                Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                    // 仅在下钻（文件夹/专辑/艺术家/歌单）时展示返回面包屑栏；未下钻时不展示任何TopBar且不保留空间
                    val isDrillDown = openedFolderPath != null || openedAlbum != null || openedArtist != null || openedPlaylist != null
                    if (isDrillDown) {
                        TabletDrillDownTopBar(
                            openedFolderPath = openedFolderPath,
                            openedAlbum = openedAlbum,
                            openedArtist = openedArtist,
                            openedPlaylist = openedPlaylist,
                            playlistSongsCount = playlistSongs.size,
                            isSearching = libraryState.isSearching,
                            searchQuery = libraryState.searchQuery,
                            onToggleSearch = { viewModel.toggleSearch() },
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            viewMode = currentActiveViewMode,
                            onCycleViewMode = { viewModel.cycleViewMode(currentPageKey) },
                            onBack = {
                                openedFolderPath = null
                                openedAlbum = null
                                openedArtist = null
                                openedPlaylist = null
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

                            // 2. 浮动视图切换按钮（支持在「列表」、「高密度Grid」与「3D Cover Flow」之间切换）
                            Surface(
                                onClick = { viewModel.cycleViewMode(currentPageKey) },
                                shape = CircleShape,
                                color = OrbitTheme.colors.surfaceCard.copy(alpha = 0.94f),
                                border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.5f)),
                                shadowElevation = 8.dp,
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    val icon = when (currentActiveViewMode) {
                                        LibraryViewMode.COVER_FLOW -> Icons.Default.Flip
                                        LibraryViewMode.GRID_2_COL,
                                        LibraryViewMode.GRID_3_COL,
                                        LibraryViewMode.GRID_4_COL -> Icons.Default.GridView
                                        else -> Icons.AutoMirrored.Filled.ViewList
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = "Switch View Mode",
                                        tint = OrbitTheme.colors.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // 3. 定位当前播放歌曲按钮（位于最下方）
                            LocatePlayingSongFab()
                        }

                        // 平板端多选悬浮工具栏
                        multiSelectActionBar(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 106.dp)
                        )
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

                // 手机端多选悬浮工具栏
                multiSelectActionBar(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 106.dp)
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
    renamingPlaylist?.let { targetPlaylist ->
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
                            viewModel.renamePlaylist(targetPlaylist.id, renamePlaylistName.trim())
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
    deletingPlaylist?.let { targetPlaylist ->
        AlertDialog(
            onDismissRequest = { deletingPlaylist = null },
            title = { Text(stringResource(R.string.delete_playlist), color = OrbitTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.delete_playlist_confirm, targetPlaylist.name), color = OrbitTheme.colors.textSecondary, fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePlaylist(targetPlaylist.id)
                        if (openedPlaylist?.id == targetPlaylist.id) {
                            openedPlaylist = null
                        }
                        deletingPlaylist = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4D4F))
                ) {
                    Text(stringResource(R.string.delete_playlist), color = Color.White)
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

    // 3.5 清空不喜欢列表确认弹窗
    if (showClearDislikesDialog) {
        AlertDialog(
            onDismissRequest = { showClearDislikesDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.clear_all_dislikes),
                    color = OrbitTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.clear_all_dislikes_confirm),
                    color = OrbitTheme.colors.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllDislikes()
                        showClearDislikesDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))
                ) {
                    Text(stringResource(R.string.btn_ok), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDislikesDialog = false }) {
                    Text(stringResource(R.string.btn_cancel), color = OrbitTheme.colors.textSecondary)
                }
            },
            containerColor = OrbitTheme.colors.surfaceDialog
        )
    }

    // 4. 播放列表下钻中添加歌曲弹窗
    if (showAddSongsToPlaylistDialog && openedPlaylist != null) {
        val targetPlaylist = openedPlaylist!!
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

    // 5. 歌曲列表中单曲添加到播放列表弹窗 (使用 allPlaylists 彻底解决搜索歌曲时歌单被过滤找不到的问题)
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

                    if (allPlaylists.isEmpty()) {
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
                            items(allPlaylists, key = { it.id }) { pl ->
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

    // 5.5 批量添加到播放列表弹窗
    if (showBatchAddToPlaylistDialog && selectedSongIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showBatchAddToPlaylistDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.batch_add_to_playlist_dialog_title),
                    color = OrbitTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    Text(
                        text = stringResource(R.string.selected_count, selectedSongIds.size),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = OrbitTheme.colors.primary,
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

                    if (allPlaylists.isEmpty()) {
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
                            items(allPlaylists, key = { it.id }) { pl ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            val addCount = selectedSongIds.size
                                            viewModel.addSongsToPlaylist(pl.id, selectedSongIds) {
                                                FastToast.show(context, context.getString(R.string.batch_add_to_playlist_success, addCount))
                                            }
                                            showBatchAddToPlaylistDialog = false
                                            isSelectionMode = false
                                            selectedSongIds = emptySet()
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
                TextButton(onClick = { showBatchAddToPlaylistDialog = false }) {
                    Text(stringResource(R.string.btn_cancel), color = OrbitTheme.colors.textSecondary)
                }
            },
            containerColor = OrbitTheme.colors.surfaceDialog
        )
    }

    // 5.8 批量删除歌曲确认弹窗
    if (showBatchDeleteDialog && selectedSongIds.isNotEmpty()) {
        val deleteCount = selectedSongIds.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.batch_delete_dialog_title),
                    color = OrbitTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.batch_delete_confirm_message, deleteCount),
                        fontSize = 14.sp,
                        color = OrbitTheme.colors.textSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { batchDeleteLocalFileChecked = !batchDeleteLocalFileChecked },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = batchDeleteLocalFileChecked,
                            onCheckedChange = { batchDeleteLocalFileChecked = it },
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
                Button(
                    onClick = {
                        val targets = selectedSongsList
                        val deleteLocal = batchDeleteLocalFileChecked
                        showBatchDeleteDialog = false
                        viewModel.deleteSongs(targets, deleteLocal) { success ->
                            FastToast.show(
                                context,
                                if (success) context.getString(R.string.batch_delete_success, targets.size)
                                else context.getString(R.string.delete_song_failed)
                            )
                        }
                        isSelectionMode = false
                        selectedSongIds = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4D4F))
                ) {
                    Text(stringResource(R.string.batch_delete), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
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

                // 操作项 0.5：取消标记不喜欢
                if (longClickedSong.isDisliked) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val target = longClickedSong
                                activeSongForLongClickMenu = null
                                viewModel.removeDislike(target)
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbDown,
                            contentDescription = null,
                            tint = Color(0xFFE57373),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.remove_dislike),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = OrbitTheme.colors.textPrimary
                        )
                    }
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

                // 操作项 1.2：查看专辑
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val target = longClickedSong
                            activeSongForLongClickMenu = null
                            viewModel.openAlbum(target)
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

                // 操作项 1.5：多选操作
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val target = longClickedSong
                            activeSongForLongClickMenu = null
                            isSelectionMode = true
                            selectedSongIds = setOf(target.id)
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Checklist,
                        contentDescription = null,
                        tint = OrbitTheme.colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.menu_multi_select),
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
                            val cached = com.orbit.music.data.cover.MusicBrainzCoverService.getCachedCandidates(target.id)
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

                // 操作项 3.5：详细信息
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val target = longClickedSong
                            activeSongForLongClickMenu = null
                            songForDetailInfo = target
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

                // 操作项 4：删除歌曲
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val target = longClickedSong
                            activeSongForLongClickMenu = null
                            deleteLocalFileChecked = false
                            songToDelete = target
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
        }
    }

    // 歌曲详细信息弹窗 (参考图高质感展示)
    songForDetailInfo?.let { targetSong ->
        com.orbit.music.ui.components.SongDetailInfoDialog(
            song = targetSong,
            onDismissRequest = { songForDetailInfo = null },
            onChangeCover = { s ->
                songForDetailInfo = null
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
            }
        )
    }

    // 删除歌曲确认对话框 (可勾选是否同时删除本地文件)
    if (songToDelete != null) {
        val target = songToDelete!!
        AlertDialog(
            onDismissRequest = { songToDelete = null },
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
                        text = stringResource(R.string.delete_song_confirm_message, target.title),
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
                        songToDelete = null

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

                        viewModel.deleteSong(target, deleteLocal) { success ->
                            if (success) {
                                FastToast.show(context, R.string.delete_song_success)
                            } else {
                                FastToast.show(context, R.string.delete_song_failed)
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
                TextButton(onClick = { songToDelete = null }) {
                    Text(
                        text = stringResource(R.string.btn_cancel),
                        color = OrbitTheme.colors.textSecondary
                    )
                }
            },
            containerColor = OrbitTheme.colors.surfaceDialog
        )
    }

    // 候选专辑封面选择对话框 (展示已有封面或在线检索封面，支持下载/重新下载并保存)
    if (showSelectAlbumCoverDialog && candidateSong != null) {
        val songToApply = candidateSong!!
        val hasDownloadedCover = remember(songToApply.id, coverVer) {
            com.orbit.music.utils.CoverHelper.hasDownloadedCover(context, songToApply.id)
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
                        com.orbit.music.utils.CoverHelper.resetOnlineSearchStatus(songToApply.id)
                        val result = com.orbit.music.data.cover.MusicBrainzCoverService.checkAndFetchLargeCover(
                            context = context,
                            song = songToApply,
                            force = true
                        )
                        withContext(Dispatchers.Main) {
                            searchingSong = null
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
                            searchingSong = null
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
        Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = OrbitTheme.colors.textSecondary, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = OrbitTheme.colors.textPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(subtitle, fontSize = 12.sp, color = OrbitTheme.colors.textSecondary)
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
 * 专为平板大屏打造的紧凑侧边导航栏 (Tablet Side Navigation Rail)
 * 采用紧凑轻量级设计：展开宽度更窄(180dp)、内边距更精细，为右侧媒体内容留出更多视觉空间；
 * 折叠时极简灵动(60dp)，各按钮与Tab尺寸统一对称，并具备防极端矮屏溢出的平滑滚动支持。
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
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    horizontal = if (isExpanded) 8.dp else 6.dp,
                    vertical = 8.dp
                ),
            horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally
        ) {
            // 顶部 Logo 与折叠/展开按钮（紧凑精简）
            if (isExpanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = OrbitTheme.colors.primary.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.35f)),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = OrbitTheme.colors.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = "OrBit Player",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrbitTheme.colors.textPrimary
                        )
                    }

                    // 折叠按钮
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuOpen,
                            contentDescription = "Collapse sidebar",
                            tint = OrbitTheme.colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                // 折叠状态下居中展开按钮
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Expand sidebar",
                        tint = OrbitTheme.colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 中间 Tab 项导航栏（支持纵向平滑滚动，杜绝极端矮屏高度溢出）
            val scrollState = rememberScrollState()
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
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
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) {
                                OrbitTheme.colors.primary.copy(alpha = 0.18f)
                            } else {
                                Color.Transparent
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = getTabTitle(tab),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                                    fontSize = 13.sp,
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
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // 折叠状态：紧凑小巧居中方块(40dp)，与底部按钮统一尺寸，垂直轴对称
                        Surface(
                            onClick = { onTabSelected(tab) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) {
                                OrbitTheme.colors.primary.copy(alpha = 0.18f)
                            } else {
                                Color.Transparent
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = getTabTitle(tab),
                                    tint = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                if (count > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 5.dp, end = 5.dp)
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 底部操作区 (扫描本地媒体与偏好设置，紧凑优雅排布)
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isExpanded) {
                    // 展开状态：扫描媒体库
                    Surface(
                        onClick = onScanMedia,
                        shape = RoundedCornerShape(8.dp),
                        color = OrbitTheme.colors.surfaceCard,
                        border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Scan",
                                tint = if (isScanning) OrbitTheme.colors.tertiary else OrbitTheme.colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
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
                        shape = RoundedCornerShape(8.dp),
                        color = OrbitTheme.colors.surfaceCard,
                        border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.settings),
                                fontSize = 12.sp,
                                color = OrbitTheme.colors.textPrimary
                            )
                        }
                    }
                } else {
                    // 折叠状态：紧凑居中图标按钮(40dp)，与Tab项保持视觉对齐
                    Surface(
                        onClick = onScanMedia,
                        shape = RoundedCornerShape(8.dp),
                        color = OrbitTheme.colors.surfaceCard,
                        border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.15f)),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Scan",
                                tint = if (isScanning) OrbitTheme.colors.tertiary else OrbitTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(8.dp),
                        color = OrbitTheme.colors.surfaceCard,
                        border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.15f)),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(18.dp)
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
    openedPlaylist: Playlist?,
    playlistSongsCount: Int = 0,
    isSearching: Boolean,
    searchQuery: String,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    viewMode: LibraryViewMode,
    onCycleViewMode: () -> Unit,
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
                    openedPlaylist != null -> openedPlaylist.name
                    else -> ""
                }
                val subtitle = when {
                    openedFolderPath != null -> openedFolderPath
                    openedAlbum != null -> "${openedAlbum.artist} • ${stringResource(R.string.tracks_count, openedAlbum.songCount)}"
                    openedArtist != null -> "${openedArtist.albumCount} albums • ${stringResource(R.string.tracks_count, openedArtist.songCount)}"
                    openedPlaylist != null -> {
                        val count = if (playlistSongsCount > 0 || openedPlaylist.id in listOf(FAVORITE_PLAYLIST_ID, DISLIKED_PLAYLIST_ID)) playlistSongsCount else openedPlaylist.songCount
                        stringResource(R.string.tracks_count, count)
                    }
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

            AnimatedVisibility(
                visible = isSearching,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(stringResource(R.string.search_hint), fontSize = 12.sp, color = OrbitTheme.colors.textSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = OrbitTheme.colors.surfaceCard,
                        unfocusedContainerColor = OrbitTheme.colors.surfaceCard,
                        focusedBorderColor = OrbitTheme.colors.primary.copy(alpha = 0.7f),
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = OrbitTheme.colors.primary,
                        focusedTextColor = OrbitTheme.colors.textPrimary,
                        unfocusedTextColor = OrbitTheme.colors.textPrimary
                    ),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = OrbitTheme.colors.textSecondary, modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    modifier = Modifier.width(220.dp).height(44.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = if (isSearching) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (isSearching) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary
                )
            }

            IconButton(onClick = onCycleViewMode) {
                val icon = when (viewMode) {
                    LibraryViewMode.LIST_NO_ART -> Icons.AutoMirrored.Filled.FormatListBulleted
                    LibraryViewMode.LIST_SMALL_ART -> Icons.AutoMirrored.Filled.ViewList
                    LibraryViewMode.LIST_LARGE_ART -> Icons.Default.ViewAgenda
                    LibraryViewMode.GRID_2_COL -> Icons.Default.GridView
                    LibraryViewMode.GRID_3_COL -> Icons.Default.GridOn
                    LibraryViewMode.GRID_4_COL -> Icons.Default.Apps
                    LibraryViewMode.COVER_FLOW -> Icons.Default.Flip
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "View Mode",
                    tint = OrbitTheme.colors.primary
                )
            }
        }
    }
}

