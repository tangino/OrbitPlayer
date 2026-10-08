package com.orbit.music.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.orbit.music.data.online.model.OnlinePlatform
import com.orbit.music.data.online.model.OnlineSongItem
import com.orbit.music.ui.theme.OrbitTheme
import com.orbit.music.ui.viewmodel.OnlineSearchFilterPlatform
import com.orbit.music.ui.viewmodel.OnlineSongSearchViewModel

/**
 * 各网络平台的专属主题色彩
 */
fun getPlatformBrandColor(platform: OnlinePlatform): Color {
    return when (platform) {
        OnlinePlatform.NETEASE -> Color(0xFFE60026) // 网易云红
        OnlinePlatform.QQ -> Color(0xFF31C27C)      // QQ绿
        OnlinePlatform.KUGOU -> Color(0xFF00A9FF)   // 酷狗蓝
        OnlinePlatform.KUWO -> Color(0xFFFF8F00)    // 酷我金橙
        OnlinePlatform.MIGU -> Color(0xFFE91E63)    // 咪咕粉红
    }
}

/**
 * 全网歌曲搜索全屏主视图（既可作为独立Tab页面，也可嵌入Dialog中）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnlineSongSearchView(
    initialKeyword: String = "",
    showBackButton: Boolean = false,
    onBack: (() -> Unit)? = null,
    onPlaySong: (List<OnlineSongItem>, Int) -> Unit,
    onAddSongToPlaylist: ((OnlineSongItem) -> Unit)? = null,
    onViewAlbum: ((OnlineSongItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: OnlineSongSearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    playerViewModel: com.orbit.music.ui.viewmodel.MusicPlayerViewModel? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    var pendingAddToPlaylistSong by remember { mutableStateOf<OnlineSongItem?>(null) }

    LaunchedEffect(initialKeyword) {
        if (initialKeyword.isNotBlank() && uiState.titleQuery.isBlank() && uiState.artistQuery.isBlank()) {
            viewModel.setTitleQuery(initialKeyword)
            viewModel.performSearch(title = initialKeyword)
        }
    }

    // 添加到歌单弹窗
    if (pendingAddToPlaylistSong != null && playerViewModel != null) {
        AddToPlaylistDialog(
            onlineSong = pendingAddToPlaylistSong,
            viewModel = playerViewModel,
            onDismiss = { pendingAddToPlaylistSong = null }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OrbitTheme.colors.background)
    ) {
        // 1. 顶部标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (showBackButton) 10.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton && onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = OrbitTheme.colors.textPrimary
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = OrbitTheme.colors.primary.copy(alpha = 0.15f),
                    modifier = Modifier
                        .size(34.dp)
                        .padding(end = 4.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.TravelExplore,
                            contentDescription = null,
                            tint = OrbitTheme.colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "全网歌曲搜索",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = OrbitTheme.colors.textPrimary
                )
                Text(
                    text = "支持网易云、QQ、酷狗、酷我、咪咕多源联合检索",
                    fontSize = 11.5.sp,
                    color = OrbitTheme.colors.textSecondary
                )
            }

            // 切换单框 / 独立双输入框（歌名 & 歌手）
            TextButton(
                onClick = { viewModel.toggleDualInputMode() },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (uiState.isDualInputMode) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary
                )
            ) {
                Icon(
                    imageVector = if (uiState.isDualInputMode) Icons.Default.Tune else Icons.Default.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (uiState.isDualInputMode) "精准分栏" else "单框智能",
                    fontSize = 12.sp,
                    fontWeight = if (uiState.isDualInputMode) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        // 2. 搜索输入区域
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            color = OrbitTheme.colors.surfaceCard,
            border = BorderStroke(0.8.dp, OrbitTheme.colors.surfaceBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!uiState.isDualInputMode) {
                    // 模式 A：单输入框（支持输入歌名、歌手或“歌名 歌手”）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = OrbitTheme.colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = uiState.titleQuery,
                            onValueChange = { viewModel.setTitleQuery(it) },
                            placeholder = {
                                Text(
                                    "输入歌名 / 歌手名（如：晴天 或 晴天 周杰伦）",
                                    fontSize = 13.5.sp,
                                    color = OrbitTheme.colors.textSecondary.copy(alpha = 0.6f)
                                )
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = OrbitTheme.colors.textPrimary,
                                unfocusedTextColor = OrbitTheme.colors.textPrimary
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { viewModel.performSearch() }
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (uiState.titleQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.clearSearch() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "清空",
                                    tint = OrbitTheme.colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Button(
                            onClick = { viewModel.performSearch() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                "搜索",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (OrbitTheme.colors.isDark) Color(0xFF101216) else Color.White
                            )
                        }
                    }
                } else {
                    // 模式 B：独立双输入框（同时输入歌名和歌手/用户名）
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 歌名输入行
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "歌名:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OrbitTheme.colors.textPrimary
                            )
                            TextField(
                                value = uiState.titleQuery,
                                onValueChange = { viewModel.setTitleQuery(it) },
                                placeholder = {
                                    Text(
                                        "输入歌曲名称（可选）",
                                        fontSize = 13.sp,
                                        color = OrbitTheme.colors.textSecondary.copy(alpha = 0.6f)
                                    )
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = OrbitTheme.colors.textPrimary,
                                    unfocusedTextColor = OrbitTheme.colors.textPrimary
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Divider(thickness = 0.5.dp, color = OrbitTheme.colors.surfaceBorder)
                        // 歌手/用户名输入行
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "歌手:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OrbitTheme.colors.textPrimary
                            )
                            TextField(
                                value = uiState.artistQuery,
                                onValueChange = { viewModel.setArtistQuery(it) },
                                placeholder = {
                                    Text(
                                        "输入歌手/用户名（可选）",
                                        fontSize = 13.sp,
                                        color = OrbitTheme.colors.textSecondary.copy(alpha = 0.6f)
                                    )
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = OrbitTheme.colors.textPrimary,
                                    unfocusedTextColor = OrbitTheme.colors.textPrimary
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = { viewModel.performSearch() }
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.performSearch() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    "联合搜索",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (OrbitTheme.colors.isDark) Color(0xFF101216) else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. 搜索结果平台筛选胶囊条 (Tab Filter)
        val filterOptions = listOf(
            OnlineSearchFilterPlatform.ALL,
            OnlineSearchFilterPlatform.Single(OnlinePlatform.NETEASE),
            OnlineSearchFilterPlatform.Single(OnlinePlatform.QQ),
            OnlineSearchFilterPlatform.Single(OnlinePlatform.KUGOU),
            OnlineSearchFilterPlatform.Single(OnlinePlatform.KUWO),
            OnlineSearchFilterPlatform.Single(OnlinePlatform.MIGU)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filterOptions.forEach { filter ->
                val isSelected = when (filter) {
                    is OnlineSearchFilterPlatform.ALL -> uiState.selectedPlatformFilter is OnlineSearchFilterPlatform.ALL
                    is OnlineSearchFilterPlatform.Single -> (uiState.selectedPlatformFilter as? OnlineSearchFilterPlatform.Single)?.platform == filter.platform
                }
                val count = when (filter) {
                    is OnlineSearchFilterPlatform.ALL -> uiState.platformResultCounts.values.sum()
                    is OnlineSearchFilterPlatform.Single -> uiState.platformResultCounts[filter.platform] ?: 0
                }

                val brandColor = when (filter) {
                    is OnlineSearchFilterPlatform.ALL -> OrbitTheme.colors.primary
                    is OnlineSearchFilterPlatform.Single -> getPlatformBrandColor(filter.platform)
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) brandColor.copy(alpha = 0.2f) else OrbitTheme.colors.surfaceCard,
                    border = BorderStroke(
                        if (isSelected) 1.2.dp else 0.5.dp,
                        if (isSelected) brandColor else OrbitTheme.colors.surfaceBorder
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { viewModel.selectPlatformFilter(filter) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 平台小圆点
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(brandColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = filter.displayName,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) OrbitTheme.colors.textPrimary else OrbitTheme.colors.textSecondary
                        )
                        if (count > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "($count)",
                                fontSize = 11.sp,
                                color = if (isSelected) brandColor else OrbitTheme.colors.textSecondary.copy(alpha = 0.7f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 4. 搜索中状态
        if (uiState.isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = OrbitTheme.colors.primary,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = "正在全网 5 大平台并行检索匹配歌曲...",
                        fontSize = 13.sp,
                        color = OrbitTheme.colors.textSecondary
                    )
                }
            }
        } else if (uiState.searchResults.isEmpty() && uiState.titleQuery.isBlank() && uiState.artistQuery.isBlank()) {
            // 5. 初始状态：仅在有搜索历史时展示搜索历史
            if (uiState.searchHistory.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "搜索历史",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrbitTheme.colors.textPrimary
                        )
                        TextButton(onClick = { viewModel.clearHistory() }) {
                            Text("清空", fontSize = 11.5.sp, color = OrbitTheme.colors.textSecondary)
                        }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.searchHistory.forEach { hist ->
                            SuggestionChip(
                                onClick = {
                                    viewModel.setTitleQuery(hist)
                                    viewModel.performSearch(title = hist)
                                },
                                label = { Text(hist, fontSize = 12.sp) },
                                shape = RoundedCornerShape(12.dp),
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = OrbitTheme.colors.surfaceCard,
                                    labelColor = OrbitTheme.colors.textPrimary
                                ),
                                border = BorderStroke(0.5.dp, OrbitTheme.colors.surfaceBorder)
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        } else if (uiState.searchResults.isEmpty()) {
            // 6. 无搜索结果空状态
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicOff,
                        contentDescription = null,
                        tint = OrbitTheme.colors.textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "未在对应网络平台检索到匹配歌曲",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = OrbitTheme.colors.textPrimary
                    )
                    Text(
                        text = "请尝试更换搜索关键词或切换到“全部平台”查看",
                        fontSize = 12.sp,
                        color = OrbitTheme.colors.textSecondary
                    )
                }
            }
        } else {
            // 7. 搜索结果列表展示
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // 结果统计与快捷播放操作条
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "共找到 ${uiState.searchResults.size} 首歌曲",
                        fontSize = 12.5.sp,
                        color = OrbitTheme.colors.textSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { onPlaySong(uiState.searchResults, 0) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "播放全部",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = OrbitTheme.colors.primary
                            )
                        }
                        TextButton(
                            onClick = { onPlaySong(uiState.searchResults.shuffled(), 0) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = null,
                                tint = OrbitTheme.colors.textSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "随机播放",
                                fontSize = 12.sp,
                                color = OrbitTheme.colors.textSecondary
                            )
                        }
                    }
                }

                val listState = rememberLazyListState()

                // 监听滚动位置，在接近底部时自动触发加载更多
                LaunchedEffect(listState) {
                    snapshotFlow {
                        val layoutInfo = listState.layoutInfo
                        val totalItems = layoutInfo.totalItemsCount
                        val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        totalItems > 0 && lastVisibleItem >= totalItems - 4
                    }.collect { shouldLoadMore ->
                        if (shouldLoadMore && uiState.hasMore && !uiState.isLoadingMore && !uiState.isSearching) {
                            viewModel.loadMore()
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(
                        items = uiState.searchResults,
                        key = { index, song -> "${song.platform.id}_${song.id}_$index" }
                    ) { index, song ->
                        OnlineSongSearchItemRow(
                            song = song,
                            index = index,
                            onClick = { onPlaySong(uiState.searchResults, index) },
                            onAddPlaylist = {
                                if (onAddSongToPlaylist != null) {
                                    onAddSongToPlaylist(song)
                                } else {
                                    pendingAddToPlaylistSong = song
                                }
                            },
                            onViewAlbum = { onViewAlbum?.invoke(song) }
                        )
                    }

                    // 底部分页载入更多状态条
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.isLoadingMore) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = OrbitTheme.colors.primary,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "正在载入更多歌曲...",
                                        fontSize = 12.5.sp,
                                        color = OrbitTheme.colors.textSecondary
                                    )
                                }
                            } else if (uiState.hasMore) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = OrbitTheme.colors.surfaceCard,
                                    border = BorderStroke(0.6.dp, OrbitTheme.colors.surfaceBorder),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { viewModel.loadMore() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = OrbitTheme.colors.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "点击载入更多歌曲",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = OrbitTheme.colors.primary
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = OrbitTheme.colors.surfaceCard.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "已加载全部匹配歌曲（共 ${uiState.searchResults.size} 首）",
                                        fontSize = 11.5.sp,
                                        color = OrbitTheme.colors.textSecondary.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
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

/**
 * 全网网络平台歌曲搜索全屏对话框
 */
@Composable
fun OnlineSongSearchDialog(
    initialKeyword: String = "",
    onDismiss: () -> Unit,
    onPlaySong: (List<OnlineSongItem>, Int) -> Unit,
    onAddSongToPlaylist: ((OnlineSongItem) -> Unit)? = null,
    onViewAlbum: ((OnlineSongItem) -> Unit)? = null,
    viewModel: OnlineSongSearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    playerViewModel: com.orbit.music.ui.viewmodel.MusicPlayerViewModel? = null
) {
    val dialogBg = if (OrbitTheme.colors.background == Color.Transparent) {
        if (OrbitTheme.colors.isDark) Color(0xFF111318) else Color(0xFFF8FAFC)
    } else {
        OrbitTheme.colors.background
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val isDark = OrbitTheme.colors.isDark
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            var parent = view.parent
            while (parent != null) {
                if (parent is androidx.compose.ui.window.DialogWindowProvider) {
                    val window = parent.window
                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        window.isStatusBarContrastEnforced = false
                        window.isNavigationBarContrastEnforced = false
                    }
                    val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = !isDark
                    insetsController.isAppearanceLightNavigationBars = !isDark
                    break
                }
                parent = parent.parent
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(dialogBg)
        ) {
            OnlineSongSearchView(
                initialKeyword = initialKeyword,
                showBackButton = true,
                onBack = onDismiss,
                onPlaySong = onPlaySong,
                onAddSongToPlaylist = onAddSongToPlaylist,
                onViewAlbum = onViewAlbum,
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
                viewModel = viewModel,
                playerViewModel = playerViewModel
            )
        }
    }
}

/**
 * 搜索结果单曲卡片行
 */
@Composable
fun OnlineSongSearchItemRow(
    song: OnlineSongItem,
    index: Int,
    onClick: () -> Unit,
    onAddPlaylist: () -> Unit,
    onViewAlbum: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val brandColor = getPlatformBrandColor(song.platform)
    var showMoreMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = OrbitTheme.colors.surfaceCard.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 序号
            Text(
                text = "${index + 1}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = OrbitTheme.colors.textSecondary.copy(alpha = 0.6f),
                modifier = Modifier.width(28.dp),
                textAlign = TextAlign.Center
            )

            // 歌曲封面（点击可直接查看专辑）
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(OrbitTheme.colors.surfaceBorder)
                    .clickable {
                        if (onViewAlbum != null) {
                            onViewAlbum()
                        } else {
                            onClick()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!song.coverUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = song.coverUrl,
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = OrbitTheme.colors.textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 歌曲信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OrbitTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (song.isVip) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFFB300).copy(alpha = 0.18f))
                                .border(0.5.dp, Color(0xFFFFB300), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "VIP",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB300)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 平台徽章 Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(brandColor.copy(alpha = 0.15f))
                            .border(0.5.dp, brandColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = song.platform.displayName.replace("音乐", ""),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = brandColor
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    val subtitle = if (song.album.isNotBlank() && song.album != "单曲" && song.album != "热歌") {
                        "${song.artist} · ${song.album}"
                    } else {
                        song.artist
                    }
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = OrbitTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 播放按钮
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircleOutline,
                    contentDescription = "播放",
                    tint = OrbitTheme.colors.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 更多操作按钮
            Box {
                IconButton(
                    onClick = { showMoreMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = OrbitTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("查看专辑", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Album,
                                contentDescription = null,
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            onViewAlbum?.invoke()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("添加到播放列表", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PlaylistAdd,
                                contentDescription = null,
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            onAddPlaylist()
                        }
                    )
                }
            }
        }
    }
}
