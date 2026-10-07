package com.orbit.installer.ui.screens

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbit.installer.core.AppManager
import com.orbit.installer.core.DownloadManager
import com.orbit.installer.core.PackageInstallerHelper
import com.orbit.installer.ui.components.AppIconImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallCenterScreen(
    onTriggerInstall: (File) -> Unit,
    onShowMessage: (String) -> Unit,
    onShowError: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val downloadManager = remember { DownloadManager(context) }

    var downloadUrl by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var progressInfo by remember { mutableStateOf("") }

    var selectedApkFile by remember { mutableStateOf<File?>(null) }
    var parsedApkLabel by remember { mutableStateOf<String?>(null) }

    var clipboardUrl by remember { mutableStateOf<String?>(null) }
    var scannedApks by remember { mutableStateOf<List<File>>(emptyList()) }
    var isScanningLocal by remember { mutableStateOf(true) }

    // 扫描本地已有 APK
    val scanLocalApks: () -> Unit = {
        isScanningLocal = true
        coroutineScope.launch {
            val list = withContext(Dispatchers.IO) {
                findLocalApks(context)
            }
            scannedApks = list
            isScanningLocal = false
        }
    }

    // 检查剪贴板链接
    LaunchedEffect(Unit) {
        scanLocalApks()
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (cm != null && cm.hasPrimaryClip()) {
                val clipData = cm.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0)?.text?.toString()?.trim()
                    if (!text.isNullOrEmpty() && (text.startsWith("http://") || text.startsWith("https://"))) {
                        clipboardUrl = text
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    // 本地文件选择 Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                onShowMessage("正在解析并载入选中的本地安装包...")
                val file = copyUriToCache(context, uri)
                if (file != null && file.exists()) {
                    selectedApkFile = file
                    val res = PackageInstallerHelper.verifyApk(context, file)
                    res.onSuccess { label ->
                        parsedApkLabel = label
                        onShowMessage("成功载入: $label")
                    }.onFailure { err ->
                        parsedApkLabel = null
                        onShowError(err.localizedMessage ?: "安装包解析失败")
                    }
                } else {
                    onShowError("无法读取所选文件")
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. 剪贴板快速粘贴条（如果有检测到链接）
        AnimatedVisibility(
            visible = clipboardUrl != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0284C7).copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("检测到已复制的下载链接", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            text = clipboardUrl ?: "",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            downloadUrl = clipboardUrl ?: ""
                            clipboardUrl = null
                            onShowMessage("已自动填入下载地址")
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("一键填入", fontSize = 12.sp)
                    }
                }
            }
        }

        // 2. 车机存储与健康状态仪表卡
        StorageStatusCard()

        Spacer(modifier = Modifier.height(16.dp))

        // 3. OrbitPlayer 专属通道横幅卡片
        OrbitPlayerFeaturedCard(
            onQuickInstall = {
                // 自动预设并启动官方直链下载或直接扫描
                downloadUrl = "https://github.com/tangino/OrbitPlayer/releases/latest/download/OrbitPlayer.apk"
                onShowMessage("已填入 OrbitPlayer 专属安装通道直链，点击立即下载即可！")
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 4. 网络下载安装核心卡片 (发光微渐变毛玻璃质感)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    Brush.linearGradient(
                        listOf(Color(0xFF3B82F6).copy(alpha = 0.5f), Color(0xFF8B5CF6).copy(alpha = 0.2f))
                    ),
                    RoundedCornerShape(20.dp)
                ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131926).copy(alpha = 0.85f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("网络安装包极速下载", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("直链、短链、GitHub 等", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 辅助本地选择按键
                    FilledTonalButton(
                        onClick = { filePickerLauncher.launch("application/vnd.android.package-archive") },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color(0xFF38BDF8)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("本地选择", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = downloadUrl,
                    onValueChange = { downloadUrl = it },
                    placeholder = { Text("请输入或粘贴 APK 下载直链 (HTTP/HTTPS)...", color = Color(0xFF64748B), fontSize = 13.sp) },
                    singleLine = true,
                    enabled = !isDownloading,
                    trailingIcon = {
                        if (downloadUrl.isNotEmpty() && !isDownloading) {
                            IconButton(onClick = { downloadUrl = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF1E293B),
                        focusedContainerColor = Color(0xFF0C1017),
                        unfocusedContainerColor = Color(0xFF0C1017),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isDownloading) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF38BDF8)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("正在高速下载中...", fontSize = 13.sp, color = Color(0xFFE2E8F0))
                            }
                            Text(progressInfo, fontSize = 13.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF3B82F6),
                            trackColor = Color(0xFF1E293B)
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            val url = downloadUrl.trim()
                            if (url.isEmpty()) {
                                onShowError("请输入有效的应用下载链接")
                                return@Button
                            }
                            isDownloading = true
                            downloadProgress = 0f
                            progressInfo = "正在建立连接..."

                            coroutineScope.launch {
                                val res = downloadManager.downloadApk(url) { progress, current, total ->
                                    downloadProgress = progress
                                    progressInfo = if (total > 0) {
                                        "${(current / 1024 / 1024)}MB / ${(total / 1024 / 1024)}MB (${(progress * 100).toInt()}%)"
                                    } else {
                                        "${(current / 1024)} KB"
                                    }
                                }
                                isDownloading = false
                                res.fold(
                                    onSuccess = { apkFile ->
                                        selectedApkFile = apkFile
                                        val verifyRes = PackageInstallerHelper.verifyApk(context, apkFile)
                                        verifyRes.onSuccess { label -> parsedApkLabel = label }
                                        onShowMessage("下载完成，已准备就绪")
                                        onTriggerInstall(apkFile)
                                    },
                                    onFailure = { err ->
                                        onShowError(err.localizedMessage ?: "下载失败")
                                    }
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = Color(0xFF2563EB), spotColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("立即开始下载并安装", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 5. 待安装包预览卡片
        if (selectedApkFile != null && selectedApkFile!!.exists()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF059669).copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF062D24).copy(alpha = 0.85f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF059669)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = parsedApkLabel ?: selectedApkFile!!.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "文件大小: ${AppManager.formatFileSize(selectedApkFile!!.length())}",
                            fontSize = 12.sp,
                            color = Color(0xFF6EE7B7)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = { onTriggerInstall(selectedApkFile!!) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("再次安装", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 6. 车机本地/U盘 APK 雷达自动发现列表
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Radar, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("车机存储与 U 盘 APK 发现", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            IconButton(
                onClick = { scanLocalApks() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新扫描", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isScanningLocal) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF38BDF8), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("正在扫描车机下载目录与外置存储...", fontSize = 13.sp, color = Color(0xFF94A3B8))
                }
            }
        } else if (scannedApks.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF131926).copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.FolderOff, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("未在 Download 目录或 U 盘找到已下载的 APK", color = Color(0xFF64748B), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("您可点击右上角「手动选择APK」从其他目录载入", color = Color(0xFF475569), fontSize = 11.sp)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                scannedApks.forEach { apkFile ->
                    LocalApkCardItem(
                        apkFile = apkFile,
                        onInstall = { onTriggerInstall(apkFile) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun OrbitPlayerFeaturedCard(
    onQuickInstall: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(Color(0xFF3B82F6), Color(0xFFEC4899))
                ),
                RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF181528).copy(alpha = 0.9f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2563EB), Color(0xFFDB2777))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Orbit Player", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFDB2777).copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDB2777).copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "车载旗舰音乐",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF472B6),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("无损高保真播放 • 专业车载参量均衡器 • 在线音源", fontSize = 12.sp, color = Color(0xFFCBD5E1), maxLines = 1)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = onQuickInstall,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("快捷安装", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LocalApkCardItem(
    apkFile: File,
    onInstall: () -> Unit
) {
    val context = LocalContext.current
    val parsedInfo = remember(apkFile) {
        try {
            val pm = context.packageManager
            val pkg = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            if (pkg != null) {
                val appName = pkg.applicationInfo?.let { pm.getApplicationLabel(it).toString() } ?: apkFile.name
                val vName = pkg.versionName ?: "未知版本"
                val icon = try {
                    pkg.applicationInfo?.sourceDir = apkFile.absolutePath
                    pkg.applicationInfo?.publicSourceDir = apkFile.absolutePath
                    pkg.applicationInfo?.loadIcon(pm)
                } catch (e: Exception) {
                    null
                }
                Triple(appName, vName, icon)
            } else {
                Triple(apkFile.name, "APK 文件", null)
            }
        } catch (e: Exception) {
            Triple(apkFile.name, "APK 文件", null)
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF131926).copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconImage(drawable = parsedInfo.third, size = 44.dp)

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = parsedInfo.first,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "版本: ${parsedInfo.second} • 大小: ${AppManager.formatFileSize(apkFile.length())}",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = onInstall,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("安装", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun StorageStatusCard() {
    val stat = remember {
        try {
            val s = StatFs(Environment.getDataDirectory().path)
            val total = s.blockCountLong * s.blockSizeLong
            val available = s.availableBlocksLong * s.blockSizeLong
            val used = total - available
            val percent = if (total > 0) used.toFloat() / total else 0f
            Triple(total, available, percent)
        } catch (e: Exception) {
            Triple(0L, 0L, 0f)
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF131926).copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF0369A1), Color(0xFF0D9488))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("车机机身内部存储", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text(
                        "剩余 ${AppManager.formatFileSize(stat.second)} / 共 ${AppManager.formatFileSize(stat.first)}",
                        fontSize = 12.sp,
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { stat.third },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (stat.third > 0.9f) Color(0xFFEF4444) else Color(0xFF38BDF8),
                    trackColor = Color(0xFF1E293B)
                )
            }
        }
    }
}

private suspend fun findLocalApks(context: Context): List<File> = withContext(Dispatchers.IO) {
    val results = mutableListOf<File>()
    try {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadDir != null && downloadDir.exists()) {
            downloadDir.listFiles { file -> file.isFile && file.name.endsWith(".apk", ignoreCase = true) }?.let {
                results.addAll(it)
            }
        }
        val extCache = context.getExternalFilesDir(null)
        if (extCache != null && extCache.exists()) {
            extCache.walkTopDown().maxDepth(2).filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) }.forEach {
                if (!results.contains(it)) results.add(it)
            }
        }
    } catch (e: Exception) {
        // ignore
    }
    results.sortedByDescending { it.lastModified() }.take(10)
}

private fun copyUriToCache(context: Context, uri: Uri): File? {
    return try {
        val cacheDir = context.externalCacheDir ?: context.cacheDir
        val targetFile = File(cacheDir, "local_selected_install.apk")
        if (targetFile.exists()) targetFile.delete()

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
        targetFile
    } catch (e: Exception) {
        null
    }
}
