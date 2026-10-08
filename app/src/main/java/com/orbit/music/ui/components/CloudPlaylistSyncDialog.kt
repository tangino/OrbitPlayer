package com.orbit.music.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.orbit.music.data.model.Playlist
import com.orbit.music.data.online.engine.CloudLocalPlaylist
import com.orbit.music.data.online.engine.CloudPlaylistBackupData
import com.orbit.music.data.online.engine.CloudPlaylistSyncManager
import com.orbit.music.data.online.engine.CloudSourceSyncManager
import com.orbit.music.data.online.engine.PlaylistSyncMode
import com.orbit.music.data.online.model.OnlinePlaylist
import com.orbit.music.data.online.repository.OnlinePlaylistFavoriteManager
import com.orbit.music.data.playlist.PlaylistGroupManager
import com.orbit.music.ui.theme.DarkBackground
import com.orbit.music.ui.theme.OrbitTheme
import com.orbit.music.utils.FastToast
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private enum class SyncTab(val title: String) {
    UPLOAD("⬆️ 上传备份"),
    DOWNLOAD("⬇️ 从云端下载")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudPlaylistSyncDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSyncCompleted: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authManager = remember { CloudSourceSyncManager.getInstance(context) }
    val syncManager = remember { CloudPlaylistSyncManager.getInstance(context) }
    val groupManager = remember { PlaylistGroupManager.getInstance(context) }
    val onlineFavoriteManager = remember { OnlinePlaylistFavoriteManager.getInstance(context) }

    val allGroups by groupManager.groups.collectAsState()
    val onlineFavorites by onlineFavoriteManager.favorites.collectAsState()

    var currentTab by remember { mutableStateOf(SyncTab.UPLOAD) }

    // 账号登录状态
    var username by remember { mutableStateOf(authManager.getUsername() ?: "") }
    var password by remember { mutableStateOf("") }
    var isAuthExpanded by remember { mutableStateOf(!authManager.isLoggedIn()) }
    var isLoggingIn by remember { mutableStateOf(false) }

    // 同步模式状态 (增量合并 / 全量覆盖)
    var uploadSyncMode by remember { mutableStateOf(PlaylistSyncMode.MERGE) }
    var downloadSyncMode by remember { mutableStateOf(PlaylistSyncMode.MERGE) }

    // 上传状态：选中的本地歌单 ID 集合与选中的在线歌单 ID 集合
    val selectedLocalUploadIds = remember { mutableStateListOf<Long>() }
    val selectedOnlineUploadIds = remember { mutableStateListOf<String>() }

    // 初始默认全选所有歌单
    LaunchedEffect(playlists, onlineFavorites) {
        if (selectedLocalUploadIds.isEmpty() && playlists.isNotEmpty()) {
            selectedLocalUploadIds.addAll(playlists.map { it.id })
        }
        if (selectedOnlineUploadIds.isEmpty() && onlineFavorites.isNotEmpty()) {
            selectedOnlineUploadIds.addAll(onlineFavorites.map { "${it.platform.name}_${it.id}" })
        }
    }

    var isUploading by remember { mutableStateOf(false) }

    // 下载状态：从云端拉取的数据
    var cloudBackupData by remember { mutableStateOf<CloudPlaylistBackupData?>(null) }
    var isLoadingCloudData by remember { mutableStateOf(false) }
    var cloudDataError by remember { mutableStateOf<String?>(null) }

    val selectedLocalDownloadIndices = remember { mutableStateListOf<Int>() }
    val selectedOnlineDownloadIndices = remember { mutableStateListOf<Int>() }
    var isRestoring by remember { mutableStateOf(false) }
    var isDeletingCloudPlaylists by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var showClearAllCloudDialog by remember { mutableStateOf(false) }

    // 自动拉取云端数据
    val loadCloudData: () -> Unit = {
        if (authManager.isLoggedIn()) {
            isLoadingCloudData = true
            cloudDataError = null
            coroutineScope.launch {
                val res = syncManager.fetchCloudPlaylists()
                res.onSuccess { data ->
                    cloudBackupData = data
                    selectedLocalDownloadIndices.clear()
                    selectedOnlineDownloadIndices.clear()
                    data.localPlaylists.indices.forEach { selectedLocalDownloadIndices.add(it) }
                    data.onlinePlaylists.indices.forEach { selectedOnlineDownloadIndices.add(it) }
                }.onFailure { err ->
                    cloudDataError = err.localizedMessage ?: "拉取云端歌单失败"
                }
                isLoadingCloudData = false
            }
        }
    }

    LaunchedEffect(currentTab) {
        if (currentTab == SyncTab.DOWNLOAD && cloudBackupData == null && authManager.isLoggedIn()) {
            loadCloudData()
        }
    }

    // 确认删除选中云端歌单弹窗
    if (showDeleteSelectedDialog) {
        val data = cloudBackupData
        val totalSelected = selectedLocalDownloadIndices.size + selectedOnlineDownloadIndices.size
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                    Text("确认从云端删除所选歌单？", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OrbitTheme.colors.textPrimary)
                }
            },
            text = {
                Text(
                    text = "即将从云端账号 [${authManager.getUsername()}] 中永久删除勾选的 $totalSelected 个歌单。\n本地设备上的歌单不会受到影响。",
                    fontSize = 12.5.sp,
                    color = OrbitTheme.colors.textSecondary,
                    lineHeight = 17.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteSelectedDialog = false
                        if (data == null) return@Button
                        val localNames = selectedLocalDownloadIndices.mapNotNull { data.localPlaylists.getOrNull(it)?.name }
                        val onlineIds = selectedOnlineDownloadIndices.mapNotNull { data.onlinePlaylists.getOrNull(it)?.id }

                        isDeletingCloudPlaylists = true
                        coroutineScope.launch {
                            val res = syncManager.deleteSelectedCloudPlaylists(localNames, onlineIds)
                            res.onSuccess { msg ->
                                FastToast.show(context, msg)
                                loadCloudData()
                            }.onFailure { err ->
                                FastToast.show(context, "删除失败: ${err.localizedMessage}")
                            }
                            isDeletingCloudPlaylists = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("确认删除", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text("取消", fontSize = 12.sp, color = OrbitTheme.colors.textSecondary)
                }
            },
            containerColor = OrbitTheme.colors.surfaceDialog,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 确认清空所有云端歌单弹窗
    if (showClearAllCloudDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllCloudDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                    Text("确认清空所有云端歌单？", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OrbitTheme.colors.textPrimary)
                }
            },
            text = {
                Text(
                    text = "此操作将彻底清空当前账号 [${authManager.getUsername()}] 在云端存储的所有歌单备份！\n本地现有歌单不受影响。",
                    fontSize = 12.5.sp,
                    color = OrbitTheme.colors.textSecondary,
                    lineHeight = 17.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllCloudDialog = false
                        isDeletingCloudPlaylists = true
                        coroutineScope.launch {
                            val res = syncManager.clearAllCloudPlaylists()
                            res.onSuccess { msg ->
                                FastToast.show(context, msg)
                                loadCloudData()
                            }.onFailure { err ->
                                FastToast.show(context, "清空失败: ${err.localizedMessage}")
                            }
                            isDeletingCloudPlaylists = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("彻底清空", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllCloudDialog = false }) {
                    Text("取消", fontSize = 12.sp, color = OrbitTheme.colors.textSecondary)
                }
            },
            containerColor = OrbitTheme.colors.surfaceDialog,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = OrbitTheme.colors.surfaceDialog,
            border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.25f)),
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. 顶部 Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(OrbitTheme.colors.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "歌单多端云同步",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = OrbitTheme.colors.textPrimary
                            )
                            val currentUser = authManager.getUsername()
                            Text(
                                text = if (!currentUser.isNullOrBlank()) "账号: $currentUser" else "未登录云同步账号",
                                fontSize = 12.sp,
                                color = if (!currentUser.isNullOrBlank()) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = OrbitTheme.colors.textSecondary
                        )
                    }
                }

                // 2. 账号快捷切换/登录栏
                if (!authManager.isLoggedIn() || isAuthExpanded) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = OrbitTheme.colors.surfaceCard,
                        border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "🔑 登录云同步账号（手机与车机输入相同账号即可互通）",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OrbitTheme.colors.textPrimary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text("账号", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("密码", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        if (username.trim().isNotBlank() && password.trim().isNotBlank()) {
                                            isLoggingIn = true
                                            coroutineScope.launch {
                                                val res = authManager.loginOrRegister(username.trim(), password.trim())
                                                res.onSuccess {
                                                    FastToast.show(context, "账号登录成功")
                                                    isAuthExpanded = false
                                                    if (currentTab == SyncTab.DOWNLOAD) {
                                                        loadCloudData()
                                                    }
                                                }.onFailure {
                                                    FastToast.show(context, "登录失败: ${it.localizedMessage}")
                                                }
                                                isLoggingIn = false
                                            }
                                        } else {
                                            FastToast.show(context, "请输入账号和密码")
                                        }
                                    },
                                    enabled = !isLoggingIn,
                                    colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (isLoggingIn) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("登录 / 自动注册", fontSize = 12.sp, color = if (OrbitTheme.colors.isDark) DarkBackground else Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Tab 切换 (上传 / 下载)
                TabRow(
                    selectedTabIndex = currentTab.ordinal,
                    containerColor = OrbitTheme.colors.surfaceCard,
                    contentColor = OrbitTheme.colors.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp))
                ) {
                    SyncTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        Tab(
                            selected = isSelected,
                            onClick = { currentTab = tab },
                            text = {
                                Text(
                                    text = tab.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.5.sp
                                )
                            }
                        )
                    }
                }

                // 4. Tab 内容区域
                Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    when (currentTab) {
                        SyncTab.UPLOAD -> {
                            // 上传面板
                            val totalLocal = playlists.size
                            val totalOnline = onlineFavorites.size
                            val isAllSelected = (selectedLocalUploadIds.size == totalLocal) && (selectedOnlineUploadIds.size == totalOnline)

                            Column(modifier = Modifier.fillMaxSize()) {
                                // 工具栏：全选与计数
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "请勾选需要备份到云端的歌单：",
                                        fontSize = 12.5.sp,
                                        color = OrbitTheme.colors.textSecondary
                                    )
                                    TextButton(
                                        onClick = {
                                            if (isAllSelected) {
                                                selectedLocalUploadIds.clear()
                                                selectedOnlineUploadIds.clear()
                                            } else {
                                                selectedLocalUploadIds.clear()
                                                selectedLocalUploadIds.addAll(playlists.map { it.id })
                                                selectedOnlineUploadIds.clear()
                                                selectedOnlineUploadIds.addAll(onlineFavorites.map { "${it.platform.name}_${it.id}" })
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = if (isAllSelected) "取消全选" else "全选",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = OrbitTheme.colors.primary
                                        )
                                    }
                                }

                                if (playlists.isEmpty() && onlineFavorites.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("本机暂无自建或收藏歌单", color = OrbitTheme.colors.textSecondary, fontSize = 13.sp)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(bottom = 12.dp)
                                    ) {
                                        // 本地歌单列表
                                        if (playlists.isNotEmpty()) {
                                            item {
                                                Text(
                                                    text = "📁 本地自建歌单 (${playlists.size})",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = OrbitTheme.colors.primary,
                                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                                )
                                            }
                                            items(playlists, key = { "local_up_${it.id}" }) { p ->
                                                val isChecked = selectedLocalUploadIds.contains(p.id)
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isChecked) OrbitTheme.colors.primary.copy(alpha = 0.12f) else OrbitTheme.colors.surfaceCard,
                                                    border = BorderStroke(1.dp, if (isChecked) OrbitTheme.colors.primary.copy(alpha = 0.4f) else OrbitTheme.colors.primary.copy(alpha = 0.1f)),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            if (isChecked) selectedLocalUploadIds.remove(p.id) else selectedLocalUploadIds.add(p.id)
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Checkbox(
                                                            checked = isChecked,
                                                            onCheckedChange = {
                                                                if (it) selectedLocalUploadIds.add(p.id) else selectedLocalUploadIds.remove(p.id)
                                                            },
                                                            colors = CheckboxDefaults.colors(checkedColor = OrbitTheme.colors.primary)
                                                        )
                                                        Box(
                                                            modifier = Modifier
                                                                .size(42.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(OrbitTheme.colors.surface),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (!p.coverArtUri.isNullOrBlank()) {
                                                                AsyncImage(
                                                                    model = p.coverArtUri,
                                                                    contentDescription = null,
                                                                    contentScale = ContentScale.Crop,
                                                                    modifier = Modifier.fillMaxSize()
                                                                )
                                                            } else {
                                                                Icon(
                                                                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                                                    contentDescription = null,
                                                                    tint = OrbitTheme.colors.primary,
                                                                    modifier = Modifier.size(24.dp)
                                                                )
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = p.name,
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = OrbitTheme.colors.textPrimary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                if (p.groupName.isNotBlank() && p.groupName != "默认") {
                                                                    Surface(
                                                                        shape = RoundedCornerShape(4.dp),
                                                                        color = OrbitTheme.colors.primary.copy(alpha = 0.15f)
                                                                    ) {
                                                                        Text(
                                                                            text = p.groupName,
                                                                            color = OrbitTheme.colors.primary,
                                                                            fontSize = 9.sp,
                                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                        )
                                                                    }
                                                                }
                                                                Text(
                                                                    text = "${p.songCount} 首歌曲",
                                                                    fontSize = 11.5.sp,
                                                                    color = OrbitTheme.colors.textSecondary
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 在线收藏歌单列表
                                        if (onlineFavorites.isNotEmpty()) {
                                            item {
                                                Text(
                                                    text = "🌐 在线收藏歌单 (${onlineFavorites.size})",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = OrbitTheme.colors.primary,
                                                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                                                )
                                            }
                                            items(onlineFavorites, key = { "online_up_${it.platform.name}_${it.id}" }) { op ->
                                                val opKey = "${op.platform.name}_${op.id}"
                                                val isChecked = selectedOnlineUploadIds.contains(opKey)
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isChecked) OrbitTheme.colors.primary.copy(alpha = 0.12f) else OrbitTheme.colors.surfaceCard,
                                                    border = BorderStroke(1.dp, if (isChecked) OrbitTheme.colors.primary.copy(alpha = 0.4f) else OrbitTheme.colors.primary.copy(alpha = 0.1f)),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            if (isChecked) selectedOnlineUploadIds.remove(opKey) else selectedOnlineUploadIds.add(opKey)
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Checkbox(
                                                            checked = isChecked,
                                                            onCheckedChange = {
                                                                if (it) selectedOnlineUploadIds.add(opKey) else selectedOnlineUploadIds.remove(opKey)
                                                            },
                                                            colors = CheckboxDefaults.colors(checkedColor = OrbitTheme.colors.primary)
                                                        )
                                                        Box(
                                                            modifier = Modifier
                                                                .size(42.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(OrbitTheme.colors.surface),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (op.coverUrl.isNotBlank()) {
                                                                AsyncImage(
                                                                    model = op.coverUrl,
                                                                    contentDescription = null,
                                                                    contentScale = ContentScale.Crop,
                                                                    modifier = Modifier.fillMaxSize()
                                                                )
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = op.title,
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = OrbitTheme.colors.textPrimary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Surface(
                                                                    shape = RoundedCornerShape(4.dp),
                                                                    color = OrbitTheme.colors.primary.copy(alpha = 0.15f)
                                                                ) {
                                                                    Text(
                                                                        text = op.platform.displayName,
                                                                        color = OrbitTheme.colors.primary,
                                                                        fontSize = 9.sp,
                                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                    )
                                                                }
                                                                if (op.customGroup.isNotBlank() && op.customGroup != "默认") {
                                                                    Surface(
                                                                        shape = RoundedCornerShape(4.dp),
                                                                        color = OrbitTheme.colors.primary.copy(alpha = 0.15f)
                                                                    ) {
                                                                        Text(
                                                                            text = op.customGroup,
                                                                            color = OrbitTheme.colors.primary,
                                                                            fontSize = 9.sp,
                                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                        )
                                                                    }
                                                                }
                                                                Text(
                                                                    text = "${op.trackCount} 首",
                                                                    fontSize = 11.5.sp,
                                                                    color = OrbitTheme.colors.textSecondary
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 上传同步模式选择 (增量合并 / 全量覆盖)
                                    SyncModeSelector(
                                        selectedMode = uploadSyncMode,
                                        onModeSelect = { uploadSyncMode = it },
                                        isUpload = true,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }
                            }
                        }

                        SyncTab.DOWNLOAD -> {
                            // 下载恢复面板
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val timeStr = cloudBackupData?.let {
                                        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                                        sdf.format(Date(it.timestamp))
                                    } ?: ""
                                    Text(
                                        text = if (timeStr.isNotBlank()) "备份时间: $timeStr" else "请选择云端歌单：",
                                        fontSize = 11.5.sp,
                                        color = OrbitTheme.colors.textSecondary
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val curData = cloudBackupData
                                        val hasData = curData != null && (curData.localPlaylists.isNotEmpty() || curData.onlinePlaylists.isNotEmpty())
                                        val totalSelected = selectedLocalDownloadIndices.size + selectedOnlineDownloadIndices.size

                                        if (curData != null && hasData) {
                                            // 全选/反选
                                            TextButton(
                                                onClick = {
                                                    val allLocalCount = curData.localPlaylists.size
                                                    val allOnlineCount = curData.onlinePlaylists.size
                                                    if (selectedLocalDownloadIndices.size == allLocalCount && selectedOnlineDownloadIndices.size == allOnlineCount) {
                                                        selectedLocalDownloadIndices.clear()
                                                        selectedOnlineDownloadIndices.clear()
                                                    } else {
                                                        selectedLocalDownloadIndices.clear()
                                                        selectedOnlineDownloadIndices.clear()
                                                        curData.localPlaylists.indices.forEach { selectedLocalDownloadIndices.add(it) }
                                                        curData.onlinePlaylists.indices.forEach { selectedOnlineDownloadIndices.add(it) }
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                val allSelected = selectedLocalDownloadIndices.size == curData.localPlaylists.size && selectedOnlineDownloadIndices.size == curData.onlinePlaylists.size
                                                Text(if (allSelected) "取消全选" else "全选", fontSize = 11.sp, color = OrbitTheme.colors.primary)
                                            }

                                            // 删除勾选的云端歌单
                                            if (totalSelected > 0) {
                                                TextButton(
                                                    onClick = { showDeleteSelectedDialog = true },
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                                    modifier = Modifier.height(28.dp),
                                                    enabled = !isDeletingCloudPlaylists && !isRestoring
                                                ) {
                                                    Text("🗑️ 删除已选 ($totalSelected)", fontSize = 11.sp, color = Color(0xFFFF5252))
                                                }
                                            }

                                            // 清空全部云端歌单备份
                                            TextButton(
                                                onClick = { showClearAllCloudDialog = true },
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                                modifier = Modifier.height(28.dp),
                                                enabled = !isDeletingCloudPlaylists && !isRestoring
                                            ) {
                                                Text("⚠️ 清空云端", fontSize = 11.sp, color = Color(0xFFFF5252).copy(alpha = 0.8f))
                                            }
                                        }

                                        IconButton(
                                            onClick = loadCloudData,
                                            modifier = Modifier.size(28.dp),
                                            enabled = !isLoadingCloudData
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "刷新",
                                                tint = OrbitTheme.colors.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                if (isLoadingCloudData) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = OrbitTheme.colors.primary)
                                    }
                                } else if (cloudDataError != null) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(cloudDataError ?: "拉取失败", color = OrbitTheme.colors.textSecondary, fontSize = 13.sp)
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Button(
                                                onClick = loadCloudData,
                                                colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary)
                                            ) {
                                                Text("重新加载", color = Color.White)
                                            }
                                        }
                                    }
                                } else if (cloudBackupData == null || (cloudBackupData!!.localPlaylists.isEmpty() && cloudBackupData!!.onlinePlaylists.isEmpty())) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("云端暂无可恢复的歌单数据", color = OrbitTheme.colors.textSecondary, fontSize = 13.sp)
                                    }
                                } else {
                                    val data = cloudBackupData!!
                                    LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(bottom = 12.dp)
                                    ) {
                                        // 云端本地歌单
                                        if (data.localPlaylists.isNotEmpty()) {
                                            item {
                                                Text(
                                                    text = "📁 云端自建歌单 (${data.localPlaylists.size})",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = OrbitTheme.colors.primary,
                                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                                )
                                            }
                                            items(data.localPlaylists.indices.toList(), key = { "cloud_local_$it" }) { idx ->
                                                val p = data.localPlaylists[idx]
                                                val isChecked = selectedLocalDownloadIndices.contains(idx)
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isChecked) OrbitTheme.colors.primary.copy(alpha = 0.12f) else OrbitTheme.colors.surfaceCard,
                                                    border = BorderStroke(1.dp, if (isChecked) OrbitTheme.colors.primary.copy(alpha = 0.4f) else OrbitTheme.colors.primary.copy(alpha = 0.1f)),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            if (isChecked) selectedLocalDownloadIndices.remove(idx) else selectedLocalDownloadIndices.add(idx)
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Checkbox(
                                                            checked = isChecked,
                                                            onCheckedChange = {
                                                                if (it) selectedLocalDownloadIndices.add(idx) else selectedLocalDownloadIndices.remove(idx)
                                                            },
                                                            colors = CheckboxDefaults.colors(checkedColor = OrbitTheme.colors.primary)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = p.name,
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = OrbitTheme.colors.textPrimary
                                                            )
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                if (p.groupName.isNotBlank() && p.groupName != "默认") {
                                                                    Surface(
                                                                        shape = RoundedCornerShape(4.dp),
                                                                        color = OrbitTheme.colors.primary.copy(alpha = 0.15f)
                                                                    ) {
                                                                        Text(
                                                                            text = p.groupName,
                                                                            color = OrbitTheme.colors.primary,
                                                                            fontSize = 9.sp,
                                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                        )
                                                                    }
                                                                }
                                                                Text(
                                                                    text = "${p.songs.size} 首歌曲",
                                                                    fontSize = 11.5.sp,
                                                                    color = OrbitTheme.colors.textSecondary
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 云端在线歌单
                                        if (data.onlinePlaylists.isNotEmpty()) {
                                            item {
                                                Text(
                                                    text = "🌐 云端在线歌单 (${data.onlinePlaylists.size})",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = OrbitTheme.colors.primary,
                                                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                                                )
                                            }
                                            items(data.onlinePlaylists.indices.toList(), key = { "cloud_online_$it" }) { idx ->
                                                val op = data.onlinePlaylists[idx]
                                                val isChecked = selectedOnlineDownloadIndices.contains(idx)
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isChecked) OrbitTheme.colors.primary.copy(alpha = 0.12f) else OrbitTheme.colors.surfaceCard,
                                                    border = BorderStroke(1.dp, if (isChecked) OrbitTheme.colors.primary.copy(alpha = 0.4f) else OrbitTheme.colors.primary.copy(alpha = 0.1f)),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            if (isChecked) selectedOnlineDownloadIndices.remove(idx) else selectedOnlineDownloadIndices.add(idx)
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Checkbox(
                                                            checked = isChecked,
                                                            onCheckedChange = {
                                                                if (it) selectedOnlineDownloadIndices.add(idx) else selectedOnlineDownloadIndices.remove(idx)
                                                            },
                                                            colors = CheckboxDefaults.colors(checkedColor = OrbitTheme.colors.primary)
                                                        )
                                                        Box(
                                                            modifier = Modifier
                                                                .size(42.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(OrbitTheme.colors.surface),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (op.coverUrl.isNotBlank()) {
                                                                AsyncImage(
                                                                    model = op.coverUrl,
                                                                    contentDescription = null,
                                                                    contentScale = ContentScale.Crop,
                                                                    modifier = Modifier.fillMaxSize()
                                                                )
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = op.title,
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = OrbitTheme.colors.textPrimary
                                                            )
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Surface(
                                                                    shape = RoundedCornerShape(4.dp),
                                                                    color = OrbitTheme.colors.primary.copy(alpha = 0.15f)
                                                                ) {
                                                                    Text(
                                                                        text = op.platform.displayName,
                                                                        color = OrbitTheme.colors.primary,
                                                                        fontSize = 9.sp,
                                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                    )
                                                                }
                                                                if (op.customGroup.isNotBlank() && op.customGroup != "默认") {
                                                                    Surface(
                                                                        shape = RoundedCornerShape(4.dp),
                                                                        color = OrbitTheme.colors.primary.copy(alpha = 0.15f)
                                                                    ) {
                                                                        Text(
                                                                            text = op.customGroup,
                                                                            color = OrbitTheme.colors.primary,
                                                                            fontSize = 9.sp,
                                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                        )
                                                                    }
                                                                }
                                                                Text(
                                                                    text = "${op.trackCount} 首",
                                                                    fontSize = 11.5.sp,
                                                                    color = OrbitTheme.colors.textSecondary
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 下载恢复同步模式选择 (增量合并 / 全量覆盖)
                                    SyncModeSelector(
                                        selectedMode = downloadSyncMode,
                                        onModeSelect = { downloadSyncMode = it },
                                        isUpload = false,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. 底部固定操作栏
                Surface(
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                    color = OrbitTheme.colors.surfaceCard,
                    border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        when (currentTab) {
                            SyncTab.UPLOAD -> {
                                val totalSel = selectedLocalUploadIds.size + selectedOnlineUploadIds.size
                                Column {
                                    Text(
                                        text = "已选 $totalSel 个歌单 · ${uploadSyncMode.title}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = OrbitTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = if (uploadSyncMode == PlaylistSyncMode.OVERWRITE) "将全量替换云端备份" else "增量去重追加至云端",
                                        fontSize = 10.5.sp,
                                        color = OrbitTheme.colors.textSecondary
                                    )
                                }
                                Button(
                                    onClick = {
                                        if (totalSel == 0) {
                                            FastToast.show(context, "请至少勾选一个要上传的歌单")
                                            return@Button
                                        }
                                        val selectedLocals = playlists.filter { it.id in selectedLocalUploadIds }
                                        val selectedOnlines = onlineFavorites.filter { "${it.platform.name}_${it.id}" in selectedOnlineUploadIds }
                                        val relatedGroups = (selectedLocals.map { it.groupName } + selectedOnlines.map { it.customGroup } + allGroups).distinct()

                                        isUploading = true
                                        coroutineScope.launch {
                                            val res = syncManager.uploadSelectedPlaylists(
                                                selectedLocalPlaylists = selectedLocals,
                                                selectedOnlinePlaylists = selectedOnlines,
                                                includeGroups = relatedGroups,
                                                syncMode = uploadSyncMode
                                            )
                                            res.onSuccess { msg ->
                                                FastToast.show(context, msg)
                                                onSyncCompleted()
                                            }.onFailure { err ->
                                                FastToast.show(context, "上传失败: ${err.localizedMessage}")
                                            }
                                            isUploading = false
                                        }
                                    },
                                    enabled = !isUploading && totalSel > 0,
                                    colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary),
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                                ) {
                                    if (isUploading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("正在上传...", color = Color.White, fontSize = 13.sp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.CloudUpload,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (OrbitTheme.colors.isDark) DarkBackground else Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val btnText = if (uploadSyncMode == PlaylistSyncMode.OVERWRITE) "全量覆盖上传" else "增量备份上传"
                                        Text(btnText, color = if (OrbitTheme.colors.isDark) DarkBackground else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            SyncTab.DOWNLOAD -> {
                                val totalSel = selectedLocalDownloadIndices.size + selectedOnlineDownloadIndices.size
                                Column {
                                    Text(
                                        text = "已选 $totalSel 个歌单 · ${downloadSyncMode.title}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = OrbitTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = if (downloadSyncMode == PlaylistSyncMode.OVERWRITE) "清除同名歌单歌曲后替换" else "增量去重追加至本地",
                                        fontSize = 10.5.sp,
                                        color = OrbitTheme.colors.textSecondary
                                    )
                                }
                                Button(
                                    onClick = {
                                        if (cloudBackupData == null || totalSel == 0) {
                                            FastToast.show(context, "请至少勾选一个要恢复的歌单")
                                            return@Button
                                        }
                                        val data = cloudBackupData!!
                                        val selectedLocals = selectedLocalDownloadIndices.mapNotNull { data.localPlaylists.getOrNull(it) }
                                        val selectedOnlines = selectedOnlineDownloadIndices.mapNotNull { data.onlinePlaylists.getOrNull(it) }

                                        isRestoring = true
                                        coroutineScope.launch {
                                            val res = syncManager.restoreSelectedPlaylists(
                                                selectedLocal = selectedLocals,
                                                selectedOnline = selectedOnlines,
                                                restoreGroups = data.groups,
                                                syncMode = downloadSyncMode
                                            )
                                            res.onSuccess { msg ->
                                                FastToast.show(context, msg)
                                                onSyncCompleted()
                                                onDismiss()
                                            }.onFailure { err ->
                                                FastToast.show(context, "恢复失败: ${err.localizedMessage}")
                                            }
                                            isRestoring = false
                                        }
                                    },
                                    enabled = !isRestoring && totalSel > 0,
                                    colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary),
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                                ) {
                                    if (isRestoring) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("正在恢复...", color = Color.White, fontSize = 13.sp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (OrbitTheme.colors.isDark) DarkBackground else Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val btnText = if (downloadSyncMode == PlaylistSyncMode.OVERWRITE) "全量覆盖恢复" else "增量合并恢复"
                                        Text(btnText, color = if (OrbitTheme.colors.isDark) DarkBackground else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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

/**
 * 同步模式选择器 (增量合并 / 全量覆盖)
 */
@Composable
private fun SyncModeSelector(
    selectedMode: PlaylistSyncMode,
    onModeSelect: (PlaylistSyncMode) -> Unit,
    isUpload: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = OrbitTheme.colors.surfaceCard,
        border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SyncAlt,
                    contentDescription = null,
                    tint = OrbitTheme.colors.primary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = if (isUpload) "云端同步模式：" else "本机恢复模式：",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = OrbitTheme.colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. 增量合并
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedMode == PlaylistSyncMode.MERGE) OrbitTheme.colors.primary.copy(alpha = 0.16f) else Color.Transparent,
                    border = BorderStroke(
                        1.dp,
                        if (selectedMode == PlaylistSyncMode.MERGE) OrbitTheme.colors.primary else OrbitTheme.colors.primary.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onModeSelect(PlaylistSyncMode.MERGE) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == PlaylistSyncMode.MERGE,
                            onClick = { onModeSelect(PlaylistSyncMode.MERGE) },
                            colors = RadioButtonDefaults.colors(selectedColor = OrbitTheme.colors.primary),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = "增量合并 (推荐)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedMode == PlaylistSyncMode.MERGE) OrbitTheme.colors.primary else OrbitTheme.colors.textPrimary
                            )
                            Text(
                                text = if (isUpload) "去重追加，保留云端已有" else "去重追加，保留本地已有",
                                fontSize = 9.5.sp,
                                color = OrbitTheme.colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 2. 全量覆盖
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedMode == PlaylistSyncMode.OVERWRITE) OrbitTheme.colors.primary.copy(alpha = 0.16f) else Color.Transparent,
                    border = BorderStroke(
                        1.dp,
                        if (selectedMode == PlaylistSyncMode.OVERWRITE) OrbitTheme.colors.primary else OrbitTheme.colors.primary.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onModeSelect(PlaylistSyncMode.OVERWRITE) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == PlaylistSyncMode.OVERWRITE,
                            onClick = { onModeSelect(PlaylistSyncMode.OVERWRITE) },
                            colors = RadioButtonDefaults.colors(selectedColor = OrbitTheme.colors.primary),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = "全量覆盖",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedMode == PlaylistSyncMode.OVERWRITE) OrbitTheme.colors.primary else OrbitTheme.colors.textPrimary
                            )
                            Text(
                                text = if (isUpload) "直接替代云端全部歌单" else "清空同名歌单原有歌曲",
                                fontSize = 9.5.sp,
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
}
