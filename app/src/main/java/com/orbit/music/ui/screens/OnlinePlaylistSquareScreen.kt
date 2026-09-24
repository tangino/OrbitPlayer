package com.orbit.music.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.orbit.music.data.online.model.OnlineLeaderboard
import com.orbit.music.data.online.model.OnlinePlatform
import com.orbit.music.data.online.model.OnlinePlaylist
import com.orbit.music.data.online.model.OnlinePlaylistTag
import com.orbit.music.ui.theme.*
import com.orbit.music.ui.viewmodel.OnlinePlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlinePlaylistSquareScreen(
    viewModel: OnlinePlaylistViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // 如果处于歌单详情页，则显示歌单详情界面
    if (uiState.activePlaylist != null) {
        OnlinePlaylistDetailScreen(
            playlist = uiState.activePlaylist!!,
            songs = uiState.activePlaylistSongs,
            isLoading = uiState.isLoadingDetail,
            errorMessage = uiState.detailErrorMessage,
            onBack = { viewModel.closePlaylistDetail() }
        )
        return
    }

    // 导入链接弹窗
    if (uiState.isImportDialogOpen) {
        ImportPlaylistDialog(
            linkText = uiState.importLinkInput,
            onLinkChange = { viewModel.updateImportInput(it) },
            errorMsg = uiState.importError,
            onDismiss = { viewModel.closeImportDialog() },
            onConfirm = { viewModel.importPlaylistLink() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "歌单广场",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        // 音源切换选择胶囊
                        PlatformSwitchSegment(
                            currentPlatform = uiState.currentPlatform,
                            onSwitch = { viewModel.switchPlatform(it) }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    // 导入解析链接
                    IconButton(onClick = { viewModel.openImportDialog() }) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "导入歌单链接",
                            tint = PrimaryNeonCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 搜索栏
            SearchBarSection(
                keyword = uiState.searchKeyword,
                onSearch = { viewModel.search(it) },
                onClear = { viewModel.clearSearch() }
            )

            if (uiState.isSearchMode) {
                // 搜索结果列表
                SearchResultsSection(
                    isSearching = uiState.isSearching,
                    results = uiState.searchResults,
                    onPlaylistClick = { viewModel.openPlaylistDetail(it) }
                )
            } else {
                // 顶部 Tab（精选、分类、榜单）
                val tabs = listOf("精选推荐", "分类广场", "官方榜单")
                TabRow(
                    selectedTabIndex = uiState.selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = PrimaryNeonCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[uiState.selectedTab]),
                            color = PrimaryNeonCyan,
                            height = 3.dp
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = uiState.selectedTab == index,
                            onClick = { viewModel.selectTab(index) },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (uiState.selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 15.sp,
                                    color = if (uiState.selectedTab == index) PrimaryNeonCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }

                // 错误提示
                if (uiState.errorMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uiState.errorMessage ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.loadCurrentPlatformData() },
                                colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                            ) {
                                Text("重试")
                            }
                        }
                    }
                }

                // 主体内容
                when (uiState.selectedTab) {
                    0 -> {
                        // 精选推荐
                        PlaylistGridView(
                            isLoading = uiState.isLoading,
                            playlists = uiState.playlists,
                            isPagingLoading = uiState.isPagingLoading,
                            onLoadMore = { viewModel.loadNextPage() },
                            onPlaylistClick = { viewModel.openPlaylistDetail(it) }
                        )
                    }
                    1 -> {
                        // 分类广场
                        Column(modifier = Modifier.fillMaxSize()) {
                            // 分类标签横向滑动
                            TagsScrollView(
                                tags = uiState.tags,
                                selectedTag = uiState.selectedTag,
                                onTagSelect = { viewModel.selectTag(it) }
                            )
                            PlaylistGridView(
                                isLoading = uiState.isLoading,
                                playlists = uiState.playlists,
                                isPagingLoading = uiState.isPagingLoading,
                                onLoadMore = { viewModel.loadNextPage() },
                                onPlaylistClick = { viewModel.openPlaylistDetail(it) }
                            )
                        }
                    }
                    2 -> {
                        // 官方排行榜
                        LeaderboardsView(
                            isLoading = uiState.isLoading,
                            leaderboards = uiState.leaderboards,
                            onLeaderboardClick = { board ->
                                viewModel.openPlaylistDetail(
                                    OnlinePlaylist(
                                        id = board.id,
                                        platform = board.platform,
                                        title = board.title,
                                        coverUrl = board.coverUrl
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 音源平台快速切换小胶囊 (网易云 / QQ音乐)
 */
@Composable
private fun PlatformSwitchSegment(
    currentPlatform: OnlinePlatform,
    onSwitch: (OnlinePlatform) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isNetease = currentPlatform == OnlinePlatform.NETEASE
        // 网易云
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isNetease) Color(0xFFE60026) else Color.Transparent)
                .clickable { onSwitch(OnlinePlatform.NETEASE) }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "网易云",
                color = if (isNetease) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = if (isNetease) FontWeight.Bold else FontWeight.Normal
            )
        }

        // QQ 音乐
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (!isNetease) Color(0xFF1ECF96) else Color.Transparent)
                .clickable { onSwitch(OnlinePlatform.QQ) }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "QQ音乐",
                color = if (!isNetease) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = if (!isNetease) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/**
 * 搜索输入框
 */
@Composable
private fun SearchBarSection(
    keyword: String,
    onSearch: (String) -> Unit,
    onClear: () -> Unit
) {
    var text by remember(keyword) { mutableStateOf(keyword) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                if (it.isBlank() && keyword.isNotEmpty()) {
                    onClear()
                }
            },
            modifier = Modifier.weight(1f),
            placeholder = { Text("搜索歌单名称或关键词...", fontSize = 14.sp) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingIcon = {
                if (text.isNotEmpty()) {
                    IconButton(onClick = {
                        text = ""
                        onClear()
                    }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "清除")
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryNeonCyan,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = { onSearch(text) },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeonCyan)
        ) {
            Text("搜索", color = Color.White)
        }
    }
}

/**
 * 标签横向滑动条
 */
@Composable
private fun TagsScrollView(
    tags: List<OnlinePlaylistTag>,
    selectedTag: OnlinePlaylistTag,
    onTagSelect: (OnlinePlaylistTag) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tags.forEach { tag ->
            val isSelected = tag.id == selectedTag.id
            FilterChip(
                selected = isSelected,
                onClick = { onTagSelect(tag) },
                label = { Text(tag.name, fontSize = 13.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryNeonCyan.copy(alpha = 0.2f),
                    selectedLabelColor = PrimaryNeonCyan
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = if (isSelected) PrimaryNeonCyan else MaterialTheme.colorScheme.outlineVariant
                )
            )
        }
    }
}

/**
 * 歌单网格卡片瀑布流
 */
@Composable
private fun PlaylistGridView(
    isLoading: Boolean,
    playlists: List<OnlinePlaylist>,
    isPagingLoading: Boolean,
    onLoadMore: () -> Unit,
    onPlaylistClick: (OnlinePlaylist) -> Unit
) {
    val gridState = rememberLazyGridState()

    // 监听滑动到底部触发分页加载
    val isScrolledToEnd by remember {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 4
        }
    }

    LaunchedEffect(isScrolledToEnd) {
        if (isScrolledToEnd) {
            onLoadMore()
        }
    }

    if (isLoading && playlists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PrimaryNeonCyan)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(playlists, key = { "${it.platform.id}_${it.id}" }) { item ->
            OnlinePlaylistCard(
                playlist = item,
                onClick = { onPlaylistClick(item) }
            )
        }

        if (isPagingLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = PrimaryNeonCyan)
                }
            }
        }
    }
}

/**
 * 歌单卡片单项
 */
@Composable
private fun OnlinePlaylistCard(
    playlist: OnlinePlaylist,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column {
            // 封面容器
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (playlist.coverUrl.isNotEmpty()) {
                    AsyncImage(
                        model = playlist.coverUrl,
                        contentDescription = playlist.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // 播放量毛玻璃角标
                if (playlist.playCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "▶ ${formatCount(playlist.playCount)}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 标题与副标题
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                if (!playlist.creatorName.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = playlist.creatorName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 官方排行榜列表
 */
@Composable
private fun LeaderboardsView(
    isLoading: Boolean,
    leaderboards: List<OnlineLeaderboard>,
    onLeaderboardClick: (OnlineLeaderboard) -> Unit
) {
    if (isLoading && leaderboards.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PrimaryNeonCyan)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(leaderboards, key = { "${it.platform.id}_${it.id}" }) { board ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLeaderboardClick(board) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 榜单封面
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
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
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(vertical = 2.dp),
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

                    Spacer(modifier = Modifier.width(14.dp))

                    // 榜单曲目速览
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = board.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        board.topSongsPreview.take(3).forEachIndexed { idx, songStr ->
                            Text(
                                text = "${idx + 1}. $songStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 搜索结果展示
 */
@Composable
private fun SearchResultsSection(
    isSearching: Boolean,
    results: List<OnlinePlaylist>,
    onPlaylistClick: (OnlinePlaylist) -> Unit
) {
    if (isSearching) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PrimaryNeonCyan)
        }
        return
    }

    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("没有搜到相关歌单", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(results, key = { "${it.platform.id}_${it.id}" }) { playlist ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlaylistClick(playlist) },
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = playlist.coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${playlist.creatorName ?: "未知创建者"} · ${formatCount(playlist.playCount)} 次播放",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 导入歌单链接对话框
 */
@Composable
private fun ImportPlaylistDialog(
    linkText: String,
    onLinkChange: (String) -> Unit,
    errorMsg: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "导入歌单链接", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "支持直接粘贴网易云音乐或 QQ 音乐的分享链接、口令或纯歌单 ID：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = linkText,
                    onValueChange = onLinkChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://music.163.com/... 或 https://y.qq.com/...", fontSize = 13.sp) },
                    shape = RoundedCornerShape(8.dp),
                    maxLines = 3,
                    isError = errorMsg != null
                )
                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorMsg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeonCyan)
            ) {
                Text("解析并打开", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
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
