package com.orbit.music.ui.components

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.orbit.music.ui.theme.OrbitTheme
import com.orbit.music.utils.AppStorageVolume
import com.orbit.music.utils.FastToast
import com.orbit.music.utils.PermissionHelper
import com.orbit.music.utils.StorageVolumeHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val SUPPORTED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic")

/**
 * 快捷常用目录
 */
private data class FolderShortcut(
    val name: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val path: File
)

/**
 * 本地磁盘图片文件浏览器对话框
 * 全面支持检测与列出内部存储、MicroSD 卡、车载 USB U 盘与 OTG 扩展卷
 */
@Composable
fun ImageFilePickerDialog(
    initialDirectory: File? = null,
    onDismissRequest: () -> Unit,
    onImageSelected: (File) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 权限状态与刷新版本计数
    var hasPermission by remember { mutableStateOf(PermissionHelper.hasStorageAccess(context)) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // 若无权限，以 Toast 轻量提醒用户
    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            FastToast.show(context, "未获得文件访问权限，部分目录可能无法读取", 2000L)
        }
    }

    // 监听应用切回时的生命周期，自动刷新权限与目录
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = PermissionHelper.hasStorageAccess(context)
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 获取所有可用存储卷 (内部存储、SD卡、USB U盘、车载挂载卷)
    val storageVolumes = remember(refreshKey) {
        StorageVolumeHelper.getAllStorageVolumes(context)
    }

    var currentDir by remember(refreshKey) {
        val defaultStart = initialDirectory?.takeIf { it.exists() && it.isDirectory }
            ?: run {
                val primaryStorage = storageVolumes.firstOrNull { it.isPrimary }?.path
                    ?: Environment.getExternalStorageDirectory()
                    ?: File("/storage/emulated/0")
                val pictures = File(primaryStorage, "Pictures")
                if (pictures.exists() && pictures.canRead()) pictures else primaryStorage
            }
        mutableStateOf(defaultStart)
    }

    // 计算当前选中的所属存储设备
    val activeVolume = remember(currentDir, storageVolumes) {
        storageVolumes.find { currentDir.absolutePath.startsWith(it.path.absolutePath) }
            ?: storageVolumes.firstOrNull()
    }

    // 常用目录快捷入口 (根据当前卷动态生成)
    val folderShortcuts = remember(activeVolume, refreshKey) {
        val list = mutableListOf<FolderShortcut>()
        val base = activeVolume?.path ?: Environment.getExternalStorageDirectory()
        if (base != null && base.exists()) {
            val pics = File(base, "Pictures")
            if (pics.exists() && pics.canRead()) list.add(FolderShortcut("壁纸与图片", Icons.Default.PhotoLibrary, pics))
            val dcim = File(base, "DCIM")
            if (dcim.exists() && dcim.canRead()) list.add(FolderShortcut("相册 (DCIM)", Icons.Default.CameraAlt, dcim))
            val download = File(base, "Download")
            if (download.exists() && download.canRead()) list.add(FolderShortcut("下载目录", Icons.Default.Download, download))
            val music = File(base, "Music")
            if (music.exists() && music.canRead()) list.add(FolderShortcut("音乐目录", Icons.Default.LibraryMusic, music))
        }
        list
    }

    // 扫描当前目录下的子文件夹与支持的图片文件
    val (folders, images) = remember(currentDir, refreshKey) {
        try {
            val allFiles = currentDir.listFiles() ?: emptyArray()
            val folderList = allFiles.filter { it.isDirectory && !it.name.startsWith(".") && it.canRead() }
                .sortedBy { it.name.lowercase(Locale.getDefault()) }
            val imageList = allFiles.filter { file ->
                file.isFile && !file.name.startsWith(".") &&
                        SUPPORTED_IMAGE_EXTENSIONS.contains(file.extension.lowercase(Locale.getDefault()))
            }.sortedBy { it.name.lowercase(Locale.getDefault()) }
            Pair(folderList, imageList)
        } catch (_: Exception) {
            Pair(emptyList(), emptyList())
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth(0.95f)
                .heightIn(max = 620.dp)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = OrbitTheme.colors.surfaceDialog,
            border = androidx.compose.foundation.BorderStroke(1.dp, OrbitTheme.colors.surfaceBorder),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // 1. 顶部标题与关闭
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(OrbitTheme.colors.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "选择背景图片",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OrbitTheme.colors.textPrimary
                            )
                            Text(
                                text = "支持内部存储、MicroSD 卡与车载 USB U 盘直接读取",
                                style = MaterialTheme.typography.bodySmall,
                                color = OrbitTheme.colors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = OrbitTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. 存储设备 / 磁盘卷选择器 (多存储设备横向切换)
                if (storageVolumes.size > 1) {
                    Text(
                        text = "存储设备 (${storageVolumes.size} 个可用介质):",
                        style = MaterialTheme.typography.labelSmall,
                        color = OrbitTheme.colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        storageVolumes.forEach { vol ->
                            val isVolActive = currentDir.absolutePath.startsWith(vol.path.absolutePath)
                            val icon = when {
                                vol.isPrimary -> Icons.Default.SdStorage
                                vol.name.contains("USB", ignoreCase = true) || vol.name.contains("U盘") -> Icons.Default.Usb
                                else -> Icons.Default.SdCard
                            }

                            FilterChip(
                                selected = isVolActive,
                                onClick = {
                                    if (!vol.path.canRead()) {
                                        FastToast.show(context, "无法访问该存储设备，缺少读取权限", 1500L)
                                    }
                                    currentDir = vol.path
                                },
                                label = {
                                    Text(
                                        text = vol.displayLabel,
                                        fontSize = 12.sp,
                                        fontWeight = if (isVolActive) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isVolActive) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = OrbitTheme.colors.primary.copy(alpha = 0.18f),
                                    selectedLabelColor = OrbitTheme.colors.primary,
                                    containerColor = OrbitTheme.colors.background.copy(alpha = 0.6f),
                                    labelColor = OrbitTheme.colors.textPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isVolActive,
                                    borderColor = Color.White.copy(alpha = 0.1f),
                                    selectedBorderColor = OrbitTheme.colors.primary.copy(alpha = 0.6f),
                                    borderWidth = 1.dp,
                                    selectedBorderWidth = 1.2.dp
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 3. 常用文件夹快捷入口
                if (folderShortcuts.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        folderShortcuts.forEach { shortcut ->
                            val isSelected = currentDir.absolutePath == shortcut.path.absolutePath
                            AssistChip(
                                onClick = {
                                    if (!shortcut.path.canRead()) {
                                        FastToast.show(context, "无法访问该目录，缺少读取权限", 1500L)
                                    }
                                    currentDir = shortcut.path
                                },
                                label = {
                                    Text(
                                        text = shortcut.name,
                                        fontSize = 11.5.sp,
                                        color = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textPrimary
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = shortcut.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary
                                    )
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (isSelected) OrbitTheme.colors.primary.copy(alpha = 0.12f) else OrbitTheme.colors.background.copy(alpha = 0.4f)
                                ),
                                border = AssistChipDefaults.assistChipBorder(
                                    enabled = true,
                                    borderColor = if (isSelected) OrbitTheme.colors.primary.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f)
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 4. 当前路径导航栏与返回上一级按钮
                val parentDir = currentDir.parentFile
                val canGoUp = parentDir != null && parentDir.canRead() && parentDir.absolutePath != "/" && parentDir.absolutePath != "/storage"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(OrbitTheme.colors.background.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canGoUp) {
                        TextButton(
                            onClick = { parentDir?.let { currentDir = it } },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Up",
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("上一级", fontSize = 12.sp, color = OrbitTheme.colors.primary, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Text(
                        text = currentDir.absolutePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = OrbitTheme.colors.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 5. 目录与图片文件列表
                if (folders.isEmpty() && images.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (!hasPermission) Icons.Default.FolderOff else Icons.Default.ImageNotSupported,
                                contentDescription = null,
                                tint = OrbitTheme.colors.textSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (!hasPermission) "当前目录无读取权限，请检查文件访问权限" else "当前目录下没有支持的图片文件或子文件夹",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OrbitTheme.colors.textSecondary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        // 子文件夹列表
                        items(folders, key = { "dir_${it.absolutePath}" }) { folder ->
                            val childCount = remember(folder) {
                                try { folder.list()?.size ?: 0 } catch (_: Exception) { 0 }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(OrbitTheme.colors.background.copy(alpha = 0.35f))
                                    .clickable {
                                        if (!folder.canRead()) {
                                            FastToast.show(context, "无法访问该目录，缺少读取权限", 1500L)
                                        }
                                        currentDir = folder
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(OrbitTheme.colors.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = OrbitTheme.colors.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = folder.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OrbitTheme.colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$childCount 项",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OrbitTheme.colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = OrbitTheme.colors.textSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // 图片文件列表
                        items(images, key = { "img_${it.absolutePath}" }) { imgFile ->
                            val fileSizeStr = remember(imgFile) { formatFileSize(imgFile.length()) }
                            val dateStr = remember(imgFile) {
                                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                sdf.format(Date(imgFile.lastModified()))
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(OrbitTheme.colors.background.copy(alpha = 0.55f))
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                    .clickable { onImageSelected(imgFile) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 缩略图预览
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(imgFile)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.2f)),
                                    contentScale = ContentScale.Crop
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = imgFile.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = OrbitTheme.colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = fileSizeStr,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OrbitTheme.colors.primary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = dateStr,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OrbitTheme.colors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Button(
                                    onClick = { onImageSelected(imgFile) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("选用", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 6. 底部关闭按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("取消", color = OrbitTheme.colors.textSecondary)
                    }
                }
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val formatted = String.format(Locale.getDefault(), "%.1f", size / Math.pow(1024.0, digitGroups.toDouble()))
    return "$formatted ${units[digitGroups]}"
}
