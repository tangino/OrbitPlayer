package com.orbit.music.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbit.music.data.online.engine.SourceScriptManager
import com.orbit.music.data.online.engine.ResolveStrategy
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
    val enabledScripts by sourceManager.enabledScripts.collectAsState()
    val activeScript by sourceManager.activeScript.collectAsState()
    val preferredQuality by sourceManager.preferredQuality.collectAsState()
    val resolveStrategy by sourceManager.resolveStrategy.collectAsState()

    val cloudSyncManager = remember { com.orbit.music.data.online.engine.CloudSourceSyncManager.getInstance(context) }
    var showUrlImportDialog by remember { mutableStateOf(false) }
    var showPasteImportDialog by remember { mutableStateOf(false) }
    var showCloudSyncDialog by remember { mutableStateOf(false) }
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

    val resolvedBgColor = if (OrbitTheme.colors.background == Color.Transparent) {
        if (OrbitTheme.colors.isDark) Color(0xFF101216) else Color(0xFFF8FAFC)
    } else {
        OrbitTheme.colors.background
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
                actions = {
                    // Cloudflare D1 云端备份与同步入口
                    FilledTonalButton(
                        onClick = { showCloudSyncDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = OrbitTheme.colors.primary.copy(alpha = 0.16f),
                            contentColor = OrbitTheme.colors.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "云端同步",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "云同步",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = resolvedBgColor)
            )
        },
        containerColor = resolvedBgColor
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. 多音源协同解析引擎状态卡片
            item {
                ActiveSourceStatusCard(
                    activeScript = activeScript,
                    enabledCount = enabledScripts.size,
                    totalCount = scripts.size,
                    onEnableAll = { sourceManager.enableAllScripts() },
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

            // 2.2 寻源解析调度策略配置 (支持 0.3.0 经典多源轮询 与 并发智能寻源)
            item {
                ResolveStrategyCard(
                    currentStrategy = resolveStrategy,
                    onSelectStrategy = { sourceManager.setResolveStrategy(it) }
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

            // 4. 音源列表标题 (支持多选并发启用与主备协同)
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
                        text = "支持同时开启多个，主源失效自动切备用源",
                        fontSize = 11.sp,
                        color = OrbitTheme.colors.primary
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

            // 6. 脚本列表项 (支持多选开启与设为主源)
            items(scripts, key = { it.id }) { item ->
                SourceScriptCard(
                    script = item,
                    isUpdating = isUpdatingId == item.id,
                    onToggleEnabled = { isEnabled -> sourceManager.toggleScriptEnabled(item.id, isEnabled) },
                    onSetPrimary = { sourceManager.setPrimaryScript(item.id) },
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

    // Cloudflare D1 音源云同步与备份恢复对话框
    if (showCloudSyncDialog) {
        CloudSourceSyncDialog(
            cloudSyncManager = cloudSyncManager,
            currentScripts = scripts,
            onDismiss = { showCloudSyncDialog = false },
            onMergeRestore = { restored ->
                sourceManager.mergeScripts(restored)
                Toast.makeText(context, "已增量合并 ${restored.size} 个云端音源", Toast.LENGTH_SHORT).show()
            },
            onReplaceRestore = { restored ->
                sourceManager.replaceAllScripts(restored)
                Toast.makeText(context, "已全量覆盖恢复 ${restored.size} 个云端音源", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

/**
 * 当前活动音源状态卡片 (多源协同与主备状态呈现)
 */
@Composable
private fun ActiveSourceStatusCard(
    activeScript: SourceScriptItem?,
    enabledCount: Int,
    totalCount: Int,
    onEnableAll: () -> Unit,
    onDisableAll: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = OrbitTheme.colors.surfaceCard,
        border = BorderStroke(1.dp, if (enabledCount > 0) OrbitTheme.colors.primary.copy(alpha = 0.4f) else OrbitTheme.colors.surfaceBorder),
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
                            .background(if (enabledCount > 0) Color(0xFF10B981) else OrbitTheme.colors.primary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (enabledCount > 0) "多音源协同寻源: 已开启 ($enabledCount 个音源在线)" else "活动解析引擎: 官方直链兜底",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrbitTheme.colors.textPrimary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (totalCount > 1 && enabledCount < totalCount) {
                        TextButton(
                            onClick = onEnableAll,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("全部开启", fontSize = 11.5.sp, color = OrbitTheme.colors.primary)
                        }
                    }
                    if (enabledCount > 0) {
                        TextButton(
                            onClick = onDisableAll,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("全部禁用", fontSize = 11.5.sp, color = Color(0xFFF43F5E))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (activeScript != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFFD700).copy(alpha = 0.16f),
                        border = BorderStroke(0.6.dp, Color(0xFFFFD700).copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "主音源",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = activeScript.name,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrbitTheme.colors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = OrbitTheme.colors.surface,
                        border = BorderStroke(0.5.dp, OrbitTheme.colors.surfaceBorder)
                    ) {
                        Text(
                            text = "v${activeScript.version}",
                            fontSize = 10.sp,
                            color = OrbitTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                if (enabledCount > 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🚀 当主音源无法解析某首歌曲时，其余 ${enabledCount - 1} 个备用音源将自动并发搜救",
                        fontSize = 11.sp,
                        color = OrbitTheme.colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                // 平台支持标签
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("主源平台:", fontSize = 11.sp, color = OrbitTheme.colors.textSecondary)
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
 * 寻源解析调度策略配置卡片 (0.3.0 经典多源轮询 / 并发智能寻源)
 */
@Composable
private fun ResolveStrategyCard(
    currentStrategy: ResolveStrategy,
    onSelectStrategy: (ResolveStrategy) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = OrbitTheme.colors.surfaceCard,
        border = BorderStroke(0.5.dp, OrbitTheme.colors.surfaceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "寻源解析调度策略",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OrbitTheme.colors.textPrimary
                )
                Text(
                    text = "支持双向预加载",
                    fontSize = 11.sp,
                    color = OrbitTheme.colors.primary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResolveStrategy.values().forEach { strategy ->
                    val isSelected = currentStrategy == strategy
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) OrbitTheme.colors.primary.copy(alpha = 0.15f)
                                else OrbitTheme.colors.surface
                            )
                            .border(
                                width = if (isSelected) 1.2.dp else 0.5.dp,
                                color = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.surfaceBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectStrategy(strategy) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = strategy.title,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (strategy == ResolveStrategy.CLASSIC_POLLING) "多源轮询 / 流长严检" else "多源并发 / 极速秒开",
                                fontSize = 10.sp,
                                color = OrbitTheme.colors.textSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单个音源卡片项 (分层优雅布局，支持 Switch 开关、设为主音源、更新与删除确认)
 */
@Composable
private fun SourceScriptCard(
    script: SourceScriptItem,
    isUpdating: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onSetPrimary: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (script.isEnabled) OrbitTheme.colors.surfaceCard else OrbitTheme.colors.surfaceCard.copy(alpha = 0.5f),
        border = BorderStroke(
            1.2.dp,
            if (script.isPrimary) Color(0xFFFFD700).copy(alpha = 0.7f)
            else if (script.isEnabled) OrbitTheme.colors.primary.copy(alpha = 0.45f)
            else OrbitTheme.colors.surfaceBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // 1. 顶部主行：左侧音源标识与名称、版本；右侧独立开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 主备徽章
                    if (script.isPrimary) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFFD700).copy(alpha = 0.18f),
                            border = BorderStroke(0.8.dp, Color(0xFFFFD700).copy(alpha = 0.8f))
                        ) {
                            Text(
                                text = "主音源",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD700),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    } else if (script.isEnabled) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            border = BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "备用协同",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // 音源名称
                    Text(
                        text = script.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (script.isEnabled) OrbitTheme.colors.textPrimary else OrbitTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // 版本号胶囊 (单行不换行，超长优雅截断)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = OrbitTheme.colors.surface,
                        border = BorderStroke(0.5.dp, OrbitTheme.colors.surfaceBorder)
                    ) {
                        Text(
                            text = "v${script.version}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = OrbitTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // 右侧独立 Switch 开关
                Switch(
                    checked = script.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = OrbitTheme.colors.primary,
                        uncheckedThumbColor = OrbitTheme.colors.textSecondary,
                        uncheckedTrackColor = OrbitTheme.colors.surface
                    ),
                    modifier = Modifier.scale(0.85f)
                )
            }

            // 2. 中部详情与描述
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "作者: ${script.author.ifBlank { "未知" }}",
                    fontSize = 12.sp,
                    color = OrbitTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (script.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = script.description,
                    fontSize = 12.sp,
                    color = OrbitTheme.colors.textSecondary.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }

            // 3. 底部操作栏
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(
                color = OrbitTheme.colors.surfaceBorder.copy(alpha = 0.5f),
                thickness = 0.6.dp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧状态说明
                if (script.isPrimary) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "首选解析主源",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFFD700)
                        )
                    }
                } else if (script.isEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "协同备用源",
                            fontSize = 11.5.sp,
                            color = Color(0xFF10B981)
                        )
                    }
                } else {
                    Text(
                        text = "未启用",
                        fontSize = 11.5.sp,
                        color = OrbitTheme.colors.textSecondary.copy(alpha = 0.6f)
                    )
                }

                // 右侧操作按钮组
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 设为主源按钮
                    if (script.isEnabled && !script.isPrimary) {
                        FilledTonalButton(
                            onClick = onSetPrimary,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = OrbitTheme.colors.primary.copy(alpha = 0.15f),
                                contentColor = OrbitTheme.colors.primary
                            ),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("设为主源", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // 在线更新按钮
                    if (!script.sourceUrl.isNullOrBlank()) {
                        IconButton(
                            onClick = onUpdate,
                            enabled = !isUpdating,
                            modifier = Modifier.size(30.dp)
                        ) {
                            if (isUpdating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = OrbitTheme.colors.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "检查更新",
                                    tint = OrbitTheme.colors.primary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    // 删除按钮 (带二次确认)
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "删除音源",
                            tint = Color(0xFFF43F5E),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "删除音源脚本",
                    fontWeight = FontWeight.Bold,
                    color = OrbitTheme.colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "确定要删除音源「${script.name}」吗？删除后将无法通过此脚本在线解析音乐。",
                    fontSize = 13.5.sp,
                    color = OrbitTheme.colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E))
                ) {
                    Text("确认删除", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消", color = OrbitTheme.colors.textSecondary)
                }
            },
            containerColor = OrbitTheme.colors.surfaceDialog
        )
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
                    placeholder = { Text("https://example.com/lx-source.js", fontSize = 12.sp, color = OrbitTheme.colors.textSecondary.copy(alpha = 0.6f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = OrbitTheme.colors.surfaceCard,
                        unfocusedContainerColor = OrbitTheme.colors.surfaceCard,
                        focusedBorderColor = OrbitTheme.colors.primary,
                        unfocusedBorderColor = OrbitTheme.colors.surfaceBorder,
                        focusedTextColor = OrbitTheme.colors.textPrimary,
                        unfocusedTextColor = OrbitTheme.colors.textPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (url.isNotBlank()) onConfirm(url) },
                enabled = url.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("下载并导入", color = if (OrbitTheme.colors.isDark) Color(0xFF101216) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = OrbitTheme.colors.textSecondary, fontSize = 12.sp)
            }
        },
        containerColor = OrbitTheme.colors.surfaceDialog,
        shape = RoundedCornerShape(16.dp)
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
                    placeholder = { Text("音源名称 (可选，留空自动提取)", fontSize = 12.sp, color = OrbitTheme.colors.textSecondary.copy(alpha = 0.6f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = OrbitTheme.colors.surfaceCard,
                        unfocusedContainerColor = OrbitTheme.colors.surfaceCard,
                        focusedBorderColor = OrbitTheme.colors.primary,
                        unfocusedBorderColor = OrbitTheme.colors.surfaceBorder,
                        focusedTextColor = OrbitTheme.colors.textPrimary,
                        unfocusedTextColor = OrbitTheme.colors.textPrimary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    placeholder = { Text("在此粘贴 JavaScript 脚本源码...", fontSize = 12.sp, color = OrbitTheme.colors.textSecondary.copy(alpha = 0.6f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = OrbitTheme.colors.surfaceCard,
                        unfocusedContainerColor = OrbitTheme.colors.surfaceCard,
                        focusedBorderColor = OrbitTheme.colors.primary,
                        unfocusedBorderColor = OrbitTheme.colors.surfaceBorder,
                        focusedTextColor = OrbitTheme.colors.textPrimary,
                        unfocusedTextColor = OrbitTheme.colors.textPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (code.isNotBlank()) onConfirm(name, code) },
                enabled = code.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("确认导入", color = if (OrbitTheme.colors.isDark) Color(0xFF101216) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = OrbitTheme.colors.textSecondary, fontSize = 12.sp)
            }
        },
        containerColor = OrbitTheme.colors.surfaceDialog,
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * Cloudflare D1 音源脚本多端云同步对话框 (基于账号密码多端互通)
 */
@Composable
private fun CloudSourceSyncDialog(
    cloudSyncManager: com.orbit.music.data.online.engine.CloudSourceSyncManager,
    currentScripts: List<SourceScriptItem>,
    onDismiss: () -> Unit,
    onMergeRestore: (List<SourceScriptItem>) -> Unit,
    onReplaceRestore: (List<SourceScriptItem>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoggedIn by remember { mutableStateOf(cloudSyncManager.isLoggedIn()) }
    var currentUsername by remember { mutableStateOf(cloudSyncManager.getUsername() ?: "") }
    
    var inputUsername by remember { mutableStateOf(cloudSyncManager.getUsername() ?: "") }
    var inputPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    
    var isAuthenticating by remember { mutableStateOf(false) }
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var isDeletingSource by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var lastBackupTime by remember { mutableStateOf(cloudSyncManager.getLastBackupTime()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccessStatus by remember { mutableStateOf(true) }
    var showServerSettings by remember { mutableStateOf(!cloudSyncManager.hasServerUrl()) }
    var serverUrlInput by remember { mutableStateOf(cloudSyncManager.getServerUrl()) }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                    Text("确认删除云端音源备份？", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OrbitTheme.colors.textPrimary)
                }
            },
            text = {
                Text(
                    text = "此操作将彻底删除云端账号 [$currentUsername] 存储的音源备份数据，其他设备将无法再从云端拉取该备份。\n本地设备上的音源将继续保留，不会受到影响。",
                    fontSize = 12.5.sp,
                    color = OrbitTheme.colors.textSecondary,
                    lineHeight = 17.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        isDeletingSource = true
                        statusMessage = "正在删除云端音源备份..."
                        isSuccessStatus = true
                        scope.launch {
                            val result = cloudSyncManager.deleteCloudSourceBackup()
                            isDeletingSource = false
                            if (result.isSuccess) {
                                lastBackupTime = 0L
                                statusMessage = "✅ ${result.getOrNull() ?: "云端音源已删除"}"
                                isSuccessStatus = true
                            } else {
                                statusMessage = "❌ 删除失败: ${result.exceptionOrNull()?.localizedMessage ?: "未知错误"}"
                                isSuccessStatus = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("确认删除", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("取消", fontSize = 12.sp, color = OrbitTheme.colors.textSecondary)
                }
            },
            containerColor = OrbitTheme.colors.surfaceDialog,
            shape = RoundedCornerShape(16.dp)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = OrbitTheme.colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (isLoggedIn) "音源云端同步" else "Orbit 云账号登录",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrbitTheme.colors.textPrimary
                    )
                }

                IconButton(
                    onClick = { showServerSettings = !showServerSettings },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "服务器设置",
                        tint = if (showServerSettings) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 0. 服务器地址配置面板 (展开时显示)
                if (showServerSettings) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = OrbitTheme.colors.surfaceCard,
                        border = BorderStroke(0.6.dp, OrbitTheme.colors.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🌐 云端 Worker API 节点地址",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = OrbitTheme.colors.primary
                            )
                            Text(
                                text = "默认连接 Cloudflare 边缘节点，若在国内受限可填入绑定的自定义域名",
                                fontSize = 10.sp,
                                color = OrbitTheme.colors.textSecondary,
                                lineHeight = 13.sp
                            )
                            OutlinedTextField(
                                value = serverUrlInput,
                                onValueChange = { serverUrlInput = it },
                                placeholder = { Text("https://...", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = OrbitTheme.colors.surface,
                                    unfocusedContainerColor = OrbitTheme.colors.surface,
                                    focusedBorderColor = OrbitTheme.colors.primary,
                                    unfocusedBorderColor = OrbitTheme.colors.surfaceBorder,
                                    focusedTextColor = OrbitTheme.colors.textPrimary,
                                    unfocusedTextColor = OrbitTheme.colors.textPrimary
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        cloudSyncManager.setServerUrl(serverUrlInput)
                                        serverUrlInput = cloudSyncManager.getServerUrl()
                                        Toast.makeText(context, "服务器地址已保存", Toast.LENGTH_SHORT).show()
                                        showServerSettings = false
                                    }
                                ) {
                                    Text("保存生效", fontSize = 11.5.sp, color = OrbitTheme.colors.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 1. 状态 A：未登录状态 -> 账号密码登录 / 自动注册表单
                if (!isLoggedIn) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = OrbitTheme.colors.surfaceCard,
                        border = BorderStroke(0.6.dp, OrbitTheme.colors.surfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "登录自定义账号后，即可在手机、车机与多端之间无缝同步音源！",
                                fontSize = 11.sp,
                                color = OrbitTheme.colors.textSecondary,
                                lineHeight = 15.sp
                            )

                            // 账号输入框
                            OutlinedTextField(
                                value = inputUsername,
                                onValueChange = { inputUsername = it },
                                label = { Text("账号名称 (如手机号/昵称)", fontSize = 11.sp) },
                                placeholder = { Text("输入您的自定义账号", fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = OrbitTheme.colors.surface,
                                    unfocusedContainerColor = OrbitTheme.colors.surface,
                                    focusedBorderColor = OrbitTheme.colors.primary,
                                    unfocusedBorderColor = OrbitTheme.colors.surfaceBorder,
                                    focusedTextColor = OrbitTheme.colors.textPrimary,
                                    unfocusedTextColor = OrbitTheme.colors.textPrimary
                                )
                            )

                            // 密码输入框
                            OutlinedTextField(
                                value = inputPassword,
                                onValueChange = { inputPassword = it },
                                label = { Text("密码 (至少 4 位)", fontSize = 11.sp) },
                                placeholder = { Text("输入账号密码", fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }, modifier = Modifier.size(24.dp)) {
                                        Icon(
                                            imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = OrbitTheme.colors.textSecondary
                                        )
                                    }
                                },
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = OrbitTheme.colors.surface,
                                    unfocusedContainerColor = OrbitTheme.colors.surface,
                                    focusedBorderColor = OrbitTheme.colors.primary,
                                    unfocusedBorderColor = OrbitTheme.colors.surfaceBorder,
                                    focusedTextColor = OrbitTheme.colors.textPrimary,
                                    unfocusedTextColor = OrbitTheme.colors.textPrimary
                                )
                            )

                            Text(
                                text = "✨ 免注册机制：输入新账号和密码将自动创建，已有账号将验证密码登录",
                                fontSize = 10.sp,
                                color = OrbitTheme.colors.primary.copy(alpha = 0.85f)
                            )

                            // 登录/注册提交按钮
                            Button(
                                onClick = {
                                    if (inputUsername.trim().length < 2) {
                                        Toast.makeText(context, "账号名称不能少于 2 位", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (inputPassword.trim().length < 4) {
                                        Toast.makeText(context, "密码不能少于 4 位", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }

                                    isAuthenticating = true
                                    statusMessage = "正在连接云端验证账号..."
                                    isSuccessStatus = true
                                    scope.launch {
                                        val result = cloudSyncManager.loginOrRegister(inputUsername, inputPassword)
                                        isAuthenticating = false
                                        if (result.isSuccess) {
                                            val info = result.getOrNull()
                                            isLoggedIn = true
                                            currentUsername = info?.username ?: inputUsername.trim()
                                            lastBackupTime = cloudSyncManager.getLastBackupTime()
                                            statusMessage = "✅ ${info?.message ?: "登录成功！"}"
                                            isSuccessStatus = true
                                        } else {
                                            statusMessage = "❌ ${result.exceptionOrNull()?.localizedMessage ?: "登录失败"}"
                                            isSuccessStatus = false
                                        }
                                    }
                                },
                                enabled = !isAuthenticating,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary)
                            ) {
                                if (isAuthenticating) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("正在验证...", fontSize = 12.sp)
                                } else {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("登录 / 自动注册", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // 2. 状态 B：已登录状态 -> 展示当前账号卡片与一键备份/恢复操作
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = OrbitTheme.colors.surfaceCard,
                        border = BorderStroke(0.6.dp, OrbitTheme.colors.surfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = OrbitTheme.colors.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = currentUsername,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = OrbitTheme.colors.primary
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { showDeleteConfirmDialog = true },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        enabled = !isBackingUp && !isRestoring && !isDeletingSource
                                    ) {
                                        Text("🗑️ 删除云端备份", fontSize = 11.sp, color = Color(0xFFFF5252))
                                    }

                                    TextButton(
                                        onClick = {
                                            cloudSyncManager.logout()
                                            isLoggedIn = false
                                            currentUsername = ""
                                            inputPassword = ""
                                            statusMessage = "已退出当前账号"
                                            isSuccessStatus = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Text("退出账号", fontSize = 11.sp, color = OrbitTheme.colors.textSecondary)
                                    }
                                }
                            }

                            if (lastBackupTime > 0L) {
                                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                Text(
                                    text = "上次云端备份时间: ${sdf.format(java.util.Date(lastBackupTime))}",
                                    fontSize = 10.sp,
                                    color = OrbitTheme.colors.textSecondary.copy(alpha = 0.7f)
                                )
                            } else {
                                Text(
                                    text = "云端暂无此账号的音源备份",
                                    fontSize = 10.sp,
                                    color = OrbitTheme.colors.textSecondary.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }

                    // 上传备份按钮
                    Button(
                        onClick = {
                            if (currentScripts.isEmpty()) {
                                Toast.makeText(context, "当前未导入任何音源，无需备份", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isBackingUp = true
                            statusMessage = "正在上传备份至云端账号..."
                            isSuccessStatus = true
                            scope.launch {
                                val result = cloudSyncManager.backupSourcesToCloud(currentScripts)
                                isBackingUp = false
                                if (result.isSuccess) {
                                    val info = result.getOrNull()
                                    lastBackupTime = info?.updatedAt ?: System.currentTimeMillis()
                                    statusMessage = "✅ 成功备份 ${info?.sourceCount} 个音源至云端账号 [$currentUsername]！"
                                    isSuccessStatus = true
                                } else {
                                    statusMessage = "❌ 备份失败: ${result.exceptionOrNull()?.localizedMessage ?: "未知错误"}"
                                    isSuccessStatus = false
                                }
                            }
                        },
                        enabled = !isBackingUp && !isRestoring,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary)
                    ) {
                        if (isBackingUp) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("正在上传备份至云端...", fontSize = 12.sp)
                        } else {
                            Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("一键上传备份当前音源 (${currentScripts.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 恢复操作按钮行 (合并恢复 / 覆盖恢复)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                isRestoring = true
                                statusMessage = "正在从云端拉取备份..."
                                isSuccessStatus = true
                                scope.launch {
                                    val result = cloudSyncManager.restoreSourcesFromCloud()
                                    isRestoring = false
                                    if (result.isSuccess) {
                                        val list = result.getOrNull() ?: emptyList()
                                        if (list.isEmpty()) {
                                            statusMessage = "云端未找到音源数据"
                                            isSuccessStatus = false
                                        } else {
                                            onMergeRestore(list)
                                            statusMessage = "✅ 已增量合并 ${list.size} 个云端音源！"
                                            isSuccessStatus = true
                                        }
                                    } else {
                                        statusMessage = "❌ 拉取失败: ${result.exceptionOrNull()?.localizedMessage ?: "未找到备份"}"
                                        isSuccessStatus = false
                                    }
                                }
                            },
                            enabled = !isBackingUp && !isRestoring,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isRestoring) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                            } else {
                                Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("增量合并恢复", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                isRestoring = true
                                statusMessage = "正在从云端拉取备份..."
                                isSuccessStatus = true
                                scope.launch {
                                    val result = cloudSyncManager.restoreSourcesFromCloud()
                                    isRestoring = false
                                    if (result.isSuccess) {
                                        val list = result.getOrNull() ?: emptyList()
                                        if (list.isEmpty()) {
                                            statusMessage = "云端未找到音源数据"
                                            isSuccessStatus = false
                                        } else {
                                            onReplaceRestore(list)
                                            statusMessage = "✅ 已全量覆盖恢复 ${list.size} 个云端音源！"
                                            isSuccessStatus = true
                                        }
                                    } else {
                                        statusMessage = "❌ 拉取失败: ${result.exceptionOrNull()?.localizedMessage ?: "未找到备份"}"
                                        isSuccessStatus = false
                                    }
                                }
                            },
                            enabled = !isBackingUp && !isRestoring,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("全量覆盖恢复", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFFFF9800))
                        }
                    }

                    // 跨端使用提示
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = OrbitTheme.colors.surfaceCard.copy(alpha = 0.5f),
                        border = BorderStroke(0.5.dp, OrbitTheme.colors.surfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "💡 手机与车机跨端同步说明：",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = OrbitTheme.colors.primary
                            )
                            Text(
                                text = "1. 📱 手机端：登录此账号，点击【一键上传备份当前音源】\n2. 🚗 车机端：登录同一个账号，点击【全量覆盖恢复】，音源即刻同步！",
                                fontSize = 10.sp,
                                color = OrbitTheme.colors.textSecondary,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // 2. 状态与提示信息
                if (statusMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSuccessStatus) OrbitTheme.colors.primary.copy(alpha = 0.12f) else Color(0xFFFF3366).copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, if (isSuccessStatus) OrbitTheme.colors.primary.copy(alpha = 0.4f) else Color(0xFFFF3366).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusMessage ?: "",
                            fontSize = 11.5.sp,
                            color = if (isSuccessStatus) OrbitTheme.colors.primary else Color(0xFFFF3366),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary)
            ) {
                Text("完成", color = if (OrbitTheme.colors.isDark) Color(0xFF101216) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = OrbitTheme.colors.surfaceDialog,
        shape = RoundedCornerShape(16.dp)
    )
}
