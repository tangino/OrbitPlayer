package com.orbit.music.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.orbit.music.data.online.model.OnlineLeaderboard
import com.orbit.music.data.online.model.OnlinePlatform
import com.orbit.music.data.online.model.OnlinePlaylist
import com.orbit.music.data.online.model.OnlinePlaylistTag
import com.orbit.music.data.online.model.OnlineSongItem
import com.orbit.music.data.online.repository.OnlineMusicRepository
import com.orbit.music.ui.theme.OrbitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 在线歌单广场内容视图（完全融入主音乐库设计语言与配色规范）
 */
/**
 * 在线歌单广场内容视图（完全融入主音乐库设计语言与配色规范）
 */
@Composable
fun OnlinePlaylistSquareView(
    platform: OnlinePlatform,
    onPlaylistClick: (OnlinePlaylist) -> Unit,
    onArtistClick: ((com.orbit.music.data.online.model.OnlineArtist) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: com.orbit.music.ui.viewmodel.OnlinePlaylistViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = "online_square_${platform.name}",
        factory = com.orbit.music.ui.viewmodel.OnlinePlaylistViewModel.Factory(platform)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val repository = remember { OnlineMusicRepository.getInstance() }
    var showSourceManagerDialog by remember { mutableStateOf(false) }

    val tabTitles = listOf("精选推荐", "热门分类", "官方榜单", "我的收藏")

    if (showSourceManagerDialog) {
        val dialogBg = if (OrbitTheme.colors.background == Color.Transparent) {
            if (OrbitTheme.colors.isDark) Color(0xFF101216) else Color(0xFFF8FAFC)
        } else {
            OrbitTheme.colors.background
        }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showSourceManagerDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(dialogBg)
            ) {
                com.orbit.music.ui.screens.AudioSourceManagementScreen(
                    onBack = { showSourceManagerDialog = false }
                )
            }
        }
    }

    // 链接导入对话框
    if (uiState.isImportDialogOpen) {
        ImportPlaylistInlineDialog(
            repository = repository,
            onDismiss = { viewModel.closeImportDialog() },
            onPlaylistResolved = { playlist ->
                viewModel.closeImportDialog()
                onPlaylistClick(playlist)
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 1. 顶部操作栏：次级分类 Pill 胶囊与搜索/导入入口
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 次级 Tab: 精选推荐 | 热门分类 | (歌手库) | 官方榜单 | 我的收藏
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(OrbitTheme.colors.surfaceCard)
                    .border(0.5.dp, OrbitTheme.colors.surfaceBorder, RoundedCornerShape(20.dp))
                    .padding(2.dp)
            ) {
                tabTitles.forEachIndexed { index, title ->
                    val isSelected = uiState.selectedTab == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) OrbitTheme.colors.primary else Color.Transparent)
                            .clickable {
                                viewModel.selectTab(index)
                            }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = title,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) {
                                if (OrbitTheme.colors.isDark) Color(0xFF101216) else Color.White
                            } else {
                                OrbitTheme.colors.textSecondary
                            }
                        )
                    }
                }
            }

            // 搜索、导入链接与音源管理按钮
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { showSourceManagerDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "在线音源管理",
                        tint = OrbitTheme.colors.primary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(
                    onClick = { viewModel.setSearchActive(!uiState.isSearchMode) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isSearchMode) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "搜索歌单",
                        tint = if (uiState.isSearchMode) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(
                    onClick = { viewModel.openImportDialog() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "导入歌单链接",
                        tint = OrbitTheme.colors.textSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        // 2. 嵌入式极简搜索栏 (展开时显示)
        AnimatedVisibility(visible = uiState.isSearchMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
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
                            value = uiState.searchKeyword,
                            onValueChange = {
                                viewModel.updateSearchKeyword(it)
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
                                onSearch = {
                                    if (uiState.searchKeyword.isNotBlank()) {
                                        viewModel.search(uiState.searchKeyword)
                                    }
                                }
                            ),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (uiState.searchKeyword.isEmpty()) {
                                        Text(
                                            text = "搜索歌单名称或关键词...",
                                            fontSize = 13.sp,
                                            color = OrbitTheme.colors.textSecondary.copy(alpha = 0.6f)
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (uiState.searchKeyword.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.updateSearchKeyword("") },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = OrbitTheme.colors.textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (uiState.searchKeyword.isNotBlank()) {
                            viewModel.search(uiState.searchKeyword)
                        }
                    },
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

        // 3. 搜索结果视图
        if (uiState.isSearchMode && uiState.searchKeyword.isNotBlank()) {
            if (uiState.isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = OrbitTheme.colors.primary)
                }
            } else if (uiState.searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("未找到相关歌单", fontSize = 13.sp, color = OrbitTheme.colors.textSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.searchResults, key = { it.id }) { item ->
                        OnlinePlaylistListRow(playlist = item, onClick = { onPlaylistClick(item) })
                    }
                }
            }
            return
        }

        // 4. 错误提示
        if (uiState.errorMessage != null) {
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
                        text = uiState.errorMessage ?: "",
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
                            .clickable {
                                if (uiState.selectedTab == 2) viewModel.loadLeaderboards() else viewModel.loadCurrentPlatformData()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // 5. 分类标签横向滑动条 (仅在 Tab 1 热门分类 下展示)
        if (uiState.selectedTab == 1 && uiState.tags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                uiState.tags.forEach { tag ->
                    val isSelected = tag.id == uiState.selectedTag.id
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
                                viewModel.selectTag(tag)
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = tag.name,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary
                        )
                    }
                }
            }
        }

        // 6. 主体歌单网格与榜单
        if (uiState.isLoading && uiState.playlists.isEmpty() && uiState.leaderboards.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp, color = OrbitTheme.colors.primary)
            }
            return
        }

        val effectiveTab = uiState.selectedTab
        if (effectiveTab == 0 || effectiveTab == 1) {
            // 精选推荐 / 分类歌单 (自适应网格)
            val gridState = rememberLazyGridState()
            val isScrolledToEnd by remember {
                derivedStateOf {
                    val total = gridState.layoutInfo.totalItemsCount
                    val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    total > 0 && lastVisible >= total - 4
                }
            }

            LaunchedEffect(isScrolledToEnd) {
                if (isScrolledToEnd && !uiState.isLoading && !uiState.isPagingLoading && uiState.hasMorePlaylists) {
                    viewModel.loadNextPage()
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 98.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.playlists, key = { "${it.platform.id}_${it.id}" }) { item ->
                    OnlinePlaylistCardItem(playlist = item, onClick = { onPlaylistClick(item) })
                }

                if (uiState.isPagingLoading) {
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
        } else if (effectiveTab == 2) {
            // 官方排行榜
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 98.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.leaderboards, key = { "${it.platform.id}_${it.id}" }) { board ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = OrbitTheme.colors.surfaceCard,
                        border = BorderStroke(0.5.dp, OrbitTheme.colors.surfaceBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPlaylistClick(
                                    OnlinePlaylist(
                                        id = board.id,
                                        platform = board.platform,
                                        title = board.title,
                                        coverUrl = board.coverUrl
                                    )
                                )
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(OrbitTheme.colors.surface)
                            ) {
                                AsyncImage(
                                    model = board.coverUrl,
                                    contentDescription = board.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (!board.updateFrequency.isNullOrEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.55f))
                                            .padding(vertical = 1.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = board.updateFrequency,
                                            color = Color.White,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = board.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OrbitTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                board.topSongsPreview.take(3).forEachIndexed { idx, track ->
                                    Text(
                                        text = "${idx + 1}. $track",
                                        fontSize = 11.sp,
                                        color = OrbitTheme.colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // 我的收藏网络歌单视图
            val context = androidx.compose.ui.platform.LocalContext.current
            val favoriteManager = remember { com.orbit.music.data.online.repository.OnlinePlaylistFavoriteManager.getInstance(context) }
            val allFavorites by favoriteManager.favorites.collectAsState()
            val platformFavorites = remember(allFavorites, platform) {
                allFavorites.filter { it.platform == platform }
            }

            if (platformFavorites.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = OrbitTheme.colors.textSecondary.copy(alpha = 0.45f),
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "暂无收藏的${platform.displayName}歌单",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OrbitTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "在歌单广场浏览歌单并进入详情，点击收藏即可收录至此处",
                            fontSize = 12.sp,
                            color = OrbitTheme.colors.textSecondary.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 98.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(platformFavorites, key = { "fav_${it.platform.id}_${it.id}" }) { item ->
                        OnlinePlaylistCardItem(
                            playlist = item,
                            onClick = { onPlaylistClick(item) },
                            isFavorite = true
                        )
                    }
                }
            }
        }
    }
}

/**
 * 歌单网格卡片
 */
@Composable
private fun OnlinePlaylistCardItem(
    playlist: OnlinePlaylist,
    onClick: () -> Unit,
    isFavorite: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val favoriteManager = remember { com.orbit.music.data.online.repository.OnlinePlaylistFavoriteManager.getInstance(context) }
    val allFavorites by favoriteManager.favorites.collectAsState()
    val itemFav = remember(allFavorites, playlist, isFavorite) {
        if (isFavorite) true else favoriteManager.isFavorite(playlist)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = OrbitTheme.colors.surfaceCard,
        border = BorderStroke(0.5.dp, if (itemFav) Color(0xFFFF3366).copy(alpha = 0.35f) else OrbitTheme.colors.surfaceBorder),
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
                if (playlist.coverUrl.isNotEmpty()) {
                    AsyncImage(
                        model = playlist.coverUrl,
                        contentDescription = playlist.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // 收藏小红心角标
                if (itemFav) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "已收藏",
                            tint = Color(0xFFFF3366),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                // 播放量毛玻璃角标
                if (playlist.playCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = "▶ ${formatCount(playlist.playCount)}",
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = playlist.title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OrbitTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
                if (!playlist.creatorName.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = playlist.creatorName,
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
 * 歌单列表行展示（搜索结果）
 */
@Composable
private fun OnlinePlaylistListRow(
    playlist: OnlinePlaylist,
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
            AsyncImage(
                model = playlist.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = OrbitTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${playlist.creatorName ?: "未知创建者"} · ${formatCount(playlist.playCount)} 次播放",
                    fontSize = 11.sp,
                    color = OrbitTheme.colors.textSecondary
                )
            }
        }
    }
}

/**
 * 歌单下钻详情内容视图（完全适配主库下钻规范，支持播放全部与高亮联动）
 */
@Composable
fun OnlinePlaylistDetailView(
    playlist: OnlinePlaylist,
    songs: List<OnlineSongItem>,
    isLoading: Boolean,
    errorMessage: String?,
    onSongClick: (index: Int, song: OnlineSongItem) -> Unit,
    onPlayAll: () -> Unit = { if (songs.isNotEmpty()) onSongClick(0, songs.first()) },
    onShufflePlay: () -> Unit = { if (songs.isNotEmpty()) onSongClick((0 until songs.size).random(), songs.random()) },
    currentPlayingTitle: String? = null,
    currentPlayingArtist: String? = null,
    isPlaying: Boolean = false,
    locateIndex: Int = -1,
    locateTrigger: Long = 0L,
    isSearching: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isDescExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val filteredSongs = remember(songs, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) songs else songs.filter {
            it.title.contains(q, ignoreCase = true) ||
            it.artist.contains(q, ignoreCase = true) ||
            it.album.contains(q, ignoreCase = true)
        }
    }

    // 监听外部定位请求，平滑滚动至当前正在播放的歌曲
    LaunchedEffect(locateTrigger) {
        if (locateTrigger > 0L && locateIndex >= 0 && locateIndex < filteredSongs.size) {
            listState.animateScrollToItem(locateIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
    ) {
        // 1. 精致歌单详情 Header 卡片
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
                    // 左侧：封面大图
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(OrbitTheme.colors.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        if (playlist.coverUrl.isNotEmpty()) {
                            AsyncImage(
                                model = playlist.coverUrl,
                                contentDescription = playlist.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // 平台标识
                        val platformBadgeColor = when (playlist.platform) {
                            OnlinePlatform.NETEASE -> Color(0xFFE60026)
                            OnlinePlatform.QQ -> Color(0xFF1ECF96)
                            OnlinePlatform.KUGOU -> Color(0xFF0088FF)
                            OnlinePlatform.KUWO -> Color(0xFFFF9500)
                            OnlinePlatform.MIGU -> Color(0xFFE91E63)
                        }
                        val platformBadgeText = when (playlist.platform) {
                            OnlinePlatform.NETEASE -> "网易云"
                            OnlinePlatform.QQ -> "QQ音乐"
                            OnlinePlatform.KUGOU -> "酷狗"
                            OnlinePlatform.KUWO -> "酷我"
                            OnlinePlatform.MIGU -> "咪咕"
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .background(
                                    color = platformBadgeColor,
                                    shape = RoundedCornerShape(3.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = platformBadgeText,
                                color = Color.White,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // 右侧：歌单元数据
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = playlist.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrbitTheme.colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!playlist.creatorName.isNullOrEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!playlist.creatorAvatarUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = playlist.creatorAvatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = playlist.creatorName,
                                    fontSize = 11.5.sp,
                                    color = OrbitTheme.colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Text(
                            text = "▶ ${formatCount(playlist.playCount)} 播放 · 共 ${if (songs.isNotEmpty()) songs.size else playlist.trackCount} 首",
                            fontSize = 11.sp,
                            color = OrbitTheme.colors.textSecondary.copy(alpha = 0.8f)
                        )
                    }
                }

                // 简介折叠展开
                if (!playlist.description.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { isDescExpanded = !isDescExpanded }
                            .padding(2.dp)
                    ) {
                        Text(
                            text = playlist.description,
                            fontSize = 11.sp,
                            color = OrbitTheme.colors.textSecondary.copy(alpha = 0.75f),
                            maxLines = if (isDescExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // 2. 歌单内即时搜索输入框
        AnimatedVisibility(
            visible = isSearching,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = "搜索歌单内歌曲",
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
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
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

        // 3. 播放控制条 (播放全部 / 随机播放 / 收藏歌单)
        val context = androidx.compose.ui.platform.LocalContext.current
        val favoriteManager = remember { com.orbit.music.data.online.repository.OnlinePlaylistFavoriteManager.getInstance(context) }
        val allFavorites by favoriteManager.favorites.collectAsState()
        val isFav = remember(allFavorites, playlist) {
            favoriteManager.isFavorite(playlist)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filteredSongs.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = OrbitTheme.colors.primary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.clickable {
                        if (filteredSongs.isNotEmpty()) onSongClick(0, filteredSongs.first())
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "播放全部",
                            tint = OrbitTheme.colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "播放全部 (${filteredSongs.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrbitTheme.colors.primary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = OrbitTheme.colors.surfaceCard,
                    border = BorderStroke(0.5.dp, OrbitTheme.colors.surfaceBorder),
                    modifier = Modifier.clickable {
                        if (filteredSongs.isNotEmpty()) {
                            val randomIdx = (0 until filteredSongs.size).random()
                            onSongClick(randomIdx, filteredSongs[randomIdx])
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "随机播放",
                            tint = OrbitTheme.colors.textSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "随机",
                            fontSize = 12.sp,
                            color = OrbitTheme.colors.textSecondary
                        )
                    }
                }
            }

            // 收藏/取消收藏网络歌单按钮 (紧邻随机播放按钮)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isFav) Color(0xFFFF3366).copy(alpha = 0.15f) else OrbitTheme.colors.surfaceCard,
                border = BorderStroke(
                    0.5.dp,
                    if (isFav) Color(0xFFFF3366).copy(alpha = 0.5f) else OrbitTheme.colors.surfaceBorder
                ),
                modifier = Modifier.clickable {
                    favoriteManager.toggleFavorite(playlist)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFav) "已收藏歌单" else "收藏歌单",
                        tint = if (isFav) Color(0xFFFF3366) else OrbitTheme.colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isFav) "已收藏" else "收藏",
                        fontSize = 12.sp,
                        fontWeight = if (isFav) FontWeight.Bold else FontWeight.Normal,
                        color = if (isFav) Color(0xFFFF3366) else OrbitTheme.colors.textSecondary
                    )
                }
            }
        }

        // 4. 加载与错误提示
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = OrbitTheme.colors.primary)
            }
        }

        if (errorMessage != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = OrbitTheme.colors.surfaceCard,
                border = BorderStroke(0.5.dp, Color(0xFFF43F5E).copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFF43F5E),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        // 5. 歌曲列表
        if (filteredSongs.isEmpty() && !isLoading && errorMessage == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = OrbitTheme.colors.textSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "未找到与 \"$searchQuery\" 相关的歌曲" else "歌单中暂无歌曲",
                        fontSize = 13.sp,
                        color = OrbitTheme.colors.textSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 98.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(filteredSongs, key = { index, item -> "${item.id}_$index" }) { index, song ->
                    val isCurrent = currentPlayingTitle == song.title &&
                            (currentPlayingArtist == null || currentPlayingArtist == song.artist)

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCurrent) OrbitTheme.colors.primary.copy(alpha = 0.1f) else Color.Transparent,
                        border = if (isCurrent) BorderStroke(0.5.dp, OrbitTheme.colors.primary.copy(alpha = 0.3f)) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSongClick(index, song) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = (index + 1).toString().padStart(2, '0'),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrent) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary.copy(alpha = 0.6f),
                                modifier = Modifier.width(28.dp)
                            )

                            if (!song.coverUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = song.coverUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(OrbitTheme.colors.surface),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = song.title,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isCurrent) OrbitTheme.colors.primary else OrbitTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (song.isVip) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .border(0.5.dp, Color(0xFFF43F5E), RoundedCornerShape(3.dp))
                                            .padding(horizontal = 3.dp, vertical = 0.5.dp)
                                    ) {
                                        Text("VIP", color = Color(0xFFF43F5E), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${song.artist} · ${song.album}",
                                fontSize = 11.sp,
                                color = OrbitTheme.colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isCurrent && isPlaying) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Equalizer,
                                contentDescription = "正在播放",
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (song.durationMs > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formatDuration(song.durationMs),
                                fontSize = 11.sp,
                                color = OrbitTheme.colors.textSecondary.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}
}

/**
 * 导入歌单链接对话框 (遵从 OrbitTheme)
 */
@Composable
private fun ImportPlaylistInlineDialog(
    repository: OnlineMusicRepository,
    onDismiss: () -> Unit,
    onPlaylistResolved: (OnlinePlaylist) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "导入在线歌单",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = OrbitTheme.colors.textPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "支持粘贴网易云音乐或 QQ 音乐的分享链接、口令或歌单 ID：",
                    fontSize = 12.sp,
                    color = OrbitTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = OrbitTheme.colors.surfaceCard,
                    border = BorderStroke(0.5.dp, OrbitTheme.colors.surfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = input,
                        onValueChange = {
                            input = it
                            errorMsg = null
                        },
                        placeholder = {
                            Text("粘贴分享链接...", fontSize = 12.sp, color = OrbitTheme.colors.textSecondary.copy(alpha = 0.6f))
                        },
                        maxLines = 3,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = OrbitTheme.colors.textPrimary,
                            unfocusedTextColor = OrbitTheme.colors.textPrimary
                        )
                    )
                }
                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = errorMsg ?: "", color = Color(0xFFF43F5E), fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = repository.parseLinkOrText(input)
                    if (parsed == null) {
                        errorMsg = "未能识别出歌单 ID，请检查链接"
                    } else {
                        val (platform, id) = parsed
                        onPlaylistResolved(
                            OnlinePlaylist(
                                id = id,
                                platform = platform,
                                title = "正在解析歌单...",
                                coverUrl = ""
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("解析打开", color = if (OrbitTheme.colors.isDark) Color(0xFF101216) else Color.White, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = OrbitTheme.colors.textSecondary, fontSize = 12.sp)
            }
        },
        containerColor = OrbitTheme.colors.surfaceCard,
        shape = RoundedCornerShape(16.dp)
    )
}

private fun formatCount(count: Long): String {
    return when {
        count >= 100_000_000 -> String.format("%.1f亿", count / 100_000_000.0)
        count >= 10_000 -> String.format("%.1f万", count / 10_000.0)
        count > 0 -> count.toString()
        else -> "0"
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
