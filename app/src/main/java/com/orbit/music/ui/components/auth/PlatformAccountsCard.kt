package com.orbit.music.ui.components.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.orbit.music.data.online.auth.PlatformAccountManager
import com.orbit.music.data.online.auth.model.PlatformAccount
import com.orbit.music.data.online.model.OnlinePlatform

/**
 * 设置页面中的在线音乐平台账号管理卡片
 */
@Composable
fun PlatformAccountsCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accountManager = remember { PlatformAccountManager.getInstance(context) }
    val accountsMap by accountManager.accounts.collectAsState()

    var activeLoginPlatform by remember { mutableStateOf<OnlinePlatform?>(null) }
    var activeSyncPlatform by remember { mutableStateOf<OnlinePlatform?>(null) }

    // 支持登录的平台列表（优先 QQ 音乐和酷狗音乐）
    val supportedPlatforms = listOf(OnlinePlatform.QQ, OnlinePlatform.KUGOU)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "音乐平台账号与授权",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "填入 QQ 音乐 / 酷狗音乐 Cookie 凭证，可同步个人自建歌单与 VIP 权益",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (platform in supportedPlatforms) {
                    val account = accountsMap[platform]
                    PlatformAccountRow(
                        platform = platform,
                        account = account,
                        onLoginClick = { activeLoginPlatform = platform },
                        onLogoutClick = { accountManager.logout(platform) },
                        onSyncPlaylistsClick = { activeSyncPlatform = platform }
                    )
                }
            }
        }
    }

    // 扫码登录弹窗
    activeLoginPlatform?.let { platform ->
        PlatformLoginDialog(
            platform = platform,
            onDismiss = { activeLoginPlatform = null },
            onLoginSuccess = {
                activeLoginPlatform = null
            }
        )
    }

    // 同步歌单弹窗
    activeSyncPlatform?.let { platform ->
        PlatformPlaylistSyncDialog(
            platform = platform,
            onDismiss = { activeSyncPlatform = null }
        )
    }
}

@Composable
private fun PlatformAccountRow(
    platform: OnlinePlatform,
    account: PlatformAccount?,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onSyncPlaylistsClick: () -> Unit
) {
    val context = LocalContext.current
    val isLoggedIn = account != null && account.userId.isNotBlank()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像 / 平台图标
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (isLoggedIn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isLoggedIn && account?.avatarUrl?.isNotBlank() == true) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(account.avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = account.nickname,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = if (platform == OnlinePlatform.QQ) Icons.Default.MusicNote else Icons.Default.Headphones,
                        contentDescription = null,
                        tint = if (isLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // 账号信息区
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = platform.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (isLoggedIn && account != null) {
                        if (account.isVip) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFFB300),
                                modifier = Modifier.padding(horizontal = 2.dp)
                            ) {
                                Text(
                                    text = account.vipLevel.ifBlank { "VIP" },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = if (isLoggedIn && account != null) {
                        "${account.nickname} (${account.userId})"
                    } else {
                        "未登录"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isLoggedIn) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 按钮操作区
            if (isLoggedIn) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onSyncPlaylistsClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "同步歌单",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onLogoutClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "退出登录",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Button(
                    onClick = onLoginClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "填入 Cookie", fontSize = 12.sp)
                }
            }
        }
    }
}
