package com.orbit.installer

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbit.installer.core.*
import com.orbit.installer.core.engine.LocalAdbEngine
import com.orbit.installer.core.engine.ShizukuInstallEngine
import com.orbit.installer.ui.components.AuroraBackground
import com.orbit.installer.ui.screens.AppManagementScreen
import com.orbit.installer.ui.screens.InstallCenterScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AuroraBackground {
                InstallerMainApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallerMainApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentNavIndex by remember { mutableStateOf(0) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showPermissionDialog by remember { mutableStateOf(false) }
    var showEngineDialog by remember { mutableStateOf(false) }
    var currentEngineMode by remember { mutableStateOf(InstallerDispatcher.currentMode) }
    var engineStatus by remember { mutableStateOf(EngineStatus(false, false, false, LocalAdbEngine.getAdbPort(context))) }
    var customPortInput by remember { mutableStateOf<String>(LocalAdbEngine.getAdbPort(context).toString()) }
    var isProbingPort by remember { mutableStateOf(false) }

    // 刷新引擎状态
    val refreshEngineStatus: () -> Unit = {
        coroutineScope.launch {
            engineStatus = InstallerDispatcher.checkEngineStatus(context)
            customPortInput = engineStatus.localAdbPort.toString()
        }
    }

    LaunchedEffect(Unit) {
        refreshEngineStatus()
    }

    // 自动清除提示消息
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(3000)
            toastMessage = null
        }
    }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            delay(4500)
            errorMessage = null
        }
    }

    val triggerInstall: (File) -> Unit = { apkFile ->
        coroutineScope.launch {
            when (val dispatchRes = InstallerDispatcher.dispatchInstall(context, apkFile, currentEngineMode)) {
                is DispatchInstallResult.SilentSuccess -> {
                    toastMessage = "【${dispatchRes.engineUsed}】${dispatchRes.message}"
                }
                is DispatchInstallResult.FallbackToSystemIntent -> {
                    when (val result = PackageInstallerHelper.installApk(context, apkFile)) {
                        is InstallResult.Success -> {
                            toastMessage = "已调起系统安装程序，请在屏幕弹窗中确认安装"
                        }
                        is InstallResult.NeedUnknownSourcePermission -> {
                            showPermissionDialog = true
                        }
                        is InstallResult.Failure -> {
                            errorMessage = result.message
                        }
                    }
                }
                is DispatchInstallResult.Failure -> {
                    errorMessage = dispatchRes.message
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 车载/移动端响应式顶部 Header
            ResponsiveCarHeader(
                currentNavIndex = currentNavIndex,
                currentEngineMode = currentEngineMode,
                engineStatus = engineStatus,
                onNavSelected = { currentNavIndex = it },
                onEngineClicked = {
                    refreshEngineStatus()
                    showEngineDialog = true
                }
            )

            // 主内容呈现区
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (currentNavIndex) {
                    0 -> InstallCenterScreen(
                        onTriggerInstall = triggerInstall,
                        onShowMessage = { toastMessage = it },
                        onShowError = { errorMessage = it }
                    )
                    1 -> AppManagementScreen(
                        onShowMessage = { toastMessage = it },
                        onShowError = { errorMessage = it }
                    )
                }
            }
        }

        // 底部悬浮提示浮层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            // 成功提示条
            AnimatedVisibility(
                visible = toastMessage != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF064E3B).copy(alpha = 0.95f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
                    shadowElevation = 12.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(toastMessage ?: "", color = Color(0xFFD1FAE5), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // 错误提示条
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF450A0A).copy(alpha = 0.95f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                    shadowElevation = 12.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(errorMessage ?: "", color = Color(0xFFFEE2E2), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    // 安装引擎设置弹窗
    if (showEngineDialog) {
        AlertDialog(
            onDismissRequest = { showEngineDialog = false },
            containerColor = Color(0xFF131926),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCBD5E1),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0284C7).copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("安装/卸载引擎配置", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("选择车机应用安装与卸载的执行引擎：", fontSize = 13.sp, color = Color(0xFF94A3B8))

                    InstallEngineMode.values().forEach { mode ->
                        Surface(
                            onClick = {
                                currentEngineMode = mode
                                InstallerDispatcher.currentMode = mode
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (currentEngineMode == mode) Color(0xFF1E3A8A).copy(alpha = 0.6f) else Color(0xFF0F141F),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (currentEngineMode == mode) Color(0xFF3B82F6) else Color(0xFF1E293B)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentEngineMode == mode,
                                    onClick = {
                                        currentEngineMode = mode
                                        InstallerDispatcher.currentMode = mode
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(mode.displayName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                    Text(mode.desc, fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E293B))

                    // 本地无线 ADB 自定义端口配置区
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F141F))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "本地无线 ADB 端口设置",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )

                            // 一键自动探测按钮
                            Surface(
                                onClick = {
                                    isProbingPort = true
                                    coroutineScope.launch {
                                        val detectedPort = LocalAdbEngine.probeOpenPort(context)
                                        if (detectedPort != null) {
                                            LocalAdbEngine.setAdbPort(context, detectedPort)
                                            customPortInput = detectedPort.toString()
                                            toastMessage = "已探测到开放端口: $detectedPort"
                                        } else {
                                            toastMessage = "未探测到开放的 ADB 端口"
                                        }
                                        refreshEngineStatus()
                                        isProbingPort = false
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1E3A8A).copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.6f)),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Radar, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isProbingPort) "探测中..." else "智能探测", fontSize = 10.sp, color = Color(0xFF93C5FD), fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        // 端口输入框与保存应用
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customPortInput,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() }.take(5)
                                    customPortInput = filtered
                                    filtered.toIntOrNull()?.let { port ->
                                        if (port in 1..65535) {
                                            LocalAdbEngine.setAdbPort(context, port)
                                            coroutineScope.launch {
                                                engineStatus = InstallerDispatcher.checkEngineStatus(context)
                                            }
                                        }
                                    }
                                },
                                placeholder = { Text("例如 5555", fontSize = 11.sp, color = Color(0xFF64748B)) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedContainerColor = Color(0xFF131926),
                                    unfocusedContainerColor = Color(0xFF131926),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }

                        // 常用端口快速点选标签
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("常用预设:", fontSize = 10.sp, color = Color(0xFF64748B))
                            listOf(5555, 5556, 6666, 8888, 2333).forEach { port ->
                                val isSelected = customPortInput == port.toString()
                                Surface(
                                    onClick = {
                                        customPortInput = port.toString()
                                        LocalAdbEngine.setAdbPort(context, port)
                                        coroutineScope.launch {
                                            engineStatus = InstallerDispatcher.checkEngineStatus(context)
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFF60A5FA) else Color(0xFF334155)
                                    ),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Box(modifier = Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "$port",
                                            fontSize = 10.sp,
                                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E293B))

                    // 引擎状态检测列表
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("当前车机环境检测状态：", fontSize = 12.sp, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (engineStatus.isShizukuAvailable) Color(0xFF10B981) else Color(0xFF64748B))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Shizuku 服务: " + if (engineStatus.isShizukuAvailable) {
                                    if (engineStatus.hasShizukuPermission) "运行中 (已授权)" else "运行中 (未授权)"
                                } else "未运行",
                                fontSize = 12.sp,
                                color = Color(0xFFCBD5E1)
                            )
                            if (engineStatus.isShizukuAvailable && !engineStatus.hasShizukuPermission) {
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(
                                    onClick = {
                                        ShizukuInstallEngine.requestPermission()
                                        refreshEngineStatus()
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("点击授权", fontSize = 12.sp, color = Color(0xFF38BDF8))
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (engineStatus.isLocalAdbPortOpen) Color(0xFF10B981) else Color(0xFF64748B))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "本地 ADB 端口 (${engineStatus.localAdbPort}): " + if (engineStatus.isLocalAdbPortOpen) "开放连接" else "未开放",
                                fontSize = 12.sp,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showEngineDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("完成")
                }
            }
        )
    }

    // 未知来源安装权限引导弹窗
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            containerColor = Color(0xFF131926),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCBD5E1),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("需要安装未知应用权限")
                }
            },
            text = {
                Text(
                    "当前模式下车机系统要求本安装助手获得「允许安装未知应用」权限。点击前往设置开启后，即可继续完成安装。",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        val opened = PackageInstallerHelper.requestUnknownAppSourcesPermission(context)
                        if (!opened) {
                            Toast.makeText(context, "未能直接唤起设置页，请在车机设置-安全中手动授权", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("前往设置开启")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("取消", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

@Composable
fun ResponsiveCarHeader(
    currentNavIndex: Int,
    currentEngineMode: InstallEngineMode,
    engineStatus: EngineStatus,
    onNavSelected: (Int) -> Unit,
    onEngineClicked: () -> Unit
) {
    val deviceModel = remember { Build.MODEL ?: "车载设备" }

    val engineColor = when {
        engineStatus.isShizukuAvailable && engineStatus.hasShizukuPermission -> Color(0xFF10B981)
        engineStatus.isLocalAdbPortOpen -> Color(0xFF06B6D4)
        else -> Color(0xFF3B82F6)
    }

    Surface(
        color = Color(0xFF0F1420).copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // 第一行：Logo、应用名与右侧微状态胶囊
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧 Logo
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF2563EB), Color(0xFF7C3AED))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Widgets,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "车机安装助手",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Orbit Installer • $deviceModel",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // 右侧：引擎切换胶囊
                Surface(
                    onClick = onEngineClicked,
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.8f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(engineColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentEngineMode.displayName,
                            fontSize = 12.sp,
                            color = Color(0xFFE2E8F0),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 第二行：自适应全宽现代流光滑动药丸 TabBar（横竖屏均完美呈现）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0C1017))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ModernNavPill(
                    title = "应用安装中心",
                    icon = Icons.Default.CloudDownload,
                    selected = currentNavIndex == 0,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavSelected(0) }
                )
                ModernNavPill(
                    title = "系统应用管理",
                    icon = Icons.Default.Apps,
                    selected = currentNavIndex == 1,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavSelected(1) }
                )
            }
        }
    }
}

@Composable
fun ModernNavPill(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color(0xFF2563EB) else Color.Transparent,
        modifier = modifier.height(38.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) Color.White else Color(0xFF64748B),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else Color(0xFF94A3B8)
            )
        }
    }
}
