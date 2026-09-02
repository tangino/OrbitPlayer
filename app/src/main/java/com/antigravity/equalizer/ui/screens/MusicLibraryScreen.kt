package com.antigravity.equalizer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.antigravity.equalizer.ui.components.SongItem
import com.antigravity.equalizer.ui.theme.*
import com.antigravity.equalizer.ui.utils.pinchToZoomViewMode
import com.antigravity.equalizer.ui.viewmodel.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MusicLibraryScreen(
    viewModel: MusicPlayerViewModel,
    equalizerUiState: EqualizerUiState,
    onOpenEqualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
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

    // 文件夹下钻：当前展开的文件夹路径
    var openedFolderPath by remember { mutableStateOf<String?>(null) }
    // 专辑下钻：当前展开的专辑
    var openedAlbum by remember { mutableStateOf<AlbumItem?>(null) }
    // 艺术家下钻：当前展开的艺术家
    var openedArtist by remember { mutableStateOf<ArtistItem?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val songsGridState = rememberLazyGridState()
    val foldersListState = rememberLazyListState()
    val folderSongsGridState = rememberLazyGridState()
    val albumsGridState = rememberLazyGridState()
    val albumSongsGridState = rememberLazyGridState()
    val artistsListState = rememberLazyListState()
    val artistSongsGridState = rememberLazyGridState()
    val playlistsListState = rememberLazyListState()

    // 监听列表滚动状态：滚动时自动隐藏下方播放 Dock，停止时自动浮现
    val isAnyScrolling by remember {
        derivedStateOf {
            songsGridState.isScrollInProgress ||
                    foldersListState.isScrollInProgress ||
                    folderSongsGridState.isScrollInProgress ||
                    albumsGridState.isScrollInProgress ||
                    albumSongsGridState.isScrollInProgress ||
                    artistsListState.isScrollInProgress ||
                    artistSongsGridState.isScrollInProgress ||
                    playlistsListState.isScrollInProgress
        }
    }

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

    // 5. 搜索栏开启状态 -> 侧滑返回关闭搜索
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
                songsGridState.animateScrollToItem(targetIndex)
            }
        }
    }

    // 全 Tab 通用 Pinch 手势修饰符
    val pinchGestureModifier = Modifier.pinchToZoomViewMode(
        currentViewMode = libraryState.viewMode,
        onViewModeChange = { viewModel.setViewMode(it) },
        onTransformChange = {}
    )

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(DarkBackground)) {
                TopAppBar(
                    title = {
                        if (openedFolderPath != null) {
                            // 文件夹下钻标题
                            Column {
                                val folderName = openedFolderPath!!.substringAfterLast("/").ifEmpty { "Folder" }
                                Text(folderName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(openedFolderPath!!, fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else if (openedAlbum != null) {
                            // 专辑下钻标题
                            Column {
                                Text(openedAlbum!!.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${openedAlbum!!.artist} • ${openedAlbum!!.songCount} tracks", fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else if (openedArtist != null) {
                            // 艺术家下钻标题
                            Column {
                                Text(openedArtist!!.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${openedArtist!!.albumCount} albums • ${openedArtist!!.songCount} tracks", fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else if (libraryState.isSearching) {
                            OutlinedTextField(
                                value = libraryState.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text("Search songs, artists, albums...", color = TextSecondary) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryNeonCyan,
                                    unfocusedBorderColor = SurfaceDark,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 8.dp)
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = PrimaryNeonCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Music Library",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (openedFolderPath != null) {
                            IconButton(onClick = { openedFolderPath = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryNeonCyan)
                            }
                        } else if (openedAlbum != null) {
                            IconButton(onClick = { openedAlbum = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryNeonCyan)
                            }
                        } else if (openedArtist != null) {
                            IconButton(onClick = { openedArtist = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryNeonCyan)
                            }
                        }
                    },
                    actions = {
                        if (openedFolderPath == null && openedAlbum == null && openedArtist == null) {
                            IconButton(onClick = { viewModel.toggleSearch() }) {
                                Icon(
                                    imageVector = if (libraryState.isSearching) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = TextPrimary
                                )
                            }

                            // 🎯 定位当前播放歌曲
                            if (playbackState.currentSong != null) {
                                IconButton(onClick = { locateCurrentPlayingSong() }) {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = "Locate Playing Track",
                                        tint = PrimaryNeonCyan
                                    )
                                }
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
                            Icon(imageVector = icon, contentDescription = "View Mode", tint = PrimaryNeonCyan)
                        }

                        // 扫描本地媒体
                        IconButton(onClick = { viewModel.scanMedia() }) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Scan",
                                tint = if (isScanning) AccentOrange else TextSecondary
                            )
                        }

                        // 均衡器快捷入口
                        IconButton(onClick = onOpenEqualizer) {
                            Icon(imageVector = Icons.Default.Tune, contentDescription = "Equalizer", tint = PrimaryNeonCyan)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
                )

                // 媒体库 Tab 分页栏 (下钻时隐藏 Tab 保持沉浸)
                if (openedFolderPath == null && openedAlbum == null && openedArtist == null) {
                    ScrollableTabRow(
                        selectedTabIndex = libraryState.currentTab.ordinal,
                        containerColor = DarkBackground,
                        contentColor = PrimaryNeonCyan,
                        edgePadding = 16.dp,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[libraryState.currentTab.ordinal]),
                                color = PrimaryNeonCyan
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
                                        text = tab.name,
                                        fontWeight = if (libraryState.currentTab == tab) FontWeight.Bold else FontWeight.Normal,
                                        color = if (libraryState.currentTab == tab) PrimaryNeonCyan else TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val columnsCount = when (libraryState.viewMode) {
                LibraryViewMode.LIST_NO_ART,
                LibraryViewMode.LIST_SMALL_ART,
                LibraryViewMode.LIST_LARGE_ART -> 1
                LibraryViewMode.GRID_2_COL -> 2
                LibraryViewMode.GRID_3_COL -> 3
                LibraryViewMode.GRID_4_COL -> 4
            }

            val hSpacing = when (libraryState.viewMode) {
                LibraryViewMode.GRID_4_COL -> 6.dp
                LibraryViewMode.GRID_3_COL -> 8.dp
                LibraryViewMode.GRID_2_COL -> 10.dp
                else -> 0.dp
            }

            val vSpacing = when (libraryState.viewMode) {
                LibraryViewMode.GRID_4_COL -> 6.dp
                LibraryViewMode.GRID_3_COL -> 8.dp
                LibraryViewMode.GRID_2_COL -> 10.dp
                LibraryViewMode.LIST_LARGE_ART -> 6.dp
                else -> 3.dp
            }

            // ========== 如果处于文件夹下钻内部，展示该文件夹的歌曲 ==========
            if (openedFolderPath != null) {
                val folderSongs = filteredSongs.filter { it.folderPath == openedFolderPath }
                if (folderSongs.isEmpty()) {
                    EmptyStateView(title = "Empty Folder", subtitle = "No playable audio tracks found in this directory.")
                } else {
                    LazyVerticalGrid(
                        state = folderSongsGridState,
                        columns = GridCells.Fixed(columnsCount),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 98.dp),
                        horizontalArrangement = Arrangement.spacedBy(hSpacing),
                        verticalArrangement = Arrangement.spacedBy(vSpacing),
                        modifier = Modifier
                            .fillMaxSize()
                            .then(pinchGestureModifier)
                    ) {
                        itemsIndexed(
                            items = folderSongs,
                            key = { index, song -> "${song.id}_$index" }
                        ) { index, song ->
                            SongItem(
                                song = song,
                                isPlaying = playbackState.isPlaying,
                                isCurrent = playbackState.currentSong?.id == song.id,
                                viewMode = libraryState.viewMode,
                                onClick = { viewModel.playSong(folderSongs, index) }
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
                    EmptyStateView(title = "Empty Album", subtitle = "No playable audio tracks found in this album.")
                } else {
                    LazyVerticalGrid(
                        state = albumSongsGridState,
                        columns = GridCells.Fixed(columnsCount),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 98.dp),
                        horizontalArrangement = Arrangement.spacedBy(hSpacing),
                        verticalArrangement = Arrangement.spacedBy(vSpacing),
                        modifier = Modifier
                            .fillMaxSize()
                            .then(pinchGestureModifier)
                    ) {
                        itemsIndexed(
                            items = albumSongs,
                            key = { index, song -> "${song.id}_$index" }
                        ) { index, song ->
                            SongItem(
                                song = song,
                                isPlaying = playbackState.isPlaying,
                                isCurrent = playbackState.currentSong?.id == song.id,
                                viewMode = libraryState.viewMode,
                                onClick = { viewModel.playSong(albumSongs, index) }
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
                    EmptyStateView(title = "Empty Artist", subtitle = "No playable audio tracks found for this artist.")
                } else {
                    LazyVerticalGrid(
                        state = artistSongsGridState,
                        columns = GridCells.Fixed(columnsCount),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 98.dp),
                        horizontalArrangement = Arrangement.spacedBy(hSpacing),
                        verticalArrangement = Arrangement.spacedBy(vSpacing),
                        modifier = Modifier
                            .fillMaxSize()
                            .then(pinchGestureModifier)
                    ) {
                        itemsIndexed(
                            items = artistSongs,
                            key = { index, song -> "${song.id}_$index" }
                        ) { index, song ->
                            SongItem(
                                song = song,
                                isPlaying = playbackState.isPlaying,
                                isCurrent = playbackState.currentSong?.id == song.id,
                                viewMode = libraryState.viewMode,
                                onClick = { viewModel.playSong(artistSongs, index) }
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
                                title = if (isScanning) "Scanning local music..." else "No songs found",
                                subtitle = "Tap the sync icon above to scan your device storage."
                            )
                        } else {
                            LazyVerticalGrid(
                                state = songsGridState,
                                columns = GridCells.Fixed(columnsCount),
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 98.dp),
                                horizontalArrangement = Arrangement.spacedBy(hSpacing),
                                verticalArrangement = Arrangement.spacedBy(vSpacing),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(pinchGestureModifier)
                            ) {
                                itemsIndexed(
                                    items = filteredSongs,
                                    key = { index, song -> "${song.id}_$index" }
                                ) { index, song ->
                                    SongItem(
                                        song = song,
                                        isPlaying = playbackState.isPlaying,
                                        isCurrent = playbackState.currentSong?.id == song.id,
                                        viewMode = libraryState.viewMode,
                                        onClick = { viewModel.playSong(filteredSongs, index) }
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
                            modifier = Modifier
                                .fillMaxSize()
                                .then(pinchGestureModifier)
                        ) {
                            items(folders, key = { it.folderPath }) { folder ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SurfaceCard)
                                        .clickable {
                                            // 点击进入该文件夹展示歌曲
                                            openedFolderPath = folder.folderPath
                                        }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(folder.folderName, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                                        Text(folder.folderPath, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${folder.songCount} tracks", fontSize = 12.sp, color = PrimaryNeonCyan, fontWeight = FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    LibraryTab.ALBUMS -> {
                        // 3. 专辑库 (支持全 6 档自适应 Pinch 手势与点击下钻)
                        if (albums.isEmpty()) {
                            EmptyStateView(
                                title = if (isScanning) "Scanning local music..." else "No albums found",
                                subtitle = "Your music albums will appear here."
                            )
                        } else {
                            LazyVerticalGrid(
                                state = albumsGridState,
                                columns = GridCells.Fixed(columnsCount),
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 98.dp),
                                horizontalArrangement = Arrangement.spacedBy(hSpacing),
                                verticalArrangement = Arrangement.spacedBy(vSpacing),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(pinchGestureModifier)
                            ) {
                                itemsIndexed(albums, key = { index, album -> "${album.title}_${album.id}_$index" }) { _, album ->
                                    val isAlbumPlaying = playbackState.currentSong?.album == album.title

                                    AlbumItem(
                                        album = album,
                                        viewMode = libraryState.viewMode,
                                        isCurrent = isAlbumPlaying,
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
                                title = if (isScanning) "Scanning local music..." else "No artists found",
                                subtitle = "Artists from your music library will appear here."
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
                                            .background(if (isArtistPlaying) PrimaryNeonCyan.copy(alpha = 0.12f) else SurfaceCard)
                                            .clickable {
                                                openedArtist = artist
                                            }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(28.dp))
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = artist.name,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isArtistPlaying) PrimaryNeonCyan else TextPrimary,
                                                fontSize = 14.sp
                                            )
                                            Text("${artist.albumCount} albums • ${artist.songCount} tracks", color = TextSecondary, fontSize = 11.sp)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("${artist.songCount} tracks", fontSize = 12.sp, color = PrimaryNeonCyan, fontWeight = FontWeight.SemiBold)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    LibraryTab.PLAYLISTS -> {
                        // 5. 播放列表 (支持 Pinch 手势)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .then(pinchGestureModifier)
                        ) {
                            Button(
                                onClick = { showNewPlaylistDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeonCyan),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = DarkBackground)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create New Playlist", color = DarkBackground, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            LazyColumn(
                                state = playlistsListState,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(playlists, key = { it.id }) { playlist ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(SurfaceCard)
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = PrimaryNeonCyan, modifier = Modifier.size(28.dp))
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(playlist.name, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                                            Text("${playlist.songCount} tracks", color = TextSecondary, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 底部常驻 macOS Dock 播放条：列表滑动时自动平滑隐藏，停止时自动浮现
            if (playbackState.currentSong != null) {
                AnimatedVisibility(
                    visible = !isAnyScrolling,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(240)
                    ) + fadeIn(animationSpec = tween(200)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(200)
                    ) + fadeOut(animationSpec = tween(160)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    MiniPlayerBar(
                        playbackState = playbackState,
                        onTogglePlay = { viewModel.togglePlayPause() },
                        onPlayNext = { viewModel.playNext() },
                        onPlayPrevious = { viewModel.playPrevious() },
                        onClick = { viewModel.setNowPlayingExpanded(true) }
                    )
                }
            }
        }
    }

    // 新建播放列表弹窗
    if (showNewPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showNewPlaylistDialog = false },
            title = { Text("New Playlist", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name", color = TextSecondary) },
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
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeonCyan)
                ) {
                    Text("Create", color = DarkBackground)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewPlaylistDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceCard
        )
    }

    // 全屏正在播放页面展开过渡
    AnimatedVisibility(
        visible = libraryState.isNowPlayingExpanded,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        NowPlayingScreen(
            viewModel = viewModel,
            equalizerUiState = equalizerUiState,
            onBack = { viewModel.setNowPlayingExpanded(false) },
            onOpenEqualizer = onOpenEqualizer
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
