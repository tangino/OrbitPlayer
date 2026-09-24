package com.orbit.music.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.orbit.music.data.online.engine.SourceScriptManager
import com.orbit.music.data.online.model.SourceScriptItem
import com.orbit.music.ui.theme.*
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 在线音源管理与订阅设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSourceManagementScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sourceManager = remember { SourceScriptManager.getInstance(context) }

    val scripts by sourceManager.scripts.collectAsState()
    val activeScript by sourceManager.activeScript.collectAsState()
    val preferredQuality by sourceManager.preferredQuality.collectAsState()

    var showUrlImportDialog by remember { mutableStateOf(false) }
    var showPasteImportDialog by remember { mutableStateOf(false) }
    var isUpdatingId by remember { mutableStateOf<String?>(null) }

    // 本地 .js 文件导入选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val content = reader.readText()
                    val result = sourceManager.importFromText(content)
                    if (result.isSuccess) {
                        Toast.makeText(context, "成功导入音源: ${result.getOrNull()?.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "导入失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "读取文件失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "在线音源管理",
                        fontWeight = FontWeight.Bold,
                        color = OrbitTheme.colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = OrbitTheme.colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OrbitTheme.colors.background)
            )
        },
        containerColor = OrbitTheme.colors.background
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. 活动音源状态卡片
            item {
                ActiveSourceStatusCard(
                    activeScript = activeScript,
                    onDisableAll = { sourceManager.disableAllScripts() }
                )
            }

            // 2. 默认音质偏好配置
            item {
                QualityPreferenceCard(
                    currentQuality = preferredQuality,
                    onSelectQuality = { sourceManager.setPreferredQuality(it) }
                )
            }

            // 3. 快速导入操作栏
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showUrlImportDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OrbitTheme.colors.primary),
                        border = BorderStroke(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("网络导入", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OrbitTheme.colors.textPrimary),
                        border = BorderStroke(0.5.dp, OrbitTheme.colors.surfaceBorder)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("本地文件", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = { showPasteImportDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OrbitTheme.colors.textPrimary),
                        border = BorderStroke(0.5.dp, OrbitTheme.colors.surfaceBorder)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("粘贴代码", fontSize = 13.sp)
                    }
                }
            }

            // 4. 音源列表标题
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已安装音源脚本 (${scripts.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrbitTheme.colors.textPrimary
                    )
                    Text(
                        text = "点击即可切换激活",
                        fontSize = 11.5.sp,
                        color = OrbitTheme.colors.textSecondary
                    )
                }
            }

            // 5. 空状态提示
            if (scripts.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = OrbitTheme.colors.surfaceCard,
                        border = BorderStroke(0.5.dp, OrbitTheme.colors.surfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicOff,
                                contentDescription = null,
                                tint = OrbitTheme.colors.textSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "暂未导入第三方音源脚本",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OrbitTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "目前正在使用网易云官方公共直链兜底播放。导入洛雪标准音源脚本即可解锁多平台高品质与无损音乐！",
                                fontSize = 12.sp,
                                color = OrbitTheme.colors.textSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            // 6. 脚本列表项
            items(scripts, key = { it.id }) { item ->
                SourceScriptCard(
                    script = item,
                    isUpdating = isUpdatingId == item.id,
                    onEnable = { sourceManager.enableScript(item.id) },
                    onDelete = { sourceManager.deleteScript(item.id) },
                    onUpdate = {
                        scope.launch {
                            isUpdatingId = item.id
                            val res = sourceManager.updateScript(item.id)
                            isUpdatingId = null
                            if (res.isSuccess) {
                                Toast.makeText(context, "音源已更新至 v${res.getOrNull()?.version}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "更新失败: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }
        }
    }

    // 网络导入对话框
    if (showUrlImportDialog) {
        ImportUrlDialog(
            onDismiss = { showUrlImportDialog = false },
            onConfirm = { url ->
                showUrlImportDialog = false
                scope.launch {
                    val res = sourceManager.importFromUrl(url)
                    if (res.isSuccess) {
                        Toast.makeText(context, "已成功导入并激活: ${res.getOrNull()?.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "导入失败: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // 粘贴代码导入对话框
    if (showPasteImportDialog) {
        PasteScriptDialog(
            onDismiss = { showPasteImportDialog = false },
            onConfirm = { name, code ->
                showPasteImportDialog = false
                val res = sourceManager.importFromText(code, customName = name)
                if (res.isSuccess) {
                    Toast.makeText(context, "已成功导入并激活: ${res.getOrNull()?.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "导入失败: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}

/**
 * 当前活动音源状态卡片
 */
@Composable
private fun ActiveSourceStatusCard(
    activeScript: SourceScriptItem?,
    onDisableAll: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = OrbitTheme.colors.surfaceCard,
        border = BorderStroke(1.dp, if (activeScript != null) OrbitTheme.colors.primary.copy(alpha = 0.4f) else OrbitTheme.colors.surfaceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (activeScript != null) Color(0xFF10B981) else OrbitTheme.colors.primary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (activeScript != null) "活动解析引擎: 已就绪" else "活动解析引擎: 官方直链兜底",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrbitTheme.colors.textPrimary
                    )
                }

                if (activeScript != null) {
                    TextButton(
                        onClick = onDisableAll,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("禁用脚本", fontSize = 11.5.sp, color = Color(0xFFF43F5E))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (activeScript != null) {
                Text(
                    text = "${activeScript.name} (v${activeScript.version})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = OrbitTheme.colors.primary
                )
                if (activeScript.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = activeScript.description,
                        fontSize = 11.5.sp,
                        color = OrbitTheme.colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                // 平台支持标签
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("支持平台:", fontSize = 11.sp, color = OrbitTheme.colors.textSecondary)
                    listOf("wy" to "网易云", "tx" to "QQ音乐", "kg" to "酷狗", "kw" to "酷我", "mg" to "咪咕").forEach { (key, label) ->
                        val isSupported = activeScript.supportPlatforms.contains(key) || activeScript.supportPlatforms.isEmpty()
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isSupported) OrbitTheme.colors.primary.copy(alpha = 0.15f) else OrbitTheme.colors.surface.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .border(
                                    width = 0.5.dp,
                                    color = if (isSupported) OrbitTheme.colors.primary.copy(alpha = 0.4f) else Color.Transparent,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSupported) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSupported) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "网易云音乐公共直链模式 (支持大多数免 VIP 曲目播放)",
                    fontSize = 12.5.sp,
                    color = OrbitTheme.colors.textSecondary
                )
            }
        }
    }
}

/**
 * 音质偏好选择卡片
 */
@Composable
private fun QualityPreferenceCard(
    currentQuality: String,
    onSelectQuality: (String) -> Unit
) {
    val qualities = listOf(
        "128k" to "标准 (128K)",
        "320k" to "高品 (320K)",
        "flac" to "无损 (FLAC)",
        "flac24bit" to "母带 (Hi-Res)"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = OrbitTheme.colors.surfaceCard,
        border = BorderStroke(0.5.dp, OrbitTheme.colors.surfaceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "首选解析音质 (优先请求所选音质，失败时自动降级)",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = OrbitTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                qualities.forEach { (key, label) ->
                    val isSelected = currentQuality == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.surface)
                            .border(
                                width = 0.5.dp,
                                color = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.surfaceBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectQuality(key) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) (if (OrbitTheme.colors.isDark) Color(0xFF101216) else Color.White) else OrbitTheme.colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个音源卡片项
 */
@Composable
private fun SourceScriptCard(
    script: SourceScriptItem,
    isUpdating: Boolean,
    onEnable: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (script.isEnabled) OrbitTheme.colors.surfaceCard else OrbitTheme.colors.surfaceCard.copy(alpha = 0.6f),
        border = BorderStroke(
            1.dp,
            if (script.isEnabled) OrbitTheme.colors.primary.copy(alpha = 0.5f) else OrbitTheme.colors.surfaceBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!script.isEnabled) onEnable() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = script.isEnabled,
                    onClick = onEnable,
                    colors = RadioButtonDefaults.colors(selectedColor = OrbitTheme.colors.primary)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = script.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (script.isEnabled) OrbitTheme.colors.primary else OrbitTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(OrbitTheme.colors.surface, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "v${script.version}",
                                fontSize = 10.sp,
                                color = OrbitTheme.colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "作者: ${script.author}",
                        fontSize = 11.5.sp,
                        color = OrbitTheme.colors.textSecondary
                    )
                }

                // 操作按钮组
                if (!script.sourceUrl.isNullOrBlank()) {
                    IconButton(
                        onClick = onUpdate,
                        enabled = !isUpdating,
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = OrbitTheme.colors.primary)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "在线检查更新",
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "删除音源",
                        tint = Color(0xFFF43F5E).copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (script.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = script.description,
                    fontSize = 11.5.sp,
                    color = OrbitTheme.colors.textSecondary.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

/**
 * 导入网络 URL 对话框
 */
@Composable
private fun ImportUrlDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("导入网络音源脚本", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OrbitTheme.colors.textPrimary)
        },
        text = {
            Column {
                Text(
                    text = "支持粘贴洛雪标准音源脚本（.js）的网络下载直链：",
                    fontSize = 12.sp,
                    color = OrbitTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("https://example.com/lx-source.js", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (url.isNotBlank()) onConfirm(url) },
                enabled = url.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary)
            ) {
                Text("下载并导入", color = if (OrbitTheme.colors.isDark) Color(0xFF101216) else Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = OrbitTheme.colors.textSecondary)
            }
        }
    )
}

/**
 * 粘贴代码导入对话框
 */
@Composable
private fun PasteScriptDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, code: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("粘贴脚本代码", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OrbitTheme.colors.textPrimary)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("音源名称 (可选，留空自动提取)", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    placeholder = { Text("在此粘贴 JavaScript 脚本源码...", fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (code.isNotBlank()) onConfirm(name, code) },
                enabled = code.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary)
            ) {
                Text("确认导入", color = if (OrbitTheme.colors.isDark) Color(0xFF101216) else Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = OrbitTheme.colors.textSecondary)
            }
        }
    )
}
