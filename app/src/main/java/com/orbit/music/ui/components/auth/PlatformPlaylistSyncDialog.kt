package com.orbit.music.ui.components.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.orbit.music.data.online.auth.PlatformAccountManager
import com.orbit.music.data.online.model.OnlinePlatform
import com.orbit.music.data.online.model.OnlinePlaylist
import com.orbit.music.data.online.repository.OnlinePlaylistFavoriteManager
import com.orbit.music.data.playlist.PlaylistGroupManager
import kotlinx.coroutines.launch

/**
 * 平台歌单同步与批量导入弹窗
 */
@Composable
fun PlatformPlaylistSyncDialog(
    platform: OnlinePlatform,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountManager = remember { PlatformAccountManager.getInstance(context) }
    val favoriteManager = remember { OnlinePlaylistFavoriteManager.getInstance(context) }
    val groupManager = remember { PlaylistGroupManager.getInstance(context) }

    var isLoading by remember { mutableStateOf(true) }
    var playlists by remember { mutableStateOf<List<OnlinePlaylist>>(emptyList()) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 加载用户歌单
    LaunchedEffect(platform) {
        isLoading = true
        errorMessage = null
        try {
            val list = accountManager.fetchUserPlaylists(platform)
            playlists = list
            selectedIds.clear()
            // 默认全选
            selectedIds.addAll(list.map { it.id })
            if (list.isEmpty()) {
                errorMessage = "未获取到歌单，或当前账号在 ${platform.displayName} 暂无自建歌单"
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "获取歌单列表失败"
        } finally {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 500.dp)
                .fillMaxHeight(0.8f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "同步 ${platform.displayName} 歌单",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (playlists.isNotEmpty()) "已获取到 ${playlists.size} 个歌单，勾选后导入" else "正在读取云端歌单...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 全选与反选控制条
                if (playlists.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                if (selectedIds.size == playlists.size) {
                                    selectedIds.clear()
                                } else {
                                    selectedIds.clear()
                                    selectedIds.addAll(playlists.map { it.id })
                                }
                            }
                        ) {
                            Checkbox(
                                checked = selectedIds.size == playlists.size && playlists.isNotEmpty(),
                                onCheckedChange = { checked ->
                                    selectedIds.clear()
                                    if (checked) {
                                        selectedIds.addAll(playlists.map { it.id })
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (selectedIds.size == playlists.size) "取消全选" else "全选 (${selectedIds.size}/${playlists.size})",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }

                // 歌单列表区
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    } else if (errorMessage != null && playlists.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage ?: "暂无歌单",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(playlists, key = { it.id }) { item ->
                                val isSelected = selectedIds.contains(item.id)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isSelected) {
                                                selectedIds.remove(item.id)
                                            } else {
                                                selectedIds.add(item.id)
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                if (checked) selectedIds.add(item.id) else selectedIds.remove(item.id)
                                            }
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // 封面
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(item.coverUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${item.trackCount} 首歌曲",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 底部操作栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("取消")
                    }

                    Button(
                        onClick = {
                            val selectedList = playlists.filter { selectedIds.contains(it.id) }
                            if (selectedList.isEmpty()) {
                                Toast.makeText(context, "请先勾选需要导入的歌单", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val groupName = platform.displayName
                            groupManager.addGroup(groupName)

                            for (pl in selectedList) {
                                val itemToSave = pl.copy(customGroup = groupName)
                                favoriteManager.addFavorite(itemToSave)
                            }

                            Toast.makeText(context, "已成功导入 ${selectedList.size} 个歌单至「$groupName」分组", Toast.LENGTH_LONG).show()
                            onDismiss()
                        },
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("一键导入 (${selectedIds.size})")
                    }
                }
            }
        }
    }
}
