package com.orbit.music.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.orbit.music.R
import com.orbit.music.data.model.Playlist
import com.orbit.music.data.model.Song
import com.orbit.music.data.online.engine.OnlineAudioSourceManager
import com.orbit.music.data.online.model.OnlineSongItem
import com.orbit.music.ui.theme.DarkBackground
import com.orbit.music.ui.theme.OrbitTheme
import com.orbit.music.ui.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.launch

private const val FAVORITE_PLAYLIST_ID = -999L

/**
 * 通用添加到歌单对话框
 * 支持本地歌曲 (Song) 与网络歌曲 (OnlineSongItem) 添加至已有自建歌单或新建网络歌单
 */
@Composable
fun AddToPlaylistDialog(
    song: Song? = null,
    onlineSong: OnlineSongItem? = null,
    songs: List<Song> = emptyList(),
    onlineSongs: List<OnlineSongItem> = emptyList(),
    viewModel: MusicPlayerViewModel,
    onDismiss: () -> Unit,
    onAdded: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val playlists by viewModel.playlists.collectAsState()
    val allGroups by viewModel.playlistGroups.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()

    var selectedGroupFilter by remember { mutableStateOf("全部") }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    // 整合并标准化目标歌曲列表
    val targetSongs = remember(song, onlineSong, songs, onlineSongs) {
        val list = mutableListOf<Song>()
        if (song != null) list.add(song)
        if (onlineSong != null) {
            list.add(OnlineAudioSourceManager.getInstance(context).toSong(onlineSong))
        }
        if (songs.isNotEmpty()) list.addAll(songs)
        if (onlineSongs.isNotEmpty()) {
            list.addAll(OnlineAudioSourceManager.getInstance(context).toSongList(onlineSongs))
        }
        list
    }
    val targetSongCount = targetSongs.size

    // 目标歌曲信息展示
    val isMulti = songs.size > 1 || onlineSongs.size > 1
    val isOnlineTarget = onlineSong != null || onlineSongs.isNotEmpty() || (song != null && song.isOnlineSong)
    val displayTitle = when {
        isMulti -> "共 ${songs.size + onlineSongs.size} 首歌曲"
        onlineSong != null -> onlineSong.title
        song != null -> song.title
        onlineSongs.isNotEmpty() -> onlineSongs.first().title
        songs.isNotEmpty() -> songs.first().title
        else -> "未知歌曲"
    }
    val displayArtist = when {
        isMulti -> "批量添加到歌单"
        onlineSong != null -> onlineSong.artist
        song != null -> song.artist
        onlineSongs.isNotEmpty() -> onlineSongs.first().artist
        songs.isNotEmpty() -> songs.first().artist
        else -> ""
    }
    val displayCover = onlineSong?.coverUrl ?: song?.albumArtUri ?: onlineSongs.firstOrNull()?.coverUrl ?: songs.firstOrNull()?.albumArtUri
    val targetPlatform = onlineSong?.platform ?: song?.resolvedPlatform ?: onlineSongs.firstOrNull()?.platform

    // 本地立即反馈状态：点击添加到红心时瞬间点亮微标，避免数据库异步写入的微小延迟影响视觉反馈
    var locallyMarkedFavorite by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 检测目标歌曲是否已在红心歌单中 (单曲时只要匹配即算；多选时全包含才算)
    val isTargetAlreadyFavorite = remember(targetSongs, favoriteSongs, locallyMarkedFavorite) {
        if (locallyMarkedFavorite) true
        else if (targetSongs.isEmpty()) false
        else {
            val favPaths = favoriteSongs.map { it.path }.toSet()
            val favIds = favoriteSongs.map { it.id }.toSet()
            targetSongs.all { s -> s.id in favIds || s.path in favPaths || s.isFavorite }
        }
    }
    val isTargetFavEffective = isTargetAlreadyFavorite || locallyMarkedFavorite

    // 异步查询目标歌曲所在的自建歌单 ID 列表及各歌单包含该批歌曲的数量
    var containingPlaylistIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var playlistSongCounts by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }

    LaunchedEffect(targetSongs) {
        if (targetSongs.size == 1) {
            val single = targetSongs.first()
            val ids = viewModel.getPlaylistIdsContainingSong(single.id, single.path)
            containingPlaylistIds = ids
            playlistSongCounts = ids.associateWith { 1 }
        } else if (targetSongs.size > 1) {
            val counts = viewModel.getPlaylistSongCounts(targetSongs.map { it.id })
            playlistSongCounts = counts
            containingPlaylistIds = counts.keys
        } else {
            containingPlaylistIds = emptySet()
            playlistSongCounts = emptyMap()
        }
    }

    // 系统红心歌单虚拟实体
    val favoritePlaylist = remember(favoriteSongs.size, locallyMarkedFavorite) {
        Playlist(
            id = FAVORITE_PLAYLIST_ID,
            name = "我喜欢的音乐",
            songCount = if (locallyMarkedFavorite) favoriteSongs.size + 1 else favoriteSongs.size,
            createdAt = 0L,
            groupName = "系统"
        )
    }

    // 执行添加歌曲核心逻辑
    val executeAdd = { targetPlaylist: Playlist ->
        if (targetPlaylist.id == FAVORITE_PLAYLIST_ID) {
            if (isTargetFavEffective) {
                Toast.makeText(context, "歌曲已在「我喜欢的音乐」中", Toast.LENGTH_SHORT).show()
            } else {
                locallyMarkedFavorite = true
                scope.launch {
                    when {
                        onlineSong != null -> {
                            val s = OnlineAudioSourceManager.getInstance(context).toSong(onlineSong)
                            viewModel.setFavorite(s, true)
                            Toast.makeText(context, "已添加「${onlineSong.title}」至我喜欢的音乐", Toast.LENGTH_SHORT).show()
                            onAdded?.invoke("我喜欢的音乐")
                        }
                        song != null -> {
                            viewModel.setFavorite(song, true)
                            Toast.makeText(context, "已添加「${song.title}」至我喜欢的音乐", Toast.LENGTH_SHORT).show()
                            onAdded?.invoke("我喜欢的音乐")
                        }
                        targetSongs.isNotEmpty() -> {
                            viewModel.setFavoriteBatch(targetSongs, true)
                            Toast.makeText(context, "已添加 ${targetSongs.size} 首歌曲至我喜欢的音乐", Toast.LENGTH_SHORT).show()
                            onAdded?.invoke("我喜欢的音乐")
                        }
                        else -> {}
                    }
                    kotlinx.coroutines.delay(420)
                    onDismiss()
                }
            }
        } else {
            when {
                onlineSong != null -> {
                    viewModel.addOnlineSongToPlaylist(targetPlaylist.id, onlineSong) {
                        Toast.makeText(context, "已添加「${onlineSong.title}」至歌单「${targetPlaylist.name}」", Toast.LENGTH_SHORT).show()
                        onAdded?.invoke(targetPlaylist.name)
                        onDismiss()
                    }
                }
                song != null -> {
                    viewModel.addSongToPlaylist(targetPlaylist.id, song) {
                        Toast.makeText(context, "已添加「${song.title}」至歌单「${targetPlaylist.name}」", Toast.LENGTH_SHORT).show()
                        onAdded?.invoke(targetPlaylist.name)
                        onDismiss()
                    }
                }
                onlineSongs.isNotEmpty() -> {
                    viewModel.addOnlineSongsToPlaylist(targetPlaylist.id, onlineSongs) {
                        Toast.makeText(context, "已添加 ${onlineSongs.size} 首网络歌曲至歌单「${targetPlaylist.name}」", Toast.LENGTH_SHORT).show()
                        onAdded?.invoke(targetPlaylist.name)
                        onDismiss()
                    }
                }
                songs.isNotEmpty() -> {
                    viewModel.addSongsToPlaylist(targetPlaylist.id, songs) {
                        Toast.makeText(context, "已添加 ${songs.size} 首歌曲至歌单「${targetPlaylist.name}」", Toast.LENGTH_SHORT).show()
                        onAdded?.invoke(targetPlaylist.name)
                        onDismiss()
                    }
                }
                else -> onDismiss()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = OrbitTheme.colors.surfaceDialog,
            border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.2f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // 1. 顶部标题栏 + 新建歌单快捷入口
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = OrbitTheme.colors.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlaylistAdd,
                                    contentDescription = null,
                                    tint = OrbitTheme.colors.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isOnlineTarget) "添加至网络歌单" else "添加至歌单",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = OrbitTheme.colors.textPrimary
                        )
                    }

                    // ➕ 新建歌单按钮
                    Button(
                        onClick = { showCreatePlaylistDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新建",
                            tint = if (OrbitTheme.colors.isDark) DarkBackground else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isOnlineTarget) "新建网络歌单" else "新建歌单",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (OrbitTheme.colors.isDark) DarkBackground else Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. 待添加歌曲信息横幅预览 (已加红心歌单的在封面与标题上显示红心)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = OrbitTheme.colors.surfaceCard.copy(alpha = 0.6f),
                    border = BorderStroke(0.5.dp, OrbitTheme.colors.primary.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(OrbitTheme.colors.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!displayCover.isNullOrBlank()) {
                                AsyncImage(
                                    model = displayCover,
                                    contentDescription = displayTitle,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = if (isOnlineTarget) Icons.Default.CloudQueue else Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = OrbitTheme.colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // 封面右下角红心微标
                            if (isTargetFavEffective) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(2.dp)
                                        .size(15.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF3366)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = "已是红心歌曲",
                                        tint = Color.White,
                                        modifier = Modifier.size(9.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (targetPlatform != null) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(OrbitTheme.colors.primary.copy(alpha = 0.15f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = targetPlatform.displayName.replace("音乐", ""),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = OrbitTheme.colors.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = displayTitle,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    color = OrbitTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (isTargetFavEffective) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFFF3366).copy(alpha = 0.15f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = null,
                                                tint = Color(0xFFFF3366),
                                                modifier = Modifier.size(9.dp)
                                            )
                                            Text(
                                                text = "已喜欢",
                                                color = Color(0xFFFF3366),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            if (displayArtist.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = displayArtist,
                                    fontSize = 11.5.sp,
                                    color = OrbitTheme.colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. 分组过滤选择 Chips
                val filterList = remember(allGroups) {
                    val list = mutableListOf("全部", "系统")
                    list.addAll(allGroups)
                    list.distinct()
                }

                if (filterList.size > 2) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filterList.forEach { grp ->
                            val isSel = selectedGroupFilter == grp
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSel) OrbitTheme.colors.primary.copy(alpha = 0.2f) else OrbitTheme.colors.surfaceCard,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSel) OrbitTheme.colors.primary else OrbitTheme.colors.primary.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.clickable { selectedGroupFilter = grp }
                            ) {
                                Text(
                                    text = grp,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 4. 歌单列表 (包含系统红心歌单及自建歌单；已存在的变灰不能添加)
                val filteredPlaylists = remember(playlists, selectedGroupFilter, favoritePlaylist) {
                    when (selectedGroupFilter) {
                        "全部" -> listOf(favoritePlaylist) + playlists
                        "系统" -> listOf(favoritePlaylist)
                        else -> playlists.filter { it.groupName == selectedGroupFilter }
                    }
                }

                if (filteredPlaylists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.PlaylistRemove,
                                contentDescription = null,
                                tint = OrbitTheme.colors.textSecondary.copy(alpha = 0.4f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (playlists.isEmpty()) "暂无歌单，请点击右上角新建" else "该分组下暂无歌单",
                                fontSize = 12.5.sp,
                                color = OrbitTheme.colors.textSecondary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredPlaylists, key = { it.id }) { playlist ->
                            val isFavPlaylist = playlist.id == FAVORITE_PLAYLIST_ID
                            val isAlreadyInPlaylist = if (isFavPlaylist) {
                                isTargetFavEffective
                            } else if (isMulti) {
                                (playlistSongCounts[playlist.id] ?: 0) >= targetSongCount
                            } else {
                                containingPlaylistIds.contains(playlist.id)
                            }
                            val partialCount = if (!isFavPlaylist && isMulti) (playlistSongCounts[playlist.id] ?: 0) else 0

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isAlreadyInPlaylist) {
                                    OrbitTheme.colors.surfaceCard.copy(alpha = 0.35f)
                                } else if (isFavPlaylist) {
                                    Color(0xFFFF3366).copy(alpha = 0.08f)
                                } else {
                                    OrbitTheme.colors.surfaceCard
                                },
                                border = BorderStroke(
                                    if (isAlreadyInPlaylist) 0.5.dp else if (isFavPlaylist) 1.dp else 0.5.dp,
                                    if (isAlreadyInPlaylist) OrbitTheme.colors.textSecondary.copy(alpha = 0.15f)
                                    else if (isFavPlaylist) Color(0xFFFF3366).copy(alpha = 0.3f)
                                    else OrbitTheme.colors.primary.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isAlreadyInPlaylist) {
                                            val hint = if (isFavPlaylist) "歌曲已在「我喜欢的音乐」中" else "歌单「${playlist.name}」已包含该歌曲"
                                            Toast.makeText(context, hint, Toast.LENGTH_SHORT).show()
                                        } else {
                                            executeAdd(playlist)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 歌单左侧图标
                                    if (isFavPlaylist) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (isAlreadyInPlaylist) Color(0xFFFF3366).copy(alpha = 0.1f)
                                                    else Color(0xFFFF3366).copy(alpha = 0.2f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = null,
                                                tint = if (isAlreadyInPlaylist) Color(0xFFFF3366).copy(alpha = 0.35f) else Color(0xFFFF3366),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.QueueMusic,
                                            contentDescription = null,
                                            tint = if (isAlreadyInPlaylist) OrbitTheme.colors.textSecondary.copy(alpha = 0.35f) else OrbitTheme.colors.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = playlist.name,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isAlreadyInPlaylist) {
                                                    OrbitTheme.colors.textSecondary.copy(alpha = 0.5f)
                                                } else {
                                                    OrbitTheme.colors.textPrimary
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Spacer(modifier = Modifier.width(6.dp))

                                            if (isFavPlaylist) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (isAlreadyInPlaylist) Color.Gray.copy(alpha = 0.15f) else Color(0xFFFF3366).copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = if (isAlreadyInPlaylist) "已在红心歌单" else "系统红心",
                                                        color = if (isAlreadyInPlaylist) OrbitTheme.colors.textSecondary.copy(alpha = 0.6f) else Color(0xFFFF3366),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            } else {
                                                // 智能类型 Badge (网络 / 混合 / 本地)
                                                val typeColor = when (playlist.type) {
                                                    com.orbit.music.data.model.PlaylistType.ONLINE -> Color(0xFF00E5FF)
                                                    com.orbit.music.data.model.PlaylistType.HYBRID -> Color(0xFFFF9100)
                                                    com.orbit.music.data.model.PlaylistType.LOCAL -> Color(0xFF3B82F6)
                                                }
                                                val typeText = when (playlist.type) {
                                                    com.orbit.music.data.model.PlaylistType.ONLINE -> "网络"
                                                    com.orbit.music.data.model.PlaylistType.HYBRID -> "混合"
                                                    com.orbit.music.data.model.PlaylistType.LOCAL -> "本地"
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (isAlreadyInPlaylist) OrbitTheme.colors.textSecondary.copy(alpha = 0.08f) else typeColor.copy(alpha = 0.12f),
                                                    border = BorderStroke(0.5.dp, if (isAlreadyInPlaylist) Color.Transparent else typeColor.copy(alpha = 0.4f))
                                                ) {
                                                    Text(
                                                        text = typeText,
                                                        color = if (isAlreadyInPlaylist) OrbitTheme.colors.textSecondary.copy(alpha = 0.5f) else typeColor,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }

                                                if (playlist.groupName.isNotBlank() && playlist.groupName != "默认") {
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(
                                                                if (isAlreadyInPlaylist) OrbitTheme.colors.textSecondary.copy(alpha = 0.08f)
                                                                else OrbitTheme.colors.primary.copy(alpha = 0.12f)
                                                            )
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            text = playlist.groupName,
                                                            fontSize = 9.sp,
                                                            color = if (isAlreadyInPlaylist) OrbitTheme.colors.textSecondary.copy(alpha = 0.5f) else OrbitTheme.colors.primary
                                                        )
                                                    }
                                                }

                                                if (isAlreadyInPlaylist) {
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = OrbitTheme.colors.textSecondary.copy(alpha = 0.12f)
                                                    ) {
                                                        Text(
                                                            text = if (isMulti) "已全部包含" else "已包含此歌曲",
                                                            color = OrbitTheme.colors.textSecondary.copy(alpha = 0.65f),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))
                                        val countDesc = if (isFavPlaylist) {
                                            "${playlist.songCount} 首红心曲目"
                                        } else {
                                            when (playlist.type) {
                                                com.orbit.music.data.model.PlaylistType.HYBRID -> "${playlist.songCount} 首 (本地 ${playlist.localSongCount} · 网络 ${playlist.onlineSongCount})"
                                                com.orbit.music.data.model.PlaylistType.ONLINE -> "${playlist.songCount} 首网络歌曲"
                                                com.orbit.music.data.model.PlaylistType.LOCAL -> "${playlist.songCount} 首本地歌曲"
                                            }
                                        }
                                        val extraHint = if (!isAlreadyInPlaylist && partialCount > 0) " (已包含 $partialCount 首，可追加)" else ""
                                        Text(
                                            text = "$countDesc$extraHint",
                                            fontSize = 11.sp,
                                            color = if (isAlreadyInPlaylist) OrbitTheme.colors.textSecondary.copy(alpha = 0.45f) else OrbitTheme.colors.textSecondary
                                        )
                                    }

                                    // 右侧状态图标：已存在的显示打勾禁用图标，未添加的显示添加图标
                                    if (isAlreadyInPlaylist) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "已存在此歌单",
                                            tint = OrbitTheme.colors.textSecondary.copy(alpha = 0.35f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.AddCircleOutline,
                                            contentDescription = "添加到此歌单",
                                            tint = if (isFavPlaylist) Color(0xFFFF3366) else OrbitTheme.colors.primary.copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. 底部取消按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.btn_cancel),
                            color = OrbitTheme.colors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    // 嵌套弹窗：新建歌单 / 新建网络歌单
    if (showCreatePlaylistDialog) {
        var playlistNameInput by remember { mutableStateOf("") }
        var selectedGroupForNew by remember {
            mutableStateOf(if (isOnlineTarget) "网络列表" else "默认")
        }
        var isCustomGroupInput by remember { mutableStateOf(false) }
        var customGroupNameInput by remember { mutableStateOf("") }

        val defaultGroupCandidates = remember(allGroups, isOnlineTarget) {
            val list = mutableListOf("默认", "网络列表", "网络歌单", "车载精选")
            list.addAll(allGroups)
            list.distinct()
        }

        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = {
                Text(
                    text = if (isOnlineTarget) "新建网络歌单" else "新建歌单",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = OrbitTheme.colors.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = playlistNameInput,
                        onValueChange = { playlistNameInput = it },
                        label = { Text("歌单名称", color = OrbitTheme.colors.textSecondary) },
                        placeholder = { Text(if (isOnlineTarget) "例如：我的云端收藏" else "例如：流行热歌", fontSize = 12.sp) },
                        singleLine = true,
                        trailingIcon = {
                            if (playlistNameInput.isNotEmpty()) {
                                IconButton(onClick = { playlistNameInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "清空", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "选择所属分组：",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = OrbitTheme.colors.textSecondary
                    )

                    // 快捷分组列表
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        defaultGroupCandidates.forEach { g ->
                            val isSel = !isCustomGroupInput && selectedGroupForNew == g
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSel) OrbitTheme.colors.primary else OrbitTheme.colors.surfaceCard,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSel) OrbitTheme.colors.primary else OrbitTheme.colors.primary.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.clickable {
                                    isCustomGroupInput = false
                                    selectedGroupForNew = g
                                }
                            ) {
                                Text(
                                    text = g,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) {
                                        if (OrbitTheme.colors.isDark) DarkBackground else Color.White
                                    } else OrbitTheme.colors.textPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // 自定义分组按钮
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCustomGroupInput) OrbitTheme.colors.primary else OrbitTheme.colors.surfaceCard,
                            border = BorderStroke(
                                1.dp,
                                if (isCustomGroupInput) OrbitTheme.colors.primary else OrbitTheme.colors.primary.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.clickable { isCustomGroupInput = true }
                        ) {
                            Text(
                                text = "＋ 自定义",
                                fontSize = 11.5.sp,
                                fontWeight = if (isCustomGroupInput) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCustomGroupInput) {
                                    if (OrbitTheme.colors.isDark) DarkBackground else Color.White
                                } else OrbitTheme.colors.textPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    if (isCustomGroupInput) {
                        OutlinedTextField(
                            value = customGroupNameInput,
                            onValueChange = { customGroupNameInput = it },
                            label = { Text("输入新分组名称", color = OrbitTheme.colors.textSecondary) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = playlistNameInput.trim()
                        if (name.isNotBlank()) {
                            val finalGroup = if (isCustomGroupInput) {
                                customGroupNameInput.trim().ifBlank { "网络列表" }
                            } else {
                                selectedGroupForNew
                            }
                            viewModel.createPlaylist(name, finalGroup) { newId ->
                                val newlyCreated = Playlist(
                                    id = newId,
                                    name = name,
                                    songCount = 0,
                                    createdAt = System.currentTimeMillis(),
                                    groupName = finalGroup
                                )
                                executeAdd(newlyCreated)
                            }
                            showCreatePlaylistDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary)
                ) {
                    Text("创建并添加", color = if (OrbitTheme.colors.isDark) DarkBackground else Color.White)
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
}
