package com.orbit.installer.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbit.installer.core.AppManager
import com.orbit.installer.core.InstallerDispatcher
import com.orbit.installer.model.AppInfo
import com.orbit.installer.ui.components.AppIconImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AppSortType(val label: String) {
    TIME("更新时间"),
    SIZE("占用体积"),
    NAME("名称")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagementScreen(
    onShowMessage: (String) -> Unit,
    onShowError: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var includeSystemApps by remember { mutableStateOf(false) }
    var sortType by remember { mutableStateOf(AppSortType.TIME) }
    var isLoading by remember { mutableStateOf(true) }
    var appList by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var appToUninstall by remember { mutableStateOf<AppInfo?>(null) }

    val loadApps: () -> Unit = {
        isLoading = true
        coroutineScope.launch {
            appList = AppManager.getInstalledApps(context, includeSystem = includeSystemApps)
            isLoading = false
        }
    }

    LaunchedEffect(includeSystemApps) {
        loadApps()
    }

    val filteredList = remember(appList, searchQuery, sortType) {
        val query = searchQuery.trim().lowercase()
        val base = if (query.isBlank()) {
            appList
        } else {
            appList.filter {
                it.appName.lowercase().contains(query) || it.packageName.lowercase().contains(query)
            }
        }

        when (sortType) {
            AppSortType.TIME -> base.sortedByDescending { it.lastUpdateTime }
            AppSortType.SIZE -> base.sortedByDescending { it.apkSize }
            AppSortType.NAME -> base.sortedBy { it.appName.lowercase() }
        }
    }

    val totalSize = remember(filteredList) {
        filteredList.sumOf { it.apkSize }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // 1. 全宽通栏现代搜索栏
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("搜索已安装应用名称或包名...", color = Color(0xFF64748B), fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "清除", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF3B82F6),
                unfocusedBorderColor = Color(0xFF1E293B),
                focusedContainerColor = Color(0xFF0F1420).copy(alpha = 0.85f),
                unfocusedContainerColor = Color(0xFF0F1420).copy(alpha = 0.85f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2. 筛选与排序工具栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 分类切换胶囊
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F1420).copy(alpha = 0.8f))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CategoryTag(
                    text = "第三方应用",
                    selected = !includeSystemApps,
                    onClick = { includeSystemApps = false }
                )
                CategoryTag(
                    text = "包含系统",
                    selected = includeSystemApps,
                    onClick = { includeSystemApps = true }
                )
            }

            // 排序切换与刷新控制组
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.wrapContentWidth()
            ) {
                // 排序切换按钮 (高度 34.dp，圆角 10.dp，带精致图标与下拉提示)
                Surface(
                    onClick = {
                        sortType = when (sortType) {
                            AppSortType.TIME -> AppSortType.SIZE
                            AppSortType.SIZE -> AppSortType.NAME
                            AppSortType.NAME -> AppSortType.TIME
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF131926).copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.7f)),
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = sortType.label,
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 刷新按钮 (严格限定 34.dp x 34.dp，与排序按钮保持统一圆角与背景边框质感)
                Surface(
                    onClick = { loadApps() },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF131926).copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.7f)),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. 统计仪表卡片（双格精美设计）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 左格：数量
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF131926).copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E3A8A).copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Apps, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("应用总数", fontSize = 10.sp, color = Color(0xFF64748B))
                        Text("${filteredList.size} 款应用", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            // 右格：占用存储
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF131926).copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F766E).copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PieChart, contentDescription = null, tint = Color(0xFF2DD4BF), modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("空间占用", fontSize = 10.sp, color = Color(0xFF64748B))
                        Text(AppManager.formatFileSize(totalSize), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2DD4BF))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. 应用列表
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF38BDF8), strokeWidth = 2.dp)
            }
        } else if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Inbox,
                        contentDescription = null,
                        tint = Color(0xFF334155),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("未找到匹配的应用", color = Color(0xFF64748B), fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredList, key = { it.packageName }) { app ->
                    ModernAppItemCard(
                        app = app,
                        onOpen = {
                            val res = AppManager.launchApp(context, app.packageName)
                            res.onSuccess {
                                onShowMessage("已启动 ${app.appName}")
                            }.onFailure { err ->
                                onShowError(err.localizedMessage ?: "启动失败")
                            }
                        },
                        onUninstall = {
                            appToUninstall = app
                        },
                        onOpenSettings = {
                            val res = AppManager.openAppSettings(context, app.packageName)
                            res.onFailure { err ->
                                onShowError("无法打开应用设置页: ${err.localizedMessage}")
                            }
                        }
                    )
                }
            }
        }
    }

    // 卸载确认弹窗
    if (appToUninstall != null) {
        val targetApp = appToUninstall!!
        AlertDialog(
            onDismissRequest = { appToUninstall = null },
            containerColor = Color(0xFF131926),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCBD5E1),
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(28.dp))
                }
            },
            title = {
                Text("确认卸载应用？", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Column {
                    Text("即将卸载：${targetApp.appName}", fontWeight = FontWeight.SemiBold, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("包名: ${targetApp.packageName}", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("系统将调用当前选定引擎执行安全卸载。", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pkg = targetApp.packageName
                        appToUninstall = null
                        coroutineScope.launch {
                            val res = InstallerDispatcher.dispatchUninstall(context, pkg)
                            res.onSuccess { msg ->
                                onShowMessage(msg)
                                delay(1000)
                                loadApps()
                            }.onFailure { err ->
                                onShowError(err.localizedMessage ?: "卸载失败")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("确认卸载", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { appToUninstall = null }
                ) {
                    Text("取消", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

@Composable
fun CategoryTag(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFF2563EB) else Color.Transparent,
        modifier = Modifier.height(30.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Color.White else Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
fun ModernAppItemCard(
    app: AppInfo,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF131926).copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 应用图标
            AppIconImage(drawable = app.icon, size = 44.dp)

            Spacer(modifier = Modifier.width(12.dp))

            // 信息主体
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.appName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    if (app.isSystemApp) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF334155).copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "系统",
                                fontSize = 9.sp,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 版本与体积
                Text(
                    text = "v${app.versionName} • ${AppManager.formatFileSize(app.apkSize)}",
                    fontSize = 11.sp,
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Medium
                )

                // 包名细字展示
                Text(
                    text = app.packageName,
                    fontSize = 10.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 紧凑操作按钮组
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (app.hasLaunchIntent) {
                    FilledTonalButton(
                        onClick = onOpen,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF1E3A8A).copy(alpha = 0.6f),
                            contentColor = Color(0xFF60A5FA)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("打开", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                ActionIconButton(
                    icon = Icons.Default.Settings,
                    contentDescription = "详情设置",
                    tint = Color(0xFF94A3B8),
                    bgColor = Color(0xFF1E293B),
                    borderColor = Color(0xFF334155).copy(alpha = 0.5f),
                    onClick = onOpenSettings
                )

                if (!app.isSystemApp) {
                    ActionIconButton(
                        icon = Icons.Default.DeleteOutline,
                        contentDescription = "卸载",
                        tint = Color(0xFFF87171),
                        bgColor = Color(0xFF450A0A).copy(alpha = 0.6f),
                        borderColor = Color(0xFF991B1B).copy(alpha = 0.5f),
                        onClick = onUninstall
                    )
                }
            }
        }
    }
}

@Composable
fun ActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    tint: Color,
    bgColor: Color,
    borderColor: Color? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = borderColor?.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
        modifier = Modifier.size(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
