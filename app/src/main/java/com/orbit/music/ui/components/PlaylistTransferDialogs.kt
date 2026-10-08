package com.orbit.music.ui.components

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.orbit.music.data.model.Playlist
import com.orbit.music.data.model.Song
import com.orbit.music.data.online.model.OnlinePlaylist
import com.orbit.music.data.online.model.OnlineSongItem
import com.orbit.music.data.playlist.PlaylistTransferManager
import com.orbit.music.data.playlist.PlaylistTransferManager.ExportFormat
import com.orbit.music.ui.theme.OrbitTheme
import com.orbit.music.utils.FastToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 歌单导出对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportPlaylistDialog(
    playlistTitle: String,
    songs: List<Song> = emptyList(),
    onlinePlaylist: OnlinePlaylist? = null,
    onlineSongs: List<OnlineSongItem> = emptyList(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedFormat by remember { mutableStateOf(ExportFormat.JSON) }
    val defaultFileName = remember(playlistTitle, selectedFormat) {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val sanitized = playlistTitle.trim().replace(Regex("[\\\\/:*?\"<>|\\s]"), "_").ifBlank { "Playlist" }
        "${sanitized}_$dateStr.${selectedFormat.extension}"
    }
    var fileName by remember { mutableStateOf(defaultFileName) }
    var isExporting by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = OrbitTheme.colors.surfaceCard,
            border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.2f)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部标题与图标
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = OrbitTheme.colors.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = null,
                                    tint = OrbitTheme.colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "导出歌单",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = OrbitTheme.colors.textPrimary
                            )
                            Text(
                                text = playlistTitle,
                                fontSize = 12.sp,
                                color = OrbitTheme.colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isExporting,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = OrbitTheme.colors.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 格式选择卡片
                Text(
                    text = "选择导出格式",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OrbitTheme.colors.textSecondary,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExportFormat.values().forEach { format ->
                        val isSelected = selectedFormat == format
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) OrbitTheme.colors.primary.copy(alpha = 0.15f) else OrbitTheme.colors.surface,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.primary.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedFormat = format
                                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                                    val sanitized = playlistTitle.trim().replace(Regex("[\\\\/:*?\"<>|\\s]"), "_").ifBlank { "Playlist" }
                                    fileName = "${sanitized}_$dateStr.${format.extension}"
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (format == ExportFormat.JSON) "JSON 备份" else "M3U8 列表",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textPrimary
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = OrbitTheme.colors.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (format == ExportFormat.JSON) "包含完整元数据与在线音源" else "主流播放器通用播放列表",
                                    fontSize = 10.5.sp,
                                    color = OrbitTheme.colors.textSecondary,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 文件名编辑框
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("导出文件名", fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OrbitTheme.colors.primary,
                        unfocusedBorderColor = OrbitTheme.colors.primary.copy(alpha = 0.2f),
                        cursorColor = OrbitTheme.colors.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 操作按钮组：保存到公开下载目录 / 调用系统分享
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 分享到其他应用
                    OutlinedButton(
                        onClick = {
                            isExporting = true
                            coroutineScope.launch {
                                val transferPlaylist = if (onlinePlaylist != null) {
                                    PlaylistTransferManager.toTransferPlaylist(onlinePlaylist, onlineSongs)
                                } else {
                                    PlaylistTransferManager.toTransferPlaylist(playlistTitle, songs)
                                }
                                val content = if (selectedFormat == ExportFormat.JSON) {
                                    PlaylistTransferManager.exportToJson(transferPlaylist)
                                } else {
                                    PlaylistTransferManager.exportToM3u8(transferPlaylist)
                                }
                                val shareUri = PlaylistTransferManager.saveAndGetShareUri(context, fileName, content)
                                isExporting = false
                                if (shareUri != null) {
                                    PlaylistTransferManager.sharePlaylistFile(context, shareUri, playlistTitle, selectedFormat.mimeType)
                                    onDismiss()
                                } else {
                                    FastToast.show(context, "导出失败，请重试")
                                }
                            }
                        },
                        enabled = !isExporting,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OrbitTheme.colors.primary),
                        border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.45f))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("分享导出", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // 保存到文件
                    Button(
                        onClick = {
                            isExporting = true
                            coroutineScope.launch {
                                val transferPlaylist = if (onlinePlaylist != null) {
                                    PlaylistTransferManager.toTransferPlaylist(onlinePlaylist, onlineSongs)
                                } else {
                                    PlaylistTransferManager.toTransferPlaylist(playlistTitle, songs)
                                }
                                val content = if (selectedFormat == ExportFormat.JSON) {
                                    PlaylistTransferManager.exportToJson(transferPlaylist)
                                } else {
                                    PlaylistTransferManager.exportToM3u8(transferPlaylist)
                                }
                                val result = PlaylistTransferManager.saveToPublicDirectory(context, fileName, content, selectedFormat.mimeType)
                                isExporting = false
                                result.onSuccess { pathMsg ->
                                    FastToast.show(context, pathMsg, 3000L)
                                    onDismiss()
                                }.onFailure { err ->
                                    FastToast.show(context, "保存失败: ${err.message}")
                                }
                            }
                        },
                        enabled = !isExporting,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OrbitTheme.colors.primary,
                            contentColor = if (OrbitTheme.colors.isDark) Color.Black else Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = if (OrbitTheme.colors.isDark) Color.Black else Color.White
                            )
                        } else {
                            Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("保存到文件", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 歌单导入对话框（支持网络分享链接解析导入 与 本地文件导入）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPlaylistDialog(
    onDismiss: () -> Unit,
    onImportSuccess: (playlistId: Long, playlistTitle: String, songCount: Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 链接/文本解析, 1: 本地文件
    var inputText by remember { mutableStateOf("") }
    var customPlaylistTitle by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 本地文件选择器
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var parsedFilePlaylist by remember { mutableStateOf<PlaylistTransferManager.TransferPlaylist?>(null) }
    var restoreToOnlineFavoritesChecked by remember { mutableStateOf(true) }
    var saveAsLocalPlaylistChecked by remember { mutableStateOf(true) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            coroutineScope.launch {
                try {
                    val content = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
                        }
                    } ?: ""

                    val parsed = if (content.trim().startsWith("{")) {
                        PlaylistTransferManager.parseJson(content)
                    } else {
                        PlaylistTransferManager.parseM3u8(content, "本地导入歌单")
                    }

                    if (parsed != null && parsed.songs.isNotEmpty()) {
                        parsedFilePlaylist = parsed
                        customPlaylistTitle = parsed.title
                        errorMessage = null
                    } else {
                        parsedFilePlaylist = null
                        errorMessage = "文件解析失败或歌单内没有有效歌曲"
                    }
                } catch (e: Exception) {
                    errorMessage = "读取文件出错: ${e.localizedMessage ?: e.message}"
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = OrbitTheme.colors.surfaceCard,
            border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.2f)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部标题
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = OrbitTheme.colors.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    tint = OrbitTheme.colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "导入歌单",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = OrbitTheme.colors.textPrimary
                            )
                            Text(
                                text = "支持网络链接/文本解析与文件导入",
                                fontSize = 11.5.sp,
                                color = OrbitTheme.colors.textSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isImporting,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = OrbitTheme.colors.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tab 切换：网络链接/文本 与 本地文件
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = OrbitTheme.colors.surface,
                    contentColor = OrbitTheme.colors.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = OrbitTheme.colors.primary
                        )
                    },
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; errorMessage = null },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text("网络链接 / 文本", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; errorMessage = null },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text("本地文件 (JSON/M3U)", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 错误提示
                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFF5252).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                            Text(text = errorMessage ?: "", color = Color(0xFFFF5252), fontSize = 11.5.sp)
                        }
                    }
                }

                if (selectedTab == 0) {
                    // ========== Tab 0: 网络链接与分享文本解析 ==========
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it; errorMessage = null },
                            placeholder = {
                                Text(
                                    text = "粘贴网易云/QQ/酷狗/酷我/咪咕歌单链接、分享文本，或多行「歌名 - 歌手」",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = OrbitTheme.colors.textSecondary.copy(alpha = 0.6f)
                                )
                            },
                            maxLines = 5,
                            minLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrbitTheme.colors.primary,
                                unfocusedBorderColor = OrbitTheme.colors.primary.copy(alpha = 0.2f),
                                cursorColor = OrbitTheme.colors.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 快捷粘贴按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "支持各平台歌单直链与批量文本",
                                fontSize = 11.sp,
                                color = OrbitTheme.colors.textSecondary.copy(alpha = 0.7f)
                            )
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = clipboard?.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        val text = clip.getItemAt(0).text?.toString() ?: ""
                                        if (text.isNotBlank()) {
                                            inputText = text
                                            errorMessage = null
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("粘贴剪贴板", fontSize = 11.5.sp)
                            }
                        }

                        // 自定义保存歌单名称
                        OutlinedTextField(
                            value = customPlaylistTitle,
                            onValueChange = { customPlaylistTitle = it },
                            label = { Text("自定保存歌单名称 (选填)", fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrbitTheme.colors.primary,
                                unfocusedBorderColor = OrbitTheme.colors.primary.copy(alpha = 0.2f),
                                cursorColor = OrbitTheme.colors.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    // ========== Tab 1: 本地文件导入 (JSON / M3U / M3U8) ==========
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = OrbitTheme.colors.surface,
                            border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    filePickerLauncher.launch("*/*")
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (parsedFilePlaylist != null) Icons.Default.CheckCircle else Icons.Default.UploadFile,
                                    contentDescription = null,
                                    tint = OrbitTheme.colors.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = if (parsedFilePlaylist != null) {
                                        "已加载: ${parsedFilePlaylist!!.title} (${parsedFilePlaylist!!.songs.size} 首歌曲)"
                                    } else {
                                        "点击选取 .json / .m3u / .m3u8 歌单文件"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (parsedFilePlaylist != null) OrbitTheme.colors.primary else OrbitTheme.colors.textPrimary,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "支持 OrBitPlayer 备份或标准播放列表",
                                    fontSize = 11.sp,
                                    color = OrbitTheme.colors.textSecondary
                                )
                            }
                        }

                        if (parsedFilePlaylist != null) {
                            val p = parsedFilePlaylist!!
                            if (p.platform != null) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = OrbitTheme.colors.primary.copy(alpha = 0.1f),
                                    border = BorderStroke(0.5.dp, OrbitTheme.colors.primary.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = OrbitTheme.colors.primary, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "识别为「${p.platform.displayName}」网络歌单备份",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = OrbitTheme.colors.primary
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { restoreToOnlineFavoritesChecked = !restoreToOnlineFavoritesChecked }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = restoreToOnlineFavoritesChecked,
                                        onCheckedChange = { restoreToOnlineFavoritesChecked = it }
                                    )
                                    Text(
                                        text = "恢复至「${p.platform.displayName}」网络歌单收藏 (还原封面、平台与在线详情)",
                                        fontSize = 12.sp,
                                        color = OrbitTheme.colors.textPrimary
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { saveAsLocalPlaylistChecked = !saveAsLocalPlaylistChecked }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = saveAsLocalPlaylistChecked,
                                        onCheckedChange = { saveAsLocalPlaylistChecked = it }
                                    )
                                    Text(
                                        text = "同时导入至本地自建歌单",
                                        fontSize = 12.sp,
                                        color = OrbitTheme.colors.textPrimary
                                    )
                                }
                            }

                            if (saveAsLocalPlaylistChecked || p.platform == null) {
                                OutlinedTextField(
                                    value = customPlaylistTitle,
                                    onValueChange = { customPlaylistTitle = it },
                                    label = { Text("本地保存歌单名称", fontSize = 12.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = OrbitTheme.colors.primary,
                                        unfocusedBorderColor = OrbitTheme.colors.primary.copy(alpha = 0.2f),
                                        cursorColor = OrbitTheme.colors.primary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 底部操作确认按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isImporting,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("取消", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            if (selectedTab == 0) {
                                // 执行网络/文本解析导入
                                if (inputText.isBlank()) {
                                    errorMessage = "请输入或粘贴歌单链接/文本"
                                    return@Button
                                }
                                isImporting = true
                                errorMessage = null
                                coroutineScope.launch {
                                    val parseResult = PlaylistTransferManager.importFromLinkOrText(context, inputText)
                                    parseResult.onSuccess { transferPlaylist ->
                                        if (transferPlaylist.platform != null) {
                                            PlaylistTransferManager.restoreToOnlineFavorites(context, transferPlaylist)
                                        }
                                        val pId = PlaylistTransferManager.saveTransferPlaylistToLocal(
                                            context,
                                            transferPlaylist,
                                            customPlaylistTitle.takeIf { it.isNotBlank() }
                                        )
                                        isImporting = false
                                        val finalTitle = customPlaylistTitle.ifBlank { transferPlaylist.title }
                                        val tip = if (transferPlaylist.platform != null) {
                                            "已成功恢复「$finalTitle」至【${transferPlaylist.platform.displayName}】网络收藏并存入本地"
                                        } else {
                                            "成功导入歌单「$finalTitle」(${transferPlaylist.songs.size} 首歌曲)"
                                        }
                                        FastToast.show(context, tip)
                                        onImportSuccess(pId, finalTitle, transferPlaylist.songs.size)
                                        onDismiss()
                                    }.onFailure { err ->
                                        isImporting = false
                                        errorMessage = "解析失败: ${err.localizedMessage ?: err.message}"
                                    }
                                }
                            } else {
                                // 执行本地文件导入
                                val playlist = parsedFilePlaylist
                                if (playlist == null) {
                                    errorMessage = "请先选择要导入的歌单文件"
                                    return@Button
                                }
                                isImporting = true
                                errorMessage = null
                                coroutineScope.launch {
                                    try {
                                        val isOnline = playlist.platform != null
                                        if (isOnline && restoreToOnlineFavoritesChecked) {
                                            PlaylistTransferManager.restoreToOnlineFavorites(context, playlist)
                                        }
                                        val pId = if (saveAsLocalPlaylistChecked || !isOnline) {
                                            PlaylistTransferManager.saveTransferPlaylistToLocal(
                                                context,
                                                playlist,
                                                customPlaylistTitle.takeIf { it.isNotBlank() }
                                            )
                                        } else {
                                            0L
                                        }
                                        isImporting = false
                                        val finalTitle = customPlaylistTitle.ifBlank { playlist.title }
                                        val tip = if (isOnline && restoreToOnlineFavoritesChecked && saveAsLocalPlaylistChecked) {
                                            "已恢复「$finalTitle」至【${playlist.platform?.displayName}】网络收藏并同步至本地歌单"
                                        } else if (isOnline && restoreToOnlineFavoritesChecked) {
                                            "已成功恢复「$finalTitle」至【${playlist.platform?.displayName}】网络歌单收藏"
                                        } else {
                                            "成功导入歌单「$finalTitle」(${playlist.songs.size} 首歌曲)"
                                        }
                                        FastToast.show(context, tip)
                                        onImportSuccess(pId, finalTitle, playlist.songs.size)
                                        onDismiss()
                                    } catch (e: Exception) {
                                        isImporting = false
                                        errorMessage = "导入失败: ${e.localizedMessage ?: e.message}"
                                    }
                                }
                            }
                        },
                        enabled = !isImporting,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OrbitTheme.colors.primary,
                            contentColor = if (OrbitTheme.colors.isDark) Color.Black else Color.White
                        ),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(44.dp)
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = if (OrbitTheme.colors.isDark) Color.Black else Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("正在解析导入...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("确认导入", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
