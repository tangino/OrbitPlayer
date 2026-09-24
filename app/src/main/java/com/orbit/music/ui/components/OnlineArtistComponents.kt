package com.orbit.music.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.orbit.music.data.online.model.OnlineAlbum
import com.orbit.music.data.online.model.OnlineArtist
import com.orbit.music.data.online.model.OnlineArtistCategory
import com.orbit.music.data.online.model.OnlinePlatform
import com.orbit.music.data.online.model.OnlineSongItem
import com.orbit.music.data.online.repository.OnlineMusicRepository
import com.orbit.music.ui.theme.OrbitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 在线歌手广场 UI 视图（仅从 QQ 音乐平台拉取数据）
 */
@Composable
fun OnlineArtistSquareView(
    onArtistClick: (OnlineArtist) -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = remember { OnlineMusicRepository.getInstance() }
    val scope = rememberCoroutineScope()

    var categories by remember { mutableStateOf<List<OnlineArtistCategory>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<OnlineArtistCategory?>(null) }
    var artists by remember { mutableStateOf<List<OnlineArtist>>(emptyList()) }
    var currentPage by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var isPagingLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 搜索状态
    var searchKeyword by remember { mutableStateOf("") }
    var isSearchMode by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<OnlineArtist>>(emptyList()) }

    // 初始化加载分类与热门歌手
    fun loadInitialArtists(categoryKey: String = "all_all_all") {
        scope.launch {
            isLoading = true
            errorMessage = null
            currentPage = 1
            hasMore = true

            if (categories.isEmpty()) {
                val catRes = repository.getArtistCategories(OnlinePlatform.QQ)
                catRes.onSuccess {
                    categories = it
                    if (selectedCategory == null) {
                        selectedCategory = it.firstOrNull()
                    }
                }
            }

            val res = repository.getArtists(
                category = categoryKey,
                page = 1,
                pageSize = 30,
                platform = OnlinePlatform.QQ
            )
            res.onSuccess { list ->
                artists = list
                hasMore = list.size >= 30
                isLoading = false
            }.onFailure { err ->
                errorMessage = "加载歌手失败: ${err.localizedMessage ?: "网络异常"}"
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadInitialArtists()
    }

    // 分页加载更多
    fun loadNextPage() {
        if (isLoading || isPagingLoading || !hasMore || isSearchMode) return
        val catKey = selectedCategory?.id ?: "all_all_all"
        val nextPage = currentPage + 1
        isPagingLoading = true

        scope.launch {
            val res = repository.getArtists(
                category = catKey,
                page = nextPage,
                pageSize = 30,
                platform = OnlinePlatform.QQ
            )
            res.onSuccess { list ->
                artists = artists + list
                currentPage = nextPage
                hasMore = list.size >= 20
                isPagingLoading = false
            }.onFailure {
                isPagingLoading = false
            }
        }
    }

    // 搜索歌手
    fun performSearch(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) {
            isSearchMode = false
            searchResults = emptyList()
            return
        }
        isSearchMode = true
        isSearching = true
        scope.launch {
            val res = repository.searchArtists(trimmed, page = 1, pageSize = 30, platform = OnlinePlatform.QQ)
            res.onSuccess { list ->
                searchResults = list
                isSearching = false
            }.onFailure {
                searchResults = emptyList()
                isSearching = false
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 1. 顶部操作栏（分类胶囊与搜索按钮）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "QQ 音乐 · 歌手库",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = OrbitTheme.colors.textPrimary
            )

            IconButton(
                onClick = {
                    isSearchMode = !isSearchMode
                    if (!isSearchMode) {
                        searchKeyword = ""
                        searchResults = emptyList()
                    }
                },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = if (isSearchMode) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "搜索歌手",
                    tint = if (isSearchMode) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        // 2. 搜索输入框
        AnimatedVisibility(visible = isSearchMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = OrbitTheme.colors.surfaceCard,
                    border = BorderStroke(0.5.dp, OrbitTheme.colors.surfaceBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = OrbitTheme.colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchKeyword,
                            onValueChange = {
                                searchKeyword = it
                                if (it.isBlank()) {
                                    searchResults = emptyList()
                                }
                            },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = OrbitTheme.colors.textPrimary,
                                fontSize = 13.sp
                            ),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Search
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onSearch = { performSearch(searchKeyword) }
                            ),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (searchKeyword.isEmpty()) {
                                        Text(
                                            text = "输入歌手姓名搜索...",
                                            fontSize = 13.sp,
                                            color = OrbitTheme.colors.textSecondary.copy(alpha = 0.6f)
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (searchKeyword.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    searchKeyword = ""
                                    searchResults = emptyList()
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "清空",
                                    tint = OrbitTheme.colors.textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { performSearch(searchKeyword) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(
                        text = "搜索",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (OrbitTheme.colors.isDark) Color(0xFF101216) else Color.White
                    )
                }
            }
        }

        // 3. 搜索结果列表
        if (isSearchMode && searchKeyword.isNotBlank()) {
            if (isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp, color = OrbitTheme.colors.primary)
                }
            } else if (searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("未找到相关歌手", fontSize = 13.sp, color = OrbitTheme.colors.textSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults, key = { it.id }) { artist ->
                        OnlineArtistRowItem(artist = artist, onClick = { onArtistClick(artist) })
                    }
                }
            }
            return
        }

        // 4. 分类标签横向滑动条
        if (categories.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = cat.id == (selectedCategory?.id ?: "all_all_all")
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) OrbitTheme.colors.primary.copy(alpha = 0.18f) else OrbitTheme.colors.surfaceCard)
                            .border(
                                0.5.dp,
                                if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.surfaceBorder,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                if (selectedCategory?.id != cat.id) {
                                    selectedCategory = cat
                                    loadInitialArtists(cat.id)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = cat.name,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary
                        )
                    }
                }
            }
        }

        // 5. 错误提示
        if (errorMessage != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = OrbitTheme.colors.surfaceCard,
                border = BorderStroke(0.5.dp, Color(0xFFF43F5E).copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFF43F5E),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "重试",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrbitTheme.colors.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { loadInitialArtists(selectedCategory?.id ?: "all_all_all") }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // 6. 主体歌手网格
        if (isLoading && artists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp, color = OrbitTheme.colors.primary)
            }
            return
        }

        val gridState = rememberLazyGridState()
        val isScrolledToEnd by remember {
            derivedStateOf {
                val total = gridState.layoutInfo.totalItemsCount
                val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                total > 0 && lastVisible >= total - 4
            }
        }

        LaunchedEffect(isScrolledToEnd) {
            if (isScrolledToEnd && !isLoading && !isPagingLoading && hasMore) {
                loadNextPage()
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 98.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(artists, key = { it.id }) { artist ->
                OnlineArtistCardItem(artist = artist, onClick = { onArtistClick(artist) })
            }

            if (isPagingLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = OrbitTheme.colors.primary)
                    }
                }
            }
        }
    }
}

/**
 * 歌手卡片（圆形头像）
 */
@Composable
private fun OnlineArtistCardItem(
    artist: OnlineArtist,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = OrbitTheme.colors.surfaceCard,
        border = BorderStroke(0.5.dp, OrbitTheme.colors.surfaceBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(OrbitTheme.colors.surface)
                    .border(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (artist.avatarUrl.isNotEmpty()) {
                    AsyncImage(
                        model = artist.avatarUrl,
                        contentDescription = artist.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = OrbitTheme.colors.textSecondary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = artist.name,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = OrbitTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            if (artist.songCount > 0 || artist.albumCount > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${artist.songCount}首单曲 · ${artist.albumCount}张专辑",
                    fontSize = 10.sp,
                    color = OrbitTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 歌手列表行展示（搜索结果）
 */
@Composable
private fun OnlineArtistRowItem(
    artist: OnlineArtist,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = OrbitTheme.colors.surfaceCard,
        border = BorderStroke(0.5.dp, OrbitTheme.colors.surfaceBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(OrbitTheme.colors.surface),
                contentAlignment = Alignment.Center
            ) {
                if (artist.avatarUrl.isNotEmpty()) {
                    AsyncImage(
                        model = artist.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = OrbitTheme.colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = OrbitTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val desc = if (artist.songCount > 0) "${artist.songCount} 首单曲 · ${artist.albumCount} 张专辑" else "QQ 音乐认证歌手"
                Text(
                    text = desc,
                    fontSize = 11.5.sp,
                    color = OrbitTheme.colors.textSecondary
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = OrbitTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 在线歌手详情视图（包含歌手基本信息写真、单曲 Tab 与专辑 Tab）
 */
@Composable
fun OnlineArtistDetailView(
    artist: OnlineArtist,
    onBack: () -> Unit,
    onSongClick: (index: Int, song: OnlineSongItem, allSongs: List<OnlineSongItem>) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    currentPlayingTitle: String? = null,
    currentPlayingArtist: String? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    val repository = remember { OnlineMusicRepository.getInstance() }
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) } // 0: 热门歌曲, 1: 全部专辑
    var songs by remember { mutableStateOf<List<OnlineSongItem>>(emptyList()) }
    var albums by remember { mutableStateOf<List<OnlineAlbum>>(emptyList()) }
    var isLoadingSongs by remember { mutableStateOf(false) }
    var isLoadingAlbums by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentArtistInfo by remember { mutableStateOf(artist) }

    // 加载歌手单曲与专辑
    LaunchedEffect(artist.id) {
        isLoadingSongs = true
        isLoadingAlbums = true
        errorMessage = null

        scope.launch {
            val detailRes = repository.getArtistDetail(artist.id, OnlinePlatform.QQ)
            detailRes.onSuccess { detail ->
                currentArtistInfo = detail.artist
                songs = detail.hotSongs
                albums = detail.albums
                isLoadingSongs = false
                isLoadingAlbums = false
            }.onFailure { err ->
                errorMessage = "加载歌手详情失败: ${err.localizedMessage ?: "网络错误"}"
                isLoadingSongs = false
                isLoadingAlbums = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
    ) {
        // 1. 歌手头部 Header 卡片（写真与基本信息）
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = OrbitTheme.colors.surfaceCard.copy(alpha = 0.75f),
            border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.15f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 圆形头像
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(OrbitTheme.colors.surface)
                            .border(1.5.dp, OrbitTheme.colors.primary.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentArtistInfo.avatarUrl.isNotEmpty()) {
                            AsyncImage(
                                model = currentArtistInfo.avatarUrl,
                                contentDescription = currentArtistInfo.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = OrbitTheme.colors.textSecondary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = currentArtistInfo.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrbitTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "平台：QQ 音乐",
                            fontSize = 11.5.sp,
                            color = OrbitTheme.colors.primary,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = "收录 ${songs.size} 首歌曲 · ${albums.size} 张专辑",
                            fontSize = 11.5.sp,
                            color = OrbitTheme.colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 一键播放全部与随机播放
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (songs.isNotEmpty()) {
                                onSongClick(0, songs.first(), songs)
                            }
                        },
                        enabled = songs.isNotEmpty(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (OrbitTheme.colors.isDark) Color(0xFF101216) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "播放全部",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (OrbitTheme.colors.isDark) Color(0xFF101216) else Color.White
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (songs.isNotEmpty()) {
                                val randomIdx = (0 until songs.size).random()
                                onSongClick(randomIdx, songs[randomIdx], songs)
                            }
                        },
                        enabled = songs.isNotEmpty(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OrbitTheme.colors.primary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = OrbitTheme.colors.primary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "随机播放",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OrbitTheme.colors.primary
                        )
                    }
                }
            }
        }

        // 2. Tab 切换：歌曲 (Count) / 专辑 (Count)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("歌曲 (${songs.size})", "专辑 (${albums.size})").forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) OrbitTheme.colors.primary.copy(alpha = 0.18f) else OrbitTheme.colors.surfaceCard)
                        .border(
                            0.5.dp,
                            if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.surfaceBorder,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { selectedTab = index }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3. 内容区
        if (isLoadingSongs && songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp, color = OrbitTheme.colors.primary)
            }
            return
        }

        if (selectedTab == 0) {
            // 歌曲列表
            if (songs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂未找到该歌手的歌曲", fontSize = 13.sp, color = OrbitTheme.colors.textSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 98.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(songs, key = { index, song -> "${song.id}_$index" }) { index, song ->
                        val isCurrent = currentPlayingTitle == song.title &&
                                (currentPlayingArtist.isNullOrBlank() || song.artist == currentPlayingArtist)
                        OnlineSongListItem(
                            index = index + 1,
                            song = song,
                            isCurrentPlaying = isCurrent,
                            isPlaying = isPlaying && isCurrent,
                            onClick = { onSongClick(index, song, songs) }
                        )
                    }
                }
            }
        } else {
            // 专辑列表网格
            if (albums.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂未找到该歌手的专辑", fontSize = 13.sp, color = OrbitTheme.colors.textSecondary)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 98.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(albums, key = { it.id }) { album ->
                        OnlineAlbumCardItem(album = album, onClick = { onAlbumClick(album) })
                    }
                }
            }
        }
    }
}

/**
 * 专辑卡片
 */
@Composable
private fun OnlineAlbumCardItem(
    album: OnlineAlbum,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = OrbitTheme.colors.surfaceCard,
        border = BorderStroke(0.5.dp, OrbitTheme.colors.surfaceBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(OrbitTheme.colors.surface)
            ) {
                if (album.coverUrl.isNotEmpty()) {
                    AsyncImage(
                        model = album.coverUrl,
                        contentDescription = album.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                if (album.songCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "${album.songCount}首",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = album.title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OrbitTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!album.publishTime.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = album.publishTime,
                        fontSize = 10.5.sp,
                        color = OrbitTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 在线专辑详情视图（包含专辑封面、曲目列表与播放全部）
 */
@Composable
fun OnlineAlbumDetailView(
    album: OnlineAlbum,
    onBack: () -> Unit,
    onSongClick: (index: Int, song: OnlineSongItem, allSongs: List<OnlineSongItem>) -> Unit,
    currentPlayingTitle: String? = null,
    currentPlayingArtist: String? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    val repository = remember { OnlineMusicRepository.getInstance() }
    val scope = rememberCoroutineScope()

    var songs by remember { mutableStateOf<List<OnlineSongItem>>(emptyList()) }
    var currentAlbum by remember { mutableStateOf(album) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(album.id) {
        isLoading = true
        errorMessage = null
        scope.launch {
            val res = repository.getAlbumDetail(album.id, OnlinePlatform.QQ)
            res.onSuccess { (alb, trackList) ->
                currentAlbum = alb
                songs = trackList
                isLoading = false
            }.onFailure { err ->
                errorMessage = "加载专辑曲目失败: ${err.localizedMessage ?: "网络错误"}"
                isLoading = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
    ) {
        // 1. 专辑头部卡片
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = OrbitTheme.colors.surfaceCard.copy(alpha = 0.75f),
            border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.15f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 封面
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(OrbitTheme.colors.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentAlbum.coverUrl.isNotEmpty()) {
                            AsyncImage(
                                model = currentAlbum.coverUrl,
                                contentDescription = currentAlbum.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Album,
                                contentDescription = null,
                                tint = OrbitTheme.colors.textSecondary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = currentAlbum.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrbitTheme.colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "歌手：${currentAlbum.artist}",
                            fontSize = 12.sp,
                            color = OrbitTheme.colors.textSecondary
                        )

                        val pubInfo = listOfNotNull(
                            currentAlbum.publishTime?.takeIf { it.isNotBlank() }?.let { "发行：$it" },
                            "${songs.size} 首歌曲"
                        ).joinToString(" · ")

                        Text(
                            text = pubInfo,
                            fontSize = 11.sp,
                            color = OrbitTheme.colors.textSecondary.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 一键播放整张专辑
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (songs.isNotEmpty()) {
                                onSongClick(0, songs.first(), songs)
                            }
                        },
                        enabled = songs.isNotEmpty(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (OrbitTheme.colors.isDark) Color(0xFF101216) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "播放整张专辑",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (OrbitTheme.colors.isDark) Color(0xFF101216) else Color.White
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (songs.isNotEmpty()) {
                                val randomIdx = (0 until songs.size).random()
                                onSongClick(randomIdx, songs[randomIdx], songs)
                            }
                        },
                        enabled = songs.isNotEmpty(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OrbitTheme.colors.primary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = OrbitTheme.colors.primary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "随机播放",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OrbitTheme.colors.primary
                        )
                    }
                }
            }
        }

        // 2. 曲目列表
        if (isLoading && songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp, color = OrbitTheme.colors.primary)
            }
            return
        }

        if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂未获取到专辑曲目", fontSize = 13.sp, color = OrbitTheme.colors.textSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 98.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(songs, key = { index, song -> "${song.id}_$index" }) { index, song ->
                    val isCurrent = currentPlayingTitle == song.title &&
                            (currentPlayingArtist.isNullOrBlank() || song.artist == currentPlayingArtist)
                    OnlineSongListItem(
                        index = index + 1,
                        song = song,
                        isCurrentPlaying = isCurrent,
                        isPlaying = isPlaying && isCurrent,
                        onClick = { onSongClick(index, song, songs) }
                    )
                }
            }
        }
    }
}

/**
 * 歌曲列表项
 */
@Composable
private fun OnlineSongListItem(
    index: Int,
    song: OnlineSongItem,
    isCurrentPlaying: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isCurrentPlaying) OrbitTheme.colors.primary.copy(alpha = 0.12f) else OrbitTheme.colors.surfaceCard,
        border = BorderStroke(
            0.5.dp,
            if (isCurrentPlaying) OrbitTheme.colors.primary.copy(alpha = 0.5f) else OrbitTheme.colors.surfaceBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 序号 / 播放动画指示器
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isCurrentPlaying) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.VolumeUp else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = OrbitTheme.colors.primary,
                        modifier = Modifier.size(17.dp)
                    )
                } else {
                    Text(
                        text = index.toString(),
                        fontSize = 12.sp,
                        color = OrbitTheme.colors.textSecondary.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 歌曲信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.title,
                        fontSize = 13.5.sp,
                        fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCurrentPlaying) OrbitTheme.colors.primary else OrbitTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (song.isVip) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .border(0.5.dp, Color(0xFFFF8000), RoundedCornerShape(3.dp))
                                .padding(horizontal = 3.dp, vertical = 0.5.dp)
                        ) {
                            Text(
                                text = "VIP",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF8000)
                            )
                        }
                    }
                }

                Text(
                    text = "${song.artist} · ${song.album.ifEmpty { "单曲" }}",
                    fontSize = 11.sp,
                    color = OrbitTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
